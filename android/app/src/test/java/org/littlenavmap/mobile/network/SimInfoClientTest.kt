/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.network

import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littlenavmap.mobile.model.ServerProfile

class SimInfoClientTest {
    @Test
    fun `requests expected endpoint and parses a complete active response`() {
        val recordedRequest = AtomicReference<RecordedRequest>()

        withServer(
            responseFor = { request ->
                recordedRequest.set(request)
                mockResponse(200, "OK", COMPLETE_ACTIVE_RESPONSE)
            },
        ) { profile ->
            val response = SimInfoClient().fetch(profile)

            assertTrue(response.active)
            assertEquals("Connected to simulator", response.simConnectStatus)
            assertEquals(49.1947, response.position?.lat ?: Double.NaN, 0.0001)
            assertEquals(-123.1839, response.position?.lon ?: Double.NaN, 0.0001)
            assertEquals(112.4, response.indicatedSpeed ?: Double.NaN, 0.0001)
            assertEquals(118.7, response.trueAirspeed ?: Double.NaN, 0.0001)
            assertEquals(115.2, response.groundSpeed ?: Double.NaN, 0.0001)
            assertEquals(650.0, response.verticalSpeed ?: Double.NaN, 0.0001)
            assertEquals(5420.0, response.indicatedAltitude ?: Double.NaN, 0.0001)
            assertEquals(14.0, response.groundAltitude ?: Double.NaN, 0.0001)
            assertEquals(5406.0, response.altitudeAboveGround ?: Double.NaN, 0.0001)
            assertEquals(173.6, response.heading ?: Double.NaN, 0.0001)
            assertEquals(245.0, response.windDirection ?: Double.NaN, 0.0001)
            assertEquals(18.0, response.windSpeed ?: Double.NaN, 0.0001)
            assertEquals(1013.2, response.seaLevelPressure ?: Double.NaN, 0.0001)

            assertEquals("GET", recordedRequest.get().method)
            assertEquals(SIM_INFO_PATH, recordedRequest.get().target)
            assertEquals("application/json", recordedRequest.get().headers["accept"])
            assertTrue(
                recordedRequest.get().headers["user-agent"]
                    .orEmpty()
                    .startsWith("LittleNavmap-Android"),
            )
        }
    }

    @Test
    fun `accepts inactive response without optional fields`() = withJsonResponse(
        """{"active":false}""",
    ) { profile ->
        val response = SimInfoClient().fetch(profile)

        assertFalse(response.active)
        assertNull(response.position)
        assertNull(response.groundSpeed)
    }

    @Test
    fun `accepts active response with missing optional and unknown fields`() = withJsonResponse(
        """{"active":true,"future_field":{"value":42}}""",
    ) { profile ->
        val response = SimInfoClient().fetch(profile)

        assertTrue(response.active)
        assertNull(response.position)
        assertNull(response.indicatedAltitude)
    }

    @Test
    fun `rejects malformed response and response without active flag`() {
        listOf("{not-json", """{"ground_speed":120}""").forEach { body ->
            withJsonResponse(body) { profile ->
                failureOfType<InvalidSimInfoResponseException> {
                    SimInfoClient().fetch(profile)
                }
            }
        }
    }

    @Test
    fun `reports HTTP errors without following redirects`() {
        val redirectedRequests = AtomicInteger()
        withServer(
            responseFor = { request ->
                when (request.target) {
                    SIM_INFO_PATH -> mockResponse(
                        302,
                        "Found",
                        headers = mapOf("Location" to REDIRECT_TARGET),
                    )
                    REDIRECT_TARGET -> {
                        redirectedRequests.incrementAndGet()
                        mockResponse(200, "OK", COMPLETE_ACTIVE_RESPONSE)
                    }
                    else -> mockResponse(404, "Not Found")
                }
            },
        ) { profile ->
            val failure = failureOfType<SimInfoHttpException> {
                SimInfoClient().fetch(profile)
            }

            assertEquals(302, failure.statusCode)
            assertEquals(0, redirectedRequests.get())
        }
    }

    @Test
    fun `rejects a response larger than configured limit`() = withJsonResponse(
        "x".repeat(65),
    ) { profile ->
        val failure = failureOfType<InvalidSimInfoResponseException> {
            SimInfoClient(maxResponseCharacters = 64).fetch(profile)
        }

        assertEquals("Simulator response is unexpectedly large.", failure.message)
    }

    @Test
    fun `honors read timeout`() = withServer(
        responseFor = {
            mockResponse(
                statusCode = 200,
                reasonPhrase = "OK",
                body = COMPLETE_ACTIVE_RESPONSE,
                delayMillis = 250,
            )
        },
    ) { profile ->
        failureOfType<SocketTimeoutException> {
            SimInfoClient(readTimeoutMillis = 50).fetch(profile)
        }
    }

    private fun withJsonResponse(
        body: String,
        test: suspend (ServerProfile) -> Unit,
    ) = withServer(
        responseFor = { mockResponse(200, "OK", body) },
        test = test,
    )

    private fun withServer(
        responseFor: (RecordedRequest) -> MockResponse,
        test: suspend (ServerProfile) -> Unit,
    ) = runBlocking {
        TestHttpServer(responseFor).use { server ->
            test(ServerProfile(host = TestHttpServer.LOOPBACK_HOST, port = server.port))
        }
    }

    private suspend inline fun <reified T : Throwable> failureOfType(
        crossinline block: suspend () -> Unit,
    ): T {
        val failure = try {
            block()
            null
        } catch (exception: Throwable) {
            exception
        }
        assertTrue(
            "Expected ${T::class.java.simpleName}, but was ${failure?.javaClass?.simpleName}",
            failure is T,
        )
        return failure as T
    }

    private companion object {
        const val SIM_INFO_PATH = "/api/sim/info"
        const val REDIRECT_TARGET = "/redirect-target"
        const val COMPLETE_ACTIVE_RESPONSE = """
            {
              "active": true,
              "simconnect_status": "Connected to simulator",
              "position": {"lat": 49.1947, "lon": -123.1839},
              "indicated_speed": 112.4,
              "true_airspeed": 118.7,
              "ground_speed": 115.2,
              "vertical_speed": 650.0,
              "indicated_altitude": 5420.0,
              "ground_altitude": 14.0,
              "altitude_above_ground": 5406.0,
              "heading": 173.6,
              "wind_direction": 245.0,
              "wind_speed": 18.0,
              "sea_level_pressure": 1013.2,
              "future_field": "ignored"
            }
        """
    }
}
