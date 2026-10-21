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
package dev.animeplayer.sources.anilist.internal.parser

import dev.animeplayer.core.model.CatalogEpisode
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.core.model.CatalogPage
import dev.animeplayer.core.model.CatalogRelation
import dev.animeplayer.core.model.CatalogStatus
import dev.animeplayer.core.util.HtmlEntities
import dev.animeplayer.sources.anilist.internal.dto.EpisodeDto
import dev.animeplayer.sources.anilist.internal.dto.MediaDto
import dev.animeplayer.sources.anilist.internal.dto.PageDto
import dev.animeplayer.sources.anilist.internal.dto.RelationEdgeDto

/**
 * Maps the payloads of the catalogue onto the models of the core module.
 */
public object AniListParser {

    /**
     * Maps a page of entries.
     */
    public fun page(dto: PageDto?): CatalogPage {
        val page = dto ?: return CatalogPage()
        val info = page.pageInfo
        return CatalogPage(
            media = page.media.orEmpty().mapNotNull { media(it) },
            hasNextPage = info?.hasNextPage ?: false,
            currentPage = info?.currentPage ?: 1,
            total = info?.total ?: 0,
        )
    }

    /**
     * Maps a single entry.
     */
    public fun media(dto: MediaDto?): CatalogMedia? {
        if (dto == null) return null
        val id = dto.id?.toString() ?: return null
        val titles = listOfNotNull(
            dto.title?.english?.takeIf { it.isNotBlank() },
            dto.title?.romaji?.takeIf { it.isNotBlank() },
            dto.title?.native?.takeIf { it.isNotBlank() },
        ).distinct()
        return CatalogMedia(
            id = id,
            titles = titles,
            description = plainText(dto.description),
            posterUrl = dto.coverImage?.large,
            bannerUrl = dto.bannerImage,
            posterColor = dto.coverImage?.color,
            genres = dto.genres.orEmpty(),
            format = dto.format,
            status = status(dto.status),
            seasonYear = dto.seasonYear,
            episodeCount = dto.episodes,
            durationMinutes = dto.duration,
            score = dto.averageScore,
            popularity = dto.popularity,
            nextAiringAt = dto.nextAiringEpisode?.airingAt,
            nextAiringEpisode = dto.nextAiringEpisode?.episode,
        )
    }

    /**
     * Maps a list of entries, dropping the ones that cannot be mapped.
     */
    public fun mediaList(dtos: List<MediaDto>?): List<CatalogMedia> =
        dtos.orEmpty().mapNotNull { media(it) }

    /**
     * Maps one episode.
     */
    public fun episode(dto: EpisodeDto?): CatalogEpisode? {
        if (dto == null) return null
        val number = dto.number ?: return null
        return CatalogEpisode(
            number = number,
            title = dto.title?.takeIf { it.isNotBlank() },
            description = plainText(dto.description),
            thumbnailUrl = dto.thumbnail,
            airDate = dto.airDate,
            score = dto.averageScore,
            lengthMinutes = dto.length,
        )
    }

    /**
     * Maps a list of episodes.
     */
    public fun episodes(dtos: List<EpisodeDto>?): List<CatalogEpisode> =
        dtos.orEmpty().mapNotNull { episode(it) }

    /**
     * Maps one relation.
     */
    public fun relation(edge: RelationEdgeDto?): CatalogRelation? {
        if (edge == null) return null
        val media = media(edge.node) ?: return null
        return CatalogRelation(
            media = media,
            relationType = edge.relationType?.uppercase() ?: "OTHER",
        )
    }

    /**
     * Maps a list of relations.
     */
    public fun relations(edges: List<RelationEdgeDto>?): List<CatalogRelation> =
        edges.orEmpty().mapNotNull { relation(it) }

    /**
     * Maps the raw status of an entry.
     */
    public fun status(raw: String?): CatalogStatus {
        if (raw.isNullOrBlank()) return CatalogStatus.Unknown
        return when (raw.uppercase()) {
            "RELEASING" -> CatalogStatus(raw = raw, isAiring = true)
            "FINISHED" -> CatalogStatus(raw = raw, isFinished = true)
            "NOT_YET_RELEASED" -> CatalogStatus(raw = raw, isUpcoming = true)
            else -> CatalogStatus(raw = raw)
        }
    }

    /**
     * Turns the markup of a description into plain text.
     *
     * The catalogue is asked for the description without HTML, but it still contains line
     * breaks and the occasional entity, so both are cleaned up.
     */
    public fun plainText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val withoutTags = buildString {
            var index = 0
            while (index < raw.length) {
                val char = raw[index]
                if (char == '<') {
                    val end = raw.indexOf('>', index)
                    if (end < 0) break
                    val tag = raw.substring(index + 1, end)
                    if (tag.equals("br", ignoreCase = true) ||
                        tag.equals("br/", ignoreCase = true) ||
                        tag.equals("br /", ignoreCase = true)
                    ) {
                        append('\n')
                    }
                    index = end + 1
                } else {
                    append(char)
                    index++
                }
            }
        }
        return HtmlEntities.decode(withoutTags)
            .replace(Regex("[ \\t\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
