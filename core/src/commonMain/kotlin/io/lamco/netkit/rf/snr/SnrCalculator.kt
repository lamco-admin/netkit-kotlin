package io.lamco.netkit.rf.snr

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.rf.model.McsLevel
import io.lamco.netkit.rf.noise.NoiseModel

/**
 * Signal-to-Noise Ratio (SNR) and link margin calculator
 *
 * SNR is the fundamental metric for wireless link quality. It determines:
 * - Maximum achievable MCS (modulation/coding scheme)
 * - Link reliability and packet error rate
 * - Maximum data rate
 * - Connection stability
 *
 * **SNR Calculation:**
 * ```
 * SNR (dB) = RSSI (dBm) - Noise Floor (dBm)
 * ```
 *
 * **Link Margin:**
 * Link margin is the "safety buffer" above the minimum SNR required for a given MCS.
 * Positive margin = reliable link, negative margin = unreliable/impossible.
 * ```
 * Link Margin (dB) = Actual SNR - Required SNR for MCS
 * ```
 *
 * **Typical SNR Requirements:**
 * - MCS 0 (BPSK 1/2): 6-8 dB minimum
 * - MCS 7 (64-QAM 5/6): 25 dB minimum
 * - MCS 11 (1024-QAM 5/6): 35 dB minimum (WiFi 6)
 * - MCS 13 (4096-QAM 5/6): 40 dB minimum (WiFi 7)
 *
 * @property noiseModel Model for noise floor estimation
 * @property mcsTable Table of MCS-specific SNR requirements
 */
class SnrCalculator(
    private val noiseModel: NoiseModel,
    private val mcsTable: McsSnrTable = McsSnrTable.default(),
) {
    /**
     * Calculate SNR from RSSI and band
     *
     * @param rssiDbm Received signal strength in dBm
     * @param band WiFi frequency band
     * @return SNR in dB
     */
    fun calculateSnr(
        rssiDbm: Int,
        band: WiFiBand,
    ): Double {
        require(rssiDbm in -120..0) { "RSSI must be between -120 and 0 dBm: $rssiDbm" }

        val noiseFloor = noiseModel.noiseFloorDbm(band)
        return rssiDbm - noiseFloor
    }

    /**
     * Calculate SNR with specific frequency (more granular)
     *
     * @param rssiDbm Received signal strength in dBm
     * @param freqMHz Center frequency in MHz
     * @param band WiFi frequency band
     * @return SNR in dB
     */
    fun calculateSnr(
        rssiDbm: Int,
        freqMHz: Int,
        band: WiFiBand,
    ): Double {
        require(rssiDbm in -120..0) { "RSSI must be between -120 and 0 dBm: $rssiDbm" }
        require(freqMHz > 0) { "Frequency must be positive: $freqMHz" }

        val noiseFloor = noiseModel.noiseFloorDbm(freqMHz, band)
        return rssiDbm - noiseFloor
    }

    /**
     * Calculate link margin for a specific MCS level
     *
     * Link margin indicates how much "headroom" exists above the minimum required SNR:
     * - **Positive margin**: Link can sustain this MCS reliably
     * - **Zero margin**: Link is at threshold, marginal reliability
     * - **Negative margin**: Link cannot sustain this MCS
     *
     * Recommended margins:
     * - 6+ dB: Excellent (very reliable)
     * - 3-6 dB: Good (reliable under most conditions)
     * - 0-3 dB: Fair (marginal, may degrade with interference)
     * - < 0 dB: Poor (unsustainable)
     *
     * @param snrDb Current SNR in dB
     * @param mcs Target MCS level
     * @param standard WiFi standard (affects MCS requirements)
     * @param channelWidth Channel bandwidth
     * @param nss Number of spatial streams
     * @return Link margin in dB (positive = good, negative = insufficient SNR)
     */
    fun calculateLinkMargin(
        snrDb: Double,
        mcs: McsLevel,
        standard: WifiStandard = WifiStandard.WIFI_6,
        channelWidth: ChannelWidth = ChannelWidth.WIDTH_80MHZ,
        nss: Int = 1,
    ): Double {
        val requiredSnr = mcsTable.requiredSnrFor(mcs, standard, channelWidth, nss)
        return snrDb - requiredSnr
    }

    /**
     * Find maximum achievable MCS level for current SNR
     *
     * Returns the highest MCS that has non-negative link margin.
     * This is the best MCS the link can reliably sustain.
     *
     * @param snrDb Current SNR in dB
     * @param standard WiFi standard
     * @param channelWidth Channel bandwidth
     * @param nss Number of spatial streams
     * @param minMargin Minimum required link margin in dB (default: 3 dB)
     * @return Maximum achievable MCS level, or null if SNR too low for any MCS
     */
    fun maxAchievableMcs(
        snrDb: Double,
        standard: WifiStandard,
        channelWidth: ChannelWidth,
        nss: Int,
        minMargin: Double = 3.0,
    ): McsLevel? {
        val maxMcsLevel =
            when (standard) {
                WifiStandard.WIFI_7 -> 13
                WifiStandard.WIFI_6, WifiStandard.WIFI_6E -> 11
                WifiStandard.WIFI_5 -> 9
                WifiStandard.WIFI_4 -> 7 // HT MCS 0-31, but per-stream max is 7
                WifiStandard.LEGACY -> 0
                WifiStandard.UNKNOWN -> 0 // Default if unknown
            }

        // Search from highest to lowest MCS
        for (level in maxMcsLevel downTo 0) {
            val mcs = McsLevel(level)
            val margin = calculateLinkMargin(snrDb, mcs, standard, channelWidth, nss)
            if (margin >= minMargin) {
                return mcs
            }
        }

        return null // SNR too low for any MCS
    }

    /**
     * Calculate SNR quality category
     *
     * @param snrDb SNR in dB
     * @return Quality category
     */
    fun snrQuality(snrDb: Double): SnrQuality =
        when {
            snrDb >= 40.0 -> SnrQuality.EXCELLENT // WiFi 7 capable
            snrDb >= 30.0 -> SnrQuality.VERY_GOOD // WiFi 6 high MCS
            snrDb >= 20.0 -> SnrQuality.GOOD // WiFi 5/6 medium MCS
            snrDb >= 15.0 -> SnrQuality.FAIR // WiFi 4/5 low MCS
            snrDb >= 10.0 -> SnrQuality.POOR // Minimum viable
            else -> SnrQuality.VERY_POOR // Unreliable
        }

    /**
     * Whether SNR is sufficient for reliable connectivity
     * Requires minimum SNR of 15 dB (enough for basic WiFi 4/5)
     */
    fun isReliableSnr(snrDb: Double): Boolean = snrDb >= 15.0

    /**
     * Whether SNR supports high-performance WiFi (MCS 8+)
     * Requires minimum SNR of 25 dB
     */
    fun supportsHighPerformance(snrDb: Double): Boolean = snrDb >= 25.0
}

