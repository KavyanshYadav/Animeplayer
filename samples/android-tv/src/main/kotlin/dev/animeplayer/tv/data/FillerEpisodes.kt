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

import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.time.Duration.Companion.seconds

internal class FillerEpisodes(private val transport: HttpTransport) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<Pair<Int, Int>, Set<Int>>(16, 0.75f, true)

    suspend fun forEpisodes(malId: Int, numbers: List<String>): Set<String> {
        val requested = numbers.mapNotNull { it.toIntOrNull()?.takeIf { n -> n > 0 } }.toSet()
        val pages = requested.map { (it - 1) / 25 + 1 }.distinct().take(3)
        val fillers = mutex.withLock {
            pages.flatMap { page ->
                val key = malId to page
                cache[key] ?: (fetch(malId, page)?.also {
                    cache[key] = it
                    if (cache.size > 48) cache.remove(cache.keys.first())
                } ?: emptySet())
            }
        }
        return fillers.filter { it in requested }.mapTo(mutableSetOf(), Int::toString)
    }

    private suspend fun fetch(id: Int, page: Int): Set<Int>? = try {
        val reply = transport.execute(HttpRequest("https://api.jikan.moe/v4/anime/$id/episodes?page=$page", timeout = 4.seconds))
        if (reply.isSuccessful) parseFillerEpisodes(reply.bodyAsText()) else null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

internal fun parseFillerEpisodes(json: String): Set<Int>? {
    val root = Json.parseToJsonElement(json) as? JsonObject ?: return null
    val data = root["data"] as? JsonArray ?: return null
    return data.mapNotNullTo(mutableSetOf()) { element ->
        val episode = element as? JsonObject ?: return@mapNotNullTo null
        if ((episode["filler"] as? JsonPrimitive)?.booleanOrNull != true) return@mapNotNullTo null
        (episode["mal_id"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
    }
}
