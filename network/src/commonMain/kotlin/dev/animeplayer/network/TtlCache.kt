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
package dev.animeplayer.network

import dev.animeplayer.core.spi.Cache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * A simple in-memory cache with per-entry expiry.
 *
 * The oldest entry is evicted once [maxEntries] is reached.
 */
public class TtlCache(
    private val maxEntries: Int = 256,
    private val clock: Clock = Clock.System,
) : Cache {

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry>()

    override suspend fun get(key: String): ByteArray? = mutex.withLock {
        val entry = entries[key] ?: return@withLock null
        if (clock.now() >= entry.expiresAt) {
            entries.remove(key)
            return@withLock null
        }
        entry.value
    }

    override suspend fun put(key: String, value: ByteArray, ttl: Duration) {
        mutex.withLock {
            if (entries.size >= maxEntries) {
                val oldest = entries.keys.firstOrNull()
                if (oldest != null) entries.remove(oldest)
            }
            entries[key] = Entry(value, clock.now() + ttl)
        }
    }

    override suspend fun invalidate(key: String) {
        mutex.withLock { entries.remove(key) }
    }

    override suspend fun clear() {
        mutex.withLock { entries.clear() }
    }

    /** The number of entries that have not expired yet. */
    public suspend fun size(): Int = mutex.withLock { entries.size }

    private class Entry(val value: ByteArray, val expiresAt: kotlin.time.Instant)
}
