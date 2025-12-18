package io.lamco.netkit.rf.model

/**
 * Modulation and Coding Scheme (MCS) level
 *
 * MCS defines the modulation, coding rate, and data rate for WiFi transmissions.
 * Higher MCS requires better SNR but provides higher throughput.
 *
 * MCS ranges:
 * - **802.11n (HT)**: MCS 0-31 (up to 4 spatial streams)
 * - **802.11ac (VHT)**: MCS 0-9 per stream
 * - **802.11ax (HE)**: MCS 0-11 per stream
 * - **802.11be (EHT)**: MCS 0-13 per stream (4096-QAM)
 *
 * @property level MCS index (0-15)
 * @property modulation Modulation scheme (BPSK, QPSK, 16-QAM, 64-QAM, 256-QAM, 1024-QAM, 4096-QAM)
 * @property codingRate Forward error correction rate (1/2, 2/3, 3/4, 5/6)
 */
data class McsLevel(
    val level: Int,
) {
    init {
        require(level in 0..15) { "MCS level must be in range 0-15, got $level" }
    }

    /**
     * Approximate modulation scheme for this MCS level
     * - MCS 0-1: BPSK/QPSK
     * - MCS 2-4: 16-QAM
     * - MCS 5-7: 64-QAM
     * - MCS 8-9: 256-QAM
     * - MCS 10-11: 1024-QAM (WiFi 6)
     * - MCS 12-13: 4096-QAM (WiFi 7)
     */
    val modulation: String
        get() =
            when (level) {
                0 -> "BPSK"
                1 -> "QPSK"
                in 2..4 -> "16-QAM"
                in 5..7 -> "64-QAM"
                in 8..9 -> "256-QAM"
                in 10..11 -> "1024-QAM"
                in 12..13 -> "4096-QAM"
                else -> "Unknown"
            }

    /**
     * Approximate coding rate for this MCS level
     */
    val codingRate: String
        get() =
            when (level % 8) {
                0, 1 -> "1/2"
                2, 3 -> "2/3"
                4, 5 -> "3/4"
                6, 7 -> "5/6"
                else -> "Unknown"
            }

    /**
     * Relative complexity (0.0 = simplest, 1.0 = most complex)
     * - Lower MCS: more robust, lower throughput
     * - Higher MCS: more fragile, higher throughput
     */
    val complexity: Double
        get() = level / 13.0

    /**
     * Whether this is a high-performance MCS (8+)
     * Requires good SNR (>= 20 dB typically)
     */
    val isHighPerformance: Boolean
        get() = level >= 8

    companion object {
        /** Minimum MCS level (most robust) */
        val MIN = McsLevel(0)

        /** Maximum MCS level for WiFi 6 (802.11ax) */
        val MAX_WIFI_6 = McsLevel(11)

        /** Maximum MCS level for WiFi 7 (802.11be) */
        val MAX_WIFI_7 = McsLevel(13)

        /**
         * Typical MCS used for good indoor WiFi (64-QAM, 3/4 rate)
         */
        val TYPICAL = McsLevel(7)
    }
}
