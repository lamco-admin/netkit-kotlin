package io.lamco.netkit.security.advisor

import io.lamco.netkit.security.analyzer.BssSecurityAnalysis
import io.lamco.netkit.security.analyzer.NetworkSecurityAnalysis
import io.lamco.netkit.security.analyzer.ThreatLevel

/**
 * Risk prioritization engine for security assessment
 *
 * The RiskPrioritizer analyzes security risks across multiple dimensions and
 * generates a prioritized action plan based on risk scores, impact, and effort.
 *
 * Risk Dimensions:
 * - Security posture (authentication, encryption)
 * - WPS vulnerabilities
 * - Cipher weaknesses
 * - PMF compliance
 * - Configuration inconsistencies
 *
 * Prioritization Factors:
 * - Risk severity (critical issues first)
 * - Impact scope (network-wide vs per-AP)
 * - Implementation effort (quick wins prioritized)
 * - Dependencies (prerequisite actions)
 *
 * Example Usage:
 * ```kotlin
 * val prioritizer = RiskPrioritizer()
 * val plan = prioritizer.prioritize(networkAnalysis)
 * plan.immediateActions.forEach { println(it.description) }
 * ```
 *
 * @see RiskAssessment
 * @see PrioritizedRisk
 */
class RiskPrioritizer {
    /**
     * Prioritize risks and generate action plan
     *
     * @param networkAnalysis Network security analysis
     * @return Risk assessment with prioritized action plan
     */
    fun prioritize(networkAnalysis: NetworkSecurityAnalysis): RiskAssessment {
        val risks = mutableListOf<PrioritizedRisk>()

        // Assess network-wide risks
        risks.addAll(assessNetworkWideRisks(networkAnalysis))

        // Assess per-BSS risks
        networkAnalysis.bssAnalyses.forEach { bss ->
            risks.addAll(assessBssRisks(bss))
        }

        // Sort by priority score (highest first)
        val prioritized =
            risks.sortedWith(
                compareByDescending<PrioritizedRisk> { it.riskScore }
                    .thenBy { it.effort.ordinal }
                    .thenByDescending { it.impact.ordinal },
            )

        return RiskAssessment(
            networkAnalysis = networkAnalysis,
            prioritizedRisks = prioritized,
            totalRiskScore = calculateTotalRiskScore(prioritized),
        )
    }

    /**
     * Assess network-wide security risks
     *
     * @param networkAnalysis Network security analysis
     * @return List of network-wide risks
     */
    private fun assessNetworkWideRisks(networkAnalysis: NetworkSecurityAnalysis): List<PrioritizedRisk> {
        val risks = mutableListOf<PrioritizedRisk>()

        // Critical threat level
        if (networkAnalysis.overallThreatLevel == ThreatLevel.CRITICAL) {
            risks.add(
                PrioritizedRisk(
                    id = "NETWORK_CRITICAL_THREAT",
                    title = "Critical Network Threat Level",
                    description = "Network has critical security threats requiring immediate attention",
                    impact = RiskImpact.CRITICAL,
                    likelihood = RiskLikelihood.CERTAIN,
                    effort = EffortLevel.HIGH,
                    category = RiskCategory.SECURITY_POSTURE,
                    affectedBssids = networkAnalysis.bssAnalyses.map { it.bssid },
                    mitigationSteps =
                        listOf(
                            "Identify all BSSs with critical threat level",
                            "Address critical security issues immediately",
                            "Consider taking vulnerable APs offline until fixed",
                        ),
                ),
            )
        }

        // Low compliance
        if (networkAnalysis.complianceLevel.ordinal >= 3) { // LOW or NON_COMPLIANT
            risks.add(
                PrioritizedRisk(
                    id = "NETWORK_LOW_COMPLIANCE",
                    title = "Low Security Compliance",
                    description = "Network does not meet minimum security standards",
                    impact = RiskImpact.HIGH,
                    likelihood = RiskLikelihood.CERTAIN,
                    effort = EffortLevel.HIGH,
                    category = RiskCategory.COMPLIANCE,
                    affectedBssids = emptyList(),
                    mitigationSteps =
                        listOf(
                            "Review security policy requirements",
                            "Upgrade authentication to WPA2 or WPA3",
                            "Enable modern encryption ciphers",
                        ),
                ),
            )
        }

        // Poor minimum security
        if (networkAnalysis.minimumSecurityPercentage < 50.0) {
            risks.add(
                PrioritizedRisk(
                    id = "NETWORK_LOW_MINIMUM_SECURITY",
                    title = "Insufficient Minimum Security",
                    description = "Less than 50% of APs meet minimum security standards",
                    impact = RiskImpact.HIGH,
                    likelihood = RiskLikelihood.LIKELY,
                    effort = EffortLevel.MEDIUM,
                    category = RiskCategory.SECURITY_POSTURE,
                    affectedBssids =
                        networkAnalysis.bssAnalyses
                            .filter { !it.securityScore.meetsMinimumSecurity }
                            .map { it.bssid },
                    mitigationSteps =
                        listOf(
                            "Upgrade weak APs to meet minimum security level",
                            "Replace obsolete hardware if necessary",
                        ),
                ),
            )
        }

        return risks
    }

