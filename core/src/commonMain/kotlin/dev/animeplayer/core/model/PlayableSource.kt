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
package dev.animeplayer.core.model

/**
 * A subtitle track offered alongside a stream.
 *
 * @property url the absolute URL of the subtitle file (usually WebVTT).
 */
public data class SubtitleTrack(
    public val language: String,
    public val label: String,
    public val url: String,
    public val isDefault: Boolean = false,
) {
    override fun toString(): String = "$label ($language)"
}

/** A time range expressed in whole seconds. */
public data class TimeRange(
    public val startSeconds: Int,
    public val endSeconds: Int,
) {
    init {
        require(startSeconds >= 0) { "startSeconds must not be negative" }
        require(endSeconds >= startSeconds) { "endSeconds must not be before startSeconds" }
    }

    public val durationSeconds: Int get() = endSeconds - startSeconds
}

/**
 * Segments that can be skipped automatically (intro / outro).
 *
 * Upstream providers embed this information into their player configuration, which makes a
 * separate "skip detection" service unnecessary.
 */
public data class SkipSegments(
    public val intro: TimeRange? = null,
    public val outro: TimeRange? = null,
) {
    public val isEmpty: Boolean get() = intro == null && outro == null
}

/**
 * Everything a player needs in order to play one episode.
 *
 * @property referer the `Referer` header value that the streaming host requires. Most
 *   upstream CDNs answer with `403 Forbidden` when it is missing, so it must be applied to
 *   every request the player makes — including subtitle requests.
 * @property serverName the name of the upstream server the stream was resolved from.
 */
public data class PlayableSource(
    public val variant: StreamVariant,
    public val subtitles: List<SubtitleTrack> = emptyList(),
    public val referer: String,
    public val skip: SkipSegments? = null,
    public val downloadUrl: String? = null,
    public val serverName: String? = null,
    public val audioTrack: AudioTrack = AudioTrack.Sub,
    /**
     * The URL of the HLS master playlist, when the upstream offers one.
     *
     * The master playlist lists every rendition, so a player that supports adaptive
     * bitrate can use it instead of the single, already selected [variant].
     */
    public val masterUrl: String? = null,
    /** Every rendition that was discovered, best first. */
    public val variants: List<StreamVariant> = listOf(variant),
) {
    /** The subtitle track that should be enabled by default. */
    public val defaultSubtitle: SubtitleTrack?
        get() = subtitles.firstOrNull { it.isDefault } ?: subtitles.firstOrNull()
}
