package io.lamco.netkit.model.network

/**
 * WiFi standard (generation) with technical details
 *
 * Each generation represents a significant advancement in WiFi technology:
 * - WiFi 4 (802.11n): First to support MIMO, 2.4/5 GHz
 * - WiFi 5 (802.11ac): Gigabit WiFi, 5 GHz only
 * - WiFi 6 (802.11ax): OFDMA, MU-MIMO, 2.4/5 GHz
 * - WiFi 6E (802.11ax): WiFi 6 + 6 GHz band
 * - WiFi 7 (802.11be): MLO, 320 MHz channels, 4096-QAM
 *
 * @property displayName Consumer-friendly name (WiFi 6, WiFi 7)
 * @property standardName IEEE standard designation (802.11ax, 802.11be)
 * @property releaseYear Year of initial release
 * @property maxTheoreticalSpeedGbps Maximum theoretical speed in Gbps (single stream)
 * @property bands Supported frequency bands
 */
enum class WifiStandard(
    val displayName: String,
    val standardName: String,
    val generation: Int,
    val releaseYear: Int,
    val maxTheoreticalSpeedGbps: Double,
    val bands: List<WiFiBand>,
) {
    /**
     * WiFi 4 (802.11n)
     * - Release: 2009
     * - Max speed: 600 Mbps (4 spatial streams)
     * - Bands: 2.4 GHz, 5 GHz
     * - Key features: MIMO, 40 MHz channels
     */
    WIFI_4(
        displayName = "WiFi 4",
        standardName = "802.11n",
        generation = 4,
        releaseYear = 2009,
        maxTheoreticalSpeedGbps = 0.6,
        bands = listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ),
    ),

    /**
     * WiFi 5 (802.11ac)
     * - Release: 2014
     * - Max speed: 6.9 Gbps (8 spatial streams)
     * - Bands: 5 GHz only
     * - Key features: MU-MIMO, 160 MHz channels, 256-QAM
     */
    WIFI_5(
        displayName = "WiFi 5",
        standardName = "802.11ac",
        generation = 5,
        releaseYear = 2014,
        maxTheoreticalSpeedGbps = 6.9,
        bands = listOf(WiFiBand.BAND_5GHZ),
    ),

    /**
     * WiFi 6 (802.11ax)
     * - Release: 2019
     * - Max speed: 9.6 Gbps (8 spatial streams)
     * - Bands: 2.4 GHz, 5 GHz
     * - Key features: OFDMA, BSS Coloring, TWT, 1024-QAM
     */
    WIFI_6(
        displayName = "WiFi 6",
        standardName = "802.11ax",
        generation = 6,
        releaseYear = 2019,
        maxTheoreticalSpeedGbps = 9.6,
        bands = listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ),
    ),

    /**
     * WiFi 6E (802.11ax on 6 GHz)
     * - Release: 2020
     * - Max speed: 9.6 Gbps (8 spatial streams)
     * - Bands: 6 GHz (also supports 2.4/5 GHz)
     * - Key features: WiFi 6 + 6 GHz spectrum, mandatory WPA3
     */
    WIFI_6E(
        displayName = "WiFi 6E",
        standardName = "802.11ax (6 GHz)",
        generation = 6,
        releaseYear = 2020,
        maxTheoreticalSpeedGbps = 9.6,
        bands = listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ, WiFiBand.BAND_6GHZ),
    ),

    /**
     * WiFi 7 (802.11be)
     * - Release: 2024
     * - Max speed: 46 Gbps (16 spatial streams)
     * - Bands: 2.4 GHz, 5 GHz, 6 GHz
     * - Key features: MLO, 320 MHz channels, 4096-QAM, Multi-RU
     */
    WIFI_7(
        displayName = "WiFi 7",
        standardName = "802.11be",
        generation = 7,
        releaseYear = 2024,
        maxTheoreticalSpeedGbps = 46.0,
        bands = listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ, WiFiBand.BAND_6GHZ),
    ),

    /**
     * Legacy 802.11a/b/g (pre-WiFi 4)
     * - Max speed: 54 Mbps (802.11a/g)
     * - Not commonly seen on modern networks
     */
    LEGACY(
        displayName = "Legacy WiFi",
        standardName = "802.11a/b/g",
        generation = 3,
        releaseYear = 1999,
        maxTheoreticalSpeedGbps = 0.054,
        bands = listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ),
    ),

    /**
     * Unknown or undetectable standard
     */
    UNKNOWN(
        displayName = "Unknown",
        standardName = "Unknown",
        generation = 0,
        releaseYear = 0,
        maxTheoreticalSpeedGbps = 0.0,
        bands = emptyList(),
    ),
    ;

    /**
     * Key features introduced in this WiFi generation
     */
    val keyFeatures: List<String>
        get() =
            when (this) {
                WIFI_4 -> listOf("MIMO", "40 MHz channels", "Frame aggregation")
                WIFI_5 -> listOf("MU-MIMO", "160 MHz channels", "256-QAM", "Beamforming")
                WIFI_6 -> listOf("OFDMA", "BSS Coloring", "TWT", "1024-QAM", "MU-MIMO UL/DL")
                WIFI_6E -> listOf("6 GHz band", "More spectrum", "Mandatory WPA3", "Less congestion")
                WIFI_7 -> listOf("MLO", "320 MHz channels", "4096-QAM", "Multi-RU", "Puncturing")
                LEGACY -> listOf("Basic WiFi", "Single stream", "Limited speed")
                UNKNOWN -> emptyList()
            }

    /**
     * Whether this standard is considered modern (WiFi 6 or newer)
     */
    val isModern: Boolean
        get() = this in listOf(WIFI_6, WIFI_6E, WIFI_7)

    /**
     * Whether this standard is deprecated and should be upgraded
     */
    val isDeprecated: Boolean
        get() = this == LEGACY

    /**
     * Supports OFDMA (Orthogonal Frequency Division Multiple Access)
     */
    val supportsOFDMA: Boolean
        get() = this in listOf(WIFI_6, WIFI_6E, WIFI_7)

    /**
     * Supports Multi-Link Operation (WiFi 7)
     */
    val supportsMLO: Boolean
        get() = this == WIFI_7

    /**
     * Maximum channel width supported by this standard
     */
    val maxChannelWidth: ChannelWidth
        get() =
            when (this) {
                WIFI_7 -> ChannelWidth.WIDTH_320MHZ
                WIFI_6, WIFI_6E, WIFI_5 -> ChannelWidth.WIDTH_160MHZ
                WIFI_4 -> ChannelWidth.WIDTH_40MHZ
                LEGACY -> ChannelWidth.WIDTH_20MHZ
                UNKNOWN -> ChannelWidth.UNKNOWN
            }

    /**
     * Maximum spatial streams typically supported
     */
    val maxSpatialStreams: Int
        get() =
            when (this) {
                WIFI_7 -> 16
                WIFI_6, WIFI_6E, WIFI_5 -> 8
                WIFI_4 -> 4
                LEGACY -> 1
                UNKNOWN -> 0
            }

    companion object {
        /**
         * Detect WiFi standard from Information Element presence
         *
         * Detection logic:
         * - EHT IE present → WiFi 7
         * - HE IE + 6GHz → WiFi 6E
         * - HE IE → WiFi 6
         * - VHT IE → WiFi 5
         * - HT IE → WiFi 4
         * - None → Legacy or Unknown
         *
         * @param hasHT Has HT (High Throughput) IE
         * @param hasVHT Has VHT (Very High Throughput) IE
         * @param hasHE Has HE (High Efficiency) IE
         * @param hasEHT Has EHT (Extremely High Throughput) IE
         * @param band Operating band
         * @return Detected WiFi standard
         */
        fun detect(
            hasHT: Boolean,
            hasVHT: Boolean,
            hasHE: Boolean,
            hasEHT: Boolean,
            band: WiFiBand,
        ): WifiStandard =
            when {
                hasEHT -> WIFI_7
                hasHE && band == WiFiBand.BAND_6GHZ -> WIFI_6E
                hasHE -> WIFI_6
                hasVHT -> WIFI_5
                hasHT -> WIFI_4
                else -> LEGACY
            }
    }
}
