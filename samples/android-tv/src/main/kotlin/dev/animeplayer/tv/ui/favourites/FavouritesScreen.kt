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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvCard
import dev.animeplayer.tv.ui.components.TvEmptyState
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import dev.animeplayer.tv.ui.theme.TvDimens

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
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val ids = container.favourites.ids()
            if (ids.isEmpty()) emptyList() else container.catalog.media(ids)
        }
            .onSuccess { media -> cards = media.map { it.toCard() } }
            .onFailure { error = it.message ?: "The favourites could not be loaded." }
    }

    when {
        error != null -> TvErrorState(message = error!!)
        cards == null -> TvLoading(message = "The favourites are loading.")
        cards!!.isEmpty() -> TvEmptyState(
            message = "Nothing marked yet.",
            hint = "The heart of a series marks it.",
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = TvDimens.ScreenMargin,
                end = TvDimens.ScreenMargin,
                top = 16.dp,
                bottom = 48.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(cards!!, key = { it.id }) { card ->
                TvCard(
                    title = card.title,
                    artwork = card.posterUrl ?: card.bannerUrl,
                    tint = card.posterColor,
                    metadata = card.infoLine,
                    onClick = { onOpen(card) },
                )
            }
        }
    }
}
