/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.littlenavmap.mobile.model.AircraftSnapshot
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.model.toAircraftSnapshot
import org.littlenavmap.mobile.network.InvalidSimInfoResponseException
import org.littlenavmap.mobile.network.SimInfoHttpException
import org.littlenavmap.mobile.network.SimInfoSource

internal sealed interface AircraftUiState {
    data object Loading : AircraftUiState
    data object SimulatorInactive : AircraftUiState
    data class Active(val snapshot: AircraftSnapshot) : AircraftUiState
    data class Stale(
        val snapshot: AircraftSnapshot,
        val failure: AircraftDataFailure,
    ) : AircraftUiState
    data class Error(val failure: AircraftDataFailure) : AircraftUiState
}

internal sealed interface AircraftDataFailure {
    data object Timeout : AircraftDataFailure
    data object HostNotFound : AircraftDataFailure
    data object ServerUnavailable : AircraftDataFailure
    data object SecureConnection : AircraftDataFailure
    data class Http(val statusCode: Int) : AircraftDataFailure
    data object InvalidResponse : AircraftDataFailure
    data object Connection : AircraftDataFailure
}

/** Produces one immediate sample and then polls while its collector remains active. */
internal class AircraftStatusPoller(
    private val source: SimInfoSource,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    init {
        require(pollIntervalMillis > 0)
    }

    fun states(profile: ServerProfile): Flow<AircraftUiState> = flow {
        emit(AircraftUiState.Loading)
        var lastSnapshot: AircraftSnapshot? = null

        while (currentCoroutineContext().isActive) {
            val state = try {
                val response = source.fetch(profile)
                if (response.active) {
                    val snapshot = response.toAircraftSnapshot(nowMillis())
                    lastSnapshot = snapshot
                    AircraftUiState.Active(snapshot)
                } else {
                    lastSnapshot = null
                    AircraftUiState.SimulatorInactive
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val failure = exception.toAircraftDataFailure()
                lastSnapshot?.let { AircraftUiState.Stale(it, failure) }
                    ?: AircraftUiState.Error(failure)
            }

            emit(state)
            delay(pollIntervalMillis)
        }
    }

    private fun Throwable.toAircraftDataFailure(): AircraftDataFailure = when (this) {
        is SocketTimeoutException -> AircraftDataFailure.Timeout
        is UnknownHostException -> AircraftDataFailure.HostNotFound
        is ConnectException, is NoRouteToHostException -> AircraftDataFailure.ServerUnavailable
        is SSLException -> AircraftDataFailure.SecureConnection
        is SimInfoHttpException -> AircraftDataFailure.Http(statusCode)
        is InvalidSimInfoResponseException -> AircraftDataFailure.InvalidResponse
        is IOException -> AircraftDataFailure.Connection
        else -> AircraftDataFailure.InvalidResponse
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 1_000L
    }
}
