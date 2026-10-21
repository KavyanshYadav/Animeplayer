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
package dev.animeplayer.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes

/**
 * The shapes of the application.
 */
public val TvShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(TvDimens.CardRadius / 2),
    small = RoundedCornerShape(TvDimens.CardRadius),
    medium = RoundedCornerShape(TvDimens.CardRadius + 2.dp),
    large = RoundedCornerShape(TvDimens.PanelRadius),
    extraLarge = RoundedCornerShape(TvDimens.PanelRadius + 8.dp),
)

/**
 * The parts of the theme the components need but Material 3 does not carry.
 *
 * @property accent the accent colour of the user, used by the focus rings.
 * @property isDark `true` for the dark themes.
 */
public data class TvThemeExtras(
    val accent: Color,
    val isDark: Boolean,
)

/** The extras of the current theme. */
public val LocalTvThemeExtras: ProvidableCompositionLocal<TvThemeExtras> =
    staticCompositionLocalOf { TvThemeExtras(TV_DEFAULT_ACCENT, true) }

/**
 * Wraps the content in the theme of the application.
 *
 * @param theme the theme to use.
 * @param accent the accent colour, used by the [TvThemeName.Custom] theme and by the
 *   focus rings.
 */
@Composable
public fun AnimeTvTheme(
    theme: TvThemeName = TV_DEFAULT_THEME,
    accent: Color = TV_DEFAULT_ACCENT,
    content: @Composable () -> Unit,
) {
    val tokens = theme.tokens(accent)
    val scheme = tokens.toColorScheme()

    CompositionLocalProvider(
        LocalTvThemeExtras provides TvThemeExtras(
            accent = tokens.primary,
            isDark = tokens.isDark,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TvTypography,
            shapes = TvShapes,
            content = content,
        )
    }
}
