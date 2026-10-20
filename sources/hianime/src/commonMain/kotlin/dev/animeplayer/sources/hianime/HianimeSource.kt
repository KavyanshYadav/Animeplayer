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
package dev.animeplayer.sources.hianime

import dev.animeplayer.core.error.AnimeException
import dev.animeplayer.core.model.Anime
import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.EpisodeList
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.QualityPreference
import dev.animeplayer.core.model.QualitySelector
import dev.animeplayer.core.model.SkipSegments
import dev.animeplayer.core.model.StreamVariant
import dev.animeplayer.core.model.SubtitleTrack
import dev.animeplayer.core.model.TimeRange
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpResponse
import dev.animeplayer.core.spi.HttpTransport
import dev.animeplayer.core.spi.AnimeSource
import dev.animeplayer.core.util.M3u8Parser
import dev.animeplayer.core.util.UrlResolver
import dev.animeplayer.sources.hianime.internal.HianimeEndpoints
import dev.animeplayer.sources.hianime.internal.parser.AnimeDetailsParser
import dev.animeplayer.sources.hianime.internal.parser.EmbedConfigParser
import dev.animeplayer.sources.hianime.internal.parser.EpisodeListParser
import dev.animeplayer.sources.hianime.internal.parser.SearchParser
import dev.animeplayer.sources.hianime.internal.parser.ServerEntry
import dev.animeplayer.sources.hianime.internal.parser.ServerListParser
import dev.animeplayer.sources.hianime.internal.dto.HtmlEnvelope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

/**
 * The [AnimeSource] implementation for `hianime.at`.
 *
 * This is a port of the scraping logic of the original `ani-cli` shell script. The request
 * flow is identical — search, episode list, server list, player page, master playlist —
 * but every step is parsed structurally instead of with regular expressions, the servers
 * are tried one after another instead of only `ZokoAnime` being supported, and the
 * failures are reported as typed exceptions.
 *
 * @param transport the HTTP transport to use. Any implementation of
 *   [dev.animeplayer.core.spi.HttpTransport] works.
 * @param config the site configuration.
 */
