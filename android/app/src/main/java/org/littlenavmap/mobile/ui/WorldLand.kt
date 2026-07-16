/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.ui

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class GeoCoordinate(val longitude: Double, val latitude: Double)

internal data class WorldLandPolygon(val rings: List<List<GeoCoordinate>>)

/** Loads the bundled, public-domain Natural Earth 1:110m land geometry. */
internal object WorldLand {
    fun load(context: Context): List<WorldLandPolygon> = runCatching {
        val root = context.assets.open("natural_earth_110m_land.geojson").bufferedReader().use { reader ->
            Json.parseToJsonElement(reader.readText()).jsonObject
        }
        root.getValue("features").jsonArray.flatMap { feature ->
            val geometry = feature.jsonObject.getValue("geometry").jsonObject
            when (geometry.getValue("type").jsonPrimitive.content) {
                "Polygon" -> listOf(WorldLandPolygon(parseRings(geometry.getValue("coordinates").jsonArray)))
                "MultiPolygon" -> geometry.getValue("coordinates").jsonArray.map { polygon ->
                    WorldLandPolygon(parseRings(polygon.jsonArray))
                }
                else -> emptyList()
            }
        }
    }.getOrElse { emptyList() }

    private fun parseRings(rings: JsonArray): List<List<GeoCoordinate>> = rings.map { ring ->
        ring.jsonArray.map { coordinate ->
            val pair = coordinate.jsonArray
            GeoCoordinate(pair[0].jsonPrimitive.content.toDouble(), pair[1].jsonPrimitive.content.toDouble())
        }
    }
}
