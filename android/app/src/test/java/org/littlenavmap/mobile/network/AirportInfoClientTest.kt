/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.network

import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littlenavmap.mobile.model.ServerProfile

class AirportInfoClientTest {
    @Test
    fun `encodes ident and requests expected endpoint`() {
        val recordedRequest = AtomicReference<RecordedRequest>()
        withServer(
            responseFor = { request ->
                recordedRequest.set(request)
                mockResponse(200, "OK", MINIMAL_AIRPORT_RESPONSE)
            },
        ) { profile ->
            val airport = AirportInfoClient().fetch(profile, "K P+D/X?")

            assertEquals("KPDX", airport.ident)
            assertEquals("Portland International", airport.name)
            assertEquals("GET", recordedRequest.get().method)
            assertEquals(
                "/api/airport/info?ident=K%20P%2BD%2FX%3F",
                recordedRequest.get().target,
            )
            assertEquals("application/json", recordedRequest.get().headers["accept"])
            assertTrue(
                recordedRequest.get().headers["user-agent"]
                    .orEmpty()
                    .startsWith("LittleNavmap-Android"),
            )
        }
    }

    @Test
    fun `maps non JSON 404 body to airport not found`() = withServer(
        responseFor = { mockResponse(404, "Not Found", "Airport not found") },
    ) { profile ->
        val failure = failureOfType<AirportNotFoundException> {
            AirportInfoClient().fetch(profile, "KSEA")
        }

        assertEquals("KSEA", failure.ident)
    }

    @Test
    fun `reports non 404 HTTP errors`() = withServer(
        responseFor = { mockResponse(503, "Service Unavailable", "not-json") },
    ) { profile ->
        val failure = failureOfType<AirportInfoHttpException> {
            AirportInfoClient().fetch(profile, "KPDX")
        }

        assertEquals(503, failure.statusCode)
    }

    @Test
    fun `classifies HTTP errors before reading an oversized error body`() {
        listOf(404, 503).forEach { statusCode ->
            withServer(
                responseFor = {
                    mockResponse(statusCode, "Error", "x".repeat(256))
                },
            ) { profile ->
                val failure = try {
                    AirportInfoClient(maxResponseCharacters = 32).fetch(profile, "KPDX")
                    null
                } catch (exception: Throwable) {
                    exception
                }

                when (statusCode) {
                    404 -> assertTrue(failure is AirportNotFoundException)
                    else -> assertEquals(
                        statusCode,
                        (failure as AirportInfoHttpException).statusCode,
                    )
                }
            }
        }
    }

    @Test
    fun `rejects malformed response and response without ident`() {
        listOf(
            "{not-json",
            """{"name":"Airport without ident"}""",
        ).forEach { body ->
            withJsonResponse(body) { profile ->
                failureOfType<InvalidAirportInfoResponseException> {
                    AirportInfoClient().fetch(profile, "KPDX")
                }
            }
        }
    }

    @Test
    fun `rejects response larger than configured limit`() = withJsonResponse(
        "x".repeat(65),
    ) { profile ->
        val failure = failureOfType<InvalidAirportInfoResponseException> {
            AirportInfoClient(maxResponseCharacters = 64).fetch(profile, "KPDX")
        }

        assertEquals("Airport response is unexpectedly large.", failure.message)
    }

    @Test
    fun `honors read timeout`() = withServer(
        responseFor = {
            mockResponse(
                statusCode = 200,
                reasonPhrase = "OK",
                body = MINIMAL_AIRPORT_RESPONSE,
                delayMillis = 250,
            )
        },
    ) { profile ->
        failureOfType<SocketTimeoutException> {
            AirportInfoClient(readTimeoutMillis = 50).fetch(profile, "KPDX")
        }
    }

    @Test
    fun `cancelling fetch disconnects an in flight request promptly`() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        TestHttpServer {
            requestStarted.complete(Unit)
            mockResponse(
                statusCode = 200,
                reasonPhrase = "OK",
                body = MINIMAL_AIRPORT_RESPONSE,
                delayMillis = 1_000,
            )
        }.use { server ->
            val profile = ServerProfile(host = TestHttpServer.LOOPBACK_HOST, port = server.port)
            val job = launch {
                AirportInfoClient(readTimeoutMillis = 5_000).fetch(profile, "KPDX")
            }
            requestStarted.await()

            val cancellationMillis = measureTimeMillis { job.cancelAndJoin() }

            assertTrue("Cancellation took ${cancellationMillis}ms", cancellationMillis < 750)
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
        const val MINIMAL_AIRPORT_RESPONSE = """
            {
              "ident": "KPDX",
              "name": "Portland International",
              "unknown_future_field": true
            }
        """
    }
}
