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
package dev.animeplayer.sources.anilist

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The configuration of [AniListSource].
 *
 * @property endpoint the GraphQL endpoint of the catalogue.
 * @property requestTimeout the timeout of a single HTTP request.
 * @property userAgent the `User-Agent` used for every request.
 */
public data class AniListConfig(
    public val endpoint: String = DEFAULT_ENDPOINT,
    public val requestTimeout: Duration = 15.seconds,
    public val userAgent: String = DEFAULT_USER_AGENT,
) {
    public companion object {
        public const val DEFAULT_ENDPOINT: String = "https://graphql.anilist.co"

        public const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
