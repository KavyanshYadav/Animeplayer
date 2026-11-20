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
package dev.animeplayer.tv.ui.home

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.seconds

internal enum class ScreensaverCommand { Previous, Next, Open }

internal class HomeScreensaverState {
    private val interactions = MutableStateFlow(0)
    private val remoteCommands = MutableSharedFlow<ScreensaverCommand>(extraBufferCapacity = 8)
    private val handledKeys = mutableSetOf<Int>()
    val commands = remoteCommands.asSharedFlow()

    var visible by mutableStateOf(false)
        private set

    suspend fun watchForInactivity() {
        try {
            interactions.collectLatest {
                delay(60.seconds)
                visible = true
            }
        } finally {
            visible = false
        }
    }

    fun recordInteraction() {
        interactions.update { it + 1 }
    }

    fun dismiss() {
        visible = false
        recordInteraction()
    }

    fun interceptKey(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return handledKeys.remove(key)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return key in handledKeys
        handledKeys.remove(key)
        if (!visible) return false

        handledKeys += key
        when (key) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                remoteCommands.tryEmit(ScreensaverCommand.Previous)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT ->
                remoteCommands.tryEmit(ScreensaverCommand.Next)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                remoteCommands.tryEmit(ScreensaverCommand.Open)
            else -> dismiss()
        }
        return true
    }
}

@Composable
internal fun HomeScreensaver(
    featured: List<CatalogCard>,
    controls: HomeScreensaverState,
    onDismiss: () -> Unit,
    onOpen: (CatalogCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slides = remember(featured) {
        featured.mapNotNull { card -> (card.bannerUrl ?: card.posterUrl)?.let { it to card } }.toMap()
    }
    val artwork = remember(slides) { slides.keys.toList() }
    if (artwork.isEmpty()) return
    var index by remember(artwork) { mutableIntStateOf(0) }
    var displayed by remember(artwork) { mutableStateOf<String?>(null) }
    val incoming = artwork[index]

    fun openDisplayed(): Boolean {
        val card = slides[displayed] ?: return false
        onOpen(card)
        return true
    }

    LaunchedEffect(controls, slides) {
        controls.commands.collect { command ->
            when (command) {
                ScreensaverCommand.Previous -> index = (index - 1 + artwork.size) % artwork.size
                ScreensaverCommand.Next -> index = (index + 1) % artwork.size
                ScreensaverCommand.Open -> openDisplayed()
            }
        }
    }

    LaunchedEffect(index, displayed) {
        // Leave the last loaded slide visible while the next one decodes.
        delay(if (incoming == displayed) 12.seconds else 5.seconds)
        index = (index + 1) % artwork.size
    }
    LaunchedEffect(artwork) {
        delay(10.seconds)
        if (displayed == null) onDismiss()
    }

    Box(
        modifier = modifier.fillMaxSize()
            .background(Color.Black)
            .pointerInput(slides, onOpen) { detectTapGestures(onTap = { openDisplayed() }) }
            .semantics {
                contentDescription = "Artwork screensaver"
                onClick(label = "Open series", action = ::openDisplayed)
            },
    ) {
        for (url in listOfNotNull(displayed, incoming).distinct()) {
            key(url) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { if (url == artwork[index]) displayed = url },
                )
            }
        }
        slides[displayed]?.let { card ->
            val compact = LocalTvLayout.current.height < 450.dp
            card.featuredLogo?.let { logo ->
                Image(
                    bitmap = logo,
                    contentDescription = card.title,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = TvDimens.ScreenMargin, bottom = 32.dp)
                        .width(if (compact) 162.dp else 226.dp)
                        .height(if (compact) 58.dp else 80.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                )
            }
        }
    }
}
