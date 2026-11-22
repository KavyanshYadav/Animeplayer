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
package dev.animeplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceSettingsTest {
    @Test
    fun tmdbCredentialsUseTheCorrectAuthenticationMethod() {
        val key = "a".repeat(32)
        val query = requireNotNull(TmdbCredential.parse(" $key ")).request("search/tv?query=test")
        assertEquals("https://api.themoviedb.org/3/search/tv?query=test&api_key=$key", query.url)
        assertFalse("Authorization" in query.headers)
        val bearer = requireNotNull(TmdbCredential.parse("example.test.token")).request("configuration")
        assertEquals("https://api.themoviedb.org/3/configuration", bearer.url)
        assertEquals("Bearer example.test.token", bearer.headers["Authorization"])
        assertNull(TmdbCredential.parse("credential?extra=true"))
    }

    @Test
    fun updaterSelectsNewestVerifiedTvApkAndIgnoresDesktopAndPreReleases() {
        val body = listOf(
            """{"tag_name":"v99.0","assets":[]}""",
            release("1.9"),
            release("1.10"),
            release("3.0", prerelease = true),
            release("4.0", origin = "https://example.com"),
            release("5.0", digest = "bad"),
        ).joinToString(prefix = "[", postfix = "]")
        val update = requireNotNull(findTvRelease(body, "1.8"))
        assertEquals("1.10", update.version)
        assertEquals("a".repeat(64), update.sha256)
        assertNull(findTvRelease(body, "1.10.0"))
    }

    private fun release(
        version: String,
        prerelease: Boolean = false,
        origin: String = "https://github.com",
        digest: String = "sha256:" + "a".repeat(64),
    ): String {
        val name = "animeplayer-tv-v$version.apk"
        val url = "$origin/KavyanshYadav/Animeplayer/releases/download/android-tv-v$version/$name"
        return """{"tag_name":"android-tv-v$version","prerelease":$prerelease,
            "assets":[{"name":"$name","browser_download_url":"$url","size":1024,"digest":"$digest"}]}"""
    }
}
