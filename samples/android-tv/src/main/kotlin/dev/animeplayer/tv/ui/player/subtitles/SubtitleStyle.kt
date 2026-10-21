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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the edge of the subtitle is drawn.
 */
public enum class SubtitleEdge {
    /** No edge at all. Only readable over a flat background. */
    None,

    /** A soft drop shadow behind the letters. The style of the streaming services. */
    Shadow,

    /** A hard outline around the letters. The style of the fansubs. */
    Outline,
}

/**
 * What sits behind the letters.
 */
public enum class SubtitlePlate {
    /** Nothing. */
    None,

    /** A soft, translucent plate. */
    Soft,

    /** A solid, dark plate. */
    Solid,
}

/**
 * Where the subtitles sit on the screen.
 */
public enum class SubtitlePosition {
    /** At the bottom, above the controls. */
    Bottom,

    /** At the top. The style of the Japanese broadcast captions. */
    Top,

    /** In the middle of the frame. */
    Center,
}

/**
 * The presets of the subtitle style.
 *
 * Every one of them is a style that fansubs and the streaming services actually use, and
 * each of them changes more than the colour: the size, the edge, the plate and the
 * position all belong to the style.
 */
public enum class SubtitlePreset(
    public val label: String,
    public val position: SubtitlePosition,
    public val edge: SubtitleEdge,
    public val plate: SubtitlePlate,
    public val sizeSp: Int,
    public val textColor: Color = Color.White,
    public val plateColor: Color = Color.Black,
    public val plateAlpha: Float = 0.6f,
) {
    /** White letters with a soft shadow, at the bottom. What every streaming service does. */
    Default("Default", SubtitlePosition.Bottom, SubtitleEdge.Shadow, SubtitlePlate.Soft, 20),

    /**
     * The Japanese broadcast style: small letters with a hard outline, at the top of the
     * frame. It is the style of the fansubs that put the translation notes and the lyrics
     * out of the way of the picture.
     */
    Josei("Jōsei", SubtitlePosition.Top, SubtitleEdge.Outline, SubtitlePlate.None, 15),

    /** A solid plate behind the letters. The style of the subtitled fansub releases. */
    Plate("Plate", SubtitlePosition.Bottom, SubtitleEdge.None, SubtitlePlate.Solid, 20),

    /** Hard outlines, no shadow, no plate. The cleanest style. */
    Outline("Outline", SubtitlePosition.Bottom, SubtitleEdge.Outline, SubtitlePlate.None, 20),

    /** Large letters in the middle of the frame, the way a title card is written. */
    Sign("Sign", SubtitlePosition.Center, SubtitleEdge.Shadow, SubtitlePlate.None, 34),

    /** A coloured plate, the way the karaoke releases mark the line that is sung. */
    Karaoke("Karaoke", SubtitlePosition.Bottom, SubtitleEdge.Shadow, SubtitlePlate.Solid, 20, Color(0xFFFFD166)),
    ;
}

/**
 * The style of the subtitles.
 *
 * The preset is the starting point; the size and the position are then moved by the
 * user, in the steps a person can feel with a remote.
 */
public data class SubtitleStyle(
    val preset: SubtitlePreset = SubtitlePreset.Default,
    /** How much bigger or smaller the letters are, in steps. */
    val sizeStep: Int = 0,
    /** How much higher or lower the subtitles are, in steps. */
    val positionStep: Int = 0,
) {
    /** The colour of the letters. */
    public val textColor: Color get() = preset.textColor

    /** The size of the letters, in scale units. */
    public val textSize: Int
        get() = (preset.sizeSp + sizeStep * 2).coerceIn(12, 48)

    /** Where the subtitles sit. */
    public val position: SubtitlePosition get() = preset.position

    /** The edge of the letters. */
    public val edge: SubtitleEdge get() = preset.edge

    /** What sits behind the letters. */
    public val plate: SubtitlePlate get() = preset.plate

    /** The colour of the plate. */
    public val plateColor: Color get() = preset.plateColor

    /** How opaque the plate is. */
    public val plateAlpha: Float get() = preset.plateAlpha

    /**
     * How far the subtitles are from the edge of the frame.
     *
     * A positive value moves them towards the middle of the screen.
     */
    public val verticalOffset: Dp
        get() = (positionStep * 24).dp

    /** The style with the letters two steps bigger. */
    public fun larger(): SubtitleStyle =
        copy(sizeStep = (sizeStep + 1).coerceIn(-3, 3))

    /** The style with the letters two steps smaller. */
    public fun smaller(): SubtitleStyle =
        copy(sizeStep = (sizeStep - 1).coerceIn(-3, 3))

    /** The style one step higher. */
    public fun higher(): SubtitleStyle =
        copy(positionStep = (positionStep + 1).coerceIn(-3, 3))

    /** The style one step lower. */
    public fun lower(): SubtitleStyle =
        copy(positionStep = (positionStep - 1).coerceIn(-3, 3))
}
