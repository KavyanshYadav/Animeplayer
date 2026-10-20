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
package dev.animeplayer.sources.hianime.internal.codec

import dev.animeplayer.core.util.Base64

/**
 * Decodes the obfuscated player configuration.
 *
 * The player page embeds its configuration as
 *
 * ```
 * window.__P="<base64>"
 * ```
 *
 * where the decoded bytes are XORed with the repeating key `otaku-embed-v1`. The original
 * shell implementation did the same thing with `od`, `printf "%b"` and a rotating
 * positional parameter list; this is the direct equivalent.
 */
internal object EmbedDeobfuscator {

    private val KEY: ByteArray = "otaku-embed-v1".encodeToByteArray()

    /**
     * Decodes [encoded] into the raw player configuration.
     *
     * @throws IllegalArgumentException when the payload is not valid Base64.
     */
    fun decode(encoded: String): ByteArray {
        val obfuscated = Base64.decode(encoded)
        val plain = ByteArray(obfuscated.size)
        for (index in obfuscated.indices) {
            plain[index] = (obfuscated[index].toInt() xor KEY[index % KEY.size].toInt()).toByte()
        }
        return plain
    }

    /** Decodes [encoded] into the player configuration as UTF-8 text. */
    fun decodeToString(encoded: String): String = decode(encoded).decodeToString()
}
