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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.text.Cue
import androidx.tv.material3.MaterialTheme
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
 */
@Composable
public fun TvSubtitles(
    cues: List<Cue>,
    style: SubtitleStyle,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val text = cues.lastOrNull()?.text?.toString()?.takeIf { it.isNotBlank() } ?: return

    val alignment = when (style.position) {
        SubtitlePosition.Bottom -> Alignment.BottomCenter
        SubtitlePosition.Top -> Alignment.TopCenter
        SubtitlePosition.Center -> Alignment.Center
    }

    Box(
        modifier = modifier
            .padding(
                bottom = if (style.position == SubtitlePosition.Bottom) {
                    bottomPadding + style.verticalOffset
                } else {
                    0.dp
                },
                top = if (style.position == SubtitlePosition.Top) {
                    120.dp + style.verticalOffset
                } else {
                    0.dp
                },
            ),
        contentAlignment = alignment,
    ) {
        SubtitleText(
            text = text,
            style = style,
            modifier = Modifier
                .align(alignment)
                .offset(y = if (style.position == SubtitlePosition.Center) style.verticalOffset else 0.dp),
        )
    }
}

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
        fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
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
            // The outline is drawn by the same letters, four times, a hair off in each
            // direction. It is the way the fansubs do it, and it keeps the letters
            // readable over a bright frame without a plate.
            val outlineStyle = textStyle.copy(
                color = Color.Black,
                shadow = null,
            )
            listOf(
                androidx.compose.ui.geometry.Offset(-1.5f, 0f),
                androidx.compose.ui.geometry.Offset(1.5f, 0f),
                androidx.compose.ui.geometry.Offset(0f, -1.5f),
                androidx.compose.ui.geometry.Offset(0f, 1.5f),
            ).forEach { offset ->
                Text(
                    text = text,
                    style = outlineStyle,
                    modifier = Modifier.offset(
                        x = offset.x.dp,
                        y = offset.y.dp,
                    ),
                )
            }
        }

        Text(
            text = text,
            style = textStyle,
        )
    }
}
