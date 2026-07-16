/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.littlenavmap.mobile.model.XPlaneSnapshot

/** Reads X-Plane DataRef values through the documented UDP RREF protocol. */
internal class XPlaneRrefClient(
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    suspend fun read(host: String, port: Int): XPlaneSnapshot = withContext(Dispatchers.IO) {
        require(port in MIN_PORT..MAX_PORT) { "X-Plane UDP port is invalid." }
        val address = InetAddress.getByName(host)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMillis
            DATA_REFS.forEach { reference ->
                val request = XPlaneRrefProtocol.request(reference)
                socket.send(DatagramPacket(request, request.size, address, port))
            }

            val values = mutableMapOf<Int, Float>()
            val deadline = System.nanoTime() + timeoutMillis * NANOS_PER_MILLISECOND
            val packet = DatagramPacket(ByteArray(MAX_RESPONSE_BYTES), MAX_RESPONSE_BYTES)
            while (System.nanoTime() < deadline && values.keys.containsAll(REQUIRED_DATA_REF_IDS).not()) {
                try {
                    socket.receive(packet)
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
                values.putAll(XPlaneRrefProtocol.response(packet.data, packet.length))
            }
            XPlaneRrefProtocol.snapshot(values)
                ?: throw IllegalStateException("X-Plane did not return position data. Enable UDP dataref access and check the address.")
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 2_500
        const val MAX_RESPONSE_BYTES = 4_096
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535
        const val NANOS_PER_MILLISECOND = 1_000_000L
        val REQUIRED_DATA_REF_IDS = setOf(XPlaneDataRef.Latitude.id, XPlaneDataRef.Longitude.id)
        val DATA_REFS = XPlaneDataRef.entries.toList()
    }
}

internal enum class XPlaneDataRef(val id: Int, val path: String) {
    Latitude(1, "sim/flightmodel/position/latitude"),
    Longitude(2, "sim/flightmodel/position/longitude"),
    Elevation(3, "sim/flightmodel/position/elevation"),
    GroundSpeed(4, "sim/flightmodel/position/groundspeed"),
    TrueHeading(5, "sim/flightmodel/position/true_psi"),
    IndicatedAirspeed(6, "sim/cockpit2/gauges/indicators/airspeed_kts_pilot"),
    VerticalSpeed(7, "sim/cockpit2/gauges/indicators/vvi_fpm_pilot"),
    WindDirection(8, "sim/weather/wind_direction_degt"),
    WindSpeed(9, "sim/weather/wind_speed_kt"),
}

internal object XPlaneRrefProtocol {
    private val requestHeader = "RREF\u0000".toByteArray(StandardCharsets.US_ASCII)
    private val responseHeader = "RREF,".toByteArray(StandardCharsets.US_ASCII)

    fun request(reference: XPlaneDataRef): ByteArray = ByteBuffer
        .allocate(REQUEST_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put(requestHeader)
        .putInt(REQUEST_FREQUENCY_HZ)
        .putInt(reference.id)
        .put(reference.path.toByteArray(StandardCharsets.US_ASCII).copyOf(DATAREF_NAME_BYTES))
        .array()

    fun response(bytes: ByteArray, length: Int): Map<Int, Float> {
        if (length < RESPONSE_HEADER_BYTES || !bytes.copyOfRange(0, RESPONSE_HEADER_BYTES).contentEquals(responseHeader)) {
            return emptyMap()
        }
        val buffer = ByteBuffer.wrap(bytes, RESPONSE_HEADER_BYTES, length - RESPONSE_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        return buildMap {
            while (buffer.remaining() >= RESPONSE_ENTRY_BYTES) {
                put(buffer.int, buffer.float)
            }
        }
    }

    fun snapshot(values: Map<Int, Float>): XPlaneSnapshot? {
        val latitude = values[XPlaneDataRef.Latitude.id]?.toDouble() ?: return null
        val longitude = values[XPlaneDataRef.Longitude.id]?.toDouble() ?: return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        return XPlaneSnapshot(
            latitude = latitude,
            longitude = longitude,
            elevationMeters = values[XPlaneDataRef.Elevation.id],
            groundSpeedMetersPerSecond = values[XPlaneDataRef.GroundSpeed.id],
            trueHeading = values[XPlaneDataRef.TrueHeading.id],
            indicatedAirspeedKnots = values[XPlaneDataRef.IndicatedAirspeed.id],
            verticalSpeedFeetPerMinute = values[XPlaneDataRef.VerticalSpeed.id],
            windDirectionDegrees = values[XPlaneDataRef.WindDirection.id],
            windSpeedKnots = values[XPlaneDataRef.WindSpeed.id],
        )
    }

    private const val REQUEST_FREQUENCY_HZ = 1
    private const val DATAREF_NAME_BYTES = 400
    private const val REQUEST_BYTES = 5 + 4 + 4 + DATAREF_NAME_BYTES
    private const val RESPONSE_HEADER_BYTES = 5
    private const val RESPONSE_ENTRY_BYTES = 8
}
