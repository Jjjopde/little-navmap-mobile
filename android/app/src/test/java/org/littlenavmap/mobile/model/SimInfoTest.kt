/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimInfoTest {
    @Test
    fun `snapshot validates coordinates and non-finite measurements`() {
        val snapshot = SimInfoResponse(
            active = true,
            position = SimPosition(lat = 91.0, lon = -123.25),
            groundSpeed = Double.POSITIVE_INFINITY,
            indicatedAltitude = Double.NaN,
        ).toAircraftSnapshot(receivedAtMillis = 42L)

        assertNull(snapshot.latitude)
        assertEquals(-123.25, snapshot.longitude ?: Double.NaN, 0.0001)
        assertNull(snapshot.groundSpeedKts)
        assertNull(snapshot.indicatedAltitudeFeet)
        assertEquals(42L, snapshot.receivedAtMillis)
    }

    @Test
    fun `snapshot normalizes magnetic directions`() {
        val snapshot = SimInfoResponse(
            active = true,
            heading = -10.0,
            windDirection = 721.0,
        ).toAircraftSnapshot(receivedAtMillis = 1L)

        assertEquals(350.0, snapshot.magneticHeadingDegrees ?: Double.NaN, 0.0001)
        assertEquals(1.0, snapshot.magneticWindDirectionDegrees ?: Double.NaN, 0.0001)
    }

    @Test
    fun `snapshot trims and bounds server status text`() {
        val snapshot = SimInfoResponse(
            active = true,
            simConnectStatus = "  " + "x".repeat(200) + "  ",
        ).toAircraftSnapshot(receivedAtMillis = 1L)

        assertEquals(160, snapshot.simConnectStatus?.length)
        assertTrue(snapshot.simConnectStatus.orEmpty().all { it == 'x' })
    }
}
