package io.lamco.netkit.diagnostics.integration

import io.lamco.netkit.diagnostics.model.*

/**
 * Intelligent advisor providing actionable recommendations based on network diagnostics.
 *
 * DiagnosticAdvisor analyzes diagnostic results and provides:
 * - Prioritized list of issues with severity levels
 * - Specific, actionable recommendations for each issue
 * - Quick wins (easy improvements with high impact)
 * - Long-term optimization suggestions
 *
 * ## Recommendation Categories
 *
 * 1. **Critical Fixes**: Immediate actions required
 * 2. **Performance Improvements**: Optimize speed and reliability
 * 3. **Configuration Optimizations**: Better settings
 * 4. **Security Enhancements**: Improve network security
 * 5. **Monitoring**: What to watch going forward
 *
 * ## Usage Example
 *
 * ```kotlin
 * val advisor = DiagnosticAdvisor()
 * val recommendations = advisor.analyzeAndRecommend(
 *     diagnostics = activeDiagnostics,
 *     healthScore = networkHealthScore
 * )
 *
 * recommendations.critical.forEach { rec ->
 *     println("[CRITICAL] ${rec.issue}: ${rec.action}")
 * }
 * ```
 *
 * @see Recommendation
 * @see RecommendationSet
 */
class DiagnosticAdvisor {
    /**
     * Analyze diagnostics and generate recommendations.
     *
     * @param diagnostics Active diagnostic test results
     * @param healthScore Network health score (optional)
     * @return Set of categorized recommendations
     */
    fun analyzeAndRecommend(
        diagnostics: ActiveDiagnostics,
        healthScore: NetworkHealthScore? = null,
    ): RecommendationSet {
        val critical = mutableListOf<Recommendation>()
        val performance = mutableListOf<Recommendation>()
        val configuration = mutableListOf<Recommendation>()
        val security = mutableListOf<Recommendation>()
        val monitoring = mutableListOf<Recommendation>()

        analyzeConnectivity(diagnostics, critical, performance, configuration)
        analyzeRouting(diagnostics, critical, performance, configuration)
        analyzeThroughput(diagnostics, critical, performance, configuration)
        analyzeDns(diagnostics, critical, performance, configuration)

        healthScore?.let {
            analyzeOverallHealth(it, critical, performance, configuration, monitoring)
        }

        val quickWins =
            identifyQuickWins(
                critical + performance + configuration + security,
            )

        return RecommendationSet(
            critical = critical,
            performance = performance,
            configuration = configuration,
            security = security,
            monitoring = monitoring,
            quickWins = quickWins,
        )
    }

    /**
     * Analyze connectivity and generate recommendations.
     */
    private fun analyzeConnectivity(
        diagnostics: ActiveDiagnostics,
        critical: MutableList<Recommendation>,
        performance: MutableList<Recommendation>,
        configuration: MutableList<Recommendation>,
    ) {
        if (diagnostics.worstPacketLoss >= 10.0) {
            critical.add(
                Recommendation(
                    category = RecommendationCategory.CRITICAL,
                    issue = "High packet loss (${diagnostics.worstPacketLoss}%)",
                    action = "Check WiFi signal strength and move closer to access point. Verify no physical obstructions or interference sources nearby.",
                    impact = RecommendationImpact.HIGH,
                    effort = RecommendationEffort.LOW,
                    technicalDetails = "Packet loss above 10% severely impacts all network activities, especially real-time applications like VoIP and gaming.",
                ),
            )
        } else if (diagnostics.worstPacketLoss >= 5.0) {
            performance.add(
                Recommendation(
                    category = RecommendationCategory.PERFORMANCE,
                    issue = "Moderate packet loss (${diagnostics.worstPacketLoss}%)",
                    action = "Optimize WiFi channel selection to reduce interference. Consider using 5GHz band if available.",
                    impact = RecommendationImpact.MEDIUM,
                    effort = RecommendationEffort.LOW,
                ),
            )
        }

        val latency = diagnostics.bestLatency
        if (latency != null) {
            if (latency.inWholeMilliseconds >= 100) {
                performance.add(
                    Recommendation(
                        category = RecommendationCategory.PERFORMANCE,
                        issue = "High latency (${latency.inWholeMilliseconds}ms)",
                        action = "Check for network congestion. Consider QoS settings to prioritize traffic. Verify ISP connection speed.",
                        impact = RecommendationImpact.MEDIUM,
                        effort = RecommendationEffort.MEDIUM,
                        technicalDetails = "Latency above 100ms impacts interactive applications. For gaming, aim for <50ms. For VoIP, aim for <150ms.",
                    ),
                )
            }
        }
    }

