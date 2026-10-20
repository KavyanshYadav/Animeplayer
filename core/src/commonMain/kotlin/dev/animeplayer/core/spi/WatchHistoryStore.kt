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
import kotlin.time.Instant

/**
 * A single entry of the watch history.
 *
 * @property positionMs how far the user got into the episode.
 */
public data class WatchHistoryEntry(
    public val animeId: AnimeId,
    public val title: String,
    public val episodeNumber: String,
    public val updatedAt: Instant,
    public val positionMs: Long = 0,
    public val durationMs: Long = 0,
) {
    /** Progress in the `0.0..1.0` range, or `0.0` when the duration is unknown. */
    public val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** `true` when the episode is finished (more than 90% watched). */
    public val isCompleted: Boolean
        get() = durationMs > 0L && progress >= 0.9f
}

/**
 * Persistence for the watch history and the playback progress.
 *
 * The library never ships an opinionated storage engine here; see the `storage` module for
 * a SQLDelight backed implementation and `InMemoryWatchHistoryStore` in this module for
 * tests and for platforms without a file system.
 */
public interface WatchHistoryStore {
    /** All entries, most recently updated first. */
    public suspend fun entries(): List<WatchHistoryEntry>

    /** The entry for a specific episode, or `null`. */
    public suspend fun entry(animeId: AnimeId, episodeNumber: String): WatchHistoryEntry?

    /** Inserts or updates an entry. */
    public suspend fun upsert(entry: WatchHistoryEntry)

    /** Removes a single entry. */
    public suspend fun remove(animeId: AnimeId, episodeNumber: String)

    /** Removes every entry. */
    public suspend fun clear()
}
