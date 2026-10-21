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

import dev.animeplayer.core.model.CatalogEpisode
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.CatalogPage
import dev.animeplayer.core.model.CatalogQuery
import dev.animeplayer.core.model.CatalogRelation

/**
 * A provider of catalogue metadata.
 *
 * A catalogue knows *about* series — artwork, descriptions, scores, genres — but it never
 * serves a stream. It is therefore a separate port from [AnimeSource]: the two come from
 * different sites, and joining them is the job of the application, not of the library.
 *
 * The identifiers are opaque strings. An implementation may use numbers, slugs or UUIDs —
 * the library never interprets them.
 */
public interface CatalogSource {
    /** Stable, machine readable identifier, for example `anilist`. */
    public val id: String

    /** Human readable name, for example `AniList`. */
    public val displayName: String

    /** The genres the catalogue knows about. */
    public val genres: List<String>

    /** Loads a page of entries matching [query]. */
    public suspend fun page(query: CatalogQuery): CatalogPage

    /** Loads a single entry. */
    public suspend fun media(id: String): CatalogMedia

    /** Loads several entries at once. */
    public suspend fun media(ids: List<String>): List<CatalogMedia>

    /** Loads the episodes of an entry. */
    public suspend fun episodes(id: String): List<CatalogEpisode>

    /** Loads the relations of an entry — sequels, prequels, side stories. */
    public suspend fun relations(id: String): List<CatalogRelation>

    /** Loads the entries recommended alongside [id]. */
    public suspend fun recommendations(id: String): List<CatalogMedia>
}
