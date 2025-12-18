package io.lamco.netkit.topology.analysis

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.topology.*

/**
 * Channel plan quality analyzer
 *
 * Analyzes channel allocation across APs and identifies issues like
 * overlapping channels, congestion, and sub-optimal configurations.
 */
class ChannelPlanAnalyzer {
    /**
     * Analyze channel plan for a cluster
     *
     * @param cluster AP cluster to analyze
     * @return Channel plan report with issues and recommendations
     */
    fun analyzeChannelPlan(cluster: ApCluster): ChannelPlanReport {
        val channelUsages = buildChannelUsageMap(cluster)
        val issues = detectChannelIssues(cluster, channelUsages)
        val score = calculateChannelPlanScore(cluster, issues)

        return ChannelPlanReport(
            ssid = cluster.ssid,
            clusters = listOf(cluster),
            perBandChannelUsage = channelUsages,
            channelPlanScore = score,
            issues = issues,
        )
    }

    /**
     * Analyze channel plan across multiple clusters (all networks)
     */
    fun analyzeMultipleNetworks(clusters: List<ApCluster>): ChannelPlanReport {
        val allChannelUsages = clusters.flatMap { buildChannelUsageMap(it) }
        val allIssues =
            clusters.flatMap { cluster ->
                detectChannelIssues(cluster, buildChannelUsageMap(cluster))
            }
        val overallScore =
            if (clusters.isNotEmpty()) {
                clusters.map { calculateChannelPlanScore(it, allIssues) }.average()
            } else {
                0.0
            }

        return ChannelPlanReport(
            ssid = "All Networks",
            clusters = clusters,
            perBandChannelUsage = allChannelUsages,
            channelPlanScore = overallScore,
            issues = allIssues,
        )
    }

    private fun buildChannelUsageMap(cluster: ApCluster): List<ChannelUsage> =
        cluster.bssids
            .groupBy { it.channel to it.band }
            .map { (channelBand, bssList) ->
                val (channel, band) = channelBand
                val avgRssi = bssList.mapNotNull { it.rssiDbm }.average()
                val maxRssi = bssList.mapNotNull { it.rssiDbm }.maxOrNull() ?: -100

                ChannelUsage(
                    band = band,
                    channel = channel,
                    channelWidth = bssList.first().channelWidth,
                    apClusterIds = setOf(cluster.id),
                    bssids = bssList.map { it.bssid }.toSet(),
                    averageRssiDbm = avgRssi,
                    maxRssiDbm = maxRssi,
                    utilizationPercent = 0, // Would come from BSS Load IE
                    has11bLegacy = false, // Would detect from capability IE
                )
            }

    private fun detectChannelIssues(
        cluster: ApCluster,
        channelUsages: List<ChannelUsage>,
    ): List<ChannelPlanIssue> {
        val issues = mutableListOf<ChannelPlanIssue>()

        // Check for 2.4 GHz overlapping channels
        val twoFourGhzUsages = channelUsages.filter { it.band == WiFiBand.BAND_2_4GHZ }
        if (twoFourGhzUsages.size > 1) {
            val channels = twoFourGhzUsages.map { it.channel }
            if (!areNonOverlapping24GHz(channels)) {
                issues.add(
                    ChannelPlanIssue(
                        severity = IssueSeverity.HIGH,
                        issueType = ChannelIssueType.OVERLAPPING_CHANNELS,
                        description = "2.4 GHz APs using overlapping channels",
                        affectedChannels = channels.toSet(),
                        affectedBssids = twoFourGhzUsages.flatMap { it.bssids }.toSet(),
                        recommendation = "Use non-overlapping channels 1, 6, and 11",
                    ),
                )
            }
        }

        // Check for wide channels in 2.4 GHz
        val wide24GHz =
            twoFourGhzUsages.filter {
                it.channelWidth != ChannelWidth.WIDTH_20MHZ
            }
        if (wide24GHz.isNotEmpty()) {
            issues.add(
                ChannelPlanIssue(
                    severity = IssueSeverity.MEDIUM,
                    issueType = ChannelIssueType.WIDE_CHANNEL_24GHZ,
                    description = "40 MHz channels used in crowded 2.4 GHz band",
                    affectedChannels = wide24GHz.map { it.channel }.toSet(),
                    affectedBssids = wide24GHz.flatMap { it.bssids }.toSet(),
                    recommendation = "Use 20 MHz channels in 2.4 GHz for better compatibility",
                ),
            )
        }

        // Check for channel reuse (same channel, multiple APs)
        channelUsages
            .filter { it.bssids.size > 1 }
            .forEach { usage ->
                issues.add(
                    ChannelPlanIssue(
                        severity = IssueSeverity.LOW,
                        issueType = ChannelIssueType.CHANNEL_REUSE,
                        description = "${usage.bssids.size} APs on same channel ${usage.channel}",
                        affectedChannels = setOf(usage.channel),
                        affectedBssids = usage.bssids,
                        recommendation = "Distribute APs across different channels if possible",
                    ),
                )
            }

        // Check for congested channels
        channelUsages
            .filter { it.isCongested }
            .forEach { usage ->
                issues.add(
                    ChannelPlanIssue(
                        severity = IssueSeverity.MEDIUM,
                        issueType = ChannelIssueType.CHANNEL_CONGESTION,
                        description = "Channel ${usage.channel} is congested (${usage.apCount} APs)",
                        affectedChannels = setOf(usage.channel),
                        affectedBssids = usage.bssids,
                        recommendation = "Consider switching to a less congested channel",
                    ),
                )
            }

        return issues
    }

    private fun calculateChannelPlanScore(
        cluster: ApCluster,
        issues: List<ChannelPlanIssue>,
    ): Double {
        var score = 1.0

        // Penalty for each issue
        issues.forEach { issue ->
            val penalty =
                when (issue.severity) {
                    IssueSeverity.CRITICAL -> 0.3
                    IssueSeverity.HIGH -> 0.2
                    IssueSeverity.MEDIUM -> 0.1
                    IssueSeverity.LOW -> 0.05
                    IssueSeverity.INFO -> 0.0
                }
            score -= penalty
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Check if channels are non-overlapping in 2.4 GHz
     *
     * Non-overlapping channels: 1, 6, 11 (or 1, 5, 9, 13 in some regions)
     */
    private fun areNonOverlapping24GHz(channels: List<Int>): Boolean {
        val nonOverlapping =
            setOf(
                setOf(1, 6, 11), // US/most regions
                setOf(1, 5, 9, 13), // Europe
            )

        return nonOverlapping.any { validSet ->
            channels.all { it in validSet }
        }
    }
}
