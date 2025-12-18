package io.lamco.netkit.rf.capacity

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.rf.model.McsLevel

/**
 * MCS (Modulation and Coding Scheme) rate lookup tables
 *
 * Maps MCS level, channel width, and spatial streams to PHY layer data rate.
 * Provides accurate throughput estimation based on IEEE 802.11 specifications.
 *
 * **Standards Coverage:**
 * - **WiFi 4 (802.11n)**: MCS 0-31 (up to 8 spatial streams)
 * - **WiFi 5 (802.11ac)**: MCS 0-9 per stream (up to 8 streams)
 * - **WiFi 6/6E (802.11ax)**: MCS 0-11 per stream (up to 8 streams)
 * - **WiFi 7 (802.11be)**: MCS 0-13 per stream (up to 16 streams)
 *
 * **Channel Widths:**
 * - 20 MHz (all standards)
 * - 40 MHz (WiFi 4+)
 * - 80 MHz (WiFi 5+)
 * - 160 MHz (WiFi 5+)
 * - 320 MHz (WiFi 7 only)
 *
 * **Usage:**
 * ```kotlin
 * val rateTable = McsRateTable.standard()
 * val phyRate = rateTable.getPhyRateMbps(
 *     mcs = McsLevel(9),
 *     standard = WifiStandard.WIFI_6,
 *     channelWidth = ChannelWidth.WIDTH_80MHZ,
 *     nss = 2
 * )
 * println("PHY Rate: $phyRate Mbps") // e.g., 1200.4 Mbps
 * ```
 *
 * **References:**
 * - IEEE 802.11n-2009 Table 20-35 (WiFi 4 MCS rates)
 * - IEEE 802.11ac-2013 Table 21-30 (WiFi 5 MCS rates)
 * - IEEE 802.11ax-2021 Table 27-64 (WiFi 6 MCS rates)
 * - IEEE 802.11be Table 36-140 (WiFi 7 MCS rates)
 */
interface McsRateTable {
    /**
     * Get PHY layer data rate for given parameters
     *
     * @param mcs MCS level (modulation and coding scheme)
     * @param standard WiFi standard (4/5/6/7)
     * @param channelWidth Channel bandwidth
     * @param nss Number of spatial streams (1-8 for WiFi 4-6, 1-16 for WiFi 7)
     * @return PHY rate in Mbps, or null if combination not supported
     */
    fun getPhyRateMbps(
        mcs: McsLevel,
        standard: WifiStandard,
        channelWidth: ChannelWidth,
        nss: Int,
    ): Double?

    /**
     * Get maximum PHY rate for a standard and configuration
     *
     * @param standard WiFi standard
     * @param channelWidth Channel bandwidth
     * @param nss Number of spatial streams
     * @return Maximum PHY rate in Mbps
     */
    fun getMaxPhyRateMbps(
        standard: WifiStandard,
        channelWidth: ChannelWidth,
        nss: Int,
    ): Double?

    companion object {
        /**
         * Create standard MCS rate table based on IEEE 802.11 specifications
         */
        fun standard(): McsRateTable = StandardMcsRateTable()
    }
}

/**
 * Standard MCS rate table implementation
 *
 * Uses IEEE 802.11 specification values for accurate PHY rate calculation.
 */
class StandardMcsRateTable : McsRateTable {
    override fun getPhyRateMbps(
        mcs: McsLevel,
        standard: WifiStandard,
        channelWidth: ChannelWidth,
        nss: Int,
    ): Double? {
        require(nss >= 1) { "NSS must be at least 1: $nss" }
        require(nss <= 16) { "NSS must be at most 16: $nss" }

        // Validate standard supports the MCS level
        if (!isValidMcsForStandard(mcs, standard)) {
            return null
        }

        // Validate channel width is supported by standard
        if (!isValidWidthForStandard(channelWidth, standard)) {
            return null
        }

        return when (standard) {
            WifiStandard.WIFI_4 -> getWiFi4Rate(mcs, channelWidth, nss)
            WifiStandard.WIFI_5 -> getWiFi5Rate(mcs, channelWidth, nss)
            WifiStandard.WIFI_6, WifiStandard.WIFI_6E -> getWiFi6Rate(mcs, channelWidth, nss)
            WifiStandard.WIFI_7 -> getWiFi7Rate(mcs, channelWidth, nss)
            else -> null // Legacy standards (WiFi 1-3) not supported
        }
    }

