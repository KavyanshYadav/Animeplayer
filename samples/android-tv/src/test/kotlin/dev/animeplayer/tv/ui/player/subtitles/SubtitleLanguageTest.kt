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

import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.StreamVariant
import dev.animeplayer.core.model.SubtitleTrack
import dev.animeplayer.core.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleLanguageTest {
    @Test
    fun matchesLanguageVariantsAndFallsBackToTheProvidedDefault() {
        val english = SubtitleTrack("eng", "English", "english")
        val signs = SubtitleTrack("en-US", "English signs", "signs", isDefault = true)
        val source = PlayableSource(
            StreamVariant(VideoQuality(0, 720, "720p"), "video"),
            subtitles = listOf(signs, english),
            referer = "https://example.invalid",
        )
        assertEquals("english", source.withSubtitleLanguage(SubtitleLanguage.English).defaultSubtitle?.url)
        assertEquals("signs", source.withSubtitleLanguage(SubtitleLanguage.Japanese).defaultSubtitle?.url)
    }
}
