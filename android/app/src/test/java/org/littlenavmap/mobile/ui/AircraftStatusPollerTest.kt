/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.model.SimInfoResponse
import org.littlenavmap.mobile.model.SimPosition
import org.littlenavmap.mobile.network.SimInfoSource

@OptIn(ExperimentalCoroutinesApi::class)
class AircraftStatusPollerTest {
    @Test
    fun `fetches immediately and then at fixed interval`() = runTest {
        val source = QueueSource(listOf(
            Result.success(activeResponse(groundSpeed = 101.0)),
            Result.success(activeResponse(groundSpeed = 102.0)),
        ))
        val states = mutableListOf<AircraftUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AircraftStatusPoller(
                source = source,
                pollIntervalMillis = 1_000,
                nowMillis = { 12_345L },
            ).states(PROFILE).collect(states::add)
        }

        runCurrent()
        assertEquals(1, source.callCount.get())
        assertEquals(AircraftUiState.Loading, states[0])
        assertEquals(101.0, (states[1] as AircraftUiState.Active).snapshot.groundSpeedKts)

        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, source.callCount.get())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, source.callCount.get())
        assertEquals(102.0, (states[2] as AircraftUiState.Active).snapshot.groundSpeedKts)
        assertEquals(12_345L, (states[2] as AircraftUiState.Active).snapshot.receivedAtMillis)

        job.cancelAndJoin()
    }

    @Test
    fun `keeps last valid snapshot while a request fails and recovers on success`() = runTest {
        val source = QueueSource(listOf(
            Result.success(activeResponse(groundSpeed = 110.0)),
            Result.failure(IOException("offline")),
            Result.success(activeResponse(groundSpeed = 120.0)),
        ))
        val states = mutableListOf<AircraftUiState>()
        var receivedAt = 100L
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AircraftStatusPoller(
                source = source,
                pollIntervalMillis = 1_000,
                nowMillis = { receivedAt++ },
            ).states(PROFILE).collect(states::add)
        }

        runCurrent()
        val first = (states.last() as AircraftUiState.Active).snapshot
        advanceTimeBy(1_000)
        runCurrent()
        val stale = states.last() as AircraftUiState.Stale
        assertSame(first, stale.snapshot)
        assertEquals(AircraftDataFailure.Connection, stale.failure)

        advanceTimeBy(1_000)
        runCurrent()
        val recovered = states.last() as AircraftUiState.Active
        assertEquals(120.0, recovered.snapshot.groundSpeedKts)
        assertEquals(101L, recovered.snapshot.receivedAtMillis)

        job.cancelAndJoin()
    }

    @Test
    fun `inactive simulator clears previous aircraft snapshot`() = runTest {
        val source = QueueSource(listOf(
            Result.success(activeResponse()),
            Result.success(SimInfoResponse(active = false)),
            Result.failure(IOException("offline")),
        ))
        val states = mutableListOf<AircraftUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AircraftStatusPoller(source, pollIntervalMillis = 1_000)
                .states(PROFILE)
                .collect(states::add)
        }

        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(AircraftUiState.SimulatorInactive, states.last())

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(
            AircraftUiState.Error(AircraftDataFailure.Connection),
            states.last(),
        )

        job.cancelAndJoin()
    }

    @Test
    fun `cancelling collection stops future requests`() = runTest {
        val source = QueueSource(listOf(
            Result.success(activeResponse()),
            Result.success(activeResponse()),
        ))
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AircraftStatusPoller(source, pollIntervalMillis = 1_000)
                .states(PROFILE)
                .collect()
        }

        runCurrent()
        assertEquals(1, source.callCount.get())
        job.cancelAndJoin()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, source.callCount.get())
    }

    @Test
    fun `cancellation from source is never converted to error state`() = runTest {
        val states = mutableListOf<AircraftUiState>()
        val source = SimInfoSource { throw CancellationException("stopped") }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AircraftStatusPoller(source).states(PROFILE).collect(states::add)
        }

        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(listOf(AircraftUiState.Loading), states)
    }

    private class QueueSource(results: List<Result<SimInfoResponse>>) : SimInfoSource {
        private val responses = ArrayDeque(results)
        val callCount = AtomicInteger()

        override suspend fun fetch(profile: ServerProfile): SimInfoResponse {
            callCount.incrementAndGet()
            return responses.removeFirst().getOrThrow()
        }
    }

    private companion object {
        val PROFILE = ServerProfile(host = "localhost")

        fun activeResponse(groundSpeed: Double = 105.0) = SimInfoResponse(
            active = true,
            simConnectStatus = "Connected",
            position = SimPosition(lat = 49.0, lon = -123.0),
            indicatedSpeed = 100.0,
            trueAirspeed = 108.0,
            groundSpeed = groundSpeed,
            verticalSpeed = 500.0,
            indicatedAltitude = 5_000.0,
            groundAltitude = 50.0,
            altitudeAboveGround = 4_950.0,
            heading = 180.0,
            windDirection = 270.0,
            windSpeed = 12.0,
            seaLevelPressure = 1_013.0,
        )
    }
}
