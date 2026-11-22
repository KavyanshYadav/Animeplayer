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

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.animeplayer.tv.readSubtitleStyle
import dev.animeplayer.tv.writeSubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitlePreferencesTest {
    @Test
    fun oldPreferencesRetainTheirSizeAndPlacement() {
        val saved = mutablePreferencesOf().apply {
            this[stringPreferencesKey("subtitle-preset")] = "Josei"
            this[stringPreferencesKey("subtitle-size")] = "2"
            this[stringPreferencesKey("subtitle-position")] = "-2"
            this[stringPreferencesKey("subtitle-font")] = "Rounded"
        }
        val style = saved.readSubtitleStyle()

        assertEquals(19, style.textSize)
        assertEquals(SubtitlePosition.Top, style.position)
        assertEquals(-2, style.positionStep)
        assertEquals(SubtitleFont.Rounded, style.font)
    }

    @Test
    fun advancedPreferencesRoundTripAndChoosingAPresetClearsOverrides() {
        val custom = SubtitleStyle(
            preset = SubtitlePreset.Cinema,
            sizeOverrideSp = 26,
            weight = SubtitleWeight.Regular,
            positionOverride = SubtitlePosition.Top,
            positionStep = 2,
            edgeOverride = SubtitleEdge.Outline,
            edgeStrength = SubtitleEdgeStrength.Strong,
            textColorOverride = SubtitleColor.Gold,
            textOpacityPercent = 75,
            plateOverride = SubtitlePlate.Solid,
            plateColorOverride = SubtitleColor.Navy,
            plateOpacityPercent = 40,
        )
        val saved = mutablePreferencesOf()
        saved.writeSubtitleStyle(custom)
        assertEquals(custom, saved.readSubtitleStyle())
        assertEquals(0.4f, saved.readSubtitleStyle().plateAlpha, 0.01f)

        val preset = SubtitleStyle(SubtitlePreset.AnimeOutline)
        saved.writeSubtitleStyle(preset)
        assertEquals(preset, saved.readSubtitleStyle())
        assertEquals(0f, saved.readSubtitleStyle().plateAlpha, 0.01f)
    }
}
