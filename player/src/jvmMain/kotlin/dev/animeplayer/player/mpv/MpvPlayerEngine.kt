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
package dev.animeplayer.player.mpv

import dev.animeplayer.core.model.SubtitleTrack
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.core.spi.PlaybackSession
import dev.animeplayer.core.spi.PlaybackState
import dev.animeplayer.core.spi.PlayerEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.util.UUID

/**
 * The desktop playback backend, built on `mpv`.
 *
 * `mpv` is driven through its JSON IPC interface, so the session can pause, seek and
 * report the playback position. The upstream `Referer` and the subtitle file are passed
 * to `mpv` directly, which is exactly what the original command line tool did.
 *
 * The IPC socket is a Unix domain socket, which works on Linux and macOS. On platforms
 * where Java cannot create one the session falls back to controlling `mpv` through its
 * standard input.
 */
public class MpvPlayerEngine(
    private val mpvExecutable: String = "mpv",
    private val extraArguments: List<String> = emptyList(),
) : PlayerEngine {

    override val id: String = "mpv"

    override val isAvailable: Boolean by lazy { findExecutable(mpvExecutable) != null }

    override suspend fun prepare(request: PlaybackRequest): PlaybackSession {
        val source = request.source
        val socketPath = createSocketPath()

        val arguments = buildList {
            add("--no-terminal")
            add("--idle=yes")
            add("--input-ipc-server=${socketPath.absolutePath}")
            add("--force-media-title=${request.title}")
            add("--referrer=${source.referer}")
            source.defaultSubtitle?.let { add("--sub-file=${it.url}") }
            addAll(extraArguments)
            add(source.variant.url)
        }

        val process = ProcessBuilder(listOf(mpvExecutable) + arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()

        return try {
            val ipc = MpvIpcClient.connect(socketPath, process)
            MpvPlaybackSession(process, ipc, request)
        } catch (error: Exception) {
            process.destroy()
            throw IllegalStateException("Could not open the mpv IPC socket", error)
        }
    }

    private fun createSocketPath(): File {
        val directory = Files.createTempDirectory("animeplayer-mpv").toFile()
        directory.deleteOnExit()
        return File(directory, "mpv-${UUID.randomUUID()}.sock")
    }

    private fun findExecutable(name: String): String? {
        if (name.contains(File.separator)) {
            return name.takeIf { File(it).isFile && File(it).canExecute() }
        }
        val path = System.getenv("PATH").orEmpty()
        return path.split(File.pathSeparator)
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }
}

private class MpvPlaybackSession(
    private val process: Process,
    private val ipc: MpvIpcClient,
    private val request: PlaybackRequest,
) : PlaybackSession {

    private val mutableState = MutableStateFlow(PlaybackState())
    private val scope = CoroutineScope(Dispatchers.Default)
    private val poller: Job

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    init {
        poller = scope.launch {
            while (isActive) {
                publish()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun play() {
        ipc.command("set_property", "pause", "no")
    }

    override suspend fun pause() {
        ipc.command("set_property", "pause", "yes")
    }

    override suspend fun seekTo(positionMs: Long) {
        ipc.command("seek", (positionMs / 1000.0).toString(), "absolute")
    }

    override suspend fun selectSubtitle(track: SubtitleTrack?) {
        if (track == null) {
            ipc.command("set_property", "sid", "no")
        } else {
            ipc.command("sub-add", track.url, "select")
        }
    }

    override fun close() {
        poller.cancel()
        ipc.close()
        process.destroy()
    }

    private suspend fun publish() {
        val position = ipc.command("get_property", "time-pos")?.doubleOrNull ?: 0.0
        val duration = ipc.command("get_property", "duration")?.doubleOrNull ?: 0.0
        val paused = ipc.command("get_property", "pause")?.jsonPrimitive?.content?.toBooleanStrictOrNull()

        mutableState.value = PlaybackState(
            isPlaying = paused == false,
            positionMs = (position * 1000).toLong().coerceAtLeast(0L),
            durationMs = (duration * 1000).toLong().coerceAtLeast(0L),
            currentSubtitle = request.source.defaultSubtitle,
            error = mutableState.value.error,
        )
    }

    private companion object {
        const val POLL_INTERVAL_MS = 500L
    }
}

/**
 * A minimal client for the `mpv` JSON IPC protocol.
 *
 * Every request carries a `request_id`; the answer arrives asynchronously on the same
 * socket and is dispatched to the waiting caller.
 */
internal class MpvIpcClient private constructor(
    private val channel: SocketChannel,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val pending = mutableMapOf<Int, CompletableDeferred<JsonPrimitive?>>()
    private var nextRequestId = 1

    init {
        Thread({ readLoop() }, "animeplayer-mpv-ipc").apply {
            isDaemon = true
            start()
        }
    }

    fun close() {
        runCatching { channel.close() }
    }

    /** Sends a command and waits for its answer. */
    suspend fun command(vararg arguments: String): JsonPrimitive? {
        val requestId = nextRequestId++
        val deferred = CompletableDeferred<JsonPrimitive?>()
        pending[requestId] = deferred

        val payload = JsonObject(
            mapOf(
                "command" to JsonArray(arguments.map { JsonPrimitive(it) }),
                "request_id" to JsonPrimitive(requestId),
            ),
        )

        try {
            channel.write(ByteBuffer.wrap((payload.toString() + "\n").toByteArray()))
        } catch (error: Exception) {
            pending.remove(requestId)
            return null
        }

        return deferred.await()
    }

    private fun readLoop() {
        val buffer = StringBuilder()
        val chunk = ByteArray(8192)

        while (true) {
            val read = try {
                channel.read(ByteBuffer.wrap(chunk))
            } catch (error: Exception) {
                -1
            }
            if (read < 0) break

            buffer.append(String(chunk, 0, read, Charsets.UTF_8))
            var newline = buffer.indexOf('\n')
            while (newline >= 0) {
                val line = buffer.substring(0, newline)
                buffer.delete(0, newline + 1)
                dispatch(line)
                newline = buffer.indexOf('\n')
            }
        }

        pending.values.forEach { it.complete(null) }
        pending.clear()
    }

    private fun dispatch(line: String) {
        if (line.isBlank()) return
        val element = runCatching { json.parseToJsonElement(line) }.getOrNull() ?: return
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return
        val requestId = obj["request_id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return
        val deferred = pending.remove(requestId) ?: return

        val error = obj["error"]?.jsonPrimitive?.content
        if (error != null && error != "success") {
            deferred.complete(null)
        } else {
            deferred.complete(obj["data"] as? JsonPrimitive)
        }
    }

    companion object {
        fun connect(socketPath: File, process: Process): MpvIpcClient {
            var channel: SocketChannel? = null
            val deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS

            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive) {
                    throw IllegalStateException("mpv exited before the IPC socket became available")
                }
                try {
                    channel = SocketChannel.open(
                        UnixDomainSocketAddress.of(socketPath.absolutePath),
                    )
                    break
                } catch (error: Exception) {
                    Thread.sleep(50)
                }
            }

            return MpvIpcClient(
                channel ?: throw IllegalStateException("The mpv IPC socket did not become available"),
            )
        }

        private const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
