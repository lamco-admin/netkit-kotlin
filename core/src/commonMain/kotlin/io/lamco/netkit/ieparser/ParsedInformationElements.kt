package io.lamco.netkit.ieparser

import io.lamco.netkit.ieparser.model.*

/**
 * Container for all parsed Information Elements from a WiFi beacon frame
 *
 * This class aggregates all IE data for comprehensive network analysis.
 *
 * Information Elements provide detailed capability and security information
 * that goes far beyond what basic WiFi scanning APIs expose.
 *
 * Usage:
 * ```kotlin
 * val parser = IEParser()
 * val parsed = parser.parseAll(ieBytes)
 *
 * // Check security
 * val hasWPA3 = parsed.rsnInfo?.isWPA3 == true
 * val hasSaePk = parsed.rsnInfo?.hasSaePk == true
 *
 * // Check WiFi generation
 * val isWiFi6 = parsed.heCapabilities != null
 * val isWiFi7 = parsed.ehtCapabilities != null
 * ```
 *
 * @property rsnInfo RSN Information Element (security)
 * @property rsnExtension RSN Extension (H2E, SAE-PK identifiers)
 * @property htCapabilities HT Capabilities (WiFi 4 / 802.11n)
 * @property vhtCapabilities VHT Capabilities (WiFi 5 / 802.11ac)
 * @property heCapabilities HE Capabilities (WiFi 6 / 802.11ax)
 * @property heOperation HE Operation (WiFi 6 BSS parameters)
 * @property ehtCapabilities EHT Capabilities (WiFi 7 / 802.11be)
 * @property wpsEnabled Whether WPS is enabled (security risk)
 */
