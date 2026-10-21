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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus

/**
 * The top row of the player.
 *
 * The back button is the way out, so it is the first stop of the focus, and it is the
 * only way *down* into the player — the focus graph says so, and the spatial search is
 * never asked.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TopControls(
    title: String,
    episodeLabel: String,
    focus: DirectionalFocus,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        // The requester and the links belong on the button itself: a requester on a
        // container that is not focusable cannot take the focus.
        PlayerCircleButton(
            icon = Icons.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier
                .focusRequester(focus.top)
                .focusProperties {
                    down = focus.transport
                },
        )

        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = episodeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        PlayerCircleButton(
            icon = Icons.Filled.Info,
            contentDescription = "Information",
            onClick = onInfo,
        )
    }
}
