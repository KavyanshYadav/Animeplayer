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
package dev.animeplayer.tv.ui.home

import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.CatalogStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days

class HomeSnapshotTest {
    @Test
    fun roundTripKeepsArtworkSeasonIdentityAndTrailer() {
        val media = CatalogMedia(
            id = "131",
            titles = listOf("The Series", "Alternate title"),
            status = CatalogStatus("RELEASING", isAiring = true),
            bannerUrl = "https://image.tmdb.org/t/p/w1280/example.jpg",
            seasonLinks = listOf("115"),
            nextAiringEpisode = 3,
            youtubeTrailerId = "pv8A7eubPQQ",
        )
        val logo = "https://image.tmdb.org/t/p/w500/logo.png"
        val snapshot = HomeSnapshot(150L, true, mapOf("Trending Now" to listOf(HomeMedia(media))),
            listOf(FeaturedSnapshot(HomeMedia(media), logo)), hasTrailerMetadata = true)

        val restored = Json.decodeFromString<HomeSnapshot>(Json.encodeToString(snapshot))

        assertEquals(media, restored.rows.getValue("Trending Now").single().toMedia())
        assertEquals(media, restored.featured.single().media.toMedia())
        assertEquals(logo, restored.featured.single().logo)
        assertTrue(restored.hasTrailerMetadata)
    }

    @Test
    fun olderSnapshotCanBeRestoredAndMarkedForTrailerMetadataUpgrade() {
        val restored = Json.decodeFromString<HomeSnapshot>(
            """{"savedAt":150,"usesTmdb":true,"rows":{},"featured":[]}""",
        )

        assertTrue(restored.isReusable(usingTmdb = true, now = 160L))
        assertFalse(restored.hasTrailerMetadata)
    }

    @Test
    fun rejectsExpiredFutureDatedAndDifferentlyConfiguredSnapshots() {
        val snapshot = HomeSnapshot(10L, true, emptyMap(), emptyList())

        assertTrue(snapshot.isReusable(usingTmdb = true, now = 20L))
        assertFalse(snapshot.isReusable(usingTmdb = false, now = 20L))
        assertFalse(snapshot.isReusable(usingTmdb = true, now = 1L))
        assertFalse(snapshot.isReusable(usingTmdb = true, now = 8.days.inWholeMilliseconds))
    }
}
