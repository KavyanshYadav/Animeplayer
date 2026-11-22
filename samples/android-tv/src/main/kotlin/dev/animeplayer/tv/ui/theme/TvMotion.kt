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
package dev.animeplayer.tv.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val TvMotionEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Move the rendered content while keeping layout and remote focus available immediately. */
@Composable
internal fun Modifier.tvEntrance(
    contentKey: Any? = Unit,
    durationMillis: Int = 180,
    fromX: Dp = 0.dp,
    fromY: Dp = 0.dp,
    startAlpha: Float = 0.72f,
): Modifier {
    val spatialMotion = LocalTvAnimationsEnabled.current
    val progress = remember(contentKey) { Animatable(0f) }
    LaunchedEffect(progress, spatialMotion, durationMillis) {
        progress.animateTo(1f, tween(if (spatialMotion) durationMillis else 120, easing = TvMotionEase))
    }
    return graphicsLayer {
        val remaining = 1f - progress.value
        alpha = 1f - remaining * (1f - if (spatialMotion) startAlpha else 0.84f)
        if (spatialMotion) {
            translationX = fromX.toPx() * remaining
            translationY = fromY.toPx() * remaining
        }
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
