import { useQuery } from "@tanstack/react-query";
import { CalendarDays } from "lucide-react";

import { Skeleton } from "@/components/ui/skeleton";
import { useLibrary } from "@/lib/library";

/**
 * The broadcast schedule.
 *
 * The schedule comes from a different site than the streams — the library talks to
 * both of them.
 */
export function Airing() {
  const { client } = useLibrary();

  const schedule = useQuery({
    queryKey: ["schedule"],
    queryFn: () => client!.schedule(),
    enabled: Boolean(client),
    staleTime: 10 * 60 * 1000,
  });

  return (
    <div className="space-y-6 p-6 lg:p-10">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold tracking-tight">Airing</h1>
        <p className="text-sm text-muted-foreground">
          What is airing today, according to the schedule of the library.
        </p>
      </header>

      {schedule.isLoading && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="h-28 w-full rounded-xl" />
          ))}
        </div>
      )}

      {schedule.data?.length === 0 && (
        <p className="rounded-xl border border-dashed border-border/60 p-8 text-center text-sm text-muted-foreground">
          The schedule is empty.
        </p>
      )}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {schedule.data?.map((entry) => (
          <div
            key={`${entry.title}-${entry.nextSub}`}
            className="space-y-2 rounded-xl border border-border/60 bg-card/50 p-4 transition-colors hover:border-primary/40"
          >
            <div className="flex items-start justify-between gap-2">
              <p className="text-sm font-bold leading-snug">{entry.title}</p>
              <span className="shrink-0 rounded-md bg-muted px-2 py-0.5 text-[10px] font-semibold uppercase text-muted-foreground">
                {entry.status}
              </span>
            </div>

            <div className="space-y-1 text-xs text-muted-foreground">
              <p className="flex items-center gap-1.5">
                <CalendarDays className="size-3.5" />
                Sub: {entry.nextSub ?? "—"}
              </p>
              <p className="pl-5">Dub: {entry.nextDub ?? "—"}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
