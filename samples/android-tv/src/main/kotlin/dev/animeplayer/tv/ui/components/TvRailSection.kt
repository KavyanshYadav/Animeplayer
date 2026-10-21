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
package dev.animeplayer.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.focus.TvRail
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * A section of the home screen — a title and a rail of cards.
 *
 * The title is decoration: it is not focusable, so the D-pad goes straight from the last
 * card of one rail to the first card of the next. That is how every streaming service
 * on a television behaves, and it keeps the movement of the focus predictable.
 *
 * @param title the name of the section.
 * @param cards the entries of the section.
 * @param onOpen called with the entry that was pressed.
 * @param onSeeAll called when the "See all" tile is pressed.
 */
@Composable
public fun TvRailSection(
    title: String,
    cards: List<CatalogCard>,
    onOpen: (CatalogCard) -> Unit,
    modifier: Modifier = Modifier,
    screen: String? = null,
    memory: FocusMemory? = null,
    progressOf: (CatalogCard) -> Float? = { null },
    onSeeAll: (() -> Unit)? = null,
    isLoading: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
        )

        if (isLoading) {
            TvSkeletonRow(
                count = 6,
                modifier = Modifier.padding(top = 2.dp),
            )
            return@Column
        }

        TvRail(
            items = cards + if (onSeeAll != null) listOf(SEE_ALL) else emptyList(),
            key = { if (it == SEE_ALL) "see-all" else it.id },
            screen = screen,
            memory = memory,
            contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
            itemSpacing = TvDimens.RailGap,
        ) { card ->
            if (card == SEE_ALL) {
                TvSeeAllCard(
                    title = title,
                    width = TvDimens.ScreenMargin * 1.6f,
                    onClick = { onSeeAll?.invoke() },
                )
            } else {
                TvCard(
                    title = card.title,
                    artwork = card.posterUrl ?: card.bannerUrl,
                    tint = card.posterColor,
                    metadata = card.infoLine,
                    progress = progressOf(card),
                    onClick = { onOpen(card) },
                )
            }
        }
    }
}

/**
 * The marker of the "See all" tile.
 */
private val SEE_ALL: CatalogCard = CatalogCard(id = "__see_all__", title = "See all")
