package io.lamco.netkit.security.model

import io.lamco.netkit.model.topology.AuthType
import io.lamco.netkit.model.topology.CipherSuite
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Comprehensive tests for SecurityIssue sealed class hierarchy
 *
 * Validates:
 * - All issue types are properly defined
 * - Severity levels are appropriate
 * - Descriptions are clear and actionable
 * - Recommendations are present
 */
class SecurityIssueTest {
    // ============================================
    // LEGACY CIPHER TESTS
    // ============================================

    @Test
    fun `LegacyCipher with WEP-40 has CRITICAL severity`() {
        val issue = SecurityIssue.LegacyCipher(CipherSuite.WEP_40)
        assertEquals(SecurityIssue.Severity.CRITICAL, issue.severity)
        assertTrue(issue.shortDescription.contains("WEP"))
        assertTrue(issue.technicalDetails.isNotBlank())
        assertTrue(issue.recommendation.isNotBlank())
    }

    @Test
    fun `LegacyCipher with WEP-104 has CRITICAL severity`() {
        val issue = SecurityIssue.LegacyCipher(CipherSuite.WEP_104)
        assertEquals(SecurityIssue.Severity.CRITICAL, issue.severity)
        assertTrue(issue.technicalDetails.contains("cracked in minutes"))
    }

    @Test
    fun `LegacyCipher with TKIP has HIGH severity`() {
        val issue = SecurityIssue.LegacyCipher(CipherSuite.TKIP)
        assertEquals(SecurityIssue.Severity.HIGH, issue.severity)
        assertTrue(issue.technicalDetails.contains("TKIP"))
        assertTrue(issue.recommendation.contains("WPA2") || issue.recommendation.contains("WPA3"))
    }

    @Test
    fun `LegacyCipher recommendation mentions upgrade`() {
        val wepIssue = SecurityIssue.LegacyCipher(CipherSuite.WEP_40)
        assertTrue(wepIssue.recommendation.contains("upgrade", ignoreCase = true))

        val tkipIssue = SecurityIssue.LegacyCipher(CipherSuite.TKIP)
        assertTrue(tkipIssue.recommendation.contains("upgrade", ignoreCase = true))
    }

    @Test
    fun `WepInUse object has CRITICAL severity`() {
        val issue = SecurityIssue.WepInUse
        assertEquals(SecurityIssue.Severity.CRITICAL, issue.severity)
        assertTrue(issue.shortDescription.contains("WEP"))
        assertTrue(issue.technicalDetails.contains("broken"))
        assertTrue(issue.recommendation.contains("WPA"))
    }

    @Test
    fun `TkipInUse object has HIGH severity`() {
        val issue = SecurityIssue.TkipInUse
        assertEquals(SecurityIssue.Severity.HIGH, issue.severity)
        assertTrue(issue.shortDescription.contains("TKIP"))
        assertTrue(issue.technicalDetails.contains("deprecated"))
    }

    // ============================================
    // PMF TESTS
    // ============================================

    @Test
    fun `PmfDisabledOnProtectedNetwork with WPA3 has CRITICAL severity`() {
        val issue = SecurityIssue.PmfDisabledOnProtectedNetwork(AuthType.WPA3_SAE)
        assertEquals(SecurityIssue.Severity.CRITICAL, issue.severity)
        assertTrue(issue.technicalDetails.contains("MANDATORY"))
    }

    @Test
    fun `PmfDisabledOnProtectedNetwork with WPA2 has MEDIUM severity`() {
        val issue = SecurityIssue.PmfDisabledOnProtectedNetwork(AuthType.WPA2_PSK)
        assertEquals(SecurityIssue.Severity.MEDIUM, issue.severity)
        assertTrue(issue.recommendation.contains("PMF") || issue.recommendation.contains("802.11w"))
    }

    @Test
    fun `PmfDisabledOnProtectedNetwork with WPA3-Enterprise has CRITICAL severity`() {
        val issue = SecurityIssue.PmfDisabledOnProtectedNetwork(AuthType.WPA3_ENTERPRISE)
        assertEquals(SecurityIssue.Severity.CRITICAL, issue.severity)
    }

    @Test
    fun `WeakGroupMgmtCipher has MEDIUM severity`() {
        val issue = SecurityIssue.WeakGroupMgmtCipher
        assertEquals(SecurityIssue.Severity.MEDIUM, issue.severity)
        assertTrue(issue.technicalDetails.contains("BIP"))
        assertTrue(issue.recommendation.contains("BIP"))
    }

