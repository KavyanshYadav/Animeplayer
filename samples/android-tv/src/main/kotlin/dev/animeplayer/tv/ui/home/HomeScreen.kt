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
package dev.animeplayer.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.ui.components.TvHero
import dev.animeplayer.tv.ui.components.TvRailSection
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * The home screen.
 *
 * The screen is a stack of rails under a banner. The whole page scrolls with the D-pad,
 * and the focus moves from rail to rail — which is the spatial model every television
 * interface uses.
 *
 * @param container the container of the dependencies.
 * @param memory the memory of the focus.
 * @param onOpen called with the series the user picked.
 */
@Composable
public fun HomeScreen(
    container: AppContainer,
    memory: FocusMemory,
    onOpen: (CatalogCard) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
) {
    val model: HomeViewModel = viewModel<HomeViewModel>(
        factory = HomeViewModel.factory(container),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val layout = LocalTvLayout.current

    LaunchedEffect(Unit) { model.load() }

    // The banner is the way into the screen, so the navigation bar hands the focus here.
    val playRequester = remember { FocusRequester() }
    entry.Register(playRequester)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TvDimens.RailSpacing),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "hero") {
            TvHero(
                featured = (state.hero as? TvState.Ready)?.value?.map { it.toCard() }.orEmpty(),
                height = layout.height * TvDimens.HeroHeightFraction,
                modifier = Modifier.fillMaxWidth(),
                onPlay = { onOpen(it) },
                playRequester = playRequester,
            )
        }

        items(state.rails, key = { it.title }) { rail ->
            val railState = state.rail(rail.title)
            TvRailSection(
                title = rail.title,
                cards = (railState as? TvState.Ready)?.value?.map { it.toCard() }.orEmpty(),
                isLoading = railState.isLoading,
                onOpen = onOpen,
                screen = "${FocusKeys.Home}:${rail.title}",
                memory = memory,
            )
        }
    }
}
