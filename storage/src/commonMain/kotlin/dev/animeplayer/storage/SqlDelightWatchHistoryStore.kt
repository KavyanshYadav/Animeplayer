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
package dev.animeplayer.storage

import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.spi.WatchHistoryEntry
import dev.animeplayer.core.spi.WatchHistoryStore
import dev.animeplayer.storage.db.AnimePlayerDatabase
import dev.animeplayer.storage.db.Watch_history
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.coroutines.CoroutineContext

/**
 * A [WatchHistoryStore] backed by SQLite through SQLDelight.
 *
 * @param database the database to use. Create one with [createDatabase] or with the
 *   driver of your choice.
 * @param dispatcher the context the queries run on. Pass `Dispatchers.IO` on Android so
 *   that the database is not touched from the main thread.
 */
public class SqlDelightWatchHistoryStore(
    private val database: AnimePlayerDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : WatchHistoryStore {

    override suspend fun entries(): List<WatchHistoryEntry> = withContext(dispatcher) {
        database.watchHistoryQueries.selectAll().executeAsList().map { it.toEntry() }
    }

    override suspend fun entry(animeId: AnimeId, episodeNumber: String): WatchHistoryEntry? =
        withContext(dispatcher) {
            database.watchHistoryQueries.selectByEpisode(animeId.slug, episodeNumber)
                .executeAsOneOrNull()
                ?.toEntry()
        }

    /** The entries that were started but not finished, most recent first. */
    public suspend fun inProgress(): List<WatchHistoryEntry> = withContext(dispatcher) {
        database.watchHistoryQueries.selectInProgress().executeAsList().map { it.toEntry() }
    }

    override suspend fun upsert(entry: WatchHistoryEntry) {
        withContext(dispatcher) {
            database.watchHistoryQueries.upsert(
                anime_id = entry.animeId.slug,
                slug = entry.animeId.slug,
                numeric_id = entry.animeId.numericId,
                title = entry.title,
                episode_number = entry.episodeNumber,
                updated_at = entry.updatedAt.toEpochMilliseconds(),
                position_ms = entry.positionMs,
                duration_ms = entry.durationMs,
            )
        }
    }

    override suspend fun remove(animeId: AnimeId, episodeNumber: String) {
        withContext(dispatcher) {
            database.watchHistoryQueries.deleteByEpisode(animeId.slug, episodeNumber)
        }
    }

    override suspend fun clear() {
        withContext(dispatcher) {
            database.watchHistoryQueries.deleteAll()
        }
    }

    private fun Watch_history.toEntry(): WatchHistoryEntry = WatchHistoryEntry(
        animeId = AnimeId(slug = slug, numericId = numeric_id),
        title = title,
        episodeNumber = episode_number,
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        positionMs = position_ms,
        durationMs = duration_ms,
    )
}

/**
 * Creates the database on top of [driver].
 */
public fun createDatabase(driver: app.cash.sqldelight.db.SqlDriver): AnimePlayerDatabase =
    AnimePlayerDatabase(driver)
