/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirportInfoTest {
    @Test
    fun `normalizes colon suffixed COM labels and both frequency scales`() {
        val snapshot = AirportInfoResponse(
            ident = "KPDX",
            com = linkedMapOf(
                "Tower:" to 118_775L,
                "ATIS:" to 120_425_000L,
                "Unused:" to 0L,
            ),
        ).toAirportSnapshot()!!

        assertEquals(listOf("ATIS", "Tower"), snapshot.communications.map { it.label })
        assertEquals(120.425, snapshot.communications[0].megahertz, 0.000_001)
        assertEquals(118.775, snapshot.communications[1].megahertz, 0.000_001)
        assertEquals(118.775, rawComFrequencyToMhz(118_775L)!!, 0.000_001)
        assertEquals(120.425, rawComFrequencyToMhz(120_425_000L)!!, 0.000_001)
        assertNull(rawComFrequencyToMhz(0L))
    }

    @Test
    fun `filters empty METAR values and removes duplicate reports`() {
        val snapshot = AirportInfoResponse(
            ident = "KPDX",
            metar = linkedMapOf(
                "noaa" to AirportMetarSource(
                    station = "  KPDX SAME REPORT  ",
                    nearest = "KTTD UNIQUE NEAREST",
                ),
                "simulator" to AirportMetarSource(
                    station = "KPDX SAME REPORT",
                    nearest = "   ",
                    interpolated = "KPDX UNIQUE INTERPOLATED",
                ),
                "vatsim" to AirportMetarSource(station = ""),
                "ivao" to null,
            ),
        ).toAirportSnapshot()!!

        assertEquals(
            listOf(
                AirportWeatherReport(
                    source = "simulator",
                    kind = AirportWeatherKind.Station,
                    text = "KPDX SAME REPORT",
                ),
                AirportWeatherReport(
                    source = "simulator",
                    kind = AirportWeatherKind.Interpolated,
                    text = "KPDX UNIQUE INTERPOLATED",
                ),
                AirportWeatherReport(
                    source = "noaa",
                    kind = AirportWeatherKind.Nearest,
                    text = "KTTD UNIQUE NEAREST",
                ),
            ),
            snapshot.weatherReports,
        )
    }

    @Test
    fun `preserves translated display arrays and normalizes surface code`() {
        val translatedFacility = "\u6ed1\u884c\u9053"
        val translatedRunway = "\u5df2\u7167\u660e"
        val snapshot = AirportInfoResponse(
            ident = " kpdx ",
            name = " Portland International ",
            facilities = listOf(" Aprons ", translatedFacility, "Aprons", " "),
            runways = listOf("Hard", translatedRunway, translatedRunway),
            longestRunwayLength = 11_000.0,
            longestRunwayWidth = 150.0,
            longestRunwaySurface = " ce ",
        ).toAirportSnapshot()!!

        assertEquals("KPDX", snapshot.ident)
        assertEquals("Portland International", snapshot.name)
        assertEquals(listOf("Aprons", translatedFacility), snapshot.facilities)
        assertEquals(listOf("Hard", translatedRunway), snapshot.runwayFlags)
        assertEquals("CE", snapshot.longestRunwaySurfaceCode)
        assertEquals(11_000.0, snapshot.longestRunwayLengthFeet!!, 0.0)
        assertEquals(150.0, snapshot.longestRunwayWidthFeet!!, 0.0)
    }

    @Test
    fun `rejects response without a usable identifier`() {
        assertNull(AirportInfoResponse(ident = "   ").toAirportSnapshot())
        assertNull(AirportInfoResponse().toAirportSnapshot())
    }

    @Test
    fun `bounds collections and drops invalid weather sources`() {
        val snapshot = AirportInfoResponse(
            ident = "KPDX",
            metar = buildMap {
                put("   ", AirportMetarSource(station = "INVALID SOURCE"))
                repeat(40) { index ->
                    put("source-$index", AirportMetarSource(station = "REPORT $index"))
                }
                put("simulator", AirportMetarSource(station = "SIM REPORT"))
            },
            parking = buildMap {
                repeat(40) { index -> put("parking-$index", 1) }
                put("gates", 2)
            },
            com = buildMap {
                repeat(80) { index -> put("COM $index:", 118_000L + index) }
                put("ATIS:", 120_425_000L)
            },
        ).toAirportSnapshot()!!

        assertEquals(16, snapshot.weatherReports.size)
        assertEquals(16, snapshot.parking.size)
        assertEquals(32, snapshot.communications.size)
        assertEquals(false, snapshot.weatherReports.any { it.source.isBlank() })
        assertEquals("simulator", snapshot.weatherReports.first().source)
        assertEquals("gates", snapshot.parking.first().key)
        assertEquals("ATIS", snapshot.communications.first().label)
    }
}
