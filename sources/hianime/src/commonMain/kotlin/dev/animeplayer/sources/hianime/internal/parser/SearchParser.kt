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
 * Extracts the search results from the search page.
 *
 * The page contains the actual results followed by a sidebar with the ten most popular
 * series, which repeats entries from the result list. The original command line tool cut
 * the document at `id="main-sidebar"` for exactly that reason.
 */
internal object SearchParser {

    private const val SIDEBAR_MARKER = "id=\"main-sidebar\""

    fun parse(html: String): List<Anime> {
        val truncated = html.substringBefore(SIDEBAR_MARKER)

        val results = LinkedHashMap<String, Anime>()

        HtmlFragment.elements(truncated, "h3", "film-name").forEach { heading ->
            val anchor = HtmlFragment.elements(heading.content, "a").firstOrNull() ?: return@forEach
            val href = anchor.attribute("href") ?: return@forEach
            val title = anchor.attribute("title") ?: return@forEach

            val slug = slugOf(href) ?: return@forEach
            val animeId = AnimeId(slug = slug, numericId = numericIdOf(slug))

            results.getOrPut(slug) {
                Anime(
                    id = animeId,
                    title = HtmlEntities.decode(title),
                    japaneseTitle = anchor.attribute("data-jname")
                        ?.let { HtmlEntities.decode(it) }
                        ?.takeIf { it.isNotBlank() && it != HtmlEntities.decode(title) },
                )
            }
        }

        return results.values.toList()
    }

    /** The last path segment of a result link, for example `one-piece-1`. */
    internal fun slugOf(href: String): String? {
        val withoutQuery = href.substringBefore('?').substringBefore('#')
        val segment = withoutQuery.trimEnd('/').substringAfterLast('/')
        return segment.takeIf { it.isNotBlank() }
    }

    /**
     * The numeric id of a series.
     *
     * Upstream slugs always end with the numeric id (`one-piece-1`, `frieren-5678`), and
     * the episode list endpoint is keyed by that number.
     */
    internal fun numericIdOf(slug: String): String {
        val segments = slug.split('-')
        val last = segments.lastOrNull().orEmpty()
        if (last.isNotEmpty() && last.all { it.isDigit() }) return last
        val digits = segments.lastOrNull { it.isNotEmpty() && it.all(Char::isDigit) }
        return digits ?: slug
    }
}
