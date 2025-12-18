package io.lamco.netkit.optimizer.algorithm

import io.lamco.netkit.model.network.WiFiBand
import kotlin.math.max

/**
 * Client steering advisor for band and AP optimization
 *
 * Provides intelligent recommendations for steering clients between:
 * - Frequency bands (2.4 GHz → 5 GHz → 6 GHz)
 * - Access points (load balancing)
 *
 * Steering strategies:
 * - Band steering: Move dual-band clients to less congested bands
 * - AP steering: Balance load across multiple APs
 * - Smart steering: Consider signal strength, capabilities, and load
 *
 * Based on:
 * - IEEE 802.11v BSS Transition Management
 * - IEEE 802.11k Neighbor Reports
 * - Industry best practices for client distribution
 */
class ClientSteeringAdvisor {
    /**
     * Recommend band steering for dual/tri-band capable clients
     *
     * Analyzes client capabilities and current band utilization to determine
     * if the client would benefit from moving to a different frequency band.
     *
     * Band preferences (in ideal conditions):
     * 1. 6 GHz (WiFi 6E): Fastest, least congested, shortest range
     * 2. 5 GHz: Fast, moderate congestion, good range
     * 3. 2.4 GHz: Slowest, most congested, longest range
     *
     * @param client Client capabilities and supported bands
     * @param currentBand Currently connected band
     * @param availableBands Bands available on this AP
     * @param congestion Per-band congestion metrics
     * @return Band steering recommendation with rationale
     */
    fun recommendBandSteering(
        client: ClientCapabilities,
        currentBand: WiFiBand,
        availableBands: List<WiFiBand>,
        congestion: Map<WiFiBand, BandCongestionMetrics> = emptyMap(),
    ): BandSteeringRecommendation {
        // Cannot steer if client doesn't support other bands
        val clientBands = client.supportedBands.filter { it in availableBands }
        if (clientBands.size <= 1) {
            return BandSteeringRecommendation(
                shouldSteer = false,
                currentBand = currentBand,
                targetBand = currentBand,
                rationale = "Client only supports ${currentBand.displayName}",
                expectedImprovement = "",
                risks = emptyList(),
            )
        }

        val bandScores =
            clientBands.associateWith { band ->
                scoreBandForClient(
                    band = band,
                    client = client,
                    congestion = congestion[band],
                    isCurrent = band == currentBand,
                )
            }

        val bestBand = bandScores.maxByOrNull { it.value }?.key ?: currentBand

        val currentScore = bandScores[currentBand] ?: 0.0
        val bestScore = bandScores[bestBand] ?: 0.0
        val improvement = bestScore - currentScore

        val shouldSteer = improvement >= MINIMUM_STEERING_IMPROVEMENT && bestBand != currentBand

        return if (shouldSteer) {
            BandSteeringRecommendation(
                shouldSteer = true,
                currentBand = currentBand,
                targetBand = bestBand,
                rationale = buildBandSteeringRationale(currentBand, bestBand, client, congestion),
                expectedImprovement = estimateBandImprovement(currentBand, bestBand, client),
                risks = assessBandSteeringRisks(currentBand, bestBand, client),
            )
        } else {
            BandSteeringRecommendation(
                shouldSteer = false,
                currentBand = currentBand,
                targetBand = currentBand,
                rationale = "Current band (${currentBand.displayName}) is optimal",
                expectedImprovement = "",
                risks = emptyList(),
            )
        }
    }

