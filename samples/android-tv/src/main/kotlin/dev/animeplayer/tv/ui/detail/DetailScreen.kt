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
package dev.animeplayer.tv.ui.detail

import dev.animeplayer.tv.data.seasonLabel
import dev.animeplayer.tv.ui.model.withTmdbArtwork
import dev.animeplayer.tv.ui.model.tmdbTitleLogo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.components.TvArtwork
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvButtonRow
import dev.animeplayer.tv.ui.components.TvChip
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.components.TvRailSection
import dev.animeplayer.tv.ui.components.TvTitleLogo
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.focus.TvNearestEdgeScroll
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun DetailScreen(
    container: AppContainer,
    memory: FocusMemory,
    catalogId: String,
    onBack: () -> Unit,
    onPlay: (CatalogCard, String, String) -> Unit,
    onOpen: (CatalogCard) -> Unit,
    modifier: Modifier = Modifier,
    onSelectSeason: (CatalogCard) -> Unit = onOpen,
) {
    val model: DetailViewModel = viewModel(factory = DetailViewModel.factory(container, catalogId))
    val state by model.state.collectAsStateWithLifecycle()
    val listEntry by model.listEntry.collectAsStateWithLifecycle()
    var showListEditor by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val slug = state.slug
    val playable = slug != null
    val catalogueAvailable = catalogId.toLongOrNull() != null
    val episodeShelf = "detail:$catalogId:episodes"
    var episodeLaunched by rememberSaveable { mutableIntStateOf(0) }
    var selectedRange by rememberSaveable { mutableIntStateOf(-1) }
    var showRanges by rememberSaveable { mutableStateOf(false) }
    var requestedEpisode by remember { mutableStateOf<String?>(null) }
    val episodeRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    val layout = LocalTvLayout.current

    LaunchedEffect(model) { model.load() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        model.refreshHistory()
        if (episodeLaunched == 2) requestedEpisode = memory.recall(episodeShelf)
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val media = state.media) {
            TvState.Loading -> TvLoading(message = "Loading series…")
            is TvState.Failed -> TvErrorState(
                media.message, onRetry = { model.load(force = true) }, onBack = onBack, autoFocus = true,
            )
            is TvState.Ready -> {
                val card = remember(media.value) { media.value.toCard() }.withTmdbArtwork()
                val episodes = state.episodes.valueOrNull().orEmpty()
                if (showListEditor) AnimeListEditor(
                    id = catalogId,
                    entry = listEntry,
                    episodes = episodes,
                    episodeCount = media.value.episodeCount,
                    onChange = model::editList,
                    onMark = model::markEpisode,
                    onDismiss = { showListEditor = false },
                )

                val resume = state.resumeEpisode
                val primaryEpisode = resume ?: episodes.firstOrNull { !it.isWatched }?.number ?: episodes.firstOrNull()?.number
                val primaryRequester = remember { FocusRequester() }
                val ranges = remember(episodes) { episodes.chunked(EpisodesPerRange) }
                val initialEpisode = remember(episodes.isEmpty()) { memory.recall(episodeShelf) ?: resume }
                val initialRange = episodes.indexOfFirst { it.number == initialEpisode }
                    .coerceAtLeast(0) / EpisodesPerRange
                val range = (if (selectedRange < 0) initialRange else selectedRange)
                    .coerceIn(0, ranges.lastIndex.coerceAtLeast(0))
                val rangeEpisodes = ranges.getOrNull(range).orEmpty()
                val numbers = remember(rangeEpisodes) { rangeEpisodes.map { it.number } }
                val malId = media.value.malId
                var filler by remember(malId, numbers) { mutableStateOf(emptySet<String>()) }
                LaunchedEffect(malId, numbers) {
                    if (malId != null && numbers.isNotEmpty()) filler = container.fillerEpisodes.forEpisodes(malId, numbers)
                }
                val episodesInRange = remember(rangeEpisodes, filler) {
                    rangeEpisodes.map { it.copy(isFiller = it.number in filler) }
                }
                val hasSeasons = state.seasons.isNotEmpty()
                fun openRange(index: Int) {
                    selectedRange = index
                    requestedEpisode = ranges.getOrNull(index)?.firstOrNull()?.number
                }
                fun openEpisodes() {
                    val number = memory.recall(episodeShelf) ?: resume
                    val index = episodes.indexOfFirst { it.number == number }.coerceAtLeast(0)
                    selectedRange = index / EpisodesPerRange
                    requestedEpisode = episodes.getOrNull(index)?.number
                }
                LaunchedEffect(requestedEpisode, episodesInRange, hasSeasons) {
                    val target = requestedEpisode ?: return@LaunchedEffect
                    val row = episodesInRange.indexOfFirst { it.number == target }
                    if (row < 0) return@LaunchedEffect
                    val heading = if (hasSeasons) 2 else 1
                    listState.scrollToItem(if (row == 0) heading else heading + 1 + row)
                    withFrameNanos { }
                    if (episodeRequester.requestTvFocus()) {
                        requestedEpisode = null
                        episodeLaunched = 0
                    }
                }
                LaunchedEffect(playable, primaryEpisode, episodeLaunched) {
                    if (episodeLaunched == 2 || requestedEpisode != null || listState.firstVisibleItemIndex != 0) return@LaunchedEffect
                    withFrameNanos { }
                    if (playable && primaryEpisode != null) {
                        primaryRequester.requestTvFocus()
                    } else {
                        backRequester.requestTvFocus()
                    }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides TvNearestEdgeScroll) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 52.dp),
                        verticalArrangement = Arrangement.spacedBy(TvDimens.RailSpacing),
                    ) {
                        item(key = "series", contentType = "series") {
                            SeriesHeader(
                                card = card,
                                saved = state.isFavourite,
                                canSave = catalogueAvailable,
                                primaryEpisode = primaryEpisode.takeIf { playable },
                                resume = resume != null,
                                backRequester = backRequester,
                                playRequester = primaryRequester,
                                onBack = onBack,
                                onFavourite = { showListEditor = true },
                                onEpisodes = if (episodes.isEmpty()) null else ::openEpisodes,
                                onPlay = {
                                    if (primaryEpisode != null && slug != null) {
                                        episodeLaunched = 1
                                        onPlay(card, primaryEpisode, slug)
                                    }
                                },
                            )
                        }
                        if (state.seasons.isNotEmpty()) {
                            item(key = "seasons") {
                                val selected = state.seasons.indexOfFirst { it.id == catalogId }.coerceAtLeast(0)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Seasons",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                                    )
                                    LazyRow(
                                        state = rememberLazyListState((selected - 3).coerceAtLeast(0)),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
                                    ) {
                                        items(state.seasons.size, key = { state.seasons[it].id }) { index ->
                                            val season = state.seasons[index]
                                            TvChip(
                                                text = state.seasons.seasonLabel(index),
                                                active = season.id == catalogId,
                                                onClick = { if (season.id != catalogId) onSelectSeason(season.toCard()) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        episodeList(
                            state = state.episodes,
                            episodes = episodesInRange,
                            count = episodes.size,
                            playable = playable,
                            resume = resume,
                            requestedEpisode = requestedEpisode,
                            focusRequester = episodeRequester,
                            rangeLabel = if (ranges.size > 1) episodeRangeLabel(episodesInRange) else null,
                            onChooseRange = { showRanges = true },
                            onNextRange = if (range < ranges.lastIndex) {
                                { openRange(range + 1) }
                            } else null,
                            onRetry = { model.load(force = true) },
                            onFocused = { memory.remember(episodeShelf, it) },
                            onPlay = {
                                if (slug != null) {
                                    memory.remember(episodeShelf, it)
                                    episodeLaunched = 2
                                    onPlay(card, it, slug)
                                }
                            },
                        )
                        item(key = "relations", contentType = "shelf") {
                            val relations = remember(state.relations, state.seasons) {
                                val seasons = state.seasons.mapTo(HashSet()) { it.id }
                                state.relations.valueOrNull().orEmpty().distinctBy { it.media.id }
                                    .filterNot { it.media.id in seasons }.map {
                                    it.media.toCard().copy(
                                        status = null,
                                        metadata = listOf(it.relationType.replace('_', ' ').lowercase()
                                            .replaceFirstChar(Char::uppercaseChar)),
                                    )
                                }
                            }
                            if (relations.isNotEmpty()) {
                                TvRailSection("Related series", relations, onOpen, cardWidth = layout.cardWidth, landscape = true)
                            }
                        }
                        item(key = "recommendations", contentType = "shelf") {
                            val recommendations = remember(state.recommendations) {
                                state.recommendations.valueOrNull().orEmpty().distinctBy { it.id }.map { it.toCard() }
                            }
                            if (recommendations.isNotEmpty()) {
                                TvRailSection("More like this", recommendations, onOpen, cardWidth = layout.cardWidth, landscape = true)
                            }
                        }
                    }
                }
                if (showRanges && ranges.size > 1) {
                    EpisodeRangePicker(
                        ranges = ranges,
                        selected = range,
                        onDismiss = { showRanges = false },
                        onSelect = {
                            showRanges = false
                            openRange(it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(
    card: CatalogCard,
    saved: Boolean,
    canSave: Boolean,
    primaryEpisode: String?,
    resume: Boolean,
    backRequester: FocusRequester,
    playRequester: FocusRequester,
    onBack: () -> Unit,
    onFavourite: () -> Unit,
    onEpisodes: (() -> Unit)?,
    onPlay: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val layout = LocalTvLayout.current
    Box(Modifier.fillMaxWidth().heightIn(min = (layout.height * 0.53f).coerceIn(238.dp, 350.dp))) {
        TvArtwork(
            url = card.bannerUrl ?: card.posterUrl,
            tint = scheme.surface,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            alignment = if (card.bannerUrl == null) Alignment.TopEnd else Alignment.Center,
            hero = true,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to scheme.background,
                    0.36f to scheme.background.copy(alpha = 0.93f),
                    0.68f to scheme.background.copy(alpha = 0.57f),
                    1f to scheme.background.copy(alpha = 0.04f),
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.62f to Color.Transparent, 1f to scheme.background),
            ),
        )
        Column(
            modifier = Modifier.fillMaxWidth(0.69f).padding(
                start = TvDimens.ScreenMargin,
                top = 25.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TvButton(
                "Back",
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                prominent = false,
                modifier = Modifier.focusRequester(backRequester),
            )
            val compact = layout.height < 450.dp
            TvTitleLogo(
                url = card.tmdbTitleLogo(),
                title = card.title,
                modifier = Modifier.widthIn(max = if (compact) 246.dp else 316.dp).fillMaxWidth()
                    .height(if (compact) 65.dp else 82.dp),
            ) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 42.sp),
                    color = scheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val info = listOfNotNull(
                card.year?.toString(),
                card.genres.firstOrNull(),
                card.episodeCount?.let { "$it episodes" },
                card.durationMinutes?.let { "$it min" },
                card.score?.let { "$it% rating" },
            ).joinToString("  ·  ")
            if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            card.nextEpisodeLine?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = scheme.tertiary) }
            card.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onBackground.copy(alpha = 0.90f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvButtonRow(modifier = Modifier.padding(top = 5.dp)) {
                if (primaryEpisode != null) {
                    TvButton(
                        text = if (resume) "Resume episode $primaryEpisode" else "Play episode $primaryEpisode",
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.focusRequester(playRequester),
                        onClick = onPlay,
                    )
                }
                if (onEpisodes != null) {
                    TvButton("Episodes", onClick = onEpisodes, prominent = false)
                }
                if (canSave) {
                    TvButton(
                        text = if (saved) "In My List" else "My List",
                        icon = if (saved) Icons.Filled.Check else Icons.Filled.Add,
                        prominent = false,
                        onClick = onFavourite,
                    )
                }
            }
        }
    }
}
