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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.theme.LocalTvAnimationsEnabled
import dev.animeplayer.tv.ui.theme.LocalTvLayout

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

/** Shared navigation. Selection and remote focus have independent treatments. */
@Composable
public fun TvTopBar(
    entries: List<TvNavEntry>,
    current: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    onDown: (() -> Boolean)? = null,
    backgroundAlpha: Float = 1f,
    settingsSelected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val compact = LocalTvLayout.current.width < 820.dp
    val alpha by animateFloatAsState(
        targetValue = backgroundAlpha.coerceIn(0f, 1f),
        animationSpec = if (LocalTvAnimationsEnabled.current) tween(160) else snap(),
        label = "navigation backdrop",
    )
    val overArtwork = alpha < 0.5f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TvDimens.TopBarHeight)
            .drawBehind { drawRect(scheme.background.copy(alpha = alpha)) }
            .padding(start = TvDimens.ScreenMargin, end = TvDimens.ScreenMargin, top = 6.dp)
            .focusGroup()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown &&
                    onDown?.invoke() == true
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(end = if (compact) 8.dp else 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(27.dp).clip(RoundedCornerShape(7.dp)).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                TvIcon(Icons.Filled.PlayArrow, size = 19.dp, tint = if (overArtwork) Color.Black else scheme.onPrimary)
            }
            if (!compact) {
                Text(
                    "animeplayer",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (overArtwork) Color.Black else scheme.onBackground,
                )
            }
        }

        entries.forEach { entry ->
            TvNavItem(entry.label, entry.icon, entry.route == current, compact, overArtwork, onClick = { onSelect(entry.route) })
        }
        Spacer(Modifier.weight(1f))

        if (onSettings != null) {
            TvNavItem("Settings", Icons.Filled.Settings, settingsSelected, true, overArtwork, onClick = onSettings, iconOnly = true)
        }
    }
}

@Composable
private fun TvNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    compact: Boolean,
    overArtwork: Boolean,
    onClick: () -> Unit,
    iconOnly: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val scheme = MaterialTheme.colorScheme
    val content = when {
        overArtwork -> Color.Black
        selected -> scheme.onPrimaryContainer
        else -> scheme.onBackground
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1f),
        border = TvFocus.border(shape, color = if (overArtwork) Color.Black.copy(alpha = 0.82f) else TvFocus.ringColor),
        colors = TvFocus.colors(
            containerColor = when {
                overArtwork || !selected -> Color.Transparent
                else -> scheme.primaryContainer
            },
            contentColor = content,
            focusedContainerColor = if (overArtwork) Color.Transparent else scheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.height(36.dp).padding(horizontal = if (compact) 10.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (!compact || iconOnly) {
                TvIcon(icon, size = 17.dp, tint = content, contentDescription = label.takeIf { iconOnly })
            }
            if (!iconOnly) Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
