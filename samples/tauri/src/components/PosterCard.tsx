import { Link } from "react-router-dom";
import { Play } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { displayTitle, formatScore } from "@/lib/format";
import type { AnimeSummary } from "@/lib/anilist";

interface PosterCardProps {
  anime: AnimeSummary;
  className?: string;
}

/** One poster of the catalog. */
export function PosterCard({ anime, className }: PosterCardProps) {
  const title = displayTitle(anime.title);

  return (
    <Link
      to={`/anime/${anime.id}`}
      className={cn(
        "group relative block overflow-hidden rounded-xl border border-border/60 bg-card",
        "transition-all duration-300 hover:-translate-y-1 hover:border-primary/50",
        "hover:shadow-[0_12px_40px_-12px_hsl(258_90%_68%/0.45)]",
        className,
      )}
    >
      <div className="relative aspect-[2/3] overflow-hidden">
        <img
          src={anime.coverImage.large}
          alt={title}
          loading="lazy"
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-card via-card/10 to-transparent opacity-80" />

        {anime.averageScore !== null && (
          <Badge
            className="absolute right-2 top-2 border-none bg-highlight/90 font-semibold text-highlight-foreground shadow-lg"
            title="The score of the catalog"
          >
            {formatScore(anime.averageScore)}
          </Badge>
        )}

        <div className="absolute inset-x-0 bottom-0 flex items-center gap-2 p-3 opacity-0 transition-opacity duration-300 group-hover:opacity-100">
          <span className="flex size-8 items-center justify-center rounded-full bg-primary text-primary-foreground">
            <Play className="size-4 fill-current" />
          </span>
        </div>
      </div>

      <div className="space-y-1 p-3">
        <p className="line-clamp-2 text-sm font-medium leading-snug">{title}</p>
        <p className="text-xs text-muted-foreground">
          {[anime.format, anime.seasonYear].filter(Boolean).join(" · ") || "—"}
        </p>
      </div>
    </Link>
  );
}
