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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvContinueWatchingRail
import dev.animeplayer.tv.ui.components.TvRailSection
import dev.animeplayer.tv.ui.components.TvPageHeading
import dev.animeplayer.tv.ui.components.TvTextField
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.ContinueWatchingItem
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
public fun LibraryScreen(
    container: AppContainer,
    memory: FocusMemory,
    onOpen: (CatalogCard) -> Unit,
    onResume: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<ContinueWatchingItem>?>(null) }
    var historyFailed by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(false) }
    var restoreResume by rememberSaveable { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    entry.Register(fieldFocus)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh = !refresh }

    LaunchedEffect(refresh) {
        try {
            historyFailed = false
            history = container.history.inProgress().take(24).map {
                ContinueWatchingItem(
                    card = CatalogCard(id = it.animeId.slug, title = it.title),
                    episodeNumber = it.episodeNumber,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                    thumbnailUrl = container.history.episodeArtwork(it.animeId.slug, it.episodeNumber),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            historyFailed = true
            history = emptyList()
        }
    }

    LaunchedEffect(query) {
        results = emptyList()
        error = null
        searching = query.trim().length >= 2
        if (!searching) return@LaunchedEffect
        delay(350)
        try {
            results = container.library.search(query.trim()).distinctBy { it.id.slug }.map {
                CatalogCard(
                    id = it.id.slug,
                    title = it.title,
                    description = it.description,
                    posterUrl = it.posterUrl,
                    year = it.year,
                    genres = it.genres,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Couldn't search the streaming library. Try again in a moment."
        }
        searching = false
    }

    val margin = Modifier.padding(horizontal = TvDimens.ScreenMargin)
    val layout = LocalTvLayout.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(19.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "library-heading") {
            TvPageHeading("Library", "Pick up an episode or search the streaming library.", margin)
        }
        item(key = "library-search") {
            Box(margin.fillMaxWidth()) {
                TvTextField(
                    value = query,
                    onValueChange = { query = it },
                    onClear = { query = "" },
                    placeholder = "Search available titles",
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().focusRequester(fieldFocus),
                )
            }
        }
        item(key = "continue-watching") {
            val items = history.orEmpty()
            if (items.isEmpty()) {
                Text(
                    text = when {
                        historyFailed -> "Couldn't read watch history."
                        history == null -> "Loading watch history…"
                        else -> "Episodes you start watching will appear here."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = margin,
                )
            } else {
                TvContinueWatchingRail(
                    items = items,
                    memory = memory,
                    onResume = {
                        restoreResume = true
                        onResume(it)
                    },
                    cardWidth = maxOf(layout.cardWidth, 140.dp),
                    restoreFocus = restoreResume,
                    screen = "library:continue-watching",
                )
            }
        }
        if (query.trim().length >= 2 && (searching || results.isEmpty())) {
            item(key = "library-search-status") {
                Text(
                    text = when {
                        searching -> "Searching available titles…"
                        error != null -> error.orEmpty()
                        else -> "No playable titles found."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = margin,
                )
            }
        }
        if (results.isNotEmpty()) {
            item(key = "library-results") {
                TvRailSection(
                    title = "Matches",
                    cards = results,
                    onOpen = onOpen,
                    landscape = true,
                    cardWidth = layout.cardWidth,
                    memory = memory,
                    screen = "library:results",
                )
            }
        }
    }
}
