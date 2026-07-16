/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.littlenavmap.mobile.model.FlightPlan
import org.littlenavmap.mobile.model.FlightPlanCodec
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.model.XPlaneEndpoint

/** Persists connection settings without retaining credentials or response data. */
class PreferencesRepository(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadProfile(): ServerProfile? {
        val profile = try {
            val host = preferences.getString(KEY_HOST, null)?.takeIf { it.isNotBlank() }
                ?: return null
            ServerProfile(
                scheme = preferences.getString(
                    KEY_SCHEME,
                    ServerProfile.DEFAULT_SCHEME,
                ) ?: ServerProfile.DEFAULT_SCHEME,
                host = host,
                port = preferences.getInt(KEY_PORT, ServerProfile.DEFAULT_PORT),
            ).normalized
        } catch (_: ClassCastException) {
            return null
        }
        return profile.takeIf { it.validate() == null }
    }

    fun saveProfile(profile: ServerProfile) {
        val validationError = profile.validate()
        require(validationError == null) { validationError.orEmpty() }

        val value = profile.normalized
        preferences.edit {
            putString(KEY_SCHEME, value.scheme)
            putString(KEY_HOST, value.host)
            putInt(KEY_PORT, value.port)
        }
    }

    fun clearProfile() {
        preferences.edit {
            remove(KEY_SCHEME)
            remove(KEY_HOST)
            remove(KEY_PORT)
        }
    }

    fun keepScreenOn(): Boolean = try {
        preferences.getBoolean(KEY_KEEP_SCREEN_ON, true)
    } catch (_: ClassCastException) {
        true
    }

    fun setKeepScreenOn(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }
    }

    fun loadXPlaneEndpoint(): XPlaneEndpoint? = try {
        val host = preferences.getString(KEY_XPLANE_HOST, null)?.takeIf { it.isNotBlank() } ?: return null
        XPlaneEndpoint(
            host = host,
            port = preferences.getInt(KEY_XPLANE_PORT, XPlaneEndpoint.DEFAULT_PORT),
        ).takeIf { it.validate() == null }
    } catch (_: ClassCastException) {
        null
    }

    fun saveXPlaneEndpoint(endpoint: XPlaneEndpoint) {
        val validationError = endpoint.validate()
        require(validationError == null) { validationError.orEmpty() }
        preferences.edit {
            putString(KEY_XPLANE_HOST, endpoint.host)
            putInt(KEY_XPLANE_PORT, endpoint.port)
        }
    }

    fun loadFlightPlan(): FlightPlan = runCatching {
        val content = preferences.getString(KEY_FLIGHT_PLAN, null) ?: return FlightPlan()
        FlightPlanCodec.decode(content)
    }.getOrDefault(FlightPlan())

    fun saveFlightPlan(plan: FlightPlan) {
        preferences.edit { putString(KEY_FLIGHT_PLAN, FlightPlanCodec.encode(plan)) }
    }

    private companion object {
        const val PREFERENCES_NAME = "little_navmap_mobile"
        const val KEY_SCHEME = "server_scheme"
        const val KEY_HOST = "server_host"
        const val KEY_PORT = "server_port"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_FLIGHT_PLAN = "flight_plan"
        const val KEY_XPLANE_HOST = "xplane_host"
        const val KEY_XPLANE_PORT = "xplane_port"
    }
}
