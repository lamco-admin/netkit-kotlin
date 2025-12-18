package io.lamco.netkit.rf.snr

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.rf.model.McsLevel

/**
 * MCS-to-SNR requirement lookup table
 *
 * Maps MCS levels to minimum required SNR values based on WiFi standard,
 * channel width, and number of spatial streams.
 *
 * SNR requirements are derived from:
 * - IEEE 802.11 standards (802.11n/ac/ax/be)
 * - Empirical measurements
 * - Vendor datasheets
 * - Research literature
 *
 * Values include ~3 dB margin for reliable operation (10% PER threshold).
 *
 * **Standard-specific notes:**
 * - **WiFi 4 (11n)**: MCS 0-7 per stream, BPSK to 64-QAM
 * - **WiFi 5 (11ac)**: MCS 0-9 per stream, up to 256-QAM
 * - **WiFi 6 (11ax)**: MCS 0-11 per stream, up to 1024-QAM
 * - **WiFi 7 (11be)**: MCS 0-13 per stream, up to 4096-QAM
 *
 * @see SnrCalculator for SNR calculation and link margin
 */
interface McsSnrTable {
    /**
     * Get required SNR for specific MCS configuration
     *
     * @param mcs MCS level (0-13)
     * @param standard WiFi standard (affects modulation schemes)
     * @param channelWidth Channel bandwidth (affects noise bandwidth)
     * @param nss Number of spatial streams (affects SNR requirements)
     * @return Required SNR in dB for 10% PER (packet error rate)
     */
    fun requiredSnrFor(
        mcs: McsLevel,
        standard: WifiStandard,
        channelWidth: ChannelWidth,
        nss: Int,
    ): Double

    companion object {
        /**
         * Create default MCS-SNR table with standard values
         */
        fun default(): McsSnrTable = StandardMcsSnrTable()
    }
}

/**
 * Standard MCS-SNR table with IEEE 802.11 and empirical values
 *
 * SNR requirements are conservative (include 3 dB margin) to ensure
 * reliable operation in real-world conditions with interference.
 */
class StandardMcsSnrTable : McsSnrTable {
    override fun requiredSnrFor(
        mcs: McsLevel,
        standard: WifiStandard,
        channelWidth: ChannelWidth,
        nss: Int,
    ): Double {
        require(nss in 1..16) { "NSS must be 1-16: $nss" }

        // Base SNR requirement for single stream
        val baseSnr = getBaseSnrForMcs(mcs, standard)

        // Adjust for channel width (wider = more noise)
        val widthPenalty = getChannelWidthPenalty(channelWidth)

        // Adjust for multiple streams (MIMO penalty)
        val nssPenalty = getNssPenalty(nss)

        return baseSnr + widthPenalty + nssPenalty
    }

    /**
     * Get base SNR requirement for MCS level (20 MHz, 1 stream)
     *
     * Values from IEEE 802.11 standards and empirical data:
     * - Include ~3 dB margin for 10% PER
     * - Account for real-world impairments
     */
    private fun getBaseSnrForMcs(
        mcs: McsLevel,
        standard: WifiStandard,
    ): Double =
        when (standard) {
            WifiStandard.WIFI_7 -> getWiFi7BaseSnr(mcs)
            WifiStandard.WIFI_6, WifiStandard.WIFI_6E -> getWiFi6BaseSnr(mcs)
            WifiStandard.WIFI_5 -> getWiFi5BaseSnr(mcs)
            WifiStandard.WIFI_4 -> getWiFi4BaseSnr(mcs)
            WifiStandard.LEGACY -> 10.0 // Legacy OFDM minimum
            WifiStandard.UNKNOWN -> 10.0 // Default if unknown
        }

    /**
     * WiFi 7 (802.11be) MCS-SNR requirements
     * MCS 0-13, includes 4096-QAM (MCS 12-13)
     */
    private fun getWiFi7BaseSnr(mcs: McsLevel): Double =
        WIFI_7_SNR_TABLE.getOrElse(mcs.level) { 45.0 } // Safety margin for invalid MCS

    companion object {
        /**
         * WiFi 7 MCS-SNR lookup table (MCS 0-13).
         * Indexed by MCS level.
         */
        private val WIFI_7_SNR_TABLE =
            doubleArrayOf(
                6.0, // MCS 0: BPSK 1/2
                8.0, // MCS 1: QPSK 1/2
                10.0, // MCS 2: QPSK 3/4
                12.0, // MCS 3: 16-QAM 1/2
                14.0, // MCS 4: 16-QAM 3/4
                18.0, // MCS 5: 64-QAM 2/3
                20.0, // MCS 6: 64-QAM 3/4
                22.0, // MCS 7: 64-QAM 5/6
                24.0, // MCS 8: 256-QAM 3/4
                26.0, // MCS 9: 256-QAM 5/6
                30.0, // MCS 10: 1024-QAM 3/4
                33.0, // MCS 11: 1024-QAM 5/6
                38.0, // MCS 12: 4096-QAM 3/4 (WiFi 7)
                41.0, // MCS 13: 4096-QAM 5/6 (WiFi 7)
            )
    }

