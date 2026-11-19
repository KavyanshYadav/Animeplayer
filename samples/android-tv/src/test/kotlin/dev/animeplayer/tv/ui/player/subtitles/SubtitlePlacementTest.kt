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
package dev.animeplayer.tv.ui.player.subtitles

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePlacementTest {
    @Test
    fun savedLowPositionNeverProducesNegativePaddingWhenControlsHide() {
        val style = SubtitleStyle(positionStep = -3)
        val withControls = style.placement(topPadding = 120.dp, bottomPadding = 190.dp)
        val withoutControls = style.placement(topPadding = 60.dp, bottomPadding = 60.dp)

        assertEquals(Alignment.BottomCenter, withoutControls.alignment)
        assertEquals(118.dp, withControls.bottom)
        assertEquals(12.dp, withoutControls.bottom)
    }

    @Test
    fun verticalButtonsMoveTopAndCenterCaptionsInTheSameScreenDirection() {
        val top = SubtitleStyle(preset = SubtitlePreset.Josei)
        val center = SubtitleStyle(preset = SubtitlePreset.Sign)
        val raisedTop = top.higher().placement(60.dp, 60.dp)
        val raisedCenter = center.higher().placement(60.dp, 60.dp)

        assertEquals(Alignment.TopCenter, raisedTop.alignment)
        assertEquals(36.dp, raisedTop.top)
        assertEquals(Alignment.Center, raisedCenter.alignment)
        assertEquals((-24).dp, raisedCenter.centerOffset)
        assertEquals(24.dp, center.lower().placement(60.dp, 60.dp).centerOffset)
    }

    @Test
    fun staleSavedOffsetsStayWithinThePlayerBounds() {
        assertEquals(72.dp, SubtitleStyle(positionStep = Int.MAX_VALUE).verticalOffset)
        assertEquals((-72).dp, SubtitleStyle(positionStep = Int.MIN_VALUE).verticalOffset)
        assertTrue(SubtitleStyle(sizeStep = Int.MAX_VALUE).textSize > SubtitleStyle().textSize)
    }
}
