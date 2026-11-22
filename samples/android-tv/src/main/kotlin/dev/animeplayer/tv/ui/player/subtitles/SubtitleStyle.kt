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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.animeplayer.tv.R

/**
 * The typeface of the subtitles.
 *
 * The Japanese television captions have their own tradition, and the fansubs follow it:
 *
 *  * **丸ゴシック** — the rounded gothic. The shape of the captions of a Japanese
 *    broadcast, and the face most fansubs use. *Zen Maru Gothic* and *M PLUS Rounded 1c*
 *    are the open equivalents.
 *  * **ゴシック** — the plain gothic. The workhorse of the subtitles: it is small, it is
 *    dense, and it stays out of the way of the picture. *Zen Kaku Gothic New* is the open
 *    equivalent.
 *  * **隷書** — the clerical script, the brush-like face the Japanese television uses for
 *    the dramatic moments. There is no open equivalent, so a heavy display gothic
 *    stands in for it. *BIZ UDPGothic* is the one.
 */
public enum class SubtitleFont(
    public val label: String,
    public val regular: Font,
    public val bold: Font,
    public val heavy: Font,
) {
    /** The rounded gothic of the Japanese broadcast captions. */
    Maru(
        label = "Maru gothic",
        regular = Font(R.font.zen_maru_gothic_400, FontWeight.Normal),
        bold = Font(R.font.zen_maru_gothic_700, FontWeight.Bold),
        heavy = Font(R.font.zen_maru_gothic_900, FontWeight.Black),
    ),

    /** The plain gothic — the workhorse of the subtitles. */
    Gothic(
        label = "Gothic",
        regular = Font(R.font.zen_kaku_gothic_new_400, FontWeight.Normal),
        bold = Font(R.font.zen_kaku_gothic_new_700, FontWeight.Bold),
        heavy = Font(R.font.zen_kaku_gothic_new_900, FontWeight.Black),
    ),

    /** The heavy display face, the one the Japanese television uses for the drama. */
    Display(
        label = "Display",
        regular = Font(R.font.biz_udp_gothic_400, FontWeight.Normal),
        bold = Font(R.font.biz_udp_gothic_700, FontWeight.Bold),
        heavy = Font(R.font.biz_udp_gothic_700, FontWeight.Black),
    ),

    /** The rounded sans of the Japanese web. */
    Rounded(
        label = "Rounded",
        regular = Font(R.font.mplus_rounded_1c_400, FontWeight.Normal),
        bold = Font(R.font.mplus_rounded_1c_700, FontWeight.Bold),
        heavy = Font(R.font.mplus_rounded_1c_700, FontWeight.Black),
    ),
    ;

    /** The family of the face. */
    public val family: FontFamily = FontFamily(regular, bold, heavy)
}

/**
 * How the edge of the subtitle is drawn.
 */
public enum class SubtitleEdge(public val label: String) {
    /** No edge at all. Only readable over a flat background. */
    None("None"),

    /** A soft drop shadow behind the letters. The style of the streaming services. */
    Shadow("Shadow"),

    /** A hard outline around the letters. The style of the fansubs. */
    Outline("Outline"),
}

/**
 * What sits behind the letters.
 */
public enum class SubtitlePlate(public val label: String) {
    /** Nothing. */
    None("Off"),

    /** A soft, translucent plate. */
    Soft("Translucent"),

    /** A solid, dark plate. */
    Solid("Solid"),
}

/**
 * Where the subtitles sit on the screen.
 */
public enum class SubtitlePosition(public val label: String) {
    /** At the bottom, above the controls. */
    Bottom("Bottom"),

    /** At the top. The style of the Japanese broadcast captions. */
    Top("Top"),

    /** In the middle of the frame. */
    Center("Center"),
}

public enum class SubtitleColor(public val label: String, public val color: Color) {
    White("White", Color.White),
    Black("Black", Color.Black),
    Gold("Gold", Color(0xFFFFD166)),
    Cyan("Cyan", Color(0xFF83E8FF)),
    Navy("Navy", Color(0xFF131E3A)),
}

public enum class SubtitleWeight(public val label: String, internal val fontWeight: FontWeight) {
    Regular("Regular", FontWeight.Normal),
    Bold("Bold", FontWeight.Bold),
    Heavy("Heavy", FontWeight.Black),
}

public enum class SubtitleEdgeStrength(public val label: String, internal val factor: Float) {
    Light("Light", 0.65f),
    Medium("Medium", 1f),
    Strong("Strong", 1.45f),
}

