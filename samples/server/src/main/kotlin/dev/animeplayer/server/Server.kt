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
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.network.KtorHttpTransport
import dev.animeplayer.network.TransportConfig
import dev.animeplayer.network.createDefaultHttpClient
import dev.animeplayer.proxy.AnimeProxyService
import dev.animeplayer.proxy.ProxyConfig
import dev.animeplayer.proxy.animeProxyRoutes
import dev.animeplayer.sources.hianime.HianimeConfig
import dev.animeplayer.sources.hianime.HianimeSource
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(val results: List<SearchResult>)

@Serializable
data class SearchResult(val slug: String, val title: String, val japaneseTitle: String?)

@Serializable
data class EpisodeResponse(val slug: String, val episodes: List<String>)

@Serializable
data class ScheduleResponse(val entries: List<ScheduleItem>)

@Serializable
data class ScheduleItem(
    val title: String,
    val status: String,
    val nextSub: String?,
    val nextDub: String?,
)

/**
 * A small backend that turns the library into a browser friendly streaming API.
 *
 * The browser cannot talk to the upstream site directly — it is not allowed to set the
 * `Referer` header the CDN requires — so every playlist, segment and subtitle is served
 * through the proxy.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val publicUrl = System.getenv("PUBLIC_URL") ?: "http://localhost:$port"

    val client = createDefaultHttpClient()
    val transport = KtorHttpTransport(
        client = client,
        config = TransportConfig(userAgent = HianimeConfig.DEFAULT_USER_AGENT),
    )
    val source = HianimeSource(transport, HianimeConfig())
    val scheduleSource = dev.animeplayer.sources.schedule.AnimescheduleSource(transport)
    val proxyService = AnimeProxyService(
        source = source,
        transport = transport,
        config = ProxyConfig(baseUrl = publicUrl),
    )

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = true })
        }

        routing {
            animeProxyRoutes(proxyService)

            get("/api/search") {
                val query = call.request.queryParameters["q"].orEmpty()
                val results = source.search(query)
                call.respondText(
                    Json.encodeToString(
                        SearchResponse.serializer(),
                        SearchResponse(
                            results = results.map {
                                SearchResult(it.id.slug, it.title, it.japaneseTitle)
                            },
                        ),
                    ),
                    ContentType.Application.Json,
                )
            }

            get("/api/episodes") {
                val slug = call.request.queryParameters["slug"].orEmpty()
                val id = AnimeId(slug = slug, numericId = numericIdOf(slug))
                val episodes = source.episodes(id)
                call.respondText(
                    Json.encodeToString(
                        EpisodeResponse.serializer(),
                        EpisodeResponse(slug, episodes.episodes.map { it.number }),
                    ),
                    ContentType.Application.Json,
                )
            }

            get("/api/schedule") {
                val query = call.request.queryParameters["q"].orEmpty()
                val entries = scheduleSource.schedule(query)
                call.respondText(
                    Json.encodeToString(
                        ScheduleResponse.serializer(),
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
                    ),
                    ContentType.Application.Json,
                )
            }

            get("/") {
                call.respondText(INDEX_HTML, ContentType.Text.Html)
            }
        }
    }.start(wait = true)
}

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
