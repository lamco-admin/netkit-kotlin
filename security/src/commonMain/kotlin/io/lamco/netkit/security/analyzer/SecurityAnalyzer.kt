package io.lamco.netkit.security.analyzer

import io.lamco.netkit.model.topology.SecurityFingerprint
import io.lamco.netkit.security.model.*

/**
 * Overall security posture analyzer for WiFi networks
 *
 * Provides comprehensive security analysis by aggregating:
 * - Per-BSS security scores
 * - WPS vulnerability assessments
 * - Configuration quality metrics
 * - Network-wide security trends
 *
 * Produces actionable security reports with:
 * - Overall security posture rating
 * - Critical vulnerabilities requiring immediate attention
 * - Risk prioritization
 * - Compliance assessment
 *
 * Based on:
 * - IEEE 802.11-2020 security best practices
 * - WPA3 security requirements
 * - NIST wireless security guidelines
 * - Industry security benchmarks
 *
 * @see SecurityScorePerBss
 * @see WpsRiskScore
 * @see ConfigurationReport
 */
class SecurityAnalyzer {
    /**
     * Analyze overall security posture for a single BSS
     *
     * @param bssid Access point BSSID
     * @param ssid Network name (may be null for hidden networks)
     * @param fingerprint Security configuration fingerprint
     * @param wpsInfo WPS configuration (null if WPS not present)
     * @param pmfCapable Whether PMF is supported but not required
     * @param managementCipher BIP cipher for management frames (null if no PMF)
     * @return Complete security analysis
     */
    fun analyzeBss(
        bssid: String,
        ssid: String?,
        fingerprint: SecurityFingerprint,
        wpsInfo: WpsInfo? = null,
        pmfCapable: Boolean = false,
        managementCipher: io.lamco.netkit.model.topology.CipherSuite? = null,
    ): BssSecurityAnalysis {
        // Generate security score
        val securityScore =
            SecurityScorePerBss.fromFingerprint(
                bssid = bssid,
                ssid = ssid,
                fingerprint = fingerprint,
                pmfCapable = pmfCapable,
                managementCipher = managementCipher,
            )

        // Generate WPS risk assessment
        val wpsRisk = WpsRiskScore.fromWpsInfo(bssid, wpsInfo)

        // Determine overall threat level
        val threatLevel = determineThreatLevel(securityScore, wpsRisk)

        // Identify critical issues
        val criticalIssues = identifyCriticalIssues(securityScore, wpsRisk)

        return BssSecurityAnalysis(
            bssid = bssid,
            ssid = ssid,
            securityScore = securityScore,
            wpsRisk = wpsRisk,
            threatLevel = threatLevel,
            criticalIssues = criticalIssues,
            requiresImmediateAction = criticalIssues.isNotEmpty(),
        )
    }

    /**
     * Analyze security across multiple BSSs in a network
     *
     * @param analyses List of per-BSS security analyses
     * @return Aggregated network security analysis
     */
    fun analyzeNetwork(analyses: List<BssSecurityAnalysis>): NetworkSecurityAnalysis {
        require(analyses.isNotEmpty()) { "At least one BSS analysis required" }

        // Calculate aggregate scores
        val avgSecurityScore = analyses.map { it.securityScore.overallSecurityScore }.average()
        val avgWpsRisk = analyses.mapNotNull { it.wpsRisk.riskScore }.average()

        // Determine worst-case threat level
        val overallThreatLevel =
            analyses.maxByOrNull { it.threatLevel.ordinal }?.threatLevel
                ?: ThreatLevel.LOW

        // Aggregate all critical issues
        val allCriticalIssues = analyses.flatMap { it.criticalIssues }.distinct()

        // Count APs by security level
        val securityDistribution =
            analyses
                .groupBy { it.securityScore.securityLevel }
                .mapValues { it.value.size }

        // Count APs with critical WPS risk
        val criticalWpsCount = analyses.count { it.wpsRisk.isCriticalRisk }

        // Overall compliance assessment
        val complianceLevel = assessCompliance(analyses)

        return NetworkSecurityAnalysis(
            totalBssCount = analyses.size,
            averageSecurityScore = avgSecurityScore,
            averageWpsRisk = avgWpsRisk,
            overallThreatLevel = overallThreatLevel,
            criticalIssues = allCriticalIssues,
            securityDistribution = securityDistribution,
            criticalWpsCount = criticalWpsCount,
            complianceLevel = complianceLevel,
            requiresImmediateAction = allCriticalIssues.isNotEmpty(),
            bssAnalyses = analyses,
        )
    }

