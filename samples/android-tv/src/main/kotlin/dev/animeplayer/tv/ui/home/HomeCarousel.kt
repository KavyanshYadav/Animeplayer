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
package dev.animeplayer.tv.ui.home

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.tv.data.TmdbCatalog
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** Prepare a small lineup without decoding backdrops the user may never see. */
internal suspend fun prepareHomeCarousel(
    candidates: List<CatalogMedia>,
    tmdb: TmdbCatalog,
    context: Context,
    logoWidth: Int,
    logoHeight: Int,
): List<CatalogCard> {
    val ready = mutableListOf<CatalogCard>()
    val maxSlides = 12
    withTimeoutOrNull(18.seconds) {
        for (batch in candidates.take(30).chunked(3)) {
            val slides = coroutineScope {
                batch.map { media ->
                    async {
                        val logo = tmdb.titleLogo(media) ?: return@async null
                        val image = loadFeaturedLogo(context, logo, logoWidth, logoHeight) ?: return@async null
                        val artwork = tmdb.artwork(media)
                        val card = media.toCard()
                        card.copy(
                            bannerUrl = artwork?.backdrop ?: card.bannerUrl,
                            posterUrl = artwork?.poster ?: card.posterUrl,
                            featuredLogo = image,
                            featuredLogoUrl = logo,
                        ).takeIf { it.bannerUrl != null || it.posterUrl != null }
                    }
                }.awaitAll()
            }
            ready += slides.filterNotNull().take(maxSlides - ready.size)
            if (ready.size == maxSlides) break
        }
    }
    return ready
}

/** Decode saved logos locally before publishing their slides. */
internal suspend fun restoreHomeCarousel(
    cached: List<FeaturedSnapshot>,
    context: Context,
    logoWidth: Int,
    logoHeight: Int,
): List<CatalogCard> = buildList {
    for (slide in cached.take(4)) {
        val logo = loadFeaturedLogo(context, slide.logo, logoWidth, logoHeight, cacheOnly = true) ?: continue
        add(slide.media.toMedia().toCard().copy(featuredLogo = logo, featuredLogoUrl = slide.logo))
    }
}

private suspend fun loadFeaturedLogo(
    context: Context,
    url: String,
    width: Int,
    height: Int,
    cacheOnly: Boolean = false,
): ImageBitmap? {
    val request = ImageRequest.Builder(context).data(url).size(width, height).scale(Scale.FIT)
        .apply { if (cacheOnly) networkCachePolicy(CachePolicy.DISABLED) }
        .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
    return (result?.image as? BitmapImage)?.bitmap?.asImageBitmap()
}
