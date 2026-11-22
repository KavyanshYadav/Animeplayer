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

import android.util.AtomicFile
import dev.animeplayer.core.spi.HttpRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

internal class TmdbCredentialError(message: String) : Exception(message)

internal class TmdbCredential private constructor(private val value: String, private val apiKey: Boolean) {
    fun request(path: String): HttpRequest = HttpRequest(
        url = "https://api.themoviedb.org/3/$path" + if (apiKey) {
            (if ('?' in path) "&" else "?") + "api_key=$value"
        } else "",
        headers = if (apiKey) mapOf("Accept" to "application/json")
        else mapOf("Authorization" to "Bearer $value", "Accept" to "application/json"),
        timeout = 5.seconds,
    )

    companion object {
        fun bearer(value: String): TmdbCredential = TmdbCredential(value, apiKey = false)

        fun parse(input: String): TmdbCredential? {
            val value = input.trim()
            return when {
                value.matches(Regex("[a-fA-F0-9]{32}")) -> TmdbCredential(value, apiKey = true)
                value.length <= 4096 && value.matches(Regex("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+){2}")) ->
                    TmdbCredential(value, apiKey = false)
                else -> null
            }
        }
    }
}

internal class TmdbCredentialStore(file: File) {
    private val storage = AtomicFile(file)

    fun read(): TmdbCredential? = runCatching {
        TmdbCredential.parse(storage.readFully().decodeToString())
    }.getOrNull()

    suspend fun write(value: String?) = withContext(Dispatchers.IO) {
        // An empty file clears the custom credential atomically.
        val output = storage.startWrite()
        try {
            output.write(value.orEmpty().trim().encodeToByteArray())
            storage.finishWrite(output)
        } catch (failure: Exception) {
            storage.failWrite(output)
            throw failure
        }
    }
}
