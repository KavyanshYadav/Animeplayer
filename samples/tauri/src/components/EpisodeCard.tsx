import { Star } from "lucide-react";

import { cn } from "@/lib/utils";
import { formatDuration, plainText } from "@/lib/format";
import type { LibraryEpisode } from "@/lib/server";
import type { AnimeEpisode } from "@/lib/anilist";

interface EpisodeCardProps {
  episode: LibraryEpisode & { meta?: AnimeEpisode };
  progress?: number;
  onPlay: () => void;
}

/**
 * One episode of a series.
 *
 * The number and the title come from the library, the thumbnail and the description
 * from the catalog.
 */
export function EpisodeCard({ episode, progress, onPlay }: EpisodeCardProps) {
  const meta = episode.meta;
  const title = meta?.title ?? `Episode ${episode.number}`;
  const description = plainText(meta?.description ?? null);
  const watched = progress !== undefined && progress > 0.9;

  return (
    <button
      type="button"
      onClick={onPlay}
      className={cn(
        "group flex gap-3 rounded-lg border border-border/60 bg-card/50 p-2 text-left transition-colors",
        "hover:border-primary/60 hover:bg-card",
      )}
    >
      <div className="relative w-28 shrink-0 overflow-hidden rounded bg-muted sm:w-36">
        {meta?.thumbnail ? (
          <img
            src={meta.thumbnail}
            alt={title}
            loading="lazy"
            className="aspect-video h-full w-full object-cover"
          />
        ) : (
          <div className="flex aspect-video items-center justify-center bg-muted/60">
            <span className="text-2xl font-black text-muted-foreground/40">
              {episode.number}
            </span>
          </div>
        )}

        <div className="absolute inset-x-0 bottom-0 flex items-center justify-between px-1 pb-1">
          {episode.durationSeconds ? (
            <span className="rounded bg-black/75 px-1 py-0.5 text-[10px] font-medium text-white">
              {formatDuration(episode.durationSeconds)}
            </span>
          ) : (
            <span />
          )}
          {meta?.averageScore ? (
            <span className="flex items-center gap-0.5 rounded bg-black/75 px-1 py-0.5 text-[10px] font-medium text-white">
              <Star className="size-2.5 fill-highlight text-highlight" />
              {meta.averageScore.toFixed(2)}
            </span>
          ) : null}
        </div>

        {progress !== undefined && progress > 0 && (
          <span className="absolute inset-x-0 bottom-0 h-0.5 bg-black/40">
            <span
              className="block h-full bg-primary"
              style={{ width: `${Math.round(progress * 100)}%` }}
            />
          </span>
        )}
      </div>

      <div className="min-w-0 flex-1 py-0.5">
        <p className="truncate text-sm font-bold">
          {episode.number}. {title}
        </p>
        {description && (
          <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-muted-foreground">
            {description}
          </p>
        )}
        <p className="mt-1.5 text-[11px] text-muted-foreground/80">
          {watched ? "Watched" : (meta?.airDate ? relativeTime(meta.airDate) : "")}
          {episode.isSpecial && " · Special"}
        </p>
      </div>
    </button>
  );
}

/** `3 weeks ago` */
function relativeTime(timestamp: number): string {
  const seconds = Math.floor((Date.now() - timestamp * 1000) / 1000);
  if (seconds < 0) return "soon";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} minutes ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hours ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} days ago`;
  const weeks = Math.floor(days / 7);
  if (weeks < 5) return `${weeks} weeks ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} months ago`;
  return `${Math.floor(days / 365)} years ago`;
}
