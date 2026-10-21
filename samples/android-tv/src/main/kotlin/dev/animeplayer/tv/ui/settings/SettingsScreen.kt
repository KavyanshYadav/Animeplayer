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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.TvSettings
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.focus.TvFocus as FocusTreatment
import dev.animeplayer.tv.ui.theme.TV_ACCENTS
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.theme.TvThemeName
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
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = TvSettings(),
    )
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(
            start = TvDimens.ScreenMargin,
            end = TvDimens.ScreenMargin,
            top = 16.dp,
            bottom = 48.dp,
        ),
    ) {
        item(key = "title") {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item(key = "themes") {
            Section(title = "The theme") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(TvThemeName.entries.toList(), key = { it.name }) { theme ->
                        ThemeSwatch(
                            theme = theme,
                            selected = settings?.theme == theme,
                            onClick = { scope.launch { container.settings.setTheme(theme) } },
                        )
                    }
                }
            }
        }

        item(key = "accents") {
            Section(title = "The accent colour") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(TV_ACCENTS, key = { it.toHex() }) { accent ->
                        AccentSwatch(
                            accent = accent,
                            selected = settings?.accent == accent,
                            onClick = { scope.launch { container.settings.setAccent(accent) } },
                        )
                    }
                }
            }
        }

        item(key = "playback") {
            Section(title = "Playback") {
                TvListItem(
                    title = "Play the next episode automatically",
                    subtitle = "The next episode starts when the current one ends.",
                    trailing = if (settings?.autoplayNext == true) "On" else "Off",
                    onClick = {
                        scope.launch {
                            container.settings.setAutoplayNext(settings?.autoplayNext != true)
                        }
                    },
                )
                TvListItem(
                    title = "Skip the intro",
                    subtitle = "The intro is skipped as soon as it starts.",
                    trailing = if (settings?.skipIntro == true) "On" else "Off",
                    onClick = {
                        scope.launch { container.settings.setSkipIntro(settings?.skipIntro != true) }
                    },
                )
                TvListItem(
                    title = "The audio track",
                    subtitle = "The track that is used when a series is played.",
                    trailing = if (settings?.dubbed == true) "Dubbed" else "Subtitled",
                    onClick = {
                        scope.launch { container.settings.setDubbed(settings?.dubbed != true) }
                    },
                )
                TvListItem(
                    title = "Forget the watch history",
                    subtitle = "Every episode the user watched is forgotten.",
                    trailing = "Clear",
                    onClick = { scope.launch { container.history.clear() } },
                )
            }
        }

        item(key = "close") {
            TvButton(text = "Close", onClick = onClose, prominent = false)
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
