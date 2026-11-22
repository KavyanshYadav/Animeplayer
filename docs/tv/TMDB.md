<!--
  Copyright (C) 2026 animeplayer contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->

# TMDB on Android TV

On the TV, open **Settings → TMDB** and enter your **API Read Access Token**
or **v3 API key** from the [TMDB API settings](https://www.themoviedb.org/settings/api).
The app validates the credential and stores it on this TV. Remove the custom
credential from the same Settings menu to return to the build’s default.

For a private development build, you can also put your **API Read Access Token** from the [TMDB API settings](https://www.themoviedb.org/settings/api) into a file named `.env` at the repository root.

```dotenv
TMDB_READ_ACCESS_TOKEN=
```

You can also set the variable in the shell, which takes precedence over the file. Rebuild and install with:

```sh
./gradlew :samples:android-tv:installDebug
```

The build reads the token locally and compiles it into the APK. Anyone with that APK can extract the token. Keep builds with a personal token private and leave the value empty for builds you distribute. Git ignores `.env`.

AniList supplies the catalog, filters, and stable IDs. TMDB can replace posters and backdrops, including the art in the Home screensaver. The series page and the player’s episode picker use the title, still, runtime, and description when available. Unwatched episode details are hidden until revealed by default. The app loads artwork for visible titles in the background, while AniList results appear immediately.

TV metadata is matched by title and release year. Explicitly named sequels also require a corresponding TMDB season. Split cours and later arcs can use different numbering, so the app uses AniList metadata wherever it cannot establish a match. A blank token leaves AniList active.

This product uses the TMDB API but is not endorsed or certified by TMDB. The official logo in Settings comes from [TMDB's logos and attribution page](https://www.themoviedb.org/about/logos-attribution).
