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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus

@Composable
internal fun TvScrubber(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    focus: DirectionalFocus,
    onSeek: (Long) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val duration = durationMs.coerceAtLeast(1)
    val played = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (bufferedMs.toFloat() / duration).coerceIn(played, 1f)
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = { focus.focusOn(focus.transport) },
        modifier = modifier
            .height(25.dp)
            .focusRequester(focus.scrubber)
            .focusProperties {
                up = focus.top
                down = focus.transport
            }
            .semantics {
                contentDescription = "Seek"
                progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f)
            }
            .onFocusChanged {
                focused = it.isFocused
                if (focused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || durationMs <= 0L) return@onPreviewKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -10_000L
                    Key.DirectionRight -> 10_000L
                    else -> return@onPreviewKeyEvent false
                }
                onSeek((positionMs + step).coerceIn(0L, durationMs))
                true
            },
        shape = TvFocus.shape(RoundedCornerShape(4.dp)),
        scale = TvFocus.scale(focused = 1f, pressed = 1f),
        colors = TvFocus.colors(containerColor = Color.Transparent, contentColor = Color.White),
    ) {
        Canvas(Modifier.fillMaxWidth().height(25.dp)) {
            val radius = (if (focused) 6.dp else 4.dp).toPx()
            val left = radius
            val right = (size.width - radius).coerceAtLeast(left)
            val centerY = center.y
            val stroke = (if (focused) 4.dp else 3.dp).toPx()
            fun position(fraction: Float): Offset = Offset(left + (right - left) * fraction, centerY)
            val start = position(0f)
            drawLine(Color.White.copy(alpha = 0.24f), start, position(1f), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.44f), start, position(buffered), stroke, StrokeCap.Round)
            drawLine(accent, start, position(played), stroke, StrokeCap.Round)
            drawCircle(Color.White, radius, position(played))
        }
    }
}

/**
 * The time under the scrubber.
 */
@Composable
internal fun PlayerTime(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "${formatPlayerTime(positionMs)} / ${formatPlayerTime(durationMs)}",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        modifier = modifier,
    )
}

/**
 * `1:02:05` or `12:04`.
 */
internal fun formatPlayerTime(milliseconds: Long): String {
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
