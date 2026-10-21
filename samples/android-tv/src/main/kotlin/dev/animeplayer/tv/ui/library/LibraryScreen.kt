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
package dev.animeplayer.tv.ui.library

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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvCard
import dev.animeplayer.tv.ui.components.TvEmptyState
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.components.TvTextField
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * The library.
 *
 * Two things live here: what the user watched, and what the upstream site knows. The
 * second one matters, because the catalogue of the other site is only artwork — the
 * library is where the episodes actually are.
 *
 * @param container the container of the dependencies.
 */
@Composable
public fun LibraryScreen(
    container: AppContainer,
    memory: FocusMemory,
    onOpen: (dev.animeplayer.tv.ui.model.CatalogCard) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<dev.animeplayer.tv.ui.model.CatalogCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<HistoryRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { container.history.inProgress() }
            .onSuccess { entries ->
                history = entries.map {
                    HistoryRow(
                        slug = it.animeId.slug,
                        title = it.title,
                        episode = it.episodeNumber,
                        progress = it.progress,
                        positionMs = it.positionMs,
                        durationMs = it.durationMs,
                    )
                }
                // The artwork of an episode is not in the history, so it is asked of the
                // library — the rows of the history are few.
                history = history.map { row ->
                    row.copy(
                        poster = runCatching {
                            container.library.anime(row.slug).posterUrl
                        }.getOrNull(),
                    )
                }
            }
    }

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        error = null
        runCatching { container.library.search(query) }
            .onSuccess { found ->
                results = found.map {
                    dev.animeplayer.tv.ui.model.CatalogCard(
                        id = it.id.slug,
                        title = it.title,
                        description = it.description,
                        posterUrl = it.posterUrl,
                        metadata = listOfNotNull(it.year?.toString(), it.status.name),
                        year = it.year,
                        format = it.status.name,
                        status = it.status.name,
                        genres = it.genres,
                    )
                }
            }
            .onFailure { error = it.message ?: "The library could not be searched." }
        searching = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TvDimens.RailSpacing),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "history") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Continue watching",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                )
                if (history.isEmpty()) {
                    Text(
                        text = "Nothing watched yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
                        horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
                    ) {
                        items(history, key = { it.slug + it.episode }) { row ->
                            TvCard(
                                title = row.title,
                                artwork = row.poster,
                                tint = MaterialTheme.colorScheme.surfaceVariant,
                                metadata = "Episode ${row.episode} · ${formatTime(row.positionMs)}",
                                progress = row.progress,
                                width = TvDimens.PosterCardWidth,
                                onClick = { },
                            )
                        }
                    }
                }
            }
        }

        item(key = "search") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Search the upstream site",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                )
                TvTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Type a title",
                    modifier = Modifier
                        .width(520.dp)
                        .padding(horizontal = TvDimens.ScreenMargin),
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                    )
                }
                if (searching) {
                    TvLoading(message = "The library is searching.")
                } else {
                    results.forEach { card ->
                        TvListItem(
                            title = card.title,
                            subtitle = card.genres.take(3).joinToString(" · "),
                            trailing = card.year?.toString(),
                            onClick = { onOpen(card) },
                            modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One entry of the watch history.
 */
public data class HistoryRow(
    val slug: String,
    val title: String,
    val episode: String,
    val progress: Float,
    val positionMs: Long,
    val durationMs: Long,
    val poster: String? = null,
)

/** `12:04` */
private fun formatTime(milliseconds: Long): String {
    val total = milliseconds / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