    /**
     * Recommend AP steering for load balancing
     *
     * Analyzes load distribution across APs to determine if a client should
     * be steered to a different AP for better performance.
     *
     * Considers:
     * - AP load (client count, utilization)
     * - Signal strength to candidate APs
     * - Client capabilities (802.11v/k support)
     *
     * @param client Client metrics and capabilities
     * @param currentAp Current AP BSSID
     * @param availableAps Candidate APs with metrics
     * @return AP steering recommendation
     */
    fun recommendApSteering(
        client: ClientMetrics,
        currentAp: String,
        availableAps: List<ApMetrics>,
    ): ApSteeringRecommendation {
        require(availableAps.isNotEmpty()) {
            "Need at least one available AP"
        }

        val currentApMetrics =
            availableAps.find { it.bssid == currentAp }
                ?: return ApSteeringRecommendation(
                    shouldSteer = false,
                    currentBssid = currentAp,
                    targetBssid = currentAp,
                    rationale = "Current AP metrics unavailable",
                    expectedLoadReduction = 0.0,
                    signalImprovement = 0,
                )

        val apScores =
            availableAps.associateWith { ap ->
                scoreApForClient(
                    ap = ap,
                    client = client,
                    isCurrent = ap.bssid == currentAp,
                )
            }

        val bestAp = apScores.maxByOrNull { it.value }?.key ?: currentApMetrics
        val currentScore = apScores[currentApMetrics] ?: 0.0
        val bestScore = apScores[bestAp] ?: 0.0
        val improvement = bestScore - currentScore

        val shouldSteer =
            improvement >= MINIMUM_AP_STEERING_IMPROVEMENT &&
                bestAp.bssid != currentAp &&
                (bestAp.signalRssi ?: Int.MIN_VALUE) >= MINIMUM_STEERING_RSSI

        return if (shouldSteer) {
            val signalDiff = (bestAp.signalRssi ?: 0) - (currentApMetrics.signalRssi ?: 0)
            val loadReduction =
                (
                    (currentApMetrics.clientCount - bestAp.clientCount).toDouble() /
                        currentApMetrics.clientCount.coerceAtLeast(1)
                ) *
                    100.0

            ApSteeringRecommendation(
                shouldSteer = true,
                currentBssid = currentAp,
                targetBssid = bestAp.bssid,
                rationale = buildApSteeringRationale(currentApMetrics, bestAp),
                expectedLoadReduction = loadReduction.coerceAtLeast(0.0),
                signalImprovement = signalDiff,
            )
        } else {
            ApSteeringRecommendation(
                shouldSteer = false,
                currentBssid = currentAp,
                targetBssid = currentAp,
                rationale = "Current AP is optimal for this client",
                expectedLoadReduction = 0.0,
                signalImprovement = 0,
            )
        }
    }

