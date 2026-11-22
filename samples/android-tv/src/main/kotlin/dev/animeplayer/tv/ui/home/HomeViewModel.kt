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

import android.content.Context
import dev.animeplayer.tv.data.groupSeasons
import android.os.SystemClock
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
import dev.animeplayer.tv.data.TmdbCatalog
import dev.animeplayer.tv.data.WatchHistoryRepository
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.ContinueWatchingItem
import dev.animeplayer.tv.ui.model.toCard
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    internal val featuredSlides: List<CatalogCard>? = null,
    val loaded: Map<String, TvState<List<CatalogMedia>>> = emptyMap(),
    val continueWatching: TvState<List<ContinueWatchingItem>> = TvState.Loading,
    val continueArtworkReady: Boolean = false,
    val myList: TvState<List<CatalogMedia>> = TvState.Loading,
    val recommendations: TvState<List<CatalogMedia>> = TvState.Loading,
    val favouriteIds: Set<String> = emptySet(),
    internal val restoredSnapshot: Boolean = false,
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
public class HomeViewModel private constructor(
    private val catalog: CatalogRepository,
    private val historyRepository: WatchHistoryRepository,
    private val favouritesRepository: FavouritesRepository,
    private val snapshotStore: HomeSnapshotStore?,
    private val tmdb: TmdbCatalog?,
) : ViewModel() {
    public constructor(
        catalog: CatalogRepository,
        historyRepository: WatchHistoryRepository,
        favouritesRepository: FavouritesRepository,
    ) : this(catalog, historyRepository, favouritesRepository, null, null)

    private val rails: List<HomeRail> = buildRails()
    private val featuredRail = rails.first()
    private val seasonalRail = rails[1]
    private val mutableState = MutableStateFlow(HomeState(rails = rails))
    private val catalogRequests = Mutex()
    private val requestedRails = mutableSetOf<String>()
    internal var carouselUpdatedAt: Long = 0L
        private set
    private var trendingPool = emptyList<CatalogMedia>()
    private var featuredJob: Job? = null
    private var personalJob: Job? = null
    private var snapshotJob: Job? = null
    private val snapshotWrites = Mutex()
    private var restored = false
    private val snapshotRows = mutableMapOf<String, List<CatalogMedia>>()
    private var snapshotSlides = emptyList<FeaturedSnapshot>()
    private val missingEpisodeArtwork = mutableSetOf<Pair<String, String>>()

    public val state: StateFlow<HomeState> = mutableState.asStateFlow()

    internal suspend fun restore(context: Context, logoWidth: Int, logoHeight: Int) {
        if (restored) return
        val snapshot = snapshotStore?.read()
        if (snapshot != null) {
            val rows = snapshot.rows.mapValues { (_, entries) -> entries.map(HomeMedia::toMedia) }
            tmdb?.primeHomeArtwork(rows.values.flatMap { row -> row.take(6).map { it.id } }.distinct())
            val slides = restoreHomeCarousel(snapshot.featured, context, logoWidth, logoHeight)
            snapshotRows.putAll(rows)
            snapshotSlides = snapshot.featured
            mutableState.value = mutableState.value.copy(
                loaded = rows.mapValues { (_, entries) -> TvState.Ready(entries) },
                featuredSlides = slides.ifEmpty { null },
                restoredSnapshot = rows.values.any { it.isNotEmpty() } || slides.isNotEmpty(),
            )
            if (slides.isNotEmpty()) carouselUpdatedAt = SystemClock.elapsedRealtime()
        }
        restored = true
    }

    public fun load(force: Boolean = false) {
        if (featuredJob != null && !force) return
        featuredJob?.cancel()
        featuredJob = viewModelScope.launch {
            if (force) {
                requestedRails.removeAll { mutableState.value.loaded[it] is TvState.Failed }
                mutableState.value = mutableState.value.copy(
                    hero = TvState.Loading,
                    featuredSlides = null,
                    restoredSnapshot = false,
                    loaded = mutableState.value.loaded.filterValues { it !is TvState.Failed },
                )
            }
            try {
                val page = catalogRequests.withLock { catalog.page(featuredRail.query) }
                val grouped = page.media.groupSeasons()
                trendingPool = grouped
                setRail(featuredRail.title, TvState.Ready(grouped))
                publishFeatured()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val failure = TvState.Failed("Couldn't load anime. Try again.")
                setRail(featuredRail.title, failure)
                trendingPool = emptyList()
                publishFeatured()
            }
        }
        loadRail(seasonalRail)
    }

    internal fun loadRail(rail: HomeRail) {
        if (rail == featuredRail || !requestedRails.add(rail.title)) return
        viewModelScope.launch {
            featuredJob?.join()
            try {
                val page = catalogRequests.withLock { catalog.page(rail.query) }
                setRail(rail.title, TvState.Ready(page.media))
                if (rail == seasonalRail) publishFeatured()
            } catch (cancelled: CancellationException) {
                requestedRails.remove(rail.title)
                throw cancelled
            } catch (_: Exception) {
                setRail(rail.title, TvState.Failed("Couldn't load this row"))
                if (rail == seasonalRail) publishFeatured()
            }
        }
    }

    private fun publishFeatured() {
        val current = mutableState.value
        val seasonal = current.rail(seasonalRail.title)
        if (seasonal.isLoading) return
        val lineup = interleaveFeatured(trendingPool, snapshotRows[seasonalRail.title] ?: seasonal.valueOrNull().orEmpty())
        mutableState.value = current.copy(
            hero = if (lineup.isEmpty()) TvState.Failed("Couldn’t load featured titles.") else TvState.Ready(lineup),
        )
    }

    internal fun publishCarousel(slides: List<CatalogCard>) {
        carouselUpdatedAt = SystemClock.elapsedRealtime()
        mutableState.value = mutableState.value.copy(featuredSlides = slides)
        val persistent = slides.take(4).mapNotNull { card ->
            val media = card.catalogMedia ?: return@mapNotNull null
            val logo = card.featuredLogoUrl ?: return@mapNotNull null
            FeaturedSnapshot(HomeMedia(media.copy(posterUrl = card.posterUrl, bannerUrl = card.bannerUrl)), logo)
        }
        if (persistent.isNotEmpty()) {
            snapshotSlides = persistent
            saveSnapshot()
        }
    }

    internal suspend fun refreshFeaturedCandidates(): List<CatalogMedia> {
        val trending = recoverOr(trendingPool) {
            catalogRequests.withLock { catalog.page(featuredRail.query).media }
        }
        val seasonal = recoverOr(snapshotRows[seasonalRail.title].orEmpty()) {
            catalogRequests.withLock { catalog.page(seasonalRail.query).media }
        }
        val topRated = state.value.rail(rails[2].title).valueOrNull().orEmpty()
        return interleaveFeatured(trending, seasonal, topRated)
    }

    private fun interleaveFeatured(vararg sources: List<CatalogMedia>): List<CatalogMedia> {
        // Shuffle within rank bands so each source contributes picks without
        // burying the strongest titles at the end of the candidate list.
        val ranked = sources.map { source -> source.groupSeasons().chunked(6).flatMap { it.shuffled() } }
        return buildList {
            repeat(ranked.maxOfOrNull { it.size } ?: 0) { index ->
                ranked.forEach { it.getOrNull(index)?.let(::add) }
            }
        }.groupSeasons()
    }

    private fun setRail(title: String, content: TvState<List<CatalogMedia>>) {
        val current = mutableState.value
        if (title == featuredRail.title || title == seasonalRail.title) {
            content.valueOrNull()?.takeIf { it.isNotEmpty() }?.let { entries ->
                snapshotRows[title] = entries
                saveSnapshot()
            }
            // Keep restored rows stable for this Home session, including during a failed refresh.
            if (current.restoredSnapshot && current.rail(title) is TvState.Ready) return
        }
        mutableState.value = current.copy(loaded = current.loaded + (title to content))
    }

    private fun saveSnapshot() {
        val store = snapshotStore ?: return
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            delay(300)
            val rows = snapshotRows.toMap()
            val slides = snapshotSlides
            snapshotWrites.withLock { recoverOr(Unit) { store.write(rows, slides) } }
        }
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
                val seriesSlug = entry.animeId.slug
                val episode = entry.episodeNumber
                val storedStill = recoverOr(null) { historyRepository.episodeArtwork(seriesSlug, episode) }
                ContinueWatchingItem(
                    card = known[entry.animeId.slug]?.card ?: CatalogCard(
                        id = entry.animeId.slug,
                        title = entry.title,
                        posterColor = Color.hsl(hue, 0.38f, 0.22f),
                    ),
                    episodeNumber = entry.episodeNumber,
                    positionMs = entry.positionMs,
                    durationMs = entry.durationMs,
                    seriesSlug = seriesSlug,
                    thumbnailUrl = storedStill,
                    episodeArtworkPending = storedStill == null && episode.toIntOrNull() != null &&
                        (seriesSlug to episode) !in missingEpisodeArtwork,
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
            var items = localItems.map { item ->
                artwork[item.card.title]?.let { item.copy(card = it.toCard()) } ?: item
            }
            mutableState.value = mutableState.value.copy(
                continueWatching = TvState.Ready(items),
                continueArtworkReady = true,
            )

            for (batch in items.filter { it.episodeArtworkPending }.chunked(2)) {
                val enriched = coroutineScope {
                    batch.map { item -> async { loadEpisodeArtwork(item) } }.awaitAll()
                }.associateBy { it.seriesSlug }
                items = items.map { enriched[it.seriesSlug] ?: it }
                mutableState.value = mutableState.value.copy(continueWatching = TvState.Ready(items))
            }
            val savedIds = mutableState.value.favouriteIds
            val saved = loadMyList(savedIds)
            if (savedIds == mutableState.value.favouriteIds) {
                mutableState.value = mutableState.value.copy(myList = TvState.Ready(saved))
            }
            val recommendations = loadRecommendations(items)
            mutableState.value = mutableState.value.copy(recommendations = TvState.Ready(recommendations))
        }
    }

    private suspend fun loadEpisodeArtwork(item: ContinueWatchingItem): ContinueWatchingItem {
        val catalogId = item.card.catalogMedia?.id
        val metadata = catalogId?.let { recoverOr(null) { catalog.episodes(it) } }
        val thumbnail = metadata?.firstOrNull { it.number.toString() == item.episodeNumber }?.thumbnailUrl
        if (thumbnail != null) {
            recoverOr(Unit) { historyRepository.rememberEpisodeArtwork(item.seriesSlug, item.episodeNumber, thumbnail) }
        } else if (metadata != null) {
            missingEpisodeArtwork += item.seriesSlug to item.episodeNumber
        }
        return item.copy(thumbnailUrl = thumbnail, episodeArtworkPending = false)
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
                    HomeViewModel(container.catalog, container.history, container.favourites, container.homeSnapshot, container.tmdb) as T
            }
    }
}
