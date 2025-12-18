package io.lamco.netkit.model.topology

import org.junit.jupiter.api.Test
import kotlin.test.*

class CipherSuiteTest {
    @Test
    fun `all cipher suites have valid properties`() {
        CipherSuite.entries.forEach { cipher ->
            assertNotNull(cipher.displayName)
            assertTrue(cipher.displayName.isNotBlank())
            assertTrue(cipher.strength in 0..100)
            assertTrue(cipher.keyLength >= 0)
        }
    }

    @Test
    fun `secure ciphers have strength at least 80`() {
        assertTrue(CipherSuite.CCMP.isSecure)
        assertTrue(CipherSuite.GCMP.isSecure)
        assertTrue(CipherSuite.GCMP_256.isSecure)
        assertTrue(CipherSuite.CCMP_256.isSecure)
    }

    @Test
    fun `weak ciphers are not secure`() {
        assertFalse(CipherSuite.WEP_40.isSecure)
        assertFalse(CipherSuite.WEP_104.isSecure)
        assertFalse(CipherSuite.TKIP.isSecure)
        assertFalse(CipherSuite.NONE.isSecure)
    }

    @Test
    fun `deprecated ciphers are identified`() {
        assertTrue(CipherSuite.WEP_40.isDeprecated)
        assertTrue(CipherSuite.WEP_104.isDeprecated)
        assertTrue(CipherSuite.TKIP.isDeprecated)
    }

    @Test
    fun `modern ciphers are not deprecated`() {
        assertFalse(CipherSuite.CCMP.isDeprecated)
        assertFalse(CipherSuite.GCMP.isDeprecated)
        assertFalse(CipherSuite.GCMP_256.isDeprecated)
        assertFalse(CipherSuite.CCMP_256.isDeprecated)
    }

    @Test
    fun `management ciphers are identified`() {
        assertTrue(CipherSuite.BIP_CMAC_128.isManagementCipher)
        assertTrue(CipherSuite.BIP_GMAC_128.isManagementCipher)
        assertTrue(CipherSuite.BIP_GMAC_256.isManagementCipher)
        assertTrue(CipherSuite.BIP_CMAC_256.isManagementCipher)
    }

    @Test
    fun `data ciphers are not management ciphers`() {
        assertFalse(CipherSuite.CCMP.isManagementCipher)
        assertFalse(CipherSuite.GCMP.isManagementCipher)
        assertFalse(CipherSuite.TKIP.isManagementCipher)
        assertFalse(CipherSuite.WEP_40.isManagementCipher)
    }

    @Test
    fun `modern ciphers provide forward secrecy`() {
        assertTrue(CipherSuite.CCMP.providesForwardSecrecy)
        assertTrue(CipherSuite.GCMP.providesForwardSecrecy)
        assertTrue(CipherSuite.CCMP_256.providesForwardSecrecy)
        assertTrue(CipherSuite.GCMP_256.providesForwardSecrecy)
    }

    @Test
    fun `legacy ciphers do not provide forward secrecy`() {
        assertFalse(CipherSuite.WEP_40.providesForwardSecrecy)
        assertFalse(CipherSuite.WEP_104.providesForwardSecrecy)
        assertFalse(CipherSuite.TKIP.providesForwardSecrecy)
    }

    @Test
    fun `256-bit ciphers have maximum strength`() {
        assertEquals(100, CipherSuite.GCMP_256.strength)
        assertEquals(100, CipherSuite.CCMP_256.strength)
        assertEquals(100, CipherSuite.BIP_GMAC_256.strength)
        assertEquals(100, CipherSuite.BIP_CMAC_256.strength)
    }

    @Test
    fun `256-bit ciphers have correct key length`() {
        assertEquals(256, CipherSuite.GCMP_256.keyLength)
        assertEquals(256, CipherSuite.CCMP_256.keyLength)
        assertEquals(256, CipherSuite.BIP_GMAC_256.keyLength)
        assertEquals(256, CipherSuite.BIP_CMAC_256.keyLength)
    }

