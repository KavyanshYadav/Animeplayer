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
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.animeplayer.tv.ui.player.subtitles.SubtitleColor
import dev.animeplayer.tv.ui.player.subtitles.SubtitleEdge
import dev.animeplayer.tv.ui.player.subtitles.SubtitleEdgeStrength
import dev.animeplayer.tv.ui.player.subtitles.SubtitlePlate
import dev.animeplayer.tv.ui.player.subtitles.SubtitlePosition
import dev.animeplayer.tv.ui.player.subtitles.SubtitleWeight
import dev.animeplayer.tv.ui.model.TitleLanguage
import dev.animeplayer.tv.ui.player.subtitles.SubtitleLanguage
import dev.animeplayer.tv.ui.player.subtitles.SubtitleFont
import dev.animeplayer.tv.ui.player.subtitles.SubtitlePreset
import dev.animeplayer.tv.ui.player.subtitles.SubtitleStyle
import dev.animeplayer.tv.ui.theme.TV_DEFAULT_ACCENT
import dev.animeplayer.tv.ui.theme.TV_DEFAULT_THEME
import dev.animeplayer.tv.ui.theme.TvThemeName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** How much space an artwork card takes in the Home rows. */
public enum class HomeCardSize(public val label: String, internal val cardsAcross: Float) {
    Standard("Standard", 4.3f),
    Large("Large", 3.4f),
    ExtraLarge("Extra large", 2.5f),
}

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
    val homeCardSize: HomeCardSize = HomeCardSize.Large,
    val autoCheckUpdates: Boolean = true,
    val hideSpoilers: Boolean = true,
    val skipEnding: Boolean = false,
    val subtitleLanguage: SubtitleLanguage = SubtitleLanguage.Automatic,
    val titleLanguage: TitleLanguage = TitleLanguage.English,
    val seekSeconds: Int = 10,
    val watchRoomServer: String = "",
    val carouselTrailers: Boolean = true,
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

    internal suspend fun setHomeCardSize(size: HomeCardSize) {
        store.edit { it[KEY_HOME_CARD_SIZE] = size.name }
    }

    internal suspend fun setCarouselTrailers(enabled: Boolean) {
        store.edit { it[KEY_CAROUSEL_TRAILERS] = enabled }
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

    internal suspend fun setWatchRoomServer(url: String) {
        store.edit { it[KEY_WATCH_ROOM_SERVER] = url }
    }

    internal suspend fun setSeekSeconds(seconds: Int) {
        require(seconds in seekIntervals)
        store.edit { it[KEY_SEEK_SECONDS] = seconds.toString() }
    }

    internal suspend fun setTitleLanguage(language: TitleLanguage) {
        store.edit { it[KEY_TITLE_LANGUAGE] = language.name }
    }

    internal suspend fun setSubtitleLanguage(language: SubtitleLanguage) {
        store.edit { it[KEY_SUBTITLE_LANGUAGE] = language.name }
    }

    internal suspend fun setSkipEnding(enabled: Boolean) {
        store.edit { it[KEY_SKIP_ENDING] = enabled }
    }

    internal suspend fun setHideSpoilers(enabled: Boolean) {
        store.edit { it[KEY_HIDE_SPOILERS] = enabled }
    }

    internal suspend fun setAutoCheckUpdates(enabled: Boolean) {
        store.edit { it[KEY_AUTO_UPDATE] = enabled }
    }

    /** Remembers the style of the subtitles. */
    public suspend fun setSubtitleStyle(style: SubtitleStyle) {
        store.edit { it.writeSubtitleStyle(style) }
    }

    internal suspend fun updateSubtitleStyle(change: (SubtitleStyle) -> SubtitleStyle) {
        store.edit { it.writeSubtitleStyle(change(it.readSubtitleStyle())) }
    }

    private fun Preferences.toSettings(): TvSettings = TvSettings(
        theme = this[KEY_THEME]?.let { name ->
            TvThemeName.entries.firstOrNull { it.name == name }
        } ?: TV_DEFAULT_THEME,
        accent = this[KEY_ACCENT]?.toColor() ?: TV_DEFAULT_ACCENT,
        homeCardSize = HomeCardSize.entries.firstOrNull { it.name == this[KEY_HOME_CARD_SIZE] } ?: HomeCardSize.Large,
        autoplayNext = this[KEY_AUTOPLAY] ?: true,
        skipIntro = this[KEY_SKIP_INTRO] ?: true,
        quality = this[KEY_QUALITY],
        dubbed = this[KEY_DUBBED] ?: false,
        subtitleStyle = readSubtitleStyle(),
        autoCheckUpdates = this[KEY_AUTO_UPDATE] ?: true,
        hideSpoilers = this[KEY_HIDE_SPOILERS] ?: true,
        skipEnding = this[KEY_SKIP_ENDING] ?: false,
        subtitleLanguage = subtitleValue<SubtitleLanguage>(KEY_SUBTITLE_LANGUAGE) ?: SubtitleLanguage.Automatic,
        titleLanguage = subtitleValue<TitleLanguage>(KEY_TITLE_LANGUAGE) ?: TitleLanguage.English,
        seekSeconds = this[KEY_SEEK_SECONDS]?.toIntOrNull()?.takeIf { it in seekIntervals } ?: 10,
        watchRoomServer = this[KEY_WATCH_ROOM_SERVER].orEmpty(),
        carouselTrailers = this[KEY_CAROUSEL_TRAILERS] ?: true,
    )

    private companion object {
        val KEY_HOME_CARD_SIZE = stringPreferencesKey("home-card-size")
        val KEY_CAROUSEL_TRAILERS = booleanPreferencesKey("carousel-trailers")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_ACCENT = stringPreferencesKey("accent")
        val KEY_AUTOPLAY = booleanPreferencesKey("autoplay-next")
        val KEY_SKIP_INTRO = booleanPreferencesKey("skip-intro")
        val KEY_QUALITY = stringPreferencesKey("quality")
        val KEY_DUBBED = booleanPreferencesKey("dubbed")
        val KEY_AUTO_UPDATE = booleanPreferencesKey("auto-check-updates")
        val KEY_HIDE_SPOILERS = booleanPreferencesKey("hide-episode-spoilers")
        val KEY_SKIP_ENDING = booleanPreferencesKey("skip-endings")
        val KEY_SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle-language")
        val KEY_TITLE_LANGUAGE = stringPreferencesKey("title-language")
        val KEY_SEEK_SECONDS = stringPreferencesKey("seek-seconds")
        val KEY_WATCH_ROOM_SERVER = stringPreferencesKey("watch-room-server")
    }
}

