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
package dev.animeplayer.core.spi

import dev.animeplayer.core.model.AnimeId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [WatchHistoryStore] that keeps everything in memory.
 *
 * Useful for tests, for short lived processes and for platforms without a file system.
 */
public class InMemoryWatchHistoryStore : WatchHistoryStore {

    private val mutex = Mutex()
    private val entries = mutableMapOf<Pair<String, String>, WatchHistoryEntry>()

    override suspend fun entries(): List<WatchHistoryEntry> = mutex.withLock {
        entries.values.sortedByDescending { it.updatedAt }
    }

    override suspend fun entry(animeId: AnimeId, episodeNumber: String): WatchHistoryEntry? =
        mutex.withLock { entries[animeId.slug to episodeNumber] }

    override suspend fun upsert(entry: WatchHistoryEntry) {
        mutex.withLock { entries[entry.animeId.slug to entry.episodeNumber] = entry }
    }

    override suspend fun remove(animeId: AnimeId, episodeNumber: String) {
        mutex.withLock { entries.remove(animeId.slug to episodeNumber) }
    }

    override suspend fun clear() {
        mutex.withLock { entries.clear() }
    }
}
