package com.mobileftp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigValidatorsTest {

    @Test fun port() {
        assertNull(ConfigValidators.validatePort("21"))
        assertNull(ConfigValidators.validatePort("65535"))
        assertNotNull(ConfigValidators.validatePort(""))
        assertNotNull(ConfigValidators.validatePort("0"))
        assertNotNull(ConfigValidators.validatePort("65536"))
        assertNotNull(ConfigValidators.validatePort("abc"))
        // Server must use an unprivileged port.
        assertNotNull(ConfigValidators.validatePort("21", ConfigValidators.MIN_UNPRIVILEGED_PORT))
        assertNull(ConfigValidators.validatePort("2121", ConfigValidators.MIN_UNPRIVILEGED_PORT))
    }

    @Test fun host() {
        assertNull(ConfigValidators.validateHost("192.168.1.10"))
        assertNull(ConfigValidators.validateHost("nas.local"))
        assertNull(ConfigValidators.validateHost("ftp.example.com"))
        assertNull(ConfigValidators.validateHost("fe80::1"))
        assertNotNull(ConfigValidators.validateHost(""))
        assertNotNull(ConfigValidators.validateHost("192.168.1"))
        assertNotNull(ConfigValidators.validateHost("300.1.1.1"))
        assertNotNull(ConfigValidators.validateHost("my host"))
        assertNotNull(ConfigValidators.validateHost("ftp://host"))
        assertNotNull(ConfigValidators.validateHost("-bad-.com"))
    }

    @Test fun normalizeHost() {
        assertEquals("192.168.1.10" to 2121, ConfigValidators.normalizeHost("ftp://user:pw@192.168.1.10:2121/dir"))
        assertEquals("nas.local" to null, ConfigValidators.normalizeHost("  nas.local "))
        assertEquals("::1" to 21, ConfigValidators.normalizeHost("[::1]:21"))
        assertEquals("fe80::1" to null, ConfigValidators.normalizeHost("fe80::1"))
    }

    @Test fun chunks() {
        assertNull(ConfigValidators.validateChunks("8"))
        assertNotNull(ConfigValidators.validateChunks("0"))
        assertNotNull(ConfigValidators.validateChunks("33"))
        assertNotNull(ConfigValidators.validateChunks(""))
    }

    @Test fun serverCredentials() {
        assertNull(ConfigValidators.validateServerUsername("mobile"))
        assertNotNull(ConfigValidators.validateServerUsername(""))
        assertNotNull(ConfigValidators.validateServerUsername("anonymous"))
        assertNotNull(ConfigValidators.validateServerUsername("bad name"))
        // Default password "ftp" must stay valid so existing installs aren't blocked.
        assertNull(ConfigValidators.validateServerPassword("ftp"))
        assertNotNull(ConfigValidators.validateServerPassword("ab"))
    }

    @Test fun pasvRange() {
        assertEquals(null to null, ConfigValidators.validatePasvRange("50000", "51000", 2121))
        assertNotNull(ConfigValidators.validatePasvRange("51000", "50000", 2121).second)
        assertNotNull(ConfigValidators.validatePasvRange("2000", "3000", 2121).first)
        assertNotNull(ConfigValidators.validatePasvRange("80", "3000", 2121).first)
    }

    @Test fun remoteName() {
        assertNull(ConfigValidators.validateRemoteName("Photos 2024"))
        assertNotNull(ConfigValidators.validateRemoteName(""))
        assertNotNull(ConfigValidators.validateRemoteName(".."))
        assertNotNull(ConfigValidators.validateRemoteName("a/b"))
    }
}
