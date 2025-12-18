package io.lamco.netkit.model.analysis

import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.security.*

/**
 * Complete security analysis result for a WiFi network
 *
 * This is the primary data model returned from network security analysis,
 * containing all detected security features, ratings, and recommendations.
 *
 * @property ssid Network name
 * @property bssid MAC address of the access point (null if not available)
 * @property securityTypes All detected security protocols (can be multiple in transition mode)
 * @property securityFeatures Advanced security features detected
 * @property band WiFi frequency band (2.4/5/6 GHz)
 * @property frequency Exact frequency in MHz
 * @property channel Channel number
 * @property signalStrength Signal strength bars (0-4 scale)
 * @property rssi Signal strength in dBm (e.g., -45 dBm)
 * @property linkSpeed Link speed in Mbps (if connected)
 * @property isTransitionMode Whether network runs both WPA2 and WPA3 (dual mode)
 * @property pmfStatus Protected Management Frames status
 * @property rating Overall security rating (0-100 score + letter grade)
 * @property recommendations List of security recommendations
 * @property lastAnalyzed Timestamp of analysis (milliseconds since epoch)
 * @property wifiGeneration WiFi generation info (WiFi 4/5/6/6E/7) with capabilities
 * @property routerInfo Router manufacturer and model info
 * @property advancedCapabilities Advanced features (SAE-PK, H2E, WiFi 6/7 features)
 * @property wpsVulnerability WPS vulnerability assessment (if WPS detected)
 */
data class NetworkSecurityAnalysis(
    // Basic Network Info
    val ssid: String,
    val bssid: String? = null,
    // Security Analysis
    val securityTypes: List<SecurityType>,
    val securityFeatures: List<SecurityFeature>,
    val band: WiFiBand,
    val frequency: Int,
    val channel: Int,
    // Signal Info
    val signalStrength: Int, // 0-4 bars
    val rssi: Int = -70, // dBm (-30 to -100)
    val linkSpeed: Int = 0, // Mbps
    // Security Status
    val isTransitionMode: Boolean,
    val pmfStatus: PMFStatus,
    val rating: SecurityRating,
    val recommendations: List<SecurityRecommendation>,
    // Metadata
    val lastAnalyzed: Long = System.currentTimeMillis(),
    // Advanced Analysis (nullable for backward compatibility)
    val wifiGeneration: WiFiGenerationInfo? = null,
    val routerInfo: RouterInfo? = null,
    val advancedCapabilities: AdvancedCapabilities? = null,
    val wpsVulnerability: WPSVulnerability? = null,
) {
    /**
     * Whether network meets basic security standards
     * Threshold: Score >= 60 (C grade or better)
     */
    val isSecure: Boolean
        get() = rating.score >= 60

    /**
     * Whether network has excellent security
     * Threshold: Score >= 85 (A grade)
     */
    val isExcellent: Boolean
        get() = rating.score >= 85

    /**
     * Whether network uses modern security protocols
     * Modern: WPA3 or Enhanced Open
     */
    val hasModernSecurity: Boolean
        get() = securityTypes.any { it.isModern }

    /**
     * Whether network requires security upgrade
     * Threshold: Score < 60 (below C grade)
     */
    val requiresUpgrade: Boolean
        get() = rating.score < 60

    /**
     * Whether network has critical security issues
     * Threshold: Score < 30 (F grade)
     */
    val requiresUrgentAction: Boolean
        get() = rating.score < 30

    /**
     * List of critical security issues with this network
     * Issues that require immediate attention
     */
    val criticalIssues: List<String>
        get() =
            buildList {
                if (SecurityType.WEP in securityTypes) {
                    add("WEP encryption is broken and can be cracked in minutes")
                }
                if (SecurityType.OPEN in securityTypes && SecurityType.ENHANCED_OPEN !in securityTypes) {
                    add("No encryption - all traffic visible to attackers")
                }
                if (band == WiFiBand.BAND_2_4GHZ && !isSecure) {
                    add("2.4GHz band with weak security increases attack range")
                }
                if (pmfStatus == PMFStatus.DISABLED && isTransitionMode) {
                    add("Management frames unprotected - vulnerable to deauth attacks")
                }
                if (wpsVulnerability?.severity == WPSSeverity.CRITICAL) {
                    add("WPS PIN mode enabled - network can be cracked in hours")
                }
            }

    /**
     * Whether this network supports WiFi 6 or newer
     */
    val isWiFi6OrNewer: Boolean
        get() =
            wifiGeneration?.let {
                it.generation in
                    listOf(
                        WifiGeneration.WIFI_6,
                        WifiGeneration.WIFI_6E,
                        WifiGeneration.WIFI_7,
                    )
            } ?: false

    /**
     * Whether this network runs on 6 GHz (WiFi 6E)
     */
    val isWiFi6E: Boolean
        get() = band == WiFiBand.BAND_6GHZ

    /**
     * Signal quality category
     */
    val signalQuality: SignalQuality
        get() =
            when (rssi) {
                in -30..-50 -> SignalQuality.EXCELLENT
                in -51..-60 -> SignalQuality.GOOD
                in -61..-70 -> SignalQuality.FAIR
                in -71..-80 -> SignalQuality.POOR
                else -> SignalQuality.VERY_POOR
            }

    /**
     * Number of high or critical priority recommendations
     */
    val urgentRecommendationCount: Int
        get() =
            recommendations.count {
                it.priority == SecurityRecommendation.Priority.CRITICAL ||
                    it.priority == SecurityRecommendation.Priority.HIGH
            }
}

