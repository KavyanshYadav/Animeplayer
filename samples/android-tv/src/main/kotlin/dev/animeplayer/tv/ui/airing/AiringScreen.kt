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
package dev.animeplayer.tv.ui.airing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvEmptyState
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.components.TvPageHeading
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.theme.TvDimens
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

private data class ScheduleRow(
    val title: String,
    val status: String,
    val nextSub: Instant?,
    val nextDub: Instant?,
) {
    val latest: Instant? get() = listOfNotNull(nextSub, nextDub).maxOrNull()

    fun upcoming(now: Instant): Instant? =
        listOfNotNull(nextSub, nextDub).filter { it >= now }.minOrNull()
}

@Composable
public fun AiringScreen(
    container: AppContainer,
    onOpenSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
) {
    var entries by remember { mutableStateOf<List<ScheduleRow>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retries by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    val zone = remember { ZoneId.systemDefault() }
    entry.Register(firstFocus)

    LaunchedEffect(retries) {
        entries = null
        failed = false
        try {
            val now = Clock.System.now()
            val scheduled = container.scheduleSource.schedule("")
                .map { ScheduleRow(it.title, it.status.name, it.nextSubAt, it.nextDubAt) }
                .distinct()
            val (upcoming, remaining) = scheduled.partition { it.upcoming(now) != null }
            entries = upcoming.sortedBy { it.upcoming(now) } +
                remaining.sortedWith(compareBy(nullsLast(reverseOrder())) { it.latest })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TvPageHeading(
            "Airing",
            "Release times for subtitled and dubbed episodes. Select a title to search.",
            Modifier.padding(horizontal = TvDimens.ScreenMargin),
        )
        val schedule = entries
        when {
            failed -> TvErrorState("Couldn't load the airing schedule.", onRetry = { retries++ })
            schedule == null -> TvLoading(message = "Loading release times…")
            schedule.isEmpty() -> TvEmptyState("No releases listed")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(
                    start = TvDimens.ScreenMargin,
                    end = TvDimens.ScreenMargin,
                    top = 4.dp,
                    bottom = 48.dp,
                ),
            ) {
                itemsIndexed(schedule, contentType = { _, _ -> "schedule" }) { index, row ->
                    val times = listOfNotNull(
                        row.nextSub?.let { "Sub  ${formatTime(it, zone, dateFormat)}" },
                        row.nextDub?.let { "Dub  ${formatTime(it, zone, dateFormat)}" },
                    )
                    TvListItem(
                        title = row.title,
                        subtitle = times.joinToString("     ·     ").ifBlank { null },
                        trailing = row.status.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar),
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { onOpenSearch(row.title) },
                    )
                }
            }
        }
    }
}

private fun formatTime(time: Instant, zone: ZoneId, format: DateTimeFormatter): String =
    java.time.Instant.ofEpochMilli(time.toEpochMilliseconds()).atZone(zone).format(format)
