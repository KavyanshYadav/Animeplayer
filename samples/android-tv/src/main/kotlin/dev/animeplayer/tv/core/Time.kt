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
package dev.animeplayer.tv.core

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The current season of the catalogue.
 *
 * The seasons of the catalogue are the calendar ones: the winter covers December to
 * February, the spring March to May, the summer June to August and the autumn September
 * to November.
 */
public fun currentSeason(): Pair<String, Int> {
    val today = LocalDate.now(ZoneOffset.UTC)
    val season = when (today.monthValue) {
        12, 1, 2 -> "WINTER"
        3, 4, 5 -> "SPRING"
        6, 7, 8 -> "SUMMER"
        else -> "FALL"
    }
    val year = if (today.monthValue == 12) today.year + 1 else today.year
    return season to year
}

/** The current year. */
public fun currentYear(): Int = LocalDate.now(ZoneOffset.UTC).year
