import { PosterCard } from "@/components/PosterCard";
import { Skeleton } from "@/components/ui/skeleton";
import type { AnimeSummary } from "@/lib/anilist";

interface AnimeGridProps {
  anime: AnimeSummary[] | undefined;
  isLoading?: boolean;
  emptyMessage?: string;
}

/** The grid of posters of the catalog. */
export function AnimeGrid({ anime, isLoading, emptyMessage }: AnimeGridProps) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
        {Array.from({ length: 12 }).map((_, index) => (
          <div key={index} className="space-y-2">
            <Skeleton className="aspect-[2/3] w-full rounded-xl" />
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-3 w-1/3" />
          </div>
        ))}
      </div>
    );
  }

  if (!anime?.length) {
    return (
      <p className="rounded-xl border border-dashed border-border/60 p-10 text-center text-sm text-muted-foreground">
        {emptyMessage ?? "Nothing here yet."}
      </p>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
      {anime.map((item) => (
        <PosterCard key={item.id} anime={item} />
      ))}
    </div>
  );
}
