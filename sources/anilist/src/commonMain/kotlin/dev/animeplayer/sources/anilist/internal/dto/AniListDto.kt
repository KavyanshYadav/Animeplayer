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
package dev.animeplayer.sources.anilist.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The `title` selection of the catalogue. */
@Serializable
public data class TitleDto(
    @SerialName("romaji") public val romaji: String? = null,
    @SerialName("english") public val english: String? = null,
    @SerialName("native") public val native: String? = null,
)

/** The `coverImage` selection of the catalogue. */
@Serializable
public data class CoverImageDto(
    @SerialName("large") public val large: String? = null,
    @SerialName("color") public val color: String? = null,
)

/** The `nextAiringEpisode` selection of the catalogue. */
@Serializable
public data class NextAiringEpisodeDto(
    @SerialName("airingAt") public val airingAt: Long? = null,
    @SerialName("episode") public val episode: Int? = null,
)

/** One entry of the catalogue. */
@Serializable
public data class MediaDto(
    @SerialName("id") public val id: Long? = null,
    @SerialName("title") public val title: TitleDto? = null,
    @SerialName("coverImage") public val coverImage: CoverImageDto? = null,
    @SerialName("bannerImage") public val bannerImage: String? = null,
    @SerialName("averageScore") public val averageScore: Int? = null,
    @SerialName("genres") public val genres: List<String>? = null,
    @SerialName("seasonYear") public val seasonYear: Int? = null,
    @SerialName("episodes") public val episodes: Int? = null,
    @SerialName("duration") public val duration: Int? = null,
    @SerialName("status") public val status: String? = null,
    @SerialName("format") public val format: String? = null,
    @SerialName("description") public val description: String? = null,
    @SerialName("popularity") public val popularity: Int? = null,
    @SerialName("nextAiringEpisode") public val nextAiringEpisode: NextAiringEpisodeDto? = null,
)

/** The `pageInfo` selection of the catalogue. */
@Serializable
public data class PageInfoDto(
    @SerialName("hasNextPage") public val hasNextPage: Boolean = false,
    @SerialName("currentPage") public val currentPage: Int = 1,
    @SerialName("total") public val total: Int = 0,
)

/** The `Page` selection of the catalogue. */
@Serializable
public data class PageDto(
    @SerialName("pageInfo") public val pageInfo: PageInfoDto? = null,
    @SerialName("media") public val media: List<MediaDto>? = null,
)

/** One episode of a catalogue entry. */
@Serializable
public data class EpisodeDto(
    @SerialName("number") public val number: Int? = null,
    @SerialName("title") public val title: String? = null,
    @SerialName("description") public val description: String? = null,
    @SerialName("thumbnail") public val thumbnail: String? = null,
    @SerialName("airDate") public val airDate: Long? = null,
    @SerialName("averageScore") public val averageScore: Int? = null,
    @SerialName("length") public val length: Int? = null,
)

/** The `episodes` connection of a catalogue entry. */
@Serializable
public data class EpisodeConnectionDto(
    @SerialName("nodes") public val nodes: List<EpisodeDto>? = null,
)

/** One edge of the `relations` connection. */
@Serializable
public data class RelationEdgeDto(
    @SerialName("relationType") public val relationType: String? = null,
    @SerialName("node") public val node: MediaDto? = null,
)

/** The `relations` connection of a catalogue entry. */
@Serializable
public data class RelationConnectionDto(
    @SerialName("edges") public val edges: List<RelationEdgeDto>? = null,
)

/** The `recommendations` connection of a catalogue entry. */
@Serializable
public data class RecommendationConnectionDto(
    @SerialName("nodes") public val nodes: List<MediaDto>? = null,
)

/** The envelope of a `Page` query. */
@Serializable
public data class PageEnvelope(
    @SerialName("Page") public val page: PageDto? = null,
)

/** The envelope of a `Media` query. */
@Serializable
public data class MediaEnvelope(
    @SerialName("Media") public val media: MediaDto? = null,
)

/** The envelope of a query that returns the episodes of an entry. */
@Serializable
public data class EpisodeEnvelope(
    @SerialName("Media") public val media: MediaWithEpisodesDto? = null,
)

/** An entry with its episodes. */
@Serializable
public data class MediaWithEpisodesDto(
    @SerialName("episodes") public val episodes: EpisodeConnectionDto? = null,
)

/** The envelope of a query that returns the relations of an entry. */
@Serializable
public data class RelationEnvelope(
    @SerialName("Media") public val media: MediaWithRelationsDto? = null,
)

/** An entry with its relations. */
@Serializable
public data class MediaWithRelationsDto(
    @SerialName("relations") public val relations: RelationConnectionDto? = null,
)

/** The envelope of a query that returns the recommendations of an entry. */
@Serializable
public data class RecommendationEnvelope(
    @SerialName("Media") public val media: MediaWithRecommendationsDto? = null,
)

/** An entry with its recommendations. */
@Serializable
public data class MediaWithRecommendationsDto(
    @SerialName("recommendations") public val recommendations: RecommendationConnectionDto? = null,
)

/** The envelope of a query that returns several entries. */
@Serializable
public data class MediaListEnvelope(
    @SerialName("Page") public val page: PageDto? = null,
)

/** The GraphQL request body. */
@Serializable
public data class GraphQlRequest(
    @SerialName("query") public val query: String,
    @SerialName("variables") public val variables: Map<String, JsonElement> = emptyMap(),
)
