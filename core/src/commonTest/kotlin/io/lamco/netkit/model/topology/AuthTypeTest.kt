package io.lamco.netkit.model.topology

import org.junit.jupiter.api.Test
import kotlin.test.*

class AuthTypeTest {
    @Test
    fun `all auth types have valid properties`() {
        AuthType.entries.forEach { authType ->
            assertNotNull(authType.displayName)
            assertTrue(authType.displayName.isNotBlank())
            assertTrue(authType.securityLevel in 0..100)
        }
    }

    @Test
    fun `WPA3 variants are modern`() {
        assertTrue(AuthType.WPA3_SAE.isModern)
        assertTrue(AuthType.WPA3_SAE_PK.isModern)
        assertTrue(AuthType.WPA3_ENTERPRISE.isModern)
        assertTrue(AuthType.WPA3_ENTERPRISE_192.isModern)
    }

    @Test
    fun `OWE is modern`() {
        assertTrue(AuthType.OWE.isModern)
    }

    @Test
    fun `WPA2 and older are not modern`() {
        assertFalse(AuthType.WPA2_PSK.isModern)
        assertFalse(AuthType.WPA2_ENTERPRISE.isModern)
        assertFalse(AuthType.WPA_PSK.isModern)
        assertFalse(AuthType.WEP.isModern)
        assertFalse(AuthType.OPEN.isModern)
    }

    @Test
    fun `WPA2 and better are secure with score at least 70`() {
        assertTrue(AuthType.WPA2_PSK.isSecure)
        assertTrue(AuthType.WPA2_ENTERPRISE.isSecure)
        assertTrue(AuthType.WPA3_SAE.isSecure)
        assertTrue(AuthType.WPA3_ENTERPRISE.isSecure)
    }

    @Test
    fun `WPA, WEP, OPEN, OWE are not secure`() {
        assertFalse(AuthType.WPA_PSK.isSecure)
        assertFalse(AuthType.WEP.isSecure)
        assertFalse(AuthType.OPEN.isSecure)
        assertFalse(AuthType.OWE.isSecure) // OWE is 60, just below threshold
    }

    @Test
    fun `deprecated auth types are identified`() {
        assertTrue(AuthType.WPA_PSK.isDeprecated)
        assertTrue(AuthType.WPA_ENTERPRISE.isDeprecated)
        assertTrue(AuthType.WEP.isDeprecated)
    }

    @Test
    fun `modern auth types are not deprecated`() {
        assertFalse(AuthType.WPA3_SAE.isDeprecated)
        assertFalse(AuthType.WPA2_PSK.isDeprecated)
        assertFalse(AuthType.WPA2_ENTERPRISE.isDeprecated)
    }

    @Test
    fun `Personal auth types are identified`() {
        assertTrue(AuthType.WPA_PSK.isPersonal)
        assertTrue(AuthType.WPA2_PSK.isPersonal)
        assertTrue(AuthType.WPA3_SAE.isPersonal)
        assertTrue(AuthType.WPA3_SAE_PK.isPersonal)
    }

    @Test
    fun `Enterprise auth types are identified`() {
        assertTrue(AuthType.WPA_ENTERPRISE.isEnterprise)
        assertTrue(AuthType.WPA2_ENTERPRISE.isEnterprise)
        assertTrue(AuthType.WPA3_ENTERPRISE.isEnterprise)
        assertTrue(AuthType.WPA3_ENTERPRISE_192.isEnterprise)
    }

    @Test
    fun `Enterprise auth types require RADIUS`() {
        assertTrue(AuthType.WPA_ENTERPRISE.requiresRadius)
        assertTrue(AuthType.WPA2_ENTERPRISE.requiresRadius)
        assertTrue(AuthType.WPA3_ENTERPRISE.requiresRadius)
        assertTrue(AuthType.WPA3_ENTERPRISE_192.requiresRadius)
    }

    @Test
    fun `Personal and Open auth types do not require RADIUS`() {
        assertFalse(AuthType.WPA2_PSK.requiresRadius)
        assertFalse(AuthType.WPA3_SAE.requiresRadius)
        assertFalse(AuthType.OPEN.requiresRadius)
        assertFalse(AuthType.OWE.requiresRadius)
        assertFalse(AuthType.WEP.requiresRadius)
    }

    @Test
    fun `security levels are correctly ordered`() {
        assertTrue(AuthType.WPA3_SAE_PK.securityLevel >= AuthType.WPA3_SAE.securityLevel)
        assertTrue(AuthType.WPA3_SAE.securityLevel >= AuthType.WPA2_PSK.securityLevel)
        assertTrue(AuthType.WPA2_PSK.securityLevel >= AuthType.OWE.securityLevel)
        assertTrue(AuthType.OWE.securityLevel >= AuthType.WPA_PSK.securityLevel)
        assertTrue(AuthType.WPA_PSK.securityLevel >= AuthType.WEP.securityLevel)
        assertTrue(AuthType.WEP.securityLevel > AuthType.OPEN.securityLevel)
    }

    @Test
    fun `WPA3 has highest security levels at least 95`() {
        assertTrue(AuthType.WPA3_SAE.securityLevel >= 95)
        assertTrue(AuthType.WPA3_SAE_PK.securityLevel >= 95)
        assertTrue(AuthType.WPA3_ENTERPRISE.securityLevel >= 95)
        assertTrue(AuthType.WPA3_ENTERPRISE_192.securityLevel >= 95)
    }

    @Test
    fun `OPEN and UNKNOWN have zero security`() {
        assertEquals(0, AuthType.OPEN.securityLevel)
        assertEquals(0, AuthType.UNKNOWN.securityLevel)
    }

    @Test
    fun `WEP has very low security`() {
        assertTrue(AuthType.WEP.securityLevel <= 10)
    }

    @Test
    fun `fromAkmSuite returns correct auth types`() {
        assertEquals(AuthType.OPEN, AuthType.fromAkmSuite(0, false))
        assertEquals(AuthType.WPA_PSK, AuthType.fromAkmSuite(2, false))
        assertEquals(AuthType.WPA2_PSK, AuthType.fromAkmSuite(6, false))
        assertEquals(AuthType.WPA3_SAE, AuthType.fromAkmSuite(8, false))
        assertEquals(AuthType.WPA3_ENTERPRISE, AuthType.fromAkmSuite(9, false))
        assertEquals(AuthType.WPA3_ENTERPRISE_192, AuthType.fromAkmSuite(12, false))
        assertEquals(AuthType.OWE, AuthType.fromAkmSuite(18, false))
        assertEquals(AuthType.WPA3_SAE_PK, AuthType.fromAkmSuite(24, false))
    }

    @Test
    fun `fromAkmSuite returns UNKNOWN for unrecognized suite`() {
        assertEquals(AuthType.UNKNOWN, AuthType.fromAkmSuite(99, false))
        assertEquals(AuthType.UNKNOWN, AuthType.fromAkmSuite(-1, false))
    }
}
