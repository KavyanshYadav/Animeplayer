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
package dev.animeplayer.tv.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.animeplayer.tv.data.AnimeTheme
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvSheet
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.player.media.VideoSurface

internal fun openTrailer(context: Context, id: String): Boolean {
    if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return false
    for (uri in listOf("vnd.youtube:$id", "https://www.youtube.com/watch?v=$id")) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            return true
        } catch (_: ActivityNotFoundException) {
            continue
        }
    }
    return false
}

@Composable
internal fun ThemesPicker(themes: List<AnimeTheme>, onPlay: (AnimeTheme) -> Unit, onClose: () -> Unit) {
    TvSheet(title = "Opening and ending themes", onDismiss = onClose) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(themes, key = { it.url }) { theme ->
                TvListItem(theme.title ?: theme.label, subtitle = theme.label, onClick = { onPlay(theme) })
            }
        }
    }
}

@Composable
internal fun ThemePlayback(theme: AnimeTheme, onClose: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val player = remember(theme.url) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        player.setMediaItem(MediaItem.fromUri(theme.url))
        player.prepare()
        player.playWhenReady = true
        onDispose { player.release() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { withFrameNanos { }; focus.requestTvFocus() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(player)
            TvButton("Close theme", onClick = onClose, modifier = Modifier.align(Alignment.TopStart)
                .padding(22.dp).focusRequester(focus))
        }
    }
}
