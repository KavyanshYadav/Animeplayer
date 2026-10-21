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

import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

/**
 * The colour tokens of the application.
 *
 * The values are the ones the desktop client uses, ported from its CSS custom properties.
 * Every theme redefines the same set of tokens, so a component never has to know which
 * theme is active.
 *
 * @property background the colour behind everything.
 * @property foreground the default colour of the text.
 * @property muted the colour of a recessed surface.
 * @property mutedForeground the colour of the secondary text.
 * @property popover the colour of a floating surface.
 * @property popoverForeground the colour of the text on a floating surface.
 * @property card the colour of a card.
 * @property cardForeground the colour of the text on a card.
 * @property border the colour of a hairline.
 * @property input the colour of an input surface.
 * @property primary the accent colour.
 * @property primaryForeground the colour of the text on the accent colour.
 * @property secondary the secondary accent colour.
 * @property secondaryForeground the colour of the text on the secondary accent colour.
 * @property accent a quiet accent, used for chips and hovered rows.
 * @property accentForeground the colour of the text on the quiet accent.
 * @property destructive the colour of a failure.
 * @property destructiveForeground the colour of the text on a failure.
 * @property ring the colour of the focus ring.
 * @property success the colour of a success.
 * @property successForeground the colour of the text on a success.
 */
public data class TvColorTokens(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val popover: Color,
    val popoverForeground: Color,
    val card: Color,
    val cardForeground: Color,
    val border: Color,
    val input: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val destructive: Color,
    val destructiveForeground: Color,
    val ring: Color,
    val success: Color,
    val successForeground: Color,
    /** `true` when the theme is a dark one. */
    val isDark: Boolean = true,
) {
    /**
     * Maps the tokens onto a Material 3 colour scheme.
     */
    public fun toColorScheme(): ColorScheme {
        val scheme = if (isDark) {
            darkColorScheme(
                primary = primary,
                onPrimary = primaryForeground,
                primaryContainer = accent,
                onPrimaryContainer = accentForeground,
                secondary = secondary,
                onSecondary = secondaryForeground,
                secondaryContainer = secondary,
                onSecondaryContainer = secondaryForeground,
                tertiary = success,
                onTertiary = successForeground,
                background = background,
                onBackground = foreground,
                surface = card,
                onSurface = cardForeground,
                surfaceVariant = muted,
                onSurfaceVariant = mutedForeground,
                surfaceTint = primary,
                inverseSurface = popover,
                inverseOnSurface = popoverForeground,
                error = destructive,
                onError = destructiveForeground,
                border = border,
                borderVariant = border,
                scrim = Color.Black,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = primaryForeground,
                primaryContainer = accent,
                onPrimaryContainer = accentForeground,
                secondary = secondary,
                onSecondary = secondaryForeground,
                secondaryContainer = secondary,
                onSecondaryContainer = secondaryForeground,
                tertiary = success,
                onTertiary = successForeground,
                background = background,
                onBackground = foreground,
                surface = card,
                onSurface = cardForeground,
                surfaceVariant = muted,
                onSurfaceVariant = mutedForeground,
                surfaceTint = primary,
                inverseSurface = popover,
                inverseOnSurface = popoverForeground,
                error = destructive,
                onError = destructiveForeground,
                border = border,
                borderVariant = border,
                scrim = Color.Black,
            )
        }
        return scheme
    }
}

/**
 * The themes of the application.
 *
 * The names match the ones the desktop client offers, so a user who switches between the
 * two applications finds the same theme under the same name.
 */
public enum class TvThemeName(
    public val label: String,
    /** The colour shown in the picker. */
    public val swatch: Color,
    public val isDark: Boolean,
) {
    Default("Default", Color(0xFFF5A524), true),
    Light("Light", Color(0xFFE5E5E5), false),
    Amber("Amber", Color(0xFFF5A524), true),
    Lavender("Lavender", Color(0xFFA78BFA), true),
    Catppuccin("Catppuccin", Color(0xFFF5A0A0), true),
    Dracula("Dracula", Color(0xFFBD93F9), true),
    Custom("Custom", Color(0xFFA78BFA), true),
    ;

    /** The tokens of this theme, without the accent override of [Custom]. */
    public fun tokens(accent: Color = Default.swatch): TvColorTokens = when (this) {
        Default -> darkTokens()
        Light -> lightTokens()
        Amber -> amberTokens()
        Lavender -> lavenderTokens()
        Catppuccin -> catppuccinTokens()
        Dracula -> draculaTokens()
        Custom -> lavenderTokens().copy(primary = accent, primaryForeground = contrastOn(accent))
    }
}

/**
 * The accent colours the picker offers.
 *
 * The list is the one of the desktop client.
 */
