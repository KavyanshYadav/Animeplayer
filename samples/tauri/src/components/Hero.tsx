import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Bookmark, Heart, Play } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { displayTitle, formatScore, plainText } from "@/lib/format";
import { useFavourites } from "@/lib/favourites";
import type { AnimeSummary } from "@/lib/anilist";

interface HeroProps {
  featured: AnimeSummary[];
  isLoading?: boolean;
}

/**
 * The banner at the top of the home page.
 *
 * The anime with a key visual take turns; the dots underneath switch between them.
 */
export function Hero({ featured, isLoading }: HeroProps) {
  const [index, setIndex] = useState(0);
  const { isFavourite, toggle } = useFavourites();

  useEffect(() => {
    if (featured.length < 2) return;
    const timer = window.setInterval(() => {
      setIndex((value) => (value + 1) % featured.length);
    }, 9000);
    return () => window.clearInterval(timer);
  }, [featured.length]);

  if (isLoading) {
    return <div className="h-[68vh] w-full animate-pulse bg-muted" />;
  }

  const anime = featured[index];
  if (!anime) {
    return <div className="h-[68vh] w-full bg-muted" />;
  }

  const title = displayTitle(anime.title);
  const description = plainText(anime.description);

  return (
    <section className="relative h-[68vh] min-h-[32rem] w-full overflow-hidden">
      {featured.map((item, position) => (
        <div
          key={item.id}
          className={cn(
            "absolute inset-0 bg-cover bg-center transition-opacity duration-700",
            position === index ? "opacity-100" : "opacity-0",
          )}
          style={{
            backgroundImage: `url(${item.bannerImage ?? item.coverImage.large})`,
          }}
        />
      ))}

      <div className="absolute inset-0 bg-gradient-to-t from-background via-background/60 to-background/20" />
      <div className="absolute inset-0 bg-gradient-to-r from-background/90 via-background/30 to-transparent" />

      <div className="absolute inset-x-0 bottom-0 flex flex-col gap-8 p-6 lg:flex-row lg:items-end lg:justify-between lg:p-10">
        <div className="max-w-2xl space-y-4">
          <h1 className="text-balance text-4xl font-black leading-tight tracking-tight text-shadow-lg lg:text-6xl">
            {title}
          </h1>

          <div className="flex flex-wrap items-center gap-2">
            {anime.episodes && (
              <Badge tone="primary">{anime.episodes} Episodes</Badge>
            )}
            {anime.format && <Badge tone="primary">{anime.format}</Badge>}
            {anime.status && <Badge tone="primary">{anime.status}</Badge>}
            {anime.seasonYear && <Badge tone="primary">{anime.seasonYear}</Badge>}
            {anime.averageScore !== null && (
              <Badge tone="success">{formatScore(anime.averageScore)}%</Badge>
            )}
          </div>

          <div className="flex items-center gap-2">
            <Button asChild className="gap-2 font-bold">
              <Link to={`/anime/${anime.id}`} viewTransition>
                <Play className="size-4 fill-current" />
                Watch Now
              </Link>
            </Button>

            <Button
              variant="secondary"
              size="icon"
              className="size-9 rounded-lg"
              title={isFavourite(anime.id) ? "Remove from the favourites" : "Add to the favourites"}
              onClick={() => toggle(anime.id)}
            >
              <Heart
                className={cn(
                  "size-4",
                  isFavourite(anime.id) && "fill-pink-500 text-pink-500",
                )}
              />
            </Button>

            <Button variant="secondary" size="icon" className="size-9 rounded-lg" title="Bookmark">
              <Bookmark className="size-4" />
            </Button>
          </div>
        </div>

        <div className="hidden max-w-md space-y-3 lg:block">
          {description && (
            <p className="line-clamp-4 text-sm leading-relaxed text-foreground/85">{description}</p>
          )}
          <div className="flex flex-wrap gap-1.5">
            {anime.genres.map((genre) => (
              <span
                key={genre}
                className="rounded-md bg-primary/15 px-2.5 py-1 text-xs font-medium text-primary"
              >
                {genre}
              </span>
            ))}
          </div>
        </div>
      </div>

      {featured.length > 1 && (
        <div className="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-1.5">
          {featured.map((item, position) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setIndex(position)}
              className={cn(
                "h-1 rounded-full transition-all",
                position === index ? "w-6 bg-primary" : "w-2 bg-foreground/30 hover:bg-foreground/60",
              )}
              aria-label={displayTitle(item.title)}
            />
          ))}
        </div>
      )}
    </section>
  );
}

/** A small badge — the metadata of the hero and of the detail page. */
export function Badge({
  children,
  tone = "muted",
}: {
  children: React.ReactNode;
  tone?: "primary" | "muted" | "success" | "outline";
}) {
  return (
    <span
      className={cn(
        "rounded-md px-2.5 py-1 text-xs font-semibold",
        tone === "primary" && "bg-primary text-primary-foreground",
        tone === "muted" && "bg-muted text-muted-foreground",
        tone === "success" && "bg-success text-success-foreground",
        tone === "outline" && "border border-border text-muted-foreground",
      )}
    >
      {children}
    </span>
  );
}
