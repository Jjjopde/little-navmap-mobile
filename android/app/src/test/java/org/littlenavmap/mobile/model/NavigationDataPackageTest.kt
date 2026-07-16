package org.littlenavmap.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDataPackageTest {
    private val data = NavigationDataPackage(
        cycle = "2607",
        airports = listOf(
            NavigationAirport(
                identifier = "ZBAA",
                latitude = 40.0801,
                longitude = 116.5846,
                elevationFeet = 116,
                sids = listOf("DAXING 1A"),
            ),
            NavigationAirport(
                identifier = "ZSPD",
                latitude = 31.1434,
                longitude = 121.8052,
                elevationFeet = 13,
                stars = listOf("LADIX 1A"),
                approaches = listOf("ILS Z RWY 35L"),
            ),
        ),
        fixes = listOf(
            NavigationPoint("GITUM", 39.0, 117.0, 34_000),
        ),
    )

    @Test
    fun resolvesManualRouteToCoordinates() {
        val resolved = data.resolve(
            FlightPlan(origin = "zbaa", destination = "zspd", waypoints = listOf("gitum")),
        )

        assertEquals("2607", resolved.airacCycle)
        assertEquals(listOf("ZBAA", "GITUM", "ZSPD"), resolved.navigationPoints.map { it.identifier })
        assertTrue(FlightPlanCodec.canExportXPlaneFms(resolved))
    }

    @Test
    fun exposesProceduresForRelevantAirport() {
        assertEquals(listOf("DAXING 1A"), data.procedures("ZBAA", ProcedureType.Sid))
        assertEquals(listOf("LADIX 1A"), data.procedures("ZSPD", ProcedureType.Star))
        assertEquals(listOf("ILS Z RWY 35L"), data.procedures("ZSPD", ProcedureType.Approach))
    }
}
