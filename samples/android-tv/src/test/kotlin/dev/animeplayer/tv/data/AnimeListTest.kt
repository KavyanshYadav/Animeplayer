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
package dev.animeplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeListTest {
    @Test
    fun existingFavouritesAppearAsPlanned() {
        assertEquals(listOf("42", "123"), readAnimeList(null, "42,123,42").map { it.id })
        assertEquals(ListStatus.Planned, readAnimeList(null, "42").single().status)
        assertEquals(emptyList<AnimeListEntry>(), readAnimeList("[]", "42"))
    }

    @Test
    fun anUnwatchedGapPreservesLaterEpisodeMarks() {
        val paused = AnimeListEntry("42", ListStatus.Paused).setProgress(5)
        val gap = paused.mark("3", false)
        assertEquals(2, gap.progress)
        assertEquals(false, gap.watched("3"))
        assertEquals(true, gap.watched("5"))
        assertEquals(5, gap.mark("3", true).progress)
        assertEquals(ListStatus.Paused, gap.status)
        assertEquals(5, paused.mark("5.5", true).progress)
        assertNull(AnimeListEntry("42", ListStatus.Planned).watched("1"))
    }
}
