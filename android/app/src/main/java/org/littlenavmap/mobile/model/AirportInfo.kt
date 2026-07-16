/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable
import java.util.Locale

@Serializable
internal data class AirportInfoResponse(
    val ident: String? = null,
    val icao: String? = null,
    val faa: String? = null,
    val local: String? = null,
    val iata: String? = null,
    val name: String? = null,
    val region: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val closed: Boolean? = null,
    val elevation: Double? = null,
    val magneticDeclination: Double? = null,
    val position: SimPosition? = null,
    val rating: Int? = null,
    val transitionAltitude: Double? = null,
    val facilities: List<String>? = null,
    val runways: List<String>? = null,
    val parking: Map<String, Int>? = null,
    val longestRunwayLength: Double? = null,
    val longestRunwayWidth: Double? = null,
    val longestRunwayHeading: String? = null,
    val longestRunwaySurface: String? = null,
    val metar: Map<String, AirportMetarSource?>? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val activeDateTime: String? = null,
    val activeDateTimeSource: String? = null,
    val com: Map<String, Long>? = null,
)

@Serializable
internal data class AirportMetarSource(
    val station: String? = null,
    val nearest: String? = null,
    val interpolated: String? = null,
)

internal data class AirportSnapshot(
    val ident: String,
    val icao: String?,
    val faa: String?,
    val local: String?,
    val iata: String?,
    val name: String,
    val region: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val closed: Boolean,
    val elevationFeet: Double?,
    val magneticDeclinationDegrees: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val rating: Int?,
    val transitionAltitudeFeet: Double?,
    val facilities: List<String>,
    val runwayFlags: List<String>,
    val parking: List<AirportParkingCount>,
    val longestRunwayLengthFeet: Double?,
    val longestRunwayWidthFeet: Double?,
    val longestRunwayHeading: String?,
    val longestRunwaySurfaceCode: String?,
    val weatherReports: List<AirportWeatherReport>,
    val sunriseUtc: String?,
    val sunsetUtc: String?,
    val activeDateTime: String?,
    val activeDateTimeSource: String?,
    val communications: List<AirportFrequency>,
) : JavaSerializable

internal data class AirportFrequency(
    val label: String,
    val megahertz: Double,
) : JavaSerializable

internal data class AirportParkingCount(
    val key: String,
    val count: Int,
) : JavaSerializable

internal data class AirportWeatherReport(
    val source: String,
    val kind: AirportWeatherKind,
    val text: String,
) : JavaSerializable

internal enum class AirportWeatherKind {
    Station,
    Nearest,
    Interpolated,
}

internal fun AirportInfoResponse.toAirportSnapshot(): AirportSnapshot? {
    val normalizedIdent = ident.clean(MAX_IDENT_CHARACTERS)?.uppercase(Locale.ROOT) ?: return null
    val reports = buildList {
        metar.orEmpty()
            .entries
            .asSequence()
            .mapNotNull { (source, value) ->
                val normalizedSource = source.clean(MAX_LABEL_CHARACTERS)
                if (value == null || normalizedSource == null) null else {
                    normalizedSource to value
                }
            }
            .asIterable()
            .prioritizedTake(
                priority = WEATHER_SOURCE_ORDER,
                maximumItems = MAX_WEATHER_SOURCES,
                key = { (source, _) -> source },
            )
            .forEach { (source, value) ->
                listOf(
                    AirportWeatherKind.Station to value.station,
                    AirportWeatherKind.Nearest to value.nearest,
                    AirportWeatherKind.Interpolated to value.interpolated,
                ).forEach { (kind, report) ->
                    report.clean(MAX_METAR_CHARACTERS)?.let { text ->
                        add(
                            AirportWeatherReport(
                                source = source,
                                kind = kind,
                                text = text,
                            ),
                        )
                    }
                }
            }
    }.distinctBy(AirportWeatherReport::text)

    return AirportSnapshot(
        ident = normalizedIdent,
        icao = icao.clean(MAX_IDENT_CHARACTERS),
        faa = faa.clean(MAX_IDENT_CHARACTERS),
        local = local.clean(MAX_IDENT_CHARACTERS),
        iata = iata.clean(MAX_IDENT_CHARACTERS),
        name = name.clean(MAX_NAME_CHARACTERS) ?: normalizedIdent,
        region = region.clean(MAX_LABEL_CHARACTERS),
        city = city.clean(MAX_NAME_CHARACTERS),
        state = state.clean(MAX_NAME_CHARACTERS),
        country = country.clean(MAX_NAME_CHARACTERS),
        closed = closed == true,
        elevationFeet = elevation.finiteOrNull(),
        magneticDeclinationDegrees = magneticDeclination.finiteOrNull(),
        latitude = position?.lat.finiteOrNull()?.takeIf { it in -90.0..90.0 },
        longitude = position?.lon.finiteOrNull()?.takeIf { it in -180.0..180.0 },
        rating = rating?.takeIf { it in MIN_RATING..MAX_RATING },
        transitionAltitudeFeet = transitionAltitude.positiveFiniteOrNull(),
        facilities = facilities.cleanDisplayList(),
        runwayFlags = runways.cleanDisplayList(),
        parking = parking.orEmpty().entries
            .asSequence()
            .mapNotNull { (key, count) ->
                val normalizedKey = key.clean(MAX_LABEL_CHARACTERS)
                if (count <= 0 || normalizedKey == null) null else {
                    AirportParkingCount(normalizedKey, count)
                }
            }
            .asIterable()
            .prioritizedTake(PARKING_ORDER, MAX_PARKING_ITEMS, AirportParkingCount::key),
        longestRunwayLengthFeet = longestRunwayLength.positiveFiniteOrNull(),
        longestRunwayWidthFeet = longestRunwayWidth.positiveFiniteOrNull(),
        longestRunwayHeading = longestRunwayHeading.clean(MAX_LABEL_CHARACTERS),
        longestRunwaySurfaceCode = longestRunwaySurface
            .clean(MAX_LABEL_CHARACTERS)
            ?.uppercase(Locale.ROOT),
        weatherReports = reports,
        sunriseUtc = sunrise.clean(MAX_LABEL_CHARACTERS),
        sunsetUtc = sunset.clean(MAX_LABEL_CHARACTERS),
        activeDateTime = activeDateTime.clean(MAX_NAME_CHARACTERS),
        activeDateTimeSource = activeDateTimeSource.clean(MAX_LABEL_CHARACTERS),
        communications = com.orEmpty().entries
            .asSequence()
            .mapNotNull { (rawLabel, rawFrequency) ->
                val label = rawLabel.trim().trimEnd(':').clean(MAX_LABEL_CHARACTERS)
                val frequency = rawComFrequencyToMhz(rawFrequency)
                if (label == null || frequency == null) null else AirportFrequency(label, frequency)
            }
            .asIterable()
            .prioritizedTake(
                COMMUNICATION_ORDER,
                MAX_COMMUNICATION_ITEMS,
                AirportFrequency::label,
            ),
    )
}

