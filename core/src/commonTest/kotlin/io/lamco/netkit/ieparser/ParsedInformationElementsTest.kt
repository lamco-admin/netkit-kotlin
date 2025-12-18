package io.lamco.netkit.ieparser

import io.lamco.netkit.ieparser.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ParsedInformationElements Tests")
class ParsedInformationElementsTest {
    @Test fun wifiGeneration_WiFi7() {
        val parsed = ParsedInformationElements(ehtCapabilities = EHTCapabilities())
        assertEquals(WiFiGeneration.WIFI_7, parsed.wifiGeneration)
    }

    @Test fun wifiGeneration_WiFi6() {
        val parsed = ParsedInformationElements(heCapabilities = HECapabilities())
        assertEquals(WiFiGeneration.WIFI_6, parsed.wifiGeneration)
    }

    @Test fun wifiGeneration_WiFi5() {
        val parsed = ParsedInformationElements(vhtCapabilities = VHTCapabilities())
        assertEquals(WiFiGeneration.WIFI_5, parsed.wifiGeneration)
    }

    @Test fun wifiGeneration_WiFi4() {
        val parsed = ParsedInformationElements(htCapabilities = HTCapabilities())
        assertEquals(WiFiGeneration.WIFI_4, parsed.wifiGeneration)
    }

    @Test fun wifiGeneration_Legacy() {
        val parsed = ParsedInformationElements()
        assertEquals(WiFiGeneration.LEGACY, parsed.wifiGeneration)
    }

    @Test fun isWPA3_true() {
        val rsn = RSNInfo(akmSuites = listOf(AKMSuite.SAE))
        val parsed = ParsedInformationElements(rsnInfo = rsn)
        assertTrue(parsed.isWPA3)
    }

    @Test fun hasSaePk_true() {
        val rsn = RSNInfo(akmSuites = listOf(AKMSuite.SAE_PK))
        val parsed = ParsedInformationElements(rsnInfo = rsn)
        assertTrue(parsed.hasSaePk)
    }

    @Test fun pmfRequired_true() {
        val rsn = RSNInfo(pmfRequired = true)
        val parsed = ParsedInformationElements(rsnInfo = rsn)
        assertTrue(parsed.pmfRequired)
    }

    @Test fun beaconProtection_true() {
        val rsn = RSNInfo(beaconProtectionCapable = true)
        val parsed = ParsedInformationElements(rsnInfo = rsn)
        assertTrue(parsed.beaconProtection)
    }

    @Test fun hasDeprecatedCiphers_true() {
        val rsn = RSNInfo(pairwiseCiphers = listOf(CipherSuite.TKIP))
        val parsed = ParsedInformationElements(rsnInfo = rsn)
        assertTrue(parsed.hasDeprecatedCiphers)
    }

    @Test fun maxSpatialStreams_acrossGenerations() {
        val parsed =
            ParsedInformationElements(
                htCapabilities = HTCapabilities(maxSpatialStreams = 2),
                vhtCapabilities = VHTCapabilities(maxSpatialStreams = 4),
                heCapabilities = HECapabilities(maxSpatialStreams = 2),
            )
        assertEquals(4, parsed.maxSpatialStreams)
    }

    @Test fun maxChannelWidthMHz_WiFi7() {
        val parsed =
            ParsedInformationElements(
                ehtCapabilities = EHTCapabilities(supports320MHz = true),
            )
        assertEquals(320, parsed.maxChannelWidthMHz)
    }

    @Test fun maxChannelWidthMHz_WiFi6() {
        val parsed =
            ParsedInformationElements(
                heCapabilities = HECapabilities(supports160MHz = true),
            )
        assertEquals(160, parsed.maxChannelWidthMHz)
    }

    @Test fun supportsMUMIMO_true() {
        val parsed =
            ParsedInformationElements(
                vhtCapabilities = VHTCapabilities(muMimoCapable = true),
            )
        assertTrue(parsed.supportsMUMIMO)
    }

