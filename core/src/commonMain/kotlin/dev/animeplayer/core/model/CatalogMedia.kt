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
package dev.animeplayer.core.model

/**
 * The publication state of a catalogue entry.
 *
 * Catalogue sites use a richer vocabulary than the stream sources do, so the raw value is
 * kept next to the normalised one.
 */
public data class CatalogStatus(
    public val raw: String? = null,
    public val isAiring: Boolean = false,
    public val isFinished: Boolean = false,
    public val isUpcoming: Boolean = false,
) {
    public companion object {
        /** The empty status, for entries that do not declare one. */
        public val Unknown: CatalogStatus = CatalogStatus()
    }
}

/**
 * One entry of a catalogue.
 *
 * A catalogue holds *metadata* — artwork, descriptions, scores, genres — but never a
 * stream. It is the mirror image of [dev.animeplayer.core.model.Anime], which describes
 * a series that can actually be played.
 *
 * @property id the identifier of the entry inside the catalogue.
 * @property titles every known title of the series. The first one is the primary title.
 */
public data class CatalogMedia(
    public val id: String,
    public val titles: List<String> = emptyList(),
    public val description: String? = null,
    public val posterUrl: String? = null,
    public val bannerUrl: String? = null,
    public val posterColor: String? = null,
    public val genres: List<String> = emptyList(),
    public val format: String? = null,
    public val status: CatalogStatus = CatalogStatus.Unknown,
    public val seasonYear: Int? = null,
    public val episodeCount: Int? = null,
    public val durationMinutes: Int? = null,
    /** The score of the entry, in the `0..100` range. */
    public val score: Int? = null,
    public val popularity: Int? = null,
    public val nextAiringAt: Long? = null,
    public val nextAiringEpisode: Int? = null,
) {
    /** The primary title — the first one, or an empty string. */
    public val title: String get() = titles.firstOrNull() ?: ""

    /** `true` when the series has a banner, which a hero banner needs. */
    public val hasBanner: Boolean get() = !bannerUrl.isNullOrBlank()
}

/**
 * The relation between two catalogue entries.
 *
 * @property relationType the kind of the relation, for example `PREQUEL` or `SEQUEL`.
 */
public data class CatalogRelation(
    public val media: CatalogMedia,
    public val relationType: String,
)

/**
 * One episode of a catalogue entry.
 *
 * @property number the episode number.
 * @property airDate the release date, in epoch seconds.
 */
public data class CatalogEpisode(
    public val number: Int,
    public val title: String? = null,
    public val description: String? = null,
    public val thumbnailUrl: String? = null,
    public val airDate: Long? = null,
    public val score: Int? = null,
    public val lengthMinutes: Int? = null,
)

/**
 * A page of catalogue entries.
 *
 * @property hasNextPage whether more entries can be fetched.
 * @property total the total number of entries matching the query.
 */
public data class CatalogPage(
    public val media: List<CatalogMedia> = emptyList(),
    public val hasNextPage: Boolean = false,
    public val currentPage: Int = 1,
    public val total: Int = 0,
)

/**
 * The filters of a catalogue query.
 *
 * Every field is optional; a `null` means "do not filter on this".
 */
public data class CatalogQuery(
    public val search: String? = null,
    public val genre: String? = null,
    public val season: String? = null,
    public val seasonYear: Int? = null,
    public val format: String? = null,
    public val status: String? = null,
    /** The sort order, in the catalogue's own vocabulary, for example `TRENDING_DESC`. */
    public val sort: String? = null,
    public val page: Int = 1,
    public val perPage: Int = 24,
)

/**
 * The sort orders a catalogue understands.
 *
 * The values are the ones the reference catalogue uses; a source that does not understand
 * one is free to ignore it.
 */
public object CatalogSort {
    public const val Trending: String = "TRENDING_DESC"
    public const val Popularity: String = "POPULARITY_DESC"
    public const val Score: String = "SCORE_DESC"
    public const val StartDate: String = "START_DATE_DESC"
    public const val Favourites: String = "FAVOURITES_DESC"
    public const val Episodes: String = "EPISODE_DESC"
}
