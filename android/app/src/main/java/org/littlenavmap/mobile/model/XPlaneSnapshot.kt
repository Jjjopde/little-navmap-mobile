/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.model

data class XPlaneSnapshot(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Float?,
    val groundSpeedMetersPerSecond: Float?,
    val trueHeading: Float?,
    val indicatedAirspeedKnots: Float?,
    val verticalSpeedFeetPerMinute: Float?,
    val windDirectionDegrees: Float?,
    val windSpeedKnots: Float?,
)
