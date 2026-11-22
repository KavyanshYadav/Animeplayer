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
package dev.animeplayer.tv.ui.player.panel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.RoomPlayback
import dev.animeplayer.tv.data.WatchRoom
import dev.animeplayer.tv.data.roomServer
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.components.TvTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun WatchRoomPanel(
    container: AppContainer,
    savedServer: String,
    room: WatchRoom?,
    playback: () -> RoomPlayback?,
    onRoom: (WatchRoom?) -> Unit,
    onClose: () -> Unit,
) {
    var address by remember { mutableStateOf(savedServer) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun enter(host: Boolean) {
        val server = roomServer(address)
        val current = playback()
        if (server == null || current == null) {
            message = if (server == null) "Enter the HTTPS address of your room server." else "Wait until the episode is ready."
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            try {
                val joined = if (host) container.watchRooms.create(server, current)
                else container.watchRooms.join(server, code.trim().uppercase(), current.episode)
                container.settings.setWatchRoomServer(server)
                message = null
                onRoom(joined)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IllegalArgumentException) {
                message = failure.message
            } catch (_: Exception) {
                message = "Couldn’t reach the room. Check the server address and code."
            } finally {
                busy = false
            }
        }
    }

    TvSheet(title = "Watch together", onDismiss = onClose, actions = {
        TvButton("Close", prominent = false, onClick = onClose)
        if (room == null) {
            TvButton("Host room", enabled = !busy, onClick = { enter(true) })
            TvButton("Join room", enabled = !busy, onClick = { enter(false) })
        } else {
            TvButton("Leave room", onClick = { onRoom(null) })
        }
    }) {
        val color = MaterialTheme.colorScheme.onSurface
        if (room == null) {
            Text("Enter your room server, then host or join the same episode.", color = color)
            TvTextField(address, { address = it.take(250) }, Modifier.fillMaxWidth(), placeholder = "HTTPS room server")
            TvTextField(code, { code = it.take(8).uppercase() }, Modifier.fillMaxWidth(), placeholder = "Room code")
        } else {
            Text("Room code  ${room.code}", color = color, style = MaterialTheme.typography.headlineMedium)
            Text("Open on your phone for chat", color = color)
            Text("${room.server}/watch-room/${room.code}", color = color)
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
