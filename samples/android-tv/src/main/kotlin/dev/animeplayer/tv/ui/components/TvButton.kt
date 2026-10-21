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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus

/**
 * A button.
 *
 * The button of a television is bigger than the button of a phone, because the user
 * presses it with a remote from three metres away.
 */
@Composable
public fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    prominent: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = TvFocus.buttonShape(shape),
        scale = TvFocus.buttonScale(),
        glow = TvFocus.buttonGlow(accent),
        border = TvFocus.buttonBorder(shape),
        colors = if (prominent) {
            ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                pressedContainerColor = MaterialTheme.colorScheme.primary,
                pressedContentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContentColor = MaterialTheme.colorScheme.onSurface,
                pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                pressedContentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        if (icon != null) {
            TvIcon(icon = icon, size = 18.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A button that only shows an icon.
 */
@Composable
public fun TvIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    prominent: Boolean = false,
    scrim: Boolean = false,
    iconTint: Color = Color.Unspecified,
) {
    val shape = RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary

    Button(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        enabled = enabled,
        shape = TvFocus.buttonShape(shape),
        scale = TvFocus.buttonScale(focused = 1.1f),
        glow = TvFocus.buttonGlow(accent),
        border = TvFocus.buttonBorder(shape),
        colors = if (prominent) {
            ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else if (scrim) {
            ButtonDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
            )
        } else {
            ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        TvIcon(icon = icon, size = 20.dp, contentDescription = contentDescription, tint = iconTint)
    }
}

/**
 * An icon.
 *
 * The icon is drawn from its vector description, so the application does not need a
 * second Material library.
 */
@Composable
public fun TvIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified,
) {
    val painter = rememberVectorPainter(image = icon)
    androidx.compose.foundation.Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = if (tint == Color.Unspecified) null else androidx.compose.ui.graphics.ColorFilter.tint(tint),
    )
}

/**
 * A row of buttons.
 */
@Composable
public fun TvButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
