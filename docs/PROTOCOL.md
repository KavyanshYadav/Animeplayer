# The upstream protocol

Everything in this document was verified against the live site on **2026-10-21**.
Where the original `ani-cli` gets it wrong, the difference is called out.

The upstream is `https://hianime.at`. It is a server-rendered site with a small JSON API
under `/api/theme/…`.

---

## 0. The flow at a glance

```
search  ──▶  slug          one-piece-1
             │
             ├─▶ numericId  1            (the trailing number of the slug)
             │
episodes ──▶ episodeId     149894        (data-id, NOT the episode number)
             │
servers  ──▶ embedUrl      https://zokoanime.video/stream/mal/21/1/sub
             │
embed     ──▶ masterUrl    https://hls.dramahot.top/p/…/master.m3u8
             │              + subtitles[] + skip{intro,outro}
master    ──▶ variants     1080p, 720p, 480p …
```

Five requests. Everything else is parsing.

---

## 1. Search

```
GET https://hianime.at/search?keyword={urlencoded query}
Accept: text/html
User-Agent: <a browser UA>
```

The response is a full HTML page. The results are `<div class="film-detail">` blocks:

```html
<div class="film-detail">
    <h3 class="film-name">
        <a href="https://hianime.at/one-piece-1"
            title="One Piece"
            class="dynamic-name"
            data-jname="One Piece">
            One Piece
        </a>
    </h3>
</div>
```

| Field | Source |
|---|---|
| `slug` | the last path segment of `href` → `one-piece-1` |
| `numericId` | the last `-`-separated segment of the slug, if it is all digits → `1` |
| `title` | the `title` attribute, HTML-decoded |
| `nativeTitle` | the `data-jname` attribute, HTML-decoded — **only kept when it differs from the title** |

### Gotchas

- **The sidebar repeats the top ten series.** The document has to be cut at
  `id="main-sidebar"` before the results are extracted, otherwise every popular series
  appears twice.
  *ani-cli does this with `sed '/id="main-sidebar"/,$d'`.*
- **The attributes are spread over several lines.** A naive line-based regex fails.
  *ani-cli survives only because it flattens the document with `tr '\n' ' '` first.*
- **`data-jname` is often identical to `title`** (for western titles). Keeping both
  would show the same string twice.
- The absolute `href` is fine — only the last path segment matters.

---

## 2. The episode list

```
GET https://hianime.at/api/theme/episode/list/{numericId}
```

The response is JSON:

```json
{"status": true, "totalItems": 12, "html": "<div class=\"seasons-block\">…</div>"}
```

The `html` field contains one `<a>` per episode:

```html
<a title="Episode 1"
   class="ssl-item ep-item"
   data-number="1"
   data-id="1"
   href="https://hianime.at/watch/one-piece-1?ep=1">
```

| Field | Source |
|---|---|
| `number` | `data-number` — the number shown to the user, e.g. `"12.5"` for a special |
| `episodeId` | `data-id` — the internal id, **not** the episode number |
| `slug` | derivable from `href` |

### Gotchas

- **`totalItems` is wrong.** For One Piece it reports `12` while the list actually holds
  `1180` episodes. It is the size of one page. *ani-cli never reads it; it also does not
  notice that it is wrong.* Use the parsed count.
- **The first episode anchor sometimes has no `ep-item` class** (`class="ssl-item "`).
  Match on the presence of `data-number` and `data-id` instead of on the class.
- **`data-id` and the episode number diverge.** Later episodes have huge ids
  (episode 1180 → `data-id="149894"`). Never derive the id from the number.
