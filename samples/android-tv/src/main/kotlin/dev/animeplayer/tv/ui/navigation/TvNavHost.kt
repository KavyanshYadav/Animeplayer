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

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvNavEntry
import dev.animeplayer.tv.ui.components.TvStartupScreen
import dev.animeplayer.tv.ui.components.TvTopBar
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.theme.TvDimens
import kotlinx.coroutines.delay

/**
 * The entries of the navigation bar.
 */
public val NAV_ENTRIES: List<TvNavEntry> = listOf(
    TvNavEntry(Destinations.Home, "Home", Icons.Filled.Home),
    TvNavEntry(Destinations.Search, "Search", Icons.Filled.Search),
    TvNavEntry(Destinations.Airing, "Airing", Icons.Filled.DateRange),
    TvNavEntry(Destinations.Library, "Library", Icons.Filled.List),
    TvNavEntry(Destinations.Favourites, "My List", Icons.Filled.Favorite),
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
    var homeHeaderAlpha by remember { mutableFloatStateOf(0f) }
    val startupStartedAt = remember { SystemClock.elapsedRealtime() }
    var startupVisible by remember { mutableStateOf(true) }
    var startupDataReady by remember { mutableStateOf(false) }
    var startupProgress by remember { mutableFloatStateOf(0.06f) }

    // Only the home screen becomes artwork-only when idle. Playback and other screens
    // stay where the viewer left them.
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idle by remember { mutableStateOf(false) }
    val showBar = route != Destinations.Watch && !(route == Destinations.Home && idle)

    LaunchedEffect(route, startupVisible) {
        while (true) {
            delay(1_000)
            idle = !startupVisible && route == Destinations.Home &&
                System.currentTimeMillis() - lastInput > HOME_AMBIENT_TIMEOUT_MS
        }
    }

    LaunchedEffect(startupDataReady) {
        if (!startupDataReady) return@LaunchedEffect
        val minimumVisibleMs = 640L
        val elapsedMs = SystemClock.elapsedRealtime() - startupStartedAt
        val remainingMs = (minimumVisibleMs - elapsedMs).coerceAtLeast(0L)
        if (remainingMs > 0L) delay(remainingMs)
        startupProgress = 1f
        lastInput = System.currentTimeMillis()
        startupVisible = false
    }

    LaunchedEffect(startupDataReady) {
        if (startupDataReady) return@LaunchedEffect
        while (true) {
            delay(80)
            val elapsed = SystemClock.elapsedRealtime() - startupStartedAt
            val waitingProgress = 0.06f + 0.46f * (elapsed / 6_000f).coerceIn(0f, 1f)
            startupProgress = maxOf(startupProgress, waitingProgress)
        }
    }

    LaunchedEffect(startupDataReady) {
        if (startupDataReady) return@LaunchedEffect
        delay(STARTUP_SCREEN_MAX_MS)
        if (!startupDataReady) {
            startupProgress = 1f
            startupDataReady = true
        }
    }

    LaunchedEffect(route) {
        if (route != null && route != Destinations.Home && !startupDataReady) {
            startupProgress = 1f
            startupDataReady = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (startupVisible && event.type == KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown) {
                    lastInput = System.currentTimeMillis()
                    if (idle) {
                        idle = false
                        return@onPreviewKeyEvent true
                    }
                }
                false
            },
    ) {
        NavHost(
            navController = navController,
            startDestination = Destinations.Home,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (showBar && route != Destinations.Home) {
                        TvDimens.TopBarHeight
                    } else {
                        0.dp
                    },
                ),
        ) {
            composable(Destinations.Home) {
                dev.animeplayer.tv.ui.home.HomeScreen(
                    container = container,
                    memory = memory,
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
                    onResume = { card, episode ->
                        navController.navigate(Destinations.watch(card.id, episode))
                    },
                    onHeaderBackgroundChange = { homeHeaderAlpha = it },
                    ambientMode = route == Destinations.Home && idle,
                    entry = entry,
                    onStartupProgress = { progress ->
                        if (!startupDataReady) {
                            startupProgress = maxOf(startupProgress, progress.coerceIn(0f, 1f))
                        }
                    },
                    onStartupReady = {
                        if (!startupDataReady) {
                            startupDataReady = true
                            startupProgress = 1f
                        }
                    },
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
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
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

        if (showBar) {
            TvTopBar(
                entries = NAV_ENTRIES,
                current = route,
                modifier = Modifier.align(Alignment.TopCenter),
                onSelect = { target ->
                    navController.navigate(target) {
                        popUpTo(Destinations.Home) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSettings = { navController.navigate(Destinations.Settings) },
                onDown = { entry.focus() },
                backgroundAlpha = if (route == Destinations.Home) homeHeaderAlpha else 1f,
            )
        }

        AnimatedVisibility(
            visible = startupVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(260)),
        ) {
            TvStartupScreen(progress = startupProgress)
        }

    }
}

/**
 * How long the home screen waits before it shows artwork without controls.
 */
private const val HOME_AMBIENT_TIMEOUT_MS = 30_000L

/** Keep a stalled catalogue request from trapping the viewer on the launch screen. */
private const val STARTUP_SCREEN_MAX_MS = 8_000L
