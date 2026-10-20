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
 * The complete episode list of a series.
 *
 * @property totalReported the episode count as reported by the upstream API. This value is
 *   frequently wrong (upstream reports the size of a single page), so it is informational
 *   only — use [episodes] for anything that matters.
 */
public data class EpisodeList(
    public val animeId: AnimeId,
    public val episodes: List<Episode>,
    public val totalReported: Int? = null,
) {
    /** Looks up an episode by its upstream [Episode.number]. */
    public fun find(number: String): Episode? = episodes.firstOrNull { it.number == number }

    /** The episode that follows [number], or `null` at the end of the list. */
    public fun next(number: String): Episode? {
        val idx = episodes.indexOfFirst { it.number == number }
        return if (idx in 0 until episodes.lastIndex) episodes[idx + 1] else null
    }

    /** The episode that precedes [number], or `null` at the start of the list. */
    public fun previous(number: String): Episode? {
        val idx = episodes.indexOfFirst { it.number == number }
        return if (idx > 0) episodes[idx - 1] else null
    }

    /** The last episode of the list. */
    public val latest: Episode? get() = episodes.lastOrNull()
}
