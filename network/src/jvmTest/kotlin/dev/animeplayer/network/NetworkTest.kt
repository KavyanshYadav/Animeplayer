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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TtlCacheTest {

    @Test
    fun storesAndReturnsValues() = runTest {
        val cache = TtlCache()
        cache.put("key", "value".encodeToByteArray(), 5.seconds)
        assertEquals("value", cache.get("key")?.decodeToString())
    }

    @Test
    fun expiresEntries() = runTest {
        val cache = TtlCache()
        cache.put("key", "value".encodeToByteArray(), 1.milliseconds)
        kotlinx.coroutines.delay(20)
        assertNull(cache.get("key"))
    }

    @Test
    fun invalidatesAndClears() = runTest {
        val cache = TtlCache()
        cache.put("a", "1".encodeToByteArray(), 5.seconds)
        cache.put("b", "2".encodeToByteArray(), 5.seconds)
        assertEquals(2, cache.size())

        cache.invalidate("a")
        assertNull(cache.get("a"))
        assertEquals(1, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
    }
}

class BackoffTest {

    @Test
    fun growsExponentially() {
        val first = Backoff.delayFor(0, 400.milliseconds, 2.0, 8.seconds, jitter = 0.0)
        val second = Backoff.delayFor(1, 400.milliseconds, 2.0, 8.seconds, jitter = 0.0)
        val third = Backoff.delayFor(2, 400.milliseconds, 2.0, 8.seconds, jitter = 0.0)

        assertEquals(400, first.inWholeMilliseconds)
        assertEquals(800, second.inWholeMilliseconds)
        assertEquals(1600, third.inWholeMilliseconds)
    }

    @Test
    fun isCappedAtTheMaximum() {
        val delay = Backoff.delayFor(20, 400.milliseconds, 2.0, 8.seconds, jitter = 0.0)
        assertEquals(8000, delay.inWholeMilliseconds)
    }

    @Test
    fun appliesJitterWithinTheBounds() {
        val base = 1.seconds.inWholeMilliseconds
        repeat(50) {
            val delay = Backoff.delayFor(0, 1.seconds, 1.0, 10.seconds, jitter = 0.25)
            assertTrue(delay.inWholeMilliseconds in (base * 0.75).toLong()..(base * 1.25).toLong())
        }
    }
}

class CloudflareChallengeDetectorTest {

    @Test
    fun detectsTheInterstitial() {
        val challenge = """
            <!DOCTYPE html><html><head><title>Just a moment...</title></head>
            <body><div class="cf-browser-verification">Checking your browser</div></body></html>
        """.trimIndent()
        assertTrue(CloudflareChallengeDetector.isChallenge(403, challenge))
    }

    @Test
    fun ignoresRegularContent() {
        assertFalse(CloudflareChallengeDetector.isChallenge(200, "<html><body>hello</body></html>"))
        assertFalse(CloudflareChallengeDetector.isChallenge(200, ""))
    }

    @Test
    fun ignoresTheMarkerInASuccessfulResponse() {
        assertFalse(CloudflareChallengeDetector.isChallenge(200, "<p>Just a moment</p>"))
    }
}
