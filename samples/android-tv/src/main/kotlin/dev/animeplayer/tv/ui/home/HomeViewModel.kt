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
import dev.animeplayer.tv.data.FavouritesRepository
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.data.WatchHistoryRepository
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.ContinueWatchingItem
import dev.animeplayer.tv.ui.model.toCard
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        /** A row of portrait poster cards. */
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
    val continueWatching: TvState<List<ContinueWatchingItem>> = TvState.Loading,
    val myList: TvState<List<CatalogMedia>> = TvState.Loading,
    val recommendations: TvState<List<CatalogMedia>> = TvState.Loading,
    val favouriteIds: Set<String> = emptySet(),
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
    private val historyRepository: WatchHistoryRepository,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {
    private val rails: List<HomeRail> = buildRails()
    private val featuredRail = rails.first()
    private val mutableState = MutableStateFlow(HomeState(rails = rails))
    private val catalogRequests = Mutex()
    private val requestedRails = mutableSetOf<String>()
    private var featuredJob: Job? = null
    private var personalJob: Job? = null

    public val state: StateFlow<HomeState> = mutableState.asStateFlow()

    public fun load(force: Boolean = false) {
        if (featuredJob != null && !force) return
        featuredJob?.cancel()
        featuredJob = viewModelScope.launch {
            if (force) {
                requestedRails.removeAll { mutableState.value.loaded[it] is TvState.Failed }
                mutableState.value = mutableState.value.copy(
                    hero = TvState.Loading,
                    loaded = mutableState.value.loaded.filterValues { it !is TvState.Failed },
                )
            }
            try {
                val page = catalogRequests.withLock { catalog.page(featuredRail.query) }
                val featured = page.media.filter { it.hasBanner }.ifEmpty { page.media }.take(5)
                val shelf = page.media.filterNot { entry -> featured.any { it.id == entry.id } }
                setRail(featuredRail.title, TvState.Ready(shelf))
                mutableState.value = mutableState.value.copy(
                    hero = if (featured.isEmpty()) TvState.Failed("No featured titles") else TvState.Ready(featured),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val failure = TvState.Failed("Couldn't load anime. Try again.")
                setRail(featuredRail.title, failure)
                mutableState.value = mutableState.value.copy(hero = failure)
            }
        }
    }

    internal fun loadRail(rail: HomeRail) {
        if (rail == featuredRail || !requestedRails.add(rail.title)) return
        viewModelScope.launch {
            featuredJob?.join()
            try {
                val page = catalogRequests.withLock { catalog.page(rail.query) }
                setRail(rail.title, TvState.Ready(page.media))
            } catch (cancelled: CancellationException) {
                requestedRails.remove(rail.title)
                throw cancelled
            } catch (_: Exception) {
                setRail(rail.title, TvState.Failed("Couldn't load this row"))
            }
        }
    }

    private fun setRail(title: String, content: TvState<List<CatalogMedia>>) {
        mutableState.value = mutableState.value.copy(loaded = mutableState.value.loaded + (title to content))
    }

    internal fun refreshContinueWatching() {
        personalJob?.cancel()
        personalJob = viewModelScope.launch {
            val history = recoverOr(emptyList()) {
                historyRepository.inProgress().distinctBy { it.animeId.slug }.take(12)
            }
            val favouriteIds = recoverOr(emptySet()) { favouritesRepository.ids().toSet() }
            val known = state.value.continueWatching.valueOrNull().orEmpty().associateBy { it.seriesSlug }
            val localItems = history.map { entry ->
                val hue = (entry.animeId.slug.hashCode().and(Int.MAX_VALUE) % 360).toFloat()
                ContinueWatchingItem(
                    card = known[entry.animeId.slug]?.card ?: CatalogCard(
                        id = entry.animeId.slug,
                        title = entry.title,
                        posterColor = Color.hsl(hue, 0.38f, 0.22f),
                    ),
                    episodeNumber = entry.episodeNumber,
                    positionMs = entry.positionMs,
                    durationMs = entry.durationMs,
                    seriesSlug = entry.animeId.slug,
                )
            }
            mutableState.value = mutableState.value.copy(
                continueWatching = TvState.Ready(localItems),
                favouriteIds = favouriteIds,
            )

            featuredJob?.join()
            val artwork = localItems.filter { it.card.id == it.seriesSlug }
                .map { it.card.title }.distinct().associateWith { title ->
                    recoverOr(null) {
                        val normalizedTitle = title.normalizedTitle()
                        catalogRequests.withLock { catalog.page(CatalogQuery(search = title, perPage = 5)) }
                            .media.firstOrNull { media -> media.titles.any { it.normalizedTitle() == normalizedTitle } }
                    }
                }
            val items = localItems.map { item ->
                artwork[item.card.title]?.let { item.copy(card = it.toCard()) } ?: item
            }
            mutableState.value = mutableState.value.copy(continueWatching = TvState.Ready(items))
            val savedIds = mutableState.value.favouriteIds
            val saved = loadMyList(savedIds)
            if (savedIds == mutableState.value.favouriteIds) {
                mutableState.value = mutableState.value.copy(myList = TvState.Ready(saved))
            }
            val recommendations = loadRecommendations(items)
            mutableState.value = mutableState.value.copy(recommendations = TvState.Ready(recommendations))
        }
    }

    /** Adds or removes a series from My List and refreshes the home rail. */
    internal fun toggleFavourite(card: CatalogCard): Unit {
        viewModelScope.launch {
            val isFavourite = recoverOr<Boolean?>(null) { favouritesRepository.toggle(card.id) } ?: return@launch
            val favouriteIds = mutableState.value.favouriteIds.toMutableSet().apply {
                if (isFavourite) add(card.id) else remove(card.id)
            }
            mutableState.value = mutableState.value.copy(favouriteIds = favouriteIds)
            val saved = loadMyList(favouriteIds)
            if (favouriteIds == mutableState.value.favouriteIds) {
                mutableState.value = mutableState.value.copy(myList = TvState.Ready(saved))
            }
        }
    }

    private suspend fun loadMyList(ids: Set<String>): List<CatalogMedia> = recoverOr(emptyList()) {
        if (ids.isEmpty()) emptyList() else catalogRequests.withLock { catalog.media(ids.toList().take(20)) }
    }

    private suspend fun loadRecommendations(items: List<ContinueWatchingItem>): List<CatalogMedia> {
        val sourceId = items.firstNotNullOfOrNull { item ->
            item.card.id.takeIf { it.toLongOrNull() != null }
        } ?: return emptyList()
        return recoverOr(emptyList()) { catalogRequests.withLock { catalog.recommendations(sourceId) } }
    }

    private suspend fun <T> recoverOr(fallback: T, action: suspend () -> T): T = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }

    private fun String.normalizedTitle(): String =
        lowercase().filter(Char::isLetterOrDigit)

    private fun buildRails(): List<HomeRail> {
        val (season, year) = currentSeason()
        return listOf(
            HomeRail(
                title = "Trending Now",
                query = CatalogQuery(sort = CatalogSort.Trending, perPage = 24),
            ),
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
                title = "Top Rated",
                query = CatalogQuery(sort = CatalogSort.Score, perPage = 10),
                kind = HomeRail.Kind.Top10,
            ),
            HomeRail(
                title = "All Time Popular",
                query = CatalogQuery(sort = CatalogSort.Popularity, perPage = 20),
            ),
            HomeRail(
                title = "Coming Soon",
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
                    HomeViewModel(container.catalog, container.history, container.favourites) as T
            }
    }
}
