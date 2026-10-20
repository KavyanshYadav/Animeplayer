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
package dev.animeplayer.core.model

/**
 * A single episode of a series.
 *
 * @property number the episode number as displayed by the upstream site. Specials and
 *   fractional episodes keep their upstream spelling (for example `12.5`).
 * @property index the zero based position of this episode inside [EpisodeList.episodes].
 */
public data class Episode(
    public val animeId: AnimeId,
    public val number: String,
    public val index: Int,
    public val title: String? = null,
    public val durationSeconds: Int? = null,
) {
    /** `true` when the episode number is not a plain integer (specials, recaps, ...). */
    public val isSpecial: Boolean
        get() = number.toIntOrNull() == null

    /** The numeric part of [number], or `null` for specials. */
    public val numericNumber: Int?
        get() = number.toIntOrNull()

    override fun toString(): String = "${animeId.slug} episode $number"
}
