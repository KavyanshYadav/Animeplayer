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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.R

/** A branded launch screen shown while the home catalogue and first artwork are prepared. */
@Composable
internal fun TvStartupScreen(progress: Float) {
    val fill by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(420),
        label = "startup-logo-fill",
    )
    val accent = MaterialTheme.colorScheme.primary
    val logo = painterResource(R.drawable.ic_launcher_foreground)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Box(modifier = Modifier.size(132.dp)) {
                Image(
                    painter = logo,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(accent),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.17f },
                )
                Image(
                    painter = logo,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(accent),
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val revealTop = size.height * (1f - fill)
                            clipRect(
                                left = 0f,
                                top = revealTop,
                                right = size.width,
                                bottom = size.height,
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        },
                )
            }
            Text(
                text = "animeplayer",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
