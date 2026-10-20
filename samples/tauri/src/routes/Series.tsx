import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Play } from "lucide-react";

import { EpisodeList } from "@/components/EpisodeList";
import { Badge } from "@/components/ui/badge";
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
    <div className="mx-auto w-full max-w-5xl space-y-6 p-6 lg:p-10">
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
              className="w-40 rounded-lg border border-border/60 shadow-xl"
            />
          )}
          <div className="min-w-0 flex-1 space-y-3">
            <h1 className="text-2xl font-semibold tracking-tight">{anime.data.title}</h1>
            {anime.data.japaneseTitle && (
              <p className="text-sm text-muted-foreground">{anime.data.japaneseTitle}</p>
            )}

            <div className="flex flex-wrap gap-2 text-xs">
              <Badge variant="secondary">{anime.data.status}</Badge>
              {anime.data.year && <Badge variant="secondary">{anime.data.year}</Badge>}
              <Badge variant="outline" className="font-mono">
                {anime.data.slug}
              </Badge>
            </div>

            {anime.data.genres.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {anime.data.genres.map((genre) => (
                  <span
                    key={genre}
                    className="rounded-full border border-border/70 px-2 py-0.5 text-[11px] text-muted-foreground"
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
              <Button onClick={() => play(episodes.data!.episodes[0].number)}>
                <Play className="size-4" />
                Play the first episode
              </Button>
            )}
          </div>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
          Episodes
        </h2>
        {episodes.isLoading && <Skeleton className="h-64 w-full rounded-xl" />}
        {episodes.data && (
          <EpisodeList
            episodes={episodes.data.episodes}
            watched={watched}
            onSelect={(episode) => play(episode.number)}
          />
        )}
      </section>
    </div>
  );
}
