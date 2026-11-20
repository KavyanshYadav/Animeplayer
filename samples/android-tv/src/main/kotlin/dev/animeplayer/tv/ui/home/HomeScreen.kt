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

import android.os.SystemClock
import dev.animeplayer.tv.data.groupSeasons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.HomeCardSize
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.components.TvContinueWatchingRail
import dev.animeplayer.tv.ui.components.TvHero
import dev.animeplayer.tv.ui.components.TvRailKind
import dev.animeplayer.tv.ui.components.TvRailSection
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.focus.TvNearestEdgeScroll
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.ContinueWatchingItem
import dev.animeplayer.tv.ui.model.toCard
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.core.model.CatalogMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun HomeScreen(
    container: AppContainer,
    memory: FocusMemory,
    onOpen: (CatalogCard) -> Unit,
    onResume: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
    onFeaturedArtworkChange: (List<CatalogCard>) -> Unit = {},
    preparingStartup: Boolean = false,
    onStartupReady: () -> Unit = {},
    onCarouselFocusChange: (Boolean) -> Unit = {},
    onRequestTopBar: () -> Unit = {},
    topBarRequest: Int = 0,
    cardSize: HomeCardSize = HomeCardSize.Large,
    allowCarouselRefresh: Boolean = true,
) {
    val model: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    val layout = LocalTvLayout.current
    val listState = rememberLazyListState()
    val heroRequester = remember { FocusRequester() }
    val firstRailRequester = remember { FocusRequester() }
    val trendingRequester = remember { FocusRequester() }
    val focusWork = rememberCoroutineScope()
    val context = LocalContext.current
    val logoWidth = with(LocalDensity.current) { 352.dp.roundToPx() }.coerceAtMost(704)
    val logoHeight = with(LocalDensity.current) { 104.dp.roundToPx() }.coerceAtMost(208)
    var carouselFocused by remember { mutableStateOf(false) }
    val featured = state.featuredSlides.orEmpty()
    val featuredLoading = container.tmdb != null && state.featuredSlides == null
    val canRefreshOnHome by rememberUpdatedState(allowCarouselRefresh)
    val candidates = state.hero
    LaunchedEffect(candidates, logoWidth, logoHeight) {
        val tmdb = container.tmdb ?: return@LaunchedEffect
        if (candidates.isLoading) return@LaunchedEffect

        if (model.state.value.featuredSlides == null) {
            model.publishCarousel(prepareHomeCarousel(candidates.valueOrNull().orEmpty(), tmdb, context, logoWidth, logoHeight))
        }

        fun canRefresh() = canRefreshOnHome && !carouselFocused
        val interval = 5.minutes.inWholeMilliseconds
        var nextRefresh = model.carouselUpdatedAt + interval
        while (true) {
            delay((nextRefresh - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            snapshotFlow { canRefresh() }.first { it }
            val current = model.state.value.featuredSlides.orEmpty()
            val currentIds = current.mapTo(HashSet()) { it.id }
            val next = model.refreshFeaturedCandidates().sortedBy { it.id in currentIds }
            val ready = prepareHomeCarousel(next, tmdb, context, logoWidth, logoHeight)
            snapshotFlow { canRefresh() }.first { it }
            model.publishCarousel(ready.ifEmpty { current })
            nextRefresh = model.carouselUpdatedAt + interval
        }
    }
    val continueItems = state.continueWatching.valueOrNull().orEmpty()
    val returnShelf = remember { memory.recall(FocusKeys.Home) }
    val firstRail = state.rails.firstOrNull()
    val pairFirstRow = cardSize != HomeCardSize.ExtraLarge && firstRail != null &&
        continueItems.size == 1 && layout.width >= 620.dp && layout.height < 450.dp
    val homeCardWidth = maxOf(layout.cardWidth, (layout.contentWidth - TvDimens.RailGap * 3) / cardSize.cardsAcross)
    val resumeCardWidth = if (pairFirstRow) maxOf(192.dp, homeCardWidth) else homeCardWidth
    var settledBackdrop by remember { mutableStateOf<String?>(null) }
    var settledLeadCards by remember { mutableStateOf(emptySet<String>()) }
    val leadIds = if (continueItems.isNotEmpty()) {
        continueItems.take(2).map { it.seriesSlug }
    } else {
        state.rail(firstRail?.title.orEmpty()).valueOrNull().orEmpty().take(2).map { it.id }
    }
    val onLeadArtSettled: ((String) -> Unit)? = if (preparingStartup) {
        { id -> if (id !in settledLeadCards) settledLeadCards = settledLeadCards + id }
    } else {
        null
    }

    if (preparingStartup) {
        val firstFeature = featured.firstOrNull()
        val firstBackdrop = firstFeature?.bannerUrl ?: firstFeature?.posterUrl
        val featureReady = !featuredLoading && (firstBackdrop == null || firstBackdrop == settledBackdrop)
        val leadReady = state.continueArtworkReady && leadIds.all { it in settledLeadCards }
        LaunchedEffect(featureReady, leadReady) {
            if (featureReady && leadReady) onStartupReady()
        }
    }

    fun openFromRail(title: String, card: CatalogCard) {
        memory.remember(FocusKeys.Home, title)
        onOpen(card)
    }

    fun resume(item: ContinueWatchingItem) {
        memory.remember(FocusKeys.Home, ContinueWatchingKey)
        onResume(item)
    }
    entry.Register(heroRequester)

    LaunchedEffect(model) { model.load() }
    LaunchedEffect(featured) {
        onFeaturedArtworkChange(featured.filter { card -> card.bannerUrl != null || card.posterUrl != null }
            .distinctBy { card -> card.bannerUrl ?: card.posterUrl })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshContinueWatching() }
    var handledTopBarRequest by remember { mutableIntStateOf(topBarRequest) }
    LaunchedEffect(topBarRequest) {
        if (topBarRequest != handledTopBarRequest) {
            listState.scrollToItem(0)
            handledTopBarRequest = topBarRequest
        }
    }

    val moveToShelf = {
        firstRailRequester.requestTvFocus()
    }
    val moveToHero: () -> Unit = {
        focusWork.launch {
            listState.scrollToItem(0)
            withFrameNanos { }
            heroRequester.requestTvFocus()
        }
    }

    val shelfScroll = LocalBringIntoViewSpec.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides TvNearestEdgeScroll) {
        LazyColumn(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            state = listState,
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "feature", contentType = "feature") {
                TvHero(
                    featured = featured,
                    height = if (featured.isEmpty() && !featuredLoading) 265.dp else layout.height.coerceAtLeast(221.dp),
                    failed = featured.isEmpty() && !featuredLoading,
                    canRetry = container.tmdb != null,
                    onOpen = {
                        memory.forget(FocusKeys.Home)
                        onOpen(it)
                    },
                    onRetry = { model.load(force = true) },
                    onNavigateDown = moveToShelf,
                    entryRequester = heroRequester,
                    restoreFocus = returnShelf == null && memory.recall("home-feature") == "opened",
                    onFeatureFocused = { memory.forget("home-feature") },
                    onFeatureOpened = { memory.remember("home-feature", "opened") },
                    onCarouselFocusChange = {
                        carouselFocused = it
                        onCarouselFocusChange(it)
                    },
                    onNavigateUp = onRequestTopBar,
                    onBackdropSettled = if (preparingStartup) { url -> settledBackdrop = url } else null,
                ) {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides shelfScroll) {
                        when {
                            pairFirstRow -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = TvDimens.ScreenMargin, end = TvDimens.ScreenMargin, bottom = TvDimens.RailSpacing),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                ) {
                                    TvContinueWatchingRail(
                                        items = continueItems,
                                        memory = memory,
                                        onResume = ::resume,
                                        modifier = Modifier.width(resumeCardWidth),
                                        cardWidth = resumeCardWidth,
                                        entryFocusRequester = firstRailRequester,
                                        onNavigateUp = moveToHero,
                                        restoreFocus = returnShelf == ContinueWatchingKey,
                                        horizontalPadding = 0.dp,
                                        cardHeight = maxOf(56.dp, homeCardWidth * (9f / 16f)),
                                        onArtworkSettled = onLeadArtSettled,
                                    )
                                    HomeCatalogShelf(
                                        rail = firstRail,
                                        model = model,
                                        railState = state.rail(firstRail.title),
                                        featuredLoading = state.hero is TvState.Loading,
                                        cardWidth = homeCardWidth,
                                        modifier = Modifier.weight(1f),
                                        memory = memory,
                                        onOpen = { openFromRail(firstRail.title, it) },
                                        onNavigateUp = moveToHero,
                                        restoreFocus = returnShelf == firstRail.title,
                                        horizontalPadding = 0.dp,
                                    )
                                }
                            }

                            continueItems.isNotEmpty() -> TvContinueWatchingRail(
                                items = continueItems,
                                memory = memory,
                                onResume = ::resume,
                                modifier = Modifier.padding(horizontal = HomeRowGutter)
                                    .padding(bottom = TvDimens.RailSpacing),
                                horizontalPadding = HomeRowPadding,
                                cardWidth = resumeCardWidth,
                                entryFocusRequester = firstRailRequester,
                                onNavigateUp = moveToHero,
                                onNavigateDown = { trendingRequester.requestTvFocus() },
                                restoreFocus = returnShelf == ContinueWatchingKey,
                                onArtworkSettled = onLeadArtSettled,
                            )

                            firstRail != null -> HomeCatalogShelf(
                                rail = firstRail,
                                model = model,
                                railState = state.rail(firstRail.title),
                                featuredLoading = state.hero is TvState.Loading,
                                cardWidth = homeCardWidth,
                                modifier = Modifier.padding(bottom = TvDimens.RailSpacing),
                                memory = memory,
                                onOpen = { openFromRail(firstRail.title, it) },
                                entryFocusRequester = firstRailRequester,
                                onNavigateUp = moveToHero,
                                restoreFocus = returnShelf == firstRail.title,
                                onArtworkSettled = onLeadArtSettled,
                            )
                        }
                    }
                }
            }

            val rails = if (pairFirstRow || continueItems.isEmpty()) state.rails.drop(1) else state.rails
            items(rails, key = { it.title }, contentType = { "shelf" }) { rail ->
                CompositionLocalProvider(LocalBringIntoViewSpec provides shelfScroll) {
                    HomeCatalogShelf(
                        rail = rail,
                        model = model,
                        railState = state.rail(rail.title),
                        featuredLoading = state.hero is TvState.Loading,
                        cardWidth = homeCardWidth,
                        modifier = Modifier.padding(bottom = TvDimens.RailSpacing),
                        memory = memory,
                        onOpen = { openFromRail(rail.title, it) },
                        entryFocusRequester = trendingRequester.takeIf { rail == firstRail },
                        restoreFocus = returnShelf == rail.title,
                    )
                }
            }

            val personalShelves = listOf(
                "My List" to state.myList,
                "Because You Watched" to state.recommendations,
            )
            items(personalShelves, key = { it.first }, contentType = { "shelf" }) { (title, shelf) ->
                val cards = remember(shelf) {
                    shelf.valueOrNull().orEmpty().groupSeasons().map { it.toCard() }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides shelfScroll) {
                    if (cards.isNotEmpty()) {
                        TvRailSection(
                            title = title,
                            cards = cards,
                            onOpen = { openFromRail(title, it) },
                            modifier = Modifier.padding(start = HomeRowGutter, end = HomeRowGutter, bottom = TvDimens.RailSpacing),
                            horizontalPadding = HomeRowPadding,
                            screen = "${FocusKeys.Home}:$title",
                            memory = memory,
                            cardWidth = homeCardWidth,
                            landscape = true,
                            restoreFocus = returnShelf == title,
                        )
                    }
                }
            }
        }
    }
}

