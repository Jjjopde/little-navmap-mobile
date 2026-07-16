/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.littlenavmap.mobile.R
import org.littlenavmap.mobile.model.AircraftSnapshot
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.network.SimInfoClient

@Composable
internal fun AircraftScreen(
    profile: ServerProfile,
    refreshKey: Int,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val poller = remember(profile.baseUrl) {
        AircraftStatusPoller(SimInfoClient())
    }
    val state by produceState<AircraftUiState>(
        initialValue = AircraftUiState.Loading,
        profile.baseUrl,
        refreshKey,
        lifecycleOwner,
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            poller.states(profile).collect { value = it }
        }
    }

    AircraftStatusContent(
        state = state,
        onRetry = onRefresh,
        modifier = modifier,
    )
}

@Composable
internal fun AircraftStatusContent(
    state: AircraftUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (state) {
            AircraftUiState.Loading -> AircraftStateMessage(
                title = stringResourceCompat(R.string.aircraft_loading),
                showProgress = true,
            )
            AircraftUiState.SimulatorInactive -> AircraftStateMessage(
                title = stringResourceCompat(R.string.simulator_inactive),
                body = stringResourceCompat(R.string.simulator_inactive_detail),
                showAircraftIcon = true,
            )
            is AircraftUiState.Error -> AircraftStateMessage(
                title = stringResourceCompat(R.string.aircraft_data_unavailable),
                body = aircraftFailureMessage(state.failure),
                showAircraftIcon = true,
                onRetry = onRetry,
            )
            is AircraftUiState.Active -> AircraftDashboard(
                snapshot = state.snapshot,
                staleFailure = null,
                onRetry = onRetry,
            )
            is AircraftUiState.Stale -> AircraftDashboard(
                snapshot = state.snapshot,
                staleFailure = state.failure,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun AircraftStateMessage(
    title: String,
    body: String? = null,
    showProgress: Boolean = false,
    showAircraftIcon: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            showProgress -> CircularProgressIndicator(modifier = Modifier.size(36.dp))
            showAircraftIcon -> Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_plane),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResourceCompat(R.string.retry))
            }
        }
    }
}

@Composable
private fun AircraftDashboard(
    snapshot: AircraftSnapshot,
    staleFailure: AircraftDataFailure?,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (staleFailure != null) {
            item {
                StaleDataBanner(
                    failure = staleFailure,
                    onRetry = onRetry,
                )
            }
        }
        item { AircraftStatusHeader(snapshot, isStale = staleFailure != null) }
        item { PrimaryReadouts(snapshot) }
        item {
            DataSection(title = stringResourceCompat(R.string.flight_data)) {
                DetailPair(
                    first = DetailValue(
                        R.string.indicated_air_speed,
                        formatWhole(snapshot.indicatedSpeedKts),
                        R.string.unit_knots,
                    ),
                    second = DetailValue(
                        R.string.true_air_speed,
                        formatWhole(snapshot.trueAirspeedKts),
                        R.string.unit_knots,
                    ),
                )
                HorizontalDivider()
                DetailPair(
                    first = DetailValue(
                        R.string.vertical_speed,
                        formatWhole(snapshot.verticalSpeedFeetPerMinute, showSign = true),
                        R.string.unit_feet_per_minute,
                    ),
                    second = DetailValue(
                        R.string.altitude_above_ground,
                        formatWhole(snapshot.altitudeAboveGroundFeet),
                        R.string.unit_feet,
                    ),
                )
                HorizontalDivider()
                DetailPair(
                    first = DetailValue(
                        R.string.ground_elevation,
                        formatWhole(snapshot.groundAltitudeFeet),
                        R.string.unit_feet,
                    ),
                    second = DetailValue(
                        R.string.sea_level_pressure,
                        formatWhole(snapshot.seaLevelPressureMbar),
                        R.string.unit_mbar,
                    ),
                )
            }
        }
        item {
            DataSection(title = stringResourceCompat(R.string.environment)) {
                DetailPair(
                    first = DetailValue(
                        R.string.wind_direction,
                        formatDirection(snapshot.magneticWindDirectionDegrees),
                        R.string.unit_degrees_magnetic,
                    ),
                    second = DetailValue(
                        R.string.wind_speed,
                        formatWhole(snapshot.windSpeedKts),
                        R.string.unit_knots,
                    ),
                )
            }
        }
        item {
            DataSection(title = stringResourceCompat(R.string.position)) {
                DetailPair(
                    first = DetailValue(
                        R.string.latitude,
                        formatCoordinate(snapshot.latitude),
                        null,
                    ),
                    second = DetailValue(
                        R.string.longitude,
                        formatCoordinate(snapshot.longitude),
                        null,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StaleDataBanner(
    failure: AircraftDataFailure,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResourceCompat(R.string.aircraft_connection_interrupted),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = aircraftFailureMessage(failure),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onRetry) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResourceCompat(R.string.retry),
                )
            }
        }
    }
}

