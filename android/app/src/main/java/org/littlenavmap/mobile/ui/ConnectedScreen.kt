/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.littlenavmap.mobile.R
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.network.AirportInfoClient

internal enum class PageDestination(
    @StringRes val labelResource: Int,
    @DrawableRes val iconResource: Int,
    val clickScript: String?,
) {
    Map(R.string.map, R.drawable.ic_map, WebScripts.CLICK_MAP),
    FlightPlan(R.string.flight_plan, R.drawable.ic_route, WebScripts.CLICK_FLIGHT_PLAN),
    Aircraft(R.string.aircraft, R.drawable.ic_plane, null),
    Progress(R.string.progress, R.drawable.ic_progress, WebScripts.CLICK_PROGRESS),
    Airports(R.string.airports, R.drawable.ic_airport, null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectedScreen(
    profile: ServerProfile,
    keepScreenOn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var destination by rememberSaveable(profile.baseUrl) {
        mutableStateOf(PageDestination.Map)
    }
    var webView by remember(profile.baseUrl) { mutableStateOf<WebView?>(null) }
    var isLoading by rememberSaveable(profile.baseUrl) { mutableStateOf(true) }
    var webError by rememberSaveable(profile.baseUrl) { mutableStateOf<String?>(null) }
    var aircraftRefreshKey by rememberSaveable(profile.baseUrl) { mutableIntStateOf(0) }
    var airportQuery by rememberSaveable(profile.baseUrl) { mutableStateOf("") }
    var submittedAirportIdent by rememberSaveable(profile.baseUrl) {
        mutableStateOf<String?>(null)
    }
    var airportRefreshKey by rememberSaveable(profile.baseUrl) { mutableIntStateOf(0) }
    var airportState by rememberSaveable(profile.baseUrl) {
        mutableStateOf<AirportUiState>(AirportUiState.Idle)
    }
    var loadedAirportQuery by rememberSaveable(profile.baseUrl) {
        mutableStateOf<String?>(null)
    }
    val airportLoader = remember(profile.baseUrl) {
        AirportSearchLoader(AirportInfoClient())
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLegalNotices by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(profile.baseUrl, submittedAirportIdent, airportRefreshKey) {
        val ident = submittedAirportIdent
        if (ident == null) {
            airportState = AirportUiState.Idle
        } else {
            val previousAirport = when (val current = airportState) {
                is AirportUiState.Found -> current.airport
                is AirportUiState.Stale -> current.airport
                is AirportUiState.Refreshing -> current.airport
                else -> null
            }.takeIf { loadedAirportQuery == ident }
            val previousFailure = when (val current = airportState) {
                is AirportUiState.Stale -> current.failure
                is AirportUiState.Refreshing -> current.previousFailure
                else -> null
            }
            airportLoader.states(profile, ident).collect { nextState ->
                airportState = when {
                    nextState is AirportUiState.Loading && previousAirport != null -> {
                        AirportUiState.Refreshing(
                            ident = ident,
                            airport = previousAirport,
                            previousFailure = previousFailure,
                        )
                    }
                    nextState is AirportUiState.Error && previousAirport != null -> {
                        AirportUiState.Stale(
                            ident = ident,
                            airport = previousAirport,
                            failure = nextState.failure,
                        )
                    }
                    else -> nextState
                }
                if (nextState is AirportUiState.Found) loadedAirportQuery = ident
            }
        }
    }

    fun refresh() {
        when (destination) {
            PageDestination.Aircraft -> {
                aircraftRefreshKey += 1
                return
            }
            PageDestination.Airports -> {
                val requestInProgress = airportState is AirportUiState.Loading ||
                    airportState is AirportUiState.Refreshing
                if (submittedAirportIdent != null && !requestInProgress) {
                    airportRefreshKey += 1
                }
                return
            }
            else -> Unit
        }
        webError = null
        isLoading = true
        webView?.reload()
    }

    BackHandler(enabled = !showSettings) {
        if (destination != PageDestination.Map) {
            destination = PageDestination.Map
            PageDestination.Map.clickScript?.let { script ->
                webView?.evaluateJavascript(script, null)
            }
        } else {
            onExit()
        }
    }
    BackHandler(enabled = showSettings) {
        if (showLegalNotices) {
            showLegalNotices = false
        } else {
            showSettings = false
        }
    }

    DisposableEffect(webView, lifecycleOwner, destination) {
        val webContentShouldRun = destination != PageDestination.Aircraft &&
            destination != PageDestination.Airports

        fun updateWebViewLifecycle() {
            if (
                webContentShouldRun &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                webView?.onResume()
                webView?.resumeTimers()
            } else {
                webView?.onPause()
                webView?.pauseTimers()
            }
        }

        updateWebViewLifecycle()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> updateWebViewLifecycle()
                Lifecycle.Event.ON_PAUSE -> {
                    webView?.onPause()
                    webView?.pauseTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.onPause()
            webView?.pauseTimers()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(7.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                            ) { }
                            Text(
                                text = stringResource(R.string.connected_to, profile.displayName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = ::refresh,
                        enabled = destination != PageDestination.Airports ||
                            submittedAirportIdent != null &&
                            airportState !is AirportUiState.Loading &&
                            airportState !is AirportUiState.Refreshing,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.reload),
                        )
                    }
                    IconButton(
                        onClick = {
                            showLegalNotices = false
                            showSettings = true
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val onDestinationSelected: (PageDestination) -> Unit = { selected ->
                destination = selected
                selected.clickScript?.let { script ->
                    webView?.evaluateJavascript(script, null)
                }
            }

            val useNavigationRail = maxWidth >= TABLET_BREAKPOINT &&
                maxHeight >= TABLET_MIN_HEIGHT

            Row(Modifier.fillMaxSize()) {
                if (useNavigationRail) {
                    DestinationRail(
                        selected = destination,
                        onSelected = onDestinationSelected,
                    )
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                }
                Column(Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) {
                        val isWebContentVisible = destination != PageDestination.Aircraft &&
                            destination != PageDestination.Airports
                        WebContent(
                            profile = profile,
                            destination = destination,
                            isVisible = isWebContentVisible,
                            isLoading = isLoading,
                            errorMessage = webError,
                            onWebViewReady = { webView = it },
                            onLoadingChanged = { isLoading = it },
                            onLoaded = { webError = null },
                            onError = { webError = it },
                            onRetry = ::refresh,
                            modifier = Modifier.fillMaxSize(),
                        )
                        when (destination) {
                            PageDestination.Aircraft -> AircraftScreen(
                                profile = profile,
                                refreshKey = aircraftRefreshKey,
                                onRefresh = ::refresh,
                                modifier = Modifier.fillMaxSize(),
                            )
                            PageDestination.Airports -> AirportScreen(
                                profile = profile,
                                query = airportQuery,
                                state = airportState,
                                onQueryChanged = { airportQuery = it },
                                onSearch = { ident ->
                                    airportQuery = ident
                                    submittedAirportIdent = ident
                                    airportRefreshKey += 1
                                },
                                onRetry = ::refresh,
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> Unit
                        }
                    }
                    if (!useNavigationRail) {
                        DestinationBar(
                            selected = destination,
                            onSelected = onDestinationSelected,
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = {
                showLegalNotices = false
                showSettings = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        ) {
            if (showLegalNotices) {
                LegalNoticesContent(onBack = { showLegalNotices = false })
            } else {
                BackHandler { showSettings = false }
                SettingsContent(
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChanged = onKeepScreenOnChanged,
                    onOpenBrowser = {
                        val browserUrl = if (destination.clickScript == null) {
                            profile.baseUrl
                        } else {
                            webView?.url ?: profile.baseUrl
                        }
                        openExternalUri(context, browserUrl)
                        showSettings = false
                    },
                    onOpenLegalNotices = { showLegalNotices = true },
                    onDisconnect = {
                        showSettings = false
                        onDisconnect()
                    },
                )
            }
        }
    }
}

@Composable
private fun WebContent(
    profile: ServerProfile,
    destination: PageDestination,
    isVisible: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onWebViewReady: (WebView) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onLoaded: () -> Unit,
    onError: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LittleNavmapWebView(
            profile = profile,
            destination = destination,
            isVisible = isVisible,
            onWebViewReady = onWebViewReady,
            onLoadingChanged = onLoadingChanged,
            onMainFrameLoaded = onLoaded,
            onMainFrameError = onError,
            modifier = Modifier.fillMaxSize(),
        )

        if (isVisible && isLoading && errorMessage == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }

        if (isVisible && errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.connection_failed),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(20.dp))
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationBar(
    selected: PageDestination,
    onSelected: (PageDestination) -> Unit,
) {
    NavigationBar(windowInsets = WindowInsets(0)) {
        PageDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconResource),
                        contentDescription = stringResource(destination.labelResource),
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelResource),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.heightIn(min = 64.dp),
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun DestinationRail(
    selected: PageDestination,
    onSelected: (PageDestination) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        windowInsets = WindowInsets(0),
    ) {
        Spacer(Modifier.size(8.dp))
        PageDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconResource),
                        contentDescription = stringResource(destination.labelResource),
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelResource),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun SettingsContent(
    keepScreenOn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenLegalNotices: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Text(
        text = stringResource(R.string.settings),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = keepScreenOn,
                role = Role.Switch,
                onValueChange = onKeepScreenOnChanged,
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.keep_screen_on),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = keepScreenOn,
            onCheckedChange = null,
        )
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.open_browser)) },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_external_link),
                contentDescription = null,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpenBrowser),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.legal_notices)) },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpenLegalNotices),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    HorizontalDivider()
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.disconnect),
                color = MaterialTheme.colorScheme.error,
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_unlink),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onDisconnect),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    Spacer(Modifier.navigationBarsPadding())
}

private val TABLET_BREAKPOINT = 700.dp
private val TABLET_MIN_HEIGHT = 480.dp
