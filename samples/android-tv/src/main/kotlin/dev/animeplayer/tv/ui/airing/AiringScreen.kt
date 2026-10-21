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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.ui.components.TvEmptyState
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.theme.TvDimens
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One entry of the schedule.
 */
public data class ScheduleRow(
    val title: String,
    val status: String,
    val nextSub: String?,
    val nextDub: String?,
)

/**
 * The broadcast schedule.
 *
 * The schedule comes from a different site than the streams, so the two are kept apart.
 *
 * @param container the container of the dependencies.
 */
@Composable
public fun AiringScreen(
    container: AppContainer,
    memory: FocusMemory,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
) {
    var entries by remember { mutableStateOf<List<ScheduleRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { container.scheduleSource.schedule("") }
            .onSuccess { schedule ->
                entries = schedule.map {
                    ScheduleRow(
                        title = it.title,
                        status = it.status.name,
                        nextSub = it.nextSubAt?.let { formatTime(it) },
                        nextDub = it.nextDubAt?.let { formatTime(it) },
                    )
                }
            }
            .onFailure { error = it.message ?: "The schedule could not be loaded." }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Airing",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = TvDimens.ScreenMargin,
                end = TvDimens.ScreenMargin,
                bottom = 16.dp,
            ),
        )

        when {
            loading -> TvLoading(message = "The schedule is loading.")
            error != null -> TvErrorState(message = error!!, onRetry = { })
            entries.isNullOrEmpty() -> TvEmptyState(
                message = "Nothing is airing right now.",
                hint = "The schedule is empty.",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = TvDimens.ScreenMargin,
                    end = TvDimens.ScreenMargin,
                    bottom = 48.dp,
                ),
            ) {
                items(entries!!, key = { it.title }) { entry ->
                    TvListItem(
                        title = entry.title,
                        subtitle = listOfNotNull(
                            entry.nextSub?.let { "Sub · $it" },
                            entry.nextDub?.let { "Dub · $it" },
                        ).joinToString("     "),
                        trailing = entry.status,
                        onClick = { },
                    )
                }
            }
        }
    }
}

/**
 * A timestamp, as `in 3 days` or `tomorrow`.
 */
private fun formatTime(instant: Instant): String {
    val seconds = instant.epochSeconds - Clock.System.now().epochSeconds
    if (seconds < 0) return "soon"
    val days = seconds / 86_400
    return when {
        days <= 0 -> "today"
        days == 1L -> "tomorrow"
        days < 7 -> "in $days days"
        days < 30 -> "in ${days / 7} weeks"
        else -> "in ${days / 30} months"
    }
}
