/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.network

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class RecordedRequest(
    val method: String,
    val target: String,
    val headers: Map<String, String>,
)

internal data class MockResponse(
    val statusCode: Int,
    val reasonPhrase: String,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
    val delayMillis: Long = 0,
)

internal fun mockResponse(
    statusCode: Int,
    reasonPhrase: String,
    body: String = "",
    headers: Map<String, String> = emptyMap(),
    delayMillis: Long = 0,
) = MockResponse(
    statusCode = statusCode,
    reasonPhrase = reasonPhrase,
    body = body.toByteArray(StandardCharsets.UTF_8),
    headers = headers,
    delayMillis = delayMillis,
)

internal class TestHttpServer(
    private val responseFor: (RecordedRequest) -> MockResponse,
) : Closeable {
    private val serverSocket = ServerSocket(
        0,
        SERVER_BACKLOG,
        InetAddress.getByName(LOOPBACK_HOST),
    )
    private val executor = Executors.newSingleThreadExecutor()
    private val serverTask = executor.submit { serve() }

    val port: Int
        get() = serverSocket.localPort

    private fun serve() {
        while (!serverSocket.isClosed) {
            val socket = try {
                serverSocket.accept()
            } catch (exception: SocketException) {
                if (!serverSocket.isClosed) throw exception
                return
            }

            try {
                socket.use(::respond)
            } catch (_: IOException) {
                // Timeout and size-limit tests intentionally close responses early.
            }
        }
    }

    private fun respond(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MILLIS
        val reader = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
        val requestLine = reader.readLine() ?: return
        val requestParts = requestLine.split(' ', limit = 3)
        if (requestParts.size < 3) throw IOException("Malformed HTTP request line: $requestLine")

        val headers = buildMap {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    put(
                        line.substring(0, separator).trim().lowercase(Locale.ROOT),
                        line.substring(separator + 1).trim(),
                    )
                }
            }
        }
        val response = responseFor(
            RecordedRequest(
                method = requestParts[0],
                target = requestParts[1],
                headers = headers,
            ),
        )
        if (response.delayMillis > 0) Thread.sleep(response.delayMillis)

        val responseHead = buildString {
            append("HTTP/1.1 ${response.statusCode} ${response.reasonPhrase}\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            append("Connection: close\r\n")
            response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)

        socket.getOutputStream().use { output ->
            output.write(responseHead)
            output.write(response.body)
            output.flush()
        }
    }

    override fun close() {
        serverSocket.close()
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS)) {
                "HTTP server executor did not terminate"
            }
        }
        try {
            serverTask.get()
        } catch (exception: ExecutionException) {
            throw AssertionError("HTTP server failed", exception.cause)
        }
    }

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        private const val SERVER_BACKLOG = 4
        private const val SOCKET_TIMEOUT_MILLIS = 2_000
    }
}
