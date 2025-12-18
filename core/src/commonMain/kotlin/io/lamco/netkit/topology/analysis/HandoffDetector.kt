package io.lamco.netkit.topology.analysis

import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.topology.*

/**
 * Handoff and transition zone detector
 *
 * Identifies zones where roaming between APs is likely to occur.
 * Analyzes signal overlap, historical roaming events, and predicts
 * optimal handoff points.
 *
 * @property roamingThreshold RSSI threshold that triggers roaming consideration (default: -70 dBm)
 * @property hysteresis RSSI hysteresis to prevent ping-ponging (default: 5 dB)
 */
class HandoffDetector(
    private val roamingThreshold: Int = -70,
    private val hysteresis: Int = 5,
) {
    init {
        require(roamingThreshold in -100..-30) {
            "Roaming threshold must be between -100 and -30 dBm, got: $roamingThreshold"
        }
        require(hysteresis >= 0) {
            "Hysteresis must be non-negative, got: $hysteresis"
        }
    }

    /**
     * Detect transition zones between APs in a cluster
     *
     * @param cluster AP cluster to analyze
     * @return List of identified transition zones
     */
    fun detectTransitionZones(cluster: ApCluster): List<TransitionZone> {
        val zones = mutableListOf<TransitionZone>()
        val bssList = cluster.bssids

        // Analyze all AP pairs
        for (i in bssList.indices) {
            for (j in (i + 1) until bssList.size) {
                val bss1 = bssList[i]
                val bss2 = bssList[j]

                // Only consider same-band APs for transition zones
                if (bss1.band == bss2.band) {
                    val zone = analyzeApPair(bss1, bss2, cluster)
                    if (zone != null) {
                        zones.add(zone)
                    }
                }
            }
        }

        return zones.sortedByDescending { it.handoffLikelihood }
    }

    /**
     * Predict handoff likelihood at current signal conditions
     *
     * @param currentBssid Currently connected BSSID
     * @param currentRssi Current RSSI from connected AP
     * @param targetBssid Target BSSID for potential handoff
     * @param targetRssi RSSI from target AP
     * @return Handoff likelihood (0.0 to 1.0)
     */
    fun predictHandoffLikelihood(
        currentBssid: String,
        currentRssi: Int,
        targetBssid: String,
        targetRssi: Int,
    ): Double {
        // Don't handoff if current signal is strong
        if (currentRssi >= roamingThreshold) {
            return 0.0
        }

        // Calculate RSSI differential
        val differential = targetRssi - currentRssi

        // Need significant improvement to trigger handoff (hysteresis)
        if (differential < hysteresis) {
            return 0.0
        }

        // Likelihood increases with differential
        val likelihood =
            when {
                differential >= 20 -> 1.0 // Very likely
                differential >= 15 -> 0.8 // Likely
                differential >= 10 -> 0.6 // Moderate
                differential >= hysteresis -> 0.3 // Possible
                else -> 0.0
            }

        return likelihood
    }

    /**
     * Analyze historical roaming events to identify patterns
     *
     * @param events Historical roaming events
     * @return Roaming pattern analysis
     */
    fun analyzeRoamingPatterns(events: List<RoamingEvent>): RoamingPatternAnalysis {
        if (events.isEmpty()) {
            return RoamingPatternAnalysis(
                totalEvents = 0,
                successfulRoams = 0,
                failedRoams = 0,
                averageDurationMs = 0,
                averageRssiImprovement = 0,
                commonTransitions = emptyList(),
                peakRoamingHours = emptyList(),
            )
        }

        val successful = events.count { it.isSuccessful }
        val failed = events.size - successful
        val avgDuration = events.mapNotNull { it.durationMs }.average().toLong()
        val avgImprovement = events.mapNotNull { it.rssiImprovement }.average().toInt()

        // Find common transitions (BSSID pairs) - filter for actual roaming events only
        val transitions =
            events
                .filter { it.fromBssid != null && it.toBssid != null }
                .groupBy { Pair(it.fromBssid!!, it.toBssid!!) }
                .map { (pair, evts) ->
                    TransitionPattern(
                        fromBssid = pair.first,
                        toBssid = pair.second,
                        count = evts.size,
                        averageRssi = evts.mapNotNull { it.rssiBeforeDbm }.average().toInt(),
                        successRate = evts.count { it.isSuccessful }.toDouble() / evts.size,
                    )
                }.sortedByDescending { it.count }
                .take(10)

        return RoamingPatternAnalysis(
            totalEvents = events.size,
            successfulRoams = successful,
            failedRoams = failed,
            averageDurationMs = avgDuration,
            averageRssiImprovement = avgImprovement,
            commonTransitions = transitions,
            peakRoamingHours = emptyList(), // Would require timestamp analysis
        )
    }

    /**
     * Detect if device is currently in a transition zone
     *
     * @param cluster AP cluster
     * @param currentBssid Current BSSID
     * @param currentRssi Current RSSI
     * @return Transition zone info if in zone, null otherwise
     */
    fun detectCurrentTransitionZone(
        cluster: ApCluster,
        currentBssid: String,
        currentRssi: Int,
    ): TransitionZone? {
        // Check if current signal is weak enough to be in transition
        if (currentRssi >= roamingThreshold) {
            return null
        }

        // Find best alternative AP
        val alternatives =
            cluster.bssids
                .filter { it.bssid != currentBssid }
                .filter {
                    val rssiValue = it.rssiDbm
                    rssiValue != null && rssiValue >= currentRssi + hysteresis
                }

        if (alternatives.isEmpty()) {
            return null
        }

        val bestAlternative = alternatives.maxByOrNull { it.rssiDbm ?: -999 }!!
        val currentBss = cluster.bssids.find { it.bssid == currentBssid }!!

        return analyzeApPair(currentBss, bestAlternative, cluster)
    }

    /**
     * Recommend optimal handoff timing
     *
     * @param currentRssi Current RSSI
     * @param targetRssi Target AP RSSI
     * @param fastRoamingSupported Whether fast roaming is supported
     * @return Handoff recommendation
     */
    fun recommendHandoffTiming(
        currentRssi: Int,
        targetRssi: Int,
        fastRoamingSupported: Boolean,
    ): HandoffRecommendation {
        val differential = targetRssi - currentRssi

        val urgency =
            when {
                currentRssi < -80 -> HandoffUrgency.CRITICAL
                currentRssi < -75 -> HandoffUrgency.HIGH
                currentRssi < -70 -> HandoffUrgency.MEDIUM
                currentRssi < -65 && differential >= 10 -> HandoffUrgency.LOW
                else -> HandoffUrgency.NONE
            }

        val shouldHandoff =
            when (urgency) {
                HandoffUrgency.CRITICAL -> true
                HandoffUrgency.HIGH -> differential >= hysteresis
                HandoffUrgency.MEDIUM -> differential >= hysteresis + 2
                HandoffUrgency.LOW -> differential >= hysteresis + 5
                HandoffUrgency.NONE -> false
            }

        val reason =
            when {
                !shouldHandoff -> "Current signal is acceptable"
                urgency == HandoffUrgency.CRITICAL -> "Critical: Signal very weak ($currentRssi dBm)"
                differential >= 20 -> "Target significantly stronger (+$differential dB)"
                differential >= 10 -> "Target stronger (+$differential dB)"
                else -> "Signal below threshold"
            }

        return HandoffRecommendation(
            shouldHandoff = shouldHandoff,
            urgency = urgency,
            reason = reason,
            estimatedLatencyMs = if (fastRoamingSupported) 50 else 300,
            confidenceScore = calculateConfidence(currentRssi, targetRssi, differential),
        )
    }

    // Private helper methods

    private fun analyzeApPair(
        bss1: ClusteredBss,
        bss2: ClusteredBss,
        cluster: ApCluster,
    ): TransitionZone? {
        val rssi1 = bss1.rssiDbm ?: return null
        val rssi2 = bss2.rssiDbm ?: return null

        // Calculate signal overlap
        val overlap = calculateSignalOverlap(rssi1, rssi2)

        // Both signals must be reasonably strong for a valid transition zone
        if (rssi1 < -85 && rssi2 < -85) {
            return null
        }

        val likelihood =
            when {
                overlap > 0.7 -> 0.9 // High overlap
                overlap > 0.5 -> 0.7 // Moderate overlap
                overlap > 0.3 -> 0.4 // Low overlap
                else -> 0.1 // Minimal overlap
            }

        val expectedDurationMs =
            if (cluster.supportsFastRoaming) {
                50L
            } else {
                300L
            }

        return TransitionZone(
            fromBssid = bss1.bssid,
            toBssid = bss2.bssid,
            band = bss1.band,
            overlapPercentage = overlap,
            handoffLikelihood = likelihood,
            expectedDurationMs = expectedDurationMs,
            recommendedThreshold = roamingThreshold - 5,
        )
    }

    private fun calculateSignalOverlap(
        rssi1: Int,
        rssi2: Int,
    ): Double {
        val stronger = maxOf(rssi1, rssi2)
        val weaker = minOf(rssi1, rssi2)
        val differential = stronger - weaker

        // Less differential = more overlap
        return when {
            differential <= 3 -> 1.0
            differential <= 5 -> 0.8
            differential <= 10 -> 0.5
            differential <= 15 -> 0.3
            else -> 0.1
        }
    }

    private fun calculateConfidence(
        currentRssi: Int,
        targetRssi: Int,
        differential: Int,
    ): Double {
        var confidence = 0.5 // Base confidence

        // Higher differential increases confidence
        if (differential >= 15) {
            confidence += 0.3
        } else if (differential >= 10) {
            confidence += 0.2
        } else if (differential >= 5) {
            confidence += 0.1
        }

        // Target signal strength affects confidence
        if (targetRssi >= -60) {
            confidence += 0.2
        } else if (targetRssi >= -70) {
            confidence += 0.1
        }

        // Current signal weakness affects confidence
        if (currentRssi < -80) confidence += 0.1

        return confidence.coerceIn(0.0, 1.0)
    }
}

