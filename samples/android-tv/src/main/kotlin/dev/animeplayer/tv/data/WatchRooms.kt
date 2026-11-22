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

import dev.animeplayer.core.spi.HttpMethod
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpTransport
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class RoomPlayback(val episode: String, val position: Long, val playing: Boolean)

@Serializable
internal data class RoomSnapshot(val playback: RoomPlayback, val ageMs: Long)

@Serializable
private data class RoomCreated(val code: String, val hostKey: String)

internal data class WatchRoom(val server: String, val code: String, val episode: String, val hostKey: String? = null)

internal fun roomServer(input: String): String? = runCatching {
    val value = input.trim().trimEnd('/')
    val url = URI(value)
    value.takeIf { url.scheme == "https" && url.host != null && url.path.orEmpty().isEmpty() &&
        url.userInfo == null && url.query == null && url.fragment == null }
}.getOrNull()

internal class WatchRooms(private val transport: HttpTransport) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun create(server: String, playback: RoomPlayback): WatchRoom {
        val body = request(server, "", HttpMethod.Post, json.encodeToString(playback))
        val created = json.decodeFromString<RoomCreated>(body)
        require(created.code.matches(Regex("[A-Z2-9]{8}")))
        return WatchRoom(server, created.code, playback.episode, created.hostKey)
    }

    suspend fun join(server: String, code: String, episode: String): WatchRoom {
        require(code.matches(Regex("[A-Z2-9]{8}"))) { "Enter the eight-character room code." }
        val room = WatchRoom(server, code, episode)
        if (read(room).playback.episode != episode) {
            throw IllegalArgumentException("Open the same episode as the host before joining.")
        }
        return room
    }

    suspend fun read(room: WatchRoom): RoomSnapshot =
        json.decodeFromString(request(room.server, "/${room.code}", HttpMethod.Get))

    suspend fun update(room: WatchRoom, playback: RoomPlayback) {
        request(room.server, "/${room.code}/playback", HttpMethod.Post, json.encodeToString(playback), room.hostKey)
    }

    private suspend fun request(server: String, path: String, method: HttpMethod, body: String? = null, key: String? = null): String {
        val response = transport.execute(HttpRequest(
            url = "$server/api/watch-rooms$path", method = method, timeout = 5.seconds,
            body = body?.encodeToByteArray(),
            headers = buildMap {
                put("Content-Type", "application/json")
                if (key != null) put("Authorization", "Bearer $key")
            },
        ))
        if (!response.isSuccessful) error("The watch room is unavailable.")
        return response.bodyAsText()
    }
}
