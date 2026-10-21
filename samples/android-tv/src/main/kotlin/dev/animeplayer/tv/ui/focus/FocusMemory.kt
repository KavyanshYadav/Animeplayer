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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The memory of the focus.
 *
 * A television user moves the focus, opens a series, watches an episode and comes back.
 * If the focus jumped to the first card of the first rail every time, the user would lose
 * their place — so the last focused item of every screen is remembered and the focus is
 * restored when the screen is entered again.
 */
@Stable
public class FocusMemory {

    private val remembered = mutableStateOf<Map<String, String>>(emptyMap())

    /** The item that was focused last on [screen]. */
    public fun recall(screen: String): String? = remembered.value[screen]

    /** Remembers the item that is focused on [screen]. */
    public fun remember(screen: String, item: String) {
        if (remembered.value[screen] == item) return
        remembered.value = remembered.value + (screen to item)
    }

    /** Forgets the focus of a screen. */
    public fun forget(screen: String) {
        remembered.value = remembered.value - screen
    }
}

/**
 * The key of a screen, used by the [FocusMemory].
 */
public object FocusKeys {
    public const val Home: String = "home"
    public const val Search: String = "search"
    public const val Airing: String = "airing"
    public const val Library: String = "library"
    public const val Favourites: String = "favourites"
    public const val Detail: String = "detail"
    public const val Player: String = "player"

    /** The key of the rail of a detail screen. */
    public fun detail(rail: String): String = "detail:$rail"
}
