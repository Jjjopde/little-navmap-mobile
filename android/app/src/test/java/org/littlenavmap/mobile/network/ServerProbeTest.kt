/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.network

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littlenavmap.mobile.model.ServerProfile

class ServerProbeTest {
    @Test
    fun `accepts Little Navmap information from the expected endpoint`() {
        val requestedPath = AtomicReference<String>()

        withServer(
            responseFor = { path ->
                requestedPath.set(path)
                response(200, "OK", VALID_INFO_RESPONSE)
            },
        ) { profile ->
            val result = ServerProbe().probe(profile)

            assertTrue(result.isSuccess)
            assertEquals(INFO_PATH, requestedPath.get())
        }
    }

    @Test
    fun `rejects JSON from a server that is not Little Navmap`() = withJsonResponse(
        """{"name":"another service","status":"ok"}""",
    ) { profile ->
        val failure = ServerProbe().probe(profile).failureOfType<InvalidServerResponseException>()

        assertEquals(
            "This address does not appear to be a Little Navmap web server.",
            failure.message,
        )
    }

    @Test
    fun `rejects invalid JSON`() = withJsonResponse("{not-json") { profile ->
        val failure = ServerProbe().probe(profile).failureOfType<InvalidServerResponseException>()

        assertEquals("The server did not return valid Little Navmap data.", failure.message)
        assertNotNull(failure.cause)
    }

    @Test
    fun `reports HTTP errors`() = withServer(
        responseFor = {
            response(
                503,
                "Service Unavailable",
                """{"error":"temporarily unavailable"}""",
            )
        },
    ) { profile ->
        val failure = ServerProbe().probe(profile).failureOfType<ServerHttpException>()

        assertEquals(503, failure.statusCode)
    }

    @Test
    fun `does not follow redirects`() {
        val redirectedRequests = AtomicInteger()

        withServer(
            responseFor = { path ->
                when (path) {
                    INFO_PATH -> response(
                        302,
                        "Found",
                        headers = mapOf("Location" to REDIRECT_TARGET),
                    )
                    REDIRECT_TARGET -> {
                        redirectedRequests.incrementAndGet()
                        response(200, "OK", VALID_INFO_RESPONSE)
                    }
                    else -> response(404, "Not Found")
                }
            },
        ) { profile ->
            val failure = ServerProbe().probe(profile).failureOfType<ServerHttpException>()

            assertEquals(302, failure.statusCode)
            assertEquals(0, redirectedRequests.get())
        }
    }

    @Test
    fun `rejects responses larger than the configured limit`() = withJsonResponse(
        "x".repeat(1_048_577),
    ) { profile ->
        val failure = ServerProbe().probe(profile).failureOfType<InvalidServerResponseException>()

        assertEquals("Server response is unexpectedly large.", failure.message)
    }

    private fun withJsonResponse(
        body: String,
        test: suspend (ServerProfile) -> Unit,
    ) = withServer(
        responseFor = { response(200, "OK", body) },
        test = test,
    )

    private fun withServer(
        responseFor: (String) -> MockResponse,
        test: suspend (ServerProfile) -> Unit,
    ) = runBlocking {
        val server = TestHttpServer { request -> responseFor(request.target) }

        try {
            test(ServerProfile(host = TestHttpServer.LOOPBACK_HOST, port = server.port))
        } finally {
            server.close()
        }
    }

    private fun response(
        statusCode: Int,
        reasonPhrase: String,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
    ) = MockResponse(
        statusCode,
        reasonPhrase,
        body.toByteArray(Charsets.UTF_8),
        headers,
    )

    private inline fun <reified T : Throwable> Result<Unit>.failureOfType(): T {
        val failure = exceptionOrNull()
        assertNotNull("Expected ${T::class.java.simpleName}, but probe succeeded", failure)
        assertTrue(
            "Expected ${T::class.java.simpleName}, but was ${failure!!::class.java.simpleName}",
            failure is T,
        )
        return failure as T
    }

    private companion object {
        const val INFO_PATH = "/api/ui/info"
        const val REDIRECT_TARGET = "/redirect-target"
        const val VALID_INFO_RESPONSE =
            """{"zoom_ui":8.5,"latLonRect_ui":[-10.0,20.0,30.0,40.0]}"""
    }
}
