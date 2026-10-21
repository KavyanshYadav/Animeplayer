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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.animeplayer.core.model.CatalogEpisode
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.Episode
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.TvState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One episode of a series, as the detail screen shows it.
 *
 * @property number the number of the episode, as the upstream site spells it.
 * @property title the title of the episode.
 * @property description the description of the episode.
 * @property thumbnail the artwork of the episode.
 * @property duration the length of the episode, in seconds.
 * @property score the score of the episode.
 * @property watched how far the episode was watched, in the `0..1` range.
 */
public data class EpisodeRow(
    val number: String,
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,
    val score: Int? = null,
    val watched: Float = 0f,
) {
    /** `true` when the episode was watched to the end. */
    public val isWatched: Boolean get() = watched >= 0.9f
}

/**
 * The state of the detail screen.
 */
public data class DetailState(
    val media: TvState<CatalogMedia> = TvState.Loading,
    val slug: String? = null,
    val episodes: TvState<List<EpisodeRow>> = TvState.Loading,
    val recommendations: TvState<List<CatalogMedia>> = TvState.Loading,
    val isFavourite: Boolean = false,
)

/**
 * The details of a series.
 *
 * The screen joins the two sites: the catalogue knows the artwork and the description of
 * the series, the library knows the episodes. The join is the title match, so it costs a
 * request to the library the first time the series is opened.
 */
public class DetailViewModel(
    private val container: AppContainer,
    private val catalogId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailState())
    private var job: Job? = null

    /** The state of the screen. */
    public val state: StateFlow<DetailState> = mutableState.asStateFlow()

    /**
     * Loads the series.
     */
    public fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            // The catalogue first — it is the fastest of the two.
            val media = runCatching { container.catalog.media(catalogId) }
            media.onSuccess { entry ->
                mutableState.value = mutableState.value.copy(media = TvState.Ready(entry))
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    media = TvState.Failed(error.message ?: "The series could not be loaded."),
                )
                return@launch
            }
            val entry = media.getOrThrow()

            mutableState.value = mutableState.value.copy(
                isFavourite = container.favourites.isFavourite(catalogId),
            )

            // The episodes of the catalogue — the artwork and the description.
            val catalogEpisodes = runCatching { container.catalog.episodes(catalogId) }
                .getOrDefault(emptyList())

            // The episodes of the library — the ones that can actually be played.
            val slug = runCatching { container.matcher.slug(entry) }.getOrNull()
            mutableState.value = mutableState.value.copy(slug = slug)

            if (slug == null) {
                mutableState.value = mutableState.value.copy(
                    episodes = TvState.Ready(
                        catalogEpisodes.map {
                            EpisodeRow(
                                number = it.number.toString(),
                                title = it.title ?: "Episode ${it.number}",
                                description = it.description,
                                thumbnail = it.thumbnailUrl,
                                score = it.score,
                            )
                        },
                    ),
                )
            } else {
                val libraryEpisodes = runCatching { container.library.episodes(slug) }
                    .getOrNull()
                val history = runCatching { container.history.entries() }.getOrDefault(emptyList())
                val progress = history
                    .filter { it.animeId.slug == slug }
                    .associate { it.episodeNumber to it.progress }

                mutableState.value = mutableState.value.copy(
                    episodes = TvState.Ready(
                        merge(libraryEpisodes?.episodes.orEmpty(), catalogEpisodes, progress),
                    ),
                )
            }

            // The recommendations are a nice-to-have; they never block the screen.
            val recommendations = runCatching { container.catalog.recommendations(catalogId) }
            mutableState.value = mutableState.value.copy(
                recommendations = recommendations.fold(
                    onSuccess = { TvState.Ready(it) },
                    onFailure = { TvState.Ready(emptyList()) },
                ),
            )
        }
    }

    /**
     * Adds the series to the favourites, or removes it.
     */
    public fun toggleFavourite() {
        viewModelScope.launch {
            val favourite = container.favourites.toggle(catalogId)
            mutableState.value = mutableState.value.copy(isFavourite = favourite)
        }
    }

    private fun merge(
        library: List<Episode>,
        catalog: List<CatalogEpisode>,
        progress: Map<String, Float>,
    ): List<EpisodeRow> {
        if (library.isEmpty()) {
            return catalog.map {
                EpisodeRow(
                    number = it.number.toString(),
                    title = it.title ?: "Episode ${it.number}",
                    description = it.description,
                    thumbnail = it.thumbnailUrl,
                    score = it.score,
                )
            }
        }
        val byNumber = catalog.associateBy { it.number.toString() }
        return library.map { episode ->
            val meta = byNumber[episode.number]
            EpisodeRow(
                number = episode.number,
                title = meta?.title ?: episode.title ?: "Episode ${episode.number}",
                description = meta?.description,
                thumbnail = meta?.thumbnailUrl,
                duration = episode.durationSeconds,
                score = meta?.score,
                watched = progress[episode.number] ?: 0f,
            )
        }
    }

    public companion object {

        /**
         * The factory of the view model.
         */
        public fun factory(container: AppContainer, catalogId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DetailViewModel(container, catalogId) as T
            }
    }
}
