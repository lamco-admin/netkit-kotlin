package io.lamco.netkit.topology.advisor

import io.lamco.netkit.model.topology.*
import io.lamco.netkit.topology.analysis.HandoffDetector
import io.lamco.netkit.topology.analysis.TransitionZone
import io.lamco.netkit.topology.scoring.RoamingCandidate
import io.lamco.netkit.topology.scoring.RoamingScorer
import io.lamco.netkit.topology.scoring.StickyClientDetector

/**
 * Comprehensive roaming recommendation engine
 *
 * Analyzes current network conditions and provides actionable recommendations
 * for roaming behavior, network optimization, and user experience improvements.
 *
 * Combines insights from:
 * - RoamingScorer: Candidate evaluation
 * - StickyClientDetector: Sticky behavior detection
 * - HandoffDetector: Transition zone analysis
 */
class RoamingAdvisor(
    private val roamingScorer: RoamingScorer = RoamingScorer(),
    private val stickyDetector: StickyClientDetector = StickyClientDetector(),
    private val handoffDetector: HandoffDetector = HandoffDetector(),
) {
    /**
     * Generate comprehensive roaming advice for current situation
     *
     * @param currentBssid Currently connected BSSID
     * @param currentRssi Current RSSI
     * @param cluster AP cluster
     * @return Comprehensive roaming advice
     */
    fun advise(
        currentBssid: String,
        currentRssi: Int,
        cluster: ApCluster,
    ): RoamingAdvice {
        val stickyMetrics =
            stickyDetector.detectCurrentSticky(
                ssid = cluster.ssid,
                currentBssid = currentBssid,
                currentRssi = currentRssi,
                cluster = cluster,
            )

        val candidates =
            roamingScorer.scoreRoamingCandidates(
                currentBssid = currentBssid,
                cluster = cluster,
                currentRssi = currentRssi,
            )

        val transitionZone =
            handoffDetector.detectCurrentTransitionZone(
                cluster = cluster,
                currentBssid = currentBssid,
                currentRssi = currentRssi,
            )

        val bestCandidate = candidates.firstOrNull()

        val recommendation =
            determineRecommendation(
                currentRssi = currentRssi,
                stickyMetrics = stickyMetrics,
                bestCandidate = bestCandidate,
                transitionZone = transitionZone,
                cluster = cluster,
            )

        val suggestions =
            generateSuggestions(
                currentRssi = currentRssi,
                stickyMetrics = stickyMetrics,
                candidates = candidates,
                cluster = cluster,
                recommendation = recommendation,
            )

        return RoamingAdvice(
            recommendation = recommendation,
            currentSituation =
                CurrentSituation(
                    currentBssid = currentBssid,
                    currentRssi = currentRssi,
                    isSticky = stickyMetrics != null,
                    inTransitionZone = transitionZone != null,
                ),
            bestCandidate = bestCandidate,
            transitionZone = transitionZone,
            stickyMetrics = stickyMetrics,
            suggestions = suggestions,
            overallScore = calculateOverallScore(currentRssi, cluster, candidates),
        )
    }

    /**
     * Analyze network topology and provide optimization recommendations
     *
     * @param cluster AP cluster to analyze
     * @return Network optimization recommendations
     */
    fun analyzeNetworkOptimization(cluster: ApCluster): NetworkOptimizationAdvice {
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        when {
            cluster.apCount == 1 -> {
                issues.add("Single AP deployment - no roaming possible")
                recommendations.add("Consider adding additional APs for coverage redundancy")
            }
            cluster.apCount >= 10 -> {
                issues.add("Large deployment with ${cluster.apCount} APs")
                recommendations.add("Ensure proper channel planning to avoid interference")
            }
        }

        if (!cluster.supportsFastRoaming) {
            issues.add("No fast roaming support detected")
            recommendations.add("Enable 802.11k/r/v on all APs for seamless roaming")
        }

        if (!cluster.isDualBand && cluster.apCount > 1) {
            issues.add("Single-band deployment limits performance")
            recommendations.add("Deploy dual-band APs for better client distribution")
        }

        if (!cluster.supportsWiFi6 && cluster.apCount > 2) {
            issues.add("Legacy WiFi standards in use")
            recommendations.add("Upgrade to WiFi 6 or newer for improved roaming and capacity")
        }

        val deploymentQuality =
            when {
                cluster.isEnterprise && cluster.supportsFastRoaming -> DeploymentQuality.EXCELLENT
                cluster.isMesh && cluster.apCount >= 3 -> DeploymentQuality.GOOD
                cluster.apCount >= 2 && cluster.isDualBand -> DeploymentQuality.FAIR
                else -> DeploymentQuality.POOR
            }

        return NetworkOptimizationAdvice(
            deploymentType = cluster.deploymentType,
            deploymentQuality = deploymentQuality,
            issues = issues,
            recommendations = recommendations,
            supportsFastRoaming = cluster.supportsFastRoaming,
            supportsWiFi6 = cluster.supportsWiFi6,
            apCount = cluster.apCount,
        )
    }

    /**
     * Evaluate roaming performance from historical events
     *
     * @param events Historical roaming events
     * @return Performance evaluation
     */
    fun evaluateRoamingPerformance(events: List<RoamingEvent>): RoamingPerformanceEvaluation {
        if (events.isEmpty()) {
            return RoamingPerformanceEvaluation(
                totalEvents = 0,
                successRate = 0.0,
                averageLatency = 0,
                grade = PerformanceGrade.UNKNOWN,
                strengths = emptyList(),
                weaknesses = emptyList(),
                improvementSuggestions = listOf("No roaming events recorded yet"),
            )
        }

        val successful = events.count { it.isSuccessful }
        val successRate = (successful.toDouble() / events.size) * 100.0
        val avgLatency = events.mapNotNull { it.durationMs }.average().toInt()

        val strengths = mutableListOf<String>()
        if (successRate >= 95) strengths.add("Very high success rate")
        if (avgLatency <= 100) strengths.add("Fast roaming latency")
        if (events.any { it.usesFastRoaming }) strengths.add("802.11k/r/v in use")

        val weaknesses = mutableListOf<String>()
        if (successRate < 90) weaknesses.add("Lower than expected success rate")
        if (avgLatency > 500) weaknesses.add("High roaming latency")
        if (events.none { it.usesFastRoaming }) weaknesses.add("Fast roaming not utilized")

        val suggestions = mutableListOf<String>()
        if (successRate < 95) {
            suggestions.add("Investigate failed roaming events for root cause")
        }
        if (avgLatency > 300 && !events.any { it.usesFastRoaming }) {
            suggestions.add("Enable 802.11k/r/v fast roaming to reduce latency")
        }
        if (events.count { it.isStickyRoam } > events.size / 4) {
            suggestions.add("Optimize roaming thresholds to reduce sticky client behavior")
        }

        val grade =
            when {
                successRate >= 95 && avgLatency <= 100 -> PerformanceGrade.A
                successRate >= 90 && avgLatency <= 300 -> PerformanceGrade.B
                successRate >= 80 -> PerformanceGrade.C
                successRate >= 70 -> PerformanceGrade.D
                else -> PerformanceGrade.F
            }

        return RoamingPerformanceEvaluation(
            totalEvents = events.size,
            successRate = successRate,
            averageLatency = avgLatency,
            grade = grade,
            strengths = strengths,
            weaknesses = weaknesses,
            improvementSuggestions = suggestions,
        )
    }

    private fun determineRecommendation(
        currentRssi: Int,
        stickyMetrics: StickyClientMetrics?,
        bestCandidate: RoamingCandidate?,
        transitionZone: TransitionZone?,
        cluster: ApCluster,
    ): RecommendationType {
        // RSSI < -85 dBm: connection quality degraded to point of imminent failure
        if (currentRssi < -85) {
            return if (bestCandidate != null) {
                RecommendationType.ROAM_IMMEDIATELY
            } else {
                RecommendationType.MOVE_CLOSER
            }
        }

        if (stickyMetrics != null && bestCandidate != null) {
            return when (stickyMetrics.severity) {
                StickySeverity.CRITICAL -> RecommendationType.ROAM_IMMEDIATELY
                StickySeverity.HIGH -> RecommendationType.ROAM_SOON
                StickySeverity.MEDIUM -> RecommendationType.CONSIDER_ROAMING
                StickySeverity.LOW, StickySeverity.NONE -> RecommendationType.MONITOR
            }
        }

        if (transitionZone != null && bestCandidate != null) {
            return if (bestCandidate.rssiImprovement >= 10) {
                RecommendationType.ROAM_WHEN_READY
            } else {
                RecommendationType.MONITOR
            }
        }

        if (currentRssi < -75 && bestCandidate != null && bestCandidate.rssiImprovement >= 15) {
            return RecommendationType.CONSIDER_ROAMING
        }

        return if (currentRssi >= -60) {
            RecommendationType.STAY_CONNECTED
        } else {
            RecommendationType.MONITOR
        }
    }

    private fun generateSuggestions(
        currentRssi: Int,
        stickyMetrics: StickyClientMetrics?,
        candidates: List<RoamingCandidate>,
        cluster: ApCluster,
        recommendation: RecommendationType,
    ): List<String> {
        val suggestions = mutableListOf<String>()

        when (recommendation) {
            RecommendationType.ROAM_IMMEDIATELY -> {
                suggestions.add("Roam to ${candidates.firstOrNull()?.bssid ?: "better AP"} immediately")
                suggestions.add("Current signal is critically weak")
            }
            RecommendationType.ROAM_SOON -> {
                suggestions.add("Plan to roam within next few seconds")
                if (cluster.supportsFastRoaming) {
                    suggestions.add("Fast roaming enabled - transition should be seamless")
                }
            }
            RecommendationType.ROAM_WHEN_READY -> {
                suggestions.add("Roam when convenient (no urgency)")
            }
            RecommendationType.CONSIDER_ROAMING -> {
                suggestions.add("Better signal available on nearby AP")
                suggestions.add("Consider roaming if application performance is affected")
            }
            RecommendationType.MOVE_CLOSER -> {
                suggestions.add("Move closer to an AP for better signal")
                suggestions.add("All APs have weak signal from current location")
            }
            RecommendationType.STAY_CONNECTED -> {
                suggestions.add("Current connection is optimal")
            }
            RecommendationType.MONITOR -> {
                suggestions.add("Continue monitoring signal strength")
            }
        }

        if (stickyMetrics != null) {
            suggestions.add("Sticky client behavior detected - roaming recommended")
        }

        if (!cluster.supportsFastRoaming && cluster.apCount > 1) {
            suggestions.add("Network: Enable fast roaming (802.11k/r/v) for better experience")
        }

        return suggestions
    }

    private fun calculateOverallScore(
        currentRssi: Int,
        cluster: ApCluster,
        candidates: List<RoamingCandidate>,
    ): Int {
        var score = 50 // Base score

        // Current signal quality (0-30 points)
        score +=
            when {
                currentRssi >= -50 -> 30
                currentRssi >= -60 -> 25
                currentRssi >= -70 -> 15
                currentRssi >= -80 -> 5
                else -> 0
            }

        // Network capabilities (0-20 points)
        if (cluster.supportsFastRoaming) score += 10
        if (cluster.isDualBand) score += 5
        if (cluster.supportsWiFi6) score += 5

        // Roaming options (0-20 points)
        if (candidates.isNotEmpty()) {
            score +=
                when {
                    candidates.first().score >= 80 -> 20
                    candidates.first().score >= 60 -> 15
                    candidates.first().score >= 40 -> 10
                    else -> 5
                }
        }

        // Multi-AP bonus (0-10 points)
        score +=
            when (cluster.apCount) {
                0, 1 -> 0
                2 -> 5
                else -> 10
            }

        return score.coerceIn(0, 100)
    }
}

