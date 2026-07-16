/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.network

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.model.SimInfoResponse

internal fun interface SimInfoSource {
    suspend fun fetch(profile: ServerProfile): SimInfoResponse
}

/** Fetches one simulator sample from Little Navmap without retaining a connection. */
internal class SimInfoClient(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    maxResponseCharacters: Int = MAX_RESPONSE_CHARACTERS,
) : SimInfoSource {
    private val httpClient = HttpTextClient(
        ioDispatcher = ioDispatcher,
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis,
        maxResponseCharacters = maxResponseCharacters,
    )

    override suspend fun fetch(profile: ServerProfile): SimInfoResponse {
        val response = try {
            httpClient.get(profile, INFO_PATH)
        } catch (exception: ResponseTooLargeException) {
            throw InvalidSimInfoResponseException(
                "Simulator response is unexpectedly large.",
                exception,
            )
        }
        if (response.statusCode !in 200..299) {
            throw SimInfoHttpException(response.statusCode)
        }
        return try {
            JSON.decodeFromString<SimInfoResponse>(response.body)
        } catch (exception: SerializationException) {
            throw InvalidSimInfoResponseException(
                "Little Navmap returned invalid simulator data.",
                exception,
            )
        }
    }

    private companion object {
        const val INFO_PATH = "api/sim/info"
        const val DEFAULT_TIMEOUT_MILLIS = 4_000
        const val MAX_RESPONSE_CHARACTERS = 65_536
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

internal class SimInfoHttpException(
    val statusCode: Int,
) : IOException("Little Navmap returned HTTP status $statusCode for simulator data.")

internal class InvalidSimInfoResponseException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
