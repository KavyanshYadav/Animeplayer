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

/** Severity of a log record. */
public enum class LogLevel {
    Debug,
    Info,
    Warn,
    Error,
    ;
}

/**
 * The logging boundary.
 *
 * The library never writes to `println` — everything goes through a [Logger] so that
 * consumers can route it into whatever logging framework they use.
 */
public interface Logger {
    public fun log(level: LogLevel, message: String, throwable: Throwable? = null)

    public fun debug(message: String): Unit = log(LogLevel.Debug, message)

    public fun info(message: String): Unit = log(LogLevel.Info, message)

    public fun warn(message: String, throwable: Throwable? = null): Unit =
        log(LogLevel.Warn, message, throwable)

    public fun error(message: String, throwable: Throwable? = null): Unit =
        log(LogLevel.Error, message, throwable)
}

/** A [Logger] that discards everything. */
public object NoopLogger : Logger {
    override fun log(level: LogLevel, message: String, throwable: Throwable?): Unit = Unit
}

/**
 * Receives request metrics.
 *
 * Kept deliberately tiny so that it can be implemented by a Prometheus, OpenTelemetry or
 * StatsD adapter without pulling a dependency into the library.
 */
public interface MetricsSink {
    public fun recordRequest(
        host: String,
        path: String,
        status: Int,
        durationMs: Long,
        error: String? = null,
    )

    public fun recordCacheHit(key: String)

    public fun recordCacheMiss(key: String)
}

/** A [MetricsSink] that discards everything. */
public object NoopMetricsSink : MetricsSink {
    override fun recordRequest(
        host: String,
        path: String,
        status: Int,
        durationMs: Long,
        error: String?,
    ): Unit = Unit

    override fun recordCacheHit(key: String): Unit = Unit

    override fun recordCacheMiss(key: String): Unit = Unit
}
