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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus

/**
 * The scrubber of the player.
 *
 * It is a control, not a picture: the D-pad moves along it and the position follows
 * immediately, the way it does in every streaming application on a television. The
 * reference implementations bind the thumb to the position of the player, so the bar
 * never lies about where the playback is.
 *
 * @param positionMs the current position.
 * @param durationMs the length of the episode.
 * @param onSeek called with the new position.
 * @param onSeekPreview called while the user moves along the bar, so the time under the
 *   thumb can be shown.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvScrubber(
    positionMs: Long,
    durationMs: Long,
    focus: DirectionalFocus,
    onSeek: (Long) -> Unit,
    onSeekPreview: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    stepMs: Long = 10_000,
) {
    val accent = MaterialTheme.colorScheme.primary
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    var width by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = { },
        modifier = modifier
            .height(40.dp)
            .focusRequester(focus.scrubber)
            .focusProperties {
                up = focus.transport
                down = focus.menu
            }
            .onSizeChanged { width = it.width }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    // The vertical movement of the focus graph is handled here rather
                    // than by the spatial search, so the user always knows where the
                    // focus will go.
                    Key.DirectionDown -> {
                        focus.focusOn(focus.menu)
                        true
                    }

                    Key.DirectionUp -> {
                        focus.focusOn(focus.transport)
                        true
                    }

                    Key.DirectionLeft -> {
                        val target = (positionMs - stepMs).coerceAtLeast(0L)
                        onSeek(target)
                        onSeekPreview?.invoke(target)
                        true
                    }

                    Key.DirectionRight -> {
                        val target = (positionMs + stepMs).coerceAtMost(durationMs.coerceAtLeast(0L))
                        onSeek(target)
                        onSeekPreview?.invoke(target)
                        true
                    }

                    else -> false
                }
            },
        shape = TvFocus.shape(RoundedCornerShape(4.dp)),
        scale = TvFocus.scale(focused = 1f),
        // The bar itself is never ringed — a ring around a bar that is a metre long
        // looks like a mistake. The thumb grows slightly to show focus without a halo.
        glow = TvFocus.glow(accent),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border.None,
            pressedBorder = androidx.tv.material3.Border.None,
            disabledBorder = androidx.tv.material3.Border.None,
            focusedDisabledBorder = androidx.tv.material3.Border.None,
        ),
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
