/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

private enum class AppSurface {
    Planner,
    Connection,
    LiveMap,
}

@Composable
fun LittleNavmapApp(
    viewModel: LittleNavmapViewModel,
    onExit: () -> Unit,
) {
    val state = viewModel.uiState
    var surface by rememberSaveable { mutableStateOf(AppSurface.Planner) }

    LaunchedEffect(surface, state.phase) {
        if (surface == AppSurface.Connection && state.phase == ConnectionPhase.Connected) {
            surface = AppSurface.LiveMap
        }
        if (surface == AppSurface.LiveMap && state.phase != ConnectionPhase.Connected) {
            surface = AppSurface.Planner
        }
    }

    when (surface) {
        AppSurface.Planner -> FlightPlanningScreen(
            plan = viewModel.flightPlan,
            onPlanChange = viewModel::updateFlightPlan,
            navigationData = viewModel.navigationData,
            onImportNavigationData = viewModel::importNavigationData,
            xPlaneState = viewModel.xPlaneUiState,
            onXPlaneHostChange = viewModel::updateXPlaneHost,
            onXPlanePortChange = viewModel::updateXPlanePort,
            onXPlaneConnect = viewModel::connectXPlane,
            onXPlaneRefresh = viewModel::refreshXPlane,
            isConnected = state.phase == ConnectionPhase.Connected,
            onConnect = { surface = AppSurface.Connection },
            onOpenLiveMap = { surface = AppSurface.LiveMap },
        )
        AppSurface.Connection -> ConnectionScreen(
            state = state,
            onSchemeChanged = viewModel::updateScheme,
            onAddressChanged = viewModel::updateAddress,
            onPortChanged = viewModel::updatePort,
            onConnect = viewModel::connect,
            onBack = { surface = AppSurface.Planner },
        )
        AppSurface.LiveMap -> {
            val profile = state.profile
            if (state.phase == ConnectionPhase.Connected && profile != null) {
                ConnectedScreen(
                    profile = profile,
                    keepScreenOn = state.keepScreenOn,
                    onKeepScreenOnChanged = viewModel::setKeepScreenOn,
                    onDisconnect = {
                        viewModel.disconnect()
                        surface = AppSurface.Planner
                    },
                    onExit = { surface = AppSurface.Planner },
                )
            }
        }
    }
}
