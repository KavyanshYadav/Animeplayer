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
package dev.animeplayer.tv.data

import dev.animeplayer.core.model.Anime
import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.EpisodeList
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.QualityPreference
import dev.animeplayer.core.spi.AnimeSource

/**
 * The library of the application — the site the streams actually come from.
 *
 * The catalogue knows *about* series, the library knows the series that can be watched.
 * They are two different sites, and the two are joined by [MatchResolver].
 */
public class LibraryRepository(private val source: AnimeSource) {

    /** The identifier of the source. */
    public val id: String get() = source.id

    /** Searches the library. */
    public suspend fun search(query: String): List<Anime> = source.search(query)

    /** Loads the metadata of a series. */
    public suspend fun anime(slug: String): Anime = source.anime(AnimeId(slug, numericIdOf(slug)))

    /** Loads the episodes of a series. */
    public suspend fun episodes(slug: String): EpisodeList =
        source.episodes(AnimeId(slug, numericIdOf(slug)))

    /**
     * Resolves an episode into something a player can stream.
     *
     * @param quality the preferred quality, or `null` for the best one.
     */
    public suspend fun resolve(
        slug: String,
        episode: Episode,
        audioTrack: AudioTrack = AudioTrack.Sub,
        quality: String? = null,
    ): PlayableSource = source.resolve(
        id = AnimeId(slug, numericIdOf(slug)),
        episode = episode,
        audioTrack = audioTrack,
        quality = when (quality?.lowercase()) {
            null, "", "best" -> QualityPreference.Best
            "worst" -> QualityPreference.Worst
            else -> QualityPreference.Height(quality.toIntOrNull() ?: 0)
        },
    )

    /**
     * The numeric part of a slug.
     *
     * The upstream site keys its episode list by the number at the end of the slug, for
     * example `one-piece-1` is the series `1`.
     */
    public fun numericIdOf(slug: String): String =
        slug.substringAfterLast('-', slug).takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: "0"
}
