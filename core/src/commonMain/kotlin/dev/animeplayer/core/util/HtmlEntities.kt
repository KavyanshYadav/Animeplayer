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
 * Decodes the HTML entities that upstream sites use inside attribute values.
 *
 * Titles are frequently stored as `Tom &amp; Jerry` or `It&#039;s My Hero`, and the raw
 * entity has to be removed before the title is shown or compared.
 */
public object HtmlEntities {

    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "hellip" to "…",
        "mdash" to "—",
        "ndash" to "–",
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
        "middot" to "·",
        "bull" to "•",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "deg" to "°",
        "eacute" to "é",
        "egrave" to "è",
        "agrave" to "à",
        "uuml" to "ü",
        "ouml" to "ö",
        "auml" to "ä",
        "szlig" to "ß",
    )

    /**
     * Replaces every HTML entity in [input] with the character it represents.
     *
     * Unknown entities are left untouched.
     */
    public fun decode(input: String): String {
        if ('&' !in input) return input

        val builder = StringBuilder(input.length)
        var index = 0

        while (index < input.length) {
            val char = input[index]
            if (char != '&') {
                builder.append(char)
                index++
                continue
            }

            val semicolon = input.indexOf(';', index + 1)
            if (semicolon < 0 || semicolon - index > MAX_ENTITY_LENGTH) {
                builder.append(char)
                index++
                continue
            }

            val entity = input.substring(index + 1, semicolon)
            val replacement = resolve(entity)
            if (replacement == null) {
                builder.append(char)
                index++
            } else {
                builder.append(replacement)
                index = semicolon + 1
            }
        }

        return builder.toString()
    }

    private fun resolve(entity: String): String? {
        if (entity.isEmpty()) return null
        if (entity[0] == '#') {
            val code = when {
                entity.length > 1 && (entity[1] == 'x' || entity[1] == 'X') ->
                    entity.substring(2).toIntOrNull(16)

                else -> entity.substring(1).toIntOrNull()
            } ?: return null
            if (code < 0 || code > 0x10FFFF) return null
            return code.toChar().toString()
        }
        return NAMED[entity.lowercase()]
    }

    private const val MAX_ENTITY_LENGTH = 10
}
