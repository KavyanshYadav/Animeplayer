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
 * The resolution of a stream variant.
 *
 * @property width the pixel width, or `0` when the playlist does not declare a resolution.
 * @property height the pixel height, or `0` when the playlist does not declare a resolution.
 * @property label the human readable label, by convention `<height>p`.
 */
public data class VideoQuality(
    public val width: Int,
    public val height: Int,
    public val label: String = if (height > 0) "${height}p" else "auto",
) : Comparable<VideoQuality> {
    init {
        require(width >= 0) { "width must not be negative" }
        require(height >= 0) { "height must not be negative" }
    }

    /** `true` when the resolution is known. */
    public val isKnown: Boolean get() = height > 0

    /** `true` for HD and above. */
    public val isHd: Boolean get() = height >= 720

    /** `true` for Full HD and above. */
    public val isFullHd: Boolean get() = height >= 1080

    override fun compareTo(other: VideoQuality): Int = height.compareTo(other.height)

    override fun toString(): String = label
}

/**
 * A single playable rendition of an episode.
 *
 * @property url the absolute URL of the variant playlist.
 */
public data class StreamVariant(
    public val quality: VideoQuality,
    public val url: String,
    public val bandwidth: Long? = null,
    public val codecs: String? = null,
    public val frameRate: Double? = null,
) {
    override fun toString(): String = "${quality.label} ($url)"
}
