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

import kotlinx.serialization.Serializable

/** The answer of the health endpoint. */
@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val port: Int,
)

/** One search result. */
@Serializable
data class SearchResult(
    val slug: String,
    val numericId: String,
    val title: String,
    val japaneseTitle: String?,
    val posterUrl: String?,
    val year: Int?,
    val genres: List<String>,
    val status: String,
)

@Serializable
data class SearchResponse(val results: List<SearchResult>)

/** The metadata the library knows about one anime. */
@Serializable
data class AnimeResponse(
    val slug: String,
    val numericId: String,
    val title: String,
    val japaneseTitle: String?,
    val posterUrl: String?,
    val description: String?,
    val genres: List<String>,
    val year: Int?,
    val status: String,
    val titles: List<String>,
)

/** One episode of a series. */
@Serializable
data class EpisodeDto(
    val number: String,
    val index: Int,
    val title: String?,
    val durationSeconds: Int?,
    val isSpecial: Boolean,
)

@Serializable
data class EpisodeResponse(
    val slug: String,
    val totalReported: Int?,
    val episodes: List<EpisodeDto>,
)

@Serializable
data class ScheduleItem(
    val title: String,
    val status: String,
    val nextSub: String?,
    val nextDub: String?,
)

@Serializable
data class ScheduleResponse(val entries: List<ScheduleItem>)

/** One entry of the watch history. */
@Serializable
data class HistoryEntryDto(
    val slug: String,
    val numericId: String,
    val title: String,
    val episode: String,
    val updatedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
    val progress: Float,
    val isCompleted: Boolean,
)

@Serializable
data class HistoryResponse(val entries: List<HistoryEntryDto>)

/** The body of a watch history update. */
@Serializable
data class HistoryUpsertRequest(
    val slug: String,
    val title: String = "",
    val episode: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/** The answer of a write endpoint. */
@Serializable
data class OkResponse(val ok: Boolean = true)
