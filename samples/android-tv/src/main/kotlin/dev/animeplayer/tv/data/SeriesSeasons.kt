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

import dev.animeplayer.core.model.CatalogMedia

internal fun CatalogMedia.isTelevisionSeason(): Boolean = format in televisionFormats

private val televisionFormats = setOf("TV", "TV_SHORT", "ONA")

private val seasonSuffix = Regex(
    """[\s:–-]+(?:season\s+\d+|\d+(?:st|nd|rd|th)\s+season|(?:part|cour)\s+\d+)$""",
    RegexOption.IGNORE_CASE,
)

private val ordinalSeason = Regex("""^(\d+)(?:st|nd|rd|th)\s+season""", RegexOption.IGNORE_CASE)

internal fun List<CatalogMedia>.seasonLabel(index: Int): String {
    val title = get(index).title.trim()
    val base = title.seriesBaseTitle()
    if (title != base) {
        val suffix = title.substring(base.length).trimStart(' ', ':', '-', '–')
        return suffix.replace(ordinalSeason, "Season $1").replace(
            Regex("""\s+(part|cour)\s+""", RegexOption.IGNORE_CASE),
        ) { match -> " · ${match.groupValues[1].replaceFirstChar(Char::uppercaseChar)} " }
    }
    if (index == 0 && drop(1).any { media ->
            media.titles.any { it.seriesBaseTitle().equals(base, ignoreCase = true) }
        }) return "Season 1"
    return title
}

internal fun String.seriesBaseTitle(): String {
    var base = trim()
    while (true) {
        val suffix = seasonSuffix.find(base) ?: break
        base = base.substring(0, suffix.range.first).trimEnd()
    }
    return base
}

/** Retain the first ranked entry as the card and preserve its original AniList ID. */
internal fun List<CatalogMedia>.groupSeasons(): List<CatalogMedia> {
    val parents = mutableMapOf<String, String>()
    fun root(key: String): String {
        var current = key
        while (parents[current] != null) current = parents.getValue(current)
        return current
    }
    val television = filter { it.isTelevisionSeason() }
    fun join(id: String, related: List<String>) {
        for (key in related) {
            val other = root(key)
            if (other != id) parents[other] = id
        }
    }
    for (media in television) join(root(media.id), media.seasonLinks)

    // Two unrelated shows can share an English title. Only use it as a fallback
    // when at least one release has a season suffix and the base title is unambiguous.
    val aliases = television.flatMap { media ->
        media.titles.map { title ->
            val base = title.seriesBaseTitle()
            val key = base.lowercase().filter(Char::isLetterOrDigit)
            Triple(key, media.id, base != title.trim())
        }
    }.filter { it.first.isNotEmpty() }.groupBy { it.first }
    for (matches in aliases.values) {
        if (matches.none { it.third }) continue
        val baseIds = matches.filterNot { it.third }.map { root(it.second) }.distinct()
        if (baseIds.size > 1) continue
        val ids = matches.map { root(it.second) }.distinct()
        join(baseIds.firstOrNull() ?: ids.first(), ids)
    }
    return distinctBy { root(it.id) }
}