    override fun getMaxPhyRateMbps(
        standard: WifiStandard,
        channelWidth: ChannelWidth,
        nss: Int,
    ): Double? {
        // Max MCS per-stream (NSS is passed separately)
        val maxMcs =
            when (standard) {
                WifiStandard.WIFI_4 -> McsLevel(7) // Per-stream max is MCS 7 (64-QAM 5/6)
                WifiStandard.WIFI_5 -> McsLevel(9)
                WifiStandard.WIFI_6, WifiStandard.WIFI_6E -> McsLevel(11)
                WifiStandard.WIFI_7 -> McsLevel(13)
                else -> return null
            }
        return getPhyRateMbps(maxMcs, standard, channelWidth, nss)
    }

    /**
     * Get WiFi 4 (802.11n) PHY rate
     *
     * WiFi 4 uses MCS 0-31 encoding (includes NSS in MCS index).
     * Formula: base rate * NSS * width multiplier
     */
    private fun getWiFi4Rate(
        mcs: McsLevel,
        width: ChannelWidth,
        nss: Int,
    ): Double? {
        // WiFi 4 MCS 0-7 base rates for 20 MHz, long GI (800ns)
        val baseRates20MHz =
            mapOf(
                0 to 6.5,
                1 to 13.0,
                2 to 19.5,
                3 to 26.0,
                4 to 39.0,
                5 to 52.0,
                6 to 58.5,
                7 to 65.0,
            )

        val mcsIndex = mcs.level % 8 // WiFi 4 encodes NSS in MCS level
        val baseRate = baseRates20MHz[mcsIndex] ?: return null

        val widthMultiplier =
            when (width) {
                ChannelWidth.WIDTH_20MHZ -> 1.0
                ChannelWidth.WIDTH_40MHZ -> 2.0 // Double for 40 MHz
                else -> return null // WiFi 4 only supports 20/40 MHz
            }

        return baseRate * widthMultiplier * nss
    }

    /**
     * Get WiFi 5 (802.11ac) PHY rate
     *
     * WiFi 5: MCS 0-9 per stream, supports up to 8 streams
     * Uses 800ns GI (short GI: 400ns gives ~11% increase)
     */
    private fun getWiFi5Rate(
        mcs: McsLevel,
        width: ChannelWidth,
        nss: Int,
    ): Double? {
        if (nss > 8) return null // WiFi 5 max 8 streams

        // MCS 0-9 base rates for 20 MHz, 800ns GI
        val baseRates20MHz =
            mapOf(
                0 to 6.5,
                1 to 13.0,
                2 to 19.5,
                3 to 26.0,
                4 to 39.0,
                5 to 52.0,
                6 to 58.5,
                7 to 65.0,
                8 to 78.0,
                9 to 86.7, // MCS 9: 256-QAM 5/6
            )

        val baseRate = baseRates20MHz[mcs.level] ?: return null

        val widthMultiplier =
            when (width) {
                ChannelWidth.WIDTH_20MHZ -> 1.0
                ChannelWidth.WIDTH_40MHZ -> 2.0
                ChannelWidth.WIDTH_80MHZ -> 4.0
                ChannelWidth.WIDTH_160MHZ -> 8.0
                else -> return null
            }

        return baseRate * widthMultiplier * nss
    }

    /**
     * Get WiFi 6/6E (802.11ax) PHY rate
     *
     * WiFi 6: MCS 0-11 per stream, supports up to 8 streams
     * Uses 3200ns symbol duration (OFDMA), 800ns GI
     */
    private fun getWiFi6Rate(
        mcs: McsLevel,
        width: ChannelWidth,
        nss: Int,
    ): Double? {
        if (nss > 8) return null // WiFi 6 max 8 streams

        // MCS 0-11 base rates for 20 MHz, 800ns GI
        // WiFi 6 adds MCS 10-11 (1024-QAM)
        val baseRates20MHz =
            mapOf(
                0 to 8.6,
                1 to 17.2,
                2 to 25.8,
                3 to 34.4,
                4 to 51.5,
                5 to 68.8,
                6 to 77.4,
                7 to 86.0,
                8 to 103.2,
                9 to 114.7,
                10 to 129.0,
                11 to 143.4, // MCS 10-11: 1024-QAM
            )

        val baseRate = baseRates20MHz[mcs.level] ?: return null

        val widthMultiplier =
            when (width) {
                ChannelWidth.WIDTH_20MHZ -> 1.0
                ChannelWidth.WIDTH_40MHZ -> 2.0
                ChannelWidth.WIDTH_80MHZ -> 4.0
                ChannelWidth.WIDTH_160MHZ -> 8.0
                else -> return null // WiFi 6 doesn't support 320 MHz
            }

        return baseRate * widthMultiplier * nss
    }

