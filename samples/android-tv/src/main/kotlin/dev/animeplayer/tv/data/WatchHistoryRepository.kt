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

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.spi.WatchHistoryEntry
import dev.animeplayer.core.spi.WatchHistoryStore
import dev.animeplayer.storage.SqlDelightWatchHistoryStore
import dev.animeplayer.storage.db.AnimePlayerDatabase
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The watch history of the user.
 *
 * The entries are kept in SQLite, so they survive a restart of the application and a
 * reboot of the television.
 */
public class WatchHistoryRepository(context: Context) {

    private val driver = AndroidSqliteDriver(
        schema = AnimePlayerDatabase.Schema,
        context = context,
        name = "animeplayer.db",
    )

    private val store = SqlDelightWatchHistoryStore(
        database = AnimePlayerDatabase(driver),
        dispatcher = Dispatchers.IO,
    )

    /** All the entries, most recent first. */
    public suspend fun entries(): List<WatchHistoryEntry> = store.entries()

    /** The entries that were started but not finished. */
    public suspend fun inProgress(): List<WatchHistoryEntry> = store.inProgress()

    /** The entry of one episode. */
    public suspend fun entry(slug: String, episode: String): WatchHistoryEntry? =
        store.entry(AnimeId(slug, slug.substringAfterLast('-', slug)), episode)

    /** Records the progress of an episode. */
    public suspend fun save(
        slug: String,
        title: String,
        episode: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        store.upsert(
            WatchHistoryEntry(
                animeId = AnimeId(slug, slug.substringAfterLast('-', slug)),
                title = title,
                episodeNumber = episode,
                updatedAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
                positionMs = positionMs,
                durationMs = durationMs,
            ),
        )
    }

    /** Forgets one episode. */
    public suspend fun forget(slug: String, episode: String) {
        store.remove(AnimeId(slug, slug.substringAfterLast('-', slug)), episode)
    }

    /** Forgets everything. */
    public suspend fun clear() {
        store.clear()
    }
}
