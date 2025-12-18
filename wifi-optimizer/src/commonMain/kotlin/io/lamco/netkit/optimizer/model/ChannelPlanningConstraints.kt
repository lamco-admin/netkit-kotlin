package io.lamco.netkit.optimizer.model

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand

/**
 * Constraints and inputs for automated channel planning
 *
 * Defines the requirements and limitations for the channel planning algorithm
 * to generate optimal channel assignments for multi-AP deployments.
 *
 * Channel planning must consider:
 * - Regulatory constraints (allowed channels, DFS, power limits)
 * - Hardware capabilities (supported widths, bands)
 * - Network goals (throughput vs. interference balance)
 * - Environmental factors (existing networks, congestion)
 *
 * @property band Frequency band to plan channels for
 * @property supportsDfs Whether DFS (Dynamic Frequency Selection) channels can be used
 * @property minChannelSeparation Minimum channel separation for non-overlapping (default 5 for 2.4 GHz)
 * @property preferredWidths Ordered list of preferred channel widths (first = most preferred)
 * @property regulatoryDomain Regulatory domain (e.g., FCC, ETSI) determining available channels
 * @property allowedChannels Specific channels allowed (null = all regulatory channels)
 * @property excludedChannels Channels to exclude from consideration (e.g., known interference)
 * @property maxApCountPerChannel Maximum APs allowed on same channel (for load balancing)
 * @property prioritizeStability Prioritize stable channels over performance (avoid DFS, avoid congested)
 *
 * @throws IllegalArgumentException if constraints are invalid
 */
data class ChannelPlanningConstraints(
    val band: WiFiBand,
    val supportsDfs: Boolean = true,
    val minChannelSeparation: Int = 5,
    val preferredWidths: List<ChannelWidth>,
    val regulatoryDomain: RegulatoryDomain,
    val allowedChannels: Set<Int>? = null,
    val excludedChannels: Set<Int> = emptySet(),
    val maxApCountPerChannel: Int = 3,
    val prioritizeStability: Boolean = false
) {
    init {
        require(band != WiFiBand.UNKNOWN) {
            "Cannot plan channels for unknown band"
        }
        require(minChannelSeparation > 0) {
            "Minimum channel separation must be positive, got $minChannelSeparation"
        }
        require(preferredWidths.isNotEmpty()) {
            "Must specify at least one preferred channel width"
        }
        require(preferredWidths.none { it == ChannelWidth.UNKNOWN }) {
            "Preferred widths cannot include UNKNOWN"
        }
        require(maxApCountPerChannel > 0) {
            "Max AP count per channel must be positive, got $maxApCountPerChannel"
        }
        if (allowedChannels != null) {
            require(allowedChannels.isNotEmpty()) {
                "If specifying allowed channels, list must not be empty"
            }
            require(allowedChannels.none { it in excludedChannels }) {
                "Allowed and excluded channels cannot overlap"
            }
        }

        // Validate channel widths are appropriate for band
        when (band) {
            WiFiBand.BAND_2_4GHZ -> {
                val invalidWidths = preferredWidths.filter { !it.recommendedFor24GHz && it != ChannelWidth.WIDTH_40MHZ }
                require(invalidWidths.isEmpty()) {
                    "Invalid widths for 2.4 GHz: $invalidWidths (only 20/40 MHz supported)"
                }
            }
            WiFiBand.BAND_5GHZ -> {
                val invalidWidths = preferredWidths.filter { !it.availableOn5GHz }
                require(invalidWidths.isEmpty()) {
                    "Invalid widths for 5 GHz: $invalidWidths"
                }
            }
            WiFiBand.BAND_6GHZ -> {
                val invalidWidths = preferredWidths.filter { !it.availableOn6GHz }
                require(invalidWidths.isEmpty()) {
                    "Invalid widths for 6 GHz: $invalidWidths"
                }
            }
            WiFiBand.UNKNOWN -> {} // Already checked above
        }
    }

    /**
     * Whether 40 MHz channels are acceptable in 2.4 GHz
     * (Generally not recommended due to overlap)
     */
    val allows40MHzIn24GHz: Boolean
        get() = band == WiFiBand.BAND_2_4GHZ &&
                preferredWidths.contains(ChannelWidth.WIDTH_40MHZ)

    /**
     * Most preferred channel width
     */
    val preferredWidth: ChannelWidth
        get() = preferredWidths.first()

    /**
     * Whether wide channels (80+ MHz) are preferred
     */
    val prefersWideChannels: Boolean
        get() = preferredWidths.any { it.widthMHz >= 80 }

    /**
     * Available channels considering all constraints
     */
    fun getAvailableChannels(): Set<Int> {
        val regulatory = regulatoryDomain.getChannelsForBand(band, supportsDfs)

        return if (allowedChannels != null) {
            // Intersection of allowed and regulatory, excluding excluded
            (allowedChannels.intersect(regulatory) - excludedChannels)
        } else {
            // All regulatory channels minus excluded
            (regulatory - excludedChannels)
        }
    }

    /**
     * Whether a specific channel is usable under these constraints
     */
    fun isChannelUsable(channel: Int): Boolean {
        return channel in getAvailableChannels()
    }

    /**
     * Get constraint summary for logging/debugging
     */
    val summary: String
        get() = buildString {
            append("${band.displayName}, ")
            append("DFS: $supportsDfs, ")
            append("Widths: ${preferredWidths.joinToString("+") { "${it.widthMHz}MHz" }}, ")
            append("Domain: ${regulatoryDomain.name}, ")
            append("Channels: ${getAvailableChannels().size} available")
        }
}

