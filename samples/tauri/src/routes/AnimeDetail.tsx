import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Calendar, Film, Link2Off, Play, Tv } from "lucide-react";

import { EpisodeList } from "@/components/EpisodeList";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { animeById } from "@/lib/anilist";
import { displayTitle, formatScore, plainText, secondaryTitle } from "@/lib/format";
import { useHistory, useLibrary } from "@/lib/library";
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

  const episodes = useQuery({
    queryKey: ["library-episodes", slug],
    enabled: Boolean(client) && Boolean(slug),
    staleTime: 5 * 60 * 1000,
    queryFn: () => client!.episodes(slug!),
  });

  const history = useHistory();

  const watched = useMemo(() => {
    const map: Record<string, number> = {};
    for (const entry of history.data ?? []) {
      if (entry.slug === slug) map[entry.episode] = entry.progress;
    }
    return map;
  }, [history.data, slug]);

  const play = (episode: LibraryEpisode) => {
    if (!slug) return;
    navigate(`/watch/${id}/${encodeURIComponent(episode.number)}?slug=${encodeURIComponent(slug)}`);
  };

  if (anime.isLoading) {
    return (
      <div className="space-y-6 p-6 lg:p-10">
        <Skeleton className="h-56 w-full rounded-xl" />
        <Skeleton className="h-8 w-1/3" />
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
    <div className="pb-10">
      {/* the banner */}
      <div className="relative">
        <div
          className="h-56 w-full bg-cover bg-center sm:h-72 lg:h-80"
          style={
            item.bannerImage
              ? { backgroundImage: `url(${item.bannerImage})` }
              : { backgroundImage: `url(${item.coverImage.large})` }
          }
        />
        <div className="absolute inset-0 bg-gradient-to-t from-background via-background/70 to-background/20" />

        <div className="absolute inset-x-0 bottom-0 p-6 lg:p-10">
          <Link
            to="/"
            className="mb-4 inline-flex items-center gap-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="size-3.5" />
            Back to the catalog
          </Link>

          <div className="flex flex-col gap-6 sm:flex-row sm:items-end">
            <img
              src={item.coverImage.large}
              alt={title}
              className="hidden w-36 rounded-lg border border-border/60 shadow-2xl sm:block"
            />

            <div className="min-w-0 flex-1 space-y-3">
              <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{title}</h1>
              {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}

              <div className="flex flex-wrap items-center gap-2 text-xs">
                {item.averageScore !== null && (
                  <Badge className="border-none bg-highlight/90 font-semibold text-highlight-foreground">
                    {formatScore(item.averageScore)}
                  </Badge>
                )}
                {item.format && (
                  <Badge variant="secondary" className="gap-1">
                    <Film className="size-3" />
                    {item.format}
                  </Badge>
                )}
                {item.seasonYear && (
                  <Badge variant="secondary" className="gap-1">
                    <Calendar className="size-3" />
                    {item.seasonYear}
                  </Badge>
                )}
                {item.episodes && (
                  <Badge variant="secondary" className="gap-1">
                    <Tv className="size-3" />
                    {item.episodes} episodes
                  </Badge>
                )}
                {item.status && <Badge variant="outline">{item.status}</Badge>}
              </div>

              <div className="flex flex-wrap gap-1.5">
                {item.genres.map((genre) => (
                  <span
                    key={genre}
                    className="rounded-full border border-border/70 px-2 py-0.5 text-[11px] text-muted-foreground"
                  >
                    {genre}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="grid gap-8 p-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:p-10">
        <div className="space-y-8">
          {description && (
            <section className="space-y-2">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                The story
              </h2>
              <p className="max-w-3xl whitespace-pre-line text-sm leading-relaxed text-foreground/85">
                {description}
              </p>
            </section>
          )}

          <section className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                Episodes
              </h2>
              {episodes.data?.totalReported ? (
                <span className="text-xs text-muted-foreground">
                  the site reports {episodes.data.totalReported}
                </span>
              ) : null}
            </div>

            {match.isLoading && <Skeleton className="h-64 w-full rounded-xl" />}

            {!match.isLoading && !slug && (
              <div className="flex items-start gap-3 rounded-xl border border-dashed border-border/60 p-6 text-sm text-muted-foreground">
                <Link2Off className="mt-0.5 size-4 shrink-0" />
                <p>
                  The library does not know this title. Try the search of the library — the
                  upstream site carries a different catalog.
                </p>
              </div>
            )}

            {slug && episodes.isLoading && <Skeleton className="h-64 w-full rounded-xl" />}

            {slug && episodes.data && (
              <EpisodeList
                episodes={episodes.data.episodes}
                watched={watched}
                onSelect={play}
              />
            )}
          </section>
        </div>

        {/* what the library knows */}
        <aside className="space-y-4">
          <div className="rounded-xl border border-border/60 bg-card/60 p-4">
            <h3 className="mb-3 text-sm font-semibold">The library</h3>

            {libraryAnime.isLoading && <Skeleton className="h-20 w-full" />}

            {libraryAnime.data && (
              <dl className="space-y-2 text-xs">
                <div className="flex justify-between gap-4">
                  <dt className="text-muted-foreground">Title</dt>
                  <dd className="text-right font-medium">{libraryAnime.data.title}</dd>
                </div>
                {libraryAnime.data.japaneseTitle && (
                  <div className="flex justify-between gap-4">
                    <dt className="text-muted-foreground">Japanese</dt>
                    <dd className="text-right">{libraryAnime.data.japaneseTitle}</dd>
                  </div>
                )}
                <div className="flex justify-between gap-4">
                  <dt className="text-muted-foreground">Slug</dt>
                  <dd className="truncate font-mono text-[11px]">{libraryAnime.data.slug}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-muted-foreground">Id</dt>
                  <dd className="font-mono text-[11px]">{libraryAnime.data.numericId}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-muted-foreground">Status</dt>
                  <dd className="text-right">{libraryAnime.data.status}</dd>
                </div>
                {libraryAnime.data.year && (
                  <div className="flex justify-between gap-4">
                    <dt className="text-muted-foreground">Year</dt>
                    <dd className="text-right">{libraryAnime.data.year}</dd>
                  </div>
                )}
              </dl>
            )}

            {slug && episodes.data?.episodes.length ? (
              <Button className="mt-4 w-full" onClick={() => play(episodes.data.episodes[0])}>
                <Play className="size-4" />
                Play the first episode
              </Button>
            ) : null}
          </div>

          {item.nextAiringEpisode && (
            <div className="rounded-xl border border-border/60 bg-card/60 p-4">
              <h3 className="mb-2 text-sm font-semibold">Next episode</h3>
              <p className="text-xs text-muted-foreground">
                Episode {item.nextAiringEpisode.episode} airs on{" "}
                {new Date(item.nextAiringEpisode.airingAt * 1000).toLocaleString()}.
              </p>
            </div>
          )}

          <Separator />
        </aside>
      </div>
    </div>
  );
}