    /**
     * Analyze routing and generate recommendations.
     */
    private fun analyzeRouting(
        diagnostics: ActiveDiagnostics,
        critical: MutableList<Recommendation>,
        performance: MutableList<Recommendation>,
        configuration: MutableList<Recommendation>,
    ) {
        if (!diagnostics.hasHealthyGateway && diagnostics.tracerouteResults.isNotEmpty()) {
            critical.add(
                Recommendation(
                    category = RecommendationCategory.CRITICAL,
                    issue = "Gateway not responding or has high latency",
                    action = "Restart your router. If problem persists, check router settings or contact ISP.",
                    impact = RecommendationImpact.HIGH,
                    effort = RecommendationEffort.LOW,
                    technicalDetails = "First hop (gateway/router) should respond with <10ms latency. High gateway latency affects all internet traffic.",
                ),
            )
        }

        val hopCount = diagnostics.bestHopCount
        if (hopCount != null && hopCount > 15) {
            configuration.add(
                Recommendation(
                    category = RecommendationCategory.CONFIGURATION,
                    issue = "Excessive network hops ($hopCount)",
                    action = "This may indicate suboptimal ISP routing. Contact ISP if speeds are affected.",
                    impact = RecommendationImpact.LOW,
                    effort = RecommendationEffort.HIGH,
                    technicalDetails = "Typical internet routes are 8-12 hops. More hops can increase latency and failure points.",
                ),
            )
        }
    }

    /**
     * Analyze throughput and generate recommendations.
     */
    private fun analyzeThroughput(
        diagnostics: ActiveDiagnostics,
        critical: MutableList<Recommendation>,
        performance: MutableList<Recommendation>,
        configuration: MutableList<Recommendation>,
    ) {
        val download = diagnostics.bestDownloadSpeed
        val upload = diagnostics.bestUploadSpeed

        if (download != null && download < 10.0) {
            critical.add(
                Recommendation(
                    category = RecommendationCategory.CRITICAL,
                    issue = "Very low download speed (${"%.1f".format(download)} Mbps)",
                    action = "Check WiFi signal strength. Verify ISP service tier. Test with ethernet to rule out WiFi issues. Restart router and modem.",
                    impact = RecommendationImpact.HIGH,
                    effort = RecommendationEffort.LOW,
                    technicalDetails = "Minimum 25 Mbps recommended for HD streaming. 50+ Mbps for 4K and multiple devices.",
                ),
            )
        } else if (download != null && download < 25.0) {
            performance.add(
                Recommendation(
                    category = RecommendationCategory.PERFORMANCE,
                    issue = "Low download speed (${"%.1f".format(download)} Mbps)",
                    action = "Consider upgrading ISP plan for better streaming and download performance.",
                    impact = RecommendationImpact.MEDIUM,
                    effort = RecommendationEffort.MEDIUM,
                ),
            )
        }

        if (upload != null && upload < 5.0) {
            performance.add(
                Recommendation(
                    category = RecommendationCategory.PERFORMANCE,
                    issue = "Low upload speed (${"%.1f".format(upload)} Mbps)",
                    action = "Upload speed impacts video calls and file sharing. Consider ISP plan with higher upload speeds.",
                    impact = RecommendationImpact.MEDIUM,
                    effort = RecommendationEffort.MEDIUM,
                    technicalDetails = "Minimum 3 Mbps upload for HD video calls. 10+ Mbps recommended for multiple simultaneous video calls.",
                ),
            )
        }
    }

    /**
     * Analyze DNS and generate recommendations.
     */
    private fun analyzeDns(
        diagnostics: ActiveDiagnostics,
        critical: MutableList<Recommendation>,
        performance: MutableList<Recommendation>,
        configuration: MutableList<Recommendation>,
    ) {
        if (diagnostics.dnsSuccessRate < 90.0 && diagnostics.dnsTests.isNotEmpty()) {
            critical.add(
                Recommendation(
                    category = RecommendationCategory.CRITICAL,
                    issue = "DNS resolution failures (${"%.0f".format(
                        100.0 - diagnostics.dnsSuccessRate,
                    )}% failure rate)",
                    action = "Switch to reliable public DNS servers: Google DNS (8.8.8.8, 8.8.4.4) or Cloudflare DNS (1.1.1.1, 1.0.0.1)",
                    impact = RecommendationImpact.HIGH,
                    effort = RecommendationEffort.LOW,
                    technicalDetails = "DNS failures prevent accessing websites by name. Affects all internet browsing.",
                ),
            )
        }

        // Slow DNS
        val dnsTime = diagnostics.avgDnsResolutionTime
        if (dnsTime != null && dnsTime.inWholeMilliseconds >= 100) {
            performance.add(
                Recommendation(
                    category = RecommendationCategory.PERFORMANCE,
                    issue = "Slow DNS resolution (${dnsTime.inWholeMilliseconds}ms average)",
                    action = "Switch to faster DNS servers. Google DNS (8.8.8.8) or Cloudflare DNS (1.1.1.1) typically offer <20ms resolution.",
                    impact = RecommendationImpact.MEDIUM,
                    effort = RecommendationEffort.LOW,
                    technicalDetails = "DNS resolution happens before every website load. Faster DNS improves browsing responsiveness.",
                ),
            )
        }
    }

    /**
     * Analyze overall health and generate recommendations.
     */
    private fun analyzeOverallHealth(
        health: NetworkHealthScore,
        critical: MutableList<Recommendation>,
        performance: MutableList<Recommendation>,
        configuration: MutableList<Recommendation>,
        monitoring: MutableList<Recommendation>,
    ) {
        monitoring.add(
            Recommendation(
                category = RecommendationCategory.MONITORING,
                issue = "Ongoing network health",
                action = "Run diagnostics periodically (weekly) to track network performance trends and catch issues early.",
                impact = RecommendationImpact.MEDIUM,
                effort = RecommendationEffort.LOW,
                technicalDetails = "Regular monitoring helps identify degradation before it becomes critical.",
            ),
        )

        if (health.overallHealth == NetworkHealth.CRITICAL) {
            critical.add(
                Recommendation(
                    category = RecommendationCategory.CRITICAL,
                    issue = "Critical network health (${health.overallScore.toInt()}/100)",
                    action = "Multiple serious issues detected. Address critical items first, then systematic improvements.",
                    impact = RecommendationImpact.HIGH,
                    effort = RecommendationEffort.HIGH,
                    technicalDetails = "Overall health below 40/100 indicates severe problems affecting usability.",
                ),
            )
        }
    }

    /**
     * Identify quick wins (high impact, low effort).
     */
    private fun identifyQuickWins(recommendations: List<Recommendation>): List<Recommendation> =
        recommendations
            .filter {
                it.impact == RecommendationImpact.HIGH && it.effort == RecommendationEffort.LOW
            }.sortedByDescending { it.category.priority }
}

