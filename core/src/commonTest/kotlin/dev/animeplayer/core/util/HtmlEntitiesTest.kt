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

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEntitiesTest {

    @Test
    fun decodesTheEntitiesUpstreamUses() {
        assertEquals("It's", HtmlEntities.decode("It&#039;s"))
        assertEquals("\"quoted\"", HtmlEntities.decode("&quot;quoted&quot;"))
        assertEquals("Tom & Jerry", HtmlEntities.decode("Tom &amp; Jerry"))
    }

    @Test
    fun decodesNumericEntities() {
        assertEquals("A", HtmlEntities.decode("&#65;"))
        assertEquals("A", HtmlEntities.decode("&#x41;"))
    }

    @Test
    fun leavesUnknownEntitiesAlone() {
        assertEquals("&unknown;", HtmlEntities.decode("&unknown;"))
        assertEquals("100% & rising", HtmlEntities.decode("100% & rising"))
    }

    @Test
    fun leavesPlainTextUnchanged() {
        assertEquals("One Piece", HtmlEntities.decode("One Piece"))
    }
}
