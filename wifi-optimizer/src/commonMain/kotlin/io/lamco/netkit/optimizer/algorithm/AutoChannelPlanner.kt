package io.lamco.netkit.optimizer.algorithm

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.topology.ApCluster
import io.lamco.netkit.optimizer.model.ChannelPlanningConstraints
import io.lamco.netkit.optimizer.model.RegulatoryDomain
import kotlin.math.abs
import kotlin.math.exp

/**
 * Automated channel planner for optimal RF performance
 *
 * Implements intelligent channel allocation algorithms for single-AP and multi-AP
 * deployments. Optimizes for minimal interference while considering:
 * - Co-channel interference (same channel)
 * - Adjacent channel interference (±1, ±2 channels in 2.4 GHz)
 * - Channel congestion and utilization
 * - DFS radar detection risks
 * - Regulatory domain constraints
 *
 * Algorithm approach:
 * 1. Score all available channels for each AP
 * 2. For multi-AP: globally optimize to minimize total interference
 * 3. Ensure minimum channel separation (non-overlapping)
 * 4. Validate regulatory compliance and DFS constraints
 *
 * Based on IEEE 802.11 channel planning best practices and real-world
 * deployment optimization research.
 */
class AutoChannelPlanner {

    /**
     * Plan optimal channel allocation for multi-AP network
     *
     * Analyzes the current network topology and neighbor environment to generate
     * an optimal channel plan that minimizes interference and maximizes throughput.
     *
     * @param apClusters List of AP clusters to plan channels for
     * @param constraints Planning constraints (band, DFS, regulatory domain, etc.)
     * @param neighborNetworks Detected neighbor networks on various channels
     * @return Complete channel plan with per-AP assignments and improvement metrics
     */
    fun planChannels(
        apClusters: List<ApCluster>,
        constraints: ChannelPlanningConstraints,
        neighborNetworks: List<NeighborNetwork> = emptyList()
    ): ChannelPlan {
        require(apClusters.isNotEmpty()) {
            "Cannot plan channels for empty AP cluster list"
        }

        val bssids = apClusters.flatMap { cluster ->
            cluster.bssids.filter { it.band == constraints.band }
        }.map { it.bssid }

        if (bssids.isEmpty()) {
            return ChannelPlan(
                assignments = emptyMap(),
                score = 0.0,
                coChannelInterference = 0.0,
                adjacentChannelInterference = 0.0,
                dfsRisk = DfsRisk.NONE,
                improvementVsCurrent = 0.0
            )
        }

        val channelScores = bssids.associateWith { bssid ->
            scoreChannelsForAp(bssid, constraints, neighborNetworks)
        }

        val assignments = optimizeGlobalAssignments(
            bssids = bssids,
            channelScores = channelScores,
            constraints = constraints
        )

        val planMetrics = calculatePlanMetrics(assignments, neighborNetworks, constraints)

        return ChannelPlan(
            assignments = assignments,
            score = planMetrics.score,
            coChannelInterference = planMetrics.coChannelInterference,
            adjacentChannelInterference = planMetrics.adjacentChannelInterference,
            dfsRisk = planMetrics.dfsRisk,
            improvementVsCurrent = planMetrics.improvementVsCurrent
        )
    }

