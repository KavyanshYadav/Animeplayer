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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.text.Cue
import androidx.tv.material3.Text

/**
 * The subtitles of the episode.
 *
 * Media3 hands the cues of the subtitle track to the player, but it does not draw them:
 * the Compose player draws the video and nothing else. Both reference implementations
 * attach a subtitle *view* — which lands above everything the application draws, because
 * a child view is painted after the composition.
 *
 * The subtitles belong between the picture and the controls, so they are drawn here, from
 * the same cues, in the typeface of the application and in the style the user picked.
 *
 * @param cues the cues of the current moment.
 * @param style the style of the subtitles.
 * @param bottomPadding how far the subtitles sit above the bottom of the screen. The
 *   controls need that room.
 * @param topPadding safe space for top-aligned captions.
 */
@Composable
public fun TvSubtitles(
    cues: List<Cue>,
    style: SubtitleStyle,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    topPadding: Dp = 120.dp,
) {
    val text = cues.lastOrNull()?.text?.toString()?.takeIf { it.isNotBlank() } ?: return

    SubtitleOverlay(text, style, topPadding, bottomPadding, modifier)
}

@Composable
internal fun SubtitleOverlay(
    text: String,
    style: SubtitleStyle,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    offsetScale: Float = 1f,
) {
    val placement = style.placement(topPadding, bottomPadding, offsetScale)
    Box(
        modifier = modifier.padding(top = placement.top, bottom = placement.bottom),
        contentAlignment = placement.alignment,
    ) {
        SubtitleText(
            text = text,
            style = style,
            modifier = Modifier.offset(y = placement.centerOffset),
        )
    }
}

internal data class SubtitlePlacement(
    val alignment: Alignment,
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val centerOffset: Dp = 0.dp,
)

internal fun SubtitleStyle.placement(
    topPadding: Dp,
    bottomPadding: Dp,
    offsetScale: Float = 1f,
): SubtitlePlacement {
    val offset = verticalOffset * offsetScale
    return when (position) {
        SubtitlePosition.Bottom -> SubtitlePlacement(
            alignment = Alignment.BottomCenter,
            bottom = subtitleEdgeInset(bottomPadding, offset),
        )
        SubtitlePosition.Top -> SubtitlePlacement(
            alignment = Alignment.TopCenter,
            top = subtitleEdgeInset(topPadding, offset),
        )
        SubtitlePosition.Center -> SubtitlePlacement(
            alignment = Alignment.Center,
            centerOffset = -offset,
        )
    }
}

// A lowered caption can move into the overscan margin but cannot produce negative padding.
private fun subtitleEdgeInset(safeArea: Dp, adjustment: Dp): Dp =
    (safeArea + adjustment).coerceAtLeast(12.dp)

/**
 * The letters of the subtitles, drawn in the style of the user.
 *
 * The panel of the styles draws the same thing, so the preview is the truth and not a
 * picture of it.
 */
@Composable
public fun SubtitleText(
    text: String,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val textStyle = TextStyle(
        fontFamily = style.font.family,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        fontSize = style.textSize.sp,
        lineHeight = (style.textSize * 1.25).sp,
        textAlign = TextAlign.Center,
        color = style.textColor,
        shadow = if (style.edge == SubtitleEdge.Shadow) {
            Shadow(color = Color.Black.copy(alpha = 0.85f), blurRadius = 8f, offset = androidx.compose.ui.geometry.Offset(2f, 2f))
        } else {
            null
        },
    )

    Box(
        modifier = modifier
            .background(
                color = when (style.plate) {
                    SubtitlePlate.None -> Color.Transparent
                    SubtitlePlate.Soft -> style.plateColor.copy(alpha = style.plateAlpha * 0.7f)
                    SubtitlePlate.Solid -> style.plateColor.copy(alpha = 0.85f)
                },
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (style.edge == SubtitleEdge.Outline) {
            val outlineWidth = with(LocalDensity.current) { 3.dp.toPx() }
            Text(
                text = text,
                style = textStyle.copy(
                    color = Color.Black,
                    shadow = null,
                    drawStyle = Stroke(width = outlineWidth),
                ),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }

        Text(
            text = text,
            style = textStyle,
        )
    }
}
