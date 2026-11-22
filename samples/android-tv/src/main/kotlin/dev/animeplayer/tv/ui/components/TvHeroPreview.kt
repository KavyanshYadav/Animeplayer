/*
 * Copyright (C) 2026 animeplayer contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package dev.animeplayer.tv.ui.components

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import kotlinx.coroutines.delay

@Composable
internal fun TvHeroPreview(
    id: String,
    constrained: Boolean,
    background: Color,
    modifier: Modifier,
    onVisible: (Boolean) -> Unit,
) {
    var url by remember { mutableStateOf<String?>(null) }
    val onVisibleChanged by rememberUpdatedState(onVisible)
    LaunchedEffect(id, constrained) {
        delay(if (constrained) 3_500L else 2_000L)
        url = YoutubePreviewSource.resolve(id)
    }
    url?.let { stream ->
        HeroPreviewVideo(
            url = stream,
            constrained = constrained,
            background = background,
            modifier = modifier,
            onVisible = { onVisibleChanged(it) },
            onComplete = { failed ->
                if (failed) YoutubePreviewSource.playbackFailed(id)
                url = null
            },
        )
    }
}

@Composable
private fun HeroPreviewVideo(
    url: String,
    constrained: Boolean,
    background: Color,
    modifier: Modifier,
    onVisible: (Boolean) -> Unit,
    onComplete: (failed: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val visibleCallback by rememberUpdatedState(onVisible)
    val completeCallback by rememberUpdatedState(onComplete)
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var firstFrame by remember { mutableStateOf(false) }
    DisposableEffect(context, url) {
        val preview = ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_500, 4_000, 400, 750)
                    .build(),
            )
            .build()
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame = true
                visibleCallback(true)
                Log.d("YoutubePreview", "First frame")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) completeCallback(false)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.d("YoutubePreview", "Playback failed: ${error.errorCodeName}")
                completeCallback(true)
            }
        }
        preview.addListener(listener)
        preview.trackSelectionParameters = preview.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        preview.volume = 0f
        preview.setMediaItem(
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP4).build(),
        )
        player = preview
        preview.prepare()
        preview.playWhenReady = true
        onDispose {
            preview.removeListener(listener)
            preview.release()
            visibleCallback(false)
            Log.d("YoutubePreview", "Released preview")
        }
    }
    LaunchedEffect(firstFrame) {
        // Bound wall time even if the network stalls or the trailer keeps buffering.
        delay(if (firstFrame) 22_000L else 6_000L)
        onComplete(!firstFrame)
    }

    val alpha by animateFloatAsState(
        targetValue = if (firstFrame) 1f else 0f,
        animationSpec = tween(if (constrained) 0 else 210),
        label = "Preview reveal",
    )
    player?.let { currentPlayer ->
        val presentation = rememberPresentationState(currentPlayer)
        Box(modifier.clipToBounds().graphicsLayer { this.alpha = alpha }) {
            PlayerSurface(
                player = currentPlayer,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.fillMaxSize().resizeWithContentScale(ContentScale.Crop, presentation.videoSizeDp),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.65f to Color.Transparent,
                        0.86f to background.copy(alpha = 0.66f),
                        1f to background,
                    ),
                ),
            )
        }
    }
}
