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
import org.littlenavmap.mobile.model.AirportInfoResponse
import org.littlenavmap.mobile.model.AirportSnapshot
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.model.toAirportSnapshot

internal fun interface AirportInfoSource {
    suspend fun fetch(profile: ServerProfile, ident: String): AirportSnapshot
}

internal class AirportInfoClient(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    maxResponseCharacters: Int = MAX_RESPONSE_CHARACTERS,
) : AirportInfoSource {
    private val httpClient = HttpTextClient(
        ioDispatcher = ioDispatcher,
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis,
        maxResponseCharacters = maxResponseCharacters,
    )

    override suspend fun fetch(profile: ServerProfile, ident: String): AirportSnapshot {
        val response = try {
            httpClient.get(
                profile = profile,
                path = INFO_PATH,
                queryParameters = listOf("ident" to ident),
            )
        } catch (exception: ResponseTooLargeException) {
            throw InvalidAirportInfoResponseException(
                "Airport response is unexpectedly large.",
                exception,
            )
        }
        if (response.statusCode == HTTP_NOT_FOUND) {
            throw AirportNotFoundException(ident)
        }
        if (response.statusCode !in 200..299) {
            throw AirportInfoHttpException(response.statusCode)
        }

        val wireResponse = try {
            JSON.decodeFromString<AirportInfoResponse>(response.body)
        } catch (exception: SerializationException) {
            throw InvalidAirportInfoResponseException(
                "Little Navmap returned invalid airport data.",
                exception,
            )
        }
        return wireResponse.toAirportSnapshot()
            ?: throw InvalidAirportInfoResponseException(
                "Little Navmap returned airport data without a valid identifier.",
            )
    }

    private companion object {
        const val INFO_PATH = "api/airport/info"
        const val DEFAULT_TIMEOUT_MILLIS = 4_000
        const val MAX_RESPONSE_CHARACTERS = 131_072
        const val HTTP_NOT_FOUND = 404
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

internal class AirportNotFoundException(
    val ident: String,
) : IOException("Airport $ident was not found.")

internal class AirportInfoHttpException(
    val statusCode: Int,
) : IOException("Little Navmap returned HTTP status $statusCode for airport data.")

internal class InvalidAirportInfoResponseException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