    // ============================================
    // OPEN NETWORK TESTS
    // ============================================

    @Test
    fun `OpenNetworkWithoutOwe has MEDIUM severity`() {
        val issue = SecurityIssue.OpenNetworkWithoutOwe
        assertEquals(SecurityIssue.Severity.MEDIUM, issue.severity)
        assertTrue(issue.technicalDetails.contains("plaintext") || issue.technicalDetails.contains("unencrypted"))
        assertTrue(issue.recommendation.contains("OWE") || issue.recommendation.contains("Enhanced Open"))
    }

    @Test
    fun `OweTransitionWithOpenSideVisible has LOW severity`() {
        val issue = SecurityIssue.OweTransitionWithOpenSideVisible("GuestNetwork")
        assertEquals(SecurityIssue.Severity.LOW, issue.severity)
        assertTrue(issue.shortDescription.contains("OWE"))
        assertTrue(issue.technicalDetails.contains("GuestNetwork"))
    }

    // ============================================
    // SUITE-B TESTS
    // ============================================

    @Test
    fun `SuiteBMissingForHighSecurityClaim has HIGH severity`() {
        val issue = SecurityIssue.SuiteBMissingForHighSecurityClaim
        assertEquals(SecurityIssue.Severity.HIGH, issue.severity)
        assertTrue(issue.technicalDetails.contains("192-bit") || issue.technicalDetails.contains("GCMP-256"))
        assertTrue(issue.recommendation.contains("WPA3-Enterprise"))
    }

    // ============================================
    // TRANSITIONAL MODE TESTS
    // ============================================

    @Test
    fun `TransitionalMode WPA2 to WPA3 has MEDIUM severity`() {
        val issue = SecurityIssue.TransitionalMode(AuthType.WPA2_PSK, AuthType.WPA3_SAE)
        assertEquals(SecurityIssue.Severity.MEDIUM, issue.severity)
        assertTrue(issue.shortDescription.contains("WPA2") && issue.shortDescription.contains("WPA3"))
        assertTrue(issue.recommendation.contains("WPA3-only"))
    }

    @Test
    fun `TransitionalMode OPEN to OWE has LOW severity`() {
        val issue = SecurityIssue.TransitionalMode(AuthType.OPEN, AuthType.OWE)
        assertEquals(SecurityIssue.Severity.LOW, issue.severity)
    }

    @Test
    fun `TransitionalMode WPA to WPA2 has HIGH severity`() {
        val issue = SecurityIssue.TransitionalMode(AuthType.WPA_PSK, AuthType.WPA2_PSK)
        assertEquals(SecurityIssue.Severity.HIGH, issue.severity)
    }

    // ============================================
    // WPS TESTS
    // ============================================

    @Test
    fun `WpsPinEnabled has HIGH severity`() {
        val issue = SecurityIssue.WpsPinEnabled
        assertEquals(SecurityIssue.Severity.HIGH, issue.severity)
        assertTrue(issue.technicalDetails.contains("PIN") || issue.technicalDetails.contains("brute force"))
        assertTrue(issue.recommendation.contains("Disable"))
    }

    @Test
    fun `WpsUnknownOrRiskyMode has MEDIUM severity`() {
        val issue = SecurityIssue.WpsUnknownOrRiskyMode
        assertEquals(SecurityIssue.Severity.MEDIUM, issue.severity)
        assertTrue(issue.recommendation.contains("WPS"))
    }

    // ============================================
    // ROAMING TESTS
    // ============================================

    @Test
    fun `MissingRoamingOptimizations has LOW severity`() {
        val issue = SecurityIssue.MissingRoamingOptimizations
        assertEquals(SecurityIssue.Severity.LOW, issue.severity)
        assertTrue(
            issue.technicalDetails.contains("802.11k") ||
                issue.technicalDetails.contains("802.11v") ||
                issue.technicalDetails.contains("802.11r"),
        )
        assertTrue(issue.recommendation.contains("Enable"))
    }

    // ============================================
    // CONFIGURATION TESTS
    // ============================================

    @Test
    fun `InconsistentSecurityAcrossAps has HIGH severity`() {
        val issue = SecurityIssue.InconsistentSecurityAcrossAps("CorporateWiFi", 3)
        assertEquals(SecurityIssue.Severity.HIGH, issue.severity)
        assertTrue(issue.shortDescription.contains("Inconsistent"))
        assertTrue(issue.technicalDetails.contains("CorporateWiFi"))
        assertTrue(issue.technicalDetails.contains("3"))
    }