/**
 * WiFi generation information with capabilities
 *
 * @property generation WiFi generation (WiFi 4/5/6/6E/7)
 * @property releaseYear Year of standard release
 * @property maxSpeed Maximum theoretical speed
 * @property features Key features of this generation
 * @property standardName IEEE standard designation
 */
data class WiFiGenerationInfo(
    val generation: WifiGeneration,
    val releaseYear: Int,
    val maxSpeed: String, // e.g., "9.6 Gbps"
    val features: List<String>,
    val standardName: String,
)

/**
 * WiFi generation enum
 */
enum class WifiGeneration(
    val displayName: String,
) {
    WIFI_4("WiFi 4 (802.11n)"),
    WIFI_5("WiFi 5 (802.11ac)"),
    WIFI_6("WiFi 6 (802.11ax)"),
    WIFI_6E("WiFi 6E (6 GHz)"),
    WIFI_7("WiFi 7 (802.11be)"),
    LEGACY("Legacy WiFi"),
    UNKNOWN("Unknown"),
}

/**
 * Router/AP information
 *
 * @property manufacturer Router manufacturer (from OUI lookup)
 * @property oui Organizationally Unique Identifier (first 3 bytes of MAC)
 * @property modelHint Possible model hint (if detectable)
 * @property deviceClass Router class (consumer/enterprise/ISP)
 */
data class RouterInfo(
    val manufacturer: String,
    val oui: String, // e.g., "00:11:22"
    val modelHint: String? = null,
    val deviceClass: RouterClass = RouterClass.UNKNOWN,
)

/**
 * Router device class
 */
enum class RouterClass {
    CONSUMER, // Home routers (TP-Link, Netgear, Asus, etc.)
    ENTERPRISE, // Business APs (Cisco, Aruba, Ubiquiti, etc.)
    ISP, // ISP-provided routers (limited user control)
    MESH, // Mesh system nodes
    UNKNOWN,
}

/**
 * Signal quality categories
 */
enum class SignalQuality(
    val displayName: String,
    val colorHex: String,
) {
    EXCELLENT("Excellent", "4CAF50"), // Green
    GOOD("Good", "8BC34A"), // Light Green
    FAIR("Fair", "FFEB3B"), // Yellow
    POOR("Poor", "FF9800"), // Orange
    VERY_POOR("Very Poor", "F44336"), // Red
}
