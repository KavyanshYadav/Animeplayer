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
package dev.animeplayer.tv.ui.player.focus

import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusRequester
import dev.animeplayer.tv.ui.focus.requestTvFocus

/** Explicit remote focus links between the player controls and open panels. */
@Stable
public class DirectionalFocus {

    /** The back button of the top row. */
    public val top: FocusRequester = FocusRequester()

    /** The play and pause button of the transport. */
    public val transport: FocusRequester = FocusRequester()

    /** The scrubber. */
    public val scrubber: FocusRequester = FocusRequester()

    /** The first item of the open panel. */
    public val panel: FocusRequester = FocusRequester()

    /** The first style of the open style panel. */
    public val style: FocusRequester = FocusRequester()

    /**
     * Moves the focus to an area of the graph.
     *
     * @return `true` when the focus was moved.
     */
    public fun focusOn(requester: FocusRequester): Boolean =
        requester.requestTvFocus()
}
