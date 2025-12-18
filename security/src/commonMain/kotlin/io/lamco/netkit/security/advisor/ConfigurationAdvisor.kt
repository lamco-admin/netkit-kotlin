package io.lamco.netkit.security.advisor

import io.lamco.netkit.security.analyzer.BssSecurityAnalysis
import io.lamco.netkit.security.analyzer.ConfigAnalyzer
import io.lamco.netkit.security.analyzer.ConfigConsistencyResult

/**
 * Configuration optimization advisor for multi-AP networks
 *
 * The ConfigurationAdvisor analyzes WiFi network configuration and provides
 * recommendations for optimizing multi-AP deployments, focusing on:
 * - Security configuration consistency
 * - Roaming optimization (802.11k/r/v)
 * - Channel planning
 * - AP placement and coverage
 *
 * Use Cases:
 * - Enterprise WLAN optimization
 * - Multi-AP home network setup
 * - Network performance troubleshooting
 * - Deployment best practices validation
 *
 * Best Practices:
 * - Identical security configuration across all APs in same SSID
 * - Consistent roaming feature support
 * - Non-overlapping channel assignments
 * - Appropriate AP density for coverage area
 *
 * Example Usage:
 * ```kotlin
 * val advisor = ConfigurationAdvisor()
 * val advice = advisor.analyze(ssid, bssAnalyses)
 * advice.recommendations.forEach { println(it.description) }
 * ```
 *
 * @property configAnalyzer Configuration consistency analyzer
 *
 * @see ConfigurationAdvice
 * @see ConfigurationRecommendation
 */
