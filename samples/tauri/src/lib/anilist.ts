/**
 * The AniList GraphQL API.
 *
 * The catalog of the application comes from here: the posters, the descriptions and
 * the scores. The streams themselves come from the animeplayer library — the two are
 * joined by the title.
 *
 * The API allows thirty requests per minute, so every query is cached for a long time
 * and the cache survives a restart.
 */

const ENDPOINT = "https://graphql.anilist.co";

export interface AnimeTitle {
  romaji: string | null;
  english: string | null;
  native: string | null;
}

export interface AnimeSummary {
  id: number;
  title: AnimeTitle;
  coverImage: { large: string; color: string | null };
  bannerImage: string | null;
  averageScore: number | null;
  genres: string[];
  seasonYear: number | null;
  episodes: number | null;
  duration: number | null;
  status: string | null;
  format: string | null;
  description: string | null;
  popularity: number | null;
  nextAiringEpisode: {
    airingAt: number;
    episode: number;
  } | null;
}

export interface AnimePage {
  pageInfo: { hasNextPage: boolean; currentPage: number; total: number };
  media: AnimeSummary[];
}

/** The sorts the catalog knows. */
export const SORTS = [
  { value: "TRENDING_DESC", label: "Trending" },
  { value: "POPULARITY_DESC", label: "Popular" },
  { value: "SCORE_DESC", label: "Top rated" },
  { value: "START_DATE_DESC", label: "Newest" },
  { value: "FAVOURITES_DESC", label: "Favourites" },
  { value: "EPISODE_DESC", label: "Most episodes" },
] as const;

/** The genres the catalog knows — the first ones are the popular ones. */
export const GENRES = [
  "Action",
  "Adventure",
  "Comedy",
  "Drama",
  "Fantasy",
  "Horror",
  "Mystery",
  "Romance",
  "Sci-Fi",
  "Slice of Life",
  "Sports",
  "Supernatural",
  "Thriller",
] as const;

export const FORMATS = ["TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC"] as const;

export const STATUSES = [
  "FINISHED",
  "RELEASING",
  "NOT_YET_RELEASED",
  "CANCELLED",
  "HIATUS",
] as const;

export interface CatalogQuery {
  search?: string;
  sort?: string;
  genre?: string;
  season?: string;
  seasonYear?: number;
  format?: string;
  status?: string;
  perPage?: number;
  page?: number;
}

const MEDIA_FIELDS = `
  id
  title { romaji english native }
  coverImage { large color }
  bannerImage
  averageScore
  genres
  seasonYear
  episodes
  duration
  status
  format
  description(asHtml: false)
  popularity
  nextAiringEpisode { airingAt episode }
`;

class AniListError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AniListError";
  }
}

async function graphql<T>(document: string, variables: Record<string, unknown>): Promise<T> {
  const response = await fetch(ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ query: document, variables }),
  });

  if (!response.ok) {
    throw new AniListError(`AniList answered with ${response.status} ${response.statusText}.`);
  }

  const payload = (await response.json()) as {
    data?: T;
    errors?: { message: string }[];
  };

  if (payload.errors?.length) {
    throw new AniListError(payload.errors.map((error) => error.message).join(" "));
  }
  if (!payload.data) {
    throw new AniListError("AniList returned an empty answer.");
  }
  return payload.data;
}

/** The current season and the current year. */
export function currentSeason(): { season: string; year: number } {
  const now = new Date();
  const seasons = ["WINTER", "SPRING", "SUMMER", "FALL"];
  return {
    season: seasons[Math.floor(((now.getUTCMonth() + 1) % 12) / 3)],
    year: now.getUTCFullYear(),
  };
}

/**
 * Searches the catalog.
 *
 * Every filter is optional — the query is built from the ones that are set.
 */
