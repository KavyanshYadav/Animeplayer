import { Loader2, RefreshCw, TriangleAlert } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useLibrary } from "@/lib/library";

/**
 * Holds the application until the library is ready.
 *
 * The Kotlin library runs as a sidecar process; the JVM needs a moment to start.
 */
export function BackendGate({ children }: { children: React.ReactNode }) {
  const { status, retry } = useLibrary();

  if (status.state === "ready") {
    return <>{children}</>;
  }

  if (status.state === "failed") {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <div className="max-w-md space-y-4 text-center">
          <TriangleAlert className="mx-auto size-8 text-destructive" />
          <div className="space-y-1">
            <h1 className="text-lg font-semibold">The library did not start</h1>
            <p className="text-sm text-muted-foreground">
              {status.message ?? "The animeplayer server could not be started."}
            </p>
          </div>
          <div className="flex justify-center gap-2">
            <Button onClick={retry}>
              <RefreshCw className="size-4" />
              Try again
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            The server is built by{" "}
            <code className="rounded bg-muted px-1 py-0.5">npm run sidecar:build</code>.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full items-center justify-center p-6">
      <div className="space-y-3 text-center">
        <Loader2 className="mx-auto size-7 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">Starting the animeplayer library…</p>
      </div>
    </div>
  );
}
