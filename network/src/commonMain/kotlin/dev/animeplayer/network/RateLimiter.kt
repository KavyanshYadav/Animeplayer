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

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Limits how often requests are sent to a host.
 *
 * Public anime sites are easy to overload and they do start blocking aggressive clients,
 * so a small rate limit is a good idea in production.
 */
public interface RateLimiter {
    /** Waits until another request to [host] is allowed. */
    public suspend fun acquire(host: String)

    /** A limiter that never delays anything. */
    public object Disabled : RateLimiter {
        override suspend fun acquire(host: String): Unit = Unit
    }
}

/**
 * A per-host token bucket.
 *
 * @param permitsPerSecond how many requests per second are allowed on average.
 * @param burst how many requests may be sent back to back.
 */
public class TokenBucketRateLimiter(
    private val permitsPerSecond: Double,
    private val burst: Int = 1,
    private val clock: Clock = Clock.System,
) : RateLimiter {

    private val mutex = Mutex()
    private val buckets = mutableMapOf<String, Bucket>()

    override suspend fun acquire(host: String) {
        val waitMillis = mutex.withLock {
            val now = clock.now()
            val bucket = buckets.getOrPut(host) { Bucket(tokens = burst.toDouble(), updatedAt = now) }
            bucket.refill(now, permitsPerSecond, burst)
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                0L
            } else {
                val missing = 1.0 - bucket.tokens
                val delayMillis = (missing / permitsPerSecond * 1000.0).toLong().coerceAtLeast(1L)
                bucket.tokens = 0.0
                delayMillis
            }
        }
        if (waitMillis > 0L) delay(waitMillis)
    }

    private class Bucket(var tokens: Double, var updatedAt: Instant) {
        fun refill(now: Instant, permitsPerSecond: Double, burst: Int) {
            val elapsedSeconds = (now - updatedAt).inWholeMilliseconds / 1000.0
            if (elapsedSeconds <= 0.0) return
            tokens = (tokens + elapsedSeconds * permitsPerSecond).coerceAtMost(burst.toDouble())
            updatedAt = now
        }
    }
}

/**
 * Exponential backoff with jitter.
 */
public object Backoff {
    /**
     * The delay to wait before attempt number [attempt] (zero based).
     */
    public fun delayFor(
        attempt: Int,
        initial: Duration,
        multiplier: Double,
        max: Duration,
        jitter: Double,
        random: Random = Random.Default,
    ): Duration {
        val baseMillis = initial.inWholeMilliseconds.toDouble() * multiplier.pow(attempt.coerceAtLeast(0))
        val cappedMillis = baseMillis.coerceAtMost(max.inWholeMilliseconds.toDouble())
        val jitterFactor = if (jitter <= 0.0) 1.0 else 1.0 + (random.nextDouble() * 2.0 - 1.0) * jitter
        val millis = (cappedMillis * jitterFactor).toLong().coerceAtLeast(0L)
        return millis.milliseconds
    }
}

/**
 * Stops sending requests to a host that keeps failing.
 *
 * After [failureThreshold] consecutive failures the breaker opens for [openDuration] and
 * every request fails fast until the duration has passed.
 */
public interface CircuitBreaker {
    /** Runs [block], counting successes and failures. */
    public suspend fun <T> execute(host: String, block: suspend () -> T): T

    /** A breaker that never trips. */
    public object Disabled : CircuitBreaker {
        override suspend fun <T> execute(host: String, block: suspend () -> T): T = block()
    }
}

/**
 * A per-host circuit breaker with a fixed cool-down.
 */
public class SimpleCircuitBreaker(
    private val failureThreshold: Int = 5,
    private val openDuration: Duration = 30.seconds,
    private val clock: Clock = Clock.System,
) : CircuitBreaker {

    private val mutex = Mutex()
    private val state = mutableMapOf<String, HostState>()

    override suspend fun <T> execute(host: String, block: suspend () -> T): T {
        mutex.withLock {
            val hostState = state.getOrPut(host) { HostState() }
            val openUntil = hostState.openUntil
            if (openUntil != null) {
                if (clock.now() < openUntil) {
                    throw CircuitOpenException(host, openUntil - clock.now())
                }
                hostState.openUntil = null
                hostState.consecutiveFailures = 0
            }
        }

        return try {
            val result = block()
            mutex.withLock {
                state.getOrPut(host) { HostState() }.consecutiveFailures = 0
            }
            result
        } catch (error: Throwable) {
            mutex.withLock {
                val hostState = state.getOrPut(host) { HostState() }
                hostState.consecutiveFailures++
                if (hostState.consecutiveFailures >= failureThreshold) {
                    hostState.openUntil = clock.now() + openDuration
                }
            }
            throw error
        }
    }

    /** `true` when the breaker for [host] is currently open. */
    public suspend fun isOpen(host: String): Boolean = mutex.withLock {
        val openUntil = state[host]?.openUntil ?: return@withLock false
        clock.now() < openUntil
    }

    private class HostState(
        var consecutiveFailures: Int = 0,
        var openUntil: Instant? = null,
    )
}

/** Thrown when a request is rejected because the circuit breaker for that host is open. */
public class CircuitOpenException(
    public val host: String,
    public val retryAfter: Duration,
) : IllegalStateException("The circuit breaker for $host is open, retry in $retryAfter")
