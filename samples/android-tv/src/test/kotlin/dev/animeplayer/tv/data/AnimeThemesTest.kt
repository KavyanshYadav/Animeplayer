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
import org.junit.Test

class AnimeThemesTest {
    @Test
    fun onlyExplicitThemesWithTrustedVideoHostsAreShown() {
        val valid = "https://v.animethemes.moe/theme.webm"
        val payload = """{"anime":[{"animethemes":[
            {"slug":"OP1","song":{"title":"Opening"},"animethemeentries":[{"videos":[{"link":"https://example.invalid/theme"}]}]},
            {"slug":"ED1","animethemeentries":[{"videos":[{"link":"$valid"}]}]}
        ]}]}"""
        assertEquals(listOf(AnimeTheme("ED1", null, valid)), parseAnimeThemes(payload))
    }
}