public val TV_ACCENTS: List<Color> = listOf(
    Color(0xFFF5A524),
    Color(0xFF8B5CF6),
    Color(0xFF3B82F6),
    Color(0xFF10B981),
    Color(0xFFEF4444),
    Color(0xFFEC4899),
    Color(0xFFF43F5E),
    Color(0xFF06B6D4),
    Color(0xFFEAB308),
    Color(0xFF84CC16),
)

/** The default accent. */
public val TV_DEFAULT_ACCENT: Color = TV_ACCENTS.first()

/** The default theme. */
public val TV_DEFAULT_THEME: TvThemeName = TvThemeName.Default

// ---------------------------------------------------------------------------
// The token sets, ported from the CSS custom properties of the desktop client.
// ---------------------------------------------------------------------------

private fun darkTokens(): TvColorTokens = TvColorTokens(
    background = hsl(0f, 0f, 0f),
    foreground = hsl(0f, 0f, 0.96f),
    muted = hsl(0f, 0f, 0.04f),
    mutedForeground = hsl(0f, 0f, 0.50f),
    popover = hsl(0f, 0f, 0.04f),
    popoverForeground = hsl(0f, 0f, 0.96f),
    card = hsl(0f, 0f, 0.04f),
    cardForeground = hsl(0f, 0f, 0.96f),
    border = hsl(0f, 0f, 0.10f),
    input = hsl(0f, 0f, 0.15f),
    primary = hsl(38f, 0.92f, 0.50f),
    primaryForeground = hsl(0f, 0f, 0.08f),
    secondary = hsl(38f, 0.30f, 0.18f),
    secondaryForeground = hsl(0f, 0f, 0.96f),
    accent = hsl(38f, 0.30f, 0.10f),
    accentForeground = hsl(38f, 0.92f, 0.85f),
    destructive = hsl(0f, 0.628f, 0.306f),
    destructiveForeground = hsl(0f, 0f, 0.98f),
    ring = hsl(38f, 0.92f, 0.50f),
    success = hsl(145f, 0.63f, 0.45f),
    successForeground = hsl(0f, 0f, 0.06f),
)

private fun lightTokens(): TvColorTokens = TvColorTokens(
    background = hsl(0f, 0f, 1f),
    foreground = hsl(0f, 0f, 0f),
    muted = hsl(0f, 0f, 0.92f),
    mutedForeground = hsl(0f, 0f, 0.28f),
    popover = hsl(0f, 0f, 1f),
    popoverForeground = hsl(0f, 0f, 0f),
    card = hsl(0f, 0f, 0.97f),
    cardForeground = hsl(0f, 0f, 0f),
    border = hsl(0f, 0f, 0.80f),
    input = hsl(0f, 0f, 0.67f),
    primary = hsl(38f, 0.92f, 0.45f),
    primaryForeground = hsl(0f, 0f, 1f),
    secondary = hsl(38f, 0.30f, 0.88f),
    secondaryForeground = hsl(0f, 0f, 0f),
    accent = hsl(38f, 0.30f, 0.83f),
    accentForeground = hsl(0f, 0f, 0f),
    destructive = hsl(0f, 0.628f, 0.306f),
    destructiveForeground = hsl(0f, 0f, 1f),
    ring = hsl(38f, 0.92f, 0.45f),
    success = hsl(145f, 0.63f, 0.35f),
    successForeground = hsl(0f, 0f, 1f),
    isDark = false,
)

private fun amberTokens(): TvColorTokens = TvColorTokens(
    background = hsl(38f, 0.20f, 0.02f),
    foreground = hsl(0f, 0f, 0.96f),
    muted = hsl(38f, 0.20f, 0.06f),
    mutedForeground = hsl(0f, 0f, 0.55f),
    popover = hsl(38f, 0.20f, 0.06f),
    popoverForeground = hsl(0f, 0f, 0.96f),
    card = hsl(38f, 0.20f, 0.04f),
    cardForeground = hsl(0f, 0f, 0.96f),
    border = hsl(38f, 0.20f, 0.12f),
    input = hsl(38f, 0.15f, 0.18f),
    primary = hsl(38f, 0.92f, 0.50f),
    primaryForeground = hsl(0f, 0f, 0.10f),
    secondary = hsl(38f, 0.30f, 0.18f),
    secondaryForeground = hsl(0f, 0f, 0.96f),
    accent = hsl(38f, 0.30f, 0.10f),
    accentForeground = hsl(38f, 0.92f, 0.85f),
    destructive = hsl(0f, 0.628f, 0.306f),
    destructiveForeground = hsl(0f, 0f, 0.98f),
    ring = hsl(38f, 0.92f, 0.50f),
    success = hsl(145f, 0.63f, 0.45f),
    successForeground = hsl(0f, 0f, 0.06f),
)

