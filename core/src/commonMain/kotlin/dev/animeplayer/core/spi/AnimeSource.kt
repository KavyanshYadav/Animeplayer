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
package dev.animeplayer.core.spi

import dev.animeplayer.core.model.Anime
import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.EpisodeList
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.QualityPreference

/**
 * A provider of anime metadata and streams.
 *
 * Every upstream site is implemented as one [AnimeSource]. The rest of the library never
 * talks to a website directly, so adding a new site means adding one implementation of this
 * interface — no changes anywhere else.
 */
public interface AnimeSource {
    /** Stable, machine readable identifier, for example `hianime`. */
    public val id: String

    /** Human readable name, for example `HiAnime`. */
    public val displayName: String

    /** Searches for series matching [query]. */
    public suspend fun search(query: String): List<Anime>

    /** Loads the metadata of a single series. */
    public suspend fun anime(id: AnimeId): Anime

    /** Loads the complete episode list of a series. */
    public suspend fun episodes(id: AnimeId): EpisodeList

    /**
     * Resolves an episode into something a player can stream.
     *
     * @param audioTrack the desired audio track.
     * @param quality which rendition to prefer.
     */
    public suspend fun resolve(
        id: AnimeId,
        episode: Episode,
        audioTrack: AudioTrack = AudioTrack.Sub,
        quality: QualityPreference = QualityPreference.Best,
    ): PlayableSource
}
