package org.littlenavmap.mobile.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class XPlaneRrefProtocolTest {
    @Test
    fun encodesLittleEndianRrefRequest() {
        val request = XPlaneRrefProtocol.request(XPlaneDataRef.Latitude)
        val buffer = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RREF\u0000", CharArray(5) { buffer.get().toInt().toChar() }.concatToString())
        assertEquals(1, buffer.int)
        assertEquals(XPlaneDataRef.Latitude.id, buffer.int)
    }

    @Test
    fun encodesZeroFrequencyToCancelSubscription() {
        val request = XPlaneRrefProtocol.request(XPlaneDataRef.WindSpeed, frequencyHz = 0)
        val buffer = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN)

        buffer.position(5)
        assertEquals(0, buffer.int)
        assertEquals(XPlaneDataRef.WindSpeed.id, buffer.int)
    }

    @Test
    fun decodesRrefResponseIntoSnapshot() {
        val response = ByteBuffer.allocate(5 + 8 * 3).order(ByteOrder.LITTLE_ENDIAN)
        response.put("RREF,".toByteArray())
        response.putInt(XPlaneDataRef.Latitude.id).putFloat(40.0801f)
        response.putInt(XPlaneDataRef.Longitude.id).putFloat(116.5846f)
        response.putInt(XPlaneDataRef.TrueHeading.id).putFloat(271.4f)

        val snapshot = XPlaneRrefProtocol.snapshot(XPlaneRrefProtocol.response(response.array(), response.position()))

        assertNotNull(snapshot)
        assertEquals(40.0801, snapshot!!.latitude, 0.0001)
        assertEquals(116.5846, snapshot.longitude, 0.0001)
        assertEquals(271.4f, snapshot.trueHeading)
    }
}