/** Subtitle looks inspired by anime releases and streaming caption options. */
public enum class SubtitlePreset(
    public val label: String,
    public val font: SubtitleFont,
    public val position: SubtitlePosition,
    public val edge: SubtitleEdge,
    public val plate: SubtitlePlate,
    public val sizeSp: Int,
    public val textColor: Color = Color.White,
    public val plateColor: Color = Color.Black,
    public val plateAlpha: Float = 0.6f,
) {
    /** White letters with a soft shadow, at the bottom. What every streaming service does. */
    Default(
        label = "Default",
        font = SubtitleFont.Gothic,
        position = SubtitlePosition.Bottom,
        edge = SubtitleEdge.Shadow,
        plate = SubtitlePlate.Soft,
        sizeSp = 20,
    ),

    /**
     * The Japanese broadcast style: small rounded letters with a hard outline, at the top
     * of the frame. It is the style of the fansubs that put the translation notes and the
     * lyrics out of the way of the picture.
     */
    Josei(
        label = "Jōsei",
        font = SubtitleFont.Maru,
        position = SubtitlePosition.Top,
        edge = SubtitleEdge.Outline,
        plate = SubtitlePlate.None,
        sizeSp = 15,
    ),

    /** A solid plate behind the letters. The style of the subtitled fansub releases. */
    Plate(
        label = "Plate",
        font = SubtitleFont.Gothic,
        position = SubtitlePosition.Bottom,
        edge = SubtitleEdge.None,
        plate = SubtitlePlate.Solid,
        sizeSp = 20,
    ),

    /** Hard outlines, no shadow, no plate. The cleanest style. */
    Outline(
        label = "Outline",
        font = SubtitleFont.Gothic,
        position = SubtitlePosition.Bottom,
        edge = SubtitleEdge.Outline,
        plate = SubtitlePlate.None,
        sizeSp = 20,
    ),

    /** Large letters in the middle of the frame, the way a title card is written. */
    Sign(
        label = "Sign",
        font = SubtitleFont.Display,
        position = SubtitlePosition.Center,
        edge = SubtitleEdge.Shadow,
        plate = SubtitlePlate.None,
        sizeSp = 34,
    ),

    /** A coloured plate, the way the karaoke releases mark the line that is sung. */
    Karaoke(
        label = "Karaoke",
        font = SubtitleFont.Rounded,
        position = SubtitlePosition.Bottom,
        edge = SubtitleEdge.Shadow,
        plate = SubtitlePlate.Solid,
        sizeSp = 20,
        textColor = Color(0xFFFFD166),
    ),
    AnimeOutline(
        label = "Anime outline",
        font = SubtitleFont.Maru,
        position = SubtitlePosition.Bottom,
        edge = SubtitleEdge.Outline,
        plate = SubtitlePlate.None,
        sizeSp = 22,
    ),
    Cinema(
        label = "Cinema shadow",
        font = SubtitleFont.Gothic,
        position = SubtitlePosition.Bottom,
        edge = SubtitleEdge.Shadow,
        plate = SubtitlePlate.None,
        sizeSp = 22,
    ),
    ClosedCaptions(
        label = "Closed captions",
        font = SubtitleFont.Gothic,
        position = SubtitlePosition.Bottom,
        edge = SubtitleEdge.None,
        plate = SubtitlePlate.Solid,
        sizeSp = 22,
    ),
    HighContrast(
        label = "High contrast",
        font = SubtitleFont.Maru,
        position = SubtitlePosition.Bottom,
        edge = SubtitleEdge.None,
        plate = SubtitlePlate.Solid,
        sizeSp = 26,
        textColor = Color(0xFFFFD166),
    ),
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
    /** The typeface, when the user picked one of their own. */
    val fontOverride: SubtitleFont? = null,
    val sizeOverrideSp: Int? = null,
    val weight: SubtitleWeight = SubtitleWeight.Bold,
    val positionOverride: SubtitlePosition? = null,
    val edgeOverride: SubtitleEdge? = null,
    val edgeStrength: SubtitleEdgeStrength = SubtitleEdgeStrength.Medium,
    val textColorOverride: SubtitleColor? = null,
    val textOpacityPercent: Int = 100,
    val plateOverride: SubtitlePlate? = null,
    val plateColorOverride: SubtitleColor? = null,
    val plateOpacityPercent: Int? = null,
) {
    /** The typeface of the letters. */
    public val font: SubtitleFont get() = fontOverride ?: preset.font

    /** The colour of the letters. */
    public val textColor: Color
        get() = (textColorOverride?.color ?: preset.textColor).copy(alpha = textOpacityPercent.coerceIn(0, 100) / 100f)

    /** The size of the letters, in scale units. */
    public val textSize: Int
        get() = (sizeOverrideSp ?: (preset.sizeSp + sizeStep.coerceIn(-3, 3) * 2)).coerceIn(12, 48)

    /** Where the subtitles sit. */
    public val position: SubtitlePosition get() = positionOverride ?: preset.position

    /** The edge of the letters. */
    public val edge: SubtitleEdge get() = edgeOverride ?: preset.edge

    /** What sits behind the letters. */
    public val plate: SubtitlePlate get() = plateOverride ?: preset.plate

    /** The colour of the plate. */
    public val plateColor: Color get() = plateColorOverride?.color ?: preset.plateColor

    /** How opaque the plate is. */
    public val plateAlpha: Float
        get() = when (plate) {
            SubtitlePlate.None -> 0f
            SubtitlePlate.Soft -> plateOpacityPercent?.coerceIn(0, 100)?.div(100f) ?: preset.plateAlpha * 0.7f
            SubtitlePlate.Solid -> plateOpacityPercent?.coerceIn(0, 100)?.div(100f) ?: 0.85f
        }

    /**
     * How far the subtitles are from the edge of the frame.
     *
     * For edge-aligned captions a positive value moves them away from that edge.
     */
    public val verticalOffset: Dp
        get() = (positionStep.coerceIn(-3, 3) * 24).dp

    /** The style with the letters two steps bigger. */
    public fun larger(): SubtitleStyle =
        copy(sizeOverrideSp = (textSize + 2).coerceAtMost(48))

    /** The style with the letters two steps smaller. */
    public fun smaller(): SubtitleStyle =
        copy(sizeOverrideSp = (textSize - 2).coerceAtLeast(12))

    /** The style one step higher. */
    public fun higher(): SubtitleStyle = moveVertically(1)

    /** The style one step lower. */
    public fun lower(): SubtitleStyle = moveVertically(-1)

    private fun moveVertically(direction: Int): SubtitleStyle {
        val step = if (position == SubtitlePosition.Top) -direction else direction
        return copy(positionStep = (positionStep.coerceIn(-3, 3) + step).coerceIn(-3, 3))
    }
}
