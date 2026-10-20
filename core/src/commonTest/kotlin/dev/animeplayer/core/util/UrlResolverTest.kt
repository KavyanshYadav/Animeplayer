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

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlResolverTest {

    @Test
    fun resolvesRelativeVariantUri() {
        val base = "https://hls.dramahot.top/p/scxqicy/ta9t4m1bhw/cm72fkvbpx/09qmw7k6mi5nzd/master.m3u8"
        assertEquals(
            "https://hls.dramahot.top/p/scxqicy/ta9t4m1bhw/cm72fkvbpx/09qmw7k6mi5nzd/1080/index.m3u8",
            UrlResolver.resolve(base, "1080/index.m3u8"),
        )
    }

    @Test
    fun resolvesAbsolutePath() {
        assertEquals(
            "https://example.com/other.m3u8",
            UrlResolver.resolve("https://example.com/a/b/master.m3u8", "/other.m3u8"),
        )
    }

    @Test
    fun keepsAbsoluteUrls() {
        val absolute = "https://other.cdn/segment.ts"
        assertEquals(absolute, UrlResolver.resolve("https://example.com/a/b.m3u8", absolute))
    }

    @Test
    fun resolvesProtocolRelativeUrls() {
        assertEquals(
            "https://other.cdn/segment.ts",
            UrlResolver.resolve("https://example.com/a/b.m3u8", "//other.cdn/segment.ts"),
        )
    }

    @Test
    fun resolvesParentSegments() {
        assertEquals(
            "https://example.com/a/c/segment.ts",
            UrlResolver.resolve("https://example.com/a/b/playlist.m3u8", "../c/segment.ts"),
        )
    }

    @Test
    fun keepsQueryString() {
        assertEquals(
            "https://example.com/a/b.m3u8?token=abc",
            UrlResolver.resolve("https://example.com/a/master.m3u8", "b.m3u8?token=abc"),
        )
    }

    @Test
    fun computesOrigin() {
        assertEquals(
            "https://zokoanime.video/",
            UrlResolver.origin("https://zokoanime.video/stream/mal/21/1/sub"),
        )
        assertEquals(
            "https://hls.dramahot.top/",
            UrlResolver.origin("https://hls.dramahot.top/p/x/y/master.m3u8"),
        )
    }

    @Test
    fun extractsHost() {
        assertEquals("hls.dramahot.top", UrlResolver.host("https://hls.dramahot.top/p/x.m3u8"))
        assertEquals("example.com", UrlResolver.host("https://example.com:8443/p/x.m3u8"))
    }
}
