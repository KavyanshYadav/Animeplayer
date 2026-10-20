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

import kotlin.time.Instant

/**
 * A broadcast schedule entry as published by an airing-schedule provider.
 *
 * @property nextSubAt the timestamp of the next subtitled episode, when known.
 * @property nextDubAt the timestamp of the next dubbed episode, when known.
 */
public data class ScheduleEntry(
    public val title: String,
    public val japaneseTitle: String? = null,
    public val status: ReleaseStatus = ReleaseStatus.Unknown,
    public val nextSubAt: Instant? = null,
    public val nextDubAt: Instant? = null,
    public val route: String? = null,
    public val malId: String? = null,
    public val description: String? = null,
    public val genres: List<String> = emptyList(),
    public val studios: List<String> = emptyList(),
    public val posterUrl: String? = null,
) {
    /** `true` when the series is still airing. */
    public val isOngoing: Boolean get() = status == ReleaseStatus.Ongoing
}
