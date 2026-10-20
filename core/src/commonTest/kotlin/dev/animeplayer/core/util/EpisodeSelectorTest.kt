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
package dev.animeplayer.core.util

import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.EpisodeList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpisodeSelectorTest {

    private val animeId = AnimeId("one-piece-1", "1")
    private val list = EpisodeList(
        animeId = animeId,
        episodes = listOf(1, 2, 3, 4, 5).map { Episode(animeId, it.toString(), it - 1) },
    )

    @Test
    fun selectsASingleEpisode() {
        assertEquals(listOf("3"), EpisodeSelector.expand(list, "3").map { it.number })
    }

    @Test
    fun selectsARange() {
        assertEquals(listOf("2", "3", "4"), EpisodeSelector.expand(list, "2-4").map { it.number })
    }

    @Test
    fun selectsAnOpenEndedRange() {
        assertEquals(listOf("3", "4", "5"), EpisodeSelector.expand(list, "3-").map { it.number })
    }

    @Test
    fun selectsTheLatestEpisode() {
        assertEquals(listOf("5"), EpisodeSelector.expand(list, "-1").map { it.number })
    }

    @Test
    fun selectsAnExplicitList() {
        assertEquals(listOf("1", "3", "5"), EpisodeSelector.expand(list, "1 3 5").map { it.number })
    }

    @Test
    fun returnsNothingForUnknownEpisodes() {
        assertTrue(EpisodeSelector.expand(list, "99").isEmpty())
        assertTrue(EpisodeSelector.expand(list, "").isEmpty())
    }
}