    /**
     * Assess per-BSS security risks
     *
     * @param bss BSS security analysis
     * @return List of BSS-specific risks
     */
    private fun assessBssRisks(bss: BssSecurityAnalysis): List<PrioritizedRisk> {
        val risks = mutableListOf<PrioritizedRisk>()

        // WPS risks
        val wpsRisk = bss.wpsRisk
        if (wpsRisk != null && wpsRisk.riskScore >= 0.6) {
            risks.add(
                PrioritizedRisk(
                    id = "BSS_WPS_${bss.bssid}",
                    title = "WPS Vulnerability",
                    description = "WPS enabled with high risk score (${String.format("%.2f", wpsRisk.riskScore)})",
                    impact =
                        when {
                            wpsRisk.riskScore >= 0.9 -> RiskImpact.CRITICAL
                            wpsRisk.riskScore >= 0.6 -> RiskImpact.HIGH
                            else -> RiskImpact.MEDIUM
                        },
                    likelihood = RiskLikelihood.LIKELY,
                    effort = EffortLevel.LOW,
                    category = RiskCategory.WPS,
                    affectedBssids = listOf(bss.bssid),
                    mitigationSteps =
                        listOf(
                            "Access router admin interface",
                            "Navigate to WPS settings",
                            "Disable WPS completely",
                            "Verify WPS is disabled after reboot",
                        ),
                ),
            )
        }

        // Weak ciphers
        if (bss.securityScore.cipherStrengthScore < 0.4) {
            risks.add(
                PrioritizedRisk(
                    id = "BSS_WEAK_CIPHER_${bss.bssid}",
                    title = "Weak Encryption",
                    description = "Network uses weak or deprecated encryption (${bss.securityScore.cipherSet.joinToString {
                        it.displayName
                    }})",
                    impact = RiskImpact.HIGH,
                    likelihood = RiskLikelihood.CERTAIN,
                    effort = EffortLevel.MEDIUM,
                    category = RiskCategory.ENCRYPTION,
                    affectedBssids = listOf(bss.bssid),
                    mitigationSteps =
                        listOf(
                            "Upgrade to WPA2-Personal with AES-CCMP",
                            "Or upgrade to WPA3 if supported",
                            "Change WiFi password during upgrade",
                            "Update all client devices",
                        ),
                ),
            )
        }

        // Missing PMF
        if (!bss.pmfEnabled && bss.pmfCapable && bss.securityScore.authType.requiresPmf) {
            risks.add(
                PrioritizedRisk(
                    id = "BSS_MISSING_PMF_${bss.bssid}",
                    title = "PMF Not Enabled",
                    description = "Protected Management Frames required but not enabled",
                    impact = RiskImpact.MEDIUM,
                    likelihood = RiskLikelihood.POSSIBLE,
                    effort = EffortLevel.LOW,
                    category = RiskCategory.PMF,
                    affectedBssids = listOf(bss.bssid),
                    mitigationSteps =
                        listOf(
                            "Access router settings",
                            "Enable PMF (802.11w) if available",
                            "Set to 'Required' for WPA3, 'Capable' for WPA2",
                        ),
                ),
            )
        }

        // Critical threat level
        if (bss.threatLevel == ThreatLevel.CRITICAL) {
            risks.add(
                PrioritizedRisk(
                    id = "BSS_CRITICAL_THREAT_${bss.bssid}",
                    title = "Critical Security Threat",
                    description = "BSS has critical security vulnerabilities",
                    impact = RiskImpact.CRITICAL,
                    likelihood = RiskLikelihood.CERTAIN,
                    effort = EffortLevel.HIGH,
                    category = RiskCategory.SECURITY_POSTURE,
                    affectedBssids = listOf(bss.bssid),
                    mitigationSteps =
                        listOf(
                            "Review all critical issues",
                            "Take immediate corrective action",
                            "Consider disconnecting AP until fixed",
                        ),
                ),
            )
        }

        return risks
    }

    /**
     * Calculate total risk score for the network
     *
     * @param risks List of prioritized risks
     * @return Overall risk score (0.0-1.0)
     */
    private fun calculateTotalRiskScore(risks: List<PrioritizedRisk>): Double {
        if (risks.isEmpty()) return 0.0

        // Weight by impact
        val weightedSum =
            risks.sumOf { risk ->
                when (risk.impact) {
                    RiskImpact.CRITICAL -> 1.0
                    RiskImpact.HIGH -> 0.7
                    RiskImpact.MEDIUM -> 0.4
                    RiskImpact.LOW -> 0.2
                    RiskImpact.NEGLIGIBLE -> 0.1
                }
            }

        // Normalize to 0.0-1.0 range
        return (weightedSum / risks.size).coerceIn(0.0, 1.0)
    }
}

