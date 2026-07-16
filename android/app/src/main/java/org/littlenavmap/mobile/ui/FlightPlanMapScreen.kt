/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.littlenavmap.mobile.model.FlightPlan
import org.littlenavmap.mobile.model.NavigationPoint
import kotlin.math.max
import kotlin.math.min

/** A local, lightweight route display. It deliberately avoids a WebView or network map engine. */
@Composable
internal fun FlightPlanMapScreen(
    plan: FlightPlan,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var enabledLayers by remember { mutableStateOf(setOf("ROUTE", "AIRSPACE")) }
    val points = remember(plan.navigationPoints) { plan.navigationPoints.distinctBy { it.identifier to it.latitude to it.longitude } }
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(localized("Route map", "航路地图"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    localized("Local vector display", "本地矢量显示"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${plan.origin.ifBlank { "----" }}  -  ${plan.destination.ifBlank { "----" }}", style = MaterialTheme.typography.labelLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapLayerChip("ROUTE", "ROUTE" in enabledLayers) { enabledLayers = enabledLayers.toggle("ROUTE") }
            MapLayerChip("AIRSPACE", "AIRSPACE" in enabledLayers) { enabledLayers = enabledLayers.toggle("AIRSPACE") }
            MapLayerChip("WX", "WX" in enabledLayers) { enabledLayers = enabledLayers.toggle("WX") }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            color = Color(0xFF101B27),
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                RouteCanvas(
                    points = points,
                    zoom = zoom,
                    showRoute = "ROUTE" in enabledLayers,
                    showAirspace = "AIRSPACE" in enabledLayers,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MapControl("+") { zoom = min(MAX_ZOOM, zoom + ZOOM_STEP) }
                    MapControl("-") { zoom = max(MIN_ZOOM, zoom - ZOOM_STEP) }
                }
                Text(
                    text = if (points.size >= 2) localized("VECTOR ROUTE", "矢量航路") else localized("RESOLVE ROUTE POINTS TO DRAW", "请解析航路点以绘制航图"),
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = Color(0xFFC8E5F7),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MapLayerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 36.dp),
    )
}

@Composable
private fun MapControl(symbol: String, onClick: () -> Unit) {
    Surface(color = Color(0xFF1D2B3B), shape = RoundedCornerShape(6.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Text(symbol, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun RouteCanvas(
    points: List<NavigationPoint>,
    zoom: Float,
    showRoute: Boolean,
    showAirspace: Boolean,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val gridColor = Color(0xFF395064)
        val routeColor = Color(0xFF71D9FF)
        val margin = 36.dp.toPx()
        val columns = 6
        val rows = 8
        if (showAirspace) {
            repeat(columns + 1) { index ->
                val x = size.width * index / columns
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            repeat(rows + 1) { index ->
                val y = size.height * index / rows
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
        }
        if (points.size < 2 || !showRoute) return@Canvas
        val minLatitude = points.minOf(NavigationPoint::latitude)
        val maxLatitude = points.maxOf(NavigationPoint::latitude)
        val minLongitude = points.minOf(NavigationPoint::longitude)
        val maxLongitude = points.maxOf(NavigationPoint::longitude)
        val latitudeRange = max(maxLatitude - minLatitude, MIN_COORDINATE_RANGE)
        val longitudeRange = max(maxLongitude - minLongitude, MIN_COORDINATE_RANGE)
        val usableWidth = max(size.width - margin * 2, 1f)
        val usableHeight = max(size.height - margin * 2, 1f)
        val center = Offset(size.width / 2, size.height / 2)
        fun position(point: NavigationPoint): Offset {
            val x = margin + ((point.longitude - minLongitude) / longitudeRange).toFloat() * usableWidth
            val y = size.height - margin - ((point.latitude - minLatitude) / latitudeRange).toFloat() * usableHeight
            return Offset(
                x = center.x + (x - center.x) * zoom,
                y = center.y + (y - center.y) * zoom,
            )
        }
        val coordinates = points.map(::position)
        coordinates.zipWithNext().forEach { (from, to) ->
            drawLine(routeColor, from, to, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        coordinates.forEachIndexed { index, point ->
            drawCircle(if (index == 0 || index == coordinates.lastIndex) Color(0xFFFFD56B) else routeColor, 7.dp.toPx(), point)
            drawCircle(Color(0xFF101B27), 3.dp.toPx(), point, style = Stroke(width = 1.dp.toPx()))
        }
    }
}

private const val MIN_COORDINATE_RANGE = 0.25
private const val MIN_ZOOM = 0.7f
private const val MAX_ZOOM = 2.4f
private const val ZOOM_STEP = 0.2f

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
