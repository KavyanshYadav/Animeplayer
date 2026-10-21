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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.theme.TvDimens
import kotlinx.coroutines.delay

/**
 * The banner at the top of the home screen.
 *
 * The banner turns on its own, but it stops the moment the user looks at it: a banner
 * that keeps changing while it is being read is a banner that is never read.
 *
 * @param featured the series the banner shows.
 * @param height the height of the banner.
 * @param onPlay called when the user presses the play button.
 * @param onFavourite called when the user presses the heart.
 * @param isFavourite `true` when the series is marked.
 */
@Composable
public fun TvHero(
    featured: List<CatalogCard>,
    height: Dp,
    modifier: Modifier = Modifier,
    onPlay: (CatalogCard) -> Unit = {},
    onFavourite: (CatalogCard) -> Unit = {},
    isFavourite: (CatalogCard) -> Boolean = { false },
    playRequester: FocusRequester? = null,
) {
    if (featured.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(height))
        return
    }

    var index by remember(featured) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }

    // The banner turns every nine seconds, but never while it is being watched.
    LaunchedEffect(featured.size, focused) {
        if (focused || featured.size < 2) return@LaunchedEffect
        while (true) {
            delay(9_000)
            index = (index + 1) % featured.size
        }
    }

    val current = featured[index.coerceIn(featured.indices)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup(),
    ) {
        Crossfade(
            targetState = current,
            animationSpec = tween(650),
            label = "hero-artwork",
        ) { artwork ->
            TvArtwork(
                url = artwork.bannerUrl ?: artwork.posterUrl,
                tint = artwork.posterColor,
                contentDescription = artwork.title,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The scrim. The desktop client uses two gradients — one from the bottom, one
        // from the left — and so does the television client.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.42f to MaterialTheme.colorScheme.background.copy(alpha = 0.08f),
                        0.76f to MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                        1.0f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        0.48f to MaterialTheme.colorScheme.background.copy(alpha = 0.52f),
                        0.78f to MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                        1.0f to Color.Transparent,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = TvDimens.ScreenMargin, bottom = 20.dp, end = TvDimens.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedContent(
                targetState = current,
                modifier = Modifier.width(620.dp),
                transitionSpec = {
                    (fadeIn(tween(320, delayMillis = 50)) +
                        slideInVertically(tween(320)) { it / 14 }) togetherWith
                        (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 14 })
                },
                contentKey = { it.id },
                label = "hero-copy",
            ) { featuredItem ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = featuredItem.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(620.dp),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        featuredItem.metadata.forEach { badge ->
                            TvBadge(
                                text = badge,
                                tone = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    featuredItem.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(620.dp),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvButton(
                    text = "Watch now",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onPlay(current) },
                    modifier = Modifier
                        .then(
                            playRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                        ),
                )
                TvIconButton(
                    icon = if (isFavourite(current)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    onClick = { onFavourite(current) },
                    contentDescription = "Favourite",
                    scrim = true,
                )
            }
        }

        // The dots sit in the bottom right corner. They are not at the bottom centre,
        // because the D-pad would find them when the user pressed "down" to leave the
        // banner for the first rail.
        if (featured.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = TvDimens.ScreenMargin),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                featured.forEachIndexed { position, item ->
                    TvDot(
                        selected = position == index,
                        onClick = { index = position },
                    )
                }
            }
        }
    }
}

/**
 * One of the dots under the banner.
 */
@Composable
private fun TvDot(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = CircleShape
    Surface(
        onClick = onClick,
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.2f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = if (selected) {
                accent
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            },
            contentColor = Color.Transparent,
        ),
        modifier = Modifier.size(18.dp),
    ) {
        Spacer(Modifier.fillMaxSize())
    }
}

