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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * One entry of the navigation bar.
 *
 * @property route the route the entry leads to.
 * @property label the name of the entry.
 * @property icon the icon of the entry.
 */
public data class TvNavEntry(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * The navigation bar of the application.
 *
 * The desktop client puts a narrow rail of icons on the left. On a television that rail
 * is unreadable from three metres away, so the entries move to the top, where they get a
 * name and enough room to be pressed.
 *
 * Selection uses a small accent underline; focus stays visible through the shared ring
 * and glow without turning the whole navigation bar into a bright block.
 *
 * @param entries the entries of the bar.
 * @param current the route that is open.
 * @param onSelect called with the route of the entry that was pressed.
 * @param onSettings called when the settings button is pressed.
 * @param backgroundAlpha the opacity of the bar backdrop.
 */
@Composable
public fun TvTopBar(
    entries: List<TvNavEntry>,
    current: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    onDown: (() -> Boolean)? = null,
    backgroundAlpha: Float = 1f,
) {
    val animatedBackground by animateFloatAsState(
        targetValue = backgroundAlpha.coerceIn(0f, 1f),
        animationSpec = tween(240),
        label = "top-bar-backdrop",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TvDimens.TopBarHeight)
            .background(MaterialTheme.colorScheme.background.copy(alpha = animatedBackground)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TvDimens.TopBarHeight)
                .padding(horizontal = TvDimens.ScreenMargin)
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    // The navigation bar hands the focus to the content, so the user
                    // never has to hunt for the first stop.
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        onDown?.invoke() == true
                    } else {
                        false
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "animeplayer",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 16.dp),
            )

            entries.forEach { entry ->
                TvNavItem(
                    entry = entry,
                    selected = entry.route == current,
                    onClick = { onSelect(entry.route) },
                )
            }

            Spacer(Modifier.weight(1f))

            if (onSettings != null) {
                TvIconButton(
                    icon = Icons.Filled.Settings,
                    onClick = onSettings,
                    contentDescription = "Settings",
                    iconTint = Color.White,
                )
            }
        }
    }
}

/**
 * One entry of the navigation bar.
 */
@Composable
private fun TvNavItem(
    entry: TvNavEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.05f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = if (selected) 1f else 0.84f),
            focusedContainerColor = Color.White.copy(alpha = 0.10f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.16f),
            pressedContentColor = Color.White,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvIcon(
                    icon = entry.icon,
                    size = 18.dp,
                    tint = Color.White.copy(alpha = if (selected) 1f else 0.84f),
                )
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(32.dp)
                    .height(2.dp)
                    .background(if (selected) accent else Color.Transparent),
            )
        }
    }
}

/**
 * A chip — a small, focusable label.
 *
 * The chips are the television replacement of the drop-downs of the desktop client: a
 * chip opens a list, the list is scrolled with the D-pad.
 */
@Composable
public fun TvChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    trailing: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.04f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = if (active) {
            TvFocus.colors(
                containerColor = accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            TvFocus.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A row of chips that scrolls with the D-pad.
 *
 * The row is a scrollable container, so the focus brings a chip into view on its own —
 * there is no scroll bar and no button.
 */
@Composable
public fun TvChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = TvDimens.ScreenMargin),
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * A small square used to carry a number, for example the rank of a card.
 */
@Composable
public fun TvNumberBadge(
    number: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
