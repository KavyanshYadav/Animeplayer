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
 * Resolves relative URLs against a base URL.
 *
 * HLS master playlists routinely reference their variants with relative URIs
 * (`1080/index.m3u8`), and variant playlists reference their segments with absolute URIs
 * pointing at a different host. Both have to be resolved correctly, which is what this
 * RFC 3986 style implementation does.
 */
public object UrlResolver {

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:")

    /**
     * Resolves [reference] against [base].
     *
     * @return the absolute URL, or [reference] unchanged when it cannot be resolved.
     */
    public fun resolve(base: String, reference: String): String {
        if (reference.isEmpty()) return base
        if (SCHEME_REGEX.containsMatchIn(reference)) return reference

        val baseParts = split(base)
        if (baseParts.scheme == null) return reference

        if (reference.startsWith("//")) {
            return "${baseParts.scheme}:$reference"
        }

        val refParts = split(reference)

        val path = when {
            refParts.path.isEmpty() -> baseParts.path
            refParts.path.startsWith("/") -> removeDotSegments(refParts.path)
            else -> removeDotSegments(mergePaths(baseParts.path, refParts.path))
        }

        val query = if (reference.contains('?')) refParts.query else baseParts.query
        val fragment = refParts.fragment

        return buildString {
            append(baseParts.scheme)
            append("://")
            append(baseParts.authority)
            append(path)
            if (query != null) {
                append('?')
                append(query)
            }
            if (fragment != null) {
                append('#')
                append(fragment)
            }
        }
    }

    /**
     * The origin of [url], for example `https://zokoanime.video/`.
     *
     * This is the value upstream CDNs expect in the `Referer` header.
     */
    public fun origin(url: String): String {
        val parts = split(url)
        val scheme = parts.scheme ?: return url
        return "$scheme://${parts.authority}/"
    }

    /** The host of [url], without the port. */
    public fun host(url: String): String? = split(url).authority?.substringBefore(':')

    /** The path of [url], without query and fragment. */
    public fun path(url: String): String = split(url).path

    private fun mergePaths(basePath: String, referencePath: String): String {
        val baseDirectory = if (basePath.contains('/')) {
            basePath.substringBeforeLast('/')
        } else {
            ""
        }
        return if (baseDirectory.endsWith('/')) baseDirectory + referencePath else "$baseDirectory/$referencePath"
    }

    private fun removeDotSegments(path: String): String {
        if (!path.contains("./")) return path
        val output = ArrayDeque<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "." -> Unit
                ".." -> if (output.isNotEmpty()) output.removeLast()
                else -> output.addLast(segment)
            }
        }
        val joined = output.joinToString("/")
        return when {
            path.startsWith("/") && !joined.startsWith("/") -> "/$joined"
            path.endsWith("/") && !joined.endsWith("/") -> "$joined/"
            else -> joined
        }
    }

    private class Parts(
        val scheme: String?,
        val authority: String?,
        val path: String,
        val query: String?,
        val fragment: String?,
    )

    private fun split(url: String): Parts {
        var rest = url

        val fragmentIndex = rest.indexOf('#')
        val fragment = if (fragmentIndex >= 0) rest.substring(fragmentIndex + 1) else null
        if (fragmentIndex >= 0) rest = rest.substring(0, fragmentIndex)

        val queryIndex = rest.indexOf('?')
        val query = if (queryIndex >= 0) rest.substring(queryIndex + 1) else null
        if (queryIndex >= 0) rest = rest.substring(0, queryIndex)

        val schemeSeparator = rest.indexOf("://")
        val scheme: String?
        var authority: String?
        if (schemeSeparator >= 0) {
            scheme = rest.substring(0, schemeSeparator)
            rest = rest.substring(schemeSeparator + 3)
            val pathStart = rest.indexOf('/')
            if (pathStart >= 0) {
                authority = rest.substring(0, pathStart)
                rest = rest.substring(pathStart)
            } else {
                authority = rest
                rest = ""
            }
        } else {
            scheme = null
            authority = null
        }

        return Parts(scheme, authority, rest, query, fragment)
    }
}
