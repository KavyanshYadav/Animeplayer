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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.R as MediaR
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.BuildConfig
import dev.animeplayer.tv.HomeCardSize
import dev.animeplayer.tv.R
import dev.animeplayer.tv.TvSettings
import dev.animeplayer.tv.seekIntervals
import dev.animeplayer.tv.data.TmdbCredentialError
import dev.animeplayer.tv.data.UpdateStatus
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvIcon
import dev.animeplayer.tv.ui.components.TvPageHeading
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.components.TvTextField
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.model.TitleLanguage
import dev.animeplayer.tv.ui.player.panel.SubtitleStylePanel
import dev.animeplayer.tv.ui.player.subtitles.SubtitleLanguage
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TV_ACCENTS
import dev.animeplayer.tv.ui.theme.TvDimens
import dev.animeplayer.tv.ui.theme.TvThemeName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class SettingsSection(val label: String, val icon: ImageVector? = null) {
    Playback("Playback", Icons.Filled.PlayArrow),
    Appearance("Appearance", Icons.Filled.Settings),
    Subtitles("Subtitles"),
    History("Watch history", Icons.Filled.DateRange),
    Tmdb("TMDB", Icons.Filled.Search),
    AniList("AniList", Icons.AutoMirrored.Filled.List),
    Updates("Updates", Icons.Filled.DateRange),
    About("About", Icons.AutoMirrored.Filled.List),
}