    @Test
    fun `DeprecatedAuthType with WEP has CRITICAL severity`() {
        val issue = SecurityIssue.DeprecatedAuthType(AuthType.WEP)
        assertEquals(SecurityIssue.Severity.CRITICAL, issue.severity)
        assertTrue(issue.technicalDetails.contains("broken") || issue.technicalDetails.contains("deprecated"))
    }

    @Test
    fun `DeprecatedAuthType with WPA-PSK has HIGH severity`() {
        val issue = SecurityIssue.DeprecatedAuthType(AuthType.WPA_PSK)
        assertEquals(SecurityIssue.Severity.HIGH, issue.severity)
        assertTrue(issue.recommendation.contains("WPA2") || issue.recommendation.contains("WPA3"))
    }

    // ============================================
    // SEVERITY ENUM TESTS
    // ============================================

    @Test
    fun `Severity enum has all expected values`() {
        val severities = SecurityIssue.Severity.values()
        assertTrue(severities.contains(SecurityIssue.Severity.INFO))
        assertTrue(severities.contains(SecurityIssue.Severity.LOW))
        assertTrue(severities.contains(SecurityIssue.Severity.MEDIUM))
        assertTrue(severities.contains(SecurityIssue.Severity.HIGH))
        assertTrue(severities.contains(SecurityIssue.Severity.CRITICAL))
    }

    @Test
    fun `Severity enum ordering is correct`() {
        assertTrue(SecurityIssue.Severity.INFO.ordinal < SecurityIssue.Severity.LOW.ordinal)
        assertTrue(SecurityIssue.Severity.LOW.ordinal < SecurityIssue.Severity.MEDIUM.ordinal)
        assertTrue(SecurityIssue.Severity.MEDIUM.ordinal < SecurityIssue.Severity.HIGH.ordinal)
        assertTrue(SecurityIssue.Severity.HIGH.ordinal < SecurityIssue.Severity.CRITICAL.ordinal)
    }

    // ============================================
    // COMPREHENSIVE VALIDATION
    // ============================================

    @Test
    fun `All issue types have non-blank descriptions`() {
        val issues =
            listOf(
                SecurityIssue.WepInUse,
                SecurityIssue.TkipInUse,
                SecurityIssue.LegacyCipher(CipherSuite.TKIP),
                SecurityIssue.PmfDisabledOnProtectedNetwork(AuthType.WPA2_PSK),
                SecurityIssue.WeakGroupMgmtCipher,
                SecurityIssue.OpenNetworkWithoutOwe,
                SecurityIssue.OweTransitionWithOpenSideVisible("Test"),
                SecurityIssue.SuiteBMissingForHighSecurityClaim,
                SecurityIssue.TransitionalMode(AuthType.WPA2_PSK, AuthType.WPA3_SAE),
                SecurityIssue.WpsPinEnabled,
                SecurityIssue.WpsUnknownOrRiskyMode,
                SecurityIssue.MissingRoamingOptimizations,
                SecurityIssue.InconsistentSecurityAcrossAps("Test", 2),
                SecurityIssue.DeprecatedAuthType(AuthType.WPA_PSK),
            )

        issues.forEach { issue ->
            assertTrue(issue.shortDescription.isNotBlank(), "Short description blank for ${issue::class.simpleName}")
            assertTrue(issue.technicalDetails.isNotBlank(), "Technical details blank for ${issue::class.simpleName}")
            assertTrue(issue.recommendation.isNotBlank(), "Recommendation blank for ${issue::class.simpleName}")
        }
    }

    @Test
    fun `All issue types have appropriate severity`() {
        // All severities should be within enum range
        val issues =
            listOf(
                SecurityIssue.WepInUse,
                SecurityIssue.TkipInUse,
                SecurityIssue.LegacyCipher(CipherSuite.WEP_40),
                SecurityIssue.PmfDisabledOnProtectedNetwork(AuthType.WPA3_SAE),
                SecurityIssue.TransitionalMode(AuthType.WPA2_PSK, AuthType.WPA3_SAE),
            )

        issues.forEach { issue ->
            assertNotNull(issue.severity, "Severity null for ${issue::class.simpleName}")
            assertTrue(issue.severity in SecurityIssue.Severity.values())
        }
    }
}
