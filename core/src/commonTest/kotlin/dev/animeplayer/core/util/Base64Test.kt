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
package dev.animeplayer.core.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Base64Test {

    @Test
    fun decodesTheUpstreamServerHash() {
        // data-hash attribute of the ZokoAnime server entry.
        val encoded = "aHR0cHM6Ly96b2tvYW5pbWUudmlkZW8vc3RyZWFtL21hbC8yMS8xL3N1Yg=="
        assertEquals(
            "https://zokoanime.video/stream/mal/21/1/sub",
            Base64.decode(encoded).decodeToString(),
        )
    }

    @Test
    fun roundTripsBinaryData() {
        val original = ByteArray(256) { it.toByte() }
        assertContentEquals(original, Base64.decode(Base64.encode(original)))
    }

    @Test
    fun encodesKnownVectors() {
        assertEquals("", Base64.encode(ByteArray(0)))
        assertEquals("Zg==", Base64.encode("f".encodeToByteArray()))
        assertEquals("Zm8=", Base64.encode("fo".encodeToByteArray()))
        assertEquals("Zm9v", Base64.encode("foo".encodeToByteArray()))
        assertEquals("Zm9vYg==", Base64.encode("foob".encodeToByteArray()))
        assertEquals("Zm9vYmE=", Base64.encode("fooba".encodeToByteArray()))
        assertEquals("Zm9vYmFy", Base64.encode("foobar".encodeToByteArray()))
    }

    @Test
    fun ignoresWhitespace() {
        assertEquals("foobar", Base64.decode("Zm9v\nYmFy").decodeToString())
        assertEquals("foobar", Base64.decode("Zm9v YmFy").decodeToString())
        assertEquals("foobar", Base64.decode("  Zm9vYmFy  ").decodeToString())
    }

    @Test
    fun acceptsUrlSafeAlphabet() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xEF.toByte(), 0xFF.toByte())
        val standard = Base64.encode(bytes)
        assertEquals("++//", standard)
        assertContentEquals(bytes, Base64.decode(standard.replace('+', '-').replace('/', '_')))
    }

    @Test
    fun rejectsInvalidCharacters() {
        assertFailsWith<IllegalArgumentException> { Base64.decode("Zm9v*mFy") }
    }
}
