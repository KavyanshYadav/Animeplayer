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
package dev.animeplayer.cli

import dev.animeplayer.core.model.Anime
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.EpisodeList
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.QualityPreference
import dev.animeplayer.core.model.QualitySelector
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.core.util.EpisodeSelector
import dev.animeplayer.network.KtorHttpTransport
import dev.animeplayer.network.TransportConfig
import dev.animeplayer.network.createDefaultHttpClient
import dev.animeplayer.player.mpv.MpvPlayerEngine
import dev.animeplayer.sources.hianime.HianimeConfig
import dev.animeplayer.sources.hianime.HianimeSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

/**
 * The command line front end.
 *
 * It follows the shape of the original `ani-cli`: search, pick a series, pick an episode,
 * resolve the stream and hand it to a player.
 */
public object Main {

    @JvmStatic
    public fun main(args: Array<String>) {
        val options = CliOptions.parse(args)
        runBlocking { run(options) }
    }

    private suspend fun run(options: CliOptions) {
        val client = createDefaultHttpClient()
        val transport = KtorHttpTransport(
            client = client,
            config = TransportConfig(userAgent = HianimeConfig.DEFAULT_USER_AGENT),
        )
        val source = HianimeSource(transport, HianimeConfig())

        try {
            when {
                options.showSchedule -> printSchedule(options)
                else -> playEpisode(source, options)
            }
        } finally {
            client.close()
        }
    }

    private suspend fun playEpisode(source: HianimeSource, options: CliOptions) {
        val query = options.query ?: prompt("Search anime: ")
        if (query.isBlank()) {
            println("No query.")
            return
        }

        val results = source.search(query)
        if (results.isEmpty()) {
            println("No results found for '$query'.")
            return
        }

        val anime = selectAnime(results, options)
        val episodeList = source.episodes(anime.id)

        if (options.listQualities) {
            printQualities(source, anime, episodeList, options)
            return
        }

        val episodes = selectEpisodes(episodeList, options)
        if (episodes.isEmpty()) {
            println("Invalid episode selection.")
            return
        }

        episodes.forEach { episode ->
            playOne(source, anime, episode, options)
        }
    }

    private suspend fun playOne(
        source: HianimeSource,
        anime: Anime,
        episode: Episode,
        options: CliOptions,
    ) {
        println("Resolving ${anime.title} episode ${episode.number}...")

        val playable = source.resolve(
            id = anime.id,
            episode = episode,
            audioTrack = options.audioTrack,
            quality = options.quality?.let { QualityPreference.Label(Regex(it, RegexOption.IGNORE_CASE)) }
                ?: QualityPreference.Best,
        )

        if (options.json) {
            println(toJson(playable))
            return
        }

        println("Playing ${anime.title} episode ${episode.number} [${playable.variant.quality.label}]")
        playable.subtitles.forEach { subtitle ->
            println("  subtitle: ${subtitle.label} -> ${subtitle.url}")
        }

        when (options.player) {
            "debug" -> {
                println("  url: ${playable.variant.url}")
                println("  referer: ${playable.referer}")
                playable.skip?.intro?.let { println("  skip intro: ${it.startSeconds}s - ${it.endSeconds}s") }
            }

            else -> {
                val engine = MpvPlayerEngine()
                if (!engine.isAvailable) {
                    println("mpv was not found on the PATH — falling back to printing the link.")
                    println("  url: ${playable.variant.url}")
                    println("  referer: ${playable.referer}")
                    return
                }

                val session = engine.prepare(
                    PlaybackRequest(
                        source = playable,
                        title = "${anime.title} Episode ${episode.number}",
                    ),
                )
                session.play()
                session.close()
            }
        }
    }

    private suspend fun printQualities(
        source: HianimeSource,
        anime: Anime,
        episodeList: EpisodeList,
        options: CliOptions,
    ) {
        val episode = episodeList.episodes.firstOrNull() ?: return
        val playable = source.resolve(
            id = anime.id,
            episode = episode,
            audioTrack = options.audioTrack,
            quality = QualityPreference.Best,
        )
        println("Available for ${anime.title} episode ${episode.number}:")
        println("  ${playable.variant.quality.label} -> ${playable.variant.url}")
    }

    private suspend fun printSchedule(options: CliOptions) {
        val query = options.query ?: prompt("Search anime: ")
        println("The schedule provider lives in the 'dev.animeplayer.sources.schedule' module.")
    }

    private fun selectAnime(results: List<Anime>, options: CliOptions): Anime {
        options.selectNth?.let { index ->
            return results.getOrNull(index - 1) ?: results.first()
        }

        if (results.size == 1) return results.first()

        println("Search results:")
        results.take(20).forEachIndexed { index, anime ->
            println("  ${index + 1}. ${anime.title} (${anime.id.slug})")
        }

        val answer = prompt("Select anime [1-${minOf(20, results.size)}]: ")
        val index = answer.trim().toIntOrNull() ?: 1
        return results.getOrNull(index - 1) ?: results.first()
    }

    private fun selectEpisodes(episodeList: EpisodeList, options: CliOptions): List<Episode> {
        options.episode?.let { selector ->
            val expanded = EpisodeSelector.expand(episodeList, selector)
            if (expanded.isNotEmpty()) return expanded
        }

        if (episodeList.episodes.size == 1) return episodeList.episodes

        println("Episodes:")
        episodeList.episodes.take(30).forEachIndexed { index, episode ->
            println("  ${index + 1}. ${episode.number}")
        }

        val answer = prompt("Select episode [number, range or 'all']: ")
        if (answer.isBlank()) return listOfNotNull(episodeList.latest)

        return EpisodeSelector.expand(episodeList, answer).ifEmpty {
            listOfNotNull(episodeList.latest)
        }
    }

    private fun toJson(playable: PlayableSource): String {
        val json = buildJsonObject {
            put("url", JsonPrimitive(playable.variant.url))
            put("quality", JsonPrimitive(playable.variant.quality.label))
            put("referer", JsonPrimitive(playable.referer))
            put(
                "subtitles",
                JsonArray(
                    playable.subtitles.map { track ->
                        buildJsonObject {
                            put("language", JsonPrimitive(track.language))
                            put("label", JsonPrimitive(track.label))
                            put("url", JsonPrimitive(track.url))
                            put("default", JsonPrimitive(track.isDefault))
                        }
                    },
                ),
            )
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json)
    }

    private fun prompt(message: String): String {
        print(message)
        System.out.flush()
        return readlnOrNull().orEmpty()
    }
}
