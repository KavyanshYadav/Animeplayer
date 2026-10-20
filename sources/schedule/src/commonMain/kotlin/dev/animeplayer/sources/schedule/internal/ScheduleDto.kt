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
package dev.animeplayer.sources.schedule.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The response of the schedule API. */
@Serializable
internal data class ScheduleResponse(
    @SerialName("page") val page: Int = 1,
    @SerialName("totalAmount") val totalAmount: Int = 0,
    @SerialName("anime") val anime: List<ScheduleAnime> = emptyList(),
)

@Serializable
internal data class ScheduleAnime(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("route") val route: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("imageVersionRoute") val imageVersionRoute: String? = null,
    @SerialName("jpnTime") val jpnTime: String? = null,
    @SerialName("subTime") val subTime: String? = null,
    @SerialName("dubTime") val dubTime: String? = null,
    @SerialName("lengthMin") val lengthMin: Int? = null,
    @SerialName("genres") val genres: List<Named> = emptyList(),
    @SerialName("studios") val studios: List<Named> = emptyList(),
    @SerialName("names") val names: Names? = null,
    @SerialName("websites") val websites: Websites? = null,
)

@Serializable
internal data class Named(
    @SerialName("name") val name: String? = null,
    @SerialName("route") val route: String? = null,
)

@Serializable
internal data class Names(
    @SerialName("native") val native: String? = null,
    @SerialName("abbreviation") val abbreviation: String? = null,
    @SerialName("synonyms") val synonyms: List<String> = emptyList(),
)

@Serializable
internal data class Websites(
    @SerialName("official") val official: String? = null,
    @SerialName("mal") val mal: String? = null,
    @SerialName("aniList") val aniList: String? = null,
)
