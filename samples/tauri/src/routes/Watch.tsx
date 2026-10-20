import { useCallback, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, ChevronLeft, ChevronRight, Loader2, TriangleAlert } from "lucide-react";

import { VideoPlayer } from "@/components/VideoPlayer";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { animeById } from "@/lib/anilist";
import { displayTitle } from "@/lib/format";
import { useHistory, useLibrary } from "@/lib/library";

/**
 * The player page.
 *
 * The episode is resolved by the library; the playlist it answers with points at the
 * proxy, which fetches the segments with the header the CDN demands.
 */
export function Watch() {
  const { animeId, episode } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { client } = useLibrary();
  const queryClient = useQueryClient();

  const slug = searchParams.get("slug") ?? "";
  const episodeNumber = decodeURIComponent(episode ?? "");
  const [audioTrack, setAudioTrack] = useState<"sub" | "dub">("sub");

  const anime = useQuery({
    queryKey: ["anime", Number(animeId)],
    queryFn: () => animeById(Number(animeId)),
    enabled: Number.isFinite(Number(animeId)),
    staleTime: 10 * 60 * 1000,
  });

  const resolved = useQuery({
    queryKey: ["resolve", slug, episodeNumber, audioTrack],
    queryFn: () => client!.resolve(slug, episodeNumber, { audioTrack }),
    enabled: Boolean(client) && Boolean(slug) && Boolean(episodeNumber),
    retry: 1,
    staleTime: 0,
  });

  const episodes = useQuery({
    queryKey: ["library-episodes", slug],
    queryFn: () => client!.episodes(slug),
    enabled: Boolean(client) && Boolean(slug),
    staleTime: 5 * 60 * 1000,
  });

  const history = useHistory();

  const resumeAtMs = useMemo(
    () =>
      history.data?.find(
        (entry) => entry.slug === slug && entry.episode === episodeNumber,
      )?.positionMs,
    [history.data, slug, episodeNumber],
  );

  const title = anime.data ? displayTitle(anime.data.title) : slug;

  const saveProgress = useCallback(
    (positionMs: number, durationMs: number) => {
      void client?.saveProgress({
        slug,
        title,
        episode: episodeNumber,
        positionMs: Math.round(positionMs),
        durationMs: Math.round(durationMs),
      });
      // The library page shows the history, so it has to learn about the new position.
      void queryClient.invalidateQueries({ queryKey: ["history"], refetchType: "none" });
    },
    [client, slug, title, episodeNumber, queryClient],
  );

  const index = episodes.data?.episodes.findIndex(
    (item) => item.number === episodeNumber,
  );
  const previous = index !== undefined && index > 0 ? episodes.data?.episodes[index - 1] : null;
  const next =
    index !== undefined && episodes.data && index + 1 < episodes.data.episodes.length
      ? episodes.data.episodes[index + 1]
      : null;

  const goTo = (number: string) => {
    navigate(
      `/watch/${animeId}/${encodeURIComponent(number)}?slug=${encodeURIComponent(slug)}`,
    );
  };

  return (
    <div className="mx-auto w-full max-w-6xl space-y-4 p-4 lg:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          to={`/anime/${animeId}`}
          className="inline-flex items-center gap-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="size-3.5" />
          {title}
        </Link>

        <Tabs value={audioTrack} onValueChange={(value) => setAudioTrack(value as "sub" | "dub")}>
          <TabsList>
            <TabsTrigger value="sub">Subtitled</TabsTrigger>
            <TabsTrigger value="dub">Dubbed</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {resolved.isLoading && (
        <div className="flex aspect-video w-full items-center justify-center rounded-xl border border-border/60 bg-black">
          <Loader2 className="size-8 animate-spin text-primary" />
        </div>
      )}

      {resolved.isError && (
        <div className="flex aspect-video w-full flex-col items-center justify-center gap-2 rounded-xl border border-destructive/40 bg-destructive/5 p-6 text-center">
          <TriangleAlert className="size-7 text-destructive" />
          <p className="text-sm text-destructive">
            The episode could not be resolved. The upstream site may be busy.
          </p>
          <Button variant="outline" size="sm" onClick={() => resolved.refetch()}>
            Try again
          </Button>
        </div>
      )}

      {resolved.data && (
        <VideoPlayer
          key={`${resolved.data.playlistUrl}-${audioTrack}`}
          source={resolved.data}
          title={title}
          episodeLabel={`Episode ${resolved.data.episodeNumber} · ${resolved.data.quality}`}
          resumeAtMs={resumeAtMs}
          onProgress={saveProgress}
        />
      )}

      {resolved.data && (
        <div className="flex items-center justify-between gap-3">
          <Button
            variant="outline"
            size="sm"
            disabled={!previous}
            onClick={() => previous && goTo(previous.number)}
          >
            <ChevronLeft className="size-4" />
            Previous
          </Button>

          <p className="truncate text-xs text-muted-foreground">
            Episode {resolved.data.episodeNumber}
            {resolved.data.skipIntroStartSeconds !== null &&
              ` · intro ${Math.round(resolved.data.skipIntroStartSeconds)}s–${Math.round(
                resolved.data.skipIntroEndSeconds ?? 0,
              )}s`}
          </p>

          <Button
            variant="outline"
            size="sm"
            disabled={!next}
            onClick={() => next && goTo(next.number)}
          >
            Next
            <ChevronRight className="size-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
