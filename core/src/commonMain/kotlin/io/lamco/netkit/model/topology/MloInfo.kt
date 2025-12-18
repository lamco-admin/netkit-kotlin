package io.lamco.netkit.model.topology

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand

/**
 * WiFi 7 Multi-Link Operation (MLO) information
 *
 * MLO allows a single logical connection to use multiple physical links
 * simultaneously across different bands/channels. This provides:
 * - Increased throughput (aggregated bandwidth)
 * - Lower latency (traffic on fastest available link)
 * - Improved reliability (fallback if one link fails)
 *
 * Defined in IEEE 802.11be (WiFi 7) specification.
 *
 * @property mldMacAddress Multi-Link Device (MLD) MAC address
 * @property links List of physical links comprising this MLO AP
 * @property isPrimaryLink Whether this is the primary link (usually lowest band)
 */
data class ApMloInfo(
    val mldMacAddress: String,
    val links: List<MloLinkInfo>,
    val isPrimaryLink: Boolean,
) {
    init {
        require(mldMacAddress.isNotBlank()) {
            "MLD MAC address must not be blank"
        }
        require(links.isNotEmpty()) {
            "MLO must have at least one link"
        }
        require(links.size <= 16) {
            "MLO supports maximum 16 links, got ${links.size}"
        }
    }

    /**
     * Number of active MLO links
     */
    val linkCount: Int
        get() = links.size

    /**
     * Total aggregated bandwidth across all links (Mbps)
     */
    val aggregatedBandwidthMbps: Int
        get() = links.sumOf { it.maxPhyRateMbps }

    /**
     * Whether this is a multi-band MLO deployment (recommended)
     */
    val isMultiBand: Boolean
        get() = links.map { it.band }.distinct().size > 1

    /**
     * Bands used by this MLO AP
     */
    val usedBands: Set<WiFiBand>
        get() = links.map { it.band }.toSet()

    /**
     * Whether this MLO configuration includes 6 GHz
     */
    val has6GHz: Boolean
        get() = usedBands.contains(WiFiBand.BAND_6GHZ)

    /**
     * MLO configuration quality score (0-100)
     */
    val configurationScore: Int
        get() {
            var score = 50 // Base score

            // Multi-band is better than single-band
            if (isMultiBand) score += 20

            // 6 GHz presence is valuable
            if (has6GHz) score += 15

            // More links = higher throughput potential
            score += (linkCount - 1) * 5

            // Wide channels are good
            val hasWideChannels =
                links.any {
                    it.channelWidth == ChannelWidth.WIDTH_160MHZ ||
                        it.channelWidth == ChannelWidth.WIDTH_320MHZ
                }
            if (hasWideChannels) score += 10

            return score.coerceAtMost(100)
        }

    /**
     * Find link by band
     */
    fun findLinkByBand(band: WiFiBand): MloLinkInfo? = links.firstOrNull { it.band == band }

    /**
     * Human-readable MLO summary
     */
    val mloSummary: String
        get() =
            buildString {
                append("WiFi 7 MLO: $linkCount links")
                if (isMultiBand) {
                    append(" (${usedBands.joinToString("+") { it.displayName }})")
                }
                append(", $aggregatedBandwidthMbps Mbps aggregated")
            }
}

/**
 * Information about a single link in an MLO configuration
 *
 * @property linkId Link identifier (0-15)
 * @property bssid BSSID for this specific link
 * @property band Frequency band for this link
 * @property channel Primary channel number
 * @property channelWidth Channel width for this link
 * @property maxPhyRateMbps Maximum PHY rate in Mbps
 */
