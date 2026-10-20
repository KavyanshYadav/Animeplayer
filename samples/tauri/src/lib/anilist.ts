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

export type CatalogSection = "trending" | "popular" | "seasonal" | "upcoming";

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

/** Searches the catalog. */
export async function searchAnime(search: string, page = 1): Promise<AnimePage> {
  const data = await graphql<{ Page: AnimePage }>(
    `query ($search: String, $page: Int) {
       Page(page: $page, perPage: 24) {
         pageInfo { hasNextPage currentPage total }
         media(type: ANIME, search: $search, sort: SEARCH_MATCH) { ${MEDIA_FIELDS} }
       }
     }`,
    { search, page },
  );
  return data.Page;
}

/** The catalog of a section — trending, popular, seasonal or upcoming. */
export async function catalog(section: CatalogSection, page = 1): Promise<AnimePage> {
  const now = new Date();
  const year = now.getUTCFullYear();
  const season = ["WINTER", "SPRING", "SUMMER", "FALL"][
    Math.floor(((now.getUTCMonth() + 1) % 12) / 3)
  ];

  // Every section has its own query — a GraphQL query may not declare a variable it
  // does not use.
  const pageQuery = (sort: string, filter: string, extra: string) =>
    `query ($page: Int${extra}) {
       Page(page: $page, perPage: 24) {
         pageInfo { hasNextPage currentPage total }
         media(type: ANIME, sort: ${sort}${filter}) { ${MEDIA_FIELDS} }
       }
     }`;

  let statement: string;
  let variables: Record<string, unknown> = { page };

  switch (section) {
    case "trending":
      statement = pageQuery("TRENDING_DESC", "", "");
      break;
    case "popular":
      statement = pageQuery("POPULARITY_DESC", "", "");
      break;
    case "seasonal":
      statement = pageQuery(
        "POPULARITY_DESC",
        ", season: $season, seasonYear: $year",
        ", $season: MediaSeason, $year: Int",
      );
      variables = { page, season, year };
      break;
    default:
      statement = pageQuery("START_DATE_DESC", ", status: NOT_YET_RELEASED", "");
      break;
  }

  const data = await graphql<{ Page: AnimePage }>(statement, variables);
  return data.Page;
}

/** One anime of the catalog. */
export async function animeById(id: number): Promise<AnimeSummary> {
  const data = await graphql<{ Media: AnimeSummary }>(
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