public class HianimeSource(
    private val transport: HttpTransport,
    private val config: HianimeConfig = HianimeConfig(),
) : AnimeSource {

    override val id: String = "hianime"

    override val displayName: String = "HiAnime"

    private val endpoints = HianimeEndpoints(config.baseUrl)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val episodeListMutex = Mutex()
    private val episodeListCache = mutableMapOf<String, CachedEpisodeList>()

    override suspend fun search(query: String): List<Anime> {
        val html = get(endpoints.search(query))
        return SearchParser.parse(html)
    }

    override suspend fun anime(id: AnimeId): Anime {
        val html = get(endpoints.watchPage(id.slug))
        return AnimeDetailsParser.parse(html, id)
    }

    override suspend fun episodes(id: AnimeId): EpisodeList {
        val envelope = episodeListEnvelope(id)
        return EpisodeListParser.parse(envelope.html, id, envelope.totalItems)
    }

    override suspend fun resolve(
        id: AnimeId,
        episode: Episode,
        audioTrack: AudioTrack,
        quality: QualityPreference,
    ): PlayableSource {
        val episodeHtml = episodeListEnvelope(id).html
        val upstreamId = EpisodeListParser.episodeIdOf(episodeHtml, episode.number)
            ?: throw AnimeException.EpisodeNotReleasedError(id, episode.number)

        val servers = parseEnvelope(get(endpoints.servers(upstreamId))).html
            .let { ServerListParser.parse(it) }
            .filter { it.type == audioTrack }

        if (servers.isEmpty()) {
            throw AnimeException.EpisodeNotReleasedError(id, episode.number)
        }

        var lastError: Throwable? = null

        for (server in orderServers(servers)) {
            try {
                return resolveFromServer(server, audioTrack, quality)
            } catch (error: AnimeException.CloudflareChallengeError) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw lastError as? AnimeException.NetworkError
            ?: AnimeException.NoSourcesError(id, episode.number)
    }

    private suspend fun resolveFromServer(
        server: ServerEntry,
        audioTrack: AudioTrack,
        quality: QualityPreference,
    ): PlayableSource {
        val embedHtml = get(server.embedUrl, referer = endpoints.siteReferer)
        val embedConfig = EmbedConfigParser.parse(embedHtml)

        val masterUrl = embedConfig.src
            ?: throw AnimeException.ParseError("the player configuration", "no master playlist")

        val referer = UrlResolver.origin(server.embedUrl)
        val master = get(masterUrl, referer = referer)
        val variants = M3u8Parser.parseMaster(master, masterUrl)

        if (variants.isEmpty()) {
            throw AnimeException.ParseError("the master playlist", master.take(240))
        }

        val selection = QualitySelector.select(variants, quality)
        val variant: StreamVariant = selection.variant
            ?: throw AnimeException.NoSourcesError(AnimeId("unknown", "0"), "unknown")

        return PlayableSource(
            variant = variant,
            subtitles = embedConfig.subtitles.mapNotNull { subtitle ->
                val src = subtitle.src ?: return@mapNotNull null
                SubtitleTrack(
                    language = subtitle.lang.orEmpty(),
                    label = subtitle.label ?: subtitle.lang.orEmpty(),
                    url = UrlResolver.resolve(masterUrl, src),
                    isDefault = subtitle.isDefault,
                )
            },
            referer = referer,
            skip = embedConfig.skip?.let { skip ->
                SkipSegments(
                    intro = skip.intro?.let { TimeRange(it.start, it.end) },
                    outro = skip.outro?.let { TimeRange(it.start, it.end) },
                )
            },
            downloadUrl = embedConfig.downloadUrl?.let { UrlResolver.resolve(server.embedUrl, it) },
            serverName = server.name,
            audioTrack = audioTrack,
            masterUrl = masterUrl,
            variants = variants,
        )
    }

    /**
     * Orders the servers so that the configured ones are tried first.
     */
    private fun orderServers(servers: List<ServerEntry>): List<ServerEntry> {
        val preferred = config.preferredServers
        return servers.sortedBy { server ->
            val index = preferred.indexOfFirst { it.equals(server.name, ignoreCase = true) }
            if (index >= 0) index else preferred.size
        }
    }

    private suspend fun episodeListEnvelope(id: AnimeId): HtmlEnvelope {
        val now = Clock.System.now()
        episodeListMutex.withLock {
            val cached = episodeListCache[id.slug]
            if (cached != null && now < cached.expiresAt) return cached.envelope
        }

        val envelope = parseEnvelope(get(endpoints.episodeList(id.numericId)))

        episodeListMutex.withLock {
            episodeListCache[id.slug] = CachedEpisodeList(envelope, Clock.System.now() + EPISODE_LIST_TTL)
        }

        return envelope
    }

    private fun parseEnvelope(body: String): HtmlEnvelope =
        runCatching { json.decodeFromString<HtmlEnvelope>(body) }
            .getOrElse { throw AnimeException.ParseError("the API response", body.take(240)) }

    private suspend fun get(url: String, referer: String? = null): String {
        val request = HttpRequest(
            url = url,
            headers = buildMap {
                put("User-Agent", config.userAgent)
                put("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                put("Accept-Language", "en-US,en;q=0.9")
                referer?.let { put("Referer", it) }
            },
            timeout = config.requestTimeout,
        )

        val response: HttpResponse = transport.execute(request)

        if (!response.isSuccessful) {
            throw AnimeException.HttpStatusError(url, response.status, response.bodyAsText().take(512))
        }

        return response.bodyAsText()
    }

    private class CachedEpisodeList(val envelope: HtmlEnvelope, val expiresAt: Instant)

    private companion object {
        val EPISODE_LIST_TTL = 5.minutes
    }
}
