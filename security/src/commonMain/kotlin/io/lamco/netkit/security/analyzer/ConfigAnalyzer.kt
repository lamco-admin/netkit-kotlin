package io.lamco.netkit.security.analyzer

import io.lamco.netkit.model.topology.SecurityFingerprint
import io.lamco.netkit.security.model.ConfigurationIssue
import io.lamco.netkit.security.model.ConfigurationReport

/**
 * Configuration consistency analyzer for multi-AP networks
 *
 * Analyzes configuration across multiple APs broadcasting the same SSID to identify:
 * - Security configuration inconsistencies
 * - Roaming capability mismatches
 * - Channel planning issues
 * - Network-wide quality problems
 *
 * Consistency is Critical For:
 * - Client roaming behavior (802.11k/v/r)
 * - Security policy enforcement
 * - User experience (no connection drops)
 * - Enterprise compliance
 *
 * Best Practices:
 * - All APs in same SSID should have identical security configuration
 * - Same authentication type across all APs
 * - Same cipher suites across all APs
 * - Same PMF setting across all APs
 * - Consistent roaming feature support
 *
 * References:
 * - IEEE 802.11-2020 ESS (Extended Service Set) requirements
 * - Wi-Fi Alliance certification requirements
 * - Enterprise WLAN deployment best practices
 *
 * @see SecurityFingerprint
 * @see ConfigurationReport
 */
class ConfigAnalyzer {
    /**
     * Analyze configuration consistency across APs in a network
     *
     * @param ssid Network name
     * @param apConfigurations List of (BSSID, SecurityFingerprint) pairs
     * @param roamingCapabilities List of (BSSID, has802.11k/v/r) pairs
     * @return Configuration consistency analysis
     */
    fun analyze(
        ssid: String,
        apConfigurations: List<Pair<String, SecurityFingerprint>>,
        roamingCapabilities: List<Pair<String, Boolean>>,
    ): ConfigConsistencyResult {
        require(apConfigurations.isNotEmpty()) { "At least one AP configuration required" }

        val apCount = apConfigurations.size

        val securityConsistency = analyzeSecurityConsistency(apConfigurations)

        val roamingConsistency = analyzeRoamingConsistency(roamingCapabilities)

        val issues =
            identifyConfigurationIssues(
                ssid,
                apCount,
                securityConsistency,
                roamingConsistency,
            )

        val overallScore = calculateConsistencyScore(securityConsistency, roamingConsistency)

        return ConfigConsistencyResult(
            ssid = ssid,
            apCount = apCount,
            securityConsistency = securityConsistency,
            roamingConsistency = roamingConsistency,
            issues = issues,
            overallConsistencyScore = overallScore,
        )
    }

    /**
     * Analyze security configuration consistency
     *
     * @param apConfigurations List of (BSSID, SecurityFingerprint) pairs
     * @return Security consistency analysis
     */
    private fun analyzeSecurityConsistency(apConfigurations: List<Pair<String, SecurityFingerprint>>): SecurityConsistencyResult {
        val fingerprints = apConfigurations.map { it.second }
        val uniqueFingerprints = fingerprints.distinct()

        val isConsistent = uniqueFingerprints.size == 1

        val differences =
            if (!isConsistent) {
                identifySecurityDifferences(fingerprints)
            } else {
                emptyList()
            }

        val score =
            if (isConsistent) {
                1.0
            } else {
                // Penalty based on number of unique configurations
                1.0 - (uniqueFingerprints.size - 1).toDouble() / apConfigurations.size
            }

        return SecurityConsistencyResult(
            isConsistent = isConsistent,
            uniqueConfigCount = uniqueFingerprints.size,
            differences = differences,
            consistencyScore = score.coerceIn(0.0, 1.0),
        )
    }

