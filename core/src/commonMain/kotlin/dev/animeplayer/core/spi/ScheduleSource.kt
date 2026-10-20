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
package dev.animeplayer.core.spi

import dev.animeplayer.core.model.ScheduleEntry

/**
 * A provider of broadcast schedules.
 *
 * Schedules are kept separate from [AnimeSource] because they usually come from a
 * different site than the streams themselves.
 */
public interface ScheduleSource {
    /** Stable, machine readable identifier, for example `animeschedule`. */
    public val id: String

    /** Human readable name, for example `Anime Schedule`. */
    public val displayName: String

    /** Loads the schedule entries matching [query]. */
    public suspend fun schedule(query: String): List<ScheduleEntry>
}
