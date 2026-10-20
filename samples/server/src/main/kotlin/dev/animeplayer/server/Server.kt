/*
 * Copyright (C) 2026 animeplayer contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. See <https://www.gnu.org/licenses/> for the full text.
 */
package dev.animeplayer.server

import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.spi.WatchHistoryEntry
import dev.animeplayer.core.spi.WatchHistoryStore
import dev.animeplayer.network.KtorHttpTransport
import dev.animeplayer.network.TokenBucketRateLimiter
import dev.animeplayer.network.TransportConfig
import dev.animeplayer.network.createDefaultHttpClient
import dev.animeplayer.proxy.AnimeProxyService
import dev.animeplayer.proxy.ProxyConfig
import dev.animeplayer.proxy.animeProxyRoutes
import dev.animeplayer.sources.hianime.HianimeConfig
import dev.animeplayer.sources.hianime.HianimeSource
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * A small backend that turns the library into a browser friendly streaming API.
 *
 * The browser cannot talk to the upstream site directly — it is not allowed to set the
 * `Referer` header the CDN requires — so every playlist, segment and subtitle is served
 * through the proxy.
 *
 * The desktop sample in `samples/tauri` runs this server as a sidecar process and talks
 * to it over HTTP.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "127.0.0.1"
    val publicUrl = System.getenv("PUBLIC_URL") ?: "http://$host:$port"

    val client = createDefaultHttpClient()
    val transport = KtorHttpTransport(
        client = client,
        config = TransportConfig(
            userAgent = HianimeConfig.DEFAULT_USER_AGENT,
            // The CDN of the upstream site blocks clients that ask for too many
            // segments at once. A small bucket per host keeps the stream alive.
            rateLimiter = TokenBucketRateLimiter(permitsPerSecond = 3.0, burst = 2),
            // A blocked request is worth another try.
            retryableStatusCodes = setOf(403, 408, 425, 429, 500, 502, 503, 504),
        ),
    )
    val source = HianimeSource(transport, HianimeConfig())
    val scheduleSource = dev.animeplayer.sources.schedule.AnimescheduleSource(transport)
    val proxyService = AnimeProxyService(
        source = source,
        transport = transport,
        config = ProxyConfig(baseUrl = publicUrl),
    )
    val history = openHistoryStore()

    embeddedServer(Netty, port = port, host = host) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = true; encodeDefaults = true })
        }

        // The Tauri webview and the Vite dev server talk to this API from another origin.
        // Ktor matches the origin literally, so the port is part of the host.
        install(CORS) {
            allowHost("localhost:1420", schemes = listOf("http"))
            allowHost("127.0.0.1:1420", schemes = listOf("http"))
            allowHost("localhost", schemes = listOf("tauri"))
            allowHost("tauri.localhost", schemes = listOf("http"))
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
        }

        routing {
            animeProxyRoutes(proxyService)

            get("/api/health") {
                call.respond(HealthResponse(status = "ok", version = "0.1.0", port = port))
            }

            get("/api/search") {
                val query = call.request.queryParameters["q"].orEmpty()
                val results = source.search(query)
                call.respond(
                    SearchResponse(
                        results = results.map {
                            SearchResult(
                                slug = it.id.slug,
                                numericId = it.id.numericId,
                                title = it.title,
                                japaneseTitle = it.japaneseTitle,
                                posterUrl = it.posterUrl,
                                year = it.year,
                                genres = it.genres,
                                status = it.status.name,
                            )
                        },
                    ),
                )
            }

            get("/api/anime") {
                val slug = call.request.queryParameters["slug"].orEmpty()
                val anime = source.anime(AnimeId(slug = slug, numericId = numericIdOf(slug)))
                call.respond(
                    AnimeResponse(
                        slug = anime.id.slug,
                        numericId = anime.id.numericId,
                        title = anime.title,
                        japaneseTitle = anime.japaneseTitle,
                        posterUrl = anime.posterUrl,
                        description = anime.description,
                        genres = anime.genres,
                        year = anime.year,
                        status = anime.status.name,
                        titles = anime.titles,
                    ),
                )
            }

            get("/api/episodes") {
                val slug = call.request.queryParameters["slug"].orEmpty()
                val episodes = source.episodes(AnimeId(slug = slug, numericId = numericIdOf(slug)))
                call.respond(
                    EpisodeResponse(
                        slug = slug,
                        totalReported = episodes.totalReported,
                        episodes = episodes.episodes.map {
                            EpisodeDto(
                                number = it.number,
                                index = it.index,
                                title = it.title,
                                durationSeconds = it.durationSeconds,
                                isSpecial = it.isSpecial,
                            )
                        },
                    ),
                )
            }

            get("/api/schedule") {
                val query = call.request.queryParameters["q"].orEmpty()
                val entries = scheduleSource.schedule(query)
                call.respond(
                    ScheduleResponse(
                        entries = entries.map {
                            ScheduleItem(
                                title = it.title,
                                status = it.status.name,
                                nextSub = it.nextSubAt?.toString(),
                                nextDub = it.nextDubAt?.toString(),
                            )
                        },
                    ),
                )
            }

            historyRoutes(history)

            get("/") {
                call.respondText(INDEX_HTML, ContentType.Text.Html)
            }
        }
    }.start(wait = true)
}

