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
import android.util.AtomicFile
import dev.animeplayer.core.spi.HttpMethod
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

internal data class AniListSyncPlan(val local: List<AnimeListEntry>, val outgoing: List<AnimeListEntry>)

internal fun planAniListSync(
    local: List<AnimeListEntry>,
    remote: List<AnimeListEntry>,
    ignored: Set<String>,
): AniListSyncPlan {
    val localById = local.associateBy { it.id }
    val remoteById = remote.associateBy { it.id }
    val outgoing = mutableListOf<AnimeListEntry>()
    val merged = local.map { entry ->
        val online = remoteById[entry.id]
        when {
            online == null || entry.updatedAt > online.updatedAt -> {
                if (online == null || !entry.sameListValues(online)) outgoing += entry
                entry
            }
            entry.sameListValues(online) -> entry
            else -> entry.copy(
                status = online.status, progress = online.progress, score = online.score,
                updatedAt = online.updatedAt,
                manualProgress = entry.manualProgress || entry.progress > online.progress,
                watchedOverrides = if (entry.progress == online.progress) entry.watchedOverrides else emptyMap(),
            )
        }
    }
    return AniListSyncPlan(merged + remote.filter { it.id !in localById && it.id !in ignored }, outgoing)
}

private fun AnimeListEntry.sameListValues(other: AnimeListEntry): Boolean =
    status == other.status && progress == other.progress && score == other.score

internal data class AniListAccountState(
    val name: String? = null,
    val linked: Boolean = false,
    val syncing: Boolean = false,
    val message: String? = null,
)

internal class AniListAccount(context: Context, private val transport: HttpTransport, private val lists: FavouritesRepository) {
    private val secret = AtomicFile(context.noBackupFilesDir.resolve("anilist-token"))
    private var token = runCatching { secret.readFully().decodeToString().takeIf(String::isNotBlank) }.getOrNull()
    private val mutableState = MutableStateFlow(AniListAccountState(linked = token != null))
    val state = mutableState.asStateFlow()
    private val operation = Mutex()

    suspend fun connect(input: String) = exclusive {
        val key = input.trim()
        if (key.length !in 10..4096 || key.any { it.isWhitespace() }) throw AniListFailure("Enter the access token from the sign-in page.")
        val viewer = viewer(key)
        writeToken(key)
        token = key
        mutableState.value = AniListAccountState(name = viewer.name, linked = true)
    }

    suspend fun disconnect() = exclusive {
        writeToken(null)
        token = null
        mutableState.value = AniListAccountState(message = "AniList disconnected. Your TV list is still saved.")
    }

    suspend fun sync() = exclusive {
        val key = token ?: return@exclusive
        val viewer = viewer(key)
        mutableState.value = AniListAccountState(name = viewer.name, linked = true, syncing = true)
        val remote = buildList {
            var chunk = 1
            do {
                val collection = query(key, collectionQuery, buildJsonObject {
                    put("user", viewer.id)
                    put("chunk", chunk++)
                }).objectAt("MediaListCollection") ?: throw AniListFailure("AniList did not return your list.")
                (collection["lists"] as? JsonArray).orEmpty().forEach { group ->
                    ((group as? JsonObject)?.get("entries") as? JsonArray).orEmpty().forEach { value ->
                        (value as? JsonObject)?.readEntry()?.let(::add)
                    }
                }
                val more = collection.string("hasNextChunk") == "true"
                if (more && chunk > 100) throw AniListFailure("AniList returned too many list pages.")
            } while (more)
        }.distinctBy { it.id }
        val changes = lists.reconcile(remote)
        changes.forEach { entry ->
            query(key, saveQuery, buildJsonObject {
                put("id", entry.id.toInt())
                put("status", encodeStatus(entry.status))
                put("progress", entry.progress)
                put("score", entry.score * 10)
            })
        }
        mutableState.value = AniListAccountState(viewer.name, linked = true, message = "AniList sync complete.")
    }