- The `html` field is a JSON string, so its quotes are escaped. Decode the JSON first —
  stripping backslashes by hand (*ani-cli's* `sed 's|\\||g'`) corrupts any content that
  legitimately contains a backslash.

---

## 3. The server list

```
GET https://hianime.at/api/theme/episode/servers?episodeId={data-id}
```

Again a JSON envelope, with one `<div>` per server:

```html
<div class="item server-item" data-type="sub"
     data-server-name="ZokoAnime"
     data-hash="aHR0cHM6Ly96b2tvYW5pbWUudmlkZW8vc3RyZWFtL21hbC8yMS8xL3N1Yg==">
```

| Field | Source |
|---|---|
| `audioTrack` | `data-type` → `sub` / `dub` |
| `name` | `data-server-name` |
| `embedUrl` | Base64-decoded `data-hash` |

Decoded, the hash of the first server is:

```
https://zokoanime.video/stream/mal/21/1/sub
                ↑         ↑   ↑  ↑
                host      MAL ep  track
```

The MAL id is useful for metadata and for the skip detection that `ani-cli` gets from
the external `ani-skip` service.

### Gotchas

- *ani-cli only understands the `ZokoAnime` embed* and dies on the others. This library
  tries every server in turn.
- The server list is **per episode and per track**. The `dub` list is separate from the
  `sub` list.
- `data-type` is the only reliable way to tell the two apart.

---

## 4. The player page

```
GET {embedUrl}
```

The page embeds its configuration as an obfuscated blob:

```html
<script>window.__P="FFYFBAJDCQIDATtYBF1NTkNEEUISAw4KBUlZXA4YTll…"</script>
```

### The deobfuscation

```
plain[i] = base64decode(blob)[i] xor "otaku-embed-v1"[i % 13]
```

The key is the 13 byte string `otaku-embed-v1`, applied cyclically.

*ani-cli does this with `od`, a rotating positional parameter list and
`printf "%b"` with octal escapes. The result is identical.*

The decoded JSON:

```json
{
  "src": "https://hls.dramahot.top/p/scxqicy/…/master.m3u8",
  "subtitles": [
    {"lang": "en", "label": "English", "default": true,
     "src": "https://hls.dramahot.top/v/scxqicy/…/subs/….vtt"}
  ],
  "poster": "",
  "download_url": "/download/mal/21/1/sub",
  "video_id": 2806,
  "skip": {"intro": {"start": 31, "end": 111},
           "outro": {"start": 1376, "end": 1447}},
  "view": {"min_watch": 30, "dedupe_hours": 24},
  "player": {"engine": "jwplayer", "default_quality": "auto", "…": "…"}
}
```

### Gotchas

- **`skip` is free.** `ani-cli` calls the external `ani-skip` service to find the intro;
  the information is already in this payload.
- The subtitle `src` may be relative — resolve it against the master URL.
- `download_url` is relative to the embed URL.

---

## 5. The master playlist

```
GET {src}
Referer: {origin of the embed URL}      ← MANDATORY
```

```m3u8
#EXTM3U
#EXT-X-VERSION:4
#EXT-X-STREAM-INF:BANDWIDTH=5300000,RESOLUTION=1920x1080
1080/index.m3u8
```

### Gotchas

- **The `Referer` is mandatory.** Without it the CDN answers `403 Forbidden`.
  The same is true for the subtitle file. *ani-cli gets this right — it computes
  `refr` from the embed URL with `sed -E 's|^(https?://[^/]*).*|\1/|'` and passes it
  with `-e`.*
- **The variant URIs are relative.** `1080/index.m3u8` has to be resolved against the
  master URL, not string-prefixed.
- **`CODECS` may contain a comma inside quotes:** `CODECS="avc1.640028,mp4a.40.2"`.
  A naive split on `,` corrupts it.
- *ani-cli's* `sed 's|^#EXT-X-STREAM.*x||g'` is greedy and breaks when any attribute
  after `RESOLUTION` contains an `x`.
- `#EXT-X-I-FRAME-STREAM-INF` entries are trick-play streams and are not directly
  playable.

---

## 6. The variant playlist and the segments

```
GET {variant URL}      Referer: {origin of the embed URL}
```

```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-TARGETDURATION:10
#EXTINF:11.386,
https://hls1.drama1.cfd/s/scxqicy/…/1080/seg_00000.ts.jpg
```

The **segment URLs are absolute and point at a different host** than the playlist. This
matters for the browser: the segments have to be proxied as well, not just the playlist.

---

## 7. The schedule site

`https://animeschedule.net` is a different site and is implemented in
`sources/schedule`.

```
GET https://animeschedule.net/api/v3/anime?q={query}
```

```json
{"page": 1, "totalAmount": 92, "anime": [{
  "id": "urTI", "title": "One Piece", "route": "one-piece",
  "status": "Ongoing",
  "subTime": "2025-10-17T16:00:00Z", "dubTime": "2026-08-27T16:00:00Z",
  "jpnTime": "0001-01-01T00:00:00Z",
  "genres": [{"name": "Action"}], "studios": [{"name": "Toei Animation"}],
  "names": {"native": "ワンピース"},
  "websites": {"mal": "myanimelist.net/anime/21/One_Piece"},
  "imageVersionRoute": "anime/jpg/default/one-piece-f2f2a983a8.jpg"
}]}
```

| Field | Meaning |
|---|---|
| `status` | `Ongoing` / `Finished` |
| `subTime`, `dubTime` | the next broadcast timestamps, in UTC |
| `names.native` | the native title |
| `websites.mal` | the MAL url; the numeric id is the part after `/anime/` |
| `imageVersionRoute` | the poster path; the full url is `https://img.animeschedule.net/production/assets/public/img/{route}` |

### Gotchas

- **`0001-01-01T00:00:00Z` and `0002-01-01T…` mean "not set"**, not a real timestamp.
  Treat anything before the epoch as `null`.
- *ani-cli's* schedule scraper is **broken against the live site**. It looks for
  `english`, `romaji` and a `status` of `"Finished"`, and it reads the rendered HTML
  page — which is now client-side rendered, so the countdown regexes match nothing and
  the output is a wall of `N/A`. This library parses the JSON API instead.

---

## 8. Cloudflare

The site sits behind Cloudflare. On 2026-10-21 a plain client with a browser
`User-Agent` was served normally — no TLS impersonation needed. The original tool
nevertheless ships a `curl-impersonate` failover, because Cloudflare fingerprints the
TLS client and can start challenging at any time.

The library detects the interstitial and reports it as a typed error:

```kotlin
AnimeException.CloudflareChallengeError(url)
```

`CloudflareChallengeDetector` looks for `"Just a moment"`, `"cf-browser-verification"`,
`"challenge-platform"` and friends in the body of a `4xx`/`5xx` response. When it
trips, the caller can swap in a transport that impersonates a browser — the
`HttpTransport` port exists precisely so that this is possible without touching the
library.

---

## 9. Quick reference — the requests

| # | Method | URL | Headers | Response |
|---|---|---|---|---|
| 1 | GET | `{base}/search?keyword={q}` | UA | HTML |
| 2 | GET | `{base}/api/theme/episode/list/{numericId}` | UA | JSON |
| 3 | GET | `{base}/api/theme/episode/servers?episodeId={id}` | UA | JSON |
| 4 | GET | `{embedUrl}` | UA | HTML with `window.__P` |
| 5 | GET | `{masterUrl}` | UA, **Referer** | HLS |
| 6 | GET | `{variantUrl}` | UA, **Referer** | HLS |
| 7 | GET | `{subtitleUrl}` | UA, **Referer** | WebVTT |
