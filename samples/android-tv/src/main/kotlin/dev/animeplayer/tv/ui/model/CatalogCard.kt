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
package dev.animeplayer.tv.ui.model

import androidx.compose.ui.graphics.Color
import dev.animeplayer.core.model.CatalogMedia

/**
 * A series, ready to be drawn.
 *
 * The catalogue gives a dozen fields; a card needs five. The model in between keeps the
 * screens free of null checks and of formatting.
 *
 * @property id the identifier of the entry in the catalogue.
 * @property title the title of the series.
 * @property description the description of the series.
 * @property posterUrl the URL of the poster.
 * @property bannerUrl the URL of the wide artwork.
 * @property posterColor the colour of the poster, used for the placeholder.
 * @property metadata the badges of the series — the format, the status, the score.
 * @property year the year the series came out.
 */
public data class CatalogCard(
    val id: String,
    val title: String,
    val description: String? = null,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val posterColor: Color? = null,
    val metadata: List<String> = emptyList(),
    val year: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val score: Int? = null,
    val genres: List<String> = emptyList(),
    val episodeCount: Int? = null,
) {
    /** The metadata line of a card, for example `2026 · TV Series · 87 %`. */
    public val infoLine: String
        get() = listOfNotNull(
            year?.toString(),
            format?.replace('_', ' '),
            score?.let { "$it %" },
        ).joinToString(" · ")
}

/**
 * Maps a catalogue entry onto a card.
 */
public fun CatalogMedia.toCard(): CatalogCard = CatalogCard(
    id = id,
    title = title,
    description = description,
    posterUrl = posterUrl,
    bannerUrl = bannerUrl,
    posterColor = posterColor?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    },
    metadata = buildList {
        episodeCount?.let { add("$it Episodes") }
        format?.let { add(it.replace('_', ' ')) }
        status?.raw?.let { add(it.replace('_', ' ')) }
        seasonYear?.let { add(it.toString()) }
    },
    year = seasonYear,
    format = format,
    status = status.raw,
    score = score,
    genres = genres,
    episodeCount = episodeCount,
)
