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
package dev.animeplayer.sources.hianime.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The envelope the upstream JSON endpoints wrap their HTML payload in.
 */
@Serializable
internal data class HtmlEnvelope(
    @SerialName("status") val status: Boolean = false,
    @SerialName("totalItems") val totalItems: Int? = null,
    @SerialName("html") val html: String = "",
)

/**
 * The player configuration, decoded from the obfuscated `window.__P` payload.
 */
@Serializable
internal data class EmbedConfig(
    @SerialName("src") val src: String? = null,
    @SerialName("subtitles") val subtitles: List<EmbedSubtitle> = emptyList(),
    @SerialName("poster") val poster: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("video_id") val videoId: Int? = null,
    @SerialName("skip") val skip: EmbedSkip? = null,
    @SerialName("default_audio") val defaultAudio: String? = null,
)

@Serializable
internal data class EmbedSubtitle(
    @SerialName("lang") val lang: String? = null,
    @SerialName("label") val label: String? = null,
    @SerialName("src") val src: String? = null,
    @SerialName("default") val isDefault: Boolean = false,
)

@Serializable
internal data class EmbedSkip(
    @SerialName("intro") val intro: EmbedRange? = null,
    @SerialName("outro") val outro: EmbedRange? = null,
)

@Serializable
internal data class EmbedRange(
    @SerialName("start") val start: Int = 0,
    @SerialName("end") val end: Int = 0,
)
