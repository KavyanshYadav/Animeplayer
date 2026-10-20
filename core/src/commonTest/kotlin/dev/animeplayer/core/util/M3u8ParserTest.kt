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
import kotlin.test.Test
import kotlin.test.assertEquals

class M3u8ParserTest {

    @Test
    fun parsesTheRealMasterPlaylist() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-STREAM-INF:BANDWIDTH=5300000,RESOLUTION=1920x1080
            1080/index.m3u8
        """.trimIndent()

        val variants = M3u8Parser.parseMaster(playlist, "https://cdn.example.com/p/token/master.m3u8")

        assertEquals(1, variants.size)
        val variant = variants.single()
        assertEquals(1080, variant.quality.height)
        assertEquals(1920, variant.quality.width)
        assertEquals("1080p", variant.quality.label)
        assertEquals(5_300_000L, variant.bandwidth)
        assertEquals("https://cdn.example.com/p/token/1080/index.m3u8", variant.url)
    }

    @Test
    fun sortsBestFirst() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5300000,RESOLUTION=1920x1080
            1080/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480
            480/index.m3u8
        """.trimIndent()

        val variants = M3u8Parser.parseMaster(playlist, "https://cdn.example.com/master.m3u8")

        assertEquals(listOf(1080, 720, 480), variants.map { it.quality.height })
    }

    @Test
    fun ignoresIFrameStreams() {
        val playlist = """
            #EXTM3U
            #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=100000,RESOLUTION=640x360,URI="iframe/index.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=5300000,RESOLUTION=1920x1080
            1080/index.m3u8
        """.trimIndent()

        val variants = M3u8Parser.parseMaster(playlist, "https://cdn.example.com/master.m3u8")

        assertEquals(1, variants.size)
        assertEquals(1080, variants.single().quality.height)
    }

    @Test
    fun handlesCodecsWithCommas() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5300000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1920x1080,FRAME-RATE=23.976
            1080/index.m3u8
        """.trimIndent()

        val variant = M3u8Parser.parseMaster(playlist, "https://cdn.example.com/master.m3u8").single()

        assertEquals("avc1.640028,mp4a.40.2", variant.codecs)
        assertEquals(23.976, variant.frameRate)
        assertEquals(1080, variant.quality.height)
    }

    @Test
    fun handlesVariantsWithoutResolution() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5300000
            auto/index.m3u8
        """.trimIndent()

        val variant = M3u8Parser.parseMaster(playlist, "https://cdn.example.com/master.m3u8").single()

        assertEquals(0, variant.quality.height)
        assertEquals("auto", variant.quality.label)
    }

    @Test
    fun keepsAbsoluteVariantUris() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5300000,RESOLUTION=1920x1080
            https://other.cdn/p/1080/index.m3u8
        """.trimIndent()

        val variant = M3u8Parser.parseMaster(playlist, "https://cdn.example.com/master.m3u8").single()

        assertEquals("https://other.cdn/p/1080/index.m3u8", variant.url)
    }

    @Test
    fun splitsAttributes() {
        val attributes = M3u8Parser.parseAttributes(
            "BANDWIDTH=5300000,CODECS=\"avc1.640028,mp4a.40.2\",RESOLUTION=1920x1080",
        )
        assertEquals("5300000", attributes["BANDWIDTH"])
        assertEquals("avc1.640028,mp4a.40.2", attributes["CODECS"])
        assertEquals("1920x1080", attributes["RESOLUTION"])
    }

    @Test
    fun qualitySelectorPrefersBest() {
        val variants = listOf(
            StreamVariant(VideoQuality(1280, 720), "a"),
            StreamVariant(VideoQuality(1920, 1080), "b"),
        )
        val selection = dev.animeplayer.core.model.QualitySelector.select(
            variants,
            dev.animeplayer.core.model.QualityPreference.Best,
        )
        assertEquals(1080, selection.variant?.quality?.height)
        assertEquals(true, selection.wasSatisfied)
    }
}
