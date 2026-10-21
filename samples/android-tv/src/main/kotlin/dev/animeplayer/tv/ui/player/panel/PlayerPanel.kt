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
package dev.animeplayer.tv.ui.player.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.components.TvArtwork
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus

/**
 * One entry of a panel.
 */
public data class PanelItem(
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val artworkUrl: String? = null,
    val artworkFallback: String? = null,
)

/**
 * The panel of the player.
 *
 * The panel slides in from the right and holds a list — the episodes, the audio, the
 * subtitles, the quality. It is the television replacement of the drop-down menus of the
 * desktop client, which a remote can never reach.
 *
 * The panel takes the focus when it opens, and pressing *left* on the first entry closes
 * it again, so the user can always get out of it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun PlayerPanel(
    title: String,
    items: List<PanelItem>,
    focus: DirectionalFocus,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 440.dp,
) {
    // The panel asks for the focus as soon as it appears.
    LaunchedEffect(Unit) {
        runCatching { focus.panel.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.25f to Color.Black.copy(alpha = 0.6f),
                    1f to Color.Black.copy(alpha = 0.85f),
                ),
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.padding(top = 16.dp))

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focus.panel)
                    .focusProperties {
                        // The focus leaves the panel to the left, never to the right.
                        right = androidx.compose.ui.focus.FocusRequester.Cancel
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.key == Key.DirectionLeft) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items.size) { index ->
                    val item = items[index]
                    PanelRow(
                        item = item,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(focus.panel)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

/**
 * One entry of a panel.
 */
@Composable
private fun PanelRow(
    item: PanelItem,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary

    androidx.tv.material3.Surface(
        onClick = item.onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = item.enabled,
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.02f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = if (item.selected) {
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.artworkUrl != null || item.artworkFallback != null) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    TvArtwork(
                        url = item.artworkUrl,
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (item.artworkUrl.isNullOrBlank()) {
                        Text(
                            text = item.artworkFallback.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.selected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.trailing != null) {
                Text(
                    text = item.trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.selected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
