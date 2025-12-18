package io.lamco.netkit.model.network

/**
 * WiFi frequency bands with security and RF characteristics
 *
 * Different bands have different propagation, interference, and security properties:
 * - 2.4 GHz: Longest range, most interference, largest attack surface
 * - 5 GHz: Medium range, less interference, better for security
 * - 6 GHz: Shortest range, minimal interference, mandatory WPA3, best security
 *
 * @property displayName Human-readable name
 * @property frequencyRange Frequency range in MHz
 * @property securityImplications Security considerations for this band
 */
enum class WiFiBand(
    val displayName: String,
    val frequencyRange: String,
    val securityImplications: String,
) {
    /**
     * 2.4 GHz band (802.11b/g/n/ax)
     * - Frequency: 2400-2500 MHz
     * - Channels: 1-14 (regulatory dependent)
     * - Range: ~100-150m indoor
     * - Congestion: Very high (WiFi, Bluetooth, microwaves)
     * - Security: Longer range = larger attack surface
     */
    BAND_2_4GHZ(
        "2.4 GHz",
        "2400-2500 MHz",
        "Longer range increases attack surface. More interference and congestion.",
    ),

    /**
     * 5 GHz band (802.11a/n/ac/ax)
     * - Frequency: 5000-5900 MHz
     * - Channels: Many non-overlapping channels
     * - Range: ~50-100m indoor
     * - Congestion: Lower than 2.4 GHz
     * - Security: Shorter range reduces attack surface
     */
    BAND_5GHZ(
        "5 GHz",
        "5000-5900 MHz",
        "Shorter range reduces attack surface. Less interference, better for security.",
    ),

    /**
     * 6 GHz band (WiFi 6E - 802.11ax/be)
     * - Frequency: 5925-7125 MHz
     * - Channels: 1200 MHz of spectrum
     * - Range: ~25-50m indoor
     * - Congestion: Minimal (new band)
     * - Security: MANDATORY WPA3, shortest range = best security
     */
    BAND_6GHZ(
        "6 GHz (WiFi 6E)",
        "5925-7125 MHz",
        "Mandatory WPA3. Shortest range = best security. Minimal interference.",
    ),

    /**
     * Unknown or undetectable band
     */
    UNKNOWN(
        "Unknown",
        "N/A",
        "Unable to determine frequency band",
    ),
    ;

    /**
     * Security score for this band (0-100)
     * Based on mandatory security requirements and attack surface
     */
    val securityScore: Int
        get() =
            when (this) {
                BAND_6GHZ -> 100 // Mandatory WPA3
                BAND_5GHZ -> 70 // Good security characteristics
                BAND_2_4GHZ -> 50 // Acceptable but longer range
                UNKNOWN -> 0
            }

    /**
     * Typical indoor range in meters.
     * Actual range depends on TX power, environment, and obstructions.
     */
    val typicalRangeMeters: IntRange
        get() =
            when (this) {
                BAND_2_4GHZ -> 100..150
                BAND_5GHZ -> 50..100
                BAND_6GHZ -> 25..50
                UNKNOWN -> 0..0
            }

    /**
     * Relative interference level (0-100, higher = more interference)
     */
    val interferenceLevel: Int
        get() =
            when (this) {
                BAND_2_4GHZ -> 90 // Very high
                BAND_5GHZ -> 40 // Moderate
                BAND_6GHZ -> 10 // Very low
                UNKNOWN -> 0
            }

    companion object {
        /**
         * Determine WiFi band from frequency in MHz
         *
         * @param frequency Frequency in MHz
         * @return Corresponding WiFiBand
         */
        fun fromFrequency(frequency: Int): WiFiBand =
            when (frequency) {
                in 2400..2500 -> BAND_2_4GHZ
                in 5000..5900 -> BAND_5GHZ
                in 5925..7125 -> BAND_6GHZ
                else -> UNKNOWN
            }

        /**
         * Convert frequency to channel number
         *
         * Channel numbering:
         * - 2.4 GHz: Channels 1-14
         *   - Channel 14 is Japan-only at 2484 MHz
         *   - Formula: (freq - 2407) / 5
         * - 5 GHz: Channels starting at 36
         *   - Formula: (freq - 5000) / 5
         * - 6 GHz: Channels starting at 1
         *   - Formula: (freq - 5955) / 5 + 1
         *
         * @param frequency Frequency in MHz
         * @param band WiFi band
         * @return Channel number, or -1 if unknown
         */
        fun frequencyToChannel(
            frequency: Int,
            band: WiFiBand,
        ): Int =
            when (band) {
                BAND_2_4GHZ -> {
                    if (frequency == 2484) {
                        14 // Japan channel 14
                    } else {
                        (frequency - 2407) / 5
                    }
                }
                BAND_5GHZ -> {
                    (frequency - 5000) / 5
                }
                BAND_6GHZ -> {
                    // 6 GHz uses 20 MHz spacing starting at 5955 MHz
                    (frequency - 5955) / 5 + 1
                }
                UNKNOWN -> -1
            }

        /**
         * Convert channel number to center frequency
         *
         * @param channel Channel number
         * @param band WiFi band
         * @return Center frequency in MHz, or -1 if unknown
         */
        fun channelToFrequency(
            channel: Int,
            band: WiFiBand,
        ): Int =
            when (band) {
                BAND_2_4GHZ -> {
                    if (channel == 14) {
                        2484
                    } else {
                        2407 + (channel * 5)
                    }
                }
                BAND_5GHZ -> {
                    5000 + (channel * 5)
                }
                BAND_6GHZ -> {
                    5955 + ((channel - 1) * 5)
                }
                UNKNOWN -> -1
            }
    }
}
