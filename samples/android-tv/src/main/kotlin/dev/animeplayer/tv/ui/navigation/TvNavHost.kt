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

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.MaterialTheme
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvNavEntry
import dev.animeplayer.tv.ui.components.TvStartupScreen
import dev.animeplayer.tv.ui.components.TvTopBar
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.home.HomeScreensaver
import dev.animeplayer.tv.ui.home.HomeScreensaverState
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.theme.LocalTvAnimationsEnabled
import dev.animeplayer.tv.ui.theme.TvDimens
import kotlinx.coroutines.delay

/**
 * The entries of the navigation bar.
 */
public val NAV_ENTRIES: List<TvNavEntry> = listOf(
    TvNavEntry(Destinations.Home, "Home", Icons.Filled.Home),
    TvNavEntry(Destinations.Search, "Search", Icons.Filled.Search),
    TvNavEntry(Destinations.Airing, "Airing", Icons.Filled.DateRange),
    TvNavEntry(Destinations.Library, "Library", Icons.AutoMirrored.Filled.List),
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
internal fun TvApp(
    container: AppContainer,
    screensaver: HomeScreensaverState,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val memory = remember { FocusMemory() }
    val entry = remember { FocusEntry() }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Destinations.Home
    var homeHeaderAlpha by remember { mutableFloatStateOf(0f) }
    var featuredArtwork by remember { mutableStateOf(emptyList<CatalogCard>()) }
    var startupComplete by rememberSaveable { mutableStateOf(false) }
    var startupFocusPending by remember { mutableStateOf(false) }
    val showStartup = !startupComplete && route == Destinations.Home
    val startupFocus = remember { FocusRequester() }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val canShowScreensaver = !showStartup && route == Destinations.Home && featuredArtwork.size >= 2 &&
        lifecycleState == Lifecycle.State.RESUMED
    val showScreensaver = canShowScreensaver && screensaver.visible
    val showBar = route != Destinations.Watch && route != Destinations.Detail
    val showHeaderInset = showBar && route != Destinations.Home
    val animateNavigation = LocalTvAnimationsEnabled.current

    LaunchedEffect(showStartup) {
        if (showStartup) {
            startupFocusPending = true
            startupFocus.requestTvFocus()
            delay(7_000)
            startupComplete = true
        } else if (startupFocusPending) {
            startupFocusPending = false
            withFrameNanos { }
            if (route == Destinations.Home) entry.focus()
        }
    }

    LaunchedEffect(canShowScreensaver, screensaver) {
        if (canShowScreensaver) screensaver.watchForInactivity()
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val navModifier = if (showScreensaver || showStartup) {
            Modifier.clearAndSetSemantics { hideFromAccessibility() }
        } else {
            Modifier
        }
        NavHost(
            navController = navController,
            startDestination = Destinations.Home,
            enterTransition = { if (animateNavigation) fadeIn(tween(150)) else EnterTransition.None },
            exitTransition = { if (animateNavigation) fadeOut(tween(100)) else ExitTransition.None },
            popEnterTransition = { if (animateNavigation) fadeIn(tween(120)) else EnterTransition.None },
            popExitTransition = { if (animateNavigation) fadeOut(tween(90)) else ExitTransition.None },
            modifier = navModifier
                .fillMaxSize()
                .padding(
                    top = if (showHeaderInset) {
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
                    onResume = { item ->
                        navController.navigate(Destinations.watch(item.card.id, item.episodeNumber, item.seriesSlug))
                    },
                    onHeaderBackgroundChange = { homeHeaderAlpha = it },
                    entry = entry,
                    onFeaturedArtworkChange = { featuredArtwork = it },
                    preparingStartup = showStartup,
                    onStartupReady = { startupComplete = true },
                )
            }
            composable(Destinations.Search) { searchEntry ->
                val requestedQuery by searchEntry.savedStateHandle
                    .getStateFlow<String?>(SCHEDULE_QUERY, null).collectAsStateWithLifecycle()
                dev.animeplayer.tv.ui.search.SearchScreen(
                    container = container,
                    memory = memory,
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
                    entry = entry,
                    requestedQuery = requestedQuery,
                    onQueryAccepted = { searchEntry.savedStateHandle[SCHEDULE_QUERY] = null },
                )
            }
            composable(Destinations.Airing) {
                dev.animeplayer.tv.ui.airing.AiringScreen(
                    container = container,
                    entry = entry,
                    onOpenSearch = { title ->
                        memory.forget(dev.animeplayer.tv.ui.focus.FocusKeys.Search)
                        navController.navigate(Destinations.Search)
                        navController.currentBackStackEntry?.savedStateHandle?.set(SCHEDULE_QUERY, title)
                    },
                )
            }
            composable(Destinations.Library) {
                dev.animeplayer.tv.ui.library.LibraryScreen(
                    container = container,
                    memory = memory,
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
                    onResume = { item ->
                        navController.navigate(Destinations.watch(item.card.id, item.episodeNumber, item.seriesSlug))
                    },
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
                    entry = entry,
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
                    onBack = { navController.popBackStack() },
                    onPlay = { card, episode, slug ->
                        navController.navigate(Destinations.watch(card.id, episode, slug))
                    },
                    onSelectSeason = { season ->
                        navController.navigate(Destinations.detail(season.id)) {
                            popUpTo(backStackEntry.destination.id) { inclusive = true }
                        }
                    },
                    onOpen = { card -> navController.navigate(Destinations.detail(card.id)) },
                )
            }
            composable(
                route = Destinations.Watch,
                arguments = listOf(
                    navArgument("catalogId") { type = NavType.StringType },
                    navArgument("episode") { type = NavType.StringType },
                    navArgument("slug") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val catalogId = backStackEntry.arguments?.getString("catalogId").orEmpty()
                val episode = backStackEntry.arguments?.getString("episode").orEmpty()
                dev.animeplayer.tv.ui.player.PlayerScreen(
                    container = container,
                    catalogId = catalogId,
                    episode = episode,
                    seriesSlug = backStackEntry.arguments?.getString("slug"),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (showBar && !showStartup) {
            TvTopBar(
                entries = NAV_ENTRIES,
                current = route,
                modifier = navModifier.align(Alignment.TopCenter),
                onSelect = { target ->
                    if (target != route) navController.navigate(target) {
                        popUpTo(Destinations.Home) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSettings = {
                    navController.navigate(Destinations.Settings) { launchSingleTop = true }
                },
                settingsSelected = route == Destinations.Settings,
                onDown = { entry.focus() },
                backgroundAlpha = if (route == Destinations.Home) homeHeaderAlpha else 1f,
            )
        }

        if (showStartup) {
            TvStartupScreen(
                modifier = Modifier.focusRequester(startupFocus)
                    .onPreviewKeyEvent { it.key != Key.Back }
                    .focusable(),
            )
        }

        if (showScreensaver) {
            androidx.activity.compose.BackHandler(onBack = screensaver::dismiss)
            HomeScreensaver(
                featured = featuredArtwork,
                controls = screensaver,
                onDismiss = screensaver::dismiss,
                onOpen = { card ->
                    screensaver.dismiss()
                    navController.navigate(Destinations.detail(card.id))
                },
            )
        }
    }
}

private const val SCHEDULE_QUERY = "schedule-search-query"
