package io.lamco.netkit.ieparser.model

/**
 * WiFi cipher suites for data and management frame encryption
 *
 * Cipher suites define the encryption algorithm used for:
 * - **Group cipher**: Broadcast/multicast traffic
 * - **Pairwise cipher**: Unicast traffic (device ↔ AP)
 * - **Management cipher**: Protected Management Frames (PMF)
 *
 * Standards:
 * - IEEE 802.11-2020 Section 9.4.2.25.2 (Cipher Suite Selectors)
 * - OUI: 00-0F-AC (WiFi Alliance)
 *
 * @property displayName Human-readable cipher name
 * @property deprecated Whether this cipher is deprecated/insecure
 * @property oui Organizationally Unique Identifier
 * @property type Cipher type within OUI namespace
 */
enum class CipherSuite(
    val displayName: String,
    val deprecated: Boolean,
    val oui: ByteArray = byteArrayOf(0x00, 0x0F, 0xAC.toByte()),
    val type: Int,
) {
    /**
     * WEP-40 (deprecated, broken)
     * - 40-bit key (5 bytes)
     * - Crackable in seconds with modern tools
     * - **DO NOT USE**
     */
    WEP40("WEP-40", deprecated = true, type = 1),

    /**
     * WEP-104 (deprecated, broken)
     * - 104-bit key (13 bytes)
     * - Crackable in minutes
     * - **DO NOT USE**
     */
    WEP104("WEP-104", deprecated = true, type = 5),

    /**
     * TKIP (deprecated since 2012)
     * - Temporal Key Integrity Protocol
     * - Used in WPA and early WPA2
     * - Vulnerable to attacks (KRACK, etc.)
     * - **UPGRADE TO CCMP**
     */
    TKIP("TKIP", deprecated = true, type = 2),

    /**
     * CCMP (AES) - Standard cipher for WPA2/WPA3
     * - Counter Mode with CBC-MAC Protocol
     * - 128-bit AES encryption
     * - Secure for most use cases
     * - Default for WPA2-Personal/Enterprise
     */
    CCMP_128("CCMP (AES)", deprecated = false, type = 4),

    /**
     * CCMP-256 - High-security cipher for WPA3-Enterprise
     * - 256-bit AES encryption
     * - Used in WPA3-Enterprise 192-bit mode
     * - Government/enterprise use
     */
    CCMP_256("CCMP-256", deprecated = false, type = 10),

    /**
     * GCMP - WiFi 6/7 high-performance cipher
     * - Galois/Counter Mode Protocol
     * - 128-bit AES encryption
     * - Better performance than CCMP (hardware acceleration)
     * - Standard for WiFi 6/7
     */
    GCMP_128("GCMP", deprecated = false, type = 8),

    /**
     * GCMP-256 - WiFi 6/7 high-security cipher
     * - 256-bit AES encryption
     * - Enterprise/high-security WiFi 6/7
     */
    GCMP_256("GCMP-256", deprecated = false, type = 9),

    /**
     * BIP-CMAC-128 - Management frame protection
     * - Broadcast/Multicast Integrity Protocol
     * - 128-bit AES-CMAC
     * - Used for PMF (Protected Management Frames)
     */
    BIP_CMAC_128("BIP-CMAC-128", deprecated = false, type = 6),

    /**
     * BIP-CMAC-256 - High-security management frame protection
     * - 256-bit AES-CMAC
     * - WPA3-Enterprise 192-bit mode
     */
    BIP_CMAC_256("BIP-CMAC-256", deprecated = false, type = 13),

    /**
     * BIP-GMAC-128 - WiFi 6/7 management frame protection
     * - Galois Message Authentication Code
     * - 128-bit AES
     * - Better performance than CMAC
     */
    BIP_GMAC_128("BIP-GMAC-128", deprecated = false, type = 11),

    /**
     * BIP-GMAC-256 - High-security WiFi 6/7 management protection
     * - 256-bit AES-GMAC
     */
    BIP_GMAC_256("BIP-GMAC-256", deprecated = false, type = 12),

    /**
     * Vendor-specific cipher
     * - Non-standard OUI or type
     * - Cannot determine security properties
     */
    VENDOR_SPECIFIC("Vendor Specific", deprecated = false, type = -1),

    /**
     * Unknown cipher
     * - Unable to parse or identify
     */
    UNKNOWN("Unknown", deprecated = false, type = 0),
    ;

    /**
     * Whether this is a data cipher (not management)
     */
    val isDataCipher: Boolean
        get() =
            this in
                listOf(
                    WEP40,
                    WEP104,
                    TKIP,
                    CCMP_128,
                    CCMP_256,
                    GCMP_128,
                    GCMP_256,
                )

    /**
     * Whether this is a management frame cipher (PMF)
     */
    val isManagementCipher: Boolean
        get() =
            this in
                listOf(
                    BIP_CMAC_128,
                    BIP_CMAC_256,
                    BIP_GMAC_128,
                    BIP_GMAC_256,
                )

    /**
     * Security level (0-100)
     */
    val securityLevel: Int
        get() =
            when (this) {
                WEP40, WEP104 -> 0 // Broken
                TKIP -> 20 // Deprecated, vulnerable
                CCMP_128 -> 70 // Good (WPA2 standard)
                GCMP_128 -> 75 // Better (WiFi 6)
                BIP_CMAC_128, BIP_GMAC_128 -> 70
                CCMP_256 -> 90 // Excellent (WPA3-Enterprise)
                GCMP_256 -> 95 // Excellent (WiFi 6/7 high-sec)
                BIP_CMAC_256, BIP_GMAC_256 -> 90
                VENDOR_SPECIFIC, UNKNOWN -> 0
            }

    companion object {
        /**
         * Parse cipher suite from OUI + type
         *
         * @param oui 3-byte OUI (e.g., 00-0F-AC for WiFi Alliance)
         * @param type Cipher type within OUI namespace
         * @return Matched CipherSuite or VENDOR_SPECIFIC/UNKNOWN
         */
        fun fromBytes(
            oui: ByteArray,
            type: Int,
        ): CipherSuite {
            // Check if WiFi Alliance OUI (00-0F-AC)
            if (oui.contentEquals(byteArrayOf(0x00, 0x0F, 0xAC.toByte()))) {
                return entries.find { it.type == type } ?: UNKNOWN
            }

            return VENDOR_SPECIFIC
        }
    }
}
