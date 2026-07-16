/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.littlenavmap.mobile.R
import org.littlenavmap.mobile.model.AirportInfoResponse
import org.littlenavmap.mobile.model.AirportMetarSource
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.model.toAirportSnapshot
import org.littlenavmap.mobile.ui.theme.LittleNavmapTheme

@RunWith(AndroidJUnit4::class)
class AirportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idleStatePromptsForAnAirportIdentifier() {
        setState(AirportUiState.Idle)

        composeRule.onNodeWithText(text(R.string.airport_search_prompt)).assertIsDisplayed()
    }

    @Test
    fun foundStateShowsAirportIdentityFrequencyAndMetar() {
        setState(AirportUiState.Found(KPDX))

        composeRule
            .onAllNodesWithText("KPDX", useUnmergedTree = true)[0]
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Portland International", useUnmergedTree = true)
            .assertIsDisplayed()

        val frequency = String.format(Locale.getDefault(), "%.3f", 118.775)
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(frequency, substring = true))
        composeRule
            .onNode(hasContentDescription(frequency, substring = true))
            .assertIsDisplayed()

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(METAR))
        composeRule.onNodeWithText(METAR, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun notFoundStateNamesTheRequestedAirport() {
        setState(AirportUiState.NotFound("KSEA"))

        composeRule
            .onNodeWithText(text(R.string.airport_not_found, "KSEA"))
            .assertIsDisplayed()
    }

    @Test
    fun errorStateExplainsFailureAndRetries() {
        val retries = AtomicInteger()
        setState(
            state = AirportUiState.Error("KPDX", AirportDataFailure.Timeout),
            onRetry = retries::incrementAndGet,
        )

        composeRule.onNodeWithText(text(R.string.airport_data_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.airport_error_timeout)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.retry)).performClick()

        assertEquals(1, retries.get())
    }

    @Test
    fun staleStateKeepsAirportVisibleAndOffersRetry() {
        val retries = AtomicInteger()
        setState(
            state = AirportUiState.Stale(
                ident = "KPDX",
                airport = KPDX,
                failure = AirportDataFailure.Connection,
            ),
            onRetry = retries::incrementAndGet,
        )

        composeRule.onNodeWithText("Portland International").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.airport_refresh_failed)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.retry)).performClick()
        assertEquals(1, retries.get())
    }

    @Test
    fun searchNormalizesInputAndSubmitsIdent() {
        val query = mutableStateOf("")
        val submitted = AtomicReference<String?>()
        composeRule.setContent {
            LittleNavmapTheme {
                AirportScreen(
                    profile = PROFILE,
                    query = query.value,
                    state = AirportUiState.Idle,
                    onQueryChanged = { query.value = it },
                    onSearch = submitted::set,
                    onRetry = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("kp dx!")
        composeRule.waitForIdle()
        assertEquals("KPDX", query.value)

        composeRule.onNodeWithText(text(R.string.airport_search_action)).performClick()

        assertEquals("KPDX", submitted.get())
    }

    private fun setState(
        state: AirportUiState,
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            LittleNavmapTheme {
                AirportStatusContent(state = state, onRetry = onRetry)
            }
        }
    }

    private fun text(resource: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(
            resource,
            *formatArgs,
        )

    private companion object {
        val PROFILE = ServerProfile(host = "localhost")
        const val METAR = "KPDX 161238Z 17008KT 10SM FEW025 SCT060 16/09 A3004"
        val KPDX = AirportInfoResponse(
            ident = "KPDX",
            name = "Portland International",
            city = "Portland",
            state = "Oregon",
            country = "United States",
            com = mapOf("Tower:" to 118_775L),
            metar = mapOf(
                "simulator" to AirportMetarSource(station = METAR),
            ),
        ).toAirportSnapshot()!!
    }
}
