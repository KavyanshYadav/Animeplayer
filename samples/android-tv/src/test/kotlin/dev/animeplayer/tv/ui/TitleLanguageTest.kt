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

import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.tv.ui.model.TitleLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleLanguageTest {
    @Test
    fun selectsNamedTitlesAndFallsBackWithoutGuessingArrayOrder() {
        val media = CatalogMedia("42", listOf("Fallback"), englishTitle = "English",
            romajiTitle = "Romanized", nativeTitle = "日本語")
        assertEquals("English", TitleLanguage.English.select(media))
        assertEquals("Romanized", TitleLanguage.Romaji.select(media))
        assertEquals("日本語", TitleLanguage.Native.select(media))
        assertEquals("Fallback", TitleLanguage.Native.select(media.copy(nativeTitle = null)))
    }
}
