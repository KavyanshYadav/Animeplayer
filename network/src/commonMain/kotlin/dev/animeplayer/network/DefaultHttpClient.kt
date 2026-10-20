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

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * The Ktor engine that backs [createDefaultHttpClient] on the current platform.
 *
 * The engine is chosen so that the library behaves natively everywhere:
 *  * JVM / desktop and servers use the CIO engine,
 *  * Android uses the OkHttp engine,
 *  * the browser uses the `fetch` based engine.
 */
public expect fun defaultHttpClientEngineFactory(): HttpClientEngineFactory<*>

/**
 * Creates a [HttpClient] with the engine that fits the current platform.
 *
 * @param block additional client configuration.
 */
public fun createDefaultHttpClient(block: io.ktor.client.HttpClientConfig<*>.() -> Unit = {}): HttpClient =
    HttpClient(defaultHttpClientEngineFactory()) {
        block()
    }
