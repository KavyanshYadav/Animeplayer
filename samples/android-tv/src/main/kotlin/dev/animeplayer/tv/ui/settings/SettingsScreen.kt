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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.BuildConfig
import dev.animeplayer.tv.HomeCardSize
import dev.animeplayer.tv.R
import dev.animeplayer.tv.TvSettings
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvIcon
import dev.animeplayer.tv.ui.components.TvPageHeading
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.player.panel.SubtitleStylePanel
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TV_ACCENTS
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.theme.TvThemeName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class SettingsSection(val label: String, val icon: ImageVector) {
    Playback("Playback", Icons.Filled.PlayArrow),
    Appearance("Appearance", Icons.Filled.Settings),
    History("Watch history", Icons.Filled.DateRange),
    About("About", Icons.AutoMirrored.Filled.List),
}

private enum class SettingsDialog {
    Quality, Audio, Theme, Accent, HomeCards, Subtitles, ClearHistory,
}

private val QualityOptions = listOf(null, "480", "720", "1080")
private val AccentNames = listOf("Amber", "Violet", "Blue", "Emerald", "Red", "Pink", "Rose", "Cyan", "Gold", "Lime")

/** Settings for the Android TV app. */
@Composable
public fun SettingsScreen(
    container: AppContainer,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = TvSettings())
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val layout = LocalTvLayout.current
    val compact = layout.width < 700.dp || layout.height < 430.dp
    var section by rememberSaveable { mutableStateOf(SettingsSection.Playback) }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var historyCleared by remember { mutableStateOf(false) }
    var clearingHistory by remember { mutableStateOf(false) }
    val sectionFocus = remember { SettingsSection.entries.associateWith { FocusRequester() } }
    val dialogFocus = remember { SettingsDialog.entries.associateWith { FocusRequester() } }
    var lastDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val controlFocus = when (section) {
        SettingsSection.Playback -> dialogFocus.getValue(SettingsDialog.Quality)
        SettingsSection.Appearance -> dialogFocus.getValue(SettingsDialog.Theme)
        SettingsSection.History -> dialogFocus.getValue(SettingsDialog.ClearHistory)
        SettingsSection.About -> null
    }
    entry.Register(sectionFocus.getValue(section))

    LaunchedEffect(Unit) {
        withFrameNanos { }
        sectionFocus.getValue(section).requestTvFocus()
    }

    LaunchedEffect(dialog) {
        if (dialog == null) {
            val target = lastDialog ?: return@LaunchedEffect
            lastDialog = null
            withFrameNanos { }
            dialogFocus.getValue(target).requestTvFocus()
        }
    }

    fun openDialog(option: SettingsDialog) {
        lastDialog = option
        dialog = option
    }

    fun save(onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        scope.launch {
            saveError = null
            try {
                action()
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveError = "Couldn’t save that change. Try again."
            }
        }
    }

    val autoDescription = if (container.isMemoryConstrained) {
        "Auto favors up to 720p on this TV."
    } else {
        "Auto adjusts to your connection."
    }

    Row(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = TvDimens.ScreenMargin, vertical = if (compact) 12.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (layout.width < 700.dp) 16.dp else 32.dp),
    ) {
        Column(
            modifier = Modifier.width((layout.contentWidth * 0.27f).coerceIn(136.dp, 230.dp))
                .fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp),
        ) {
            TvPageHeading("Settings", modifier = Modifier.padding(bottom = if (compact) 6.dp else 12.dp))
            SettingsSection.entries.forEach { option ->
                SettingsNavigationRow(
                    section = option,
                    active = option == section,
                    compact = compact,
                    modifier = Modifier.focusRequester(sectionFocus.getValue(option))
                        .onFocusChanged { if (it.isFocused) section = option }
                        .onPreviewKeyEvent { event ->
                            event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown &&
                                controlFocus?.requestTvFocus() == true
                        },
                    onClick = {
                        section = option
                        controlFocus?.requestTvFocus()
                    },
                )
            }
            TvButton(
                "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                prominent = false,
                modifier = Modifier.padding(top = if (compact) 6.dp else 14.dp),
                onClick = onClose,
            )
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border.copy(alpha = 0.45f)))

        key(section) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight().onPreviewKeyEvent { event ->
                    event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown &&
                        sectionFocus.getValue(section).requestTvFocus()
                },
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item("heading") {
                    Text(
                        section.label,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onBackground,
                        modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
                    )
                }
                saveError?.let { message ->
                    item("error") { Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
                }
                when (section) {
                    SettingsSection.Playback -> {
                        item("quality") {
                            SettingRow(
                                "Video quality",
                                if (settings.quality == null) autoDescription else "Default resolution for new episodes.",
                                qualityLabel(settings.quality),
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.Quality)),
                                onClick = { openDialog(SettingsDialog.Quality) },
                            )
                        }
                        item("audio") {
                            SettingRow(
                                "Preferred audio", "Choose the default audio for new episodes.", audioLabel(settings.dubbed),
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.Audio)),
                                onClick = { openDialog(SettingsDialog.Audio) },
                            )
                        }
                        item("autoplay") {
                            SettingRow(
                                "Autoplay next episode", "Continue automatically when an episode ends.",
                                checked = settings.autoplayNext,
                                onClick = { save { container.settings.setAutoplayNext(!settings.autoplayNext) } },
                            )
                        }
                        item("intro") {
                            SettingRow(
                                "Skip intros automatically", "Skip the opening when timing is available.",
                                checked = settings.skipIntro,
                                onClick = { save { container.settings.setSkipIntro(!settings.skipIntro) } },
                            )
                        }
                    }
                    SettingsSection.Appearance -> {
                        item("theme") {
                            SettingRow(
                                "App theme", "Change the colors used throughout the app.", settings.theme.label,
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.Theme)),
                                swatch = settings.theme.tokens(settings.accent).primary,
                                onClick = { openDialog(SettingsDialog.Theme) },
                            )
                        }
                        if (settings.theme == TvThemeName.Custom) {
                            item("accent") {
                                SettingRow(
                                    "Accent color", "Used for selections and playback controls.", accentName(settings.accent),
                                    modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.Accent)),
                                    swatch = settings.accent,
                                    onClick = { openDialog(SettingsDialog.Accent) },
                                )
                            }
                        }
                        item("home-cards") {
                            SettingRow(
                                "Home card size", "Adjust artwork size in Home rows.", settings.homeCardSize.label,
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.HomeCards)),
                                onClick = { openDialog(SettingsDialog.HomeCards) },
                            )
                        }
                        item("subtitles") {
                            SettingRow(
                                "Subtitle appearance", "Preview the font, size, and position.", settings.subtitleStyle.preset.label,
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.Subtitles)),
                                onClick = { openDialog(SettingsDialog.Subtitles) },
                            )
                        }
                    }
                    SettingsSection.History -> item("history") {
                        SettingRow(
                            "Clear watch history", "Removes Continue Watching and saved episode progress on this TV.",
                            modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.ClearHistory)),
                            labelColor = colors.error,
                            onClick = { openDialog(SettingsDialog.ClearHistory) },
                        )
                        if (historyCleared) {
                            Text(
                                "Watch history cleared.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primary,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    SettingsSection.About -> item("about") {
                        AboutSettings(showTmdb = container.tmdb != null)
                    }
                }
            }
        }
    }

    val dismiss = {
        dialog = null
        saveError = null
    }
    when (dialog) {
        SettingsDialog.Quality -> SettingsOptions(
            "Video quality", QualityOptions.indexOf(settings.quality), QualityOptions.size, dismiss, saveError,
        ) { index, focusModifier ->
            val quality = QualityOptions[index]
            SettingRow(
                title = qualityLabel(quality),
                subtitle = autoDescription.takeIf { quality == null },
                modifier = focusModifier,
                chosen = quality == settings.quality,
                onClick = { save(onSuccess = dismiss) { container.settings.setQuality(quality) } },
            )
        }
        SettingsDialog.Audio -> SettingsOptions(
            "Preferred audio", if (settings.dubbed) 1 else 0, 2, dismiss, saveError,
        ) { index, focusModifier ->
            val dubbed = index == 1
            SettingRow(
                title = audioLabel(dubbed),
                subtitle = if (dubbed) "English voice track when available." else "Original voice track with subtitles.",
                modifier = focusModifier,
                chosen = dubbed == settings.dubbed,
                onClick = { save(onSuccess = dismiss) { container.settings.setDubbed(dubbed) } },
            )
        }
        SettingsDialog.Theme -> SettingsOptions(
            "App theme", settings.theme.ordinal, TvThemeName.entries.size, dismiss, saveError,
        ) { index, focusModifier ->
            val theme = TvThemeName.entries[index]
            SettingRow(
                title = theme.label,
                swatch = theme.tokens(settings.accent).primary,
                modifier = focusModifier,
                chosen = theme == settings.theme,
                onClick = { save(onSuccess = dismiss) { container.settings.setTheme(theme) } },
            )
        }
        SettingsDialog.Accent -> SettingsOptions(
            "Accent color", TV_ACCENTS.indexOf(settings.accent), TV_ACCENTS.size, dismiss, saveError,
        ) { index, focusModifier ->
            val accent = TV_ACCENTS[index]
            SettingRow(
                title = accentName(accent),
                swatch = accent,
                modifier = focusModifier,
                chosen = accent == settings.accent,
                onClick = { save(onSuccess = dismiss) { container.settings.setAccent(accent) } },
            )
        }
        SettingsDialog.HomeCards -> SettingsOptions(
            "Home card size", settings.homeCardSize.ordinal, HomeCardSize.entries.size, dismiss, saveError,
        ) { index, focusModifier ->
            val size = HomeCardSize.entries[index]
            SettingRow(
                title = size.label,
                subtitle = when (size) {
                    HomeCardSize.Standard -> "About four cards per row"
                    HomeCardSize.Large -> "About three cards per row"
                    HomeCardSize.ExtraLarge -> "About two cards per row"
                },
                modifier = focusModifier,
                chosen = size == settings.homeCardSize,
                onClick = { save(onSuccess = dismiss) { container.settings.setHomeCardSize(size) } },
            )
        }
        SettingsDialog.Subtitles -> TvSheet(title = "Subtitle appearance", onDismiss = dismiss) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                saveError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                SubtitleStylePanel(
                    style = settings.subtitleStyle,
                    onStyleChange = { style -> save { container.settings.setSubtitleStyle(style) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SettingsDialog.ClearHistory -> TvSheet(
            title = "Clear watch history?",
            onDismiss = { if (!clearingHistory) dismiss() },
            actions = {
                TvButton("Cancel", enabled = !clearingHistory, onClick = dismiss)
                TvButton(
                    if (clearingHistory) "Clearing…" else "Clear history",
                    enabled = !clearingHistory,
                    prominent = false,
                    onClick = {
                        if (!clearingHistory) {
                            clearingHistory = true
                            scope.launch {
                                saveError = null
                                try {
                                    container.history.clear()
                                    historyCleared = true
                                    dismiss()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    saveError = "Couldn’t clear watch history. Try again."
                                } finally {
                                    clearingHistory = false
                                }
                            }
                        }
                    },
                )
            },
        ) {
            Text("This removes Continue Watching and episode progress from this TV.",
                style = MaterialTheme.typography.bodyLarge)
            saveError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
        }
        null -> Unit
    }
}

@Composable
private fun SettingsNavigationRow(
    section: SettingsSection,
    active: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { selected = active },
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1f),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = if (active) colors.primaryContainer else Color.Transparent,
            contentColor = if (active) colors.onPrimaryContainer else colors.onBackground,
            focusedContainerColor = if (active) colors.primaryContainer else colors.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.heightIn(min = if (compact) 40.dp else 49.dp)
                .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 5.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIcon(section.icon, size = 19.dp)
            Text(section.label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    chosen: Boolean? = null,
    swatch: Color? = null,
    labelColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics {
            if (checked != null) {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                stateDescription = if (checked) "On" else "Off"
            }
            if (chosen != null) selected = chosen
        },
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1f),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(colors.surface, colors.onSurface, colors.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (swatch != null) {
                Box(Modifier.size(18.dp).background(swatch, CircleShape))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = labelColor)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            value?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary) }
            when {
                checked != null -> SettingSwitch(checked)
                chosen == true -> TvIcon(Icons.Filled.Check, tint = colors.primary)
                chosen == null -> TvIcon(
                    Icons.AutoMirrored.Filled.ArrowBack, Modifier.rotate(180f), size = 16.dp, tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.width(36.dp).height(21.dp)
            .background(if (checked) colors.primary else colors.border, CircleShape)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(15.dp).background(if (checked) colors.onPrimary else colors.surface, CircleShape))
    }
}

