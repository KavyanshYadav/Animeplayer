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
package dev.animeplayer.sources.hianime

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The configuration of [HianimeSource].
 *
 * @property baseUrl the root of the upstream site.
 * @property requestTimeout the timeout of a single HTTP request.
 * @property preferredServers the servers to try first, in order. The original command line
 *   tool only understood the `ZokoAnime` embed; this library understands every server that
 *   follows the `window.__P` convention and falls back through the remaining ones.
 * @property userAgent the `User-Agent` used for every request.
 */
public data class HianimeConfig(
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val requestTimeout: Duration = 10.seconds,
    public val preferredServers: List<String> = listOf("ZokoAnime"),
    public val userAgent: String = DEFAULT_USER_AGENT,
) {
    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://hianime.at"

        public const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
