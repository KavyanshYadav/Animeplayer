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
import dev.animeplayer.core.spi.Logger
import dev.animeplayer.core.spi.MetricsSink
import dev.animeplayer.core.spi.NoopLogger
import dev.animeplayer.core.spi.NoopMetricsSink
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The tuning knobs of [KtorHttpTransport].
 *
 * The defaults are chosen for talking to a public anime site: a browser user agent, a short
 * timeout, a handful of retries and an in-memory cache.
 */
public data class TransportConfig(
    /** The `User-Agent` sent with every request that does not set one explicitly. */
    public val userAgent: String = DEFAULT_USER_AGENT,

    /** The timeout used when a request does not specify one. */
    public val defaultTimeout: Duration = 10.seconds,

    /** How many times a request is attempted in total. `1` disables retries. */
    public val maxAttempts: Int = 3,

    /** The delay before the first retry. */
    public val initialBackoff: Duration = 400.milliseconds,

    /** The upper bound for the backoff delay. */
    public val maxBackoff: Duration = 8.seconds,

    /** The factor the backoff grows by after every failed attempt. */
    public val backoffMultiplier: Double = 2.0,

    /** The relative amount of random jitter applied to the backoff, `0.0` disables it. */
    public val backoffJitter: Double = 0.25,

    /** The status codes that trigger a retry. */
    public val retryableStatusCodes: Set<Int> = setOf(408, 425, 429, 500, 502, 503, 504),

    /** The cache used for `GET` requests. `null` disables caching. */
    public val cache: Cache? = null,

    /** How long a cached response stays valid. */
    public val cacheTtl: Duration = 5.minutes,

    /** Limits the request rate per host. */
    public val rateLimiter: RateLimiter = RateLimiter.Disabled,

    /** Stops hammering a host that keeps failing. */
    public val circuitBreaker: CircuitBreaker = CircuitBreaker.Disabled,

    /** The logger every request is reported to. */
    public val logger: Logger = NoopLogger,

    /** The sink every request is reported to. */
    public val metrics: MetricsSink = NoopMetricsSink,

    /** The clock used for timeouts, backoff and circuit breaker state. */
    public val clock: Clock = Clock.System,
) {
    public companion object {
        /**
         * A current desktop browser user agent.
         *
         * Upstream sites serve a different, more complete page to recognised browsers and
         * rate limit unknown clients more aggressively.
         */
        public const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
