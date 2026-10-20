/**
 * The bridge between the catalog and the library.
 *
 * The catalog knows an anime by its AniList title, the library knows it by the slug of
 * the upstream site. Nothing connects the two — so the titles are compared.
 */

/** Removes everything that is not a letter or a digit. */
export function normalizeTitle(title: string): string {
  return title
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

/**
 * How well two titles match.
 *
 * `1` is an exact match, `0` means the titles have nothing in common.
 */
export function titleScore(left: string, right: string): number {
  const a = normalizeTitle(left);
  const b = normalizeTitle(right);
  if (!a || !b) return 0;
  if (a === b) return 1;

  if (a.startsWith(b) || b.startsWith(a)) return 0.85;

  const tokensA = new Set(a.split(" "));
  const tokensB = new Set(b.split(" "));
  const shared = [...tokensA].filter((token) => tokensB.has(token)).length;
  const union = new Set([...tokensA, ...tokensB]).size;
  const overlap = union === 0 ? 0 : shared / union;

  if (a.includes(b) || b.includes(a)) return Math.max(0.7, overlap);

  // "one piece film" against "one piece" — the shorter title wins.
  if (tokensA.size > 0 && tokensB.size > 0) {
    const shorter = tokensA.size <= tokensB.size ? tokensA : tokensB;
    const longer = tokensA.size <= tokensB.size ? tokensB : tokensA;
    if ([...shorter].every((token) => longer.has(token))) {
      return 0.6 * (shorter.size / longer.size) + 0.35;
    }
  }

  return overlap * 0.5;
}

/** The best match of a list of titles. */
export function bestTitle(candidates: (string | null | undefined)[], target: string): number {
  let best = 0;
  for (const candidate of candidates) {
    if (!candidate) continue;
    best = Math.max(best, titleScore(candidate, target));
  }
  return best;
}
