# animeplayer

A Kotlin Multiplatform library for resolving and playing anime streams.

It is a Kotlin port of the scraping logic of [`ani-cli`](https://github.com/pystardust/ani-cli),
rebuilt as a production-grade library: the network client is pluggable, the upstream
sites are behind a source interface, the failures are typed, and the result runs on
Android, on the desktop, on a server and in the browser.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  animeplayer-core        models · ports · parsers · no I/O, no platform   │
└───────────────┬──────────────────────────────────────────────────────────┘
                │
   ┌────────────┼──────────────┬─────────────────┬────────────────────┐
   │            │              │                 │                    │
┌──▼───────┐ ┌──▼────────┐ ┌──▼──────────┐ ┌────▼───────────┐ ┌──────▼────────┐
│ network  │ │  storage  │ │   player    │ │ sources/hianime│ │ sources/       │
│ Ktor     │ │ SQLDelight│ │ Media3/mpv  │ │                │ │ schedule       │
│ +retry   │ │ +memory   │ │ /hls.js     │ │                │ │                │
└──────────┘ └───────────┘ └─────────────┘ └────────────────┘ └───────────────┘
```

---

## Modules

| Module | Targets | What it does |
|---|---|---|
| `animeplayer-core` | jvm, android, js | The domain model and every port (interface). No network, no platform APIs. |
| `animeplayer-network` | jvm, android, js | `KtorHttpTransport` plus retries, caching, rate limiting and a circuit breaker. |
| `animeplayer-storage` | jvm, android | The watch history, backed by SQLite through SQLDelight. |
| `animeplayer-player` | jvm, android, js | Media3 (Android), mpv (desktop) and hls.js (browser). |
| `animeplayer-sources-hianime` | jvm, android, js | The `hianime.at` source. |
| `animeplayer-sources-schedule` | jvm, android, js | The `animeschedule.net` schedule source. |
| `animeplayer-sources-anilist` | jvm, android, js | The `anilist.co` catalogue — artwork, descriptions, scores. Metadata, never a stream. |
| `animeplayer-proxy` | jvm | A Ktor server that proxies the playlists, the segments and the subtitles. |

Samples live in `samples/`: a command line client, a **Swing desktop GUI** that drives
`mpv`, a web backend, an Android phone app and an **Android TV app**.

---

## Quick start

### Desktop (JVM)

```kotlin
import dev.animeplayer.core.model.*
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.network.*
import dev.animeplayer.player.mpv.MpvPlayerEngine
import dev.animeplayer.sources.hianime.*

suspend fun main() {
    val client = createDefaultHttpClient()
    val transport = KtorHttpTransport(client, TransportConfig())
    val source = HianimeSource(transport)

    val anime = source.search("one piece").first()
    val episode = source.episodes(anime.id).episodes.first()
    val playable = source.resolve(anime.id, episode, AudioTrack.Sub, QualityPreference.Best)

    println(playable.variant.url)      // the HLS master playlist
    println(playable.referer)           // the Referer the CDN requires
    println(playable.skip?.intro)       // the intro segment, straight from the player config

    val session = MpvPlayerEngine().prepare(PlaybackRequest(playable, "One Piece Episode 1"))
    session.play()
}
```

### Android

```kotlin
val engine = Media3PlayerEngine(context)
val session = engine.prepare(PlaybackRequest(playable, "One Piece Episode 1"))
session.play()
```

### Bringing your own HTTP client

`core` never talks to the network directly. It only knows the `HttpTransport` port, so
any client can back it:

```kotlin
interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}
```

Implement it with OkHttp, with `java.net.http`, with `fetch`, or use the bundled
`KtorHttpTransport`. `RecordingHttpTransport` is included for tests — it answers from
canned payloads and records every request.

---

## How the upstream site is scraped

The whole flow is five requests. Every one of them was verified against the live site
before it was implemented.

```
1. GET https://hianime.at/search?keyword={query}
       → HTML. The results are <div class="film-detail"> blocks; each one contains
         an <a href="…/{slug}" title="{name}" data-jname="{native name}">.
         The document is cut at id="main-sidebar" first, because the sidebar repeats
         the ten most popular series.

2. GET https://hianime.at/api/theme/episode/list/{numericId}
       → {"status":true,"totalItems":12,"html":"…"}
         Each episode is an <a> with data-number, data-id and href.
         `totalItems` is wrong (it reports the size of one page, not the number of
         episodes), so the parsed count is authoritative.

3. GET https://hianime.at/api/theme/episode/servers?episodeId={data-id}
       → {"status":true,"html":"…"}
         Each server is a <div> with data-type (sub|dub), data-server-name and
         data-hash. The hash is the Base64 encoded URL of the player page.

4. GET {player page}
       → <script>window.__P="{base64}"</script>
         Decoding it and XORing every byte with the repeating key "otaku-embed-v1"
         yields a JSON document with the master playlist URL, the subtitle tracks and
         the intro/outro segments.

5. GET {master.m3u8}          with  Referer: https://zokoanime.video/
       → #EXT-X-STREAM-INF lines. The variant URIs are relative and have to be
         resolved against the master URL.
