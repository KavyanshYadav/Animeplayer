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
package dev.animeplayer.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
internal data class RoomPlayback(val episode: String, val position: Long, val playing: Boolean)

@Serializable
private data class RoomCreated(val code: String, val hostKey: String)

@Serializable
private data class RoomMessage(val text: String)

@Serializable
private data class RoomSnapshot(val playback: RoomPlayback, val ageMs: Long, val chat: List<String>)

internal fun Route.watchRoomRoutes() {
    val random = SecureRandom()
    val rooms = linkedMapOf<String, Room>()
    fun now() = System.nanoTime() / 1_000_000
    fun clearExpired() {
        rooms.entries.removeAll { now() - it.value.updated > 60 * 60 * 1000L }
    }
    fun room(code: String?) = synchronized(rooms) {
        clearExpired()
        if (code == null) null else rooms[code]
    }

    post("/api/watch-rooms") {
        val playback = call.bounded<RoomPlayback>()?.valid()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val created = synchronized(rooms) {
            clearExpired()
            if (rooms.size >= 200) null else {
                val key = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also(random::nextBytes))
                var code: String
                do { code = (1..8).joinToString("") { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[random.nextInt(32)].toString() } }
                while (code in rooms)
                RoomCreated(code, key).also { rooms[code] = Room(key, playback, now()) }
            }
        } ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
        call.respond(created)
    }
    get("/api/watch-rooms/{code}") {
        val room = room(call.parameters["code"]) ?: return@get call.respond(HttpStatusCode.NotFound)
        val snapshot = synchronized(room) { RoomSnapshot(room.playback, (now() - room.updated).coerceAtLeast(0), room.chat.toList()) }
        call.respond(snapshot)
    }
    post("/api/watch-rooms/{code}/playback") {
        val room = room(call.parameters["code"]) ?: return@post call.respond(HttpStatusCode.NotFound)
        if (call.request.header(HttpHeaders.Authorization) != "Bearer ${room.hostKey}") {
            return@post call.respond(HttpStatusCode.Forbidden)
        }
        val playback = call.bounded<RoomPlayback>()?.valid()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        synchronized(room) {
            room.playback = playback
            room.updated = now()
        }
        call.respond(HttpStatusCode.NoContent)
    }
    post("/api/watch-rooms/{code}/chat") {
        val room = room(call.parameters["code"]) ?: return@post call.respond(HttpStatusCode.NotFound)
        val message = call.bounded<RoomMessage>()?.text?.trim()?.takeIf { it.length in 1..240 }
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        synchronized(room) {
            if (room.chat.size >= 40) room.chat.removeFirst()
            room.chat += message
        }
        call.respond(HttpStatusCode.NoContent)
    }
    get("/watch-room/{code}") {
        if (room(call.parameters["code"]) == null) return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(roomChatPage, ContentType.Text.Html)
    }
}

private class Room(val hostKey: String, var playback: RoomPlayback, @Volatile var updated: Long) {
    val chat = ArrayDeque<String>()
}

internal fun RoomPlayback.valid(): RoomPlayback? =
    takeIf { episode.matches(Regex("[a-zA-Z0-9.:-]{3,180}")) && position in 0..172_800_000 }

private suspend inline fun <reified T : Any> ApplicationCall.bounded(): T? {
    val length = request.header(HttpHeaders.ContentLength)?.toIntOrNull() ?: return null
    if (length !in 1..4096) return null
    return try {
        receive<T>()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private val roomChatPage = """
<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Animeplayer Watch Room</title>
<style>
html{color-scheme:dark;font:17px system-ui;background:#101018;color:#f2ecfc}
main{max-width:38rem;margin:auto;padding:1rem}li{padding:.5rem;overflow-wrap:anywhere}
input,button{font:inherit;padding:.65rem;border-radius:.5rem}input{width:70%}
</style>
<main><h1>Watch room chat</h1><p id="status" role="status"></p><ul id="messages"></ul>
<form id="form"><input id="message" maxlength="240" aria-label="Message" required><button>Send</button></form></main>
<script>
const code = location.pathname.split('/').pop();
const uri = '/api/watch-rooms/' + encodeURIComponent(code);
const list = document.querySelector('#messages');
const status = document.querySelector('#status');
const field = document.querySelector('#message');
async function refresh() {
  try {
    const response = await fetch(uri);
    if (!response.ok) throw Error();
    const room = await response.json();
    status.textContent = 'Room ' + code + (room.playback.playing ? ' · Playing' : ' · Paused');
    list.replaceChildren(...room.chat.map(text => {
      const item = document.createElement('li');
      item.textContent = text;
      return item;
    }));
  } catch { status.textContent = 'Room unavailable.'; }
}
document.querySelector('#form').addEventListener('submit', async event => {
  event.preventDefault();
  const response = await fetch(uri + '/chat', { method:'POST',
    headers:{'Content-Type':'application/json'}, body:JSON.stringify({text:field.value}) });
  if (response.ok) { field.value = ''; await refresh(); }
});
refresh();
setInterval(refresh, 3000);
</script></html>
""".trimIndent()
