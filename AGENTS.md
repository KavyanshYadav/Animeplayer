# AGENTS.md — animeplayer

**Read this file before you touch the repository.** It is the canonical guide for
AI agents and new contributors.

`animeplayer` is a Kotlin Multiplatform library that resolves and plays anime streams.
It is a Kotlin port of the scraping logic of [`ani-cli`](https://github.com/pystardust/ani-cli),
rebuilt with a pluggable network layer, typed errors and per-platform playback engines.

---

## 1. Requirements

| Requirement | Version | Notes |
|---|---|---|
| JDK | **21** | `sudo pacman -S jdk21-openjdk`. The Gradle build sets `jvmToolchain(21)`. |
| Gradle | **9.8.2** | Use the wrapper: `./gradlew`. Do not install Gradle system wide. |
| Android SDK | platform **37** (`android-37.0`), build-tools 36.0.0 | Point at it with `local.properties`: `sdk.dir=$HOME/Android/Sdk`. |
| Node.js | auto-provisioned | The Kotlin/JS toolchain downloads its own Node from `nodejs.org/dist`. |

### Toolchain (pinned in `gradle/libs.versions.toml`)

| Tool | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.4.1 |
| Ktor | 3.6.0 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization | 1.11.0 |
| kotlinx-html | 0.12.0 (declared, currently unused) |
| SQLDelight | 2.4.1 |
| Media3 | 1.11.1 |
| hls.js | 1.7.3 (loaded by the host page, not bundled) |

> **Kotlin is pinned to 2.2.10 on purpose.** AGP 9.4.1 bundles Kotlin 2.2.10 for its
> built-in Kotlin support. A newer compiler produces metadata that the Android application
> module cannot read. Do not bump `kotlin` in the version catalog without bumping AGP too.

---

## 2. Commands

```bash
./gradlew build                       # compile every target + run every test
./gradlew jvmTest                     # the JVM tests only (fastest loop)
./gradlew :core:jvmTest               # one module
./gradlew liveTest -Plive=true        # contract tests against the live upstream site
./gradlew :samples:cli:installDist    # build the CLI
./gradlew :samples:android:assembleDebug
./gradlew publishToMavenLocal         # publish every library to ~/.m2
```

Run a single test class:

```bash
./gradlew :sources:hianime:jvmTest --tests "*HianimeSourceTest*"
```

---

## 3. Architecture

Ports and adapters. The dependency arrow only ever points **inwards**.

```
                      ┌───────────────────────────────────────┐
                      │            animeplayer-core           │
                      │  model · spi · error · util           │
                      │  NO network, NO platform APIs         │
                      └───────┬───────────────────────┬───────┘
                              │                       │
        ┌─────────────────────┼─────────────┬─────────┴──────────┐
        │                     │             │                    │
 ┌──────▼───────┐   ┌─────────▼──────┐  ┌───▼──────────┐  ┌──────▼────────┐
 │   network    │   │    storage    │  │    player    │  │ sources/hianime│
 │ Ktor + retry │   │  SQLDelight   │  │ Media3/mpv   │  │  sources/      │
 │ + cache      │   │  + in-memory  │  │  /hls.js     │  │  schedule      │
 └──────────────┘   └────────────────┘  └──────────────┘  └────────────────┘
        ▲
 ┌──────┴──────────────────┐
 │     animeplayer-proxy   │  Ktor server: resolve + HLS proxy
 └─────────────────────────┘
```

### The dependency rules (enforced by convention, not by tooling)

| Module | May depend on | Must **not** depend on |
|---|---|---|
| `core` | *(nothing but the Kotlin stdlib and coroutines)* | any HTTP, database, platform or player library |
| `network` | `core` | `storage`, `player`, `sources/*` |
| `storage` | `core` | `network`, `player`, `sources/*` |
| `player` | `core` | `network`, `storage`, `sources/*` |
| `sources/*` | `core` | `network`, `storage`, `player` — **a source only speaks through the `HttpTransport` port** |
| `proxy` | `core`, `network`, `sources/*` | `player`, `storage` |

**`core` is the contract.** If you need something in `core`, it has to be a *port*
(an interface) or a *model* (a data class). Never a concrete implementation.

---

## 4. The ports (`core/src/commonMain/kotlin/dev/animeplayer/core/spi/`)

```kotlin
// The network boundary. The only thing the library knows about HTTP.
interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

// One per upstream site. Everything the rest of the library does goes through this.
interface AnimeSource {
    val id: String
    val displayName: String
    suspend fun search(query: String): List<Anime>
    suspend fun anime(id: AnimeId): Anime
    suspend fun episodes(id: AnimeId): EpisodeList
    suspend fun resolve(id: AnimeId, episode: Episode,
                        audioTrack: AudioTrack = AudioTrack.Sub,
                        quality: QualityPreference = QualityPreference.Best): PlayableSource
}

// Broadcast schedules come from a different site than the streams.
interface ScheduleSource {
    val id: String
    val displayName: String
    suspend fun schedule(query: String): List<ScheduleEntry>
}

// Playback. One implementation per platform.
interface PlayerEngine {
    val id: String
    val isAvailable: Boolean
    suspend fun prepare(request: PlaybackRequest): PlaybackSession
}

interface PlaybackSession {
    val state: StateFlow<PlaybackState>
    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(positionMs: Long)
    suspend fun selectSubtitle(track: SubtitleTrack?)
    fun close()
}

// Persistence, caching, logging and metrics.
interface WatchHistoryStore { /* entries / entry / upsert / remove / clear */ }
interface Cache { /* get / put / invalidate / clear */ }
interface Logger { /* log(level, message, throwable) */ }
interface MetricsSink { /* recordRequest / recordCacheHit / recordCacheMiss */ }
```

---

## 5. The model (`core/src/commonMain/kotlin/dev/animeplayer/core/model/`)

| Type | Purpose |
|---|---|
| `AnimeId(slug, numericId)` | Identifies a series. `slug` = `one-piece-1`, `numericId` = `1`. The numeric id is what the upstream episode API is keyed by. |
| `Anime` | Metadata: title, native title, poster, description, genres, year, status. |
| `Episode` | One episode. `number` is the upstream spelling (`"12.5"` for specials), `index` is the position in the list. |
| `EpisodeList` | The episodes of a series plus `totalReported` (the upstream's, usually wrong, count). |
| `StreamVariant` | A rendition: `quality`, `url`, `bandwidth`, `codecs`, `frameRate`. |
| `VideoQuality(width, height, label)` | `label` is `"1080p"`. `height == 0` means "unknown". |
| `SubtitleTrack(language, label, url, isDefault)` | A WebVTT track. |
| `SkipSegments(intro, outro)` | Intro/outro ranges. Comes from the player configuration. |
| `TimeRange(startSeconds, endSeconds)` | A range in whole seconds. |
| `PlayableSource` | **The central type.** See below. |
| `Anime` `AudioTrack` | `Sub` or `Dub`. |
| `QualityPreference` | `Best`, `Worst`, `Height(n)`, `Label(regex)`. |
| `ScheduleEntry` | One airing schedule entry. |

### `PlayableSource` — the type that matters most

```kotlin
data class PlayableSource(
    val variant: StreamVariant,               // the selected rendition
    val subtitles: List<SubtitleTrack> = emptyList(),
    val referer: String,                      // REQUIRED by the upstream CDN
    val skip: SkipSegments? = null,           // intro / outro, free of charge
    val downloadUrl: String? = null,
    val serverName: String? = null,
    val audioTrack: AudioTrack = AudioTrack.Sub,
    val masterUrl: String? = null,            // the master playlist, for ABR
    val variants: List<StreamVariant> = listOf(variant),
)
```

> **The `referer` is not optional decoration.** The streaming CDN answers `403 Forbidden`
> when it is missing — for the master playlist *and* for the subtitle file. Every player
> must apply it to every request it makes. See `docs/PROTOCOL.md` §5.

---

## 6. Errors (`core/.../error/AnimeException.kt`)

A sealed hierarchy. Catch the subtype you can handle; the rest are bugs.

```
AnimeException
├── NetworkError(url, cause)            the request never reached the server
├── HttpStatusError(url, status, body)  a 4xx/5xx answer
├── CloudflareChallengeError(url)       an interstitial; retry with another transport
├── ParseError(reason, snippet)         the upstream markup changed
├── NotFoundError(what)                 the resource does not exist
├── NoSourcesError(animeId, episode)    no server could be resolved
├── EpisodeNotReleasedError(...)        the episode does not exist yet
└── QualityNotAvailableError(requested, available)
```

---

## 7. Common recipes

### Use the library

```kotlin
val client = createDefaultHttpClient()                     // network module
val transport = KtorHttpTransport(client, TransportConfig())
val source = HianimeSource(transport)                       // sources/hianime

val anime = source.search("one piece").first()
val episode = source.episodes(anime.id).episodes.first()
val playable = source.resolve(anime.id, episode)

val session = MpvPlayerEngine().prepare(PlaybackRequest(playable, "Episode 1"))
session.play()
```

### Bring your own HTTP client

Implement `HttpTransport` — five lines — and the whole library works with it.
`RecordingHttpTransport` (in the `network` module) answers from canned payloads and
records every request; use it in tests.

### Add a new upstream site

1. Create `sources/<name>/` with a `build.gradle.kts` copied from `sources/hianime`.
2. Implement `AnimeSource`. Speak only through `HttpTransport`.
3. Add the module to `settings.gradle.kts`.
4. Add golden fixtures under `fixtures/` and a fixture test.

Do **not** touch `core`, `network` or `player`.

### Add a new playback backend

Implement `PlayerEngine` + `PlaybackSession` in `player/src/<platform>Main/`.
Do **not** touch `core`.

---

## 8. The upstream protocol

The full reverse-engineered specification lives in **[`docs/PROTOCOL.md`](docs/PROTOCOL.md)**.
Read it before you touch `sources/hianime`.

Short version — five requests:

```
1. GET hianime.at/search?keyword={q}                 → HTML
2. GET hianime.at/api/theme/episode/list/{numericId} → JSON {status,totalItems,html}
3. GET hianime.at/api/theme/episode/servers?episodeId=…  → JSON {status,html}
4. GET {player page}                                  → window.__P="{base64}"
5. GET {master.m3u8}   with Referer: {embed origin}   → HLS
```

Step 4 is decoded with a repeating XOR key:

```kotlin
val KEY = "otaku-embed-v1".encodeToByteArray()
out[i] = data[i] xor KEY[i % KEY.size]
```

---

## 9. The `Referer` problem (read this before writing a player)

The upstream CDN requires a `Referer` header. A browser is **not allowed** to set it —
`Referer` is a forbidden header name for `fetch` and `XMLHttpRequest`, and the CDN
answers `403`.

| Platform | How to set the header |
|---|---|
| Android (Media3) | `DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Referer" to referer))`. One factory for media **and** subtitles. `MediaItem.setHttpHeaders` does **not** cover subtitles. |
| Desktop (mpv) | `--referrer={referer}` on the command line, `--sub-file={url}` for the subtitles. |
| Browser (hls.js) | **Impossible.** Use `animeplayer-proxy`. |

The proxy fetches the playlist and the segments with the correct header and rewrites
every URI back to itself:

```
browser ──▶ POST /api/resolve ──▶ { playlistUrl: "…/hls/<token>" }
         ──▶ GET  /hls/<token>   ──▶ the master playlist, URIs rewritten
         ──▶ GET  /hls/<token>   ──▶ the variant playlist, URIs rewritten
         ──▶ GET  /hls/<token>   ──▶ the media segments
```

Tokens are opaque and expire (`ProxyConfig.targetTtl`).

---

## 10. Storage schema

SQLDelight, one table. The `.sq` file lives in
`storage/src/commonMain/sqldelight/dev/animeplayer/storage/db/WatchHistory.sq`.

```sql
CREATE TABLE watch_history (
    anime_id       TEXT    NOT NULL,   -- the slug
    slug           TEXT    NOT NULL,
    numeric_id     TEXT    NOT NULL,
    title          TEXT    NOT NULL,
    episode_number TEXT    NOT NULL,
    updated_at     INTEGER NOT NULL,   -- epoch millis
    position_ms    INTEGER NOT NULL DEFAULT 0,
    duration_ms    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (anime_id, episode_number)
);
```

Queries: `selectAll`, `selectByEpisode`, `selectInProgress` (started but under 90 %),
`upsert`, `deleteByEpisode`, `deleteAll`.

`InMemoryWatchHistoryStore` (in `core`) is the test and fallback implementation.

---

## 11. Testing

| Kind | Where | How |
|---|---|---|
| Unit | `*/src/commonTest` | Pure logic, no I/O. |
| Fixture | `*/src/jvmTest` | Runs against the golden payloads in `fixtures/`. |
| Live contract | `sources/hianime/src/jvmTest/LiveContractTest.kt` | Skipped unless `-Plive=true`. |

The `fixtures/` directory holds payloads captured from the live site on 2026-10-21:
`search.html`, `episodes.json`, `servers.json`, `embed.html`, `master.m3u8`,
`variant.m3u8`, `schedule.json`.

**When the upstream changes, the fixture tests fail first.** That is the point. Update
the fixture and the parser together — never weaken the assertion to make it pass.

---

## 12. Conventions

- **Every file starts with the GPL header.** The library is GPL-3.0-or-later.
- **`explicitApi()` is on.** Every public declaration needs an explicit visibility
  modifier and an explicit return type. The build fails otherwise.
- **No `println`.** Route everything through the `Logger` port.
- **No platform APIs in `commonMain`.** Use `expect`/`actual` only in the platform
  source sets, and only for genuine platform differences.
- **No `java.*` in `commonMain`.** `kotlin.time.Instant` and `kotlin.time.Clock` replace
  `kotlinx.datetime`. `core` has its own Base64 codec (`util/Base64.kt`) to avoid the
  experimental `kotlin.io.encoding` API.
- **No lookbehind in regular expressions.** Common Kotlin regex does not support it on
  JS and Native.
- **Parsers match on attributes, not CSS classes.** The upstream markup does not
  reliably carry the expected class on every element — the very first episode anchor
  had no `ep-item` class at all.

---

## 13. Legal

GPL-3.0-or-later. An application that links this library is derived work and inherits
the GPL-3.0. The library resolves links to third-party content and ships none of it
itself. See `NOTICE` and `LICENSE`.

---

## 14. Where things are

```
core/                    the model, the ports, the errors, the pure utilities
network/                 Ktor transport, retry, cache, rate limit, circuit breaker
storage/                 SQLDelight schema and the watch history store
player/                  Media3 (android), mpv (jvm), hls.js (js)
sources/hianime/         the hianime.at source
sources/schedule/        the animeschedule.net source
proxy/                   the Ktor proxy
samples/cli/             the terminal client
samples/server/          the web backend
samples/android/         the Android application
fixtures/                golden payloads captured from the live site
docs/PROTOCOL.md         the reverse-engineered upstream specification
docs/ARCHITECTURE.md     the design, in detail
```
