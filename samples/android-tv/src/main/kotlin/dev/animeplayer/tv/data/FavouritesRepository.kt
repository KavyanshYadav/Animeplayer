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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * The favourites of the user.
 *
 * The catalogue gives every series a number, and that number is what is remembered. The
 * list survives a restart, so the favourites are the same on the television and on the
 * desktop.
 */
public class FavouritesRepository(context: Context) {

    private val store: DataStore<Preferences> = context.favouritesStore
    private val mutex = Mutex()

    /** The identifiers of the favourite series. */
    public suspend fun ids(): List<String> {
        val preferences = read()
        return preferences[KEY_FAVOURITES]
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    /** `true` when the series is a favourite. */
    public suspend fun isFavourite(id: String): Boolean = id in ids()

    /**
     * Adds the series to the favourites, or removes it.
     *
     * @return `true` when the series is a favourite afterwards.
     */
    public suspend fun toggle(id: String): Boolean = mutex.withLock {
        val current = ids().toMutableList()
        val favourite = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(0, id)
            true
        }
        store.edit { it[KEY_FAVOURITES] = current.joinToString(",") }
        favourite
    }

    private suspend fun read(): Preferences = try {
        store.data.first()
    } catch (error: IOException) {
        emptyPreferences()
    }

    private companion object {
        val KEY_FAVOURITES = stringPreferencesKey("favourites")
    }
}

private val Context.favouritesStore by preferencesDataStore(name = "animeplayer-favourites")