private val subtitlePresetKey = stringPreferencesKey("subtitle-preset")
private val subtitleSizeKey = stringPreferencesKey("subtitle-size")
private val subtitlePositionKey = stringPreferencesKey("subtitle-position")
private val subtitleFontKey = stringPreferencesKey("subtitle-font")
private val subtitleSizeOverrideKey = stringPreferencesKey("subtitle-size-sp")
private val subtitleWeightKey = stringPreferencesKey("subtitle-weight")
private val subtitleAnchorKey = stringPreferencesKey("subtitle-anchor")
private val subtitleEdgeKey = stringPreferencesKey("subtitle-edge")
private val subtitleEdgeStrengthKey = stringPreferencesKey("subtitle-edge-strength")
private val subtitleTextColorKey = stringPreferencesKey("subtitle-text-color")
private val subtitleTextOpacityKey = stringPreferencesKey("subtitle-text-opacity")
private val subtitlePlateKey = stringPreferencesKey("subtitle-plate")
private val subtitlePlateColorKey = stringPreferencesKey("subtitle-plate-color")
private val subtitlePlateOpacityKey = stringPreferencesKey("subtitle-plate-opacity")

internal fun Preferences.readSubtitleStyle() = SubtitleStyle(
    preset = subtitleValue<SubtitlePreset>(subtitlePresetKey) ?: SubtitlePreset.Default,
    sizeStep = this[subtitleSizeKey]?.toIntOrNull() ?: 0,
    positionStep = this[subtitlePositionKey]?.toIntOrNull() ?: 0,
    fontOverride = subtitleValue<SubtitleFont>(subtitleFontKey),
    sizeOverrideSp = this[subtitleSizeOverrideKey]?.toIntOrNull(),
    weight = subtitleValue<SubtitleWeight>(subtitleWeightKey) ?: SubtitleWeight.Bold,
    positionOverride = subtitleValue<SubtitlePosition>(subtitleAnchorKey),
    edgeOverride = subtitleValue<SubtitleEdge>(subtitleEdgeKey),
    edgeStrength = subtitleValue<SubtitleEdgeStrength>(subtitleEdgeStrengthKey) ?: SubtitleEdgeStrength.Medium,
    textColorOverride = subtitleValue<SubtitleColor>(subtitleTextColorKey),
    textOpacityPercent = this[subtitleTextOpacityKey]?.toIntOrNull() ?: 100,
    plateOverride = subtitleValue<SubtitlePlate>(subtitlePlateKey),
    plateColorOverride = subtitleValue<SubtitleColor>(subtitlePlateColorKey),
    plateOpacityPercent = this[subtitlePlateOpacityKey]?.toIntOrNull(),
)

internal fun MutablePreferences.writeSubtitleStyle(style: SubtitleStyle) {
    this[subtitlePresetKey] = style.preset.name
    this[subtitleSizeKey] = style.sizeStep.toString()
    this[subtitlePositionKey] = style.positionStep.toString()
    setSubtitleValue(subtitleFontKey, style.fontOverride?.name)
    setSubtitleValue(subtitleSizeOverrideKey, style.sizeOverrideSp?.toString())
    this[subtitleWeightKey] = style.weight.name
    setSubtitleValue(subtitleAnchorKey, style.positionOverride?.name)
    setSubtitleValue(subtitleEdgeKey, style.edgeOverride?.name)
    this[subtitleEdgeStrengthKey] = style.edgeStrength.name
    setSubtitleValue(subtitleTextColorKey, style.textColorOverride?.name)
    this[subtitleTextOpacityKey] = style.textOpacityPercent.toString()
    setSubtitleValue(subtitlePlateKey, style.plateOverride?.name)
    setSubtitleValue(subtitlePlateColorKey, style.plateColorOverride?.name)
    setSubtitleValue(subtitlePlateOpacityKey, style.plateOpacityPercent?.toString())
}

private inline fun <reified E : Enum<E>> Preferences.subtitleValue(key: Preferences.Key<String>): E? =
    enumValues<E>().firstOrNull { it.name == this[key] }

private fun MutablePreferences.setSubtitleValue(key: Preferences.Key<String>, value: String?) {
    if (value == null) remove(key) else this[key] = value
}

internal val seekIntervals = listOf(10, 30, 60)

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
