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
package dev.animeplayer.core.util

import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.EpisodeList

/**
 * Parses the episode selectors the CLI accepts.
 *
 * Supported forms are a single number (`5`), a closed range (`5-8`), an open ended range
 * (`5-`) and an explicit list (`5 8 12`). The special value `-1` means "the latest
 * episode", which mirrors the behaviour of the original command line tool.
 */
public object EpisodeSelector {

    public const val LATEST: Int = -1

    /**
     * Expands [selector] into the concrete episodes of [list].
     *
     * @return the matching episodes, in list order. An empty list means nothing matched.
     */
    public fun expand(list: EpisodeList, selector: String): List<Episode> {
        val trimmed = selector.trim()
        if (trimmed.isEmpty()) return emptyList()

        val episodes = list.episodes
        val byNumber = episodes.associateBy { it.number }

        // A plain number — including the "-1" marker for "the latest episode" — is checked
        // first, because "-1" would otherwise be read as a range.
        val plainNumber = trimmed.toIntOrNull()
        if (plainNumber != null) {
            return when (plainNumber) {
                LATEST -> listOfNotNull(episodes.lastOrNull())
                else -> byNumber[plainNumber.toString()]?.let { listOf(it) } ?: emptyList()
            }
        }

        return when {
            trimmed.contains(',') || trimmed.contains(' ') ->
                trimmed.split(',', ' ').filter { it.isNotBlank() }.mapNotNull { byNumber[it.trim()] }

            trimmed.contains('-') && trimmed != "-" -> {
                val bounds = trimmed.split('-', limit = 2).map { it.trim() }
                val start = bounds[0].toIntOrNull()
                val end = bounds.getOrNull(1)?.toIntOrNull() ?: LATEST
                if (start == null) {
                    emptyList()
                } else {
                    val resolvedEnd = if (end == LATEST) episodes.size else end
                    episodes.filter { episode ->
                        val number = episode.numericNumber ?: return@filter false
                        number in start..resolvedEnd
                    }
                }
            }

            else -> byNumber[trimmed]?.let { listOf(it) } ?: emptyList()
        }
    }
}
