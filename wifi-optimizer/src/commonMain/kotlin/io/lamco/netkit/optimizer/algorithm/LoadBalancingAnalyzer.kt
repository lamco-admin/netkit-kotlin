package io.lamco.netkit.optimizer.algorithm

import io.lamco.netkit.model.topology.ApCluster
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Load balancing analyzer for multi-AP networks
 *
 * Analyzes client and traffic distribution across multiple APs to identify
 * load imbalances and recommend optimization strategies.
 *
 * Load balancing metrics:
 * - Client count distribution
 * - Bandwidth utilization distribution
 * - Airtime distribution
 * - Variance-based imbalance scoring
 *
 * Based on:
 * - Statistical variance analysis
 * - IEEE 802.11v load balancing recommendations
 * - Enterprise WiFi deployment best practices
 */
class LoadBalancingAnalyzer {
    /**
     * Analyze load balancing across APs in a cluster
     *
     * Evaluates how evenly distributed clients and traffic are across APs,
     * and recommends client steering to improve balance.
     *
     * @param cluster AP cluster to analyze
     * @param apLoads Current load metrics for each AP
     * @return Load balancing analysis with recommendations
     */
    fun analyzeLoadBalancing(
        cluster: ApCluster,
        apLoads: Map<String, ApLoadMetrics>,
    ): LoadBalancingAnalysis {
        require(cluster.isMultiAp) {
            "Load balancing only applicable to multi-AP deployments"
        }

        val loads =
            cluster.bssids.mapNotNull { bss ->
                apLoads[bss.bssid]
            }

        if (loads.isEmpty()) {
            return LoadBalancingAnalysis(
                isBalanced = true,
                imbalanceFactor = 0.0,
                overloadedAps = emptyList(),
                underutilizedAps = emptyList(),
                recommendations = listOf("No load data available"),
            )
        }

        val clientImbalance = calculateImbalanceFactor(loads.map { it.clientCount.toDouble() })
        val utilizationImbalance = calculateImbalanceFactor(loads.map { it.utilizationPercent.toDouble() })
        val airtimeImbalance = calculateImbalanceFactor(loads.map { it.airtimePercent.toDouble() })

        // Overall imbalance (weighted average)
        val imbalanceFactor = (clientImbalance * 0.4 + utilizationImbalance * 0.4 + airtimeImbalance * 0.2)

        val meanClientCount = loads.map { it.clientCount }.average()
        val meanUtilization = loads.map { it.utilizationPercent }.average()

        val overloaded =
            loads
                .filter {
                    it.clientCount > meanClientCount * 1.5 || it.utilizationPercent > meanUtilization * 1.3
                }.map { it.bssid }

        val underutilized =
            loads
                .filter {
                    it.clientCount < meanClientCount * 0.5 || it.utilizationPercent < meanUtilization * 0.5
                }.map { it.bssid }

        val recommendations =
            buildLoadBalancingRecommendations(
                imbalanceFactor = imbalanceFactor,
                overloadedCount = overloaded.size,
                underutilizedCount = underutilized.size,
                totalAps = loads.size,
            )

        return LoadBalancingAnalysis(
            isBalanced = imbalanceFactor < 0.3,
            imbalanceFactor = imbalanceFactor,
            overloadedAps = overloaded,
            underutilizedAps = underutilized,
            recommendations = recommendations,
        )
    }

    /**
     * Recommend specific client moves for load balancing
     *
     * Identifies which clients should be steered from overloaded APs to
     * underutilized APs to improve overall balance.
     *
     * @param overloadedAp Overloaded AP BSSID
     * @param candidates Candidate APs for client redistribution
     * @param clients Clients currently on overloaded AP
     * @return List of recommended client moves
     */
    fun recommendClientMoves(
        overloadedAp: String,
        candidates: List<ApMetrics>,
        clients: List<ClientMetrics>,
    ): List<ClientMoveRecommendation> {
        if (candidates.isEmpty() || clients.isEmpty()) {
            return emptyList()
        }

        // Sort candidates by desirability (less loaded, good signal)
        val sortedCandidates =
            candidates.sortedBy { ap ->
                // Lower score is better
                ap.clientCount * 10.0 + (100 - (ap.signalRssi ?: -80) + 80)
            }

        // Sort clients by ease of steering (stronger signal to candidates)
        val clientMoves = mutableListOf<ClientMoveRecommendation>()

        for (client in clients) {
            val bestCandidate =
                sortedCandidates.firstOrNull { candidate ->
                    // Ensure candidate has acceptable signal
                    (candidate.signalRssi ?: -100) >= -75 &&
                        // Ensure we're not overloading the candidate
                        candidate.clientCount < clients.size / candidates.size + 2
                }

            if (bestCandidate != null) {
                val signalDiff = (bestCandidate.signalRssi ?: -80) - client.signalStrength

                clientMoves.add(
                    ClientMoveRecommendation(
                        clientMac = client.macAddress,
                        fromBssid = overloadedAp,
                        toBssid = bestCandidate.bssid,
                        rationale = buildMoveRationale(client, bestCandidate),
                        signalDifference = signalDiff,
                        priority = calculateMovePriority(client, bestCandidate, signalDiff),
                    ),
                )
            }
        }

        return clientMoves.sortedByDescending { it.priority }
    }