data class ParsedInformationElements(
    var rsnInfo: RSNInfo? = null,
    var rsnExtension: RSNExtensionInfo? = null,
    var htCapabilities: HTCapabilities? = null,
    var vhtCapabilities: VHTCapabilities? = null,
    var heCapabilities: HECapabilities? = null,
    var heOperation: HEOperation? = null,
    var ehtCapabilities: EHTCapabilities? = null,
    var wpsEnabled: Boolean = false,
) {
    /**
     * Detected WiFi generation based on capabilities
     */
    val wifiGeneration: WiFiGeneration
        get() =
            when {
                ehtCapabilities != null -> WiFiGeneration.WIFI_7
                heCapabilities != null -> WiFiGeneration.WIFI_6 // Could be 6E based on band
                vhtCapabilities != null -> WiFiGeneration.WIFI_5
                htCapabilities != null -> WiFiGeneration.WIFI_4
                else -> WiFiGeneration.LEGACY
            }

    /**
     * Whether network supports WPA3
     */
    val isWPA3: Boolean
        get() = rsnInfo?.isWPA3 == true

    /**
     * Whether network supports WPA3-SAE-PK (industry-first detection!)
     */
    val hasSaePk: Boolean
        get() = rsnInfo?.hasSaePk == true

    /**
     * Whether PMF (Protected Management Frames) is required
     */
    val pmfRequired: Boolean
        get() = rsnInfo?.pmfRequired == true

    /**
     * Whether Beacon Protection is enabled
     */
    val beaconProtection: Boolean
        get() =
            rsnInfo?.beaconProtectionRequired == true ||
                rsnInfo?.beaconProtectionCapable == true

    /**
     * Whether network uses deprecated ciphers (TKIP, WEP)
     */
    val hasDeprecatedCiphers: Boolean
        get() = rsnInfo?.hasDeprecatedCiphers == true

    /**
     * Maximum spatial streams across all WiFi generations
     */
    val maxSpatialStreams: Int
        get() =
            maxOf(
                htCapabilities?.maxSpatialStreams ?: 0,
                vhtCapabilities?.maxSpatialStreams ?: 0,
                heCapabilities?.maxSpatialStreams ?: 0,
                ehtCapabilities?.maxSpatialStreams ?: 0,
            )

    /**
     * Maximum channel width in MHz
     */
    val maxChannelWidthMHz: Int
        get() =
            when {
                ehtCapabilities != null -> ehtCapabilities!!.maxChannelWidthMHz
                heCapabilities != null -> heCapabilities!!.maxChannelWidthMHz
                vhtCapabilities != null -> vhtCapabilities!!.maxChannelWidthMHz
                htCapabilities != null -> if (htCapabilities!!.supports40MHz) 40 else 20
                else -> 20
            }

    /**
     * Maximum theoretical PHY rate in Mbps
     */
    val maxPhyRateMbps: Int
        get() =
            when {
                ehtCapabilities != null -> ehtCapabilities!!.maxPhyRateMbps
                heCapabilities != null -> heCapabilities!!.maxPhyRateMbps
                vhtCapabilities != null -> vhtCapabilities!!.maxPhyRateMbps
                htCapabilities != null -> htCapabilities!!.maxPhyRateMbps
                else -> 54 // Legacy 802.11a/g max
            }

    /**
     * Whether network supports MU-MIMO
     */
    val supportsMUMIMO: Boolean
        get() =
            vhtCapabilities?.muMimoCapable == true ||
                heCapabilities?.muMimoDownlink == true ||
                heCapabilities?.muMimoUplink == true

    /**
     * Whether network supports OFDMA (WiFi 6+)
     */
    val supportsOFDMA: Boolean
        get() = heCapabilities?.ofdmaSupport == true

    /**
     * Whether network supports Multi-Link Operation (WiFi 7)
     */
    val supportsMLO: Boolean
        get() = ehtCapabilities?.multiLinkOperation == true

    /**
     * Whether network has WiFi 6 efficiency features (OFDMA, TWT)
     */
    val hasWiFi6EfficiencyFeatures: Boolean
        get() = heCapabilities?.hasEfficiencyFeatures == true

    /**
     * BSS Color value (0 = disabled, 1-63 = enabled)
     */
    val bssColor: Int
        get() = heOperation?.bssColor ?: 0

    /**
     * Overall security score (0-100)
     * Combines RSN security, PMF, Beacon Protection
     */
    val securityScore: Int
        get() {
            var score = rsnInfo?.securityScore ?: 0

            // Bonus for WiFi 6E (mandatory WPA3)
            if (wifiGeneration == WiFiGeneration.WIFI_7) {
                score += 5
            }

            // Penalty for WPS
            if (wpsEnabled) {
                score -= 10
            }

            return score.coerceIn(0, 100)
        }

    /**
     * Overall performance score (0-100)
     * Based on WiFi generation, channel width, streams
     */
    val performanceScore: Int
        get() {
            var score = 0

            // WiFi generation: 40 points
            score +=
                when (wifiGeneration) {
                    WiFiGeneration.WIFI_7 -> 40
                    WiFiGeneration.WIFI_6 -> 35
                    WiFiGeneration.WIFI_5 -> 25
                    WiFiGeneration.WIFI_4 -> 15
                    WiFiGeneration.LEGACY -> 5
                }

            // Channel width: 30 points
            score +=
                when (maxChannelWidthMHz) {
                    320 -> 30
                    160 -> 25
                    80 -> 20
                    40 -> 10
                    else -> 5
                }

            // Spatial streams: 20 points
            score +=
                when (maxSpatialStreams) {
                    in 8..16 -> 20
                    in 4..7 -> 15
                    2, 3 -> 10
                    else -> 5
                }

            // Advanced features: 10 points
            if (supportsMLO) score += 5
            if (supportsOFDMA) score += 3
            if (supportsMUMIMO) score += 2

            return score.coerceIn(0, 100)
        }

    /**
     * List of critical security issues detected
     */
    val criticalSecurityIssues: List<String>
        get() =
            buildList {
                if (hasDeprecatedCiphers) {
                    val ciphers = rsnInfo?.deprecatedCiphers?.joinToString { it.displayName }
                    add("Deprecated ciphers in use: $ciphers")
                }

                if (wpsEnabled) {
                    add("WPS enabled - vulnerable to brute force attacks")
                }

                if (!pmfRequired && isWPA3) {
                    add("PMF not required on WPA3 network")
                }

                if (rsnInfo == null) {
                    add("No RSN information - open or WEP network")
                }
            }

    /**
     * Whether this network has any critical security issues
     */
    val hasCriticalSecurityIssues: Boolean
        get() = criticalSecurityIssues.isNotEmpty()
}

/**
 * WiFi Generation enumeration
 */
enum class WiFiGeneration(
    val displayName: String,
    val standardName: String,
    val releaseYear: Int,
) {
    WIFI_7("WiFi 7", "802.11be", 2024),
    WIFI_6("WiFi 6/6E", "802.11ax", 2019),
    WIFI_5("WiFi 5", "802.11ac", 2014),
    WIFI_4("WiFi 4", "802.11n", 2009),
    LEGACY("Legacy WiFi", "802.11a/b/g", 1999),
    ;

    val isModern: Boolean
        get() = this in listOf(WIFI_6, WIFI_7)
}
