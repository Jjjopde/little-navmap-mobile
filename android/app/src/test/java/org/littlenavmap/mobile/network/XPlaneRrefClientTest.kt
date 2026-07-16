/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XPlaneRrefClientTest {
    @Test
    fun `reads UDP position and cancels subscriptions`() = runBlocking {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = SOCKET_TIMEOUT_MILLIS
            val executor = Executors.newSingleThreadExecutor()
            try {
                val serverTask = executor.submit<List<RrefRequest>> {
                    val subscriptions = receiveRequests(server, XPlaneDataRef.entries.size)
                    val client = subscriptions.first().sender
                    sendPosition(server, client)
                    subscriptions + receiveRequests(server, XPlaneDataRef.entries.size)
                }

                val snapshot = XPlaneRrefClient(timeoutMillis = SOCKET_TIMEOUT_MILLIS).read(
                    host = LOOPBACK_HOST,
                    port = server.localPort,
                )
                val requests = serverTask.get(SOCKET_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)

                assertEquals(40.0801, snapshot.latitude, 0.0001)
                assertEquals(116.5846, snapshot.longitude, 0.0001)
                assertEquals(18, requests.size)
                assertEquals(
                    XPlaneDataRef.entries.map { it.id }.toSet(),
                    requests.take(XPlaneDataRef.entries.size).map { it.dataRefId }.toSet(),
                )
                assertTrue(requests.take(XPlaneDataRef.entries.size).all { it.frequencyHz == 1 })
                assertEquals(
                    XPlaneDataRef.entries.map { it.id }.toSet(),
                    requests.drop(XPlaneDataRef.entries.size).map { it.dataRefId }.toSet(),
                )
                assertTrue(requests.drop(XPlaneDataRef.entries.size).all { it.frequencyHz == 0 })
            } finally {
                executor.shutdownNow()
                check(executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    "UDP test server executor did not terminate"
                }
            }
        }
    }

    private fun receiveRequests(server: DatagramSocket, count: Int): List<RrefRequest> =
        List(count) {
            val packet = DatagramPacket(ByteArray(MAX_PACKET_BYTES), MAX_PACKET_BYTES)
            server.receive(packet)
            val buffer = ByteBuffer.wrap(packet.data, packet.offset, packet.length).order(ByteOrder.LITTLE_ENDIAN)
            check(CharArray(5) { buffer.get().toInt().toChar() }.concatToString() == "RREF\u0000")
            RrefRequest(
                frequencyHz = buffer.int,
                dataRefId = buffer.int,
                sender = InetSocketAddress(packet.address, packet.port),
            )
        }

    private fun sendPosition(server: DatagramSocket, client: InetSocketAddress) {
        val bytes = ByteBuffer.allocate(5 + 16).order(ByteOrder.LITTLE_ENDIAN)
            .put("RREF,".toByteArray())
            .putInt(XPlaneDataRef.Latitude.id)
            .putFloat(40.0801f)
            .putInt(XPlaneDataRef.Longitude.id)
            .putFloat(116.5846f)
            .array()
        server.send(DatagramPacket(bytes, bytes.size, client))
    }

    private data class RrefRequest(
        val frequencyHz: Int,
        val dataRefId: Int,
        val sender: InetSocketAddress,
    )

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val SOCKET_TIMEOUT_MILLIS = 2_000
        const val MAX_PACKET_BYTES = 1_024
    }
}
