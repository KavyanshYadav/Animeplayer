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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.TvSettings
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.theme.TvMotionEase
import dev.animeplayer.tv.ui.theme.tvEntrance
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvSidePanel
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.components.TvSpinner
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.player.controls.BottomControls
import dev.animeplayer.tv.ui.player.controls.TopControls
import dev.animeplayer.tv.ui.player.controls.formatPlayerTime
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus
import dev.animeplayer.tv.ui.player.media.VideoSurface
import dev.animeplayer.tv.ui.player.panel.PanelItem
import dev.animeplayer.tv.ui.player.panel.PlayerEpisodesPanel
import dev.animeplayer.tv.ui.player.panel.PlayerPanel
import dev.animeplayer.tv.ui.player.panel.SubtitleStylePanel
import dev.animeplayer.tv.ui.player.subtitles.TvSubtitles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The panels of the player.
 */
private enum class Panel {
    Episodes,
    Audio,
    Subtitles,
    SubtitleStyle,
    Quality,
    Speed,
    Info,
}

private val playbackSpeeds = listOf(
    0.5f to "0.5×",
    0.75f to "0.75×",
    1f to "Normal (1×)",
    1.25f to "1.25×",
    1.5f to "1.5×",
    2f to "2×",
)

@Composable
public fun PlayerScreen(
    container: AppContainer,
    catalogId: String,
    episode: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    seriesSlug: String? = null,
) {
    val model: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(container, catalogId, episode, seriesSlug),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = TvSettings(),
    )
    val subtitleStyle = settings.subtitleStyle
    val scope = rememberCoroutineScope()
    val focus = remember { DirectionalFocus() }
    val wakeFocus = remember { FocusRequester() }
    var saveFailed by remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var interactions by remember { mutableIntStateOf(0) }
    var seekPreviewVisible by remember { mutableStateOf(false) }
    var seekPreviewDirection by remember { mutableIntStateOf(1) }
    var seekPreviewInteractions by remember { mutableIntStateOf(0) }

    LifecycleStartEffect(catalogId, episode) {
        model.load()
        onStopOrDispose { model.release() }
    }

    LaunchedEffect(interactions, panel, state.isPlaying, controlsVisible) {
        if (panel != null || !state.isPlaying || !controlsVisible) return@LaunchedEffect
        delay(5_000)
        controlsVisible = false
    }
    LaunchedEffect(seekPreviewInteractions) {
        if (seekPreviewInteractions == 0) return@LaunchedEffect
        seekPreviewVisible = true
        delay(1_500)
        seekPreviewVisible = false
    }
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(3_500)
            model.clearNotice()
        }
    }
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            delay(3_500)
            saveFailed = false
        }
    }
    LaunchedEffect(state.phase, controlsVisible, panel) {
        if (state.phase == TvPlayerState.Phase.Ready && panel == null) {
            withFrameNanos { }
            if (controlsVisible) focus.transport.requestTvFocus() else wakeFocus.requestTvFocus()
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == TvPlayerState.Phase.Failed) panel = null
    }

    // The style panel takes the focus when it opens.
    LaunchedEffect(panel) {
        if (panel == Panel.Episodes) model.loadEpisodeMetadata()
        if (panel == Panel.SubtitleStyle) {
            focus.style.requestTvFocus()
        }
    }

    // Back closes the topmost layer first: panel, controls, then the player screen.
    BackHandler {
        when {
            panel != null -> panel = null
            seekPreviewVisible && state.phase == TvPlayerState.Phase.Ready -> seekPreviewVisible = false
            controlsVisible && state.phase == TvPlayerState.Phase.Ready -> controlsVisible = false
            else -> onBack()
        }
    }

    val overlayVisible = controlsVisible && panel == null
    val controlShade by animateFloatAsState(
        if (overlayVisible) 1f else 0f,
        tween(if (overlayVisible) 185 else 125, easing = TvMotionEase),
        label = "Player control scrim",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key == Key.Back) {
                    return@onPreviewKeyEvent false
                }

                val isDirectionalSeek = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                if (
                    isDirectionalSeek &&
                    !controlsVisible &&
                    panel == null &&
                    state.phase == TvPlayerState.Phase.Ready &&
                    state.isPlaying
                ) {
                    val direction = if (event.key == Key.DirectionLeft) -1 else 1
                    model.seekBy(direction * 10_000L)
                    seekPreviewDirection = direction
                    seekPreviewInteractions++
                    interactions++
                    return@onPreviewKeyEvent true
                }

                val wokeControls = !controlsVisible && state.phase == TvPlayerState.Phase.Ready
                if (wokeControls) controlsVisible = true
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

                    // The first remote key reveals the overlay; it must not also
                    // activate whichever control still has focus underneath it.
                    else -> wokeControls
                }
            },
    ) {
        val current = state
        when (current.phase) {
            TvPlayerState.Phase.Loading -> TvLoading(
                message = "Opening episode…",
            )

            TvPlayerState.Phase.Failed -> TvErrorState(
                message = current.error ?: "The episode could not be played.",
                onRetry = { model.load() },
                onBack = onBack,
                autoFocus = true,
            )

            TvPlayerState.Phase.Ready -> {
                val player = model.player
                if (player != null) {
                    VideoSurface(player = player)
                    if (!overlayVisible && panel == null) {
                        Box(Modifier.matchParentSize().focusRequester(wakeFocus).focusable())
                    }
                    if (current.buffering && panel == null) {
                        TvSpinner(Modifier.align(Alignment.Center).padding(bottom = if (overlayVisible) 120.dp else 0.dp))
                    }

                    // The subtitles sit above the picture and below the controls.
                    TvSubtitles(
                        cues = current.cues,
                        style = subtitleStyle,
                        bottomPadding = when {
                            overlayVisible -> 146.dp
                            seekPreviewVisible -> 114.dp
                            else -> 60.dp
                        },
                        topPadding = if (overlayVisible) 120.dp else 60.dp,
                        modifier = Modifier.matchParentSize().padding(horizontal = TvDimens.ScreenMargin * 2),
                    )

                    if (!controlsVisible && panel == null) {
                        SeekPreviewTimeline(
                            positionMs = current.positionMs,
                            durationMs = current.durationMs,
                            direction = seekPreviewDirection,
                            visible = seekPreviewVisible,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }

                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(104.dp).drawWithCache {
                        val shade = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.82f),
                            0.55f to Color.Black.copy(alpha = 0.38f),
                            1f to Color.Transparent,
                            endY = size.height,
                        )
                        onDrawBehind { if (controlShade > 0f) drawRect(shade, alpha = controlShade) }
                    })
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(184.dp).drawWithCache {
                        val shade = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.24f to Color.Black.copy(alpha = 0.38f),
                            0.68f to Color.Black.copy(alpha = 0.78f),
                            1f to Color.Black.copy(alpha = 0.94f),
                            endY = size.height,
                        )
                        onDrawBehind { if (controlShade > 0f) drawRect(shade, alpha = controlShade) }
                    })

                    if (overlayVisible) {
                        TopControls(
                            title = current.title,
                            episodeLabel = current.episodeLabel,
                            focus = focus,
                            onBack = onBack,
                            onInfo = { panel = Panel.Info },
                            modifier = Modifier.align(Alignment.TopCenter).tvEntrance(fromY = (-8).dp),
                        )

                        BottomControls(
                            state = current,
                            focus = focus,
                            onSeekTo = { model.seekTo(it) },
                            onTogglePlayback = model::togglePlay,
                            onSeekBy = model::seekBy,
                            onNext = model::playNextEpisode,
                            onEpisodes = { panel = Panel.Episodes },
                            onAudio = { panel = Panel.Audio },
                            onSubtitles = { panel = Panel.Subtitles },
                            onQuality = { panel = Panel.Quality },
                            onSpeed = { panel = Panel.Speed },
                            onSkipIntro = { model.skipIntro() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 25.dp)
                                .tvEntrance(fromY = 10.dp),
                        )
                    }
                }
            }
        }

        val notice = state.notice ?: if (saveFailed) "Couldn't save subtitle style." else null
        if (notice != null && panel == null) {
            Text(
                notice,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
                    .background(Color(0xE6171F2B), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            )
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
                        scope.launch {
                            try {
                                container.settings.setSubtitleStyle(style)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                saveFailed = true
                            }
                        }
                    },
                    focus = focus.style,
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth(0.80f)
                        .padding(32.dp),
                )
            }
        }

        if (panel == Panel.Info && current.phase == TvPlayerState.Phase.Ready) {
            PlayerInfo(current, focus.panel, onClose = { panel = null })
        }
        if (panel == Panel.Episodes && current.phase == TvPlayerState.Phase.Ready) {
            PlayerEpisodesPanel(
                episodes = current.episodes,
                currentEpisode = current.currentEpisode,
                onDismiss = { panel = null },
                onSelect = {
                    model.playEpisode(it)
                    panel = null
                },
            )
        }
        panel?.takeIf {
            it != Panel.SubtitleStyle && it != Panel.Info && it != Panel.Episodes && current.phase == TvPlayerState.Phase.Ready
        }?.let { open ->
            PlayerPanel(
                title = when (open) {
                    Panel.Episodes -> "Episodes"
                    Panel.Audio -> "Audio"
                    Panel.Subtitles -> "Subtitles"
                    Panel.SubtitleStyle -> "The style"
                    Panel.Quality -> "Quality"
                    Panel.Speed -> "Playback speed"
                    Panel.Info -> "Information"
                },
                items = when (open) {
                    Panel.Episodes -> emptyList()

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

                    Panel.Speed -> playbackSpeeds.map { (speed, label) ->
                        PanelItem(
                            title = label,
                            selected = current.playbackSpeed == speed,
                            onClick = {
                                model.setPlaybackSpeed(speed)
                                panel = null
                            },
                        )
                    }

                    Panel.Subtitles -> buildList {
                        add(
                            PanelItem(
                                title = "Subtitle appearance",
                                subtitle = "Font, size, and placement",
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
                                subtitle = "Adjust to the connection",
                                selected = current.activeQuality == null,
                                onClick = {
                                    model.selectQuality(null)
                                    panel = null
                                },
                            ),
                        )
                        current.variants.filter { it.quality.height > 0 }
                            .distinctBy { it.quality.height }.sortedByDescending { it.quality.height }.forEach { variant ->
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

                    Panel.Info -> emptyList()
                },
                focus = focus,
                onDismiss = { panel = null },
            )
        }
    }
}

@Composable
private fun PlayerInfo(state: TvPlayerState, focus: FocusRequester, onClose: () -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestTvFocus()
    }
    TvSidePanel(title = "Now playing", onDismiss = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.title, style = MaterialTheme.typography.titleLarge)
            Text(state.episodeLabel, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Length  ${formatPlayerTime(state.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val audio = if (state.audioTrack == AudioTrack.Dub) "Dubbed" else "Subtitled"
            Text(
                "$audio  ·  ${state.activeQuality ?: "Auto quality"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TvButton("Close", modifier = Modifier.padding(top = 8.dp).focusRequester(focus), onClick = onClose)
    }
}

@Composable
private fun SeekPreviewTimeline(
    positionMs: Long,
    durationMs: Long,
    direction: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 140 else 260),
        label = "seek preview",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .padding(start = 88.dp, end = 88.dp, bottom = 42.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatPlayerTime(positionMs)} / ${formatPlayerTime(durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (direction < 0) "−10 sec" else "+10 sec",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.34f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}
