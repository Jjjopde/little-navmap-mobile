/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

@Serializable
data class NavigationPoint(
    val identifier: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeFeet: Int = 0,
    val xPlaneType: Int = XPLANE_FIX_TYPE,
) {
    companion object {
        const val XPLANE_AIRPORT_TYPE = 1
        const val XPLANE_FIX_TYPE = 11
    }
}

/** A portable, local-first flight plan with optional resolved navigation coordinates. */
@Serializable
data class FlightPlan(
    val origin: String = "",
    val destination: String = "",
    val alternate: String = "",
    val cruiseLevel: String = "FL340",
    val departureProcedure: String = "",
    val arrivalProcedure: String = "",
    val approach: String = "",
    val waypoints: List<String> = emptyList(),
    val navigationPoints: List<NavigationPoint> = emptyList(),
    val airacCycle: String = "",
)

object FlightPlanCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(plan: FlightPlan): String = json.encodeToString(FlightPlan.serializer(), plan)

    fun decode(content: String): FlightPlan = json.decodeFromString(FlightPlan.serializer(), content)

    fun decodeImported(content: String): FlightPlan {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("{") -> decode(trimmed)
            trimmed.startsWith("<?xml") || trimmed.startsWith("<LittleNavmap") -> decodeLittleNavmap(trimmed)
            trimmed.lineSequence().map(String::trim).any { it.endsWith(" Version") } -> decodeXPlaneFms(trimmed)
            else -> decodeRouteText(trimmed)
        }
    }

    fun routeText(plan: FlightPlan): String = buildString {
        appendLine("LNM MOBILE ROUTE")
        appendLine("FROM ${plan.origin}")
        appendLine("TO ${plan.destination}")
        if (plan.alternate.isNotBlank()) appendLine("ALTN ${plan.alternate}")
        appendLine("CRUISE ${plan.cruiseLevel}")
        if (plan.departureProcedure.isNotBlank()) appendLine("SID ${plan.departureProcedure}")
        if (plan.waypoints.isNotEmpty()) appendLine("ROUTE ${plan.waypoints.joinToString(" ")}")
        if (plan.arrivalProcedure.isNotBlank()) appendLine("STAR ${plan.arrivalProcedure}")
        if (plan.approach.isNotBlank()) appendLine("APP ${plan.approach}")
    }

    fun canExportXPlaneFms(plan: FlightPlan): Boolean = resolvedPoints(plan) != null

    fun xPlaneFms(plan: FlightPlan): String {
        val points = requireNotNull(resolvedPoints(plan)) {
            "X-Plane export needs coordinates for every airport and waypoint. Import an LNM/FMS plan or resolve the route in Little Navmap first."
        }
        return buildString {
            appendLine("I")
            appendLine("1100 Version")
            plan.airacCycle.takeIf(String::isNotBlank)?.let { appendLine("CYCLE $it") }
            appendLine("ADEP ${plan.origin}")
            appendLine("ADES ${plan.destination}")
            appendLine("NUMENR ${points.size}")
            points.forEachIndexed { index, point ->
                val type = when (index) {
                    0, points.lastIndex -> NavigationPoint.XPLANE_AIRPORT_TYPE
                    else -> point.xPlaneType
                }
                appendLine(
                    "%d %s %d %.6f %.6f".format(
                        Locale.US,
                        type,
                        point.identifier,
                        point.altitudeFeet,
                        point.latitude,
                        point.longitude,
                    ),
                )
            }
        }
    }

    fun decodeRouteText(content: String): FlightPlan {
        val fields = content.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .associate { line ->
                val split = line.indexOf(' ')
                if (split < 0) line to "" else line.substring(0, split) to line.substring(split + 1).trim()
            }
        return FlightPlan(
            origin = fields["FROM"].orEmpty(),
            destination = fields["TO"].orEmpty(),
            alternate = fields["ALTN"].orEmpty(),
            cruiseLevel = fields["CRUISE"].orEmpty().ifBlank { "FL340" },
            departureProcedure = fields["SID"].orEmpty(),
            arrivalProcedure = fields["STAR"].orEmpty(),
            approach = fields["APP"].orEmpty(),
            waypoints = fields["ROUTE"].orEmpty().split(Regex("\\s+")).filter(String::isNotBlank),
        )
    }

    private fun decodeXPlaneFms(content: String): FlightPlan {
        val values = content.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        require(values.any { it.endsWith(" Version") }) { "Not an X-Plane FMS file." }
        val header = values.associate { line ->
            val split = line.indexOf(' ')
            if (split < 0) line to "" else line.substring(0, split) to line.substring(split + 1).trim()
        }
        val waypointEntries = values.mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            parts.getOrNull(0)?.toIntOrNull()?.takeIf {
                parts.size >= FMS_WAYPOINT_FIELD_COUNT &&
                    parts[2].toDoubleOrNull() != null &&
                    parts[3].toDoubleOrNull() != null &&
                    parts[4].toDoubleOrNull() != null
            }?.let { type ->
                parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { ident ->
                    NavigationPoint(
                        identifier = ident,
                        altitudeFeet = parts[2].toDouble().toInt(),
                        latitude = parts[3].toDouble(),
                        longitude = parts[4].toDouble(),
                        xPlaneType = type,
                    )
                }
            }
        }
        val identifiers = waypointEntries.map(NavigationPoint::identifier)
        require(identifiers.size >= 2) { "The X-Plane FMS file has no usable route." }
        return FlightPlan(
            origin = header["ADEP"].orEmpty().ifBlank { identifiers.first() },
            destination = header["ADES"].orEmpty().ifBlank { identifiers.last() },
            waypoints = identifiers.drop(1).dropLast(1),
            navigationPoints = waypointEntries,
            airacCycle = header["CYCLE"].orEmpty(),
        )
    }

    private fun decodeLittleNavmap(content: String): FlightPlan {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(content)))
        val nodes = document.getElementsByTagName("Waypoint")
        val navigationPoints = buildList {
            for (index in 0 until nodes.length) {
                val waypoint = nodes.item(index)
                waypoint.asNavigationPoint()?.let(::add)
            }
        }
        val identifiers = navigationPoints.map(NavigationPoint::identifier)
        require(identifiers.size >= 2) { "The Little Navmap plan has no usable route." }
        return FlightPlan(
            origin = identifiers.first(),
            destination = identifiers.last(),
            cruiseLevel = document.textOfFirst("CruisingAlt").ifBlank { "FL340" },
            approach = document.procedureName("Approach"),
            departureProcedure = document.procedureName("Sid"),
            arrivalProcedure = document.procedureName("Star"),
            waypoints = identifiers.drop(1).dropLast(1),
            navigationPoints = navigationPoints,
        )
    }

    private fun org.w3c.dom.Document.textOfFirst(tag: String): String =
        getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()

    private fun org.w3c.dom.Document.procedureName(tag: String): String {
        val procedure = getElementsByTagName(tag).item(0) ?: return ""
        val children = procedure.childNodes
        return (0 until children.length)
            .map { children.item(it) }
            .firstOrNull { it.nodeName == "Name" }
            ?.textContent
            ?.trim()
            .orEmpty()
    }

    private fun org.w3c.dom.Node.asNavigationPoint(): NavigationPoint? {
        val children = childNodes
        fun child(name: String): org.w3c.dom.Node? =
            (0 until children.length).map { children.item(it) }.firstOrNull { it.nodeName == name }
        val identifier = child("Ident")?.textContent?.trim().orEmpty()
        val position = child("Pos") as? org.w3c.dom.Element ?: return null
        val latitude = position.getAttribute("Lat").toDoubleOrNull() ?: return null
        val longitude = position.getAttribute("Lon").toDoubleOrNull() ?: return null
        return identifier.takeIf(String::isNotBlank)?.let {
            NavigationPoint(
                identifier = it,
                latitude = latitude,
                longitude = longitude,
                altitudeFeet = position.getAttribute("Alt").toDoubleOrNull()?.toInt() ?: 0,
            )
        }
    }

    private fun resolvedPoints(plan: FlightPlan): List<NavigationPoint>? {
        val identifiers = listOf(plan.origin) + plan.waypoints + plan.destination
        if (identifiers.any(String::isBlank)) return null
        val pointsByIdentifier = plan.navigationPoints.associateBy {
            it.identifier.trim().uppercase(Locale.ROOT)
        }
        return identifiers.map { identifier ->
            pointsByIdentifier[identifier.trim().uppercase(Locale.ROOT)] ?: return null
        }
    }

    private const val FMS_WAYPOINT_FIELD_COUNT = 5
}
