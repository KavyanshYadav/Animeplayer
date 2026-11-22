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

class FillerEpisodesTest {
    @Test
    fun onlyProviderConfirmedEpisodesGetABadge() {
        val response = """{"data":[
            {"mal_id":2,"filler":true},{"mal_id":3,"filler":false},
            {"mal_id":4},{"mal_id":0,"filler":true}
        ]}"""
        assertEquals(setOf(2), parseFillerEpisodes(response))
        assertNull(parseFillerEpisodes("""{"error":"unavailable"}"""))
    }
}
