import { useQuery } from "@tanstack/react-query";

import { PosterCard } from "@/components/PosterCard";
import { Skeleton } from "@/components/ui/skeleton";
import { animeByIds } from "@/lib/anilist";
import { useFavourites } from "@/lib/favourites";

/** The series the user marked. */
export function Favourites() {
  const { ids } = useFavourites();

  const query = useQuery({
    queryKey: ["favourites", ids.join(",")],
    queryFn: () => animeByIds(ids),
    enabled: ids.length > 0,
    staleTime: 10 * 60 * 1000,
  });

  return (
    <div className="space-y-6 p-6 lg:p-10">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold tracking-tight">Favourites</h1>
        <p className="text-sm text-muted-foreground">
          The series you marked with the heart.
        </p>
      </header>

      {ids.length === 0 && (
        <p className="rounded-xl border border-dashed border-border/60 p-12 text-center text-sm text-muted-foreground">
          Nothing marked yet. The heart of a series marks it.
        </p>
      )}

      {query.isLoading && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8">
          {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={index} className="aspect-[2/3] w-full rounded-md" />
          ))}
        </div>
      )}

      {query.data && (
        <div className="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8">
          {query.data.map((anime, index) => (
            <div key={anime.id} className="animate-load-in">
              <PosterCard anime={anime} index={index} className="w-full" />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
