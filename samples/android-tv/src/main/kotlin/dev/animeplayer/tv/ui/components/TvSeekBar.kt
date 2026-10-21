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
package dev.animeplayer.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import dev.animeplayer.tv.ui.focus.TvFocus

/**
 * The scrubber of the player.
 *
 * It is a control, not a picture: the D-pad moves along it and the position follows
 * immediately, the way it does in every streaming application on a television.
 *
 * @param positionMs the current position.
 * @param durationMs the length of the episode.
 * @param onSeek called with the new position.
 * @param stepMs how far one press of the D-pad jumps.
 */
@Composable
public fun TvSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    stepMs: Long = 10_000,
    height: Dp = 32.dp,
) {
    val shape = RoundedCornerShape(4.dp)
    val accent = MaterialTheme.colorScheme.primary
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    var width by remember { mutableIntStateOf(0) }
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }

    Surface(
        onClick = { },
        modifier = modifier
            .height(height)
            .onSizeChanged { width = it.width }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeek((positionMs - stepMs).coerceAtLeast(0L))
                        true
                    }

                    Key.DirectionRight -> {
                        onSeek((positionMs + stepMs).coerceAtMost(durationMs.coerceAtLeast(0L)))
                        true
                    }

                    else -> false
                }
            },
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // The track.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
            // The part that was watched.
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            // The thumb, on the position.
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = ((width - 4) * progress).toInt().coerceAtLeast(0),
                            y = 0,
                        )
                    }
                    .size(if (focused) 20.dp else 14.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
