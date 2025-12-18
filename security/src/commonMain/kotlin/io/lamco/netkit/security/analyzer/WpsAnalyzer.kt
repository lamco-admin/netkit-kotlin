package io.lamco.netkit.security.analyzer

import io.lamco.netkit.security.model.*

/**
 * WiFi Protected Setup (WPS) vulnerability analyzer
 *
 * Analyzes WPS configuration and identifies security risks including:
 * - PIN method vulnerabilities (brute force attacks)
 * - Unlocked WPS state (unlimited attack attempts)
 * - Configured-but-active WPS (unnecessary exposure)
 * - Unknown or risky configurations
 *
 * WPS Security Background:
 * - PIN method has only 10^4 possible values (due to checksum)
 * - Vulnerable to Reaver brute force (4-10 hours typical)
 * - Vulnerable to Pixie Dust attacks (seconds offline)
 * - Push-button is safer but has timing attack risks
 * - Many routers never properly disable WPS
 *
 * References:
 * - Wi-Fi Alliance WPS specification
 * - CVE-2011-5053 (WPS PIN brute force vulnerability)
 * - Viehböck (2011) "Brute forcing Wi-Fi Protected Setup"
 * - Pixie Dust attack (Dominique Bongard, 2014)
 *
 * @see WpsInfo
 * @see WpsRiskScore
 */
class WpsAnalyzer {
    /**
     * Analyze WPS configuration for security vulnerabilities
     *
     * @param bssid Access point BSSID
     * @param wpsInfo WPS configuration (null if WPS not present)
     * @return Complete WPS vulnerability analysis
     */
    fun analyze(
        bssid: String,
        wpsInfo: WpsInfo?,
    ): WpsAnalysisResult {
        // Generate risk score
        val riskScore = WpsRiskScore.fromWpsInfo(bssid, wpsInfo)

        // Identify vulnerabilities
        val vulnerabilities = identifyVulnerabilities(wpsInfo, riskScore)

        // Generate recommendations
        val recommendations = generateRecommendations(wpsInfo, riskScore, vulnerabilities)

        // Determine if WPS should be disabled
        val shouldDisable = shouldDisableWps(wpsInfo, riskScore)

        return WpsAnalysisResult(
            bssid = bssid,
            wpsInfo = wpsInfo,
            riskScore = riskScore,
            vulnerabilities = vulnerabilities,
            recommendations = recommendations,
            shouldDisableWps = shouldDisable,
        )
    }

    /**
     * Batch analyze WPS across multiple APs
     *
     * @param wpsDataList List of (BSSID, WpsInfo) pairs
     * @return Aggregated WPS analysis
     */
    fun analyzeMultiple(wpsDataList: List<Pair<String, WpsInfo?>>): WpsNetworkAnalysis {
        val analyses =
            wpsDataList.map { (bssid, wpsInfo) ->
                analyze(bssid, wpsInfo)
            }

        val totalAps = analyses.size
        val wpsEnabledCount = analyses.count { it.wpsInfo != null && !it.wpsInfo.isDisabled }
        val criticalRiskCount = analyses.count { it.riskScore.isCriticalRisk }
        val highRiskCount = analyses.count { it.riskScore.riskLevel == RiskLevel.HIGH }
        val pinSupportedCount = analyses.count { it.wpsInfo?.supportsPinMethod == true }

        val overallRisk =
            when {
                criticalRiskCount > 0 -> RiskLevel.CRITICAL
                highRiskCount > 0 -> RiskLevel.HIGH
                wpsEnabledCount > totalAps / 2 -> RiskLevel.MEDIUM
                wpsEnabledCount > 0 -> RiskLevel.LOW
                else -> RiskLevel.LOW
            }

        return WpsNetworkAnalysis(
            totalApCount = totalAps,
            wpsEnabledCount = wpsEnabledCount,
            criticalRiskCount = criticalRiskCount,
            highRiskCount = highRiskCount,
            pinSupportedCount = pinSupportedCount,
            overallRisk = overallRisk,
            analyses = analyses,
        )
    }

