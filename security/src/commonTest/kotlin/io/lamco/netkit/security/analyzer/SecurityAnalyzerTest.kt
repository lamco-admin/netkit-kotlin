package io.lamco.netkit.security.analyzer

import io.lamco.netkit.model.topology.*
import io.lamco.netkit.security.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Comprehensive tests for SecurityAnalyzer
 */
class SecurityAnalyzerTest {
    private val analyzer = SecurityAnalyzer()

    @Test
    fun `analyzeBss with WPA3 creates excellent security analysis`() {
        val fingerprint = SecurityFingerprint.wpa3Personal()

        val result =
            analyzer.analyzeBss(
                bssid = "00:11:22:33:44:55",
                ssid = "SecureNetwork",
                fingerprint = fingerprint,
                pmfCapable = true,
                managementCipher = CipherSuite.BIP_GMAC_128,
            )

        assertEquals("00:11:22:33:44:55", result.bssid)
        assertEquals("SecureNetwork", result.ssid)
        assertEquals(SecurityLevel.EXCELLENT, result.securityScore.securityLevel)
        assertFalse(result.requiresImmediateAction)
    }

    @Test
    fun `analyzeBss with WEP creates critical threat level`() {
        val fingerprint =
            SecurityFingerprint(
                authType = AuthType.WEP,
                cipherSet = setOf(CipherSuite.WEP_40),
                pmfRequired = false,
                transitionMode = null,
            )

        val result =
            analyzer.analyzeBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "OldNetwork",
                fingerprint = fingerprint,
            )