    /**
     * Identify security configuration differences
     *
     * @param fingerprints List of security fingerprints
     * @return List of differences
     */
    private fun identifySecurityDifferences(fingerprints: List<SecurityFingerprint>): List<ConfigDifference> {
        val differences = mutableListOf<ConfigDifference>()

        val uniqueAuthTypes = fingerprints.map { it.authType }.distinct()
        if (uniqueAuthTypes.size > 1) {
            differences.add(
                ConfigDifference(
                    category = DifferenceCategory.AUTHENTICATION,
                    description = "${uniqueAuthTypes.size} different authentication types",
                    values = uniqueAuthTypes.map { it.displayName },
                ),
            )
        }

        val allCiphers = fingerprints.flatMap { it.cipherSet }.distinct()
        val cipherVariations = fingerprints.map { it.cipherSet }.distinct()
        if (cipherVariations.size > 1) {
            differences.add(
                ConfigDifference(
                    category = DifferenceCategory.CIPHERS,
                    description = "${cipherVariations.size} different cipher configurations",
                    values =
                        cipherVariations.map { ciphers ->
                            ciphers.joinToString(", ") { it.displayName }
                        },
                ),
            )
        }

        val uniquePmfSettings = fingerprints.map { it.pmfRequired }.distinct()
        if (uniquePmfSettings.size > 1) {
            differences.add(
                ConfigDifference(
                    category = DifferenceCategory.PMF,
                    description = "Inconsistent PMF (Protected Management Frames) settings",
                    values = uniquePmfSettings.map { if (it) "Required" else "Optional/Disabled" },
                ),
            )
        }

        val uniqueTransitionModes = fingerprints.map { it.transitionMode }.distinct()
        if (uniqueTransitionModes.size > 1) {
            differences.add(
                ConfigDifference(
                    category = DifferenceCategory.TRANSITION_MODE,
                    description = "Inconsistent transition modes",
                    values = uniqueTransitionModes.map { it?.displayName ?: "None" },
                ),
            )
        }

        return differences
    }

    /**
     * Analyze roaming capability consistency
     *
     * @param roamingCapabilities List of (BSSID, hasRoaming) pairs
     * @return Roaming consistency analysis
     */
    private fun analyzeRoamingConsistency(roamingCapabilities: List<Pair<String, Boolean>>): RoamingConsistencyResult {
        if (roamingCapabilities.isEmpty()) {
            return RoamingConsistencyResult(
                isConsistent = true,
                roamingEnabledCount = 0,
                roamingDisabledCount = 0,
                consistencyScore = 1.0,
            )
        }

        val roamingEnabled = roamingCapabilities.count { it.second }
        val roamingDisabled = roamingCapabilities.count { !it.second }

        val isConsistent =
            roamingEnabled == roamingCapabilities.size ||
                roamingDisabled == roamingCapabilities.size

        val score = roamingEnabled.toDouble() / roamingCapabilities.size

        return RoamingConsistencyResult(
            isConsistent = isConsistent,
            roamingEnabledCount = roamingEnabled,
            roamingDisabledCount = roamingDisabled,
            consistencyScore = score,
        )
    }

    /**
     * Identify configuration issues
     *
     * @param ssid Network name
     * @param apCount Number of APs
     * @param securityConsistency Security consistency result
     * @param roamingConsistency Roaming consistency result
     * @return List of configuration issues
     */
    private fun identifyConfigurationIssues(
        ssid: String,
        apCount: Int,
        securityConsistency: SecurityConsistencyResult,
        roamingConsistency: RoamingConsistencyResult,
    ): List<ConfigurationIssue> {
        val issues = mutableListOf<ConfigurationIssue>()

        if (!securityConsistency.isConsistent) {
            issues.add(
                ConfigurationIssue.InconsistentSecurity(
                    ssid = ssid,
                    uniqueConfigCount = securityConsistency.uniqueConfigCount,
                ),
            )
        }

        if (apCount > 1 && !roamingConsistency.isConsistent) {
            issues.add(
                ConfigurationIssue.MissingRoamingFeatures(
                    ssid = ssid,
                    apCount = apCount,
                    roamingCoverage = roamingConsistency.consistencyScore,
                ),
            )
        }

        return issues
    }

