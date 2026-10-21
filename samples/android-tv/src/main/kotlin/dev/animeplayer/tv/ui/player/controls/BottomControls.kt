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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus

/**
 * The bottom of the player.
 *
 * Two rows: the timeline first, then the compact player actions. The transport floats
 * over the picture so the most common controls stay easy to reach and visually distinct.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BottomControls(
    positionMs: Long,
    durationMs: Long,
    focus: DirectionalFocus,
    onSeekTo: (Long) -> Unit,
    onEpisodes: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onQuality: () -> Unit,
    onInfo: () -> Unit,
    canSkipIntro: Boolean,
    onSkipIntro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (canSkipIntro) {
                TvButton(
                    text = "Skip intro",
                    onClick = onSkipIntro,
                    prominent = true,
                )
            }
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
            containerColor = Color(0xD012141A),
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