private enum class SettingsDialog {
    Quality, Audio, Theme, Accent, HomeCards, ClearHistory, TmdbKey, AniListToken, SubtitleLanguage, TitleLanguage, SeekInterval,
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
    val context = LocalContext.current
    val updates by container.updates.status.collectAsStateWithLifecycle()
    val aniList by container.aniListAccount.state.collectAsStateWithLifecycle()
    val checkFocus = remember { FocusRequester() }
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
    val subtitleFocus = dialogFocus.getValue(SettingsDialog.SubtitleLanguage)
    val controlFocus = when (section) {
        SettingsSection.Playback -> dialogFocus.getValue(SettingsDialog.Quality)
        SettingsSection.Appearance -> dialogFocus.getValue(SettingsDialog.Theme)
        SettingsSection.Subtitles -> subtitleFocus
        SettingsSection.History -> dialogFocus.getValue(SettingsDialog.ClearHistory)
        SettingsSection.Tmdb -> dialogFocus.getValue(SettingsDialog.TmdbKey)
        SettingsSection.AniList -> dialogFocus.getValue(SettingsDialog.AniListToken)
        SettingsSection.Updates -> checkFocus
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

        if (section == SettingsSection.Subtitles) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Subtitles",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground,
                    modifier = Modifier.padding(top = 5.dp),
                )
                saveError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
                SettingRow(
                    "Preferred subtitle language", "Applies to newly opened episodes.", settings.subtitleLanguage.label,
                    modifier = Modifier.focusRequester(subtitleFocus),
                    onClick = { openDialog(SettingsDialog.SubtitleLanguage) },
                )
                SubtitleStylePanel(
                    style = settings.subtitleStyle,
                    onStyleChange = { change -> save { container.settings.updateSubtitleStyle(change) } },
                    modifier = Modifier.weight(1f),
                    onNavigateLeft = { sectionFocus.getValue(SettingsSection.Subtitles).requestTvFocus() },
                )
            }
        } else key(section) {
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
                        item("seek-interval") {
                            SettingRow(
                                "Seek interval", "Applies to remote shortcuts and the playback timeline.", "${settings.seekSeconds} sec",
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.SeekInterval)),
                                onClick = { openDialog(SettingsDialog.SeekInterval) },
                            )
                        }
                        item("autoplay") {
                            SettingRow(
                                "Autoplay next episode", "Continue automatically when an episode ends.",
                                checked = settings.autoplayNext,
                                onClick = { save { container.settings.setAutoplayNext(!settings.autoplayNext) } },
                            )
                        }
                        item("spoilers") {
                            SettingRow(
                                "Hide episode spoilers",
                                "Hide titles, images, and descriptions for unwatched episodes.",
                                checked = settings.hideSpoilers,
                                onClick = { save { container.settings.setHideSpoilers(!settings.hideSpoilers) } },
                            )
                        }
                        item("ending") {
                            SettingRow(
                                "Skip endings automatically", "Skip the ending when the stream provides timing.",
                                checked = settings.skipEnding,
                                onClick = { save { container.settings.setSkipEnding(!settings.skipEnding) } },
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
                        item("title-language") {
                            SettingRow(
                                "Title language", "Choose how anime titles are displayed.", settings.titleLanguage.label,
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.TitleLanguage)),
                                onClick = { openDialog(SettingsDialog.TitleLanguage) },
                            )
                        }
                        item("home-cards") {
                            SettingRow(
                                "Home card size", "Adjust artwork size in Home rows.", settings.homeCardSize.label,
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.HomeCards)),
                                onClick = { openDialog(SettingsDialog.HomeCards) },
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
                    SettingsSection.Tmdb -> {
                        item("tmdb-key") {
                            SettingRow(
                                "TMDB API key",
                                "Use TMDB artwork, logos, and episode details.",
                                when {
                                    container.hasCustomTmdb -> "Custom"
                                    container.hasBundledTmdb -> "Built in"
                                    else -> "Off"
                                },
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.TmdbKey)),
                                onClick = { openDialog(SettingsDialog.TmdbKey) },
                            )
                        }
                        if (container.hasCustomTmdb) item("clear-tmdb") {
                            SettingRow(
                                "Remove custom key",
                                if (container.hasBundledTmdb) "Use this build’s bundled TMDB credential."
                                else "Turn off TMDB on this TV.",
                                onClick = { save { container.updateTmdb(null) } },
                            )
                        }
                    }
                    SettingsSection.AniList -> {
                        item("anilist-connect") {
                            SettingRow(
                                if (aniList.linked) "Replace access token" else "Connect AniList",
                                "On your phone, open kavyanshyadav.github.io/Animeplayer/anilist.html.",
                                aniList.name ?: if (aniList.linked) "Linked" else "Offline",
                                modifier = Modifier.focusRequester(dialogFocus.getValue(SettingsDialog.AniListToken)),
                                onClick = { if (!aniList.syncing) openDialog(SettingsDialog.AniListToken) },
                            )
                        }
                        if (aniList.linked) {
                            item("anilist-sync") {
                                SettingRow(
                                    if (aniList.syncing) "Syncing…" else "Sync now",
                                    "Sync list status, episode progress, and scores.",
                                    onClick = { scope.launch { container.aniListAccount.sync() } },
                                )
                            }
                            item("anilist-disconnect") {
                                SettingRow("Disconnect", "Keep the list and history on this TV.",
                                    onClick = { scope.launch { container.aniListAccount.disconnect() } })
                            }
                        }
                        aniList.message?.let { message ->
                            item("anilist-message") { Text(message, color = colors.onBackground) }
                        }
                    }
                    SettingsSection.Updates -> {
                        item("check-update") {
                            SettingRow(
                                "Check for updates",
                                updateMessage(updates),
                                "v${BuildConfig.VERSION_NAME}",
                                modifier = Modifier.focusRequester(checkFocus),
                                onClick = { scope.launch { container.updates.check() } },
                            )
                        }
                        item("auto-update") {
                            SettingRow(
                                "Automatic updates",
                                "Check daily and download eligible updates. Android asks before installing.",
                                checked = settings.autoCheckUpdates,
                                onClick = { save { container.settings.setAutoCheckUpdates(!settings.autoCheckUpdates) } },
                            )
                        }
                        when (val current = updates) {
                            is UpdateStatus.Available -> item("download-update") {
                                SettingRow("Download update", "Android asks before installing it.", onClick = {
                                    scope.launch { container.updates.download(current.release) }
                                })
                            }
                            is UpdateStatus.Ready -> item("install-update") {
                                SettingRow("Install update", "Open the Android installer.", onClick = {
                                    saveError = container.updates.install(context, current)
                                })
                            }
                            is UpdateStatus.Failed -> current.release?.let { release ->
                                item("retry-update") {
                                    SettingRow("Retry download", onClick = { scope.launch { container.updates.download(release) } })
                                }
                            }
                            else -> Unit
                        }
                    }
                    SettingsSection.Subtitles -> Unit
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
        SettingsDialog.SeekInterval -> SettingsOptions(
            "Seek interval", seekIntervals.indexOf(settings.seekSeconds), seekIntervals.size, dismiss, saveError,
        ) { index, modifier ->
            val seconds = seekIntervals[index]
            SettingRow("$seconds seconds", modifier = modifier, chosen = seconds == settings.seekSeconds,
                onClick = { save(onSuccess = dismiss) { container.settings.setSeekSeconds(seconds) } })
        }
        SettingsDialog.TitleLanguage -> SettingsOptions(
            "Title language", settings.titleLanguage.ordinal, TitleLanguage.entries.size, dismiss, saveError,
        ) { index, modifier ->
            val choice = TitleLanguage.entries[index]
            SettingRow(choice.label, modifier = modifier, chosen = choice == settings.titleLanguage,
                onClick = { save(onSuccess = dismiss) { container.settings.setTitleLanguage(choice) } })
        }
        SettingsDialog.SubtitleLanguage -> SettingsOptions(
            "Preferred subtitle language", settings.subtitleLanguage.ordinal, SubtitleLanguage.entries.size, dismiss, saveError,
        ) { index, modifier ->
            val language = SubtitleLanguage.entries[index]
            SettingRow(language.label, chosen = settings.subtitleLanguage == language, modifier = modifier,
                onClick = { save(onSuccess = dismiss) { container.settings.setSubtitleLanguage(language) } })
        }
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
        SettingsDialog.TmdbKey -> ServiceKeyDialog(
            title = "TMDB API key",
            detail = "Enter a TMDB v3 API key or API Read Access Token.",
            onDismiss = dismiss,
            onSave = { container.updateTmdb(it); null },
        )
        SettingsDialog.AniListToken -> ServiceKeyDialog(
            title = "AniList access token",
            detail = "Paste the token from the AniList sign-in page. You can use the remote keyboard on your phone.",
            onDismiss = dismiss,
            onSave = {
                container.aniListAccount.connect(it)
                container.aniListAccount.state.value.message
            },
        )
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

