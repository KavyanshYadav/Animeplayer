import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useQuery } from "@tanstack/react-query";

import {
  fetchBackendStatus,
  onBackendStatus,
  restartBackend,
  type BackendStatus,
} from "./backend";
import { createLibraryClient, type HistoryEntry, type LibraryClient } from "./server";

interface LibraryContextValue {
  status: BackendStatus;
  client: LibraryClient | null;
  retry: () => void;
}

const LibraryContext = createContext<LibraryContextValue | null>(null);

/**
 * Waits for the Kotlin sidecar and hands its client to the rest of the application.
 */
export function LibraryProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<BackendStatus>({
    state: "starting",
    baseUrl: "",
    port: 0,
    message: null,
  });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let alive = true;
    let timer: number | undefined;

    const poll = async () => {
      let next: BackendStatus;
      try {
        next = await fetchBackendStatus();
      } catch {
        next = {
          state: "failed",
          baseUrl: "",
          port: 0,
          message: "The animeplayer server is not reachable.",
        };
      }
      if (!alive) return;
      setStatus(next);
      if (next.state === "starting") {
        timer = window.setTimeout(poll, 400);
      }
    };

    void poll();
    const unlisten = onBackendStatus((next) => setStatus(next));

    return () => {
      alive = false;
      if (timer) window.clearTimeout(timer);
      void unlisten.then((stop) => stop());
    };
  }, [attempt]);

  const client = useMemo(
    () => (status.state === "ready" ? createLibraryClient(status.baseUrl) : null),
    [status.baseUrl, status.state],
  );

  const retry = useCallback(() => {
    setStatus((current) => ({ ...current, state: "starting", message: null }));
    void restartBackend().catch(() => {
      /* the polling loop reports the failure */
    });
    setAttempt((value) => value + 1);
  }, []);

  return (
    <LibraryContext.Provider value={{ status, client, retry }}>
      {children}
    </LibraryContext.Provider>
  );
}

/** The client of the library — `null` while the sidecar is not ready. */
export function useLibrary(): LibraryContextValue {
  const value = useContext(LibraryContext);
  if (!value) {
    throw new Error("useLibrary must be used inside a LibraryProvider.");
  }
  return value;
}

/**
 * The watch history.
 *
 * The history changes while an episode plays, so it is always read again when a page
 * opens.
 */
export function useHistory() {
  const { client } = useLibrary();
  return useQuery({
    queryKey: ["history"],
    queryFn: () => client!.history(),
    enabled: Boolean(client),
    staleTime: 0,
    refetchOnMount: "always",
  });
}

/** The watch history as a map of episode number to progress. */
export function useWatchedProgress(slug?: string): Record<string, number> {
  const history = useHistory();
  const entries: HistoryEntry[] = history.data ?? [];
  return useMemo(() => {
    const map: Record<string, number> = {};
    for (const entry of entries) {
      if (!slug || entry.slug === slug) map[entry.episode] = entry.progress;
    }
    return map;
  }, [entries, slug]);
}