    /**
     * Get WiFi 7 (802.11be) PHY rate
     *
     * WiFi 7: MCS 0-13 per stream, supports up to 16 streams
     * Adds 4096-QAM (MCS 12-13) and 320 MHz channels
     */
    private fun getWiFi7Rate(
        mcs: McsLevel,
        width: ChannelWidth,
        nss: Int,
    ): Double? {
        if (nss > 16) return null // WiFi 7 max 16 streams

        // MCS 0-13 base rates for 20 MHz, 800ns GI
        // WiFi 7 adds MCS 12-13 (4096-QAM)
        val baseRates20MHz =
            mapOf(
                0 to 8.6,
                1 to 17.2,
                2 to 25.8,
                3 to 34.4,
                4 to 51.5,
                5 to 68.8,
                6 to 77.4,
                7 to 86.0,
                8 to 103.2,
                9 to 114.7,
                10 to 129.0,
                11 to 143.4, // Same as WiFi 6
                12 to 154.9,
                13 to 172.1, // MCS 12-13: 4096-QAM
            )

        val baseRate = baseRates20MHz[mcs.level] ?: return null

        val widthMultiplier =
            when (width) {
                ChannelWidth.WIDTH_20MHZ -> 1.0
                ChannelWidth.WIDTH_40MHZ -> 2.0
                ChannelWidth.WIDTH_80MHZ -> 4.0
                ChannelWidth.WIDTH_160MHZ -> 8.0
                ChannelWidth.WIDTH_320MHZ -> 16.0 // WiFi 7 exclusive
                else -> return null
            }

        return baseRate * widthMultiplier * nss
    }

    /**
     * Check if MCS level is valid for WiFi standard
     *
     * Uses per-stream MCS notation (0-7 for WiFi 4, not legacy 0-31).
     * NSS is handled separately in getPhyRateMbps().
     */
    private fun isValidMcsForStandard(
        mcs: McsLevel,
        standard: WifiStandard,
    ): Boolean =
        when (standard) {
            WifiStandard.WIFI_4 -> mcs.level in 0..7 // Per-stream MCS (not legacy 0-31)
            WifiStandard.WIFI_5 -> mcs.level in 0..9
            WifiStandard.WIFI_6, WifiStandard.WIFI_6E -> mcs.level in 0..11
            WifiStandard.WIFI_7 -> mcs.level in 0..13
            else -> false
        }

    /**
     * Check if channel width is valid for WiFi standard
     */
    private fun isValidWidthForStandard(
        width: ChannelWidth,
        standard: WifiStandard,
    ): Boolean =
        when (standard) {
            WifiStandard.WIFI_4 -> width in listOf(ChannelWidth.WIDTH_20MHZ, ChannelWidth.WIDTH_40MHZ)
            WifiStandard.WIFI_5 ->
                width in
                    listOf(
                        ChannelWidth.WIDTH_20MHZ,
                        ChannelWidth.WIDTH_40MHZ,
                        ChannelWidth.WIDTH_80MHZ,
                        ChannelWidth.WIDTH_160MHZ,
                    )
            WifiStandard.WIFI_6, WifiStandard.WIFI_6E ->
                width in
                    listOf(
                        ChannelWidth.WIDTH_20MHZ,
                        ChannelWidth.WIDTH_40MHZ,
                        ChannelWidth.WIDTH_80MHZ,
                        ChannelWidth.WIDTH_160MHZ,
                    )
            WifiStandard.WIFI_7 -> true // Supports all widths including 320 MHz
            else -> false
        }
}

/**
 * Calculate effective throughput accounting for protocol overhead
 *
 * Helper function for MCS rate calculations.
 *
 * @param phyRateMbps PHY layer rate
 * @param standard WiFi standard (affects overhead)
 * @return Estimated effective throughput in Mbps
 */
fun calculateEffectiveThroughput(
    phyRateMbps: Double,
    standard: WifiStandard,
): Double {
    val efficiency =
        when (standard) {
            WifiStandard.WIFI_4 -> 0.50 // 50% efficiency (high overhead)
            WifiStandard.WIFI_5 -> 0.60 // 60% efficiency (improved)
            WifiStandard.WIFI_6, WifiStandard.WIFI_6E -> 0.70 // 70% efficiency (OFDMA, better MAC)
            WifiStandard.WIFI_7 -> 0.75 // 75% efficiency (MLO, better aggregation)
            else -> 0.40 // Legacy standards
        }
    return phyRateMbps * efficiency
}

/**
 * Get maximum theoretical throughput for a configuration
 *
 * @param standard WiFi standard
 * @param channelWidth Channel bandwidth
 * @param nss Number of spatial streams
 * @return Maximum PHY rate in Mbps, or null if unsupported
 */
fun getMaxTheoreticalThroughput(
    standard: WifiStandard,
    channelWidth: ChannelWidth,
    nss: Int,
): Double? {
    val rateTable = McsRateTable.standard()
    return rateTable.getMaxPhyRateMbps(standard, channelWidth, nss)
}
