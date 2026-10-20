import { Check } from "lucide-react";

import { cn } from "@/lib/utils";
import { formatDuration } from "@/lib/format";
import type { LibraryEpisode } from "@/lib/server";

interface EpisodeListProps {
  episodes: LibraryEpisode[];
  watched?: Record<string, number>;
  onSelect: (episode: LibraryEpisode) => void;
  selected?: string;
}

/** The episodes of a series, as the library knows them. */
export function EpisodeList({ episodes, watched, onSelect, selected }: EpisodeListProps) {
  if (!episodes.length) {
    return (
      <p className="rounded-xl border border-dashed border-border/60 p-8 text-center text-sm text-muted-foreground">
        The library did not find any episode.
      </p>
    );
  }

  return (
    <ul className="divide-y divide-border/50 overflow-hidden rounded-xl border border-border/60">
      {episodes.map((episode) => {
        const progress = watched?.[episode.number];
        const isWatched = progress !== undefined && progress > 0.9;
        const isActive = selected === episode.number;

        return (
          <li key={`${episode.index}-${episode.number}`}>
            <button
              type="button"
              onClick={() => onSelect(episode)}
              className={cn(
                "flex w-full items-center gap-4 px-4 py-3 text-left transition-colors",
                "hover:bg-accent/60",
                isActive && "bg-primary/10",
              )}
            >
              <span
                className={cn(
                  "flex size-9 shrink-0 items-center justify-center rounded-md border text-xs font-semibold",
                  isActive
                    ? "border-primary/60 bg-primary/20 text-primary"
                    : "border-border bg-muted/40 text-muted-foreground",
                )}
              >
                {isWatched ? <Check className="size-4" /> : episode.number}
              </span>

              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">
                  Episode {episode.number}
                  {episode.isSpecial && (
                    <span className="ml-2 text-xs text-highlight">Special</span>
                  )}
                </span>
                {episode.title && (
                  <span className="block truncate text-xs text-muted-foreground">
                    {episode.title}
                  </span>
                )}
              </span>

              {progress !== undefined && progress > 0 && !isWatched && (
                <span className="hidden w-24 sm:block">
                  <span className="block h-1 overflow-hidden rounded-full bg-muted">
                    <span
                      className="block h-full rounded-full bg-primary"
                      style={{ width: `${Math.round(progress * 100)}%` }}
                    />
                  </span>
                </span>
              )}

              {episode.durationSeconds && (
                <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                  {formatDuration(episode.durationSeconds)}
                </span>
              )}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
