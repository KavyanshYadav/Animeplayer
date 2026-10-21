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
package dev.animeplayer.tv

import android.content.Context
import dev.animeplayer.core.spi.AnimeSource
import dev.animeplayer.core.spi.CatalogSource
import dev.animeplayer.network.KtorHttpTransport
import dev.animeplayer.network.TransportConfig
import dev.animeplayer.network.createDefaultHttpClient
import dev.animeplayer.player.media3.Media3PlayerEngine
import dev.animeplayer.sources.anilist.AniListConfig
import dev.animeplayer.sources.anilist.AniListSource
import dev.animeplayer.sources.hianime.HianimeConfig
import dev.animeplayer.sources.hianime.HianimeSource
import dev.animeplayer.core.spi.ScheduleSource
import dev.animeplayer.tv.data.CatalogRepository
import dev.animeplayer.tv.data.FavouritesRepository
import dev.animeplayer.tv.data.LibraryRepository
import dev.animeplayer.tv.data.MatchResolver
import dev.animeplayer.tv.data.WatchHistoryRepository

/**
 * The container of the dependencies of the application.
 *
 * A hand written container keeps the build free of annotation processors, which matters
 * for a sample that people are expected to read.
 */
public class AppContainer(private val context: Context) {

    /** The HTTP transport every source speaks through. */
    private val transport by lazy {
        KtorHttpTransport(
            client = createDefaultHttpClient(),
            config = TransportConfig(userAgent = HianimeConfig.DEFAULT_USER_AGENT),
        )
    }

    /** The catalogue — the site that knows *about* the series. */
    public val catalogSource: CatalogSource by lazy {
        AniListSource(transport, AniListConfig())
    }

    /** The library — the site the streams come from. */
    public val librarySource: AnimeSource by lazy {
        HianimeSource(transport, HianimeConfig())
    }

    /** The schedule of the broadcasts. */
    public val scheduleSource: ScheduleSource by lazy {
        dev.animeplayer.sources.schedule.AnimescheduleSource(transport)
    }

    /** The catalogue of the application, with its cache. */
    public val catalog: CatalogRepository by lazy { CatalogRepository(catalogSource) }

    /** The library of the application. */
    public val library: LibraryRepository by lazy { LibraryRepository(librarySource) }

    /** The bridge between the catalogue and the library. */
    public val matcher: MatchResolver by lazy { MatchResolver(library) }

    /** The watch history of the user. */
    public val history: WatchHistoryRepository by lazy { WatchHistoryRepository(context) }

    /** The favourites of the user. */
    public val favourites: FavouritesRepository by lazy { FavouritesRepository(context) }

    /** The settings of the user — the theme, the accent and the playback preferences. */
    public val settings: SettingsRepository by lazy { SettingsRepository(context) }

    /** The playback backend. */
    public val player: Media3PlayerEngine by lazy {
        Media3PlayerEngine(context, HianimeConfig.DEFAULT_USER_AGENT)
    }
}
