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
package dev.animeplayer.sources.hianime.internal

/**
 * A tiny, dependency free HTML fragment reader.
 *
 * The upstream pages are huge, hand written documents whose interesting parts are simple
 * tags with attributes. Pulling in a full HTML parser for that would add a dependency
 * without buying anything, so this reader scans for the tags it needs and parses their
 * attributes. It deliberately handles attribute values that contain `>` and tags whose
 * attributes span several lines — both of which occur upstream.
 */
internal object HtmlFragment {

    private val ATTRIBUTE = Regex(
        """([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
    )

    /**
     * A tag found in a document.
     *
     * @param attributes the attributes of the opening tag, lower cased names.
     * @param content the raw markup between the opening and the closing tag.
     */
    internal data class Element(
        val attributes: Map<String, String>,
        val content: String,
    ) {
        /** The `class` attribute split into its tokens. */
        val classes: Set<String>
            get() = attributes["class"]
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()

        /** The value of [name], or `null` when the attribute is absent. */
        fun attribute(name: String): String? = attributes[name]
    }

    /**
     * Every occurrence of `<tagName ...>` in [html].
     *
     * @param classFilter when non-null, only tags whose `class` attribute contains this
     *   (space separated) token are returned.
     * @param requireAttributes when not empty, only tags that carry all of these
     *   attributes are returned. Attribute driven matching is more robust than class
     *   driven matching, because the upstream markup does not always carry the expected
     *   CSS class on every element.
     */
    fun elements(
        html: String,
        tagName: String,
        classFilter: String? = null,
        requireAttributes: Set<String> = emptySet(),
    ): List<Element> {
        val results = mutableListOf<Element>()
        val openTag = "<$tagName"
        var index = 0

        while (index < html.length) {
            val start = html.indexOf(openTag, index)
            if (start < 0) break

            var cursor = start + openTag.length
            if (cursor < html.length) {
                val next = html[cursor]
                if (!next.isWhitespace() && next != '>' && next != '/') {
                    index = cursor
                    continue
                }
            }

            val attributes = readAttributes(html, cursor)
            cursor = attributes.second
            index = cursor + 1

            val element = Element(attributes.first, content = "")
            if (classFilter != null && !element.classes.contains(classFilter)) continue
            if (requireAttributes.any { !attributes.first.containsKey(it) }) continue

            val contentStart = cursor + 1
            val closingTag = "</$tagName"
            val closingIndex = html.indexOf(closingTag, contentStart)
            val content = if (closingIndex < 0) {
                html.substring(contentStart)
            } else {
                html.substring(contentStart, closingIndex)
            }

            results += Element(attributes.first, content)
        }

        return results
    }

    /** The first element matching [classFilter], or `null`. */
    fun firstElement(html: String, tagName: String, classFilter: String): Element? =
        elements(html, tagName, classFilter).firstOrNull()

    /**
     * The text content of the first element with [className], with nested tags stripped.
     */
    fun textOfClass(html: String, tagName: String, className: String): String? {
        val element = firstElement(html, tagName, className) ?: return null
        return element.content
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /** The value of the first attribute named [name] in [html]. */
    fun attributeOf(html: String, name: String): String? {
        val pattern = Regex("""\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)')""")
        val match = pattern.find(html) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    private fun readAttributes(html: String, start: Int): Pair<Map<String, String>, Int> {
        val builder = StringBuilder()
        var cursor = start
        var quote: Char? = null

        while (cursor < html.length) {
            val char = html[cursor]
            if (quote != null) {
                builder.append(char)
                if (char == quote) quote = null
            } else {
                when (char) {
                    '"', '\'' -> {
                        quote = char
                        builder.append(char)
                    }

                    '>' -> return parseAttributes(builder.toString()) to cursor
                    else -> builder.append(char)
                }
            }
            cursor++
        }

        return parseAttributes(builder.toString()) to cursor
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        ATTRIBUTE.findAll(raw).forEach { match ->
            val name = match.groupValues[1].lowercase()
            val value = match.groupValues[2]
                .ifEmpty { match.groupValues[3] }
                .ifEmpty { match.groupValues[4] }
            attributes[name] = value
        }
        return attributes
    }
}
