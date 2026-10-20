import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { Play, Search, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useDebounce } from "@/hooks/useDebounce";
import { formatDuration, timeAgo } from "@/lib/format";
import { useHistory, useLibrary } from "@/lib/library";

/**
 * The library of the user: what was watched and what the upstream site offers.
 */
export function Library() {
  const { client } = useLibrary();
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const query = useDebounce(search.trim(), 400);

  const history = useHistory();

  const results = useQuery({
    queryKey: ["library-search", query],
    queryFn: () => client!.search(query),
    enabled: Boolean(client) && query.length > 1,
  });

  const forget = async (slug: string, episode: string) => {
    await client?.forget(slug, episode);
    await history.refetch();
  };

  return (
    <div className="space-y-10 p-6 lg:p-10">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold tracking-tight">Library</h1>
        <p className="text-sm text-muted-foreground">
          The watch history and the catalog of the upstream site.
        </p>
      </header>

      <section className="space-y-4">
        <h2 className="text-lg font-bold tracking-tight">Continue watching</h2>

        {history.isLoading && (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-24 w-full rounded-xl" />
            ))}
          </div>
        )}

        {history.data?.length === 0 && (
          <p className="rounded-xl border border-dashed border-border/60 p-8 text-center text-sm text-muted-foreground">
            Nothing watched yet.
          </p>
        )}

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {history.data?.map((entry) => (
            <div
              key={`${entry.slug}-${entry.episode}`}
              className="group flex items-center gap-3 rounded-xl border border-border/60 bg-card/50 p-3 transition-colors hover:border-primary/40"
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold">{entry.title}</p>
                <p className="text-xs text-muted-foreground">
                  Episode {entry.episode} · {timeAgo(entry.updatedAt)}
                  {entry.durationMs > 0 &&
                    ` · ${formatDuration(entry.positionMs / 1000)} of ${formatDuration(
                      entry.durationMs / 1000,
                    )}`}
                </p>
                {entry.durationMs > 0 && (
                  <span className="mt-1.5 block h-1 overflow-hidden rounded-full bg-muted">
                    <span
                      className="block h-full rounded-full bg-primary"
                      style={{ width: `${Math.round(entry.progress * 100)}%` }}
                    />
                  </span>
                )}
              </div>

              <Button
                variant="ghost"
                size="icon"
                title="Play again"
                onClick={() =>
                  navigate(
                    `/watch/0/${encodeURIComponent(entry.episode)}?slug=${encodeURIComponent(entry.slug)}`,
                  )
                }
              >
                <Play className="size-4" />
              </Button>

              <Button
                variant="ghost"
                size="icon"
                title="Forget"
                onClick={() => void forget(entry.slug, entry.episode)}
              >
                <Trash2 className="size-4" />
              </Button>
            </div>
          ))}
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-lg font-bold tracking-tight">Search the upstream site</h2>

        <div className="relative max-w-xl">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search the library…"
            className="h-10 pl-9"
          />
        </div>

        {results.isLoading && <Skeleton className="h-40 w-full rounded-xl" />}

        <ul className="divide-y divide-border/50 overflow-hidden rounded-xl border border-border/60">
          {results.data?.map((result) => (
            <li key={result.slug}>
              <Link
                to={`/series/${encodeURIComponent(result.slug)}`}
                className="flex items-center gap-4 px-4 py-3 transition-colors hover:bg-accent/50"
              >
                {result.posterUrl ? (
                  <img
                    src={result.posterUrl}
                    alt={result.title}
                    loading="lazy"
                    className="h-14 w-10 rounded object-cover"
                  />
                ) : (
                  <div className="h-14 w-10 rounded bg-muted" />
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold">{result.title}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {[result.year, result.status].filter(Boolean).join(" · ")}
                  </p>
                </div>
                <div className="hidden flex-wrap justify-end gap-1 sm:flex">
                  {result.genres.slice(0, 3).map((genre) => (
                    <span
                      key={genre}
                      className="rounded-full border border-border/70 px-2 py-0.5 text-[11px] text-muted-foreground"
                    >
                      {genre}
                    </span>
                  ))}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
