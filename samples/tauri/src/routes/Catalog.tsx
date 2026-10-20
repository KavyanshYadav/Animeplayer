import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, X } from "lucide-react";

import { AnimeGrid } from "@/components/AnimeGrid";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useDebounce } from "@/hooks/useDebounce";
import { catalog, searchAnime, type CatalogSection } from "@/lib/anilist";

const SECTIONS: { value: CatalogSection; label: string }[] = [
  { value: "trending", label: "Trending" },
  { value: "popular", label: "Popular" },
  { value: "seasonal", label: "This season" },
  { value: "upcoming", label: "Upcoming" },
];

/**
 * The catalog.
 *
 * The metadata comes from AniList — it knows the posters, the descriptions and the
 * scores. The streams come from the library.
 */
export function Catalog() {
  const [section, setSection] = useState<CatalogSection>("trending");
  const [search, setSearch] = useState("");
  const query = useDebounce(search.trim(), 400);

  const listing = useQuery({
    queryKey: ["catalog", section],
    queryFn: () => catalog(section),
    staleTime: 5 * 60 * 1000,
  });

  const results = useQuery({
    queryKey: ["catalog-search", query],
    queryFn: () => searchAnime(query),
    enabled: query.length > 1,
    staleTime: 10 * 60 * 1000,
  });

  const isSearching = query.length > 1;

  return (
    <div className="space-y-6 p-6 lg:p-10">
      <header className="space-y-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Catalog</h1>
          <p className="text-sm text-muted-foreground">
            Browse what is airing. Every entry can be played through the library.
          </p>
        </div>

        <div className="relative max-w-xl">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search the catalog…"
            className="h-10 pl-9 pr-9"
          />
          {search && (
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setSearch("")}
              className="absolute right-1 top-1/2 size-8 -translate-y-1/2"
            >
              <X className="size-4" />
            </Button>
          )}
        </div>

        {!isSearching && (
          <Tabs value={section} onValueChange={(value) => setSection(value as CatalogSection)}>
            <TabsList>
              {SECTIONS.map((item) => (
                <TabsTrigger key={item.value} value={item.value}>
                  {item.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        )}
      </header>

      {isSearching ? (
        <AnimeGrid
          anime={results.data?.media}
          isLoading={results.isLoading}
          emptyMessage="The catalog does not know this title."
        />
      ) : (
        <AnimeGrid
          anime={listing.data?.media}
          isLoading={listing.isLoading}
          emptyMessage="The catalog is empty."
        />
      )}
    </div>
  );
}
