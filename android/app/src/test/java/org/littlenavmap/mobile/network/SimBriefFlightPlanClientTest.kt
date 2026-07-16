package org.littlenavmap.mobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimBriefFlightPlanClientTest {
    @Test
    fun decodesPublishedDispatchIntoEditableFlightPlan() {
        val plan = SimBriefFlightPlanClient().decode(
            """
            <OFP>
              <origin><icao_code>ZBAA</icao_code></origin>
              <destination><icao_code>ZSPD</icao_code></destination>
              <alternate><icao_code>ZSSS</icao_code></alternate>
              <general>
                <initial_altitude>35000</initial_altitude>
                <atc_route>ZBAA DCT GITUM A593 HONGQ ZSPD</atc_route>
                <sid>DAXING1A</sid>
                <star>SASAN2B</star>
              </general>
            </OFP>
            """.trimIndent(),
        )

        assertEquals("ZBAA", plan.origin)
        assertEquals("ZSPD", plan.destination)
        assertEquals("ZSSS", plan.alternate)
        assertEquals("FL350", plan.cruiseLevel)
        assertEquals("DAXING1A", plan.departureProcedure)
        assertEquals("SASAN2B", plan.arrivalProcedure)
        assertEquals(listOf("GITUM", "A593", "HONGQ"), plan.waypoints)
    }

    @Test
    fun rejectsExternalEntityPayload() {
        val failure = runCatching {
            SimBriefFlightPlanClient().decode(
                """<!DOCTYPE data [<!ENTITY xxe SYSTEM "file:///not-allowed">]><OFP/>""",
            )
        }.exceptionOrNull()

        assertTrue(failure != null)
    }
}
