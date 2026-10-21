# animeplayer — the desktop sample

A Tauri application that drives the library. It has two halves:

- **The catalog** — what is airing, the posters, the descriptions and the scores. The
  metadata comes from the [AniList](https://anilist.co) GraphQL API.
- **The player** — the episodes and the streams. Everything here comes from the
  library: the search, the episode list, the resolution, the subtitles, the intro
  skip and the watch history.

The two are joined by the title: the catalog knows an anime by its AniList title, the
library knows it by the slug of the upstream site, and the application compares the
titles to find the match.

## How it is put together

The library is written in Kotlin, so the desktop shell cannot call it directly. It runs
it as a **sidecar** process and talks to it over HTTP:

```
┌──────────────────────────────────────────────────────────┐
│  Tauri (Rust)                                            │
│  ├── spawns the sidecar on a free port                   │
│  ├── waits for /api/health                               │
│  └── tells the frontend where to find it                 │
├──────────────────────────────────────────────────────────┤
│  The frontend — Vite, React, Tailwind, shadcn/ui         │
│  ├── catalog   → AniList                                 │
│  └── player    → http://127.0.0.1:<port>                 │
├──────────────────────────────────────────────────────────┤
│  The sidecar — samples/server (Kotlin)                   │
│  ├── /api/search, /api/episodes, /api/anime, /api/schedule│
│  ├── /api/history — the SQLDelight watch history         │
│  ├── /api/resolve — the episode becomes a playlist       │
│  └── /hls/{token} — the playlist, the segments, the subs │
└──────────────────────────────────────────────────────────┘
```

The `/hls` route is the reason the library is used as a sidecar and not as a native
module: the CDN of the upstream site answers `403 Forbidden` when the `Referer` header
is missing, and a browser is not allowed to send that header. The proxy of the library
sends it and rewrites every URI of the playlist back to itself.

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | The sidecar is a JVM process. |
| Rust | stable | `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \| sh` |
| Node.js | 20 or newer | The frontend. |
| webkit2gtk | 4.1 | `sudo pacman -S webkit2gtk-4.1` |
| GStreamer | `gst-plugins-good`, `gst-libav` | **Without them the webview crashes**: `autoaudiosink` and the H.264/AAC decoders live there. `sudo pacman -S gst-plugins-good gst-libav` |

## Running it

```bash
cd samples/tauri
npm install
npm run app          # builds the sidecar and starts the desktop application
```

`npm run app` does two things: it builds the Kotlin server
(`./gradlew :samples:server:installDist`) and installs it as the sidecar, then it
starts `tauri dev`.

| Script | What it does |
|---|---|
| `npm run app` | The sidecar build and the desktop application. |
| `npm run app:build` | The same, but a release bundle. |
| `npm run sidecar:build` | Only builds the Kotlin sidecar. |
| `npm run dev` | Only the frontend, in a browser. |

### The frontend in a browser

The frontend runs without Tauri as well — it then talks to a server you started
yourself:

```bash
./gradlew :samples:server:installDist
./samples/server/build/install/server/bin/server   # listens on 127.0.0.1:8080
cd samples/tauri && npm run dev
```

The address of the server can be changed with `VITE_LIBRARY_URL`.

## The design

The interface follows the design language of
[hayase-app/interface](https://github.com/hayase-app/interface) — the idea is ported,
the code is not: the components are React and shadcn/ui.

**The themes.** A theme is a class on the root element, and every theme redefines the
same semantic tokens (`--background`, `--muted`, `--primary`, `--ring`, …). The tokens
are mapped to the Tailwind colours with `@theme inline`, so `bg-primary/40` keeps
working in every theme.

| Theme | |
|---|---|
| `default` | A warm amber on black. |
| `light` | The same, on white. |
| `amber`, `lavender` | Warm and cool. |
| `catppuccin`, `dracula` | The two classics. |
| `custom` | The accent colour of the user drives the whole palette. |
| `system` | The accent colour of the desktop, mixed into the palette. |

The picker is in the settings — the gear at the bottom of the rail. The accent colour
expands into a full scale (`custom-50` … `custom-950`) with the relative colour syntax,
so any colour the user picks works everywhere.

**The layout.** The home page is a banner with the series that are worth watching now,
and rows of posters below it — the same way a television guide does it. The catalog
with the filters lives on its own page. The detail page of a series shows the banner,
the metadata, the genres and the episodes; the episodes of the library are enriched
with the thumbnails and the descriptions of the catalog.

**The motion.** The posters rise into place, every clickable thing answers the press
with a small scale, the navigation fades, and `prefers-reduced-motion` switches all of
it off.

**The type.** Nunito Variable for the interface, Geist Mono for the numbers — both
bundled, so the application does not need the network for its fonts.

## The endpoints of the sidecar

| Route | What it answers |
|---|---|
| `GET /api/health` | Whether the server is up. |
| `GET /api/search?q=` | The search of the upstream site. |
| `GET /api/anime?slug=` | The metadata the library knows about a series. |
| `GET /api/episodes?slug=` | The episodes of a series. |
| `GET /api/schedule?q=` | The broadcast schedule — a different site than the streams. |
| `POST /api/resolve` | Resolves an episode into a playlist. |
| `GET /api/history` | The watch history. |
| `POST /api/history` | Remembers the position of an episode. |
| `DELETE /api/history` | Forgets an episode. |
| `GET /hls/{token}` | The playlist, the segments and the subtitles. |

## Notes

- The sidecar runs on a port the operating system picks. The frontend learns the
  address over the Tauri IPC.
- The upstream site blocks clients that ask for too many segments at once, so the
  sample throttles the requests per host and retries a blocked request.
- The watch history lives in `~/.local/state/animeplayer-desktop/history.db`. The
  directory can be changed with `ANIMEPLAYER_STATE_DIR`.

## The bundles

```bash
npm run app:build -- --bundles deb,appimage
```

The bundles carry the library with them: the Kotlin server is built, a small runtime
image of the JVM is linked for it (`jlink`), and both are shipped in the resources of
the application. An installed copy therefore needs **no JVM on the machine**.

| Bundle | Size | Notes |
|---|---|---|
| `animeplayer_0.1.0_amd64.deb` | 80 MB | The installer for Debian and Ubuntu. It declares the GStreamer packages the webview needs. |
| `animeplayer_0.1.0_amd64.AppImage` | 185 MB | Runs everywhere. It does not carry the GStreamer plugins — see below. |

Both are also built by the `tauri linux` workflow on GitHub, whenever the desktop
sample, the server or one of the library modules change.

### The GStreamer plugins

The webview plays the episodes with the media framework of the desktop. On a machine
without it the webview crashes instead of the application:

```bash
sudo pacman -S gst-plugins-good gst-libav      # Arch
sudo apt install gstreamer1.0-plugins-good gstreamer1.0-libav   # Debian
```

The `.deb` declares the packages, so `apt install` pulls them in. The AppImage cannot
carry them — the plugins of the build machine are not the plugins of the machine that
runs it.