        assertEquals(ThreatLevel.CRITICAL, result.threatLevel)
        assertTrue(result.requiresImmediateAction)
        assertTrue(result.criticalIssues.isNotEmpty())
    }

    @Test
    fun `analyzeBss with critical WPS risk overrides other threats`() {
        val fingerprint = SecurityFingerprint.wpa2Personal(pmfRequired = false)
        val wpsInfo =
            WpsInfo(
                configMethods = setOf(WpsConfigMethod.LABEL),
                wpsState = WpsState.CONFIGURED,
                locked = false,
                deviceName = null,
                manufacturer = null,
                modelName = null,
                version = null,
            )

        val result =
            analyzer.analyzeBss(
                bssid = "00:11:22:33:44:55",
                ssid = "Network",
                fingerprint = fingerprint,
                wpsInfo = wpsInfo,
            )

        assertEquals(ThreatLevel.CRITICAL, result.threatLevel)
        assertTrue(result.criticalIssues.any { it.category == CriticalIssueCategory.WPS })
    }

    @Test
    fun `analyzeNetwork aggregates multiple BSS analyses`() {
        val analyses =
            listOf(
                createMockAnalysis("00:11:22:33:44:55", SecurityLevel.EXCELLENT, ThreatLevel.NONE),
                createMockAnalysis("AA:BB:CC:DD:EE:FF", SecurityLevel.GOOD, ThreatLevel.LOW),
                createMockAnalysis("11:22:33:44:55:66", SecurityLevel.FAIR, ThreatLevel.MEDIUM),
            )

        val result = analyzer.analyzeNetwork(analyses)

        assertEquals(3, result.totalBssCount)
        assertEquals(ThreatLevel.MEDIUM, result.overallThreatLevel)
        assertTrue(result.averageSecurityScore > 0.0)
    }

    @Test
    fun `analyzeNetwork calculates compliance levels correctly`() {
        // All modern security = FULL compliance
        val allModern =
            listOf(
                createMockAnalysis("00:11:22:33:44:55", SecurityLevel.EXCELLENT, ThreatLevel.NONE, usesModern = true),
                createMockAnalysis("AA:BB:CC:DD:EE:FF", SecurityLevel.EXCELLENT, ThreatLevel.NONE, usesModern = true),
            )
        assertEquals(ComplianceLevel.FULL, analyzer.analyzeNetwork(allModern).complianceLevel)

        // Mixed security = MODERATE/LOW compliance
        val mixed =
            listOf(
                createMockAnalysis("00:11:22:33:44:55", SecurityLevel.EXCELLENT, ThreatLevel.NONE, usesModern = true),
                createMockAnalysis("AA:BB:CC:DD:EE:FF", SecurityLevel.WEAK, ThreatLevel.HIGH, usesModern = false),
            )
        val mixedResult = analyzer.analyzeNetwork(mixed)
        assertTrue(mixedResult.complianceLevel in listOf(ComplianceLevel.MODERATE, ComplianceLevel.LOW))
    }

    @Test
    fun `ThreatLevel enum has correct ordering`() {
        assertTrue(ThreatLevel.NONE.ordinal < ThreatLevel.LOW.ordinal)
        assertTrue(ThreatLevel.LOW.ordinal < ThreatLevel.MEDIUM.ordinal)
        assertTrue(ThreatLevel.MEDIUM.ordinal < ThreatLevel.HIGH.ordinal)
        assertTrue(ThreatLevel.HIGH.ordinal < ThreatLevel.CRITICAL.ordinal)
    }

    @Test
    fun `ThreatLevel descriptions are meaningful`() {
        ThreatLevel.values().forEach { level ->
            assertTrue(level.description.isNotBlank())
        }
    }

    @Test
    fun `ComplianceLevel descriptions are meaningful`() {
        ComplianceLevel.values().forEach { level ->
            assertTrue(level.description.isNotBlank())
        }
    }

    @Test
    fun `CriticalIssue contains required fields`() {
        val issue =
            CriticalIssue(
                category = CriticalIssueCategory.SECURITY,
                severity = CriticalIssueSeverity.CRITICAL,
                description = "Test issue",
                recommendation = "Fix it",
            )

        assertEquals(CriticalIssueCategory.SECURITY, issue.category)
        assertEquals(CriticalIssueSeverity.CRITICAL, issue.severity)
        assertTrue(issue.description.isNotBlank())
        assertTrue(issue.recommendation.isNotBlank())
    }

    @Test
    fun `NetworkSecurityAnalysis calculates minimum security percentage`() {
        val analyses =
            listOf(
                createMockAnalysis("00:11:22:33:44:55", SecurityLevel.EXCELLENT, ThreatLevel.NONE),
                createMockAnalysis("AA:BB:CC:DD:EE:FF", SecurityLevel.GOOD, ThreatLevel.LOW),
                createMockAnalysis("11:22:33:44:55:66", SecurityLevel.WEAK, ThreatLevel.HIGH),
                createMockAnalysis("22:33:44:55:66:77", SecurityLevel.INSECURE, ThreatLevel.CRITICAL),
            )

        val result = analyzer.analyzeNetwork(analyses)

        // 2 out of 4 meet minimum (EXCELLENT, GOOD)
        assertEquals(50.0, result.minimumSecurityPercentage, 0.1)
    }

    @Test
    fun `NetworkSecurityAnalysis summary includes key information`() {
        val analyses =
            listOf(
                createMockAnalysis("00:11:22:33:44:55", SecurityLevel.GOOD, ThreatLevel.LOW),
            )

        val result = analyzer.analyzeNetwork(analyses)
        val summary = result.summary

        assertTrue(summary.contains("1")) // BSS count
        assertTrue(summary.isNotBlank())
    }

    @Test
    fun `BssSecurityAnalysis summary includes BSS ID`() {
        val fingerprint = SecurityFingerprint.wpa2Personal()

        val result =
            analyzer.analyzeBss(
                bssid = "00:11:22:33:44:55",
                ssid = "TestNetwork",
                fingerprint = fingerprint,
            )

        val summary = result.summary
        assertTrue(summary.contains("00:11:22:33:44:55"))
    }

    // Helper function to create mock analyses
    private fun createMockAnalysis(
        bssid: String,
        securityLevel: SecurityLevel,
        threatLevel: ThreatLevel,
        usesModern: Boolean = false,
        hasWpsRisk: Boolean = false,
    ): BssSecurityAnalysis {
        // Create fingerprints that match the expected security levels
        val fingerprint =
            when (securityLevel) {
                SecurityLevel.EXCELLENT -> SecurityFingerprint.wpa3Personal()
                SecurityLevel.GOOD -> SecurityFingerprint.wpa2Personal(pmfRequired = true) // PMF required for clean GOOD score
                SecurityLevel.FAIR -> SecurityFingerprint.wpa2Personal(pmfRequired = false) // No PMF = FAIR
                else ->
                    SecurityFingerprint(
                        authType = AuthType.WPA_PSK,
                        cipherSet = setOf(CipherSuite.TKIP),
                        pmfRequired = false,
                        transitionMode = null,
                    )
            }

        val wpsInfo =
            if (hasWpsRisk) {
                WpsInfo(
                    configMethods = setOf(WpsConfigMethod.LABEL),
                    wpsState = WpsState.CONFIGURED,
                    locked = false,
                    deviceName = null,
                    manufacturer = null,
                    modelName = null,
                    version = null,
                )
            } else {
                null
            }

        // Create the base analysis and override the threat level for testing purposes
        return analyzer
            .analyzeBss(
                bssid = bssid,
                ssid = "Network",
                fingerprint = fingerprint,
                wpsInfo = wpsInfo,
            ).copy(threatLevel = threatLevel)
    }
}
