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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.TvSettings
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.player.controls.BottomControls
import dev.animeplayer.tv.ui.player.controls.TopControls
import dev.animeplayer.tv.ui.player.controls.formatPlayerTime
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus
import dev.animeplayer.tv.ui.player.media.VideoSurface
import dev.animeplayer.tv.ui.player.panel.PanelItem
import dev.animeplayer.tv.ui.player.panel.PlayerPanel
import dev.animeplayer.tv.ui.player.panel.SubtitleStylePanel
import dev.animeplayer.tv.ui.player.subtitles.TvSubtitles
import kotlinx.coroutines.delay

/**
 * The panels of the player.
 */
private enum class Panel {
    Episodes,
    Audio,
    Subtitles,
    SubtitleStyle,
    Quality,
    Info,
}

/**
 * The player.
 *
 * The controls are always in the composition and are only faded in and out. Removing them
 * would destroy the focused element, the video would take the focus, and no key would
 * ever reach the player again — the controls have to stay, and the reference
 * implementations do the same.
 *
 * The focus moves through the player along a graph that is declared, not guessed: the top
 * row, the transport, the scrubber, the menus.
 *
 * @param container the container of the dependencies.
 * @param catalogId the identifier of the series in the catalogue.
 * @param episode the number of the episode.
 * @param onBack called when the user asks to go back.
 */