/**
 * The watch history endpoints.
 *
 * They are the window into the `storage` module of the library: the desktop sample
 * writes the playback position here and reads it back for "continue watching".
 */
private fun Route.historyRoutes(history: WatchHistoryStore?) {
    get("/api/history") {
        val store = history
        if (store == null) {
            call.respond(HistoryResponse(entries = emptyList()))
            return@get
        }
        val entries = store.entries().map {
            HistoryEntryDto(
                slug = it.animeId.slug,
                numericId = it.animeId.numericId,
                title = it.title,
                episode = it.episodeNumber,
                updatedAt = it.updatedAt.toEpochMilliseconds(),
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                progress = it.progress,
                isCompleted = it.isCompleted,
            )
        }
        call.respond(HistoryResponse(entries = entries))
    }

    post("/api/history") {
        val store = history
        if (store == null) {
            call.respond(OkResponse(ok = false))
            return@post
        }
        val request = call.receive<HistoryUpsertRequest>()
        store.upsert(
            WatchHistoryEntry(
                animeId = AnimeId(slug = request.slug, numericId = numericIdOf(request.slug)),
                title = request.title.ifEmpty { request.slug },
                episodeNumber = request.episode,
                updatedAt = Clock.System.now(),
                positionMs = request.positionMs,
                durationMs = request.durationMs,
            ),
        )
        call.respond(OkResponse())
    }

    delete("/api/history") {
        val store = history
        val slug = call.request.queryParameters["slug"].orEmpty()
        val episode = call.request.queryParameters["episode"].orEmpty()
        if (store != null && slug.isNotEmpty() && episode.isNotEmpty()) {
            store.remove(AnimeId(slug = slug, numericId = numericIdOf(slug)), episode)
        }
        call.respond(OkResponse())
    }
}

/**
 * The numeric id of a slug.
 *
 * `one-piece-1` is the slug of the series and `1` is the id the upstream episode API is
 * keyed by.
 */
private fun numericIdOf(slug: String): String {
    val segments = slug.split('-')
    val last = segments.lastOrNull().orEmpty()
    if (last.isNotEmpty() && last.all { it.isDigit() }) return last
    return segments.lastOrNull { it.isNotEmpty() && it.all(Char::isDigit) } ?: slug
}

private val INDEX_HTML = """
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>animeplayer</title>
    <script src="https://cdn.jsdelivr.net/npm/hls.js@1.7.3/dist/hls.min.js"></script>
    <style>
        body { font-family: system-ui, sans-serif; margin: 2rem; max-width: 60rem; }
        input, button { font: inherit; padding: .4rem .6rem; }
        video { width: 100%; background: #000; aspect-ratio: 16/9; }
        li { cursor: pointer; padding: .2rem 0; }
        li:hover { color: #b83; }
    </style>
</head>
<body>
<h1>animeplayer</h1>
<input id="query" placeholder="Search anime…" size="40">
<button id="go">Search</button>
<ul id="results"></ul>
<h2 id="title"></h2>
<video id="video" controls></video>
<script>
    const results = document.getElementById('results');
    const video = document.getElementById('video');
    let hls;

    async function search() {
        const q = document.getElementById('query').value;
        const response = await fetch('/api/search?q=' + encodeURIComponent(q));
        const data = await response.json();
        results.innerHTML = '';
        data.results.slice(0, 20).forEach(result => {
            const item = document.createElement('li');
            item.textContent = result.title;
            item.onclick = () => showEpisodes(result);
            results.appendChild(item);
        });
    }

    async function showEpisodes(anime) {
        const response = await fetch('/api/episodes?slug=' + encodeURIComponent(anime.slug));
        const data = await response.json();
        results.innerHTML = '';
        data.episodes.slice(-30).reverse().forEach(number => {
            const item = document.createElement('li');
            item.textContent = 'Episode ' + number;
            item.onclick = () => play(anime, number);
            results.appendChild(item);
        });
    }

    async function play(anime, episode) {
        const response = await fetch('/api/resolve', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({slug: anime.slug, episode: episode})
        });
        const data = await response.json();
        document.getElementById('title').textContent = anime.title + ' — episode ' + episode;
        if (hls) { hls.destroy(); hls = null; }
        if (window.Hls && window.Hls.isSupported()) {
            hls = new window.Hls();
            hls.loadSource(data.playlistUrl);
            hls.attachMedia(video);
        } else {
            video.src = data.playlistUrl;
        }
        if (data.skipIntroEndSeconds) {
            video.addEventListener('loadedmetadata', () => {
                video.currentTime = data.skipIntroEndSeconds;
            }, {once: true});
        }
    }

    document.getElementById('go').onclick = search;
</script>
</body>
</html>
""".trimIndent()
