package io.lamco.netkit.security.model

/**
 * Per-SSID configuration quality report
 *
 * Provides comprehensive analysis of WiFi network configuration including:
 * - Security configuration consistency
 * - Roaming capability assessment
 * - Channel planning quality
 * - Multi-AP coordination
 * - Overall network health
 *
 * Based on IEEE 802.11-2020 best practices, Wi-Fi Alliance certification requirements,
 * enterprise WLAN deployment guidelines, and NIST wireless security standards.
 *
 * Analyzes all APs broadcasting the same SSID to assess configuration consistency
 * and overall network quality.
 *
 * @property ssid Network name
 * @property apCount Number of access points in this network
 * @property securityConsistencyScore Consistency of security config across APs (0.0-1.0)
 * @property roamingCapabilityScore Quality of roaming support (0.0-1.0)
 * @property channelPlanScore Channel planning quality (0.0-1.0)
 * @property overallQualityScore Composite configuration quality (0.0-1.0)
 * @property issues List of configuration problems detected
 */
data class ConfigurationReport(
    val ssid: String,
    val apCount: Int,
    val securityConsistencyScore: Double,
    val roamingCapabilityScore: Double,
    val channelPlanScore: Double,
    val overallQualityScore: Double,
    val issues: List<ConfigurationIssue>,
) {
    init {
        require(ssid.isNotBlank()) { "SSID must not be blank" }
        require(apCount > 0) { "AP count must be positive, got $apCount" }
        require(securityConsistencyScore in 0.0..1.0) {
            "Security consistency score must be 0.0-1.0, got $securityConsistencyScore"
        }
        require(roamingCapabilityScore in 0.0..1.0) {
            "Roaming capability score must be 0.0-1.0, got $roamingCapabilityScore"
        }
        require(channelPlanScore in 0.0..1.0) {
            "Channel plan score must be 0.0-1.0, got $channelPlanScore"
        }
        require(overallQualityScore in 0.0..1.0) {
            "Overall quality score must be 0.0-1.0, got $overallQualityScore"
        }
    }

    /**
     * Configuration quality level
     */
    val qualityLevel: ConfigurationQualityLevel
        get() =
            when {
                overallQualityScore >= 0.90 -> ConfigurationQualityLevel.EXCELLENT
                overallQualityScore >= 0.75 -> ConfigurationQualityLevel.GOOD
                overallQualityScore >= 0.60 -> ConfigurationQualityLevel.FAIR
                overallQualityScore >= 0.40 -> ConfigurationQualityLevel.POOR
                else -> ConfigurationQualityLevel.CRITICAL
            }

    /**
     * Whether this is a multi-AP network
     */
    val isMultiApNetwork: Boolean
        get() = apCount > 1

    /**
     * Whether this is a single AP network
     */
    val isSingleAp: Boolean
        get() = apCount == 1

    /**
     * Count of issues by severity
     */
    val criticalIssueCount: Int
        get() = issues.count { it.severity == ConfigurationIssueSeverity.CRITICAL }

    val highIssueCount: Int
        get() = issues.count { it.severity == ConfigurationIssueSeverity.HIGH }

    val mediumIssueCount: Int
        get() = issues.count { it.severity == ConfigurationIssueSeverity.MEDIUM }

    val lowIssueCount: Int
        get() = issues.count { it.severity == ConfigurationIssueSeverity.LOW }

    /**
     * Whether there are critical configuration issues
     */
    val hasCriticalIssues: Boolean
        get() = criticalIssueCount > 0

    /**
     * Total issue count
     */
    val totalIssueCount: Int
        get() = issues.size

    /**
     * Primary concern (highest severity issue)
     */
    val primaryConcern: ConfigurationIssue?
        get() = issues.maxByOrNull { it.severity.ordinal }

    /**
     * Human-readable configuration summary
     */
    val configurationSummary: String
        get() =
            buildString {
                append("$qualityLevel: ")
                append(if (isMultiApNetwork) "$apCount APs" else "Single AP")
                if (hasCriticalIssues) {
                    append(" - $criticalIssueCount CRITICAL issue(s)")
                } else if (totalIssueCount > 0) {
                    append(" - $totalIssueCount issue(s)")
                } else {
                    append(" - Well configured")
                }
            }

    /**
     * Detailed quality report
     */
    val detailedReport: String
        get() =
            buildString {
                appendLine("=== Configuration Analysis for '$ssid' ===")
                appendLine("Network Type: ${if (isMultiApNetwork) "Multi-AP ($apCount APs)" else "Single AP"}")
                appendLine("Overall Quality: $qualityLevel (${(overallQualityScore * 100).toInt()}%)")
                appendLine()
                appendLine("Component Scores:")
                appendLine("  Security Consistency: ${(securityConsistencyScore * 100).toInt()}%")
                appendLine("  Roaming Capability:   ${(roamingCapabilityScore * 100).toInt()}%")
                appendLine("  Channel Planning:     ${(channelPlanScore * 100).toInt()}%")
                appendLine()

                if (issues.isNotEmpty()) {
                    appendLine("Configuration Issues (${issues.size}):")
                    issues.sortedByDescending { it.severity }.forEach { issue ->
                        appendLine("  [${issue.severity}] ${issue.shortDescription}")
                    }
                } else {
                    appendLine("No configuration issues detected.")
                }
            }

    companion object {
        /**
         * Calculate overall quality score from component scores
         *
         * Weighted combination:
         * - Security consistency: 40%
         * - Roaming capability: 30%
         * - Channel planning: 30%
         *
         * @param securityScore Security consistency (0.0-1.0)
         * @param roamingScore Roaming capability (0.0-1.0)
         * @param channelScore Channel planning quality (0.0-1.0)
         * @return Overall score 0.0-1.0
         */
        fun calculateOverallQuality(
            securityScore: Double,
            roamingScore: Double,
            channelScore: Double,
        ): Double {
            val weighted =
                (securityScore * 0.40) +
                    (roamingScore * 0.30) +
                    (channelScore * 0.30)

            return weighted.coerceIn(0.0, 1.0)
        }

        /**
         * Create configuration report for single-AP network
         *
         * @param ssid Network name
         * @param securityScore Security configuration score
         * @param channelScore Channel selection quality
         * @return ConfigurationReport
         */
        fun forSingleAp(
            ssid: String,
            securityScore: Double,
            channelScore: Double,
        ): ConfigurationReport {
            // Single AP gets max roaming score (N/A)
            val overallScore = calculateOverallQuality(securityScore, 1.0, channelScore)

            return ConfigurationReport(
                ssid = ssid,
                apCount = 1,
                securityConsistencyScore = securityScore,
                roamingCapabilityScore = 1.0, // N/A for single AP
                channelPlanScore = channelScore,
                overallQualityScore = overallScore,
                issues = emptyList(),
            )
        }
    }
}

