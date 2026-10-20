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
package dev.animeplayer.sources.hianime.internal.parser

import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.util.Base64
import dev.animeplayer.sources.hianime.internal.HtmlFragment

/**
 * A single upstream server that can serve an episode.
 *
 * @property embedUrl the URL of the player page.
 */
internal data class ServerEntry(
    val type: AudioTrack,
    val name: String,
    val embedUrl: String,
) {
    /**
     * The MyAnimeList id, when the embed URL encodes one.
     *
     * The ZokoAnime embed URL has the shape `…/stream/mal/{malId}/{episodeId}/{track}`.
     */
    val malId: String?
        get() = MAL_ID.find(embedUrl)?.groupValues?.get(1)

    private companion object {
        val MAL_ID = Regex("""/mal/(\d+)(?:/|\?)""")
    }
}

/**
 * Extracts the available servers from the servers endpoint.
 *
 * The endpoint answers with a JSON envelope whose `html` field contains one `<div>` per
 * server:
 *
 * ```html
 * <div class="item server-item" data-type="sub"
 *      data-server-name="ZokoAnime"
 *      data-hash="aHR0cHM6Ly96b2tvYW5pbWUudmlkZW8vc3RyZWFtL21hbC8yMS8xL3N1Yg==">
 * ```
 *
 * The `data-hash` attribute is the Base64 encoded URL of the player page.
 */
internal object ServerListParser {

    fun parse(html: String): List<ServerEntry> {
        val servers = mutableListOf<ServerEntry>()

        HtmlFragment.elements(
            html = html,
            tagName = "div",
            requireAttributes = setOf("data-type", "data-hash"),
        ).forEach { element ->
            val type = when (element.attribute("data-type")?.trim()?.lowercase()) {
                "sub" -> AudioTrack.Sub
                "dub" -> AudioTrack.Dub
                else -> return@forEach
            }
            val hash = element.attribute("data-hash")?.trim().orEmpty()
            if (hash.isEmpty()) return@forEach

            val embedUrl = runCatching { Base64.decode(hash).decodeToString().trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: return@forEach

            servers += ServerEntry(
                type = type,
                name = element.attribute("data-server-name")?.trim().orEmpty(),
                embedUrl = embedUrl,
            )
        }

        return servers
    }
}
