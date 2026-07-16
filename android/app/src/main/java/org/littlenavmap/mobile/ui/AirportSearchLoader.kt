/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import java.io.IOException
import java.io.Serializable
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.littlenavmap.mobile.model.AirportSnapshot
import org.littlenavmap.mobile.model.ServerProfile
import org.littlenavmap.mobile.network.AirportInfoHttpException
import org.littlenavmap.mobile.network.AirportInfoSource
import org.littlenavmap.mobile.network.AirportNotFoundException
import org.littlenavmap.mobile.network.InvalidAirportInfoResponseException

internal sealed interface AirportUiState : Serializable {
    data object Idle : AirportUiState
    data class Loading(val ident: String) : AirportUiState
    data class Found(val airport: AirportSnapshot) : AirportUiState
    data class NotFound(val ident: String) : AirportUiState
    data class Error(
        val ident: String,
        val failure: AirportDataFailure,
    ) : AirportUiState
    data class Stale(
        val ident: String,
        val airport: AirportSnapshot,
        val failure: AirportDataFailure,
    ) : AirportUiState
    data class Refreshing(
        val ident: String,
        val airport: AirportSnapshot,
        val previousFailure: AirportDataFailure? = null,
    ) : AirportUiState
}

internal sealed interface AirportDataFailure : Serializable {
    data object Timeout : AirportDataFailure
    data object HostNotFound : AirportDataFailure
    data object ServerUnavailable : AirportDataFailure
    data object SecureConnection : AirportDataFailure
    data class Http(val statusCode: Int) : AirportDataFailure
    data object InvalidResponse : AirportDataFailure
    data object Connection : AirportDataFailure
}

internal class AirportSearchLoader(
    private val source: AirportInfoSource,
) {
    fun states(profile: ServerProfile, ident: String): Flow<AirportUiState> = flow {
        emit(AirportUiState.Loading(ident))
        val result = try {
            AirportUiState.Found(source.fetch(profile, ident))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AirportNotFoundException) {
            AirportUiState.NotFound(ident)
        } catch (exception: Exception) {
            AirportUiState.Error(ident, exception.toAirportDataFailure())
        }
        emit(result)
    }

    private fun Throwable.toAirportDataFailure(): AirportDataFailure = when (this) {
        is SocketTimeoutException -> AirportDataFailure.Timeout
        is UnknownHostException -> AirportDataFailure.HostNotFound
        is ConnectException, is NoRouteToHostException -> AirportDataFailure.ServerUnavailable
        is SSLException -> AirportDataFailure.SecureConnection
        is AirportInfoHttpException -> AirportDataFailure.Http(statusCode)
        is InvalidAirportInfoResponseException -> AirportDataFailure.InvalidResponse
        is IOException -> AirportDataFailure.Connection
        else -> AirportDataFailure.InvalidResponse
    }
}

internal fun normalizeAirportIdent(input: String): Result<String> = runCatching {
    val ident = input.trim().uppercase(Locale.ROOT)
    require(ident.length in MIN_AIRPORT_IDENT_LENGTH..MAX_AIRPORT_IDENT_LENGTH)
    require(ident.all { it in 'A'..'Z' || it in '0'..'9' || it == '-' })
    ident
}

private const val MIN_AIRPORT_IDENT_LENGTH = 2
private const val MAX_AIRPORT_IDENT_LENGTH = 12
