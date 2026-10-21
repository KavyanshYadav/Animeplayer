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

/**
 * The focus graph of the player.
 *
 * The spatial focus of Compose is good inside a row and unreliable between the areas of a
 * screen. The player has four areas — the top row, the transport, the scrubber and the
 * menu row — and the user has to be able to walk through them in a fixed order with the
 * D-pad and nothing else.
 *
 * So the graph is declared, not guessed. Every area says where "up" and "down" go, and
 * the edges of a row are closed with [FocusRequester.Cancel] so the focus never wraps
 * around.
 *
 * The same idea is used by the reference implementations: the focus is a contract between
 * the parts of a screen, not a side effect of where they happen to be drawn.
 */
@Stable
public class DirectionalFocus {

    /** The back button of the top row. */
    public val top: FocusRequester = FocusRequester()

    /** The play and pause button of the transport. */
    public val transport: FocusRequester = FocusRequester()

    /** The scrubber. */
    public val scrubber: FocusRequester = FocusRequester()

    /** The menu row. */
    public val menu: FocusRequester = FocusRequester()

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
        runCatching { requester.requestFocus() }.isSuccess
}
