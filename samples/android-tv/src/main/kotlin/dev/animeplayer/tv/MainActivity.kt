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
package dev.animeplayer.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.data.TvUpdates
import dev.animeplayer.tv.data.UpdateStatus
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.animeplayer.tv.ui.components.TvImageLoaderEffect
import dev.animeplayer.tv.ui.home.HomeScreensaverState
import dev.animeplayer.tv.ui.model.LocalTmdbCatalog
import dev.animeplayer.tv.ui.navigation.TvApp
import dev.animeplayer.tv.ui.theme.AnimeTvTheme
import dev.animeplayer.tv.ui.theme.LocalTvAnimationsEnabled
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.rememberTvLayout

/**
 * The single activity of the application.
 */
public class MainActivity : ComponentActivity() {
    private val screensaver = HomeScreensaverState()

    override fun onUserInteraction() {
        super.onUserInteraction()
        screensaver.recordInteraction()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        screensaver.interceptKey(event) || super.dispatchKeyEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A television has no system bars — the video and the rails own the screen.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val container = (application as AnimeTvApp).container

        setContent {
            val settings by container.settings.settings.collectAsState(initial = TvSettings())
            val layout = rememberTvLayout()
            val tmdb by container.tmdbChanges.collectAsState()
            LaunchedEffect(container) {
                container.settings.settings.map { it.autoCheckUpdates }.distinctUntilChanged().collectLatest { enabled ->
                    if (enabled) {
                        container.updates.check(automatic = true)
                        val candidate = container.updates.status.value as? UpdateStatus.Available
                        if (candidate?.automatic == true) container.updates.download(candidate.release, automatic = true)
                    }
                }
            }

            val constrained = container.isMemoryConstrained
            TvImageLoaderEffect(constrained)

            CompositionLocalProvider(
                LocalTvLayout provides layout,
                LocalTvAnimationsEnabled provides !constrained,
                LocalTmdbCatalog provides tmdb,
            ) {
                AnimeTvTheme(theme = settings.theme, accent = settings.accent) {
                    key(tmdb) {
                        TvApp(container = container, screensaver = screensaver, homeCardSize = settings.homeCardSize)
                    }
                    if (settings.autoCheckUpdates) UpdateAnnouncement(container.updates)
                }
            }
        }
    }
}


@Composable
private fun UpdateAnnouncement(updates: TvUpdates) {
    val status by updates.status.collectAsState()
    var dismissed by remember { mutableStateOf<String?>(null) }
    val ready = (status as? UpdateStatus.Ready)?.takeIf { it.automatic } ?: return
    val context = LocalContext.current
    if (dismissed == ready.release.version) return
    var installMessage by remember(ready.release.version) { mutableStateOf<String?>(null) }
    val close = { dismissed = ready.release.version }

    TvSheet(
        title = "Animeplayer ${ready.release.version} is ready",
        onDismiss = close,
        actions = {
            TvButton("Later", prominent = false, onClick = close)
            TvButton("Install update", onClick = {
                installMessage = updates.install(context, ready)
                if (installMessage == null) close()
            })
        },
    ) {
        Text(
            installMessage ?: "The GitHub download is verified. Android will ask before installing it.",
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
