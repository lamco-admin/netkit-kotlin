package io.lamco.netkit.ieparser.model

/**
 * HT (High Throughput) Capabilities - IEEE 802.11n / WiFi 4
 *
 * WiFi 4 introduced MIMO (Multiple Input Multiple Output) and 40 MHz channels.
 *
 * Key features:
 * - 40 MHz channel bonding (2x20 MHz)
 * - Up to 4 spatial streams (MIMO)
 * - Short Guard Interval (400ns instead of 800ns)
 * - Frame aggregation (A-MPDU, A-MSDU)
 * - Max theoretical speed: 600 Mbps (4 streams, 40 MHz, SGI)
 *
 * Information Element ID: 45
 * Length: 26 bytes
 *
 * Structure:
 * - HT Capabilities Info (2 bytes)
 * - A-MPDU Parameters (1 byte)
 * - Supported MCS Set (16 bytes)
 * - HT Extended Capabilities (2 bytes)
 * - Transmit Beamforming Capabilities (4 bytes)
 * - ASEL Capabilities (1 byte)
 *
 * @property supports40MHz Whether 40 MHz channels are supported
 * @property shortGI20MHz Short Guard Interval for 20 MHz (better throughput)
 * @property shortGI40MHz Short Guard Interval for 40 MHz
 * @property maxSpatialStreams Maximum MIMO spatial streams (1-4)
 * @property greenfield Greenfield mode (HT-only, no legacy support)
 * @property supportedMCS List of supported MCS indices (0-31)
 */
data class HTCapabilities(
    val supports40MHz: Boolean = false,
    val shortGI20MHz: Boolean = false,
    val shortGI40MHz: Boolean = false,
    val maxSpatialStreams: Int = 1,
    val greenfield: Boolean = false,
    val supportedMCS: List<Int> = emptyList(),
) {
    /**
     * Maximum theoretical PHY rate in Mbps
     * Based on: streams, channel width, guard interval
     */
    val maxPhyRateMbps: Int
        get() =
            when {
                maxSpatialStreams == 4 && supports40MHz && shortGI40MHz -> 600
                maxSpatialStreams == 4 && supports40MHz -> 540
                maxSpatialStreams == 3 && supports40MHz && shortGI40MHz -> 450
                maxSpatialStreams == 3 && supports40MHz -> 405
                maxSpatialStreams == 2 && supports40MHz && shortGI40MHz -> 300
                maxSpatialStreams == 2 && supports40MHz -> 270
                maxSpatialStreams == 1 && supports40MHz && shortGI40MHz -> 150
                maxSpatialStreams == 1 && supports40MHz -> 135
                maxSpatialStreams == 1 && shortGI20MHz -> 72
                else -> 65 // 1 stream, 20 MHz, LGI
            }

    /**
     * Whether this is a high-performance HT configuration
     * (40 MHz, 2+ streams, Short GI)
     */
    val isHighPerformance: Boolean
        get() = supports40MHz && maxSpatialStreams >= 2 && shortGI40MHz

    /**
     * Recommended for 2.4 GHz band
     * (40 MHz not recommended on 2.4 GHz due to overlap)
     */
    val recommendedFor24GHz: Boolean
        get() = !supports40MHz // Only 20 MHz recommended

    companion object {
        /**
         * Calculate spatial streams from MCS bitmap
         *
         * MCS 0-7: 1 stream
         * MCS 8-15: 2 streams
         * MCS 16-23: 3 streams
         * MCS 24-31: 4 streams
         *
         * @param mcsBitmap 16-byte bitmap of supported MCS indices
         * @return Number of spatial streams (1-4)
         */
        fun calculateSpatialStreams(mcsBitmap: ByteArray): Int {
            // Check each byte - each represents 8 MCS values
            // Bytes 0-1: streams 1-2, Bytes 2-3: streams 3-4
            return when {
                mcsBitmap.getOrNull(3)?.toInt() != 0 -> 4 // MCS 24-31
                mcsBitmap.getOrNull(2)?.toInt() != 0 -> 3 // MCS 16-23
                mcsBitmap.getOrNull(1)?.toInt() != 0 -> 2 // MCS 8-15
                mcsBitmap.getOrNull(0)?.toInt() != 0 -> 1 // MCS 0-7
                else -> 1
            }
        }

        /**
         * Extract supported MCS indices from bitmap
         *
         * @param mcsBitmap 16-byte bitmap
         * @return List of supported MCS indices (0-31)
         */
        fun extractSupportedMCS(mcsBitmap: ByteArray): List<Int> {
            val supported = mutableListOf<Int>()
            for (byteIndex in 0 until 4) { // Only first 4 bytes (MCS 0-31)
                val byte = mcsBitmap.getOrNull(byteIndex)?.toInt() ?: continue
                for (bit in 0..7) {
                    if ((byte and (1 shl bit)) != 0) {
                        supported.add(byteIndex * 8 + bit)
                    }
                }
            }
            return supported
        }
    }
}
