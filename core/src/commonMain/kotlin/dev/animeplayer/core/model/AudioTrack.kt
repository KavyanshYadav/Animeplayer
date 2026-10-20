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

/** The audio track of an episode. */
public enum class AudioTrack {
    /** Original language with hardcoded subtitles. */
    Sub,

    /** Dubbed audio. */
    Dub,
    ;

    /** The value used by upstream APIs for this track. */
    public val wireValue: String
        get() = when (this) {
            Sub -> "sub"
            Dub -> "dub"
        }
}
