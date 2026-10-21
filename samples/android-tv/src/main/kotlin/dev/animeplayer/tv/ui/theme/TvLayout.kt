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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measurements of the current screen.
 *
 * Everything is derived from the width of the screen, so the same number of cards is
 * visible on a 1080p television and on a 4K one, and the interface never looks sparse or
 * crowded.
 *
 * @property width the width of the screen.
 * @property height the height of the screen.
 * @property contentWidth the width the content may use, without the margins.
 * @property cardWidth the width of a card of a rail.
 * @property cardHeight the height of the artwork of a card.
 * @property columns how many cards fit in a grid.
 */
public data class TvLayout(
    val width: Dp,
    val height: Dp,
    val contentWidth: Dp,
    val cardWidth: Dp,
    val cardHeight: Dp,
    val columns: Int,
)

/** The measurements of the current screen. */
public val LocalTvLayout: ProvidableCompositionLocal<TvLayout> = compositionLocalOf {
    TvLayout(
        width = 960.dp,
        height = 540.dp,
        contentWidth = 864.dp,
        cardWidth = 134.dp,
        cardHeight = 75.dp,
        columns = 4,
    )
}

/**
 * The measurements of the current screen.
 */
@Composable
public fun rememberTvLayout(
    visibleCards: Int = TvDimens.VisibleCards,
    gap: Dp = TvDimens.RailGap,
): TvLayout {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val width = with(density) { window.width.toDp() }
    val height = with(density) { window.height.toDp() }
    return remember(width, height, visibleCards, gap) {
        val margin = TvDimens.ScreenMargin
        val contentWidth = (width - margin * 2).coerceAtLeast(120.dp)
        val cardWidth = ((contentWidth - gap * (visibleCards - 1)) / visibleCards).coerceAtLeast(80.dp)
        TvLayout(
            width = width,
            height = height,
            contentWidth = contentWidth,
            cardWidth = cardWidth,
            cardHeight = cardWidth * TvDimens.CardArtRatio,
            columns = ((contentWidth + gap) / (cardWidth * 2 + gap)).toInt().coerceIn(2, 6),
        )
    }
}
