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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.SkipSegments
import dev.animeplayer.core.model.StreamVariant
import dev.animeplayer.core.model.SubtitleTrack
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.core.spi.PlaybackSession
import dev.animeplayer.player.media3.Media3PlaybackSession
import dev.animeplayer.tv.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The state of the player.
 */
public sealed interface PlayerState {

    /** The episode is being resolved. */
    public data object Loading : PlayerState

    /** The episode could not be resolved. */
    public data class Failed(val message: String) : PlayerState

    /** The episode is ready. */
    public data class Ready(
        val player: Player,
        val session: PlaybackSession,
        val title: String,
        val episodeLabel: String,
        val description: String?,
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
        val subtitles: List<SubtitleTrack>,
        val activeSubtitle: SubtitleTrack?,
        val variants: List<StreamVariant>,
        val skip: SkipSegments?,
        val resumeAtMs: Long,
        val audioTrack: AudioTrack,
        val quality: String?,
    ) : PlayerState {
        /** `true` when the episode is watched to the end. */
        public val isFinished: Boolean get() = durationMs > 0L && positionMs >= durationMs * 0.9

        /** Starts or pauses the playback. */
        public fun togglePlay() {
            if (player.isPlaying) player.pause() else player.play()
        }

        /** Moves the playback by [milliseconds]. */
        public fun seekBy(milliseconds: Long) {
            val target = (player.currentPosition + milliseconds).coerceAtLeast(0L)
            player.seekTo(target)
        }
    }
}

/**
 * The player of an episode.
 */
public class PlayerViewModel(
    private val container: AppContainer,
    private val catalogId: String,
    private val episode: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow<PlayerState>(PlayerState.Loading)

    private var slug: String? = null
    private var seriesTitle: String = ""
    private var seriesDescription: String? = null
    private var episodeNumber: String = episode

    /** The state of the player. */
    public val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    /**
     * Resolves the episode and prepares the playback.
     */
    public fun load() {
        viewModelScope.launch {
            mutableState.value = PlayerState.Loading
            try {
                val media = container.catalog.media(catalogId)
                seriesTitle = media.title
                seriesDescription = media.description
                val resolved = container.matcher.slug(media)
                    ?: throw IllegalStateException("The library does not know this series.")
                slug = resolved
                val episodes = container.library.episodes(resolved)
                val found = episodes.find(episode)
                    ?: throw IllegalStateException("The library does not have this episode.")
                episodeNumber = found.number

                val history = container.history.entry(resolved, found.number)
                val resumeAt = history?.positionMs ?: 0L

                val source = container.library.resolve(
                    slug = resolved,
                    episode = found,
                    audioTrack = AudioTrack.Sub,
                )

                start(source, media.title, found.number, resumeAt)
            } catch (error: Throwable) {
                mutableState.value = PlayerState.Failed(
                    error.message ?: "The episode could not be resolved.",
                )
            }
        }
    }

    /**
     * Chooses the subtitle track, or turns the subtitles off.
     */
    public fun selectSubtitle(track: SubtitleTrack?) {
        val current = mutableState.value as? PlayerState.Ready ?: return
        viewModelScope.launch {
            current.session.selectSubtitle(track)
            mutableState.value = current.copy(activeSubtitle = track)
        }
    }

    /**
     * Switches to the other audio track.
     *
     * The upstream site serves the dubbed track as a different stream, so the episode has
     * to be resolved again. The position is kept.
     */
    public fun toggleAudio() {
        val current = mutableState.value as? PlayerState.Ready ?: return
        val next = if (current.audioTrack == AudioTrack.Sub) AudioTrack.Dub else AudioTrack.Sub
        viewModelScope.launch {
            val resolved = slug ?: return@launch
            val position = current.player.currentPosition
            runCatching {
                container.library.resolve(
                    slug = resolved,
                    episode = dev.animeplayer.core.model.Episode(
                        animeId = dev.animeplayer.core.model.AnimeId(resolved, resolved),
                        number = episodeNumber,
                        index = 0,
                    ),
                    audioTrack = next,
                )
            }.onSuccess { source ->
                current.session.close()
                start(source, seriesTitle, episodeNumber, position)
            }
        }
    }

    /**
     * Switches to another quality.
     *
     * The upstream site offers every rendition of an episode, so the quality is chosen
     * when the episode is resolved.
     */
    public fun selectQuality(height: Int?) {
        val current = mutableState.value as? PlayerState.Ready ?: return
        viewModelScope.launch {
            val resolved = slug ?: return@launch
            val position = current.player.currentPosition
            runCatching {
                container.library.resolve(
                    slug = resolved,
                    episode = dev.animeplayer.core.model.Episode(
                        animeId = dev.animeplayer.core.model.AnimeId(resolved, resolved),
                        number = episodeNumber,
                        index = 0,
                    ),
                    audioTrack = current.audioTrack,
                    quality = height?.toString(),
                )
            }.onSuccess { source ->
                current.session.close()
                start(source, seriesTitle, episodeNumber, position)
            }
        }
    }

    /**
     * Releases the player.
     */
    public fun release() {
        (mutableState.value as? PlayerState.Ready)?.session?.close()
        mutableState.value = PlayerState.Loading
    }

    /**
     * Prepares the playback of a resolved source.
     */
    private suspend fun start(
        source: PlayableSource,
        title: String,
        number: String,
        resumeAt: Long,
    ) {
        val session = container.player.prepare(
            PlaybackRequest(
                source = source,
                title = "$title · Episode $number",
                resumePositionMs = resumeAt,
            ),
        )
        val media = session as? Media3PlaybackSession ?: return
        media.exoPlayer.playWhenReady = true
        if (resumeAt > 5_000L) {
            media.exoPlayer.seekTo(resumeAt)
        }

        val label = buildString {
            append("Episode $number")
            source.serverName?.let { append(" · ").append(it) }
        }

        val state = PlayerState.Ready(
            player = media.exoPlayer,
            session = session,
            title = title,
            episodeLabel = label,
            description = seriesDescription,
            positionMs = resumeAt,
            durationMs = 0L,
            isPlaying = true,
            subtitles = source.subtitles,
            activeSubtitle = source.defaultSubtitle,
            variants = source.variants,
            skip = source.skip,
            resumeAtMs = resumeAt,
            audioTrack = source.audioTrack,
            quality = source.variant.quality.label,
        )
        mutableState.value = state

        // The position of the video is followed closely, so the clock of the interface
        // does not lag behind. The history is written every five seconds.
        viewModelScope.launch {
            var sinceSave = 0L
            while (true) {
                delay(500)
                val current = mutableState.value as? PlayerState.Ready ?: break
                val position = current.player.currentPosition.coerceAtLeast(0L)
                val duration = current.player.duration.takeIf { it > 0L } ?: 0L
                mutableState.value = current.copy(
                    positionMs = position,
                    durationMs = duration,
                    isPlaying = current.player.isPlaying,
                )
                sinceSave += 500
                if (sinceSave >= 5_000) {
                    sinceSave = 0
                    if (duration > 0L) {
                        container.history.save(
                            slug = slug.orEmpty(),
                            title = title,
                            episode = number,
                            positionMs = position,
                            durationMs = duration,
                        )
                    }
                }
            }
        }
    }

    public companion object {

        /**
         * The factory of the view model.
         */
        public fun factory(
            container: AppContainer,
            catalogId: String,
            episode: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayerViewModel(container, catalogId, episode) as T
        }
    }
}

/**
 * The state, when it is ready.
 */
public fun PlayerState.asReady(): PlayerState.Ready? = this as? PlayerState.Ready
