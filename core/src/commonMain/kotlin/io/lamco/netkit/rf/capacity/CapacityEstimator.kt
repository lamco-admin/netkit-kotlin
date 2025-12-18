package io.lamco.netkit.rf.capacity

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.rf.model.CapacityMetrics
import io.lamco.netkit.rf.snr.SnrCalculator

/**
 * Estimates WiFi capacity and throughput for a BSS
 *
 * Calculates maximum and effective throughput based on:
 * - **Signal quality**: RSSI/SNR determines achievable MCS level
 * - **WiFi standard**: Supports WiFi 4/5/6/7 with appropriate MCS tables
 * - **Channel width**: 20/40/80/160/320 MHz bandwidth
 * - **Spatial streams**: NSS (1-8 for WiFi 4-6, 1-16 for WiFi 7)
 * - **Protocol overhead**: Real-world efficiency (50-75% depending on standard)
 * - **Channel utilization**: Accounts for congestion and available airtime
 *
 * **Capacity Metrics:**
 * - **Max PHY Rate**: Theoretical maximum from MCS tables (e.g., 9600 Mbps)
 * - **Effective Downlink**: Realistic throughput with protocol overhead (e.g., 6000 Mbps)
 * - **Effective Uplink**: Typically 70-80% of downlink (asymmetric capacity)
 * - **Utilization-Adjusted**: Available capacity after current channel usage
 *
 * **Algorithm:**
 * ```
 * 1. Calculate SNR from RSSI and noise floor
 * 2. Determine max achievable MCS from SNR
 * 3. Look up PHY rate from MCS + width + NSS
 * 4. Apply protocol overhead (WiFi 7: 75%, WiFi 6: 70%, WiFi 5: 60%, WiFi 4: 50%)
 * 5. Adjust for channel utilization (available = effective * (1 - utilization))
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val estimator = CapacityEstimator(
 *     snrCalculator = SnrCalculator(StaticNoiseModel.default()),
 *     mcsRateTable = McsRateTable.standard()
 * )
 *
 * val capacity = estimator.estimateCapacity(
 *     bssid = "00:11:22:33:44:55",
 *     rssiDbm = -55,
 *     band = WiFiBand.BAND_5GHZ,
 *     standard = WifiStandard.WIFI_6,
 *     channelWidth = ChannelWidth.WIDTH_80MHZ,
 *     nss = 2,
 *     channelUtilizationPct = 30.0
 * )
 * println("Effective throughput: ${capacity.estimatedEffectiveDownlinkMbps} Mbps")
 * println("Available capacity: ${capacity.availableCapacityMbps} Mbps")
 * ```
 *
 * **References:**
 * - IEEE 802.11-2020 Section 9.4.2.23 (BSS Load)
 * - IEEE 802.11ax-2021 Section 27.3.10.9 (Throughput estimation)
 *
 * @property snrCalculator SNR calculator with noise model
 * @property mcsRateTable MCS rate lookup table
 * @property uplinkRatio Uplink/downlink capacity ratio (default 0.75 = 75%)
 */
