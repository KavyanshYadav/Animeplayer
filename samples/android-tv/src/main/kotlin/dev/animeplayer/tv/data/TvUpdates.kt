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
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.animeplayer.core.spi.HttpRequest
import dev.animeplayer.core.spi.HttpTransport
import dev.animeplayer.tv.BuildConfig
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class TvRelease(val version: String, val url: String, val size: Long, val sha256: String)

internal sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val release: TvRelease, val automatic: Boolean) : UpdateStatus
    data object Downloading : UpdateStatus
    data class Ready(val release: TvRelease, val apk: File, val automatic: Boolean) : UpdateStatus
    data class Failed(val message: String, val release: TvRelease? = null) : UpdateStatus
}

internal class TvUpdates(private val context: Context, private val transport: HttpTransport) {
    private val mutableStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private val operations = Mutex()
    private val preferences = context.getSharedPreferences("github-updates", Context.MODE_PRIVATE)

    suspend fun check(automatic: Boolean = false) {
        if (!operations.tryLock()) return
        val previous = mutableStatus.value
        try {
            val now = System.currentTimeMillis()
            val last = preferences.getLong("last-check", 0L)
            if (automatic && now - last in 0 until TimeUnit.DAYS.toMillis(1)) return
            mutableStatus.value = UpdateStatus.Checking
            preferences.edit().putLong("last-check", now).apply()
            val response = transport.execute(HttpRequest(
                url = "https://api.github.com/repos/$Repository/releases?per_page=30",
                headers = mapOf("Accept" to "application/vnd.github+json"),
            ))
            if (!response.isSuccessful) throw UpdateFailure("GitHub is unavailable. Try again.")
            val next = findTvRelease(response.bodyAsText(), BuildConfig.VERSION_NAME)
            mutableStatus.value = when {
                next == null -> UpdateStatus.Current
                previous is UpdateStatus.Ready && previous.release == next && previous.apk.isFile -> previous
                else -> UpdateStatus.Available(next, automatic)
            }
        } catch (cancelled: CancellationException) {
            mutableStatus.value = previous
            throw cancelled
        } catch (_: Exception) {
            mutableStatus.value = if (automatic) previous else UpdateStatus.Failed("Couldn’t check for updates. Try again.")
        } finally {
            operations.unlock()
        }
    }

    suspend fun download(release: TvRelease, automatic: Boolean = false) {
        if (!operations.tryLock()) return
        val folder = context.cacheDir.resolve("updates")
        val partial = folder.resolve("update.part")
        try {
            mutableStatus.value = UpdateStatus.Downloading
            val apk = withContext(Dispatchers.IO) {
                folder.mkdirs()
                val connection = URL(release.url).openConnection() as HttpsURLConnection
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    if (connection.responseCode != 200) throw UpdateFailure("GitHub didn’t return the APK.")
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                                if (bytes > release.size) throw UpdateFailure("The APK size didn’t match GitHub.")
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (bytes != release.size || hash != release.sha256) {
                    throw UpdateFailure("The downloaded APK failed its integrity check.")
                }
                val ready = folder.resolve("update.apk")
                if (!partial.renameTo(ready)) throw UpdateFailure("Couldn’t save the APK.")
                try {
                    verifyPackage(ready)
                } catch (failure: Exception) {
                    ready.delete()
                    throw failure
                }
                ready
            }
            mutableStatus.value = UpdateStatus.Ready(release, apk, automatic)
        } catch (cancelled: CancellationException) {
            mutableStatus.value = UpdateStatus.Available(release, automatic = false)
            throw cancelled
        } catch (failure: UpdateFailure) {
            mutableStatus.value = UpdateStatus.Failed(failure.message.orEmpty(), release)
        } catch (_: Exception) {
            mutableStatus.value = UpdateStatus.Failed("Couldn’t download the update. Try again.", release)
        } finally {
            partial.delete()
            operations.unlock()
        }
    }

    fun install(activityContext: Context, ready: UpdateStatus.Ready): String? = try {
        if (!context.packageManager.canRequestPackageInstalls()) {
            activityContext.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            "Allow installs for Animeplayer, then choose Install update again."
        } else {
            if (!ready.apk.isFile) throw UpdateFailure("The APK was cleared. Download it again.")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.apk)
            activityContext.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            null
        }
    } catch (failure: UpdateFailure) {
        mutableStatus.value = UpdateStatus.Failed(failure.message.orEmpty(), ready.release)
        failure.message
    } catch (_: Exception) {
        "The TV couldn’t open Android’s installer."
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        val candidate = manager.getPackageArchiveInfo(file.path, flags)
            ?: throw UpdateFailure("The downloaded file isn’t a valid APK.")
        val installed = manager.getPackageInfo(context.packageName, flags)
        if (candidate.packageName != installed.packageName || candidate.code() <= installed.code()) {
            throw UpdateFailure("The APK isn’t a newer version of Animeplayer for TV.")
        }
        if (candidate.signers().isEmpty() || candidate.signers() != installed.signers()) {
            throw UpdateFailure("This release uses a different signing key. Android can’t install it over this build.")
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.code(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun PackageInfo.signers(): Set<android.content.pm.Signature> =
        (if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures).orEmpty().toSet()
}

private const val Repository = "KavyanshYadav/Animeplayer"
private const val MaxApkBytes = 100L * 1024 * 1024
private class UpdateFailure(message: String) : Exception(message)

private val releaseJson = Json { ignoreUnknownKeys = true }
private val versionPattern = Regex("[0-9]{1,6}(?:\\.[0-9]{1,6}){1,3}")
private val digestPattern = Regex("sha256:([a-fA-F0-9]{64})")

/** The repository also publishes desktop releases, so select the TV tag and exact TV APK. */
internal fun findTvRelease(body: String, installed: String): TvRelease? {
    val installedVersion = versionParts(installed) ?: return null
    return releaseJson.decodeFromString<List<GitHubRelease>>(body).mapNotNull { release ->
        if (release.draft || release.prerelease || !release.tag_name.startsWith("android-tv-v")) return@mapNotNull null
        val version = release.tag_name.removePrefix("android-tv-v")
        val parts = versionParts(version) ?: return@mapNotNull null
        if (compareVersions(parts, installedVersion) <= 0) return@mapNotNull null
        val name = "animeplayer-tv-v$version.apk"
        val url = "https://github.com/$Repository/releases/download/${release.tag_name}/$name"
        val asset = release.assets.firstOrNull { it.name == name && it.browser_download_url == url && it.size in 1..MaxApkBytes }
            ?: return@mapNotNull null
        val digest = asset.digest?.let { digestPattern.matchEntire(it)?.groupValues?.get(1) } ?: return@mapNotNull null
        TvRelease(version, url, asset.size, digest.lowercase()) to parts
    }.maxWithOrNull { a, b -> compareVersions(a.second, b.second) }?.first
}

private fun versionParts(version: String): List<Int>? = version.takeIf(versionPattern::matches)?.split('.')?.map(String::toInt)

private fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (index in 0 until maxOf(a.size, b.size)) {
        val comparison = (a.getOrNull(index) ?: 0).compareTo(b.getOrNull(index) ?: 0)
        if (comparison != 0) return comparison
    }
    return 0
}

@Serializable
private data class GitHubRelease(
    val tag_name: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long,
    val digest: String? = null,
)
