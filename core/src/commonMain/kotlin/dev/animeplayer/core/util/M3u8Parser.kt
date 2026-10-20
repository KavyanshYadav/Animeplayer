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
package dev.animeplayer.core.util

import dev.animeplayer.core.model.StreamVariant
import dev.animeplayer.core.model.VideoQuality

/**
 * A parser for HLS master playlists.
 *
 * Only the parts the library needs are understood: the variant streams declared through
 * `#EXT-X-STREAM-INF`. Media playlists (`#EXTINF` / `#EXT-X-TARGETDURATION`) are consumed by
 * the player, not by the library.
 */
public object M3u8Parser {

    private const val STREAM_INF = "#EXT-X-STREAM-INF:"
    private const val I_FRAME_STREAM_INF = "#EXT-X-I-FRAME-STREAM-INF:"

    /**
     * Parses the variants of a master playlist.
     *
     * @param playlist the raw playlist body.
     * @param baseUrl the URL the playlist was loaded from; relative variant URIs are resolved
     *   against it.
     * @return the variants, best quality first.
     */
    public fun parseMaster(playlist: String, baseUrl: String): List<StreamVariant> {
        val variants = mutableListOf<StreamVariant>()
        var pendingAttributes: Map<String, String>? = null

        playlist.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith(STREAM_INF) -> {
                    pendingAttributes = parseAttributes(line.removePrefix(STREAM_INF))
                }

                line.startsWith(I_FRAME_STREAM_INF) -> {
                    // Trick play streams are not directly playable and are skipped.
                    pendingAttributes = null
                }

                line.isEmpty() || line.startsWith("#") -> Unit

                else -> {
                    val attributes = pendingAttributes
                    pendingAttributes = null
                    val url = UrlResolver.resolve(baseUrl, line)
                    val resolution = attributes?.get("RESOLUTION")
                    val width = resolution?.substringBefore('x')?.trim()?.toIntOrNull()
                    val height = resolution?.substringAfter('x', "")?.trim()?.toIntOrNull()

                    variants += StreamVariant(
                        quality = VideoQuality(
                            width = width ?: 0,
                            height = height ?: 0,
                        ),
                        url = url,
                        bandwidth = attributes?.get("BANDWIDTH")?.toLongOrNull(),
                        codecs = attributes?.get("CODECS"),
                        frameRate = attributes?.get("FRAME-RATE")?.toDoubleOrNull(),
                    )
                }
            }
        }

        return variants.sortedWith(
            compareByDescending<StreamVariant> { it.quality.height }
                .thenByDescending { it.bandwidth ?: 0L },
        )
    }

    /**
     * Splits an `#EXT-X-STREAM-INF` attribute list.
     *
     * Attribute values may contain commas inside quotes — `CODECS="avc1.640028,mp4a.40.2"` —
     * so a naive split on `,` would corrupt them.
     */
    internal fun parseAttributes(input: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        var index = 0

        while (index < input.length) {
            val equals = input.indexOf('=', index)
            if (equals < 0) break

            val key = input.substring(index, equals).trim()
            var value = StringBuilder()
            var cursor = equals + 1

            if (cursor < input.length && input[cursor] == '"') {
                cursor++
                while (cursor < input.length && input[cursor] != '"') {
                    value.append(input[cursor])
                    cursor++
                }
                cursor++ // closing quote
            } else {
                while (cursor < input.length && input[cursor] != ',') {
                    value.append(input[cursor])
                    cursor++
                }
            }

            attributes[key] = value.toString()

            while (cursor < input.length && input[cursor] != ',') cursor++
            index = cursor + 1
        }

        return attributes
    }
}
