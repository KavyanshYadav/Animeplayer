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

/**
 * A dependency free Base64 codec.
 *
 * The Kotlin standard library ships `kotlin.io.encoding.Base64`, but it is still marked as
 * experimental and its API has changed between releases. Upstream payloads embed Base64 in
 * JSON and HTML, so the codec is a hard requirement of the library — owning it keeps the
 * public surface stable and avoids opt-in annotations in consumer code.
 *
 * Decoding accepts both the standard (`+/`) and the URL safe (`-_`) alphabet and silently
 * ignores whitespace, which mirrors what the `base64` command line tool does.
 */
public object Base64 {

    private const val STANDARD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL_SAFE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val DECODE_TABLE = IntArray(128) { -1 }.also { table ->
        STANDARD_ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
        URL_SAFE_ALPHABET.forEachIndexed { index, char -> if (table[char.code] < 0) table[char.code] = index }
    }

    /**
     * Decodes [input] into the raw bytes it represents.
     *
     * @throws IllegalArgumentException when the input contains a character that is neither
     *   part of the alphabet nor whitespace.
     */
    public fun decode(input: String): ByteArray {
        val output = ByteArray((input.length * 3) / 4 + 3)
        var outIndex = 0
        var buffer = 0
        var bits = 0

        for (char in input) {
            if (char == '=') break
            if (char.isWhitespace()) continue
            val code = char.code
            val value = if (code < 128) DECODE_TABLE[code] else -1
            require(value >= 0) { "Invalid Base64 character '$char'" }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output[outIndex++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }

        return output.copyOf(outIndex)
    }

    /** Encodes [input] using the standard alphabet and `=` padding. */
    public fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        val builder = StringBuilder((input.size + 2) / 3 * 4)
        var index = 0

        while (index + 2 < input.size) {
            val chunk = ((input[index].toInt() and 0xFF) shl 16) or
                ((input[index + 1].toInt() and 0xFF) shl 8) or
                (input[index + 2].toInt() and 0xFF)
            builder.append(STANDARD_ALPHABET[(chunk shr 18) and 0x3F])
            builder.append(STANDARD_ALPHABET[(chunk shr 12) and 0x3F])
            builder.append(STANDARD_ALPHABET[(chunk shr 6) and 0x3F])
            builder.append(STANDARD_ALPHABET[chunk and 0x3F])
            index += 3
        }

        when (input.size - index) {
            1 -> {
                val chunk = (input[index].toInt() and 0xFF) shl 16
                builder.append(STANDARD_ALPHABET[(chunk shr 18) and 0x3F])
                builder.append(STANDARD_ALPHABET[(chunk shr 12) and 0x3F])
                builder.append("==")
            }

            2 -> {
                val chunk = ((input[index].toInt() and 0xFF) shl 16) or
                    ((input[index + 1].toInt() and 0xFF) shl 8)
                builder.append(STANDARD_ALPHABET[(chunk shr 18) and 0x3F])
                builder.append(STANDARD_ALPHABET[(chunk shr 12) and 0x3F])
                builder.append(STANDARD_ALPHABET[(chunk shr 6) and 0x3F])
                builder.append('=')
            }
        }

        return builder.toString()
    }
}
