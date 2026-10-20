import { QueryClient } from "@tanstack/react-query";
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client";
import { createSyncStoragePersister } from "@tanstack/query-sync-storage-persister";
import { BrowserRouter, Route, Routes } from "react-router-dom";

import { AppShell } from "@/components/AppShell";
import { BackendGate } from "@/components/BackendGate";
import { Airing } from "@/routes/Airing";
import { AnimeDetail } from "@/routes/AnimeDetail";
import { Catalog } from "@/routes/Catalog";
import { Library } from "@/routes/Library";
import { Series } from "@/routes/Series";
import { Watch } from "@/routes/Watch";
import { LibraryProvider } from "@/lib/library";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // The catalog allows thirty requests per minute, so the answers are kept.
      staleTime: 5 * 60 * 1000,
      gcTime: 24 * 60 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

const persister = createSyncStoragePersister({
  storage: window.localStorage,
  key: "animeplayer-catalog",
});

export default function App() {
  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{ persister, maxAge: 24 * 60 * 60 * 1000 }}
    >
      <LibraryProvider>
        <BackendGate>
          <BrowserRouter>
            <Routes>
              <Route element={<AppShell />}>
                <Route index element={<Catalog />} />
                <Route path="anime/:animeId" element={<AnimeDetail />} />
                <Route path="series/:slug" element={<Series />} />
                <Route path="watch/:animeId/:episode" element={<Watch />} />
                <Route path="airing" element={<Airing />} />
                <Route path="library" element={<Library />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </BackendGate>
      </LibraryProvider>
    </PersistQueryClientProvider>
  );
}
