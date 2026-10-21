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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The catalogue of the application.
 *
 * The catalogue asks for thirty requests per minute and no more, so every answer is
 * cached for a few minutes. Without the cache, moving the focus up and down a rail would
 * send a request per card.
 *
 * @param source the catalogue to read from.
 * @param ttl how long an answer stays fresh.
 */
public class CatalogRepository(
    private val source: CatalogSource,
    private val ttl: kotlin.time.Duration = 5.minutes,
) {
    private val mutex = Mutex()
    private val pages = mutableMapOf<String, Entry<CatalogPage>>()
    private val entries = mutableMapOf<String, Entry<CatalogMedia>>()

    /** Loads a page of entries. */
    public suspend fun page(query: CatalogQuery): CatalogPage {
        val key = query.cacheKey()
        mutex.withLock {
            val cached = pages[key]
            if (cached != null && cached.isFresh(ttl)) return cached.value
        }
        val page = source.page(query)
        mutex.withLock { pages[key] = Entry(page) }
        return page
    }

    /** Loads a single entry. */
    public suspend fun media(id: String): CatalogMedia {
        mutex.withLock {
            val cached = entries[id]
            if (cached != null && cached.isFresh(ttl)) return cached.value
        }
        val media = source.media(id)
        mutex.withLock { entries[id] = Entry(media) }
        return media
    }

    /** Loads several entries at once. */
    public suspend fun media(ids: List<String>): List<CatalogMedia> {
        val missing = ids.filter { id ->
            val cached = entries[id]
            cached == null || !cached.isFresh(ttl)
        }
        if (missing.isEmpty()) {
            return ids.mapNotNull { entries[it]?.value }
        }
        val loaded = source.media(missing)
        mutex.withLock {
            loaded.forEach { entries[it.id] = Entry(it) }
        }
        return ids.mapNotNull { id -> entries[id]?.value }
    }

    /** Loads the episodes of an entry. */
    public suspend fun episodes(id: String): List<CatalogEpisode> = source.episodes(id)

    /** Loads the relations of an entry. */
    public suspend fun relations(id: String): List<CatalogRelation> = source.relations(id)

    /** Loads the recommendations of an entry. */
    public suspend fun recommendations(id: String): List<CatalogMedia> = source.recommendations(id)

    /** The genres the catalogue knows about. */
    public val genres: List<String> get() = source.genres

    /** Empties the cache. */
    public suspend fun clear() {
        mutex.withLock {
            pages.clear()
            entries.clear()
        }
    }

    private class Entry<T>(val value: T, val at: Long = Clock.System.now().toEpochMilliseconds()) {
        fun isFresh(ttl: kotlin.time.Duration): Boolean =
            Clock.System.now().toEpochMilliseconds() - at < ttl.inWholeMilliseconds
    }
}

/**
 * The key a query is cached under.
 *
 * Only the fields that change the answer take part in the key, so two queries that differ
 * only in their page share the same cache entry.
 */
private fun CatalogQuery.cacheKey(): String = listOf(
    search.orEmpty(),
    genre.orEmpty(),
    season.orEmpty(),
    seasonYear?.toString().orEmpty(),
    format.orEmpty(),
    status.orEmpty(),
    sort.orEmpty(),
    page.toString(),
    perPage.toString(),
).joinToString("|")
