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
package dev.animeplayer.tv.ui.player

import androidx.media3.common.text.Cue
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.SkipSegments
import dev.animeplayer.core.model.StreamVariant
import dev.animeplayer.core.model.SubtitleTrack

/**
 * The state of the player.
 *
 * One object, one truth. The controls, the panels and the subtitles all read from it,
 * and nothing keeps a copy of its own.
 */
public data class TvPlayerState(

    /** Where the player is in its life. */
    val phase: Phase = Phase.Loading,

    /** The title of the series. */
    val title: String = "",

    /** The label of the episode, for example `Episode 3 · ZokoAnime`. */
    val episodeLabel: String = "",

    /** The description of the series. */
    val description: String? = null,

    /** The position of the playback, in milliseconds. */
    val positionMs: Long = 0L,

    /** The length of the episode, in milliseconds. */
    val durationMs: Long = 0L,

    /** How much of the episode is buffered, in milliseconds. */
    val bufferedMs: Long = 0L,

    /** `true` while the episode is playing. */
    val isPlaying: Boolean = false,

    /** The cues of the subtitle track. */
    val cues: List<Cue> = emptyList(),

    /** The subtitle tracks of the episode. */
    val subtitles: List<SubtitleTrack> = emptyList(),

    /** The subtitle track that is on. */
    val activeSubtitle: SubtitleTrack? = null,

    /** The qualities the episode is offered in. */
    val variants: List<StreamVariant> = emptyList(),

    /** The quality that is playing. */
    val activeQuality: String? = null,

    /** The audio track of the episode. */
    val audioTrack: AudioTrack = AudioTrack.Sub,

    /** The segments that can be skipped. */
    val skip: SkipSegments? = null,

    /** The episodes of the series, for the panel. */
    val episodes: List<Episode> = emptyList(),

    /** The number of the episode that is playing. */
    val currentEpisode: String = "",

    /** The message of the failure, when the episode could not be played. */
    val error: String? = null,
) {
    /** Where the player is in its life. */
    public enum class Phase {
        /** The episode is being resolved. */
        Loading,

        /** The episode is ready. */
        Ready,

        /** The episode could not be resolved. */
        Failed,
    }

    /** `true` when the intro can be skipped. */
    public val canSkipIntro: Boolean
        get() {
            val intro = skip?.intro ?: return false
            return positionMs in intro.startSeconds * 1000L until intro.endSeconds * 1000L
        }

    /** How much of the episode has been watched, in the `0..1` range. */
    public val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * One episode of the series, as the panel of the player shows it.
 */
public data class Episode(
    val number: String,
    val title: String,
    val durationSeconds: Int? = null,
    val watched: Float = 0f,
    val thumbnailUrl: String? = null,
) {
    /** `true` when the episode was watched to the end. */
    public val isWatched: Boolean get() = watched >= 0.9f
}
