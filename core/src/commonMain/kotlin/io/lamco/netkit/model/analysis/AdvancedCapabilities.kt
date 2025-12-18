package io.lamco.netkit.model.analysis

/**
 * Advanced WiFi capabilities detected from Information Elements
 *
 * These capabilities go beyond basic security and represent advanced
 * features detected through deep inspection of beacon frames and
 * Information Elements.
 *
 * @property hasSaePk SAE with Public Key (WPA3 enhancement) - INDUSTRY FIRST DETECTION
 * @property saePkIdentifier Password identifier for SAE-PK (if detected)
 * @property hasH2E Hash-to-Element support (enhanced SAE)
 * @property hasBeaconProtection Beacon frames are encrypted (2024 WPA3 feature)
 * @property hasTransitionDisable Network will disable legacy modes
 * @property ciphers Detailed cipher information
 * @property wifi6Features WiFi 6 (802.11ax) specific features
 * @property wifi7Features WiFi 7 (802.11be) specific features
 * @property fastRoaming Fast roaming capabilities (802.11k/r/v)
 */
data class AdvancedCapabilities(
    // WPA3 Advanced Features
    val hasSaePk: Boolean = false, // 🏆 INDUSTRY FIRST DETECTION IN CONSUMER APP!
    val saePkIdentifier: String? = null, // Password identifier from SAE-PK
    val hasH2E: Boolean = false, // Hash-to-Element (mandatory for WiFi 6E)
    val hasBeaconProtection: Boolean = false, // Beacon Protection (WPA3 2024)
    val hasTransitionDisable: Boolean = false, // Transition Disable indication
    // Cipher Details
    val ciphers: CipherInfo? = null,
    // WiFi 6 Advanced Features
    val wifi6Features: WiFi6Features? = null,
    // WiFi 7 Advanced Features
    val wifi7Features: WiFi7Features? = null,
    // Fast Roaming Support
    val fastRoaming: FastRoamingSupport? = null,
)

/**
 * Detailed cipher information from RSN Information Element
 *
 * @property groupCipher Cipher for broadcast/multicast (e.g., "CCMP (AES)")
 * @property pairwiseCiphers Ciphers for unicast traffic
 * @property managementCipher Cipher for management frame protection (if PMF enabled)
 * @property hasWeakCiphers Whether any weak ciphers (TKIP, WEP) are present
 * @property weakCiphers List of detected weak ciphers
 */
data class CipherInfo(
    val groupCipher: String, // "CCMP (AES)", "GCMP-256", etc.
    val pairwiseCiphers: List<String>, // ["CCMP", "GCMP"]
    val managementCipher: String?, // "BIP-CMAC-128", "BIP-GMAC-256", etc.
    val hasWeakCiphers: Boolean, // True if TKIP or WEP detected
    val weakCiphers: List<String>, // ["TKIP"] if present
)

/**
 * WiFi 6 (802.11ax) specific features from HE Information Elements
 *
 * @property ofdma OFDMA (Orthogonal Frequency Division Multiple Access) support
 * @property muMimo Multi-User MIMO configuration (e.g., "4x4", "8x8")
 * @property spatialStreams Number of spatial streams supported
 * @property bssColoring BSS Color ID (0-63, 0 = disabled)
 * @property twt Target Wake Time support (power saving)
 * @property beamforming Beamforming support
 * @property supportedMcs Highest MCS (Modulation and Coding Scheme) supported
 * @property channelWidth Maximum channel width supported
 */
data class WiFi6Features(
    val ofdma: Boolean = false,
    val muMimo: String? = null, // "2x2", "4x4", "8x8", etc.
    val spatialStreams: Int = 0, // 1-8
    val bssColoring: Int = 0, // 0-63 (0 = disabled)
    val twt: Boolean = false,
    val beamforming: Boolean = false,
    val supportedMcs: Int? = null, // 0-11 (11 = 1024-QAM)
    val channelWidth: Int = 0, // MHz (20, 40, 80, 160)
)