    @Test
    fun `128-bit ciphers have correct key length`() {
        assertEquals(128, CipherSuite.CCMP.keyLength)
        assertEquals(128, CipherSuite.GCMP.keyLength)
        assertEquals(128, CipherSuite.TKIP.keyLength)
    }

    @Test
    fun `WEP ciphers have low strength`() {
        assertTrue(CipherSuite.WEP_40.strength <= 10)
        assertTrue(CipherSuite.WEP_104.strength <= 10)
    }

    @Test
    fun `cipher categories are correctly assigned`() {
        assertEquals(CipherCategory.WEP, CipherSuite.WEP_40.category)
        assertEquals(CipherCategory.WEP, CipherSuite.WEP_104.category)
        assertEquals(CipherCategory.TKIP, CipherSuite.TKIP.category)
        assertEquals(CipherCategory.CCMP_128, CipherSuite.CCMP.category)
        assertEquals(CipherCategory.GCMP_128, CipherSuite.GCMP.category)
        assertEquals(CipherCategory.HIGH_SECURITY_256, CipherSuite.GCMP_256.category)
        assertEquals(CipherCategory.HIGH_SECURITY_256, CipherSuite.CCMP_256.category)
        assertEquals(CipherCategory.MANAGEMENT, CipherSuite.BIP_CMAC_128.category)
    }

    @Test
    fun `fromSuiteType returns correct ciphers`() {
        assertEquals(CipherSuite.NONE, CipherSuite.fromSuiteType(0))
        assertEquals(CipherSuite.WEP_40, CipherSuite.fromSuiteType(1))
        assertEquals(CipherSuite.TKIP, CipherSuite.fromSuiteType(2))
        assertEquals(CipherSuite.CCMP, CipherSuite.fromSuiteType(4))
        assertEquals(CipherSuite.WEP_104, CipherSuite.fromSuiteType(5))
        assertEquals(CipherSuite.GCMP, CipherSuite.fromSuiteType(8))
        assertEquals(CipherSuite.GCMP_256, CipherSuite.fromSuiteType(9))
        assertEquals(CipherSuite.CCMP_256, CipherSuite.fromSuiteType(10))
    }

    @Test
    fun `fromSuiteType returns UNKNOWN for unrecognized suite`() {
        assertEquals(CipherSuite.UNKNOWN, CipherSuite.fromSuiteType(99))
        assertEquals(CipherSuite.UNKNOWN, CipherSuite.fromSuiteType(-1))
    }

    @Test
    fun `recommended cipher for WPA3 enterprise is GCMP-256`() {
        assertEquals(CipherSuite.GCMP_256, CipherSuite.recommended(wpa3 = true, enterprise = true))
    }

    @Test
    fun `recommended cipher for WPA3 personal is GCMP`() {
        assertEquals(CipherSuite.GCMP, CipherSuite.recommended(wpa3 = true, enterprise = false))
    }

    @Test
    fun `recommended cipher for WPA2 is CCMP`() {
        assertEquals(CipherSuite.CCMP, CipherSuite.recommended(wpa3 = false, enterprise = false))
        assertEquals(CipherSuite.CCMP, CipherSuite.recommended(wpa3 = false, enterprise = true))
    }

    @Test
    fun `GCMP has higher strength than CCMP`() {
        assertTrue(CipherSuite.GCMP.strength >= CipherSuite.CCMP.strength)
    }

    @Test
    fun `BIP management ciphers have appropriate strength`() {
        assertTrue(CipherSuite.BIP_CMAC_128.strength >= 80)
        assertTrue(CipherSuite.BIP_GMAC_128.strength >= 80)
        assertEquals(100, CipherSuite.BIP_GMAC_256.strength)
        assertEquals(100, CipherSuite.BIP_CMAC_256.strength)
    }
}
