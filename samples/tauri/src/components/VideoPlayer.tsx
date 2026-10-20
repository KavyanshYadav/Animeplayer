import Hls from "hls.js";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Loader2,
  Maximize,
  Minimize,
  Pause,
  Play,
  RotateCcw,
  RotateCw,
  Settings,
  SkipForward,
  Subtitles,
  Volume2,
  VolumeX,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Slider } from "@/components/ui/slider";
import { cn } from "@/lib/utils";
import { formatDuration } from "@/lib/format";
import type { ResolvedEpisode } from "@/lib/server";

interface VideoPlayerProps {
  source: ResolvedEpisode;
  title: string;
  episodeLabel: string;
  /** The position the episode was left at, in milliseconds. */
  resumeAtMs?: number;
  /** Called every few seconds with the playback position. */
  onProgress?: (positionMs: number, durationMs: number) => void;
  onEnded?: () => void;
}

interface Level {
  index: number;
  label: string;
}

/**
 * The player.
 *
 * The playlist comes from the proxy of the library, which sets the `Referer` header
 * the CDN of the upstream site demands — a browser is not allowed to do that itself.
 */
export function VideoPlayer({
  source,
  title,
  episodeLabel,
  resumeAtMs,
  onProgress,
  onEnded,
}: VideoPlayerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const hideTimer = useRef<number | undefined>(undefined);
  const recoveries = useRef(0);

  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [buffered, setBuffered] = useState(0);
  const [volume, setVolume] = useState(1);
  const [isMuted, setIsMuted] = useState(false);
  const [levels, setLevels] = useState<Level[]>([]);
  const [activeLevel, setActiveLevel] = useState(-1);
  const [activeSubtitle, setActiveSubtitle] = useState<string | null>(
    source.subtitles.find((track) => track.isDefault)?.url ?? null,
  );
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showControls, setShowControls] = useState(true);

  // The subtitles of the episode, as the library found them.
  const subtitles = useMemo(() => source.subtitles, [source.subtitles]);

  /* ---------------------------------------------------------------- the stream */

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    setIsLoading(true);
    setError(null);
    setCurrentTime(0);
    setDuration(0);
    setLevels([]);
    setActiveLevel(-1);
    recoveries.current = 0;

    let hls: Hls | null = null;

    if (Hls.isSupported()) {
      hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        backBufferLength: 60,
      });
      hlsRef.current = hls;
      hls.loadSource(source.playlistUrl);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, (_event, data) => {
        setLevels(
          data.levels.map((level, index) => ({
            index,
            label: level.height ? `${level.height}p` : `${Math.round(level.bitrate / 1000)} kbps`,
          })),
        );
        setIsLoading(false);
      });

      hls.on(Hls.Events.LEVEL_SWITCHED, (_event, data) => setActiveLevel(data.level));

      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) return;
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          // The CDN of the upstream site blocks bursts of segment requests. Give it
          // a moment and continue where the stream stopped.
          recoveries.current += 1;
          if (recoveries.current <= 3) {
            window.setTimeout(() => hls?.startLoad(), 1500);
            return;
          }
          setError("The stream could not be reached. The upstream site may be busy.");
        } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hls?.recoverMediaError();
        } else {
          setError("The stream could not be played.");
        }
        setIsLoading(false);
      });
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      // Safari plays the playlist itself.
      video.src = source.playlistUrl;
      setIsLoading(false);
    } else {
      setError("This webview cannot play HLS.");
      setIsLoading(false);
    }

    return () => {
      hls?.destroy();
      hlsRef.current = null;
    };
  }, [source.playlistUrl]);

  /* ------------------------------------------------------------- the video tag */

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const onTimeUpdate = () => {
      setCurrentTime(video.currentTime);
      if (video.buffered.length) {
        setBuffered(video.buffered.end(video.buffered.length - 1));
      }
    };
    const onMetadata = () => {
      setDuration(video.duration);
      if (resumeAtMs && resumeAtMs > 5000 && resumeAtMs / 1000 < video.duration - 10) {
        video.currentTime = resumeAtMs / 1000;
      }
    };
    const onPlay = () => setIsPlaying(true);
    const onPause = () => setIsPlaying(false);
    const onEnded = () => {
      setIsPlaying(false);
      onEnded?.();
    };
    const onVolume = () => {
      setVolume(video.volume);
      setIsMuted(video.muted);
    };

    video.addEventListener("timeupdate", onTimeUpdate);
    video.addEventListener("loadedmetadata", onMetadata);
    video.addEventListener("play", onPlay);
    video.addEventListener("pause", onPause);
    video.addEventListener("ended", onEnded);
    video.addEventListener("volumechange", onVolume);

    return () => {
      video.removeEventListener("timeupdate", onTimeUpdate);
      video.removeEventListener("loadedmetadata", onMetadata);
      video.removeEventListener("play", onPlay);
      video.removeEventListener("pause", onPause);
      video.removeEventListener("ended", onEnded);
      video.removeEventListener("volumechange", onVolume);
    };
  }, [resumeAtMs, onEnded]);

  /* -------------------------------------------------------------- the subtitles */

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const tracks = video.textTracks;
    for (let index = 0; index < tracks.length; index += 1) {
      const track = tracks[index];
      const isActive = subtitles[index]?.url === activeSubtitle;
      track.mode = isActive ? "showing" : "disabled";
    }
  }, [activeSubtitle, subtitles, source.playlistUrl]);

  /* -------------------------------------------------------------- the progress */

  useEffect(() => {
    if (!onProgress) return;
    const timer = window.setInterval(() => {
      const video = videoRef.current;
      if (!video || !video.duration) return;
      onProgress(video.currentTime * 1000, video.duration * 1000);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [onProgress]);

  /* ----------------------------------------------------------------- the keys */

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)) return;

      const video = videoRef.current;
      if (!video) return;

      switch (event.key) {
        case " ":
        case "k":
          event.preventDefault();
          video.paused ? void video.play() : video.pause();
          break;
        case "ArrowLeft":
          event.preventDefault();
          video.currentTime = Math.max(0, video.currentTime - 10);
          break;
        case "ArrowRight":
          event.preventDefault();
          video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
          break;
        case "f":
          event.preventDefault();
          void toggleFullscreen();
          break;
        case "m":
          event.preventDefault();
          video.muted = !video.muted;
          break;
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  });

  /* --------------------------------------------------------------- the actions */

  const toggleFullscreen = useCallback(async () => {
    const container = containerRef.current;
    if (!container) return;
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    } else {
      await container.requestFullscreen();
    }
  }, []);

  useEffect(() => {
    const onChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener("fullscreenchange", onChange);
    return () => document.removeEventListener("fullscreenchange", onChange);
  }, []);

  const seek = useCallback((seconds: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = Math.max(0, Math.min(video.duration || 0, seconds));
  }, []);

  const skip = useCallback(
    (delta: number) => {
      const video = videoRef.current;
      if (!video) return;
      video.currentTime = Math.max(0, Math.min(video.duration || 0, video.currentTime + delta));
    },
    [],
  );

  const changeLevel = useCallback((index: number) => {
    const hls = hlsRef.current;
    if (!hls) return;
    hls.currentLevel = index;
    setActiveLevel(index);
  }, []);

  const wake = useCallback(() => {
    setShowControls(true);
    if (hideTimer.current) window.clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => {
      if (videoRef.current && !videoRef.current.paused) setShowControls(false);
    }, 2500);
  }, []);

  useEffect(() => () => {
    if (hideTimer.current) window.clearTimeout(hideTimer.current);
  }, []);

  /* ------------------------------------------------------------------ the skip */

  const skipIntroEnd = source.skipIntroEndSeconds ?? null;
  const canSkipIntro =
    skipIntroEnd !== null &&
    currentTime >= (source.skipIntroStartSeconds ?? 0) &&
    currentTime < skipIntroEnd;

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0;
  const bufferedProgress = duration > 0 ? (buffered / duration) * 100 : 0;

  return (
    <div
      ref={containerRef}
      onMouseMove={wake}
      onMouseLeave={() => isPlaying && setShowControls(false)}
      className={cn(
        "group relative aspect-video w-full overflow-hidden rounded-xl border border-border/60 bg-black",
        !showControls && isPlaying && "cursor-none",
      )}
    >
      <video
        ref={videoRef}
        className="h-full w-full"
        playsInline
        crossOrigin="anonymous"
        onClick={() => {
          const video = videoRef.current;
          if (!video) return;
          video.paused ? void video.play() : video.pause();
        }}
      >
        {subtitles.map((track) => (
          <track
            key={track.url}
            kind="subtitles"
            src={track.url}
            srcLang={track.language}
            label={track.label}
          />
        ))}
      </video>

      {isLoading && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/60">
          <Loader2 className="size-8 animate-spin text-primary" />
        </div>
      )}

      {error && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/80 p-6 text-center">
          <p className="text-sm text-destructive">{error}</p>
          <Button variant="outline" size="sm" onClick={() => window.location.reload()}>
            Try again
          </Button>
        </div>
      )}

      {canSkipIntro && (
        <Button
          variant="secondary"
          size="sm"
          onClick={() => seek(skipIntroEnd)}
          className="absolute bottom-24 right-4 animate-in fade-in"
        >
          <SkipForward className="size-4" />
          Skip intro
        </Button>
      )}

      {/* the controls */}
      <div
        className={cn(
          "absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/90 via-black/60 to-transparent px-4 pb-3 pt-10 transition-opacity duration-300",
          showControls ? "opacity-100" : "pointer-events-none opacity-0",
        )}
      >
        {/* the seek bar */}
        <div className="relative mb-3 h-1.5 w-full cursor-pointer rounded-full bg-white/20">
          <div
            className="absolute inset-y-0 left-0 rounded-full bg-white/25"
            style={{ width: `${bufferedProgress}%` }}
          />
          <div
            className="absolute inset-y-0 left-0 rounded-full bg-primary"
            style={{ width: `${progress}%` }}
          />
          <input
            type="range"
            min={0}
            max={duration || 0}
            step={0.1}
            value={currentTime}
            onChange={(event) => seek(Number(event.target.value))}
            className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
            aria-label="The position of the episode"
          />
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            className="text-white hover:bg-white/10 hover:text-white"
            onClick={() => skip(-10)}
            title="Ten seconds back"
          >
            <RotateCcw className="size-4" />
          </Button>

          <Button
            variant="ghost"
            size="icon"
            className="text-white hover:bg-white/10 hover:text-white"
            onClick={() => {
              const video = videoRef.current;
              if (!video) return;
              video.paused ? void video.play() : video.pause();
            }}
            title={isPlaying ? "Pause" : "Play"}
          >
            {isPlaying ? <Pause className="size-5" /> : <Play className="size-5" />}
          </Button>

          <Button
            variant="ghost"
            size="icon"
            className="text-white hover:bg-white/10 hover:text-white"
            onClick={() => skip(10)}
            title="Ten seconds forward"
          >
            <RotateCw className="size-4" />
          </Button>

          <div className="hidden items-center gap-2 sm:flex">
            <Button
              variant="ghost"
              size="icon"
              className="text-white hover:bg-white/10 hover:text-white"
              onClick={() => {
                const video = videoRef.current;
                if (!video) return;
                video.muted = !video.muted;
              }}
              title={isMuted ? "Unmute" : "Mute"}
            >
              {isMuted || volume === 0 ? (
                <VolumeX className="size-4" />
              ) : (
                <Volume2 className="size-4" />
              )}
            </Button>
            <Slider
              value={[volume * 100]}
              max={100}
              step={1}
              onValueChange={([value]) => {
                const video = videoRef.current;
                if (!video) return;
                video.volume = value / 100;
                video.muted = value === 0;
              }}
              className="w-20"
            />
          </div>

          <span className="ml-1 text-xs tabular-nums text-white/80">
            {formatDuration(currentTime)} / {formatDuration(duration)}
          </span>

          <div className="ml-auto flex items-center gap-1">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="text-white hover:bg-white/10 hover:text-white"
                  title="The quality and the subtitles"
                >
                  <Settings className="size-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-52">
                <DropdownMenuLabel>Quality</DropdownMenuLabel>
                <DropdownMenuItem onSelect={() => changeLevel(-1)}>
                  <span className="flex-1">Auto</span>
                  {activeLevel === -1 && <span className="text-primary">✓</span>}
                </DropdownMenuItem>
                {levels.map((level) => (
                  <DropdownMenuItem key={level.index} onSelect={() => changeLevel(level.index)}>
                    <span className="flex-1">{level.label}</span>
                    {activeLevel === level.index && <span className="text-primary">✓</span>}
                  </DropdownMenuItem>
                ))}

                <DropdownMenuSeparator />

                <DropdownMenuLabel>Subtitles</DropdownMenuLabel>
                <DropdownMenuItem onSelect={() => setActiveSubtitle(null)}>
                  <span className="flex-1">Off</span>
                  {activeSubtitle === null && <span className="text-primary">✓</span>}
                </DropdownMenuItem>
                {subtitles.map((track) => (
                  <DropdownMenuItem key={track.url} onSelect={() => setActiveSubtitle(track.url)}>
                    <Subtitles className="mr-2 size-3.5" />
                    <span className="flex-1">{track.label}</span>
                    {activeSubtitle === track.url && <span className="text-primary">✓</span>}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>

            <Button
              variant="ghost"
              size="icon"
              className="text-white hover:bg-white/10 hover:text-white"
              onClick={() => void toggleFullscreen()}
              title={isFullscreen ? "Leave the fullscreen" : "The fullscreen"}
            >
              {isFullscreen ? <Minimize className="size-4" /> : <Maximize className="size-4" />}
            </Button>
          </div>
        </div>
      </div>

      <div className="pointer-events-none absolute left-4 top-4 rounded-md bg-black/50 px-2 py-1 text-xs text-white/90">
        {title} · {episodeLabel}
      </div>
    </div>
  );
}
