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
package dev.animeplayer.sources.hianime.internal

import dev.animeplayer.core.util.UrlResolver

/**
 * The upstream endpoints of the site.
 *
 * The whole scraping flow of the original command line tool is four requests:
 *
 *  1. `GET /search?keyword=…`                         → the search results page
 *  2. `GET /api/theme/episode/list/{numericId}`      → the episode list, as JSON
 *  3. `GET /api/theme/episode/servers?episodeId=…`   → the available servers, as JSON
 *  4. `GET {embedUrl}`                                → the player page, obfuscated
 *  5. `GET {masterPlaylist}`                          → the HLS master playlist
 */
internal class HianimeEndpoints(val baseUrl: String) {

    /** The `Referer` the upstream site expects for its own pages. */
    val siteReferer: String = UrlResolver.origin(baseUrl)

    fun search(keyword: String): String =
        "$baseUrl/search?keyword=${encodeQueryComponent(keyword)}"

    fun episodeList(numericId: String): String =
        "$baseUrl/api/theme/episode/list/$numericId"

    fun servers(episodeId: String): String =
        "$baseUrl/api/theme/episode/servers?episodeId=$episodeId"

    fun watchPage(slug: String): String =
        "$baseUrl/watch/$slug?ep=1"

    private fun encodeQueryComponent(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-_.~") {
                append(char)
            } else {
                append('%')
                append(HEX_DIGITS[code shr 4])
                append(HEX_DIGITS[code and 0x0F])
            }
        }
    }

    private companion object {
        const val HEX_DIGITS = "0123456789ABCDEF"
    }
}