    /**
     * Calculate overall consistency score
     *
     * @param securityConsistency Security consistency result
     * @param roamingConsistency Roaming consistency result
     * @return Overall consistency score (0.0-1.0)
     */
    private fun calculateConsistencyScore(
        securityConsistency: SecurityConsistencyResult,
        roamingConsistency: RoamingConsistencyResult,
    ): Double {
        // Weight security more heavily than roaming
        val securityWeight = 0.7
        val roamingWeight = 0.3

        return (securityConsistency.consistencyScore * securityWeight) +
            (roamingConsistency.consistencyScore * roamingWeight)
    }
}

/**
 * Configuration consistency analysis result
 *
 * @property ssid Network name
 * @property apCount Number of APs
 * @property securityConsistency Security configuration consistency
 * @property roamingConsistency Roaming capability consistency
 * @property issues Configuration issues detected
 * @property overallConsistencyScore Overall consistency (0.0-1.0)
 */
data class ConfigConsistencyResult(
    val ssid: String,
    val apCount: Int,
    val securityConsistency: SecurityConsistencyResult,
    val roamingConsistency: RoamingConsistencyResult,
    val issues: List<ConfigurationIssue>,
    val overallConsistencyScore: Double,
) {
    /**
     * Whether configuration is fully consistent
     */
    val isFullyConsistent: Boolean
        get() = securityConsistency.isConsistent && roamingConsistency.isConsistent

    /**
     * Consistency level
     */
    val consistencyLevel: ConsistencyLevel
        get() =
            when {
                overallConsistencyScore >= 0.95 -> ConsistencyLevel.EXCELLENT
                overallConsistencyScore >= 0.80 -> ConsistencyLevel.GOOD
                overallConsistencyScore >= 0.60 -> ConsistencyLevel.FAIR
                else -> ConsistencyLevel.POOR
            }

    /**
     * Summary of consistency analysis
     */
    val summary: String
        get() =
            buildString {
                append("$ssid ($apCount APs): ")
                append("Consistency $consistencyLevel")
                if (!isFullyConsistent) {
                    append(" - ${issues.size} issue(s)")
                }
            }
}

/**
 * Security consistency analysis result
 *
 * @property isConsistent Whether all APs have identical security configuration
 * @property uniqueConfigCount Number of unique security configurations
 * @property differences List of configuration differences
 * @property consistencyScore Consistency score (0.0-1.0)
 */
data class SecurityConsistencyResult(
    val isConsistent: Boolean,
    val uniqueConfigCount: Int,
    val differences: List<ConfigDifference>,
    val consistencyScore: Double,
)

/**
 * Roaming capability consistency analysis result
 *
 * @property isConsistent Whether roaming capabilities are consistent
 * @property roamingEnabledCount APs with roaming features
 * @property roamingDisabledCount APs without roaming features
 * @property consistencyScore Consistency score (0.0-1.0)
 */
data class RoamingConsistencyResult(
    val isConsistent: Boolean,
    val roamingEnabledCount: Int,
    val roamingDisabledCount: Int,
    val consistencyScore: Double,
)

/**
 * Configuration difference
 *
 * @property category Difference category
 * @property description Description of the difference
 * @property values Different values detected
 */
data class ConfigDifference(
    val category: DifferenceCategory,
    val description: String,
    val values: List<String>,
)

/**
 * Configuration difference categories
 */
enum class DifferenceCategory {
    /** Authentication type differences */
    AUTHENTICATION,

    /** Cipher suite differences */
    CIPHERS,

    /** PMF setting differences */
    PMF,

    /** Transition mode differences */
    TRANSITION_MODE,

    /** Roaming capability differences */
    ROAMING,
}

/**
 * Configuration consistency levels
 * Ordered from worst to best for ordinal comparison (POOR < FAIR < GOOD < EXCELLENT)
 */
enum class ConsistencyLevel {
    /** Poor consistency (<60%) */
    POOR,

    /** Fair consistency (60-79%) */
    FAIR,

    /** Good consistency (80-94%) */
    GOOD,

    /** Excellent consistency (95%+) */
    EXCELLENT,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent - Fully consistent"
                GOOD -> "Good - Minor variations"
                FAIR -> "Fair - Some inconsistencies"
                POOR -> "Poor - Major inconsistencies"
            }
}
