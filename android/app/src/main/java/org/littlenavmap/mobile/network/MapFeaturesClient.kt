/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.littlenavmap.mobile.model.NavigationPoint
import org.littlenavmap.mobile.model.ServerProfile

/** Fetches navaids from the Little Navmap database for a geographic route envelope. */
internal fun interface MapFeaturesSource {
    suspend fun fetch(profile: ServerProfile, bounds: MapFeatureBounds): List<NavigationPoint>
}

internal data class MapFeatureBounds(
    val topLatitude: Double,
    val bottomLatitude: Double,
    val leftLongitude: Double,
    val rightLongitude: Double,
) {
    init {
        require(topLatitude in -90.0..90.0 && bottomLatitude in -90.0..90.0)
        require(leftLongitude in -180.0..180.0 && rightLongitude in -180.0..180.0)
        require(topLatitude >= bottomLatitude && rightLongitude >= leftLongitude)
    }
}

internal class MapFeaturesClient(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : MapFeaturesSource {
    private val httpClient = HttpTextClient(
        ioDispatcher = ioDispatcher,
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis,
        maxResponseCharacters = MAX_RESPONSE_CHARACTERS,
    )

    override suspend fun fetch(profile: ServerProfile, bounds: MapFeatureBounds): List<NavigationPoint> {
        val response = httpClient.get(
            profile = profile,
            path = FEATURES_PATH,
            queryParameters = listOf(
                "toplat" to bounds.topLatitude.toString(),
                "bottomlat" to bounds.bottomLatitude.toString(),
                "leftlon" to bounds.leftLongitude.toString(),
                "rightlon" to bounds.rightLongitude.toString(),
                "detailfactor" to DETAIL_FACTOR.toString(),
            ),
        )
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Little Navmap returned HTTP ${response.statusCode} while resolving route points.")
        }
        val decoded = JSON.decodeFromString<MapFeaturesResponse>(response.body)
        return buildList {
            decoded.airports?.result.orEmpty().forEach { it.toNavigationPoint(NavigationPoint.XPLANE_AIRPORT_TYPE)?.let(::add) }
            decoded.waypoints?.result.orEmpty().forEach { it.toNavigationPoint(NavigationPoint.XPLANE_FIX_TYPE)?.let(::add) }
            decoded.vors?.result.orEmpty().forEach { it.toNavigationPoint(NavigationPoint.XPLANE_FIX_TYPE)?.let(::add) }
            decoded.ndbs?.result.orEmpty().forEach { it.toNavigationPoint(NavigationPoint.XPLANE_FIX_TYPE)?.let(::add) }
        }
    }

    private companion object {
        const val FEATURES_PATH = "api/map/features"
        const val DETAIL_FACTOR = 10
        const val DEFAULT_TIMEOUT_MILLIS = 5_000
        const val MAX_RESPONSE_CHARACTERS = 1_500_000
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class MapFeaturesResponse(
    val airports: MapFeatureGroup? = null,
    val waypoints: MapFeatureGroup? = null,
    val vors: MapFeatureGroup? = null,
    val ndbs: MapFeatureGroup? = null,
)

@Serializable
private data class MapFeatureGroup(
    val result: List<MapFeature> = emptyList(),
)

@Serializable
private data class MapFeature(
    val ident: String? = null,
    val elevation: Double? = null,
    val position: MapFeaturePosition? = null,
) {
    fun toNavigationPoint(type: Int): NavigationPoint? {
        val name = ident?.trim()?.uppercase()?.takeIf(String::isNotBlank) ?: return null
        val coordinates = position ?: return null
        val latitude = coordinates.lat ?: return null
        val longitude = coordinates.lon ?: return null
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return NavigationPoint(
            identifier = name,
            latitude = latitude,
            longitude = longitude,
            altitudeFeet = elevation?.toInt() ?: 0,
            xPlaneType = type,
        )
    }
}

@Serializable
private data class MapFeaturePosition(
    val lat: Double? = null,
    val lon: Double? = null,
)
