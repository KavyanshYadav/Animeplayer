@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

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
package dev.animeplayer.tv.ui.player.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

/**
 * The video of the player.
 *
 * The surface is a **texture view**, not a surface view. A surface view is drawn in its
 * own layer, above the window, and everything the application draws — the controls, the
 * subtitles — would disappear behind the video. A texture view is composited by the
 * system *under* the window, so the interface stays on top of the picture.
 *
 * The picture keeps its own proportions: the surface is measured against the size of the
 * video, so a film that is wider than the screen is not stretched.
 */
@Composable
public fun VideoSurface(
    player: Player,
    modifier: Modifier = Modifier,
) {
    val presentationState = rememberPresentationState(player)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerSurface(
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .resizeWithContentScale(
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    sourceSizeDp = presentationState.videoSizeDp,
                ),
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        )

        // The surface is black until the first frame arrives; a black plate over it keeps
        // the picture from flashing.
        if (presentationState.coverSurface) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }
}
