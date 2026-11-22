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
import dev.animeplayer.core.model.CatalogStatus
import dev.animeplayer.core.model.CatalogRelation
import dev.animeplayer.core.model.Episode
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.AnimeListEntry
import dev.animeplayer.tv.data.ListStatus
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.ui.model.EpisodeRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The state of the detail screen.
 */
public data class DetailState(
    val media: TvState<CatalogMedia> = TvState.Loading,
    val slug: String? = null,
    val episodes: TvState<List<EpisodeRow>> = TvState.Loading,
    val recommendations: TvState<List<CatalogMedia>> = TvState.Loading,
    /** The relations of the series — the sequels, the prequels, the spin-offs. */
    val relations: TvState<List<CatalogRelation>> = TvState.Loading,
    val isFavourite: Boolean = false,
    val resumeEpisode: String? = null,
    val seasons: List<CatalogMedia> = emptyList(),
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
    private val mutableListEntry = MutableStateFlow<AnimeListEntry?>(null)
    internal val listEntry = mutableListEntry.asStateFlow()
    private var playbackProgress: Map<String, Float> = emptyMap()

    init {
        if (catalogId.toLongOrNull() != null) viewModelScope.launch {
            container.favourites.entries.collect { entries ->
                mutableListEntry.value = entries.firstOrNull { it.id == catalogId }
                applyWatchedState()
            }
        }
    }

    /** The state of the screen. */
    public val state: StateFlow<DetailState> = mutableState.asStateFlow()

    /**
     * Loads the series.
     */
    public fun load(force: Boolean = false) {
        if (job != null && !force) return
        job?.cancel()
        job = viewModelScope.launch {
            val isCatalogId = catalogId.toLongOrNull() != null
            val media = attempt(null) {
                if (isCatalogId) {
                    container.catalog.media(catalogId)
                } else {
                    val anime = container.library.anime(catalogId)
                    CatalogMedia(
                        id = catalogId,
                        titles = listOfNotNull(anime.title, anime.japaneseTitle).distinct(),
                        description = anime.description,
                        posterUrl = anime.posterUrl,
                        genres = anime.genres,
                        seasonYear = anime.year,
                        status = CatalogStatus(raw = anime.status.name),
                    )
                }
            }
            if (media == null) {
                mutableState.value = DetailState(media = TvState.Failed("Couldn't load this series."))
                return@launch
            }
            val favourite = mutableListEntry.value != null
            mutableState.value = DetailState(media = TvState.Ready(media), isFavourite = favourite)

            coroutineScope {
                if (isCatalogId) launch {
                    val relations = attempt(emptyList()) { container.catalog.relations(catalogId) }
                    mutableState.value = mutableState.value.copy(relations = TvState.Ready(relations))
                    val seasons = container.catalog.seasons(media, relations)
                    mutableState.value = mutableState.value.copy(seasons = seasons)
                }
                val catalogEpisodes = async {
                    if (isCatalogId) attempt(emptyList()) { container.catalog.episodes(catalogId) } else emptyList()
                }
                val slug = if (isCatalogId) attempt(null) { container.matcher.slug(media) } else catalogId
                val episodes = slug?.let { attempt(null) { container.library.episodes(it).episodes } }
                val watched = if (slug != null) historyFor(slug) else emptyMap()
                playbackProgress = watched
                val actualEpisodes = episodes.orEmpty()
                mutableState.value = mutableState.value.copy(
                    slug = slug.takeIf { actualEpisodes.isNotEmpty() },
                    episodes = if (episodes == null && slug != null) {
                        TvState.Failed("Couldn't load the episodes.")
                    } else {
                        TvState.Ready(applyListProgress(merge(actualEpisodes, emptyList(), watched)))
                    },
                    resumeEpisode = resumeOf(watched.filter { (number, progress) ->
                        (mutableListEntry.value?.mergeProgress(number, progress) ?: progress) in 0.001f..<0.9f
                    }, actualEpisodes),
                )
                val metadata = catalogEpisodes.await()
                if (metadata.isNotEmpty() || mutableState.value.episodes !is TvState.Failed) {
                    mutableState.value = mutableState.value.copy(
                        episodes = TvState.Ready(applyListProgress(merge(actualEpisodes, metadata, watched))),
                    )
                }
            }

            if (isCatalogId) {
                val recommendations = attempt(emptyList()) { container.catalog.recommendations(catalogId) }
                mutableState.value = mutableState.value.copy(recommendations = TvState.Ready(recommendations))
            }
        }
    }

    internal fun refreshHistory() {
        val slug = state.value.slug ?: return
        viewModelScope.launch {
            playbackProgress = historyFor(slug)
            applyWatchedState()
        }
    }

    private fun applyListProgress(rows: List<EpisodeRow>): List<EpisodeRow> = rows.map { row ->
        val playback = playbackProgress[row.number] ?: 0f
        row.copy(watched = mutableListEntry.value?.mergeProgress(row.number, playback) ?: playback)
    }

    private fun applyWatchedState() {
        val current = state.value
        val rows = current.episodes.valueOrNull()?.let(::applyListProgress)
        mutableState.value = current.copy(
            isFavourite = mutableListEntry.value != null,
            episodes = rows?.let { TvState.Ready(it) } ?: current.episodes,
            resumeEpisode = rows?.firstOrNull { it.number == current.resumeEpisode && it.watched in 0.001f..<0.9f }?.number
                ?: playbackProgress.keys.firstOrNull { number ->
                    rows?.any { it.number == number && it.watched in 0.001f..<0.9f } == true
                },
        )
    }

    internal fun editList(change: (AnimeListEntry?) -> AnimeListEntry?) {
        if (catalogId.toLongOrNull() == null) return
        viewModelScope.launch {
            attempt(Unit) {
                val before = mutableListEntry.value
                val updated = container.favourites.edit(catalogId, change)
                val slug = state.value.slug
                if (slug != null && updated != null && updated.progress > (before?.progress ?: 0)) {
                    container.history.inProgress().filter {
                        it.animeId.slug == slug && updated.watched(it.episodeNumber) == true
                    }.forEach { container.history.forget(slug, it.episodeNumber) }
                    playbackProgress = historyFor(slug)
                    applyWatchedState()
                }
            }
        }
    }

    internal fun markEpisode(number: String, watched: Boolean) {
        val slug = state.value.slug
        viewModelScope.launch {
            attempt(Unit) {
                container.favourites.edit(catalogId) {
                    (it ?: AnimeListEntry(catalogId, ListStatus.Watching)).mark(number, watched)
                }
                if (slug != null) container.history.forget(slug, number)
                if (slug != null) playbackProgress = historyFor(slug)
                applyWatchedState()
            }
        }
    }

    private suspend fun historyFor(slug: String): Map<String, Float> =
        attempt(emptyList()) { container.history.entries() }
            .filter { it.animeId.slug == slug }
            .associate { it.episodeNumber to it.progress }

    private fun resumeOf(watched: Map<String, Float>, episodes: List<Episode>): String? =
        watched.keys.firstOrNull { number ->
            watched.getValue(number) in 0.001f..<0.9f && episodes.any { it.number == number }
        }

    private suspend inline fun <T> attempt(fallback: T, work: () -> T): T = try {
        work()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }

    private fun merge(
        library: List<Episode>,
        catalog: List<CatalogEpisode>,
        progress: Map<String, Float>,
    ): List<EpisodeRow> {
        if (library.isEmpty()) {
            return catalog.distinctBy { it.number }.map {
                EpisodeRow(
                    number = it.number.toString(),
                    title = it.title?.takeUnless { title -> title == it.number.toString() } ?: "Episode ${it.number}",
                    description = it.description,
                    thumbnail = it.thumbnailUrl,
                    duration = it.lengthMinutes?.times(60),
                    score = it.score,
                    airDate = it.airDate,
                )
            }
        }
        val byNumber = catalog.associateBy { it.number.toString() }
        return library.distinctBy { it.number }.map { episode ->
            val meta = byNumber[episode.number]
            EpisodeRow(
                number = episode.number,
                title = (meta?.title ?: episode.title)?.takeUnless { it == episode.number } ?: "Episode ${episode.number}",
                description = meta?.description,
                thumbnail = meta?.thumbnailUrl,
                duration = meta?.lengthMinutes?.times(60) ?: episode.durationSeconds,
                score = meta?.score,
                watched = progress[episode.number] ?: 0f,
                airDate = meta?.airDate,
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
