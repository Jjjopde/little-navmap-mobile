/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.network

import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.littlenavmap.mobile.model.FlightPlan
import org.littlenavmap.mobile.model.FlightPlanCodec

/** Reads a flight-plan export from the Navigraph Cloud API using a user-issued OAuth bearer token. */
internal class NavigraphFlightPlanClient(
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    suspend fun fetch(exportUrl: String, accessToken: String): FlightPlan = withContext(Dispatchers.IO) {
        val url = URL(exportUrl.trim())
        require(url.protocol.equals("https", true) && isNavigraphHost(url.host)) {
            "Use an HTTPS flight-plan export URL from Navigraph."
        }
        val token = accessToken.trim()
        require(token.length >= MIN_TOKEN_LENGTH) { "Enter a valid Navigraph access token." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json, application/octet-stream, text/plain")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in HTTP_SUCCESS_RANGE) {
                throw IllegalStateException("Navigraph returned HTTP $code.")
            }
            val content = connection.inputStream.use {
                InputStreamReader(it, StandardCharsets.UTF_8).readTextLimited(MAX_RESPONSE_CHARACTERS)
            }
            FlightPlanCodec.decodeImported(content)
        } finally {
            connection.disconnect()
        }
    }

    private fun isNavigraphHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "navigraph.com" || normalized.endsWith(".navigraph.com")
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 8_000
        const val MAX_RESPONSE_CHARACTERS = 1_000_000
        const val MIN_TOKEN_LENGTH = 16
        const val USER_AGENT = "LittleNavmap-Mobile"
        val HTTP_SUCCESS_RANGE = 200..299
    }
}
