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
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.littlenavmap.mobile.model.ServerProfile

/** Verifies that an address is a reachable Little Navmap web server. */
class ServerProbe(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun probe(profile: ServerProfile): Result<Unit> = withContext(ioDispatcher) {
        val validationError = profile.validate()
        if (validationError != null) {
            return@withContext Result.failure(InvalidServerProfileException(validationError))
        }

        try {
            probeBlocking(profile.normalized)
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ServerProbeException) {
            Result.failure(exception)
        } catch (exception: SocketTimeoutException) {
            Result.failure(ServerTimeoutException(exception))
        } catch (exception: UnknownHostException) {
            Result.failure(ServerHostNotFoundException(profile.displayName, exception))
        } catch (exception: ConnectException) {
            Result.failure(ServerUnavailableException(profile.displayName, exception))
        } catch (exception: NoRouteToHostException) {
            Result.failure(ServerUnavailableException(profile.displayName, exception))
        } catch (exception: SSLException) {
            Result.failure(ServerTlsException(exception))
        } catch (exception: IOException) {
            Result.failure(ServerConnectionException(profile.displayName, exception))
        }
    }

    private fun probeBlocking(profile: ServerProfile) {
        val connection = URL(profile.baseUrl + INFO_PATH).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                connection.errorStream?.use { /* Closing the error body releases the socket. */ }
                throw ServerHttpException(statusCode)
            }

            val response = connection.inputStream.use(::readLimitedUtf8)
            verifyLittleNavmapResponse(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimitedUtf8(stream: InputStream): String {
        val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
        val output = StringBuilder()
        val buffer = CharArray(READ_BUFFER_SIZE)
        while (true) {
            val count = reader.read(buffer)
            if (count == -1) break
            if (output.length + count > MAX_RESPONSE_CHARACTERS) {
                throw InvalidServerResponseException("Server response is unexpectedly large.")
            }
            output.append(buffer, 0, count)
        }
        return output.toString()
    }

    private fun verifyLittleNavmapResponse(response: String) {
        val root = try {
            JSON.parseToJsonElement(response) as? JsonObject
        } catch (exception: SerializationException) {
            throw InvalidServerResponseException(
                "The server did not return valid Little Navmap data.",
                exception,
            )
        } ?: throw InvalidServerResponseException(
            "The server did not return a Little Navmap information object.",
        )

        val zoom = (root["zoom_ui"] as? JsonPrimitive)?.doubleOrNull
        val bounds = root["latLonRect_ui"] as? JsonArray
        val hasValidBounds = bounds != null &&
            bounds.size == EXPECTED_BOUNDS_SIZE &&
            bounds.all { (it as? JsonPrimitive)?.doubleOrNull != null }

        if (zoom == null || !hasValidBounds) {
            throw InvalidServerResponseException(
                "This address does not appear to be a Little Navmap web server.",
            )
        }
    }

    private companion object {
        const val INFO_PATH = "api/ui/info"
        const val TIMEOUT_MILLIS = 4_000
        const val MAX_RESPONSE_CHARACTERS = 1_048_576
        const val READ_BUFFER_SIZE = 8_192
        const val EXPECTED_BOUNDS_SIZE = 4
        const val USER_AGENT = "LittleNavmap-Android"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

sealed class ServerProbeException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class InvalidServerProfileException(message: String) : ServerProbeException(message)

class ServerTimeoutException(cause: Throwable) : ServerProbeException(
    "Connection timed out after 4 seconds. Check that Little Navmap's web server is running.",
    cause,
)

class ServerHostNotFoundException(
    displayName: String,
    cause: Throwable,
) : ServerProbeException("Could not find the server $displayName.", cause)

class ServerUnavailableException(
    displayName: String,
    cause: Throwable,
) : ServerProbeException(
    "Could not connect to $displayName. Check the address, port, and Little Navmap web server.",
    cause,
)

class ServerTlsException(cause: Throwable) : ServerProbeException(
    "Secure connection failed. Check the server address and its HTTPS certificate.",
    cause,
)

class ServerConnectionException(
    displayName: String,
    cause: Throwable,
) : ServerProbeException("Connection to $displayName failed.", cause)

class ServerHttpException(
    val statusCode: Int,
) : ServerProbeException("Server returned HTTP status $statusCode.")

class InvalidServerResponseException(
    message: String,
    cause: Throwable? = null,
) : ServerProbeException(message, cause)
