/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebScriptsTest {
    @Test
    fun `web navigation scripts initialize and target their allowlisted buttons`() {
        val scripts = mapOf(
            "buttonMap" to WebScripts.CLICK_MAP,
            "buttonFlightPlan" to WebScripts.CLICK_FLIGHT_PLAN,
            "buttonProgress" to WebScripts.CLICK_PROGRESS,
        )

        scripts.forEach { (buttonId, script) ->
            assertTrue(script.contains("var targetId = '$buttonId';"))
        }
    }

    @Test
    fun `native destinations have no web navigation scripts`() {
        assertNull(PageDestination.Aircraft.clickScript)
        assertNull(PageDestination.Airports.clickScript)
    }

    @Test
    fun `mobile adaptation propagates pixel height only through full screen frames`() {
        val script = WebScripts.MOBILE_ADAPTATION

        assertTrue(script.contains("Math.max("))
        assertTrue(script.contains("doc.documentElement.style.minHeight = pixelHeight;"))
        assertTrue(script.contains("if (doc.body) doc.body.style.minHeight = pixelHeight;"))
        assertTrue(script.contains("frameId === 'webFrontend'"))
        assertTrue(script.contains("frameName === 'contentiframe'"))
        assertTrue(script.contains("frameName === 'addoniframe'"))
        assertFalse(script.contains("preventstandbyVideoContainer"))
    }

    @Test
    fun `mobile adaptation avoids selectors unsupported by early Android WebView`() {
        val script = WebScripts.MOBILE_ADAPTATION

        assertFalse(script.contains(":is("))
        assertTrue(script.contains("#appShell{display:block!important;height:100%!important;}"))
        assertTrue(script.contains("body.lnm-android-data-page #header button"))
    }

    @Test
    fun `mobile adaptation reapplies dimensions when the viewport changes`() {
        val script = WebScripts.MOBILE_ADAPTATION

        assertTrue(script.contains("window.addEventListener('resize'"))
        assertTrue(script.contains("window.addEventListener('orientationchange'"))
    }
}