/**
 * Comprehensive roaming advice
 */
data class RoamingAdvice(
    val recommendation: RecommendationType,
    val currentSituation: CurrentSituation,
    val bestCandidate: RoamingCandidate?,
    val transitionZone: TransitionZone?,
    val stickyMetrics: StickyClientMetrics?,
    val suggestions: List<String>,
    val overallScore: Int,
) {
    /** Priority level of the recommendation */
    val priority: RecommendationPriority
        get() =
            when (recommendation) {
                RecommendationType.ROAM_IMMEDIATELY -> RecommendationPriority.CRITICAL
                RecommendationType.ROAM_SOON -> RecommendationPriority.HIGH
                RecommendationType.ROAM_WHEN_READY, RecommendationType.CONSIDER_ROAMING -> RecommendationPriority.MEDIUM
                else -> RecommendationPriority.LOW
            }
}

/**
 * Current connection situation
 */
data class CurrentSituation(
    val currentBssid: String,
    val currentRssi: Int,
    val isSticky: Boolean,
    val inTransitionZone: Boolean,
)

/**
 * Recommendation types
 */
enum class RecommendationType {
    /** Roam immediately - critical situation */
    ROAM_IMMEDIATELY,

    /** Roam soon - high priority */
    ROAM_SOON,

    /** Roam when ready - no urgency */
    ROAM_WHEN_READY,

