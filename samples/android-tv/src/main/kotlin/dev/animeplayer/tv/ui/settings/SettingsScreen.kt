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
package dev.animeplayer.tv.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.R
import dev.animeplayer.tv.TvSettings
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvPageHeading
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.TvFocus as FocusTreatment
import dev.animeplayer.tv.ui.theme.TV_ACCENTS
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvThemeName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The settings of the application.
 *
 * The themes and the accent colours are the ones of the desktop client, so a user who
 * switches between the two applications finds the same colours.
 *
 * @param container the container of the dependencies.
 * @param onClose called when the user asks to close the settings.
 */
@Composable
public fun SettingsScreen(
    container: AppContainer,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = TvSettings(),
    )
    val scope = rememberCoroutineScope()

    var qualityOpen by remember { mutableStateOf(false) }
    var clearHistoryOpen by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val initialFocus = remember { FocusRequester() }
    entry.Register(initialFocus)
    val change: (suspend () -> Unit) -> Unit = { action ->
        scope.launch {
            try {
                action()
                saveError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveError = "Couldn't save that change. Try again."
            }
        }
    }

    val playback: @Composable () -> Unit = {
        Section(title = "Playback") {
            TvListItem(
                title = "Video quality",
                subtitle = if (container.isMemoryConstrained) "Auto favors up to 720p on this device." else "Auto adjusts to your connection.",
                trailing = settings.quality?.toIntOrNull()?.let { "${it}p" } ?: "Auto",
                modifier = Modifier.focusRequester(initialFocus),
                onClick = { qualityOpen = true },
            )
            TvListItem(
                title = "Preferred audio",
                trailing = if (settings.dubbed) "Dubbed" else "Subtitled",
                onClick = { change { container.settings.setDubbed(!settings.dubbed) } },
            )
            TvListItem(
                title = "Autoplay next episode",
                trailing = if (settings.autoplayNext) "On" else "Off",
                onClick = { change { container.settings.setAutoplayNext(!settings.autoplayNext) } },
            )
            TvListItem(
                title = "Skip intros automatically",
                trailing = if (settings.skipIntro) "On" else "Off",
                onClick = { change { container.settings.setSkipIntro(!settings.skipIntro) } },
            )
        }
    }
    val appearance: @Composable () -> Unit = {
        Section(title = "Appearance") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(TvThemeName.entries, key = { it.name }) { theme ->
                    ThemeSwatch(
                        theme = theme,
                        selected = settings.theme == theme,
                        onClick = { change { container.settings.setTheme(theme) } },
                    )
                }
            }
            if (settings.theme == TvThemeName.Custom) {
                Text("Accent color", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(TV_ACCENTS, key = { it.toHex() }) { accent ->
                        AccentSwatch(
                            accent = accent,
                            selected = settings.accent == accent,
                            onClick = { change { container.settings.setAccent(accent) } },
                        )
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(
            start = TvDimens.ScreenMargin,
            end = TvDimens.ScreenMargin,
            bottom = 48.dp,
        ),
    ) {
        item(key = "title") { TvPageHeading("Settings", modifier = Modifier.padding(top = 12.dp)) }
        saveError?.let { message ->
            item(key = "notice") {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
        }

        item(key = "preferences") {
            if (LocalTvLayout.current.width >= 830.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1.18f)) { playback() }
                    Column(Modifier.weight(1f)) { appearance() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    playback()
                    appearance()
                }
            }
        }

        item(key = "history") {
            Section(title = "Watch history") {
                TvListItem(
                    title = "Clear watch history",
                    subtitle = "Removes saved watch progress on this TV.",
                    onClick = { clearHistoryOpen = true },
                )
            }
        }

        if (container.tmdb != null) {
            item(key = "about") {
                Section(title = "About") {
                    Image(
                        painter = painterResource(R.drawable.tmdb_logo),
                        contentDescription = "The Movie Database",
                        modifier = Modifier.width(104.dp).height(26.dp),
                        alignment = Alignment.CenterStart,
                    )
                    Text(
                        "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "close") { TvButton(text = "Back", onClick = onClose, prominent = false) }
    }

    if (qualityOpen) {
        TvSheet(title = "Video quality", onDismiss = { qualityOpen = false }) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(null, "480", "720", "1080")) { quality ->
                    TvListItem(
                        title = quality?.let { "${it}p" } ?: "Auto",
                        selected = quality == settings.quality,
                        onClick = {
                            change { container.settings.setQuality(quality) }
                            qualityOpen = false
                        },
                    )
                }
            }
        }
    }
    if (clearHistoryOpen) {
        TvSheet(
            title = "Clear watch history?",
            onDismiss = { clearHistoryOpen = false },
            actions = {
                TvButton("Cancel", onClick = { clearHistoryOpen = false })
                TvButton(
                    "Clear history",
                    onClick = {
                        change { container.history.clear() }
                        clearHistoryOpen = false
                    },
                    prominent = false,
                )
            },
        ) {
            Text(
                "Continue Watching and episode progress will be removed from this TV.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * A section of the settings.
 */
@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        content()
    }
}

/**
 * One theme of the picker.
 */
@Composable
private fun ThemeSwatch(
    theme: TvThemeName,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        shape = FocusTreatment.shape(shape),
        scale = FocusTreatment.scale(focused = 1.05f),
        glow = FocusTreatment.glow(accent),
        border = FocusTreatment.border(shape),
        colors = FocusTreatment.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.swatch),
            )
            Text(text = theme.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * One accent colour of the picker.
 */
@Composable
private fun AccentSwatch(
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val focusAccent = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        shape = FocusTreatment.shape(shape),
        scale = FocusTreatment.scale(focused = 1.15f),
        glow = FocusTreatment.glow(focusAccent),
        border = FocusTreatment.border(shape),
        colors = FocusTreatment.colors(
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
        ),
        modifier = Modifier.size(44.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(if (selected) 4.dp else 8.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(accent),
        )
    }
}

/** `#RRGGBB` */
private fun Color.toHex(): String =
    "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