    /**
     * Identify specific WPS vulnerabilities
     *
     * @param wpsInfo WPS configuration
     * @param riskScore Risk assessment
     * @return List of vulnerabilities
     */
    private fun identifyVulnerabilities(
        wpsInfo: WpsInfo?,
        riskScore: WpsRiskScore,
    ): List<WpsVulnerability> {
        if (wpsInfo == null || wpsInfo.isDisabled) return emptyList()

        val vulnerabilities = mutableListOf<WpsVulnerability>()

        // Check for PIN method
        if (wpsInfo.supportsPinMethod) {
            vulnerabilities.add(
                WpsVulnerability(
                    type = WpsVulnerabilityType.PIN_BRUTE_FORCE,
                    severity =
                        if (!wpsInfo.isLocked) {
                            WpsVulnerabilitySeverity.CRITICAL
                        } else {
                            WpsVulnerabilitySeverity.HIGH
                        },
                    description = "WPS PIN method is vulnerable to brute force attacks",
                    details =
                        buildString {
                            append("The WPS PIN has only 10,000 possible values. ")
                            if (!wpsInfo.isLocked) {
                                append("Unlocked state allows unlimited attempts. ")
                                append("Can be cracked in 4-10 hours using Reaver. ")
                            } else {
                                append("Lock mechanism may not be foolproof. ")
                            }
                            append("Vulnerable to Pixie Dust offline attacks.")
                        },
                    cveReferences = listOf("CVE-2011-5053"),
                ),
            )
        }

        // Check for unlocked state
        if (!wpsInfo.isLocked && wpsInfo.wpsState == WpsState.CONFIGURED) {
            vulnerabilities.add(
                WpsVulnerability(
                    type = WpsVulnerabilityType.UNLOCKED_STATE,
                    severity = WpsVulnerabilitySeverity.HIGH,
                    description = "WPS is unlocked and allows unlimited connection attempts",
                    details =
                        "Attackers can make unlimited WPS connection attempts without rate limiting. " +
                            "This enables both online PIN brute force and offline attacks.",
                    cveReferences = emptyList(),
                ),
            )
        }

        // Check for configured-but-active (should be disabled after setup)
        if (wpsInfo.isConfigured && !wpsInfo.isLocked) {
            vulnerabilities.add(
                WpsVulnerability(
                    type = WpsVulnerabilityType.UNNECESSARY_EXPOSURE,
                    severity = WpsVulnerabilitySeverity.MEDIUM,
                    description = "WPS is still active after configuration",
                    details =
                        "Best practice is to disable WPS after initial device setup. " +
                            "Leaving it active provides an unnecessary attack surface.",
                    cveReferences = emptyList(),
                ),
            )
        }

        // Check for push-button timing attacks
        if (wpsInfo.isPushButtonOnly && !wpsInfo.isLocked) {
            vulnerabilities.add(
                WpsVulnerability(
                    type = WpsVulnerabilityType.PUSH_BUTTON_TIMING,
                    severity = WpsVulnerabilitySeverity.LOW,
                    description = "Push-button WPS has potential timing attack vulnerabilities",
                    details =
                        "While safer than PIN, push-button WPS can be exploited if an attacker " +
                            "can trigger the button or predict the timing window (typically 120 seconds).",
                    cveReferences = emptyList(),
                ),
            )
        }

        return vulnerabilities
    }

    /**
     * Generate WPS security recommendations
     *
     * @param wpsInfo WPS configuration
     * @param riskScore Risk assessment
     * @param vulnerabilities Detected vulnerabilities
     * @return List of recommendations
     */
    private fun generateRecommendations(
        wpsInfo: WpsInfo?,
        riskScore: WpsRiskScore,
        vulnerabilities: List<WpsVulnerability>,
    ): List<String> {
        if (wpsInfo == null || wpsInfo.isDisabled) {
            return listOf("WPS is disabled - good security practice")
        }

        val recommendations = mutableListOf<String>()

        // Critical: PIN method enabled
        if (wpsInfo.supportsPinMethod) {
            recommendations.add("⚠️ CRITICAL: Disable WPS PIN method immediately in router settings")
            recommendations.add("If WPS is needed, use push-button (PBC) method only")
        }

        // High: Unlocked state
        if (!wpsInfo.isLocked) {
            recommendations.add("Enable WPS lock/rate limiting to prevent brute force attacks")
        }

        // Medium: Still configured
        if (wpsInfo.isConfigured) {
            recommendations.add("Disable WPS after initial device setup is complete")
            recommendations.add("Best practice: Only enable WPS temporarily when adding new devices")
        }

        // General recommendations based on risk
        when (riskScore.riskLevel) {
            RiskLevel.CRITICAL, RiskLevel.HIGH -> {
                recommendations.add("Consider disabling WPS entirely - most modern devices don't need it")
                recommendations.add("Use WPA2-Personal or WPA3-Personal with a strong password instead")
            }
            RiskLevel.MEDIUM -> {
                recommendations.add("If WPS must remain enabled, use push-button only and enable lock")
            }
            else -> {}
        }

        // Device-specific recommendations
        wpsInfo.manufacturer?.let { manufacturer ->
            when {
                manufacturer.contains("Netgear", ignoreCase = true) ->
                    recommendations.add("Netgear routers: Disable WPS in Advanced > Wireless Settings")
                manufacturer.contains("TP-Link", ignoreCase = true) ->
                    recommendations.add("TP-Link routers: Disable WPS in Wireless > WPS Settings")
                manufacturer.contains("Linksys", ignoreCase = true) ->
                    recommendations.add("Linksys routers: Disable WPS in Wireless > WiFi Protected Setup")
            }
        }

        return recommendations
    }