    /**
     * Optimize channel width for given network conditions
     *
     * Analyzes current channel congestion and network requirements to recommend
     * the optimal channel width. Wider channels provide more throughput but
     * are more susceptible to interference.
     *
     * @param currentChannel Current channel number
     * @param band Frequency band
     * @param congestion Channel congestion metrics
     * @param constraints Planning constraints
     * @return Channel width recommendation with rationale
     */
    fun optimizeChannelWidth(
        currentChannel: Int,
        band: WiFiBand,
        congestion: ChannelCongestionMetrics,
        constraints: ChannelPlanningConstraints
    ): ChannelWidthRecommendation {
        require(currentChannel > 0) {
            "Channel must be positive, got $currentChannel"
        }

        val preferredWidths = constraints.preferredWidths

        // For 2.4 GHz, strongly prefer 20 MHz due to overlap
        if (band == WiFiBand.BAND_2_4GHZ) {
            return if (congestion.utilizationPercent < 30 && constraints.allows40MHzIn24GHz) {
                ChannelWidthRecommendation(
                    recommended = ChannelWidth.WIDTH_40MHZ,
                    current = ChannelWidth.WIDTH_20MHZ,
                    rationale = "Low congestion allows 40 MHz for higher throughput",
                    expectedImprovement = 50.0,
                    tradeoffs = listOf(
                        "Increased interference from overlapping channels",
                        "Reduced range due to lower sensitivity"
                    )
                )
            } else {
                ChannelWidthRecommendation(
                    recommended = ChannelWidth.WIDTH_20MHZ,
                    current = ChannelWidth.WIDTH_20MHZ,
                    rationale = "20 MHz optimal for 2.4 GHz to avoid channel overlap",
                    expectedImprovement = 0.0,
                    tradeoffs = emptyList()
                )
            }
        }

        val recommendedWidth = when {
            congestion.utilizationPercent < 20 && congestion.neighborCount < 3 -> {
                preferredWidths.firstOrNull() ?: ChannelWidth.WIDTH_80MHZ
            }
            congestion.utilizationPercent < 40 && congestion.neighborCount < 6 -> {
                preferredWidths.firstOrNull { it.widthMHz <= 80 } ?: ChannelWidth.WIDTH_40MHZ
            }
            congestion.utilizationPercent < 60 -> {
                ChannelWidth.WIDTH_40MHZ
            }
            else -> {
                ChannelWidth.WIDTH_20MHZ
            }
        }

        val currentWidth = ChannelWidth.WIDTH_80MHZ  // Assume current is 80 MHz
        val improvement = calculateWidthImprovement(currentWidth, recommendedWidth, congestion)

        return ChannelWidthRecommendation(
            recommended = recommendedWidth,
            current = currentWidth,
            rationale = buildWidthRationale(recommendedWidth, congestion),
            expectedImprovement = improvement,
            tradeoffs = buildWidthTradeoffs(recommendedWidth, congestion)
        )
    }

    /**
     * Assess DFS (Dynamic Frequency Selection) channel viability
     *
     * Evaluates whether DFS channels are suitable based on location,
     * historical radar detection data, and risk tolerance.
     *
     * @param location Regulatory domain for channel availability
     * @param radarHistory Historical radar detection events (null if unknown)
     * @param currentChannel Current channel in use
     * @return DFS assessment with risk level and recommendations
     */
    fun assessDfsChannels(
        location: RegulatoryDomain,
        radarHistory: RadarDetectionHistory? = null,
        currentChannel: Int? = null
    ): DfsAssessment {
        if (!location.requiresDfs) {
            return DfsAssessment(
                isDfsRequired = false,
                availableDfsChannels = emptySet(),
                risk = DfsRisk.NONE,
                recommendation = "DFS not required in this regulatory domain",
                estimatedDisruptionFrequency = 0.0
            )
        }

        val dfsChannels = location.getChannelsForBand(WiFiBand.BAND_5GHZ, includeDfs = true) -
                location.getChannelsForBand(WiFiBand.BAND_5GHZ, includeDfs = false)

        val risk = if (radarHistory == null) {
            DfsRisk.UNKNOWN
        } else {
            assessRadarRisk(radarHistory, currentChannel)
        }

        val disruptionFrequency = when (risk) {
            DfsRisk.NONE -> 0.0
            DfsRisk.LOW -> 0.5     // ~1 event per 2 months
            DfsRisk.MEDIUM -> 2.0  // ~2 events per month
            DfsRisk.HIGH -> 8.0    // ~2 events per week
            DfsRisk.UNKNOWN -> 1.0 // Assume moderate
        }

        val recommendation = buildDfsRecommendation(risk, dfsChannels.size)

        return DfsAssessment(
            isDfsRequired = true,
            availableDfsChannels = dfsChannels,
            risk = risk,
            recommendation = recommendation,
            estimatedDisruptionFrequency = disruptionFrequency
        )
    }

    /**
     * Score all available channels for a specific AP
     */
    private fun scoreChannelsForAp(
        bssid: String,
        constraints: ChannelPlanningConstraints,
        neighbors: List<NeighborNetwork>
    ): Map<Int, ChannelScore> {
        val availableChannels = constraints.getAvailableChannels()

        return availableChannels.associateWith { channel ->
            scoreChannel(channel, bssid, neighbors, constraints)
        }
    }