internal fun rawComFrequencyToMhz(rawFrequency: Long): Double? {
    if (rawFrequency <= 0) return null
    return if (rawFrequency > LARGE_FREQUENCY_THRESHOLD) {
        rawFrequency / 1_000_000.0
    } else {
        rawFrequency / 1_000.0
    }.takeIf(Double::isFinite)
}

private fun String?.clean(maxCharacters: Int): String? = this
    ?.trim()
    ?.take(maxCharacters)
    ?.takeIf { value -> value.isNotEmpty() && value.none(Char::isISOControl) }

private fun List<String>?.cleanDisplayList(): List<String> = orEmpty()
    .mapNotNull { it.clean(MAX_DISPLAY_ITEM_CHARACTERS) }
    .distinct()
    .take(MAX_DISPLAY_ITEMS)

private fun Double?.finiteOrNull(): Double? = this?.takeIf(Double::isFinite)

private fun Double?.positiveFiniteOrNull(): Double? = finiteOrNull()?.takeIf { it > 0.0 }

private fun <T : Any> Iterable<T>.prioritizedTake(
    priority: List<String>,
    maximumItems: Int,
    key: (T) -> String,
): List<T> {
    val preferred = MutableList<T?>(priority.size) { null }
    val fallback = ArrayList<T>(maximumItems)
    forEach { item ->
        val priorityIndex = priority.indexOfFirst { it.equals(key(item), ignoreCase = true) }
        if (priorityIndex >= 0) {
            if (preferred[priorityIndex] == null) preferred[priorityIndex] = item
        } else if (fallback.size < maximumItems) {
            fallback.add(item)
        }
    }

    return buildList(maximumItems) {
        preferred.forEach { item ->
            if (item != null && size < maximumItems) add(item)
        }
        fallback.forEach { item ->
            if (size < maximumItems) add(item)
        }
    }
}

private val WEATHER_SOURCE_ORDER = listOf("simulator", "noaa", "vatsim", "ivao", "activesky")
private val PARKING_ORDER = listOf(
    "gates",
    "jetWays",
    "gaRamps",
    "cargo",
    "militaryCargo",
    "militaryCombat",
    "helipads",
)
private val COMMUNICATION_ORDER = listOf("ATIS", "AWOS", "ASOS", "Tower", "UNICOM")
private const val LARGE_FREQUENCY_THRESHOLD = 10_000_000L
private const val MIN_RATING = 0
private const val MAX_RATING = 5
private const val MAX_IDENT_CHARACTERS = 16
private const val MAX_NAME_CHARACTERS = 160
private const val MAX_LABEL_CHARACTERS = 80
private const val MAX_METAR_CHARACTERS = 1_024
private const val MAX_DISPLAY_ITEM_CHARACTERS = 80
private const val MAX_DISPLAY_ITEMS = 48
private const val MAX_WEATHER_SOURCES = 16
private const val MAX_PARKING_ITEMS = 16
private const val MAX_COMMUNICATION_ITEMS = 32
