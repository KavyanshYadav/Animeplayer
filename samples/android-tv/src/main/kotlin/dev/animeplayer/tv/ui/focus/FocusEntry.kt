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
package dev.animeplayer.tv.ui.focus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * The way into the content of a screen.
 *
 * The spatial focus of Compose is good inside a row and unreliable between the areas of
 * a screen: from the navigation bar, "down" would pick the closest element below it,
 * which is the first dot of the banner, and not the button the user meant.
 *
 * A television interface therefore states where the focus goes: the navigation bar hands
 * the focus to the first stop of the screen, and the screen hands it back.
 *
 * The screens register their entry point, the navigation bar calls [focus].
 */
@Stable
public class FocusEntry {

    /** The requester of the first stop of the current screen. */
    public var requester: FocusRequester? by mutableStateOf(null)
        private set

    /**
     * Registers the first stop of a screen.
     *
     * The registration is undone when the screen goes away, so a screen that is left
     * behind does not steal the focus of the screen that replaced it.
     */
    @Composable
    public fun Register(entry: FocusRequester) {
        DisposableEffect(entry) {
            requester = entry
            onDispose {
                if (requester === entry) requester = null
            }
        }
    }

    /**
     * Moves the focus to the first stop of the current screen.
     *
     * @return `true` when the focus was moved.
     */
    public fun focus(): Boolean {
        val target = requester ?: return false
        return target.requestTvFocus()
    }
}

/** A lazy item or panel can leave the composition before a pending focus request runs. */
internal fun FocusRequester.requestTvFocus(): Boolean = try {
    requestFocus()
} catch (_: IllegalStateException) {
    false
}

/** Leaves fully visible controls in place and scrolls just enough to reveal clipped ones. */
@OptIn(ExperimentalFoundationApi::class)
internal object TvNearestEdgeScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            offset >= 0f && end <= containerSize -> 0f
            offset < 0f && end > containerSize -> 0f
            offset < 0f -> offset
            else -> end - containerSize
        }
    }
}
