/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.model

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Portable navigation data consumed by the mobile planner. */
@Serializable
data class NavigationDataPackage(
    val cycle: String,
    val airports: List<NavigationAirport> = emptyList(),
    val fixes: List<NavigationPoint> = emptyList(),
) {
    init {
        require(cycle.matches(Regex("[0-9]{4}"))) { "AIRAC cycle must use four digits." }
    }

    fun airport(identifier: String): NavigationAirport? =
        airports.firstOrNull { it.identifier.equals(identifier.trim(), ignoreCase = true) }

    fun resolve(plan: FlightPlan): FlightPlan {
        val dataPoints = buildMap {
            airports.forEach { airport ->
                put(
                    airport.identifier.normalizedIdentifier(),
                    NavigationPoint(
                        identifier = airport.identifier,
                        latitude = airport.latitude,
                        longitude = airport.longitude,
                        altitudeFeet = airport.elevationFeet,
                        xPlaneType = NavigationPoint.XPLANE_AIRPORT_TYPE,
                    ),
                )
            }
            fixes.forEach { point -> put(point.identifier.normalizedIdentifier(), point) }
        }
        val existingPoints = plan.navigationPoints.associateBy { it.identifier.normalizedIdentifier() }
        val route = listOf(plan.origin) + plan.waypoints + plan.destination
        val resolved = route.mapNotNull { identifier ->
            dataPoints[identifier.normalizedIdentifier()] ?: existingPoints[identifier.normalizedIdentifier()]
        }
        return plan.copy(navigationPoints = resolved, airacCycle = cycle)
    }

    fun procedures(identifier: String, type: ProcedureType): List<String> = airport(identifier)?.let { airport ->
        when (type) {
            ProcedureType.Sid -> airport.sids
            ProcedureType.Star -> airport.stars
            ProcedureType.Approach -> airport.approaches
        }
    }.orEmpty()
}

@Serializable
data class NavigationAirport(
    val identifier: String,
    val latitude: Double,
    val longitude: Double,
    val elevationFeet: Int = 0,
    val sids: List<String> = emptyList(),
    val stars: List<String> = emptyList(),
    val approaches: List<String> = emptyList(),
)

enum class ProcedureType {
    Sid,
    Star,
    Approach,
}

object NavigationDataCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun decode(content: String): NavigationDataPackage =
        json.decodeFromString(NavigationDataPackage.serializer(), content.trim())

    fun encode(data: NavigationDataPackage): String =
        json.encodeToString(NavigationDataPackage.serializer(), data)
}

private fun String.normalizedIdentifier(): String = trim().uppercase(Locale.ROOT)