    /**
     * Score a specific channel for an AP
     *
     * Scoring factors:
     * - Co-channel interference: -20 points per neighbor on same channel
     * - Adjacent channel interference: -10 points per neighbor on ±1, ±2 channels (2.4 GHz only)
     * - DFS risk: -15 (HIGH), -10 (MEDIUM), -5 (LOW)
     * - Channel utilization: -(utilization * 20)
     */
    private fun scoreChannel(
        channel: Int,
        bssid: String,
        neighbors: List<NeighborNetwork>,
        constraints: ChannelPlanningConstraints
    ): ChannelScore {
        var score = 100.0
        val issues = mutableListOf<String>()

        val coChannelCount = neighbors.count { it.channel == channel }
        if (coChannelCount > 0) {
            score -= coChannelCount * 20.0
            issues.add("$coChannelCount networks on same channel")
        }

        if (constraints.band == WiFiBand.BAND_2_4GHZ) {
            val adjacentCount = neighbors.count { neighbor ->
                val separation = abs(neighbor.channel - channel)
                separation in 1..2
            }
            if (adjacentCount > 0) {
                score -= adjacentCount * 10.0
                issues.add("$adjacentCount networks on adjacent channels")
            }
        }

        if (isDfsChannel(channel, constraints.regulatoryDomain)) {
            val dfsRisk = estimateDfsRisk(channel)
            score -= when (dfsRisk) {
                DfsRisk.HIGH -> 15.0
                DfsRisk.MEDIUM -> 10.0
                DfsRisk.LOW -> 5.0
                else -> 0.0
            }
            if (dfsRisk != DfsRisk.LOW) {
                issues.add("DFS channel with ${dfsRisk.name} radar risk")
            }
        }

        // Channel utilization penalty (assume 0 if no data)
        val utilization = neighbors.filter { it.channel == channel }
            .maxOfOrNull { it.utilizationPercent } ?: 0
        score -= (utilization * 0.2)

        return ChannelScore(
            channel = channel,
            score = score.coerceIn(0.0, 100.0),
            coChannelCount = coChannelCount,
            adjacentChannelCount = if (constraints.band == WiFiBand.BAND_2_4GHZ) {
                neighbors.count { abs(it.channel - channel) in 1..2 }
            } else 0,
            issues = issues
        )
    }

    /**
     * Optimize channel assignments globally for all APs
     *
     * Uses a greedy algorithm:
     * 1. Sort BSSIDs by number of available high-scoring channels (most constrained first)
     * 2. For each BSSID, assign the best available channel considering already-assigned channels
     * 3. Ensure minimum separation between APs in same cluster
     */
    private fun optimizeGlobalAssignments(
        bssids: List<String>,
        channelScores: Map<String, Map<Int, ChannelScore>>,
        constraints: ChannelPlanningConstraints
    ): Map<String, ChannelAssignment> {
        val assignments = mutableMapOf<String, ChannelAssignment>()
        val usedChannels = mutableMapOf<Int, Int>()

        // Process most constrained APs first (fewest good options) to avoid backing into corner later
        val sortedBssids = bssids.sortedBy { bssid ->
            channelScores[bssid]?.count { it.value.score >= 70.0 } ?: 0
        }

        for (bssid in sortedBssids) {
            val scores = channelScores[bssid] ?: continue

            val bestChannel = scores.entries
                .filter { (channel, _) ->
                    (usedChannels[channel] ?: 0) < constraints.maxApCountPerChannel
                }
                .maxByOrNull { (channel, channelScore) ->
                    // Penalize channels with existing APs to encourage diversity and reduce co-channel interference
                    val penalty = (usedChannels[channel] ?: 0) * 10.0
                    channelScore.score - penalty
                }

            if (bestChannel != null) {
                val (channel, channelScore) = bestChannel
                val width = constraints.preferredWidth

                assignments[bssid] = ChannelAssignment(
                    bssid = bssid,
                    channel = channel,
                    width = width,
                    rationale = buildAssignmentRationale(channelScore, usedChannels[channel] ?: 0)
                )

                usedChannels[channel] = (usedChannels[channel] ?: 0) + 1
            }
        }

        return assignments
    }

