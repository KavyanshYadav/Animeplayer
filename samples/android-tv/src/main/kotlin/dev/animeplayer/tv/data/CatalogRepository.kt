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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
public class CatalogRepository internal constructor(
    private val source: CatalogSource,
    private val tmdb: TmdbCatalog?,
    private val ttl: kotlin.time.Duration = 5.minutes,
) {
    public constructor(source: CatalogSource, ttl: kotlin.time.Duration = 5.minutes) : this(source, null, ttl)

    private val mutex = Mutex()
    private val pages = LinkedHashMap<CatalogQuery, Entry<CatalogPage>>(32, 0.75f, true)
    private val entries = LinkedHashMap<String, Entry<CatalogMedia>>(64, 0.75f, true)

    private val relationsById = LinkedHashMap<String, Entry<List<CatalogRelation>>>(32, 0.75f, true)

    /** Loads a page of entries. */
    public suspend fun page(query: CatalogQuery): CatalogPage {
        val key = query
        mutex.withLock {
            val cached = pages[key]
            if (cached != null && cached.isFresh(ttl)) return cached.value
        }
        val page = source.page(query)
        mutex.withLock {
            pages[key] = Entry(page)
            pages.trimTo(MAX_PAGES)
        }
        return page
    }

    /** Loads a single entry. */
    public suspend fun media(id: String): CatalogMedia {
        mutex.withLock {
            val cached = entries[id]
            if (cached != null && cached.isFresh(ttl)) return cached.value
        }
        val media = source.media(id)
        mutex.withLock {
            entries[id] = Entry(media)
            entries.trimTo(MAX_MEDIA)
        }
        return media
    }

    /** Loads several entries at once. */
    public suspend fun media(ids: List<String>): List<CatalogMedia> {
        val cached = mutex.withLock {
            ids.distinct().mapNotNull { id ->
                entries[id]?.takeIf { it.isFresh(ttl) }?.let { id to it.value }
            }.toMap()
        }
        val missing = ids.distinct().filterNot { it in cached }
        val loaded = if (missing.isEmpty()) emptyMap() else source.media(missing).associateBy { it.id }
        mutex.withLock {
            missing.forEach { id -> loaded[id]?.let { entries[id] = Entry(it) } }
            entries.trimTo(MAX_MEDIA)
        }
        return ids.mapNotNull { cached[it] ?: loaded[it] }
    }

    /** Loads the episodes of an entry. */
    public suspend fun episodes(id: String): List<CatalogEpisode> {
        val supplement = tmdb ?: return source.episodes(id)
        return coroutineScope {
            val enriched = async {
                try {
                    supplement.episodes(media(id))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val base = try {
                Result.success(source.episodes(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            val preferred = enriched.await()
            if (preferred.isEmpty()) base.getOrThrow() else mergeEpisodes(base.getOrDefault(emptyList()), preferred)
        }
    }

    /** Loads the relations of an entry. */
    public suspend fun relations(id: String): List<CatalogRelation> {
        mutex.withLock {
            relationsById[id]?.takeIf { it.isFresh(ttl) }?.let { return it.value }
        }
        val relations = source.relations(id)
        mutex.withLock {
            relationsById[id] = Entry(relations)
            relationsById.trimTo(MAX_MEDIA)
        }
        return relations
    }

    internal suspend fun seasons(media: CatalogMedia, direct: List<CatalogRelation>): List<CatalogMedia> {
        if (!media.isTelevisionSeason()) return emptyList()
        val visited = mutableSetOf(media.id)

        suspend fun follow(direction: String): List<CatalogMedia> {
            val chain = mutableListOf<CatalogMedia>()
            var edges = direct
            repeat(8) {
                val next = edges.filter { it.relationType == direction && it.media.isTelevisionSeason() }
                    .distinctBy { it.media.id }.singleOrNull()?.media ?: return chain
                if (!visited.add(next.id)) return chain
                chain += next
                edges = try {
                    relations(next.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return chain
                }
            }
            return chain
        }

        val earlier = follow("PREQUEL").asReversed()
        val later = follow("SEQUEL")
        return (earlier + media + later).takeIf { it.size > 1 }.orEmpty()
    }

    /** Loads the recommendations of an entry. */
    public suspend fun recommendations(id: String): List<CatalogMedia> = source.recommendations(id)

    /** The genres the catalogue knows about. */
    public val genres: List<String> get() = source.genres

    /** Empties the cache. */
    public suspend fun clear() {
        mutex.withLock {
            pages.clear()
            entries.clear()
            relationsById.clear()
        }
    }

    private class Entry<T>(val value: T, val at: Long = Clock.System.now().toEpochMilliseconds()) {
        fun isFresh(ttl: kotlin.time.Duration): Boolean =
            Clock.System.now().toEpochMilliseconds() - at < ttl.inWholeMilliseconds
    }
}

private fun mergeEpisodes(base: List<CatalogEpisode>, preferred: List<CatalogEpisode>): List<CatalogEpisode> {
    val merged = base.associateByTo(linkedMapOf()) { it.number }
    for (episode in preferred) {
        val existing = merged[episode.number]
        merged[episode.number] = episode.copy(
            title = episode.title?.takeUnless { it.equals("Episode ${episode.number}", ignoreCase = true) }
                ?: existing?.title ?: episode.title,
            description = episode.description ?: existing?.description,
            thumbnailUrl = episode.thumbnailUrl ?: existing?.thumbnailUrl,
            airDate = existing?.airDate ?: episode.airDate,
            score = episode.score ?: existing?.score,
            lengthMinutes = episode.lengthMinutes ?: existing?.lengthMinutes,
        )
    }
    return merged.values.sortedBy { it.number }
}

private const val MAX_PAGES = 32
private const val MAX_MEDIA = 96

private fun <K, V> LinkedHashMap<K, V>.trimTo(limit: Int) {
    val iterator = entries.iterator()
    while (size > limit && iterator.hasNext()) {
        iterator.next()
        iterator.remove()
    }
}
