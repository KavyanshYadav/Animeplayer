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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.animeplayer.core.model.CatalogQuery
import dev.animeplayer.core.model.CatalogSort
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.core.currentYear
import dev.animeplayer.tv.data.CatalogRepository
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
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
    val genres: List<String> = emptyList(),
    val currentYear: Int = currentYear(),
    val filters: Map<FilterSheet, String> = emptyMap(),
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

    /** The state of the screen. */
    public val state: StateFlow<SearchState> = mutableState.asStateFlow()

    /** The genres the catalogue knows about. */
    public val genres: List<String> get() = catalog.genres

    /**
     * Sets the text of the search and looks for it after a short pause.
     */
    public fun setQuery(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        job?.cancel()
        if (query.isBlank()) {
            mutableState.value = mutableState.value.copy(results = emptyList(), isLoading = false)
            return
        }
        job = viewModelScope.launch {
            delay(400)
            search()
        }
    }

    /**
     * Looks for the current query and the current filters.
     */
    public fun search() {
        job?.cancel()
        job = viewModelScope.launch {
            val current = mutableState.value
            mutableState.value = current.copy(isLoading = true, error = null)
            val filters = current.filters
            val query = CatalogQuery(
                search = current.query.takeIf { it.isNotBlank() },
                genre = filters[FilterSheet.Genre],
                season = filters[FilterSheet.Season],
                seasonYear = filters[FilterSheet.Year]?.toIntOrNull(),
                format = filters[FilterSheet.Format],
                status = filters[FilterSheet.Status],
                sort = filters[FilterSheet.Sort] ?: CatalogSort.Trending,
                perPage = 48,
            )
            runCatching { catalog.page(query) }
                .onSuccess { page ->
                    mutableState.value = mutableState.value.copy(
                        results = page.media.map { it.toCard() },
                        isLoading = false,
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        error = error.message ?: "The catalogue could not be searched.",
                    )
                }
        }
    }

    /** Empties the search. */
    public fun clear() {
        job?.cancel()
        mutableState.value = mutableState.value.copy(query = "", results = emptyList(), error = null)
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
