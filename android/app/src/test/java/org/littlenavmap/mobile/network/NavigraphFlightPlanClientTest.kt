package org.littlenavmap.mobile.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigraphFlightPlanClientTest {
    @Test
    fun rejectsNonNavigraphExportUrlsBeforeSendingToken() = runBlocking {
        val failure = runCatching {
            NavigraphFlightPlanClient().fetch(
                exportUrl = "https://example.invalid/flight-plan.fms",
                accessToken = "a".repeat(32),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("Use an HTTPS flight-plan export URL from Navigraph.", failure?.message)
    }
}
