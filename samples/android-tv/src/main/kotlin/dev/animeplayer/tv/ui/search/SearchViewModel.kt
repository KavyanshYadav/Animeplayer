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
package dev.animeplayer.tv.ui.search

import dev.animeplayer.tv.data.groupSeasons
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.CatalogQuery
import dev.animeplayer.core.model.CatalogSort
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.core.currentYear
import dev.animeplayer.tv.data.CatalogRepository
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The state of the catalogue.
 */
public data class SearchState(
    val query: String = "",
    val results: List<CatalogCard> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pagingFailed: Boolean = false,
    val genres: List<String> = emptyList(),
    val currentYear: Int = currentYear(),
    val filters: Map<FilterSheet, String> = emptyMap(),
    /** The page that is on the screen. */
    val page: Int = 1,
    /** `true` when the catalogue has more entries. */
    val hasNextPage: Boolean = false,
    /** `true` while the next page is on its way. */
    val isLoadingMore: Boolean = false,
    /** How many entries the catalogue holds for this query. */
    val total: Int = 0,
) {
    /** The value of a filter. */
    public fun valueOf(filter: FilterSheet): String? = filters[filter]
}

/**
 * The catalogue.
 */
public class SearchViewModel(
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchState())
    private var job: Job? = null
    private var nextPageJob: Job? = null

    internal fun ensureLoaded() {
        if (job == null) search()
    }

    public val state: StateFlow<SearchState> = mutableState.asStateFlow()
    private var loadedMedia: List<CatalogMedia> = emptyList()

    public fun setQuery(query: String) {
        if (mutableState.value.query == query) return
        mutableState.value = mutableState.value.copy(query = query)
        search(debounce = true)
    }

    public fun search() {
        search(debounce = false)
    }

    private fun search(debounce: Boolean) {
        job?.cancel()
        nextPageJob?.cancel()
        val current = state.value.copy(
            isLoading = true,
            isLoadingMore = false,
            hasNextPage = false,
            pagingFailed = false,
            error = null,
        )
        mutableState.value = current
        job = viewModelScope.launch {
            if (debounce) delay(350)
            try {
                val page = catalog.page(current.queryOf(page = 1))
                loadedMedia = page.media
                mutableState.value = current.copy(
                    results = page.media.groupSeasons().map { it.toCard() },
                    isLoading = false,
                    page = 1,
                    hasNextPage = page.hasNextPage,
                    total = page.total,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = current.copy(
                    isLoading = false,
                    results = emptyList(),
                    error = "Couldn't load results. Check your connection and try again.",
                )
            }
        }
    }

    public fun loadMore() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || !current.hasNextPage) return
        val next = current.page + 1
        mutableState.value = current.copy(isLoadingMore = true, pagingFailed = false)
        nextPageJob = viewModelScope.launch {
            try {
                var pageNumber = next
                var page = catalog.page(current.queryOf(page = pageNumber))
                var media = loadedMedia + page.media
                var grouped = media.groupSeasons()
                // A whole page can belong to series already on screen. Keep going
                // so continued browsing does not depend on focusing the same card again.
                while (page.hasNextPage && grouped.size <= current.results.size) {
                    page = catalog.page(current.queryOf(page = ++pageNumber))
                    media = media + page.media
                    grouped = media.groupSeasons()
                }
                loadedMedia = media
                mutableState.value = current.copy(
                    results = grouped.map { it.toCard() },
                    isLoadingMore = false,
                    pagingFailed = false,
                    page = pageNumber,
                    hasNextPage = page.hasNextPage,
                    total = page.total,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = current.copy(isLoadingMore = false, pagingFailed = true)
            }
        }
    }

    /**
     * The query of the current search, for the given page.
     */
    private fun SearchState.queryOf(page: Int): CatalogQuery {
        val filters = filters
        return CatalogQuery(
            search = query.takeIf { it.isNotBlank() },
            genre = filters[FilterSheet.Genre],
            season = filters[FilterSheet.Season],
            seasonYear = filters[FilterSheet.Year]?.toIntOrNull(),
            format = filters[FilterSheet.Format],
            status = filters[FilterSheet.Status],
            sort = filters[FilterSheet.Sort] ?: CatalogSort.Trending,
            page = page,
            perPage = 24,
        )
    }

    /** Empties the search. */
    public fun clear() {
        mutableState.value = mutableState.value.copy(query = "")
        search()
    }

    /** Sets a filter and searches again. */
    public fun setFilter(filter: FilterSheet, value: String) {
        mutableState.value = mutableState.value.copy(
            filters = mutableState.value.filters + (filter to value),
        )
        search()
    }

    /** Removes a filter and searches again. */
    public fun clearFilter(filter: FilterSheet) {
        mutableState.value = mutableState.value.copy(
            filters = mutableState.value.filters - filter,
        )
        search()
    }

    public companion object {

        /**
         * The factory of the view model.
         */
        public fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val model = SearchViewModel(container.catalog)
                    model.mutableState.value = model.mutableState.value.copy(
                        genres = container.catalog.genres,
                    )
                    return model as T
                }
            }
    }
}
