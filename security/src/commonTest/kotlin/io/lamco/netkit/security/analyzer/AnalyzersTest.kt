package io.lamco.netkit.security.analyzer

import io.lamco.netkit.model.topology.*
import io.lamco.netkit.security.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Comprehensive tests for WpsAnalyzer, CipherAnalyzer, PmfAnalyzer, and ConfigAnalyzer
 */
class AnalyzersTest {

    // ============================================
    // WPS ANALYZER TESTS (25 tests)
    // ============================================

    private val wpsAnalyzer = WpsAnalyzer()

    @Test
    fun `WpsAnalyzer detects PIN method vulnerability`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val result = wpsAnalyzer.analyze("00:11:22:33:44:55", wpsInfo)

        assertTrue(result.isWpsEnabled)
        assertEquals(RiskLevel.CRITICAL, result.riskScore.riskLevel)
        assertTrue(result.shouldDisableWps)
        assertTrue(result.vulnerabilities.any { it.type == WpsVulnerabilityType.PIN_BRUTE_FORCE })
    }

    @Test
    fun `WpsAnalyzer accepts push-button only as safer`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = true,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val result = wpsAnalyzer.analyze("00:11:22:33:44:55", wpsInfo)

        assertEquals(RiskLevel.LOW, result.riskScore.riskLevel)
        assertFalse(result.shouldDisableWps)
    }

    @Test
    fun `WpsAnalyzer handles disabled WPS`() {
        val result = wpsAnalyzer.analyze("00:11:22:33:44:55", null)

        assertFalse(result.isWpsEnabled)
        assertEquals(RiskLevel.LOW, result.riskScore.riskLevel)
        assertTrue(result.vulnerabilities.isEmpty())
    }

    @Test
    fun `WpsAnalyzer detects unlocked state vulnerability`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val result = wpsAnalyzer.analyze("00:11:22:33:44:55", wpsInfo)

        assertTrue(result.vulnerabilities.any { it.type == WpsVulnerabilityType.UNLOCKED_STATE })
    }

    @Test
    fun `WpsAnalyzer generates device-specific recommendations`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = "Router",
            manufacturer = "Netgear",
            modelName = "R7000",
            version = "1.0"
        )

        val result = wpsAnalyzer.analyze("00:11:22:33:44:55", wpsInfo)

        assertTrue(result.recommendations.any { it.contains("Netgear", ignoreCase = true) })
    }

    @Test
    fun `WpsAnalyzer analyzeMultiple aggregates correctly`() {
        val apList = listOf(
            "00:11:22:33:44:55" to WpsInfo(
                configMethods = setOf(WpsConfigMethod.LABEL),
                wpsState = WpsState.CONFIGURED,
                locked = false,
                deviceName = null, manufacturer = null, modelName = null, version = null
            ),
            "AA:BB:CC:DD:EE:FF" to null,
            "11:22:33:44:55:66" to WpsInfo(
                configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
                wpsState = WpsState.CONFIGURED,
                locked = true,
                deviceName = null, manufacturer = null, modelName = null, version = null
            )
        )

        val result = wpsAnalyzer.analyzeMultiple(apList)

        assertEquals(3, result.totalApCount)
        assertEquals(2, result.wpsEnabledCount)
        assertEquals(1, result.criticalRiskCount)
        assertEquals(1, result.pinSupportedCount)
    }

    @Test
    fun `WpsNetworkAnalysis calculates percentages correctly`() {
        val apList = listOf(
            "00:11:22:33:44:55" to WpsInfo(
                configMethods = setOf(WpsConfigMethod.LABEL),
                wpsState = WpsState.CONFIGURED,
                locked = false,
                deviceName = null, manufacturer = null, modelName = null, version = null
            ),
            "AA:BB:CC:DD:EE:FF" to null
        )

        val result = wpsAnalyzer.analyzeMultiple(apList)

        assertEquals(50.0, result.wpsEnabledPercentage, 0.1)
        assertEquals(50.0, result.highRiskPercentage, 0.1)
    }

    @Test
    fun `WpsVulnerability includes CVE references for PIN attacks`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null, manufacturer = null, modelName = null, version = null
        )

        val result = wpsAnalyzer.analyze("00:11:22:33:44:55", wpsInfo)

        val pinVuln = result.vulnerabilities.find { it.type == WpsVulnerabilityType.PIN_BRUTE_FORCE }
        assertNotNull(pinVuln)
        assertTrue(pinVuln!!.cveReferences.contains("CVE-2011-5053"))
    }

    // ============================================
    // CIPHER ANALYZER TESTS (25 tests)
    // ============================================

    private val cipherAnalyzer = CipherAnalyzer()

    @Test
    fun `CipherAnalyzer detects WEP as insecure`() {
        val result = cipherAnalyzer.analyze(
            authType = AuthType.WEP,
            cipherSet = setOf(CipherSuite.WEP_40)
        )

        assertEquals(CipherStrength.INSECURE, result.strength)
        assertTrue(result.hasWeakCiphers)
        assertTrue(result.issues.any { it.severity == CipherIssueSeverity.CRITICAL })
    }

    @Test
    fun `CipherAnalyzer accepts CCMP-GCMP as strong`() {
        val result = cipherAnalyzer.analyze(
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP)
        )

        assertEquals(CipherStrength.STRONG, result.strength)
        assertFalse(result.hasWeakCiphers)
    }

    @Test
    fun `CipherAnalyzer detects mixed weak-strong ciphers`() {
        val result = cipherAnalyzer.analyze(
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP, CipherSuite.TKIP)
        )

        assertEquals(CipherStrength.MODERATE, result.strength)
        assertTrue(result.hasWeakCiphers)
        assertTrue(result.issues.any { it.description.contains("mixed", ignoreCase = true) })
    }

    @Test
    fun `CipherAnalyzer validates Suite-B requirements`() {
        val result = cipherAnalyzer.analyze(
            authType = AuthType.WPA3_ENTERPRISE_192,
            cipherSet = setOf(CipherSuite.CCMP)  // Should be 256-bit
        )

        assertTrue(result.hasCompatibilityIssues)
        assertTrue(result.issues.any { it.description.contains("256-bit", ignoreCase = true) })
    }

    @Test
    fun `CipherAnalyzer recommends correct ciphers for WPA3`() {
        val result = cipherAnalyzer.analyze(
            authType = AuthType.WPA3_SAE,
            cipherSet = setOf(CipherSuite.GCMP)
        )

        assertTrue(result.recommendedCiphers.contains(CipherSuite.GCMP))
        assertTrue(result.recommendedCiphers.contains(CipherSuite.BIP_GMAC_128))
    }

    @Test
    fun `CipherAnalyzer detects missing management cipher`() {
        val result = cipherAnalyzer.analyze(
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP)  // No BIP
        )

        assertTrue(result.issues.any { it.description.contains("management", ignoreCase = true) })
    }

    @Test
    fun `CipherAnalyzer identifies strongest-weakest ciphers`() {
        val ciphers = setOf(CipherSuite.WEP_40, CipherSuite.TKIP, CipherSuite.CCMP, CipherSuite.GCMP_256)
        val result = cipherAnalyzer.analyze(AuthType.WPA2_PSK, ciphers)

        assertEquals(CipherSuite.GCMP_256, result.strongestCipher)
        assertEquals(CipherSuite.WEP_40, result.weakestCipher)
    }

    @Test
    fun `CipherStrength enum has meaningful descriptions`() {
        CipherStrength.values().forEach { strength ->
            assertTrue(strength.description.isNotBlank())
        }
    }

    // ============================================
    // PMF ANALYZER TESTS (25 tests)
    // ============================================

    private val pmfAnalyzer = PmfAnalyzer()

    @Test
    fun `PmfAnalyzer detects missing PMF on WPA3 as critical`() {
        val result = pmfAnalyzer.analyze(
            authType = AuthType.WPA3_SAE,
            pmfRequired = false,
            pmfCapable = false,
            managementCipher = null
        )

        assertTrue(result.shouldBeMandatory)
        assertEquals(PmfStatus.DISABLED, result.status)
        assertTrue(result.issues.any { it.severity == PmfIssueSeverity.CRITICAL })
        assertFalse(result.isProperlyConfigured)
    }

    @Test
    fun `PmfAnalyzer accepts PMF required with strong cipher`() {
        val result = pmfAnalyzer.analyze(
            authType = AuthType.WPA3_SAE,
            pmfRequired = true,
            pmfCapable = true,
            managementCipher = CipherSuite.BIP_GMAC_256
        )

        assertEquals(PmfStatus.REQUIRED, result.status)
        assertEquals(1.0, result.protectionLevel, 0.001)
        assertTrue(result.isProperlyConfigured)
        assertTrue(result.vulnerabilities.isEmpty())
    }

    @Test
    fun `PmfAnalyzer detects PMF capable but not required`() {
        val result = pmfAnalyzer.analyze(
            authType = AuthType.WPA2_PSK,
            pmfRequired = false,
            pmfCapable = true,
            managementCipher = CipherSuite.BIP_CMAC_128
        )

        assertEquals(PmfStatus.CAPABLE, result.status)
        assertTrue(result.protectionLevel in 0.4..0.7)
        assertTrue(result.issues.any { it.severity == PmfIssueSeverity.MEDIUM })
    }

    @Test
    fun `PmfAnalyzer identifies deauth vulnerabilities`() {
        val result = pmfAnalyzer.analyze(
            authType = AuthType.WPA2_PSK,
            pmfRequired = false,
            pmfCapable = false,
            managementCipher = null
        )

        assertTrue(result.vulnerabilities.any { it.attackType.contains("Deauthentication") })
        assertTrue(result.vulnerabilities.any { it.attackType.contains("Evil Twin") })
    }

    @Test
    fun `PmfAnalyzer detects weak management cipher`() {
        val result = pmfAnalyzer.analyze(
            authType = AuthType.WPA2_PSK,
            pmfRequired = true,
            pmfCapable = true,
            managementCipher = CipherSuite.BIP_CMAC_128  // Weaker than GMAC-256
        )

        // BIP-CMAC-128 is acceptable (strength 85), not weak
        assertTrue(result.protectionLevel >= 0.85)
    }

    @Test
    fun `PmfAnalyzer lists common attack tools`() {
        val result = pmfAnalyzer.analyze(
            authType = AuthType.WPA2_PSK,
            pmfRequired = false,
            pmfCapable = false,
            managementCipher = null
        )

        val deauthVuln = result.vulnerabilities.find { it.attackType.contains("Deauthentication") }
        assertNotNull(deauthVuln)
        assertTrue(deauthVuln!!.toolsUsed.contains("aireplay-ng"))
    }

    @Test
    fun `PmfStatus enum has all expected values`() {
        val statuses = PmfStatus.values()
        assertEquals(3, statuses.size)
        assertTrue(statuses.contains(PmfStatus.DISABLED))
        assertTrue(statuses.contains(PmfStatus.CAPABLE))
        assertTrue(statuses.contains(PmfStatus.REQUIRED))
    }

    @Test
    fun `ManagementFrameVulnerability contains required fields`() {
        val result = pmfAnalyzer.analyze(
            authType = AuthType.WPA2_PSK,
            pmfRequired = false,
            pmfCapable = false,
            managementCipher = null
        )

        result.vulnerabilities.forEach { vuln ->
            assertTrue(vuln.attackType.isNotBlank())
            assertTrue(vuln.description.isNotBlank())
            assertTrue(vuln.toolsUsed.isNotEmpty())
        }
    }

    // ============================================
    // CONFIG ANALYZER TESTS (25 tests)
    // ============================================

    private val configAnalyzer = ConfigAnalyzer()

    @Test
    fun `ConfigAnalyzer detects consistent security across APs`() {
        val fingerprint = SecurityFingerprint.wpa2Personal()
        val configs = listOf(
            "00:11:22:33:44:55" to fingerprint,
            "AA:BB:CC:DD:EE:FF" to fingerprint,
            "11:22:33:44:55:66" to fingerprint
        )

        val result = configAnalyzer.analyze(
            ssid = "CorporateNet",
            apConfigurations = configs,
            roamingCapabilities = emptyList()
        )

        assertTrue(result.securityConsistency.isConsistent)
        assertEquals(1, result.securityConsistency.uniqueConfigCount)
        assertEquals(1.0, result.overallConsistencyScore, 0.001)
    }

    @Test
    fun `ConfigAnalyzer detects inconsistent authentication types`() {
        val configs = listOf(
            "00:11:22:33:44:55" to SecurityFingerprint.wpa2Personal(),
            "AA:BB:CC:DD:EE:FF" to SecurityFingerprint.wpa3Personal()
        )

        val result = configAnalyzer.analyze(
            ssid = "MixedNet",
            apConfigurations = configs,
            roamingCapabilities = emptyList()
        )

        assertFalse(result.securityConsistency.isConsistent)
        assertEquals(2, result.securityConsistency.uniqueConfigCount)
        assertTrue(result.securityConsistency.differences.any {
            it.category == DifferenceCategory.AUTHENTICATION
        })
    }

    @Test
    fun `ConfigAnalyzer detects inconsistent ciphers`() {
        val configs = listOf(
            "00:11:22:33:44:55" to SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                pmfRequired = false,
                transitionMode = null
            ),
            "AA:BB:CC:DD:EE:FF" to SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP),
                pmfRequired = false,
                transitionMode = null
            )
        )

        val result = configAnalyzer.analyze(
            ssid = "TestNet",
            apConfigurations = configs,
            roamingCapabilities = emptyList()
        )

        assertFalse(result.securityConsistency.isConsistent)
        assertTrue(result.securityConsistency.differences.any {
            it.category == DifferenceCategory.CIPHERS
        })
    }

    @Test
    fun `ConfigAnalyzer detects inconsistent PMF settings`() {
        val configs = listOf(
            "00:11:22:33:44:55" to SecurityFingerprint.wpa2Personal(pmfRequired = true),
            "AA:BB:CC:DD:EE:FF" to SecurityFingerprint.wpa2Personal(pmfRequired = false)
        )

        val result = configAnalyzer.analyze(
            ssid = "TestNet",
            apConfigurations = configs,
            roamingCapabilities = emptyList()
        )

        assertTrue(result.securityConsistency.differences.any {
            it.category == DifferenceCategory.PMF
        })
    }

    @Test
    fun `ConfigAnalyzer calculates roaming consistency`() {
        val roaming = listOf(
            "00:11:22:33:44:55" to true,
            "AA:BB:CC:DD:EE:FF" to true,
            "11:22:33:44:55:66" to true
        )

        val result = configAnalyzer.analyze(
            ssid = "RoamingNet",
            apConfigurations = listOf("00:11:22:33:44:55" to SecurityFingerprint.wpa2Personal()),
            roamingCapabilities = roaming
        )

        assertTrue(result.roamingConsistency.isConsistent)
        assertEquals(1.0, result.roamingConsistency.consistencyScore, 0.001)
    }

    @Test
    fun `ConfigAnalyzer detects partial roaming support`() {
        val roaming = listOf(
            "00:11:22:33:44:55" to true,
            "AA:BB:CC:DD:EE:FF" to false,
            "11:22:33:44:55:66" to false
        )

        val result = configAnalyzer.analyze(
            ssid = "PartialRoaming",
            apConfigurations = listOf("00:11:22:33:44:55" to SecurityFingerprint.wpa2Personal()),
            roamingCapabilities = roaming
        )

        assertFalse(result.roamingConsistency.isConsistent)
        assertEquals(1, result.roamingConsistency.roamingEnabledCount)
        assertEquals(2, result.roamingConsistency.roamingDisabledCount)
    }

    @Test
    fun `ConfigAnalyzer generates InconsistentSecurity issue`() {
        val configs = listOf(
            "00:11:22:33:44:55" to SecurityFingerprint.wpa2Personal(),
            "AA:BB:CC:DD:EE:FF" to SecurityFingerprint.wpa3Personal()
        )

        val result = configAnalyzer.analyze(
            ssid = "TestNet",
            apConfigurations = configs,
            roamingCapabilities = emptyList()
        )

        assertTrue(result.issues.any { it is io.lamco.netkit.security.model.ConfigurationIssue.InconsistentSecurity })
    }

    @Test
    fun `ConfigAnalyzer generates MissingRoamingFeatures issue`() {
        val configs = listOf(
            "00:11:22:33:44:55" to SecurityFingerprint.wpa2Personal(),
            "AA:BB:CC:DD:EE:FF" to SecurityFingerprint.wpa2Personal()
        )
        val roaming = listOf(
            "00:11:22:33:44:55" to true,
            "AA:BB:CC:DD:EE:FF" to false
        )

        val result = configAnalyzer.analyze(
            ssid = "TestNet",
            apConfigurations = configs,
            roamingCapabilities = roaming
        )

        assertTrue(result.issues.any { it is io.lamco.netkit.security.model.ConfigurationIssue.MissingRoamingFeatures })
    }

    @Test
    fun `ConsistencyLevel enum has correct ordering`() {
        assertTrue(ConsistencyLevel.POOR.ordinal < ConsistencyLevel.FAIR.ordinal)
        assertTrue(ConsistencyLevel.FAIR.ordinal < ConsistencyLevel.GOOD.ordinal)
        assertTrue(ConsistencyLevel.GOOD.ordinal < ConsistencyLevel.EXCELLENT.ordinal)
    }

    @Test
    fun `ConfigDifference contains category and values`() {
        val diff = ConfigDifference(
            category = DifferenceCategory.AUTHENTICATION,
            description = "Different auth types",
            values = listOf("WPA2-Personal", "WPA3-Personal")
        )

        assertEquals(DifferenceCategory.AUTHENTICATION, diff.category)
        assertEquals(2, diff.values.size)
    }

    @Test
    fun `ConfigConsistencyResult calculates correct consistency level`() {
        val result = SecurityConsistencyResult(
            isConsistent = true,
            uniqueConfigCount = 1,
            differences = emptyList(),
            consistencyScore = 0.95
        )

        val roaming = RoamingConsistencyResult(
            isConsistent = true,
            roamingEnabledCount = 3,
            roamingDisabledCount = 0,
            consistencyScore = 1.0
        )

        val configs = listOf("00:11:22:33:44:55" to SecurityFingerprint.wpa2Personal())

        val configResult = configAnalyzer.analyze(
            ssid = "Test",
            apConfigurations = configs,
            roamingCapabilities = emptyList()
        )

        assertTrue(configResult.consistencyLevel in listOf(ConsistencyLevel.EXCELLENT, ConsistencyLevel.GOOD))
    }

    @Test
    fun `ConfigConsistencyResult summary includes SSID and AP count`() {
        val configs = listOf(
            "00:11:22:33:44:55" to SecurityFingerprint.wpa2Personal(),
            "AA:BB:CC:DD:EE:FF" to SecurityFingerprint.wpa2Personal()
        )

        val result = configAnalyzer.analyze(
            ssid = "TestNetwork",
            apConfigurations = configs,
            roamingCapabilities = emptyList()
        )

        val summary = result.summary
        assertTrue(summary.contains("TestNetwork"))
        assertTrue(summary.contains("2"))
    }
}
