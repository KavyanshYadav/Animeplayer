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
 * The bridge between a catalogue and a stream source.
 *
 * A catalogue knows a series by its catalogue title, a stream source knows it by the slug
 * of the upstream site. Nothing connects the two — so the titles are compared.
 *
 * The comparison is a pure function, which is why it lives in the core module: every
 * client (desktop, web, TV) needs exactly the same behaviour.
 */
public object TitleMatch {

    /**
     * Reduces a title to the words that matter.
     *
     * Case, accents and punctuation are removed, so `Kusuriya no Hitorigoto` and
     * `薬屋のひとりごと` both end up as a single lowercase run of words.
     */
    public fun normalize(title: String): String {
        val normalized = title.lowercase().normalizeDecomposed()
        val builder = StringBuilder(normalized.length)
        var previousWasSeparator = false
        for (character in normalized) {
            if (character.isLetterOrDigit()) {
                builder.append(character)
                previousWasSeparator = false
            } else if (!previousWasSeparator) {
                builder.append(' ')
                previousWasSeparator = true
            }
        }
        return builder.toString().trim()
    }

    /**
     * How well two titles match.
     *
     * `1` is an exact match, `0` means the titles have nothing in common.
     */
    public fun score(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0

        if (a.startsWith(b) || b.startsWith(a)) return 0.85

        val tokensA = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val tokensB = b.split(' ').filter { it.isNotEmpty() }.toSet()
        val shared = tokensA.count { it in tokensB }
        val union = (tokensA + tokensB).size
        val overlap = if (union == 0) 0.0 else shared.toDouble() / union

        if (a.contains(b) || b.contains(a)) return maxOf(0.7, overlap)

        // "one piece film" against "one piece" — the shorter title wins.
        if (tokensA.isNotEmpty() && tokensB.isNotEmpty()) {
            val shorter = if (tokensA.size <= tokensB.size) tokensA else tokensB
            val longer = if (tokensA.size <= tokensB.size) tokensB else tokensA
            if (shorter.all { it in longer }) {
                return 0.6 * (shorter.size.toDouble() / longer.size) + 0.35
            }
        }

        return overlap * 0.5
    }

    /**
     * The best score of a list of candidate titles against [target].
     *
     * A catalogue entry usually has several titles — the romanised one, the english one and
     * the native one — and the stream source may use any of them.
     */
    public fun best(candidates: Iterable<String?>, target: String): Double {
        var best = 0.0
        for (candidate in candidates) {
            if (candidate.isNullOrBlank()) continue
            best = maxOf(best, score(candidate, target))
        }
        return best
    }

    /**
     * The best score of a list of candidate titles against every target.
     *
     * This is the form the clients use: a catalogue entry with three titles is compared
     * against a stream source result that also carries several titles.
     */
    public fun best(candidates: Iterable<String?>, targets: Iterable<String?>): Double {
        var best = 0.0
        for (candidate in candidates) {
            if (candidate.isNullOrBlank()) continue
            for (target in targets) {
                if (target.isNullOrBlank()) continue
                best = maxOf(best, score(candidate, target))
            }
        }
        return best
    }
}

/**
 * Removes the combining marks of a string, so `é` becomes `e`.
 *
 * Kotlin's `String.normalize` is not available in the common standard library, so the
 * Latin accents are folded by hand. The table covers the Latin-1 supplement and the
 * Latin extended-A block, which is where the titles of the catalogue live.
 */
private fun String.normalizeDecomposed(): String {
    val builder = StringBuilder(length)
    for (character in this) {
        val folded = fold(character)
        if (folded != null) {
            builder.append(folded)
        } else if (character.code !in 0x0300..0x036F) {
            builder.append(character)
        }
    }
    return builder.toString()
}

/** The base letter of a Latin accent, or `null` when the character is not an accent. */
private fun fold(character: Char): Char? = when (character) {
    'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> 'a'
    'À', 'Á', 'Â', 'Ã', 'Ä', 'Å', 'Ā', 'Ă', 'Ą' -> 'A'
    'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> 'e'
    'È', 'É', 'Ê', 'Ë', 'Ē', 'Ĕ', 'Ė', 'Ę', 'Ě' -> 'E'
    'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į', 'ı' -> 'i'
    'Ì', 'Í', 'Î', 'Ï', 'Ĩ', 'Ī', 'Ĭ', 'Į', 'İ' -> 'I'
    'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ŏ', 'ő' -> 'o'
    'Ò', 'Ó', 'Ô', 'Õ', 'Ö', 'Ø', 'Ō', 'Ŏ', 'Ő' -> 'O'
    'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų' -> 'u'
    'Ù', 'Ú', 'Û', 'Ü', 'Ũ', 'Ū', 'Ŭ', 'Ů', 'Ű', 'Ų' -> 'U'
    'ç', 'ć', 'č' -> 'c'
    'Ç', 'Ć', 'Č' -> 'C'
    'ñ', 'ń', 'ň' -> 'n'
    'Ñ', 'Ń', 'Ň' -> 'N'
    'ý', 'ÿ' -> 'y'
    'Ý', 'Ÿ' -> 'Y'
    'š', 'ś', 'ş' -> 's'
    'Š', 'Ś', 'Ş' -> 'S'
    'ž', 'ź', 'ż' -> 'z'
    'Ž', 'Ź', 'Ż' -> 'Z'
    else -> null
}
