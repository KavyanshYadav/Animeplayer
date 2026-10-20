fn main() {
    tauri_build::build();

    // The sidecar binary carries the target triple in its name, exactly like the
    // binaries Tauri bundles. The triple is the one of the machine that builds the
    // application, which is the one that runs it in development.
    let rustc = std::env::var("RUSTC").unwrap_or_else(|_| "rustc".to_string());
    let triple = std::process::Command::new(rustc)
        .args(["--print", "host-tuple"])
        .output()
        .ok()
        .and_then(|output| String::from_utf8(output.stdout).ok())
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| "unknown".to_string());

    println!("cargo:rustc-env=ANIMEPLAYER_TARGET_TRIPLE={triple}");
    println!("cargo:rerun-if-changed=build.rs");
}