@Composable
private fun AircraftStatusHeader(
    snapshot: AircraftSnapshot,
    isStale: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val updatedTime = remember(snapshot.receivedAtMillis, context) {
        DateFormat.getTimeFormat(context).format(Date(snapshot.receivedAtMillis))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(9.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = if (isStale) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        ) { }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isStale) {
                    stringResourceCompat(R.string.aircraft_data_stale)
                } else {
                    stringResourceCompat(R.string.aircraft_live_data)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResourceCompat(R.string.last_update, updatedTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            snapshot.simConnectStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PrimaryReadouts(snapshot: AircraftSnapshot) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val metrics = listOf(
            PrimaryMetric(
                R.string.indicated_altitude,
                formatWhole(snapshot.indicatedAltitudeFeet),
                R.string.unit_feet,
            ),
            PrimaryMetric(
                R.string.ground_speed,
                formatWhole(snapshot.groundSpeedKts),
                R.string.unit_knots,
            ),
            PrimaryMetric(
                R.string.magnetic_heading,
                formatDirection(snapshot.magneticHeadingDegrees),
                R.string.unit_degrees_magnetic,
            ),
        )

        if (maxWidth >= PRIMARY_METRICS_WIDE_BREAKPOINT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { metric ->
                    PrimaryMetricTile(metric, Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryMetricTile(metrics[0], Modifier.weight(1f))
                    PrimaryMetricTile(metrics[1], Modifier.weight(1f))
                }
                PrimaryMetricTile(metrics[2], Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PrimaryMetricTile(metric: PrimaryMetric, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 92.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResourceCompat(metric.labelResource),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = metric.value,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResourceCompat(metric.unitResource),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DataSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(bottom = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun DetailPair(first: DetailValue, second: DetailValue) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailCell(first, Modifier.weight(1f))
        DetailCell(second, Modifier.weight(1f))
    }
}

@Composable
private fun DetailCell(value: DetailValue, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResourceCompat(value.labelResource),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value.value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            value.unitResource?.let { unitResource ->
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResourceCompat(unitResource),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun aircraftFailureMessage(failure: AircraftDataFailure): String = when (failure) {
    AircraftDataFailure.Timeout -> stringResourceCompat(R.string.aircraft_error_timeout)
    AircraftDataFailure.HostNotFound -> stringResourceCompat(R.string.aircraft_error_host_not_found)
    AircraftDataFailure.ServerUnavailable -> stringResourceCompat(
        R.string.aircraft_error_server_unavailable,
    )
    AircraftDataFailure.SecureConnection -> stringResourceCompat(R.string.aircraft_error_tls)
    is AircraftDataFailure.Http -> stringResourceCompat(
        R.string.aircraft_error_http,
        failure.statusCode,
    )
    AircraftDataFailure.InvalidResponse -> stringResourceCompat(
        R.string.aircraft_error_invalid_response,
    )
    AircraftDataFailure.Connection -> stringResourceCompat(R.string.aircraft_error_connection)
}

@Composable
private fun stringResourceCompat(resource: Int, vararg formatArgs: Any): String =
    androidx.compose.ui.res.stringResource(resource, *formatArgs)

private fun formatWhole(value: Double?, showSign: Boolean = false): String {
    if (value == null) return MISSING_VALUE
    val pattern = when {
        abs(value) >= MAX_PLAIN_DISPLAY_VALUE -> if (showSign) "%+.1e" else "%.1e"
        showSign -> "%+.0f"
        else -> "%.0f"
    }
    return String.format(Locale.getDefault(), pattern, value)
}

private fun formatDirection(value: Double?): String = value?.let {
    String.format(Locale.getDefault(), "%03d", it.roundToInt() % FULL_CIRCLE_DEGREES)
} ?: MISSING_VALUE

private fun formatCoordinate(value: Double?): String = value?.let {
    String.format(Locale.getDefault(), "%.5f", it)
} ?: MISSING_VALUE

private data class PrimaryMetric(
    val labelResource: Int,
    val value: String,
    val unitResource: Int,
)

private data class DetailValue(
    val labelResource: Int,
    val value: String,
    val unitResource: Int?,
)

private val PRIMARY_METRICS_WIDE_BREAKPOINT = 520.dp
private const val MISSING_VALUE = "--"
private const val MAX_PLAIN_DISPLAY_VALUE = 10_000_000.0
private const val FULL_CIRCLE_DEGREES = 360