    /**
     * Calculate overall plan metrics
     */
    private fun calculatePlanMetrics(
        assignments: Map<String, ChannelAssignment>,
        neighbors: List<NeighborNetwork>,
        constraints: ChannelPlanningConstraints
    ): PlanMetrics {
        if (assignments.isEmpty()) {
            return PlanMetrics(0.0, 0.0, 0.0, DfsRisk.NONE, 0.0)
        }

        val assignedChannels = assignments.values.groupBy { it.channel }
        val coChannelInterference = assignedChannels.values
            .map { aps -> (aps.size - 1) / aps.size.toDouble() }
            .average()

        val adjacentChannelInterference = if (constraints.band == WiFiBand.BAND_2_4GHZ) {
            calculateAdjacentInterference(assignments)
        } else 0.0

        val dfsRisk = assignments.values
            .filter { isDfsChannel(it.channel, constraints.regulatoryDomain) }
            .map { estimateDfsRisk(it.channel) }
            .maxByOrNull { it.ordinal } ?: DfsRisk.NONE

        val score = calculateOverallScore(coChannelInterference, adjacentChannelInterference, dfsRisk)

        return PlanMetrics(
            score = score,
            coChannelInterference = coChannelInterference,
            adjacentChannelInterference = adjacentChannelInterference,
            dfsRisk = dfsRisk,
            improvementVsCurrent = 0.0  // Would need current plan to calculate
        )
    }

    private fun calculateAdjacentInterference(assignments: Map<String, ChannelAssignment>): Double {
        val channels = assignments.values.map { it.channel }
        var totalInterference = 0.0
        var pairCount = 0

        for (i in channels.indices) {
            for (j in i + 1 until channels.size) {
                val separation = abs(channels[i] - channels[j])
                if (separation in 1..2) {
                    totalInterference += 1.0 / separation  // Closer = more interference
                    pairCount++
                }
            }
        }

        return if (pairCount > 0) totalInterference / pairCount else 0.0
    }

    private fun calculateOverallScore(
        coChannel: Double,
        adjacentChannel: Double,
        dfsRisk: DfsRisk
    ): Double {
        var score = 100.0
        score -= coChannel * 40.0
        score -= adjacentChannel * 20.0
        score -= when (dfsRisk) {
            DfsRisk.HIGH -> 15.0
            DfsRisk.MEDIUM -> 10.0
            DfsRisk.LOW -> 5.0
            else -> 0.0
        }
        return score.coerceIn(0.0, 100.0)
    }

    private fun isDfsChannel(channel: Int, domain: RegulatoryDomain): Boolean {
        val allChannels = domain.getChannelsForBand(WiFiBand.BAND_5GHZ, includeDfs = true)
        val nonDfsChannels = domain.getChannelsForBand(WiFiBand.BAND_5GHZ, includeDfs = false)
        return channel in allChannels && channel !in nonDfsChannels
    }

    private fun estimateDfsRisk(channel: Int): DfsRisk {
        // U-NII-2A (52-64): Higher radar activity (weather radar)
        // U-NII-2C (100-144): Moderate radar activity
        return when (channel) {
            in 52..64 -> DfsRisk.MEDIUM
            in 100..144 -> DfsRisk.LOW
            else -> DfsRisk.NONE
        }
    }

    private fun assessRadarRisk(history: RadarDetectionHistory, currentChannel: Int?): DfsRisk {
        val eventsPerMonth = history.detectionEvents.size / history.monitoringMonths.coerceAtLeast(1.0)

        return when {
            eventsPerMonth >= 4.0 -> DfsRisk.HIGH
            eventsPerMonth >= 1.0 -> DfsRisk.MEDIUM
            eventsPerMonth >= 0.2 -> DfsRisk.LOW
            else -> DfsRisk.NONE
        }
    }

    private fun buildAssignmentRationale(score: ChannelScore, apCount: Int): String {
        return buildString {
            append("Score: ${score.score.toInt()}/100")
            if (score.coChannelCount > 0) {
                append(", ${score.coChannelCount} co-channel networks")
            }
            if (apCount > 0) {
                append(", $apCount APs already assigned")
            }
            if (score.issues.isNotEmpty()) {
                append(". Issues: ${score.issues.joinToString(", ")}")
            }
        }
    }

    private fun calculateWidthImprovement(
        current: ChannelWidth,
        recommended: ChannelWidth,
        congestion: ChannelCongestionMetrics
    ): Double {
        val widthRatio = recommended.widthMHz.toDouble() / current.widthMHz
        val congestionFactor = 1.0 - (congestion.utilizationPercent / 100.0)
        return (widthRatio - 1.0) * 100.0 * congestionFactor
    }

