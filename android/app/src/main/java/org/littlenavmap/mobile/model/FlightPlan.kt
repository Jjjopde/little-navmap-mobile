/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** A portable, local-first flight plan. Coordinates are resolved from navigation data later. */
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
                parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { ident -> type to ident }
            }
        }
        val identifiers = waypointEntries.map { it.second }
        require(identifiers.size >= 2) { "The X-Plane FMS file has no usable route." }
        return FlightPlan(
            origin = header["ADEP"].orEmpty().ifBlank { identifiers.first() },
            destination = header["ADES"].orEmpty().ifBlank { identifiers.last() },
            waypoints = identifiers.drop(1).dropLast(1),
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
        val identifiers = buildList {
            for (index in 0 until nodes.length) {
                val waypoint = nodes.item(index)
                val ident = waypoint.childNodes
                    .let { children ->
                        (0 until children.length)
                            .map { children.item(it) }
                            .firstOrNull { it.nodeName == "Ident" }
                            ?.textContent
                            ?.trim()
                    }
                if (!ident.isNullOrBlank()) add(ident)
            }
        }
        require(identifiers.size >= 2) { "The Little Navmap plan has no usable route." }
        return FlightPlan(
            origin = identifiers.first(),
            destination = identifiers.last(),
            cruiseLevel = document.textOfFirst("CruisingAlt").ifBlank { "FL340" },
            approach = document.procedureName("Approach"),
            departureProcedure = document.procedureName("Sid"),
            arrivalProcedure = document.procedureName("Star"),
            waypoints = identifiers.drop(1).dropLast(1),
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

    private const val FMS_WAYPOINT_FIELD_COUNT = 5
}
