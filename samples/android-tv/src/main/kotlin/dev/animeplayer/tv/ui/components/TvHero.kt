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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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
 * The full-screen, trending feature at the top of the home screen.
 *
 * The banner turns on its own, but it stops the moment the user looks at it: a banner
 * that keeps changing while it is being read is a banner that is never read.
 *
 * @param featured the series the banner shows.
 * @param height the height of the banner.
 * @param onPlay called when the user presses the play button.
 * @param onFavourite called when the user presses the heart.
 * @param isFavourite `true` when the series is marked.
 * @param onNavigateDown called when the user leaves the hero for the first poster rail.
 */
@Composable
public fun TvHero(
    featured: List<CatalogCard>,
    height: Dp,
    modifier: Modifier = Modifier,
    onPlay: (CatalogCard) -> Unit = {},
    onFavourite: (CatalogCard) -> Unit = {},
    isFavourite: (CatalogCard) -> Boolean = { false },
    onNavigateDown: (() -> Unit)? = null,
    playRequester: FocusRequester? = null,
) {
    if (featured.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .background(MaterialTheme.colorScheme.background)
                .then(playRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionDown &&
                        onNavigateDown != null
                    ) {
                        onNavigateDown()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Finding what’s trending",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = TvDimens.ScreenMargin),
            )
        }
        return
    }

    var index by remember(featured) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }

    // Give the artwork room to breathe, and stop rotating while any hero control has focus.
    LaunchedEffect(featured.size, focused) {
        if (focused || featured.size < 2) return@LaunchedEffect
        while (true) {
            delay(12_000)
            index = (index + 1) % featured.size
        }
    }

    val current = featured[index.coerceIn(featured.indices)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    onNavigateDown != null
                ) {
                    onNavigateDown()
                    true
                } else {
                    false
                }
            },
    ) {
        Crossfade(
            targetState = current,
            animationSpec = tween(650),
            label = "hero-artwork",
        ) { artwork ->
            HeroArtwork(artwork)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.38f),
                        0.28f to Color.Transparent,
                        0.65f to MaterialTheme.colorScheme.background.copy(alpha = 0.34f),
                        1.0f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                        0.40f to MaterialTheme.colorScheme.background.copy(alpha = 0.76f),
                        0.66f to MaterialTheme.colorScheme.background.copy(alpha = 0.20f),
                        1.0f to Color.Transparent,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = TvDimens.ScreenMargin, bottom = 76.dp, end = TvDimens.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedContent(
                targetState = current,
                modifier = Modifier.width(640.dp),
                transitionSpec = {
                    (fadeIn(tween(320, delayMillis = 50)) +
                        slideInVertically(tween(320)) { it / 14 }) togetherWith
                        (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 14 })
                },
                contentKey = { it.id },
                label = "hero-copy",
            ) { featuredItem ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "TRENDING  ·  #${featured.indexOfFirst { it.id == featuredItem.id } + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = featuredItem.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(640.dp),
                    )

                    val meta = listOfNotNull(
                        featuredItem.format?.replace('_', ' '),
                        featuredItem.year?.toString(),
                        featuredItem.score?.let { "$it%" },
                        featuredItem.episodeCount?.let { "$it episodes" },
                    ).joinToString("   ·   ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }

                    if (featuredItem.genres.isNotEmpty()) {
                        Text(
                            text = featuredItem.genres.take(3).joinToString("   ·   ").uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.74f),
                            maxLines = 1,
                        )
                    }

                    featuredItem.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(640.dp),
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
                        .then(playRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                )
                TvIconButton(
                    icon = if (isFavourite(current)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    onClick = { onFavourite(current) },
                    contentDescription = if (isFavourite(current)) "Remove from My List" else "Add to My List",
                    scrim = true,
                )
            }
        }

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

@Composable
private fun HeroArtwork(card: CatalogCard) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(card.posterColor ?: MaterialTheme.colorScheme.background),
    ) {
        if (!card.bannerUrl.isNullOrBlank()) {
            TvArtwork(
                url = card.bannerUrl,
                tint = card.posterColor,
                contentDescription = card.title,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            TvArtwork(
                url = card.posterUrl,
                tint = card.posterColor,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(28.dp),
            )
            TvArtwork(
                url = card.posterUrl,
                tint = card.posterColor,
                contentDescription = card.title,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.88f)
                    .aspectRatio(2f / 3f)
                    .padding(end = 72.dp),
                contentScale = ContentScale.Fit,
            )
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
        modifier = Modifier.size(if (selected) 24.dp else 10.dp),
    ) {
        Spacer(Modifier.fillMaxSize())
    }
}

