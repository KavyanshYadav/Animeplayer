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
package dev.animeplayer.tv

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.animeplayer.tv.ui.player.subtitles.SubtitlePreset
import dev.animeplayer.tv.ui.player.subtitles.SubtitleStyle
import dev.animeplayer.tv.ui.theme.TV_DEFAULT_ACCENT
import dev.animeplayer.tv.ui.theme.TV_DEFAULT_THEME
import dev.animeplayer.tv.ui.theme.TvThemeName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The preferences of the user. */
public data class TvSettings(
    val theme: TvThemeName = TV_DEFAULT_THEME,
    val accent: Color = TV_DEFAULT_ACCENT,
    val autoplayNext: Boolean = true,
    val skipIntro: Boolean = true,
    /** The preferred quality, or `null` for the best one. */
    val quality: String? = null,
    /** `true` for the dubbed track, `false` for the subtitled one. */
    val dubbed: Boolean = false,
    /** The style of the subtitles. */
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
)

/**
 * The settings of the application.
 *
 * The settings live in a [DataStore], so they survive a restart and are shared between
 * the screens.
 */
public class SettingsRepository(context: Context) {

    private val store: DataStore<Preferences> = context.settingsStore

    /** The settings, as a flow. */
    public val settings: Flow<TvSettings> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toSettings() }

    /** Sets the theme. */
    public suspend fun setTheme(theme: TvThemeName) {
        store.edit { it[KEY_THEME] = theme.name }
    }

    /** Sets the accent colour. */
    public suspend fun setAccent(accent: Color) {
        store.edit { it[KEY_ACCENT] = accent.toHex() }
    }

    /** Turns the automatic playback of the next episode on or off. */
    public suspend fun setAutoplayNext(enabled: Boolean) {
        store.edit { it[KEY_AUTOPLAY] = enabled }
    }

    /** Turns the automatic skipping of the intro on or off. */
    public suspend fun setSkipIntro(enabled: Boolean) {
        store.edit { it[KEY_SKIP_INTRO] = enabled }
    }

    /** Sets the preferred quality. `null` means "the best one". */
    public suspend fun setQuality(quality: String?) {
        store.edit { preferences ->
            if (quality == null) preferences.remove(KEY_QUALITY) else preferences[KEY_QUALITY] = quality
        }
    }

    /** Chooses between the subtitled and the dubbed track. */
    public suspend fun setDubbed(dubbed: Boolean) {
        store.edit { it[KEY_DUBBED] = dubbed }
    }

    /** Remembers the style of the subtitles. */
    public suspend fun setSubtitleStyle(style: SubtitleStyle) {
        store.edit {
            it[KEY_SUBTITLE_PRESET] = style.preset.name
            it[KEY_SUBTITLE_SIZE] = style.sizeStep.toString()
            it[KEY_SUBTITLE_POSITION] = style.positionStep.toString()
        }
    }

    private fun Preferences.toSettings(): TvSettings = TvSettings(
        theme = this[KEY_THEME]?.let { name ->
            TvThemeName.entries.firstOrNull { it.name == name }
        } ?: TV_DEFAULT_THEME,
        accent = this[KEY_ACCENT]?.toColor() ?: TV_DEFAULT_ACCENT,
        autoplayNext = this[KEY_AUTOPLAY] ?: true,
        skipIntro = this[KEY_SKIP_INTRO] ?: true,
        quality = this[KEY_QUALITY],
        dubbed = this[KEY_DUBBED] ?: false,
        subtitleStyle = SubtitleStyle(
            preset = this[KEY_SUBTITLE_PRESET]?.let { name ->
                SubtitlePreset.entries.firstOrNull { it.name == name }
            } ?: SubtitlePreset.Default,
            sizeStep = this[KEY_SUBTITLE_SIZE]?.toIntOrNull() ?: 0,
            positionStep = this[KEY_SUBTITLE_POSITION]?.toIntOrNull() ?: 0,
        ),
    )

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_ACCENT = stringPreferencesKey("accent")
        val KEY_AUTOPLAY = booleanPreferencesKey("autoplay-next")
        val KEY_SKIP_INTRO = booleanPreferencesKey("skip-intro")
        val KEY_QUALITY = stringPreferencesKey("quality")
        val KEY_DUBBED = booleanPreferencesKey("dubbed")
        val KEY_SUBTITLE_PRESET = stringPreferencesKey("subtitle-preset")
        val KEY_SUBTITLE_SIZE = stringPreferencesKey("subtitle-size")
        val KEY_SUBTITLE_POSITION = stringPreferencesKey("subtitle-position")
    }
}

private val Context.settingsStore by preferencesDataStore(name = "animeplayer")

/** `#RRGGBB` */
private fun Color.toHex(): String =
    "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** Parses `#RRGGBB`. */
private fun String.toColor(): Color? {
    val hex = removePrefix("#")
    if (hex.length != 6) return null
    val value = hex.toLongOrNull(16) ?: return null
    return Color(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
    )
}