/**
 * SNR quality categories
 *
 * @property minSnrDb Minimum SNR for this quality level
 * @property displayName Human-readable name
 * @property description Quality description with capabilities
 */
enum class SnrQuality(
    val minSnrDb: Double,
    val displayName: String,
    val description: String,
) {
    EXCELLENT(
        minSnrDb = 40.0,
        displayName = "Excellent",
        description = "WiFi 7 capable (4096-QAM), ultra-high throughput, lowest latency",
    ),
    VERY_GOOD(
        minSnrDb = 30.0,
        displayName = "Very Good",
        description = "WiFi 6 high MCS (1024-QAM), very high throughput, low latency",
    ),
    GOOD(
        minSnrDb = 20.0,
        displayName = "Good",
        description = "WiFi 5/6 medium MCS (256-QAM), high throughput, suitable for 4K",
    ),
    FAIR(
        minSnrDb = 15.0,
        displayName = "Fair",
        description = "WiFi 4/5 low MCS (64-QAM), adequate throughput, suitable for HD",
    ),
    POOR(
        minSnrDb = 10.0,
        displayName = "Poor",
        description = "Minimum viable (QPSK/16-QAM), low throughput, frequent retries",
    ),
    VERY_POOR(
        minSnrDb = Double.NEGATIVE_INFINITY,
        displayName = "Very Poor",
        description = "Unreliable connection, frequent packet loss and disconnections",
    ),
    ;

    /**
     * Numeric score (0-100) for this quality level
     */
    val score: Int
        get() =
            when (this) {
                EXCELLENT -> 100
                VERY_GOOD -> 85
                GOOD -> 70
                FAIR -> 55
                POOR -> 35
                VERY_POOR -> 15
            }
}
