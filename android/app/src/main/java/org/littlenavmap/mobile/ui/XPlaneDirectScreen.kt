/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.littlenavmap.mobile.model.XPlaneSnapshot

@Composable
internal fun XPlaneDirectScreen(
    state: XPlaneUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnecting = state.phase == XPlanePhase.Connecting
    if (state.phase == XPlanePhase.Connected && state.endpoint != null) {
        LaunchedEffect(state.endpoint) {
            while (isActive) {
                delay(REFRESH_INTERVAL_MILLIS)
                onRefresh()
            }
        }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("X-Plane 12", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Connect directly to X-Plane UDP DataRef on your local network.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Simulator address") },
            singleLine = true,
            enabled = !isConnecting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = state.port,
            onValueChange = onPortChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("UDP port") },
            singleLine = true,
            enabled = !isConnecting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = if (state.phase == XPlanePhase.Connected) onRefresh else onConnect,
                enabled = !isConnecting,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(6.dp),
            ) { Text(if (isConnecting) "Reading" else if (state.phase == XPlanePhase.Connected) "Refresh" else "Connect") }
        }
        state.errorMessage?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(error, modifier = Modifier.padding(14.dp)) }
        }
        if (state.snapshot != null) {
            XPlaneSnapshotPanel(state.snapshot)
        } else if (state.phase != XPlanePhase.Error) {
            Text(
                "In X-Plane, allow UDP DataRef access for this device and use port 49000 unless you changed it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun XPlaneSnapshotPanel(snapshot: XPlaneSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Live aircraft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricRow("Position", "${snapshot.latitude.format(4)}, ${snapshot.longitude.format(4)}")
                MetricRow("Elevation", snapshot.elevationMeters?.let { "${it.format(0)} m" }.orUnavailable())
                MetricRow("Ground speed", snapshot.groundSpeedMetersPerSecond?.let { "${(it * METERS_PER_SECOND_TO_KNOTS).format(0)} kt" }.orUnavailable())
                MetricRow("True heading", snapshot.trueHeading?.let { "${it.format(0)} deg" }.orUnavailable())
                MetricRow("Indicated airspeed", snapshot.indicatedAirspeedKnots?.let { "${it.format(0)} kt" }.orUnavailable())
                MetricRow("Vertical speed", snapshot.verticalSpeedFeetPerMinute?.let { "${it.format(0)} ft/min" }.orUnavailable())
                MetricRow("Wind", windText(snapshot))
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun windText(snapshot: XPlaneSnapshot): String = when {
    snapshot.windDirectionDegrees != null && snapshot.windSpeedKnots != null ->
        "${snapshot.windDirectionDegrees.format(0)} deg / ${snapshot.windSpeedKnots.format(0)} kt"
    else -> "Unavailable"
}

private fun String?.orUnavailable(): String = this ?: "Unavailable"

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(Locale.US, this)

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(Locale.US, this)

private const val METERS_PER_SECOND_TO_KNOTS = 1.943844f
private const val REFRESH_INTERVAL_MILLIS = 1_000L
