package org.littlenavmap.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FlightPlanCodecTest {
    @Test
    fun jsonRoundTripPreservesPlan() {
        val plan = FlightPlan(
            origin = "ZBAA",
            destination = "ZSPD",
            alternate = "ZSSS",
            cruiseLevel = "FL340",
            departureProcedure = "DAXING 1A",
            arrivalProcedure = "LADIX 1A",
            approach = "ILS Z RWY 35L",
            waypoints = listOf("GITUM", "A593", "SASAN"),
        )

        assertEquals(plan, FlightPlanCodec.decode(FlightPlanCodec.encode(plan)))
    }

    @Test
    fun routeTextRoundTripPreservesRouteFields() {
        val plan = FlightPlan(
            origin = "ZBAA",
            destination = "ZSPD",
            alternate = "ZSSS",
            cruiseLevel = "FL340",
            departureProcedure = "DAXING1A",
            arrivalProcedure = "LADIX1A",
            approach = "ILS-Z-35L",
            waypoints = listOf("GITUM", "A593", "SASAN"),
        )

        assertEquals(plan, FlightPlanCodec.decodeRouteText(FlightPlanCodec.routeText(plan)))
    }

    @Test
    fun importsXPlaneFmsRoute() {
        val fms = """
            I
            1100 Version
            CYCLE 2607
            ADEP ZBAA
            ADES ZSPD
            NUMENR 3
            1 ZBAA 0.0 40.0800 116.5840
            11 GITUM 34000.0 39.0000 117.0000
            1 ZSPD 0.0 31.1430 121.8050
        """.trimIndent()

        val plan = FlightPlanCodec.decodeImported(fms)

        assertEquals("ZBAA", plan.origin)
        assertEquals("ZSPD", plan.destination)
        assertEquals(listOf("GITUM"), plan.waypoints)
        assertEquals("2607", plan.airacCycle)
        assertEquals(3, plan.navigationPoints.size)
        assertEquals(40.08, plan.navigationPoints.first().latitude, 0.0001)
        val exported = FlightPlanCodec.decodeImported(FlightPlanCodec.xPlaneFms(plan))
        assertEquals(plan.origin, exported.origin)
        assertEquals(plan.destination, exported.destination)
        assertEquals(plan.waypoints, exported.waypoints)
        assertEquals(plan.navigationPoints, exported.navigationPoints)
    }

    @Test
    fun importsLittleNavmapRoute() {
        val plan = """
            <?xml version="1.0" encoding="UTF-8"?>
            <LittleNavmap><Flightplan><Header><CruisingAlt>12000</CruisingAlt></Header>
            <Waypoints><Waypoint><Ident>EGHJ</Ident><Pos Lat="50.6798" Lon="-1.1094" Alt="28"/></Waypoint>
            <Waypoint><Ident>SAM</Ident><Pos Lat="50.9552" Lon="-1.3450" Alt="5694"/></Waypoint>
            <Waypoint><Ident>EGPC</Ident><Pos Lat="58.4589" Lon="-3.0930" Alt="126"/></Waypoint></Waypoints></Flightplan></LittleNavmap>
        """.trimIndent()

        val imported = FlightPlanCodec.decodeImported(plan)

        assertEquals("EGHJ", imported.origin)
        assertEquals("EGPC", imported.destination)
        assertEquals("12000", imported.cruiseLevel)
        assertEquals(listOf("SAM"), imported.waypoints)
        assertEquals(3, imported.navigationPoints.size)
        assertEquals(-3.093, imported.navigationPoints.last().longitude, 0.0001)
    }
}
