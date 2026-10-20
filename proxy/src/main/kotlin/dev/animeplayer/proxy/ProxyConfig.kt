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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The configuration of the proxy.
 *
 * @property baseUrl the public base URL of the proxy. It is used to build the URLs that
 *   are handed to the browser, so it has to be reachable from the client.
 * @property targetTtl how long a target stays resolvable.
 */
public data class ProxyConfig(
    public val baseUrl: String = "http://localhost:8080",
    public val targetTtl: Duration = 30.minutes,
    public val userAgent: String = DEFAULT_USER_AGENT,
) {
    public companion object {
        public const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

/**
 * What a proxy token points at.
 *
 * @property url the real upstream URL.
 * @property referer the `Referer` header the upstream host requires.
 */
public data class ProxyTarget(
    val url: String,
    val referer: String,
    val kind: Kind,
) {
    public enum class Kind {
        /** An HLS master playlist. */
        Master,

        /** An HLS media playlist. */
        Variant,

        /** A single media segment. */
        Segment,

        /** A subtitle file. */
        Subtitle,
    }
}
