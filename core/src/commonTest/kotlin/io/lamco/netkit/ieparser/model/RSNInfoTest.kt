package io.lamco.netkit.ieparser.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Comprehensive unit tests for RSNInfo
 *
 * Covers:
 * - WPA3 detection
 * - SAE-PK detection (industry-first!)
 * - Deprecated cipher detection
 * - Security score calculation
 * - PMF and Beacon Protection
 */
@DisplayName("RSNInfo Tests")
class RSNInfoTest {
    @Nested
    @DisplayName("WPA3 Detection Tests")
    inner class WPA3DetectionTests {
        @Test
        @DisplayName("SAE is detected as WPA3")
        fun saeIsWPA3() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.SAE),
                )
            assertTrue(rsn.isWPA3)
        }

        @Test
        @DisplayName("SAE-PK is detected as WPA3")
        fun saePKIsWPA3() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.SAE_PK),
                )
            assertTrue(rsn.isWPA3)
        }

        @Test
        @DisplayName("Suite-B SHA384 is detected as WPA3")
        fun suiteBIsWPA3() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.SUITE_B_EAP_SHA384),
                )
            assertTrue(rsn.isWPA3)
        }

        @Test
        @DisplayName("FT over SAE is detected as WPA3")
        fun ftOverSAEIsWPA3() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.FT_OVER_SAE),
                )
            assertTrue(rsn.isWPA3)
        }

        @Test
        @DisplayName("PSK is not WPA3")
        fun pskNotWPA3() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.PSK),
                )
            assertFalse(rsn.isWPA3)
        }

        @Test
        @DisplayName("Mixed WPA2/WPA3 is detected as WPA3")
        fun mixedIsWPA3() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.PSK, AKMSuite.SAE),
                )
            assertTrue(rsn.isWPA3)
        }
    }

    @Nested
    @DisplayName("SAE-PK Detection Tests (Industry-First!)")
    inner class SAEPKDetectionTests {
        @Test
        @DisplayName("SAE-PK is correctly detected")
        fun saePKDetected() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.SAE_PK),
                )
            assertTrue(rsn.hasSaePk)
        }

        @Test
        @DisplayName("SAE without PK is not SAE-PK")
        fun saeWithoutPKNotDetected() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.SAE),
                )
            assertFalse(rsn.hasSaePk)
        }

        @Test
        @DisplayName("Mixed SAE and SAE-PK detects SAE-PK")
        fun mixedDetectsSAEPK() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.SAE, AKMSuite.SAE_PK),
                )
            assertTrue(rsn.hasSaePk)
        }

        @Test
        @DisplayName("Empty AKM list doesn't have SAE-PK")
        fun emptyNoSAEPK() {
            val rsn = RSNInfo()
            assertFalse(rsn.hasSaePk)
        }
    }

    @Nested
    @DisplayName("Deprecated Cipher Detection Tests")
    inner class DeprecatedCipherTests {
        @Test
        @DisplayName("TKIP pairwise cipher is deprecated")
        fun tkipPairwiseDeprecated() {
            val rsn =
                RSNInfo(
                    pairwiseCiphers = listOf(CipherSuite.TKIP),
                )
            assertTrue(rsn.hasDeprecatedCiphers)
        }

        @Test
        @DisplayName("TKIP group cipher is deprecated")
        fun tkipGroupDeprecated() {
            val rsn =
                RSNInfo(
                    groupCipher = CipherSuite.TKIP,
                )
            assertTrue(rsn.hasDeprecatedCiphers)
        }

        @Test
        @DisplayName("WEP is deprecated")
        fun wepDeprecated() {
            val rsn =
                RSNInfo(
                    pairwiseCiphers = listOf(CipherSuite.WEP40),
                )
            assertTrue(rsn.hasDeprecatedCiphers)
        }

        @Test
        @DisplayName("CCMP is not deprecated")
        fun ccmpNotDeprecated() {
            val rsn =
                RSNInfo(
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                )
            assertFalse(rsn.hasDeprecatedCiphers)
        }

        @Test
        @DisplayName("Mixed CCMP and TKIP is deprecated")
        fun mixedDeprecated() {
            val rsn =
                RSNInfo(
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128, CipherSuite.TKIP),
                )
            assertTrue(rsn.hasDeprecatedCiphers)
        }

        @Test
        @DisplayName("Deprecated ciphers list includes all deprecated")
        fun deprecatedList() {
            val rsn =
                RSNInfo(
                    groupCipher = CipherSuite.TKIP,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128, CipherSuite.WEP40),
                )
            val deprecated = rsn.deprecatedCiphers
            assertEquals(2, deprecated.size)
            assertTrue(deprecated.contains(CipherSuite.TKIP))
            assertTrue(deprecated.contains(CipherSuite.WEP40))
        }
    }

    @Nested
    @DisplayName("Security Score Tests")
    inner class SecurityScoreTests {
        @Test
        @DisplayName("WPA3-SAE-PK with GCMP-256 scores highest")
        fun wpa3SAEPKHighest() {
            val rsn =
                RSNInfo(
                    groupCipher = CipherSuite.GCMP_256,
                    pairwiseCiphers = listOf(CipherSuite.GCMP_256),
                    akmSuites = listOf(AKMSuite.SAE_PK),
                    pmfRequired = true,
                    beaconProtectionRequired = true,
                )
            val score = rsn.securityScore
            assertTrue(score >= 90, "WPA3-SAE-PK should score >= 90, got $score")
        }

        @Test
        @DisplayName("WPA2-PSK with CCMP scores good")
        fun wpa2PSKGood() {
            val rsn =
                RSNInfo(
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                    akmSuites = listOf(AKMSuite.PSK),
                    pmfCapable = true,
                )
            val score = rsn.securityScore
            assertTrue(score >= 50 && score < 90, "WPA2-PSK should score 50-89, got $score")
        }

        @Test
        @DisplayName("PMF required adds 10 points")
        fun pmfRequiredBonus() {
            val rsnWithoutPMF =
                RSNInfo(
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                    akmSuites = listOf(AKMSuite.SAE),
                    pmfCapable = false,
                    pmfRequired = false,
                )

            val rsnWithPMF =
                RSNInfo(
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                    akmSuites = listOf(AKMSuite.SAE),
                    pmfCapable = true,
                    pmfRequired = true,
                )

            assertTrue(rsnWithPMF.securityScore > rsnWithoutPMF.securityScore)
        }

        @Test
        @DisplayName("Beacon Protection adds points")
        fun beaconProtectionBonus() {
            val rsnWithoutBP =
                RSNInfo(
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                    akmSuites = listOf(AKMSuite.SAE),
                    pmfRequired = true,
                )

            val rsnWithBP =
                RSNInfo(
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                    akmSuites = listOf(AKMSuite.SAE),
                    pmfRequired = true,
                    beaconProtectionRequired = true,
                )

            assertTrue(rsnWithBP.securityScore > rsnWithoutBP.securityScore)
        }

        @Test
        @DisplayName("Security score is bounded 0-100")
        fun scoreBounded() {
            val rsn =
                RSNInfo(
                    groupCipher = CipherSuite.GCMP_256,
                    pairwiseCiphers = listOf(CipherSuite.GCMP_256),
                    akmSuites = listOf(AKMSuite.SAE_PK),
                    pmfRequired = true,
                    beaconProtectionRequired = true,
                )
            assertTrue(rsn.securityScore in 0..100)
        }
    }

    @Nested
    @DisplayName("Best Security Level Tests")
    inner class BestSecurityLevelTests {
        @Test
        @DisplayName("Best cipher is highest pairwise cipher")
        fun bestCipher() {
            val rsn =
                RSNInfo(
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128, CipherSuite.GCMP_256),
                )
            assertEquals(95, rsn.bestCipherSecurityLevel)
        }

        @Test
        @DisplayName("Best AKM is highest AKM")
        fun bestAKM() {
            val rsn =
                RSNInfo(
                    akmSuites = listOf(AKMSuite.PSK, AKMSuite.SAE, AKMSuite.SAE_PK),
                )
            assertEquals(100, rsn.bestAKMSecurityLevel)
        }

        @Test
        @DisplayName("Empty ciphers returns 0")
        fun emptyCiphersZero() {
            val rsn = RSNInfo()
            assertEquals(0, rsn.bestCipherSecurityLevel)
        }

        @Test
        @DisplayName("Empty AKMs returns 0")
        fun emptyAKMsZero() {
            val rsn = RSNInfo()
            assertEquals(0, rsn.bestAKMSecurityLevel)
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    inner class RealWorldScenarios {
        @Test
        @DisplayName("Home WiFi WPA2-Personal")
        fun homeWiFiWPA2() {
            val rsn =
                RSNInfo(
                    version = 1,
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                    akmSuites = listOf(AKMSuite.PSK),
                    pmfCapable = false,
                )

            assertFalse(rsn.isWPA3)
            assertFalse(rsn.hasSaePk)
            assertFalse(rsn.hasDeprecatedCiphers)
            assertTrue(rsn.securityScore >= 50)
        }

        @Test
        @DisplayName("Home WiFi WPA3-Personal (SAE)")
        fun homeWiFiWPA3SAE() {
            val rsn =
                RSNInfo(
                    version = 1,
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                    akmSuites = listOf(AKMSuite.SAE),
                    pmfRequired = true,
                )

            assertTrue(rsn.isWPA3)
            assertFalse(rsn.hasSaePk)
            assertFalse(rsn.hasDeprecatedCiphers)
            assertTrue(rsn.securityScore >= 70)
        }

        @Test
        @DisplayName("Home WiFi WPA3-SAE-PK (evil twin protection)")
        fun homeWiFiSAEPK() {
            val rsn =
                RSNInfo(
                    version = 1,
                    groupCipher = CipherSuite.CCMP_128,
                    pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                    akmSuites = listOf(AKMSuite.SAE_PK),
                    pmfRequired = true,
                    beaconProtectionCapable = true,
                )

            assertTrue(rsn.isWPA3)
            assertTrue(rsn.hasSaePk)
            assertFalse(rsn.hasDeprecatedCiphers)
            assertTrue(rsn.securityScore >= 80)
        }

        @Test
        @DisplayName("Enterprise WiFi WPA3-192bit")
        fun enterpriseWPA3192() {
            val rsn =
                RSNInfo(
                    version = 1,
                    groupCipher = CipherSuite.GCMP_256,
                    pairwiseCiphers = listOf(CipherSuite.GCMP_256),
                    akmSuites = listOf(AKMSuite.SUITE_B_EAP_SHA384),
                    pmfRequired = true,
                    beaconProtectionRequired = true,
                )

            assertTrue(rsn.isWPA3)
            assertFalse(rsn.hasSaePk)
            assertFalse(rsn.hasDeprecatedCiphers)
            assertTrue(rsn.securityScore >= 90)
        }

        @Test
        @DisplayName("Legacy WiFi with TKIP (insecure)")
        fun legacyTKIP() {
            val rsn =
                RSNInfo(
                    version = 1,
                    groupCipher = CipherSuite.TKIP,
                    pairwiseCiphers = listOf(CipherSuite.TKIP),
                    akmSuites = listOf(AKMSuite.PSK),
                )

            assertFalse(rsn.isWPA3)
            assertTrue(rsn.hasDeprecatedCiphers)
            assertTrue(rsn.securityScore < 50)
        }
    }
}
