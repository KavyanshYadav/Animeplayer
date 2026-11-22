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

import dev.animeplayer.tv.ui.components.hasSpoilerDetails
import dev.animeplayer.tv.ui.components.withSpoilersHidden
import dev.animeplayer.tv.ui.model.EpisodeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeSpoilersTest {
    @Test
    fun concealmentRemovesMetadataWhilePreservingEpisodeIdentity() {
        val episode = EpisodeRow("4", "Reunion", "A spoiler", "https://example.org/still.jpg", score = 82, watched = .4f)
        val hidden = episode.withSpoilersHidden(true)
        assertEquals("Episode 4", hidden.title)
        assertNull(hidden.description)
        assertNull(hidden.thumbnail)
        assertNull(hidden.score)
        assertEquals(episode.watched, hidden.watched)
        assertFalse(hidden.hasSpoilerDetails())
        assertEquals(episode, episode.withSpoilersHidden(false))
    }
}
