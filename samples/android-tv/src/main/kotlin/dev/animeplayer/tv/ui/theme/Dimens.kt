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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measurements of the application.
 *
 * A television screen is 960dp wide when it has 1080 pixels, so the values here are
 * chosen for that canvas: the margin is 5 % of the width, which is the safe area every
 * television guarantees.
 */
public object TvDimens {

    /** The margin between the content and the edge of the screen. */
    public val ScreenMargin: Dp = 48.dp

    /** The gap between two cards of a rail. */
    public val RailGap: Dp = 12.dp

    /** The gap between two rails. */
    public val RailSpacing: Dp = 28.dp

    /** The height of the navigation bar. */
    public val TopBarHeight: Dp = 72.dp

    /** The radius of a card. */
    public val CardRadius: Dp = 8.dp

    /** The radius of a panel or a sheet. */
    public val PanelRadius: Dp = 16.dp

    /** The width of the focus ring. */
    public val FocusRingWidth: Dp = 2.dp

    /** The blur radius of the focus glow. */
    public val FocusGlow: Dp = 8.dp

    /** How much a focused element grows. */
    public const val FocusScale: Float = 1.06f

    /** How much a pressed element shrinks. */
    public const val PressedScale: Float = 0.97f

    /**
     * How many cards of a rail are visible at once.
     *
     * The card width is derived from the width of the screen, so the same number of cards
     * is visible on a 1080p set and on a 4K one.
     */
    public const val VisibleCards: Int = 6

    /** The width of the side panel of the player. */
    public val SidePanelWidth: Dp = 420.dp

    /** The height of the hero banner, as a fraction of the height of the screen. */
    public const val HeroHeightFraction: Float = 0.42f

    /** The width of a resume card, wide enough for the episode progress line. */
    public val ContinueCardWidth: Dp = 220.dp

    /** The height of the artwork of a landscape card, as a fraction of its width. */
    public const val CardArtRatio: Float = 9f / 16f
}
