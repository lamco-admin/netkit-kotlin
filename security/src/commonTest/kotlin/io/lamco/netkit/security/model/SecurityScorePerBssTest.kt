package io.lamco.netkit.security.model

import io.lamco.netkit.model.topology.AuthType
import io.lamco.netkit.model.topology.CipherSuite
import io.lamco.netkit.model.topology.SecurityFingerprint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Comprehensive tests for SecurityScorePerBss
 *
 * Validates:
 * - Security score calculation algorithms
 * - Cipher strength scoring
 * - Management protection scoring
 * - Overall score computation
 * - Issue detection from fingerprints
 * - Security level categorization
 */
class SecurityScorePerBssTest {

    // ============================================
    // CONSTRUCTION AND VALIDATION
    // ============================================

    @Test
    fun `create SecurityScorePerBss with valid parameters`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "TestNetwork",
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP),
            cipherStrengthScore = 0.85,
            managementProtectionScore = 0.7,
            overallSecurityScore = 0.8,
            issues = emptyList()
        )

        assertEquals("00:11:22:33:44:55", score.bssid)
        assertEquals("TestNetwork", score.ssid)
        assertEquals(AuthType.WPA2_PSK, score.authType)
        assertEquals(0.85, score.cipherStrengthScore, 0.001)
        assertEquals(0.7, score.managementProtectionScore, 0.001)
        assertEquals(0.8, score.overallSecurityScore, 0.001)
        assertTrue(score.issues.isEmpty())
    }

    @Test
    fun `reject blank BSSID`() {
        assertThrows<IllegalArgumentException> {
            SecurityScorePerBss(
                bssid = "",
                ssid = "Test",
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                cipherStrengthScore = 0.8,
                managementProtectionScore = 0.7,
                overallSecurityScore = 0.75,
                issues = emptyList()
            )
        }
    }

    @Test
    fun `reject cipher score out of range`() {
        assertThrows<IllegalArgumentException> {
            SecurityScorePerBss(
                bssid = "00:11:22:33:44:55",
                ssid = "Test",
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                cipherStrengthScore = 1.5,  // Invalid
                managementProtectionScore = 0.7,
                overallSecurityScore = 0.8,
                issues = emptyList()
            )
        }
    }

    @Test
    fun `reject management protection score out of range`() {
        assertThrows<IllegalArgumentException> {
            SecurityScorePerBss(
                bssid = "00:11:22:33:44:55",
                ssid = "Test",
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                cipherStrengthScore = 0.8,
                managementProtectionScore = -0.1,  // Invalid
                overallSecurityScore = 0.8,
                issues = emptyList()
            )
        }
    }

    @Test
    fun `reject overall score out of range`() {
        assertThrows<IllegalArgumentException> {
            SecurityScorePerBss(
                bssid = "00:11:22:33:44:55",
                ssid = "Test",
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                cipherStrengthScore = 0.8,
                managementProtectionScore = 0.7,
                overallSecurityScore = 1.1,  // Invalid
                issues = emptyList()
            )
        }
    }

    @Test
    fun `accept null SSID for hidden networks`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = null,
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP),
            cipherStrengthScore = 0.8,
            managementProtectionScore = 0.7,
            overallSecurityScore = 0.75,
            issues = emptyList()
        )

        assertNull(score.ssid)
    }

    // ============================================
    // CIPHER SCORE CALCULATION
    // ============================================

    @Test
    fun `calculateCipherScore with strong ciphers returns high score`() {
        val score = SecurityScorePerBss.calculateCipherScore(
            setOf(CipherSuite.CCMP, CipherSuite.GCMP)
        )
        assertTrue(score >= 0.85, "Expected score >= 0.85, got $score")
    }

    @Test
    fun `calculateCipherScore with GCMP-256 returns maximum score`() {
        val score = SecurityScorePerBss.calculateCipherScore(
            setOf(CipherSuite.GCMP_256)
        )
        assertTrue(score >= 0.95, "Expected score >= 0.95, got $score")
    }

    @Test
    fun `calculateCipherScore with TKIP returns low score`() {
        val score = SecurityScorePerBss.calculateCipherScore(
            setOf(CipherSuite.TKIP)
        )
        assertTrue(score <= 0.35, "Expected score <= 0.35, got $score")
    }

    @Test
    fun `calculateCipherScore with WEP returns minimal score`() {
        val score = SecurityScorePerBss.calculateCipherScore(
            setOf(CipherSuite.WEP_40)
        )
        assertTrue(score <= 0.1, "Expected score <= 0.1, got $score")
    }

    @Test
    fun `calculateCipherScore with mixed ciphers penalizes weak ones`() {
        val mixedScore = SecurityScorePerBss.calculateCipherScore(
            setOf(CipherSuite.CCMP, CipherSuite.TKIP)
        )
        val pureScore = SecurityScorePerBss.calculateCipherScore(
            setOf(CipherSuite.CCMP)
        )

        assertTrue(mixedScore < pureScore, "Mixed cipher score should be lower")
    }

    @Test
    fun `calculateCipherScore with empty set returns zero`() {
        val score = SecurityScorePerBss.calculateCipherScore(emptySet())
        assertEquals(0.0, score, 0.001)
    }

    // ============================================
    // MANAGEMENT PROTECTION SCORE CALCULATION
    // ============================================

    @Test
    fun `calculateManagementProtectionScore with PMF required and strong cipher returns 1 point 0`() {
        val score = SecurityScorePerBss.calculateManagementProtectionScore(
            pmfRequired = true,
            pmfCapable = true,
            managementCipher = CipherSuite.BIP_GMAC_256
        )
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `calculateManagementProtectionScore with PMF required and CMAC-128 returns 0 point 9`() {
        val score = SecurityScorePerBss.calculateManagementProtectionScore(
            pmfRequired = true,
            pmfCapable = true,
            managementCipher = CipherSuite.BIP_CMAC_128
        )
        assertEquals(0.9, score, 0.001)
    }

    @Test
    fun `calculateManagementProtectionScore with PMF capable returns mid-range score`() {
        val score = SecurityScorePerBss.calculateManagementProtectionScore(
            pmfRequired = false,
            pmfCapable = true,
            managementCipher = CipherSuite.BIP_CMAC_128
        )
        assertTrue(score in 0.4..0.7, "Expected score 0.4-0.7, got $score")
    }

    @Test
    fun `calculateManagementProtectionScore with no PMF returns zero`() {
        val score = SecurityScorePerBss.calculateManagementProtectionScore(
            pmfRequired = false,
            pmfCapable = false,
            managementCipher = null
        )
        assertEquals(0.0, score, 0.001)
    }

    // ============================================
    // OVERALL SCORE CALCULATION
    // ============================================

    @Test
    fun `calculateOverallScore combines auth cipher and PMF with correct weighting`() {
        val score = SecurityScorePerBss.calculateOverallScore(
            authType = AuthType.WPA3_SAE,      // 95% = 0.95
            cipherScore = 0.9,
            mgmtProtectionScore = 1.0
        )

        // Expected: (0.95 * 0.40) + (0.9 * 0.35) + (1.0 * 0.25) = 0.945
        assertTrue(score >= 0.94, "Expected score >= 0.94, got $score")
    }

    @Test
    fun `calculateOverallScore with weak auth type reduces overall score`() {
        // WPA_PSK has 40% security level, resulting in lower overall score
        // Calculation: (0.40 * 0.4) + (0.85 * 0.35) + (0.85 * 0.25) = 0.16 + 0.2975 + 0.2125 = 0.67
        val score = SecurityScorePerBss.calculateOverallScore(
            authType = AuthType.WPA_PSK,  // 40% = 0.40
            cipherScore = 0.85,
            mgmtProtectionScore = 0.85
        )

        assertTrue(score < 0.70, "Expected score < 0.70 due to weak auth, got $score")
    }

    @Test
    fun `calculateOverallScore bounds result to 0 point 0 to 1 point 0`() {
        val maxScore = SecurityScorePerBss.calculateOverallScore(
            authType = AuthType.WPA3_ENTERPRISE_192,
            cipherScore = 1.0,
            mgmtProtectionScore = 1.0
        )
        assertTrue(maxScore <= 1.0)

        val minScore = SecurityScorePerBss.calculateOverallScore(
            authType = AuthType.OPEN,
            cipherScore = 0.0,
            mgmtProtectionScore = 0.0
        )
        assertTrue(minScore >= 0.0)
    }

    // ============================================
    // fromFingerprint FACTORY METHOD
    // ============================================

    @Test
    fun `fromFingerprint detects WEP issues`() {
        val fingerprint = SecurityFingerprint(
            authType = AuthType.WEP,
            cipherSet = setOf(CipherSuite.WEP_40),
            pmfRequired = false,
            transitionMode = null
        )

        val score = SecurityScorePerBss.fromFingerprint(
            bssid = "00:11:22:33:44:55",
            ssid = "OldNetwork",
            fingerprint = fingerprint
        )

        assertTrue(score.issues.any { it is SecurityIssue.WepInUse })
        assertTrue(score.issues.any { it is SecurityIssue.LegacyCipher })
        assertTrue(score.issues.any { it is SecurityIssue.DeprecatedAuthType })
    }

    @Test
    fun `fromFingerprint detects TKIP issues`() {
        val fingerprint = SecurityFingerprint(
            authType = AuthType.WPA_PSK,
            cipherSet = setOf(CipherSuite.TKIP),
            pmfRequired = false,
            transitionMode = null
        )

        val score = SecurityScorePerBss.fromFingerprint(
            bssid = "00:11:22:33:44:55",
            ssid = "LegacyNetwork",
            fingerprint = fingerprint
        )

        assertTrue(score.issues.any { it is SecurityIssue.TkipInUse })
        assertTrue(score.issues.any { it is SecurityIssue.LegacyCipher })
    }

    @Test
    fun `fromFingerprint detects missing PMF on WPA2`() {
        val fingerprint = SecurityFingerprint(
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP),
            pmfRequired = false,
            transitionMode = null
        )

        val score = SecurityScorePerBss.fromFingerprint(
            bssid = "00:11:22:33:44:55",
            ssid = "TestNetwork",
            fingerprint = fingerprint
        )

        assertTrue(score.issues.any { it is SecurityIssue.PmfDisabledOnProtectedNetwork })
    }

    @Test
    fun `fromFingerprint detects open network without OWE`() {
        val fingerprint = SecurityFingerprint(
            authType = AuthType.OPEN,
            cipherSet = setOf(CipherSuite.NONE),
            pmfRequired = false,
            transitionMode = null
        )

        val score = SecurityScorePerBss.fromFingerprint(
            bssid = "00:11:22:33:44:55",
            ssid = "FreeWiFi",
            fingerprint = fingerprint
        )

        assertTrue(score.issues.any { it is SecurityIssue.OpenNetworkWithoutOwe })
    }

    @Test
    fun `fromFingerprint detects WPA2-WPA3 transition mode`() {
        val fingerprint = SecurityFingerprint(
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP),
            pmfRequired = false,
            transitionMode = io.lamco.netkit.model.topology.TransitionMode.WPA2_WPA3
        )

        val score = SecurityScorePerBss.fromFingerprint(
            bssid = "00:11:22:33:44:55",
            ssid = "TransitionNet",
            fingerprint = fingerprint
        )

        assertTrue(score.issues.any { it is SecurityIssue.TransitionalMode })
    }

    @Test
    fun `fromFingerprint with WPA3 and no issues returns high score`() {
        val fingerprint = SecurityFingerprint(
            authType = AuthType.WPA3_SAE,
            cipherSet = setOf(CipherSuite.GCMP),
            pmfRequired = true,
            transitionMode = null
        )

        val score = SecurityScorePerBss.fromFingerprint(
            bssid = "00:11:22:33:44:55",
            ssid = "SecureNetwork",
            fingerprint = fingerprint,
            pmfCapable = true,
            managementCipher = CipherSuite.BIP_GMAC_128
        )

        assertTrue(score.overallSecurityScore >= 0.90)
        assertEquals(SecurityLevel.EXCELLENT, score.securityLevel)
    }

    // ============================================
    // SECURITY LEVEL CATEGORIZATION
    // ============================================

    @Test
    fun `securityLevel EXCELLENT for score above 0 point 90`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA3_SAE,
            cipherSet = setOf(CipherSuite.GCMP),
            cipherStrengthScore = 0.95,
            managementProtectionScore = 0.95,
            overallSecurityScore = 0.95,
            issues = emptyList()
        )

        assertEquals(SecurityLevel.EXCELLENT, score.securityLevel)
    }

    @Test
    fun `securityLevel GOOD for score 0 point 70 to 0 point 89`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP),
            cipherStrengthScore = 0.8,
            managementProtectionScore = 0.7,
            overallSecurityScore = 0.75,
            issues = emptyList()
        )

        assertEquals(SecurityLevel.GOOD, score.securityLevel)
    }

    @Test
    fun `securityLevel FAIR for score 0 point 50 to 0 point 69`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP),
            cipherStrengthScore = 0.6,
            managementProtectionScore = 0.5,
            overallSecurityScore = 0.55,
            issues = emptyList()
        )

        assertEquals(SecurityLevel.FAIR, score.securityLevel)
    }

    @Test
    fun `securityLevel WEAK for score 0 point 30 to 0 point 49`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA_PSK,
            cipherSet = setOf(CipherSuite.TKIP),
            cipherStrengthScore = 0.4,
            managementProtectionScore = 0.3,
            overallSecurityScore = 0.35,
            issues = emptyList()
        )

        assertEquals(SecurityLevel.WEAK, score.securityLevel)
    }

    @Test
    fun `securityLevel INSECURE for score below 0 point 30`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WEP,
            cipherSet = setOf(CipherSuite.WEP_104),
            cipherStrengthScore = 0.1,
            managementProtectionScore = 0.0,
            overallSecurityScore = 0.15,
            issues = emptyList()
        )

        assertEquals(SecurityLevel.INSECURE, score.securityLevel)
    }

    // ============================================
    // CONVENIENCE PROPERTIES
    // ============================================

    @Test
    fun `meetsMinimumSecurity true for score above 0 point 70`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP),
            cipherStrengthScore = 0.8,
            managementProtectionScore = 0.7,
            overallSecurityScore = 0.75,
            issues = emptyList()
        )

        assertTrue(score.meetsMinimumSecurity)
    }

    @Test
    fun `meetsMinimumSecurity false for score below 0 point 70`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA_PSK,
            cipherSet = setOf(CipherSuite.TKIP),
            cipherStrengthScore = 0.5,
            managementProtectionScore = 0.4,
            overallSecurityScore = 0.45,
            issues = emptyList()
        )

        assertFalse(score.meetsMinimumSecurity)
    }

    @Test
    fun `usesModernSecurity true for WPA3`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA3_SAE,
            cipherSet = setOf(CipherSuite.GCMP),
            cipherStrengthScore = 0.9,
            managementProtectionScore = 0.9,
            overallSecurityScore = 0.92,
            issues = emptyList()
        )

        assertTrue(score.usesModernSecurity)
    }

    @Test
    fun `usesModernSecurity true for strong WPA2`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP),
            cipherStrengthScore = 0.9,
            managementProtectionScore = 0.9,
            overallSecurityScore = 0.85,
            issues = emptyList()
        )

        assertTrue(score.usesModernSecurity)
    }

    @Test
    fun `issue counts match issue list`() {
        val issues = listOf(
            SecurityIssue.TkipInUse,  // HIGH
            SecurityIssue.WepInUse,   // CRITICAL
            SecurityIssue.PmfDisabledOnProtectedNetwork(AuthType.WPA2_PSK),  // MEDIUM
            SecurityIssue.MissingRoamingOptimizations  // LOW
        )

        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WPA_PSK,
            cipherSet = setOf(CipherSuite.TKIP),
            cipherStrengthScore = 0.3,
            managementProtectionScore = 0.2,
            overallSecurityScore = 0.25,
            issues = issues
        )

        assertEquals(1, score.criticalIssueCount)
        assertEquals(1, score.highIssueCount)
        assertEquals(1, score.mediumIssueCount)
        assertEquals(1, score.lowIssueCount)
        assertEquals(4, score.totalIssueCount)
        assertTrue(score.hasCriticalIssues)
        assertTrue(score.hasHighSeverityIssues)
    }

    @Test
    fun `primaryConcern returns highest severity issue`() {
        val issues = listOf(
            SecurityIssue.MissingRoamingOptimizations,  // LOW
            SecurityIssue.WepInUse,  // CRITICAL
            SecurityIssue.TkipInUse  // HIGH
        )

        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "Test",
            authType = AuthType.WEP,
            cipherSet = setOf(CipherSuite.WEP_104),
            cipherStrengthScore = 0.1,
            managementProtectionScore = 0.0,
            overallSecurityScore = 0.05,
            issues = issues
        )

        assertEquals(SecurityIssue.WepInUse, score.primaryConcern)
    }

    @Test
    fun `securitySummary includes network name and level`() {
        val score = SecurityScorePerBss(
            bssid = "00:11:22:33:44:55",
            ssid = "TestNetwork",
            authType = AuthType.WPA2_PSK,
            cipherSet = setOf(CipherSuite.CCMP),
            cipherStrengthScore = 0.8,
            managementProtectionScore = 0.7,
            overallSecurityScore = 0.75,
            issues = emptyList()
        )

        val summary = score.securitySummary
        assertTrue(summary.contains("GOOD"))
        assertTrue(summary.contains("WPA2"))
    }

    @Test
    fun `detailedReport includes all key information`() {
        val score = SecurityScorePerBss(
            bssid = "AA:BB:CC:DD:EE:FF",
            ssid = "CorporateNet",
            authType = AuthType.WPA3_SAE,
            cipherSet = setOf(CipherSuite.GCMP),
            cipherStrengthScore = 0.95,
            managementProtectionScore = 1.0,
            overallSecurityScore = 0.97,
            issues = emptyList()
        )

        val report = score.detailedReport
        assertTrue(report.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(report.contains("CorporateNet"))
        assertTrue(report.contains("EXCELLENT"))
        assertTrue(report.contains("WPA3"))
    }
}
