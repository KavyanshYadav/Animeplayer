/** Small helpers for the user interface. */

/** `95` becomes `1:35`, `3725` becomes `1:02:05`. */
export function formatDuration(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return "0:00";
  const seconds = Math.floor(totalSeconds % 60);
  const minutes = Math.floor((totalSeconds / 60) % 60);
  const hours = Math.floor(totalSeconds / 3600);
  const pad = (value: number) => value.toString().padStart(2, "0");
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`;
}

/** The score of the catalog is a number between zero and a hundred. */
export function formatScore(score: number | null): string {
  if (score === null || score === undefined) return "—";
  return `${Math.round(score)}`;
}

/** `3 hours ago`, `yesterday`. */
export function timeAgo(timestamp: number): string {
  const seconds = Math.floor((Date.now() - timestamp) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? "" : "s"} ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"} ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return "yesterday";
  if (days < 30) return `${days} days ago`;
  const months = Math.floor(days / 30);
  return `${months} month${months === 1 ? "" : "s"} ago`;
}

/** The description of the catalog is plain text with the occasional line break. */
export function plainText(html: string | null): string {
  if (!html) return "";
  return html
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&quot;/g, '"')
    .replace(/&amp;/g, "&")
    .replace(/&#039;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .trim();
}

/** The title of an anime, in the language that is available. */
export function displayTitle(title: {
  romaji: string | null;
  english: string | null;
  native: string | null;
}): string {
  return title.english ?? title.romaji ?? title.native ?? "Unknown";
}

/** The second title of an anime, if there is one. */
export function secondaryTitle(title: {
  romaji: string | null;
  english: string | null;
  native: string | null;
}): string | null {
  const primary = displayTitle(title);
  const others = [title.romaji, title.english, title.native].filter(
    (value): value is string => Boolean(value) && value !== primary,
  );
  return others[0] ?? null;
}