    /**
     * Analyze steering effectiveness over time
     *
     * Evaluates historical steering events to determine if steering
     * is working as intended or if adjustments are needed.
     *
     * @param steeringEvents Historical steering attempts
     * @return Effectiveness analysis with metrics
     */
    fun analyzeSteeringEffectiveness(steeringEvents: List<SteeringEvent>): SteeringEffectivenessResult {
        if (steeringEvents.isEmpty()) {
            return SteeringEffectivenessResult(
                totalAttempts = 0,
                successfulSteers = 0,
                successRate = 0.0,
                averageImprovementPercent = 0.0,
                issues = listOf("No steering data available"),
                recommendations = listOf("Enable steering and collect data"),
            )
        }

        val successful = steeringEvents.count { it.wasSuccessful }
        val successRate = (successful.toDouble() / steeringEvents.size) * 100.0

        val improvements =
            steeringEvents
                .filter { it.wasSuccessful && it.actualImprovement != null }
                .mapNotNull { it.actualImprovement }

        val avgImprovement = if (improvements.isNotEmpty()) improvements.average() else 0.0

        val issues = mutableListOf<String>()
        if (successRate < 50.0) {
            issues.add("Low success rate (${successRate.toInt()}%) - clients refusing to steer")
        }
        if (avgImprovement < 10.0 && improvements.isNotEmpty()) {
            issues.add("Low actual improvement (${avgImprovement.toInt()}%) - steering criteria may be too aggressive")
        }

        val stickyClients =
            steeringEvents
                .filter { !it.wasSuccessful }
                .groupingBy { it.clientMac }
                .eachCount()
                .filter { it.value >= 3 }

        if (stickyClients.isNotEmpty()) {
            issues.add("${stickyClients.size} sticky clients detected (refusing multiple steering attempts)")
        }

        val recommendations =
            buildSteeringRecommendations(
                successRate = successRate,
                avgImprovement = avgImprovement,
                stickyClientCount = stickyClients.size,
            )

        return SteeringEffectivenessResult(
            totalAttempts = steeringEvents.size,
            successfulSteers = successful,
            successRate = successRate,
            averageImprovementPercent = avgImprovement,
            issues = issues,
            recommendations = recommendations,
        )
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Score a band for a specific client
     *
     * Factors:
     * - Band capacity (wider channels = higher capacity)
     * - Congestion level (fewer devices = better)
     * - Client capabilities (WiFi 6/6E features)
     * - Range requirements (2.4 GHz better for distant clients)
     */
    private fun scoreBandForClient(
        band: WiFiBand,
        client: ClientCapabilities,
        congestion: BandCongestionMetrics?,
        isCurrent: Boolean,
    ): Double {
        var score = 50.0

        score +=
            when (band) {
                WiFiBand.BAND_6GHZ -> 30.0
                WiFiBand.BAND_5GHZ -> 20.0
                WiFiBand.BAND_2_4GHZ -> 10.0
                else -> 0.0
            }

        if (congestion != null) {
            score -= congestion.utilizationPercent * 0.3
            score -= congestion.clientCount * 2.0
        }

        // Client capability bonus (can utilize band features)
        if (band == WiFiBand.BAND_6GHZ && client.supportsWiFi6E) {
            score += 15.0
        } else if (band == WiFiBand.BAND_5GHZ && client.supportsWiFi6) {
            score += 10.0
        }

        if (client.signalStrength < -70 && band == WiFiBand.BAND_2_4GHZ) {
            // Weak signal clients benefit from 2.4 GHz range
            score += 15.0
        }

        // Current band penalty (prefer to steer only if significantly better)
        if (isCurrent) {
            score += 10.0 // Slight bonus to stay put (hysteresis)
        }

        return score.coerceIn(0.0, 100.0)
    }

    /**
     * Score an AP for a client
     */
    private fun scoreApForClient(
        ap: ApMetrics,
        client: ClientMetrics,
        isCurrent: Boolean,
    ): Double {
        var score = 50.0

        // Signal strength (most important factor)
        val rssi = ap.signalRssi ?: -80
        score +=
            when {
                rssi >= -60 -> 30.0
                rssi >= -70 -> 20.0
                rssi >= -75 -> 10.0
                else -> 0.0
            }

        val loadFactor = ap.utilizationPercent / 100.0
        score -= loadFactor * 20.0

        val clientFactor = ap.clientCount / 20.0 // Normalize to ~20 clients
        score -= clientFactor * 15.0

        // Current AP bonus (hysteresis to prevent ping-ponging)
        if (isCurrent) {
            score += 15.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun buildBandSteeringRationale(
        current: WiFiBand,
        target: WiFiBand,
        client: ClientCapabilities,
        congestion: Map<WiFiBand, BandCongestionMetrics>,
    ): String {
        val currentCongestion = congestion[current]?.utilizationPercent ?: 0
        val targetCongestion = congestion[target]?.utilizationPercent ?: 0

        return buildString {
            append("Steer from ${current.displayName} to ${target.displayName}")

            when {
                target == WiFiBand.BAND_6GHZ && client.supportsWiFi6E ->
                    append(" - utilize WiFi 6E capabilities for maximum performance")
                targetCongestion < currentCongestion - 20 ->
                    append(" - reduce congestion (${current.displayName}: $currentCongestion%, ${target.displayName}: $targetCongestion%)")
                target == WiFiBand.BAND_5GHZ && current == WiFiBand.BAND_2_4GHZ ->
                    append(" - better throughput and less interference")
                else ->
                    append(" - improved performance expected")
            }
        }
    }

    private fun estimateBandImprovement(
        current: WiFiBand,
        target: WiFiBand,
        client: ClientCapabilities,
    ): String {
        val speedup =
            when {
                current == WiFiBand.BAND_2_4GHZ && target == WiFiBand.BAND_5GHZ -> "2-3x"
                current == WiFiBand.BAND_2_4GHZ && target == WiFiBand.BAND_6GHZ -> "4-5x"
                current == WiFiBand.BAND_5GHZ && target == WiFiBand.BAND_6GHZ -> "1.5-2x"
                else -> "10-20%"
            }

        return "$speedup faster throughput expected"
    }

    private fun assessBandSteeringRisks(
        current: WiFiBand,
        target: WiFiBand,
        client: ClientCapabilities,
    ): List<String> {
        val risks = mutableListOf<String>()

        if (target != WiFiBand.BAND_2_4GHZ && client.signalStrength < -70) {
            risks.add("May reduce range/reliability due to weak signal")
        }

        if (target == WiFiBand.BAND_6GHZ) {
            risks.add("6 GHz has shortest range, may lose connection if client moves")
        }

        if (target == WiFiBand.BAND_5GHZ && current == WiFiBand.BAND_2_4GHZ) {
            risks.add("5 GHz has reduced wall penetration")
        }

        return risks
    }

    private fun buildApSteeringRationale(
        current: ApMetrics,
        target: ApMetrics,
    ): String {
        val signalDiff = (target.signalRssi ?: 0) - (current.signalRssi ?: 0)
        val loadDiff = current.clientCount - target.clientCount

        return buildString {
            append("Steer to ${target.bssid}")
            when {
                signalDiff >= 10 -> append(" - much stronger signal (+${signalDiff}dB)")
                signalDiff >= 5 -> append(" - stronger signal (+${signalDiff}dB)")
                loadDiff >= 5 -> append(" - better load distribution ($loadDiff fewer clients)")
                else -> append(" - improved overall performance")
            }
        }
    }

    private fun buildSteeringRecommendations(
        successRate: Double,
        avgImprovement: Double,
        stickyClientCount: Int,
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (successRate < 50.0) {
            recommendations.add("Enable 802.11v BSS Transition Management for better client cooperation")
            recommendations.add("Increase steering RSSI threshold (clients may see better signal on current AP)")
        }

        if (avgImprovement < 10.0) {
            recommendations.add("Tighten steering criteria - only steer when improvement is >20%")
        }

        if (stickyClientCount > 0) {
            recommendations.add("Identify and whitelist sticky clients that refuse steering")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Steering is effective - continue current strategy")
        }

        return recommendations
    }

    companion object {
        private const val MINIMUM_STEERING_IMPROVEMENT = 15.0 // Minimum score improvement to steer
        private const val MINIMUM_AP_STEERING_IMPROVEMENT = 10.0
        private const val MINIMUM_STEERING_RSSI = -75 // Don't steer to APs weaker than this
    }
}

// ========================================
// Data Classes
// ========================================

data class ClientCapabilities(
    val supportedBands: List<WiFiBand>,
    val supportsWiFi6: Boolean,
    val supportsWiFi6E: Boolean,
    val supports80211v: Boolean, // BSS Transition Management
    val supports80211k: Boolean, // Neighbor Report
    val signalStrength: Int,
)

data class BandCongestionMetrics(
    val band: WiFiBand,
    val utilizationPercent: Int,
    val clientCount: Int,
    val interferenceLevel: Double,
)

data class BandSteeringRecommendation(
    val shouldSteer: Boolean,
    val currentBand: WiFiBand,
    val targetBand: WiFiBand,
    val rationale: String,
    val expectedImprovement: String,
    val risks: List<String>,
)

data class ApMetrics(
    val bssid: String,
    val signalRssi: Int?,
    val clientCount: Int,
    val utilizationPercent: Int,
    val channel: Int,
)

data class ApSteeringRecommendation(
    val shouldSteer: Boolean,
    val currentBssid: String,
    val targetBssid: String,
    val rationale: String,
    val expectedLoadReduction: Double, // Percentage
    val signalImprovement: Int, // dB
)

data class SteeringEvent(
    val clientMac: String,
    val timestampMs: Long,
    val fromBssid: String,
    val toBssid: String,
    val wasSuccessful: Boolean,
    val actualImprovement: Double?, // Percentage
)

data class SteeringEffectivenessResult(
    val totalAttempts: Int,
    val successfulSteers: Int,
    val successRate: Double, // Percentage
    val averageImprovementPercent: Double,
    val issues: List<String>,
    val recommendations: List<String>,
)