class ConfigurationAdvisor(
    private val configAnalyzer: ConfigAnalyzer = ConfigAnalyzer(),
) {
    /**
     * Analyze network configuration and provide optimization advice
     *
     * @param ssid Network SSID
     * @param bssAnalyses List of BSS security analyses for this SSID
     * @return Configuration optimization advice
     */
    fun analyze(
        ssid: String,
        bssAnalyses: List<BssSecurityAnalysis>,
    ): ConfigurationAdvice {
        require(bssAnalyses.isNotEmpty()) { "At least one BSS analysis required" }

        val recommendations = mutableListOf<ConfigurationRecommendation>()

        if (bssAnalyses.size == 1) {
            recommendations.addAll(analyzeSingleAP(bssAnalyses.first()))
        } else {
            recommendations.addAll(analyzeMultiAP(ssid, bssAnalyses))
        }

        return ConfigurationAdvice(
            ssid = ssid,
            apCount = bssAnalyses.size,
            recommendations = recommendations.sortedByDescending { it.priority.ordinal },
        )
    }

    /**
     * Analyze single AP configuration
     *
     * @param bss BSS security analysis
     * @return List of recommendations
     */
    private fun analyzeSingleAP(bss: BssSecurityAnalysis): List<ConfigurationRecommendation> {
        val recommendations = mutableListOf<ConfigurationRecommendation>()

        recommendations.add(
            ConfigurationRecommendation(
                category = RecommendationCategory.CAPACITY,
                priority = RecommendationPriority.INFO,
                title = "Single AP Network",
                description = "Network operates with a single access point",
                recommendation = "Consider adding additional APs if coverage area is large or client density increases",
                rationale = "Multiple APs improve coverage, capacity, and reliability",
                effort = EffortLevel.HIGH,
            ),
        )

        // Roaming features less critical for single AP (no handoffs to other APs)
        if (!bss.roamingFeaturesSupported) {
            recommendations.add(
                ConfigurationRecommendation(
                    category = RecommendationCategory.ROAMING,
                    priority = RecommendationPriority.LOW,
                    title = "Roaming Features Not Supported",
                    description = "802.11k/r/v fast roaming not available",
                    recommendation = "If planning to add more APs, ensure they support 802.11k/r/v",
                    rationale = "Fast roaming features are essential for multi-AP deployments",
                    effort = EffortLevel.HIGH,
                ),
            )
        }

        return recommendations
    }

    /**
     * Analyze multi-AP configuration
     *
     * @param ssid Network SSID
     * @param bssAnalyses List of BSS analyses
     * @return List of recommendations
     */
    private fun analyzeMultiAP(
        ssid: String,
        bssAnalyses: List<BssSecurityAnalysis>,
    ): List<ConfigurationRecommendation> {
        val recommendations = mutableListOf<ConfigurationRecommendation>()

        recommendations.addAll(analyzeSecurityConsistency(ssid, bssAnalyses))

        recommendations.addAll(analyzeRoamingConfiguration(ssid, bssAnalyses))

        recommendations.addAll(analyzeAPDensity(ssid, bssAnalyses))

        return recommendations
    }

    /**
     * Analyze security configuration consistency
     *
     * @param ssid Network SSID
     * @param bssAnalyses List of BSS analyses
     * @return List of recommendations
     */
    private fun analyzeSecurityConsistency(
        ssid: String,
        bssAnalyses: List<BssSecurityAnalysis>,
    ): List<ConfigurationRecommendation> {
        val recommendations = mutableListOf<ConfigurationRecommendation>()

        val uniqueAuthTypes = bssAnalyses.map { it.securityScore.authType }.distinct()
        if (uniqueAuthTypes.size > 1) {
            recommendations.add(
                ConfigurationRecommendation(
                    category = RecommendationCategory.SECURITY,
                    priority = RecommendationPriority.HIGH,
                    title = "Inconsistent Authentication Types",
                    description = "APs in '$ssid' use ${uniqueAuthTypes.size} different authentication types: ${uniqueAuthTypes.joinToString {
                        it.displayName
                    }}",
                    recommendation = "Configure all APs to use the same authentication type",
                    rationale = "Inconsistent authentication causes roaming failures and security gaps",
                    effort = EffortLevel.MEDIUM,
                ),
            )
        }

        val uniqueSecurityLevels = bssAnalyses.map { it.securityScore.securityLevel }.distinct()
        if (uniqueSecurityLevels.size > 1) {
            recommendations.add(
                ConfigurationRecommendation(
                    category = RecommendationCategory.SECURITY,
                    priority = RecommendationPriority.MEDIUM,
                    title = "Inconsistent Security Levels",
                    description = "APs have different security levels: ${uniqueSecurityLevels.joinToString()}",
                    recommendation = "Ensure all APs use identical security configuration",
                    rationale = "Security level inconsistency creates weak points in the network",
                    effort = EffortLevel.MEDIUM,
                ),
            )
        }

        val pmfEnabled = bssAnalyses.filter { it.pmfEnabled }.size
        if (pmfEnabled > 0 && pmfEnabled < bssAnalyses.size) {
            recommendations.add(
                ConfigurationRecommendation(
                    category = RecommendationCategory.SECURITY,
                    priority = RecommendationPriority.MEDIUM,
                    title = "Inconsistent PMF Configuration",
                    description = "PMF enabled on $pmfEnabled of ${bssAnalyses.size} APs",
                    recommendation = "Enable PMF on all APs or disable on all APs",
                    rationale = "PMF inconsistency causes connection issues during roaming",
                    effort = EffortLevel.LOW,
                ),
            )
        }

        return recommendations
    }

    /**
     * Analyze roaming configuration
     *
     * @param ssid Network SSID
     * @param bssAnalyses List of BSS analyses
     * @return List of recommendations
     */
    private fun analyzeRoamingConfiguration(
        ssid: String,
        bssAnalyses: List<BssSecurityAnalysis>,
    ): List<ConfigurationRecommendation> {
        val recommendations = mutableListOf<ConfigurationRecommendation>()

        val roamingSupported = bssAnalyses.filter { it.roamingFeaturesSupported }.size
        val roamingPercentage = (roamingSupported.toDouble() / bssAnalyses.size) * 100

        when {
            roamingSupported == 0 -> {
                recommendations.add(
                    ConfigurationRecommendation(
                        category = RecommendationCategory.ROAMING,
                        priority = RecommendationPriority.HIGH,
                        title = "No Fast Roaming Support",
                        description = "None of the ${bssAnalyses.size} APs support 802.11k/r/v fast roaming",
                        recommendation = "Upgrade APs to models supporting 802.11k/r/v",
                        rationale = "Fast roaming (802.11k/r/v) is essential for seamless handoffs in multi-AP networks",
                        effort = EffortLevel.HIGH,
                    ),
                )
            }
            roamingSupported < bssAnalyses.size -> {
                recommendations.add(
                    ConfigurationRecommendation(
                        category = RecommendationCategory.ROAMING,
                        priority = RecommendationPriority.MEDIUM,
                        title = "Partial Fast Roaming Support",
                        description = "Only $roamingSupported of ${bssAnalyses.size} APs (${String.format(
                            "%.0f%%",
                            roamingPercentage,
                        )}) support fast roaming",
                        recommendation = "Upgrade remaining APs to support 802.11k/r/v or enable these features",
                        rationale = "All APs should support fast roaming for optimal client experience",
                        effort = EffortLevel.MEDIUM,
                    ),
                )
            }
            else -> {
                // Full roaming support provides optimal handoff experience
                recommendations.add(
                    ConfigurationRecommendation(
                        category = RecommendationCategory.ROAMING,
                        priority = RecommendationPriority.INFO,
                        title = "Excellent Roaming Configuration",
                        description = "All ${bssAnalyses.size} APs support 802.11k/r/v fast roaming",
                        recommendation = "Configuration is optimal for client roaming",
                        rationale = "Consistent fast roaming support provides best user experience",
                        effort = EffortLevel.LOW,
                    ),
                )
            }
        }

        return recommendations
    }

    /**
     * Analyze AP density and coverage
     *
     * @param ssid Network SSID
     * @param bssAnalyses List of BSS analyses
     * @return List of recommendations
     */
    private fun analyzeAPDensity(
        ssid: String,
        bssAnalyses: List<BssSecurityAnalysis>,
    ): List<ConfigurationRecommendation> {
        val recommendations = mutableListOf<ConfigurationRecommendation>()

        when {
            bssAnalyses.size in 2..3 -> {
                recommendations.add(
                    ConfigurationRecommendation(
                        category = RecommendationCategory.CAPACITY,
                        priority = RecommendationPriority.INFO,
                        title = "Small Multi-AP Deployment",
                        description = "Network has ${bssAnalyses.size} access points",
                        recommendation = "Ensure APs are placed for optimal coverage without excessive overlap",
                        rationale = "Proper AP placement minimizes co-channel interference",
                        effort = EffortLevel.MEDIUM,
                    ),
                )
            }
            bssAnalyses.size in 4..10 -> {
                recommendations.add(
                    ConfigurationRecommendation(
                        category = RecommendationCategory.CAPACITY,
                        priority = RecommendationPriority.INFO,
                        title = "Medium Multi-AP Deployment",
                        description = "Network has ${bssAnalyses.size} access points",
                        recommendation = "Use professional WiFi planning tools to optimize channel assignments and power levels",
                        rationale = "Medium-sized deployments benefit from professional planning",
                        effort = EffortLevel.HIGH,
                    ),
                )
            }
            bssAnalyses.size > 10 -> {
                recommendations.add(
                    ConfigurationRecommendation(
                        category = RecommendationCategory.CAPACITY,
                        priority = RecommendationPriority.MEDIUM,
                        title = "Large Multi-AP Deployment",
                        description = "Network has ${bssAnalyses.size} access points",
                        recommendation = "Consider enterprise WLAN controller for centralized management and optimization",
                        rationale = "Large deployments require professional management for optimal performance",
                        effort = EffortLevel.HIGH,
                    ),
                )
            }
        }

        return recommendations
    }
}