    /**
     * Calculate optimal client distribution target
     *
     * Determines ideal client count per AP based on AP capabilities,
     * coverage, and current total clients.
     *
     * @param apMetrics List of AP metrics
     * @param totalClients Total clients to distribute
     * @return Map of BSSID to target client count
     */
    fun calculateOptimalDistribution(
        apMetrics: List<ApMetrics>,
        totalClients: Int,
    ): Map<String, Int> {
        if (apMetrics.isEmpty()) return emptyMap()

        // Simple distribution: equal clients per AP
        // More sophisticated: weight by AP capacity, signal strength, etc.

        val baseTarget = totalClients / apMetrics.size
        val remainder = totalClients % apMetrics.size

        return apMetrics
            .mapIndexed { index, ap ->
                val target = if (index < remainder) baseTarget + 1 else baseTarget
                ap.bssid to target
            }.toMap()
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Calculate imbalance factor using coefficient of variation
     *
     * CV = (standard deviation / mean)
     * - 0.0 = Perfect balance
     * - > 0.3 = Moderate imbalance
     * - > 0.5 = Significant imbalance
     * - > 1.0 = Severe imbalance
     */
    private fun calculateImbalanceFactor(values: List<Double>): Double {
        if (values.size < 2) return 0.0

        val mean = values.average()
        if (mean == 0.0) return 0.0

        val variance = values.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        return (stdDev / mean).coerceIn(0.0, 2.0) // Cap at 2.0 for practical purposes
    }

    private fun buildLoadBalancingRecommendations(
        imbalanceFactor: Double,
        overloadedCount: Int,
        underutilizedCount: Int,
        totalAps: Int,
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when {
            imbalanceFactor < 0.2 -> {
                recommendations.add("Load is well balanced across APs")
            }
            imbalanceFactor < 0.4 -> {
                recommendations.add("Moderate load imbalance detected (CV: ${(imbalanceFactor * 100).toInt()}%)")
                if (overloadedCount > 0) {
                    recommendations.add("Steer clients from $overloadedCount overloaded AP(s)")
                }
            }
            imbalanceFactor < 0.7 -> {
                recommendations.add("Significant load imbalance (CV: ${(imbalanceFactor * 100).toInt()}%)")
                recommendations.add("Enable aggressive client steering and band steering")
                if (overloadedCount > totalAps / 2) {
                    recommendations.add("Consider adding more APs to distribute load")
                }
            }
            else -> {
                recommendations.add("Severe load imbalance (CV: ${(imbalanceFactor * 100).toInt()}%)")
                recommendations.add("URGENT: Redistribute clients immediately")
                recommendations.add("Review AP placement and coverage overlap")
            }
        }

        if (underutilizedCount > 0 && underutilizedCount < totalAps) {
            recommendations.add("$underutilizedCount AP(s) are underutilized - adjust TX power or placement")
        }

        return recommendations
    }

    private fun buildMoveRationale(
        client: ClientMetrics,
        targetAp: ApMetrics,
    ): String {
        val signalDiff = (targetAp.signalRssi ?: -80) - client.signalStrength
        val loadDiff = targetAp.clientCount

        return buildString {
            append("Move to ${targetAp.bssid}: ")
            when {
                signalDiff >= 10 -> append("much better signal (+${signalDiff}dB)")
                signalDiff >= 5 -> append("better signal (+${signalDiff}dB)")
                loadDiff < 10 -> append("target AP has low load ($loadDiff clients)")
                else -> append("better overall distribution")
            }
        }
    }

    private fun calculateMovePriority(
        client: ClientMetrics,
        targetAp: ApMetrics,
        signalDiff: Int,
    ): Int {
        var priority = 50

        when {
            signalDiff >= 15 -> priority += 30
            signalDiff >= 10 -> priority += 20
            signalDiff >= 5 -> priority += 10
            signalDiff < 0 -> priority -= 20 // Worse signal = lower priority
        }

        // Current signal quality (weak clients more important to fix)
        if (client.signalStrength < -75) {
            priority += 15
        }

        // Client type (VoIP/video clients higher priority)
        if (client.hasVoipTraffic) {
            priority += 10
        }
        if (client.hasVideoTraffic) {
            priority += 5
        }

        return priority.coerceIn(0, 100)
    }
}

// ========================================
// Data Classes
// ========================================

/**
 * Load balancing analysis result
 */
data class LoadBalancingAnalysis(
    val isBalanced: Boolean,
    val imbalanceFactor: Double, // 0.0-1.0+ (coefficient of variation)
    val overloadedAps: List<String>,
    val underutilizedAps: List<String>,
    val recommendations: List<String>,
) {
    val needsRebalancing: Boolean get() = !isBalanced
    val severity: LoadImbalanceSeverity get() =
        when {
            imbalanceFactor < 0.3 -> LoadImbalanceSeverity.NONE
            imbalanceFactor < 0.5 -> LoadImbalanceSeverity.MODERATE
            imbalanceFactor < 0.7 -> LoadImbalanceSeverity.SIGNIFICANT
            else -> LoadImbalanceSeverity.SEVERE
        }
}

/**
 * Load imbalance severity levels
 */
enum class LoadImbalanceSeverity {
    NONE,
    MODERATE,
    SIGNIFICANT,
    SEVERE,
}

/**
 * AP load metrics
 */
data class ApLoadMetrics(
    val bssid: String,
    val clientCount: Int,
    val utilizationPercent: Int,
    val airtimePercent: Double,
    val throughputMbps: Double,
)

/**
 * Client move recommendation
 */
data class ClientMoveRecommendation(
    val clientMac: String,
    val fromBssid: String,
    val toBssid: String,
    val rationale: String,
    val signalDifference: Int, // dB
    val priority: Int, // 0-100, higher = more important
) {
    val isHighPriority: Boolean get() = priority >= 70
}