    /**
     * Determine threat level from security score and WPS risk
     *
     * @param securityScore Per-BSS security score
     * @param wpsRisk WPS risk assessment
     * @return Overall threat level
     */
    private fun determineThreatLevel(
        securityScore: SecurityScorePerBss,
        wpsRisk: WpsRiskScore,
    ): ThreatLevel {
        // WPS critical risk overrides everything
        if (wpsRisk.isCriticalRisk) return ThreatLevel.CRITICAL

        // Critical security issues
        if (securityScore.hasCriticalIssues) return ThreatLevel.CRITICAL

        // High severity combination
        if (securityScore.hasHighSeverityIssues && wpsRisk.hasSignificantRisk) {
            return ThreatLevel.HIGH
        }

        // High severity security issues alone
        if (securityScore.hasHighSeverityIssues) return ThreatLevel.HIGH

        // Significant WPS risk alone
        if (wpsRisk.hasSignificantRisk) return ThreatLevel.MEDIUM

        // Medium severity issues
        if (securityScore.mediumIssueCount > 0) return ThreatLevel.MEDIUM

        // Low severity or no issues
        return if (securityScore.totalIssueCount > 0) ThreatLevel.LOW else ThreatLevel.NONE
    }

    /**
     * Identify critical issues requiring immediate action
     *
     * @param securityScore Security score with issues
     * @param wpsRisk WPS risk with issues
     * @return List of critical issues
     */
    private fun identifyCriticalIssues(
        securityScore: SecurityScorePerBss,
        wpsRisk: WpsRiskScore,
    ): List<CriticalIssue> {
        val critical = mutableListOf<CriticalIssue>()

        // Security issues
        securityScore.issues
            .filter { it.severity == SecurityIssue.Severity.CRITICAL || it.severity == SecurityIssue.Severity.HIGH }
            .forEach { issue ->
                critical.add(
                    CriticalIssue(
                        category = CriticalIssueCategory.SECURITY,
                        severity =
                            when (issue.severity) {
                                SecurityIssue.Severity.CRITICAL -> CriticalIssueSeverity.CRITICAL
                                SecurityIssue.Severity.HIGH -> CriticalIssueSeverity.HIGH
                                else -> CriticalIssueSeverity.MEDIUM
                            },
                        description = issue.shortDescription,
                        recommendation = issue.recommendation,
                    ),
                )
            }

        // WPS issues
        if (wpsRisk.isCriticalRisk || wpsRisk.riskLevel == RiskLevel.HIGH) {
            critical.add(
                CriticalIssue(
                    category = CriticalIssueCategory.WPS,
                    severity =
                        if (wpsRisk.isCriticalRisk) {
                            CriticalIssueSeverity.CRITICAL
                        } else {
                            CriticalIssueSeverity.HIGH
                        },
                    description = wpsRisk.riskSummary,
                    recommendation = "Disable WPS or use push-button only mode with lock enabled",
                ),
            )
        }

        return critical
    }

    /**
     * Assess compliance with security standards
     *
     * @param analyses List of BSS analyses
     * @return Compliance level
     */
    private fun assessCompliance(analyses: List<BssSecurityAnalysis>): ComplianceLevel {
        val meetsMinimum = analyses.count { it.securityScore.meetsMinimumSecurity }
        val usesModern = analyses.count { it.securityScore.usesModernSecurity }
        val hasWpsIssues = analyses.count { it.wpsRisk.hasSignificantRisk }

        val minimumPercent = meetsMinimum.toDouble() / analyses.size
        val modernPercent = usesModern.toDouble() / analyses.size
        val wpsIssuePercent = hasWpsIssues.toDouble() / analyses.size

        return when {
            // Full compliance: all modern, no WPS issues
            modernPercent == 1.0 && wpsIssuePercent == 0.0 -> ComplianceLevel.FULL

            // High compliance: 80%+ modern, minimal WPS issues
            modernPercent >= 0.8 && wpsIssuePercent < 0.2 -> ComplianceLevel.HIGH

            // Moderate compliance: 60%+ meet minimum, some issues
            minimumPercent >= 0.6 && wpsIssuePercent < 0.5 -> ComplianceLevel.MODERATE

            // Low compliance: some APs meet minimum
            minimumPercent >= 0.3 -> ComplianceLevel.LOW

            // Non-compliant: majority fail minimum security
            else -> ComplianceLevel.NON_COMPLIANT
        }
    }
}

/**
 * Security analysis result for a single BSS
 *
 * @property bssid Access point BSSID
 * @property ssid Network name (may be null)
 * @property securityScore Per-BSS security score
 * @property wpsRisk WPS vulnerability assessment
 * @property threatLevel Overall threat level
 * @property criticalIssues Issues requiring immediate action
 * @property requiresImmediateAction Whether immediate action is needed
 */
data class BssSecurityAnalysis(
    val bssid: String,
    val ssid: String?,
    val securityScore: SecurityScorePerBss,
    val wpsRisk: WpsRiskScore,
    val threatLevel: ThreatLevel,
    val criticalIssues: List<CriticalIssue>,
    val requiresImmediateAction: Boolean,
    val pmfEnabled: Boolean = false,
    val pmfCapable: Boolean = false,
    val roamingFeaturesSupported: Boolean = false,
) {
    /**
     * Summary of security analysis
     */
    val summary: String
        get() =
            buildString {
                append("Threat: $threatLevel")
                if (ssid != null) append(" - $ssid")
                append(" ($bssid)")
                if (requiresImmediateAction) {
                    append(" - ${criticalIssues.size} critical issue(s)")
                }
            }
}

