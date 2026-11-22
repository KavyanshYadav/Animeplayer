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
package dev.animeplayer.tv.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.components.TvSheet

@Composable
internal fun SeriesInformation(media: CatalogMedia, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var failed by remember { mutableStateOf(false) }
    val links = listOfNotNull(
        media.id.toIntOrNull()?.let { "AniList" to "https://anilist.co/anime/$it" },
        media.malId?.let { "MyAnimeList" to "https://myanimelist.net/anime/$it" },
    )
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            failed = true
        }
    }

    val fields = listOf(
        "Studios" to media.studios,
        "Tags" to media.tags,
        "Alternative titles" to (media.titles + media.alternativeTitles).distinct(),
    ).filter { it.second.isNotEmpty() }
    TvSheet(title = "Series information", onDismiss = onClose, actions = {
        TvButton("Close", onClick = onClose)
    }) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            fields.forEach { (title, names) ->
                item(title) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    Text(names.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                }
            }
            links.forEach { (name, url) ->
                item(name) {
                    TvListItem("Open $name", subtitle = url, detailColor = scheme.onSurface,
                        onClick = { open(url) })
                }
            }
            if (failed) item("link-error") {
                Text("This TV has no browser for that link. Open the address on your phone.", color = scheme.error)
            }
        }
    }
}