@Composable
private fun SettingsOptions(
    title: String,
    selected: Int,
    count: Int,
    onDismiss: () -> Unit,
    error: String?,
    row: @Composable (Int, Modifier) -> Unit,
) {
    val first = selected.coerceAtLeast(0)
    val initialFocus = remember { FocusRequester() }
    TvSheet(title, onDismiss = onDismiss, initialFocus = initialFocus) {
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(
            state = rememberLazyListState(first),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(count) { index ->
                val focusModifier = if (index == first) Modifier.focusRequester(initialFocus) else Modifier
                row(index, focusModifier)
            }
        }
    }
}

@Composable
private fun AboutSettings(showTmdb: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Animeplayer", style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (showTmdb) {
            Image(
                painter = painterResource(R.drawable.tmdb_logo),
                contentDescription = "The Movie Database",
                modifier = Modifier.padding(top = 12.dp).width(104.dp).height(30.dp),
                alignment = Alignment.CenterStart,
            )
            Text(
                "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun audioLabel(dubbed: Boolean): String = if (dubbed) "Dubbed" else "Subtitled"

private fun accentName(accent: Color): String = AccentNames.getOrNull(TV_ACCENTS.indexOf(accent)) ?: "Custom"

private fun qualityLabel(quality: String?): String = quality?.toIntOrNull()?.let { "${it}p" } ?: "Auto"
