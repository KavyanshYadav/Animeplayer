/*
 * Copyright (C) 2026 animeplayer contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.animeplayer.tv.ui.components

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

internal object YoutubePreviewSource {
    private data class Cached(val url: String?, val expiresAt: Long)

    private val cache = linkedMapOf<String, Cached>()
    private val extraction = Mutex()
    private val validId = Regex("[A-Za-z0-9_-]{11}")
    private val initialized by lazy { NewPipe.init(PreviewDownloader) }

    // Calls arrive on the main thread. NewPipe's blocking work is serialized off it.
    suspend fun resolve(id: String): String? = extraction.withLock {
        if (!validId.matches(id)) return@withLock null
        cache[id]?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }?.let {
            return@withLock it.url
        }

        val url = try {
            withTimeoutOrNull(10_000L) {
                runInterruptible(Dispatchers.IO) {
                    Log.d("YoutubePreview", "Resolving trailer")
                    initialized
                    val stream = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$id")
                    stream.fetchPage()
                    // A silent preview only needs one stream. Prefer H.264 for older TVs.
                    selectStream(stream.videoOnlyStreams) ?: selectStream(stream.videoStreams)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.d("YoutubePreview", "Extraction failed: ${error.javaClass.simpleName}")
            null
        } catch (error: LinkageError) {
            Log.w("YoutubePreview", "Extractor unavailable: ${error.javaClass.simpleName}")
            null
        }
        remember(id, url)
        url
    }

    fun playbackFailed(id: String) {
        remember(id, null)
    }

    private fun remember(id: String, url: String?) {
        cache.remove(id)
        if (cache.size >= 16) cache.remove(cache.keys.first())
        cache[id] = Cached(url, SystemClock.elapsedRealtime() + if (url == null) 60_000L else 600_000L)
    }

    private fun selectStream(streams: List<VideoStream>): String? = streams.asSequence()
        .filter {
            it.isUrl && it.content.startsWith("https://") &&
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                it.codec.orEmpty().startsWith("avc1") &&
                it.height in 1..360 && it.width in 1..640 && it.fps <= 30
        }
        .maxByOrNull { it.height }?.content
}

private object PreviewDownloader : Downloader() {
    override fun execute(request: Request): Response {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
        val connection = URL(request.url()).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.requestMethod = request.httpMethod()
            request.headers().forEach { (name, values) ->
                values.forEach { connection.addRequestProperty(name, it) }
            }
            request.dataToSend()?.let { data ->
                connection.doOutput = true
                connection.outputStream.use { it.write(data) }
            }
            val code = connection.responseCode
            if (code == 429) throw ReCaptchaException("YouTube rate limit", request.url())
            val body = (if (code >= 400) connection.errorStream else connection.inputStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Response(
                code, connection.responseMessage.orEmpty(), connection.headerFields.filterKeys { it != null },
                body, connection.url.toString(),
            )
        } finally {
            connection.disconnect()
        }
    }
}
