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
package dev.animeplayer.sources.anilist.internal

import dev.animeplayer.core.model.CatalogQuery
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The GraphQL documents of the catalogue.
 *
 * The catalogue is a GraphQL API, so the "parsing" here is the mapping between a
 * [CatalogQuery] and a document plus a set of variables. The documents are built
 * dynamically, because a filter that is not used must not appear in the query at all —
 * the catalogue rejects a variable that is declared but never passed.
 */
public object AniListQueries {

    /** The fields every entry query selects. */
    private const val MEDIA_FIELDS = """
        id
        title { romaji english native }
        coverImage { large color }
        bannerImage
        averageScore
        genres
        seasonYear
        episodes
        duration
        status
        format
        description(asHtml: false)
        popularity
        nextAiringEpisode { airingAt episode }
    """

    /**
     * The document of a catalogue page.
     *
     * @return the document and the variables that go with it.
     */
    public fun page(query: CatalogQuery): Pair<String, Map<String, JsonElement>> {
        val filters = mutableListOf("type: ANIME")
        val variables = linkedMapOf<String, JsonElement>(
            "page" to JsonPrimitive(query.page),
            "perPage" to JsonPrimitive(query.perPage),
        )
        val declarations = mutableListOf("\$page: Int", "\$perPage: Int")

        query.search?.takeIf { it.isNotBlank() }?.let {
            filters += "search: \$search"
            declarations += "\$search: String"
            variables["search"] = JsonPrimitive(it)
        }
        query.sort?.takeIf { it.isNotBlank() }?.let {
            filters += "sort: \$sort"
            declarations += "\$sort: [MediaSort]"
            variables["sort"] = JsonArray(listOf(JsonPrimitive(it)))
        }
        query.genre?.takeIf { it.isNotBlank() }?.let {
            filters += "genre_in: [\$genre]"
            declarations += "\$genre: String"
            variables["genre"] = JsonPrimitive(it)
        }
        query.season?.takeIf { it.isNotBlank() }?.let {
            filters += "season: \$season"
            declarations += "\$season: MediaSeason"
            variables["season"] = JsonPrimitive(it)
        }
        query.seasonYear?.let {
            filters += "seasonYear: \$year"
            declarations += "\$year: Int"
            variables["year"] = JsonPrimitive(it)
        }
        query.format?.takeIf { it.isNotBlank() }?.let {
            filters += "format: \$format"
            declarations += "\$format: MediaFormat"
            variables["format"] = JsonPrimitive(it)
        }
        query.status?.takeIf { it.isNotBlank() }?.let {
            filters += "status: \$status"
            declarations += "\$status: MediaStatus"
            variables["status"] = JsonPrimitive(it)
        }

        val document = """
            query (${declarations.joinToString(", ")}) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage currentPage total }
                media(${filters.joinToString(", ")}) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        return document to variables
    }

    /** The document of a single entry. */
    public fun media(id: String): Pair<String, Map<String, JsonElement>> {
        val document = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                $MEDIA_FIELDS
              }
            }
        """.trimIndent()
        return document to mapOf("id" to JsonPrimitive(id.toLongOrNull() ?: 0L))
    }

    /** The document of several entries at once. */
    public fun mediaList(ids: List<String>): Pair<String, Map<String, JsonElement>> {
        val document = """
            query (${'$'}ids: [Int], ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage currentPage total }
                media(id_in: ${'$'}ids, type: ANIME, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return document to mapOf(
            "ids" to JsonArray(ids.map { JsonPrimitive(it.toLongOrNull() ?: 0L) }),
            "perPage" to JsonPrimitive(ids.size.coerceAtLeast(1)),
        )
    }

    /** The document of the episodes of an entry. */
    public fun episodes(id: String): Pair<String, Map<String, JsonElement>> {
        val document = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                episodes(sort: ASC, perPage: 60) {
                  nodes {
                    number
                    title
                    description(asHtml: false)
                    thumbnail
                    airDate
                    averageScore
                    length
                  }
                }
              }
            }
        """.trimIndent()
        return document to mapOf("id" to JsonPrimitive(id.toLongOrNull() ?: 0L))
    }

    /** The document of the relations of an entry. */
    public fun relations(id: String): Pair<String, Map<String, JsonElement>> {
        val document = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                relations {
                  edges {
                    relationType
                    node {
                      id
                      title { romaji english native }
                      coverImage { large color }
                      bannerImage
                      format
                      seasonYear
                      averageScore
                    }
                  }
                }
              }
            }
        """.trimIndent()
        return document to mapOf("id" to JsonPrimitive(id.toLongOrNull() ?: 0L))
    }

    /** The document of the recommendations of an entry. */
    public fun recommendations(id: String): Pair<String, Map<String, JsonElement>> {
        val document = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                recommendations(sort: RATING_DESC, perPage: 12) {
                  nodes {
                    $MEDIA_FIELDS
                  }
                }
              }
            }
        """.trimIndent()
        return document to mapOf("id" to JsonPrimitive(id.toLongOrNull() ?: 0L))
    }

    /** A variable that is explicitly null. */
    public fun nullVariable(): JsonElement = JsonNull
}