/**
 * Risk assessment with prioritized action plan
 *
 * @property networkAnalysis Network security analysis
 * @property prioritizedRisks List of risks sorted by priority
 * @property totalRiskScore Overall risk score (0.0-1.0)
 */
data class RiskAssessment(
    val networkAnalysis: NetworkSecurityAnalysis,
    val prioritizedRisks: List<PrioritizedRisk>,
    val totalRiskScore: Double,
) {
    init {
        require(totalRiskScore in 0.0..1.0) { "Total risk score must be between 0.0 and 1.0" }
    }

    /**
     * Immediate actions (critical/high impact, low/medium effort)
     */
    val immediateActions: List<PrioritizedRisk>
        get() =
            prioritizedRisks.filter {
                (it.impact == RiskImpact.CRITICAL || it.impact == RiskImpact.HIGH) &&
                    (it.effort == EffortLevel.LOW || it.effort == EffortLevel.MEDIUM)
            }

    /**
     * Quick wins (low effort, any impact)
     */
    val quickWins: List<PrioritizedRisk>
        get() = prioritizedRisks.filter { it.effort == EffortLevel.LOW }

    /**
     * Critical risks requiring immediate attention
     */
    val criticalRisks: List<PrioritizedRisk>
        get() = prioritizedRisks.filter { it.impact == RiskImpact.CRITICAL }

    /**
     * Risks by category
     */
    val risksByCategory: Map<RiskCategory, List<PrioritizedRisk>>
        get() = prioritizedRisks.groupBy { it.category }

    /**
     * Overall risk level
     */
    val overallRiskLevel: OverallRiskLevel
        get() =
            when {
                totalRiskScore >= 0.8 -> OverallRiskLevel.CRITICAL
                totalRiskScore >= 0.6 -> OverallRiskLevel.HIGH
                totalRiskScore >= 0.4 -> OverallRiskLevel.MEDIUM
                totalRiskScore >= 0.2 -> OverallRiskLevel.LOW
                else -> OverallRiskLevel.MINIMAL
            }

    /**
     * Summary of risk assessment
     */
    val summary: String
        get() =
            buildString {
                appendLine("Risk Assessment Summary:")
                appendLine("Overall Risk: $overallRiskLevel (score: ${String.format("%.2f", totalRiskScore)})")
                appendLine("Total Risks: ${prioritizedRisks.size}")
                appendLine("Critical: ${criticalRisks.size}")
                appendLine("Immediate Actions: ${immediateActions.size}")
                appendLine("Quick Wins: ${quickWins.size}")
            }
}

