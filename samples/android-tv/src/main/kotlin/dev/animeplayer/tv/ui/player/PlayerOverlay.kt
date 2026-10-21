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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvIcon
import dev.animeplayer.tv.ui.components.TvIconButton
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvProgressBar
import dev.animeplayer.tv.ui.components.TvSidePanel
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * The panels of the player.
 */
public enum class PlayerPanel {
    /** The episodes of the series. */
    Episodes,

    /** The audio tracks. */
    Audio,

    /** The subtitle tracks. */
    Subtitles,

    /** The qualities. */
    Quality,

    /** The information about the episode. */
    Info,
}

/**
 * The controls of the player.
 *
 * The overlay is drawn over the video and disappears after five seconds. It never takes
 * the focus while it is hidden, so the D-pad keeps moving the focus of the video itself.
 */
@Composable
internal fun PlayerOverlay(
    state: PlayerState.Ready,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onOpenPanel: (PlayerPanel) -> Unit,
    /**
     * `false` while the episode is still starting.
     *
     * The button that opened the player sends its key event with it, so the transport
     * would pause the video the moment it appears.
     */
    armed: Boolean = true,
    transportFocus: FocusRequester? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // The scrim, so the controls are readable over a bright frame.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.75f),
                        1.0f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )

        // The top row: back, the title, the information.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = TvDimens.ScreenMargin, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvIconButton(
                icon = Icons.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
            )
            Column {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.episodeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            TvIconButton(
                icon = Icons.Filled.Info,
                onClick = { onOpenPanel(PlayerPanel.Info) },
                contentDescription = "Information",
            )
        }

        // The centre: the transport.
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                onClick = { onSeekBy(-10_000) },
                label = "−10",
                enabled = armed,
            )
            TransportButton(
                onClick = onTogglePlay,
                modifier = transportFocus?.let { Modifier.focusRequester(it) } ?: Modifier,
                icon = if (state.isPlaying) null else Icons.Filled.PlayArrow,
                glyph = if (state.isPlaying) ({ PauseGlyph() }) else null,
                prominent = true,
                enabled = armed,
            )
            TransportButton(
                onClick = { onSeekBy(10_000) },
                label = "+10",
                enabled = armed,
            )
        }

        // The bottom row: the position and the menus.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = TvDimens.ScreenMargin, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = formatTime(state.positionMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                TvProgressBar(
                    progress = if (state.durationMs > 0L) {
                        state.positionMs.toFloat() / state.durationMs
                    } else {
                        0f
                    },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatTime(state.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvButton(text = "Episodes", onClick = { onOpenPanel(PlayerPanel.Episodes) }, prominent = false)
                TvButton(text = "Audio", onClick = { onOpenPanel(PlayerPanel.Audio) }, prominent = false)
                TvButton(text = "Subtitles", onClick = { onOpenPanel(PlayerPanel.Subtitles) }, prominent = false)
                TvButton(text = "Quality", onClick = { onOpenPanel(PlayerPanel.Quality) }, prominent = false)
                TvButton(text = "Info", onClick = { onOpenPanel(PlayerPanel.Info) }, prominent = false)
            }
        }

        // The "skip the intro" button, the way every streaming service offers it.
        val intro = state.skip?.intro
        if (intro != null && state.positionMs in intro.startSeconds * 1000L until intro.endSeconds * 1000L) {
            TvButton(
                text = "Skip the intro",
                onClick = { onSeekBy((intro.endSeconds * 1000L - state.positionMs)) },
                prominent = true,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = TvDimens.ScreenMargin),
            )
        }
    }
}

/**
 * One of the transport buttons.
 */