    /**
     * Determine if WPS should be disabled
     *
     * @param wpsInfo WPS configuration
     * @param riskScore Risk assessment
     * @return True if WPS should be disabled
     */
    private fun shouldDisableWps(
        wpsInfo: WpsInfo?,
        riskScore: WpsRiskScore,
    ): Boolean {
        if (wpsInfo == null || wpsInfo.isDisabled) return false

        // Disable if critical risk
        if (riskScore.isCriticalRisk) return true

        // Disable if PIN supported (regardless of lock state)
        if (wpsInfo.supportsPinMethod) return true

        // Disable if configured but not locked
        if (wpsInfo.isConfigured && !wpsInfo.isLocked) return true

        return false
    }
}

/**
 * WPS analysis result for a single AP
 *
 * @property bssid Access point BSSID
 * @property wpsInfo WPS configuration
 * @property riskScore WPS risk assessment
 * @property vulnerabilities Specific vulnerabilities detected
 * @property recommendations Security recommendations
 * @property shouldDisableWps Whether WPS should be disabled
 */
data class WpsAnalysisResult(
    val bssid: String,
    val wpsInfo: WpsInfo?,
    val riskScore: WpsRiskScore,
    val vulnerabilities: List<WpsVulnerability>,
    val shouldDisableWps: Boolean,
    val recommendations: List<String>,
) {
    /**
     * Whether WPS is enabled
     */
    val isWpsEnabled: Boolean
        get() = wpsInfo != null && !wpsInfo.isDisabled

    /**
     * Summary of WPS status
     */
    val summary: String
        get() =
            buildString {
                if (!isWpsEnabled) {
                    append("WPS Disabled")
                } else {
                    append("WPS Enabled - ${riskScore.riskLevel} Risk")
                    if (vulnerabilities.isNotEmpty()) {
                        append(" (${vulnerabilities.size} vulnerabilities)")
                    }
                }
            }
}

/**
 * WPS analysis across multiple APs
 *
 * @property totalApCount Total number of APs analyzed
 * @property wpsEnabledCount Number of APs with WPS enabled
 * @property criticalRiskCount APs with critical WPS risk
 * @property highRiskCount APs with high WPS risk
 * @property pinSupportedCount APs supporting PIN method
 * @property overallRisk Overall WPS risk level
 * @property analyses Individual AP analyses
 */
data class WpsNetworkAnalysis(
    val totalApCount: Int,
    val wpsEnabledCount: Int,
    val criticalRiskCount: Int,
    val highRiskCount: Int,
    val pinSupportedCount: Int,
    val overallRisk: RiskLevel,
    val analyses: List<WpsAnalysisResult>,
) {
    /**
     * Percentage of APs with WPS enabled
     */
    val wpsEnabledPercentage: Double
        get() = wpsEnabledCount.toDouble() / totalApCount * 100.0

    /**
     * Percentage of APs with critical/high WPS risk
     */
    val highRiskPercentage: Double
        get() = (criticalRiskCount + highRiskCount).toDouble() / totalApCount * 100.0

    /**
     * Summary of network-wide WPS status
     */
    val summary: String
        get() =
            buildString {
                appendLine("WPS Network Analysis:")
                appendLine("  Total APs: $totalApCount")
                appendLine("  WPS Enabled: $wpsEnabledCount (${wpsEnabledPercentage.toInt()}%)")
                if (criticalRiskCount > 0) {
                    appendLine("  ⚠️ Critical Risk: $criticalRiskCount APs")
                }
                if (highRiskCount > 0) {
                    appendLine("  High Risk: $highRiskCount APs")
                }
                if (pinSupportedCount > 0) {
                    appendLine("  PIN Method: $pinSupportedCount APs (VULNERABLE)")
                }
                appendLine("  Overall Risk: $overallRisk")
            }
}

/**
 * Specific WPS vulnerability
 *
 * @property type Vulnerability type
 * @property severity Severity level
 * @property description Short description
 * @property details Technical details
 * @property cveReferences CVE identifiers (if applicable)
 */
data class WpsVulnerability(
    val type: WpsVulnerabilityType,
    val severity: WpsVulnerabilitySeverity,
    val description: String,
    val details: String,
    val cveReferences: List<String>,
)

/**
 * Types of WPS vulnerabilities
 */
enum class WpsVulnerabilityType {
    /** PIN method brute force vulnerability */
    PIN_BRUTE_FORCE,

    /** Unlocked state allows unlimited attempts */
    UNLOCKED_STATE,

    /** WPS active when not needed */
    UNNECESSARY_EXPOSURE,

    /** Push-button timing attacks */
    PUSH_BUTTON_TIMING,
}

/**
 * WPS vulnerability severity levels
 */
enum class WpsVulnerabilitySeverity {
    /** Low severity */
    LOW,

    /** Medium severity */
    MEDIUM,

    /** High severity */
    HIGH,

    /** Critical severity */
    CRITICAL,
}
