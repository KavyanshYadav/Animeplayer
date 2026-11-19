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
package dev.animeplayer.tv.ui.search

import dev.animeplayer.tv.ui.model.withTmdbArtwork
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.components.TvChip
import dev.animeplayer.tv.ui.components.TvChipRow
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvEmptyState
import dev.animeplayer.tv.ui.components.TvPageHeading
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.components.TvTextField
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.theme.LocalTvLayout

/**
 * The catalogue.
 *
 * The search of the desktop client is a form of seven drop-downs. On a television the
 * form becomes a field with the on-screen keyboard and a row of chips; every chip opens
 * a full screen list that is scrolled with the D-pad.
 *
 * @param container the container of the dependencies.
 * @param memory the memory of the focus.
 * @param onOpen called with the series the user picked.
 */
@Composable
public fun SearchScreen(
    container: AppContainer,
    memory: FocusMemory,
    onOpen: (CatalogCard) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
    requestedQuery: String? = null,
    onQueryAccepted: () -> Unit = {},
) {
    val model: SearchViewModel = viewModel<SearchViewModel>(factory = SearchViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<FilterSheet?>(null) }
    val fieldRequester = remember { FocusRequester() }
    val resultRequester = remember { FocusRequester() }
    var restored by remember { mutableStateOf(false) }
    val restoreId = remember { memory.recall(FocusKeys.Search) }
    val layout = LocalTvLayout.current
    val grid = rememberLazyGridState()
    val resultsColumns = layout.columns
    entry.Register(fieldRequester)

    LaunchedEffect(requestedQuery) {
        if (requestedQuery != null) {
            model.setQuery(requestedQuery)
            onQueryAccepted()
        } else {
            model.ensureLoaded()
        }
    }
    LaunchedEffect(state.isLoading, state.results.isEmpty(), requestedQuery) {
        if (requestedQuery != null || state.isLoading || restored) return@LaunchedEffect
        restored = true
        val target = state.results.indexOfFirst { it.id == restoreId }
        if (target >= 0) {
            grid.scrollToItem(target + 1)
            withFrameNanos { }
            resultRequester.requestTvFocus()
        } else {
            fieldRequester.requestTvFocus()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = TvDimens.ScreenMargin, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvPageHeading("Search", modifier = Modifier.width(128.dp))
            Spacer(Modifier.weight(1f))
            TvTextField(
                value = state.query,
                onValueChange = { model.setQuery(it) },
                placeholder = "Search anime by title",
                onSearch = model::search,
                onClear = model::clear,
                modifier = Modifier.width(minOf(480.dp, layout.contentWidth * 0.64f)).focusRequester(fieldRequester),
            )
        }
        TvChipRow {
            FilterSheet.entries.forEach { filter ->
                val selected = state.valueOf(filter)?.takeUnless { it == filter.default }
                TvChip(
                    text = filter.title,
                    trailing = selected?.let { value ->
                        filter.options(state).firstOrNull { it.first == value }?.second ?: value
                    },
                    active = selected != null,
                    onClick = { sheet = filter },
                )
            }
        }
        when {
            state.isLoading -> TvLoading(message = "Searching anime…")
            state.error != null -> TvErrorState(state.error!!, onRetry = model::search)
            state.results.isEmpty() -> TvEmptyState(
                message = "No titles found",
                hint = "Try another title or change your filters.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(resultsColumns),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = TvDimens.ScreenMargin,
                    end = TvDimens.ScreenMargin,
                    top = 4.dp,
                    bottom = 46.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "search-heading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (state.query.isBlank() && state.filters.isEmpty()) "Trending titles" else "Results",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                itemsIndexed(state.results, key = { _, card -> card.id }, contentType = { _, _ -> "artwork" }) { index, result ->
                    val card = result.withTmdbArtwork()
                    dev.animeplayer.tv.ui.components.TvCard(
                        title = card.title,
                        artwork = card.bannerUrl ?: card.posterUrl,
                        aspectRatio = 16f / 9f,
                        tint = card.posterColor,
                        metadata = card.infoLine,
                        statusLine = card.statusLine,
                        onClick = {
                            memory.remember(FocusKeys.Search, card.id)
                            onOpen(card)
                        },
                        modifier = Modifier
                            .then(if (card.id == restoreId) Modifier.focusRequester(resultRequester) else Modifier)
                            .onFocusChanged {
                                if (it.isFocused && !state.pagingFailed && index >= state.results.size - PREFETCH_MARGIN) {
                                    model.loadMore()
                                }
                            },
                    )
                }

                if (state.isLoadingMore || state.pagingFailed) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        if (state.pagingFailed) {
                            TvButton("Load more", onClick = model::loadMore, prominent = false)
                        } else {
                            Text("Loading more titles…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    sheet?.let { entry ->
        val options = entry.options(state)
        TvSheet(
            title = entry.title,
            onDismiss = { sheet = null },
            sideAligned = true,
            actions = {
                dev.animeplayer.tv.ui.components.TvButton(
                    text = "Clear",
                    onClick = {
                        model.clearFilter(entry)
                        sheet = null
                    },
                    prominent = false,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(options, key = { it.first }) { (value, label) ->
                    TvListItem(
                        title = label ?: value,
                        selected = state.valueOf(entry) == value,
                        onClick = {
                            model.setFilter(entry, value)
                            sheet = null
                        },
                    )
                }
            }
        }
    }
}

/**
 * The filters of the catalogue.
 */
public enum class FilterSheet(
    val title: String,
    val default: String?,
) {
    Genre("Genres", null),
    Year("Year", null),
    Season("Season", null),
    Format("Format", null),
    Status("Status", null),
    Sort("Sort", "TRENDING_DESC"),
    ;

    /**
     * The options of the filter.
     */
    fun options(state: SearchState): List<Pair<String, String?>> = when (this) {
        Genre -> state.genres.map { it to null }
        Year -> (state.currentYear downTo state.currentYear - 25).map { it.toString() to null }
        Season -> listOf(
            "WINTER" to "Winter",
            "SPRING" to "Spring",
            "SUMMER" to "Summer",
            "FALL" to "Fall",
        )
        Format -> listOf(
            "TV" to "TV",
            "TV_SHORT" to "TV short",
            "MOVIE" to "Movie",
            "SPECIAL" to "Special",
            "OVA" to "OVA",
            "ONA" to "ONA",
            "MUSIC" to "Music",
        )
        Status -> listOf(
            "FINISHED" to "Finished",
            "RELEASING" to "Releasing",
            "NOT_YET_RELEASED" to "Not yet released",
            "CANCELLED" to "Cancelled",
            "HIATUS" to "Hiatus",
        )
        Sort -> listOf(
            "TRENDING_DESC" to "Trending",
            "POPULARITY_DESC" to "Popular",
            "SCORE_DESC" to "Top rated",
            "START_DATE_DESC" to "Newest",
            "FAVOURITES_DESC" to "Favourites",
            "EPISODE_DESC" to "Most episodes",
        )
    }
}

/**
 * How close to the end of the results the next page is fetched.
 */
private const val PREFETCH_MARGIN = 8