data class MloLinkInfo(
    val linkId: Int,
    val bssid: String,
    val band: WiFiBand,
    val channel: Int,
    val channelWidth: ChannelWidth,
    val maxPhyRateMbps: Int,
) {
    init {
        require(linkId in 0..15) {
            "MLO link ID must be 0-15, got $linkId"
        }
        require(bssid.isNotBlank()) {
            "BSSID must not be blank"
        }
        require(channel > 0) {
            "Channel must be positive, got $channel"
        }
        require(maxPhyRateMbps > 0) {
            "Max PHY rate must be positive, got $maxPhyRateMbps"
        }
    }

    /**
     * Whether this is a high-capacity link (>= 1 Gbps PHY rate)
     */
    val isHighCapacity: Boolean
        get() = maxPhyRateMbps >= 1000

    /**
     * Whether this link uses a wide channel (>= 80 MHz)
     */
    val usesWideChannel: Boolean
        get() =
            channelWidth == ChannelWidth.WIDTH_80MHZ ||
                channelWidth == ChannelWidth.WIDTH_160MHZ ||
                channelWidth == ChannelWidth.WIDTH_320MHZ

    /**
     * Link capacity category
     */
    val capacityCategory: LinkCapacity
        get() =
            when {
                maxPhyRateMbps >= 5000 -> LinkCapacity.ULTRA_HIGH // 5+ Gbps
                maxPhyRateMbps >= 2000 -> LinkCapacity.VERY_HIGH // 2-5 Gbps
                maxPhyRateMbps >= 1000 -> LinkCapacity.HIGH // 1-2 Gbps
                maxPhyRateMbps >= 500 -> LinkCapacity.MEDIUM // 500-1000 Mbps
                else -> LinkCapacity.LOW // < 500 Mbps
            }

    /**
     * Human-readable link summary
     */
    val linkSummary: String
        get() = "${band.displayName} Ch$channel (${channelWidth.widthMHz}MHz) - $maxPhyRateMbps Mbps"
}

/**
 * MLO link capacity categories
 */
enum class LinkCapacity(
    val displayName: String,
) {
    /** Ultra-high capacity (>= 5 Gbps) */
    ULTRA_HIGH("Ultra-High (5+ Gbps)"),

    /** Very high capacity (2-5 Gbps) */
    VERY_HIGH("Very High (2-5 Gbps)"),

    /** High capacity (1-2 Gbps) */
    HIGH("High (1-2 Gbps)"),

    /** Medium capacity (500 Mbps - 1 Gbps) */
    MEDIUM("Medium (500 Mbps - 1 Gbps)"),

    /** Low capacity (< 500 Mbps) */
    LOW("Low (< 500 Mbps)"),
}

/**
 * WiFi 7 MLO group (multiple BSSIDs part of same MLO AP)
 *
 * This represents a single WiFi 7 access point operating in MLO mode,
 * with multiple links across different bands/channels.
 *
 * @property ssid Network SSID
 * @property mldMacAddress Multi-Link Device MAC address (identifies the logical AP)
 * @property primaryLinkId Which link is the primary (usually lowest band)
 * @property links All physical links in this MLO AP
 */
data class MloGroup(
    val ssid: String,
    val mldMacAddress: String,
    val primaryLinkId: Int,
    val links: List<MloLinkInfo>,
) {
    init {
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
        require(mldMacAddress.isNotBlank()) {
            "MLD MAC address must not be blank"
        }
        require(links.isNotEmpty()) {
            "MLO group must have at least one link"
        }
        require(primaryLinkId in 0..15) {
            "Primary link ID must be 0-15, got $primaryLinkId"
        }
        require(links.any { it.linkId == primaryLinkId }) {
            "Primary link ID $primaryLinkId not found in links"
        }
    }

    /**
     * Primary link (for initial connection establishment)
     */
    val primaryLink: MloLinkInfo
        get() = links.first { it.linkId == primaryLinkId }

    /**
     * All BSSIDs in this MLO group
     */
    val bssids: Set<String>
        get() = links.map { it.bssid }.toSet()

    /**
     * Total number of links
     */
    val linkCount: Int
        get() = links.size

    /**
     * Total aggregated bandwidth
     */
    val totalBandwidthMbps: Int
        get() = links.sumOf { it.maxPhyRateMbps }

    /**
     * Whether this is a tri-band MLO configuration
     */
    val isTriBand: Boolean
        get() = links.map { it.band }.distinct().size >= 3

    /**
     * Bands used by this MLO AP
     */
    val usedBands: Set<WiFiBand>
        get() = links.map { it.band }.toSet()

    /**
     * MLO configuration quality
     */
    val quality: MloQuality
        get() =
            when {
                isTriBand && linkCount >= 3 -> MloQuality.OPTIMAL
                links.map { it.band }.distinct().size >= 2 && linkCount >= 2 -> MloQuality.GOOD
                linkCount >= 2 -> MloQuality.BASIC
                else -> MloQuality.MINIMAL
            }
}

/**
 * MLO configuration quality levels
 */
enum class MloQuality(
    val displayName: String,
) {
    /** Optimal: 3+ bands, 3+ links */
    OPTIMAL("Optimal Multi-Band MLO"),

    /** Good: 2+ bands, 2+ links */
    GOOD("Good Multi-Band MLO"),

    /** Basic: 2+ links, may be same band */
    BASIC("Basic MLO"),

    /** Minimal: Single link (not true MLO) */
    MINIMAL("Single Link"),
}
