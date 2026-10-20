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

import dev.animeplayer.core.model.AnimeId
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.EpisodeList
import dev.animeplayer.sources.hianime.internal.HtmlFragment

/**
 * Extracts the episode list from the episode list endpoint.
 *
 * The endpoint answers with a JSON envelope whose `html` field contains one `<a>` tag per
 * episode:
 *
 * ```html
 * <a title="Episode 1"
 *    class="ssl-item ep-item"
 *    data-number="1"
 *    data-id="1"
 *    href="https://hianime.at/watch/one-piece-1?ep=1">
 * ```
 *
 * `data-number` is the episode number shown to the user, `data-id` is the internal id used
 * to look the servers up. The `totalItems` field of the envelope is unreliable — it
 * reports the size of a single page rather than the number of episodes — so the parsed
 * count is used instead.
 */
internal object EpisodeListParser {

    fun parse(html: String, animeId: AnimeId, totalReported: Int? = null): EpisodeList {
        val episodes = mutableListOf<Episode>()

        HtmlFragment.elements(
            html = html,
            tagName = "a",
            requireAttributes = setOf("data-number", "data-id"),
        ).forEach { element ->
            val number = element.attribute("data-number")?.trim().orEmpty()
            val episodeId = element.attribute("data-id")?.trim().orEmpty()
            if (number.isEmpty() || episodeId.isEmpty()) return@forEach

            val href = element.attribute("href").orEmpty()
            val hrefSlug = SearchParser.slugOf(href)
            if (hrefSlug != null && hrefSlug != animeId.slug) return@forEach

            episodes += Episode(
                animeId = animeId,
                number = number,
                index = episodes.size,
                title = element.attribute("title")?.substringAfter("Episode ", "")?.takeIf { it.isNotBlank() },
            )
        }

        val sorted = episodes
            .sortedWith(compareBy({ it.numericNumber == null }, { it.numericNumber ?: Int.MAX_VALUE }, { it.number }))
            .mapIndexed { index, episode -> episode.copy(index = index) }

        return EpisodeList(animeId = animeId, episodes = sorted, totalReported = totalReported)
    }

    /**
     * The upstream id of an episode, needed to look its servers up.
     *
     * @return the id, or `null` when the episode is not part of [html].
     */
    fun episodeIdOf(html: String, episodeNumber: String): String? {
        HtmlFragment.elements(
            html = html,
            tagName = "a",
            requireAttributes = setOf("data-number", "data-id"),
        ).forEach { element ->
            if (element.attribute("data-number")?.trim() == episodeNumber) {
                return element.attribute("data-id")?.trim()
            }
        }
        return null
    }
}
