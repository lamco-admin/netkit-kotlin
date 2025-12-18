package io.lamco.netkit.model.topology

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard

/**
 * Individual BSSID within an AP cluster
 *
 * Represents a single access point (or radio) that is part of a larger
 * multi-AP deployment. Multiple ClusteredBss instances with the same SSID
 * and security configuration are grouped into an ApCluster.
 *
 * @property bssid MAC address of this access point (Basic Service Set Identifier)
 * @property band Frequency band (2.4/5/6/60 GHz)
 * @property channel Primary channel number
 * @property frequencyMHz Center frequency in MHz
 * @property channelWidth Channel width (20/40/80/160/320 MHz)
 * @property wifiStandard WiFi standard (WiFi 4/5/6/6E/7)
 * @property mloInfo WiFi 7 Multi-Link Operation information (if MLO-capable)
 * @property roamingCapabilities Fast roaming support (802.11k/r/v)
 * @property lastSeenTimestamp When this BSS was last detected (milliseconds since epoch)
 * @property rssiDbm Last observed RSSI in dBm
 */
data class ClusteredBss(
    val bssid: String,
    val band: WiFiBand,
    val channel: Int,
    val frequencyMHz: Int,
    val channelWidth: ChannelWidth,
    val wifiStandard: WifiStandard,
    val mloInfo: ApMloInfo? = null,
    val roamingCapabilities: RoamingCapabilities = RoamingCapabilities.none(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val rssiDbm: Int? = null,
) {
    init {
        require(bssid.isNotBlank()) {
            "BSSID must not be blank"
        }
        require(channel > 0) {
            "Channel must be positive, got $channel"
        }
        require(frequencyMHz > 0) {
            "Frequency must be positive, got $frequencyMHz"
        }
        if (rssiDbm != null) {
            require(rssiDbm in -120..0) {
                "RSSI must be in range [-120, 0] dBm, got $rssiDbm"
            }
        }
    }

    /**
     * OUI (Organizationally Unique Identifier) from BSSID
     * First 3 bytes identify the manufacturer
     */
    val oui: String
        get() = bssid.take(8) // First 3 bytes (XX:XX:XX format)

    /**
     * Whether this BSS supports WiFi 6E (6 GHz operation)
     */
    val isWiFi6E: Boolean
        get() = wifiStandard == WifiStandard.WIFI_6E || band == WiFiBand.BAND_6GHZ

    /**
     * Whether this BSS supports WiFi 7
     */
    val isWiFi7: Boolean
        get() = wifiStandard == WifiStandard.WIFI_7 || mloInfo != null

    /**
     * Whether this BSS supports WiFi 6 or newer
     */
    val supportsWiFi6: Boolean
        get() =
            wifiStandard == WifiStandard.WIFI_6 ||
                wifiStandard == WifiStandard.WIFI_6E ||
                wifiStandard == WifiStandard.WIFI_7

    /**
     * Whether this is an enterprise deployment (based on roaming capabilities)
     * Enterprise APs typically support full fast roaming suite
     */
    val isEnterprise: Boolean
        get() = roamingCapabilities.hasFullSuite

    /**
     * Whether this is a mesh deployment (based on roaming capabilities and multi-link)
     * Mesh systems typically have coordinated roaming
     */
    val isMesh: Boolean
        get() = roamingCapabilities.hasFastRoaming || mloInfo != null

    /**
     * Whether this BSS supports wide channels (>= 80 MHz)
     */
    val supportsWideChannels: Boolean
        get() =
            channelWidth == ChannelWidth.WIDTH_80MHZ ||
                channelWidth == ChannelWidth.WIDTH_160MHZ ||
                channelWidth == ChannelWidth.WIDTH_320MHZ

    /**
     * Whether this BSS has been seen recently (within last 60 seconds)
     */
    fun isActive(currentTimeMillis: Long = System.currentTimeMillis()): Boolean = (currentTimeMillis - lastSeenTimestamp) < 60_000

    /**
     * Time since last seen in milliseconds
     */
    fun timeSinceLastSeenMillis(currentTimeMillis: Long = System.currentTimeMillis()): Long = currentTimeMillis - lastSeenTimestamp

    /**
     * Signal strength category based on RSSI
     */
    val signalStrength: SignalStrength
        get() =
            when {
                rssiDbm == null -> SignalStrength.UNKNOWN
                rssiDbm >= -50 -> SignalStrength.EXCELLENT
                rssiDbm >= -60 -> SignalStrength.VERY_GOOD
                rssiDbm >= -70 -> SignalStrength.GOOD
                rssiDbm >= -80 -> SignalStrength.FAIR
                else -> SignalStrength.POOR
            }

    /**
     * Whether this BSS is a good roaming candidate
     * Based on signal strength and roaming capabilities
     */
    val isGoodRoamingCandidate: Boolean
        get() = (rssiDbm ?: -999) >= -75 && roamingCapabilities.hasFastRoaming

    /**
     * Human-readable BSS summary
     */
    val summary: String
        get() =
            buildString {
                append("$bssid ")
                append("(${band.displayName} Ch$channel, ${wifiStandard.displayName})")
                if (rssiDbm != null) {
                    append(" RSSI: ${rssiDbm}dBm")
                }
                if (mloInfo != null) {
                    append(" [MLO]")
                }
            }

    /**
     * Create a copy with updated RSSI and timestamp
     */
    fun withUpdatedSignal(
        newRssiDbm: Int,
        timestamp: Long = System.currentTimeMillis(),
    ): ClusteredBss = copy(rssiDbm = newRssiDbm, lastSeenTimestamp = timestamp)
}

/**
 * Signal strength categories for quick assessment
 */
enum class SignalStrength(
    val displayName: String,
    val minRssiDbm: Int,
    val maxRssiDbm: Int,
) {
    /** Excellent signal (>= -50 dBm) */
    EXCELLENT("Excellent", -50, 0),

    /** Very good signal (-60 to -50 dBm) */
    VERY_GOOD("Very Good", -60, -49),

    /** Good signal (-70 to -60 dBm) */
    GOOD("Good", -70, -59),

    /** Fair signal (-80 to -70 dBm) */
    FAIR("Fair", -80, -69),

    /** Poor signal (< -80 dBm) */
    POOR("Poor", -120, -79),

    /** Unknown signal strength */
    UNKNOWN("Unknown", -999, -999),
    ;

    /**
     * Whether this signal strength is adequate for normal use
     */
    val isAdequate: Boolean
        get() = this in listOf(EXCELLENT, VERY_GOOD, GOOD)

    /**
     * Whether this signal strength is marginal (connection may be unstable)
     */
    val isMarginal: Boolean
        get() = this == FAIR

    /**
     * Whether this signal strength is poor (connection likely unstable)
     */
    val isPoor: Boolean
        get() = this == POOR

    companion object {
        /**
         * Determine signal strength category from RSSI
         */
        fun fromRssi(rssiDbm: Int): SignalStrength =
            when {
                rssiDbm >= -50 -> EXCELLENT
                rssiDbm >= -60 -> VERY_GOOD
                rssiDbm >= -70 -> GOOD
                rssiDbm >= -80 -> FAIR
                rssiDbm >= -120 -> POOR
                else -> UNKNOWN
            }
    }
}