    @Test fun supportsOFDMA_true() {
        val parsed =
            ParsedInformationElements(
                heCapabilities = HECapabilities(ofdmaSupport = true),
            )
        assertTrue(parsed.supportsOFDMA)
    }

    @Test fun supportsMLO_true() {
        val parsed =
            ParsedInformationElements(
                ehtCapabilities = EHTCapabilities(multiLinkOperation = true),
            )
        assertTrue(parsed.supportsMLO)
    }

    @Test fun securityScore_WPA3SAEPK() {
        val rsn =
            RSNInfo(
                groupCipher = CipherSuite.GCMP_256,
                pairwiseCiphers = listOf(CipherSuite.GCMP_256),
                akmSuites = listOf(AKMSuite.SAE_PK),
                pmfRequired = true,
            )
        val parsed = ParsedInformationElements(rsnInfo = rsn)
        assertTrue(parsed.securityScore >= 80)
    }

    @Test fun securityScore_WiFi7Bonus() {
        val rsn =
            RSNInfo(
                groupCipher = CipherSuite.CCMP_128,
                pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                akmSuites = listOf(AKMSuite.SAE),
            )
        val parsedWiFi6 = ParsedInformationElements(rsnInfo = rsn, heCapabilities = HECapabilities())
        val parsedWiFi7 = ParsedInformationElements(rsnInfo = rsn, ehtCapabilities = EHTCapabilities())
        assertTrue(parsedWiFi7.securityScore >= parsedWiFi6.securityScore)
    }

    @Test fun securityScore_WPSPenalty() {
        val rsn =
            RSNInfo(
                groupCipher = CipherSuite.CCMP_128,
                pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                akmSuites = listOf(AKMSuite.SAE),
                pmfRequired = true,
            )
        val withoutWPS = ParsedInformationElements(rsnInfo = rsn)
        val withWPS = ParsedInformationElements(rsnInfo = rsn, wpsEnabled = true)
        assertTrue(withoutWPS.securityScore > withWPS.securityScore)
    }

    @Test fun performanceScore_WiFi7Highest() {
        val wifi7 = ParsedInformationElements(ehtCapabilities = EHTCapabilities())
        val wifi6 = ParsedInformationElements(heCapabilities = HECapabilities())
        assertTrue(wifi7.performanceScore > wifi6.performanceScore)
    }

    @Test fun criticalSecurityIssues_deprecatedCiphers() {
        val rsn = RSNInfo(pairwiseCiphers = listOf(CipherSuite.TKIP))
        val parsed = ParsedInformationElements(rsnInfo = rsn)
        val issues = parsed.criticalSecurityIssues
        assertTrue(issues.any { it.contains("Deprecated") })
    }

    @Test fun criticalSecurityIssues_wps() {
        val parsed = ParsedInformationElements(wpsEnabled = true)
        val issues = parsed.criticalSecurityIssues
        assertTrue(issues.any { it.contains("WPS") })
    }

    @Test fun criticalSecurityIssues_noRSN() {
        val parsed = ParsedInformationElements()
        val issues = parsed.criticalSecurityIssues
        assertTrue(issues.any { it.contains("No RSN") })
    }

    @Test fun hasCriticalSecurityIssues_true() {
        val parsed = ParsedInformationElements(wpsEnabled = true)
        assertTrue(parsed.hasCriticalSecurityIssues)
    }

    @Test fun hasCriticalSecurityIssues_false() {
        val rsn =
            RSNInfo(
                groupCipher = CipherSuite.CCMP_128,
                pairwiseCiphers = listOf(CipherSuite.CCMP_128),
                akmSuites = listOf(AKMSuite.SAE),
                pmfRequired = true,
            )
        val parsed = ParsedInformationElements(rsnInfo = rsn)
        assertFalse(parsed.hasCriticalSecurityIssues)
    }
}
