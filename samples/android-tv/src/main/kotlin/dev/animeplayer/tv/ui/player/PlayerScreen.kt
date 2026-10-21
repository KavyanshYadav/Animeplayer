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
@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package dev.animeplayer.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.focus.FocusMemory
import kotlinx.coroutines.delay

/**
 * The player.
 *
 * The controls are the television ones: everything is reachable with the D-pad, the
 * overlay disappears after five seconds, and the side panels — the episodes, the audio,
 * the subtitles, the quality — slide in from the right instead of opening a drop-down
 * that a mouse could reach but a remote never will.
 *
 * @param container the container of the dependencies.
 * @param catalogId the identifier of the series in the catalogue.
 * @param episode the number of the episode.
 * @param onBack called when the user asks to go back.
 */
@Composable
public fun PlayerScreen(
    container: AppContainer,
    memory: FocusMemory,
    catalogId: String,
    episode: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: PlayerViewModel = viewModel<PlayerViewModel>(
        factory = PlayerViewModel.factory(container, catalogId, episode),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val transportFocus = remember { FocusRequester() }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var armed by remember { mutableStateOf(false) }
    // Every key press restarts the timer of the overlay.
    var interactions by remember { mutableIntStateOf(0) }

    LaunchedEffect(catalogId, episode) { model.load() }

    // The transport is armed a moment after the episode starts, so the button that
    // opened the player does not pause it again.
    LaunchedEffect(state is PlayerState.Ready) {
        if (state is PlayerState.Ready) {
            armed = false
            delay(900)
            armed = true
        }
    }

    // The overlay takes the focus, so the D-pad drives the transport and not the video.
    LaunchedEffect(controlsVisible, state) {
        if (controlsVisible && state is PlayerState.Ready) {
            runCatching { transportFocus.requestFocus() }
        }
    }

    // The overlay disappears after five seconds, and comes back with the next key.
    LaunchedEffect(interactions) {
        if (!controlsVisible || panel != null) return@LaunchedEffect
        delay(5_000)
        controlsVisible = false
    }

    // The back button closes the panel first, and the player afterwards.
    BackHandler(enabled = panel != null) { panel = null }
    BackHandler(enabled = panel == null) { onBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    controlsVisible = true
                    interactions++
                    val ready = state.asReady()
                    when (event.key) {
                        Key.MediaPlayPause -> {
                            ready?.togglePlay()
                            true
                        }

                        Key.MediaRewind -> {
                            ready?.seekBy(-10_000)
                            true
                        }

                        Key.MediaFastForward -> {
                            ready?.seekBy(10_000)
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        when (val current = state) {
            is PlayerState.Loading -> TvLoading(message = "The episode is being resolved.")
            is PlayerState.Failed -> TvErrorState(
                message = current.message,
                onRetry = { model.load() },
            )

            is PlayerState.Ready -> {
                // The surface is a texture view, not a surface view: a surface view is
                // drawn in its own layer, above the composition, and the controls would
                // disappear behind the video.
                ContentFrame(
                    player = current.player,
                    modifier = Modifier.fillMaxSize(),
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                )

                if (controlsVisible) {
                    PlayerOverlay(
                        state = current,
                        onBack = onBack,
                        onTogglePlay = current::togglePlay,
                        onSeekBy = current::seekBy,
                        onOpenPanel = { panel = it },
                        armed = armed,
                        transportFocus = transportFocus,
                    )
                }
            }
        }

        panel?.let { open ->
            PlayerPanelHost(
                panel = open,
                state = state.asReady(),
                onDismiss = { panel = null },
                onSelectSubtitle = { model.selectSubtitle(it) },
                onSelectQuality = { model.selectQuality(it) },
                onToggleAudio = { model.toggleAudio() },
            )
        }
    }
}