@Composable
public fun PlayerScreen(
    container: AppContainer,
    catalogId: String,
    episode: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(container, catalogId, episode),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = TvSettings(),
    )
    val subtitleStyle = settings.subtitleStyle
    val scope = rememberCoroutineScope()
    val focus = remember { DirectionalFocus() }

    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var interactions by remember { mutableIntStateOf(0) }
    // The key that wakes the controls must not press a button as well.
    var wakeups by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(true) }

    LaunchedEffect(catalogId, episode) { model.load() }

    // The controls disappear after five seconds and come back with the next key.
    LaunchedEffect(interactions) {
        if (panel != null) return@LaunchedEffect
        delay(5_000)
        controlsVisible = false
    }

    // The player takes the focus, so the D-pad drives the transport and not the video.
    // Without this the focus stays wherever the system put it — on the video surface —
    // and no key ever reaches the player.
    LaunchedEffect(state.phase, controlsVisible) {
        if (state.phase == TvPlayerState.Phase.Ready && controlsVisible) {
            runCatching { focus.transport.requestFocus() }
        }
    }

    // The style panel takes the focus when it opens.
    LaunchedEffect(panel) {
        if (panel == Panel.SubtitleStyle) {
            runCatching { focus.style.requestFocus() }
        }
    }

    // The transport is armed a moment after the controls wake up.
    LaunchedEffect(wakeups) {
        if (wakeups > 0) {
            armed = false
            delay(400)
            armed = true
        }
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
                    if (!controlsVisible) {
                        controlsVisible = true
                        wakeups++
                    }
                    interactions++
                    when (event.key) {
                        Key.MediaPlayPause -> {
                            model.togglePlay()
                            true
                        }

                        Key.MediaRewind -> {
                            model.seekBy(-10_000)
                            true
                        }

                        Key.MediaFastForward -> {
                            model.seekBy(10_000)
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        val current = state
        when (current.phase) {
            TvPlayerState.Phase.Loading -> TvLoading(
                message = "The episode is being resolved.",
            )

            TvPlayerState.Phase.Failed -> TvErrorState(
                message = current.error ?: "The episode could not be played.",
                onRetry = { model.load() },
            )

            TvPlayerState.Phase.Ready -> {
                val player = model.player
                if (player != null) {
                    VideoSurface(player = player)

                    // The subtitles sit above the picture and below the controls.
                    TvSubtitles(
                        cues = current.cues,
                        style = subtitleStyle,
                        bottomPadding = if (controlsVisible) 190.dp else 60.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 140.dp),
                    )

                    // The controls stay in the composition and are only made
                    // invisible. Removing them would destroy the focused element, the
                    // video would take the focus, and no key would ever reach the player
                    // again.
                    val visible = controlsVisible && panel == null
                    val controlsAlpha by animateFloatAsState(
                        targetValue = if (visible) 1f else 0f,
                        animationSpec = tween(if (visible) 160 else 220),
                        label = "controls",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = controlsAlpha },
                    ) {
                            // The scrim, so the controls are readable over a bright frame.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Black.copy(alpha = 0.55f),
                                            1f to Color.Transparent,
                                        ),
                                    )
                                    .padding(top = 0.dp),
                            )

                            TopControls(
                                title = current.title,
                                episodeLabel = current.episodeLabel,
                                focus = focus,
                                onBack = onBack,
                                onInfo = { panel = Panel.Info },
                                modifier = Modifier.align(Alignment.TopCenter),
                            )

                            BottomControls(
                                isPlaying = current.isPlaying,
                                positionMs = current.positionMs,
                                durationMs = current.durationMs,
                                focus = focus,
                                onTogglePlay = { model.togglePlay() },
                                onSeekBy = { model.seekBy(it) },
                                onSeekTo = { model.seekTo(it) },
                                onEpisodes = { panel = Panel.Episodes },
                                onAudio = { panel = Panel.Audio },
                                onSubtitles = { panel = Panel.Subtitles },
                                onQuality = { panel = Panel.Quality },
                                onInfo = { panel = Panel.Info },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )

                            // The "skip the intro" button, the way every streaming
                            // service offers it.
                            if (current.canSkipIntro) {
                                TvButton(
                                    text = "Skip the intro",
                                    onClick = { model.skipIntro() },
                                    prominent = true,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 60.dp),
                                )
                            }
                    }
                }
            }
        }

        // The panel that picks the style of the subtitles.
        if (panel == Panel.SubtitleStyle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                SubtitleStylePanel(
                    style = subtitleStyle,
                    onStyleChange = { style ->
                        scope.launch { container.settings.setSubtitleStyle(style) }
                    },
                    focus = focus.style,
                    modifier = Modifier
                        .width(620.dp)
                        .padding(32.dp),
                )
            }
        }

        // The panel.
        panel?.takeIf { it != Panel.SubtitleStyle }?.let { open ->
            PlayerPanel(
                title = when (open) {
                    Panel.Episodes -> "Episodes"
                    Panel.Audio -> "Audio"
                    Panel.Subtitles -> "Subtitles"
                    Panel.SubtitleStyle -> "The style"
                    Panel.Quality -> "Quality"
                    Panel.Info -> "Information"
                },
                items = when (open) {
                    Panel.Episodes -> current.episodes.map { episode ->
                        PanelItem(
                            title = "Episode ${episode.number}${if (episode.isWatched) " ✓" else ""}",
                            subtitle = episode.title,
                            trailing = episode.durationSeconds?.let { formatPlayerTime(it * 1000L) },
                            selected = episode.number == current.currentEpisode,
                            onClick = { panel = null },
                        )
                    }

                    Panel.Audio -> listOf(
                        PanelItem(
                            title = "Subtitled",
                            subtitle = "The original Japanese track with subtitles.",
                            selected = current.audioTrack == AudioTrack.Sub,
                            onClick = {
                                if (current.audioTrack != AudioTrack.Sub) model.toggleAudio()
                                panel = null
                            },
                        ),
                        PanelItem(
                            title = "Dubbed",
                            subtitle = "The English track.",
                            selected = current.audioTrack == AudioTrack.Dub,
                            onClick = {
                                if (current.audioTrack != AudioTrack.Dub) model.toggleAudio()
                                panel = null
                            },
                        ),
                    )

                    Panel.Subtitles -> buildList {
                        add(
                            PanelItem(
                                title = "The style…",
                                subtitle = "Pick a style, and make the letters bigger or smaller.",
                                onClick = { panel = Panel.SubtitleStyle },
                            ),
                        )
                        add(
                            PanelItem(
                                title = "Off",
                                selected = current.activeSubtitle == null,
                                onClick = {
                                    model.selectSubtitle(null)
                                    panel = null
                                },
                            ),
                        )
                        current.subtitles.forEach { track ->
                            add(
                                PanelItem(
                                    title = track.label.ifBlank { track.language },
                                    subtitle = track.language,
                                    selected = current.activeSubtitle?.url == track.url,
                                    onClick = {
                                        model.selectSubtitle(track)
                                        panel = null
                                    },
                                ),
                            )
                        }
                    }

                    Panel.SubtitleStyle -> emptyList()

                    Panel.Quality -> buildList {
                        add(
                            PanelItem(
                                title = "Auto",
                                subtitle = "The player picks the quality.",
                                selected = current.activeQuality == null,
                                onClick = { panel = null },
                            ),
                        )
                        current.variants.forEach { variant ->
                            add(
                                PanelItem(
                                    title = variant.quality.label,
                                    subtitle = variant.codecs,
                                    selected = current.activeQuality == variant.quality.label,
                                    onClick = {
                                        model.selectQuality(variant.quality.height)
                                        panel = null
                                    },
                                ),
                            )
                        }
                    }

                    Panel.Info -> listOf(
                        PanelItem(
                            title = current.title,
                            subtitle = current.episodeLabel,
                            onClick = { },
                        ),
                        PanelItem(
                            title = "Position",
                            trailing = formatPlayerTime(current.positionMs),
                            onClick = { },
                        ),
                        PanelItem(
                            title = "Length",
                            trailing = formatPlayerTime(current.durationMs),
                            onClick = { },
                        ),
                        PanelItem(
                            title = "Audio",
                            trailing = if (current.audioTrack == AudioTrack.Dub) {
                                "Dubbed"
                            } else {
                                "Subtitled"
                            },
                            onClick = { },
                        ),
                        PanelItem(
                            title = "Quality",
                            trailing = current.activeQuality ?: "Auto",
                            onClick = { },
                        ),
                    )
                },
                focus = focus,
                onDismiss = { panel = null },
            )
        }
    }
}
