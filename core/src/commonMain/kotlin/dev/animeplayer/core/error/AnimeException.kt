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
package dev.animeplayer.core.error

import dev.animeplayer.core.model.AnimeId

/**
 * Every failure the library raises.
 *
 * Failures are modelled as a sealed hierarchy so that callers can react to them
 * programmatically — for example by retrying a [NetworkError] but giving up on a
 * [ParseError], which usually means the upstream site changed.
 */
public sealed class AnimeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The request never reached the server, or the connection broke. */
    public class NetworkError(
        public val url: String,
        cause: Throwable,
    ) : AnimeException("Network failure while requesting $url", cause)

    /** The server answered, but with an unexpected status code. */
    public class HttpStatusError(
        public val url: String,
        public val status: Int,
        public val body: String? = null,
    ) : AnimeException("HTTP $status while requesting $url") {
        /** `true` for 404 responses. */
        public val isNotFound: Boolean get() = status == 404

        /** `true` for 429 responses. */
        public val isRateLimited: Boolean get() = status == 429
    }

    /**
     * The request was answered with a Cloudflare interstitial.
     *
     * Cloudflare fingerprints the TLS client, so a plain HTTP client can be challenged even
     * with a correct user agent. Retrying with a different transport usually resolves it.
     */
    public class CloudflareChallengeError(
        public val url: String,
    ) : AnimeException("Blocked by a Cloudflare challenge while requesting $url")

    /** The response could not be understood — most likely the upstream markup changed. */
    public class ParseError(
        public val reason: String,
        public val snippet: String? = null,
    ) : AnimeException(
        buildString {
            append("Failed to parse ").append(reason)
            if (snippet != null) append(". Unexpected content: ").append(snippet.take(240))
        },
    )

    /** The requested resource does not exist. */
    public class NotFoundError(
        public val what: String,
    ) : AnimeException("Not found: $what")

    /** The episode exists but no stream could be resolved for it. */
    public class NoSourcesError(
        public val animeId: AnimeId,
        public val episodeNumber: String,
    ) : AnimeException("No playable sources for ${animeId.slug} episode $episodeNumber")

    /** The episode has not been released yet. */
    public class EpisodeNotReleasedError(
        public val animeId: AnimeId,
        public val episodeNumber: String,
    ) : AnimeException("Episode $episodeNumber of ${animeId.slug} is not released yet")

    /** The requested quality is not offered for this episode. */
    public class QualityNotAvailableError(
        public val requested: String,
        public val available: List<String>,
    ) : AnimeException("Quality '$requested' is not available. Available: ${available.joinToString()}")
}
