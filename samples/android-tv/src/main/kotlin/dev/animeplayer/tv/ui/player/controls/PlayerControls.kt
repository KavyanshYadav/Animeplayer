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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.components.TvIcon
import dev.animeplayer.tv.ui.focus.TvFocus

/**
 * A round button of the player.
 *
 * The buttons of a player are quiet: they have no background until the focus reaches
 * them, and then they grow and get a halo. That is the convention every television user
 * knows, and it keeps the picture in charge.
 */
@Composable
internal fun PlayerCircleButton(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
) {
    val shape = CircleShape
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.12f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = if (prominent) {
            TvFocus.colors(
                containerColor = accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            TvFocus.colors(
                containerColor = Color(0xC91A1C23),
                contentColor = Color.White,
            )
        },
    ) {
        Box(
            modifier = Modifier.size(if (prominent) 72.dp else 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                TvIcon(
                    icon = icon,
                    size = 22.dp,
                    contentDescription = contentDescription,
                    tint = if (prominent) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Color.White
                    },
                )
            } else if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * A round button that shows a word instead of an icon.
 */
@Composable
internal fun PlayerCircleLabel(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = CircleShape
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.12f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = Color(0xB91A1C23),
            contentColor = Color.White,
        ),
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

/**
 * A row of buttons.
 */
@Composable
internal fun PlayerButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
