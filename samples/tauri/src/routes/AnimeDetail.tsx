import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Bookmark,
  Heart,
  Link2,
  Play,
  Share2,
  TriangleAlert,
} from "lucide-react";

import { Badge } from "@/components/Hero";
import { EpisodeCard } from "@/components/EpisodeCard";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  animeById,
  animeEpisodes,
  animeRecommendations,
  animeRelations,
} from "@/lib/anilist";
import { cn } from "@/lib/utils";
import { displayTitle, formatScore, plainText, secondaryTitle } from "@/lib/format";
import { useFavourites } from "@/lib/favourites";
import { useLibrary } from "@/lib/library";
import { titleScore } from "@/lib/match";
import type { LibraryEpisode, LibrarySearchResult } from "@/lib/server";

/**
 * The detail page of a series.
 *
 * The upper half is the catalog of AniList, the lower half is the library: the
 * episodes it found and whether it can play them.
 */
export function AnimeDetail() {
  const { animeId } = useParams();
  const navigate = useNavigate();
  const { client } = useLibrary();
  const { isFavourite, toggle } = useFavourites();
  const [tab, setTab] = useState("episodes");

  const id = Number(animeId);

  const anime = useQuery({
    queryKey: ["anime", id],
    queryFn: () => animeById(id),
    enabled: Number.isFinite(id),
    staleTime: 10 * 60 * 1000,
  });

  const titles = useMemo(() => {
    if (!anime.data) return [];
    return [anime.data.title.romaji, anime.data.title.english, anime.data.title.native].filter(
      (value): value is string => Boolean(value),
    );
  }, [anime.data]);

  /**
   * The bridge: the catalog knows the anime by its title, the library by its slug.
   */
  const match = useQuery({
    queryKey: ["library-match", id, titles.join("|")],
    enabled: Boolean(client) && titles.length > 0,
    staleTime: 10 * 60 * 1000,
    queryFn: async () => {
      const results = await client!.search(titles[0]);
      let best: LibrarySearchResult | null = null;
      let bestScore = 0;
      for (const result of results) {
        const score = Math.max(
          ...titles.map((title) =>
            Math.max(titleScore(title, result.title), titleScore(title, result.japaneseTitle ?? "")),
          ),
        );
        if (score > bestScore) {
          best = result;
          bestScore = score;
        }
      }
      return best;
    },
  });

  const slug = match.data?.slug;

  const libraryAnime = useQuery({
    queryKey: ["library-anime", slug],
    enabled: Boolean(client) && Boolean(slug),
    staleTime: 10 * 60 * 1000,
    queryFn: () => client!.anime(slug!),
  });

  const libraryEpisodes = useQuery({
    queryKey: ["library-episodes", slug],
    enabled: Boolean(client) && Boolean(slug),
    staleTime: 5 * 60 * 1000,
    queryFn: () => client!.episodes(slug!),
  });

  const catalogEpisodes = useQuery({
    queryKey: ["catalog-episodes", id],
    enabled: Number.isFinite(id),
    staleTime: 10 * 60 * 1000,
    queryFn: () => animeEpisodes(id),
  });

  const relations = useQuery({
    queryKey: ["relations", id],
    enabled: Number.isFinite(id) && tab === "relations",
    staleTime: 10 * 60 * 1000,
    queryFn: () => animeRelations(id),
  });

  const recommendations = useQuery({
    queryKey: ["recommendations", id],
    enabled: Number.isFinite(id) && tab === "recommendations",
    staleTime: 10 * 60 * 1000,
    queryFn: () => animeRecommendations(id),
  });

  const history = useQuery({
    queryKey: ["history"],
    enabled: Boolean(client),
    staleTime: 0,
    refetchOnMount: "always",
    queryFn: () => client!.history(),
  });

  const watched = useMemo(() => {
    const map: Record<string, number> = {};
    for (const entry of history.data ?? []) {
      if (entry.slug === slug) map[entry.episode] = entry.progress;
    }
    return map;
  }, [history.data, slug]);

  /** The episodes of the library, with the metadata of the catalog. */
  const episodes = useMemo(() => {
    const library: LibraryEpisode[] = libraryEpisodes.data?.episodes ?? [];
    const catalog = new Map(
      (catalogEpisodes.data ?? []).map((episode) => [String(episode.number), episode]),
    );
    return library.map((episode) => ({
      ...episode,
      meta: catalog.get(String(Number(episode.number))),
    }));
  }, [libraryEpisodes.data, catalogEpisodes.data]);

  const play = (episode: LibraryEpisode) => {
    if (!slug) return;
    navigate(`/watch/${id}/${encodeURIComponent(episode.number)}?slug=${encodeURIComponent(slug)}`);
  };

  if (anime.isLoading) {
    return (
      <div className="space-y-6 p-6 lg:p-10">
        <Skeleton className="h-80 w-full rounded-xl" />
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="h-24 w-2/3" />
      </div>
    );
  }

  if (!anime.data) {
    return (
      <div className="p-10 text-center text-sm text-muted-foreground">
        The catalog does not know this anime.
      </div>
    );
  }

  const item = anime.data;
  const title = displayTitle(item.title);
  const subtitle = secondaryTitle(item.title);
  const description = plainText(item.description);

  return (
    <div className="pb-16">
      {/* the banner */}
      <div className="relative h-[22rem] w-full overflow-hidden lg:h-[26rem]">
        <div
          className="absolute inset-0 bg-cover bg-center"
          style={{
            backgroundImage: `url(${item.bannerImage ?? item.coverImage.large})`,
          }}
        />
        <div className="absolute inset-0 bg-gradient-to-t from-background via-background/50 to-background/10" />
      </div>

      {/* The row has to be positioned, otherwise the banner — which is positioned —
          paints over it. */}
      <div className="relative px-6 lg:px-10">
        <div className="-mt-32 flex flex-col gap-6 sm:flex-row lg:-mt-40">
          <img
            src={item.coverImage.large}
            alt={title}
            className="w-40 shrink-0 rounded-lg border border-border/60 shadow-2xl lg:w-48"
          />

          <div className="min-w-0 flex-1 space-y-4 pt-4">
            {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
            <h1 className="text-balance text-3xl font-black leading-tight tracking-tight lg:text-4xl">
              {title}
            </h1>

            <div className="flex flex-wrap items-center gap-2">
              {item.episodes && <Badge tone="primary">{item.episodes} Episodes</Badge>}
              {item.format && <Badge tone="primary">{item.format}</Badge>}
              {item.status && <Badge tone="primary">{item.status}</Badge>}
              {item.seasonYear && <Badge tone="primary">{item.seasonYear}</Badge>}
              {item.averageScore !== null && (
                <Badge tone="success">{formatScore(item.averageScore)}%</Badge>
              )}
            </div>

            {description && (
              <p className="max-w-3xl line-clamp-3 text-sm leading-relaxed text-foreground/80">
                {description}
              </p>
            )}

            <div className="flex flex-wrap items-center gap-2 pt-1">
              <Button
                className="gap-2 font-bold"
                disabled={!slug || episodes.length === 0}
                onClick={() => episodes[0] && play(episodes[0])}
              >
                <Play className="size-4 fill-current" />
                Watch Now
              </Button>

              <Button
                variant="secondary"
                size="icon"
                className="size-9 rounded-lg"
                title={isFavourite(item.id) ? "Remove from the favourites" : "Add to the favourites"}
                onClick={() => toggle(item.id)}
              >
                <Heart
                  className={cn(
                    "size-4",
                    isFavourite(item.id) && "fill-pink-500 text-pink-500",
                  )}
                />
              </Button>

              <Button variant="secondary" size="icon" className="size-9 rounded-lg" title="Bookmark">
                <Bookmark className="size-4" />
              </Button>

              <Button
                variant="secondary"
                size="icon"
                className="size-9 rounded-lg"
                title="Share"
                onClick={() =>
                  void navigator.clipboard?.writeText(window.location.href)
                }
              >
                <Share2 className="size-4" />
              </Button>

              <Button
                variant="secondary"
                size="icon"
                className="size-9 rounded-lg"
                title="Open in AniList"
                onClick={() => window.open(`https://anilist.co/anime/${item.id}`, "_blank")}
              >
                <Link2 className="size-4" />
              </Button>
            </div>
          </div>
        </div>

        {/* the genres */}
        <div className="mt-6 flex flex-wrap gap-1.5">
          {item.genres.map((genre) => (
            <span
              key={genre}
              className="rounded-md border border-border/70 px-2.5 py-1 text-xs text-muted-foreground"
            >
              {genre}
            </span>
          ))}
        </div>

        {/* the library */}
        {slug && libraryAnime.data && (
          <div className="bg-striped mt-6 flex flex-wrap items-center gap-x-6 gap-y-1 rounded-lg border border-border/60 bg-card/40 px-4 py-3 text-xs text-muted-foreground">
            <span className="font-semibold text-foreground">The library</span>
            <span>{libraryAnime.data.title}</span>
            <span className="font-mono">{libraryAnime.data.slug}</span>
            <span>Id {libraryAnime.data.numericId}</span>
            {libraryAnime.data.status !== "Unknown" && <span>{libraryAnime.data.status}</span>}
            {libraryAnime.data.year && <span>{libraryAnime.data.year}</span>}
          </div>
        )}

        {/* the tabs */}
        <Tabs value={tab} onValueChange={setTab} className="mt-8">
          <TabsList className="bg-transparent p-0">
            <TabsTrigger value="episodes" className="data-[state=active]:bg-primary">
              Episodes
            </TabsTrigger>
            <TabsTrigger value="relations" className="data-[state=active]:bg-primary">
              Relations
            </TabsTrigger>
            <TabsTrigger value="recommendations" className="data-[state=active]:bg-primary">
              Recommendations
            </TabsTrigger>
          </TabsList>

          <TabsContent value="episodes" className="mt-5 space-y-4">
            {match.isLoading && <Skeleton className="h-64 w-full rounded-xl" />}

            {!match.isLoading && !slug && (
              <div className="flex items-start gap-3 rounded-xl border border-dashed border-border/60 p-6 text-sm text-muted-foreground">
                <TriangleAlert className="mt-0.5 size-4 shrink-0" />
                <p>
                  The library does not know this title. Try the search of the library — the
                  upstream site carries a different catalog.
                </p>
              </div>
            )}

            {slug && libraryEpisodes.isLoading && (
              <Skeleton className="h-64 w-full rounded-xl" />
            )}

            {slug && episodes.length > 0 && (
              <div className="grid gap-4 xl:grid-cols-2">
                {episodes.map((episode) => (
                  <EpisodeCard
                    key={`${episode.index}-${episode.number}`}
                    episode={episode}
                    progress={watched[episode.number]}
                    onPlay={() => play(episode)}
                  />
                ))}
              </div>
            )}
          </TabsContent>

          <TabsContent value="relations" className="mt-5">
            <RelationList relations={relations.data ?? []} isLoading={relations.isLoading} />
          </TabsContent>

          <TabsContent value="recommendations" className="mt-5">
            <RelationList relations={recommendations.data ?? []} isLoading={recommendations.isLoading} />
          </TabsContent>
        </Tabs>
      </div>

      <Link
        to="/"
        className="fixed bottom-6 right-6 inline-flex items-center gap-1.5 rounded-full bg-background/80 px-3 py-1.5 text-xs text-muted-foreground backdrop-blur transition-colors hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" />
        Back to the home
      </Link>
    </div>
  );
}

/** The related series of a tab. */
function RelationList({
  relations,
  isLoading,
}: {
  relations: {
    id: number;
    title: { romaji: string | null; english: string | null; native: string | null };
    coverImage: { large: string; color: string | null };
    relationType?: string;
    format?: string | null;
    seasonYear?: number | null;
  }[];
  isLoading: boolean;
}) {
  if (isLoading) return <Skeleton className="h-40 w-full rounded-xl" />;
  if (!relations.length) {
    return (
      <p className="rounded-xl border border-dashed border-border/60 p-8 text-center text-sm text-muted-foreground">
        Nothing here yet.
      </p>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8">
      {relations.map((relation) => (
        <Link
          key={relation.id}
          to={`/anime/${relation.id}`} viewTransition
          className="group block"
        >
          <div className="aspect-[2/3] overflow-hidden rounded-md bg-muted">
            <img
              src={relation.coverImage.large}
              alt={displayTitle(relation.title)}
              loading="lazy"
              className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
            />
          </div>
          <p className="mt-2 line-clamp-2 text-[0.8rem] font-black leading-snug">
            {displayTitle(relation.title)}
          </p>
          {relation.relationType && (
            <p className="text-xs text-muted-foreground">{relation.relationType}</p>
          )}
        </Link>
      ))}
    </div>
  );
}