    /**
     * WiFi 6 (802.11ax) MCS-SNR requirements
     * MCS 0-11, includes 1024-QAM (MCS 10-11)
     */
    private fun getWiFi6BaseSnr(mcs: McsLevel): Double =
        when (mcs.level) {
            0 -> 6.0 // BPSK 1/2
            1 -> 8.0 // QPSK 1/2
            2 -> 10.0 // QPSK 3/4
            3 -> 12.0 // 16-QAM 1/2
            4 -> 14.0 // 16-QAM 3/4
            5 -> 18.0 // 64-QAM 2/3
            6 -> 20.0 // 64-QAM 3/4
            7 -> 22.0 // 64-QAM 5/6
            8 -> 24.0 // 256-QAM 3/4
            9 -> 26.0 // 256-QAM 5/6
            10 -> 30.0 // 1024-QAM 3/4 (WiFi 6)
            11 -> 33.0 // 1024-QAM 5/6 (WiFi 6)
            else -> 35.0 // Beyond WiFi 6 spec
        }

    /**
     * WiFi 5 (802.11ac) MCS-SNR requirements
     * MCS 0-9, max 256-QAM
     */
    private fun getWiFi5BaseSnr(mcs: McsLevel): Double =
        when (mcs.level) {
            0 -> 6.0 // BPSK 1/2
            1 -> 8.0 // QPSK 1/2
            2 -> 10.0 // QPSK 3/4
            3 -> 12.0 // 16-QAM 1/2
            4 -> 14.0 // 16-QAM 3/4
            5 -> 18.0 // 64-QAM 2/3
            6 -> 20.0 // 64-QAM 3/4
            7 -> 22.0 // 64-QAM 5/6
            8 -> 24.0 // 256-QAM 3/4
            9 -> 26.0 // 256-QAM 5/6
            else -> 28.0 // Beyond WiFi 5 spec
        }

    /**
     * WiFi 4 (802.11n) MCS-SNR requirements
     * MCS 0-7 per stream, max 64-QAM
     */
    private fun getWiFi4BaseSnr(mcs: McsLevel): Double =
        when (mcs.level) {
            0 -> 6.0 // BPSK 1/2
            1 -> 8.0 // QPSK 1/2
            2 -> 10.0 // QPSK 3/4
            3 -> 12.0 // 16-QAM 1/2
            4 -> 14.0 // 16-QAM 3/4
            5 -> 18.0 // 64-QAM 2/3
            6 -> 20.0 // 64-QAM 3/4
            7 -> 22.0 // 64-QAM 5/6
            else -> 24.0 // Beyond WiFi 4 spec
        }

    /**
     * Channel width penalty (wider channels have more noise)
     *
     * Noise power increases with bandwidth: N = N0 + 10*log10(BW)
     * - 20 MHz: 0 dB (baseline)
     * - 40 MHz: +3 dB
     * - 80 MHz: +6 dB
     * - 160 MHz: +9 dB
     * - 320 MHz: +12 dB
     */
    private fun getChannelWidthPenalty(width: ChannelWidth): Double =
        when (width) {
            ChannelWidth.WIDTH_20MHZ -> 0.0
            ChannelWidth.WIDTH_40MHZ -> 3.0
            ChannelWidth.WIDTH_80MHZ -> 6.0
            ChannelWidth.WIDTH_160MHZ -> 9.0
            ChannelWidth.WIDTH_320MHZ -> 12.0
            ChannelWidth.UNKNOWN -> 0.0 // Default to 20 MHz penalty
        }

    /**
     * NSS (spatial stream) penalty
     *
     * Multiple streams require higher SNR due to:
     * - Inter-stream interference
     * - Channel estimation errors
     * - Receiver complexity
     *
     * Penalty is logarithmic: ~1.5 dB per doubling of streams
     */
    private fun getNssPenalty(nss: Int): Double =
        when {
            nss <= 1 -> 0.0
            nss == 2 -> 1.5
            nss <= 4 -> 3.0
            nss <= 8 -> 4.5
            else -> 6.0 // 9+ streams (WiFi 7)
        }
}
