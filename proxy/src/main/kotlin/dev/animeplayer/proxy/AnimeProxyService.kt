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
package dev.animeplayer.proxy

import dev.animeplayer.core.error.AnimeException
import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.QualityPreference
import dev.animeplayer.core.spi.AnimeSource
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpTransport
import kotlinx.serialization.Serializable

/**
 * The request of the resolve endpoint.
 *
 * @property slug the slug of the series, for example `one-piece-1`.
 * @property episodeNumber the episode to resolve.
 */
public data class ResolveRequest(
    val slug: String,
    val episodeNumber: String,
    val audioTrack: AudioTrack = AudioTrack.Sub,
    val quality: String? = null,
)

/**
 * The answer of the resolve endpoint.
 *
 * @property playlistUrl the proxied URL of the HLS master playlist.
 */
@Serializable
public data class ResolveResponse(
    val title: String,
    val animeId: String,
    val episodeNumber: String,
    val quality: String,
    val playlistUrl: String,
    val subtitles: List<ProxiedSubtitle>,
    val skipIntroStartSeconds: Int? = null,
    val skipIntroEndSeconds: Int? = null,
)

/** A subtitle track whose URL points at the proxy. */
@Serializable
public data class ProxiedSubtitle(
    val language: String,
    val label: String,
    val url: String,
    val isDefault: Boolean,
)

/**
 * The payload behind a proxy token.
 */
public data class ProxyPayload(
    val content: ByteArray,
    val contentType: String,
)

/**
 * Turns an [AnimeSource] into a browser friendly streaming endpoint.
 *
 * The service resolves an episode the same way the library does, but instead of handing
 * the upstream URLs to the client it registers them in a [ProxyTargetStore] and returns
 * proxy URLs. Every request the proxy makes carries the `Referer` header the upstream CDN
 * requires, which a browser is not allowed to set.
 */
public class AnimeProxyService(
    private val source: AnimeSource,
    private val transport: HttpTransport,
    private val config: ProxyConfig = ProxyConfig(),
) {

    private val store = ProxyTargetStore(config.targetTtl)

    private val rewriter = PlaylistRewriter(config.baseUrl) { url, referer, kind ->
        val token = store.register(ProxyTarget(url = url, referer = referer, kind = kind))
        "${config.baseUrl}/hls/$token"
    }

    /**
     * Resolves an episode and returns the proxied URLs.
     */
    public suspend fun resolve(request: ResolveRequest): ResolveResponse {
        val animeId = AnimeId(slug = request.slug, numericId = numericIdOf(request.slug))
        val episodes = source.episodes(animeId)
        val episode: Episode = episodes.find(request.episodeNumber)
            ?: throw AnimeException.NotFoundError(
                "episode ${request.episodeNumber} of ${request.slug}",
            )

        val quality = when (request.quality?.lowercase()) {
            null, "", "best" -> QualityPreference.Best
            "worst" -> QualityPreference.Worst
            else -> QualityPreference.Height(request.quality.toIntOrNull() ?: 0)
        }

        val playable: PlayableSource = source.resolve(
            id = animeId,
            episode = episode,
            audioTrack = request.audioTrack,
            quality = quality,
        )

        // The master playlist is preferred, because it carries every rendition and lets
        // the player do adaptive bitrate. Older sources only offer the variant.
        val masterToken = store.register(
            ProxyTarget(
                url = playable.masterUrl ?: playable.variant.url,
                referer = playable.referer,
                kind = ProxyTarget.Kind.Master,
            ),
        )

        return ResolveResponse(
            title = "${animeId.slug} - episode ${episode.number}",
            animeId = animeId.slug,
            episodeNumber = episode.number,
            quality = playable.variant.quality.label,
            playlistUrl = "${config.baseUrl}/hls/$masterToken",
            subtitles = playable.subtitles.map { track ->
                val token = store.register(
                    ProxyTarget(
                        url = track.url,
                        referer = playable.referer,
                        kind = ProxyTarget.Kind.Subtitle,
                    ),
                )
                ProxiedSubtitle(
                    language = track.language,
                    label = track.label,
                    url = "${config.baseUrl}/hls/$token",
                    isDefault = track.isDefault,
                )
            },
            skipIntroStartSeconds = playable.skip?.intro?.startSeconds,
            skipIntroEndSeconds = playable.skip?.intro?.endSeconds,
        )
    }

    /**
     * Fetches the resource behind [token] and, for playlists, rewrites its URIs.
     */
    public suspend fun handle(token: String): ProxyPayload? {
        val target = store.resolve(token) ?: return null

        val request = HttpRequest(
            url = target.url,
            headers = mapOf(
                "User-Agent" to config.userAgent,
                "Referer" to target.referer,
            ),
        )

        val response = transport.execute(request)
        if (!response.isSuccessful) {
            throw AnimeException.HttpStatusError(target.url, response.status)
        }

        return when (target.kind) {
            ProxyTarget.Kind.Master -> {
                // The master playlist references the variant playlists.
                val playlist = response.bodyAsText()
                val rewritten = rewriter.rewrite(
                    playlist = playlist,
                    sourceUrl = target.url,
                    referer = target.referer,
                    kind = ProxyTarget.Kind.Variant,
                )
                ProxyPayload(rewritten.encodeToByteArray(), CONTENT_TYPE_PLAYLIST)
            }

            ProxyTarget.Kind.Variant -> {
                // The variant playlist references the media segments.
                val playlist = response.bodyAsText()
                val rewritten = rewriter.rewrite(
                    playlist = playlist,
                    sourceUrl = target.url,
                    referer = target.referer,
                    kind = ProxyTarget.Kind.Segment,
                )
                ProxyPayload(rewritten.encodeToByteArray(), CONTENT_TYPE_PLAYLIST)
            }

            ProxyTarget.Kind.Subtitle -> {
                ProxyPayload(response.body, CONTENT_TYPE_SUBTITLE)
            }

            ProxyTarget.Kind.Segment -> {
                ProxyPayload(response.body, CONTENT_TYPE_SEGMENT)
            }
        }
    }

    private fun numericIdOf(slug: String): String {
        val segments = slug.split('-')
        val last = segments.lastOrNull().orEmpty()
        if (last.isNotEmpty() && last.all { it.isDigit() }) return last
        return segments.lastOrNull { it.isNotEmpty() && it.all(Char::isDigit) } ?: slug
    }

    private companion object {
        const val CONTENT_TYPE_PLAYLIST = "application/vnd.apple.mpegurl"
        const val CONTENT_TYPE_SUBTITLE = "text/vtt"
        const val CONTENT_TYPE_SEGMENT = "video/mp2t"
    }
}
