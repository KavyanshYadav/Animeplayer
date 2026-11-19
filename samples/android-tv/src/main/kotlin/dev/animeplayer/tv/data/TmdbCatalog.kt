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

import dev.animeplayer.core.model.CatalogEpisode
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpTransport
import java.net.URLEncoder
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal data class TmdbArtwork(
    val poster: String?,
    val backdrop: String?,
)

/** Optional artwork and episode metadata for AniList entries. */
internal class TmdbCatalog(
    private val transport: HttpTransport,
    private val readAccessToken: String,
) {
    private val requests = Semaphore(3)
    private val configuration = Memo<String, ImageConfiguration>(1)
    private val matches = Memo<String, Match>(128)
    private val logos = Memo<Pair<Boolean, Int>, String>(64)
    private val seasonLists = Memo<Int, List<Season>>(24)
    private val episodeLists = Memo<Pair<Int, Int>, List<CatalogEpisode>>(12)

    @Volatile private var credentialsRejected = false

    suspend fun artwork(media: CatalogMedia): TmdbArtwork? {
        val match = match(media) ?: return null
        val images = imageConfiguration()
        return TmdbArtwork(
            poster = images.url(images.poster, match.poster),
            backdrop = images.url(images.backdrop, match.backdrop),
        )
    }

    suspend fun titleLogo(media: CatalogMedia): String? {
        val match = match(media) ?: return null
        return logos.get(match.movie to match.id) {
            val kind = if (match.movie) "movie" else "tv"
            val images = request("$kind/${match.id}/images?include_image_language=en,ja,null") ?: return@get null
            val logo = images.objects("logos")
                .filter { it.string("file_path")?.endsWith(".png", ignoreCase = true) == true }
                .minWithOrNull(compareBy<JsonObject> {
                    when (it.string("iso_639_1")) {
                        "en" -> 0
                        "ja" -> 1
                        null -> 2
                        else -> 3
                    }
                }.thenByDescending { it.integer("vote_count") ?: 0 }
                    .thenByDescending { it.integer("width") ?: 0 }) ?: return@get null
            val sizes = imageConfiguration()
            sizes.url(sizes.logo, logo.string("file_path"))
        }
    }

    suspend fun episodes(media: CatalogMedia): List<CatalogEpisode> {
        val match = match(media)?.takeUnless { it.movie } ?: return emptyList()
        val season = match.season ?: selectSeason(media, seasons(match.id)) ?: return emptyList()
        val lastEpisode = media.episodeCount ?: Int.MAX_VALUE
        return episodeLists.get(match.id to season.number) {
            val payload = request("tv/${match.id}/season/${season.number}?language=en-US") ?: return@get null
            val images = imageConfiguration()
            payload.objects("episodes").mapNotNull { item ->
                val number = item.integer("episode_number")?.takeIf { it > 0 } ?: return@mapNotNull null
                if (item.integer("season_number") != season.number) return@mapNotNull null
                val score = item.primitive("vote_average")?.doubleOrNull
                    ?.takeIf { it in 0.0..10.0 && (item.integer("vote_count") ?: 0) > 0 }
                CatalogEpisode(
                    number = number,
                    title = item.string("name"),
                    description = item.string("overview"),
                    thumbnailUrl = images.url(images.still, item.string("still_path")),
                    airDate = item.string("air_date")?.let { value ->
                        runCatching { LocalDate.parse(value).atStartOfDay().toEpochSecond(ZoneOffset.UTC) }.getOrNull()
                    },
                    score = score?.times(10)?.roundToInt(),
                    lengthMinutes = item.integer("runtime")?.takeIf { it > 0 },
                )
            }.distinctBy { it.number }.sortedBy { it.number }.takeIf { it.isNotEmpty() }
        }.orEmpty().filter { it.number <= lastEpisode }
    }

    private suspend fun match(media: CatalogMedia): Match? = matches.get(media.id) {
        val year = media.seasonYear ?: return@get null
        val movie = media.format.equals("MOVIE", ignoreCase = true)
        val titles = media.titles.filter { it.isNotBlank() }.distinctBy { it.normalised() }.take(3)
        val aliases = media.titles.map { it.normalised() }.filter(String::isNotEmpty).toSet()
        val path = if (movie) "movie" else "tv"
        val yearProperty = if (movie) "release_date" else "first_air_date"
        val yearParameter = if (movie) "year" else "first_air_date_year"

        for (title in titles) {
            val found = search(path, title, "&$yearParameter=$year")
                .filter { it.year(yearProperty) == year && it.matchesTitle(aliases, movie) }
            found.singleOrNull()?.toMatch(movie)?.let { return@get it }
        }

        // AniList lists sequels separately. Only reuse a parent show's artwork once
        // TMDB also has a regular season with the expected release year and number.
        if (!movie) {
            for (title in titles) {
                val seasonName = explicitSeason.find(title) ?: continue
                val expectedNumber = seasonName.groupValues.drop(1).firstNotNullOfOrNull(String::toIntOrNull) ?: continue
                val baseTitle = title.substring(0, seasonName.range.first).trimEnd(' ', ':', '-', '–')
                if (baseTitle.isBlank()) continue
                val parent = search("tv", baseTitle)
                    .filter { it.matchesTitle(setOf(baseTitle.normalised()), movie = false) }
                    .singleOrNull()?.toMatch(false) ?: continue
                val season = seasons(parent.id).singleOrNull {
                    it.number == expectedNumber && it.year == year && it.agreesWith(media)
                } ?: continue
                return@get parent.copy(poster = season.poster ?: parent.poster, season = season)
            }
        }
        null
    }

    private suspend fun search(path: String, title: String, parameters: String = ""): List<JsonObject> {
        val query = URLEncoder.encode(title, Charsets.UTF_8.name())
        return request("search/$path?query=$query&include_adult=false&language=en-US$parameters")
            ?.objects("results").orEmpty()
    }

    private suspend fun seasons(id: Int): List<Season> = seasonLists.get(id) {
        request("tv/$id?language=en-US")?.objects("seasons")?.mapNotNull { item ->
            val number = item.integer("season_number")?.takeIf { it > 0 } ?: return@mapNotNull null
            val year = item.year("air_date") ?: return@mapNotNull null
            Season(number, year, item.integer("episode_count"), item.string("poster_path"))
        }
    }.orEmpty()

    private fun selectSeason(media: CatalogMedia, seasons: List<Season>): Season? {
        // For an ongoing show with local numbering starting at one, TMDB season 1
        // can supply matching early episodes. Later season numbers never get flattened.
        return seasons.singleOrNull { it.number == 1 && it.year == media.seasonYear && it.agreesWith(media) }
    }

    private suspend fun imageConfiguration(): ImageConfiguration = configuration.get("images") {
        val images = request("configuration")?.get("images") as? JsonObject ?: return@get null
        val secureBase = images.string("secure_base_url")?.takeIf { it.startsWith("https://image.tmdb.org/") }
            ?: return@get null
        ImageConfiguration(
            baseUrl = secureBase,
            poster = images.chooseSize("poster_sizes", listOf("w500", "w342", "w780")),
            backdrop = images.chooseSize("backdrop_sizes", listOf("w1280", "w780", "w300")),
            still = images.chooseSize("still_sizes", listOf("w300", "w185", "w92")),
            logo = images.chooseSize("logo_sizes", listOf("w500", "w300", "w185")),
        )
    } ?: ImageConfiguration()

    private suspend fun request(path: String): JsonObject? {
        if (credentialsRejected) return null
        return requests.withPermit {
            if (credentialsRejected) return@withPermit null
            try {
                withTimeoutOrNull(5.seconds) {
                    val response = transport.execute(
                        HttpRequest(
                            url = "https://api.themoviedb.org/3/$path",
                            headers = mapOf(
                                "Authorization" to "Bearer $readAccessToken",
                                "Accept" to "application/json",
                            ),
                            timeout = 4.seconds,
                        ),
                    )
                    if (response.status == 401 || response.status == 403) credentialsRejected = true
                    if (!response.isSuccessful) return@withTimeoutOrNull null
                    withContext(Dispatchers.Default) {
                        Json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    private data class Match(
        val id: Int,
        val movie: Boolean,
        val poster: String?,
        val backdrop: String?,
        val season: Season? = null,
    )

    private data class Season(val number: Int, val year: Int, val episodeCount: Int?, val poster: String?) {
        fun agreesWith(media: CatalogMedia): Boolean = media.episodeCount == null ||
            media.episodeCount == episodeCount || media.status.isAiring
    }

    private data class ImageConfiguration(
        val baseUrl: String = "https://image.tmdb.org/t/p/",
        val poster: String = "w500",
        val backdrop: String = "w1280",
        val still: String = "w300",
        val logo: String = "w500",
    ) {
        fun url(size: String, path: String?): String? =
            path?.takeIf { imageFile.matches(it) }?.let { "${baseUrl.trimEnd('/')}/$size$it" }
    }

    private fun JsonObject.toMatch(movie: Boolean): Match? =
        integer("id")?.takeIf { it > 0 }?.let { Match(it, movie, string("poster_path"), string("backdrop_path")) }

    private fun JsonObject.matchesTitle(aliases: Set<String>, movie: Boolean): Boolean {
        val genres = this["genre_ids"] as? JsonArray ?: return false
        if (genres.none { (it as? JsonPrimitive)?.intOrNull == 16 }) return false
        val title = if (movie) "title" else "name"
        return listOfNotNull(string(title), string("original_$title")).any { it.normalised() in aliases }
    }

    private fun JsonObject.chooseSize(key: String, sizes: List<String>): String {
        val available = (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return sizes.firstOrNull { it in available } ?: sizes.first()
    }

    private fun JsonObject.year(key: String): Int? = string(key)?.take(4)?.toIntOrNull()
    private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
    private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.integer(key: String): Int? = primitive(key)?.intOrNull

    private class Memo<K : Any, V : Any>(private val limit: Int) {
        private val cache = LinkedHashMap<K, Cached<V>>(16, 0.75f, true)
        private val stripes = List(16) { Mutex() }

        suspend fun get(key: K, load: suspend () -> V?): V? {
            val lock = stripes[(key.hashCode() and Int.MAX_VALUE) % stripes.size]
            return lock.withLock {
                val cached = synchronized(cache) { cache[key] }
                val freshness = if (cached?.value == null) 2.minutes else 30.minutes
                if (cached != null && cached.created.elapsedNow() < freshness) {
                    return@withLock cached.value
                }
                val value = load()
                synchronized(cache) {
                    cache[key] = Cached(value)
                    if (cache.size > limit) cache.remove(cache.keys.first())
                }
                value
            }
        }
    }

    private data class Cached<V>(val value: V?, val created: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow())

    private companion object {
        val explicitSeason = Regex("""(?:\bseason\s+(\d+)|\b(\d+)(?:st|nd|rd|th)\s+season)\s*$""", RegexOption.IGNORE_CASE)
        val imageFile = Regex("/[A-Za-z0-9._-]+")
        fun String.normalised(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)
            .lowercase().filter(Char::isLetterOrDigit)
    }
}
