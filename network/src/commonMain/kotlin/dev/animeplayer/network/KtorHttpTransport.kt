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
import dev.animeplayer.core.spi.HttpMethod
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpResponse
import dev.animeplayer.core.spi.HttpTransport
import dev.animeplayer.core.util.UrlResolver
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.client.plugins.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * The default [HttpTransport], backed by Ktor.
 *
 * On top of the plain request/response cycle it adds everything a production client
 * needs: caching, retries with exponential backoff, per-host rate limiting, a circuit
 * breaker, Cloudflare challenge detection, logging and metrics.
 *
 * @param client the Ktor client to use. Build one with [createDefaultHttpClient] or supply
 *   your own engine.
 * @param config the tuning knobs.
 */
public class KtorHttpTransport(
    private val client: HttpClient,
    private val config: TransportConfig = TransportConfig(),
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val requestTimeout = if (request.timeout > ZERO) request.timeout else config.defaultTimeout
        val host = UrlResolver.host(request.url) ?: request.url
        val cacheKey = if (request.method == HttpMethod.Get) cacheKey(request) else null

        if (cacheKey != null && config.cache != null) {
            val cached = config.cache.get(cacheKey)
            if (cached != null) {
                config.metrics.recordCacheHit(cacheKey)
                config.logger.debug("Cache hit for $host")
                return decodeCached(cached, request.url)
            }
            config.metrics.recordCacheMiss(cacheKey)
        }

        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < config.maxAttempts.coerceAtLeast(1)) {
            attempt++
            val startedAt = config.clock.now()

            try {
                val response = config.circuitBreaker.execute(host) {
                    performRequest(request, requestTimeout)
                }
                val durationMs = (config.clock.now() - startedAt).inWholeMilliseconds

                if (CloudflareChallengeDetector.isChallenge(response.status, response.bodyAsText())) {
                    config.metrics.recordRequest(host, request.url, response.status, durationMs, "cloudflare")
                    config.logger.warn("Blocked by a Cloudflare challenge: $host")
                    throw AnimeException.CloudflareChallengeError(request.url)
                }

                config.metrics.recordRequest(host, request.url, response.status, durationMs)

                if (response.status in config.retryableStatusCodes && attempt < config.maxAttempts) {
                    config.logger.warn(
                        "HTTP ${response.status} from $host, retrying (attempt $attempt of ${config.maxAttempts})",
                    )
                    delayBeforeRetry(attempt)
                    continue
                }

                if (response.isSuccessful && cacheKey != null && config.cache != null) {
                    config.cache.put(cacheKey, encodeCached(response), config.cacheTtl)
                }

                return response
            } catch (error: CancellationException) {
                throw error
            } catch (error: AnimeException.CloudflareChallengeError) {
                throw error
            } catch (error: Throwable) {
                lastError = error
                config.metrics.recordRequest(host, request.url, -1, 0, error::class.simpleName)
                if (attempt >= config.maxAttempts) break
                config.logger.warn(
                    "Request to $host failed (${error::class.simpleName}: ${error.message}), retrying",
                    error,
                )
                delayBeforeRetry(attempt)
            }
        }

        throw AnimeException.NetworkError(request.url, lastError ?: IllegalStateException("Request failed"))
    }

    private suspend fun performRequest(request: HttpRequest, timeoutDuration: Duration): HttpResponse {
        config.rateLimiter.acquire(UrlResolver.host(request.url) ?: request.url)

        return try {
            val builder: HttpRequestBuilder.() -> Unit = {
                method = when (request.method) {
                    HttpMethod.Get -> KtorHttpMethod.Get
                    HttpMethod.Post -> KtorHttpMethod.Post
                    HttpMethod.Head -> KtorHttpMethod.Head
                }
                request.headers.forEach { (name, value) -> headers.append(name, value) }
                if (request.headers.keys.none { it.equals(HttpHeaders.UserAgent, ignoreCase = true) }) {
                    headers.append(HttpHeaders.UserAgent, config.userAgent)
                }
                timeout {
                    requestTimeoutMillis = timeoutDuration.inWholeMilliseconds
                }
                request.body?.let { body -> setBody(body) }
            }

            val ktorResponse = when (request.method) {
                HttpMethod.Get -> client.get(request.url, builder)
                HttpMethod.Post -> client.post(request.url, builder)
                HttpMethod.Head -> client.head(request.url, builder)
            }

            HttpResponse(
                status = ktorResponse.status.value,
                headers = ktorResponse.headers.entries()
                    .associate { entry -> entry.key to entry.value.joinToString(", ") },
                body = ktorResponse.bodyAsBytes(),
                url = request.url,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw AnimeException.NetworkError(request.url, error)
        }
    }

    private suspend fun delayBeforeRetry(attempt: Int) {
        val wait = Backoff.delayFor(
            attempt = attempt - 1,
            initial = config.initialBackoff,
            multiplier = config.backoffMultiplier,
            max = config.maxBackoff,
            jitter = config.backoffJitter,
        )
        if (wait > ZERO) delay(wait)
    }

    private fun cacheKey(request: HttpRequest): String = buildString {
        append(request.method.name)
        append('|')
        append(request.url)
        request.headers.entries
            .filter { (name) -> name.equals("Referer", ignoreCase = true) || name.equals("Accept", ignoreCase = true) }
            .sortedBy { it.key }
            .forEach { (name, value) ->
                append('|')
                append(name.lowercase())
                append('=')
                append(value)
            }
    }

    private fun encodeCached(response: HttpResponse): ByteArray {
        val statusBytes = response.status.toString().encodeToByteArray()
        return statusBytes + byteArrayOf(0) + response.body
    }

    private fun decodeCached(bytes: ByteArray, url: String): HttpResponse {
        val separator = bytes.indexOf(0)
        if (separator < 0) {
            return HttpResponse(200, emptyMap(), bytes, url)
        }
        val status = bytes.decodeToString(0, separator).toIntOrNull() ?: 200
        val body = bytes.copyOfRange(separator + 1, bytes.size)
        return HttpResponse(status, mapOf("X-Animeplayer-Cache" to "HIT"), body, url)
    }
}
