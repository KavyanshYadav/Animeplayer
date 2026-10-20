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

import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.QualityPreference
import dev.animeplayer.network.RecordingHttpTransport
import dev.animeplayer.sources.hianime.internal.dto.HtmlEnvelope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End to end test of [HianimeSource] against the golden payloads captured from the live
 * site on 2026-10-21.
 */
class HianimeSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun transport(): RecordingHttpTransport {
        val envelope = json.decodeFromString<HtmlEnvelope>(Fixtures.load("episodes.json"))
        return RecordingHttpTransport(
            listOf(
                RecordingHttpTransport.urlContains(
                    urlPart = "/search?keyword=",
                    body = Fixtures.load("search.html"),
                ),
                RecordingHttpTransport.urlContains(
                    urlPart = "/api/theme/episode/list/",
                    body = Fixtures.load("episodes.json"),
                ),
                RecordingHttpTransport.urlContains(
                    urlPart = "/api/theme/episode/servers?episodeId=",
                    body = Fixtures.load("servers.json"),
                ),
                RecordingHttpTransport.urlContains(
                    urlPart = "zokoanime.video",
                    body = Fixtures.load("embed.html"),
                ),
                RecordingHttpTransport.urlContains(
                    urlPart = "master.m3u8",
                    body = Fixtures.load("master.m3u8"),
                ),
            ),
        )
    }

    @Test
    fun searchesTheSite() = runTest {
        val source = HianimeSource(transport())

        val results = source.search("one piece")

        assertTrue(results.isNotEmpty(), "expected search results")
        val onePiece = results.first { it.title == "One Piece" }
        assertEquals("one-piece-1", onePiece.id.slug)
        assertEquals("1", onePiece.id.numericId)
        // The native title is only kept when it differs from the primary title.
        assertEquals(null, onePiece.japaneseTitle)
        assertEquals("One Piece Film: Gold", results.first { it.id.slug == "one-piece-film-gold-925" }.japaneseTitle)

        // The sidebar repeats the popular series; the parser must cut it off.
        assertEquals(results.size, results.map { it.id.slug }.distinct().size)
        assertEquals(1, results.count { it.id.slug == "one-piece-1" })
    }

    @Test
    fun parsesTheEpisodeList() = runTest {
        val source = HianimeSource(transport())

        val list = source.episodes(HianimeSourceTestIds.onePiece)

        assertEquals(30, list.episodes.size)
        assertEquals("1", list.episodes.first().number)
        assertEquals("30", list.episodes.last().number)
        assertEquals(0, list.episodes.first().index)
        assertEquals(29, list.episodes.last().index)
        // The upstream total is wrong on purpose; the parsed count is authoritative.
        assertEquals(12, list.totalReported)
    }

    @Test
    fun resolvesAnEpisodeIntoAPlayableSource() = runTest {
        val source = HianimeSource(transport())

        val playable = source.resolve(
            id = HianimeSourceTestIds.onePiece,
            episode = source.episodes(HianimeSourceTestIds.onePiece).episodes.first(),
            audioTrack = AudioTrack.Sub,
            quality = QualityPreference.Best,
        )

        assertEquals(1080, playable.variant.quality.height)
        assertEquals("1080p", playable.variant.quality.label)
        assertEquals(
            "https://hls.dramahot.top/p/scxqicy/ta9t4m1bhw/cm72fkvbpx/09qmw7k6mi5nzd/1080/index.m3u8",
            playable.variant.url,
        )
        assertEquals("https://zokoanime.video/", playable.referer)
        assertEquals("ZokoAnime", playable.serverName)
        assertEquals(AudioTrack.Sub, playable.audioTrack)

        val subtitle = assertNotNull(playable.defaultSubtitle)
        assertEquals("en", subtitle.language)
        assertTrue(subtitle.isDefault)
        assertTrue(subtitle.url.endsWith(".vtt"), "unexpected subtitle url: ${subtitle.url}")

        // The intro/outro segments come from the player configuration, so no external
        // skip detection service is needed.
        val skip = assertNotNull(playable.skip)
        assertEquals(31, skip.intro?.startSeconds)
        assertEquals(111, skip.intro?.endSeconds)
        assertEquals(1376, skip.outro?.startSeconds)
        assertEquals(1447, skip.outro?.endSeconds)
    }

    @Test
    fun sendsTheRefererOnTheMasterPlaylistRequest() = runTest {
        val http = transport()
        val source = HianimeSource(http)

        source.resolve(
            id = HianimeSourceTestIds.onePiece,
            episode = source.episodes(HianimeSourceTestIds.onePiece).episodes.first(),
        )

        val masterRequest = http.recordedRequests.last { it.url.endsWith("master.m3u8") }
        assertEquals("https://zokoanime.video/", masterRequest.headers["Referer"])
    }

    @Test
    fun usesTheEpisodeNumberAsTheUpstreamId() = runTest {
        val http = transport()
        val source = HianimeSource(http)

        source.resolve(
            id = HianimeSourceTestIds.onePiece,
            episode = source.episodes(HianimeSourceTestIds.onePiece).episodes.first(),
        )

        val serversRequest = http.recordedRequests.last { it.url.contains("/servers?episodeId=") }
        assertTrue(serversRequest.url.endsWith("episodeId=1"), serversRequest.url)
    }
}

/** The identifiers used by the golden fixtures. */
internal object HianimeSourceTestIds {
    val onePiece = dev.animeplayer.core.model.AnimeId("one-piece-1", "1")
}
