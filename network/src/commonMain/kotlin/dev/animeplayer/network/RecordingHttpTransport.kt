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
package dev.animeplayer.network

import dev.animeplayer.core.error.AnimeException
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpResponse
import dev.animeplayer.core.spi.HttpTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [HttpTransport] that answers from a fixed set of responses and records every request.
 *
 * It lives in the main source set on purpose: consumers need it to test their own code
 * against the library without touching the network.
 *
 * @param responses the canned responses, matched by URL substring or by a full matcher.
 */
public class RecordingHttpTransport(
    private val responses: List<Rule> = emptyList(),
) : HttpTransport {

    /** Every request that was executed, in order. */
    public val recordedRequests: MutableList<HttpRequest> = mutableListOf()

    private val mutex = Mutex()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        mutex.withLock { recordedRequests += request }

        val rule = responses.firstOrNull { it.matches(request) }
            ?: throw AnimeException.NetworkError(
                request.url,
                IllegalStateException("No canned response registered for ${request.url}"),
            )

        rule.failuresRemaining--
        if (rule.failuresRemaining > 0) {
            throw AnimeException.NetworkError(
                request.url,
                rule.failure ?: IllegalStateException("Simulated failure"),
            )
        }

        return HttpResponse(
            status = rule.status,
            headers = rule.headers,
            body = rule.body.encodeToByteArray(),
            url = request.url,
        )
    }

    /** A single canned response. */
    public class Rule(
        public val status: Int = 200,
        public val body: String = "",
        public val headers: Map<String, String> = emptyMap(),
        /** How often the rule fails before it starts answering successfully. */
        public var failuresRemaining: Int = 0,
        /** The exception thrown while [failuresRemaining] is positive. */
        public val failure: Throwable? = null,
        private val urlMatcher: (HttpRequest) -> Boolean,
    ) {
        internal fun matches(request: HttpRequest): Boolean = urlMatcher(request)
    }

    public companion object {
        /** A rule that matches every request whose URL contains [urlPart]. */
        public fun urlContains(
            urlPart: String,
            status: Int = 200,
            body: String = "",
            headers: Map<String, String> = emptyMap(),
            failuresRemaining: Int = 0,
            failure: Throwable? = null,
        ): Rule = Rule(status, body, headers, failuresRemaining, failure) { request ->
            request.url.contains(urlPart)
        }

        /** A rule that matches requests for exactly [url]. */
        public fun url(
            url: String,
            status: Int = 200,
            body: String = "",
            headers: Map<String, String> = emptyMap(),
            failuresRemaining: Int = 0,
            failure: Throwable? = null,
        ): Rule = Rule(status, body, headers, failuresRemaining, failure) { request ->
            request.url == url
        }
    }
}
