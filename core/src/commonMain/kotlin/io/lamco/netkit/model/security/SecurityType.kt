package io.lamco.netkit.model.security

/**
 * WiFi security types supported across different standards
 *
 * Security levels ranked 0-100:
 * - 100: WPA3-Personal, WPA3-Enterprise 192-bit
 * - 95: WPA3-Enterprise
 * - 80: WPA2-Enterprise
 * - 70: WPA2-Personal
 * - 60: Enhanced Open (OWE)
 * - 40: WPA (Legacy)
 * - 10: WEP (Broken)
 * - 0: Open, Unknown
 *
 * @property displayName Human-readable name
 * @property securityLevel Security rating (0-100)
 */
enum class SecurityType(
    val displayName: String,
    val securityLevel: Int,
) {
    /**
     * WPA3-Personal using SAE (Simultaneous Authentication of Equals)
     * - Resistant to offline dictionary attacks
     * - Forward secrecy
     * - 128-bit encryption minimum
     * - PMF required
     */
    WPA3_PERSONAL("WPA3-Personal (SAE)", 100),

    /**
     * WPA3-Enterprise with 192-bit security
     * - Suite-B cryptography
     * - 192-bit minimum strength
     * - Government/military grade
     */
    WPA3_ENTERPRISE_192("WPA3-Enterprise (192-bit)", 100),

    /**
     * WPA3-Enterprise with 128-bit security
     * - Enterprise authentication (802.1X)
     * - Enhanced security over WPA2
     */
    WPA3_ENTERPRISE("WPA3-Enterprise", 95),

    /**
     * WPA2-Personal (PSK)
     * - Pre-shared key
     * - Still secure if strong password used
     * - Vulnerable to offline dictionary attacks
     */
    WPA2_PERSONAL("WPA2-Personal", 70),

    /**
     * WPA2-Enterprise
     * - 802.1X authentication
     * - More secure than PSK
     */
    WPA2_ENTERPRISE("WPA2-Enterprise", 80),

    /**
     * WPA (Legacy)
     * - Original WPA
     * - TKIP cipher (deprecated)
     * - Should be upgraded to WPA2/WPA3
     */
    WPA_PERSONAL("WPA (Legacy)", 40),

    /**
     * WEP (Wired Equivalent Privacy)
     * - Completely broken
     * - Can be cracked in minutes
     * - Should NEVER be used
     */
    WEP("WEP (Broken)", 10),

    /**
     * Open network with no encryption
     * - All traffic visible to attackers
     * - Should use Enhanced Open (OWE) instead
     */
    OPEN("Open (No Security)", 0),

    /**
     * Enhanced Open using OWE (Opportunistic Wireless Encryption)
     * - Encrypted open network
     * - No password required
     * - Protection against passive eavesdropping
     */
    ENHANCED_OPEN("Enhanced Open (OWE)", 60),

    /**
     * Unknown or unrecognized security type
     */
    UNKNOWN("Unknown", 0),
    ;

    /**
     * Determines if this security type provides adequate protection
     * Threshold: 70+ (WPA2-Personal or better)
     */
    val isSecure: Boolean
        get() = securityLevel >= 70

    /**
     * Determines if this is a modern security protocol
     * Modern: WPA3 variants and Enhanced Open
     */
    val isModern: Boolean
        get() =
            this in
                listOf(
                    WPA3_PERSONAL,
                    WPA3_ENTERPRISE_192,
                    WPA3_ENTERPRISE,
                    ENHANCED_OPEN,
                )

    /**
     * Determines if this security type is deprecated and should be avoided
     */
    val isDeprecated: Boolean
        get() = this in listOf(WPA_PERSONAL, WEP)

    /**
     * Determines if this security type is critically insecure
     */
    val isCriticallyInsecure: Boolean
        get() = this in listOf(WEP, OPEN) && this != ENHANCED_OPEN
}