private fun updateMessage(status: UpdateStatus): String = when (status) {
    UpdateStatus.Idle -> "Look for a newer Android TV release on GitHub."
    UpdateStatus.Checking -> "Checking GitHub…"
    UpdateStatus.Current -> "You’re on the latest Android TV release."
    UpdateStatus.Downloading -> "Downloading from GitHub…"
    is UpdateStatus.Available -> "Version ${status.release.version} is available."
    is UpdateStatus.Ready -> "Version ${status.release.version} is ready to install."
    is UpdateStatus.Failed -> status.message
}

@Composable
private fun ServiceKeyDialog(
    title: String,
    detail: String,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> String?,
) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }

    fun submit() {
        if (busy || input.isBlank()) return
        busy = true
        message = null
        scope.launch {
            try {
                message = onSave(input)
                if (message == null) onDismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: TmdbCredentialError) {
                message = failure.message
            } catch (_: Exception) {
                message = "Couldn’t validate or save the credential. Try again."
            } finally {
                busy = false
            }
        }
    }

    TvSheet(
        title = title,
        onDismiss = { if (!busy) onDismiss() },
        initialFocus = fieldFocus,
        actions = {
            TvButton("Cancel", enabled = !busy, onClick = onDismiss)
            TvButton(if (busy) "Checking…" else "Save key", enabled = !busy && input.isNotBlank(),
                modifier = Modifier.focusRequester(saveFocus), onClick = ::submit)
        },
    ) {
        Text(detail, color = MaterialTheme.colorScheme.onSurface)
        TvTextField(
            value = input,
            onValueChange = { if (!busy) input = it.take(4096) },
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
            placeholder = "Enter key or token",
            onSearch = ::submit,
            downFocus = saveFocus,
            secret = true,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
            val icon = section.icon
            if (icon == null) {
                TvIcon(ImageVector.vectorResource(MediaR.drawable.exo_ic_subtitle_on), size = 19.dp)
            } else {
                TvIcon(icon, size = 19.dp)
            }
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
