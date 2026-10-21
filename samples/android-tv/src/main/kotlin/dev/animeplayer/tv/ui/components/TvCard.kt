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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * One card of a rail.
 *
 * The card is a wide rectangle, not a poster. A television screen is sixteen by nine, and
 * a portrait card wastes half of the tile and squeezes the title into three lines that
 * nobody reads from three metres away. The wide card carries the title on two lines and
 * the metadata on one.
 *
 * @param title the title of the series.
 * @param artwork the URL of the artwork.
 * @param tint the colour of the placeholder.
 * @param metadata the metadata line, for example `2026 · TV Series`.
 * @param badge a badge drawn over the artwork, for example `EP 5`.
 * @param progress how far the episode was watched, in the `0..1` range.
 * @param statusLine the line about the state of the series, for example
 *   `Episode 12 in 3 days`.
 * @param width the width of the card.
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
            // The card is opaque: the glow of the focus is drawn behind the element, and
            // a transparent card would let it bleed through the title.
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                TvArtwork(
                    url = artwork,
                    tint = tint,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                )

                if (badge != null) {
                    TvBadge(
                        text = badge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
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

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            if (metadata != null) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }

            // The state of the series. A series that is still running says so, and when
            // the next episode comes — the one line a streaming home screen is built on.
            if (statusLine != null) {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * One card of the "Top 10" rail.
 *
 * The number is drawn behind the artwork, the way the streaming services do it. The card
 * is wider than the ordinary one, because the number needs room.
 */
@Composable
public fun TvTopCard(
    rank: Int,
    title: String,
    artwork: String?,
    tint: Color?,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    width: Dp = 180.dp,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(TvDimens.CardRadius)
    val accent = MaterialTheme.colorScheme.primary

    // The number sits behind the artwork and pokes out on the left, the way the
    // streaming services draw the ten most watched series.
    //
    // The artwork is inset to make room for the number, so the number has to be centred
    // on the artwork and not on the whole card.
    val inset = 26.dp
    val artHeight = (width - inset) * 9f / 16f

    Box(
        modifier = modifier.width(width),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 68.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-14).dp, y = (artHeight * 0.5f - 26.dp).coerceAtLeast(0.dp))
                .width(62.dp),
        )

        Surface(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = inset)
                .fillMaxWidth(),
            shape = TvFocus.shape(shape),
            scale = TvFocus.scale(),
            glow = TvFocus.glow(accent),
            border = TvFocus.border(shape),
            colors = TvFocus.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                ) {
                    TvArtwork(
                        url = artwork,
                        tint = tint,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                if (metadata != null) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
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
                .aspectRatio(16f / 9f),
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
    width: Dp = 140.dp,
) {
    Column(
        modifier = modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(TvDimens.CardRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
