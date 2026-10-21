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
package dev.animeplayer.tv.ui.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus

/**
 * The bottom of the player.
 *
 * Two rows: the transport and the scrubber on the first, the menus on the second. The
 * reference implementations separate them the same way, and every area of the player
 * keeps its own place in the focus graph.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BottomControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    focus: DirectionalFocus,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onEpisodes: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onQuality: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            TransportControls(
                isPlaying = isPlaying,
                focus = focus,
                onTogglePlay = onTogglePlay,
                onSeekBack = { onSeekBy(-10_000) },
                onSeekForward = { onSeekBy(10_000) },
            )

            TvScrubber(
                positionMs = positionMs,
                durationMs = durationMs,
                focus = focus,
                onSeek = onSeekTo,
                modifier = Modifier.weight(1f),
            )

            PlayerTime(
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The requester belongs on the first button: a requester on the row cannot
            // take the focus, because the row is not focusable itself.
            PlayerMenuButton(
                text = "Episodes",
                onClick = onEpisodes,
                modifier = Modifier
                    .focusRequester(focus.menu)
                    .focusProperties { up = focus.scrubber }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            focus.focusOn(focus.scrubber)
                            true
                        } else {
                            false
                        }
                    },
            )
            PlayerMenuButton(
                text = "Audio",
                onClick = onAudio,
                modifier = Modifier.focusProperties { up = focus.scrubber },
            )
            PlayerMenuButton(
                text = "Subtitles",
                onClick = onSubtitles,
                modifier = Modifier.focusProperties { up = focus.scrubber },
            )
            PlayerMenuButton(
                text = "Quality",
                onClick = onQuality,
                modifier = Modifier.focusProperties { up = focus.scrubber },
            )
            PlayerMenuButton(
                text = "Info",
                onClick = onInfo,
                modifier = Modifier.focusProperties { up = focus.scrubber },
            )
        }
    }
}

/**
 * One of the menus of the player.
 */
@Composable
private fun PlayerMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = dev.animeplayer.tv.ui.focus.TvFocus.shape(shape),
        scale = dev.animeplayer.tv.ui.focus.TvFocus.scale(focused = 1.06f),
        glow = dev.animeplayer.tv.ui.focus.TvFocus.glow(accent),
        border = dev.animeplayer.tv.ui.focus.TvFocus.border(shape),
        colors = dev.animeplayer.tv.ui.focus.TvFocus.colors(
            containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
            contentColor = androidx.compose.ui.graphics.Color.White,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
