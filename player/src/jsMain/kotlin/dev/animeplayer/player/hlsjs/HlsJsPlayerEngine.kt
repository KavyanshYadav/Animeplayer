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
package dev.animeplayer.player.hlsjs

import dev.animeplayer.core.model.SubtitleTrack
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.core.spi.PlaybackSession
import dev.animeplayer.core.spi.PlaybackState
import dev.animeplayer.core.spi.PlayerEngine
import kotlinx.browser.document
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event

/**
 * The browser playback backend, built on `hls.js`.
 *
 * `hls.js` is expected on the page as `window.Hls` — add it with a `<script>` tag or
 * with your own bundler. The library deliberately does not bundle it, so the host
 * application stays in control of the version and of the bundling strategy.
 *
 * ```html
 * <script src="https://cdn.jsdelivr.net/npm/hls.js@1.7.3/dist/hls.min.js"></script>
 * ```
 *
 * ### A note on the `Referer` header
 *
 * Browsers refuse to let a page set the `Referer` header of a `fetch` or `XMLHttpRequest`
 * — it is a forbidden header name — and the upstream CDN answers with `403 Forbidden`
 * without it. A browser client therefore cannot stream directly and has to go through the
 * `animeplayer-proxy` module, which fetches the playlist with the correct header and
 * rewrites the URLs. Safari can play HLS natively, but the same restriction applies.
 */
public class HlsJsPlayerEngine(
    private val video: HTMLVideoElement = createVideoElement(),
) : PlayerEngine {

    override val id: String = "hlsjs"

    override val isAvailable: Boolean
        get() = hlsConstructor() != null

    override suspend fun prepare(request: PlaybackRequest): PlaybackSession {
        val source = request.source
        val hls = hlsConstructor()

        if (hls == null) {
            return NativeHlsSession(video = video, request = request)
        }

        hls.loadSource(source.variant.url)
        hls.attachMedia(video)
        hls.on("hlsError", { event: Event -> handleError(hls, event) })

        return HlsJsSession(video, hls, request)
    }

    private fun hlsConstructor(): dynamic =
        js("(typeof window !== 'undefined' && window.Hls) ? window.Hls : null")

    private fun handleError(hls: dynamic, event: Event) {
        val data = event.asDynamic().data
        if (data != null && data.fatal == true) {
            hls.destroy()
        }
    }

    private companion object {
        fun createVideoElement(): HTMLVideoElement =
            document.createElement("video") as HTMLVideoElement
    }
}

private class HlsJsSession(
    private val video: HTMLVideoElement,
    private val hls: dynamic,
    private val request: PlaybackRequest,
) : PlaybackSession {

    private val mutableState = MutableStateFlow(PlaybackState())

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    init {
        video.addEventListener("timeupdate", { publish() })
        video.addEventListener("play", { publish() })
        video.addEventListener("pause", { publish() })
        video.addEventListener("loadedmetadata", { publish() })
        publish()
    }

    override suspend fun play() {
        video.play()
    }

    override suspend fun pause() {
        video.pause()
    }

    override suspend fun seekTo(positionMs: Long) {
        video.currentTime = positionMs / 1000.0
    }

    override suspend fun selectSubtitle(track: SubtitleTrack?) {
        if (track == null) {
            hls.subtitleTrack = -1
            return
        }
        hls.subtitleTrack = request.source.subtitles.indexOf(track)
    }

    override fun close() {
        hls.destroy()
    }

    private fun publish() {
        mutableState.value = PlaybackState(
            isPlaying = !video.paused && !video.ended,
            positionMs = (video.currentTime * 1000).toLong().coerceAtLeast(0L),
            durationMs = if (video.duration.isFinite()) (video.duration * 1000).toLong() else 0L,
            bufferedMs = if (video.buffered.length > 0) (video.buffered.end(0) * 1000).toLong() else 0L,
            currentSubtitle = request.source.defaultSubtitle,
            error = mutableState.value.error,
        )
    }
}

/**
 * A fallback for browsers that can play HLS natively — Safari — or that do not provide
 * `hls.js` at all.
 */
private class NativeHlsSession(
    private val video: HTMLVideoElement,
    private val request: PlaybackRequest,
) : PlaybackSession {

    private val mutableState = MutableStateFlow(PlaybackState())

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    init {
        video.src = request.source.variant.url
        video.addEventListener("timeupdate", { publish() })
        video.addEventListener("play", { publish() })
        video.addEventListener("pause", { publish() })
        publish()
    }

    override suspend fun play() {
        video.play()
    }

    override suspend fun pause() {
        video.pause()
    }

    override suspend fun seekTo(positionMs: Long) {
        video.currentTime = positionMs / 1000.0
    }

    override suspend fun selectSubtitle(track: SubtitleTrack?) {
        if (track == null) return
        val textTrack = video.asDynamic().textTracks[0]
        if (textTrack != null) {
            textTrack.mode = "showing"
        }
    }

    override fun close() {
        video.removeAttribute("src")
    }

    private fun publish() {
        mutableState.value = PlaybackState(
            isPlaying = !video.paused && !video.ended,
            positionMs = (video.currentTime * 1000).toLong().coerceAtLeast(0L),
            durationMs = if (video.duration.isFinite()) (video.duration * 1000).toLong() else 0L,
            currentSubtitle = request.source.defaultSubtitle,
            error = mutableState.value.error,
        )
    }
}