class CapacityEstimator(
    private val snrCalculator: SnrCalculator,
    private val mcsRateTable: McsRateTable = McsRateTable.standard(),
    private val uplinkRatio: Double = 0.75,
) {
    init {
        require(uplinkRatio in 0.0..1.0) { "Uplink ratio must be 0.0-1.0: $uplinkRatio" }
    }

    /**
     * Estimate capacity for a BSS
     *
     * @param bssid MAC address of the AP
     * @param rssiDbm Received signal strength
     * @param band WiFi frequency band
     * @param standard WiFi standard (4/5/6/7)
     * @param channelWidth Channel bandwidth
     * @param nss Number of spatial streams (null = estimate from standard)
     * @param channelUtilizationPct Current channel utilization 0-100% (null = unknown)
     * @param minLinkMarginDb Minimum link margin for MCS determination (default 3 dB)
     * @return Capacity metrics with throughput estimates
     */
    fun estimateCapacity(
        bssid: String,
        rssiDbm: Int,
        band: WiFiBand,
        standard: WifiStandard,
        channelWidth: ChannelWidth,
        nss: Int? = null,
        channelUtilizationPct: Double? = null,
        minLinkMarginDb: Double = 3.0,
    ): CapacityMetrics {
        require(rssiDbm in -120..0) { "RSSI must be between -120 and 0 dBm: $rssiDbm" }
        channelUtilizationPct?.let {
            require(it in 0.0..100.0) { "Utilization must be 0-100%: $it" }
        }

        val snr = snrCalculator.calculateSnr(rssiDbm, band)

        val effectiveNss = nss ?: estimateNss(standard, channelWidth)

        val maxMcs =
            snrCalculator.maxAchievableMcs(
                snrDb = snr,
                standard = standard,
                channelWidth = channelWidth,
                nss = effectiveNss,
                minMargin = minLinkMarginDb,
            )

        val (maxPhyRate, effectiveDownlink, effectiveUplink) =
            if (maxMcs != null) {
                val phyRate =
                    mcsRateTable.getPhyRateMbps(
                        mcs = maxMcs,
                        standard = standard,
                        channelWidth = channelWidth,
                        nss = effectiveNss,
                    )

                if (phyRate != null) {
                    val downlink = calculateEffectiveThroughput(phyRate, standard)
                    val uplink = downlink * uplinkRatio
                    Triple(phyRate, downlink, uplink)
                } else {
                    Triple(null, null, null)
                }
            } else {
                Triple(null, null, null)
            }

        val utilizationAdjusted =
            if (effectiveDownlink != null && channelUtilizationPct != null) {
                effectiveDownlink * (1.0 - channelUtilizationPct / 100.0)
            } else {
                effectiveDownlink // No utilization data, return full capacity
            }

        return CapacityMetrics(
            bssid = bssid,
            band = band,
            channelWidth = channelWidth,
            nss = effectiveNss,
            maxMcs = maxMcs,
            maxPhyRateMbps = maxPhyRate,
            estimatedEffectiveDownlinkMbps = effectiveDownlink,
            estimatedEffectiveUplinkMbps = effectiveUplink,
            utilizationAdjustedDownlinkMbps = utilizationAdjusted,
        )
    }

    /**
     * Estimate capacity for multiple BSSs
     *
     * Useful for comparing capacity across different APs.
     *
     * @param bssDataList List of BSS data for capacity estimation
     * @return List of capacity metrics, sorted by effective downlink (highest first)
     */
    fun estimateCapacityForMultipleBss(bssDataList: List<BssCapacityData>): List<CapacityMetrics> {
        require(bssDataList.isNotEmpty()) { "BSS data list cannot be empty" }

        return bssDataList
            .map { bssData ->
                estimateCapacity(
                    bssid = bssData.bssid,
                    rssiDbm = bssData.rssiDbm,
                    band = bssData.band,
                    standard = bssData.standard,
                    channelWidth = bssData.channelWidth,
                    nss = bssData.nss,
                    channelUtilizationPct = bssData.channelUtilizationPct,
                )
            }.sortedByDescending { it.estimatedEffectiveDownlinkMbps }
    }

    /**
     * Find BSS with highest capacity
     *
     * @param bssDataList List of BSS data
     * @return Capacity metrics for the best BSS (highest effective downlink)
     */
    fun findBestCapacityBss(bssDataList: List<BssCapacityData>): CapacityMetrics {
        val allCapacities = estimateCapacityForMultipleBss(bssDataList)
        return allCapacities.first() // Already sorted by effective downlink
    }

    /**
     * Estimate typical NSS for a standard and channel width
     *
     * Heuristics:
     * - WiFi 4: 1-2 streams (40 MHz → 2, 20 MHz → 1)
     * - WiFi 5: 2-4 streams (160 MHz → 4, 80 MHz → 2)
     * - WiFi 6/6E: 2-4 streams (160 MHz → 4, 80 MHz → 2)
     * - WiFi 7: 4-8 streams (320 MHz → 8, 160 MHz → 4)
     *
     * @param standard WiFi standard
     * @param width Channel width
     * @return Estimated number of spatial streams
     */
    private fun estimateNss(
        standard: WifiStandard,
        width: ChannelWidth,
    ): Int = NSS_LOOKUP_TABLE[standard to width] ?: DEFAULT_NSS

    companion object {
        /** Default NSS for unknown configurations */
        private const val DEFAULT_NSS = 1

        /**
         * NSS lookup table for (standard, width) pairs.
         * Organized by WiFi generation for clarity.
         */
        private val NSS_LOOKUP_TABLE =
            mapOf(
                // WiFi 4: 1-2 streams
                (WifiStandard.WIFI_4 to ChannelWidth.WIDTH_40MHZ) to 2,
                (WifiStandard.WIFI_4 to ChannelWidth.WIDTH_20MHZ) to 1,
                // WiFi 5: 2-4 streams
                (WifiStandard.WIFI_5 to ChannelWidth.WIDTH_160MHZ) to 4,
                (WifiStandard.WIFI_5 to ChannelWidth.WIDTH_80MHZ) to 2,
                (WifiStandard.WIFI_5 to ChannelWidth.WIDTH_40MHZ) to 2,
                (WifiStandard.WIFI_5 to ChannelWidth.WIDTH_20MHZ) to 1,
                // WiFi 6: 2-4 streams
                (WifiStandard.WIFI_6 to ChannelWidth.WIDTH_160MHZ) to 4,
                (WifiStandard.WIFI_6 to ChannelWidth.WIDTH_80MHZ) to 2,
                (WifiStandard.WIFI_6 to ChannelWidth.WIDTH_40MHZ) to 2,
                (WifiStandard.WIFI_6 to ChannelWidth.WIDTH_20MHZ) to 1,
                // WiFi 6E: 2-4 streams (same as WiFi 6)
                (WifiStandard.WIFI_6E to ChannelWidth.WIDTH_160MHZ) to 4,
                (WifiStandard.WIFI_6E to ChannelWidth.WIDTH_80MHZ) to 2,
                (WifiStandard.WIFI_6E to ChannelWidth.WIDTH_40MHZ) to 2,
                (WifiStandard.WIFI_6E to ChannelWidth.WIDTH_20MHZ) to 1,
                // WiFi 7: 4-8 streams
                (WifiStandard.WIFI_7 to ChannelWidth.WIDTH_320MHZ) to 8,
                (WifiStandard.WIFI_7 to ChannelWidth.WIDTH_160MHZ) to 4,
                (WifiStandard.WIFI_7 to ChannelWidth.WIDTH_80MHZ) to 2,
                (WifiStandard.WIFI_7 to ChannelWidth.WIDTH_40MHZ) to 2,
                (WifiStandard.WIFI_7 to ChannelWidth.WIDTH_20MHZ) to 1,
            )
    }

    /**
     * Calculate realistic effective throughput accounting for protocol overhead
     *
     * Protocol overhead includes:
     * - MAC headers and management frames
     * - ACKs and block ACKs
     * - Inter-frame spacing (SIFS, DIFS)
     * - Contention and backoff
     * - Retransmissions
     *
     * Efficiency improvements:
     * - WiFi 4: ~50% (basic aggregation)
     * - WiFi 5: ~60% (improved aggregation, beamforming)
     * - WiFi 6: ~70% (OFDMA, TWT, BSS coloring)
     * - WiFi 7: ~75% (MLO, better aggregation, preamble puncturing)
     *
     * @param phyRateMbps PHY layer rate
     * @param standard WiFi standard
     * @return Effective throughput in Mbps
     */
    private fun calculateEffectiveThroughput(
        phyRateMbps: Double,
        standard: WifiStandard,
    ): Double {
        val efficiency =
            when (standard) {
                WifiStandard.WIFI_4 -> 0.50 // 50% efficiency
                WifiStandard.WIFI_5 -> 0.60 // 60% efficiency
                WifiStandard.WIFI_6, WifiStandard.WIFI_6E -> 0.70 // 70% efficiency
                WifiStandard.WIFI_7 -> 0.75 // 75% efficiency
                else -> 0.40 // Legacy standards (WiFi 1-3)
            }
        return phyRateMbps * efficiency
    }
}

