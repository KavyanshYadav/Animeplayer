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
package dev.animeplayer.tv.ui.navigation

/**
 * The routes of the application.
 */
public object Destinations {

    /** The home screen. */
    public const val Home: String = "home"

    /** The catalogue. */
    public const val Search: String = "search"

    /** The broadcast schedule. */
    public const val Airing: String = "airing"

    /** The library — what the user watched and what the upstream site knows. */
    public const val Library: String = "library"

    /** The series the user marked. */
    public const val Favourites: String = "favourites"

    /** The settings. */
    public const val Settings: String = "settings"

    /** The details of a series. */
    public const val Detail: String = "detail/{catalogId}"

    /** The player. */
    public const val Watch: String = "watch/{catalogId}/{episode}"

    /** The details of a series, with the given identifier. */
    public fun detail(catalogId: String): String = "detail/$catalogId"

    /** The player, with the given identifiers. */
    public fun watch(catalogId: String, episode: String): String = "watch/$catalogId/$episode"
}
