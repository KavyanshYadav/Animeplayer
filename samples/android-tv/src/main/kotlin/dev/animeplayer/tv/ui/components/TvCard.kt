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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * The poster carries the artwork. Its title and details fade in over a bottom scrim only
 * while it has focus, keeping an idle rail clean and letting the artwork lead.
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
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(TvDimens.CardRadius)
    val accent = MaterialTheme.colorScheme.primary
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .then(width?.let(Modifier::width) ?: Modifier.fillMaxWidth())
            .onFocusChanged { focused = it.isFocused },
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / TvDimens.CardArtRatio)
                .clip(shape),
        ) {
            TvArtwork(
                url = artwork,
                tint = tint,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = focused,
                modifier = Modifier.align(Alignment.BottomStart),
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f)),
                            ),
                        )
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!metadata.isNullOrBlank()) {
                        Text(
                            text = metadata,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!statusLine.isNullOrBlank()) {
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (!badge.isNullOrBlank()) {
                TvBadge(
                    text = badge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }

            if (progress != null && progress > 0f) {
                TvProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
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
) {
    Column(
        modifier = modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / TvDimens.CardArtRatio)
                .clip(RoundedCornerShape(TvDimens.CardRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
