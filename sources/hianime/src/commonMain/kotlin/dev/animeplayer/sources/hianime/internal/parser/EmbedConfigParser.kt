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
package dev.animeplayer.sources.hianime.internal.parser

import dev.animeplayer.core.error.AnimeException
import dev.animeplayer.sources.hianime.internal.codec.EmbedDeobfuscator
import dev.animeplayer.sources.hianime.internal.dto.EmbedConfig
import kotlinx.serialization.json.Json

/**
 * Extracts the player configuration from an upstream player page.
 *
 * The page embeds the configuration as an obfuscated, Base64 encoded JSON blob:
 *
 * ```html
 * <script>window.__P="FFYFBAJDCQIDATtY…"</script>
 * ```
 *
 * Decoding it yields a JSON document with the master playlist URL, the subtitle tracks and
 * — unlike the original command line tool — the intro and outro segments, which makes an
 * external "skip detection" service unnecessary.
 */
internal object EmbedConfigParser {

    private val PAYLOAD = Regex("""window\.__P\s*=\s*"([^"]+)"""")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(html: String): EmbedConfig {
        val match = PAYLOAD.find(html)
            ?: throw AnimeException.ParseError(
                reason = "the player configuration",
                snippet = html.take(240),
            )

        val decoded = runCatching { EmbedDeobfuscator.decodeToString(match.groupValues[1]) }
            .getOrElse {
                throw AnimeException.ParseError("the obfuscated player configuration", it.message)
            }

        return runCatching { json.decodeFromString<EmbedConfig>(decoded) }
            .getOrElse {
                throw AnimeException.ParseError("the player configuration", decoded.take(240))
            }
    }
}
