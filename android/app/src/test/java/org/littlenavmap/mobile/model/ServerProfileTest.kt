/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileTest {
    @Test
    fun `host uses defaults and normalizes case`() {
        val profile = ServerProfile.parse("  LNM-PC.Local  ", "", "HTTP").getOrThrow()

        assertEquals("http", profile.scheme)
        assertEquals("lnm-pc.local", profile.host)
        assertEquals(8965, profile.port)
        assertEquals("http://lnm-pc.local:8965/", profile.baseUrl)
        assertEquals("lnm-pc.local:8965", profile.displayName)
        assertNull(profile.validate())
    }

    @Test
    fun `complete URL supplies scheme and explicit port`() {
        val profile = ServerProfile.parse(
            "https://Example.COM:9443/",
            "8965",
            "http",
        ).getOrThrow()

        assertEquals(ServerProfile("https", "example.com", 9443), profile)
        assertEquals("https://example.com:9443/", profile.baseUrl)
    }

    @Test
    fun `complete URL without port uses port field`() {
        val profile = ServerProfile.parse(
            "https://example.com",
            "9000",
            "http",
        ).getOrThrow()

        assertEquals(9000, profile.port)
        assertEquals("https", profile.scheme)
    }

    @Test
    fun `host with embedded port is accepted`() {
        val profile = ServerProfile.parse("lnm-pc.local:9000", "8965", "http").getOrThrow()

        assertEquals("lnm-pc.local", profile.host)
        assertEquals(9000, profile.port)
    }

    @Test
    fun `IPv4 address is accepted`() {
        val profile = ServerProfile.parse("192.168.1.42", "8965", "http").getOrThrow()

        assertEquals("192.168.1.42", profile.host)
        assertEquals("http://192.168.1.42:8965/", profile.baseUrl)
    }

    @Test
    fun `bracketed IPv6 address is normalized and formatted`() {
        val profile = ServerProfile.parse("[2001:DB8::10]", "8965", "http").getOrThrow()

        assertEquals("2001:db8::10", profile.host)
        assertEquals("[2001:db8::10]:8965", profile.displayName)
        assertEquals("http://[2001:db8::10]:8965/", profile.baseUrl)
    }

    @Test
    fun `IPv6 URL and embedded raw port are accepted`() {
        val fromUrl = ServerProfile.parse(
            "https://[2001:db8::20]:9443/",
            "8965",
            "http",
        ).getOrThrow()
        val fromHost = ServerProfile.parse("[::1]:9000", "8965", "http").getOrThrow()

        assertEquals("2001:db8::20", fromUrl.host)
        assertEquals(9443, fromUrl.port)
        assertEquals("::1", fromHost.host)
        assertEquals(9000, fromHost.port)
    }

    @Test
    fun `port boundaries are accepted`() {
        assertEquals(1, ServerProfile.parse("localhost", "1", "http").getOrThrow().port)
        assertEquals(
            65_535,
            ServerProfile.parse("localhost", "65535", "http").getOrThrow().port,
        )
    }

    @Test
    fun `invalid ports are rejected`() {
        listOf("0", "65536", "-1", "+8965", "8.965", "port", "8965\n").forEach { port ->
            assertTrue("port $port must fail", ServerProfile.parse("localhost", port, "http").isFailure)
        }
        assertTrue(ServerProfile.parse("http://localhost:99999", "8965", "http").isFailure)
        assertTrue(ServerProfile.parse("[::1]:0", "8965", "http").isFailure)
    }

    @Test
    fun `unsupported schemes are rejected`() {
        assertTrue(ServerProfile.parse("localhost", "8965", "ftp").isFailure)
        assertTrue(ServerProfile.parse("ftp://localhost:8965", "8965", "http").isFailure)
    }

    @Test
    fun `paths queries fragments and credentials are rejected`() {
        val addresses = listOf(
            "http://localhost:8965/api/ui/info",
            "http://localhost:8965/?token=secret",
            "http://localhost:8965/#status",
            "http://user:secret@localhost:8965",
            "localhost/path",
            "localhost\\path",
            "localhost?query",
            "user@localhost",
        )

        addresses.forEach { address ->
            assertTrue("$address must fail", ServerProfile.parse(address, "8965", "http").isFailure)
        }
    }

    @Test
    fun `control characters and malformed IP addresses are rejected`() {
        val addresses = listOf(
            "localhost\r\nX-Injected: true",
            "localhost\u0000",
            "999.1.1.1",
            "192.168.01.1",
            "2001:db8::1",
            "[2001:db8::xyz]",
        )

        addresses.forEach { address ->
            assertTrue("$address must fail", ServerProfile.parse(address, "8965", "http").isFailure)
        }
    }

    @Test
    fun `direct profiles report validation errors without throwing`() {
        assertNotNull(ServerProfile(host = "").validate())
        assertNotNull(ServerProfile(host = "host/path").validate())
        assertNotNull(ServerProfile(host = "[hostname]").validate())
        assertNotNull(ServerProfile(host = "localhost", port = 0).validate())
        assertNotNull(ServerProfile(scheme = "file", host = "localhost").validate())
    }
}
