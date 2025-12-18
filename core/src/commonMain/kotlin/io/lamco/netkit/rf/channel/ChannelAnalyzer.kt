package io.lamco.netkit.rf.channel

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.rf.model.ChannelMetrics
import kotlin.math.abs
import kotlin.math.exp

/**
 * Analyzes WiFi channel congestion and interference
 *
 * Evaluates channel quality by analyzing:
 * - **Co-channel interference**: BSSs on the same primary channel
 * - **Adjacent-channel interference**: BSSs on overlapping channels
 * - **Channel utilization**: Airtime usage from BSS Load IE
 * - **Legacy client impact**: 802.11b presence reducing efficiency
 *
 * The analyzer produces a [ChannelMetrics] object with:
 * - Congestion score (0.0-1.0): Overall channel crowding
 * - Interference score (0.0-1.0): Adjacent channel overlap impact
 * - Channel recommendation: Whether channel is suitable for use
 *
 * **Algorithm:**
 * ```
 * Congestion Score = f(BSS count, avg RSSI, utilization)
 * - High BSS count → lower score
 * - High avg RSSI → lower score (stronger interference)
 * - High utilization → lower score
 *
 * Interference Score = f(adjacent BSS count, overlap factor)
 * - More adjacent BSSs → lower score
 * - Higher channel overlap → lower score
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val analyzer = ChannelAnalyzer()
 * val bssData = listOf(
 *     BssChannelData(channel = 6, rssiDbm = -65, utilization = 30.0),
 *     BssChannelData(channel = 6, rssiDbm = -72, utilization = null),
 *     BssChannelData(channel = 11, rssiDbm = -80, utilization = null)
 * )
 * val metrics = analyzer.analyzeChannel(
 *     band = WiFiBand.BAND_2_4GHZ,
 *     targetChannel = 6,
 *     channelWidth = ChannelWidth.WIDTH_20MHZ,
 *     bssData = bssData
 * )
 * println("Congestion: ${metrics.congestionScore}, Quality: ${metrics.qualityCategory}")
 * ```
 *
 * **Standards:**
 * - IEEE 802.11-2020 Section 9.4.2.23 (BSS Load Element)
 * - IEEE 802.11-2020 Section 17.3.9 (Channel assignment)
 *
 * @property adjacentChannelThreshold RSSI threshold for adjacent channel interference (dBm)
 * @property recommendationThreshold Minimum quality score for channel recommendation (0.0-1.0)
 */