/**
 * Prioritized security risk
 *
 * @property id Unique risk identifier
 * @property title Short title
 * @property description Detailed description
 * @property impact Impact level if risk is realized
 * @property likelihood Likelihood of risk being realized
 * @property effort Effort required to mitigate
 * @property category Risk category
 * @property affectedBssids List of affected BSSIDs
 * @property mitigationSteps Steps to mitigate the risk
 */
data class PrioritizedRisk(
    val id: String,
    val title: String,
    val description: String,
    val impact: RiskImpact,
    val likelihood: RiskLikelihood,
    val effort: EffortLevel,
    val category: RiskCategory,
    val affectedBssids: List<String>,
    val mitigationSteps: List<String>,
) {
    /**
     * Risk score based on impact and likelihood
     */
    val riskScore: Double
        get() = impact.score * likelihood.score

    /**
     * Priority score (higher = more urgent)
     */
    val priorityScore: Double
        get() {
            val effortPenalty =
                when (effort) {
                    EffortLevel.LOW -> 1.0
                    EffortLevel.MEDIUM -> 0.8
                    EffortLevel.HIGH -> 0.6
                }
            return riskScore * effortPenalty
        }

    /**
     * Risk summary
     */
    val summary: String
        get() = "[$impact] $title - ${affectedBssids.size} AP(s) affected"
}

/**
 * Risk impact levels
 */
enum class RiskImpact(
    val score: Double,
) {
    /** Critical impact - severe consequences */
    CRITICAL(1.0),

    /** High impact - significant consequences */
    HIGH(0.7),

    /** Medium impact - moderate consequences */
    MEDIUM(0.4),

    /** Low impact - minor consequences */
    LOW(0.2),

    /** Negligible impact - minimal consequences */
    NEGLIGIBLE(0.1),
    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                CRITICAL -> "Critical - Severe consequences"
                HIGH -> "High - Significant consequences"
                MEDIUM -> "Medium - Moderate consequences"
                LOW -> "Low - Minor consequences"
                NEGLIGIBLE -> "Negligible - Minimal consequences"
            }
}

/**
 * Risk likelihood levels
 */
enum class RiskLikelihood(
    val score: Double,
) {
    /** Certain - will definitely occur */
    CERTAIN(1.0),

    /** Likely - probably will occur */
    LIKELY(0.7),

    /** Possible - may occur */
    POSSIBLE(0.5),

    /** Unlikely - probably won't occur */
    UNLIKELY(0.3),

    /** Rare - very unlikely to occur */
    RARE(0.1),
    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                CERTAIN -> "Certain - Will definitely occur"
                LIKELY -> "Likely - Probably will occur"
                POSSIBLE -> "Possible - May occur"
                UNLIKELY -> "Unlikely - Probably won't occur"
                RARE -> "Rare - Very unlikely to occur"
            }
}

/**
 * Risk categories
 */
enum class RiskCategory {
    /** Overall security posture */
    SECURITY_POSTURE,

    /** WPS vulnerabilities */
    WPS,

    /** Encryption weaknesses */
    ENCRYPTION,

    /** PMF issues */
    PMF,

    /** Configuration problems */
    CONFIGURATION,

    /** Compliance violations */
    COMPLIANCE,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                SECURITY_POSTURE -> "Security Posture"
                WPS -> "WPS Vulnerabilities"
                ENCRYPTION -> "Encryption"
                PMF -> "Protected Management Frames"
                CONFIGURATION -> "Configuration"
                COMPLIANCE -> "Compliance"
            }
}

/**
 * Overall risk levels
 */
enum class OverallRiskLevel {
    /** Critical risk */
    CRITICAL,

    /** High risk */
    HIGH,

    /** Medium risk */
    MEDIUM,

    /** Low risk */
    LOW,

    /** Minimal risk */
    MINIMAL,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                CRITICAL -> "Critical - Immediate action required"
                HIGH -> "High - Action needed soon"
                MEDIUM -> "Medium - Should be addressed"
                LOW -> "Low - Minor concerns"
                MINIMAL -> "Minimal - Good security posture"
            }
}
