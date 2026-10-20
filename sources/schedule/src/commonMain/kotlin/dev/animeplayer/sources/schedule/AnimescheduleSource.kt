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
package dev.animeplayer.sources.schedule

import dev.animeplayer.core.error.AnimeException
import dev.animeplayer.core.model.ReleaseStatus
import dev.animeplayer.core.model.ScheduleEntry
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpTransport
import dev.animeplayer.core.spi.ScheduleSource
import dev.animeplayer.sources.schedule.internal.ScheduleAnime
import dev.animeplayer.sources.schedule.internal.ScheduleResponse
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The [ScheduleSource] implementation for `animeschedule.net`.
 *
 * The original command line tool scraped the rendered HTML of the schedule page and looked
 * for `english`, `romaji` and `status` fields. The API has since changed its shape, and
 * the page itself is rendered client side, so the scraper no longer finds anything. This
 * implementation reads the JSON API directly and maps the current field names.
 */
public class AnimescheduleSource(
    private val transport: HttpTransport,
    private val config: AnimescheduleConfig = AnimescheduleConfig(),
) : ScheduleSource {

    override val id: String = "animeschedule"

    override val displayName: String = "Anime Schedule"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override suspend fun schedule(query: String): List<ScheduleEntry> {
        val url = "${config.baseUrl}/api/v3/anime?q=${query.replace(' ', '+')}"

        val request = HttpRequest(
            url = url,
            headers = mapOf(
                "User-Agent" to config.userAgent,
                "Accept" to "application/json,text/plain,*/*",
            ),
            timeout = config.requestTimeout,
        )

        val response = transport.execute(request)
        if (!response.isSuccessful) {
            throw AnimeException.HttpStatusError(url, response.status, response.bodyAsText().take(512))
        }

        val parsed = runCatching { json.decodeFromString<ScheduleResponse>(response.bodyAsText()) }
            .getOrElse { throw AnimeException.ParseError("the schedule response", it.message) }

        return parsed.anime.map { it.toEntry() }
    }

    private fun ScheduleAnime.toEntry(): ScheduleEntry = ScheduleEntry(
        title = title.orEmpty(),
        japaneseTitle = names?.native?.takeIf { it.isNotBlank() },
        status = ReleaseStatus.fromRaw(status),
        nextSubAt = subTime.toInstantOrNull(),
        nextDubAt = dubTime.toInstantOrNull(),
        route = route,
        malId = websites?.mal?.let { MAL_ID.find(it)?.groupValues?.get(1) },
        description = description,
        genres = genres.mapNotNull { it.name },
        studios = studios.mapNotNull { it.name },
        posterUrl = imageVersionRoute?.let { "$IMAGE_BASE_URL/$it" },
    )

    /**
     * Parses a timestamp, treating the "unset" sentinels the API uses (`0001-01-01` and
     * `0002-01-01`) as `null`.
     */
    private fun String?.toInstantOrNull(): Instant? {
        val raw = this?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return null
        return if (instant.epochSeconds < EPOCH_CUTOFF) null else instant
    }

    private companion object {
        const val EPOCH_CUTOFF = 0L
        const val IMAGE_BASE_URL = "https://img.animeschedule.net/production/assets/public/img"
        val MAL_ID = Regex("""myanimelist\.net/anime/(\d+)""")
    }
}

/**
 * The configuration of [AnimescheduleSource].
 */
public data class AnimescheduleConfig(
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val requestTimeout: Duration = 10.seconds,
    public val userAgent: String = DEFAULT_USER_AGENT,
) {
    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://animeschedule.net"

        public const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
