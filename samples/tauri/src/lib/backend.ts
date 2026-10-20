/**
 * The state of the Kotlin sidecar.
 *
 * The desktop sample starts the library as a separate process. The frontend asks the
 * Rust side where to find it and waits until it answers.
 */
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

export interface BackendStatus {
  /** `starting`, `ready` or `failed`. */
  state: "starting" | "ready" | "failed";
  baseUrl: string;
  port: number;
  message: string | null;
}

/** The address of the server when the frontend runs in a plain browser. */
const FALLBACK_URL = import.meta.env.VITE_LIBRARY_URL ?? "http://127.0.0.1:8791";

/** `true` when the frontend runs inside the Tauri webview. */
export function isTauri(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

/** The status of the sidecar. */
export async function fetchBackendStatus(): Promise<BackendStatus> {
  if (!isTauri()) {
    // Outside of the desktop shell the server has to be started by hand.
    return { state: "ready", baseUrl: FALLBACK_URL, port: 0, message: null };
  }
  return invoke<BackendStatus>("backend_status");
}

/** Starts the sidecar again. */
export async function restartBackend(): Promise<BackendStatus> {
  return invoke<BackendStatus>("restart_backend");
}

/** Subscribes to the status changes of the sidecar. */
export function onBackendStatus(
  handler: (status: BackendStatus) => void,
): Promise<() => void> {
  if (!isTauri()) {
    return Promise.resolve(() => {});
  }
  return listen<BackendStatus>("backend-status", (event) => handler(event.payload));
}
