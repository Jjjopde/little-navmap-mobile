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

        assertEquals(
            FlightPlan(origin = "ZBAA", destination = "ZSPD", waypoints = listOf("GITUM")),
            FlightPlanCodec.decodeImported(fms),
        )
    }

    @Test
    fun importsLittleNavmapRoute() {
        val plan = """
            <?xml version="1.0" encoding="UTF-8"?>
            <LittleNavmap><Flightplan><Header><CruisingAlt>12000</CruisingAlt></Header>
            <Waypoints><Waypoint><Ident>EGHJ</Ident></Waypoint><Waypoint><Ident>SAM</Ident></Waypoint>
            <Waypoint><Ident>EGPC</Ident></Waypoint></Waypoints></Flightplan></LittleNavmap>
        """.trimIndent()

        assertEquals(
            FlightPlan(origin = "EGHJ", destination = "EGPC", cruiseLevel = "12000", waypoints = listOf("SAM")),
            FlightPlanCodec.decodeImported(plan),
        )
    }
}
