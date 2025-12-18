package io.lamco.netkit.ieparser.model

/**
 * VHT (Very High Throughput) Capabilities - IEEE 802.11ac / WiFi 5
 *
 * WiFi 5 brought gigabit speeds to consumer WiFi (5 GHz only).
 *
 * Key features:
 * - 80 MHz and 160 MHz channels
 * - Up to 8 spatial streams (consumer: typically 2-4)
 * - 256-QAM modulation
 * - MU-MIMO (Multi-User MIMO)
 * - Beamforming
 * - Max theoretical speed: 6.9 Gbps (8 streams, 160 MHz)
 *
 * Information Element ID: 191
 * Length: 12 bytes
 *
 * Structure:
 * - VHT Capabilities Info (4 bytes)
 * - Supported VHT-MCS and NSS Set (8 bytes)
 *
 * @property supports80MHz Whether 80 MHz channels are supported (mandatory in VHT)
 * @property supports160MHz Whether 160 MHz channels are supported
 * @property supports80Plus80MHz Whether non-contiguous 80+80 MHz is supported
 * @property maxSpatialStreams Maximum MIMO spatial streams (1-8)
 * @property muMimoCapable Multi-User MIMO support (AP can talk to multiple clients simultaneously)
 * @property beamformingCapable Transmit beamforming support
 * @property shortGI80MHz Short Guard Interval for 80 MHz
 * @property shortGI160MHz Short Guard Interval for 160 MHz
 * @property supportedMCS List of supported VHT-MCS indices per stream
 */
data class VHTCapabilities(
    val supports80MHz: Boolean = true, // Mandatory in VHT
    val supports160MHz: Boolean = false,
    val supports80Plus80MHz: Boolean = false,
    val maxSpatialStreams: Int = 1,
    val muMimoCapable: Boolean = false,
    val beamformingCapable: Boolean = false,
    val shortGI80MHz: Boolean = false,
    val shortGI160MHz: Boolean = false,
    val supportedMCS: Map<Int, List<Int>> = emptyMap(), // Stream -> MCS list
) {
    /**
     * Maximum channel width supported
     */
    val maxChannelWidthMHz: Int
        get() =
            when {
                supports160MHz || supports80Plus80MHz -> 160
                supports80MHz -> 80
                else -> 20 // Shouldn't happen in VHT
            }

    /**
     * Maximum theoretical PHY rate in Mbps
     * Based on: streams, channel width, guard interval
     */
    val maxPhyRateMbps: Int
        get() =
            when {
                // 8 streams, 160 MHz, SGI, MCS 9 (256-QAM 5/6)
                maxSpatialStreams == 8 && supports160MHz && shortGI160MHz -> 6933
                maxSpatialStreams == 8 && supports160MHz -> 6240

                // 4 streams, 160 MHz, SGI, MCS 9
                maxSpatialStreams == 4 && supports160MHz && shortGI160MHz -> 3467
                maxSpatialStreams == 4 && supports160MHz -> 3120

                // 4 streams, 80 MHz, SGI, MCS 9
                maxSpatialStreams == 4 && supports80MHz && shortGI80MHz -> 1733
                maxSpatialStreams == 4 && supports80MHz -> 1560

                // 2 streams, 80 MHz, SGI, MCS 9
                maxSpatialStreams == 2 && supports80MHz && shortGI80MHz -> 867
                maxSpatialStreams == 2 && supports80MHz -> 780

                // 1 stream, 80 MHz, SGI, MCS 9
                maxSpatialStreams == 1 && shortGI80MHz -> 433
                maxSpatialStreams == 1 -> 390

                else -> 390
            }

    /**
     * Whether this is a high-performance VHT configuration
     * (80+ MHz, 2+ streams, MU-MIMO or beamforming)
     */
    val isHighPerformance: Boolean
        get() =
            supports80MHz &&
                maxSpatialStreams >= 2 &&
                (muMimoCapable || beamformingCapable)

    /**
     * Whether this is a gigabit-capable configuration
     */
    val isGigabitCapable: Boolean
        get() = maxPhyRateMbps >= 1000

    companion object {
        /**
         * Calculate spatial streams from VHT-MCS map
         *
         * VHT uses 2 bits per stream to indicate max MCS:
         * - 00: MCS 0-7
         * - 01: MCS 0-8
         * - 10: MCS 0-9
         * - 11: Not supported
         *
         * @param mcsMap 16-bit RX or TX MCS map
         * @return Number of spatial streams (1-8)
         */
        fun calculateSpatialStreams(mcsMap: Int): Int {
            for (stream in 8 downTo 1) {
                val shift = (stream - 1) * 2
                val value = (mcsMap shr shift) and 0x03
                if (value != 0x03) { // 11 = not supported
                    return stream
                }
            }
            return 1
        }

        /**
         * Get maximum supported MCS for a given stream
         *
         * @param mcsMap 16-bit RX or TX MCS map
         * @param stream Spatial stream number (1-8)
         * @return Maximum MCS index (0-9), or -1 if not supported
         */
        fun getMaxMCSForStream(
            mcsMap: Int,
            stream: Int,
        ): Int {
            if (stream < 1 || stream > 8) return -1

            val shift = (stream - 1) * 2
            val value = (mcsMap shr shift) and 0x03

            return when (value) {
                0 -> 7 // MCS 0-7
                1 -> 8 // MCS 0-8
                2 -> 9 // MCS 0-9
                else -> -1 // Not supported
            }
        }

        /**
         * Extract supported MCS for all streams
         *
         * @param mcsMap 16-bit RX or TX MCS map
         * @return Map of stream number to list of supported MCS indices
         */
        fun extractSupportedMCS(mcsMap: Int): Map<Int, List<Int>> {
            val result = mutableMapOf<Int, List<Int>>()

            for (stream in 1..8) {
                val maxMCS = getMaxMCSForStream(mcsMap, stream)
                if (maxMCS >= 0) {
                    result[stream] = (0..maxMCS).toList()
                }
            }

            return result
        }
    }
}
