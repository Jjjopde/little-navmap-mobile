/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.littlenavmap.mobile.R
import org.littlenavmap.mobile.model.FlightPlan
import org.littlenavmap.mobile.model.FlightPlanCodec
import org.littlenavmap.mobile.model.NavigationDataPackage
import org.littlenavmap.mobile.model.ProcedureType
import org.littlenavmap.mobile.model.moveWaypoint
import org.littlenavmap.mobile.model.removeWaypointAt
import org.littlenavmap.mobile.network.AviationWeatherClient

private enum class PlannerDestination(val title: String, val icon: Int) {
    Plan("Plan", R.drawable.ic_route),
    Weather("Weather", R.drawable.ic_progress),
    Data("Data", R.drawable.ic_airport),
}

private enum class ProcedureField(val title: String) {
    Sid("Departure procedure"),
    Star("Arrival procedure"),
    Approach("Approach"),
}

private enum class ExportFormat(val fileName: String) {
    Json("flight-plan.json"),
    RouteText("flight-plan.route.txt"),
    XPlaneFms("flight-plan.fms"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlightPlanningScreen(
    plan: FlightPlan,
    onPlanChange: (FlightPlan) -> Unit,
    navigationData: NavigationDataPackage?,
    onImportNavigationData: (String) -> Result<NavigationDataPackage>,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onOpenLiveMap: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var destination by remember { mutableStateOf(PlannerDestination.Plan) }
    var waypointInput by remember { mutableStateOf("") }
    var procedurePicker by remember { mutableStateOf<ProcedureField?>(null) }
    var exportFormat by remember { mutableStateOf(ExportFormat.Json) }
    var fileMessage by remember { mutableStateOf<String?>(null) }
    var weatherStation by remember { mutableStateOf("ZBAA") }
    var weatherText by remember { mutableStateOf<String?>(null) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var isWeatherLoading by remember { mutableStateOf(false) }
    var navDataMessage by remember { mutableStateOf("Use the connected desktop database for procedure and waypoint resolution.") }

    val importPlan = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val content = reader.readText()
                FlightPlanCodec.decodeImported(content)
            } ?: error("The selected file could not be read.")
        }.onSuccess { imported ->
            onPlanChange(imported)
            destination = PlannerDestination.Plan
            fileMessage = "Imported ${imported.waypoints.size + 2} route points."
        }.onFailure { error ->
            fileMessage = "Import failed: ${error.message ?: "unsupported file"}"
        }
    }
    val importNavigationData = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("The selected file could not be read.")
        }.fold(
            onSuccess = { content ->
                onImportNavigationData(content).fold(
                    onSuccess = { data ->
                        navDataMessage = "Installed AIRAC ${data.cycle}: ${data.airports.size} airports, ${data.fixes.size} fixes."
                    },
                    onFailure = { error -> navDataMessage = "Navigation-data install failed: ${error.message ?: "invalid package"}" },
                )
            },
            onFailure = { error -> navDataMessage = "Navigation-data import failed: ${error.message ?: "unreadable file"}" },
        )
    }
    val exportPlan = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val content = when (exportFormat) {
                ExportFormat.Json -> FlightPlanCodec.encode(plan)
                ExportFormat.RouteText -> FlightPlanCodec.routeText(plan)
                ExportFormat.XPlaneFms -> FlightPlanCodec.xPlaneFms(plan)
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                ?: error("The selected location could not be written.")
        }.onSuccess {
            fileMessage = "${exportFormat.name} file saved."
        }.onFailure { error -> fileMessage = "Export failed: ${error.message ?: "unknown error"}" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NAVMAP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isConnected) "Live link active" else "Local flight planning",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (isConnected) {
                        IconButton(onClick = onOpenLiveMap) {
                            Icon(painterResource(R.drawable.ic_map), "Open live map")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onConnect,
                            modifier = Modifier.padding(end = 12.dp).heightIn(min = 40.dp),
                            shape = RoundedCornerShape(6.dp),
                        ) { Text("Connect") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                PlannerDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(painterResource(item.icon), item.title) },
                        label = { Text(item.title) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        when (destination) {
            PlannerDestination.Plan -> FlightPlanEditor(
                plan = plan,
                waypointInput = waypointInput,
                onPlanChange = onPlanChange,
                onWaypointInputChange = { waypointInput = it },
                onAddWaypoint = {
                    val waypoint = waypointInput.trim().uppercase()
                    if (waypoint.isNotEmpty()) {
                        onPlanChange(plan.copy(waypoints = plan.waypoints + waypoint))
                        waypointInput = ""
                    }
                },
                onRemoveWaypoint = { index ->
                    onPlanChange(plan.removeWaypointAt(index))
                },
                onMoveWaypoint = { fromIndex, toIndex ->
                    onPlanChange(plan.moveWaypoint(fromIndex, toIndex))
                },
                onProcedureClick = { procedurePicker = it },
                onImport = { importPlan.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onExport = { format ->
                    exportFormat = format
                    exportPlan.launch(format.fileName)
                },
                canExportXPlaneFms = FlightPlanCodec.canExportXPlaneFms(plan),
                fileMessage = fileMessage,
                modifier = Modifier.padding(padding),
            )
            PlannerDestination.Weather -> WeatherPanel(
                station = weatherStation,
                weatherText = weatherText,
                error = weatherError,
                loading = isWeatherLoading,
                onStationChange = { weatherStation = it.uppercase() },
                onRefresh = {
                    isWeatherLoading = true
                    weatherError = null
                    scope.launch {
                        runCatching { AviationWeatherClient().metar(weatherStation) }
                            .onSuccess { result -> weatherText = result }
                            .onFailure { error -> weatherError = error.message ?: "Weather data unavailable." }
                        isWeatherLoading = false
                    }
                },
                modifier = Modifier.padding(padding),
            )
            PlannerDestination.Data -> NavigationDataPanel(
                plan = plan,
                navigationData = navigationData,
                message = navDataMessage,
                isConnected = isConnected,
                onConnect = onConnect,
                onImport = { importNavigationData.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    procedurePicker?.let { field ->
        val suggestedProcedures = navigationData.procedureOptions(plan, field)
        var procedureDraft by remember(field, plan) {
            mutableStateOf(
                when (field) {
                    ProcedureField.Sid -> plan.departureProcedure
                    ProcedureField.Star -> plan.arrivalProcedure
                    ProcedureField.Approach -> plan.approach
                },
            )
        }
        ModalBottomSheet(
            onDismissRequest = { procedurePicker = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = field.title,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Enter the published identifier. It is validated against the connected Little Navmap navigation database.",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (suggestedProcedures.isNotEmpty()) {
                Text(
                    text = "Available in AIRAC ${navigationData?.cycle}",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                suggestedProcedures.forEach { procedure ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { procedureDraft = procedure }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(procedure, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            OutlinedTextField(
                value = procedureDraft,
                onValueChange = { procedureDraft = it.uppercase() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                label = { Text(field.title) },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (procedureDraft.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            onPlanChange(updateProcedure(plan, field, ""))
                            procedurePicker = null
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(6.dp),
                    ) { Text("Clear") }
                }
                Button(
                    onClick = {
                        onPlanChange(updateProcedure(plan, field, procedureDraft.trim()))
                        procedurePicker = null
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(6.dp),
                ) { Text("Apply") }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FlightPlanEditor(
    plan: FlightPlan,
    waypointInput: String,
    onPlanChange: (FlightPlan) -> Unit,
    onWaypointInputChange: (String) -> Unit,
    onAddWaypoint: () -> Unit,
    onRemoveWaypoint: (Int) -> Unit,
    onMoveWaypoint: (Int, Int) -> Unit,
    onProcedureClick: (ProcedureField) -> Unit,
    onImport: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    canExportXPlaneFms: Boolean,
    fileMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Flight plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Build the route locally, then send or export it when ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanField("From", plan.origin, { onPlanChange(plan.copy(origin = it.uppercase())) }, Modifier.weight(1f))
            PlanField("To", plan.destination, { onPlanChange(plan.copy(destination = it.uppercase())) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanField("Cruise", plan.cruiseLevel, { onPlanChange(plan.copy(cruiseLevel = it.uppercase())) }, Modifier.weight(1f))
            PlanField("Alternate", plan.alternate, { onPlanChange(plan.copy(alternate = it.uppercase())) }, Modifier.weight(1f))
        }
        HorizontalDivider()
        ProcedureRow("SID", plan.departureProcedure, { onProcedureClick(ProcedureField.Sid) })
        ProcedureRow("STAR", plan.arrivalProcedure, { onProcedureClick(ProcedureField.Star) })
        ProcedureRow("Approach", plan.approach, { onProcedureClick(ProcedureField.Approach) })
        HorizontalDivider()
        Text("Route", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = waypointInput,
                onValueChange = onWaypointInputChange,
                modifier = Modifier.weight(1f),
                label = { Text("Waypoint / airway") },
                singleLine = true,
            )
            Button(onClick = onAddWaypoint, modifier = Modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(6.dp)) { Text("Add") }
        }
        if (plan.waypoints.isEmpty()) {
            Text("No enroute waypoints yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            plan.waypoints.forEachIndexed { index, waypoint ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(waypoint, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onMoveWaypoint(index, index - 1) }, enabled = index > 0) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Move $waypoint up",
                            modifier = Modifier.graphicsLayer(rotationZ = 90f),
                        )
                    }
                    IconButton(
                        onClick = { onMoveWaypoint(index, index + 1) },
                        enabled = index < plan.waypoints.lastIndex,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Move $waypoint down",
                            modifier = Modifier.graphicsLayer(rotationZ = -90f),
                        )
                    }
                    IconButton(onClick = { onRemoveWaypoint(index) }) {
                        Icon(painterResource(R.drawable.ic_unlink), "Remove $waypoint")
                    }
                }
            }
        }
        HorizontalDivider()
        Text("Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) { Text("Import") }
            Button(onClick = { onExport(ExportFormat.Json) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) { Text("Export JSON") }
        }
        OutlinedButton(onClick = { onExport(ExportFormat.RouteText) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) {
            Text("Export route text")
        }
        OutlinedButton(
            onClick = { onExport(ExportFormat.XPlaneFms) },
            enabled = canExportXPlaneFms,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(6.dp),
        ) { Text("Export X-Plane FMS") }
        if (!canExportXPlaneFms) {
            Text(
                "X-Plane export needs a route with resolved coordinates.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        fileMessage?.let { message ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlanField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier, label = { Text(label) }, singleLine = true)
}

@Composable
private fun ProcedureRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "Select" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = "Select $label",
            modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = 180f),
        )
    }
}

@Composable
private fun WeatherPanel(
    station: String,
    weatherText: String?,
    error: String?,
    loading: Boolean,
    onStationChange: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Weather", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Live METAR from Aviation Weather.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = station, onValueChange = onStationChange, modifier = Modifier.weight(1f), label = { Text("Airport ICAO") }, singleLine = true)
            Button(onClick = onRefresh, enabled = !loading, modifier = Modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(6.dp)) { Text(if (loading) "Loading" else "Refresh") }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = error ?: weatherText ?: "Select an airport and refresh to load the latest METAR.",
                modifier = Modifier.padding(16.dp),
                color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun NavigationDataPanel(
    plan: FlightPlan,
    navigationData: NavigationDataPackage?,
    message: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Navigation data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            navigationData?.cycle?.let { "AIRAC $it" }
                ?: plan.airacCycle.takeIf(String::isNotBlank)?.let { "AIRAC $it" }
                ?: "AIRAC cycle unavailable",
            style = MaterialTheme.typography.titleLarge,
        )
        Text("Install a Navmap Mobile navigation-data package to resolve airports, fixes, and procedures locally. Keep the desktop Little Navmap library current for connected planning.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
            Text(message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (FlightPlanCodec.canExportXPlaneFms(plan)) {
                "Every route point has coordinates. X-Plane FMS export is available."
            } else {
                "This route still has unresolved coordinates. Import an LNM/FMS plan or resolve it in Little Navmap before exporting to X-Plane."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(6.dp),
        ) { Text("Install navigation data") }
        if (!isConnected) {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) {
                Text("Connect Little Navmap")
            }
        } else {
            Text("Connected. Update the navigation database in Little Navmap on your computer, then reconnect this mobile companion.")
        }
    }
}

private fun updateProcedure(plan: FlightPlan, field: ProcedureField, value: String): FlightPlan = when (field) {
    ProcedureField.Sid -> plan.copy(departureProcedure = value)
    ProcedureField.Star -> plan.copy(arrivalProcedure = value)
    ProcedureField.Approach -> plan.copy(approach = value)
}

private fun NavigationDataPackage?.procedureOptions(
    plan: FlightPlan,
    field: ProcedureField,
): List<String> {
    val (airport, procedureType) = when (field) {
        ProcedureField.Sid -> plan.origin to ProcedureType.Sid
        ProcedureField.Star -> plan.destination to ProcedureType.Star
        ProcedureField.Approach -> plan.destination to ProcedureType.Approach
    }
    return this?.procedures(airport, procedureType).orEmpty()
}
