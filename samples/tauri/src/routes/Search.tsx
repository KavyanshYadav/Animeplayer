import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { Eraser, Shuffle } from "lucide-react";

import { PosterCard } from "@/components/PosterCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  FORMATS,
  GENRES,
  SORTS,
  STATUSES,
  searchAnime,
} from "@/lib/anilist";

const YEARS = Array.from({ length: 26 }, (_, index) => new Date().getFullYear() - index);
const SEASONS = [
  { value: "WINTER", label: "Winter" },
  { value: "SPRING", label: "Spring" },
  { value: "SUMMER", label: "Summer" },
  { value: "FALL", label: "Fall" },
];

/**
 * The catalog with the filters.
 *
 * The filters live in the address of the window, so a link to a filtered catalog can
 * be shared and the back button works.
 */
export function Search() {
  const [params, setParams] = useSearchParams();

  const title = params.get("title") ?? "";
  const genre = params.get("genre") ?? "";
  const year = params.get("year") ?? "";
  const season = params.get("season") ?? "";
  const format = params.get("format") ?? "";
  const status = params.get("status") ?? "";
  const sort = params.get("sort") ?? "TRENDING_DESC";

  const update = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };

  const query = useQuery({
    queryKey: ["search", title, genre, year, season, format, status, sort],
    queryFn: () =>
      searchAnime({
        search: title || undefined,
        genre: genre || undefined,
        seasonYear: year ? Number(year) : undefined,
        season: season || undefined,
        format: format || undefined,
        status: status || undefined,
        sort,
        perPage: 48,
      }),
    staleTime: 10 * 60 * 1000,
  });

  const chips = useMemo(
    () =>
      [
        { key: "title", label: title },
        { key: "genre", label: genre },
        { key: "year", label: year },
        { key: "season", label: season },
        { key: "format", label: format },
        { key: "status", label: status },
      ].filter((chip) => chip.label),
    [title, genre, year, season, format, status],
  );

  return (
    <div className="space-y-6 p-6 lg:p-10">
      <div className="grid gap-4 lg:grid-cols-[2fr_1fr_1fr_1fr]">
        <Filter label="Title">
          <Input
            value={title}
            onChange={(event) => update("title", event.target.value)}
            placeholder="Any"
            className="h-10"
          />
        </Filter>

        <Filter label="Genres">
          <Select value={genre} onValueChange={(value) => update("genre", value)}>
            <SelectTrigger className="h-10">
              <SelectValue placeholder="Any" />
            </SelectTrigger>
            <SelectContent>
              {GENRES.map((item) => (
                <SelectItem key={item} value={item}>
                  {item}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Filter>

        <Filter label="Year">
          <Select value={year} onValueChange={(value) => update("year", value)}>
            <SelectTrigger className="h-10">
              <SelectValue placeholder="Any" />
            </SelectTrigger>
            <SelectContent>
              {YEARS.map((item) => (
                <SelectItem key={item} value={String(item)}>
                  {item}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Filter>

        <Filter label="Season">
          <Select value={season} onValueChange={(value) => update("season", value)}>
            <SelectTrigger className="h-10">
              <SelectValue placeholder="Any" />
            </SelectTrigger>
            <SelectContent>
              {SEASONS.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Filter>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_1fr_1fr_auto]">
        <Filter label="Formats">
          <Select value={format} onValueChange={(value) => update("format", value)}>
            <SelectTrigger className="h-10">
              <SelectValue placeholder="Any" />
            </SelectTrigger>
            <SelectContent>
              {FORMATS.map((item) => (
                <SelectItem key={item} value={item}>
                  {item.replace("_", " ")}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Filter>

        <Filter label="Status">
          <Select value={status} onValueChange={(value) => update("status", value)}>
            <SelectTrigger className="h-10">
              <SelectValue placeholder="Any" />
            </SelectTrigger>
            <SelectContent>
              {STATUSES.map((item) => (
                <SelectItem key={item} value={item}>
                  {item.replace(/_/g, " ")}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Filter>

        <Filter label="Sort">
          <Select value={sort} onValueChange={(value) => update("sort", value)}>
            <SelectTrigger className="h-10">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {SORTS.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Filter>

        <div className="flex items-end gap-2">
          <Button
            variant="outline"
            size="icon"
            title="A random anime"
            onClick={() => {
              const media = query.data?.media;
              if (!media?.length) return;
              const pick = media[Math.floor(Math.random() * media.length)];
              window.location.assign(`/anime/${pick.id}`);
            }}
          >
            <Shuffle className="size-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            title="Clear the filters"
            onClick={() => setParams(new URLSearchParams(), { replace: true })}
          >
            <Eraser className="size-4" />
          </Button>
        </div>
      </div>

      {chips.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {chips.map((chip) => (
            <button
              key={chip.key}
              type="button"
              onClick={() => update(chip.key, "")}
              className="rounded-md bg-accent px-2.5 py-1 text-xs font-medium text-accent-foreground transition-colors hover:bg-primary hover:text-primary-foreground"
            >
              {chip.label} ✕
            </button>
          ))}
        </div>
      )}

      {query.isLoading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8">
          {Array.from({ length: 16 }).map((_, index) => (
            <div key={index} className="space-y-2">
              <Skeleton className="aspect-[2/3] w-full rounded-md" />
              <Skeleton className="h-3 w-3/4" />
            </div>
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8">
          {query.data?.media.map((anime, index) => (
            <div key={anime.id} className="animate-load-in">
              <PosterCard anime={anime} index={index} className="w-full" />
            </div>
          ))}
        </div>
      )}

      {!query.isLoading && query.data?.media.length === 0 && (
        <p className="rounded-xl border border-dashed border-border/60 p-12 text-center text-sm text-muted-foreground">
          The catalog does not know this.
        </p>
      )}
    </div>
  );
}

/** One filter with a label above it. */
function Filter({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="space-y-1.5">
      <span className="block text-sm font-bold">{label}</span>
      {children}
    </label>
  );
}