private fun lavenderTokens(): TvColorTokens = TvColorTokens(
    background = hsl(270f, 0.15f, 0.02f),
    foreground = hsl(0f, 0f, 0.96f),
    muted = hsl(270f, 0.15f, 0.06f),
    mutedForeground = hsl(0f, 0f, 0.55f),
    popover = hsl(270f, 0.15f, 0.06f),
    popoverForeground = hsl(0f, 0f, 0.96f),
    card = hsl(270f, 0.15f, 0.04f),
    cardForeground = hsl(0f, 0f, 0.96f),
    border = hsl(270f, 0.15f, 0.12f),
    input = hsl(270f, 0.15f, 0.18f),
    primary = hsl(270f, 0.70f, 0.60f),
    primaryForeground = hsl(0f, 0f, 0.98f),
    secondary = hsl(270f, 0.30f, 0.18f),
    secondaryForeground = hsl(0f, 0f, 0.96f),
    accent = hsl(270f, 0.30f, 0.14f),
    accentForeground = hsl(270f, 0.70f, 0.90f),
    destructive = hsl(0f, 0.628f, 0.306f),
    destructiveForeground = hsl(0f, 0f, 0.98f),
    ring = hsl(270f, 0.70f, 0.60f),
    success = hsl(145f, 0.63f, 0.45f),
    successForeground = hsl(0f, 0f, 0.06f),
)

private fun catppuccinTokens(): TvColorTokens = TvColorTokens(
    background = hsl(240f, 0.21f, 0.15f),
    foreground = hsl(226f, 0.64f, 0.88f),
    muted = hsl(240f, 0.23f, 0.09f),
    mutedForeground = hsl(228f, 0.24f, 0.72f),
    popover = hsl(240f, 0.23f, 0.09f),
    popoverForeground = hsl(226f, 0.64f, 0.88f),
    card = hsl(240f, 0.23f, 0.09f),
    cardForeground = hsl(226f, 0.64f, 0.88f),
    border = hsl(234f, 0.13f, 0.31f),
    input = hsl(234f, 0.13f, 0.31f),
    primary = hsl(23f, 0.92f, 0.75f),
    primaryForeground = hsl(240f, 0.23f, 0.09f),
    secondary = hsl(234f, 0.13f, 0.31f),
    secondaryForeground = hsl(226f, 0.64f, 0.88f),
    accent = hsl(237f, 0.16f, 0.23f),
    accentForeground = hsl(226f, 0.64f, 0.88f),
    destructive = hsl(343f, 0.81f, 0.75f),
    destructiveForeground = hsl(240f, 0.23f, 0.09f),
    ring = hsl(23f, 0.92f, 0.75f),
    success = hsl(115f, 0.40f, 0.60f),
    successForeground = hsl(240f, 0.23f, 0.09f),
)

private fun draculaTokens(): TvColorTokens = TvColorTokens(
    background = hsl(231f, 0.15f, 0.18f),
    foreground = hsl(60f, 0.30f, 0.96f),
    muted = hsl(225f, 0.15f, 0.24f),
    mutedForeground = hsl(225f, 0.27f, 0.51f),
    popover = hsl(225f, 0.15f, 0.24f),
    popoverForeground = hsl(60f, 0.30f, 0.96f),
    card = hsl(225f, 0.15f, 0.24f),
    cardForeground = hsl(60f, 0.30f, 0.96f),
    border = hsl(232f, 0.14f, 0.31f),
    input = hsl(232f, 0.14f, 0.31f),
    primary = hsl(265f, 0.89f, 0.78f),
    primaryForeground = hsl(232f, 0.14f, 0.11f),
    secondary = hsl(235f, 0.14f, 0.15f),
    secondaryForeground = hsl(60f, 0.30f, 0.96f),
    accent = hsl(232f, 0.14f, 0.31f),
    accentForeground = hsl(60f, 0.30f, 0.96f),
    destructive = hsl(6f, 0.66f, 0.48f),
    destructiveForeground = hsl(60f, 0.30f, 0.96f),
    ring = hsl(258f, 0.61f, 0.60f),
    success = hsl(135f, 0.70f, 0.60f),
    successForeground = hsl(231f, 0.15f, 0.18f),
)

/** `hsl(38 92% 50%)` in CSS becomes this. */
private fun hsl(hue: Float, saturation: Float, lightness: Float): Color =
    Color.hsl(hue, saturation, lightness)

/**
 * Picks the colour that reads on top of [background].
 *
 * The rule is the one the CSS `color-mix` produced: a light accent gets dark text, a
 * dark accent gets light text.
 */
internal fun contrastOn(background: Color): Color {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luminance > 0.55f) Color(0xFF0A0A0A) else Color(0xFFF8F8F8)
}