```

### Two things that will bite you

**The `Referer` header is mandatory.** The streaming CDN answers with `403 Forbidden`
when it is missing — for the master playlist *and* for the subtitle file. That is why
`PlayableSource` carries the `referer` next to the URL: every player has to apply it to
every request it makes. On Android the header goes on the `DefaultHttpDataSource.Factory`
(covering media and subtitles alike); for mpv it is `--referrer=`.

**A browser cannot set `Referer`.** It is a forbidden header name, so `fetch` and
`XMLHttpRequest` silently drop it, and the CDN answers with `403`. A browser client
therefore has to go through the proxy: the server fetches the playlist and the segments
with the correct header and rewrites the URLs back to itself.

```
browser ──▶ POST /api/resolve ──▶ {"playlistUrl": "…/hls/<token>"}
         ──▶ GET  /hls/<token>   ──▶ the master playlist, rewritten
         ──▶ GET  /hls/<token>   ──▶ the media playlist, rewritten
         ──▶ GET  /hls/<token>   ──▶ the segments
```

---

## What is different from `ani-cli`

The shell script is a fine tool, but a few things did not survive the move to a
library:

| `ani-cli` | `animeplayer` |
|---|---|
| Only the `ZokoAnime` server is understood | Every server is tried in turn until one resolves |
| The skip segments come from an external service | They are in the player configuration already |
| Regular expressions over the whole page | Structural parsing, matched on attributes rather than CSS classes |
| The schedule scraper no longer matches the live API | The JSON API is parsed directly |
| No retries, no caching, no rate limit | Retries with backoff, TTL cache, rate limiter, circuit breaker |
| Failures are printed strings | A sealed `AnimeException` hierarchy |
| `totalItems` is trusted | The parsed count wins |
| The history is an ad-hoc TSV file | A SQLDelight backed store with a migration path |

---

## The desktop GUI

`samples/gui` is a Swing application: search, pick a series, pick an episode, and the
video is shown by `mpv` in its own window while the GUI acts as the remote control for
it. It uses the real `MpvPlayerEngine`, so the position, the seeking and the subtitle
selection all work.

```
./gradlew :samples:gui:installDist
./samples/gui/build/install/gui/bin/gui
```

It needs `mpv` on the `PATH` (`pacman -S mpv`). The watch history is stored in
`~/.local/state/animeplayer/history.db`.

---

## The television client

`samples/android-tv` is a television client for this library, written with **Jetpack
Compose** and **Material 3 for TV** (`androidx.tv:tv-material`). It is a translation of
the desktop client, not a port of its code: the design language is the same — the same
themes, the same accent colours, the same typeface, the same banner and the same rails
of cards — but every decision is re-made for a remote control.

* **Landscape cards.** A 16:9 television wastes half of a portrait tile, and the title
  of a portrait card wraps into three unreadable lines.
* **A navigation bar on top.** A 64-pixel rail of icons is invisible from three metres.
* **Focus is the only state.** The focused element grows, gets a ring and a glow. The
  ring is the colour of the surface, not the accent colour, so an accent-coloured control
  does not hide its own ring.
* **Chips instead of drop-downs.** Every filter opens a full-screen sheet that is
  scrolled with the D-pad.
* **The focus is never lost.** Every rail remembers where the focus was, so coming back
  from the player does not throw the user back to the first card.
* **The search field is a button** until it is pressed — only then does the on-screen
  keyboard open. A text field swallows the arrow keys, and a user must not get stuck in
  one.

```
./gradlew :samples:android-tv:installDebug
adb shell am start -a android.intent.action.MAIN \
    -c android.intent.category.LEANBACK_LAUNCHER -n dev.animeplayer.tv/.MainActivity
```

The interface is verified against a real Android TV emulator with real key events:

```bash
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_CENTER
adb exec-out screencap -p > screenshot.png
```

[`docs/tv/TV-UX-SPEC.md`](docs/tv/TV-UX-SPEC.md) is the specification of the interface:
every rule, every screen and every measurement. `docs/tv/target/` holds the screenshots
of the running application, `docs/tv/reference/` the desktop client for comparison.

---

## Testing

The `fixtures/` directory holds golden payloads captured from the live site. The
fixture tests run against them offline, so a change in the upstream markup shows up as
a failing test rather than as a broken release.

```
./gradlew build          # compiles every target and runs the tests
./gradlew jvmTest        # the JVM tests only
```

There is also a live contract test that talks to the real site. It is disabled by
default and only runs when the `live` tag is enabled:

```
./gradlew jvmTest -PincludeTags=live
```

---

## Building

The project needs a JDK 21 and an Android SDK with platform 37.

```
sudo pacman -S jdk21-openjdk          # or any JDK 21
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew build
```

| Tool | Version |
|---|---|
| Kotlin | 2.2.10 |
| Gradle | 9.8.2 |
| AGP | 9.4.1 |
| Ktor | 3.6.0 |
| coroutines | 1.11.0 |
| SQLDelight | 2.4.1 |
| Media3 | 1.11.1 |

---

## Documentation

| Document | What it covers |
|---|---|
| [`AGENTS.md`](AGENTS.md) | The canonical guide: requirements, commands, architecture, the ports, the model, conventions, and the recipes. **Start here.** |
| [`docs/PROTOCOL.md`](docs/PROTOCOL.md) | The reverse-engineered specification of the upstream site: every request, every field, and every gotcha. |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | The design in depth: layer by layer, the data flows, the decisions and their rationale, the extension points. |
| [`docs/tv/TV-UX-SPEC.md`](docs/tv/TV-UX-SPEC.md) | The television interface: the translation of the mouse design into a remote control design, screen by screen. |

---

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

**This licence has consequences.** Any application that links this library is
derived work and has to be distributed under the GPL-3.0 as well. If you need to ship
a closed source application, do not use this library — the scraping logic is simple
enough to reimplement against the protocol described above, or use a different source.

The library resolves links to third party content. It does not ship, host or cache any
copyrighted material. See the disclaimer in [NOTICE](NOTICE).