/**
 * Transition zone between two APs
 */
data class TransitionZone(
    val fromBssid: String,
    val toBssid: String,
    val band: WiFiBand,
    val overlapPercentage: Double,
    val handoffLikelihood: Double,
    val expectedDurationMs: Long,
    val recommendedThreshold: Int,
) {
    /** Quality of the transition zone */
    val quality: TransitionQuality
        get() =
            when {
                overlapPercentage >= 0.7 && handoffLikelihood >= 0.8 -> TransitionQuality.EXCELLENT
                overlapPercentage >= 0.5 && handoffLikelihood >= 0.6 -> TransitionQuality.GOOD
                overlapPercentage >= 0.3 -> TransitionQuality.FAIR
                else -> TransitionQuality.POOR
            }
}

/**
 * Transition zone quality categories
 */
enum class TransitionQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}

/**
 * Roaming pattern analysis from historical events
 */
data class RoamingPatternAnalysis(
    val totalEvents: Int,
    val successfulRoams: Int,
    val failedRoams: Int,
    val averageDurationMs: Long,
    val averageRssiImprovement: Int,
    val commonTransitions: List<TransitionPattern>,
    val peakRoamingHours: List<Int>,
) {
    /** Success rate percentage */
    val successRate: Double
        get() =
            if (totalEvents > 0) {
                (successfulRoams.toDouble() / totalEvents) * 100.0
            } else {
                0.0
            }

    /** Overall roaming quality */
    val overallQuality: RoamingQuality
        get() =
            when {
                successRate >= 95 && averageDurationMs <= 100 -> RoamingQuality.EXCELLENT
                successRate >= 90 && averageDurationMs <= 300 -> RoamingQuality.VERY_GOOD
                successRate >= 80 -> RoamingQuality.GOOD
                successRate >= 70 -> RoamingQuality.FAIR
                else -> RoamingQuality.POOR
            }
}

/**
 * Roaming quality categories
 */
enum class RoamingQuality {
    EXCELLENT,
    VERY_GOOD,
    GOOD,
    FAIR,
    POOR,
}

/**
 * Common transition pattern between two BSSIDs
 */
data class TransitionPattern(
    val fromBssid: String,
    val toBssid: String,
    val count: Int,
    val averageRssi: Int,
    val successRate: Double,
)

/**
 * Handoff recommendation
 */
data class HandoffRecommendation(
    val shouldHandoff: Boolean,
    val urgency: HandoffUrgency,
    val reason: String,
    val estimatedLatencyMs: Int,
    val confidenceScore: Double,
) {
    /** Whether handoff is highly recommended */
    val isHighlyRecommended: Boolean
        get() = shouldHandoff && urgency >= HandoffUrgency.HIGH && confidenceScore >= 0.7
}

/**
 * Handoff urgency levels
 */
enum class HandoffUrgency {
    /** No handoff needed */
    NONE,

    /** Low urgency - optional optimization */
    LOW,

    /** Medium urgency - recommended */
    MEDIUM,

    /** High urgency - strongly recommended */
    HIGH,

    /** Critical - handoff immediately */
    CRITICAL,
}
