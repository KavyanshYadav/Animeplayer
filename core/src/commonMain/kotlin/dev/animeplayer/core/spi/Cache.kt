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

import kotlin.time.Duration

/**
 * A simple, expiring key/value cache.
 *
 * Implementations must be safe to use from multiple coroutines.
 */
public interface Cache {
    /** The cached value, or `null` when absent or expired. */
    public suspend fun get(key: String): ByteArray?

    /** Stores a value for [ttl]. */
    public suspend fun put(key: String, value: ByteArray, ttl: Duration)

    /** Removes a single entry. */
    public suspend fun invalidate(key: String)

    /** Removes every entry. */
    public suspend fun clear()
}
