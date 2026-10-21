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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.SubtitleTrack
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.player.media3.Media3PlaybackSession
import dev.animeplayer.tv.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The player of an episode.
 *
 * The state is the truth, and the player is the only thing that changes it. The position
 * is followed closely, so the clock of the interface does not lag behind the picture, and
 * the history is written every five seconds.
 */
public class PlayerViewModel(
    private val container: AppContainer,
    private val catalogId: String,
    private val episode: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TvPlayerState())
    private var session: Media3PlaybackSession? = null
    private var slug: String? = null

    /** The state of the player. */
    public val state: StateFlow<TvPlayerState> = mutableState.asStateFlow()

    /**
     * The player behind the session, for the clients that draw the video themselves.
     */
    public val player: Player? get() = session?.exoPlayer

    /**
     * Resolves the episode and prepares the playback.
     */
    public fun load() {
        viewModelScope.launch {
            mutableState.value = TvPlayerState(phase = TvPlayerState.Phase.Loading)
            try {
                val media = container.catalog.media(catalogId)
                val resolved = container.matcher.slug(media)
                    ?: throw IllegalStateException("The library does not know this series.")
                slug = resolved

                val episodes = container.library.episodes(resolved)
                val found = episodes.find(episode)
                    ?: throw IllegalStateException("The library does not have this episode.")

                val history = runCatching { container.history.entries() }.getOrDefault(emptyList())
                val ofThisSeries = history.filter { it.animeId.slug == resolved }
                val progress = ofThisSeries.associate { it.episodeNumber to it.progress }
                val resumeAt = ofThisSeries
                    .firstOrNull { it.episodeNumber == found.number }
                    ?.positionMs ?: 0L

                val source = container.library.resolve(
                    slug = resolved,
                    episode = found,
                    audioTrack = AudioTrack.Sub,
                )

                val prepared = container.player.prepare(
                    PlaybackRequest(
                        source = source,
                        title = "${media.title} · Episode ${found.number}",
                        resumePositionMs = resumeAt,
                    ),
                ) as? Media3PlaybackSession
                    ?: throw IllegalStateException("The player could not be prepared.")

                session = prepared
                prepared.exoPlayer.playWhenReady = true
                if (resumeAt > 5_000L) {
                    prepared.exoPlayer.seekTo(resumeAt)
                }
                prepared.exoPlayer.addListener(cueListener)

                mutableState.value = TvPlayerState(
                    phase = TvPlayerState.Phase.Ready,
                    title = media.title,
                    episodeLabel = buildString {
                        append("Episode ${found.number}")
                        source.serverName?.let { append(" · ").append(it) }
                    },
                    description = media.description,
                    positionMs = resumeAt,
                    isPlaying = true,
                    subtitles = source.subtitles,
                    activeSubtitle = source.defaultSubtitle,
                    variants = source.variants,
                    activeQuality = source.variant.quality.label,
                    audioTrack = source.audioTrack,
                    skip = source.skip,
                    episodes = episodes.episodes.map {
                        Episode(
                            number = it.number,
                            title = it.title ?: "Episode ${it.number}",
                            durationSeconds = it.durationSeconds,
                            watched = progress[it.number] ?: 0f,
                        )
                    },
                    currentEpisode = found.number,
                )

                follow()
            } catch (error: Throwable) {
                mutableState.value = TvPlayerState(
                    phase = TvPlayerState.Phase.Failed,
                    error = error.message ?: "The episode could not be resolved.",
                )
            }
        }
    }

    /**
     * The listener that collects the cues of the subtitle track.
     */
    private val cueListener = object : Player.Listener {
        override fun onCues(cues: List<Cue>) {
            mutableState.value = mutableState.value.copy(cues = cues)
        }
    }

    /**
     * Follows the playback and writes the history.
     */
    private fun follow() {
        viewModelScope.launch {
            var sinceSave = 0L
            while (true) {
                delay(400)
                val player = session?.exoPlayer ?: break
                val current = mutableState.value
                if (current.phase != TvPlayerState.Phase.Ready) break

                val position = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration.takeIf { it > 0L } ?: 0L
                mutableState.value = current.copy(
                    positionMs = position,
                    durationMs = duration,
                    bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                    isPlaying = player.isPlaying,
                )

                sinceSave += 400
                if (sinceSave >= 5_000) {
                    sinceSave = 0
                    if (duration > 0L) {
                        container.history.save(
                            slug = slug.orEmpty(),
                            title = current.title,
                            episode = current.currentEpisode,
                            positionMs = position,
                            durationMs = duration,
                        )
                    }
                }
            }
        }
    }

    /**
     * Starts or pauses the playback.
     */
    public fun togglePlay() {
        val player = session?.exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
        mutableState.value = mutableState.value.copy(isPlaying = player.isPlaying)
    }

    /**
     * Moves the playback by [milliseconds].
     */
    public fun seekBy(milliseconds: Long) {
        val player = session?.exoPlayer ?: return
        val target = (player.currentPosition + milliseconds).coerceAtLeast(0L)
        player.seekTo(target)
        mutableState.value = mutableState.value.copy(positionMs = target)
    }

    /**
     * Moves the playback to [positionMs].
     */
    public fun seekTo(positionMs: Long) {
        val player = session?.exoPlayer ?: return
        val target = positionMs.coerceAtLeast(0L)
        player.seekTo(target)
        mutableState.value = mutableState.value.copy(positionMs = target)
    }

    /**
     * Skips the intro.
     */
    public fun skipIntro() {
        val intro = mutableState.value.skip?.intro ?: return
        seekTo(intro.endSeconds * 1000L)
    }

    /**
     * Turns the subtitles on, or off.
     */
    public fun selectSubtitle(track: SubtitleTrack?) {
        val session = session ?: return
        viewModelScope.launch {
            session.selectSubtitle(track)
            mutableState.value = mutableState.value.copy(activeSubtitle = track)
        }
    }

    /**
     * Plays another episode of the same series.
     *
     * The position is not kept — it belongs to the episode that was playing.
     */
    public fun playEpisode(number: String) {
        val resolved = slug ?: return
        viewModelScope.launch {
            val episodes = container.library.episodes(resolved)
            val found = episodes.find(number) ?: return@launch
            val source = container.library.resolve(
                slug = resolved,
                episode = found,
                audioTrack = mutableState.value.audioTrack,
            )
            restart(source, 0L) {
                it.copy(
                    currentEpisode = found.number,
                    episodeLabel = buildString {
                        append("Episode ${found.number}")
                        source.serverName?.let { name -> append(" · ").append(name) }
                    },
                )
            }
        }
    }

    /**
     * Switches to the other audio track.
     *
     * The upstream site serves the dubbed track as a different stream, so the episode has
     * to be resolved again. The position is kept.
     */
    public fun toggleAudio() {
        val current = mutableState.value
        val resolved = slug ?: return
        val next = if (current.audioTrack == AudioTrack.Sub) AudioTrack.Dub else AudioTrack.Sub
        viewModelScope.launch {
            val position = session?.exoPlayer?.currentPosition ?: current.positionMs
            runCatching {
                container.library.resolve(
                    slug = resolved,
                    episode = dev.animeplayer.core.model.Episode(
                        animeId = AnimeId(resolved, resolved),
                        number = current.currentEpisode,
                        index = 0,
                    ),
                    audioTrack = next,
                )
            }.onSuccess { source ->
                restart(source, position) { it.copy(audioTrack = next) }
            }
        }
    }

    /**
     * Switches to another quality.
     */
    public fun selectQuality(height: Int?) {
        val current = mutableState.value
        val resolved = slug ?: return
        viewModelScope.launch {
            val position = session?.exoPlayer?.currentPosition ?: current.positionMs
            runCatching {
                container.library.resolve(
                    slug = resolved,
                    episode = dev.animeplayer.core.model.Episode(
                        animeId = AnimeId(resolved, resolved),
                        number = current.currentEpisode,
                        index = 0,
                    ),
                    audioTrack = current.audioTrack,
                    quality = height?.toString(),
                )
            }.onSuccess { source ->
                restart(source, position) { it.copy(activeQuality = source.variant.quality.label) }
            }
        }
    }

    /**
     * Plays the episode again, from the beginning of the new source.
     */
    private suspend fun restart(
        source: PlayableSource,
        position: Long,
        update: (TvPlayerState) -> TvPlayerState,
    ) {
        val current = mutableState.value
        val prepared = container.player.prepare(
            PlaybackRequest(
                source = source,
                title = "${current.title} · Episode ${current.currentEpisode}",
                resumePositionMs = position,
            ),
        ) as? Media3PlaybackSession ?: return

        session?.exoPlayer?.removeListener(cueListener)
        session?.close()
        session = prepared
        prepared.exoPlayer.playWhenReady = true
        prepared.exoPlayer.seekTo(position)
        prepared.exoPlayer.addListener(cueListener)

        mutableState.value = update(
            current.copy(
                positionMs = position,
                isPlaying = true,
                subtitles = source.subtitles,
                activeSubtitle = source.defaultSubtitle,
                variants = source.variants,
                skip = source.skip,
            ),
        )
    }

    /**
     * Releases the player.
     */
    public fun release() {
        session?.exoPlayer?.removeListener(cueListener)
        session?.close()
        session = null
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
