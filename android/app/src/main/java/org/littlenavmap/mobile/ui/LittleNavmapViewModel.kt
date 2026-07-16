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
import org.littlenavmap.mobile.network.ServerProbe

internal enum class ConnectionPhase {
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

class LittleNavmapViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = PreferencesRepository(application)
    private val navigationDataRepository = NavigationDataRepository(application)
    private val serverProbe = ServerProbe()
    private var probeJob: Job? = null

    internal var uiState by mutableStateOf(
        LittleNavmapUiState(keepScreenOn = preferences.keepScreenOn()),
    )
        private set

    internal var flightPlan by mutableStateOf(preferences.loadFlightPlan())
        private set

    internal var navigationData by mutableStateOf(navigationDataRepository.load())
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

    internal fun importNavigationData(content: String): Result<NavigationDataPackage> = runCatching {
        val data = navigationDataRepository.replace(content)
        navigationData = data
        flightPlan = data.resolve(flightPlan)
        preferences.saveFlightPlan(flightPlan)
        data
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

    private companion object {
        const val MAX_PORT_LENGTH = 5
    }
}
