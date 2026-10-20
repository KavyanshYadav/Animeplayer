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
package dev.animeplayer.core.spi

import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.SubtitleTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything a player needs to start playback.
 *
 * @property resumePositionMs the position playback should start at, in milliseconds.
 * @property skipIntro whether the intro segment should be skipped automatically.
 */
public data class PlaybackRequest(
    public val source: PlayableSource,
    public val title: String,
    public val resumePositionMs: Long = 0L,
    public val skipIntro: Boolean = false,
) {
    /** The position playback should start at, taking the skip flag into account. */
    public val effectiveStartPositionMs: Long
        get() {
            if (!skipIntro) return resumePositionMs
            val intro = source.skip?.intro ?: return resumePositionMs
            val introEnd = intro.endSeconds * 1000L
            return if (resumePositionMs in 1 until introEnd) introEnd else resumePositionMs
        }
}

/** The observable state of a playback session. */
public data class PlaybackState(
    public val isPlaying: Boolean = false,
    public val positionMs: Long = 0L,
    public val durationMs: Long = 0L,
    public val bufferedMs: Long = 0L,
    public val currentSubtitle: SubtitleTrack? = null,
    public val error: String? = null,
) {
    /** Progress in the `0.0..1.0` range. */
    public val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * A live playback session.
 *
 * Implementations are expected to be usable from a single coroutine at a time and to release
 * their resources in [close].
 */
public interface PlaybackSession {
    /** The current state, updated as playback progresses. */
    public val state: StateFlow<PlaybackState>

    /** Starts or resumes playback. */
    public suspend fun play()

    /** Pauses playback. */
    public suspend fun pause()

    /** Seeks to [positionMs]. */
    public suspend fun seekTo(positionMs: Long)

    /** Enables a subtitle track, or disables subtitles when `null`. */
    public suspend fun selectSubtitle(track: SubtitleTrack?)

    /** Releases all resources. The session must not be used afterwards. */
    public fun close()
}

/**
 * A playback backend for a specific platform.
 *
 * The library ships implementations for Media3 (Android), mpv (desktop JVM) and hls.js
 * (browser); see the `player` module.
 */
public interface PlayerEngine {
    /** A stable identifier, for example `media3`. */
    public val id: String

    /** `false` when the engine cannot be used in the current environment. */
    public val isAvailable: Boolean

    /** Prepares a playback session. The session is not started yet. */
    public suspend fun prepare(request: PlaybackRequest): PlaybackSession
}
