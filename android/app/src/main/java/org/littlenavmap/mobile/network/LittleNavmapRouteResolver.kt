/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.network

import java.util.Locale
import org.littlenavmap.mobile.model.FlightPlan
import org.littlenavmap.mobile.model.NavigationPoint
import org.littlenavmap.mobile.model.ServerProfile

/** Resolves a mobile plan against a connected Little Navmap database without modifying its desktop route. */
internal class LittleNavmapRouteResolver(
    private val airports: AirportInfoSource = AirportInfoClient(),
    private val mapFeatures: MapFeaturesSource = MapFeaturesClient(),
) {
    suspend fun resolve(profile: ServerProfile, plan: FlightPlan): FlightPlan {
        val existing = plan.navigationPoints.associateBy { it.identifier.key() }.toMutableMap()
        listOf(plan.origin, plan.destination)
            .filter(String::isNotBlank)
            .distinctBy(String::key)
            .forEach { airportIdentifier ->
                runCatching { airports.fetch(profile, airportIdentifier) }
                    .getOrNull()
                    ?.let { airport ->
                        val latitude = airport.latitude ?: return@let
                        val longitude = airport.longitude ?: return@let
                        existing[airportIdentifier.key()] = NavigationPoint(
                            identifier = airport.ident,
                            latitude = latitude,
                            longitude = longitude,
                            altitudeFeet = airport.elevationFeet?.toInt() ?: 0,
                            xPlaneType = NavigationPoint.XPLANE_AIRPORT_TYPE,
                        )
                    }
            }

        val endpoints = listOfNotNull(existing[plan.origin.key()], existing[plan.destination.key()])
        if (endpoints.size == 2) {
            val envelope = routeEnvelope(endpoints)
            runCatching { mapFeatures.fetch(profile, envelope) }.getOrDefault(emptyList()).forEach { candidate ->
                existing.putIfAbsent(candidate.identifier.key(), candidate)
            }
        }
        val ordered = listOf(plan.origin) + plan.waypoints + plan.destination
        return plan.copy(
            navigationPoints = ordered.mapNotNull { identifier -> existing[identifier.key()] },
        )
    }

    private fun routeEnvelope(endpoints: List<NavigationPoint>): MapFeatureBounds {
        val latitudePadding = maxOf(MINIMUM_PADDING_DEGREES, (endpoints.maxOf(NavigationPoint::latitude) - endpoints.minOf(NavigationPoint::latitude)) * PADDING_RATIO)
        val longitudePadding = maxOf(MINIMUM_PADDING_DEGREES, (endpoints.maxOf(NavigationPoint::longitude) - endpoints.minOf(NavigationPoint::longitude)) * PADDING_RATIO)
        return MapFeatureBounds(
            topLatitude = (endpoints.maxOf(NavigationPoint::latitude) + latitudePadding).coerceAtMost(90.0),
            bottomLatitude = (endpoints.minOf(NavigationPoint::latitude) - latitudePadding).coerceAtLeast(-90.0),
            leftLongitude = (endpoints.minOf(NavigationPoint::longitude) - longitudePadding).coerceAtLeast(-180.0),
            rightLongitude = (endpoints.maxOf(NavigationPoint::longitude) + longitudePadding).coerceAtMost(180.0),
        )
    }

    private companion object {
        const val MINIMUM_PADDING_DEGREES = 1.5
        const val PADDING_RATIO = 0.2
    }
}

private fun String.key(): String = trim().uppercase(Locale.ROOT)
