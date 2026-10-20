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
package dev.animeplayer.player.media3

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.SubtitleTrack
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.core.spi.PlaybackSession
import dev.animeplayer.core.spi.PlaybackState
import dev.animeplayer.core.spi.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Android playback backend, built on Media3 / ExoPlayer.
 *
 * The one detail that matters here is the `Referer` header. Upstream CDNs answer with
 * `403 Forbidden` when it is missing, and it has to be present on the subtitle request
 * as well. Setting it on the [DefaultHttpDataSource.Factory] covers both, because Media3
 * builds the subtitle data source from the same factory as the media.
 */
public class Media3PlayerEngine(
    private val context: Context,
    private val userAgent: String? = null,
) : PlayerEngine {

    override val id: String = "media3"

    override val isAvailable: Boolean = true

    override suspend fun prepare(request: PlaybackRequest): PlaybackSession {
        val source = request.source

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .apply { userAgent?.let { setUserAgent(it) } }
            .setDefaultRequestProperties(mapOf("Referer" to source.referer))

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        exoPlayer.setMediaItem(buildMediaItem(request))
        exoPlayer.prepare()

        return Media3PlaybackSession(exoPlayer, source)
    }

    private fun buildMediaItem(request: PlaybackRequest): MediaItem {
        val source = request.source
        // The Referer is applied on the data source factory, which covers the media and
        // the subtitle requests alike.
        val builder = MediaItem.Builder()
            .setUri(source.variant.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(request.title)
                    .build(),
            )

        source.subtitles.map { track ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(track.language)
                .setLabel(track.label)
                .setSelectionFlags(if (track.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }.let { configurations ->
            builder.setSubtitleConfigurations(configurations)
        }

        return builder.build()
    }
}

private class Media3PlaybackSession(
    private val player: ExoPlayer,
    private val source: PlayableSource,
) : PlaybackSession {

    private val mutableState = MutableStateFlow(PlaybackState())

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publish()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    publish()
                }

                override fun onPlayerError(error: PlaybackException) {
                    mutableState.value = mutableState.value.copy(error = error.errorCodeName)
                }
            },
        )
        publish()
    }

    override suspend fun play() {
        player.play()
    }

    override suspend fun pause() {
        player.pause()
    }

    override suspend fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override suspend fun selectSubtitle(track: SubtitleTrack?) {
        val parameters = player.trackSelectionParameters.buildUpon()
        if (track == null) {
            parameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            parameters
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(track.language)
        }
        player.setTrackSelectionParameters(parameters.build())
    }

    override fun close() {
        player.release()
    }

    private fun publish() {
        mutableState.value = PlaybackState(
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
            currentSubtitle = source.defaultSubtitle,
            error = mutableState.value.error,
        )
    }
}
