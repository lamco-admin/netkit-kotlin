package io.lamco.netkit.ieparser.model

/**
 * AKM (Authentication and Key Management) suites
 *
 * AKM suites define the authentication method and key derivation mechanism.
 *
 * Key methods:
 * - **PSK** (Pre-Shared Key): Password-based, WPA2-Personal
 * - **SAE** (Simultaneous Authentication of Equals): WPA3-Personal
 * - **SAE-PK** (SAE with Public Key): WPA3 enhanced security
 * - **OWE** (Opportunistic Wireless Encryption): Enhanced Open
 * - **802.1X**: Enterprise authentication (RADIUS)
 *
 * Standards:
 * - IEEE 802.11-2020 Section 9.4.2.25.3 (AKM Suite Selectors)
 * - RFC 8110 (OWE)
 * - WPA3 Specification (WiFi Alliance)
 *
 * @property displayName Human-readable AKM name
 * @property oui Organizationally Unique Identifier
 * @property type AKM type within OUI namespace
 */
enum class AKMSuite(
    val displayName: String,
    val oui: ByteArray = byteArrayOf(0x00, 0x0F, 0xAC.toByte()),
    val type: Int,
) {
    /**
     * WPA-Enterprise (802.1X)
     * - RADIUS authentication
     * - Legacy WPA (deprecated)
     * - **Upgrade to WPA2/WPA3**
     */
    WPA_ENTERPRISE("WPA-Enterprise", type = 1),

    /**
     * PSK (Pre-Shared Key) - WPA2-Personal
     * - Password-based authentication
     * - Standard for home networks
     * - **Secure but consider WPA3**
     */
    PSK("WPA2-PSK", type = 2),

    /**
     * FT over 802.1X (Fast Transition)
     * - Fast roaming for Enterprise networks
     * - 802.11r support
     */
    FT_OVER_8021X("FT over 802.1X", type = 3),

    /**
     * FT-PSK (Fast Transition PSK)
     * - Fast roaming for Personal networks
     * - 802.11r support
     */
    FT_PSK("FT-PSK", type = 4),

    /**
     * WPA-SHA256 (Enterprise with SHA256)
     * - Stronger key derivation
     * - Enterprise networks
     */
    WPA_SHA256("WPA-SHA256", type = 5),

    /**
     * PSK-SHA256 (Personal with SHA256)
     * - Stronger key derivation than PSK
     * - Better than standard PSK
     */
    PSK_SHA256("PSK-SHA256", type = 6),

    /**
     * SAE (Simultaneous Authentication of Equals) - WPA3-Personal
     * - Password-based, resistant to offline attacks
     * - Dragonfly handshake
     * - **Modern, secure authentication**
     * - Required for WiFi 6E (6 GHz)
     */
    SAE("WPA3-SAE", type = 8),

    /**
     * FT over SAE
     * - Fast roaming for WPA3-Personal
     * - 802.11r + WPA3
     */
    FT_OVER_SAE("FT over SAE", type = 9),

    /**
     * Suite-B EAP-SHA256
     * - Government/enterprise security
     * - NSA Suite B cryptography
     */
    SUITE_B_EAP_SHA256("Suite-B SHA256", type = 11),

    /**
     * Suite-B EAP-SHA384 - WPA3-Enterprise 192-bit
     * - 192-bit security level
     * - Government/high-security networks
     * - Mandatory ciphers: GCMP-256, BIP-GMAC-256
     */
    SUITE_B_EAP_SHA384("Suite-B SHA384", type = 12),

    /**
     * OWE (Opportunistic Wireless Encryption) - Enhanced Open
     * - Encryption for open networks
     * - No password, but still encrypted
     * - Coffee shops, airports, public WiFi
     * - Protects against passive eavesdropping
     */
    OWE("Enhanced Open (OWE)", type = 18),

    /**
     * SAE-PK (SAE with Public Key)
     *
     * WPA3-Personal with Public Key authentication. Protects against evil twin attacks
     * by verifying network identity with digital signature. Requires both password
     * and network public key for authentication.
     *
     * How it works:
     * - Router broadcasts its public key
     * - Password contains encoded public key fingerprint
     * - Client verifies network identity during handshake
     * - Even with correct password, fake AP cannot authenticate
     *
     * Example password format: `j7xq-gkvy-zgt6`
     * (contains public key fingerprint)
     */
    SAE_PK("WPA3-SAE-PK", type = 24),

    /**
     * Vendor-specific AKM
     * - Non-standard OUI or type
     * - Cannot determine security properties
     */
    VENDOR_SPECIFIC("Vendor Specific", type = -1),

    /**
     * Unknown AKM
     * - Unable to parse or identify
     */
    UNKNOWN("Unknown", type = 0),
    ;

    /**
     * Whether this is an enterprise authentication method (802.1X)
     */
    val isEnterprise: Boolean
        get() =
            this in
                listOf(
                    WPA_ENTERPRISE,
                    FT_OVER_8021X,
                    WPA_SHA256,
                    SUITE_B_EAP_SHA256,
                    SUITE_B_EAP_SHA384,
                )

    /**
     * Whether this is a personal authentication method (password/PSK)
     */
    val isPersonal: Boolean
        get() =
            this in
                listOf(
                    PSK,
                    PSK_SHA256,
                    FT_PSK,
                    SAE,
                    SAE_PK,
                    FT_OVER_SAE,
                )

    /**
     * Whether this is a WPA3 method
     */
    val isWPA3: Boolean
        get() =
            this in
                listOf(
                    SAE,
                    SAE_PK,
                    FT_OVER_SAE,
                    SUITE_B_EAP_SHA384,
                )

    /**
     * Whether this is an open network encryption method
     */
    val isOpenEncryption: Boolean
        get() = this == OWE

    /**
     * Supports fast roaming (802.11r)
     */
    val supportsFastRoaming: Boolean
        get() =
            this in
                listOf(
                    FT_OVER_8021X,
                    FT_PSK,
                    FT_OVER_SAE,
                )

    /**
     * Security level (0-100)
     */
    val securityLevel: Int
        get() =
            when (this) {
                WPA_ENTERPRISE -> 40 // Legacy, deprecated
                PSK -> 70 // WPA2-Personal (good)
                PSK_SHA256 -> 75 // Better key derivation
                SAE -> 90 // WPA3-Personal (excellent)
                SAE_PK -> 100 // WPA3 + anti-evil-twin (maximum)
                OWE -> 50 // Encrypted open (fair)
                SUITE_B_EAP_SHA256 -> 80 // Enterprise (very good)
                SUITE_B_EAP_SHA384 -> 95 // 192-bit security (excellent)
                FT_OVER_8021X, WPA_SHA256 -> 75
                FT_PSK -> 70
                FT_OVER_SAE -> 90
                VENDOR_SPECIFIC, UNKNOWN -> 0
            }

    companion object {
        /**
         * Parse AKM suite from OUI + type
         *
         * @param oui 3-byte OUI (e.g., 00-0F-AC for WiFi Alliance)
         * @param type AKM type within OUI namespace
         * @return Matched AKMSuite or VENDOR_SPECIFIC/UNKNOWN
         */
        fun fromBytes(
            oui: ByteArray,
            type: Int,
        ): AKMSuite {
            // Check if WiFi Alliance OUI (00-0F-AC)
            if (oui.contentEquals(byteArrayOf(0x00, 0x0F, 0xAC.toByte()))) {
                return entries.find { it.type == type } ?: UNKNOWN
            }

            return VENDOR_SPECIFIC
        }
    }
}