/**
 * WiFi 7 (802.11be) specific features from EHT Information Elements
 *
 * @property mlo Multi-Link Operation support
 * @property mloLinks Number of MLO links (if MLO supported)
 * @property channel320MHz 320 MHz channel support
 * @property multiRU Multi-RU (Resource Unit) support
 * @property puncturing Channel puncturing support (selective frequency exclusion)
 * @property supportedMcs Highest MCS supported (0-13, 13 = 4096-QAM)
 */
data class WiFi7Features(
    val mlo: Boolean = false,
    val mloLinks: Int = 0, // Number of MLO links (typically 2-3)
    val channel320MHz: Boolean = false,
    val multiRU: Boolean = false,
    val puncturing: Boolean = false,
    val supportedMcs: Int? = null, // 0-13 (13 = 4096-QAM)
)

/**
 * Fast roaming capabilities (802.11k/r/v)
 *
 * These features enable seamless roaming between access points:
 * - 802.11k: Radio Resource Management (neighbor reports)
 * - 802.11r: Fast BSS Transition (pre-authentication)
 * - 802.11v: BSS Transition Management (network-assisted roaming)
 *
 * @property dot11k 802.11k Radio Resource Management support
 * @property dot11r 802.11r Fast BSS Transition support
 * @property dot11v 802.11v BSS Transition Management support
 * @property ftOverDs Fast Transition over Distribution System
 * @property ftOverAir Fast Transition over the air
 */
data class FastRoamingSupport(
    val dot11k: Boolean = false, // Neighbor reports
    val dot11r: Boolean = false, // Fast BSS transition
    val dot11v: Boolean = false, // BSS transition management
    val ftOverDs: Boolean = false, // FT over DS (wired backhaul)
    val ftOverAir: Boolean = false, // FT over air (wireless)
)

/**
 * WPS (WiFi Protected Setup) vulnerability assessment
 *
 * WPS is a convenience feature that significantly weakens security:
 * - PIN mode can be brute-forced in 4-10 hours
 * - Pixie Dust attack can crack in minutes
 * - Even when "disabled" in UI, may still be active
 *
 * @property wpsEnabled Whether WPS is enabled
 * @property configMethods WPS configuration methods available
 * @property vulnerability Type of WPS vulnerability detected
 * @property severity Severity of the vulnerability
 * @property explanation User-friendly explanation
 * @property estimatedCrackTime Estimated time to crack (if vulnerable)
 */
data class WPSVulnerability(
    val wpsEnabled: Boolean,
    val configMethods: List<String> = emptyList(), // "PIN", "PBC", "NFC", etc.
    val vulnerability: WPSVulnerabilityType?,
    val severity: WPSSeverity,
    val explanation: String,
    val estimatedCrackTime: String? = null, // "4-10 hours", "Minutes", etc.
)

/**
 * Type of WPS vulnerability
 */
enum class WPSVulnerabilityType {
    /**
     * PIN brute force attack
     * - 10,000 possible PINs, but checksum reduces to ~11,000 attempts
     * - Can be cracked in 4-10 hours
     */
    PIN_BRUTE_FORCE,

    /**
     * Pixie Dust attack
     * - Exploits weak random number generation
     * - Can be cracked in minutes
     * - Affects many older routers
     */
    PIXIE_DUST,

    /**
     * WPS always enabled
     * - Cannot be disabled through router UI
     * - Requires firmware update or router replacement
     */
    ALWAYS_ENABLED,

    /**
     * WPS enabled but locked
     * - AP has rate limiting or lockout after failed attempts
     * - Reduces but doesn't eliminate risk
     */
    RATE_LIMITED,
}

/**
 * WPS vulnerability severity
 */
enum class WPSSeverity {
    /** Critical - Active WPS PIN mode, easily exploitable */
    CRITICAL,

    /** High - WPS enabled but with some protections */
    HIGH,

    /** Medium - WPS capable but currently disabled */
    MEDIUM,

    /** Low - WPS not supported */
    LOW,

    /** Info - WPS status unknown */
    INFO,
}