/**
 * Network-wide security analysis result
 *
 * @property totalBssCount Total number of BSSs analyzed
 * @property averageSecurityScore Average security score across all BSSs
 * @property averageWpsRisk Average WPS risk score
 * @property overallThreatLevel Highest threat level detected
 * @property criticalIssues All critical issues requiring immediate action
 * @property securityDistribution Distribution of APs by security level
 * @property criticalWpsCount Number of APs with critical WPS risk
 * @property complianceLevel Overall compliance with security standards
 * @property requiresImmediateAction Whether immediate action is needed
 * @property bssAnalyses Individual BSS security analyses
 */
data class NetworkSecurityAnalysis(
    val totalBssCount: Int,
    val averageSecurityScore: Double,
    val averageWpsRisk: Double,
    val overallThreatLevel: ThreatLevel,
    val criticalIssues: List<CriticalIssue>,
    val securityDistribution: Map<SecurityLevel, Int>,
    val criticalWpsCount: Int,
    val complianceLevel: ComplianceLevel,
    val requiresImmediateAction: Boolean,
    val bssAnalyses: List<BssSecurityAnalysis> = emptyList(),
) {
    /**
     * Percentage of APs meeting minimum security
     */
    val minimumSecurityPercentage: Double
        get() {
            val meeting =
                (securityDistribution[SecurityLevel.EXCELLENT] ?: 0) +
                    (securityDistribution[SecurityLevel.GOOD] ?: 0)
            return meeting.toDouble() / totalBssCount * 100.0
        }

    /**
     * Summary of network security
     */
    val summary: String
        get() =
            buildString {
                appendLine("Network Security Analysis:")
                appendLine("  BSSs: $totalBssCount")
                appendLine("  Threat Level: $overallThreatLevel")
                appendLine("  Compliance: $complianceLevel")
                appendLine("  Avg Security: ${(averageSecurityScore * 100).toInt()}%")
                if (criticalWpsCount > 0) {
                    appendLine("  Critical WPS Issues: $criticalWpsCount APs")
                }
                if (requiresImmediateAction) {
                    appendLine("  ⚠️ ${criticalIssues.size} issue(s) require immediate action")
                }
            }
}

/**
 * Overall threat level
 */
enum class ThreatLevel {
    /** No security threats detected */
    NONE,

    /** Low severity issues present */
    LOW,

    /** Medium severity issues requiring attention */
    MEDIUM,

    /** High severity issues requiring prompt action */
    HIGH,

    /** Critical issues requiring immediate action */
    CRITICAL,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                NONE -> "No threats detected"
                LOW -> "Low - Minor issues present"
                MEDIUM -> "Medium - Attention required"
                HIGH -> "High - Prompt action needed"
                CRITICAL -> "Critical - Immediate action required"
            }
}

/**
 * Critical issue requiring immediate attention
 *
 * @property category Issue category
 * @property severity Issue severity
 * @property description Short description
 * @property recommendation Recommended action
 */
data class CriticalIssue(
    val category: CriticalIssueCategory,
    val severity: CriticalIssueSeverity,
    val description: String,
    val recommendation: String,
)

/**
 * Critical issue categories
 */
enum class CriticalIssueCategory {
    /** Security configuration issue */
    SECURITY,

    /** WPS vulnerability */
    WPS,

    /** Protected Management Frames issue */
    PMF,

    /** Weak cipher configuration */
    CIPHER,

    /** Configuration consistency */
    CONFIGURATION,

    /** Compliance violation */
    COMPLIANCE,
}

/**
 * Critical issue severity levels
 */
enum class CriticalIssueSeverity {
    /** Medium severity */
    MEDIUM,

    /** High severity */
    HIGH,

    /** Critical severity */
    CRITICAL,
}

/**
 * Compliance with security standards
 */
enum class ComplianceLevel {
    /** Fully compliant - all APs meet modern security standards */
    FULL,

    /** Highly compliant - most APs meet standards */
    HIGH,

    /** Moderately compliant - some gaps present */
    MODERATE,

    /** Low compliance - significant gaps */
    LOW,

    /** Non-compliant - fails minimum security requirements */
    NON_COMPLIANT,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                FULL -> "Full Compliance - Meets all security standards"
                HIGH -> "High Compliance - Minor gaps only"
                MODERATE -> "Moderate Compliance - Some improvements needed"
                LOW -> "Low Compliance - Significant gaps present"
                NON_COMPLIANT -> "Non-Compliant - Fails minimum requirements"
            }
}
