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
package dev.animeplayer.tv.ui

import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.player.formatTime
import dev.animeplayer.tv.ui.theme.TvDimens
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FormattingTest {

    @Test
    fun formatsTheTimeOfThePlayer() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:04", formatTime(4_000L))
        assertEquals("2:04", formatTime(124_000L))
        assertEquals("1:02:05", formatTime(3_725_000L))
    }

    @Test
    fun buildsTheInfoLineOfACard() {
        val card = CatalogCard(
            id = "1",
            title = "One Piece",
            year = 1999,
            format = "TV_SHORT",
            score = 87,
        )
        assertEquals("1999 · TV SHORT · 87 %", card.infoLine)
    }

    @Test
    fun leavesTheInfoLineEmptyWhenThereIsNothingToShow() {
        assertEquals("", CatalogCard(id = "1", title = "One Piece").infoLine)
    }

    @Test
    fun keepsSixCardsOnTheScreen() {
        // The width of a card is derived from the width of the screen, so the same number
        // of cards is visible on every television.
        val width = 960
        val margin = 48
        val gap = 12
        val visible = TvDimens.VisibleCards
        val cardWidth = (width - margin * 2 - gap * (visible - 1)) / visible
        assertEquals(134, cardWidth)
        assertTrue(cardWidth * visible + gap * (visible - 1) + margin * 2 <= width)
    }
}
