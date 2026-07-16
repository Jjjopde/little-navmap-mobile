/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.network

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.littlenavmap.mobile.model.ServerProfile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class HttpTextResponse(
    val statusCode: Int,
    val body: String,
)

/** Shared bounded GET transport for Little Navmap's small text and JSON APIs. */
internal class HttpTextClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val maxResponseCharacters: Int,
) {
    init {
        require(connectTimeoutMillis > 0)
        require(readTimeoutMillis > 0)
        require(maxResponseCharacters > 0)
    }

    suspend fun get(
        profile: ServerProfile,
        path: String,
        queryParameters: List<Pair<String, String>> = emptyList(),
    ): HttpTextResponse = withContext(ioDispatcher) {
        profile.validate()?.let { throw InvalidServerProfileException(it) }
        require(path.isNotBlank() && !path.startsWith('/') &&
            "://" !in path && '?' !in path && '#' !in path)
        getBlocking(profile.normalized, path, queryParameters)
    }

    private suspend fun getBlocking(
        profile: ServerProfile,
        path: String,
        queryParameters: List<Pair<String, String>>,
    ): HttpTextResponse = suspendCancellableCoroutine { continuation ->
        val query = queryParameters.joinToString("&") { (name, value) ->
            "${name.urlEncode()}=${value.urlEncode()}"
        }
        val requestUrl = profile.baseUrl + path + if (query.isEmpty()) "" else "?$query"
        val connection = URL(requestUrl).openConnection() as HttpURLConnection
        continuation.invokeOnCancellation { connection.disconnect() }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val statusCode = connection.responseCode
            val response = if (statusCode !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                HttpTextResponse(statusCode = statusCode, body = "")
            } else {
                val body = connection.inputStream.use {
                    readLimitedUtf8(it, maxResponseCharacters)
                }
                HttpTextResponse(statusCode = statusCode, body = body)
            }
            continuation.resume(response)
        } catch (exception: Exception) {
            if (continuation.isActive) continuation.resumeWithException(exception)
        } finally {
            connection.disconnect()
        }
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 4_000
        const val HTTP_SUCCESS_MIN = 200
        const val HTTP_SUCCESS_MAX = 299
        const val USER_AGENT = "LittleNavmap-Android"
    }
}

internal fun readLimitedUtf8(stream: InputStream, maxCharacters: Int): String {
    val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
    val output = StringBuilder()
    val buffer = CharArray(READ_BUFFER_SIZE)
    while (true) {
        val count = reader.read(buffer)
        if (count == -1) break
        if (output.length + count > maxCharacters) {
            throw ResponseTooLargeException()
        }
        output.append(buffer, 0, count)
    }
    return output.toString()
}

internal class ResponseTooLargeException : IOException("Server response is unexpectedly large.")

private const val READ_BUFFER_SIZE = 8_192
