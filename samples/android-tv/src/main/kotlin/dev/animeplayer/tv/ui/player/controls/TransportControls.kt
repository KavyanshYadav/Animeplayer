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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus

/**
 * The transport of the player.
 *
 * The play and pause button is the anchor of the focus graph: it is the way down to the
 * scrubber and the way up to the top row, so the user always knows where the picture
 * will take them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TransportControls(
    isPlaying: Boolean,
    focus: DirectionalFocus,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerCircleLabel(
            label = "−10",
            onClick = onSeekBack,
        )

        // The requester and the links belong on the button itself.
        PlayerCircleButton(
            icon = Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onTogglePlay,
            prominent = true,
            modifier = Modifier
                .focusRequester(focus.transport)
                .focusProperties {
                    up = focus.top
                    down = focus.scrubber
                },
        )

        PlayerCircleLabel(
            label = "+10",
            onClick = onSeekForward,
        )
    }
}
