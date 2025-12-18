package io.lamco.netkit.ieparser.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Comprehensive unit tests for CipherSuite enum
 *
 * Covers:
 * - fromBytes() parsing
 * - Security levels
 * - Deprecated detection
 * - Data vs management cipher classification
 * - All enum values
 */
@DisplayName("CipherSuite Tests")
class CipherSuiteTest {
    companion object {
        // WiFi Alliance OUI (00-0F-AC)
        val WIFI_ALLIANCE_OUI = byteArrayOf(0x00, 0x0F, 0xAC.toByte())
        val VENDOR_OUI = byteArrayOf(0x00, 0x50, 0xF2.toByte())
    }

    @Nested
    @DisplayName("fromBytes() Parsing Tests")
    inner class FromBytesTests {
        @Test
        @DisplayName("Parse WEP-40 (type 1)")
        fun parseWEP40() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 1)
            assertEquals(CipherSuite.WEP40, cipher)
        }

        @Test
        @DisplayName("Parse TKIP (type 2)")
        fun parseTKIP() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 2)
            assertEquals(CipherSuite.TKIP, cipher)
        }

        @Test
        @DisplayName("Parse CCMP-128 (type 4)")
        fun parseCCMP128() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 4)
            assertEquals(CipherSuite.CCMP_128, cipher)
        }

        @Test
        @DisplayName("Parse WEP-104 (type 5)")
        fun parseWEP104() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 5)
            assertEquals(CipherSuite.WEP104, cipher)
        }

        @Test
        @DisplayName("Parse BIP-CMAC-128 (type 6)")
        fun parseBIP_CMAC128() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 6)
            assertEquals(CipherSuite.BIP_CMAC_128, cipher)
        }

        @Test
        @DisplayName("Parse GCMP-128 (type 8)")
        fun parseGCMP128() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 8)
            assertEquals(CipherSuite.GCMP_128, cipher)
        }

        @Test
        @DisplayName("Parse GCMP-256 (type 9)")
        fun parseGCMP256() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 9)
            assertEquals(CipherSuite.GCMP_256, cipher)
        }

        @Test
        @DisplayName("Parse CCMP-256 (type 10)")
        fun parseCCMP256() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 10)
            assertEquals(CipherSuite.CCMP_256, cipher)
        }

        @Test
        @DisplayName("Parse BIP-GMAC-128 (type 11)")
        fun parseBIP_GMAC128() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 11)
            assertEquals(CipherSuite.BIP_GMAC_128, cipher)
        }

        @Test
        @DisplayName("Parse BIP-GMAC-256 (type 12)")
        fun parseBIP_GMAC256() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 12)
            assertEquals(CipherSuite.BIP_GMAC_256, cipher)
        }

        @Test
        @DisplayName("Parse BIP-CMAC-256 (type 13)")
        fun parseBIP_CMAC256() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 13)
            assertEquals(CipherSuite.BIP_CMAC_256, cipher)
        }

        @Test
        @DisplayName("Parse unknown type returns UNKNOWN")
        fun parseUnknownType() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 255)
            assertEquals(CipherSuite.UNKNOWN, cipher)
        }

        @Test
        @DisplayName("Parse vendor-specific OUI returns VENDOR_SPECIFIC")
        fun parseVendorSpecific() {
            val cipher = CipherSuite.fromBytes(VENDOR_OUI, 4)
            assertEquals(CipherSuite.VENDOR_SPECIFIC, cipher)
        }

        @Test
        @DisplayName("Parse with wrong OUI returns VENDOR_SPECIFIC")
        fun parseWrongOUI() {
            val wrongOUI = byteArrayOf(0x01, 0x02, 0x03)
            val cipher = CipherSuite.fromBytes(wrongOUI, 4)
            assertEquals(CipherSuite.VENDOR_SPECIFIC, cipher)
        }
    }

    @Nested
    @DisplayName("Security Level Tests")
    inner class SecurityLevelTests {
        @Test
        @DisplayName("WEP ciphers have 0 security")
        fun wepHasZeroSecurity() {
            assertEquals(0, CipherSuite.WEP40.securityLevel)
            assertEquals(0, CipherSuite.WEP104.securityLevel)
        }

        @Test
        @DisplayName("TKIP has low security (20)")
        fun tkipHasLowSecurity() {
            assertEquals(20, CipherSuite.TKIP.securityLevel)
        }

        @Test
        @DisplayName("CCMP-128 has good security (70)")
        fun ccmp128HasGoodSecurity() {
            assertEquals(70, CipherSuite.CCMP_128.securityLevel)
        }

        @Test
        @DisplayName("GCMP-128 has better security (75)")
        fun gcmp128HasBetterSecurity() {
            assertEquals(75, CipherSuite.GCMP_128.securityLevel)
        }

        @Test
        @DisplayName("256-bit ciphers have excellent security (90-95)")
        fun cipher256HasExcellentSecurity() {
            assertEquals(90, CipherSuite.CCMP_256.securityLevel)
            assertEquals(95, CipherSuite.GCMP_256.securityLevel)
        }

        @Test
        @DisplayName("Management ciphers have appropriate security levels")
        fun managementCiphersSecurity() {
            assertEquals(70, CipherSuite.BIP_CMAC_128.securityLevel)
            assertEquals(70, CipherSuite.BIP_GMAC_128.securityLevel)
            assertEquals(90, CipherSuite.BIP_CMAC_256.securityLevel)
            assertEquals(90, CipherSuite.BIP_GMAC_256.securityLevel)
        }

        @Test
        @DisplayName("Unknown and vendor-specific have 0 security")
        fun unknownHasZeroSecurity() {
            assertEquals(0, CipherSuite.UNKNOWN.securityLevel)
            assertEquals(0, CipherSuite.VENDOR_SPECIFIC.securityLevel)
        }
    }

    @Nested
    @DisplayName("Deprecated Detection Tests")
    inner class DeprecatedTests {
        @Test
        @DisplayName("WEP-40 is deprecated")
        fun wep40IsDeprecated() {
            assertTrue(CipherSuite.WEP40.deprecated)
        }

        @Test
        @DisplayName("WEP-104 is deprecated")
        fun wep104IsDeprecated() {
            assertTrue(CipherSuite.WEP104.deprecated)
        }

        @Test
        @DisplayName("TKIP is deprecated")
        fun tkipIsDeprecated() {
            assertTrue(CipherSuite.TKIP.deprecated)
        }

        @Test
        @DisplayName("Modern ciphers are not deprecated")
        fun modernCiphersNotDeprecated() {
            assertFalse(CipherSuite.CCMP_128.deprecated)
            assertFalse(CipherSuite.CCMP_256.deprecated)
            assertFalse(CipherSuite.GCMP_128.deprecated)
            assertFalse(CipherSuite.GCMP_256.deprecated)
        }

        @Test
        @DisplayName("Management ciphers are not deprecated")
        fun managementCiphersNotDeprecated() {
            assertFalse(CipherSuite.BIP_CMAC_128.deprecated)
            assertFalse(CipherSuite.BIP_CMAC_256.deprecated)
            assertFalse(CipherSuite.BIP_GMAC_128.deprecated)
            assertFalse(CipherSuite.BIP_GMAC_256.deprecated)
        }
    }

    @Nested
    @DisplayName("Cipher Classification Tests")
    inner class ClassificationTests {
        @Test
        @DisplayName("Data ciphers are correctly identified")
        fun dataCiphersIdentified() {
            assertTrue(CipherSuite.WEP40.isDataCipher)
            assertTrue(CipherSuite.WEP104.isDataCipher)
            assertTrue(CipherSuite.TKIP.isDataCipher)
            assertTrue(CipherSuite.CCMP_128.isDataCipher)
            assertTrue(CipherSuite.CCMP_256.isDataCipher)
            assertTrue(CipherSuite.GCMP_128.isDataCipher)
            assertTrue(CipherSuite.GCMP_256.isDataCipher)
        }

        @Test
        @DisplayName("Management ciphers are correctly identified")
        fun managementCiphersIdentified() {
            assertTrue(CipherSuite.BIP_CMAC_128.isManagementCipher)
            assertTrue(CipherSuite.BIP_CMAC_256.isManagementCipher)
            assertTrue(CipherSuite.BIP_GMAC_128.isManagementCipher)
            assertTrue(CipherSuite.BIP_GMAC_256.isManagementCipher)
        }

        @Test
        @DisplayName("Data ciphers are not management ciphers")
        fun dataCiphersNotManagement() {
            assertFalse(CipherSuite.CCMP_128.isManagementCipher)
            assertFalse(CipherSuite.GCMP_128.isManagementCipher)
        }

        @Test
        @DisplayName("Management ciphers are not data ciphers")
        fun managementCiphersNotData() {
            assertFalse(CipherSuite.BIP_CMAC_128.isDataCipher)
            assertFalse(CipherSuite.BIP_GMAC_128.isDataCipher)
        }

        @Test
        @DisplayName("Unknown and vendor-specific are neither data nor management")
        fun unknownNotClassified() {
            assertFalse(CipherSuite.UNKNOWN.isDataCipher)
            assertFalse(CipherSuite.UNKNOWN.isManagementCipher)
            assertFalse(CipherSuite.VENDOR_SPECIFIC.isDataCipher)
            assertFalse(CipherSuite.VENDOR_SPECIFIC.isManagementCipher)
        }
    }

    @Nested
    @DisplayName("Display Name Tests")
    inner class DisplayNameTests {
        @ParameterizedTest
        @EnumSource(CipherSuite::class)
        @DisplayName("All ciphers have non-empty display names")
        fun allHaveDisplayNames(cipher: CipherSuite) {
            assertNotNull(cipher.displayName)
            assertTrue(cipher.displayName.isNotEmpty())
        }

        @Test
        @DisplayName("CCMP displays as 'CCMP (AES)'")
        fun ccmpDisplayName() {
            assertEquals("CCMP (AES)", CipherSuite.CCMP_128.displayName)
        }

        @Test
        @DisplayName("SAE-PK-related ciphers have descriptive names")
        fun descriptiveNames() {
            assertTrue(CipherSuite.GCMP_256.displayName.contains("256"))
            assertTrue(CipherSuite.CCMP_256.displayName.contains("256"))
        }
    }

    @Nested
    @DisplayName("Enum Properties Tests")
    inner class EnumPropertiesTests {
        @Test
        @DisplayName("All ciphers have WiFi Alliance OUI except vendor-specific")
        fun allHaveCorrectOUI() {
            CipherSuite.entries
                .filter {
                    it != CipherSuite.VENDOR_SPECIFIC && it != CipherSuite.UNKNOWN
                }.forEach { cipher ->
                    assertTrue(
                        cipher.oui.contentEquals(WIFI_ALLIANCE_OUI),
                        "${cipher.displayName} should have WiFi Alliance OUI",
                    )
                }
        }

        @Test
        @DisplayName("Type values are within expected ranges")
        fun typeValuesInRange() {
            // Normal types are 1-13
            val normalCiphers =
                CipherSuite.entries.filter {
                    it != CipherSuite.VENDOR_SPECIFIC && it != CipherSuite.UNKNOWN
                }
            normalCiphers.forEach { cipher ->
                assertTrue(
                    cipher.type in 1..13,
                    "${cipher.displayName} type ${cipher.type} should be 1-13",
                )
            }

            // Special values
            assertEquals(0, CipherSuite.UNKNOWN.type)
            assertEquals(-1, CipherSuite.VENDOR_SPECIFIC.type)
        }
    }

    @Nested
    @DisplayName("Real-World Scenario Tests")
    inner class RealWorldScenarioTests {
        @Test
        @DisplayName("WPA2-Personal typical setup (CCMP-128)")
        fun wpa2PersonalSetup() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 4)
            assertEquals(CipherSuite.CCMP_128, cipher)
            assertFalse(cipher.deprecated)
            assertEquals(70, cipher.securityLevel)
            assertTrue(cipher.isDataCipher)
        }

        @Test
        @DisplayName("WPA3-Personal typical setup (CCMP-128 or GCMP-128)")
        fun wpa3PersonalSetup() {
            val ccmp = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 4)
            val gcmp = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 8)

            // Both are valid for WPA3-Personal
            assertFalse(ccmp.deprecated)
            assertFalse(gcmp.deprecated)
            assertTrue(ccmp.securityLevel >= 70)
            assertTrue(gcmp.securityLevel >= 70)
        }

        @Test
        @DisplayName("WPA3-Enterprise 192-bit setup")
        fun wpa3Enterprise192() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 9)
            assertEquals(CipherSuite.GCMP_256, cipher)
            assertEquals(95, cipher.securityLevel)
            assertFalse(cipher.deprecated)
        }

        @Test
        @DisplayName("Legacy WPA/WPA2 with TKIP (should warn)")
        fun legacyTKIP() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 2)
            assertEquals(CipherSuite.TKIP, cipher)
            assertTrue(cipher.deprecated)
            assertEquals(20, cipher.securityLevel)
        }

        @Test
        @DisplayName("PMF with BIP-CMAC-128")
        fun pmfSetup() {
            val cipher = CipherSuite.fromBytes(WIFI_ALLIANCE_OUI, 6)
            assertEquals(CipherSuite.BIP_CMAC_128, cipher)
            assertTrue(cipher.isManagementCipher)
            assertFalse(cipher.deprecated)
        }
    }
}