export async function searchAnime(query: CatalogQuery = {}): Promise<AnimePage> {
  const filters: string[] = ["type: ANIME"];
  const variables: Record<string, unknown> = {
    page: query.page ?? 1,
    perPage: query.perPage ?? 24,
  };

  if (query.search) {
    filters.push("search: $search");
    variables.search = query.search;
  }
  if (query.sort) {
    filters.push("sort: $sort");
    variables.sort = [query.sort];
  }
  if (query.genre) {
    filters.push("genre_in: [$genre]");
    variables.genre = query.genre;
  }
  if (query.season) {
    filters.push("season: $season");
    variables.season = query.season;
  }
  if (query.seasonYear) {
    filters.push("seasonYear: $year");
    variables.year = query.seasonYear;
  }
  if (query.format) {
    filters.push("format: $format");
    variables.format = query.format;
  }
  if (query.status) {
    filters.push("status: $status");
    variables.status = query.status;
  }

  const declarations: string[] = ["$page: Int", "$perPage: Int"];
  if (query.search) declarations.push("$search: String");
  if (query.sort) declarations.push("$sort: [MediaSort]");
  if (query.genre) declarations.push("$genre: String");
  if (query.season) declarations.push("$season: MediaSeason");
  if (query.seasonYear) declarations.push("$year: Int");
  if (query.format) declarations.push("$format: MediaFormat");
  if (query.status) declarations.push("$status: MediaStatus");

  const document = `query (${declarations.join(", ")}) {
    Page(page: $page, perPage: $perPage) {
      pageInfo { hasNextPage currentPage total }
      media(${filters.join(", ")}) { ${MEDIA_FIELDS} }
    }
  }`;

  const data = await graphql<{ Page: AnimePage }>(document, variables);
  return data.Page;
}

/** Several anime of the catalog, by their id. */
export async function animeByIds(ids: number[]): Promise<AnimeSummary[]> {
  if (!ids.length) return [];
  const data = await graphql<{ Page: AnimePage }>(
    `query ($ids: [Int], $perPage: Int) {
       Page(page: 1, perPage: $perPage) {
         pageInfo { hasNextPage currentPage total }
         media(id_in: $ids, type: ANIME, sort: POPULARITY_DESC) { ${MEDIA_FIELDS} }
       }
     }`,
    { ids, perPage: ids.length },
  );
  return data.Page?.media ?? [];
}

/** One anime of the catalog. */
export async function animeById(id: number): Promise<AnimeSummary> {  const data = await graphql<{ Media: AnimeSummary }>(
    `query ($id: Int) {
       Media(id: $id, type: ANIME) { ${MEDIA_FIELDS} }
     }`,
    { id },
  );
  if (!data.Media) {
    throw new AniListError(`AniList does not know the anime ${id}.`);
  }
  return data.Media;
}

/** One episode of the catalog. */
export interface AnimeEpisode {
  number: number;
  title: string | null;
  description: string | null;
  thumbnail: string | null;
  airDate: number | null;
  averageScore: number | null;
  length: number | null;
}

/** The episodes of the catalog — the thumbnails and the descriptions. */
export async function animeEpisodes(id: number): Promise<AnimeEpisode[]> {
  const data = await graphql<{
    Media: {
      episodes: {
        nodes: {
          number: number;
          title: string | null;
          description: string | null;
          thumbnail: string | null;
          airDate: number | null;
          averageScore: number | null;
          length: number | null;
        }[];
      };
    };
  }>(
    `query ($id: Int) {
       Media(id: $id, type: ANIME) {
         episodes(sort: ASC, perPage: 60) {
           nodes {
             number
             title
             description(asHtml: false)
             thumbnail
             airDate
             averageScore
             length
           }
         }
       }
     }`,
    { id },
  );

  return data.Media?.episodes?.nodes ?? [];
}

/** The relations of an anime — the sequels, the prequels and the side stories. */
export interface AnimeRelation {
  id: number;
  title: AnimeTitle;
  coverImage: { large: string; color: string | null };
  relationType: string;
  format: string | null;
  seasonYear: number | null;
}

export async function animeRelations(id: number): Promise<AnimeRelation[]> {
  const data = await graphql<{
    Media: {
      relations: {
        edges: {
          relationType: string;
          node: {
            id: number;
            title: AnimeTitle;
            coverImage: { large: string; color: string | null };
            format: string | null;
            seasonYear: number | null;
          };
        }[];
      };
    };
  }>(
    `query ($id: Int) {
       Media(id: $id, type: ANIME) {
         relations {
           edges {
             relationType
             node {
               id
               title { romaji english native }
               coverImage { large color }
               format
               seasonYear
             }
           }
         }
       }
     }`,
    { id },
  );

  return (data.Media?.relations?.edges ?? []).map((edge) => ({
    id: edge.node.id,
    title: edge.node.title,
    coverImage: edge.node.coverImage,
    relationType: edge.relationType,
    format: edge.node.format,
    seasonYear: edge.node.seasonYear,
  }));
}

/** The recommendations of an anime. */
export async function animeRecommendations(id: number): Promise<AnimeSummary[]> {
  const data = await graphql<{ Media: { recommendations: { nodes: AnimeSummary[] } } }>(
    `query ($id: Int) {
       Media(id: $id, type: ANIME) {
         recommendations(sort: RATING_DESC, perPage: 12) { nodes { ${MEDIA_FIELDS} } }
       }
     }`,
    { id },
  );
  return data.Media?.recommendations?.nodes ?? [];
}
