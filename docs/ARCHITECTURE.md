# Architecture

This document explains *why* the library is shaped the way it is. For the API surface
see [`../AGENTS.md`](../AGENTS.md); for the upstream protocol see [`PROTOCOL.md`](PROTOCOL.md).

---

## 1. The design goals

1. **The same code on Android, on the desktop, on a server and in the browser.**
   Everything that is genuinely platform-specific sits behind an `expect`/`actual` pair
   or behind a port. Everything else is in `commonMain`.
2. **The library must not own the network.** Consumers have wildly different HTTP
   stacks — OkHttp on Android, Ktor everywhere, `java.net.http` on the server, `fetch`
   in the browser. The library declares a five-line interface and gets out of the way.
3. **Every upstream site is interchangeable.** Adding a site must not require touching
   anything but the new module.
4. **Failures are data, not strings.** A caller has to be able to tell "the network
   hiccupped" from "the upstream markup changed" from "the episode is not out yet".
5. **Upstream drift is a first-class concern.** The site is a scraper target; it *will*
   change. The design makes drift visible early (fixture tests) and recoverable
   (attribute-driven parsing, multi-server failover, typed errors).

---

## 2. Layer by layer

### `core` — the contract

Contains four packages and nothing else:

| Package | Contents | Rule |
|---|---|---|
| `model` | The data classes the whole library passes around. | Immutable, no behaviour beyond small derived values. |
| `spi` | The ports: `AnimeSource`, `HttpTransport`, `PlayerEngine`, `WatchHistoryStore`, `Cache`, `Logger`, `MetricsSink`, `ScheduleSource`. | Interfaces only. |
| `error` | `AnimeException` and its subtypes. | One sealed hierarchy. |
| `util` | `Base64`, `M3u8Parser`, `UrlResolver`, `HtmlEntities`, `EpisodeSelector`. | Pure functions, no I/O, no platform APIs. |

`core` depends on nothing but the Kotlin standard library and coroutines. That is
enforced by convention: it is the module every other module depends on, so anything
added here becomes a permanent part of the public API.

Two utilities exist for concrete reasons:

- **`Base64`** — `kotlin.io.encoding.Base64` is still experimental and its API has
  changed between releases. The upstream payloads embed Base64 in JSON and in HTML, so
  the codec is a hard requirement; owning it keeps the public surface stable and avoids
  opt-in annotations in consumer code.
- **`UrlResolver`** — HLS playlists use relative variant URIs and absolute segment URIs
  on a different host. `java.net.URI` is not available in `commonMain`, so the RFC 3986
  resolution is implemented by hand.

### `network` — the HTTP adapter

`KtorHttpTransport` implements `HttpTransport` and adds the things a production client
needs:

```
request
  │
  ├─ cache lookup              (GET only, TTL)
  ├─ rate limiter acquire      (per host, token bucket)
  ├─ circuit breaker           (per host, opens after N consecutive failures)
  ├─ attempt loop
  │    ├─ perform the request
  │    ├─ Cloudflare challenge detection  → CloudflareChallengeError
  │    ├─ retryable status?     → backoff and try again
  │    └─ cache the response    (2xx only)
  └─ metrics + logging
```

The engine is selected per platform with `expect`/`actual`:

| Target | Engine |
|---|---|
| `jvm` | CIO |
| `android` | OkHttp |
| `js` | `Js` (the `fetch` based engine) |

`RecordingHttpTransport` lives in `main` rather than in the test source set on
purpose: consumers need it to test their own code against the library.

### `storage` — the persistence adapter

SQLDelight over SQLite. The `.sq` file, the generated queries and a
`WatchHistoryStore` implementation that runs its queries on an injectable
`CoroutineContext` — pass `Dispatchers.IO` on Android so the database is never touched
from the main thread.

`InMemoryWatchHistoryStore` lives in `core` so that tests and platforms without a file
system have an implementation without an extra dependency.

### `player` — the playback adapters

One `PlayerEngine` per platform. They differ completely in how they work, which is
exactly why they are behind an interface:

