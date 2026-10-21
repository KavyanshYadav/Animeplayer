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
package dev.animeplayer.tv.ui.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonGlow
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ButtonShape
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * The focus treatment of the application.
 *
 * A television has no pointer, so the focus is the only state a user can perceive. The
 * treatment is therefore loud on purpose: the element grows, a ring appears around it and
 * a glow lifts it off the background.
 *
 * The ring is drawn in the colour of the surface — near white on the dark themes — and
 * not in the accent colour. An accent coloured element — the primary button, the selected
 * tab — would hide its own ring, and a control the user cannot see the focus on is a
 * control the user cannot use.
 */
public object TvFocus {

    /** The ring that surrounds a focused element. */
    public val ringWidth: Dp = TvDimens.FocusRingWidth

    /** The glow of a focused element. */
    public val glowElevation: Dp = TvDimens.FocusGlow

    /** The colour of the focus ring. */
    public val ringColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    /** The colour of the focus glow. */
    public val glowColor: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    // -----------------------------------------------------------------------
    // For the surfaces — the cards, the chips, the list entries.
    // -----------------------------------------------------------------------

    /**
     * The shape of a surface in each state.
     *
     * The shape does not change when the element is focused — a television has no
     * pointer, so the shape is a promise about what the element is, not a reaction.
     */
    @Composable
    public fun shape(shape: Shape): ClickableSurfaceShape = ClickableSurfaceDefaults.shape(
        shape = shape,
        focusedShape = shape,
        pressedShape = shape,
        disabledShape = shape,
        focusedDisabledShape = shape,
    )

    /**
     * The colours of a surface.
     *
     * The colour does not change when the element is focused either: the ring and the
     * glow carry the focus, so a card keeps its own colour and stays recognisable. The
     * focused and the pressed colours are separate, because a card that sits on the
     * artwork of the banner has to stay transparent in every state.
     */
    @Composable
    public fun colors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        focusedContainerColor: Color = containerColor,
        focusedContentColor: Color = contentColor,
        pressedContainerColor: Color = containerColor,
        pressedContentColor: Color = contentColor,
    ): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = containerColor,
        contentColor = contentColor,
        focusedContainerColor = focusedContainerColor,
        focusedContentColor = focusedContentColor,
        pressedContainerColor = pressedContainerColor,
        pressedContentColor = pressedContentColor,
    )

    /**
     * The scale of a surface in each state.
     */
    public fun scale(
        focused: Float = TvDimens.FocusScale,
        pressed: Float = TvDimens.PressedScale,
    ): ClickableSurfaceScale = ClickableSurfaceDefaults.scale(
        scale = 1f,
        focusedScale = focused,
        pressedScale = pressed,
        disabledScale = 1f,
        focusedDisabledScale = focused,
    )

    /**
     * The glow of a surface in each state.
     */
    @Composable
    public fun glow(accent: Color = glowColor): ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(
        glow = Glow.None,
        focusedGlow = Glow(elevationColor = accent, elevation = glowElevation),
        pressedGlow = Glow(elevationColor = accent, elevation = glowElevation / 2),
    )

    /**
     * The border of a surface in each state.
     */
    @Composable
    public fun border(shape: Shape): ClickableSurfaceBorder = ClickableSurfaceDefaults.border(
        border = Border.None,
        focusedBorder = focusedBorder(shape),
        pressedBorder = focusedBorder(shape),
        disabledBorder = Border.None,
        focusedDisabledBorder = Border(
            border = BorderStroke(ringWidth, ringColor.copy(alpha = 0.4f)),
            inset = 0.dp,
            shape = shape,
        ),
    )

    // -----------------------------------------------------------------------
    // For the buttons.
    // -----------------------------------------------------------------------

    /** The shape of a button in each state. */
    public fun buttonShape(shape: Shape): ButtonShape = ButtonDefaults.shape(
        shape = shape,
        focusedShape = shape,
        pressedShape = shape,
        disabledShape = shape,
        focusedDisabledShape = shape,
    )

    /** The scale of a button in each state. */
    public fun buttonScale(
        focused: Float = TvDimens.FocusScale,
        pressed: Float = TvDimens.PressedScale,
    ): ButtonScale = ButtonDefaults.scale(
        scale = 1f,
        focusedScale = focused,
        pressedScale = pressed,
        disabledScale = 1f,
        focusedDisabledScale = focused,
    )

    /** The glow of a button in each state. */
    @Composable
    public fun buttonGlow(accent: Color = glowColor): ButtonGlow = ButtonDefaults.glow(
        glow = Glow.None,
        focusedGlow = Glow(elevationColor = accent, elevation = glowElevation),
        pressedGlow = Glow(elevationColor = accent, elevation = glowElevation / 2),
    )

    /** The border of a button in each state. */
    @Composable
    public fun buttonBorder(shape: Shape): ButtonBorder = ButtonDefaults.border(
        border = Border.None,
        focusedBorder = focusedBorder(shape),
        pressedBorder = focusedBorder(shape),
        disabledBorder = Border.None,
        focusedDisabledBorder = Border(
            border = BorderStroke(ringWidth, ringColor.copy(alpha = 0.4f)),
            inset = 0.dp,
            shape = shape,
        ),
    )

    @Composable
    private fun focusedBorder(shape: Shape): Border = Border(
        border = BorderStroke(ringWidth, ringColor),
        inset = 0.dp,
        shape = shape,
    )
}
