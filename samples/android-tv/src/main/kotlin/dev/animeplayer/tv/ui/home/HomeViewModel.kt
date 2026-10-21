/*
 * Copyright (C) 2026 animeplayer contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of (at your option) any later
 * version. See <https://www.gnu.org/licenses/> for the full text.
 */
package dev.animeplayer.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.CatalogQuery
import dev.animeplayer.core.model.CatalogSort
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.core.currentSeason
import dev.animeplayer.tv.data.CatalogRepository
import dev.animeplayer.tv.data.TvState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One rail of the home screen.
 *
 * @property title the name of the rail.
 * @property query the query that fills the rail.
 * @property kind how the rail is drawn.
 */
public data class HomeRail(
    val title: String,
    val query: CatalogQuery,
    val kind: Kind = Kind.Posters,
) {
    /** The shape of the entries of the rail. */
    public enum class Kind {
        /** A row of landscape cards. */
        Posters,

        /** A row of numbered cards. */
        Top10,

        /** A row of wide cards with a description. */
        Wide,
    }
}

/**
 * The state of the home screen.
 */
public data class HomeState(
    val hero: TvState<List<CatalogMedia>> = TvState.Loading,
    val rails: List<HomeRail> = emptyList(),
    val loaded: Map<String, TvState<List<CatalogMedia>>> = emptyMap(),
    val continueWatching: TvState<List<CatalogMedia>> = TvState.Loading,
) {
    /** The entries of a rail. */
    public fun rail(title: String): TvState<List<CatalogMedia>> = loaded[title] ?: TvState.Loading
}

/**
 * The home screen.
 *
 * The screen is a stack of rails. Every rail is loaded on its own, so the screen is
 * usable before the last rail has arrived — and the catalogue only allows thirty requests
 * per minute, so a rail that is loaded twice is a rail that is never loaded.
 */
public class HomeViewModel(
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    private var job: kotlinx.coroutines.Job? = null

    /** The state of the screen. */
    public val state: StateFlow<HomeState> = mutableState.asStateFlow()

    /** The rails of the screen. */
    public val rails: List<HomeRail> = buildRails()

    /**
     * Loads the screen.
     */
    public fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            val (season, year) = currentSeason()
            val rails = rails
            mutableState.value = mutableState.value.copy(
                rails = rails,
                hero = TvState.Loading,
                loaded = rails.associate { it.title to TvState.Loading },
            )

            // The hero goes first — it is what the user sees.
            val hero = runCatching {
                catalog.page(
                    CatalogQuery(sort = CatalogSort.Popularity, perPage = 24),
                )
            }
            mutableState.value = mutableState.value.copy(
                hero = hero.fold(
                    onSuccess = { page ->
                        TvState.Ready(
                            page.media.filter { it.hasBanner }.take(5).ifEmpty { page.media.take(5) },
                        )
                    },
                    onFailure = { TvState.Failed(it.message ?: "The catalogue could not be loaded.") },
                ),
            )

            // The rails follow, one after another, so the catalogue is not hammered.
            for (rail in rails) {
                val result = runCatching { catalog.page(rail.query) }
                val state = result.fold(
                    onSuccess = { page -> TvState.Ready(page.media) },
                    onFailure = { TvState.Failed(it.message ?: "The catalogue could not be loaded.") },
                )
                mutableState.value = mutableState.value.copy(
                    loaded = mutableState.value.loaded + (rail.title to state),
                )
            }
        }
    }

    private fun buildRails(): List<HomeRail> {
        val (season, year) = currentSeason()
        return listOf(
            HomeRail(
                title = "Popular This Season",
                query = CatalogQuery(
                    sort = CatalogSort.Popularity,
                    season = season,
                    seasonYear = year,
                    perPage = 20,
                ),
            ),
            HomeRail(
                title = "Trending Now",
                query = CatalogQuery(sort = CatalogSort.Trending, perPage = 20),
            ),
            HomeRail(
                title = "Top 10 This Week",
                query = CatalogQuery(sort = CatalogSort.Score, perPage = 10),
                kind = HomeRail.Kind.Top10,
            ),
            HomeRail(
                title = "All Time Popular",
                query = CatalogQuery(sort = CatalogSort.Popularity, perPage = 20),
            ),
            HomeRail(
                title = "Upcoming",
                query = CatalogQuery(
                    sort = CatalogSort.StartDate,
                    status = "NOT_YET_RELEASED",
                    perPage = 20,
                ),
            ),
        ) + GENRES.map { genre ->
            HomeRail(
                title = genre,
                query = CatalogQuery(sort = CatalogSort.Trending, genre = genre, perPage = 20),
            )
        }
    }

    public companion object {
        private val GENRES = listOf("Action", "Romance", "Fantasy", "Comedy", "Drama")

        /**
         * The factory of the view model.
         */
        public fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(container.catalog) as T
            }
    }
}