/**
 * Configuration quality levels
 */
enum class ConfigurationQualityLevel {
    /** Excellent configuration (90%+) - Best practices followed */
    EXCELLENT,

    /** Good configuration (75-89%) - Minor improvements possible */
    GOOD,

    /** Fair configuration (60-74%) - Some issues present */
    FAIR,

    /** Poor configuration (40-59%) - Significant problems */
    POOR,

    /** Critical configuration (<40%) - Major issues requiring immediate attention */
    CRITICAL,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent - Best practices followed"
                GOOD -> "Good - Well configured"
                FAIR -> "Fair - Some improvements needed"
                POOR -> "Poor - Significant issues present"
                CRITICAL -> "Critical - Immediate attention required"
            }
}

/**
 * Configuration issue detected in network setup
 */
sealed class ConfigurationIssue {
    /**
     * Issue severity
     */
    abstract val severity: ConfigurationIssueSeverity

    /**
     * Short description
     */
    abstract val shortDescription: String

    /**
     * Detailed explanation
     */
    abstract val details: String

    /**
     * Recommended fix
     */
    abstract val recommendation: String

    /**
     * Inconsistent security configuration across APs
     *
     * Different APs in the same network should use identical security settings.
     *
     * @property ssid Network name
     * @property uniqueConfigCount Number of different security configurations
     */
    data class InconsistentSecurity(
        val ssid: String,
        val uniqueConfigCount: Int,
    ) : ConfigurationIssue() {
        override val severity = ConfigurationIssueSeverity.HIGH

        override val shortDescription =
            "Inconsistent security: $uniqueConfigCount different configs"

        override val details =
            "Network '$ssid' has $uniqueConfigCount different security configurations across its APs. " +
                "This can cause client confusion, connection failures, and security policy gaps. " +
                "Common causes: firmware version mismatch, manual misconfiguration, controller issues."

        override val recommendation =
            "Ensure all APs for '$ssid' use identical security settings: " +
                "same authentication type (WPA2/WPA3), same ciphers (CCMP/GCMP), same PMF setting. " +
                "Use a central controller or careful manual configuration."
    }

