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
import androidx.compose.runtime.CompositionLocalProvider

private enum class AppSurface {
    Planner,
    Connection,
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
            surface = AppSurface.Planner
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides viewModel.appLanguage) {
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
            appLanguage = viewModel.appLanguage,
            onLanguageChange = viewModel::setAppLanguage,
            simBriefState = viewModel.simBriefUiState,
            onSimBriefUsernameChange = viewModel::updateSimBriefUsername,
            onSimBriefImport = viewModel::importSimBrief,
            navigraphState = viewModel.navigraphUiState,
            onNavigraphExportUrlChange = viewModel::updateNavigraphExportUrl,
            onNavigraphTokenChange = viewModel::updateNavigraphAccessToken,
            onNavigraphImport = viewModel::importNavigraph,
            isConnected = state.phase == ConnectionPhase.Connected,
            littleNavmapProfile = state.profile,
            routeResolutionState = viewModel.routeResolutionUiState,
            onResolveRouteWithLittleNavmap = viewModel::resolveRouteWithLittleNavmap,
            onConnect = { surface = AppSurface.Connection },
        )
        AppSurface.Connection -> ConnectionScreen(
            state = state,
            onSchemeChanged = viewModel::updateScheme,
            onAddressChanged = viewModel::updateAddress,
            onPortChanged = viewModel::updatePort,
            onConnect = viewModel::connect,
            onBack = { surface = AppSurface.Planner },
            appLanguage = viewModel.appLanguage,
            onLanguageChange = viewModel::setAppLanguage,
            simBriefState = viewModel.simBriefUiState,
            onSimBriefUsernameChange = viewModel::updateSimBriefUsername,
            onSimBriefImport = viewModel::importSimBrief,
            navigraphState = viewModel.navigraphUiState,
            onNavigraphExportUrlChange = viewModel::updateNavigraphExportUrl,
            onNavigraphTokenChange = viewModel::updateNavigraphAccessToken,
            onNavigraphImport = viewModel::importNavigraph,
        )
    }
    }
}
