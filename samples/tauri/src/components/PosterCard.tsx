import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarDays, ChevronLeft, ChevronRight, Play, Tv } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { displayTitle, formatScore, plainText } from "@/lib/format";
import type { AnimeSummary } from "@/lib/anilist";

interface PosterCardProps {
  anime: AnimeSummary;
  /** The delay of the entrance animation, so a rail does not appear at once. */
  index?: number;
  className?: string;
}

/** One poster of a rail. */
export function PosterCard({ anime, index = 0, className }: PosterCardProps) {
  const title = displayTitle(anime.title);
  const [loaded, setLoaded] = useState(false);

  return (
    <Link
      to={`/anime/${anime.id}`}
      viewTransition
      className={cn("group block select-none", className)}
      style={{ animationDelay: `${Math.min(index, 12) * 25}ms` }}
    >
      <div className="relative aspect-[2/3] overflow-hidden rounded-md bg-muted">
        {anime.coverImage.color && (
          <div
            className="absolute inset-0 scale-110 blur-xl transition-opacity duration-500"
            style={{ backgroundColor: anime.coverImage.color, opacity: loaded ? 0 : 0.55 }}
          />
        )}
        <img
          src={anime.coverImage.large}
          alt={title}
          loading="lazy"
          onLoad={() => setLoaded(true)}
          className={cn(
            "relative h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.04]",
            loaded ? "opacity-100" : "opacity-0",
          )}
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100" />

        {/* the preview — the poster gives way to the details of the series */}
        <div
          className={cn(
            "absolute inset-0 flex flex-col justify-end gap-2 bg-gradient-to-t from-black via-black/85 to-black/60 p-3",
            "opacity-0 transition-opacity duration-200",
            "group-hover:opacity-100 group-focus-within:opacity-100",
          )}
        >
          <p className="line-clamp-2 text-[0.8rem] font-black leading-snug text-white">
            {title}
          </p>

          {anime.description && (
            <p className="line-clamp-3 text-[11px] leading-snug text-white/70">
              {plainText(anime.description)}
            </p>
          )}

          <div className="flex items-center justify-between text-[11px] text-white/60">
            <span>
              {anime.format ?? "TV"} · {anime.seasonYear ?? "TBA"}
            </span>
            {anime.averageScore !== null && (
              <span className="font-semibold text-emerald-400">
                {formatScore(anime.averageScore)}%
              </span>
            )}
          </div>

          <span className="mt-1 flex items-center justify-center gap-1.5 rounded-md bg-primary py-1.5 text-[11px] font-bold text-primary-foreground">
            <Play className="size-3 fill-current" />
            Watch Now
          </span>
        </div>
      </div>

      <p className="mt-2 line-clamp-2 text-[0.8rem] font-black leading-snug">
        {title}
      </p>

      <div className="mt-1 flex items-center justify-between text-xs text-muted-foreground">
        <span className="flex items-center gap-1">
          <CalendarDays className="size-3" />
          {anime.seasonYear ?? "TBA"}
        </span>
        <span className="flex items-center gap-1">
          {anime.format ?? "TV"}
          <Tv className="size-3" />
        </span>
      </div>
    </Link>
  );
}

interface RailProps {
  title: string;
  anime: AnimeSummary[] | undefined;
  isLoading?: boolean;
  /** The route of the "View More" link. */
  viewMore?: string;
  children?: React.ReactNode;
}

/** A horizontal row of posters. */
export function Rail({ title, anime, isLoading, viewMore, children }: RailProps) {
  const scroller = useRef<HTMLDivElement>(null);
  const [atStart, setAtStart] = useState(true);
  const [atEnd, setAtEnd] = useState(false);

  const update = () => {
    const element = scroller.current;
    if (!element) return;
    setAtStart(element.scrollLeft < 8);
    setAtEnd(element.scrollLeft + element.clientWidth >= element.scrollWidth - 8);
  };

  const scrollBy = (direction: 1 | -1) => {
    const element = scroller.current;
    if (!element) return;
    element.scrollBy({ left: direction * element.clientWidth * 0.8, behavior: "smooth" });
  };

  return (
    <section className="space-y-3">
      <header className="flex items-center justify-between px-1">
        <h2 className="text-lg font-bold tracking-tight">{title}</h2>
        <div className="flex items-center gap-2">
          {viewMore && (
            <Link
              to={viewMore}
              className="text-xs text-muted-foreground transition-colors hover:text-foreground"
            >
              View More
            </Link>
          )}
          <Button
            variant="ghost"
            size="icon"
            className="size-7 text-muted-foreground"
            disabled={atStart}
            onClick={() => scrollBy(-1)}
            title="Back"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="size-7 text-muted-foreground"
            disabled={atEnd}
            onClick={() => scrollBy(1)}
            title="Forward"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </header>

      <div
        ref={scroller}
        onScroll={update}
        className="scrollbar-none flex gap-3 overflow-x-auto scroll-smooth pb-1"
      >
        {isLoading &&
          Array.from({ length: 8 }).map((_, index) => (
            <div key={index} className="w-[10.5rem] shrink-0 space-y-2">
              <Skeleton className="aspect-[2/3] w-full rounded-md" />
              <Skeleton className="h-3 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          ))}

        {anime?.map((item, index) => (
          <div key={item.id} className="animate-load-in w-[10.5rem] shrink-0">
            <PosterCard anime={item} index={index} />
          </div>
        ))}

        {children}
      </div>
    </section>
  );
}
