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
package dev.animeplayer.tv.ui.player.subtitles

import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.SubtitleTrack
import java.util.Locale

public enum class SubtitleLanguage(public val label: String, internal val tag: String? = null) {
    Automatic("Automatic"), Off("Off"), English("English", "en"), Japanese("Japanese", "ja"),
    Hindi("Hindi", "hi"), Spanish("Spanish", "es"), Portuguese("Portuguese", "pt"),
    French("French", "fr"), German("German", "de"), Italian("Italian", "it"),
    Arabic("Arabic", "ar"), Chinese("Chinese", "zh"), Korean("Korean", "ko"), Russian("Russian", "ru"),
}

internal fun PlayableSource.withSubtitleLanguage(preference: SubtitleLanguage): PlayableSource {
    val tag = preference.tag ?: return this
    val iso3 = Locale.forLanguageTag(tag).isO3Language
    val matches = subtitles.filter { track ->
        val language = track.language.lowercase().replace('_', '-').substringBefore('-')
        language == tag || language == iso3 ||
            track.label.lowercase().contains(Regex("\\b${preference.label.lowercase()}\\b"))
    }
    val preferred = (matches.filterNot(SubtitleTrack::signOnly).ifEmpty { matches })
        .let { tracks -> tracks.firstOrNull { it.isDefault } ?: tracks.firstOrNull() } ?: return this
    return copy(subtitles = subtitles.map { it.copy(isDefault = it.url == preferred.url) })
}

private fun SubtitleTrack.signOnly(): Boolean {
    val name = label.lowercase()
    return ("sign" in name || "song" in name) && "dialogue" !in name && "full" !in name
}
