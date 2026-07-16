/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire representation returned by Little Navmap's /api/sim/info endpoint. */
@Serializable
internal data class SimInfoResponse(
    val active: Boolean,
    @SerialName("simconnect_status") val simConnectStatus: String? = null,
    val position: SimPosition? = null,
    @SerialName("indicated_speed") val indicatedSpeed: Double? = null,
    @SerialName("true_airspeed") val trueAirspeed: Double? = null,
    @SerialName("ground_speed") val groundSpeed: Double? = null,
    @SerialName("vertical_speed") val verticalSpeed: Double? = null,
    @SerialName("indicated_altitude") val indicatedAltitude: Double? = null,
    @SerialName("ground_altitude") val groundAltitude: Double? = null,
    @SerialName("altitude_above_ground") val altitudeAboveGround: Double? = null,
    val heading: Double? = null,
    @SerialName("wind_direction") val windDirection: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("sea_level_pressure") val seaLevelPressure: Double? = null,
)

@Serializable
internal data class SimPosition(
    val lat: Double? = null,
    val lon: Double? = null,
)

/** A locally timestamped, display-ready aircraft sample. */
internal data class AircraftSnapshot(
    val simConnectStatus: String?,
    val latitude: Double?,
    val longitude: Double?,
    val indicatedSpeedKts: Double?,
    val trueAirspeedKts: Double?,
    val groundSpeedKts: Double?,
    val verticalSpeedFeetPerMinute: Double?,
    val indicatedAltitudeFeet: Double?,
    val groundAltitudeFeet: Double?,
    val altitudeAboveGroundFeet: Double?,
    val magneticHeadingDegrees: Double?,
    val magneticWindDirectionDegrees: Double?,
    val windSpeedKts: Double?,
    val seaLevelPressureMbar: Double?,
    val receivedAtMillis: Long,
)

internal fun SimInfoResponse.toAircraftSnapshot(receivedAtMillis: Long): AircraftSnapshot =
    AircraftSnapshot(
        simConnectStatus = simConnectStatus
            ?.trim()
            ?.take(MAX_STATUS_CHARACTERS)
            ?.takeIf(String::isNotEmpty),
        latitude = position?.lat.finiteOrNull()?.takeIf { it in -90.0..90.0 },
        longitude = position?.lon.finiteOrNull()?.takeIf { it in -180.0..180.0 },
        indicatedSpeedKts = indicatedSpeed.finiteOrNull(),
        trueAirspeedKts = trueAirspeed.finiteOrNull(),
        groundSpeedKts = groundSpeed.finiteOrNull(),
        verticalSpeedFeetPerMinute = verticalSpeed.finiteOrNull(),
        indicatedAltitudeFeet = indicatedAltitude.finiteOrNull(),
        groundAltitudeFeet = groundAltitude.finiteOrNull(),
        altitudeAboveGroundFeet = altitudeAboveGround.finiteOrNull(),
        magneticHeadingDegrees = heading.normalizedDirectionOrNull(),
        magneticWindDirectionDegrees = windDirection.normalizedDirectionOrNull(),
        windSpeedKts = windSpeed.finiteOrNull(),
        seaLevelPressureMbar = seaLevelPressure.finiteOrNull(),
        receivedAtMillis = receivedAtMillis,
    )

private fun Double?.finiteOrNull(): Double? = this?.takeIf(Double::isFinite)

private fun Double?.normalizedDirectionOrNull(): Double? = finiteOrNull()?.let { value ->
    ((value % FULL_CIRCLE_DEGREES) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
}

private const val MAX_STATUS_CHARACTERS = 160
private const val FULL_CIRCLE_DEGREES = 360.0
