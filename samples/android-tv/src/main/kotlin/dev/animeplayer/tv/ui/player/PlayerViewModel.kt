@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

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
package dev.animeplayer.tv.ui.player

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import dev.animeplayer.core.error.AnimeException
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.CatalogEpisode
import dev.animeplayer.core.model.CatalogQuery
import dev.animeplayer.core.model.Episode as LibraryEpisode
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.SubtitleTrack
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.player.media3.Media3PlaybackSession
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.AnimeListEntry
import dev.animeplayer.tv.data.ListStatus
import dev.animeplayer.tv.TvSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Owns one decoder for the lifetime of the active playback screen. */
public class PlayerViewModel(
    private val container: AppContainer,
    private val catalogId: String,
    private val episode: String,
    seriesSlug: String? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TvPlayerState())
    private var session: Media3PlaybackSession? = null
    private var operation: Job? = null
    private var progressUpdates: Job? = null
    private var episodeMetadataJob: Job? = null
    private var episodeMetadata = emptyMap<String, CatalogEpisode>()
    private var slug: String? = seriesSlug
    private var libraryEpisodes: List<LibraryEpisode> = emptyList()
    private var settings = TvSettings()
    private var selectedHeight: Int? = null
    private var lastSavedPosition = -1L
    private var recordedCompletion = false
    private var historyWrite: Job? = null
    private var positionOnStop: Long? = null
    private var introSkipped = false
    private var warmup: Job? = null
    private var warmedEpisode: WarmedEpisode? = null
    private var warmupAttempted = false
    private var startingWarmedEpisode = false

    private data class WarmedEpisode(
        val number: String,
        val audio: AudioTrack,
        val height: Int?,
        val source: PlayableSource,
        val resolvedAt: Long = SystemClock.elapsedRealtime(),
    )

    public val state: StateFlow<TvPlayerState> = mutableState.asStateFlow()
    public val player: Player? get() = session?.exoPlayer

    private val listener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) {
            mutableState.value = mutableState.value.copy(cues = cueGroup.cues)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (session?.exoPlayer !== player || mutableState.value.phase != TvPlayerState.Phase.Ready) return
            updatePosition()
            if (player.playbackState == Player.STATE_BUFFERING || !player.playWhenReady) warmup?.cancel()
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_ENDED
            ) {
                saveProgress(completed = true)
                if (settings.autoplayNext) playNextEpisode()
            }
        }

        override fun onRenderedFirstFrame() {
            startingWarmedEpisode = false
        }

        override fun onPlayerError(error: PlaybackException) {
            if (startingWarmedEpisode && operation?.isActive != true) {
                startingWarmedEpisode = false
                changeStream(state.value.currentEpisode, state.value.audioTrack, selectedHeight, resumeCurrent = true,
                    retryingWarmedEpisode = true)
                return
            }
            warmup?.cancel()
            saveProgress()
            val code = error.errorCodeName
            mutableState.value = mutableState.value.copy(
                phase = TvPlayerState.Phase.Failed,
                isPlaying = false,
                error = "Playback stopped ($code). Try again or choose another episode.",
            )
        }
    }

    public fun load() {
        if (operation?.isActive == true || session != null && state.value.phase == TvPlayerState.Phase.Ready) return
        val previous = state.value
        val number = previous.currentEpisode.ifBlank { episode }
        val retryPosition = positionOnStop ?: previous.positionMs.takeIf { previous.currentEpisode == number && it > 0 }
        val preferredTrack = previous.audioTrack.takeIf { previous.currentEpisode.isNotEmpty() }
        operation = viewModelScope.launch {
            mutableState.value = previous.copy(phase = TvPlayerState.Phase.Loading, error = null, notice = null)
            attempt(onFailure = ::showFailure) {
                settings = container.settings.settings.first()
                val media = if (catalogId.toLongOrNull() != null) {
                    safely(null) { container.catalog.media(catalogId) }
                } else {
                    null
                }
                val directSlug = catalogId.takeUnless { catalogId.toLongOrNull() != null || it.isBlank() }
                val resolved = slug ?: media?.let { container.matcher.slug(it) } ?: directSlug
                    ?: error("This series isn't available for playback.")
                val historyEntry = safely(null) { container.history.entry(resolved, number) }
                val title = media?.title ?: historyEntry?.title ?: container.library.anime(resolved).title
                slug = resolved
                libraryEpisodes = container.library.episodes(resolved).episodes.distinctBy { it.number }
                val found = libraryEpisodes.firstOrNull { it.number == number }
                    ?: error("This episode isn't available for playback.")
                val watched = safely(emptyList()) { container.history.entries() }
                    .filter { it.animeId.slug == resolved }
                    .associate { it.episodeNumber to it.progress }
                val position = retryPosition ?: historyEntry?.positionMs
                    ?.takeUnless { historyEntry.progress >= 0.9f } ?: 0L

                val track = preferredTrack ?: if (settings.dubbed) AudioTrack.Dub else AudioTrack.Sub
                if (preferredTrack == null) selectedHeight = settings.quality?.toIntOrNull()?.takeIf { it > 0 }
                val source = try {
                    resolve(found, track, selectedHeight)
                } catch (unavailable: AnimeException.QualityNotAvailableError) {
                    if (selectedHeight == null) throw unavailable
                    selectedHeight = null
                    resolve(found, track, null)
                }
                startPlayback(
                    source = source,
                    number = found.number,
                    position = position,
                    panelEpisodes = libraryEpisodes.map {
                        Episode(
                            number = it.number,
                            title = it.title?.takeUnless { title -> title == it.number } ?: "Episode ${it.number}",
                            durationSeconds = it.durationSeconds,
                            watched = watched[it.number] ?: 0f,
                        )
                    },
                    title = title,
                    description = media?.description,
                )
                positionOnStop = null
            }
        }
    }

    private suspend fun resolve(episode: LibraryEpisode, track: AudioTrack, height: Int?): PlayableSource =
        container.library.resolve(requireNotNull(slug), episode, track, height?.toString())

    private suspend fun startPlayback(
        source: PlayableSource,
        number: String,
        position: Long,
        panelEpisodes: List<Episode>,
        title: String,
        description: String?,
        fromWarmup: Boolean = false,
    ) {
        val heightLimit = selectedHeight ?: if (container.isMemoryConstrained) 720 else 1080
        val master = source.masterUrl?.takeIf { selectedHeight == null }
        val stream = when {
            master != null -> source.copy(variant = source.variant.copy(url = master))
            selectedHeight == null -> {
                val suitable = source.variants.filter { it.quality.height in 1..heightLimit }
                    .maxByOrNull { it.quality.height }
                source.copy(variant = suitable ?: source.variant)
            }
            else -> source
        }

        // Older TV chipsets often have a single usable decoder. Release it before preparing a replacement.
        closeSession()
        val prepared = container.player.prepare(
            request = PlaybackRequest(stream, "$title · Episode $number", position),
            maxVideoHeight = heightLimit,
            enableDecoderFallback = true,
        )
        session = prepared
        startingWarmedEpisode = fromWarmup
        val speed = state.value.playbackSpeed
        prepared.exoPlayer.setPlaybackSpeed(speed)
        val index = libraryEpisodes.indexOfFirst { it.number == number }
        val nextEpisode = libraryEpisodes.getOrNull(index + 1)?.takeIf { index >= 0 }?.number
        lastSavedPosition = -1L
        recordedCompletion = false
        introSkipped = source.skip?.intro?.let { position >= it.endSeconds * 1000L } ?: false
        mutableState.value = TvPlayerState(
            phase = TvPlayerState.Phase.Ready,
            title = title,
            episodeLabel = "Episode $number",
            description = description,
            currentEpisode = number,
            positionMs = position,
            buffering = true,
            subtitles = source.subtitles,
            activeSubtitle = source.defaultSubtitle,
            variants = source.variants,
            activeQuality = selectedHeight?.let { source.variant.quality.label },
            audioTrack = source.audioTrack,
            skip = source.skip,
            episodes = enrichEpisodes(panelEpisodes),
            nextEpisode = nextEpisode,
            playbackSpeed = speed,
        )
        prepared.exoPlayer.addListener(listener)
        prepared.exoPlayer.playWhenReady = true
        followPlayback()
        loadEpisodeMetadata()
    }

    internal fun loadEpisodeMetadata() {
        if (episodeMetadataJob?.isActive == true || episodeMetadata.isNotEmpty()) return
        val title = state.value.title.takeIf { it.isNotBlank() } ?: return
        episodeMetadataJob = viewModelScope.launch {
            val id = safely(null) {
                catalogId.takeIf { it.toLongOrNull() != null }
                    ?: container.catalog.page(CatalogQuery(search = title, perPage = 5)).media
                        .singleOrNull { entry -> entry.titles.any { it.equals(title, ignoreCase = true) } }?.id
            } ?: return@launch
            episodeMetadata = safely(emptyList()) { container.catalog.episodes(id) }.associateBy { it.number.toString() }
            val current = state.value
            if (episodeMetadata.isNotEmpty()) {
                mutableState.value = current.copy(episodes = enrichEpisodes(current.episodes))
                val seriesSlug = slug
                val still = episodeMetadata[current.currentEpisode]?.thumbnailUrl
                if (seriesSlug != null && still != null) {
                    safely(Unit) { container.history.rememberEpisodeArtwork(seriesSlug, current.currentEpisode, still) }
                }
            }
        }
    }

    private fun enrichEpisodes(episodes: List<Episode>): List<Episode> {
        if (episodeMetadata.isEmpty()) return episodes
        return episodes.map { episode ->
            val extra = episodeMetadata[episode.number] ?: return@map episode
            episode.copy(
                title = extra.title ?: episode.title,
                durationSeconds = extra.lengthMinutes?.times(60) ?: episode.durationSeconds,
                thumbnailUrl = extra.thumbnailUrl ?: episode.thumbnailUrl,
                description = extra.description ?: episode.description,
            )
        }
    }

    private fun followPlayback() {
        progressUpdates = viewModelScope.launch {
            var ticks = 0
            while (session != null) {
                delay(650)
                val player = session?.exoPlayer ?: break
                if (state.value.phase != TvPlayerState.Phase.Ready) break
                if (player.isPlaying) {
                    updatePosition()
                    if (!introSkipped && settings.skipIntro && state.value.canSkipIntro) skipIntro()
                    warmNextEpisode(player)
                }
                ticks++
                if (ticks % 8 == 0) saveProgress()
            }
        }
    }

    private fun warmNextEpisode(player: Player) {
        if (warmupAttempted || operation?.isActive == true || player.playbackState != Player.STATE_READY) return
        val remaining = player.duration - player.currentPosition
        if (player.duration <= 0 || remaining !in 2_000L..60_000L) return
        if (player.totalBufferedDuration < minOf(8_000L, remaining - 500)) return
        val current = state.value
        val next = libraryEpisodes.firstOrNull { it.number == current.nextEpisode } ?: return
        val height = selectedHeight
        warmupAttempted = true
        warmup = viewModelScope.launch {
            // Resolving already fetches the HLS master and parses the available tracks.
            // This warms the source without allocating a second decoder or loading segments.
            val source = withTimeoutOrNull(12_000) { safely(null) { resolve(next, current.audioTrack, height) } }
            if (source != null) warmedEpisode = WarmedEpisode(next.number, current.audioTrack, height, source)
        }
    }

    private fun updatePosition() {
        val player = session?.exoPlayer ?: return
        mutableState.value = state.value.copy(
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            bufferedMs = player.bufferedPosition.coerceAtLeast(0),
            isPlaying = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
        )
    }

    private fun saveProgress(completed: Boolean = false) {
        val player = session?.exoPlayer ?: return
        val current = state.value
        val seriesSlug = slug ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        val position = if (completed) duration else player.currentPosition.coerceIn(0L, duration)
        if (position == lastSavedPosition || current.currentEpisode.isBlank()) return
        lastSavedPosition = position
        val thumbnailUrl = episodeMetadata[current.currentEpisode]?.thumbnailUrl
        val shouldMark = catalogId.toLongOrNull() != null && !recordedCompletion && position.toDouble() / duration >= 0.9
        if (shouldMark) recordedCompletion = true
        val previous = historyWrite
        historyWrite = container.historyWrites.launch {
            previous?.join()
            safely(Unit) {
                container.history.save(seriesSlug, current.title, current.currentEpisode, position, duration, thumbnailUrl)
                if (shouldMark) container.favourites.edit(catalogId) {
                    val entry = it ?: AnimeListEntry(catalogId, ListStatus.Watching)
                    entry.mark(current.currentEpisode, true).let { marked ->
                        if (entry.status == ListStatus.Planned) marked.copy(status = ListStatus.Watching) else marked
                    }
                }
            }
        }
    }

    public fun togglePlay() {
        val player = session?.exoPlayer ?: return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
            saveProgress()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    public fun seekBy(milliseconds: Long) {
        val player = session?.exoPlayer ?: return
        seekTo(player.currentPosition + milliseconds)
    }

    public fun seekTo(positionMs: Long) {
        val player = session?.exoPlayer ?: return
        val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = positionMs.coerceIn(0, end)
        player.seekTo(target)
        mutableState.value = state.value.copy(positionMs = target)
    }

    public fun skipIntro() {
        val intro = state.value.skip?.intro ?: return
        introSkipped = true
        seekTo(intro.endSeconds * 1000L)
    }

    public fun selectSubtitle(track: SubtitleTrack?) {
        val currentSession = session ?: return
        viewModelScope.launch {
            attempt(onFailure = { showNotice("Couldn't change subtitles.") }) {
                currentSession.selectSubtitle(track)
                if (session === currentSession) {
                    mutableState.value = state.value.copy(activeSubtitle = track, cues = emptyList())
                }
            }
        }
    }

    public fun playEpisode(number: String) {
        if (number != state.value.currentEpisode) {
            changeStream(number, state.value.audioTrack, selectedHeight, resumeCurrent = false)
        }
    }

    internal fun playNextEpisode() {
        state.value.nextEpisode?.let(::playEpisode)
    }

    internal fun setPlaybackSpeed(speed: Float) {
        val player = session?.exoPlayer ?: return
        player.setPlaybackSpeed(speed)
        mutableState.value = state.value.copy(playbackSpeed = speed)
    }

    public fun toggleAudio() {
        val current = state.value
        val next = if (current.audioTrack == AudioTrack.Sub) AudioTrack.Dub else AudioTrack.Sub
        changeStream(current.currentEpisode, next, selectedHeight, resumeCurrent = true)
    }

    public fun selectQuality(height: Int?) {
        if (height != selectedHeight) {
            changeStream(state.value.currentEpisode, state.value.audioTrack, height, resumeCurrent = true)
        }
    }

    private fun changeStream(
        number: String,
        audio: AudioTrack,
        height: Int?,
        resumeCurrent: Boolean,
        retryingWarmedEpisode: Boolean = false,
    ) {
        val found = libraryEpisodes.firstOrNull { it.number == number } ?: return
        if (operation?.isActive == true || session == null) return
        val warmed = warmedEpisode?.takeIf {
            !resumeCurrent && it.number == number && it.audio == audio && it.height == height &&
                SystemClock.elapsedRealtime() - it.resolvedAt < 90_000
        }
        warmup?.cancel()
        warmedEpisode = null
        operation = viewModelScope.launch {
            showNotice("Switching stream…")
            attempt(onFailure = {
                if (session == null || retryingWarmedEpisode) showFailure(it)
                else showNotice("That stream is unavailable. Still on the current stream.")
            }) {
                val source = warmed?.source ?: resolve(found, audio, height)
                val current = state.value
                val position = if (resumeCurrent) session?.exoPlayer?.currentPosition ?: current.positionMs else 0
                saveProgress()
                selectedHeight = height
                startPlayback(source, number, position, current.episodes, current.title, current.description,
                    fromWarmup = warmed != null)
            }
        }
    }

    internal fun clearNotice() {
        mutableState.value = state.value.copy(notice = null)
    }

    private fun showNotice(message: String) {
        mutableState.value = state.value.copy(notice = message)
    }

    private fun showFailure(error: Exception) {
        mutableState.value = state.value.copy(
            phase = TvPlayerState.Phase.Failed,
            isPlaying = false,
            error = if (error is IllegalStateException && session == null) {
                error.message ?: "Couldn't start playback."
            } else {
                "Couldn't start playback. Check your connection and try again."
            },
        )
        closeSession()
    }

    public fun release() {
        operation?.cancel()
        operation = null
        episodeMetadataJob?.cancel()
        episodeMetadataJob = null
        if (session != null) {
            positionOnStop = session?.exoPlayer?.currentPosition?.coerceAtLeast(0)
            saveProgress()
        }
        closeSession()
    }

    private fun closeSession() {
        progressUpdates?.cancel()
        warmup?.cancel()
        warmup = null
        warmedEpisode = null
        warmupAttempted = false
        startingWarmedEpisode = false
        session?.exoPlayer?.removeListener(listener)
        session?.close()
        session = null
    }

    override fun onCleared() {
        release()
    }

    private suspend inline fun attempt(onFailure: (Exception) -> Unit, action: () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(error)
        }
    }

    private suspend inline fun <T> safely(fallback: T, action: () -> T): T = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }

    public companion object {
        public fun factory(
            container: AppContainer,
            catalogId: String,
            episode: String,
            seriesSlug: String? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayerViewModel(container, catalogId, episode, seriesSlug) as T
        }
    }
}
