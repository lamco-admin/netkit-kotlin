package io.lamco.netkit.security.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Comprehensive tests for ConfigurationQuality models
 *
 * Validates:
 * - ConfigurationReport data model
 * - ConfigurationQualityLevel categorization
 * - ConfigurationIssue sealed class hierarchy
 * - Quality score calculations
 * - Issue severity levels
 */
class ConfigurationQualityTest {

    // ============================================
    // ConfigurationReport CONSTRUCTION
    // ============================================

    @Test
    fun `create ConfigurationReport with valid parameters`() {
        val report = ConfigurationReport(
            ssid = "CorporateWiFi",
            apCount = 5,
            securityConsistencyScore = 0.95,
            roamingCapabilityScore = 0.85,
            channelPlanScore = 0.90,
            overallQualityScore = 0.90,
            issues = emptyList()
        )

        assertEquals("CorporateWiFi", report.ssid)
        assertEquals(5, report.apCount)
        assertEquals(0.95, report.securityConsistencyScore, 0.001)
        assertEquals(0.85, report.roamingCapabilityScore, 0.001)
        assertEquals(0.90, report.channelPlanScore, 0.001)
        assertEquals(0.90, report.overallQualityScore, 0.001)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `reject blank SSID`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationReport(
                ssid = "",
                apCount = 1,
                securityConsistencyScore = 0.8,
                roamingCapabilityScore = 0.7,
                channelPlanScore = 0.75,
                overallQualityScore = 0.75,
                issues = emptyList()
            )
        }
    }

    @Test
    fun `reject zero or negative AP count`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationReport(
                ssid = "Test",
                apCount = 0,
                securityConsistencyScore = 0.8,
                roamingCapabilityScore = 0.7,
                channelPlanScore = 0.75,
                overallQualityScore = 0.75,
                issues = emptyList()
            )
        }
    }

    @Test
    fun `reject security consistency score out of range`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationReport(
                ssid = "Test",
                apCount = 1,
                securityConsistencyScore = 1.5,  // Invalid
                roamingCapabilityScore = 0.7,
                channelPlanScore = 0.75,
                overallQualityScore = 0.75,
                issues = emptyList()
            )
        }
    }

    @Test
    fun `reject roaming capability score out of range`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationReport(
                ssid = "Test",
                apCount = 1,
                securityConsistencyScore = 0.8,
                roamingCapabilityScore = -0.1,  // Invalid
                channelPlanScore = 0.75,
                overallQualityScore = 0.75,
                issues = emptyList()
            )
        }
    }

    @Test
    fun `reject channel plan score out of range`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationReport(
                ssid = "Test",
                apCount = 1,
                securityConsistencyScore = 0.8,
                roamingCapabilityScore = 0.7,
                channelPlanScore = 2.0,  // Invalid
                overallQualityScore = 0.75,
                issues = emptyList()
            )
        }
    }

    @Test
    fun `reject overall quality score out of range`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationReport(
                ssid = "Test",
                apCount = 1,
                securityConsistencyScore = 0.8,
                roamingCapabilityScore = 0.7,
                channelPlanScore = 0.75,
                overallQualityScore = -0.5,  // Invalid
                issues = emptyList()
            )
        }
    }

    // ============================================
    // QUALITY LEVEL CATEGORIZATION
    // ============================================

    @Test
    fun `qualityLevel EXCELLENT for score above 0 point 90`() {
        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 3,
            securityConsistencyScore = 0.95,
            roamingCapabilityScore = 0.90,
            channelPlanScore = 0.92,
            overallQualityScore = 0.92,
            issues = emptyList()
        )

        assertEquals(ConfigurationQualityLevel.EXCELLENT, report.qualityLevel)
    }

    @Test
    fun `qualityLevel GOOD for score 0 point 75 to 0 point 89`() {
        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 2,
            securityConsistencyScore = 0.80,
            roamingCapabilityScore = 0.75,
            channelPlanScore = 0.78,
            overallQualityScore = 0.78,
            issues = emptyList()
        )

        assertEquals(ConfigurationQualityLevel.GOOD, report.qualityLevel)
    }

    @Test
    fun `qualityLevel FAIR for score 0 point 60 to 0 point 74`() {
        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 2,
            securityConsistencyScore = 0.65,
            roamingCapabilityScore = 0.60,
            channelPlanScore = 0.62,
            overallQualityScore = 0.62,
            issues = emptyList()
        )

        assertEquals(ConfigurationQualityLevel.FAIR, report.qualityLevel)
    }

    @Test
    fun `qualityLevel POOR for score 0 point 40 to 0 point 59`() {
        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 3,
            securityConsistencyScore = 0.50,
            roamingCapabilityScore = 0.45,
            channelPlanScore = 0.48,
            overallQualityScore = 0.48,
            issues = emptyList()
        )

        assertEquals(ConfigurationQualityLevel.POOR, report.qualityLevel)
    }

    @Test
    fun `qualityLevel CRITICAL for score below 0 point 40`() {
        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 2,
            securityConsistencyScore = 0.30,
            roamingCapabilityScore = 0.25,
            channelPlanScore = 0.28,
            overallQualityScore = 0.28,
            issues = emptyList()
        )

        assertEquals(ConfigurationQualityLevel.CRITICAL, report.qualityLevel)
    }

    // ============================================
    // NETWORK TYPE DETECTION
    // ============================================

    @Test
    fun `isMultiApNetwork true for AP count greater than 1`() {
        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 5,
            securityConsistencyScore = 0.8,
            roamingCapabilityScore = 0.7,
            channelPlanScore = 0.75,
            overallQualityScore = 0.75,
            issues = emptyList()
        )

        assertTrue(report.isMultiApNetwork)
        assertFalse(report.isSingleAp)
    }

    @Test
    fun `isSingleAp true for AP count of 1`() {
        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 1,
            securityConsistencyScore = 0.8,
            roamingCapabilityScore = 1.0,
            channelPlanScore = 0.75,
            overallQualityScore = 0.85,
            issues = emptyList()
        )

        assertTrue(report.isSingleAp)
        assertFalse(report.isMultiApNetwork)
    }

    // ============================================
    // ISSUE COUNTING
    // ============================================

    @Test
    fun `issue counts match issue list`() {
        val issues = listOf(
            ConfigurationIssue.InconsistentSecurity("Test", 3),  // HIGH
            ConfigurationIssue.MissingRoamingFeatures("Test", 5, 0.0),  // MEDIUM (0.0 coverage = no roaming)
            ConfigurationIssue.PoorChannelPlanning(0.25, "Overlapping channels"),  // HIGH
            ConfigurationIssue.InfoNote("Test message")  // INFO
        )

        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 5,
            securityConsistencyScore = 0.5,
            roamingCapabilityScore = 0.4,
            channelPlanScore = 0.3,
            overallQualityScore = 0.4,
            issues = issues
        )

        assertEquals(0, report.criticalIssueCount)
        assertEquals(2, report.highIssueCount)
        assertEquals(1, report.mediumIssueCount)
        assertEquals(0, report.lowIssueCount)
        assertEquals(4, report.totalIssueCount)
        assertFalse(report.hasCriticalIssues)
    }

    @Test
    fun `primaryConcern returns highest severity issue`() {
        val issues = listOf(
            ConfigurationIssue.InfoNote("Info"),  // INFO
            ConfigurationIssue.InconsistentSecurity("Test", 2),  // HIGH
            ConfigurationIssue.MissingRoamingFeatures("Test", 3, 0.5)  // LOW
        )

        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 3,
            securityConsistencyScore = 0.6,
            roamingCapabilityScore = 0.5,
            channelPlanScore = 0.7,
            overallQualityScore = 0.6,
            issues = issues
        )

        assertTrue(report.primaryConcern is ConfigurationIssue.InconsistentSecurity)
    }

    // ============================================
    // OVERALL QUALITY CALCULATION
    // ============================================

    @Test
    fun `calculateOverallQuality combines scores with correct weighting`() {
        val score = ConfigurationReport.calculateOverallQuality(
            securityScore = 0.8,   // 40%
            roamingScore = 0.6,    // 30%
            channelScore = 0.7     // 30%
        )

        // Expected: (0.8 * 0.40) + (0.6 * 0.30) + (0.7 * 0.30) = 0.71
        assertEquals(0.71, score, 0.01)
    }

    @Test
    fun `calculateOverallQuality bounds result to 0 point 0 to 1 point 0`() {
        val maxScore = ConfigurationReport.calculateOverallQuality(
            securityScore = 1.0,
            roamingScore = 1.0,
            channelScore = 1.0
        )
        assertTrue(maxScore <= 1.0)
        assertEquals(1.0, maxScore, 0.001)

        val minScore = ConfigurationReport.calculateOverallQuality(
            securityScore = 0.0,
            roamingScore = 0.0,
            channelScore = 0.0
        )
        assertTrue(minScore >= 0.0)
        assertEquals(0.0, minScore, 0.001)
    }

    // ============================================
    // forSingleAp FACTORY METHOD
    // ============================================

    @Test
    fun `forSingleAp creates report with roaming score 1 point 0`() {
        val report = ConfigurationReport.forSingleAp(
            ssid = "HomeNetwork",
            securityScore = 0.85,
            channelScore = 0.75
        )

        assertEquals("HomeNetwork", report.ssid)
        assertEquals(1, report.apCount)
        assertEquals(0.85, report.securityConsistencyScore, 0.001)
        assertEquals(1.0, report.roamingCapabilityScore, 0.001)  // N/A for single AP
        assertEquals(0.75, report.channelPlanScore, 0.001)
        assertTrue(report.isSingleAp)
        assertTrue(report.issues.isEmpty())
    }

    // ============================================
    // SUMMARY AND REPORT GENERATION
    // ============================================

    @Test
    fun `configurationSummary includes key information`() {
        val report = ConfigurationReport(
            ssid = "OfficeWiFi",
            apCount = 10,
            securityConsistencyScore = 0.92,
            roamingCapabilityScore = 0.88,
            channelPlanScore = 0.85,
            overallQualityScore = 0.88,
            issues = emptyList()
        )

        val summary = report.configurationSummary
        assertTrue(summary.contains("GOOD") || summary.contains("EXCELLENT"))
        assertTrue(summary.contains("10 APs") || summary.contains("Multi"))
    }

    @Test
    fun `configurationSummary mentions critical issues`() {
        val report = ConfigurationReport(
            ssid = "Test",
            apCount = 3,
            securityConsistencyScore = 0.3,
            roamingCapabilityScore = 0.2,
            channelPlanScore = 0.25,
            overallQualityScore = 0.25,
            issues = listOf(
                ConfigurationIssue.PoorChannelPlanning(0.2, "Bad planning")
            )
        )

        val summary = report.configurationSummary
        assertTrue(summary.contains("CRITICAL", ignoreCase = true) ||
                   summary.contains("issue", ignoreCase = true))
    }

    @Test
    fun `detailedReport includes all components`() {
        val report = ConfigurationReport(
            ssid = "EnterpriseNet",
            apCount = 8,
            securityConsistencyScore = 0.90,
            roamingCapabilityScore = 0.85,
            channelPlanScore = 0.80,
            overallQualityScore = 0.85,
            issues = listOf(
                ConfigurationIssue.InfoNote("Test info")
            )
        )

        val detailed = report.detailedReport
        assertTrue(detailed.contains("EnterpriseNet"))
        assertTrue(detailed.contains("8"))
        assertTrue(detailed.contains("Security Consistency"))
        assertTrue(detailed.contains("Roaming Capability"))
        assertTrue(detailed.contains("Channel Planning"))
    }

    // ============================================
    // ConfigurationIssue TESTS
    // ============================================

    @Test
    fun `InconsistentSecurity has HIGH severity`() {
        val issue = ConfigurationIssue.InconsistentSecurity("TestNet", 4)
        assertEquals(ConfigurationIssueSeverity.HIGH, issue.severity)
        assertTrue(issue.shortDescription.contains("Inconsistent"))
        assertTrue(issue.details.contains("TestNet"))
        assertTrue(issue.details.contains("4"))
        assertTrue(issue.recommendation.isNotBlank())
    }

    @Test
    fun `MissingRoamingFeatures severity varies with coverage`() {
        val noCoverage = ConfigurationIssue.MissingRoamingFeatures("Test", 5, 0.0)
        assertEquals(ConfigurationIssueSeverity.MEDIUM, noCoverage.severity)

        val lowCoverage = ConfigurationIssue.MissingRoamingFeatures("Test", 5, 0.3)
        assertEquals(ConfigurationIssueSeverity.LOW, lowCoverage.severity)

        val goodCoverage = ConfigurationIssue.MissingRoamingFeatures("Test", 5, 0.7)
        assertEquals(ConfigurationIssueSeverity.INFO, goodCoverage.severity)
    }

    @Test
    fun `MissingRoamingFeatures details mention 802 point 11k-v-r`() {
        val issue = ConfigurationIssue.MissingRoamingFeatures("OfficeNet", 6, 0.5)
        assertTrue(issue.details.contains("802.11k") ||
                   issue.details.contains("802.11v") ||
                   issue.details.contains("802.11r"))
        assertTrue(issue.recommendation.contains("Enable") || issue.recommendation.contains("802.11"))
    }

    @Test
    fun `PoorChannelPlanning severity varies with quality`() {
        val critical = ConfigurationIssue.PoorChannelPlanning(0.2, "Severe overlap")
        assertEquals(ConfigurationIssueSeverity.HIGH, critical.severity)

        val medium = ConfigurationIssue.PoorChannelPlanning(0.4, "Some issues")
        assertEquals(ConfigurationIssueSeverity.MEDIUM, medium.severity)

        val minor = ConfigurationIssue.PoorChannelPlanning(0.55, "Minor issues")
        assertEquals(ConfigurationIssueSeverity.LOW, minor.severity)
    }

    @Test
    fun `PoorChannelPlanning includes primary issue in details`() {
        val issue = ConfigurationIssue.PoorChannelPlanning(0.3, "Overlapping 2.4 GHz channels")
        assertTrue(issue.details.contains("Overlapping 2.4 GHz channels"))
        assertTrue(issue.recommendation.contains("channel", ignoreCase = true))
    }

    @Test
    fun `MixedWiFiGenerations severity higher for 802 point 11b`() {
        val with11b = ConfigurationIssue.MixedWiFiGenerations(hasLegacy80211b = true)
        assertEquals(ConfigurationIssueSeverity.MEDIUM, with11b.severity)
        assertTrue(with11b.details.contains("802.11b") || with11b.details.contains("11b"))

        val without11b = ConfigurationIssue.MixedWiFiGenerations(hasLegacy80211b = false)
        assertEquals(ConfigurationIssueSeverity.LOW, without11b.severity)
    }

    @Test
    fun `MixedWiFiGenerations recommendation mentions disabling for 11b`() {
        val issue = ConfigurationIssue.MixedWiFiGenerations(hasLegacy80211b = true)
        assertTrue(issue.recommendation.contains("Disable", ignoreCase = true) ||
                   issue.recommendation.contains("CRITICAL", ignoreCase = true))
    }

    @Test
    fun `ExcessiveApOverlap severity varies with overlap percentage`() {
        val extreme = ConfigurationIssue.ExcessiveApOverlap(10, 0.9)
        assertEquals(ConfigurationIssueSeverity.HIGH, extreme.severity)

        val moderate = ConfigurationIssue.ExcessiveApOverlap(8, 0.7)
        assertEquals(ConfigurationIssueSeverity.MEDIUM, moderate.severity)

        val acceptable = ConfigurationIssue.ExcessiveApOverlap(5, 0.5)
        assertEquals(ConfigurationIssueSeverity.LOW, acceptable.severity)
    }

    @Test
    fun `ExcessiveApOverlap details include AP count and overlap percentage`() {
        val issue = ConfigurationIssue.ExcessiveApOverlap(12, 0.85)
        assertTrue(issue.details.contains("12"))
        assertTrue(issue.details.contains("85%"))
        assertTrue(issue.recommendation.contains("power", ignoreCase = true) ||
                   issue.recommendation.contains("density", ignoreCase = true))
    }

    @Test
    fun `InfoNote has INFO severity`() {
        val issue = ConfigurationIssue.InfoNote("This is an informational message")
        assertEquals(ConfigurationIssueSeverity.INFO, issue.severity)
        assertEquals("This is an informational message", issue.shortDescription)
        assertEquals("This is an informational message", issue.details)
        assertTrue(issue.recommendation.contains("No action"))
    }

    // ============================================
    // ConfigurationIssueSeverity TESTS
    // ============================================

    @Test
    fun `ConfigurationIssueSeverity enum has all expected values`() {
        val severities = ConfigurationIssueSeverity.values()
        assertTrue(severities.contains(ConfigurationIssueSeverity.INFO))
        assertTrue(severities.contains(ConfigurationIssueSeverity.LOW))
        assertTrue(severities.contains(ConfigurationIssueSeverity.MEDIUM))
        assertTrue(severities.contains(ConfigurationIssueSeverity.HIGH))
        assertTrue(severities.contains(ConfigurationIssueSeverity.CRITICAL))
    }

    @Test
    fun `ConfigurationIssueSeverity ordering is correct`() {
        assertTrue(ConfigurationIssueSeverity.INFO.ordinal < ConfigurationIssueSeverity.LOW.ordinal)
        assertTrue(ConfigurationIssueSeverity.LOW.ordinal < ConfigurationIssueSeverity.MEDIUM.ordinal)
        assertTrue(ConfigurationIssueSeverity.MEDIUM.ordinal < ConfigurationIssueSeverity.HIGH.ordinal)
        assertTrue(ConfigurationIssueSeverity.HIGH.ordinal < ConfigurationIssueSeverity.CRITICAL.ordinal)
    }

    // ============================================
    // ConfigurationQualityLevel TESTS
    // ============================================

    @Test
    fun `ConfigurationQualityLevel has descriptive names`() {
        assertEquals("Excellent - Best practices followed", ConfigurationQualityLevel.EXCELLENT.description)
        assertEquals("Good - Well configured", ConfigurationQualityLevel.GOOD.description)
        assertEquals("Fair - Some improvements needed", ConfigurationQualityLevel.FAIR.description)
        assertEquals("Poor - Significant issues present", ConfigurationQualityLevel.POOR.description)
        assertEquals("Critical - Immediate attention required", ConfigurationQualityLevel.CRITICAL.description)
    }

    // ============================================
    // COMPREHENSIVE VALIDATION
    // ============================================

    @Test
    fun `all ConfigurationIssue types have non-blank fields`() {
        val issues = listOf(
            ConfigurationIssue.InconsistentSecurity("Test", 2),
            ConfigurationIssue.MissingRoamingFeatures("Test", 3, 0.5),
            ConfigurationIssue.PoorChannelPlanning(0.4, "Issues"),
            ConfigurationIssue.MixedWiFiGenerations(true),
            ConfigurationIssue.ExcessiveApOverlap(5, 0.7),
            ConfigurationIssue.InfoNote("Info message")
        )

        issues.forEach { issue ->
            assertTrue(issue.shortDescription.isNotBlank(), "Short description blank for ${issue::class.simpleName}")
            assertTrue(issue.details.isNotBlank(), "Details blank for ${issue::class.simpleName}")
            assertTrue(issue.recommendation.isNotBlank(), "Recommendation blank for ${issue::class.simpleName}")
        }
    }
}
