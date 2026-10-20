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
package dev.animeplayer.proxy

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/** The JSON body of the resolve endpoint. */
@Serializable
public data class ResolveRequestDto(
    val slug: String,
    val episode: String,
    val audioTrack: String = "sub",
    val quality: String? = null,
)

/**
 * Registers the proxy endpoints on this [Route].
 *
 *  * `POST /api/resolve` resolves an episode and returns the proxied URLs.
 *  * `GET /hls/{token}` serves the playlist, the segments and the subtitles.
 */
public fun Route.animeProxyRoutes(service: AnimeProxyService) {
    route("/api") {
        post("/resolve") {
            val request = call.receive<ResolveRequestDto>()
            val response = service.resolve(
                ResolveRequest(
                    slug = request.slug,
                    episodeNumber = request.episode,
                    audioTrack = if (request.audioTrack.equals("dub", ignoreCase = true)) {
                        dev.animeplayer.core.model.AudioTrack.Dub
                    } else {
                        dev.animeplayer.core.model.AudioTrack.Sub
                    },
                    quality = request.quality,
                ),
            )
            call.respond(response)
        }
    }

    get("/hls/{token}") {
        val token = call.parameters["token"]
        if (token == null) {
            call.respond(HttpStatusCode.BadRequest, "missing token")
            return@get
        }

        val payload = service.handle(token)
        if (payload == null) {
            call.respond(HttpStatusCode.NotFound, "unknown or expired token")
            return@get
        }

        call.respondBytes(payload.content, ContentType.parse(payload.contentType))
    }
}
