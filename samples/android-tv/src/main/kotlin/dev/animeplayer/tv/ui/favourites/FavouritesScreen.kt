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
package dev.animeplayer.tv.ui.favourites

import dev.animeplayer.tv.ui.model.withTmdbArtwork
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.ListStatus
import dev.animeplayer.tv.ui.components.TvChip
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.components.TvCard
import dev.animeplayer.tv.ui.components.TvEmptyState
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.components.TvPageHeading
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import kotlinx.coroutines.CancellationException

/**
 * The series the user marked.
 *
 * The favourites are remembered by the identifier of the catalogue, so they are the same
 * on the television and on the desktop.
 *
 * @param container the container of the dependencies.
 */
@Composable
public fun FavouritesScreen(
    container: AppContainer,
    memory: FocusMemory,
    onOpen: (CatalogCard) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
) {
    var cards by remember { mutableStateOf<List<CatalogCard>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retries by remember { mutableStateOf(0) }
    val entries by container.favourites.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf<ListStatus?>(null) }
    val ids = remember(entries) { entries.map { it.id } }
    val grid = rememberLazyGridState()
    val focus = remember { FocusRequester() }
    val restoreId = remember { memory.recall(FocusKeys.Favourites) }
    val filterFocus = remember { FocusRequester() }
    entry.Register(filterFocus)

    LaunchedEffect(retries, ids) {
        failed = false
        cards = null
        try {
            cards = ids.chunked(40).flatMap { container.catalog.media(it) }.distinctBy { it.id }.map { it.toCard() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    LaunchedEffect(cards, restoreId) {
        val index = cards.orEmpty().indexOfFirst { it.id == restoreId }
        if (index >= 0) {
            grid.scrollToItem(index)
            withFrameNanos { }
            focus.requestTvFocus()
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvPageHeading("My List", modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin, vertical = 12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf(null) + ListStatus.entries) { status ->
                TvChip(status?.label ?: "All", active = filter == status, onClick = { filter = status },
                    modifier = if (status == null) Modifier.focusRequester(filterFocus) else Modifier)
            }
        }
        val titles = cards?.filter { card -> filter == null || entries.any { it.id == card.id && it.status == filter } }
        when {
            failed -> TvErrorState("Couldn't load My List.", onRetry = { retries++ })
            titles == null -> TvLoading(message = "Loading saved titles…")
            titles.isEmpty() -> TvEmptyState(
                if (filter == null) "Your list is empty" else "No titles in ${filter?.label}",
                hint = "Use My List on a series to save it for later.",
            )
            else -> {
                val target = titles.firstOrNull { it.id == restoreId } ?: titles.first()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(LocalTvLayout.current.columns),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = TvDimens.ScreenMargin,
                        end = TvDimens.ScreenMargin,
                        top = 5.dp,
                        bottom = 48.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(titles, key = { it.id }, contentType = { "artwork" }) { entry ->
                        val card = entry.withTmdbArtwork()
                        TvCard(
                            title = card.title,
                            artwork = card.bannerUrl ?: card.posterUrl,
                            aspectRatio = 16f / 9f,
                            tint = card.posterColor,
                            metadata = entries.firstOrNull { it.id == card.id }?.let {
                                listOfNotNull(it.status.label, "${it.progress} watched",
                                    it.score.takeIf { score -> score > 0 }?.let { score -> "$score/10" }).joinToString(" · ")
                            } ?: card.infoLine,
                            modifier = if (card.id == target.id) Modifier.focusRequester(focus) else Modifier,
                            onClick = {
                                memory.remember(FocusKeys.Favourites, card.id)
                                onOpen(card)
                            },
                        )
                    }
                }
            }
        }
    }
}
