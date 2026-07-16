/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.network

import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.littlenavmap.mobile.model.FlightPlan
import org.xml.sax.InputSource
import java.io.StringReader

/** Imports the latest dispatch released by a SimBrief user without collecting credentials. */
internal class SimBriefFlightPlanClient(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    suspend fun fetch(username: String): FlightPlan = withContext(Dispatchers.IO) {
        val normalizedUsername = username.trim()
        require(USERNAME_PATTERN.matches(normalizedUsername)) {
            "Enter a valid SimBrief username."
        }
        val url = "$endpoint?username=${URLEncoder.encode(normalizedUsername, StandardCharsets.UTF_8.name())}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/xml, text/xml")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in HTTP_SUCCESS_RANGE) {
                throw IllegalStateException("SimBrief returned HTTP $code.")
            }
            val content = connection.inputStream.use {
                InputStreamReader(it, StandardCharsets.UTF_8).readTextLimited(MAX_RESPONSE_CHARACTERS)
            }
            decode(content)
        } finally {
            connection.disconnect()
        }
    }

    internal fun decode(content: String): FlightPlan {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(content)))
        val origin = document.airportCode("origin")
        val destination = document.airportCode("destination")
        require(origin.isNotBlank() && destination.isNotBlank()) {
            "The SimBrief dispatch does not contain both airports."
        }
        val route = document.firstText("atc_route", "route")
        return FlightPlan(
            origin = origin,
            destination = destination,
            alternate = document.airportCode("alternate"),
            cruiseLevel = flightLevel(document.firstText("initial_altitude", "cruise_altitude")),
            departureProcedure = document.firstText("sid"),
            arrivalProcedure = document.firstText("star"),
            waypoints = route.split(Regex("\\s+"))
                .map(String::trim)
                .filter { it.isNotBlank() && !it.equals("DCT", true) }
                .filterNot { it.equals(origin, true) || it.equals(destination, true) },
        )
    }

    private fun org.w3c.dom.Document.airportCode(section: String): String {
        val container = getElementsByTagName(section).item(0) ?: return ""
        val children = container.childNodes
        return (0 until children.length)
            .map { children.item(it) }
            .firstOrNull { it.nodeName in setOf("icao_code", "icao", "ident") }
            ?.textContent
            ?.trim()
            ?.uppercase()
            .orEmpty()
    }

    private fun org.w3c.dom.Document.firstText(vararg tags: String): String =
        tags.firstNotNullOfOrNull { tag ->
            getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf(String::isNotBlank)
        }.orEmpty()

    private fun flightLevel(value: String): String {
        val altitude = value.trim().toIntOrNull()
        return when {
            value.isBlank() -> "FL340"
            altitude != null && altitude >= 18_000 -> "FL${altitude / 100}"
            else -> value.uppercase()
        }
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://www.simbrief.com/api/xml.fetcher.php"
        const val DEFAULT_TIMEOUT_MILLIS = 8_000
        const val MAX_RESPONSE_CHARACTERS = 1_000_000
        const val USER_AGENT = "LittleNavmap-Mobile"
        val HTTP_SUCCESS_RANGE = 200..299
        val USERNAME_PATTERN = Regex("[A-Za-z0-9_.-]{1,64}")
    }
}

internal fun InputStreamReader.readTextLimited(limit: Int): String {
    val output = StringBuilder()
    val buffer = CharArray(8_192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toString()
        check(output.length + count <= limit) { "SimBrief response is unexpectedly large." }
        output.append(buffer, 0, count)
    }
}
