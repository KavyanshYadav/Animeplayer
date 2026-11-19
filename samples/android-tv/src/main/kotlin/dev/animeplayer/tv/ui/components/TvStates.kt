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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.theme.TvDimens

@Composable
internal fun TvPageHeading(title: String, caption: String? = null, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = scheme.onBackground)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * The state of a screen that is still loading.
 */
@Composable
public fun TvLoading(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvSpinner()
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A spinner.
 */
@Composable
public fun TvSpinner(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
        label = "loading rotation",
    )
    Canvas(modifier.size(34.dp)) {
        val line = 3.dp.toPx()
        val inset = Offset(line / 2, line / 2)
        val bounds = Size(size.width - line, size.height - line)
        drawArc(accent.copy(alpha = 0.24f), 0f, 360f, false, inset, bounds, style = Stroke(line))
        drawArc(accent, rotation, 86f, false, inset, bounds, style = Stroke(line, cap = StrokeCap.Round))
    }
}

/**
 * The state of a screen that has nothing to show.
 */
@Composable
public fun TvEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The state of a screen that failed.
 */
@Composable
public fun TvErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocus, onRetry != null) {
        if (autoFocus && onRetry != null) {
            withFrameNanos { }
            retryFocus.requestTvFocus()
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            TvButtonRow {
                if (onRetry != null) {
                    TvButton(text = "Try again", modifier = Modifier.focusRequester(retryFocus), onClick = onRetry)
                }
                if (onBack != null) {
                    TvButton(text = "Back", onClick = onBack, prominent = false)
                }
            }
        }
    }
}

/**
 * A row of placeholder blocks, shown while the content of a rail is on its way.
 */
@Composable
public fun TvSkeletonRow(
    count: Int,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 140.dp,
    aspectRatio: Float = 1f / TvDimens.CardArtRatio,
    horizontalPadding: androidx.compose.ui.unit.Dp = TvDimens.ScreenMargin,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
    ) {
        items(count) {
            TvCardSkeleton(width = width, aspectRatio = aspectRatio)
        }
    }
}

/** The colour of a placeholder. */
internal val PlaceholderColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
