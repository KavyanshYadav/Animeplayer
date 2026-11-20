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
import dev.animeplayer.storage.SqlDelightWatchHistoryStore
import dev.animeplayer.storage.db.AnimePlayerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * The watch history of the user.
 *
 * The entries are kept in SQLite, so they survive a restart of the application and a
 * reboot of the television.
 */
public class WatchHistoryRepository(context: Context) {

    private val artwork = context.getSharedPreferences("continue-watching-artwork", Context.MODE_PRIVATE)
    private val writes = Mutex()

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

    internal suspend fun episodeArtwork(slug: String, episode: String): String? = withContext(Dispatchers.IO) {
        artwork.getString(artworkKey(slug, episode), null)
    }

    internal suspend fun rememberEpisodeArtwork(slug: String, episode: String, thumbnailUrl: String) {
        writes.withLock {
            // An enrichment request can finish after the user clears their history.
            if (entry(slug, episode) != null) storeArtwork(slug, episode, thumbnailUrl)
        }
    }

    private suspend fun storeArtwork(slug: String, episode: String, thumbnailUrl: String) = withContext(Dispatchers.IO) {
        val key = artworkKey(slug, episode)
        if (artwork.getString(key, null) != thumbnailUrl) artwork.edit().putString(key, thumbnailUrl).apply()
    }

    private fun artworkKey(slug: String, episode: String): String = "$slug:$episode"

    /** Records the progress of an episode. */
    public suspend fun save(
        slug: String,
        title: String,
        episode: String,
        positionMs: Long,
        durationMs: Long,
        thumbnailUrl: String? = null,
    ): Unit = writes.withLock {
        store.upsert(
            WatchHistoryEntry(
                animeId = AnimeId(slug, slug.substringAfterLast('-', slug)),
                title = title,
                episodeNumber = episode,
                updatedAt = Clock.System.now(),
                positionMs = positionMs,
                durationMs = durationMs,
            ),
        )
        if (thumbnailUrl != null) storeArtwork(slug, episode, thumbnailUrl)
    }

    /** Forgets one episode. */
    public suspend fun forget(slug: String, episode: String): Unit = writes.withLock {
        store.remove(AnimeId(slug, slug.substringAfterLast('-', slug)), episode)
        withContext(Dispatchers.IO) { artwork.edit().remove(artworkKey(slug, episode)).apply() }
    }

    /** Forgets everything. */
    public suspend fun clear(): Unit = writes.withLock {
        store.clear()
        withContext(Dispatchers.IO) { artwork.edit().clear().apply() }
    }
}