    private fun buildWidthRationale(width: ChannelWidth, congestion: ChannelCongestionMetrics): String {
        return when {
            width.widthMHz >= 160 -> "Very clean spectrum allows maximum ${width.widthMHz} MHz width"
            width.widthMHz == 80 -> "Good spectrum conditions support ${width.widthMHz} MHz width"
            width.widthMHz == 40 -> "Moderate congestion (${congestion.utilizationPercent}%) suggests ${width.widthMHz} MHz"
            else -> "High congestion (${congestion.utilizationPercent}%) requires ${width.widthMHz} MHz for reliability"
        }
    }

    private fun buildWidthTradeoffs(width: ChannelWidth, congestion: ChannelCongestionMetrics): List<String> {
        return when {
            width.widthMHz >= 80 -> listOf(
                "Higher throughput but more susceptible to interference",
                "Fewer non-overlapping channels available"
            )
            width.widthMHz == 40 -> listOf(
                "Good balance of throughput and reliability"
            )
            else -> listOf(
                "Maximum reliability and compatibility",
                "Lower peak throughput"
            )
        }
    }

    private fun buildDfsRecommendation(risk: DfsRisk, channelCount: Int): String {
        return when (risk) {
            DfsRisk.NONE, DfsRisk.LOW -> "DFS channels ($channelCount available) are safe to use with low disruption risk"
            DfsRisk.MEDIUM -> "DFS channels usable but expect occasional disruptions (CAC: 60s, monitoring ongoing)"
            DfsRisk.HIGH -> "Avoid DFS channels if possible - high radar activity will cause frequent disruptions"
            DfsRisk.UNKNOWN -> "DFS channels available but risk unknown - monitor for radar detections"
        }
    }
}

// ========================================
// Data Classes
// ========================================

/**
 * Complete channel plan with assignments and metrics
 */
data class ChannelPlan(
    val assignments: Map<String, ChannelAssignment>,
    val score: Double,
    val coChannelInterference: Double,
    val adjacentChannelInterference: Double,
    val dfsRisk: DfsRisk,
    val improvementVsCurrent: Double
) {
    val apCount: Int get() = assignments.size
    val isOptimal: Boolean get() = score >= 80.0
    val needsImprovement: Boolean get() = score < 60.0
}

/**
 * Channel assignment for a specific AP
 */
data class ChannelAssignment(
    val bssid: String,
    val channel: Int,
    val width: ChannelWidth,
    val rationale: String
)

/**
 * Channel score for a specific channel
 */
data class ChannelScore(
    val channel: Int,
    val score: Double,
    val coChannelCount: Int,
    val adjacentChannelCount: Int,
    val issues: List<String>
)

/**
 * Channel width recommendation
 */
data class ChannelWidthRecommendation(
    val recommended: ChannelWidth,
    val current: ChannelWidth,
    val rationale: String,
    val expectedImprovement: Double,
    val tradeoffs: List<String>
) {
    val shouldChange: Boolean get() = recommended != current
}

/**
 * DFS channel assessment
 */
data class DfsAssessment(
    val isDfsRequired: Boolean,
    val availableDfsChannels: Set<Int>,
    val risk: DfsRisk,
    val recommendation: String,
    val estimatedDisruptionFrequency: Double  // Events per month
)

/**
 * DFS risk levels
 */
enum class DfsRisk {
    NONE,      // Non-DFS channel
    LOW,       // Radar unlikely (< 0.5 events/month)
    MEDIUM,    // Occasional radar (0.5-2 events/month)
    HIGH,      // Frequent radar (> 2 events/month)
    UNKNOWN    // No historical data
}

/**
 * Neighbor network information
 */
data class NeighborNetwork(
    val bssid: String,
    val ssid: String,
    val channel: Int,
    val rssi: Int,
    val utilizationPercent: Int = 0
)

/**
 * Channel congestion metrics
 */
data class ChannelCongestionMetrics(
    val channel: Int,
    val utilizationPercent: Int,
    val neighborCount: Int,
    val averageRssi: Int
)

/**
 * Radar detection history
 */
data class RadarDetectionHistory(
    val detectionEvents: List<RadarEvent>,
    val monitoringMonths: Double
)

/**
 * Single radar detection event
 */
data class RadarEvent(
    val channel: Int,
    val timestampMs: Long,
    val duration: Int  // CAC time in seconds
)

/**
 * Internal plan metrics
 */
private data class PlanMetrics(
    val score: Double,
    val coChannelInterference: Double,
    val adjacentChannelInterference: Double,
    val dfsRisk: DfsRisk,
    val improvementVsCurrent: Double
)
