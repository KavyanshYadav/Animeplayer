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
package dev.animeplayer.tv.data

/**
 * The state of something that is loaded from the network.
 *
 * The three states are the ones a television interface needs: a spinner while the data
 * arrives, the data itself, and a message the user can act on.
 */
public sealed interface TvState<out T> {

    /** The data is on its way. */
    public data object Loading : TvState<Nothing>

    /** The data arrived. */
    public data class Ready<T>(val value: T) : TvState<T>

    /** The data could not be loaded. */
    public data class Failed(val message: String) : TvState<Nothing>

    /** The value, or `null` when the state is not [Ready]. */
    public fun valueOrNull(): T? = (this as? Ready)?.value

    /** `true` while the data is on its way. */
    public val isLoading: Boolean get() = this is Loading
}
