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
package dev.animeplayer.sources.anilist

import dev.animeplayer.core.error.AnimeException
import dev.animeplayer.core.model.CatalogEpisode
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.CatalogPage
import dev.animeplayer.core.model.CatalogQuery
import dev.animeplayer.core.model.CatalogRelation
import dev.animeplayer.core.spi.CatalogSource
import dev.animeplayer.core.spi.HttpMethod
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpTransport
import dev.animeplayer.sources.anilist.internal.AniListQueries
import dev.animeplayer.sources.anilist.internal.dto.EpisodeEnvelope
import dev.animeplayer.sources.anilist.internal.dto.GraphQlRequest
import dev.animeplayer.sources.anilist.internal.dto.MediaEnvelope
import dev.animeplayer.sources.anilist.internal.dto.MediaListEnvelope
import dev.animeplayer.sources.anilist.internal.dto.PageEnvelope
import dev.animeplayer.sources.anilist.internal.dto.RecommendationEnvelope
import dev.animeplayer.sources.anilist.internal.dto.RelationEnvelope
import dev.animeplayer.sources.anilist.internal.parser.AniListParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The [CatalogSource] implementation for `anilist.co`.
 *
 * The catalogue knows *about* series — artwork, descriptions, scores, genres — but it
 * never serves a stream, which is why it is a port of its own and not an
 * [dev.animeplayer.core.spi.AnimeSource].
 *
 * Every request goes through the [HttpTransport], so the module has no idea which HTTP
 * client backs it.
 *
 * @param transport the HTTP transport to use.
 * @param config the catalogue configuration.
 */
public class AniListSource(
    private val transport: HttpTransport,
    private val config: AniListConfig = AniListConfig(),
) : CatalogSource {

    override val id: String = "anilist"

    override val displayName: String = "AniList"

    override val genres: List<String> = GENRES

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    override suspend fun page(query: CatalogQuery): CatalogPage {
        val (document, variables) = AniListQueries.page(query)
        val payload = execute(document, variables)
        val envelope = decode<PageEnvelope>(payload)
        return AniListParser.page(envelope.page)
    }

    override suspend fun media(id: String): CatalogMedia {
        val (document, variables) = AniListQueries.media(id)
        val payload = execute(document, variables)
        val envelope = decode<MediaEnvelope>(payload)
        return AniListParser.media(envelope.media)
            ?: throw AnimeException.NotFoundError("the catalogue entry $id")
    }

    override suspend fun media(ids: List<String>): List<CatalogMedia> {
        if (ids.isEmpty()) return emptyList()
        val (document, variables) = AniListQueries.mediaList(ids)
        val payload = execute(document, variables)
        val envelope = decode<MediaListEnvelope>(payload)
        return AniListParser.mediaList(envelope.page?.media)
    }

    override suspend fun episodes(id: String): List<CatalogEpisode> {
        val (document, variables) = AniListQueries.episodes(id)
        val payload = execute(document, variables)
        val envelope = decode<EpisodeEnvelope>(payload)
        return AniListParser.episodes(envelope.media?.episodes?.nodes)
    }

    override suspend fun relations(id: String): List<CatalogRelation> {
        val (document, variables) = AniListQueries.relations(id)
        val payload = execute(document, variables)
        val envelope = decode<RelationEnvelope>(payload)
        return AniListParser.relations(envelope.media?.relations?.edges)
    }

    override suspend fun recommendations(id: String): List<CatalogMedia> {
        val (document, variables) = AniListQueries.recommendations(id)
        val payload = execute(document, variables)
        val envelope = decode<RecommendationEnvelope>(payload)
        return AniListParser.mediaList(envelope.media?.recommendations?.nodes)
    }

    /**
     * Sends a GraphQL document and returns the raw answer.
     */
    private suspend fun execute(
        document: String,
        variables: Map<String, kotlinx.serialization.json.JsonElement>,
    ): String {
        val body = json.encodeToString(
            GraphQlRequest.serializer(),
            GraphQlRequest(query = document, variables = variables),
        ).encodeToByteArray()

        val request = HttpRequest(
            url = config.endpoint,
            method = HttpMethod.Post,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "User-Agent" to config.userAgent,
            ),
            body = body,
            timeout = config.requestTimeout,
        )

        val response = transport.execute(request)
        if (!response.isSuccessful) {
            throw AnimeException.HttpStatusError(
                url = config.endpoint,
                status = response.status,
                body = response.bodyAsText().take(500),
            )
        }
        return response.bodyAsText()
    }

    /**
     * Decodes a GraphQL answer, turning the `errors` array into a typed exception.
     */
    private inline fun <reified T> decode(payload: String): T {
        val root = try {
            json.parseToJsonElement(payload).jsonObject
        } catch (error: Throwable) {
            throw AnimeException.ParseError("the catalogue answer", payload.take(240))
        }

        root["errors"]?.let { errors ->
            val messages = errors.jsonArray.mapNotNull { element ->
                element.jsonObject["message"]?.jsonPrimitive?.content
            }
            throw AnimeException.ParseError(
                reason = "the catalogue answer: " + messages.joinToString("; ").ifEmpty { "unknown error" },
            )
        }

        val data = root["data"]?.jsonObject
            ?: throw AnimeException.ParseError("the catalogue answer", payload.take(240))

        return try {
            json.decodeFromJsonElement<T>(data)
        } catch (error: Throwable) {
            throw AnimeException.ParseError("the catalogue answer", payload.take(240))
        }
    }

    public companion object {
        /**
         * The genres the catalogue is asked about.
         *
         * The list mirrors the one the reference client uses, so the two clients offer the
         * same filters.
         */
        public val GENRES: List<String> = listOf(
            "Action",
            "Adventure",
            "Comedy",
            "Drama",
            "Fantasy",
            "Horror",
            "Mystery",
            "Romance",
            "Sci-Fi",
            "Slice of Life",
            "Sports",
            "Supernatural",
            "Thriller",
        )
    }
}
