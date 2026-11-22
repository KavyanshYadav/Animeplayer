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
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Duration.Companion.seconds

internal data class AnimeTheme(val label: String, val title: String?, val url: String)

internal class AnimeThemes(private val transport: HttpTransport) {
    suspend fun forAniList(id: Int): List<AnimeTheme> = try {
        val url = "https://api.animethemes.moe/anime?filter%5Bexternal_id%5D=$id" +
            "&filter%5Bsite%5D=AniList&filter%5Bhas%5D=resources" +
            "&include=animethemes.animethemeentries.videos,animethemes.song"
        val response = transport.execute(HttpRequest(url, timeout = 4.seconds))
        if (response.isSuccessful) parseAnimeThemes(response.bodyAsText()) else emptyList()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun parseAnimeThemes(body: String): List<AnimeTheme> {
    val root = Json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
    return root.objects("anime").flatMap { it.objects("animethemes") }.mapNotNull { theme ->
        val label = theme.text("slug")?.takeIf { it.matches(Regex("(OP|ED)[0-9]*")) } ?: return@mapNotNull null
        val title = (theme["song"] as? JsonObject)?.text("title")
        val url = theme.objects("animethemeentries").flatMap { it.objects("videos") }
            .firstNotNullOfOrNull { video -> video.text("link")?.takeIf(::trustedThemeVideo) } ?: return@mapNotNull null
        AnimeTheme(label, title, url)
    }.distinctBy(AnimeTheme::url)
}

private fun trustedThemeVideo(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host.equals("v.animethemes.moe", ignoreCase = true) &&
        uri.userInfo == null && uri.port == -1
}.getOrDefault(false)

private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
