/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.network

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AviationWeatherClient {
    suspend fun metar(station: String): String = withContext(Dispatchers.IO) {
        val ident = station.trim().uppercase()
        require(ident.matches(Regex("[A-Z0-9]{3,4}"))) { "Enter a valid airport identifier." }
        val connection = URL(
            "https://aviationweather.gov/api/data/metar?ids=$ident&format=raw&taf=false",
        ).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "text/plain")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Weather service returned HTTP ${connection.responseCode}.")
            }
            connection.inputStream.bufferedReader().use { reader ->
                reader.readText().trim().ifBlank { throw IllegalStateException("No METAR available for $ident.") }
            }
        } finally {
            connection.disconnect()
        }
    }
}