private val HomeRowGutter = 12.dp
private val HomeRowPadding = TvDimens.ScreenMargin - HomeRowGutter

private const val ContinueWatchingKey = "continue-watching"

@Composable
private fun HomeCatalogShelf(
    rail: HomeRail,
    model: HomeViewModel,
    railState: TvState<List<CatalogMedia>>,
    featuredLoading: Boolean,
    cardWidth: Dp,
    modifier: Modifier,
    memory: FocusMemory,
    onOpen: (CatalogCard) -> Unit,
    restoreFocus: Boolean,
    onNavigateUp: (() -> Unit)? = null,
    entryFocusRequester: FocusRequester? = null,
    horizontalPadding: Dp = TvDimens.ScreenMargin,
    onArtworkSettled: ((String) -> Unit)? = null,
) {
    LaunchedEffect(rail, featuredLoading) {
        if (!featuredLoading) model.loadRail(rail)
    }
    if (railState is TvState.Failed) return
    val cards = remember(railState) {
        railState.valueOrNull().orEmpty().groupSeasons().map { it.toCard() }
    }
    if (!railState.isLoading && cards.isEmpty()) return
    TvRailSection(
        title = rail.title,
        cards = cards,
        isLoading = railState.isLoading,
        modifier = if (horizontalPadding > 0.dp) modifier.padding(horizontal = HomeRowGutter) else modifier,
        onOpen = onOpen,
        screen = "${FocusKeys.Home}:${rail.title}",
        memory = memory,
        cardWidth = cardWidth,
        landscape = true,
        entryFocusRequester = entryFocusRequester,
        onNavigateUp = onNavigateUp,
        restoreFocus = restoreFocus,
        kind = if (rail.kind == HomeRail.Kind.Top10) TvRailKind.Top10 else TvRailKind.Posters,
        horizontalPadding = if (horizontalPadding > 0.dp) horizontalPadding - HomeRowGutter else 0.dp,
        onArtworkSettled = onArtworkSettled,
    )
}
