package io.lamco.netkit.model.topology

/**
 * WiFi cipher suites for data encryption
 *
 * Defined in IEEE 802.11-2020 Section 9.4.2.25.2 (Cipher suites)
 * and WPA3 specifications.
 *
 * @property displayName Human-readable name
 * @property strength Cipher strength rating (0-100)
 * @property keyLength Encryption key length in bits
 */
enum class CipherSuite(
    val displayName: String,
    val strength: Int,
    val keyLength: Int,
) {
    /**
     * No encryption (used only for group cipher in some configurations)
     */
    NONE("None", 0, 0),

    /**
     * WEP-40 (40-bit WEP)
     * - Completely broken
     * - Trivially crackable
     */
    WEP_40("WEP-40", 5, 40),

    /**
     * WEP-104 (104-bit WEP, marketed as "WEP-128")
     * - Completely broken
     * - Trivially crackable
     */
    WEP_104("WEP-104", 5, 104),

    /**
     * TKIP (Temporal Key Integrity Protocol)
     * - Used in WPA1
     * - Deprecated, weak
     * - Should not be used
     */
    TKIP("TKIP", 30, 128),

    /**
     * CCMP (AES-CCMP)
     * - Counter mode with CBC-MAC Protocol
     * - Standard cipher for WPA2
     * - 128-bit AES encryption
     * - Strong and widely supported
     */
    CCMP("AES-CCMP", 85, 128),

    /**
     * GCMP (Galois/Counter Mode Protocol)
     * - Used in WiFi 6 (802.11ax)
     * - 128-bit AES encryption
     * - More efficient than CCMP
     */
    GCMP("AES-GCMP", 90, 128),

    /**
     * GCMP-256
     * - 256-bit AES encryption
     * - Required for WPA3-Enterprise 192-bit security
     * - Highest security level
     */
    GCMP_256("AES-GCMP-256", 100, 256),

    /**
     * CCMP-256
     * - 256-bit AES-CCMP
     * - Alternative to GCMP-256
     * - Used in WPA3-Enterprise 192-bit
     */
    CCMP_256("AES-CCMP-256", 100, 256),

    /**
     * BIP-CMAC-128 (for management frame protection)
     * - Broadcast/Multicast Integrity Protocol
     * - Used for PMF (Protected Management Frames)
     * - 128-bit AES
     */
    BIP_CMAC_128("BIP-CMAC-128", 85, 128),

    /**
     * BIP-GMAC-128 (for management frame protection)
     * - Alternative to BIP-CMAC-128
     * - Galois Message Authentication Code
     */
    BIP_GMAC_128("BIP-GMAC-128", 90, 128),

    /**
     * BIP-GMAC-256 (for management frame protection)
     * - 256-bit management frame protection
     * - Required for WPA3-Enterprise 192-bit
     */
    BIP_GMAC_256("BIP-GMAC-256", 100, 256),

    /**
     * BIP-CMAC-256 (for management frame protection)
     * - 256-bit CMAC for management frames
     * - Alternative to BIP-GMAC-256
     */
    BIP_CMAC_256("BIP-CMAC-256", 100, 256),

    /**
     * Unknown or unrecognized cipher
     */
    UNKNOWN("Unknown", 0, 0),
    ;

    /**
     * Whether this cipher is considered secure (strength >= 80)
     */
    val isSecure: Boolean
        get() = strength >= 80

    /**
     * Whether this cipher is deprecated and should not be used
     */
    val isDeprecated: Boolean
        get() =
            when (this) {
                WEP_40, WEP_104, TKIP -> true
                else -> false
            }

    /**
     * Whether this is a management frame protection cipher
     */
    val isManagementCipher: Boolean
        get() =
            when (this) {
                BIP_CMAC_128, BIP_GMAC_128, BIP_GMAC_256, BIP_CMAC_256 -> true
                else -> false
            }

    /**
     * Whether this cipher provides forward secrecy
     */
    val providesForwardSecrecy: Boolean
        get() =
            when (this) {
                GCMP, GCMP_256, CCMP, CCMP_256 -> true
                else -> false
            }

    /**
     * Cipher category for grouping
     */
    val category: CipherCategory
        get() =
            when (this) {
                WEP_40, WEP_104 -> CipherCategory.WEP
                TKIP -> CipherCategory.TKIP
                CCMP -> CipherCategory.CCMP_128
                GCMP -> CipherCategory.GCMP_128
                CCMP_256, GCMP_256 -> CipherCategory.HIGH_SECURITY_256
                BIP_CMAC_128, BIP_GMAC_128, BIP_CMAC_256, BIP_GMAC_256 -> CipherCategory.MANAGEMENT
                else -> CipherCategory.UNKNOWN
            }

    companion object {
        /**
         * Determine cipher suite from RSN cipher suite number
         *
         * @param suiteType Suite type from RSN Information Element
         * @return Corresponding CipherSuite
         */
        fun fromSuiteType(suiteType: Int): CipherSuite =
            when (suiteType) {
                0 -> NONE
                1 -> WEP_40
                2 -> TKIP
                4 -> CCMP
                5 -> WEP_104
                8 -> GCMP
                9 -> GCMP_256
                10 -> CCMP_256
                11 -> BIP_GMAC_128
                12 -> BIP_GMAC_256
                13 -> BIP_CMAC_256
                else -> UNKNOWN
            }

        /**
         * Get recommended cipher for a given security level
         *
         * @param wpa3 Whether WPA3 is available
         * @param enterprise Whether this is an enterprise network
         * @return Recommended CipherSuite
         */
        fun recommended(
            wpa3: Boolean,
            enterprise: Boolean,
        ): CipherSuite =
            when {
                wpa3 && enterprise -> GCMP_256 // WPA3-Enterprise 192-bit
                wpa3 -> GCMP // WPA3-Personal
                else -> CCMP // WPA2
            }
    }
}

/**
 * Cipher suite categories for analysis
 */
enum class CipherCategory {
    /** WEP ciphers (broken, insecure) */
    WEP,

    /** TKIP cipher (deprecated, weak) */
    TKIP,

    /** 128-bit CCMP (AES-CCMP, WPA2 standard) */
    CCMP_128,

    /** 128-bit GCMP (AES-GCMP, WiFi 6 standard) */
    GCMP_128,

    /** 256-bit high-security ciphers (WPA3-Enterprise 192-bit) */
    HIGH_SECURITY_256,

    /** Management frame protection ciphers */
    MANAGEMENT,

    /** Unknown category */
    UNKNOWN,
}
