package io.lamco.netkit.ieparser.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Comprehensive unit tests for AKMSuite enum
 *
 * Covers:
 * - fromBytes() parsing
 * - Security levels
 * - Classification (Enterprise, Personal, WPA3, etc.)
 * - SAE-PK detection (industry-first!)
 * - Fast roaming support
 * - All enum values
 */
@DisplayName("AKMSuite Tests")
class AKMSuiteTest {
    companion object {
        // WiFi Alliance OUI (00-0F-AC)
        val WIFI_ALLIANCE_OUI = byteArrayOf(0x00, 0x0F, 0xAC.toByte())
        val VENDOR_OUI = byteArrayOf(0x00, 0x50, 0xF2.toByte())
    }

    @Nested
    @DisplayName("fromBytes() Parsing Tests")
    inner class FromBytesTests {
        @Test
        @DisplayName("Parse WPA-Enterprise (type 1)")
        fun parseWPAEnterprise() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 1)
            assertEquals(AKMSuite.WPA_ENTERPRISE, akm)
        }

        @Test
        @DisplayName("Parse PSK (type 2)")
        fun parsePSK() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 2)
            assertEquals(AKMSuite.PSK, akm)
        }

        @Test
        @DisplayName("Parse FT over 802.1X (type 3)")
        fun parseFTOver8021X() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 3)
            assertEquals(AKMSuite.FT_OVER_8021X, akm)
        }

        @Test
        @DisplayName("Parse FT-PSK (type 4)")
        fun parseFTPSK() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 4)
            assertEquals(AKMSuite.FT_PSK, akm)
        }

        @Test
        @DisplayName("Parse WPA-SHA256 (type 5)")
        fun parseWPASHA256() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 5)
            assertEquals(AKMSuite.WPA_SHA256, akm)
        }

        @Test
        @DisplayName("Parse PSK-SHA256 (type 6)")
        fun parsePSKSHA256() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 6)
            assertEquals(AKMSuite.PSK_SHA256, akm)
        }

        @Test
        @DisplayName("Parse SAE (type 8) - WPA3-Personal")
        fun parseSAE() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 8)
            assertEquals(AKMSuite.SAE, akm)
        }

        @Test
        @DisplayName("Parse FT over SAE (type 9)")
        fun parseFTOverSAE() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 9)
            assertEquals(AKMSuite.FT_OVER_SAE, akm)
        }

        @Test
        @DisplayName("Parse Suite-B EAP-SHA256 (type 11)")
        fun parseSuiteBSHA256() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 11)
            assertEquals(AKMSuite.SUITE_B_EAP_SHA256, akm)
        }

        @Test
        @DisplayName("Parse Suite-B EAP-SHA384 (type 12) - WPA3-Enterprise 192-bit")
        fun parseSuiteBSHA384() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 12)
            assertEquals(AKMSuite.SUITE_B_EAP_SHA384, akm)
        }

        @Test
        @DisplayName("Parse OWE (type 18) - Enhanced Open")
        fun parseOWE() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 18)
            assertEquals(AKMSuite.OWE, akm)
        }

        @Test
        @DisplayName("Parse SAE-PK (type 24)")
        fun parseSAE_PK() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 24)
            assertEquals(AKMSuite.SAE_PK, akm)
        }

        @Test
        @DisplayName("Parse unknown type returns UNKNOWN")
        fun parseUnknownType() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 255)
            assertEquals(AKMSuite.UNKNOWN, akm)
        }

        @Test
        @DisplayName("Parse vendor-specific OUI returns VENDOR_SPECIFIC")
        fun parseVendorSpecific() {
            val akm = AKMSuite.fromBytes(VENDOR_OUI, 8)
            assertEquals(AKMSuite.VENDOR_SPECIFIC, akm)
        }
    }

    @Nested
    @DisplayName("Security Level Tests")
    inner class SecurityLevelTests {
        @Test
        @DisplayName("WPA-Enterprise has low security (40)")
        fun wpaEnterpriseLowSecurity() {
            assertEquals(40, AKMSuite.WPA_ENTERPRISE.securityLevel)
        }

        @Test
        @DisplayName("PSK has good security (70)")
        fun pskGoodSecurity() {
            assertEquals(70, AKMSuite.PSK.securityLevel)
        }

        @Test
        @DisplayName("PSK-SHA256 has better security (75)")
        fun pskSHA256BetterSecurity() {
            assertEquals(75, AKMSuite.PSK_SHA256.securityLevel)
        }

        @Test
        @DisplayName("SAE (WPA3) has excellent security (90)")
        fun saeExcellentSecurity() {
            assertEquals(90, AKMSuite.SAE.securityLevel)
        }

        @Test
        @DisplayName("SAE-PK has maximum security (100)")
        fun saePKMaximumSecurity() {
            assertEquals(100, AKMSuite.SAE_PK.securityLevel)
        }

        @Test
        @DisplayName("OWE has fair security (50)")
        fun oweFairSecurity() {
            assertEquals(50, AKMSuite.OWE.securityLevel)
        }

        @Test
        @DisplayName("Suite-B SHA384 has excellent security (95)")
        fun suiteBSHA384ExcellentSecurity() {
            assertEquals(95, AKMSuite.SUITE_B_EAP_SHA384.securityLevel)
        }

        @Test
        @DisplayName("FT variants maintain base security level")
        fun ftVariantsSecurity() {
            assertEquals(70, AKMSuite.FT_PSK.securityLevel)
            assertEquals(90, AKMSuite.FT_OVER_SAE.securityLevel)
            assertEquals(75, AKMSuite.FT_OVER_8021X.securityLevel)
        }

        @Test
        @DisplayName("Unknown and vendor-specific have 0 security")
        fun unknownZeroSecurity() {
            assertEquals(0, AKMSuite.UNKNOWN.securityLevel)
            assertEquals(0, AKMSuite.VENDOR_SPECIFIC.securityLevel)
        }
    }

    @Nested
    @DisplayName("Enterprise Classification Tests")
    inner class EnterpriseTests {
        @Test
        @DisplayName("Enterprise AKMs are correctly identified")
        fun enterpriseIdentified() {
            assertTrue(AKMSuite.WPA_ENTERPRISE.isEnterprise)
            assertTrue(AKMSuite.FT_OVER_8021X.isEnterprise)
            assertTrue(AKMSuite.WPA_SHA256.isEnterprise)
            assertTrue(AKMSuite.SUITE_B_EAP_SHA256.isEnterprise)
            assertTrue(AKMSuite.SUITE_B_EAP_SHA384.isEnterprise)
        }

        @Test
        @DisplayName("Personal AKMs are not Enterprise")
        fun personalNotEnterprise() {
            assertFalse(AKMSuite.PSK.isEnterprise)
            assertFalse(AKMSuite.SAE.isEnterprise)
            assertFalse(AKMSuite.SAE_PK.isEnterprise)
            assertFalse(AKMSuite.FT_PSK.isEnterprise)
        }

        @Test
        @DisplayName("OWE is not Enterprise")
        fun oweNotEnterprise() {
            assertFalse(AKMSuite.OWE.isEnterprise)
        }
    }

    @Nested
    @DisplayName("Personal Classification Tests")
    inner class PersonalTests {
        @Test
        @DisplayName("Personal AKMs are correctly identified")
        fun personalIdentified() {
            assertTrue(AKMSuite.PSK.isPersonal)
            assertTrue(AKMSuite.PSK_SHA256.isPersonal)
            assertTrue(AKMSuite.FT_PSK.isPersonal)
            assertTrue(AKMSuite.SAE.isPersonal)
            assertTrue(AKMSuite.SAE_PK.isPersonal)
            assertTrue(AKMSuite.FT_OVER_SAE.isPersonal)
        }

        @Test
        @DisplayName("Enterprise AKMs are not Personal")
        fun enterpriseNotPersonal() {
            assertFalse(AKMSuite.WPA_ENTERPRISE.isPersonal)
            assertFalse(AKMSuite.FT_OVER_8021X.isPersonal)
            assertFalse(AKMSuite.SUITE_B_EAP_SHA384.isPersonal)
        }

        @Test
        @DisplayName("OWE is not Personal")
        fun oweNotPersonal() {
            assertFalse(AKMSuite.OWE.isPersonal)
        }
    }

    @Nested
    @DisplayName("WPA3 Detection Tests")
    inner class WPA3Tests {
        @Test
        @DisplayName("WPA3 AKMs are correctly identified")
        fun wpa3Identified() {
            assertTrue(AKMSuite.SAE.isWPA3)
            assertTrue(AKMSuite.SAE_PK.isWPA3)
            assertTrue(AKMSuite.FT_OVER_SAE.isWPA3)
            assertTrue(AKMSuite.SUITE_B_EAP_SHA384.isWPA3)
        }

        @Test
        @DisplayName("WPA2 AKMs are not WPA3")
        fun wpa2NotWPA3() {
            assertFalse(AKMSuite.PSK.isWPA3)
            assertFalse(AKMSuite.PSK_SHA256.isWPA3)
            assertFalse(AKMSuite.WPA_ENTERPRISE.isWPA3)
        }

        @Test
        @DisplayName("OWE is not WPA3")
        fun oweNotWPA3() {
            assertFalse(AKMSuite.OWE.isWPA3)
        }
    }

    @Nested
    @DisplayName("Open Encryption Tests")
    inner class OpenEncryptionTests {
        @Test
        @DisplayName("OWE is open encryption")
        fun oweIsOpenEncryption() {
            assertTrue(AKMSuite.OWE.isOpenEncryption)
        }

        @Test
        @DisplayName("Other AKMs are not open encryption")
        fun othersNotOpenEncryption() {
            assertFalse(AKMSuite.PSK.isOpenEncryption)
            assertFalse(AKMSuite.SAE.isOpenEncryption)
            assertFalse(AKMSuite.WPA_ENTERPRISE.isOpenEncryption)
        }
    }

    @Nested
    @DisplayName("Fast Roaming Tests")
    inner class FastRoamingTests {
        @Test
        @DisplayName("FT variants support fast roaming")
        fun ftSupportsFastRoaming() {
            assertTrue(AKMSuite.FT_OVER_8021X.supportsFastRoaming)
            assertTrue(AKMSuite.FT_PSK.supportsFastRoaming)
            assertTrue(AKMSuite.FT_OVER_SAE.supportsFastRoaming)
        }

        @Test
        @DisplayName("Non-FT variants don't support fast roaming")
        fun nonFTNoFastRoaming() {
            assertFalse(AKMSuite.PSK.supportsFastRoaming)
            assertFalse(AKMSuite.SAE.supportsFastRoaming)
            assertFalse(AKMSuite.WPA_ENTERPRISE.supportsFastRoaming)
        }
    }

    @Nested
    @DisplayName("Display Name Tests")
    inner class DisplayNameTests {
        @ParameterizedTest
        @EnumSource(AKMSuite::class)
        @DisplayName("All AKMs have non-empty display names")
        fun allHaveDisplayNames(akm: AKMSuite) {
            assertNotNull(akm.displayName)
            assertTrue(akm.displayName.isNotEmpty())
        }

        @Test
        @DisplayName("SAE displays as 'WPA3-SAE'")
        fun saeDisplayName() {
            assertEquals("WPA3-SAE", AKMSuite.SAE.displayName)
        }

        @Test
        @DisplayName("SAE-PK displays as 'WPA3-SAE-PK'")
        fun saePKDisplayName() {
            assertEquals("WPA3-SAE-PK", AKMSuite.SAE_PK.displayName)
        }

        @Test
        @DisplayName("OWE displays as 'Enhanced Open (OWE)'")
        fun oweDisplayName() {
            assertEquals("Enhanced Open (OWE)", AKMSuite.OWE.displayName)
        }
    }

    @Nested
    @DisplayName("Real-World Scenario Tests")
    inner class RealWorldScenarioTests {
        @Test
        @DisplayName("Home WiFi WPA2 setup")
        fun homeWiFiWPA2() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 2)
            assertEquals(AKMSuite.PSK, akm)
            assertTrue(akm.isPersonal)
            assertFalse(akm.isWPA3)
            assertEquals(70, akm.securityLevel)
        }

        @Test
        @DisplayName("Home WiFi WPA3 setup")
        fun homeWiFiWPA3() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 8)
            assertEquals(AKMSuite.SAE, akm)
            assertTrue(akm.isPersonal)
            assertTrue(akm.isWPA3)
            assertEquals(90, akm.securityLevel)
        }

        @Test
        @DisplayName("Home WiFi WPA3-SAE-PK (industry-first detection!)")
        fun homeWiFiSAE_PK() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 24)
            assertEquals(AKMSuite.SAE_PK, akm)
            assertTrue(akm.isPersonal)
            assertTrue(akm.isWPA3)
            assertEquals(100, akm.securityLevel)
        }

        @Test
        @DisplayName("Corporate WiFi (WPA2-Enterprise)")
        fun corporateWiFi() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 1)
            assertEquals(AKMSuite.WPA_ENTERPRISE, akm)
            assertTrue(akm.isEnterprise)
            assertFalse(akm.isWPA3)
        }

        @Test
        @DisplayName("Corporate WiFi with fast roaming (FT over 802.1X)")
        fun corporateWiFiFastRoaming() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 3)
            assertEquals(AKMSuite.FT_OVER_8021X, akm)
            assertTrue(akm.isEnterprise)
            assertTrue(akm.supportsFastRoaming)
        }

        @Test
        @DisplayName("Government/High-security WiFi (Suite-B SHA384)")
        fun governmentWiFi() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 12)
            assertEquals(AKMSuite.SUITE_B_EAP_SHA384, akm)
            assertTrue(akm.isEnterprise)
            assertTrue(akm.isWPA3)
            assertEquals(95, akm.securityLevel)
        }

        @Test
        @DisplayName("Coffee shop Enhanced Open (OWE)")
        fun coffeeShopOWE() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 18)
            assertEquals(AKMSuite.OWE, akm)
            assertTrue(akm.isOpenEncryption)
            assertFalse(akm.isEnterprise)
            assertFalse(akm.isPersonal)
            assertEquals(50, akm.securityLevel)
        }

        @Test
        @DisplayName("Home WiFi WPA3 with fast roaming (FT over SAE)")
        fun homeWiFiWPA3FastRoaming() {
            val akm = AKMSuite.fromBytes(WIFI_ALLIANCE_OUI, 9)
            assertEquals(AKMSuite.FT_OVER_SAE, akm)
            assertTrue(akm.isPersonal)
            assertTrue(akm.isWPA3)
            assertTrue(akm.supportsFastRoaming)
            assertEquals(90, akm.securityLevel)
        }
    }

    @Nested
    @DisplayName("Enum Properties Tests")
    inner class EnumPropertiesTests {
        @Test
        @DisplayName("All AKMs have WiFi Alliance OUI except vendor-specific")
        fun allHaveCorrectOUI() {
            AKMSuite.entries
                .filter {
                    it != AKMSuite.VENDOR_SPECIFIC && it != AKMSuite.UNKNOWN
                }.forEach { akm ->
                    assertTrue(
                        akm.oui.contentEquals(WIFI_ALLIANCE_OUI),
                        "${akm.displayName} should have WiFi Alliance OUI",
                    )
                }
        }

        @Test
        @DisplayName("Type values match WiFi Alliance spec")
        fun typeValuesMatchSpec() {
            assertEquals(1, AKMSuite.WPA_ENTERPRISE.type)
            assertEquals(2, AKMSuite.PSK.type)
            assertEquals(8, AKMSuite.SAE.type)
            assertEquals(24, AKMSuite.SAE_PK.type)
            assertEquals(18, AKMSuite.OWE.type)
            assertEquals(12, AKMSuite.SUITE_B_EAP_SHA384.type)
        }

        @Test
        @DisplayName("Special values for unknown/vendor-specific")
        fun specialValues() {
            assertEquals(0, AKMSuite.UNKNOWN.type)
            assertEquals(-1, AKMSuite.VENDOR_SPECIFIC.type)
        }
    }
}
