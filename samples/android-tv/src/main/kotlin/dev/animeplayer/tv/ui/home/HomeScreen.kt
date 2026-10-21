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
package dev.animeplayer.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.ui.components.TvContinueWatchingRail
import dev.animeplayer.tv.ui.components.TvHero
import dev.animeplayer.tv.ui.components.TvRailKind
import dev.animeplayer.tv.ui.components.TvRailSection
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The home screen.
 *
 * A full-screen feature leads into portrait poster rails. D-pad movement reveals the
 * catalogue only when the viewer chooses to move down.
 *
 * @param container the container of the dependencies.
 * @param memory the memory of the focus.
 * @param onOpen called with the series the user picked.
 * @param onResume called with the series and episode the user wants to resume.
 * @param onHeaderBackgroundChange reports how much backdrop the floating header needs.
 * @param ambientMode whether the home screen is resting on full-screen artwork.
 * @param onStartupProgress reports progress while the first home artwork is prepared.
 * @param onStartupReady called when the first home content and artwork are ready.
 */
@Composable
public fun HomeScreen(
    container: AppContainer,
    memory: FocusMemory,
    onOpen: (CatalogCard) -> Unit,
    onResume: (CatalogCard, String) -> Unit,
    onHeaderBackgroundChange: (Float) -> Unit,
    ambientMode: Boolean = false,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
    onStartupProgress: (Float) -> Unit = {},
    onStartupReady: () -> Unit = {},
) {
    val model: HomeViewModel = viewModel<HomeViewModel>(
        factory = HomeViewModel.factory(container),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val latestState by rememberUpdatedState(state)
    val layout = LocalTvLayout.current
    val continueItems = state.continueWatching.valueOrNull().orEmpty()
    val trendingState = state.rail("Trending Now")
    val hasContinueItems = continueItems.isNotEmpty()
    val myList = state.myList.valueOrNull().orEmpty()
    val recommendations = state.recommendations.valueOrNull().orEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val topBarOffsetPx = with(density) { TvDimens.TopBarHeight.roundToPx() }
    val headerFadeThresholdPx = with(density) { 12.dp.roundToPx() }
    val firstRailFocusRequester = remember { FocusRequester() }
    var pendingRailFocus by remember { mutableStateOf(false) }
    var railScrollComplete by remember { mutableStateOf(false) }
    val firstRail = if (hasContinueItems) null else state.rails.firstOrNull()
    val firstRailReady = hasContinueItems || (
        firstRail != null &&
            (state.rail(firstRail.title) as? TvState.Ready)?.value?.isNotEmpty() == true
        )

    LaunchedEffect(Unit) { model.load() }

    LaunchedEffect(state.hero, trendingState) {
        when (val hero = state.hero) {
            TvState.Loading -> onStartupProgress(0.18f)
            is TvState.Failed -> {
                onStartupProgress(0.96f)
                onStartupReady()
            }

            is TvState.Ready -> {
                val viewport = context.resources.displayMetrics
                val bannerWidthPx = viewport.widthPixels.coerceIn(1, 1_920)
                val bannerHeightPx = viewport.heightPixels.coerceIn(1, 1_080)
                val posterWidthPx = with(density) { layout.cardWidth.roundToPx() }
                    .coerceIn(1, 512)
                val posterHeightPx = with(density) {
                    (layout.cardWidth * TvDimens.CardArtRatio).roundToPx()
                }.coerceIn(1, 768)
                val artwork = buildList {
                    hero.value.take(2).mapNotNull { it.bannerUrl ?: it.posterUrl }.forEach { url ->
                        add(StartupArtwork(url, bannerWidthPx, bannerHeightPx))
                    }
                    trendingState.valueOrNull().orEmpty().take(6).mapNotNull { it.posterUrl }
                        .forEach { url -> add(StartupArtwork(url, posterWidthPx, posterHeightPx)) }
                }.distinctBy { it.url }

                if (artwork.isEmpty()) {
                    onStartupProgress(0.98f)
                    onStartupReady()
                    return@LaunchedEffect
                }

                onStartupProgress(0.58f)
                var completed = 0
                val preloadGate = Semaphore(permits = 2)
                withTimeoutOrNull(STARTUP_ARTWORK_TIMEOUT_MS) {
                    coroutineScope {
                        artwork.map { item ->
                            async {
                                try {
                                    preloadGate.withPermit {
                                        imageLoader.execute(
                                            ImageRequest.Builder(context)
                                                .data(item.url)
                                                .size(item.widthPx, item.heightPx)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .build(),
                                        )
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    // A failed image should not hold the home screen behind the splash.
                                }
                                completed++
                                onStartupProgress(
                                    0.58f + 0.38f * completed / artwork.size,
                                )
                            }
                        }.awaitAll()
                    }
                }
                onStartupProgress(0.98f)
                onStartupReady()
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            if (listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > headerFadeThresholdPx
            ) {
                0.94f
            } else {
                0f
            }
        }.distinctUntilChanged().collect(onHeaderBackgroundChange)
    }

    LaunchedEffect(pendingRailFocus, railScrollComplete, hasContinueItems, firstRailReady) {
        if (!pendingRailFocus || !railScrollComplete || !firstRailReady) return@LaunchedEffect
        repeat(6) {
            withFrameNanos { }
            if (runCatching { firstRailFocusRequester.requestFocus() }.getOrDefault(false)) {
                pendingRailFocus = false
                return@LaunchedEffect
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && latestState.hero !is TvState.Loading) {
                model.refreshContinueWatching()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The floating navigation hands focus to the hero's primary action.
    val playRequester = remember { FocusRequester() }
    entry.Register(playRequester)

    LaunchedEffect(ambientMode) {
        if (!ambientMode) return@LaunchedEffect
        listState.scrollToItem(index = 0)
        repeat(6) {
            withFrameNanos { }
            if (runCatching { playRequester.requestFocus() }.getOrDefault(false)) {
                return@LaunchedEffect
            }
        }
    }

    val navigateToFirstRail = {
        val firstItemIndex = if (hasContinueItems) 1 else 2
        if (hasContinueItems || firstRail != null) {
            pendingRailFocus = true
            railScrollComplete = false
            scope.launch {
                listState.animateScrollToItem(
                    index = firstItemIndex,
                    scrollOffset = -topBarOffsetPx,
                )
                railScrollComplete = true
            }
        }
    }

    val navigateToHero: () -> Unit = {
        scope.launch {
            runCatching { playRequester.requestFocus() }
            withFrameNanos { }
            if (listState.firstVisibleItemIndex == 0) {
                listState.animateScrollBy(-listState.firstVisibleItemScrollOffset.toFloat())
            } else {
                listState.animateScrollToItem(index = 0)
            }
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.scrollToItem(index = 0)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(bottom = 48.dp),
        userScrollEnabled = !ambientMode,
    ) {
        item(key = "hero") {
            TvHero(
                featured = (state.hero as? TvState.Ready)?.value?.map { it.toCard() }.orEmpty(),
                height = layout.height,
                modifier = Modifier.fillMaxWidth(),
                onPlay = { onOpen(it) },
                onFavourite = { model.toggleFavourite(it) },
                isFavourite = { it.id in state.favouriteIds },
                onNavigateDown = navigateToFirstRail,
                playRequester = playRequester,
                ambientMode = ambientMode,
            )
        }

        item(key = "continue-watching") {
            TvContinueWatchingRail(
                items = continueItems,
                memory = memory,
                onResume = { onResume(it.card, it.episodeNumber) },
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                cardWidth = layout.cardWidth,
                entryFocusRequester = if (hasContinueItems) firstRailFocusRequester else null,
                onNavigateUp = if (hasContinueItems) navigateToHero else null,
            )
        }

        items(state.rails, key = { it.title }) { rail ->
            val railState = state.rail(rail.title)
            val isEntryRail = !hasContinueItems && rail == firstRail
            val heroIds = (state.hero as? TvState.Ready)?.value.orEmpty().map { it.id }.toSet()
            val cards = (railState as? TvState.Ready)?.value.orEmpty()
                .filterNot { rail.title == "Trending Now" && it.id in heroIds }
                .map { it.toCard() }
            TvRailSection(
                title = rail.title,
                cards = cards,
                isLoading = railState.isLoading,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .padding(top = TvDimens.RailSpacing),
                onOpen = onOpen,
                screen = "${FocusKeys.Home}:${rail.title}",
                memory = memory,
                cardWidth = layout.cardWidth,
                entryFocusRequester = if (isEntryRail) firstRailFocusRequester else null,
                onNavigateUp = if (isEntryRail) navigateToHero else null,
                kind = if (rail.kind == HomeRail.Kind.Top10) {
                    TvRailKind.Top10
                } else {
                    TvRailKind.Posters
                },
            )
        }

        item(key = "my-list") {
            if (myList.isNotEmpty()) {
                TvRailSection(
                    title = "My List",
                    cards = myList.map { it.toCard() },
                    onOpen = onOpen,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(top = TvDimens.RailSpacing),
                    screen = "${FocusKeys.Home}:My List",
                    memory = memory,
                    cardWidth = layout.cardWidth,
                )
            }
        }

        item(key = "because-you-watched") {
            if (recommendations.isNotEmpty()) {
                TvRailSection(
                    title = "Because You Watched",
                    cards = recommendations.map { it.toCard() },
                    onOpen = onOpen,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(top = TvDimens.RailSpacing),
                    screen = "${FocusKeys.Home}:Because You Watched",
                    memory = memory,
                    cardWidth = layout.cardWidth,
                )
            }
        }
    }
}

/** Maximum time the startup screen spends warming remote artwork. */
private const val STARTUP_ARTWORK_TIMEOUT_MS = 2_400L

private data class StartupArtwork(
    val url: String,
    val widthPx: Int,
    val heightPx: Int,
)