/**
 * BSS data for capacity estimation
 *
 * @property bssid MAC address of the AP
 * @property rssiDbm Received signal strength
 * @property band WiFi frequency band
 * @property standard WiFi standard (4/5/6/7)
 * @property channelWidth Channel bandwidth
 * @property nss Number of spatial streams (null = auto-estimate)
 * @property channelUtilizationPct Channel utilization 0-100% (null = unknown)
 */
data class BssCapacityData(
    val bssid: String,
    val rssiDbm: Int,
    val band: WiFiBand,
    val standard: WifiStandard,
    val channelWidth: ChannelWidth,
    val nss: Int? = null,
    val channelUtilizationPct: Double? = null,
) {
    init {
        require(rssiDbm in -120..0) { "RSSI must be between -120 and 0 dBm: $rssiDbm" }
        nss?.let { require(it in 1..16) { "NSS must be 1-16: $it" } }
        channelUtilizationPct?.let {
            require(it in 0.0..100.0) { "Utilization must be 0-100%: $it" }
        }
    }
}

/**
 * Capacity comparison result
 *
 * Compares capacity between two BSSs to determine which provides better throughput.
 *
 * @property bss1 First BSS capacity metrics
 * @property bss2 Second BSS capacity metrics
 * @property preferredBssid BSSID of the BSS with higher capacity
 * @property capacityDifferenceMbps Capacity difference (positive = bss1 better, negative = bss2 better)
 * @property capacityDifferencePct Percentage difference in capacity
 */