    /** Consider roaming - optional optimization */
    CONSIDER_ROAMING,

    /** Stay connected - current connection is good */
    STAY_CONNECTED,

    /** Monitor signal - no action needed yet */
    MONITOR,

    /** Move closer to AP - all signals weak */
    MOVE_CLOSER,
}

/**
 * Recommendation priority levels
 */
enum class RecommendationPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * Network optimization advice
 */
data class NetworkOptimizationAdvice(
    val deploymentType: ClusterDeploymentType,
    val deploymentQuality: DeploymentQuality,
    val issues: List<String>,
    val recommendations: List<String>,
    val supportsFastRoaming: Boolean,
    val supportsWiFi6: Boolean,
    val apCount: Int,
)

/**
 * Deployment quality grades
 */
enum class DeploymentQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}

/**
 * Roaming performance evaluation
 */
data class RoamingPerformanceEvaluation(
    val totalEvents: Int,
    val successRate: Double,
    val averageLatency: Int,
    val grade: PerformanceGrade,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val improvementSuggestions: List<String>,
)

/**
 * Performance grade
 */
enum class PerformanceGrade {
    /** 95%+ success, <100ms latency */
    A,

    /** 90%+ success, <300ms latency */
    B,

    /** 80%+ success */
    C,

    /** 70%+ success */
    D,

    /** <70% success */
    F,

    /** No data */
    UNKNOWN,
}
