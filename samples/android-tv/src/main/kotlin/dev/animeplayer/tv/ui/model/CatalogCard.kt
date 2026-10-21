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
import kotlin.time.Clock

/**
 * A series, ready to be drawn.
 *
 * The catalogue gives a dozen fields; a card needs a handful. The model in between keeps
 * the screens free of null checks and of formatting.
 *
 * @property id the identifier of the entry in the catalogue.
 * @property title the title of the series.
 * @property description the description of the series.
 * @property posterUrl the URL of the poster.
 * @property bannerUrl the URL of the wide artwork.
 * @property posterColor the colour of the poster, used for the placeholder.
 * @property metadata the badges of the series — the format, the status, the score.
 * @property year the year the series came out.
 * @property status the state of the series: releasing, finished or not yet released.
 * @property durationMinutes the length of one episode.
 * @property nextAiringAt when the next episode airs, in epoch seconds.
 * @property nextAiringEpisode the number of the next episode.
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
    val durationMinutes: Int? = null,
    val nextAiringAt: Long? = null,
    val nextAiringEpisode: Int? = null,
) {
    /** The metadata line of a card, for example `2026 · TV Series · 87 %`. */
    public val infoLine: String
        get() = listOfNotNull(
            year?.toString(),
            format?.replace('_', ' '),
            score?.let { "$it %" },
        ).joinToString(" · ")

    /**
     * The line about the state of the series.
     *
     * A series that is still running is worth a line of its own: the user wants to know
     * whether there is more to come, and when.
     */
    public val statusLine: String?
        get() {
            val next = nextEpisodeLine
            if (next != null) return next
            return when {
                status == null -> null
                status.equals("RELEASING", ignoreCase = true) -> "Airing"
                status.equals("NOT_YET_RELEASED", ignoreCase = true) -> "Upcoming"
                status.equals("FINISHED", ignoreCase = true) -> "Finished"
                status.equals("HIATUS", ignoreCase = true) -> "On hiatus"
                else -> null
            }
        }

    /**
     * The line about the next episode, for example `Episode 12 in 3 days`.
     */
    public val nextEpisodeLine: String?
        get() {
            val airingAt = nextAiringAt ?: return null
            val number = nextAiringEpisode
            val when_ = relativeTime(airingAt)
            return if (number != null) "Episode $number $when_" else "Next episode $when_"
        }

    /** `true` when the series is still running. */
    public val isAiring: Boolean
        get() = status.equals("RELEASING", ignoreCase = true) ||
            (nextAiringAt != null && nextAiringAt > Clock.System.now().epochSeconds)
}

/**
 * `in 3 days`, `tomorrow`, `today`, `2 weeks ago`.
 */
public fun relativeTime(epochSeconds: Long): String {
    val now = Clock.System.now().epochSeconds
    val seconds = epochSeconds - now
    val future = seconds > 0
    val absolute = kotlin.math.abs(seconds)
    val minutes = absolute / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30
    val years = days / 365
    val phrase = when {
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes minutes"
        hours < 24 -> "$hours hours"
        days == 1L -> "1 day"
        days < 7 -> "$days days"
        days < 30 -> "$weeks weeks"
        days < 365 -> "$months months"
        else -> "$years years"
    }
    return when {
        phrase == "now" -> if (future) "now" else "now"
        future -> "in $phrase"
        else -> "$phrase ago"
    }
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
        status.raw?.let { add(it.replace('_', ' ')) }
        seasonYear?.let { add(it.toString()) }
    },
    year = seasonYear,
    format = format,
    status = status.raw,
    score = score,
    genres = genres,
    episodeCount = episodeCount,
    durationMinutes = durationMinutes,
    nextAiringAt = nextAiringAt,
    nextAiringEpisode = nextAiringEpisode,
)