/**
 * A single actionable recommendation.
 *
 * @property category Category of recommendation
 * @property issue Description of the issue
 * @property action Specific action to take
 * @property impact Expected impact of the action
 * @property effort Effort required to implement
 * @property technicalDetails Optional technical explanation
 */
data class Recommendation(
    val category: RecommendationCategory,
    val issue: String,
    val action: String,
    val impact: RecommendationImpact,
    val effort: RecommendationEffort,
    val technicalDetails: String? = null,
) {
    /**
     * Whether this is a quick win (high impact, low effort).
     */
    val isQuickWin: Boolean = impact == RecommendationImpact.HIGH && effort == RecommendationEffort.LOW

    /**
     * Format recommendation as human-readable text.
     */
    fun format(): String =
        buildString {
            append("[${category.name}] $issue\n")
            append("  Action: $action\n")
            append("  Impact: $impact | Effort: $effort")
            if (technicalDetails != null) {
                append("\n  Details: $technicalDetails")
            }
        }
}

/**
 * Set of categorized recommendations.
 *
 * @property critical Critical fixes (immediate action required)
 * @property performance Performance improvements
 * @property configuration Configuration optimizations
 * @property security Security enhancements
 * @property monitoring Monitoring recommendations
 * @property quickWins Quick wins (high impact, low effort)
 */
data class RecommendationSet(
    val critical: List<Recommendation>,
    val performance: List<Recommendation>,
    val configuration: List<Recommendation>,
    val security: List<Recommendation>,
    val monitoring: List<Recommendation>,
    val quickWins: List<Recommendation>,
) {
    /**
     * Total number of recommendations.
     */
    val totalCount: Int = critical.size + performance.size + configuration.size + security.size + monitoring.size

    /**
     * Whether there are any critical issues.
     */
    val hasCriticalIssues: Boolean = critical.isNotEmpty()

    /**
     * Get all recommendations as a single list.
     */
    fun all(): List<Recommendation> = critical + performance + configuration + security + monitoring

    /**
     * Format all recommendations as text.
     */
    fun format(): String =
        buildString {
            if (quickWins.isNotEmpty()) {
                appendLine("=== QUICK WINS (High Impact, Low Effort) ===")
                quickWins.forEach {
                    appendLine(it.format())
                    appendLine()
                }
            }

            if (critical.isNotEmpty()) {
                appendLine("=== CRITICAL FIXES ===")
                critical.forEach {
                    appendLine(it.format())
                    appendLine()
                }
            }

            if (performance.isNotEmpty()) {
                appendLine("=== PERFORMANCE IMPROVEMENTS ===")
                performance.forEach {
                    appendLine(it.format())
                    appendLine()
                }
            }

            if (configuration.isNotEmpty()) {
                appendLine("=== CONFIGURATION OPTIMIZATIONS ===")
                configuration.forEach {
                    appendLine(it.format())
                    appendLine()
                }
            }

            if (security.isNotEmpty()) {
                appendLine("=== SECURITY ENHANCEMENTS ===")
                security.forEach {
                    appendLine(it.format())
                    appendLine()
                }
            }

            if (monitoring.isNotEmpty()) {
                appendLine("=== MONITORING ===")
                monitoring.forEach {
                    appendLine(it.format())
                    appendLine()
                }
            }
        }
}

/**
 * Category of recommendation.
 */
enum class RecommendationCategory(
    val priority: Int,
) {
    CRITICAL(5),
    PERFORMANCE(4),
    CONFIGURATION(3),
    SECURITY(2),
    MONITORING(1),
}

/**
 * Expected impact of implementing a recommendation.
 */
enum class RecommendationImpact {
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * Effort required to implement a recommendation.
 */
enum class RecommendationEffort {
    LOW,
    MEDIUM,
    HIGH,
}
