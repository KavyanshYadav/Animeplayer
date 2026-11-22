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
package dev.animeplayer.tv.ui.player.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.animeplayer.tv.ui.components.TvEpisodeRow
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.model.EpisodeRow
import dev.animeplayer.tv.ui.player.Episode
import dev.animeplayer.tv.ui.theme.LocalTvLayout

@Composable
internal fun PlayerEpisodesPanel(
    episodes: List<Episode>,
    currentEpisode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val rows = remember(episodes) {
        episodes.map {
            EpisodeRow(
                number = it.number,
                title = it.title,
                description = it.description,
                thumbnail = it.thumbnailUrl,
                duration = it.durationSeconds,
                watched = it.watched,
                isFiller = it.isFiller,
            )
        }
    }
    val selected = rows.indexOfFirst { it.number == currentEpisode }.coerceAtLeast(0)
    val listState = rememberLazyListState(selected)
    val currentFocus = remember { FocusRequester() }
    val artworkWidth = (LocalTvLayout.current.width * 0.19f).coerceIn(88.dp, 230.dp)

    TvSheet(title = "Episodes", onDismiss = onDismiss) {
        LaunchedEffect(currentEpisode) {
            withFrameNanos { }
            currentFocus.requestTvFocus()
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else false
            },
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rows, key = { it.number }, contentType = { "episode" }) { row ->
                val isCurrent = row.number == currentEpisode
                TvEpisodeRow(
                    episode = row,
                    playable = true,
                    resume = false,
                    nowPlaying = isCurrent,
                    thumbnailWidth = artworkWidth,
                    modifier = if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier,
                    onClick = { onSelect(row.number) },
                )
            }
        }
    }
}
