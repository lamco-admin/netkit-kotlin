package io.lamco.netkit.model.topology

/**
 * WiFi authentication types
 *
 * Represents the authentication and key management mechanisms used
 * by WiFi networks as specified in IEEE 802.11-2020 and WPA3 specifications.
 *
 * @property displayName Human-readable name
 * @property securityLevel Security rating (0-100)
 * @property requiresRadius Whether this auth type requires a RADIUS server
 */
enum class AuthType(
    val displayName: String,
    val securityLevel: Int,
    val requiresRadius: Boolean,
) {
    /**
     * Open network - no authentication
     * - No password required
     * - All traffic unencrypted (unless using OWE)
     * - Should only be used with Enhanced Open (OWE)
     */
    OPEN("Open", 0, false),

    /**
     * Enhanced Open using OWE (Opportunistic Wireless Encryption)
     * - RFC 8110
     * - Encrypted open network
     * - No password, but traffic is encrypted
     * - Protection against passive eavesdropping
     */
    OWE("Enhanced Open (OWE)", 60, false),

    /**
     * WPA-Personal (WPA1 with pre-shared key)
     * - Legacy, deprecated
     * - TKIP cipher (weak)
     * - Should be upgraded to WPA2/WPA3
     */
    WPA_PSK("WPA-Personal", 40, false),

    /**
     * WPA2-Personal (WPA2 with pre-shared key)
     * - Most common for home networks
     * - AES-CCMP encryption
     * - Vulnerable to offline dictionary attacks
     * - Still acceptable if strong password used
     */
    WPA2_PSK("WPA2-Personal", 70, false),

    /**
     * WPA3-Personal using SAE (Simultaneous Authentication of Equals)
     * - Modern replacement for WPA2-PSK
     * - Resistant to offline dictionary attacks
     * - Forward secrecy
     * - Mandatory for WiFi 6E (6 GHz band)
     */
    WPA3_SAE("WPA3-Personal", 95, false),

    /**
     * WPA3-Personal with SAE-PK (Public Key)
     * - Enhanced WPA3 with public key validation
     * - Password includes embedded public key
     * - Protection against evil twin attacks
     */
    WPA3_SAE_PK("WPA3-Personal (SAE-PK)", 100, false),

    /**
     * WPA-Enterprise (802.1X)
     * - Legacy enterprise authentication
     * - RADIUS server required
     * - Various EAP methods
     */
    WPA_ENTERPRISE("WPA-Enterprise", 60, true),

    /**
     * WPA2-Enterprise (802.1X)
     * - Standard enterprise authentication
     * - RADIUS server required
     * - Certificate or credential-based
     * - More secure than PSK for organizations
     */
    WPA2_ENTERPRISE("WPA2-Enterprise", 85, true),

    /**
     * WPA3-Enterprise (192-bit security)
     * - Highest security level
     * - Suite-B cryptography
     * - Government/military grade
     * - RADIUS server required
     */
    WPA3_ENTERPRISE_192("WPA3-Enterprise (192-bit)", 100, true),

    /**
     * WPA3-Enterprise (standard)
     * - Modern enterprise authentication
     * - Enhanced security over WPA2-Enterprise
     * - RADIUS server required
     */
    WPA3_ENTERPRISE("WPA3-Enterprise", 95, true),

    /**
     * WEP (Wired Equivalent Privacy)
     * - Completely broken security
     * - Can be cracked in minutes
     * - Must never be used
     */
    WEP("WEP (Broken)", 5, false),

    /**
     * Unknown or unidentified authentication type
     */
    UNKNOWN("Unknown", 0, false),
    ;

    /**
     * Whether this is a modern authentication type (WPA3 or OWE)
     */
    val isModern: Boolean
        get() =
            when (this) {
                WPA3_SAE, WPA3_SAE_PK, WPA3_ENTERPRISE, WPA3_ENTERPRISE_192, OWE -> true
                else -> false
            }

    /**
     * Whether this authentication provides adequate security
     * Threshold: 70+ (WPA2-PSK or better)
     */
    val isSecure: Boolean
        get() = securityLevel >= 70

    /**
     * Whether this authentication type is deprecated
     */
    val isDeprecated: Boolean
        get() =
            when (this) {
                WPA_PSK, WPA_ENTERPRISE, WEP -> true
                else -> false
            }

    /**
     * Whether this is a Personal (PSK/SAE) authentication type
     */
    val isPersonal: Boolean
        get() =
            when (this) {
                WPA_PSK, WPA2_PSK, WPA3_SAE, WPA3_SAE_PK -> true
                else -> false
            }

    /**
     * Whether this is an Enterprise (802.1X) authentication type
     */
    val isEnterprise: Boolean
        get() = requiresRadius

    /**
     * Security strength score (0-100, same as securityLevel for compatibility)
     */
    val securityStrength: Int
        get() = securityLevel

    /**
     * Whether this authentication type requires Protected Management Frames (PMF)
     * WPA3 authentication types mandate PMF
     */
    val requiresPmf: Boolean
        get() =
            when (this) {
                WPA3_SAE, WPA3_SAE_PK, WPA3_ENTERPRISE, WPA3_ENTERPRISE_192, OWE -> true
                else -> false
            }

    companion object {
        /**
         * Determine authentication type from AKM suite and RSN capabilities
         *
         * @param akmSuite AKM suite number from RSN IE
         * @param usesPmf Whether Protected Management Frames are in use
         * @return Corresponding AuthType
         */
        fun fromAkmSuite(
            akmSuite: Int,
            usesPmf: Boolean,
        ): AuthType =
            when (akmSuite) {
                0 -> OPEN
                1 -> WPA_ENTERPRISE
                2 -> WPA_PSK
                3 -> WPA_ENTERPRISE // FT over 802.1X
                4 -> WPA_PSK // FT over PSK
                5 -> WPA2_ENTERPRISE // WPA2 802.1X
                6 -> WPA2_PSK // WPA2 PSK
                8 -> WPA3_SAE
                9 -> WPA3_ENTERPRISE
                12 -> WPA3_ENTERPRISE_192
                18 -> OWE
                24 -> WPA3_SAE_PK
                else -> UNKNOWN
            }
    }
}
