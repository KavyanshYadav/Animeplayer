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

/**
 * Detects Cloudflare interstitials.
 *
 * Cloudflare fingerprints the TLS client, so even a request with a perfect user agent can
 * be answered with a "Just a moment..." page instead of the real content. When that
 * happens the request has to be repeated with a different transport (for example one that
 * impersonates a real browser), which is why it is worth telling the two situations apart.
 */
public object CloudflareChallengeDetector {

    private val MARKERS = listOf(
        "Just a moment",
        "Checking your browser before accessing",
        "cf-browser-verification",
        "challenge-platform",
        "Attention Required! | Cloudflare",
        "__cf_chl_",
    )

    /**
     * `true` when [body] is a Cloudflare challenge rather than the requested content.
     */
    public fun isChallenge(body: String): Boolean {
        if (body.isEmpty()) return false
        val probe = body.take(8192)
        return MARKERS.any { probe.contains(it, ignoreCase = true) }
    }

    /**
     * `true` when the response is a Cloudflare challenge.
     *
     * Cloudflare usually answers with `403` or `503`, but the body is the reliable signal.
     */
    public fun isChallenge(status: Int, body: String): Boolean =
        isChallenge(body) && status >= 400
}
