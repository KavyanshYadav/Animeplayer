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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The HTTP verbs the library needs. */
public enum class HttpMethod {
    Get,
    Post,
    Head,
    ;
}

/**
 * A single HTTP request.
 *
 * The transport abstraction deliberately stays minimal so that any HTTP client can back it:
 * OkHttp on Android, Ktor or `java.net.http` on the JVM, `fetch` in the browser, or a
 * hand-written test double.
 */
public class HttpRequest(
    public val url: String,
    public val method: HttpMethod = HttpMethod.Get,
    public val headers: Map<String, String> = emptyMap(),
    public val body: ByteArray? = null,
    public val timeout: Duration = DEFAULT_TIMEOUT,
) {
    /** A copy of this request with the given changes applied. */
    public fun toBuilder(): Builder = Builder()
        .url(url)
        .method(method)
        .headers(headers)
        .body(body)
        .timeout(timeout)

    override fun toString(): String = "$method $url"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpRequest) return false
        return url == other.url &&
            method == other.method &&
            headers == other.headers &&
            timeout == other.timeout &&
            (body?.contentEquals(other.body) ?: (other.body == null))
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + timeout.hashCode()
        return result
    }

    /** Mutable builder for [HttpRequest]. */
    public class Builder internal constructor() {
        private var url: String = ""
        private var method: HttpMethod = HttpMethod.Get
        private var headers: Map<String, String> = emptyMap()
        private var body: ByteArray? = null
        private var timeout: Duration = DEFAULT_TIMEOUT

        public fun url(value: String): Builder = apply { url = value }

        public fun method(value: HttpMethod): Builder = apply { method = value }

        public fun headers(value: Map<String, String>): Builder = apply { headers = value }

        public fun header(name: String, value: String): Builder =
            apply { headers = headers + (name to value) }

        public fun body(value: ByteArray?): Builder = apply { body = value }

        public fun timeout(value: Duration): Builder = apply { timeout = value }

        public fun build(): HttpRequest = HttpRequest(url, method, headers, body, timeout)
    }

    public companion object {
        /** The timeout used when a request does not specify one. */
        public val DEFAULT_TIMEOUT: Duration = 10.seconds
    }
}
