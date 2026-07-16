/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.model

data class XPlaneEndpoint(
    val host: String,
    val port: Int = DEFAULT_PORT,
) {
    fun validate(): String? = ServerProfile(scheme = "http", host = host, port = port).validate()

    companion object {
        const val DEFAULT_PORT = 49_000

        fun parse(host: String, portText: String): Result<XPlaneEndpoint> =
            ServerProfile.parse(host, portText, "http").map { profile ->
                XPlaneEndpoint(host = profile.host, port = profile.port)
            }
    }
}