    /**
     * Missing roaming features on multi-AP network
     *
     * Networks with 2+ APs should implement 802.11k/v/r for optimal roaming.
     *
     * @property ssid Network name
     * @property apCount Number of APs
     * @property roamingCoverage Percentage of APs with roaming features (0.0-1.0)
     */
    data class MissingRoamingFeatures(
        val ssid: String,
        val apCount: Int,
        val roamingCoverage: Double,
    ) : ConfigurationIssue() {
        override val severity =
            when {
                roamingCoverage == 0.0 -> ConfigurationIssueSeverity.MEDIUM
                roamingCoverage < 0.5 -> ConfigurationIssueSeverity.LOW
                else -> ConfigurationIssueSeverity.INFO
            }

        override val shortDescription =
            "Limited roaming features: ${(roamingCoverage * 100).toInt()}% coverage"

        override val details =
            "Multi-AP network '$ssid' ($apCount APs) has limited 802.11k/v/r support. " +
                "Only ${(roamingCoverage * 100).toInt()}% of APs advertise fast roaming features. " +
                "This can cause sticky clients, connection drops during roaming, and poor VoIP/video quality."

        override val recommendation =
            "Enable 802.11k/v/r features on all APs: " +
                "- 802.11k: Radio Resource Management (neighbor reports) " +
                "- 802.11v: BSS Transition Management (roaming hints) " +
                "- 802.11r: Fast BSS Transition (quick handoffs)"
    }

    /**
     * Poor channel planning
     *
     * Channel selection impacts performance and interference.
     *
     * @property channelQuality Overall channel plan quality (0.0-1.0)
     * @property primaryIssue Main channel planning problem
     */
    data class PoorChannelPlanning(
        val channelQuality: Double,
        val primaryIssue: String,
    ) : ConfigurationIssue() {
        override val severity =
            when {
                channelQuality < 0.3 -> ConfigurationIssueSeverity.HIGH
                channelQuality < 0.5 -> ConfigurationIssueSeverity.MEDIUM
                else -> ConfigurationIssueSeverity.LOW
            }

        override val shortDescription =
            "Poor channel plan: ${(channelQuality * 100).toInt()}% quality"

        override val details =
            "Channel planning quality is ${(channelQuality * 100).toInt()}%. " +
                "Primary issue: $primaryIssue. " +
                "Poor channel planning causes co-channel interference, reduced throughput, and unreliable connections."

        override val recommendation =
            "Optimize channel selection: " +
                "- 2.4 GHz: Use non-overlapping channels (1, 6, 11 in US; 1, 5, 9, 13 in EU) " +
                "- 5 GHz: Use DFS channels if supported, avoid overlap " +
                "- 6 GHz: Optimal spacing for WiFi 6E " +
                "- Avoid 40 MHz channels in crowded 2.4 GHz band"
    }

