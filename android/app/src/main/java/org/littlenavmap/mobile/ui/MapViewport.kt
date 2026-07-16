/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.ui

import org.littlenavmap.mobile.model.NavigationPoint
import kotlin.math.max
import kotlin.math.min

internal data class MapViewport(
    val centerLongitude: Float = 0f,
    val centerLatitude: Float = 0f,
    val zoom: Float = DEFAULT_MAP_ZOOM,
) {
    fun zoomedBy(factor: Float): MapViewport = copy(zoom = (zoom * factor).coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM))

    fun pannedBy(deltaXPx: Float, deltaYPx: Float, pixelsPerDegree: Float): MapViewport {
        if (pixelsPerDegree <= 0f) return this
        return copy(
            centerLongitude = wrapLongitude(centerLongitude - deltaXPx / pixelsPerDegree),
            centerLatitude = (centerLatitude + deltaYPx / pixelsPerDegree).coerceIn(-MAX_CENTER_LATITUDE, MAX_CENTER_LATITUDE),
        )
    }
}

internal fun fitMapViewport(points: List<NavigationPoint>, widthPx: Float, heightPx: Float): MapViewport {
    if (points.isEmpty() || widthPx <= 0f || heightPx <= 0f) return MapViewport()

    val longitudes = unwrapLongitudes(points.map(NavigationPoint::longitude))
    val minLongitude = longitudes.minOrNull() ?: 0.0
    val maxLongitude = longitudes.maxOrNull() ?: 0.0
    val minLatitude = points.minOf(NavigationPoint::latitude).coerceIn(-MAX_DRAWABLE_LATITUDE.toDouble(), MAX_DRAWABLE_LATITUDE.toDouble())
    val maxLatitude = points.maxOf(NavigationPoint::latitude).coerceIn(-MAX_DRAWABLE_LATITUDE.toDouble(), MAX_DRAWABLE_LATITUDE.toDouble())
    val longitudeRange = max(maxLongitude - minLongitude, MIN_ROUTE_RANGE_DEGREES)
    val latitudeRange = max(maxLatitude - minLatitude, MIN_ROUTE_RANGE_DEGREES)
    val padding = 0.78f
    val zoomForWidth = widthPx * padding / (longitudeRange.toFloat() * basePixelsPerDegree(widthPx, heightPx))
    val zoomForHeight = heightPx * padding / (latitudeRange.toFloat() * basePixelsPerDegree(widthPx, heightPx))
    return MapViewport(
        centerLongitude = wrapLongitude(((minLongitude + maxLongitude) / 2.0).toFloat()),
        centerLatitude = ((minLatitude + maxLatitude) / 2.0).toFloat().coerceIn(-MAX_CENTER_LATITUDE, MAX_CENTER_LATITUDE),
        zoom = min(zoomForWidth, zoomForHeight).coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM),
    )
}

internal fun basePixelsPerDegree(widthPx: Float, heightPx: Float): Float =
    min(widthPx / WORLD_LONGITUDE_SPAN, heightPx / WORLD_LATITUDE_SPAN)

internal fun wrapLongitude(longitude: Float): Float {
    var wrapped = longitude % WORLD_LONGITUDE_SPAN
    if (wrapped > 180f) wrapped -= WORLD_LONGITUDE_SPAN
    if (wrapped <= -180f) wrapped += WORLD_LONGITUDE_SPAN
    return wrapped
}

internal fun longitudeDelta(longitude: Double, centerLongitude: Float): Float {
    val delta = longitude.toFloat() - centerLongitude
    return wrapLongitude(delta)
}

private fun unwrapLongitudes(longitudes: List<Double>): List<Double> {
    if (longitudes.size < 2) return longitudes
    val anchor = longitudes.first()
    return longitudes.map { longitude ->
        var unwrapped = longitude
        while (unwrapped - anchor > 180.0) unwrapped -= WORLD_LONGITUDE_SPAN
        while (unwrapped - anchor < -180.0) unwrapped += WORLD_LONGITUDE_SPAN
        unwrapped
    }
}

internal const val WORLD_LONGITUDE_SPAN = 360f
internal const val WORLD_LATITUDE_SPAN = 170f
internal const val MAX_DRAWABLE_LATITUDE = 85f
internal const val MAX_CENTER_LATITUDE = 75f
internal const val DEFAULT_MAP_ZOOM = 1f
internal const val MIN_MAP_ZOOM = 1f
internal const val MAX_MAP_ZOOM = 10f
private const val MIN_ROUTE_RANGE_DEGREES = 4.0
