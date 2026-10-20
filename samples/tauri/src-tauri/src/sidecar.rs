//! The Kotlin server sidecar.
//!
//! The library is written in Kotlin, so the desktop sample cannot call it directly:
//! it runs it as a separate process and talks to it over HTTP. This module owns that
//! process — where the launcher lives, how it is started and how it is stopped again.

use std::io::{Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::time::Duration;

/// The name of the launcher, without the target triple.
const LAUNCHER: &str = "animeplayer-server";

/// The running server.
pub struct Sidecar {
    child: Child,
}

impl Sidecar {
    /// Starts the server on the given port.
    pub fn spawn(port: u16) -> Result<Self, String> {
        let launcher = locate().ok_or_else(|| {
            format!(
                "The animeplayer server launcher was not found. Run `npm run sidecar:build` \
                 in samples/tauri to build it."
            )
        })?;

        let mut command = Command::new(&launcher);
        command
            .env("PORT", port.to_string())
            .env("HOST", "127.0.0.1")
            .env("PUBLIC_URL", format!("http://127.0.0.1:{port}"))
            .env("ANIMEPLAYER_STATE_DIR", state_directory())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());

        let child = command
            .spawn()
            .map_err(|error| format!("Could not start {}: {error}", launcher.display()))?;

        Ok(Self { child })
    }

    /// Stops the server.
    pub fn kill(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

impl Drop for Sidecar {
    fn drop(&mut self) {
        self.kill();
    }
}

/// Picks a port the operating system is not using.
pub fn free_port() -> u16 {
    TcpListener::bind("127.0.0.1:0")
        .and_then(|listener| listener.local_addr())
        .map(|address| address.port())
        .unwrap_or(8791)
}

/// Asks the server whether it is alive.
pub fn health_ok(port: u16) -> bool {
    let address = SocketAddr::from(([127, 0, 0, 1], port));
    let mut stream = match TcpStream::connect_timeout(&address, Duration::from_millis(500)) {
        Ok(stream) => stream,
        Err(_) => return false,
    };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(1000)));

    let request = format!(
        "GET /api/health HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n"
    );
    if stream.write_all(request.as_bytes()).is_err() {
        return false;
    }

    let mut buffer = [0u8; 64];
    match stream.read(&mut buffer) {
        Ok(read) if read > 12 => String::from_utf8_lossy(&buffer[..read]).starts_with("HTTP/1.1 200"),
        _ => false,
    }
}

/// Finds the launcher of the server.
///
/// The lookup follows the places the launcher can live:
///
/// 1. `ANIMEPLAYER_SIDECAR` — an explicit override, used by the development scripts.
/// 2. Next to the executable — the bundled layout.
/// 3. `src-tauri/binaries` — the development layout, relative to `target/debug`.
fn locate() -> Option<PathBuf> {
    if let Ok(path) = std::env::var("ANIMEPLAYER_SIDECAR") {
        let path = PathBuf::from(path);
        if path.exists() {
            return Some(path);
        }
    }

    let executable = std::env::current_exe().ok()?;
    let directory = executable.parent()?;

    let bundled = directory.join(LAUNCHER);
    if bundled.exists() {
        return Some(bundled);
    }

    let triple = env!("ANIMEPLAYER_TARGET_TRIPLE");
    let binaries = directory.join("../../binaries");
    for name in [format!("{LAUNCHER}-{triple}"), LAUNCHER.to_string()] {
        let candidate = binaries.join(name);
        if candidate.exists() {
            return candidate.canonicalize().ok().or(Some(candidate));
        }
    }

    None
}

/// The directory the watch history lives in.
fn state_directory() -> String {
    if let Ok(path) = std::env::var("ANIMEPLAYER_STATE_DIR") {
        return path;
    }
    let home = std::env::var("HOME")
        .or_else(|_| std::env::var("USERPROFILE"))
        .unwrap_or_else(|_| ".".to_string());
    format!("{home}/.local/state/animeplayer-desktop")
}
