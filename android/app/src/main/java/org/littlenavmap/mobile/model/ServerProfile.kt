/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.model

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Connection settings for a Little Navmap web server. */
data class ServerProfile(
    val scheme: String = DEFAULT_SCHEME,
    val host: String,
    val port: Int = DEFAULT_PORT,
) {
    /** A copy suitable for persistence and URL generation. */
    val normalized: ServerProfile
        get() = copy(
            scheme = scheme.trim().lowercase(Locale.ROOT),
            host = normalizeHost(host),
        )

    /** The server root, always ending in a slash. */
    val baseUrl: String
        get() {
            val value = normalized
            return "${value.scheme}://${formatHost(value.host)}:${value.port}/"
        }

    /** A concise address for connection screens and error messages. */
    val displayName: String
        get() {
            val value = normalized
            return "${formatHost(value.host)}:${value.port}"
        }

    /** Returns a user-facing validation error, or null when the profile is usable. */
    fun validate(): String? {
        val normalizedScheme = scheme.trim().lowercase(Locale.ROOT)
        if (normalizedScheme !in SUPPORTED_SCHEMES) {
            return "Protocol must be HTTP or HTTPS."
        }
        if (port !in MIN_PORT..MAX_PORT) {
            return "Port must be between $MIN_PORT and $MAX_PORT."
        }

        val normalizedHost = normalizeHost(host)
        if (normalizedHost.isEmpty()) {
            return "Server address is required."
        }
        if (normalizedHost.any { it.isWhitespace() || it.isISOControl() }) {
            return "Server address cannot contain spaces or control characters."
        }
        if (normalizedHost.any { it in FORBIDDEN_HOST_CHARACTERS }) {
            return "Enter only a server name or IP address, without a path or credentials."
        }
        if (normalizedHost.contains(':')) {
            if (!isValidIpv6(normalizedHost)) {
                return "IPv6 address is invalid."
            }
            return null
        }
        if (normalizedHost.all { it.isDigit() || it == '.' }) {
            if (!isValidIpv4(normalizedHost)) {
                return "IPv4 address is invalid."
            }
            return null
        }
        if (!isValidHostname(normalizedHost)) {
            return "Server name is invalid."
        }
        return null
    }

    companion object {
        const val DEFAULT_SCHEME = "http"
        const val DEFAULT_PORT = 8965

        private const val MIN_PORT = 1
        private const val MAX_PORT = 65_535
        private val SUPPORTED_SCHEMES = setOf("http", "https")
        private val FORBIDDEN_HOST_CHARACTERS = charArrayOf(
            '/', '\\', '?', '#', '@', '[', ']', '%',
        )
        private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
        private val PORT_TEXT = Regex("[0-9]+")
        private val HOST_LABEL = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        /**
         * Parses either a host/IP address or a complete HTTP(S) URL.
         *
         * A scheme and port written in [address] take precedence over the separate fields.
         */
        fun parse(
            address: String,
            portText: String,
            scheme: String,
        ): Result<ServerProfile> = runCatching {
            require(address.none { it.isISOControl() }) {
                "Server address cannot contain control characters."
            }
            val input = address.trim()
            require(input.isNotEmpty()) { "Server address is required." }

            val fieldPort = parsePort(portText)
            val parsed = when {
                SCHEME_PREFIX.containsMatchIn(input) -> parseUrl(input, fieldPort)
                input.contains("://") -> throw IllegalArgumentException("Server URL is invalid.")
                else -> parseHost(input, fieldPort, scheme)
            }
            parsed.validate()?.let { throw IllegalArgumentException(it) }
            parsed.normalized
        }

        private fun parseUrl(input: String, fieldPort: Int): ServerProfile {
            val uri = try {
                URI(input)
            } catch (exception: URISyntaxException) {
                throw IllegalArgumentException("Server URL is invalid.", exception)
            }

            require(uri.isAbsolute && !uri.isOpaque) { "Server URL is invalid." }
            require(uri.scheme.lowercase(Locale.ROOT) in SUPPORTED_SCHEMES) {
                "Protocol must be HTTP or HTTPS."
            }
            require(uri.rawUserInfo == null) { "Server URL cannot contain credentials." }
            require(uri.rawQuery == null) { "Server URL cannot contain a query." }
            require(uri.rawFragment == null) { "Server URL cannot contain a fragment." }
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
                "Server URL cannot contain a path."
            }
            require(!uri.host.isNullOrBlank()) { "Server URL must contain a valid host." }

            return ServerProfile(
                scheme = uri.scheme,
                host = uri.host,
                port = if (uri.port == -1) fieldPort else uri.port,
            )
        }

        private fun parseHost(input: String, fieldPort: Int, scheme: String): ServerProfile {
            require(input.none { it == '/' || it == '\\' || it == '?' || it == '#' || it == '@' }) {
                "Enter only a server name or IP address, without a path or credentials."
            }

            val (parsedHost, embeddedPort) = if (input.startsWith('[')) {
                parseBracketedIpv6(input)
            } else {
                val colonCount = input.count { it == ':' }
                when (colonCount) {
                    0 -> input to null
                    1 -> {
                        val separator = input.lastIndexOf(':')
                        val rawHost = input.substring(0, separator)
                        require(rawHost.isNotEmpty()) { "Server address is required." }
                        rawHost to parseRequiredPort(input.substring(separator + 1))
                    }
                    else -> throw IllegalArgumentException(
                        "IPv6 addresses must be enclosed in square brackets.",
                    )
                }
            }

            return ServerProfile(
                scheme = scheme,
                host = parsedHost,
                port = embeddedPort ?: fieldPort,
            )
        }

        private fun parseBracketedIpv6(input: String): Pair<String, Int?> {
            val closeBracket = input.indexOf(']')
            require(closeBracket > 1) { "IPv6 address is invalid." }

            val host = input.substring(1, closeBracket)
            val suffix = input.substring(closeBracket + 1)
            val port = when {
                suffix.isEmpty() -> null
                suffix.startsWith(':') -> parseRequiredPort(suffix.substring(1))
                else -> throw IllegalArgumentException("IPv6 address is invalid.")
            }
            return host to port
        }

        private fun parsePort(portText: String): Int {
            require(portText.none { it.isISOControl() }) { "Port must be a number." }
            val value = portText.trim()
            if (value.isEmpty()) return DEFAULT_PORT
            return parseRequiredPort(value)
        }

        private fun parseRequiredPort(value: String): Int {
            require(PORT_TEXT.matches(value)) { "Port must be a number." }
            val port = value.toIntOrNull()
                ?: throw IllegalArgumentException("Port must be between $MIN_PORT and $MAX_PORT.")
            require(port in MIN_PORT..MAX_PORT) {
                "Port must be between $MIN_PORT and $MAX_PORT."
            }
            return port
        }

        private fun normalizeHost(value: String): String {
            val trimmed = value.trim()
            val unwrapped = if (
                trimmed.length >= 2 &&
                trimmed.first() == '[' &&
                trimmed.last() == ']' &&
                ':' in trimmed
            ) {
                trimmed.substring(1, trimmed.lastIndex)
            } else {
                trimmed
            }
            return unwrapped.lowercase(Locale.ROOT)
        }

        private fun formatHost(value: String): String =
            if (value.contains(':')) "[$value]" else value

        private fun isValidIpv4(value: String): Boolean {
            val octets = value.split('.')
            return octets.size == 4 && octets.all { octet ->
                val number = octet.toIntOrNull()
                octet.isNotEmpty() &&
                    octet.length <= 3 &&
                    (octet.length == 1 || !octet.startsWith('0')) &&
                    number != null &&
                    number in 0..255
            }
        }

        private fun isValidIpv6(value: String): Boolean = try {
            InetAddress.getByName(value) is Inet6Address
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.net.UnknownHostException) {
            false
        }

        private fun isValidHostname(value: String): Boolean {
            val ascii = try {
                IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES)
            } catch (_: IllegalArgumentException) {
                return false
            }
            if (ascii.length !in 1..253) return false
            val withoutRootDot = ascii.removeSuffix(".")
            if (withoutRootDot.isEmpty()) return false
            return withoutRootDot.split('.').all(HOST_LABEL::matches)
        }
    }
}
