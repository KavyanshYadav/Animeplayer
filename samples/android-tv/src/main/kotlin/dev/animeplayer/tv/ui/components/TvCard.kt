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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * One card of a rail.
 *
 * Artwork, title and progress share a tile so the rail stays compact.
 *
 * @param title the title of the series.
 * @param artwork the URL of the artwork.
 * @param tint the colour of the placeholder.
 * @param metadata the metadata line, for example `2026 · TV Series`.
 * @param badge a badge drawn over the artwork, for example `EP 5`.
 * @param progress how far the episode was watched, in the `0..1` range.
 * @param statusLine the line about the state of the series, for example
 *   `Episode 12 in 3 days`.
 * @param width an optional fixed width; grids can fill their column.
 */
@Composable
public fun TvCard(
    title: String,
    artwork: String?,
    tint: Color?,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    badge: String? = null,
    progress: Float? = null,
    statusLine: String? = null,
    width: Dp? = null,
    aspectRatio: Float = 1f / TvDimens.CardArtRatio,
    enabled: Boolean = true,
    onArtworkSettled: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(TvDimens.CardRadius)
    val scheme = MaterialTheme.colorScheme
    val landscape = aspectRatio > 1f
    val textOnly = !landscape && artwork.isNullOrBlank() && badge.isNullOrBlank() && progress == null
    var focused by remember { mutableStateOf(false) }
    val subtitle = if (progress != null) metadata else statusLine?.takeUnless { it == "Finished" } ?: metadata
    val scrim = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.36f to Color.Black.copy(alpha = 0.25f),
            1f to Color(0xE606101C),
        )
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(width?.let(Modifier::width) ?: Modifier.fillMaxWidth())
            .onFocusChanged { focused = it.isFocused },
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(scheme.surface, scheme.onSurface, scheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspectRatio)) {
            if (!textOnly || onArtworkSettled != null) {
                TvArtwork(
                    url = artwork,
                    tint = tint,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    onSettled = onArtworkSettled,
                )
            }
            if (!badge.isNullOrBlank()) {
                if (artwork.isNullOrBlank()) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    )
                } else {
                    TvBadge(
                        badge,
                        Modifier.align(Alignment.TopStart).padding(6.dp),
                        tone = Color(0xDB141F35),
                        contentColor = Color(0xFFF3F5FC),
                    )
                }
            }
            Column(
                modifier = if (textOnly) {
                    Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 12.dp)
                } else {
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(scrim)
                        .padding(
                            start = 9.dp,
                            end = 9.dp,
                            top = if (progress == null) 15.dp else 7.dp,
                            bottom = if (progress == null) 7.dp else 10.dp,
                        )
                },
            ) {
                val titleStyle = when {
                    textOnly -> MaterialTheme.typography.titleMedium
                    landscape && !focused && progress == null -> MaterialTheme.typography.bodyMedium
                    else -> MaterialTheme.typography.titleSmall
                }
                Text(
                    text = title,
                    style = titleStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = if (textOnly) scheme.onSurface else Color(0xFFF3F5FC),
                    maxLines = if (landscape && !focused) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank() && (!landscape || progress != null || focused || !enabled)) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (textOnly) scheme.onSurfaceVariant else Color(0xFFCFD8E8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (progress != null) {
                TvProgressBar(
                    progress,
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 9.dp, end = 9.dp, bottom = 4.dp),
                    trackColor = Color.White.copy(alpha = 0.27f),
                    color = Color(0xFFFF6E91),
                )
            }
        }
    }
}

/**
 * A top ten poster with a rank badge.
 */
@Composable
public fun TvTopCard(
    rank: Int,
    title: String,
    artwork: String?,
    tint: Color?,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    width: Dp = TvDimens.PosterCardWidth,
    aspectRatio: Float = 1f / TvDimens.CardArtRatio,
    onClick: () -> Unit = {},
) {
    TvCard(
        title = title,
        artwork = artwork,
        tint = tint,
        modifier = modifier,
        metadata = metadata,
        badge = "#$rank",
        width = width,
        aspectRatio = aspectRatio,
        onClick = onClick,
    )
}

/**
 * The tile that ends a rail.
 *
 * It opens the whole catalogue of the rail, which is the only way to see the entries that
 * do not fit on the screen.
 */
@Composable
public fun TvSeeAllCard(
    title: String,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(TvDimens.CardRadius)
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = modifier.width(width),
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / TvDimens.CardArtRatio),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "See all",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The placeholder of a card, shown while the artwork is on its way.
 */
@Composable
public fun TvCardSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = TvDimens.PosterCardWidth,
    aspectRatio: Float = 1f / TvDimens.CardArtRatio,
) {
    Column(
        modifier = modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(TvDimens.CardRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
