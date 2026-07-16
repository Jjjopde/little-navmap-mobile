/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.littlenavmap.mobile.R
import org.littlenavmap.mobile.data.NavigationDataRepository
import org.littlenavmap.mobile.data.PreferencesRepository
import org.littlenavmap.mobile.model.FlightPlan
import org.littlenavmap.mobile.model.NavigationDataPackage
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.model.XPlaneEndpoint
import org.littlenavmap.mobile.model.XPlaneSnapshot
import org.littlenavmap.mobile.network.ServerProbe
import org.littlenavmap.mobile.network.NavigraphFlightPlanClient
import org.littlenavmap.mobile.network.SimBriefFlightPlanClient
import org.littlenavmap.mobile.network.XPlaneRrefClient

internal enum class ConnectionPhase {
    Idle,
    Connecting,
    Connected,
    Error,
}

internal enum class XPlanePhase {
    Idle,
    Connecting,
    Connected,
    Error,
}

internal data class LittleNavmapUiState(
    val scheme: String = ServerProfile.DEFAULT_SCHEME,
    val address: String = "",
    val port: String = ServerProfile.DEFAULT_PORT.toString(),
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val profile: ServerProfile? = null,
    val errorMessage: String? = null,
    val keepScreenOn: Boolean = true,
)

internal data class XPlaneUiState(
    val host: String = "",
    val port: String = XPlaneEndpoint.DEFAULT_PORT.toString(),
    val phase: XPlanePhase = XPlanePhase.Idle,
    val endpoint: XPlaneEndpoint? = null,
    val snapshot: XPlaneSnapshot? = null,
    val errorMessage: String? = null,
)

internal data class SimBriefUiState(
    val username: String = "",
    val isImporting: Boolean = false,
    val message: String? = null,
)

/** Credentials are intentionally transient and never written to SharedPreferences. */
internal data class NavigraphUiState(
    val exportUrl: String = "",
    val accessToken: String = "",
    val isImporting: Boolean = false,
    val message: String? = null,
)

class LittleNavmapViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = PreferencesRepository(application)
    private val navigationDataRepository = NavigationDataRepository(application)
    private val serverProbe = ServerProbe()
    private val xPlaneClient = XPlaneRrefClient()
    private val simBriefClient = SimBriefFlightPlanClient()
    private val navigraphClient = NavigraphFlightPlanClient()
    private var probeJob: Job? = null
    private var xPlaneJob: Job? = null
    private var cloudImportJob: Job? = null

    internal var uiState by mutableStateOf(
        LittleNavmapUiState(keepScreenOn = preferences.keepScreenOn()),
    )
        private set

    internal var flightPlan by mutableStateOf(preferences.loadFlightPlan())
        private set

    internal var navigationData by mutableStateOf(navigationDataRepository.load())
        private set

    internal var appLanguage by mutableStateOf(preferences.appLanguage())
        private set

    internal var xPlaneUiState by mutableStateOf(
        preferences.loadXPlaneEndpoint()?.let { endpoint ->
            XPlaneUiState(host = endpoint.host, port = endpoint.port.toString())
        } ?: XPlaneUiState(),
    )
        private set

    internal var simBriefUiState by mutableStateOf(SimBriefUiState())
        private set

    internal var navigraphUiState by mutableStateOf(NavigraphUiState())
        private set

    init {
        runCatching(preferences::loadProfile).getOrNull()?.let { savedProfile ->
            uiState = uiState.copy(
                scheme = savedProfile.scheme,
                address = savedProfile.host,
                port = savedProfile.port.toString(),
            )
            probe(savedProfile)
        }
    }

    internal fun updateScheme(scheme: String) {
        if (uiState.phase == ConnectionPhase.Connecting) return
        uiState = uiState.copy(
            scheme = scheme,
            phase = uiState.phase.formPhaseAfterEdit(),
            errorMessage = null,
        )
    }

    internal fun updateAddress(address: String) {
        if (uiState.phase == ConnectionPhase.Connecting) return
        uiState = uiState.copy(
            address = address,
            phase = uiState.phase.formPhaseAfterEdit(),
            errorMessage = null,
        )
    }

    internal fun updatePort(port: String) {
        if (uiState.phase == ConnectionPhase.Connecting) return
        uiState = uiState.copy(
            port = port.filter(Char::isDigit).take(MAX_PORT_LENGTH),
            phase = uiState.phase.formPhaseAfterEdit(),
            errorMessage = null,
        )
    }

    internal fun connect() {
        ServerProfile.parse(
            address = uiState.address,
            portText = uiState.port,
            scheme = uiState.scheme,
        ).fold(
            onSuccess = ::probe,
            onFailure = { exception ->
                uiState = uiState.copy(
                    phase = ConnectionPhase.Error,
                    profile = null,
                    errorMessage = exception.message
                        ?: getApplication<Application>().getString(R.string.invalid_address),
                )
            },
        )
    }

    internal fun disconnect() {
        probeJob?.cancel()
        preferences.clearProfile()
        uiState = uiState.copy(
            phase = ConnectionPhase.Idle,
            profile = null,
            errorMessage = null,
        )
    }

    internal fun setKeepScreenOn(enabled: Boolean) {
        uiState = uiState.copy(keepScreenOn = enabled)
        preferences.setKeepScreenOn(enabled)
    }

    internal fun updateFlightPlan(plan: FlightPlan) {
        flightPlan = navigationData?.resolve(plan) ?: plan
        preferences.saveFlightPlan(flightPlan)
    }

    internal fun setAppLanguage(language: AppLanguage) {
        appLanguage = language
        preferences.setAppLanguage(language)
    }

    internal fun updateSimBriefUsername(username: String) {
        if (!simBriefUiState.isImporting) {
            simBriefUiState = simBriefUiState.copy(username = username.take(MAX_USERNAME_LENGTH), message = null)
        }
    }

    internal fun importSimBrief() {
        cloudImportJob?.cancel()
        simBriefUiState = simBriefUiState.copy(isImporting = true, message = null)
        cloudImportJob = viewModelScope.launch {
            runCatching { simBriefClient.fetch(simBriefUiState.username) }
                .onSuccess { imported ->
                    updateFlightPlan(imported)
                    simBriefUiState = simBriefUiState.copy(
                        isImporting = false,
                        message = "SimBrief flight plan imported.",
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    simBriefUiState = simBriefUiState.copy(
                        isImporting = false,
                        message = error.message ?: "SimBrief import failed.",
                    )
                }
        }
    }

    internal fun updateNavigraphExportUrl(url: String) {
        if (!navigraphUiState.isImporting) {
            navigraphUiState = navigraphUiState.copy(exportUrl = url.take(MAX_CLOUD_FIELD_LENGTH), message = null)
        }
    }

    internal fun updateNavigraphAccessToken(token: String) {
        if (!navigraphUiState.isImporting) {
            navigraphUiState = navigraphUiState.copy(accessToken = token.take(MAX_CLOUD_FIELD_LENGTH), message = null)
        }
    }

    internal fun importNavigraph() {
        cloudImportJob?.cancel()
        navigraphUiState = navigraphUiState.copy(isImporting = true, message = null)
        cloudImportJob = viewModelScope.launch {
            runCatching {
                navigraphClient.fetch(
                    exportUrl = navigraphUiState.exportUrl,
                    accessToken = navigraphUiState.accessToken,
                )
            }.onSuccess { imported ->
                updateFlightPlan(imported)
                navigraphUiState = navigraphUiState.copy(
                    accessToken = "",
                    isImporting = false,
                    message = "Navigraph flight plan imported.",
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                navigraphUiState = navigraphUiState.copy(
                    isImporting = false,
                    message = error.message ?: "Navigraph import failed.",
                )
            }
        }
    }

    internal fun importNavigationData(content: String): Result<NavigationDataPackage> = runCatching {
        val data = navigationDataRepository.replace(content)
        navigationData = data
        flightPlan = data.resolve(flightPlan)
        preferences.saveFlightPlan(flightPlan)
        data
    }

    internal fun updateXPlaneHost(host: String) {
        if (xPlaneUiState.phase == XPlanePhase.Connecting) return
        xPlaneUiState = xPlaneUiState.copy(
            host = host,
            phase = xPlaneUiState.phase.afterEdit(),
            endpoint = null,
            errorMessage = null,
        )
    }

    internal fun updateXPlanePort(port: String) {
        if (xPlaneUiState.phase == XPlanePhase.Connecting) return
        xPlaneUiState = xPlaneUiState.copy(
            port = port.filter(Char::isDigit).take(MAX_PORT_LENGTH),
            phase = xPlaneUiState.phase.afterEdit(),
            endpoint = null,
            errorMessage = null,
        )
    }

    internal fun connectXPlane() {
        XPlaneEndpoint.parse(xPlaneUiState.host, xPlaneUiState.port).fold(
            onSuccess = ::readXPlane,
            onFailure = { error ->
                xPlaneUiState = xPlaneUiState.copy(
                    phase = XPlanePhase.Error,
                    endpoint = null,
                    errorMessage = error.message ?: "Enter a valid X-Plane address and UDP port.",
                )
            },
        )
    }

    internal fun refreshXPlane() {
        xPlaneUiState.endpoint?.let(::readXPlane) ?: connectXPlane()
    }

    private fun readXPlane(endpoint: XPlaneEndpoint) {
        xPlaneJob?.cancel()
        xPlaneUiState = xPlaneUiState.copy(
            host = endpoint.host,
            port = endpoint.port.toString(),
            phase = XPlanePhase.Connecting,
            endpoint = endpoint,
            errorMessage = null,
        )
        xPlaneJob = viewModelScope.launch {
            val result = try {
                Result.success(xPlaneClient.read(endpoint.host, endpoint.port))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
            result.fold(
                onSuccess = { snapshot ->
                    preferences.saveXPlaneEndpoint(endpoint)
                    xPlaneUiState = xPlaneUiState.copy(
                        phase = XPlanePhase.Connected,
                        endpoint = endpoint,
                        snapshot = snapshot,
                        errorMessage = null,
                    )
                },
                onFailure = { error ->
                    xPlaneUiState = xPlaneUiState.copy(
                        phase = XPlanePhase.Error,
                        endpoint = endpoint,
                        errorMessage = error.message ?: "Unable to read X-Plane UDP data.",
                    )
                },
            )
        }
    }

    private fun probe(profile: ServerProfile) {
        probeJob?.cancel()
        uiState = uiState.copy(
            scheme = profile.scheme,
            address = profile.host,
            port = profile.port.toString(),
            phase = ConnectionPhase.Connecting,
            profile = null,
            errorMessage = null,
        )

        probeJob = viewModelScope.launch {
            val result = try {
                serverProbe.probe(profile)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
            result.fold(
                onSuccess = {
                    preferences.saveProfile(profile)
                    uiState = uiState.copy(
                        phase = ConnectionPhase.Connected,
                        profile = profile,
                        errorMessage = null,
                    )
                },
                onFailure = { exception ->
                    uiState = uiState.copy(
                        phase = ConnectionPhase.Error,
                        profile = null,
                        errorMessage = exception.message
                            ?: getApplication<Application>().getString(
                                R.string.connection_failed,
                            ),
                    )
                },
            )
        }
    }

    private fun ConnectionPhase.formPhaseAfterEdit(): ConnectionPhase =
        if (this == ConnectionPhase.Error) ConnectionPhase.Idle else this

    private fun XPlanePhase.afterEdit(): XPlanePhase =
        if (this == XPlanePhase.Error || this == XPlanePhase.Connected) XPlanePhase.Idle else this

    private companion object {
        const val MAX_PORT_LENGTH = 5
        const val MAX_USERNAME_LENGTH = 64
        const val MAX_CLOUD_FIELD_LENGTH = 2_000
    }
}
