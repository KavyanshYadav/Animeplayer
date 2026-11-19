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
import androidx.compose.runtime.CompositionLocalProvider
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

            val constrained = container.isMemoryConstrained
            TvImageLoaderEffect(constrained)

            CompositionLocalProvider(
                LocalTvLayout provides layout,
                LocalTvAnimationsEnabled provides !constrained,
                LocalTmdbCatalog provides container.tmdb,
            ) {
                AnimeTvTheme(theme = settings.theme, accent = settings.accent) {
                    TvApp(container = container, screensaver = screensaver)
                }
            }
        }
    }
}
