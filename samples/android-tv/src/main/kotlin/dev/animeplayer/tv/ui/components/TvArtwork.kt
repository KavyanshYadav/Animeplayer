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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

/**
 * Installs the image loader of the application.
 *
 * The images of the catalogue are the same ones the desktop client shows, and they are
 * fetched over and over, so they are kept in memory and on the disk. The loader is
 * installed once, at the root of the composition.
 */
@Composable
public fun TvImageLoaderEffect() {
    val context = LocalContext.current
    setSingletonImageLoaderFactory { platformContext ->
        ImageLoader.Builder(platformContext)
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(platformContext, 0.20)
                    .build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
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
) {
    Box(modifier = modifier.background(tint ?: Color(0xFF17171A))) {
        if (!url.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(200)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