| Target | Engine | How it applies the `Referer` |
|---|---|---|
| `android` | Media3 / ExoPlayer | `DefaultHttpDataSource.Factory.setDefaultRequestProperties` |
| `jvm` | `mpv` over its JSON IPC | `--referrer=` on the command line |
| `js` | `hls.js` | **not possible** — the browser forbids it |

The mpv engine talks to `mpv` over a Unix domain socket using the JSON IPC protocol:
every request carries a `request_id` and the answer is dispatched to the waiting
caller. That gives a real `PlaybackSession` — play, pause, seek, position — instead of
fire-and-forget.

### `sources/*` — the upstream adapters

Each source implements `AnimeSource` and speaks only through `HttpTransport`. The
`hianime` source additionally contains the parsers, which are split by concern:

```
internal/
├── HtmlFragment.kt      a tiny attribute-driven HTML reader
├── HianimeEndpoints.kt  the URLs
├── codec/EmbedDeobfuscator.kt   the XOR deobfuscation
├── dto/HianimeDto.kt    the serializable payloads
└── parser/              SearchParser, EpisodeListParser, ServerListParser,
                         EmbedConfigParser, AnimeDetailsParser
```

The parsers are pure functions over strings. They are tested directly against the
golden fixtures, without any HTTP involved.

### `proxy` — the browser escape hatch

A Ktor server that resolves an episode and then serves the playlists, the segments and
the subtitles itself, with the correct `Referer` on every upstream request. It exists
because of one browser restriction: `Referer` is a forbidden header name, so a browser
cannot talk to the CDN directly.

The rewriting is done by `PlaylistRewriter`, which rewrites every URI line and every
`URI="…"` attribute (`EXT-X-KEY`, `EXT-X-MAP`) to a proxy URL backed by an opaque,
expiring token.

---

## 3. The data flow

### Resolving an episode

```
caller
  │
  ▼
AnimeSource.resolve(animeId, episode, audioTrack, quality)
  │
  ├─ GET /api/theme/episode/list/{numericId}          → episodeId
  ├─ GET /api/theme/episode/servers?episodeId={id}    → [ServerEntry]
  │
  ├─ for each server (preferred order):
  │    ├─ GET {embedUrl}            → window.__P → XOR → JSON
  │    ├─ GET {masterUrl}  (Referer) → HLS master playlist
  │    ├─ M3u8Parser                → [StreamVariant]
  │    └─ QualitySelector           → the chosen variant
  │
  └─▶ PlayableSource(variant, subtitles, referer, skip, masterUrl, variants)
```

The server loop is the resilience mechanism: if the preferred server is down or uses a
different player, the next one is tried. The original tool only understood one server
and died on the others.

### Playing

```
PlayableSource ──▶ PlayerEngine.prepare(PlaybackRequest)
                        │
                        ├─ media request   (with the Referer)
                        └─ subtitle request (with the Referer)
```

### Through the proxy

```
browser ──POST /api/resolve──▶ AnimeProxyService.resolve()
                                        │
                                        ├─ AnimeSource.resolve()
                                        ├─ register(masterUrl)  → token A
                                        └─ register(subtitleUrl) → token B
browser ◀──{playlistUrl: /hls/A}────────┘

browser ──GET /hls/A──▶ AnimeProxyService.handle(A)
                          ├─ GET {masterUrl}  (with the Referer)
                          ├─ rewrite the variant URIs → tokens C…
                          └─ respond with the rewritten playlist
browser ──GET /hls/C──▶ … → the segments
```

---

## 4. Decisions worth explaining

### Why is `referer` part of the model?

Because it is part of *how the URL is used*, not of how it was obtained. A `StreamVariant`
without its referer is not usable. Putting it on the model means no player can forget
it: the player receives a `PlayableSource`, and the referer is right there next to the
URL.

The alternative — keeping it inside the transport — would make it impossible for the
player (which does its own HTTP) to apply it.

### Why does `PlayableSource` carry both `variant` and `variants`?

`variant` is the rendition that was selected for the requested quality. `variants` is
every rendition the upstream offers. A player that supports adaptive bitrate wants the
latter; a player that does not wants the former. `masterUrl` is there for the same
reason — the master playlist is what an ABR player loads.

### Why is the episode list cached?

