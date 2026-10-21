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

import dev.animeplayer.core.model.Anime
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.util.TitleMatch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The bridge between the catalogue and the library.
 *
 * The catalogue knows a series by its catalogue title, the library knows it by the slug
 * of the upstream site. Nothing connects the two, so the titles are compared — the same
 * way the desktop client does it.
 *
 * The result of a comparison is remembered, because the user usually comes back to the
 * same series and a match needs a request to the library.
 */
public class MatchResolver(
    private val library: LibraryRepository,
    private val cache: MatchCache = InMemoryMatchCache(),
) {
    private val mutex = Mutex()

    /**
     * Finds the series of the library that matches [media].
     *
     * @return the match, or `null` when the library does not know the series.
     */
    public suspend fun match(media: CatalogMedia): Anime? {
        val known = cache.get(media.id)
        if (known != null) {
            // The slug is known, but the metadata of the library is still worth loading.
            return runCatching { library.anime(known) }.getOrNull()
        }

        val title = media.titles.firstOrNull() ?: return null
        val candidates = library.search(title)
        val best = candidates.maxByOrNull { candidate ->
            TitleMatch.best(
                candidates = media.titles,
                targets = candidate.titles,
            )
        } ?: return null

        val score = TitleMatch.best(media.titles, best.titles)
        if (score < MINIMUM_SCORE) return null

        cache.put(media.id, best.id.slug)
        return best
    }

    /**
     * The slug of the series that matches [media], or `null`.
     */
    public suspend fun slug(media: CatalogMedia): String? = match(media)?.id?.slug

    private companion object {
        /**
         * The score below which two titles are considered different series.
         *
         * A score of `0.6` means that every word of the shorter title is in the longer
         * one, which is as far as the comparison goes before it starts guessing.
         */
        const val MINIMUM_SCORE = 0.6
    }
}

/**
 * The place where the matches are remembered.
 */
public interface MatchCache {
    /** The slug remembered for a catalogue entry. */
    public suspend fun get(catalogId: String): String?

    /** Remembers the slug of a catalogue entry. */
    public suspend fun put(catalogId: String, slug: String)
}

/**
 * A cache that forgets everything when the process dies.
 */
public class InMemoryMatchCache : MatchCache {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, String>()

    override suspend fun get(catalogId: String): String? = mutex.withLock { entries[catalogId] }

    override suspend fun put(catalogId: String, slug: String) {
        mutex.withLock { entries[catalogId] = slug }
    }
}
