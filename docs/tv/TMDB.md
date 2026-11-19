<!--
  Copyright (C) 2026 animeplayer contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->

# TMDB on Android TV

Paste your **API Read Access Token** from the [TMDB API settings](https://www.themoviedb.org/settings/api) into a file named `.env` at the repository root.

```dotenv
TMDB_READ_ACCESS_TOKEN=
```

You can also set the variable in the shell, which takes precedence over the file. Rebuild and install with:

```sh
./gradlew :samples:android-tv:installDebug
```

The build reads the token locally and compiles it into the APK. Anyone with that APK can extract the token. Keep builds with a personal token private and leave the value empty for builds you distribute. Git ignores `.env`.

AniList supplies the catalog, filters, and stable IDs. TMDB can replace posters and backdrops, including the art in the Home screensaver. On the series page, focusing an episode displays its synopsis when available. Its title, still, and runtime also appear in the episode picker when present. The app loads artwork for visible titles in the background, while AniList results appear immediately.

TV metadata is matched by title and release year. Explicitly named sequels also require a corresponding TMDB season. Split cours and later arcs can use different numbering, so the app uses AniList metadata wherever it cannot establish a match. A blank token leaves AniList active.

This product uses the TMDB API but is not endorsed or certified by TMDB. The official logo in Settings comes from [TMDB's logos and attribution page](https://www.themoviedb.org/about/logos-attribution).
