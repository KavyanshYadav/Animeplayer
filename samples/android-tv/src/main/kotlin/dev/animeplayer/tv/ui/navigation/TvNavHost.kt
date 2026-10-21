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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.navigation.NavType
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvNavEntry
import dev.animeplayer.tv.ui.components.TvTopBar
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard

/**
 * The entries of the navigation bar.
 */
public val NAV_ENTRIES: List<TvNavEntry> = listOf(
    TvNavEntry(Destinations.Home, "Home", Icons.Filled.Home),
    TvNavEntry(Destinations.Search, "Search", Icons.Filled.Search),
    TvNavEntry(Destinations.Airing, "Airing", Icons.Filled.DateRange),
    TvNavEntry(Destinations.Library, "Library", Icons.Filled.List),
    TvNavEntry(Destinations.Favourites, "Favourites", Icons.Filled.Favorite),
)

/**
 * The frame of the application.
 *
 * The navigation bar sits above the content, and it disappears on the player — the
 * player is full screen, because a video with a navigation bar on top of it is a video
 * that is smaller than it has to be.
 *
 * @param container the container of the dependencies.
 */
@Composable
public fun TvApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val memory = remember { FocusMemory() }
    val entry = remember { FocusEntry() }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route != Destinations.Watch

    Column(modifier = modifier.fillMaxSize()) {
        if (showBar) {
            TvTopBar(
                entries = NAV_ENTRIES,
                current = route,
                onSelect = { target ->
                    navController.navigate(target) {
                        popUpTo(Destinations.Home) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSettings = { navController.navigate(Destinations.Settings) },
                onDown = { entry.focus() },
            )
        }

        NavHost(
            navController = navController,
            startDestination = Destinations.Home,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Destinations.Home) {
                dev.animeplayer.tv.ui.home.HomeScreen(
                    container = container,
                    memory = memory,
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
                    entry = entry,
                )
            }
            composable(Destinations.Search) {
                dev.animeplayer.tv.ui.search.SearchScreen(
                    container = container,
                    memory = memory,
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
                    entry = entry,
                )
            }
            composable(Destinations.Airing) {
                dev.animeplayer.tv.ui.airing.AiringScreen(
                    container = container,
                    memory = memory,
                    entry = entry,
                )
            }
            composable(Destinations.Library) {
                dev.animeplayer.tv.ui.library.LibraryScreen(
                    container = container,
                    memory = memory,
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
                    entry = entry,
                )
            }
            composable(Destinations.Favourites) {
                dev.animeplayer.tv.ui.favourites.FavouritesScreen(
                    container = container,
                    memory = memory,
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
                    entry = entry,
                )
            }
            composable(Destinations.Settings) {
                dev.animeplayer.tv.ui.settings.SettingsScreen(
                    container = container,
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = Destinations.Detail,
                arguments = listOf(navArgument("catalogId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val catalogId = backStackEntry.arguments?.getString("catalogId").orEmpty()
                dev.animeplayer.tv.ui.detail.DetailScreen(
                    container = container,
                    memory = memory,
                    catalogId = catalogId,
                    entry = entry,
                    onBack = { navController.popBackStack() },
                    onPlay = { card, episode ->
                        navController.navigate(Destinations.watch(card.id, episode))
                    },
                )
            }
            composable(
                route = Destinations.Watch,
                arguments = listOf(
                    navArgument("catalogId") { type = NavType.StringType },
                    navArgument("episode") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val catalogId = backStackEntry.arguments?.getString("catalogId").orEmpty()
                val episode = backStackEntry.arguments?.getString("episode").orEmpty()
                dev.animeplayer.tv.ui.player.PlayerScreen(
                    container = container,
                    catalogId = catalogId,
                    episode = episode,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
