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
package dev.animeplayer.tv.ui.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.ui.R as MediaR
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.player.TvPlayerState
import dev.animeplayer.tv.ui.player.focus.DirectionalFocus
import dev.animeplayer.tv.ui.theme.TvDimens

@Composable
internal fun BottomControls(
    state: TvPlayerState,
    focus: DirectionalFocus,
    onSeekTo: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onNext: () -> Unit,
    onEpisodes: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onQuality: () -> Unit,
    onSpeed: () -> Unit,
    onSkipIntro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedLabel by remember { mutableStateOf("") }
    val navigation = Modifier.focusProperties { up = focus.scrubber }
    val playLabel = if (state.isPlaying) "Pause" else "Play"

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = TvDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(21.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = focusedLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PlayerTime(state.positionMs, state.durationMs)
        }

        TvScrubber(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            bufferedMs = state.bufferedMs,
            focus = focus,
            onSeek = onSeekTo,
            onFocused = { focusedLabel = "Seek  ·  Left or right to skip 10 seconds" },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(
                icon = if (state.isPlaying) MediaR.drawable.exo_icon_pause else MediaR.drawable.exo_icon_play,
                label = playLabel,
                onClick = onTogglePlayback,
                onFocused = { focusedLabel = it },
                modifier = Modifier.focusRequester(focus.transport).then(navigation),
            )
            PlayerIconButton(MediaR.drawable.exo_ic_rewind, "Back 10 seconds", { onSeekBy(-10_000) },
                { focusedLabel = it }, navigation)
            PlayerIconButton(MediaR.drawable.exo_ic_forward, "Forward 10 seconds", { onSeekBy(10_000) },
                { focusedLabel = it }, navigation)
            if (state.nextEpisode != null) {
                PlayerIconButton(MediaR.drawable.exo_ic_skip_next, "Next episode", onNext,
                    { focusedLabel = it }, navigation)
            }
            if (state.canSkipIntro) {
                PlayerIconButton(MediaR.drawable.exo_ic_chevron_right, "Skip intro", onSkipIntro,
                    { focusedLabel = it }, navigation)
            }
            Spacer(Modifier.weight(1f))
            PlayerIconButton(dev.animeplayer.tv.R.drawable.ic_episode_list, "Episodes", onEpisodes,
                { focusedLabel = it }, navigation)
            PlayerIconButton(MediaR.drawable.exo_ic_audiotrack, "Audio", onAudio,
                { focusedLabel = it }, navigation)
            val captions = if (state.activeSubtitle == null) {
                MediaR.drawable.exo_ic_subtitle_off
            } else {
                MediaR.drawable.exo_ic_subtitle_on
            }
            PlayerIconButton(captions, "Subtitles", onSubtitles, { focusedLabel = it }, navigation)
            PlayerIconButton(MediaR.drawable.exo_ic_settings, "Video quality", onQuality,
                { focusedLabel = it }, navigation)
            PlayerIconButton(MediaR.drawable.exo_ic_speed, "Playback speed", onSpeed,
                { focusedLabel = it }, navigation)
        }
    }
}