class ChannelAnalyzer(
    private val adjacentChannelThreshold: Int = -75,
    private val recommendationThreshold: Double = 0.6,
) {
    /**
     * Analyze a specific channel for congestion and interference
     *
     * @param band WiFi frequency band
     * @param targetChannel Primary channel number to analyze
     * @param channelWidth Channel width (20/40/80/160/320 MHz)
     * @param bssData List of BSS data for all detected networks
     * @param legacy11bPresent Whether legacy 802.11b clients detected
     * @return Channel metrics with congestion/interference scores
     */
    fun analyzeChannel(
        band: WiFiBand,
        targetChannel: Int,
        channelWidth: ChannelWidth,
        bssData: List<BssChannelData>,
        legacy11bPresent: Boolean = false,
    ): ChannelMetrics {
        require(targetChannel > 0) { "Channel must be positive: $targetChannel" }
        require(bssData.isNotEmpty()) { "BSS data cannot be empty" }

        // Separate co-channel and adjacent-channel BSSs
        val coChannelBss = bssData.filter { isCoChannel(it.channel, targetChannel) }
        val adjChannelBss =
            bssData.filter {
                !isCoChannel(it.channel, targetChannel) && isAdjacent(band, targetChannel, it.channel, channelWidth)
            }

        // Calculate average RSSI for all BSSs on this channel
        val avgRssi =
            if (coChannelBss.isNotEmpty()) {
                coChannelBss.map { it.rssiDbm }.average()
            } else {
                null
            }

        // Calculate total channel utilization
        val totalUtilization = calculateTotalUtilization(coChannelBss)

        // Calculate congestion score (0.0 = congested, 1.0 = clear)
        val congestionScore =
            calculateCongestionScore(
                coChannelCount = coChannelBss.size,
                avgRssi = avgRssi,
                utilization = totalUtilization,
            )

        // Calculate interference score (0.0 = high interference, 1.0 = clean)
        val interferenceScore =
            calculateInterferenceScore(
                band = band,
                targetChannel = targetChannel,
                channelWidth = channelWidth,
                adjChannelBss = adjChannelBss,
            )

        // Overall quality for recommendation
        val overallQuality = (congestionScore + interferenceScore) / 2.0
        val recommended = overallQuality >= recommendationThreshold && !legacy11bPresent

        return ChannelMetrics(
            band = band,
            channel = targetChannel,
            primaryWidth = channelWidth,
            coChannelBssCount = coChannelBss.size,
            adjChannelBssCount = adjChannelBss.size,
            avgRssiDbm = avgRssi,
            totalChannelUtilizationPct = totalUtilization,
            legacy11bPresent = legacy11bPresent,
            congestionScore = congestionScore,
            interferenceScore = interferenceScore,
            recommended = recommended,
        )
    }

    /**
     * Analyze all available channels in a band
     *
     * @param band WiFi frequency band
     * @param channelWidth Channel width to analyze
     * @param bssData All BSS data from scan
     * @param legacy11bPresent Whether legacy 802.11b clients detected
     * @return List of channel metrics for all channels, sorted by quality (best first)
     */
    fun analyzeAllChannels(
        band: WiFiBand,
        channelWidth: ChannelWidth,
        bssData: List<BssChannelData>,
        legacy11bPresent: Boolean = false,
    ): List<ChannelMetrics> {
        require(bssData.isNotEmpty()) { "BSS data cannot be empty" }

        val channels = getAvailableChannels(band, channelWidth)
        return channels
            .map { channel ->
                analyzeChannel(band, channel, channelWidth, bssData, legacy11bPresent)
            }.sortedByDescending { it.overallQuality }
    }

    /**
     * Find the best channel in a band
     *
     * @param band WiFi frequency band
     * @param channelWidth Channel width
     * @param bssData All BSS data from scan
     * @param legacy11bPresent Whether legacy 802.11b clients detected
     * @return Best channel metrics (highest quality score)
     */
    fun findBestChannel(
        band: WiFiBand,
        channelWidth: ChannelWidth,
        bssData: List<BssChannelData>,
        legacy11bPresent: Boolean = false,
    ): ChannelMetrics {
        val allChannels = analyzeAllChannels(band, channelWidth, bssData, legacy11bPresent)
        return allChannels.first() // Already sorted by quality
    }

    /**
     * Calculate congestion score based on BSS count, RSSI, and utilization
     *
     * Score decreases with:
     * - More co-channel BSSs (exponential penalty)
     * - Higher average RSSI (stronger interference)
     * - Higher channel utilization (less available airtime)
     *
     * @param coChannelCount Number of co-channel BSSs
     * @param avgRssi Average RSSI of co-channel BSSs (null if none)
     * @param utilization Total channel utilization 0-100% (null if unknown)
     * @return Congestion score 0.0-1.0 (1.0 = no congestion)
     */
    private fun calculateCongestionScore(
        coChannelCount: Int,
        avgRssi: Double?,
        utilization: Double?,
    ): Double {
        var score = 1.0

        // BSS count penalty (exponential decay)
        // 0 BSSs: 1.0, 1 BSS: 0.9, 3 BSSs: 0.7, 5 BSSs: 0.5, 10 BSSs: 0.2
        score *= exp(-coChannelCount * 0.2)

        // RSSI penalty (stronger signals = more interference)
        avgRssi?.let { rssi ->
            // -50 dBm (very strong): 0.5x penalty
            // -70 dBm (medium): 0.8x penalty
            // -90 dBm (weak): 1.0x (no penalty)
            val rssiNorm = (rssi + 90.0) / 40.0 // Normalize -90 to -50 → 0.0 to 1.0
            score *= (1.0 - rssiNorm.coerceIn(0.0, 0.5))
        }

        // Utilization penalty
        utilization?.let { util ->
            // 0%: 1.0x, 50%: 0.5x, 100%: 0.0x
            score *= (1.0 - util / 100.0).coerceIn(0.0, 1.0)
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Calculate interference score based on adjacent channel overlap
     *
     * Score decreases with:
     * - More adjacent-channel BSSs
     * - Higher channel overlap (closer channels = worse)
     * - Stronger adjacent BSS signals
     *
     * @param band WiFi frequency band
     * @param targetChannel Primary channel to analyze
     * @param channelWidth Channel width
     * @param adjChannelBss List of adjacent-channel BSSs
     * @return Interference score 0.0-1.0 (1.0 = no interference)
     */
    private fun calculateInterferenceScore(
        band: WiFiBand,
        targetChannel: Int,
        channelWidth: ChannelWidth,
        adjChannelBss: List<BssChannelData>,
    ): Double {
        if (adjChannelBss.isEmpty()) {
            return 1.0 // No adjacent interference
        }

        var totalInterference = 0.0

        for (bss in adjChannelBss) {
            // Calculate channel overlap factor (0.0 = no overlap, 1.0 = full overlap)
            val overlapFactor =
                calculateChannelOverlap(
                    band = band,
                    channel1 = targetChannel,
                    channel2 = bss.channel,
                    width = channelWidth,
                )

            // RSSI-weighted interference
            // Strong signals on adjacent channels are worse than weak ones
            val rssiWeight =
                if (bss.rssiDbm >= adjacentChannelThreshold) {
                    // -75 dBm threshold: stronger signals contribute more interference
                    val rssiNorm = (bss.rssiDbm + 90.0) / 40.0 // -90 to -50 → 0.0 to 1.0
                    rssiNorm.coerceIn(0.0, 1.0)
                } else {
                    0.1 // Weak signals have minimal impact
                }

            totalInterference += overlapFactor * rssiWeight
        }

        // Convert total interference to score (higher interference → lower score)
        val score = exp(-totalInterference * 0.3)
        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Calculate total channel utilization from BSS Load elements
     *
     * Sums utilization from all co-channel BSSs (capped at 100%).
     * Returns null if no BSS provides utilization data.
     *
     * @param coChannelBss List of co-channel BSSs
     * @return Total utilization 0-100%, or null if unavailable
     */
    private fun calculateTotalUtilization(coChannelBss: List<BssChannelData>): Double? {
        val utilizationValues = coChannelBss.mapNotNull { it.utilization }
        return if (utilizationValues.isNotEmpty()) {
            utilizationValues.sum().coerceIn(0.0, 100.0)
        } else {
            null
        }
    }

    /**
     * Check if two channels are co-channel (same primary channel)
     *
     * @param channel1 First channel number
     * @param channel2 Second channel number
     * @return True if channels are the same
     */
    private fun isCoChannel(
        channel1: Int,
        channel2: Int,
    ): Boolean = channel1 == channel2

    /**
     * Check if a channel is adjacent to the target channel
     *
     * For 2.4 GHz: Channels are considered adjacent if within ±5 channels
     * For 5/6 GHz: Depends on channel width and spacing
     *
     * @param band WiFi frequency band
     * @param targetChannel Target channel
     * @param otherChannel Other channel to check
     * @param width Channel width
     * @return True if channels are adjacent/overlapping
     */
    private fun isAdjacent(
        band: WiFiBand,
        targetChannel: Int,
        otherChannel: Int,
        width: ChannelWidth,
    ): Boolean =
        when (band) {
            WiFiBand.BAND_2_4GHZ -> {
                // 2.4 GHz: 20 MHz channels overlap ±2 channels
                // 40 MHz channels overlap ±4 channels
                val range = if (width == ChannelWidth.WIDTH_40MHZ) 4 else 2
                abs(targetChannel - otherChannel) <= range
            }
            WiFiBand.BAND_5GHZ, WiFiBand.BAND_6GHZ -> {
                // 5/6 GHz: Non-overlapping channels with proper spacing
                // 20 MHz: 4 channel spacing (36, 40, 44, 48...)
                // 40 MHz: 8 channel spacing
                // 80 MHz: 16 channel spacing
                val spacing =
                    when (width) {
                        ChannelWidth.WIDTH_20MHZ -> 4
                        ChannelWidth.WIDTH_40MHZ -> 8
                        ChannelWidth.WIDTH_80MHZ -> 16
                        ChannelWidth.WIDTH_160MHZ -> 32
                        ChannelWidth.WIDTH_320MHZ -> 64
                        ChannelWidth.UNKNOWN -> 4
                    }
                abs(targetChannel - otherChannel) < spacing
            }
            WiFiBand.BAND_6GHZ -> {
                // 60 GHz: 2.16 GHz channels, minimal overlap
                abs(targetChannel - otherChannel) <= 1
            }
            else -> false
        }

    /**
     * Calculate channel overlap factor between two channels
     *
     * Returns 0.0 for non-overlapping, 1.0 for complete overlap.
     * Uses spectral overlap based on channel width and spacing.
     *
     * @param band WiFi frequency band
     * @param channel1 First channel
     * @param channel2 Second channel
     * @param width Channel width
     * @return Overlap factor 0.0-1.0
     */
    private fun calculateChannelOverlap(
        band: WiFiBand,
        channel1: Int,
        channel2: Int,
        width: ChannelWidth,
    ): Double {
        if (channel1 == channel2) return 1.0 // Complete overlap

        return when (band) {
            WiFiBand.BAND_2_4GHZ -> {
                // 2.4 GHz: Each channel is 5 MHz apart, 20 MHz wide
                // Overlap decreases linearly with channel distance
                val distance = abs(channel1 - channel2)
                val overlap = (4.0 - distance) / 4.0 // 4 channels = 20 MHz width
                overlap.coerceIn(0.0, 1.0)
            }
            WiFiBand.BAND_5GHZ, WiFiBand.BAND_6GHZ -> {
                // 5/6 GHz: Properly spaced non-overlapping channels
                // Only adjacent channels have minimal spectral leakage
                val spacing =
                    when (width) {
                        ChannelWidth.WIDTH_20MHZ -> 4
                        ChannelWidth.WIDTH_40MHZ -> 8
                        ChannelWidth.WIDTH_80MHZ -> 16
                        ChannelWidth.WIDTH_160MHZ -> 32
                        ChannelWidth.WIDTH_320MHZ -> 64
                        ChannelWidth.UNKNOWN -> 4
                    }
                val distance = abs(channel1 - channel2)
                if (distance >= spacing) {
                    0.0
                } else {
                    (spacing - distance).toDouble() / spacing * 0.2
                }
            }
            else -> 0.0
        }
    }

    /**
     * Get available channels for a band and width
     *
     * @param band WiFi frequency band
     * @param width Channel width
     * @return List of valid channel numbers
     */
    private fun getAvailableChannels(
        band: WiFiBand,
        width: ChannelWidth,
    ): List<Int> = AVAILABLE_CHANNELS_TABLE[band to width] ?: emptyList()

    companion object {
        /**
         * Available channels lookup table for (band, width) pairs.
         */
        private val AVAILABLE_CHANNELS_TABLE =
            mapOf(
                // 2.4 GHz band - limited channel availability
                (WiFiBand.BAND_2_4GHZ to ChannelWidth.WIDTH_20MHZ) to listOf(1, 6, 11), // Non-overlapping
                (WiFiBand.BAND_2_4GHZ to ChannelWidth.WIDTH_40MHZ) to listOf(3, 11), // Limited 40 MHz options
                // 5 GHz band - UNII-1, UNII-3 (varies by region)
                (WiFiBand.BAND_5GHZ to ChannelWidth.WIDTH_20MHZ) to listOf(36, 40, 44, 48, 149, 153, 157, 161, 165),
                (WiFiBand.BAND_5GHZ to ChannelWidth.WIDTH_40MHZ) to listOf(38, 46, 151, 159),
                (WiFiBand.BAND_5GHZ to ChannelWidth.WIDTH_80MHZ) to listOf(42, 155),
                (WiFiBand.BAND_5GHZ to ChannelWidth.WIDTH_160MHZ) to listOf(50),
                // 6 GHz band - WiFi 6E, wide availability
                (WiFiBand.BAND_6GHZ to ChannelWidth.WIDTH_20MHZ) to (1..233 step 4).toList(),
                (WiFiBand.BAND_6GHZ to ChannelWidth.WIDTH_40MHZ) to (3..227 step 8).toList(),
                (WiFiBand.BAND_6GHZ to ChannelWidth.WIDTH_80MHZ) to (7..223 step 16).toList(),
                (WiFiBand.BAND_6GHZ to ChannelWidth.WIDTH_160MHZ) to (15..207 step 32).toList(),
                (WiFiBand.BAND_6GHZ to ChannelWidth.WIDTH_320MHZ) to listOf(31, 95, 159),
            )
    }
}

/**
 * BSS data for channel analysis
 *
 * Minimal data required to analyze a BSS for channel congestion/interference.
 *
 * @property channel Primary channel number
 * @property rssiDbm Received signal strength in dBm
 * @property utilization Channel utilization 0-100% from BSS Load IE (null if unavailable)
 */
data class BssChannelData(
    val channel: Int,
    val rssiDbm: Int,
    val utilization: Double? = null,
) {
    init {
        require(channel > 0) { "Channel must be positive: $channel" }
        require(rssiDbm in -120..0) { "RSSI must be between -120 and 0 dBm: $rssiDbm" }
        utilization?.let {
            require(it in 0.0..100.0) { "Utilization must be 0-100%: $it" }
        }
    }
}
