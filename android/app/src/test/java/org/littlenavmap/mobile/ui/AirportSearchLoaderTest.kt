/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littlenavmap.mobile.model.AirportInfoResponse
import org.littlenavmap.mobile.model.AirportSnapshot
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.model.toAirportSnapshot
import org.littlenavmap.mobile.network.AirportInfoHttpException
import org.littlenavmap.mobile.network.AirportInfoSource
import org.littlenavmap.mobile.network.AirportNotFoundException
import org.littlenavmap.mobile.network.InvalidAirportInfoResponseException

@OptIn(ExperimentalCoroutinesApi::class)
class AirportSearchLoaderTest {
    @Test
    fun `emits loading followed by found airport`() = runTest {
        val airport = airportSnapshot()
        val states = AirportSearchLoader(AirportInfoSource { _, _ -> airport })
            .states(PROFILE, "KPDX")
            .toList()

        assertEquals(
            listOf(
                AirportUiState.Loading("KPDX"),
                AirportUiState.Found(airport),
            ),
            states,
        )
    }

    @Test
    fun `maps airport not found separately from connection failures`() = runTest {
        val states = AirportSearchLoader(
            AirportInfoSource { _, ident -> throw AirportNotFoundException(ident) },
        ).states(PROFILE, "KSEA").toList()

        assertEquals(
            listOf(
                AirportUiState.Loading("KSEA"),
                AirportUiState.NotFound("KSEA"),
            ),
            states,
        )
    }

    @Test
    fun `maps typed failures to user states`() = runTest {
        val cases = listOf(
            SocketTimeoutException("slow") to AirportDataFailure.Timeout,
            UnknownHostException("missing") to AirportDataFailure.HostNotFound,
            ConnectException("refused") to AirportDataFailure.ServerUnavailable,
            NoRouteToHostException("unreachable") to AirportDataFailure.ServerUnavailable,
            SSLException("tls") to AirportDataFailure.SecureConnection,
            AirportInfoHttpException(503) to AirportDataFailure.Http(503),
            InvalidAirportInfoResponseException("bad json") to
                AirportDataFailure.InvalidResponse,
            IOException("disconnected") to AirportDataFailure.Connection,
            IllegalStateException("unexpected") to AirportDataFailure.InvalidResponse,
        )

        cases.forEach { (exception, expectedFailure) ->
            val states = AirportSearchLoader(
                AirportInfoSource { _, _ -> throw exception },
            ).states(PROFILE, "KPDX").toList()

            assertEquals(AirportUiState.Loading("KPDX"), states.first())
            assertEquals(AirportUiState.Error("KPDX", expectedFailure), states.last())
        }
    }

    @Test
    fun `cancelling collection cancels source without emitting error`() = runTest {
        val sourceStarted = CompletableDeferred<Unit>()
        val sourceCancelled = CompletableDeferred<Unit>()
        val states = mutableListOf<AirportUiState>()
        val source = AirportInfoSource { _, _ ->
            sourceStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                sourceCancelled.complete(Unit)
            }
        }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AirportSearchLoader(source).states(PROFILE, "KPDX").collect(states::add)
        }

        runCurrent()
        assertTrue(sourceStarted.isCompleted)
        assertEquals(listOf(AirportUiState.Loading("KPDX")), states)

        job.cancelAndJoin()

        assertTrue(sourceCancelled.isCompleted)
        assertEquals(listOf(AirportUiState.Loading("KPDX")), states)
    }

    @Test
    fun `normalizes valid ident and rejects invalid input`() {
        assertEquals("KPDX", normalizeAirportIdent("  kpdx ").getOrThrow())
        assertEquals("CY-AB", normalizeAirportIdent("cy-ab").getOrThrow())
        assertTrue(normalizeAirportIdent("A").isFailure)
        assertTrue(normalizeAirportIdent("机场").isFailure)
        assertTrue(normalizeAirportIdent("ＫＰＤＸ").isFailure)
        assertTrue(normalizeAirportIdent("K PDX").isFailure)
        assertTrue(normalizeAirportIdent("KPDX/27").isFailure)
    }

    private companion object {
        val PROFILE = ServerProfile(host = "localhost")

        fun airportSnapshot(): AirportSnapshot = AirportInfoResponse(
            ident = "KPDX",
            name = "Portland International",
        ).toAirportSnapshot()!!
    }
}