data class CapacityComparison(
    val bss1: CapacityMetrics,
    val bss2: CapacityMetrics,
    val preferredBssid: String,
    val capacityDifferenceMbps: Double,
    val capacityDifferencePct: Double,
) {
    /**
     * Whether the capacity difference is significant (>= 20%)
     */
    val isSignificantDifference: Boolean
        get() = kotlin.math.abs(capacityDifferencePct) >= 20.0

    /**
     * Whether BSS 1 has better capacity
     */
    val bss1IsBetter: Boolean
        get() = bss1.bssid == preferredBssid
}

/**
 * Compare capacity between two BSSs
 *
 * @param bss1 First BSS capacity metrics
 * @param bss2 Second BSS capacity metrics
 * @return Capacity comparison result
 */
fun compareCapacity(
    bss1: CapacityMetrics,
    bss2: CapacityMetrics,
): CapacityComparison {
    val capacity1 = bss1.utilizationAdjustedDownlinkMbps ?: bss1.estimatedEffectiveDownlinkMbps ?: 0.0
    val capacity2 = bss2.utilizationAdjustedDownlinkMbps ?: bss2.estimatedEffectiveDownlinkMbps ?: 0.0

    val difference = capacity1 - capacity2
    val preferred = if (difference >= 0.0) bss1.bssid else bss2.bssid
    val diffPct =
        if (capacity2 > 0.0) {
            (difference / capacity2) * 100.0
        } else {
            0.0
        }

    return CapacityComparison(
        bss1 = bss1,
        bss2 = bss2,
        preferredBssid = preferred,
        capacityDifferenceMbps = kotlin.math.abs(difference),
        capacityDifferencePct = kotlin.math.abs(diffPct),
    )
}
