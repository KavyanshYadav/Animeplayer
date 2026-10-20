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

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistRewriterTest {

    private fun rewriter(tokens: MutableList<String>): PlaylistRewriter =
        PlaylistRewriter(baseUrl = "https://proxy.example") { url, _, kind ->
            val token = "token-${tokens.size}"
            tokens += "$token|$kind|$url"
            "https://proxy.example/hls/$token"
        }

    @Test
    fun rewritesRelativeVariantUris() = runBlocking {
        val tokens = mutableListOf<String>()
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5300000,RESOLUTION=1920x1080
            1080/index.m3u8
        """.trimIndent()

        val rewritten = rewriter(tokens).rewrite(
            playlist = playlist,
            sourceUrl = "https://cdn.example/p/token/master.m3u8",
            referer = "https://cdn.example/",
            kind = ProxyTarget.Kind.Segment,
        )

        assertTrue(rewritten.contains("https://proxy.example/hls/token-0"), rewritten)
        assertTrue(
            tokens.single().endsWith("https://cdn.example/p/token/1080/index.m3u8"),
            tokens.single(),
        )
    }

    @Test
    fun rewritesAbsoluteSegmentUris() = runBlocking {
        val tokens = mutableListOf<String>()
        val playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:11.386,
            https://segments.example/s/seg_00000.ts
        """.trimIndent()

        val rewritten = rewriter(tokens).rewrite(
            playlist = playlist,
            sourceUrl = "https://cdn.example/p/token/1080/index.m3u8",
            referer = "https://cdn.example/",
            kind = ProxyTarget.Kind.Segment,
        )

        assertTrue(rewritten.contains("https://proxy.example/hls/token-0"), rewritten)
        assertTrue(tokens.single().endsWith("https://segments.example/s/seg_00000.ts"), tokens.single())
    }

    @Test
    fun rewritesTheUriAttributeOfKeys() = runBlocking {
        val tokens = mutableListOf<String>()
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example/key.bin"
        """.trimIndent()

        val rewritten = rewriter(tokens).rewrite(
            playlist = playlist,
            sourceUrl = "https://cdn.example/p/index.m3u8",
            referer = "https://cdn.example/",
            kind = ProxyTarget.Kind.Segment,
        )

        assertTrue(rewritten.contains("""URI="https://proxy.example/hls/token-0""""), rewritten)
    }

    @Test
    fun keepsTheTagLinesIntact() = runBlocking {
        val tokens = mutableListOf<String>()
        val playlist = "#EXTM3U\n#EXT-X-VERSION:4\n"

        val rewritten = rewriter(tokens).rewrite(
            playlist = playlist,
            sourceUrl = "https://cdn.example/master.m3u8",
            referer = "https://cdn.example/",
            kind = ProxyTarget.Kind.Segment,
        )

        assertEquals(playlist, rewritten)
        assertTrue(tokens.isEmpty())
    }
}
