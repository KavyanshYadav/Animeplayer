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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import dev.animeplayer.tv.ui.theme.TvMotionEase

@Composable
internal fun TvHeroBackdrop(
    url: String,
    tint: Color,
    alignment: Alignment,
    modifier: Modifier = Modifier,
    onSettled: ((String) -> Unit)? = null,
    simpleBackdrop: Boolean = false,
    preloadUrl: String? = null,
) {
    val context = LocalContext.current
    var loaded by remember(url) { mutableStateOf(false) }
    val shade = remember(url) { Animatable(0.30f) }
    LaunchedEffect(url, loaded) {
        if (loaded) shade.animateTo(0f, tween(if (simpleBackdrop) 150 else 260, easing = TvMotionEase))
    }

    BoxWithConstraints(modifier.background(tint)) {
        val backdropHeight = maxHeight
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().drawWithContent {
                drawContent()
                // Reveal the new image in a single draw pass, without blending two full-size textures.
                val obscured = shade.value
                if (obscured > 0f) drawRect(tint, alpha = obscured)
            },
            contentScale = ContentScale.Crop,
            alignment = alignment,
            onSuccess = {
                loaded = true
                onSettled?.invoke(url)
            },
            onError = { onSettled?.invoke(url) },
        )

        if (loaded && preloadUrl != null && preloadUrl != url) {
            val (width, height) = with(LocalDensity.current) { maxWidth.roundToPx() to maxHeight.roundToPx() }
            LaunchedEffect(preloadUrl, width, height) {
                prefetchTvArtwork(context, width, height, hero = true) { preloadUrl }
            }
        }
        if (loaded && !simpleBackdrop) {
            // Reuse the cached source at a tiny resolution. The resampled band gives
            // the bottom edge a soft blur without a full-screen filter pass.
            val softArtwork = remember(context, url) {
                ImageRequest.Builder(context).data(url)
                    .size(72, 40)
                    .scale(Scale.FILL)
                    .precision(Precision.EXACT)
                    .build()
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.46f)
                    .clipToBounds()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val mask = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.62f to Color.Black,
                            1f to Color.Black,
                            endY = size.height,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(mask, blendMode = BlendMode.DstIn)
                        }
                    },
            ) {
                AsyncImage(
                    model = softArtwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                        .wrapContentHeight(Alignment.Bottom, unbounded = true).height(backdropHeight),
                    contentScale = ContentScale.Crop,
                    alignment = alignment,
                )
            }
        }
    }
}