/**
 * Configuration advice for a network
 *
 * @property ssid Network SSID
 * @property apCount Number of APs in the network
 * @property recommendations List of configuration recommendations sorted by priority
 */
data class ConfigurationAdvice(
    val ssid: String,
    val apCount: Int,
    val recommendations: List<ConfigurationRecommendation>,
) {
    init {
        require(apCount > 0) { "AP count must be positive" }
    }

    /**
     * Recommendations by priority
     */
    val recommendationsByPriority: Map<RecommendationPriority, List<ConfigurationRecommendation>>
        get() = recommendations.groupBy { it.priority }

    /**
     * Recommendations by category
     */
    val recommendationsByCategory: Map<RecommendationCategory, List<ConfigurationRecommendation>>
        get() = recommendations.groupBy { it.category }

    /**
     * High priority recommendations
     */
    val highPriorityRecommendations: List<ConfigurationRecommendation>
        get() = recommendations.filter { it.priority == RecommendationPriority.HIGH }

    /**
     * Quick summary
     */
    val summary: String
        get() =
            buildString {
                appendLine("Configuration Analysis for '$ssid':")
                appendLine("Access Points: $apCount")
                appendLine("Recommendations: ${recommendations.size}")
                if (recommendations.isNotEmpty()) {
                    appendLine()
                    appendLine("Top Recommendation:")
                    appendLine("  ${recommendations.first().title}")
                }
            }
}

/**
 * Configuration recommendation
 *
 * @property category Recommendation category
 * @property priority Recommendation priority
 * @property title Short title
 * @property description Detailed description of the issue
 * @property recommendation Specific recommendation
 * @property rationale Explanation of why this matters
 * @property effort Estimated effort to implement
 */
data class ConfigurationRecommendation(
    val category: RecommendationCategory,
    val priority: RecommendationPriority,
    val title: String,
    val description: String,
    val recommendation: String,
    val rationale: String,
    val effort: EffortLevel,
) {
    /**
     * Recommendation summary
     */
    val summary: String
        get() = "[$priority] $title"
}

/**
 * Recommendation categories
 */
enum class RecommendationCategory {
    /** Security-related recommendation */
    SECURITY,

    /** Roaming optimization */
    ROAMING,

    /** Capacity and coverage */
    CAPACITY,

    /** Channel planning */
    CHANNEL,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                SECURITY -> "Security Configuration"
                ROAMING -> "Roaming Optimization"
                CAPACITY -> "Capacity & Coverage"
                CHANNEL -> "Channel Planning"
            }
}

/**
 * Recommendation priority levels
 */
enum class RecommendationPriority {
    /** Critical - requires immediate attention */
    CRITICAL,

    /** High - should be addressed soon */
    HIGH,

    /** Medium - recommended improvement */
    MEDIUM,

    /** Low - optional optimization */
    LOW,

    /** Info - informational only */
    INFO,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                CRITICAL -> "Critical"
                HIGH -> "High"
                MEDIUM -> "Medium"
                LOW -> "Low"
                INFO -> "Informational"
            }
}
