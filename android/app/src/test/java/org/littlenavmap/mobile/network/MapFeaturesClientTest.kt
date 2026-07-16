package org.littlenavmap.mobile.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littlenavmap.mobile.model.ServerProfile

class MapFeaturesClientTest {
    @Test
    fun parsesLittleNavmapAirportAndNavaidFeatures() = runBlocking {
        var requestedTarget = ""
        TestHttpServer(
            responseFor = { request ->
                requestedTarget = request.target
                mockResponse(
                    200,
                    "OK",
                    """
                    {
                      "airports":{"result":[{"ident":"ZBAA","elevation":116,"position":{"lat":40.0801,"lon":116.5846}}]},
                      "waypoints":{"result":[{"ident":"GITUM","position":{"lat":39.2000,"lon":117.1000}}]},
                      "vors":{"result":[{"ident":"HGH","position":{"lat":30.2200,"lon":120.4300}}]},
                      "ndbs":{"result":[]}
                    }
                    """.trimIndent(),
                )
            },
        ).use { server ->
            val points = MapFeaturesClient().fetch(
                profile = ServerProfile(host = TestHttpServer.LOOPBACK_HOST, port = server.port),
                bounds = MapFeatureBounds(42.0, 38.0, 115.0, 118.0),
            )

            assertTrue(requestedTarget.startsWith("/api/map/features?"))
            assertEquals(listOf("ZBAA", "GITUM", "HGH"), points.map { it.identifier })
            assertEquals(40.0801, points.first().latitude, 0.0001)
        }
    }
}
