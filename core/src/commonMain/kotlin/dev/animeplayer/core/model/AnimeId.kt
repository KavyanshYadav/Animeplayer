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
 * Identifies a series (or a season of a series) on a specific [dev.animeplayer.core.spi.AnimeSource].
 *
 * @property slug the human readable, URL friendly identifier used by the upstream site
 *   (for example `one-piece-1`).
 * @property numericId the trailing numeric component of [slug] (for example `1`). Most
 *   upstream APIs expose the series under this id even though search results only ever
 *   contain the slug.
 */
public data class AnimeId(
    public val slug: String,
    public val numericId: String,
) {
    init {
        require(slug.isNotBlank()) { "slug must not be blank" }
        require(numericId.isNotBlank()) { "numericId must not be blank" }
    }

    override fun toString(): String = slug
}
