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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Size
import coil3.size.Scale
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Installs the image loader of the application.
 *
 * The images of the catalogue are the same ones the desktop client shows, and they are
 * fetched over and over, so they are kept in memory and on the disk. The loader is
 * installed once, at the root of the composition.
 */
@Composable
public fun TvImageLoaderEffect(isMemoryConstrained: Boolean) {
    setSingletonImageLoaderFactory { platformContext ->
        ImageLoader.Builder(platformContext)
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizeBytes(minOf(
                        Runtime.getRuntime().maxMemory() / 8,
                        (if (isMemoryConstrained) 20L else 48L) * 1024 * 1024,
                    ))
                    .build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(platformContext.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(if (isMemoryConstrained) 2 else 4))
            .crossfade(false)
            .maxBitmapSize(if (isMemoryConstrained) Size(1280, 720) else Size(1920, 1080))
            .build()
    }
}

/**
 * The artwork of a card.
 *
 * The placeholder is tinted with the colour the catalogue publishes for the poster, so a
 * rail never looks like a row of empty boxes while the images arrive.
 *
 * @param url the URL of the artwork.
 * @param tint the colour of the placeholder.
 */
@Composable
public fun TvArtwork(
    url: String?,
    tint: Color?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    hero: Boolean = false,
    onSettled: (() -> Unit)? = null,
) {
    if (url.isNullOrBlank() && onSettled != null) {
        LaunchedEffect(onSettled) { onSettled() }
    }
    Box(modifier = modifier.background(tint ?: androidx.tv.material3.MaterialTheme.colorScheme.surface)) {
        if (!url.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(context, url, hero) {
                tvArtworkRequest(context, url, hero).build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment,
                onSuccess = onSettled?.let { settled -> { settled() } },
                onError = onSettled?.let { settled -> { settled() } },
            )
        }
    }
}

private fun tvArtworkRequest(context: Context, url: String, hero: Boolean) =
    ImageRequest.Builder(context).data(url).apply {
        if (!hero) maxBitmapSize(Size(560, 800))
    }

// Visible requests bypass this queue. Keep speculative work to one image at a time.
private val artworkPrefetch = Mutex()

internal suspend fun prefetchTvArtwork(
    context: Context,
    width: Int,
    height: Int,
    hero: Boolean = false,
    artwork: suspend () -> String?,
) {
    if (width <= 0 || height <= 0) return
    delay(180)
    artworkPrefetch.withLock {
        val url = artwork()?.takeIf(String::isNotBlank) ?: return
        SingletonImageLoader.get(context).execute(
            tvArtworkRequest(context, url, hero).size(width, height).scale(Scale.FILL).build(),
        )
    }
}
