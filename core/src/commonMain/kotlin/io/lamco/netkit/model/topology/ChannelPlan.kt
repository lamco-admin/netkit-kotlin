package io.lamco.netkit.model.topology

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand

/**
 * Channel usage information for a specific channel in a band
 *
 * Tracks which APs are using a particular channel and the RF metrics
 * associated with that channel usage.
 *
 * @property band Frequency band (2.4/5/6/60 GHz)
 * @property channel Primary channel number
 * @property channelWidth Channel width in use
 * @property apClusterIds AP cluster IDs using this channel
 * @property bssids BSSIDs using this channel
 * @property averageRssiDbm Average RSSI across all BSSs on this channel
 * @property maxRssiDbm Strongest signal on this channel
 * @property utilizationPercent Channel utilization (0-100%)
 * @property has11bLegacy Whether legacy 802.11b devices detected
 */
data class ChannelUsage(
    val band: WiFiBand,
    val channel: Int,
    val channelWidth: ChannelWidth,
    val apClusterIds: Set<String>,
    val bssids: Set<String>,
    val averageRssiDbm: Double,
    val maxRssiDbm: Int,
    val utilizationPercent: Int = 0,
    val has11bLegacy: Boolean = false,
) {
    init {
        require(channel > 0) {
            "Channel must be positive, got $channel"
        }
        require(utilizationPercent in 0..100) {
            "Utilization must be 0-100%, got $utilizationPercent"
        }
        require(maxRssiDbm in -120..0) {
            "Max RSSI must be in range [-120, 0] dBm, got $maxRssiDbm"
        }
        require(bssids.isNotEmpty()) {
            "Channel usage must have at least one BSSID"
        }
    }

    /**
     * Number of access points on this channel
     */
    val apCount: Int
        get() = bssids.size

    /**
     * Number of distinct AP clusters using this channel
     */
    val clusterCount: Int
        get() = apClusterIds.size

    /**
     * Whether this channel is congested (>= 4 APs or >= 50% utilization)
     */
    val isCongested: Boolean
        get() = apCount >= 4 || utilizationPercent >= 50

    /**
     * Whether this channel is heavily congested (>= 8 APs or >= 75% utilization)
     */
    val isHeavilyCongested: Boolean
        get() = apCount >= 8 || utilizationPercent >= 75

    /**
     * Channel quality score (0.0-1.0)
     * Lower is better (less congestion)
     */
    val congestionScore: Double
        get() {
            var score = 1.0

            // AP count penalty (exponential)
            score *= kotlin.math.exp(-apCount * 0.2)

            // Utilization penalty
            score *= (100 - utilizationPercent) / 100.0

            // Legacy 802.11b penalty (slows down entire channel)
            if (has11bLegacy) score *= 0.7

            // Strong signal penalty (more interference)
            if (maxRssiDbm > -50) score *= 0.8

            return score.coerceIn(0.0, 1.0)
        }

    /**
     * Channel congestion category
     */
    val congestionLevel: CongestionLevel
        get() =
            when {
                congestionScore >= 0.7 -> CongestionLevel.LOW
                congestionScore >= 0.5 -> CongestionLevel.MODERATE
                congestionScore >= 0.3 -> CongestionLevel.HIGH
                else -> CongestionLevel.VERY_HIGH
            }

    /**
     * Whether this channel is recommended for use
     * (Low congestion, no legacy devices, moderate utilization)
     */
    val isRecommended: Boolean
        get() = !isCongested && !has11bLegacy && congestionScore >= 0.6
}

/**
 * Channel congestion levels
 */
enum class CongestionLevel(
    val displayName: String,
) {
    /** Low congestion (score >= 0.7) */
    LOW("Low Congestion"),

    /** Moderate congestion (score 0.5-0.7) */
    MODERATE("Moderate Congestion"),

    /** High congestion (score 0.3-0.5) */
    HIGH("High Congestion"),

    /** Very high congestion (score < 0.3) */
    VERY_HIGH("Very High Congestion"),
}

/**
 * Channel plan issue detected in the topology
 *
 * Identifies problems in how APs are configured across channels,
 * which can lead to poor performance or roaming issues.
 *
 * @property severity How serious this issue is
 * @property issueType Category of issue
 * @property description Human-readable description
 * @property affectedChannels Channels affected by this issue
 * @property affectedBssids BSSIDs involved in this issue
 * @property recommendation How to fix this issue
 */
data class ChannelPlanIssue(
    val severity: IssueSeverity,
    val issueType: ChannelIssueType,
    val description: String,
    val affectedChannels: Set<Int>,
    val affectedBssids: Set<String>,
    val recommendation: String,
) {
    init {
        require(description.isNotBlank()) {
            "Description must not be blank"
        }
        require(recommendation.isNotBlank()) {
            "Recommendation must not be blank"
        }
    }
}

