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
package dev.animeplayer.core.spi

/**
 * A single HTTP response.
 *
 * @property body the raw response payload.
 * @property url the final URL of the response, after redirects.
 */
public class HttpResponse(
    public val status: Int,
    public val headers: Map<String, String>,
    public val body: ByteArray,
    public val url: String,
) {
    /** `true` for 2xx status codes. */
    public val isSuccessful: Boolean get() = status in 200..299

    /** The response payload decoded as UTF-8. */
    public fun bodyAsText(): String = body.decodeToString()

    /** Case insensitive header lookup. */
    public fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    override fun toString(): String = "HTTP $status $url (${body.size} bytes)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpResponse) return false
        return status == other.status &&
            headers == other.headers &&
            url == other.url &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + headers.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}
