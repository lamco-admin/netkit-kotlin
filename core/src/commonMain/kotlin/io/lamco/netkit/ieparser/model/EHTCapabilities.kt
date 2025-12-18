package io.lamco.netkit.ieparser.model

/**
 * EHT (Extremely High Throughput) Capabilities - IEEE 802.11be / WiFi 7
 *
 * WiFi 7 delivers massive performance improvements for the next decade.
 *
 * Key features:
 * - 320 MHz channels (6 GHz only)
 * - MLO (Multi-Link Operation) - simultaneous multi-band
 * - 4096-QAM modulation
 * - Multi-RU (Resource Unit) allocation
 * - Preamble puncturing
 * - Up to 16 spatial streams (commercial: 2-4)
 * - Max theoretical speed: 46 Gbps (16 streams, 320 MHz)
 *
 * Information Element: Extension ID 106 (within IE 255)
 * Length: Variable (minimum 10 bytes)
 *
 * Structure:
 * - Extension ID (1 byte)
 * - EHT MAC Capabilities (2 bytes)
 * - EHT PHY Capabilities (9 bytes)
 * - Supported EHT-MCS and NSS Set (variable)
 * - PPE Thresholds (optional, variable)
 *
 * @property supports320MHz 320 MHz channels (6 GHz only)
 * @property multiLinkOperation Multi-Link Operation (simultaneous 2.4/5/6 GHz)
 * @property multiRUSupport Multiple Resource Units per user
 * @property puncturingSupport Preamble puncturing (skip interfered channels)
 * @property supports4096QAM 4096-QAM modulation (12 bits per symbol)
 * @property maxSpatialStreams Maximum MIMO spatial streams (1-16)
 * @property mloMaxLinks Maximum number of simultaneous links (MLO)
 * @property mloModes Supported MLO modes (STR, NSTR, etc.)
 */
data class EHTCapabilities(
    val supports320MHz: Boolean = false,
    val multiLinkOperation: Boolean = false,
    val multiRUSupport: Boolean = false,
    val puncturingSupport: Boolean = false,
    val supports4096QAM: Boolean = false,
    val maxSpatialStreams: Int = 1,
    val mloMaxLinks: Int = 1,
    val mloModes: List<MLOMode> = emptyList(),
) {
    /**
     * Maximum channel width supported
     */
    val maxChannelWidthMHz: Int
        get() = if (supports320MHz) 320 else 160

    /**
     * Maximum theoretical PHY rate in Mbps (single link)
     * Based on: streams, channel width, 4096-QAM
     * EHT adds ~20% over HE due to improved efficiency
     */
    val maxPhyRateMbps: Int
        get() =
            when {
                // 16 streams, 320 MHz, 4096-QAM
                maxSpatialStreams == 16 && supports320MHz -> 46120
                maxSpatialStreams == 16 -> 23060

                // 8 streams, 320 MHz
                maxSpatialStreams == 8 && supports320MHz -> 23060
                maxSpatialStreams == 8 -> 11530

                // 4 streams, 320 MHz
                maxSpatialStreams == 4 && supports320MHz -> 11530
                maxSpatialStreams == 4 -> 5765

                // 2 streams, 320 MHz
                maxSpatialStreams == 2 && supports320MHz -> 5765
                maxSpatialStreams == 2 -> 2882

                // 1 stream, 320 MHz
                maxSpatialStreams == 1 && supports320MHz -> 2882

                else -> 1441 // 1 stream, 160 MHz
            }

    /**
     * Maximum aggregate PHY rate with MLO (Mbps)
     * Assumes 3 simultaneous links (2.4 + 5 + 6 GHz)
     */
    val maxMLOPhyRateMbps: Int
        get() {
            if (!multiLinkOperation || mloMaxLinks < 2) {
                return maxPhyRateMbps
            }

            // Simplified: assume 6 GHz gets max rate, 5 GHz gets 70%, 2.4 GHz gets 20%
            return when (mloMaxLinks) {
                3 -> (maxPhyRateMbps * 1.9).toInt() // ~100% + 70% + 20%
                2 -> (maxPhyRateMbps * 1.7).toInt() // ~100% + 70%
                else -> maxPhyRateMbps
            }
        }

    /**
     * Whether this is a high-performance EHT configuration
     * (320 MHz, 2+ streams, MLO, 4096-QAM)
     */
    val isHighPerformance: Boolean
        get() =
            supports320MHz &&
                maxSpatialStreams >= 2 &&
                multiLinkOperation &&
                supports4096QAM

    /**
     * Whether this supports WiFi 7 advanced features
     */
    val hasAdvancedFeatures: Boolean
        get() = multiRUSupport && puncturingSupport

    /**
     * Whether this is optimized for ultra-low latency
     * (MLO + Multi-RU + puncturing)
     */
    val isUltraLowLatency: Boolean
        get() = multiLinkOperation && multiRUSupport && puncturingSupport
}

/**
 * MLO (Multi-Link Operation) Modes
 *
 * Defines how multiple links are coordinated.
 *
 * @property displayName Human-readable mode name
 * @property description Mode description
 */
enum class MLOMode(
    val displayName: String,
    val description: String,
) {
    /**
     * STR (Simultaneous Transmit and Receive)
     * - Can TX/RX on multiple links simultaneously
     * - Best performance, most complex
     * - Requires independent radios per band
     */
    STR(
        "STR (Simultaneous)",
        "Simultaneous transmit/receive on multiple links",
    ),

    /**
     * NSTR (Non-Simultaneous Transmit and Receive)
     * - Cannot TX on multiple links at same time
     * - Simpler, lower cost
     * - Still provides link aggregation benefits
     */
    NSTR(
        "NSTR (Non-Simultaneous)",
        "Non-simultaneous transmit on multiple links",
    ),

    /**
     * eMBB (enhanced Mobile Broadband)
     * - Optimized for high throughput
     * - Dynamic link selection based on conditions
     */
    EMBB(
        "eMBB (High Throughput)",
        "Enhanced mobile broadband mode",
    ),

    /**
     * URLLC (Ultra-Reliable Low-Latency Communication)
     * - Optimized for latency and reliability
     * - Duplicate transmission on multiple links
     * - Gaming, AR/VR use cases
     */
    URLLC(
        "URLLC (Low Latency)",
        "Ultra-reliable low-latency communication",
    ),
    ;

    /**
     * Whether this mode supports simultaneous TX/RX
     */
    val isSimultaneous: Boolean
        get() = this == STR

    /**
     * Whether this mode is optimized for latency
     */
    val isLatencyOptimized: Boolean
        get() = this == URLLC
}
