import { useQuery } from "@tanstack/react-query";

import { Hero } from "@/components/Hero";
import { Rail } from "@/components/PosterCard";
import { currentSeason, searchAnime } from "@/lib/anilist";

/** The rails of the home page. */
const GENRE_RAILS = ["Action", "Romance", "Fantasy", "Comedy", "Drama"];

/**
 * The home page.
 *
 * The banner shows what is worth watching now, the rails below show the rest — the
 * same way a television guide does.
 */
export function Home() {
  const { season, year } = currentSeason();

  const featured = useQuery({
    queryKey: ["featured"],
    queryFn: async () => {
      const page = await searchAnime({ sort: "POPULARITY_DESC", perPage: 24 });
      return page.media.filter((item) => item.bannerImage).slice(0, 5);
    },
    staleTime: 10 * 60 * 1000,
  });

  const seasonal = useQuery({
    queryKey: ["rail", "seasonal", season, year],
    queryFn: () =>
      searchAnime({ sort: "POPULARITY_DESC", season, seasonYear: year, perPage: 20 }),
    staleTime: 10 * 60 * 1000,
  });

  const trending = useQuery({
    queryKey: ["rail", "trending"],
    queryFn: () => searchAnime({ sort: "TRENDING_DESC", perPage: 20 }),
    staleTime: 10 * 60 * 1000,
  });

  const popular = useQuery({
    queryKey: ["rail", "popular"],
    queryFn: () => searchAnime({ sort: "POPULARITY_DESC", perPage: 20 }),
    staleTime: 10 * 60 * 1000,
  });

  const upcoming = useQuery({
    queryKey: ["rail", "upcoming"],
    queryFn: () =>
      searchAnime({ sort: "START_DATE_DESC", status: "NOT_YET_RELEASED", perPage: 20 }),
    staleTime: 10 * 60 * 1000,
  });

  return (
    <div className="pb-16">
      <Hero featured={featured.data ?? []} isLoading={featured.isLoading} />

      <div className="space-y-10 px-6 pt-10 lg:px-10">
        <Rail
          title="Popular This Season"
          anime={seasonal.data?.media}
          isLoading={seasonal.isLoading}
          viewMore={`/search?sort=POPULARITY_DESC&season=${season}&year=${year}`}
        />
        <Rail
          title="Trending Now"
          anime={trending.data?.media}
          isLoading={trending.isLoading}
          viewMore="/search?sort=TRENDING_DESC"
        />
        <Rail
          title="All Time Popular"
          anime={popular.data?.media}
          isLoading={popular.isLoading}
          viewMore="/search?sort=POPULARITY_DESC"
        />
        <Rail
          title="Upcoming"
          anime={upcoming.data?.media}
          isLoading={upcoming.isLoading}
          viewMore="/search?sort=START_DATE_DESC&status=NOT_YET_RELEASED"
        />

        {GENRE_RAILS.map((genre) => (
          <GenreRail key={genre} genre={genre} />
        ))}
      </div>
    </div>
  );
}

/** A rail of one genre. */
function GenreRail({ genre }: { genre: string }) {
  const query = useQuery({
    queryKey: ["rail", "genre", genre],
    queryFn: () => searchAnime({ sort: "TRENDING_DESC", genre, perPage: 20 }),
    staleTime: 10 * 60 * 1000,
  });

  return (
    <Rail
      title={genre}
      anime={query.data?.media}
      isLoading={query.isLoading}
      viewMore={`/search?genre=${encodeURIComponent(genre)}&sort=TRENDING_DESC`}
    />
  );
}
