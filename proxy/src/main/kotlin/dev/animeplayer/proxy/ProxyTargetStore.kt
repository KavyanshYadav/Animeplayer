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
package dev.animeplayer.proxy

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.random.Random
import kotlin.time.Duration

/**
 * A short lived registry that maps opaque tokens to upstream URLs.
 *
 * The browser never sees the real upstream URL — it only ever sees a proxy URL with a
 * random token. That keeps the upstream host and its `Referer` requirement an internal
 * detail, and it means the same mechanism can be used for playlists and for the
 * individual media segments.
 */
public class ProxyTargetStore(
    private val ttl: Duration,
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
) {

    private val mutex = Mutex()
    private val targets = mutableMapOf<String, Entry>()

    /** Registers [target] and returns its token. */
    public suspend fun register(target: ProxyTarget): String {
        val token = randomToken()
        mutex.withLock {
            purgeExpired()
            targets[token] = Entry(target, clock.now() + ttl)
        }
        return token
    }

    /** Looks a target up. */
    public suspend fun resolve(token: String): ProxyTarget? = mutex.withLock {
        val entry = targets[token] ?: return@withLock null
        if (clock.now() >= entry.expiresAt) {
            targets.remove(token)
            return@withLock null
        }
        entry.target
    }

    /** Removes every target. */
    public suspend fun clear() {
        mutex.withLock { targets.clear() }
    }

    /** The number of live targets. */
    public suspend fun size(): Int = mutex.withLock {
        purgeExpired()
        targets.size
    }

    private fun purgeExpired() {
        val now = clock.now()
        targets.entries.removeAll { now >= it.value.expiresAt }
    }

    private fun randomToken(): String = buildString {
        repeat(TOKEN_LENGTH) {
            append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)])
        }
    }

    private class Entry(val target: ProxyTarget, val expiresAt: Instant)

    private companion object {
        const val TOKEN_LENGTH = 24
        const val TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    }
}
