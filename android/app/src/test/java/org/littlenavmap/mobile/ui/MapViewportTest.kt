/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littlenavmap.mobile.model.NavigationPoint

class MapViewportTest {
    @Test
    fun `pan wraps longitude and clamps latitude`() {
        val panned = MapViewport(centerLongitude = 179f, centerLatitude = 74f)
            .pannedBy(deltaXPx = -4f, deltaYPx = 100f, pixelsPerDegree = 1f)

        assertEquals(-177f, panned.centerLongitude)
        assertEquals(MAX_CENTER_LATITUDE, panned.centerLatitude)
    }

    @Test
    fun `zoom is kept within supported limits`() {
        assertEquals(MIN_MAP_ZOOM, MapViewport().zoomedBy(0.1f).zoom)
        assertEquals(MAX_MAP_ZOOM, MapViewport().zoomedBy(100f).zoom)
    }

    @Test
    fun `fits a dateline route around the pacific`() {
        val viewport = fitMapViewport(
            points = listOf(point("NWWW", 12.0, 178.0), point("NSTU", -14.0, -171.0)),
            widthPx = 1080f,
            heightPx = 720f,
        )

        assertTrue(kotlin.math.abs(kotlin.math.abs(viewport.centerLongitude) - 180f) < 8f)
        assertTrue(viewport.zoom > MIN_MAP_ZOOM)
    }

    private fun point(identifier: String, latitude: Double, longitude: Double) =
        NavigationPoint(identifier = identifier, latitude = latitude, longitude = longitude)
}
