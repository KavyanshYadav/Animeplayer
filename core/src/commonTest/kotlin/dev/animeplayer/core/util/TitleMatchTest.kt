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
import kotlin.test.assertTrue

class TitleMatchTest {

    @Test
    fun normalizesCaseAndPunctuation() {
        assertEquals("one piece", TitleMatch.normalize("One Piece"))
        assertEquals("one piece", TitleMatch.normalize("  ONE   PIECE!! "))
        assertEquals("fate stay night", TitleMatch.normalize("Fate/stay night"))
        assertEquals("re zero kara hajimeru", TitleMatch.normalize("Re:Zero kara Hajimeru"))
    }

    @Test
    fun foldsAccents() {
        assertEquals("cafe", TitleMatch.normalize("Café"))
        assertEquals("kimi no na wa", TitleMatch.normalize("Kimi no Na wa"))
    }

    @Test
    fun keepsNonLatinTitles() {
        assertEquals("薬屋のひとりごと", TitleMatch.normalize("薬屋のひとりごと"))
    }

    @Test
    fun scoresAnExactMatchAsOne() {
        assertEquals(1.0, TitleMatch.score("One Piece", "one piece"))
    }

    @Test
    fun scoresAPrefixAsAlmostAnExactMatch() {
        assertEquals(0.85, TitleMatch.score("One Piece", "One Piece Film"))
    }

    @Test
    fun scoresAnUnrelatedPairNearZero() {
        assertTrue(TitleMatch.score("One Piece", "Bleach") < 0.2)
    }

    @Test
    fun scoresTheShorterTitleHigher() {
        val shorter = TitleMatch.score("One Piece", "One Piece Film")
        val longer = TitleMatch.score("One Piece Film", "One Piece")
        assertTrue(shorter > 0.0)
        assertTrue(longer > 0.0)
    }

    @Test
    fun picksTheBestOfSeveralCandidates() {
        val best = TitleMatch.best(listOf("Bleach", "One Piece", "Naruto"), "One Piece")
        assertEquals(1.0, best)
    }

    @Test
    fun ignoresBlankCandidates() {
        assertEquals(0.0, TitleMatch.best(listOf(null, "", "  "), "One Piece"))
    }

    @Test
    fun comparesEveryCandidateAgainstEveryTarget() {
        val best = TitleMatch.best(
            candidates = listOf("Bleach", "One Piece"),
            targets = listOf(null, "BLEACH"),
        )
        assertEquals(1.0, best)
    }
}
