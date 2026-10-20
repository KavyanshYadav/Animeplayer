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

/** Publication state of a series. */
public enum class ReleaseStatus {
    Ongoing,
    Finished,
    Unknown,
    ;

    public companion object {
        /** Maps the free form status strings used by upstream sites onto [ReleaseStatus]. */
        public fun fromRaw(raw: String?): ReleaseStatus = when (raw?.trim()?.lowercase()) {
            "ongoing", "airing", "releasing", "new" -> Ongoing
            "finished", "completed", "ended" -> Finished
            else -> Unknown
        }
    }
}

/**
 * A series as returned by a source.
 *
 * @property id the identifier used to look the series up again.
 * @property title the primary (usually english) title.
 * @property japaneseTitle the native title, when the source exposes it.
 */
public data class Anime(
    public val id: AnimeId,
    public val title: String,
    public val japaneseTitle: String? = null,
    public val posterUrl: String? = null,
    public val description: String? = null,
    public val genres: List<String> = emptyList(),
    public val year: Int? = null,
    public val status: ReleaseStatus = ReleaseStatus.Unknown,
) {
    /** All known titles, primary first. */
    public val titles: List<String>
        get() = listOfNotNull(title, japaneseTitle).distinct()
}
