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

/**
 * One episode of a series, as the television displays it.
 *
 * @property number the number of the episode, as the upstream site spells it.
 * @property title the title of the episode.
 * @property description the description of the episode.
 * @property thumbnail the artwork of the episode.
 * @property duration the length of the episode, in seconds.
 * @property score the score of the episode.
 * @property watched how far the episode was watched, in the `0..1` range.
 * @property airDate when the episode aired, in epoch seconds.
 */
public data class EpisodeRow(
    val number: String,
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,
    val score: Int? = null,
    val watched: Float = 0f,
    val airDate: Long? = null,
    val isFiller: Boolean = false,
) {
    /** `true` when the episode was watched to the end. */
    public val isWatched: Boolean get() = watched >= 0.9f

    /** The line about the date, for example `in 3 days` or `2 weeks ago`. */
    public val airDateLine: String?
        get() = airDate?.let { relativeTime(it) }
}