@Composable
private fun TransportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    prominent: Boolean = false,
    icon: ImageVector? = null,
    glyph: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shape = CircleShape
    val accent = MaterialTheme.colorScheme.primary

    androidx.tv.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(if (prominent) 72.dp else 56.dp),
        shape = dev.animeplayer.tv.ui.focus.TvFocus.shape(shape),
        scale = dev.animeplayer.tv.ui.focus.TvFocus.scale(focused = 1.08f),
        glow = dev.animeplayer.tv.ui.focus.TvFocus.glow(accent),
        border = dev.animeplayer.tv.ui.focus.TvFocus.border(shape),
        colors = dev.animeplayer.tv.ui.focus.TvFocus.colors(
            containerColor = if (prominent) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White.copy(alpha = 0.15f)
            },
            contentColor = if (prominent) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                Color.White
            },
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                TvIcon(
                    icon = icon,
                    size = if (prominent) 32.dp else 22.dp,
                )
            } else if (glyph != null) {
                glyph()
            } else {
                Text(
                    text = label,
                    style = if (prominent) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                )
            }
        }
    }
}
/**
 * The side panel of the player.
 */
@Composable
internal fun PlayerPanelHost(
    panel: PlayerPanel,
    state: PlayerState.Ready?,
    onDismiss: () -> Unit,
    onSelectSubtitle: (dev.animeplayer.core.model.SubtitleTrack?) -> Unit = {},
    onSelectQuality: (Int?) -> Unit = {},
    onToggleAudio: () -> Unit = {},
) {
    TvSidePanel(
        title = when (panel) {
            PlayerPanel.Episodes -> "Episodes"
            PlayerPanel.Audio -> "Audio"
            PlayerPanel.Subtitles -> "Subtitles"
            PlayerPanel.Quality -> "Quality"
            PlayerPanel.Info -> "Information"
        },
        onDismiss = onDismiss,
    ) {
        when (panel) {
            PlayerPanel.Episodes -> Text(
                text = "The episodes are listed on the details of the series.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PlayerPanel.Audio -> {
                TvListItem(
                    title = "Subtitled",
                    subtitle = "The original Japanese track with subtitles.",
                    selected = state?.audioTrack == dev.animeplayer.core.model.AudioTrack.Sub,
                    onClick = { onToggleAudio() },
                )
                TvListItem(
                    title = "Dubbed",
                    subtitle = "The English track.",
                    selected = state?.audioTrack == dev.animeplayer.core.model.AudioTrack.Dub,
                    onClick = { onToggleAudio() },
                )
            }

            PlayerPanel.Subtitles -> {
                TvListItem(
                    title = "Off",
                    selected = state?.activeSubtitle == null,
                    onClick = { onSelectSubtitle(null) },
                )
                state?.subtitles.orEmpty().forEach { track ->
                    TvListItem(
                        title = track.label,
                        subtitle = track.language,
                        selected = state?.activeSubtitle?.url == track.url,
                        onClick = { onSelectSubtitle(track) },
                    )
                }
            }

            PlayerPanel.Quality -> {
                TvListItem(
                    title = "Auto",
                    subtitle = "The player picks the quality.",
                    selected = state?.quality == null,
                    onClick = { onSelectQuality(null) },
                )
                state?.variants.orEmpty().forEach { variant ->
                    TvListItem(
                        title = variant.quality.label,
                        subtitle = variant.codecs,
                        selected = state?.quality == variant.quality.label,
                        onClick = { onSelectQuality(variant.quality.height) },
                    )
                }
            }

            PlayerPanel.Info -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = state?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state?.episodeLabel.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state?.description != null) {
                    Text(
                        text = state.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The pause symbol.
 *
 * The icon library of the platform does not carry one, so the two bars are drawn.
 */
@Composable
private fun PauseGlyph() {
    val colour = androidx.tv.material3.MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(26.dp)) {
        val bar = size.width * 0.22f
        val gap = size.width * 0.18f
        val height = size.height
        val start = (size.width - bar * 2 - gap) / 2f
        drawRect(
            color = colour,
            topLeft = androidx.compose.ui.geometry.Offset(start, 0f),
            size = androidx.compose.ui.geometry.Size(bar, height),
        )
        drawRect(
            color = colour,
            topLeft = androidx.compose.ui.geometry.Offset(start + bar + gap, 0f),
            size = androidx.compose.ui.geometry.Size(bar, height),
        )
    }
}

/** `12:04` */
internal fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val total = milliseconds / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
