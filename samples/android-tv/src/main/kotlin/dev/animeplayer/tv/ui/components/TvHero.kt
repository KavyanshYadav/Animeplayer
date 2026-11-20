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

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.tmdbTitleLogo
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens

@Composable
internal fun TvHero(
    featured: List<CatalogCard>,
    height: Dp,
    failed: Boolean,
    onOpen: (CatalogCard) -> Unit,
    onRetry: () -> Unit,
    onNavigateDown: () -> Boolean,
    entryRequester: FocusRequester,
    restoreFocus: Boolean,
    onFeatureFocused: () -> Unit,
    onFeatureOpened: () -> Unit,
    onBackdropSettled: ((String) -> Unit)? = null,
    leadShelf: @Composable () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val visibleIndex = index.takeIf { it in featured.indices } ?: 0
    val card = featured.getOrNull(visibleIndex)
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val background = scheme.background
    val compact = LocalTvLayout.current.height < 450.dp
    val bottom = if (compact) 41.dp else 54.dp

    LaunchedEffect(restoreFocus, card != null) {
        if (restoreFocus && card != null) {
            withFrameNanos { }
            entryRequester.requestTvFocus()
        }
    }

    Box(Modifier.fillMaxWidth()) {
        (card?.bannerUrl ?: card?.posterUrl)?.let { url ->
            TvHeroBackdrop(
                url = url,
                modifier = Modifier.matchParentSize(),
                tint = scheme.surface,
                alignment = if (card?.bannerUrl != null) Alignment.Center else Alignment.TopCenter,
                onSettled = onBackdropSettled,
            )
        }
        Box(
            Modifier.matchParentSize().drawWithCache {
                val titleShade = Brush.radialGradient(
                    0f to background.copy(alpha = 0.88f),
                    0.48f to background.copy(alpha = 0.66f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.08f, size.height * 0.65f),
                    radius = size.height * 0.64f,
                )
                onDrawBehind { drawRect(titleShade) }
            },
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.53f to Color.Transparent,
                    0.70f to background.copy(alpha = 0.25f),
                    0.85f to background.copy(alpha = 0.64f),
                    0.96f to background.copy(alpha = 0.92f),
                    1f to background,
                ),
            ),
        )
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(top = TvDimens.TopBarHeight)
                    .focusRequester(entryRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFeatureFocused()
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.key == Key.DirectionDown) return@onPreviewKeyEvent onNavigateDown()
                        if (featured.size < 2) return@onPreviewKeyEvent false
                        val step = when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> -1
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> 1
                            else -> return@onPreviewKeyEvent false
                        }
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            index = (visibleIndex + step + featured.size) % featured.size
                        }
                        true
                    }
                    .semantics { contentDescription = "Featured carousel" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = card != null || failed,
                        onClickLabel = if (failed) "Retry featured titles" else "Open series",
                    ) {
                        if (card != null) {
                            onFeatureOpened()
                            onOpen(card)
                        } else {
                            onRetry()
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(if (compact) 0.72f else 0.64f)
                        .padding(start = TvDimens.ScreenMargin, bottom = bottom, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (card == null) {
                        Text(
                            text = if (failed) "Discover anime" else "Find your next anime",
                            style = MaterialTheme.typography.displaySmall,
                            color = scheme.onBackground,
                        )
                        Text(
                            text = if (failed) "Featured titles are unavailable right now." else "Loading featured titles…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(if (compact) 74.dp else 104.dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            TvTitleLogo(
                                url = card.tmdbTitleLogo(),
                                title = card.title,
                                modifier = Modifier.widthIn(max = if (compact) 246.dp else 352.dp)
                                    .fillMaxWidth().fillMaxHeight(),
                            ) {
                                Text(
                                    text = card.title,
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontSize = if (compact) 31.sp else 42.sp,
                                        lineHeight = if (compact) 35.sp else 46.sp,
                                    ),
                                    color = scheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        val meta = listOfNotNull(
                            card.year?.toString(),
                            card.genres.firstOrNull(),
                            card.episodeCount?.let { if (it == 1) "1 episode" else "$it episodes" },
                            card.score?.let { "$it% rating" },
                        ).joinToString("  ·  ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        card.description?.lineSequence()?.firstOrNull { it.isNotBlank() }?.takeUnless { compact }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onBackground.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (featured.size > 1) {
                    FeaturedPosition(
                        index = visibleIndex,
                        count = featured.size,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = TvDimens.ScreenMargin, bottom = 22.dp),
                    )
                }
                if (focused) {
                    Box(
                        Modifier.matchParentSize()
                            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 9.dp)
                            .border(2.dp, scheme.onBackground.copy(alpha = 0.85f), RoundedCornerShape(14.dp)),
                    )
                }
            }
            leadShelf()
        }
    }
}

@Composable
private fun FeaturedPosition(index: Int, count: Int, modifier: Modifier = Modifier) {
    val selected = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.24f)
    Canvas(modifier.size(width = (count * 10).dp, height = 8.dp)) {
        val step = size.width / count
        repeat(count) { dot ->
            drawCircle(
                color = if (dot == index) selected else track,
                radius = (if (dot == index) 3.4.dp else 2.4.dp).toPx(),
                center = androidx.compose.ui.geometry.Offset(step * (dot + 0.5f), center.y),
            )
        }
    }
}
