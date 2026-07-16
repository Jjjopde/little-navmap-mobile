/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.littlenavmap.mobile.R
import org.littlenavmap.mobile.model.AircraftSnapshot
import org.littlenavmap.mobile.ui.theme.LittleNavmapTheme

@RunWith(AndroidJUnit4::class)
class AircraftScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inactiveStateExplainsThatSimulatorDataIsUnavailable() {
        setState(AircraftUiState.SimulatorInactive)

        composeRule.onNodeWithText(text(R.string.simulator_inactive)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.simulator_inactive_detail)).assertIsDisplayed()
    }

    @Test
    fun activeStateShowsPrimaryAndSecondaryAircraftValues() {
        setState(AircraftUiState.Active(SNAPSHOT))

        composeRule.onNodeWithText(text(R.string.indicated_altitude)).assertIsDisplayed()
        composeRule.onNodeWithText("5420").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.ground_speed)).assertIsDisplayed()
        composeRule.onNodeWithText("115").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.indicated_air_speed)).assertIsDisplayed()
        composeRule.onNodeWithText("112").assertIsDisplayed()
    }

    @Test
    fun staleStateKeepsDataVisibleAndOffersImmediateRetry() {
        val retries = AtomicInteger()
        setState(
            state = AircraftUiState.Stale(
                snapshot = SNAPSHOT,
                failure = AircraftDataFailure.Connection,
            ),
            onRetry = retries::incrementAndGet,
        )

        composeRule.onNodeWithText(text(R.string.aircraft_data_stale)).assertIsDisplayed()
        composeRule.onNodeWithText("115").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(text(R.string.retry))
            .performClick()

        assertEquals(1, retries.get())
    }

    @Test
    fun errorStateOffersRetry() {
        val retries = AtomicInteger()
        setState(
            state = AircraftUiState.Error(AircraftDataFailure.Timeout),
            onRetry = retries::incrementAndGet,
        )

        composeRule.onNodeWithText(text(R.string.aircraft_data_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.retry)).performClick()
        assertEquals(1, retries.get())
    }

    private fun setState(
        state: AircraftUiState,
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            LittleNavmapTheme {
                AircraftStatusContent(state = state, onRetry = onRetry)
            }
        }
    }

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private companion object {
        val SNAPSHOT = AircraftSnapshot(
            simConnectStatus = "Connected",
            latitude = 49.1947,
            longitude = -123.1839,
            indicatedSpeedKts = 112.4,
            trueAirspeedKts = 118.7,
            groundSpeedKts = 115.2,
            verticalSpeedFeetPerMinute = 650.0,
            indicatedAltitudeFeet = 5_420.0,
            groundAltitudeFeet = 14.0,
            altitudeAboveGroundFeet = 5_406.0,
            magneticHeadingDegrees = 173.6,
            magneticWindDirectionDegrees = 245.0,
            windSpeedKts = 18.0,
            seaLevelPressureMbar = 1_013.2,
            receivedAtMillis = 1_700_000_000_000L,
        )
    }
}
