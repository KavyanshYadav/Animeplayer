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

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal enum class ListStatus(val label: String) {
    Watching("Watching"), Planned("Planned"), Completed("Completed"), Paused("Paused"), Dropped("Dropped");
}

@Serializable
internal data class AnimeListEntry(
    val id: String,
    val status: ListStatus,
    val progress: Int = 0,
    val score: Int = 0,
    val watchedOverrides: Map<String, Boolean> = emptyMap(),
    val manualProgress: Boolean = false,
) {
    fun watched(number: String): Boolean? {
        val episode = number.toIntOrNull()?.takeIf { it > 0 }
        return watchedOverrides[number] ?: when {
            episode != null && episode <= progress -> true
            manualProgress && episode != null -> false
            else -> null
        }
    }

    fun mergeProgress(number: String, playback: Float): Float =
        when (watched(number)) {
            true -> 1f
            false -> if (watchedOverrides[number] == false || playback >= 0.9f) 0f else playback
            null -> playback
        }

    fun mark(number: String, watched: Boolean): AnimeListEntry {
        val overrides = watchedOverrides.toMutableMap()
        val episode = number.toIntOrNull()?.takeIf { it > 0 }
        var count = progress
        if (watched && episode != null && episode <= count) {
            overrides.remove(number)
        } else {
            overrides[number] = watched
        }
        if (!watched && episode != null && episode <= count) {
            // Keep later completed episodes visible while moving contiguous progress back to the gap.
            for (later in episode + 1..count) overrides.putIfAbsent(later.toString(), true)
            count = episode - 1
        }
        while (count < MaxListProgress && overrides[(count + 1).toString()] == true) {
            overrides.remove((++count).toString())
        }
        return copy(progress = count, watchedOverrides = overrides)
    }

    fun setProgress(count: Int): AnimeListEntry =
        copy(progress = count.coerceIn(0, MaxListProgress), manualProgress = true, watchedOverrides = emptyMap())
}

internal const val MaxListProgress = 100_000
private val legacyKey = stringPreferencesKey("favourites")
private val entriesKey = stringPreferencesKey("anime-list")
private val listJson = Json { ignoreUnknownKeys = true }

internal fun readAnimeList(encoded: String?, legacy: String?): List<AnimeListEntry> =
    if (encoded != null) listJson.decodeFromString(encoded)
    else legacy.orEmpty().split(',').filter { it.toLongOrNull() != null }.distinct()
        .map { AnimeListEntry(it, ListStatus.Planned) }

/** The status, progress and score of the AniList titles saved on this TV. */
public class FavouritesRepository(context: Context) {
    private val store = context.favouritesStore
    internal val entries = store.data.catch { failure ->
        if (failure is IOException) emit(emptyPreferences()) else throw failure
    }.map { readAnimeList(it[entriesKey], it[legacyKey]) }

    public suspend fun ids(): List<String> = entries.first().map { it.id }

    public suspend fun isFavourite(id: String): Boolean = entry(id) != null

    internal suspend fun entry(id: String): AnimeListEntry? = entries.first().firstOrNull { it.id == id }

    public suspend fun toggle(id: String): Boolean =
        edit(id) { if (it == null) AnimeListEntry(id, ListStatus.Planned) else null } != null

    internal suspend fun edit(id: String, change: (AnimeListEntry?) -> AnimeListEntry?): AnimeListEntry? {
        var result: AnimeListEntry? = null
        store.edit { preferences ->
            val current = readAnimeList(preferences[entriesKey], preferences[legacyKey])
            val previous = current.firstOrNull { it.id == id }
            result = change(previous)
            if (result == previous) return@edit
            val updated = listOfNotNull(result) + current.filterNot { it.id == id }
            preferences[entriesKey] = listJson.encodeToString(updated)
        }
        return result
    }
}

private val Context.favouritesStore by preferencesDataStore(name = "animeplayer-favourites")
