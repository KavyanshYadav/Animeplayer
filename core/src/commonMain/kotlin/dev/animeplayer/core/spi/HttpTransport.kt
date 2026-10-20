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
 * The network boundary of the library.
 *
 * Implementations are expected to
 *  * honour [HttpRequest.timeout],
 *  * send [HttpRequest.headers] verbatim, and
 *  * throw [dev.animeplayer.core.error.AnimeException.NetworkError] when the request could not
 *    be completed at all (DNS failure, connection reset, timeout).
 *
 * Any HTTP status code — including 4xx and 5xx — is a *successful* round trip and must be
 * returned as an [HttpResponse] so that callers can decide what to do with it.
 */
public interface HttpTransport {
    public suspend fun execute(request: HttpRequest): HttpResponse
}