/**
 * Regulatory domains defining allowed WiFi channels and power limits
 *
 * Different countries/regions have different regulations for WiFi spectrum:
 * - FCC (USA): Permissive DFS, high power limits
 * - ETSI (Europe): Strict DFS, moderate power
 * - MKK (Japan): Unique channel allocation
 * - CN (China): Restricted channels
 *
 * References:
 * - FCC Part 15.407
 * - ETSI EN 300 328, EN 301 893
 * - MIC (Japan) regulation
 */
enum class RegulatoryDomain(val displayName: String) {
    /**
     * FCC (Federal Communications Commission) - USA
     * - 2.4 GHz: Channels 1-11
     * - 5 GHz: U-NII-1, U-NII-2A/2C (DFS), U-NII-3
     * - 6 GHz: 1200 MHz spectrum
     * - Power: Up to 30 dBm (1W) EIRP
     */
    FCC("FCC (USA)"),

    /**
     * ETSI (European Telecommunications Standards Institute) - Europe
     * - 2.4 GHz: Channels 1-13
     * - 5 GHz: More DFS channels required
     * - 6 GHz: Limited availability
     * - Power: Up to 23 dBm (200 mW) EIRP
     */
    ETSI("ETSI (Europe)"),

    /**
     * MKK (Ministry of Internal Affairs and Communications) - Japan
     * - 2.4 GHz: Channels 1-14 (14 is 2.4 GHz only)
     * - 5 GHz: Unique channel allocation
     * - Power: Up to 20 dBm (100 mW) EIRP
     */
    MKK("MKK (Japan)"),

    /**
     * CN (China) - People's Republic of China
     * - 2.4 GHz: Channels 1-13
     * - 5 GHz: Limited channels
     * - Power: Restricted
     */
    CN("CN (China)"),

    /**
     * Rest of World - Default/Generic
     * - Conservative channel set
     * - Lower power limits
     */
    ROW("Rest of World");

    /**
     * Get available channels for a specific band
     *
     * @param band Frequency band
     * @param includeDfs Whether to include DFS channels
     * @return Set of channel numbers available in this domain
     */
    fun getChannelsForBand(band: WiFiBand, includeDfs: Boolean = true): Set<Int> {
        return when (band) {
            WiFiBand.BAND_2_4GHZ -> get24GHzChannels()
            WiFiBand.BAND_5GHZ -> get5GHzChannels(includeDfs)
            WiFiBand.BAND_6GHZ -> get6GHzChannels()
            WiFiBand.UNKNOWN -> emptySet()
        }
    }

    /**
     * 2.4 GHz channels by regulatory domain
     */
    private fun get24GHzChannels(): Set<Int> {
        return when (this) {
            FCC -> setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
            ETSI, CN -> setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
            MKK -> setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
            ROW -> setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        }
    }

    /**
     * 5 GHz channels by regulatory domain
     *
     * @param includeDfs Whether to include DFS channels
     * @return Set of 5 GHz channel numbers
     */
    private fun get5GHzChannels(includeDfs: Boolean): Set<Int> {
        // Non-DFS channels (common across domains)
        val nonDfs = when (this) {
            FCC -> setOf(36, 40, 44, 48, 149, 153, 157, 161, 165)
            ETSI -> setOf(36, 40, 44, 48)
            MKK -> setOf(36, 40, 44, 48)
            CN -> setOf(36, 40, 44, 48, 149, 153, 157, 161, 165)
            ROW -> setOf(36, 40, 44, 48)
        }

        if (!includeDfs) return nonDfs

        // DFS channels (U-NII-2A, U-NII-2C)
        val dfs = when (this) {
            FCC -> setOf(52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144)
            ETSI -> setOf(52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140)
            MKK -> setOf(52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140)
            CN -> setOf(52, 56, 60, 64)
            ROW -> setOf(52, 56, 60, 64)
        }

        return nonDfs + dfs
    }

    /**
     * 6 GHz channels (WiFi 6E)
     *
     * 6 GHz availability varies significantly by region.
     * This returns a conservative set of channels.
     */
    private fun get6GHzChannels(): Set<Int> {
        return when (this) {
            FCC -> {
                // FCC allows 5.925-7.125 GHz (1200 MHz)
                // Channels 1-233 in 20 MHz spacing
                (1..233 step 4).toSet()  // PSC (Preferred Scanning Channels)
            }
            ETSI -> {
                // ETSI: Limited 6 GHz (5.945-6.425 GHz)
                (1..93 step 4).toSet()
            }
            MKK, CN, ROW -> {
                // Limited or no 6 GHz availability
                emptySet()
            }
        }
    }

    /**
     * Maximum allowed transmit power (EIRP) for this domain
     *
     * @param band Frequency band
     * @return Maximum power in dBm
     */
    fun getMaxTxPower(band: WiFiBand): Int {
        return when (this) {
            FCC -> when (band) {
                WiFiBand.BAND_2_4GHZ -> 30  // 30 dBm = 1W
                WiFiBand.BAND_5GHZ -> 30
                WiFiBand.BAND_6GHZ -> 30
                WiFiBand.UNKNOWN -> 0
            }
            ETSI -> when (band) {
                WiFiBand.BAND_2_4GHZ -> 20  // 20 dBm = 100mW
                WiFiBand.BAND_5GHZ -> 23    // 23 dBm = 200mW
                WiFiBand.BAND_6GHZ -> 23
                WiFiBand.UNKNOWN -> 0
            }
            MKK -> 20  // Conservative across all bands
            CN -> 20
            ROW -> 20
        }
    }

    /**
     * Whether DFS is required for 5 GHz channels in this domain
     */
    val requiresDfs: Boolean
        get() = when (this) {
            FCC, ETSI, MKK -> true
            CN, ROW -> false
        }
}
