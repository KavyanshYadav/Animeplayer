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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.focus.TvRail
import dev.animeplayer.tv.ui.model.ContinueWatchingItem
import dev.animeplayer.tv.ui.theme.TvDimens

/** A resume rail backed by the user's in-progress watch history. */
@Composable
internal fun TvContinueWatchingRail(
    items: List<ContinueWatchingItem>,
    memory: FocusMemory,
    onResume: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = TvDimens.ContinueCardWidth,
    entryFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    restoreFocus: Boolean = false,
    screen: String = "${FocusKeys.Home}:Continue watching",
    horizontalPadding: Dp = TvDimens.ScreenMargin,
    cardHeight: Dp = cardWidth * (9f / 16f),
    onNavigateDown: (() -> Boolean)? = null,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = horizontalPadding),
            maxLines = 1,
        )

        TvRail(
            items = items,
            key = { "${it.seriesSlug}:${it.episodeNumber}" },
            screen = screen,
            memory = memory,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            itemSpacing = TvDimens.RailGap,
            entryFocusRequester = entryFocusRequester,
            onNavigateUp = onNavigateUp,
            restoreFocus = restoreFocus,
            onNavigateDown = onNavigateDown,
        ) { item, itemModifier ->
            val card = item.card.withTmdbArtwork()
            TvCard(
                title = card.title,
                artwork = card.bannerUrl ?: card.posterUrl,
                tint = item.card.posterColor,
                modifier = itemModifier,
                badge = "EP ${item.episodeNumber}",
                aspectRatio = cardWidth / cardHeight,
                metadata = "${formatWatchTime((item.durationMs - item.positionMs).coerceAtLeast(0L))} left",
                progress = item.progress,
                width = cardWidth,
                onClick = { onResume(item) },
            )
        }
    }
}

private fun formatWatchTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0 min"
    val totalMinutes = (milliseconds / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${totalMinutes} min"
}
