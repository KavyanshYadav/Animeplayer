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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import dev.animeplayer.tv.data.AnimeListEntry
import dev.animeplayer.tv.data.ListStatus
import dev.animeplayer.tv.data.MaxListProgress
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.model.EpisodeRow

@Composable
internal fun AnimeListEditor(
    id: String,
    entry: AnimeListEntry?,
    episodes: List<EpisodeRow>,
    episodeCount: Int?,
    onChange: ((AnimeListEntry?) -> AnimeListEntry?) -> Unit,
    onMark: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var showEpisodes by rememberSaveable { mutableStateOf(false) }
    val progress = entry?.progress ?: 0
    val limit = maxOf(progress, episodeCount ?: MaxListProgress).coerceAtMost(MaxListProgress)
    val score = entry?.score ?: 0
    fun edit(change: (AnimeListEntry) -> AnimeListEntry) {
        onChange { change(it ?: AnimeListEntry(id, ListStatus.Watching)) }
    }

    TvSheet(
        title = if (showEpisodes) "Mark episodes" else "My List",
        onDismiss = { if (showEpisodes) showEpisodes = false else onDismiss() },
        actions = {
            TvButton(if (showEpisodes) "Back to list" else "Done", onClick = {
                if (showEpisodes) showEpisodes = false else onDismiss()
            })
        },
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showEpisodes) {
                items(episodes, key = { it.number }) { episode ->
                    TvListItem(
                        title = "Episode ${episode.number}",
                        trailing = if (episode.isWatched) "Watched" else "Unwatched",
                        onClick = { onMark(episode.number, !episode.isWatched) },
                    )
                }
            } else {
                items(ListStatus.entries, key = { it.name }) { status ->
                    TvListItem(status.label, selected = status == entry?.status, onClick = { edit { it.copy(status = status) } })
                }
                item("progress") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvButton("−", enabled = progress > 0, onClick = { edit { it.setProgress(it.progress - 1) } })
                        Text("Progress  $progress", color = MaterialTheme.colorScheme.onSurface)
                        TvButton("+10", prominent = false, enabled = progress < limit,
                            onClick = { edit { it.setProgress((it.progress + 10).coerceAtMost(limit)) } })
                        TvButton("+", enabled = progress < limit,
                            onClick = { edit { it.setProgress((it.progress + 1).coerceAtMost(limit)) } })
                    }
                }
                item("score") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvButton("−", enabled = score > 0, onClick = { edit { it.copy(score = (it.score - 1).coerceAtLeast(0)) } })
                        Text(if (score == 0) "Score  Unrated" else "Score  $score/10",
                            color = MaterialTheme.colorScheme.onSurface)
                        TvButton("+", enabled = score < 10, onClick = { edit { it.copy(score = (it.score + 1).coerceAtMost(10)) } })
                    }
                }
                if (episodes.isNotEmpty()) item("episodes") {
                    TvListItem("Mark episodes watched or unwatched", onClick = { showEpisodes = true })
                }
                if (entry != null) item("remove") {
                    TvListItem("Remove from My List", onClick = { onChange { null }; onDismiss() })
                }
            }
        }
    }
}
