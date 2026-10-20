import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Play } from "lucide-react";

import { Badge } from "@/components/Hero";
import { EpisodeCard } from "@/components/EpisodeCard";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useHistory, useLibrary } from "@/lib/library";

/**
 * A series of the upstream site, without the catalog of AniList.
 */
export function Series() {
  const { slug = "" } = useParams();
  const navigate = useNavigate();
  const { client } = useLibrary();

  const anime = useQuery({
    queryKey: ["library-anime", slug],
    queryFn: () => client!.anime(slug),
    enabled: Boolean(client) && Boolean(slug),
  });

  const episodes = useQuery({
    queryKey: ["library-episodes", slug],
    queryFn: () => client!.episodes(slug),
    enabled: Boolean(client) && Boolean(slug),
  });

  const history = useHistory();

  const watched = useMemo(() => {
    const map: Record<string, number> = {};
    for (const entry of history.data ?? []) {
      if (entry.slug === slug) map[entry.episode] = entry.progress;
    }
    return map;
  }, [history.data, slug]);

  const play = (number: string) => {
    navigate(`/watch/0/${encodeURIComponent(number)}?slug=${encodeURIComponent(slug)}`);
  };

  return (
    <div className="space-y-8 p-6 lg:p-10">
      <Link
        to="/library"
        className="inline-flex items-center gap-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" />
        Back to the library
      </Link>

      {anime.isLoading && <Skeleton className="h-64 w-full rounded-xl" />}

      {anime.data && (
        <section className="flex flex-col gap-6 sm:flex-row">
          {anime.data.posterUrl && (
            <img
              src={anime.data.posterUrl}
              alt={anime.data.title}
              className="w-40 shrink-0 rounded-lg border border-border/60 shadow-2xl"
            />
          )}
          <div className="min-w-0 flex-1 space-y-3">
            <h1 className="text-2xl font-black tracking-tight">{anime.data.title}</h1>
            {anime.data.japaneseTitle && (
              <p className="text-sm text-muted-foreground">{anime.data.japaneseTitle}</p>
            )}

            <div className="flex flex-wrap gap-2">
              <Badge tone="primary">{anime.data.status}</Badge>
              {anime.data.year && <Badge tone="primary">{anime.data.year}</Badge>}
              <Badge tone="outline">{anime.data.slug}</Badge>
            </div>

            {anime.data.genres.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {anime.data.genres.map((genre) => (
                  <span
                    key={genre}
                    className="rounded-md border border-border/70 px-2.5 py-1 text-xs text-muted-foreground"
                  >
                    {genre}
                  </span>
                ))}
              </div>
            )}

            {anime.data.description && (
              <p className="max-w-2xl text-sm leading-relaxed text-foreground/80">
                {anime.data.description}
              </p>
            )}

            {episodes.data?.episodes[0] && (
              <Button className="gap-2 font-bold" onClick={() => play(episodes.data!.episodes[0].number)}>
                <Play className="size-4 fill-current" />
                Watch Now
              </Button>
            )}
          </div>
        </section>
      )}

      <section className="space-y-4">
        <h2 className="text-lg font-bold tracking-tight">Episodes</h2>
        {episodes.isLoading && <Skeleton className="h-64 w-full rounded-xl" />}
        {episodes.data && (
          <div className="grid gap-4 xl:grid-cols-2">
            {episodes.data.episodes.map((episode) => (
              <EpisodeCard
                key={`${episode.index}-${episode.number}`}
                episode={episode}
                progress={watched[episode.number]}
                onPlay={() => play(episode.number)}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
