package io.lamco.netkit.ieparser.parser

import io.lamco.netkit.ieparser.ParsedInformationElements
import io.lamco.netkit.ieparser.WiFiGeneration
import io.lamco.netkit.ieparser.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Comprehensive integration tests for IEParser
 *
 * Uses real-world beacon frame samples to validate:
 * - RSN parsing (WPA2/WPA3, SAE-PK)
 * - HT/VHT/HE/EHT capabilities
 * - WPS detection
 * - Error handling (truncated/invalid data)
 */
@DisplayName("IEParser Integration Tests")
class IEParserTest {
    private val parser = IEParser()

    @Nested
    @DisplayName("RSN Parsing Tests")
    inner class RSNParsingTests {
        @Test
        @DisplayName("Parse WPA2-PSK with CCMP")
        fun parseWPA2PSK() {
            // RSN IE: Version=1, Group=CCMP, Pairwise=CCMP, AKM=PSK, no PMF
            val rsnBytes =
                byteArrayOf(
                    0x01,
                    0x00, // Version: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04, // Group: CCMP-128
                    0x01,
                    0x00, // Pairwise count: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04, // Pairwise: CCMP-128
                    0x01,
                    0x00, // AKM count: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x02, // AKM: PSK
                    0x00,
                    0x00, // RSN Capabilities: none
                )

            val rsn = parser.parseRSN(rsnBytes)

            assertEquals(1, rsn.version)
            assertEquals(CipherSuite.CCMP_128, rsn.groupCipher)
            assertEquals(1, rsn.pairwiseCiphers.size)
            assertEquals(CipherSuite.CCMP_128, rsn.pairwiseCiphers[0])
            assertEquals(1, rsn.akmSuites.size)
            assertEquals(AKMSuite.PSK, rsn.akmSuites[0])
            assertFalse(rsn.pmfRequired)
            assertFalse(rsn.pmfCapable)
        }

        @Test
        @DisplayName("Parse WPA3-SAE with PMF required")
        fun parseWPA3SAE() {
            // RSN IE: Version=1, Group=CCMP, Pairwise=CCMP, AKM=SAE, PMF required
            val rsnBytes =
                byteArrayOf(
                    0x01,
                    0x00, // Version: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04, // Group: CCMP-128
                    0x01,
                    0x00, // Pairwise count: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04, // Pairwise: CCMP-128
                    0x01,
                    0x00, // AKM count: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x08, // AKM: SAE
                    0xC0.toByte(),
                    0x00, // RSN Capabilities: PMF required (bit 6) + capable (bit 7)
                )

            val rsn = parser.parseRSN(rsnBytes)

            assertEquals(AKMSuite.SAE, rsn.akmSuites[0])
            assertTrue(rsn.pmfRequired)
            assertTrue(rsn.pmfCapable)
        }

        @Test
        @DisplayName("Parse WPA3-SAE-PK with Beacon Protection 🏆")
        fun parseWPA3SAEPK() {
            // RSN IE: Version=1, Group=CCMP, Pairwise=CCMP, AKM=SAE-PK, PMF + BP
            val rsnBytes =
                byteArrayOf(
                    0x01,
                    0x00, // Version: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04, // Group: CCMP-128
                    0x01,
                    0x00, // Pairwise count: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04, // Pairwise: CCMP-128
                    0x01,
                    0x00, // AKM count: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x18, // AKM: SAE-PK (24)
                    0xC0.toByte(),
                    0x30, // Capabilities: PMF + Beacon Protection capable (bit 12+13)
                )

            val rsn = parser.parseRSN(rsnBytes)

            assertEquals(AKMSuite.SAE_PK, rsn.akmSuites[0])
            assertTrue(rsn.pmfRequired)
            assertTrue(rsn.pmfCapable)
            assertTrue(rsn.beaconProtectionCapable)
            assertTrue(rsn.beaconProtectionRequired)
        }

        @Test
        @DisplayName("Parse mixed WPA2/WPA3 (transition mode)")
        fun parseMixedMode() {
            // RSN IE with both PSK and SAE
            val rsnBytes =
                byteArrayOf(
                    0x01,
                    0x00, // Version: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04, // Group: CCMP-128
                    0x01,
                    0x00, // Pairwise count: 1
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04, // Pairwise: CCMP-128
                    0x02,
                    0x00, // AKM count: 2
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x02, // AKM: PSK
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x08, // AKM: SAE
                    0x80.toByte(),
                    0x00, // PMF capable
                )

            val rsn = parser.parseRSN(rsnBytes)

            assertEquals(2, rsn.akmSuites.size)
            assertTrue(rsn.akmSuites.contains(AKMSuite.PSK))
            assertTrue(rsn.akmSuites.contains(AKMSuite.SAE))
            assertTrue(rsn.pmfCapable)
            assertFalse(rsn.pmfRequired)
        }

        @Test
        @DisplayName("Parse truncated RSN returns default")
        fun parseTruncatedRSN() {
            val rsnBytes = byteArrayOf(0x01) // Only 1 byte
            val rsn = parser.parseRSN(rsnBytes)
            assertEquals(RSNInfo(), rsn)
        }
    }