    /**
     * Mixed WiFi generations creating performance bottleneck
     *
     * Legacy WiFi standards (11b/g) reduce overall network throughput.
     *
     * @property hasLegacy80211b Whether legacy 802.11b devices are present
     */
    data class MixedWiFiGenerations(
        val hasLegacy80211b: Boolean,
    ) : ConfigurationIssue() {
        override val severity =
            if (hasLegacy80211b) {
                ConfigurationIssueSeverity.MEDIUM
            } else {
                ConfigurationIssueSeverity.LOW
            }

        override val shortDescription =
            if (hasLegacy80211b) "Legacy 802.11b devices detected" else "Mixed WiFi generations"

        override val details =
            if (hasLegacy80211b) {
                "802.11b devices force network to use legacy protection mechanisms (RTS/CTS, CTS-to-self), " +
                    "reducing overall throughput by 50-80% for all clients. " +
                    "802.11b: 11 Mbps max, uses long preambles, deprecated in WiFi 6."
            } else {
                "Mix of WiFi 4/5/6/7 devices. While compatible, older devices may reduce aggregate throughput. " +
                    "Not critical but consider separate SSIDs for legacy devices in high-density environments."
            }

        override val recommendation =
            if (hasLegacy80211b) {
                "CRITICAL: Disable 802.11b support in router settings. " +
                    "Upgrade any 802.11b devices (20+ years old). " +
                    "Set minimum supported rate to 6 Mbps to exclude 802.11b."
            } else {
                "Consider creating separate SSIDs for newer devices to maximize performance. " +
                    "WiFi 6E (6 GHz) only supports WiFi 6E/7 devices by design."
            }
    }

    /**
     * Excessive AP overlap / too many APs
     *
     * Too many APs in close proximity cause co-channel interference.
     *
     * @property apCount Number of overlapping APs
     * @property averageOverlap Average overlap percentage
     */
    data class ExcessiveApOverlap(
        val apCount: Int,
        val averageOverlap: Double,
    ) : ConfigurationIssue() {
        override val severity =
            when {
                averageOverlap > 0.8 -> ConfigurationIssueSeverity.HIGH
                averageOverlap > 0.6 -> ConfigurationIssueSeverity.MEDIUM
                else -> ConfigurationIssueSeverity.LOW
            }

        override val shortDescription =
            "Excessive AP overlap: ${(averageOverlap * 100).toInt()}% average"

        override val details =
            "$apCount APs detected with ${(averageOverlap * 100).toInt()}% average coverage overlap. " +
                "Excessive overlap causes co-channel interference, hidden node problems, and roaming confusion. " +
                "Optimal overlap: 10-20% at cell edges (-67 dBm)."

        override val recommendation =
            "Reduce AP density or transmit power: " +
                "- Adjust power levels to achieve 10-20% overlap at edges " +
                "- Consider disabling redundant APs " +
                "- Use predictive site survey tools for optimal placement " +
                "- Enterprise: aim for 20% overlap at -67 dBm"
    }

    /**
     * Informational note about network configuration
     *
     * @property message Information message
     */
    data class InfoNote(
        val message: String,
    ) : ConfigurationIssue() {
        override val severity = ConfigurationIssueSeverity.INFO
        override val shortDescription = message
        override val details = message
        override val recommendation = "No action required - informational only"
    }
}

/**
 * Configuration issue severity levels
 */
enum class ConfigurationIssueSeverity {
    /** Informational only - no action required */
    INFO,

    /** Low severity - minor optimization opportunity */
    LOW,

    /** Medium severity - should be addressed */
    MEDIUM,

    /** High severity - should be fixed soon */
    HIGH,

    /** Critical severity - requires immediate attention */
    CRITICAL,
}
