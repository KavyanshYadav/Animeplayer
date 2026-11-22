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
package dev.animeplayer.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.model.EpisodeRow
import dev.animeplayer.tv.ui.theme.LocalTvLayout

internal val LocalHideEpisodeSpoilers = staticCompositionLocalOf { true }

@Composable
internal fun TvEpisodeRow(
    episode: EpisodeRow,
    playable: Boolean,
    resume: Boolean,
    modifier: Modifier,
    thumbnailWidth: Dp = (LocalTvLayout.current.contentWidth * 0.30f).coerceIn(96.dp, 288.dp),
    nowPlaying: Boolean = false,
    onClick: () -> Unit,
) {
    var revealed by rememberSaveable(episode.number) { mutableStateOf(false) }
    val canReveal = LocalHideEpisodeSpoilers.current && !episode.isWatched &&
        (episode.hasSpoilerDetails())
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpisodeCard(
            episode = episode.withSpoilersHidden(canReveal && !revealed),
            playable = playable,
            resume = resume,
            modifier = Modifier.weight(1f).then(modifier),
            thumbnailWidth = thumbnailWidth,
            nowPlaying = nowPlaying,
            onClick = onClick,
        )
        if (canReveal) {
            TvButton(
                if (revealed) "Hide details" else "Reveal",
                modifier = Modifier.padding(end = 12.dp),
                prominent = false,
                onClick = { revealed = !revealed },
            )
        }
    }
}

internal fun EpisodeRow.hasSpoilerDetails(): Boolean =
    !title.equals("Episode $number", ignoreCase = true) || description != null || thumbnail != null || score != null

internal fun EpisodeRow.withSpoilersHidden(hide: Boolean): EpisodeRow =
    if (hide) copy(title = "Episode $number", description = null, thumbnail = null, score = null) else this

@Composable
private fun EpisodeCard(
    episode: EpisodeRow,
    playable: Boolean,
    resume: Boolean,
    modifier: Modifier,
    thumbnailWidth: Dp,
    nowPlaying: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val number = "Episode ${episode.number}"
    val status = when {
        nowPlaying -> "Now playing"
        episode.isWatched -> "Watched"
        resume -> "Resume"
        episode.watched > 0f -> "In progress"
        else -> null
    }
    val details = listOfNotNull(
        episode.duration?.takeIf { it >= 60 }?.let { "${it / 60} min" },
        status,
        episode.airDateLine,
    ).joinToString(" · ")

    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = playable,
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1f),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = if (nowPlaying) scheme.surfaceVariant else scheme.surface,
            contentColor = scheme.onSurface,
            focusedContainerColor = scheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(thumbnailWidth).aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)).background(scheme.surfaceVariant),
            ) {
                if (episode.thumbnail.isNullOrBlank()) {
                    TvIcon(Icons.AutoMirrored.Filled.List, modifier = Modifier.align(Alignment.Center),
                        size = 30.dp, tint = scheme.onSurfaceVariant)
                } else {
                    TvArtwork(episode.thumbnail, tint = null, contentDescription = null, modifier = Modifier.matchParentSize())
                }
                if (episode.watched > 0f && !episode.isWatched) {
                    TvProgressBar(episode.watched, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val hasTitle = !episode.title.equals(number, ignoreCase = true)
                if (hasTitle) {
                    Text(number, style = MaterialTheme.typography.titleSmall, color = scheme.primary)
                }
                Text(
                    text = if (hasTitle) episode.title else number,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (details.isNotBlank()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                episode.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (playable) {
                TvIcon(
                    if (episode.isWatched && !nowPlaying) Icons.Filled.Check else Icons.Filled.PlayArrow,
                    size = 28.dp,
                    tint = scheme.onSurface,
                )
            }
        }
    }
}
