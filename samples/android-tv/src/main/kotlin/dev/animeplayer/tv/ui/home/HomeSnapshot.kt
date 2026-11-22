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
package dev.animeplayer.tv.ui.home

import android.util.AtomicFile
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.CatalogStatus
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A bounded startup copy. Coil continues to own the downloaded image bytes. */
internal class HomeSnapshotStore(file: File, private val usesTmdb: Boolean) {
    private val storage = AtomicFile(file)

    suspend fun read(): HomeSnapshot? = withContext(Dispatchers.IO) {
        runCatching { Json.decodeFromString<HomeSnapshot>(storage.readFully().decodeToString()) }
            .getOrNull()?.takeIf { it.isReusable(usesTmdb, System.currentTimeMillis()) }
    }

    suspend fun write(rows: Map<String, List<CatalogMedia>>, featured: List<FeaturedSnapshot>) =
        withContext(Dispatchers.IO) {
            val snapshot = HomeSnapshot(
                System.currentTimeMillis(), usesTmdb, rows.mapValues { (_, media) -> media.map(::HomeMedia) }, featured,
            )
            val bytes = Json.encodeToString(snapshot).encodeToByteArray()
            val output = storage.startWrite()
            try {
                output.write(bytes)
                storage.finishWrite(output)
            } catch (error: Exception) {
                storage.failWrite(output)
                throw error
            }
        }
}

@Serializable
internal data class HomeSnapshot(
    val savedAt: Long,
    val usesTmdb: Boolean,
    val rows: Map<String, List<HomeMedia>>,
    val featured: List<FeaturedSnapshot>,
) {
    fun isReusable(usingTmdb: Boolean, now: Long): Boolean =
        usesTmdb == usingTmdb && now - savedAt in 0..7.days.inWholeMilliseconds
}

@Serializable
internal data class FeaturedSnapshot(val media: HomeMedia, val logo: String)

@Serializable
internal data class HomeMedia(
    val id: String,
    val titles: List<String>,
    val description: String?,
    val poster: String?,
    val backdrop: String?,
    val color: String?,
    val status: String?,
    val airing: Boolean,
    val finished: Boolean,
    val upcoming: Boolean,
    val format: String?,
    val genres: List<String>,
    val year: Int?,
    val episodes: Int?,
    val duration: Int?,
    val score: Int?,
    val popularity: Int?,
    val nextAt: Long?,
    val nextEpisode: Int?,
    val startDate: String?,
    val seasonLinks: List<String>,
) {
    constructor(media: CatalogMedia) : this(
        media.id, media.titles, media.description, media.posterUrl, media.bannerUrl, media.posterColor,
        media.status.raw, media.status.isAiring, media.status.isFinished, media.status.isUpcoming,
        media.format, media.genres, media.seasonYear, media.episodeCount, media.durationMinutes,
        media.score, media.popularity, media.nextAiringAt, media.nextAiringEpisode, media.startDate, media.seasonLinks,
    )

    fun toMedia() = CatalogMedia(
        id = id, titles = titles, description = description, posterUrl = poster, bannerUrl = backdrop,
        posterColor = color, status = CatalogStatus(status, airing, finished, upcoming), format = format,
        genres = genres, seasonYear = year, episodeCount = episodes, durationMinutes = duration,
        score = score, popularity = popularity, nextAiringAt = nextAt, nextAiringEpisode = nextEpisode,
        startDate = startDate, seasonLinks = seasonLinks,
    )
}