`resolve()` needs the episode id, which lives in the episode list. Without a cache that
would be an extra request per resolve. The cache is a five-minute, in-memory, per-slug
map inside the source — deliberately simple, because the list changes only when a new
episode is released.

### Why are the parsers attribute-driven instead of class-driven?

Because the upstream does not reliably carry the expected CSS class. Concretely: the
**first episode anchor had no `ep-item` class at all** (`class="ssl-item "`). A
class-driven parser silently drops episode 1. Matching on `data-number` + `data-id` is
immune to that class of change.

### Why is there a hand-written HTML reader instead of kotlinx-html?

Because the interesting parts of the upstream markup are a handful of tags with
attributes, and the parts that are *not* interesting are a megabyte of navigation. A
full HTML parser would be a large dependency for a small job. `HtmlFragment` handles
the two things that actually bite: attributes spread over several lines, and `>`
characters inside quoted attribute values.

### Why does the mpv engine use JSON IPC instead of just launching mpv?

Because a `PlaybackSession` that cannot report the position or seek is not a session.
The JSON IPC gives play, pause, seek, position, duration and subtitle control, which
is what the `PlaybackSession` port promises.

### Why is the JS player not bundled with hls.js?

The host application owns its bundling strategy and its dependency versions. The
engine looks for `window.Hls` and falls back to native HLS (Safari) when it is absent.
The trade-off is documented in the KDoc.

---

## 5. Extension points

| I want to… | Do this |
|---|---|
| Use a different HTTP client | Implement `HttpTransport`. Nothing else changes. |
| Add a site | New module in `sources/`, implement `AnimeSource`. |
| Add a player | Implement `PlayerEngine` + `PlaybackSession` in `player/src/<platform>Main/`. |
| Change the persistence | Implement `WatchHistoryStore`. |
| Add caching / metrics / logging | Implement `Cache`, `MetricsSink`, `Logger` and pass them to `TransportConfig`. |
| Restrict the servers that are tried | `HianimeConfig(preferredServers = listOf("ZokoAnime"))`. |
| Use a different upstream base url | `HianimeConfig(baseUrl = "…")`. |

---

## 6. Testing strategy

Three layers, each with a different purpose:

1. **Unit tests** (`commonTest`) — the pure logic: Base64, URL resolution, the M3U8
   parser, HTML entities, the episode selector, the quality selector, the cache, the
   backoff, the Cloudflare detector. No I/O, no network, runs on every target.

2. **Fixture tests** (`jvmTest`) — the parsers and the whole `HianimeSource` flow
   against payloads captured from the live site. `RecordingHttpTransport` replays them,
   so the test is deterministic and offline. This is the layer that catches upstream
   drift.

3. **Live contract tests** (`LiveContractTest`) — the same flow against the real site.
   Disabled unless `-Plive=true`; run nightly in CI. This catches the drift that the
   fixtures do not cover yet.

The `fixtures/` directory is the shared source of truth for layer 2. The test JVM finds
it through the `animeplayer.fixturesDir` system property, which the build sets.

---

## 7. Known limitations

| Limitation | Why | Mitigation |
|---|---|---|
| The browser cannot stream directly | `Referer` is a forbidden header name | The proxy module |
| The upstream can change at any time | It is a scraper target | Fixture tests, live contract tests, typed errors |
| Cloudflare may start challenging | It fingerprints the TLS client | `CloudflareChallengeError` + a swappable `HttpTransport` |
| The storage module has no JS target | SQLDelight ships no JS driver | `InMemoryWatchHistoryStore` in `core` |
| No native (iOS/macOS/Linux) targets | The player engines are not implemented there | The `jvm` target covers desktop; new targets only need a `player` source set |
| The schedule source is a separate module | It is a different site | By design |

---

## 8. Possible next steps

- A second upstream source, to prove that the `AnimeSource` port really is enough.
- Native targets (`iosArm64`, `macosArm64`, `linuxX64`) with a libmpv player.
- A `NativeHlsSession` for the browser that fetches the playlist through the proxy and
  hands the segment URLs to `MediaSource`.
- Rate limiting and the circuit breaker exposed through the source configuration
  instead of the transport configuration.
- A migration path from the `ani-cli` TSV history file into `WatchHistoryStore`.
