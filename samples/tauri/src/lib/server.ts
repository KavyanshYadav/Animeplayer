/**
 * The client of the animeplayer library.
 *
 * The library runs as a sidecar process next to the application. It speaks HTTP: the
 * search and the episode list come from the upstream site, the playlist and the
 * subtitles are served through the proxy of the library, because the CDN of the
 * upstream site demands a `Referer` header a browser is not allowed to send.
 */

export interface LibrarySearchResult {
  slug: string;
  numericId: string;
  title: string;
  japaneseTitle: string | null;
  posterUrl: string | null;
  year: number | null;
  genres: string[];
  status: string;
}

export interface LibraryEpisode {
  number: string;
  index: number;
  title: string | null;
  durationSeconds: number | null;
  isSpecial: boolean;
}

export interface LibraryAnime {
  slug: string;
  numericId: string;
  title: string;
  japaneseTitle: string | null;
  posterUrl: string | null;
  description: string | null;
  genres: string[];
  year: number | null;
  status: string;
  titles: string[];
}

export interface LibraryEpisodeList {
  slug: string;
  totalReported: number | null;
  episodes: LibraryEpisode[];
}

export interface ProxiedSubtitle {
  language: string;
  label: string;
  url: string;
  isDefault: boolean;
}

export interface ResolvedEpisode {
  title: string;
  animeId: string;
  episodeNumber: string;
  quality: string;
  playlistUrl: string;
  subtitles: ProxiedSubtitle[];
  skipIntroStartSeconds: number | null;
  skipIntroEndSeconds: number | null;
}

export interface HistoryEntry {
  slug: string;
  numericId: string;
  title: string;
  episode: string;
  updatedAt: number;
  positionMs: number;
  durationMs: number;
  progress: number;
  isCompleted: boolean;
}

export interface AudioTrack {
  value: "sub" | "dub";
  label: string;
}

export const AUDIO_TRACKS: AudioTrack[] = [
  { value: "sub", label: "Subtitled" },
  { value: "dub", label: "Dubbed" },
];

export class LibraryError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "LibraryError";
  }
}

async function request<T>(baseUrl: string, path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...init,
      // Only a request with a body needs the content type. Sending it always would
      // turn every read into a preflight.
      headers: init?.body
        ? { "Content-Type": "application/json", ...(init?.headers ?? {}) }
        : init?.headers,
    });
  } catch (error) {
    throw new LibraryError(
      `The animeplayer server is not reachable: ${(error as Error).message}`,
    );
  }

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new LibraryError(`The server answered with ${response.status}. ${body}`.trim());
  }

  return (await response.json()) as T;
}

/** Everything the frontend can ask the library for. */
export function createLibraryClient(baseUrl: string) {
  return {
    baseUrl,

    /** Searches the upstream site. */
    async search(query: string): Promise<LibrarySearchResult[]> {
      const data = await request<{ results: LibrarySearchResult[] }>(
        baseUrl,
        `/api/search?q=${encodeURIComponent(query)}`,
      );
      return data.results;
    },

    /** The episodes of a series. */
    async episodes(slug: string): Promise<LibraryEpisodeList> {      return request<LibraryEpisodeList>(
        baseUrl,
        `/api/episodes?slug=${encodeURIComponent(slug)}`,
      );
    },

    /** The metadata the library has about a series. */
    async anime(slug: string): Promise<LibraryAnime> {
      return request<LibraryAnime>(
        baseUrl,
        `/api/anime?slug=${encodeURIComponent(slug)}`,
      );
    },

    /** The broadcast schedule — a different site than the streams. */
    async schedule(query = ""): Promise<ScheduleItem[]> {
      const data = await request<{ entries: ScheduleItem[] }>(
        baseUrl,
        `/api/schedule?q=${encodeURIComponent(query)}`,
      );
      return data.entries;
    },

    /** Resolves an episode into a playlist the browser can play. */
    async resolve(
      slug: string,
      episode: string,
      options: { audioTrack?: "sub" | "dub"; quality?: string } = {},
    ): Promise<ResolvedEpisode> {
      return request<ResolvedEpisode>(baseUrl, "/api/resolve", {
        method: "POST",
        body: JSON.stringify({
          slug,
          episode,
          audioTrack: options.audioTrack ?? "sub",
          quality: options.quality ?? null,
        }),
      });
    },

    /** The watch history. */
    async history(): Promise<HistoryEntry[]> {
      const data = await request<{ entries: HistoryEntry[] }>(baseUrl, "/api/history");
      return data.entries;
    },

    /** Remembers where the user got to. */
    async saveProgress(entry: {
      slug: string;
      title: string;
      episode: string;
      positionMs: number;
      durationMs: number;
    }): Promise<void> {
      await request(baseUrl, "/api/history", {
        method: "POST",
        body: JSON.stringify(entry),
      });
    },

    /** Forgets an episode. */
    async forget(slug: string, episode: string): Promise<void> {
      await request(
        baseUrl,
        `/api/history?slug=${encodeURIComponent(slug)}&episode=${encodeURIComponent(episode)}`,
        { method: "DELETE" },
      );
    },
  };
}

export interface ScheduleItem {
  title: string;
  status: string;
  nextSub: string | null;
  nextDub: string | null;
}

export type LibraryClient = ReturnType<typeof createLibraryClient>;
