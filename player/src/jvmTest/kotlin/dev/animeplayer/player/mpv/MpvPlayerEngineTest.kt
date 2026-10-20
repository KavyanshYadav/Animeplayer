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
package dev.animeplayer.player.mpv

import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.StreamVariant
import dev.animeplayer.core.model.VideoQuality
import dev.animeplayer.core.spi.PlaybackRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Drives a real `mpv` over its JSON IPC socket.
 *
 * The test uses mpv's own `lavfi` source, so it needs no network and no media file.
 * It is skipped when mpv is not installed.
 */
class MpvPlayerEngineTest {

    @Test
    fun drivesMpvOverItsIpcSocket() = runBlocking {
        val engine = MpvPlayerEngine(
            extraArguments = listOf("--vo=null", "--ao=null", "--length=30"),
        )
        assumeTrue("mpv is not installed", engine.isAvailable)

        val source = PlayableSource(
            variant = StreamVariant(
                quality = VideoQuality(320, 240, "test"),
                url = "av://lavfi:testsrc=duration=30:s=320x240",
            ),
            referer = "https://example.com/",
        )

        val session = engine.prepare(PlaybackRequest(source, "animeplayer test"))

        try {
            val started = withTimeout(30_000) {
                session.state.first { it.positionMs > 0 }
            }
            assertTrue(started.positionMs > 0, "the position never advanced")

            session.seekTo(10_000)

            val seeked = withTimeout(30_000) {
                session.state.first { it.positionMs >= 9_000 }
            }
            assertTrue(seeked.positionMs >= 9_000, "the seek did not take effect")
        } finally {
            session.close()
        }
    }

    @Test
    fun reportsWhetherMpvIsAvailable() {
        // The engine must not throw when mpv is missing.
        assertTrue(MpvPlayerEngine(mpvExecutable = "definitely-not-mpv").isAvailable.not())
        assertTrue(MpvPlayerEngine().isAvailable)
    }
}
