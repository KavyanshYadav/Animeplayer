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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Continue watching",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
        )

        TvRail(
            items = items,
            key = { "${it.card.id}:${it.episodeNumber}" },
            screen = "${FocusKeys.Home}:Continue watching",
            memory = memory,
            contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
            itemSpacing = TvDimens.RailGap,
        ) { item ->
            TvCard(
                title = item.card.title,
                artwork = item.card.bannerUrl ?: item.card.posterUrl,
                tint = item.card.posterColor,
                badge = "EP ${item.episodeNumber}",
                metadata = "${formatWatchTime(item.positionMs)} watched · ${formatWatchTime((item.durationMs - item.positionMs).coerceAtLeast(0L))} left",
                progress = item.progress,
                width = TvDimens.ContinueCardWidth,
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
