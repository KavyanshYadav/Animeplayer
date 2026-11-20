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

import dev.animeplayer.tv.ui.model.withTmdbArtwork
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.focus.TvRail
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * How the entries of a rail are drawn.
 */
public enum class TvRailKind {
    /** A row of portrait posters. */
    Posters,

    /** A row of ranked portrait posters. */
    Top10,
}

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
    kind: TvRailKind = TvRailKind.Posters,
    cardWidth: Dp = TvDimens.PosterCardWidth,
    entryFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    restoreFocus: Boolean = false,
    landscape: Boolean = false,
    horizontalPadding: Dp = TvDimens.ScreenMargin,
    onArtworkSettled: ((String) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )

        if (isLoading) {
            TvSkeletonRow(
                count = TvDimens.VisibleCards,
                width = cardWidth,
                aspectRatio = if (landscape) 16f / 9f else 1f / TvDimens.CardArtRatio,
                modifier = Modifier.padding(top = 2.dp),
                horizontalPadding = horizontalPadding,
            )
            return@Column
        }

        val entries = remember(cards, kind, onSeeAll != null) {
            cards.mapIndexed { index, card ->
                RailEntry(card, if (kind == TvRailKind.Top10) index + 1 else null)
            } + if (onSeeAll != null) listOf(RailEntry(SEE_ALL, null)) else emptyList()
        }

        TvRail(
            items = entries,
            key = { it.card.id },
            screen = screen,
            memory = memory,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            itemSpacing = TvDimens.RailGap,
            entryFocusRequester = entryFocusRequester,
            onNavigateUp = onNavigateUp,
            restoreFocus = restoreFocus,
        ) { entry, itemModifier ->
            val card = entry.card.withTmdbArtwork()
            when {
                card == SEE_ALL -> TvSeeAllCard(
                    title = title,
                    modifier = itemModifier,
                    width = TvDimens.ScreenMargin * 1.6f,
                    onClick = { onSeeAll?.invoke() },
                )

                entry.rank != null -> TvTopCard(
                    rank = entry.rank,
                    title = card.title,
                    artwork = if (landscape) card.bannerUrl ?: card.posterUrl else card.posterUrl ?: card.bannerUrl,
                    tint = card.posterColor,
                    modifier = itemModifier,
                    metadata = card.metadata.singleOrNull() ?: card.infoLine,
                    width = cardWidth,
                    aspectRatio = if (landscape) 16f / 9f else 1f / TvDimens.CardArtRatio,
                    onClick = { onOpen(card) },
                )

                else -> TvCard(
                    title = card.title,
                    artwork = if (landscape) card.bannerUrl ?: card.posterUrl else card.posterUrl ?: card.bannerUrl,
                    aspectRatio = if (landscape) 16f / 9f else 1f / TvDimens.CardArtRatio,
                    tint = card.posterColor,
                    modifier = itemModifier,
                    metadata = card.metadata.singleOrNull() ?: card.infoLine,
                    statusLine = card.statusLine,
                    progress = progressOf(card),
                    width = cardWidth,
                    onArtworkSettled = onArtworkSettled?.takeUnless { card.artworkPending }
                        ?.let { settled -> { settled(card.id) } },
                    onClick = { onOpen(card) },
                )
            }
        }
    }
}

/**
 * One entry of a rail.
 */
private data class RailEntry(
    val card: CatalogCard,
    /** The rank of the entry, when this is a ranked rail. */
    val rank: Int?,
)

/**
 * The marker of the "See all" tile.
 */
private val SEE_ALL: CatalogCard = CatalogCard(id = "__see_all__", title = "See all")
