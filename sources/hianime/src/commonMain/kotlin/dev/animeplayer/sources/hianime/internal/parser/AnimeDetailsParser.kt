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
package dev.animeplayer.sources.hianime.internal.parser

import dev.animeplayer.core.model.Anime
import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.util.HtmlEntities
import dev.animeplayer.sources.hianime.internal.HtmlFragment

/**
 * Extracts the metadata of a series from its watch page.
 *
 * The watch page carries the title, the native title, the poster and the synopsis.
 */
internal object AnimeDetailsParser {

    fun parse(html: String, animeId: AnimeId): Anime {
        val heading = HtmlFragment.firstElement(html, "h2", "film-name")
        val anchor = heading?.let { HtmlFragment.elements(it.content, "a").firstOrNull() }

        val title = anchor?.attribute("title")
            ?.let { HtmlEntities.decode(it) }
            ?.takeIf { it.isNotBlank() }
            ?: animeId.slug

        val japaneseTitle = anchor?.attribute("data-jname")
            ?.let { HtmlEntities.decode(it) }
            ?.takeIf { it.isNotBlank() && it != title }

        val poster = HtmlFragment.elements(html, "img", "film-poster-img")
            .firstOrNull()
            ?.attribute("src")

        val description = HtmlFragment.textOfClass(html, "div", "film-description")
            ?.let { HtmlEntities.decode(it) }

        return Anime(
            id = animeId,
            title = title,
            japaneseTitle = japaneseTitle,
            posterUrl = poster,
            description = description,
        )
    }
}