/**
 * Issue severity levels
 */
enum class IssueSeverity(
    val displayName: String,
) {
    /** Critical: Major performance impact */
    CRITICAL("Critical"),

    /** High: Significant performance impact */
    HIGH("High"),

    /** Medium: Moderate performance impact */
    MEDIUM("Medium"),

    /** Low: Minor performance impact */
    LOW("Low"),

    /** Info: No impact, informational */
    INFO("Info"),
}

/**
 * Types of channel plan issues
 */
enum class ChannelIssueType(
    val displayName: String,
) {
    /** APs on overlapping 2.4 GHz channels */
    OVERLAPPING_CHANNELS("Overlapping Channels"),

    /** Same channel used by multiple APs in cluster */
    CHANNEL_REUSE("Channel Reuse"),

    /** 40 MHz channels in 2.4 GHz band */
    WIDE_CHANNEL_24GHZ("Wide Channel in 2.4 GHz"),

    /** DFS channel in use (may cause interruptions) */
    DFS_CHANNEL("DFS Channel"),

    /** Legacy 802.11b device on network */
    LEGACY_DEVICES("Legacy 802.11b Devices"),

    /** Channel heavily congested */
    CHANNEL_CONGESTION("Channel Congestion"),

    /** Poor channel selection (non-standard channels) */
    NON_STANDARD_CHANNEL("Non-Standard Channel"),

    /** All APs on same channel (no band steering) */
    ALL_SAME_CHANNEL("All APs Same Channel"),
}

/**
 * Complete channel plan report for an AP cluster
 *
 * Analyzes the channel allocation across all APs in a cluster
 * and identifies optimization opportunities.
 *
 * @property ssid Network SSID
 * @property clusters AP clusters analyzed
 * @property perBandChannelUsage Channel usage broken down by band
 * @property channelPlanScore Overall channel plan quality (0.0-1.0)
 * @property issues List of detected issues
 */
data class ChannelPlanReport(
    val ssid: String,
    val clusters: List<ApCluster>,
    val perBandChannelUsage: List<ChannelUsage>,
    val channelPlanScore: Double,
    val issues: List<ChannelPlanIssue>,
) {
    init {
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
        require(channelPlanScore in 0.0..1.0) {
            "Channel plan score must be 0.0-1.0, got $channelPlanScore"
        }
    }

    /**
     * Number of critical issues
     */
    val criticalIssueCount: Int
        get() = issues.count { it.severity == IssueSeverity.CRITICAL }

    /**
     * Number of high severity issues
     */
    val highIssueCount: Int
        get() = issues.count { it.severity == IssueSeverity.HIGH }

    /**
     * Whether the channel plan is optimal (score >= 0.8, no critical issues)
     */
    val isOptimal: Boolean
        get() = channelPlanScore >= 0.8 && criticalIssueCount == 0

    /**
     * Whether the channel plan needs improvement
     */
    val needsImprovement: Boolean
        get() = channelPlanScore < 0.6 || criticalIssueCount > 0

    /**
     * Channel plan quality category
     */
    val quality: ChannelPlanQuality
        get() =
            when {
                channelPlanScore >= 0.8 && criticalIssueCount == 0 -> ChannelPlanQuality.EXCELLENT
                channelPlanScore >= 0.6 && highIssueCount == 0 -> ChannelPlanQuality.GOOD
                channelPlanScore >= 0.4 -> ChannelPlanQuality.FAIR
                else -> ChannelPlanQuality.POOR
            }

    /**
     * Total number of APs across all clusters
     */
    val totalApCount: Int
        get() = clusters.sumOf { it.apCount }

    /**
     * Bands in use across all clusters
     */
    val usedBands: Set<WiFiBand>
        get() = perBandChannelUsage.map { it.band }.toSet()

    /**
     * Average congestion score across all channels
     */
    val averageCongestionScore: Double
        get() = perBandChannelUsage.map { it.congestionScore }.average()

    /**
     * Most congested channel
     */
    val mostCongestedChannel: ChannelUsage?
        get() = perBandChannelUsage.minByOrNull { it.congestionScore }

    /**
     * Least congested channel
     */
    val leastCongestedChannel: ChannelUsage?
        get() = perBandChannelUsage.maxByOrNull { it.congestionScore }
}

/**
 * Channel plan quality categories
 */
enum class ChannelPlanQuality(
    val displayName: String,
) {
    /** Excellent: Optimal channel allocation, no issues */
    EXCELLENT("Excellent"),

    /** Good: Minor issues, overall good plan */
    GOOD("Good"),

    /** Fair: Some issues, could be improved */
    FAIR("Fair"),

    /** Poor: Major issues, needs reconfiguration */
    POOR("Poor"),
}
