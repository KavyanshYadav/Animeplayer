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

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

/** The `fetch` based engine — the only option available in a browser. */
public actual fun defaultHttpClientEngineFactory(): HttpClientEngineFactory<*> = Js