    @Nested
    @DisplayName("HT Capabilities Parsing Tests")
    inner class HTParsingTests {
        @Test
        @DisplayName("Parse HT 20 MHz, 1 stream")
        fun parseHT20() {
            val htBytes = ByteArray(26) { 0 }
            // HT Caps: no 40 MHz, no short GI
            htBytes[0] = 0x00
            htBytes[1] = 0x00
            // A-MPDU params
            htBytes[2] = 0x00
            // MCS set: only MCS 0-7 (1 stream)
            htBytes[3] = 0xFF.toByte() // MCS 0-7

            val ht = parser.parseHTCapabilities(htBytes)

            assertFalse(ht.supports40MHz)
            assertFalse(ht.shortGI20MHz)
            assertFalse(ht.shortGI40MHz)
            assertEquals(1, ht.maxSpatialStreams)
        }

        @Test
        @DisplayName("Parse HT 40 MHz, 2 streams, Short GI")
        fun parseHT40() {
            val htBytes = ByteArray(26) { 0 }
            // HT Caps: 40 MHz (bit 1), Short GI 20 (bit 5), Short GI 40 (bit 6)
            htBytes[0] = 0x62 // 0110 0010
            htBytes[1] = 0x00
            // A-MPDU params
            htBytes[2] = 0x00
            // MCS set: MCS 0-15 (2 streams)
            htBytes[3] = 0xFF.toByte() // MCS 0-7
            htBytes[4] = 0xFF.toByte() // MCS 8-15

            val ht = parser.parseHTCapabilities(htBytes)

            assertTrue(ht.supports40MHz)
            assertTrue(ht.shortGI20MHz)
            assertTrue(ht.shortGI40MHz)
            assertEquals(2, ht.maxSpatialStreams)
        }
    }

    @Nested
    @DisplayName("VHT Capabilities Parsing Tests")
    inner class VHTParsingTests {
        @Test
        @DisplayName("Parse VHT 80 MHz, 2 streams")
        fun parseVHT80() {
            val vhtBytes = ByteArray(12) { 0 }
            // VHT Caps: 80 MHz (mandatory), 2 streams
            // Channel width bits 2-3: 00 = 80 MHz only
            vhtBytes[0] = 0x00
            vhtBytes[1] = 0x00
            vhtBytes[2] = 0x00
            vhtBytes[3] = 0x00
            // MCS map (8 bytes): 2 streams with MCS 0-9
            vhtBytes[4] = 0xFA.toByte() // Stream 1: 10, Stream 2: 10 (MCS 0-9)
            vhtBytes[5] = 0xFF.toByte() // Streams 3-4: not supported

            val vht = parser.parseVHTCapabilities(vhtBytes)

            assertTrue(vht.supports80MHz)
            assertFalse(vht.supports160MHz)
            assertEquals(2, vht.maxSpatialStreams)
        }

        @Test
        @DisplayName("Parse VHT 160 MHz, MU-MIMO")
        fun parseVHT160() {
            val vhtBytes = ByteArray(12) { 0 }
            // VHT Caps: 160 MHz (bits 2-3 = 01), MU-MIMO (bit 19)
            vhtBytes[0] = 0x04 // Bit 2 set
            vhtBytes[1] = 0x00
            vhtBytes[2] = 0x08 // Bit 19 set (MU-MIMO)
            vhtBytes[3] = 0x00
            // MCS map: 2 streams
            vhtBytes[4] = 0xFA.toByte()

            val vht = parser.parseVHTCapabilities(vhtBytes)

            assertTrue(vht.supports160MHz)
            assertTrue(vht.muMimoCapable)
        }
    }

    @Nested
    @DisplayName("HE Capabilities Parsing Tests")
    inner class HEParsingTests {
        @Test
        @DisplayName("Parse HE 80 MHz with OFDMA and TWT")
        fun parseHE80() {
            val heBytes = ByteArray(18) { 0 }
            heBytes[0] = 35 // Extension ID

            // MAC caps (6 bytes): TWT requester (bit 1)
            heBytes[1] = 0x02

            // PHY caps (11 bytes): 80 MHz in 5 GHz (bits 1-7)
            heBytes[7] = 0x0C // Bits for 80 MHz

            val he = parser.parseHECapabilities(heBytes)

            assertTrue(he.ofdmaSupport)
            assertTrue(he.twtRequesterSupport)
            assertTrue(he.supports80MHzIn5GHz)
        }
    }

