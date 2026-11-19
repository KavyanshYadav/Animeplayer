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
import dev.animeplayer.tv.ui.player.controls.formatPlayerTime
import androidx.compose.ui.unit.dp
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.theme.calculateTvLayout
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FormattingTest {

    @Test
    fun formatsTheTimeOfThePlayer() {
        assertEquals("0:00", formatPlayerTime(0L))
        assertEquals("0:04", formatPlayerTime(4_000L))
        assertEquals("2:04", formatPlayerTime(124_000L))
        assertEquals("1:02:05", formatPlayerTime(3_725_000L))
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
    fun homeShelvesFitWithinTheTvSafeArea() {
        val wide = calculateTvLayout(960.dp, 540.dp)
        val compact = calculateTvLayout(640.dp, 360.dp)
        val visible = TvDimens.VisibleCards
        for (layout in listOf(wide, compact)) {
            val used = layout.cardWidth * visible + TvDimens.RailGap * (visible - 1)
            assertTrue(used <= layout.contentWidth)
        }
        assertEquals(5, wide.columns)
        assertEquals(3, compact.columns)
        assertTrue(wide.cardWidth > compact.cardWidth)
    }
}
