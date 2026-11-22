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
package dev.animeplayer.tv.ui.model

import androidx.compose.runtime.staticCompositionLocalOf
import dev.animeplayer.core.model.CatalogMedia

public enum class TitleLanguage(public val label: String) {
    English("English"), Romaji("Romaji"), Native("Native title");

    internal fun select(media: CatalogMedia): String =
        when (this) {
            English -> media.englishTitle
            Romaji -> media.romajiTitle
            Native -> media.nativeTitle
        }?.takeIf(String::isNotBlank) ?: media.title
}

internal val LocalTitleLanguage = staticCompositionLocalOf { TitleLanguage.English }

internal fun CatalogCard.localized(language: TitleLanguage): CatalogCard {
    val selected = catalogMedia?.let(language::select)?.takeIf(String::isNotBlank) ?: title
    return if (selected == title) this else copy(title = selected)
}
