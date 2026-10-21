//! The desktop sample.
//!
//! The shell is thin on purpose: the interesting work happens in the Kotlin library
//! that runs as a sidecar process. This file starts that process, waits until it
//! answers on its health endpoint and tells the frontend where to find it.

mod sidecar;

use std::sync::{Arc, Mutex};
use std::time::Duration;

use tauri::{Emitter, Manager};

/// How far along the sidecar is.
#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackendStatus {
    /// `starting`, `ready` or `failed`.
    pub state: String,
    /// The base URL of the Kotlin server, empty while it is not known yet.
    pub base_url: String,
    pub port: u16,
    /// A human readable explanation, set when the state is `failed`.
    pub message: Option<String>,
}

impl Default for BackendStatus {
    fn default() -> Self {
        Self {
            state: "starting".to_string(),
            base_url: String::new(),
            port: 0,
            message: None,
        }
    }
}

/// The sidecar and its state.
#[derive(Default)]
struct Backend {
    status: BackendStatus,
    sidecar: Option<sidecar::Sidecar>,
}

type BackendRef = Arc<Mutex<Backend>>;

/// The state of the Kotlin server.
#[tauri::command]
fn backend_status(backend: tauri::State<'_, BackendRef>) -> BackendStatus {
    status_of(&backend)
}

/// Starts the server again — useful when the first attempt failed.
#[tauri::command]
fn restart_backend(app: tauri::AppHandle, backend: tauri::State<'_, BackendRef>) -> BackendStatus {
    {
        let mut guard = backend.lock().expect("the backend lock is poisoned");
        guard.sidecar = None;
        guard.status = BackendStatus::default();
    }
    let backend = backend.inner().clone();
    let worker = Arc::clone(&backend);
    std::thread::spawn(move || start(&app, &worker));
    status_of(&backend)
}

/// The status of the backend.
fn status_of(backend: &BackendRef) -> BackendStatus {
    backend
        .lock()
        .expect("the backend lock is poisoned")
        .status
        .clone()
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![backend_status, restart_backend])
        .setup(|app| {
            let backend: BackendRef = Arc::new(Mutex::new(Backend::default()));
            app.manage(backend.clone());

            // The setup hook must not block, the window is already on screen.
            let handle = app.handle().clone();
            std::thread::spawn(move || start(&handle, &backend));
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building the animeplayer desktop sample");

    app.run(|app_handle, event| {
        if let tauri::RunEvent::Exit = event {
            // Dropping the sidecar stops the JVM.
            if let Some(backend) = app_handle.try_state::<BackendRef>() {
                if let Ok(mut guard) = backend.lock() {
                    guard.sidecar = None;
                }
            }
        }
    });
}

/// Starts the Kotlin server and waits until it is ready.
fn start(app: &tauri::AppHandle, backend: &BackendRef) {
    let port = sidecar::free_port();
    publish(
        app,
        backend,
        BackendStatus {
            state: "starting".to_string(),
            base_url: String::new(),
            port,
            message: None,
        },
    );

    // The server is bundled with the application; in development it sits in the
    // resources of the crate.
    let resource_dir = app.path().resource_dir().ok();

    let child = match sidecar::Sidecar::spawn(port, resource_dir.as_deref()) {
        Ok(child) => child,
        Err(message) => {
            publish(
                app,
                backend,
                BackendStatus {
                    state: "failed".to_string(),
                    base_url: String::new(),
                    port,
                    message: Some(message),
                },
            );
            return;
        }
    };
    backend
        .lock()
        .expect("the backend lock is poisoned")
        .sidecar = Some(child);

    // The JVM needs a moment to start.
    for _ in 0..120 {
        std::thread::sleep(Duration::from_millis(500));
        if sidecar::health_ok(port) {
            publish(
                app,
                backend,
                BackendStatus {
                    state: "ready".to_string(),
                    base_url: format!("http://127.0.0.1:{port}"),
                    port,
                    message: None,
                },
            );
            return;
        }
    }

    publish(
        app,
        backend,
        BackendStatus {
            state: "failed".to_string(),
            base_url: String::new(),
            port,
            message: Some("The animeplayer server did not become ready in time.".to_string()),
        },
    );
}

/// Stores the status and tells the frontend about it.
fn publish(app: &tauri::AppHandle, backend: &BackendRef, status: BackendStatus) {
    backend
        .lock()
        .expect("the backend lock is poisoned")
        .status = status.clone();
    let _ = app.emit("backend-status", status);
}
