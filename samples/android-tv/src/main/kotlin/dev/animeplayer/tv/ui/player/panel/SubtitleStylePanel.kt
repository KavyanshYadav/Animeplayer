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
package dev.animeplayer.tv.ui.player.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.player.subtitles.SubtitleFont
import dev.animeplayer.tv.ui.player.subtitles.SubtitlePreset
import dev.animeplayer.tv.ui.player.subtitles.SubtitleStyle
import dev.animeplayer.tv.ui.player.subtitles.SubtitleText

/**
 * The panel that picks the style of the subtitles.
 *
 * The style is shown before it is chosen: the preview at the top is drawn with the very
 * same renderer as the episode, so the user sees the letters grow and move — and change
 * their face — while they press the buttons. The reference implementations do the same,
 * and it is the only way to judge a style without starting the episode.
 *
 * @param style the style that is picked.
 * @param onStyleChange called with the new style.
 */
@Composable
public fun SubtitleStylePanel(
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The preview.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SubtitleText(
                text = "The words of the episode",
                style = style,
            )
        }

        // The list of the styles and of the typefaces.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PanelHeading("The style")
            }
            items(SubtitlePreset.entries.size) { index ->
                val preset = SubtitlePreset.entries[index]
                SubtitleStyleRow(
                    title = preset.label,
                    subtitle = when (preset) {
                        SubtitlePreset.Default -> "White letters with a soft shadow, at the bottom."
                        SubtitlePreset.Josei -> "Small rounded letters with a hard outline, at the top."
                        SubtitlePreset.Plate -> "White letters on a solid plate."
                        SubtitlePreset.Outline -> "Hard outlines, no shadow, no plate."
                        SubtitlePreset.Sign -> "Large letters in the middle of the frame."
                        SubtitlePreset.Karaoke -> "A coloured plate, the way the karaoke releases mark the line."
                    },
                    selected = style.preset == preset,
                    onClick = { onStyleChange(style.copy(preset = preset, fontOverride = null)) },
                    modifier = if (index == 0 && focus != null) {
                        Modifier.focusRequester(focus)
                    } else {
                        Modifier
                    },
                )
            }

            item {
                PanelHeading("The typeface")
            }
            items(SubtitleFont.entries.size) { index ->
                val font = SubtitleFont.entries[index]
                SubtitleFontRow(
                    font = font,
                    subtitle = when (font) {
                        SubtitleFont.Maru -> "The rounded gothic of the Japanese broadcast captions."
                        SubtitleFont.Gothic -> "The plain gothic — the workhorse of the subtitles."
                        SubtitleFont.Display -> "The heavy display face, the one the television uses for the drama."
                        SubtitleFont.Rounded -> "The rounded sans of the Japanese web."
                    },
                    selected = style.font == font,
                    onClick = { onStyleChange(style.copy(fontOverride = font)) },
                )
            }
        }

        // The size and the position, always in sight.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SubtitleStyleButton(
                text = "A −",
                onClick = { onStyleChange(style.smaller()) },
                modifier = Modifier.weight(1f),
            )
            SubtitleStyleButton(
                text = "A +",
                onClick = { onStyleChange(style.larger()) },
                modifier = Modifier.weight(1f),
            )
            SubtitleStyleButton(
                text = "↑",
                onClick = { onStyleChange(style.higher()) },
                modifier = Modifier.weight(1f),
            )
            SubtitleStyleButton(
                text = "↓",
                onClick = { onStyleChange(style.lower()) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The heading of a section of the panel.
 */
@Composable
private fun PanelHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One of the styles of the picker.
 */
@Composable
private fun SubtitleStyleRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.02f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = if (selected) {
            TvFocus.colors(
                containerColor = accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            TvFocus.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * One of the typefaces of the picker.
 *
 * The face is shown in itself, so the user reads it before picking it.
 */
@Composable
private fun SubtitleFontRow(
    font: SubtitleFont,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.02f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = if (selected) {
            TvFocus.colors(
                containerColor = accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            TvFocus.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = font.family),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = font.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * A button of the style panel.
 */
@Composable
private fun SubtitleStyleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.05f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
