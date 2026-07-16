/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.littlenavmap.mobile.R
import org.littlenavmap.mobile.model.AirportSnapshot
import org.littlenavmap.mobile.model.AirportWeatherKind
import org.littlenavmap.mobile.model.AirportWeatherReport
import org.littlenavmap.mobile.model.ServerProfile

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AirportScreen(
    profile: ServerProfile,
    query: String,
    state: AirportUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val normalizedIdent = normalizeAirportIdent(query).getOrNull()
    val searchInProgress = when (state) {
        is AirportUiState.Loading -> state.ident == normalizedIdent
        is AirportUiState.Refreshing -> state.ident == normalizedIdent
        else -> false
    }
    val submitSearch = {
        normalizedIdent?.let { ident ->
            focusManager.clearFocus()
            keyboardController?.hide()
            onSearch(ident)
        }
        Unit
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        AirportSearchBar(
            query = query,
            isValid = normalizedIdent != null,
            searchEnabled = normalizedIdent != null && !searchInProgress,
            onQueryChanged = { value ->
                onQueryChanged(
                    value
                        .uppercase(Locale.ROOT)
                        .filter { character ->
                            character in 'A'..'Z' || character in '0'..'9' || character == '-'
                        }
                        .take(MAX_AIRPORT_QUERY_CHARACTERS),
                )
            },
            onSearch = submitSearch,
        )
        HorizontalDivider()
        AirportStatusContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AirportSearchBar(
    query: String,
    isValid: Boolean,
    searchEnabled: Boolean,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val compact = maxWidth < SEARCH_ROW_BREAKPOINT
        val field: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = fieldModifier,
                label = { Text(stringResource(R.string.airport_search_label)) },
                singleLine = true,
                isError = query.isNotEmpty() && !isValid,
                supportingText = if (query.isNotEmpty() && !isValid) {
                    { Text(stringResource(R.string.airport_ident_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { if (searchEnabled) onSearch() },
                ),
            )
        }
        val action: @Composable (Modifier) -> Unit = { actionModifier ->
            Button(
                onClick = onSearch,
                enabled = searchEnabled,
                modifier = actionModifier.heightIn(min = 56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.airport_search_action),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field(Modifier.fillMaxWidth())
                action(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                field(Modifier.weight(1f))
                action(Modifier.widthIn(min = 112.dp))
            }
        }
    }
}

/** Pure result renderer kept separate from loading and search orchestration for UI tests. */
@Composable
internal fun AirportStatusContent(
    state: AirportUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AirportUiState.Idle -> AirportStateMessage(
            title = stringResource(R.string.airport_search_prompt),
            modifier = modifier,
        )
        is AirportUiState.Loading -> AirportStateMessage(
            title = stringResource(R.string.airport_loading, state.ident),
            showProgress = true,
            modifier = modifier,
        )
        is AirportUiState.Found -> AirportDetails(
            airport = state.airport,
            staleFailure = null,
            isRefreshing = false,
            onRetry = onRetry,
            modifier = modifier,
        )
        is AirportUiState.NotFound -> AirportStateMessage(
            title = stringResource(R.string.airport_not_found, state.ident),
            modifier = modifier,
        )
        is AirportUiState.Error -> AirportStateMessage(
            title = stringResource(R.string.airport_data_unavailable),
            detail = airportFailureMessage(state.failure),
            onRetry = onRetry,
            modifier = modifier,
        )
        is AirportUiState.Stale -> AirportDetails(
            airport = state.airport,
            staleFailure = state.failure,
            isRefreshing = false,
            onRetry = onRetry,
            modifier = modifier,
        )
        is AirportUiState.Refreshing -> AirportDetails(
            airport = state.airport,
            staleFailure = state.previousFailure,
            isRefreshing = true,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
private fun AirportStateMessage(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    showProgress: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_airport),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            detail?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            onRetry?.let { retry ->
                Button(
                    onClick = retry,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.retry))
                }
            }
    }
}

@Composable
private fun AirportDetails(
    airport: AirportSnapshot,
    staleFailure: AirportDataFailure?,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columnCount = if (maxWidth >= DETAILS_TWO_COLUMN_BREAKPOINT) 2 else 1
        val identifiers = buildList {
            add(AirportFact(R.string.airport_search_label, airport.ident))
            airport.icao?.let { add(AirportFact(R.string.airport_icao, it)) }
            airport.faa?.let { add(AirportFact(R.string.airport_faa, it)) }
            airport.local?.let { add(AirportFact(R.string.airport_local_code, it)) }
            airport.iata?.let { add(AirportFact(R.string.airport_iata, it)) }
        }
        val overview = buildList {
            airport.elevationFeet?.let {
                add(AirportFact(R.string.airport_elevation, formatWhole(it), R.string.unit_feet))
            }
            airport.transitionAltitudeFeet?.let {
                add(
                    AirportFact(
                        R.string.airport_transition_altitude,
                        formatWhole(it),
                        R.string.unit_feet,
                    ),
                )
            }
            airport.magneticDeclinationDegrees?.let {
                add(
                    AirportFact(
                        R.string.airport_magnetic_declination,
                        formatDecimal(it, showSign = true),
                        R.string.unit_degrees_magnetic,
                    ),
                )
            }
            airport.rating?.let {
                add(AirportFact(R.string.airport_rating, "$it/$MAX_AIRPORT_RATING"))
            }
        }
        val position = buildList {
            airport.latitude?.let {
                add(AirportFact(R.string.latitude, formatCoordinate(it)))
            }
            airport.longitude?.let {
                add(AirportFact(R.string.longitude, formatCoordinate(it)))
            }
        }
        val runway = buildList {
            airport.longestRunwayLengthFeet?.let {
                add(
                    AirportFact(
                        R.string.airport_runway_length,
                        formatWhole(it),
                        R.string.unit_feet,
                    ),
                )
            }
            airport.longestRunwayWidthFeet?.let {
                add(
                    AirportFact(
                        R.string.airport_runway_width,
                        formatWhole(it),
                        R.string.unit_feet,
                    ),
                )
            }
            airport.longestRunwayHeading?.let {
                add(AirportFact(R.string.airport_runway_heading, it))
            }
            airport.longestRunwaySurfaceCode?.let {
                add(
                    AirportFact(
                        R.string.airport_runway_surface,
                        runwaySurfaceLabel(it),
                    ),
                )
            }
        }
        val time = buildList {
            airport.sunriseUtc?.let { add(AirportFact(R.string.airport_sunrise, it)) }
            airport.sunsetUtc?.let { add(AirportFact(R.string.airport_sunset, it)) }
            airport.activeDateTime?.let {
                add(AirportFact(R.string.airport_report_time, it))
            }
            airport.activeDateTimeSource?.let {
                add(AirportFact(R.string.airport_report_source, it))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (staleFailure != null || isRefreshing) {
                item(
                    key = "airport-stale",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    if (staleFailure != null) {
                        AirportStaleBanner(
                            failure = staleFailure,
                            isRefreshing = isRefreshing,
                            onRetry = onRetry,
                        )
                    } else {
                        AirportRefreshingBanner()
                    }
                }
            }
            item(
                key = "airport-header",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                AirportHeader(airport)
            }
            item(key = "airport-identifiers") {
                FactSection(
                    title = stringResource(R.string.airport_identifiers),
                    facts = identifiers,
                )
            }
            if (overview.isNotEmpty()) {
                item(key = "airport-overview") {
                    FactSection(
                        title = stringResource(R.string.airport_overview),
                        facts = overview,
                    )
                }
            }
            if (position.isNotEmpty()) {
                item(key = "airport-position") {
                    FactSection(
                        title = stringResource(R.string.position),
                        facts = position,
                    )
                }
            }
            if (runway.isNotEmpty() || airport.runwayFlags.isNotEmpty()) {
                item(key = "airport-runway") {
                    AirportSection(title = stringResource(R.string.airport_longest_runway)) {
                        FactRows(runway)
                        if (airport.runwayFlags.isNotEmpty()) {
                            TextList(
                                label = stringResource(R.string.airport_runway_features),
                                values = airport.runwayFlags,
                            )
                        }
                    }
                }
            }
            if (airport.communications.isNotEmpty()) {
                item(key = "airport-communications") {
                    AirportSection(title = stringResource(R.string.airport_communications)) {
                        airport.communications.forEachIndexed { index, frequency ->
                            DynamicFactRow(
                                label = frequency.label,
                                value = formatFrequency(frequency.megahertz),
                                unitResource = R.string.unit_megahertz,
                            )
                            if (index != airport.communications.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
            if (airport.facilities.isNotEmpty()) {
                item(key = "airport-facilities") {
                    AirportSection(title = stringResource(R.string.airport_facilities)) {
                        TextRows(airport.facilities)
                    }
                }
            }
            if (airport.parking.isNotEmpty()) {
                item(key = "airport-parking") {
                    AirportSection(title = stringResource(R.string.airport_parking)) {
                        airport.parking.forEachIndexed { index, parking ->
                            DynamicFactRow(
                                label = parkingLabel(parking.key),
                                value = parking.count.toString(),
                            )
                            if (index != airport.parking.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
            if (time.isNotEmpty()) {
                item(key = "airport-time") {
                    FactSection(
                        title = stringResource(R.string.airport_time),
                        facts = time,
                    )
                }
            }
            if (airport.weatherReports.isNotEmpty()) {
                item(
                    key = "airport-weather",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    AirportSection(title = stringResource(R.string.airport_weather)) {
                        airport.weatherReports.forEachIndexed { index, report ->
                            WeatherReport(report)
                            if (index != airport.weatherReports.lastIndex) {
                                Spacer(Modifier.size(12.dp))
                                HorizontalDivider()
                                Spacer(Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportHeader(airport: AirportSnapshot) {
    val location = listOfNotNull(
        airport.city,
        airport.state,
        airport.country,
        airport.region,
    ).distinct().joinToString(", ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Text(
                text = airport.ident,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = airport.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (location.isNotEmpty()) {
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (airport.closed) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = stringResource(R.string.airport_closed),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FactSection(title: String, facts: List<AirportFact>) {
    AirportSection(title = title) {
        FactRows(facts)
    }
}

@Composable
private fun AirportSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier
                .padding(bottom = 7.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider()
        content()
    }
}

@Composable
private fun FactRows(facts: List<AirportFact>) {
    facts.forEachIndexed { index, fact ->
        FactRow(fact)
        if (index != facts.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun FactRow(fact: AirportFact) {
    DynamicFactRow(
        label = stringResource(fact.labelResource),
        value = fact.value,
        unitResource = fact.unitResource,
    )
}

@Composable
private fun DynamicFactRow(
    label: String,
    value: String,
    @StringRes unitResource: Int? = null,
) {
    val unit = unitResource?.let { stringResource(it) }
    val spokenValue = listOfNotNull(label, value, unit).joinToString(" ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = spokenValue },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            unit?.let { unitText ->
                Spacer(Modifier.width(5.dp))
                Text(
                    text = unitText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AirportStaleBanner(
    failure: AirportDataFailure,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
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
                    text = stringResource(R.string.airport_refresh_failed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = airportFailureMessage(failure),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onRetry) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.retry),
                    )
                }
            }
        }
    }
}

@Composable
private fun AirportRefreshingBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.airport_refreshing),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TextList(label: String, values: List<String>) {
    Text(
        text = label,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextRows(values)
}

@Composable
private fun TextRows(values: List<String>) {
    values.forEachIndexed { index, value ->
        Text(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (index != values.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun WeatherReport(report: AirportWeatherReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = report.source,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(report.kind.labelResource),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.size(7.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        SelectionContainer {
            Text(
                text = report.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private val AirportWeatherKind.labelResource: Int
    @StringRes get() = when (this) {
        AirportWeatherKind.Station -> R.string.airport_weather_station
        AirportWeatherKind.Nearest -> R.string.airport_weather_nearest
        AirportWeatherKind.Interpolated -> R.string.airport_weather_interpolated
    }

@Composable
private fun airportFailureMessage(failure: AirportDataFailure): String = when (failure) {
    AirportDataFailure.Timeout -> stringResource(R.string.airport_error_timeout)
    AirportDataFailure.HostNotFound -> stringResource(R.string.airport_error_host_not_found)
    AirportDataFailure.ServerUnavailable -> stringResource(
        R.string.airport_error_server_unavailable,
    )
    AirportDataFailure.SecureConnection -> stringResource(R.string.airport_error_tls)
    is AirportDataFailure.Http -> stringResource(
        R.string.airport_error_http,
        failure.statusCode,
    )
    AirportDataFailure.InvalidResponse -> stringResource(R.string.airport_error_invalid_response)
    AirportDataFailure.Connection -> stringResource(R.string.airport_error_connection)
}

private fun formatWhole(value: Double): String =
    String.format(Locale.getDefault(), "%,.0f", value)

private fun formatDecimal(value: Double, showSign: Boolean = false): String =
    String.format(Locale.getDefault(), if (showSign) "%+.1f" else "%.1f", value)

private fun formatCoordinate(value: Double): String =
    String.format(Locale.getDefault(), "%.5f", value)

private fun formatFrequency(value: Double): String =
    String.format(Locale.getDefault(), "%.3f", value)

@Composable
private fun runwaySurfaceLabel(code: String): String = when (code.uppercase(Locale.ROOT)) {
    "C", "CONCRETE" -> stringResourceCompat(R.string.airport_surface_concrete)
    "A", "ASPHALT" -> stringResourceCompat(R.string.airport_surface_asphalt)
    "G", "GRASS" -> stringResourceCompat(R.string.airport_surface_grass)
    "GR", "GRAVEL" -> stringResourceCompat(R.string.airport_surface_gravel)
    "W", "WATER" -> stringResourceCompat(R.string.airport_surface_water)
    "S", "SAND" -> stringResourceCompat(R.string.airport_surface_sand)
    else -> stringResourceCompat(R.string.airport_surface_unknown, code)
}

@Composable
private fun stringResourceCompat(@StringRes resource: Int, vararg formatArgs: Any): String =
    stringResource(resource, *formatArgs)

@Composable
private fun parkingLabel(key: String): String = when (key) {
    "gates" -> stringResourceCompat(R.string.airport_parking_gates)
    "jetWays" -> stringResourceCompat(R.string.airport_parking_jetways)
    "gaRamps" -> stringResourceCompat(R.string.airport_parking_ga_ramps)
    "cargo" -> stringResourceCompat(R.string.airport_parking_cargo)
    "militaryCargo" -> stringResourceCompat(R.string.airport_parking_military_cargo)
    "militaryCombat" -> stringResourceCompat(R.string.airport_parking_military_combat)
    "helipads" -> stringResourceCompat(R.string.airport_parking_helipads)
    else -> key
}

private data class AirportFact(
    @StringRes val labelResource: Int,
    val value: String,
    @StringRes val unitResource: Int? = null,
)

private val SEARCH_ROW_BREAKPOINT = 440.dp
private val DETAILS_TWO_COLUMN_BREAKPOINT = 760.dp
private const val MAX_AIRPORT_QUERY_CHARACTERS = 12
private const val MAX_AIRPORT_RATING = 5
