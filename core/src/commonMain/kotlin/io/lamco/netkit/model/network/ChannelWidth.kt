package io.lamco.netkit.model.network

/**
 * WiFi channel width (bandwidth)
 *
 * Wider channels provide higher throughput but:
 * - Consume more spectrum
 * - More susceptible to interference
 * - Not always beneficial in congested environments
 *
 * Channel widths by standard:
 * - 802.11n (WiFi 4): 20, 40 MHz
 * - 802.11ac (WiFi 5): 20, 40, 80, 160 MHz
 * - 802.11ax (WiFi 6): 20, 40, 80, 160 MHz
 * - 802.11be (WiFi 7): 20, 40, 80, 160, 320 MHz
 *
 * @property widthMHz Channel width in MHz
 * @property displayName Human-readable name
 */
enum class ChannelWidth(
    val widthMHz: Int,
    val displayName: String,
) {
    /**
     * 20 MHz - Standard channel width
     * - Available on all bands
     * - Best for congested environments
     * - Maximum compatibility
     */
    WIDTH_20MHZ(20, "20 MHz"),

    /**
     * 40 MHz - Channel bonding (2x20)
     * - 2.4 GHz: Not recommended (overlaps)
     * - 5/6 GHz: Good balance
     * - 2x throughput of 20 MHz
     */
    WIDTH_40MHZ(40, "40 MHz"),

    /**
     * 80 MHz - Wide channel (4x20)
     * - 5/6 GHz only
     * - Significantly higher throughput
     * - Requires low interference
     */
    WIDTH_80MHZ(80, "80 MHz"),

    /**
     * 160 MHz - Very wide channel (8x20)
     * - 5/6 GHz only
     * - Maximum throughput (WiFi 5/6)
     * - Rare in practice (consumes spectrum)
     * - Two modes: contiguous or 80+80
     */
    WIDTH_160MHZ(160, "160 MHz"),

    /**
     * 320 MHz - Ultra-wide channel (16x20)
     * - 6 GHz only (WiFi 7)
     * - Highest theoretical throughput
     * - Extremely rare
     * - Requires clean 6 GHz spectrum
     */
    WIDTH_320MHZ(320, "320 MHz"),

    /**
     * Unknown or undetectable width
     */
    UNKNOWN(0, "Unknown"),
    ;

    /**
     * Number of 20 MHz channels bonded together
     */
    val channelCount: Int
        get() = if (widthMHz > 0) widthMHz / 20 else 0

    /**
     * Relative throughput multiplier compared to 20 MHz
     * (Theoretical, actual depends on interference)
     */
    val throughputMultiplier: Double
        get() =
            when (this) {
                WIDTH_20MHZ -> 1.0
                WIDTH_40MHZ -> 2.0
                WIDTH_80MHZ -> 4.0
                WIDTH_160MHZ -> 8.0
                WIDTH_320MHZ -> 16.0
                UNKNOWN -> 0.0
            }

    /**
     * Recommended for 2.4 GHz band
     * (Only 20 MHz recommended due to overlap)
     */
    val recommendedFor24GHz: Boolean
        get() = this == WIDTH_20MHZ

    /**
     * Available on 5 GHz band
     */
    val availableOn5GHz: Boolean
        get() = this in listOf(WIDTH_20MHZ, WIDTH_40MHZ, WIDTH_80MHZ, WIDTH_160MHZ)

    /**
     * Available on 6 GHz band
     */
    val availableOn6GHz: Boolean
        get() = this != UNKNOWN

    companion object {
        /**
         * Convert Android ScanResult channel width constant to ChannelWidth
         *
         * Android constants:
         * - 0 = 20 MHz
         * - 1 = 40 MHz
         * - 2 = 80 MHz
         * - 3 = 160 MHz
         * - 4 = 80+80 MHz (treated as 160)
         * - 5 = 5 MHz (not used in WiFi)
         * - 6 = 10 MHz (not used in WiFi)
         * - 7 = 320 MHz (WiFi 7)
         *
         * @param androidConstant Android ScanResult.channelWidth value
         * @return Corresponding ChannelWidth enum
         */
        fun fromAndroidConstant(androidConstant: Int): ChannelWidth =
            when (androidConstant) {
                0 -> WIDTH_20MHZ
                1 -> WIDTH_40MHZ
                2 -> WIDTH_80MHZ
                3, 4 -> WIDTH_160MHZ // 3 = 160, 4 = 80+80
                7 -> WIDTH_320MHZ
                else -> UNKNOWN
            }

        /**
         * Convert width in MHz to ChannelWidth enum
         *
         * @param widthMHz Channel width in MHz
         * @return Corresponding ChannelWidth enum
         */
        fun fromMHz(widthMHz: Int): ChannelWidth = values().find { it.widthMHz == widthMHz } ?: UNKNOWN
    }
}
