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
import dev.animeplayer.network.KtorHttpTransport
import dev.animeplayer.network.TransportConfig
import dev.animeplayer.network.createDefaultHttpClient
import dev.animeplayer.sources.hianime.internal.dto.HtmlEnvelope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contract test against the live site.
 *
 * It is skipped unless the `animeplayer.live` system property is set, which the
 * `liveTest` Gradle task does. Run it with:
 *
 * ```
 * ./gradlew liveTest
 * ```
 *
 * This is the test that notices when the upstream markup changes.
 */
class LiveContractTest {

    private val enabled: Boolean
        get() = System.getProperty("animeplayer.live") == "true"

    @Test
    fun searchReturnsResults() = runTest {
        Assume.assumeTrue("live tests are disabled", enabled)

        val client = createDefaultHttpClient()
        val source = HianimeSource(KtorHttpTransport(client, TransportConfig()))

        try {
            val results = source.search("one piece")
            assertTrue(results.isNotEmpty(), "the search returned nothing")
            assertTrue(results.any { it.title == "One Piece" }, "One Piece is missing")
        } finally {
            client.close()
        }
    }

    @Test
    fun theEpisodeListIsComplete() = runTest {
        Assume.assumeTrue("live tests are disabled", enabled)

        val client = createDefaultHttpClient()
        val source = HianimeSource(KtorHttpTransport(client, TransportConfig()))

        try {
            val results = source.search("one piece")
            val onePiece = results.first { it.title == "One Piece" }
            val episodes = source.episodes(onePiece.id)

            assertTrue(episodes.episodes.size > 100, "only ${episodes.episodes.size} episodes")
            assertEquals("1", episodes.episodes.first().number)
        } finally {
            client.close()
        }
    }

    @Test
    fun anEpisodeResolvesIntoAPlayableSource() = runTest {
        Assume.assumeTrue("live tests are disabled", enabled)

        val client = createDefaultHttpClient()
        val source = HianimeSource(KtorHttpTransport(client, TransportConfig()))

        try {
            val results = source.search("one piece")
            val onePiece = results.first { it.title == "One Piece" }
            val episode = source.episodes(onePiece.id).episodes.first()

            val playable = source.resolve(
                id = onePiece.id,
                episode = episode,
                audioTrack = AudioTrack.Sub,
                quality = QualityPreference.Best,
            )

            assertTrue(playable.variant.url.endsWith(".m3u8"), playable.variant.url)
            assertTrue(playable.referer.startsWith("https://"), playable.referer)
            assertTrue(playable.subtitles.isNotEmpty(), "no subtitles were found")
        } finally {
            client.close()
        }
    }

    @Test
    fun theServersEndpointAnswers() = runTest {
        Assume.assumeTrue("live tests are disabled", enabled)

        val client = createDefaultHttpClient()
        val transport = KtorHttpTransport(client, TransportConfig())

        try {
            val body = transport.execute(
                dev.animeplayer.core.spi.HttpRequest(
                    url = "https://hianime.at/api/theme/episode/servers?episodeId=1",
                    headers = mapOf("User-Agent" to HianimeConfig.DEFAULT_USER_AGENT),
                ),
            ).bodyAsText()

            val envelope = Json { ignoreUnknownKeys = true }.decodeFromString<HtmlEnvelope>(body)
            assertTrue(envelope.html.contains("server-item"), "the server list changed its markup")
        } finally {
            client.close()
        }
    }
}
