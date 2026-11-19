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
package dev.animeplayer.tv.data

import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpResponse
import dev.animeplayer.core.spi.HttpTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbCatalogTest {
    @Test
    fun `matches by title year and animation and uses provider image sizes`() = runBlocking {
        val transport = FixtureTransport { path ->
            when {
                path.startsWith("search/tv?") -> """
                    {"results":[
                        {"id":1,"name":"Star Harbor","first_air_date":"2019-01-01","genre_ids":[16]},
                        {"id":2,"name":"Star Harbor","first_air_date":"2021-01-01","genre_ids":[18]},
                        {"id":3,"name":"Star Harbor","first_air_date":"2021-01-01","genre_ids":[16],
                         "poster_path":"/poster.jpg","backdrop_path":"/wide.jpg"}
                    ]}
                """
                path == "configuration" -> """
                    {"images":{"secure_base_url":"https://image.tmdb.org/t/p/",
                    "poster_sizes":["w342"],"backdrop_sizes":["w780"],"still_sizes":["w185"]}}
                """
                else -> error("Unexpected path $path")
            }
        }
        val catalog = TmdbCatalog(transport, "fixture-token")
        val media = CatalogMedia(id = "101", titles = listOf("Star Harbor"), seasonYear = 2021)
        val artwork = catalog.artwork(media)
        assertEquals("https://image.tmdb.org/t/p/w342/poster.jpg", artwork?.poster)
        assertEquals("https://image.tmdb.org/t/p/w780/wide.jpg", artwork?.backdrop)
        assertEquals(artwork, catalog.artwork(media))
        assertEquals(2, transport.paths.size)
    }

    @Test
    fun `maps a named sequel to its own season and keeps local episode numbering`() = runBlocking {
        val transport = FixtureTransport { path ->
            when {
                path.contains("query=Star+Harbor+Season+2") -> """{"results":[]}"""
                path.contains("query=Star+Harbor&") -> """
                    {"results":[{"id":3,"name":"Star Harbor","first_air_date":"2021-01-01","genre_ids":[16],
                                 "poster_path":"/series.jpg","backdrop_path":"/wide.jpg"}]}
                """
                path.startsWith("tv/3?") -> """
                    {"seasons":[{"season_number":0,"air_date":"2024-04-01","episode_count":12},
                                {"season_number":1,"air_date":"2021-01-01","episode_count":12},
                                {"season_number":2,"air_date":"2024-04-01","episode_count":12,"poster_path":"/season2.jpg"}]}
                """
                path.startsWith("tv/3/season/2?") -> """
                    {"episodes":[
                        {"episode_number":1,"season_number":2,"name":"A New Shore","overview":"The crew arrives.",
                         "air_date":"2024-04-01","still_path":"/shore.jpg","runtime":24},
                        {"episode_number":2,"season_number":1,"name":"Incorrect season"}
                    ]}
                """
                path == "configuration" -> "{}"
                else -> error("Unexpected path $path")
            }
        }
        val catalog = TmdbCatalog(transport, "fixture-token")
        val media = CatalogMedia(id = "102", titles = listOf("Star Harbor Season 2"), seasonYear = 2024, episodeCount = 12)
        assertEquals("https://image.tmdb.org/t/p/w500/season2.jpg", catalog.artwork(media)?.poster)
        val episode = catalog.episodes(media).single()
        assertEquals(1, episode.number)
        assertEquals("A New Shore", episode.title)
        assertEquals("The crew arrives.", episode.description)
        assertEquals(24, episode.lengthMinutes)
        assertEquals("https://image.tmdb.org/t/p/w300/shore.jpg", episode.thumbnailUrl)
        assertEquals(1711929600L, episode.airDate)
    }

    @Test
    fun `ambiguous titles and mismatched sequel lengths fall back`() = runBlocking {
        val transport = FixtureTransport { path ->
            when {
                path.contains("query=Star+Harbor+Season+2") -> """{"results":[]}"""
                path.contains("first_air_date_year=2021") -> """
                    {"results":[{"id":3,"name":"Star Harbor","first_air_date":"2021-01-01","genre_ids":[16]},
                                {"id":4,"name":"Star Harbor","first_air_date":"2021-01-01","genre_ids":[16]}]}
                """
                path.startsWith("search/") -> """
                    {"results":[{"id":3,"name":"Star Harbor","first_air_date":"2021-01-01","genre_ids":[16]}]}
                """
                path.startsWith("tv/3?") -> """
                    {"seasons":[{"season_number":2,"air_date":"2024-04-01","episode_count":24}]}
                """
                else -> error("Unexpected path $path")
            }
        }
        val catalog = TmdbCatalog(transport, "fixture-token")
        assertNull(catalog.artwork(CatalogMedia(id = "103", titles = listOf("Star Harbor"), seasonYear = 2021)))
        val splitCour = CatalogMedia(id = "104", titles = listOf("Star Harbor Season 2"), seasonYear = 2024, episodeCount = 12)
        assertTrue(catalog.episodes(splitCour).isEmpty())
        assertTrue(transport.paths.none { "/season/" in it })
    }

    @Test
    fun `loads and caches a suitable English title logo separately from card artwork`() = runBlocking {
        val transport = FixtureTransport { path ->
            when {
                path.startsWith("search/tv?") -> """
                    {"results":[{"id":3,"name":"Star Harbor","first_air_date":"2021-01-01","genre_ids":[16],
                                 "poster_path":"/poster.jpg","backdrop_path":"/wide.jpg"}]}
                """
                path.startsWith("tv/3/images?") -> """
                    {"logos":[{"iso_639_1":"en","file_path":"/unsupported.svg","vote_count":40},
                              {"iso_639_1":"ja","file_path":"/japanese.png","vote_count":50},
                              {"iso_639_1":"en","file_path":"/english.png","vote_count":10}]}
                """
                path == "configuration" -> """
                    {"images":{"secure_base_url":"https://image.tmdb.org/t/p/","logo_sizes":["w300"]}}
                """
                else -> error("Unexpected path $path")
            }
        }
        val catalog = TmdbCatalog(transport, "fixture-token")
        val media = CatalogMedia(id = "101", titles = listOf("Star Harbor"), seasonYear = 2021)
        assertEquals("https://image.tmdb.org/t/p/w1280/wide.jpg", catalog.artwork(media)?.backdrop)
        assertTrue(transport.paths.none { "/images?" in it })
        repeat(2) { assertEquals("https://image.tmdb.org/t/p/w300/english.png", catalog.titleLogo(media)) }
        assertEquals(1, transport.paths.count { "/images?" in it })
    }

    private class FixtureTransport(private val payload: (String) -> String) : HttpTransport {
        val paths = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            assertEquals("Bearer fixture-token", request.headers["Authorization"])
            val path = request.url.removePrefix("https://api.themoviedb.org/3/")
            paths += path
            return HttpResponse(200, emptyMap(), payload(path).encodeToByteArray(), request.url)
        }
    }
}
