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
package dev.animeplayer.tv.ui.player

import dev.animeplayer.core.model.SkipSegments
import dev.animeplayer.core.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndingSkipTest {
    @Test
    fun endingRequiresTimingAndNeverSeeksPastTheDuration() {
        val before = TvPlayerState(positionMs = 10_000, durationMs = 31_000,
            skip = SkipSegments(outro = TimeRange(20, 40)))
        assertNull(before.endingTargetMs)
        assertEquals(31_000L, before.copy(positionMs = 20_000).endingTargetMs)
        assertNull(before.copy(positionMs = 31_000).endingTargetMs)
        assertNull(before.copy(positionMs = 22_000, skip = null).endingTargetMs)
    }
}
