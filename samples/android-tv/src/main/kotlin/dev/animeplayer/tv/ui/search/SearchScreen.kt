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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.animeplayer.tv.ui.components.TvChip
import dev.animeplayer.tv.ui.components.TvChipRow
import dev.animeplayer.tv.ui.components.TvEmptyState
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
import kotlinx.coroutines.delay

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
) {
    val model: SearchViewModel = viewModel<SearchViewModel>(factory = SearchViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<FilterSheet?>(null) }
    val fieldRequester = remember { FocusRequester() }
    entry.Register(fieldRequester)

    // The search screen puts the cursor in the field on its own, the way a television
    // interface does.
    LaunchedEffect(Unit) {
        runCatching { fieldRequester.requestFocus() }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvTextField(
            value = state.query,
            onValueChange = { model.setQuery(it) },
            placeholder = "Type a title",
            onSearch = { model.search() },
            onClear = { model.clear() },
            onEditingChanged = { editing -> if (!editing) model.search() },
            modifier = Modifier
                .width(520.dp)
                .focusRequester(fieldRequester)
                .padding(horizontal = TvDimens.ScreenMargin),
        )

        TvChipRow {
            FilterSheet.entries.forEach { filter ->
                TvChip(
                    text = filter.title,
                    trailing = state.valueOf(filter).takeIf { it != null && it != filter.default },
                    active = state.valueOf(filter) != null && state.valueOf(filter) != filter.default,
                    onClick = { sheet = filter },
                )
            }
        }

        when {
            state.isLoading -> TvLoading(message = "The catalogue is searching.")
            state.error != null -> TvErrorState(
                message = state.error!!,
                onRetry = { model.search() },
            )

            state.results.isEmpty() -> TvEmptyState(
                message = "The catalogue does not know this.",
                hint = "Type a title, or pick a filter.",
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = TvDimens.ScreenMargin,
                    end = TvDimens.ScreenMargin,
                    bottom = 48.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(state.results, key = { _, card -> card.id }) { index, card ->
                    dev.animeplayer.tv.ui.components.TvCard(
                        title = card.title,
                        artwork = card.bannerUrl ?: card.posterUrl,
                        tint = card.posterColor,
                        metadata = card.infoLine,
                        statusLine = card.statusLine,
                        onClick = { onOpen(card) },
                        // The catalogue holds thousands of entries. The next page is
                        // fetched when the user reaches the end of what is on the screen.
                        modifier = Modifier.onFocusChanged { focused ->
                            if (focused.isFocused &&
                                index >= state.results.size - PREFETCH_MARGIN
                            ) {
                                model.loadMore()
                            }
                        },
                    )
                }

                if (state.isLoadingMore) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) }) {
                        Text(
                            text = "The catalogue is being asked for more…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    sheet?.let { entry ->
        val options = entry.options(state)
        TvSheet(
            title = entry.title,
            subtitle = "Pick one of the options, then press back to close.",
            onDismiss = { sheet = null },
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
            ) {
                items(options) { option ->
                    TvListItem(
                        title = option.first,
                        subtitle = option.second,
                        selected = state.valueOf(entry) == option.first,
                        onClick = {
                            model.setFilter(entry, option.first)
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
