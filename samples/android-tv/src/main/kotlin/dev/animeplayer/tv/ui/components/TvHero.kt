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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens

@Composable
internal fun TvHero(
    featured: List<CatalogCard>,
    height: Dp,
    failed: Boolean,
    canRetry: Boolean,
    onOpen: (CatalogCard) -> Unit,
    onRetry: () -> Unit,
    onNavigateDown: () -> Boolean,
    entryRequester: FocusRequester,
    restoreFocus: Boolean,
    onFeatureFocused: () -> Unit,
    onFeatureOpened: () -> Unit,
    onCarouselFocusChange: (Boolean) -> Unit,
    onNavigateUp: () -> Unit,
    onBackdropSettled: ((String) -> Unit)? = null,
    leadShelf: @Composable () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val visibleIndex = index.takeIf { it in featured.indices } ?: 0
    val card = featured.getOrNull(visibleIndex)
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
                    .padding(top = TvDimens.TopBarHeight),
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
                            text = when {
                                !failed -> "Preparing featured titles…"
                                canRetry -> "Featured titles are unavailable right now."
                                else -> "Browse the rows below."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                        if (failed && canRetry) {
                            TvButton(
                                text = "Try again",
                                prominent = false,
                                modifier = Modifier.focusRequester(entryRequester),
                                onClick = onRetry,
                            )
                        }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(if (compact) 74.dp else 104.dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            card.featuredLogo?.let { logo ->
                                Image(
                                    bitmap = logo,
                                    contentDescription = card.title,
                                    modifier = Modifier.widthIn(max = if (compact) 246.dp else 352.dp)
                                        .fillMaxWidth().fillMaxHeight(),
                                    alignment = Alignment.BottomStart,
                                    contentScale = ContentScale.Fit,
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

                if (featured.isNotEmpty()) {
                    FeaturedDots(
                        featured = featured,
                        selectedIndex = visibleIndex,
                        entryRequester = entryRequester,
                        onSelect = {
                            index = it
                            onFeatureFocused()
                        },
                        onOpen = {
                            onFeatureOpened()
                            onOpen(it)
                        },
                        onNavigateDown = onNavigateDown,
                        onNavigateUp = onNavigateUp,
                        onFocusChange = onCarouselFocusChange,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = TvDimens.ScreenMargin, bottom = 12.dp),
                    )
                }
            }
            leadShelf()
        }
    }
}

@Composable
private fun FeaturedDots(
    featured: List<CatalogCard>,
    selectedIndex: Int,
    entryRequester: FocusRequester,
    onSelect: (Int) -> Unit,
    onOpen: (CatalogCard) -> Unit,
    onNavigateDown: () -> Boolean,
    onNavigateUp: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dots = featured.map { card -> key(card.id) { remember { FocusRequester() } } }

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.64f), RoundedCornerShape(20.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp)
            .onFocusChanged { onFocusChange(it.hasFocus) }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        featured.forEachIndexed { index, card ->
            key(card.id) {
                var focused by remember { mutableStateOf(false) }
                val active = index == selectedIndex
                val previous = dots[(index - 1 + dots.size) % dots.size]
                val next = dots[(index + 1) % dots.size]
                Box(
                    modifier = Modifier
                        .size(width = 25.dp, height = 34.dp)
                        .then(if (active) Modifier.focusRequester(entryRequester) else Modifier)
                        .focusRequester(dots[index])
                        .focusProperties {
                            if (dots.size > 1) {
                                left = previous
                                right = next
                            }
                        }
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onSelect(index)
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> onNavigateDown()
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    onNavigateUp()
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> previous.requestTvFocus()
                                KeyEvent.KEYCODE_MEDIA_NEXT -> next.requestTvFocus()
                                else -> false
                            }
                        }
                        .semantics {
                            contentDescription = "${card.title}, featured title ${index + 1} of ${featured.size}"
                            selected = active
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Open series",
                        ) { onOpen(card) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (focused) {
                        Box(Modifier.size(22.dp).border(1.5.dp, Color.White, CircleShape))
                    }
                    Box(
                        Modifier
                            .size(width = if (active) 12.dp else 6.dp, height = 6.dp)
                            .background(Color.White.copy(alpha = if (active) 1f else 0.42f), CircleShape),
                    )
                }
            }
        }
    }
}
