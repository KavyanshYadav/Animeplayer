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
package dev.animeplayer.sources.anilist

import dev.animeplayer.core.model.CatalogQuery
import dev.animeplayer.core.model.CatalogSort
import dev.animeplayer.sources.anilist.internal.AniListQueries
import dev.animeplayer.sources.anilist.internal.dto.MediaDto
import dev.animeplayer.sources.anilist.internal.dto.PageDto
import dev.animeplayer.sources.anilist.internal.dto.TitleDto
import dev.animeplayer.sources.anilist.internal.parser.AniListParser
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniListParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapsAnEntry() {
        val dto = json.decodeFromString<MediaDto>(
            """
            {
              "id": 21,
              "title": { "romaji": "One Piece", "english": "One Piece", "native": "ONE PIECE" },
              "coverImage": { "large": "https://example.test/cover.jpg", "color": "#ff0000" },
              "bannerImage": "https://example.test/banner.jpg",
              "averageScore": 88,
              "genres": ["Action", "Adventure"],
              "seasonYear": 1999,
              "episodes": 1100,
              "duration": 24,
              "status": "RELEASING",
              "format": "TV",
              "description": "The story of Monkey D. Luffy.",
              "popularity": 12345
            }
            """.trimIndent(),
        )

        val media = AniListParser.media(dto)
        assertNotNull(media)
        assertEquals("21", media.id)
        assertEquals("One Piece", media.title)
        assertEquals(listOf("One Piece", "ONE PIECE"), media.titles)
        assertEquals("https://example.test/cover.jpg", media.posterUrl)
        assertEquals("https://example.test/banner.jpg", media.bannerUrl)
        assertEquals("#ff0000", media.posterColor)
        assertEquals(listOf("Action", "Adventure"), media.genres)
        assertEquals(1999, media.seasonYear)
        assertEquals(1100, media.episodeCount)
        assertEquals(24, media.durationMinutes)
        assertEquals(88, media.score)
        assertTrue(media.status.isAiring)
        assertFalse(media.status.isFinished)
        assertTrue(media.hasBanner)
    }

    @Test
    fun dropsAnEntryWithoutAnIdentifier() {
        assertNull(AniListParser.media(MediaDto(id = null)))
    }

    @Test
    fun mapsAPage() {
        val dto = json.decodeFromString<PageDto>(
            """
            {
              "pageInfo": { "hasNextPage": true, "currentPage": 2, "total": 120 },
              "media": [ { "id": 1, "title": { "romaji": "A" } } ]
            }
            """.trimIndent(),
        )

        val page = AniListParser.page(dto)
        assertEquals(1, page.media.size)
        assertTrue(page.hasNextPage)
        assertEquals(2, page.currentPage)
        assertEquals(120, page.total)
    }

    @Test
    fun turnsTheMarkupOfADescriptionIntoText() {
        assertEquals(
            "First line\nSecond line",
            AniListParser.plainText("First line<br>Second line"),
        )
        assertEquals(
            "Tom & Jerry",
            AniListParser.plainText("Tom &amp; Jerry"),
        )
        assertEquals(
            "Bold",
            AniListParser.plainText("<b>Bold</b>"),
        )
        assertNull(AniListParser.plainText("   "))
    }

    @Test
    fun mapsTheStatus() {
        assertTrue(AniListParser.status("RELEASING").isAiring)
        assertTrue(AniListParser.status("FINISHED").isFinished)
        assertTrue(AniListParser.status("NOT_YET_RELEASED").isUpcoming)
        assertEquals("RELEASING", AniListParser.status("RELEASING").raw)
        assertNull(AniListParser.status(null).raw)
    }

    @Test
    fun buildsAPageQueryWithOnlyTheFiltersThatAreUsed() {
        val (document, variables) = AniListQueries.page(
            CatalogQuery(search = "naruto", sort = CatalogSort.Trending, perPage = 10),
        )

        assertTrue(document.contains("search: \$search"))
        assertTrue(document.contains("sort: \$sort"))
        assertTrue(document.contains("\$search: String"))
        assertTrue(document.contains("\$sort: [MediaSort]"))
        assertFalse(document.contains("genre_in"))
        assertEquals("naruto", variables["search"].toString().trim('"'))
    }

    @Test
    fun buildsAPageQueryWithoutFilters() {
        val (document, variables) = AniListQueries.page(CatalogQuery())

        assertFalse(document.contains("search:"))
        assertFalse(document.contains("sort:"))
        assertEquals(2, variables.size)
    }
}
