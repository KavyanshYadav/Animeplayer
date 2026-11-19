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

import dev.animeplayer.core.model.CatalogEpisode
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.CatalogPage
import dev.animeplayer.core.model.CatalogQuery
import dev.animeplayer.core.model.CatalogRelation
import dev.animeplayer.core.spi.CatalogSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogRepositoryTest {
    @Test
    fun longSavedListsKeepTheirOrderWhenTheCacheEvictsEntries() = runBlocking {
        val source = TestCatalog()
        val repository = CatalogRepository(source)
        val ids = (1..105).map(Int::toString)

        assertEquals(ids, repository.media(ids).map(CatalogMedia::id))
        assertEquals(ids.takeLast(10), repository.media(ids.takeLast(10)).map(CatalogMedia::id))
        assertEquals(1, source.batches)
    }

    @Test
    fun oldSearchPagesAreEvictedWhileRecentlyUsedPagesStayCached() = runBlocking {
        val source = TestCatalog()
        val repository = CatalogRepository(source)
        val popular = CatalogQuery(search = "popular")
        repository.page(popular)
        repeat(40) { page ->
            repository.page(CatalogQuery(search = "older search", page = page + 1))
            repository.page(popular)
        }

        assertEquals(41, source.pages)
        repository.page(CatalogQuery(search = "older search", page = 1))
        assertEquals(42, source.pages)
    }

    private class TestCatalog : CatalogSource {
        override val id: String = "test"
        override val displayName: String = "Test catalog"
        override val genres: List<String> = emptyList()
        var batches: Int = 0
            private set
        var pages: Int = 0
            private set

        override suspend fun page(query: CatalogQuery): CatalogPage {
            pages++
            return CatalogPage(currentPage = query.page)
        }

        override suspend fun media(id: String): CatalogMedia = CatalogMedia(id)

        override suspend fun media(ids: List<String>): List<CatalogMedia> {
            batches++
            return ids.map { CatalogMedia(it) }.reversed()
        }

        override suspend fun episodes(id: String): List<CatalogEpisode> = emptyList()
        override suspend fun relations(id: String): List<CatalogRelation> = emptyList()
        override suspend fun recommendations(id: String): List<CatalogMedia> = emptyList()
    }
}
