package io.lamco.netkit.rf.model

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand

/**
 * Channel congestion and interference metrics
 *
 * Analyzes channel quality by measuring co-channel and adjacent-channel interference,
 * BSS Load utilization, and legacy client impact.
 *
 * Key metrics:
 * - **Congestion Score**: Overall channel quality (0.0 = congested, 1.0 = clear)
 * - **Interference Score**: Adjacent channel interference (0.0 = high, 1.0 = minimal)
 * - **BSS Count**: Number of APs on same/adjacent channels
 * - **Channel Utilization**: Airtime usage percentage (from BSS Load IE)
 *
 * @property band WiFi frequency band
 * @property channel Primary channel number
 * @property primaryWidth Channel width (20/40/80/160/320 MHz)
 * @property coChannelBssCount Number of BSSs on same primary channel
 * @property adjChannelBssCount Number of BSSs on adjacent/overlapping channels
 * @property avgRssiDbm Average RSSI of all BSSs on this channel (null if none)
 * @property totalChannelUtilizationPct Total airtime usage 0-100% (null if unavailable)
 * @property legacy11bPresent Whether legacy 802.11b clients detected
 * @property congestionScore Overall congestion (0.0 = bad, 1.0 = excellent)
 * @property interferenceScore Interference level (0.0 = high, 1.0 = clean)
 * @property recommended Whether this channel is recommended for use
 */
data class ChannelMetrics(
    val band: WiFiBand,
    val channel: Int,
    val primaryWidth: ChannelWidth,
    val coChannelBssCount: Int,
    val adjChannelBssCount: Int,
    val avgRssiDbm: Double? = null,
    val totalChannelUtilizationPct: Double? = null,
    val legacy11bPresent: Boolean = false,
    val congestionScore: Double,
    val interferenceScore: Double,
    val recommended: Boolean,
) {
    init {
        require(channel > 0) { "Channel must be positive: $channel" }
        require(coChannelBssCount >= 0) { "Co-channel BSS count cannot be negative" }
        require(adjChannelBssCount >= 0) { "Adjacent channel BSS count cannot be negative" }
        require(congestionScore in 0.0..1.0) { "Congestion score must be 0.0-1.0: $congestionScore" }
        require(interferenceScore in 0.0..1.0) { "Interference score must be 0.0-1.0: $interferenceScore" }
        totalChannelUtilizationPct?.let {
            require(it in 0.0..100.0) { "Utilization must be 0-100%: $it" }
        }
        avgRssiDbm?.let {
            require(it in -120.0..0.0) { "Average RSSI must be -120 to 0 dBm: $it" }
        }
    }

    /**
     * Total BSS count (co-channel + adjacent)
     */
    val totalBssCount: Int
        get() = coChannelBssCount + adjChannelBssCount

    /**
     * Overall channel quality score (0.0-1.0)
     * Combines congestion and interference with equal weighting
     */
    val overallQuality: Double
        get() = (congestionScore + interferenceScore) / 2.0

    /**
     * Channel quality category
     */
    val qualityCategory: ChannelQuality
        get() =
            when {
                overallQuality >= 0.8 -> ChannelQuality.EXCELLENT
                overallQuality >= 0.6 -> ChannelQuality.GOOD
                overallQuality >= 0.4 -> ChannelQuality.FAIR
                overallQuality >= 0.2 -> ChannelQuality.POOR
                else -> ChannelQuality.VERY_POOR
            }

    /**
     * Whether this channel is crowded (>= 5 co-channel BSSs)
     */
    val isCrowded: Boolean
        get() = coChannelBssCount >= 5

    /**
     * Whether this channel has heavy interference
     */
    val hasHeavyInterference: Boolean
        get() = interferenceScore < 0.4

    /**
     * Whether this channel is suitable for high-performance applications
     * Requires low congestion, low interference, and no legacy clients
     */
    val suitableForHighPerformance: Boolean
        get() =
            congestionScore >= 0.7 &&
                interferenceScore >= 0.7 &&
                !legacy11bPresent &&
                (totalChannelUtilizationPct?.let { it <= 60.0 } ?: true)

    /**
     * Estimated capacity reduction due to channel conditions (0.0-1.0)
     * - 1.0 = no reduction (ideal channel)
     * - 0.5 = 50% capacity reduction
     * - 0.0 = unusable channel
     */
    val capacityReductionFactor: Double
        get() {
            var factor = 1.0

            // Congestion impact
            factor *= congestionScore

            // Utilization impact (if known)
            totalChannelUtilizationPct?.let { util ->
                factor *= (1.0 - util / 100.0)
            }

            // Legacy client penalty (reduces airtime efficiency)
            if (legacy11bPresent) {
                factor *= 0.7 // 30% penalty
            }

            return factor.coerceIn(0.0, 1.0)
        }
}

/**
 * Channel quality categories
 *
 * @property displayName Human-readable name
 * @property description Quality description
 */
enum class ChannelQuality(
    val displayName: String,
    val description: String,
) {
    EXCELLENT(
        displayName = "Excellent",
        description = "Minimal interference, low congestion, optimal for all applications",
    ),
    GOOD(
        displayName = "Good",
        description = "Low interference, manageable congestion, suitable for high-performance",
    ),
    FAIR(
        displayName = "Fair",
        description = "Moderate interference/congestion, adequate for most uses",
    ),
    POOR(
        displayName = "Poor",
        description = "High interference/congestion, reduced performance expected",
    ),
    VERY_POOR(
        displayName = "Very Poor",
        description = "Severe interference/congestion, consider switching channels",
    ),
    ;

    /**
     * Numeric score (0-100) for this quality level
     */
    val score: Int
        get() =
            when (this) {
                EXCELLENT -> 100
                GOOD -> 80
                FAIR -> 60
                POOR -> 40
                VERY_POOR -> 20
            }
}