    private suspend fun exclusive(action: suspend () -> Unit) {
        if (!operation.tryLock()) return
        val previous = mutableState.value
        mutableState.value = previous.copy(syncing = true, message = null)
        try {
            action()
        } catch (cancelled: CancellationException) {
            mutableState.value = previous
            throw cancelled
        } catch (failure: AniListFailure) {
            mutableState.value = mutableState.value.copy(message = failure.message)
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(message = "Couldn’t contact AniList or save the changes. Try again.")
        } finally {
            mutableState.value = mutableState.value.copy(syncing = false)
            operation.unlock()
        }
    }

    private suspend fun writeToken(value: String?) = withContext(Dispatchers.IO) {
        val output = secret.startWrite()
        try {
            output.write(value.orEmpty().encodeToByteArray())
            secret.finishWrite(output)
        } catch (failure: Exception) {
            secret.failWrite(output)
            throw failure
        }
    }

    private suspend fun viewer(key: String): Viewer {
        val data = query(key, "query { Viewer { id name } }").objectAt("Viewer")
        val id = data?.string("id")?.toIntOrNull() ?: throw AniListFailure("AniList couldn’t identify this account.")
        return Viewer(id, data.string("name") ?: "AniList")
    }

    private suspend fun query(key: String, document: String, variables: JsonObject = buildJsonObject {}): JsonObject {
        val response = transport.execute(HttpRequest(
            url = "https://graphql.anilist.co", method = HttpMethod.Post,
            headers = mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json", "Accept" to "application/json"),
            body = buildJsonObject { put("query", document); put("variables", variables) }.toString().encodeToByteArray(),
        ))
        if (response.status == 401 || response.status == 403) throw AniListFailure("AniList rejected the access token.")
        if (!response.isSuccessful) throw AniListFailure("AniList is unavailable. Try again.")
        val body = Json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        if ((body?.get("errors") as? JsonArray)?.isNotEmpty() == true) {
            throw AniListFailure("AniList couldn’t complete this request.")
        }
        return body?.objectAt("data") ?: throw AniListFailure("AniList returned an empty response.")
    }

    private data class Viewer(val id: Int, val name: String)
}

private class AniListFailure(message: String) : Exception(message)
private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.readEntry(): AnimeListEntry? {
    val id = string("mediaId")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val status = when (string("status")) {
        "CURRENT", "REPEATING" -> ListStatus.Watching
        "PLANNING" -> ListStatus.Planned
        "COMPLETED" -> ListStatus.Completed
        "PAUSED" -> ListStatus.Paused
        "DROPPED" -> ListStatus.Dropped
        else -> return null
    }
    return AnimeListEntry(
        id.toString(), status,
        string("progress")?.toIntOrNull()?.coerceIn(0, MaxListProgress) ?: 0,
        string("score")?.toDoubleOrNull()?.let { (it / 10).roundToInt().coerceIn(0, 10) } ?: 0,
        updatedAt = string("updatedAt")?.toLongOrNull() ?: 0,
    )
}

private fun encodeStatus(status: ListStatus): String = when (status) {
    ListStatus.Watching -> "CURRENT"
    ListStatus.Planned -> "PLANNING"
    ListStatus.Completed -> "COMPLETED"
    ListStatus.Paused -> "PAUSED"
    ListStatus.Dropped -> "DROPPED"
}

private const val collectionQuery = """query(${'$'}user:Int!,${'$'}chunk:Int!) {
    MediaListCollection(userId:${'$'}user,type:ANIME,chunk:${'$'}chunk,perChunk:100) {
        hasNextChunk lists { entries { mediaId status progress score(format:POINT_100) updatedAt } }
    }
}"""
private const val saveQuery = """mutation(${'$'}id:Int!,${'$'}status:MediaListStatus!,${'$'}progress:Int!,${'$'}score:Int!) {
    SaveMediaListEntry(mediaId:${'$'}id,status:${'$'}status,progress:${'$'}progress,scoreRaw:${'$'}score) { id }
}"""
