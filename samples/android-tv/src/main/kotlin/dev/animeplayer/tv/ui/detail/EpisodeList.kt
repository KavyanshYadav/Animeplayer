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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvEpisodeRow
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.model.EpisodeRow
import dev.animeplayer.tv.ui.theme.TvDimens

internal const val EpisodesPerRange = 24

internal fun episodeRangeLabel(episodes: List<EpisodeRow>): String {
    val first = episodes.first().number
    val last = episodes.last().number
    return if (first == last) "Episode $first" else "Episodes $first–$last"
}

internal fun LazyListScope.episodeList(
    state: TvState<List<EpisodeRow>>,
    episodes: List<EpisodeRow>,
    count: Int,
    playable: Boolean,
    resume: String?,
    requestedEpisode: String?,
    focusRequester: FocusRequester,
    rangeLabel: String?,
    onChooseRange: () -> Unit,
    onNextRange: (() -> Unit)?,
    onRetry: () -> Unit,
    onFocused: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    item(key = "episode-heading", contentType = "episode-heading") {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = TvDimens.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Episodes", style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground)
                if (rangeLabel != null) {
                    TvButton(
                        text = rangeLabel,
                        icon = Icons.AutoMirrored.Filled.List,
                        prominent = false,
                        onClick = onChooseRange,
                    )
                } else if (count > 0) {
                    Text(
                        if (count == 1) "1 episode" else "$count episodes",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (episodes.isEmpty()) {
                val message = when (state) {
                    TvState.Loading -> "Finding episodes…"
                    is TvState.Failed -> state.message
                    is TvState.Ready -> "No episodes are available."
                }
                Text(message, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                if (state is TvState.Failed) TvButton("Try again", onClick = onRetry)
            } else if (!playable) {
                Text("Streaming is unavailable for this title.", style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant)
            }
        }
    }

    items(episodes, key = { "episode:${it.number}" }, contentType = { "episode" }) { episode ->
        TvEpisodeRow(
            episode = episode,
            playable = playable,
            resume = episode.number == resume,
            modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin)
                .then(if (requestedEpisode == episode.number) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocused(episode.number) },
            onClick = { onPlay(episode.number) },
        )
    }

    if (onNextRange != null) {
        item(key = "episode-next-range", contentType = "episode-heading") {
            TvButton(
                "Next episodes",
                modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                prominent = false,
                onClick = onNextRange,
            )
        }
    }
}

@Composable
internal fun EpisodeRangePicker(
    ranges: List<List<EpisodeRow>>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    TvSheet("Jump to episodes", onDismiss = onDismiss) {
        LazyColumn(
            state = rememberLazyListState(selected),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(ranges.size, key = { ranges[it].first().number }) { index ->
                TvListItem(
                    title = episodeRangeLabel(ranges[index]),
                    selected = index == selected,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}