    @Nested
    @DisplayName("Vendor-Specific IE Tests")
    inner class VendorSpecificTests {
        @Test
        @DisplayName("Detect WPS enabled")
        fun detectWPS() {
            // Microsoft WPS: OUI 00-50-F2, type 04
            val wpsBytes =
                byteArrayOf(
                    0x00,
                    0x50,
                    0xF2.toByte(), // Microsoft OUI
                    0x04, // WPS type
                )

            val parsed = parser.parseVendorSpecific(wpsBytes, ParsedInformationElements())

            assertTrue(parsed.wpsEnabled)
        }

        @Test
        @DisplayName("Non-WPS vendor IE doesn't set WPS")
        fun nonWPSVendor() {
            val vendorBytes =
                byteArrayOf(
                    0x00,
                    0x50,
                    0xF2.toByte(),
                    0x01, // Not WPS
                )

            val parsed = parser.parseVendorSpecific(vendorBytes, ParsedInformationElements())

            assertFalse(parsed.wpsEnabled)
        }
    }

    @Nested
    @DisplayName("Integration Tests - Complete Beacon Frames")
    inner class IntegrationTests {
        @Test
        @DisplayName("WPA2-Personal home network")
        fun wpa2HomeNetwork() {
            val rsnBytes =
                byteArrayOf(
                    0x01,
                    0x00,
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04,
                    0x01,
                    0x00,
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04,
                    0x01,
                    0x00,
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x02,
                    0x00,
                    0x00,
                )

            val htBytes = ByteArray(26) { 0 }
            htBytes[0] = 0x62
            htBytes[3] = 0xFF.toByte()
            htBytes[4] = 0xFF.toByte()

            val elements =
                listOf(
                    IEData(id = 48, bytes = rsnBytes),
                    IEData(id = 45, bytes = htBytes),
                )

            val parsed = parser.parseAll(elements)

            assertEquals(WiFiGeneration.WIFI_4, parsed.wifiGeneration)
            assertFalse(parsed.isWPA3)
            assertFalse(parsed.hasSaePk)
            assertTrue(parsed.securityScore >= 50)
        }

        @Test
        @DisplayName("WPA3-SAE-PK WiFi 6 network 🏆")
        fun wpa3SAEPKWiFi6() {
            val rsnBytes =
                byteArrayOf(
                    0x01,
                    0x00,
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04,
                    0x01,
                    0x00,
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x04,
                    0x01,
                    0x00,
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0x18, // SAE-PK
                    0xC0.toByte(),
                    0x30,
                )

            val heBytes = ByteArray(18) { 0 }
            heBytes[0] = 35
            heBytes[1] = 0x02
            heBytes[7] = 0x0C

            val elements =
                listOf(
                    IEData(id = 48, bytes = rsnBytes),
                    IEData(id = 255, idExt = 35, bytes = heBytes),
                )

            val parsed = parser.parseAll(elements)

            assertEquals(WiFiGeneration.WIFI_6, parsed.wifiGeneration)
            assertTrue(parsed.isWPA3)
            assertTrue(parsed.hasSaePk)
            assertTrue(parsed.pmfRequired)
            assertTrue(parsed.beaconProtection)
            assertTrue(parsed.securityScore >= 80)
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {
        @Test
        @DisplayName("Empty byte array returns empty result")
        fun emptyBytes() {
            val rsn = parser.parseRSN(ByteArray(0))
            assertEquals(RSNInfo(), rsn)
        }

        @Test
        @DisplayName("Truncated IE doesn't crash")
        fun truncatedIE() {
            val rsnBytes = byteArrayOf(0x01, 0x00, 0x00)
            val rsn = parser.parseRSN(rsnBytes)
            assertNotNull(rsn)
        }

        @Test
        @DisplayName("Invalid cipher type returns UNKNOWN")
        fun invalidCipherType() {
            val rsnBytes =
                byteArrayOf(
                    0x01,
                    0x00,
                    0x00,
                    0x0F,
                    0xAC.toByte(),
                    0xFF.toByte(), // Invalid type
                    0x00,
                    0x00,
                )
            val rsn = parser.parseRSN(rsnBytes)
            assertEquals(CipherSuite.UNKNOWN, rsn.groupCipher)
        }

        @Test
        @DisplayName("parseAll with empty list returns empty result")
        fun parseAllEmpty() {
            val parsed = parser.parseAll(emptyList())
            assertEquals(ParsedInformationElements(), parsed)
        }

        @Test
        @DisplayName("Unknown IE ID is ignored")
        fun unknownIEIgnored() {
            val parsed =
                parser.parseElement(
                    id = 999,
                    idExt = 0,
                    bytes = ByteArray(10),
                    parsed = ParsedInformationElements(),
                )
            assertEquals(ParsedInformationElements(), parsed)
        }
    }
}
