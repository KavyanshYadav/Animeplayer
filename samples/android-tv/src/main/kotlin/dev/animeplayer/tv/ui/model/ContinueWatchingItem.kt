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
package dev.animeplayer.tv.ui.model

/** A series and episode that can be resumed from the home screen. */
public data class ContinueWatchingItem(
    val card: CatalogCard,
    val episodeNumber: String,
    val positionMs: Long,
    val durationMs: Long,
) {
    /** Playback progress in the `0.0..1.0` range. */
    public val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
