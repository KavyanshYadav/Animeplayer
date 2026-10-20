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

import dev.animeplayer.core.util.UrlResolver

/**
 * Rewrites the URIs of an HLS playlist so that they point back at the proxy.
 *
 * A master playlist references its variants, a media playlist references its segments and
 * its keys, and the segment URLs are absolute and point at a different host than the
 * playlist. All of them have to be rewritten — a browser that fetches a segment directly
 * would be answered with `403 Forbidden`, because it cannot send the `Referer` header the
 * CDN requires.
 */
public class PlaylistRewriter(
    private val baseUrl: String,
    private val register: suspend (url: String, referer: String, kind: ProxyTarget.Kind) -> String,
) {

    /**
     * Rewrites every URI in [playlist].
     *
     * @param playlist the raw playlist body.
     * @param sourceUrl the URL the playlist was loaded from; relative URIs are resolved
     *   against it.
     * @param referer the `Referer` the upstream host requires.
     * @param kind the kind of the resources the playlist references.
     */
    public suspend fun rewrite(
        playlist: String,
        sourceUrl: String,
        referer: String,
        kind: ProxyTarget.Kind,
    ): String = playlist.split("\n").joinToString("\n") { line ->
        when {
            line.isBlank() -> line

            line.startsWith("#") -> rewriteTagAttributes(line, sourceUrl, referer, kind)

            else -> {
                val resolved = UrlResolver.resolve(sourceUrl, line.trim())
                register(resolved, referer, kind)
            }
        }
    }

    /**
     * Rewrites the `URI="…"` attributes of a tag, for example `EXT-X-KEY` or `EXT-X-MAP`.
     */
    private suspend fun rewriteTagAttributes(
        line: String,
        sourceUrl: String,
        referer: String,
        kind: ProxyTarget.Kind,
    ): String {
        if (!line.contains("URI=\"")) return line

        val builder = StringBuilder(line.length)
        var index = 0

        while (index < line.length) {
            val marker = line.indexOf("URI=\"", index)
            if (marker < 0) {
                builder.append(line, index, line.length)
                break
            }

            builder.append(line, index, marker)
            val valueStart = marker + 5
            val valueEnd = line.indexOf('"', valueStart)
            if (valueEnd < 0) {
                builder.append(line, marker, line.length)
                break
            }

            val resolved = UrlResolver.resolve(sourceUrl, line.substring(valueStart, valueEnd))
            builder.append("URI=\"").append(register(resolved, referer, kind)).append('"')
            index = valueEnd + 1
        }

        return builder.toString()
    }
}
