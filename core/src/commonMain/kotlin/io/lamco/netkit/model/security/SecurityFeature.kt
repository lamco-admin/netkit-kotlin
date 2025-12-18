package io.lamco.netkit.model.security

/**
 * Advanced security features available in modern WiFi networks
 *
 * These features go beyond basic encryption and represent state-of-the-art
 * WiFi security capabilities introduced in WPA3, WiFi 6/6E/7, and other
 * recent standards.
 *
 * @property displayName Human-readable feature name
 * @property description Detailed explanation of the feature
 */
enum class SecurityFeature(
    val displayName: String,
    val description: String,
) {
    /**
     * SAE (Simultaneous Authentication of Equals)
     * - Replaces PSK in WPA3
     * - Resistant to offline dictionary attacks
     * - Forward secrecy (compromise of password doesn't reveal past sessions)
     * - Dragonfly key exchange protocol
     */
    SAE_AUTHENTICATION(
        "SAE Authentication",
        "Simultaneous Authentication of Equals - resistant to dictionary attacks",
    ),

    /**
     * SAE-PK (SAE with Public Key)
     * - Adds public key authentication to SAE
     * - Protects against evil twin attacks
     * - AP proves possession of private key matching public key in password
     * - INDUSTRY FIRST DETECTION in WiFi Intelligence app
     */
    SAE_PK(
        "SAE-PK",
        "SAE with Public Key - protects against evil twin attacks",
    ),

    /**
     * OWE (Opportunistic Wireless Encryption)
     * - Encrypted open network (no password)
     * - Protects against passive eavesdropping
     * - Each client gets unique encryption keys
     * - "Enhanced Open" branding
     */
    OWE_ENCRYPTION(
        "OWE Encryption",
        "Opportunistic Wireless Encryption - protects open networks",
    ),

    /**
     * PMF Required (Protected Management Frames)
     * - Mandatory encryption of management frames
     * - Prevents deauth/disassoc attacks
     * - Required for WPA3
     */
    PMF_REQUIRED(
        "PMF Required",
        "Protected Management Frames mandatory - prevents deauth attacks",
    ),

    /**
     * PMF Capable (Protected Management Frames)
     * - Optional encryption of management frames
     * - Partial protection (client-dependent)
     * - Available for WPA2
     */
    PMF_CAPABLE(
        "PMF Capable",
        "Protected Management Frames supported but not required",
    ),

    /**
     * H2E (Hash-to-Element)
     * - Enhanced SAE security
     * - Protects against side-channel attacks
     * - Mandatory for WiFi 6E (6 GHz)
     * - Optional for 2.4/5 GHz WPA3
     */
    H2E_SUPPORT(
        "Hash-to-Element",
        "Enhanced SAE security - mandatory for WiFi 6E",
    ),

    /**
     * Transition Disable
     * - Network signals it will disable legacy modes
     * - Prevents downgrade attacks
     * - Enforces WPA3-only after initial connection
     */
    TRANSITION_DISABLE(
        "Transition Disable",
        "Network enforces WPA3-only connections",
    ),

    /**
     * Suite-B 192-bit Security
     * - Government/military grade encryption
     * - 192-bit minimum cryptographic strength
     * - GCMP-256 cipher
     * - HMAC-SHA384
     * - WPA3-Enterprise feature
     */
    SUITE_B_192BIT(
        "Suite-B 192-bit",
        "Enterprise-grade encryption for government/military use",
    ),

    /**
     * WiFi 6E (6 GHz band)
     * - 6 GHz spectrum (5925-7125 MHz)
     * - Mandatory WPA3
     * - Mandatory PMF
     * - Less congestion, better security
     */
    WIFI_6E(
        "WiFi 6E",
        "6GHz band with mandatory WPA3",
    ),

    /**
     * WiFi 7 MLO (Multi-Link Operation)
     * - Simultaneous connections on multiple bands
     * - Aggregated bandwidth
     * - Seamless switching
     * - Enhanced reliability
     */
    WIFI_7_MLO(
        "WiFi 7 MLO",
        "Multi-Link Operation - simultaneous band connections",
    ),

    /**
     * Beacon Protection
     * - Encrypts beacon frames
     * - Prevents beacon spoofing
     * - Protects SSID and network info
     * - WPA3 enhancement (2024)
     */
    BEACON_PROTECTION(
        "Beacon Protection",
        "Encrypted beacon frames - prevents network spoofing",
    ),

    /**
     * 802.11k (Radio Resource Management)
     * - Neighbor AP reports
     * - Assists with roaming decisions
     * - Better handoff performance
     */
    RADIO_RESOURCE_MGMT(
        "802.11k Support",
        "Radio resource management for optimized roaming",
    ),

    /**
     * 802.11v (Wireless Network Management)
     * - BSS transition management
     * - Network-assisted roaming
     * - Load balancing
     */
    BSS_TRANSITION_MGMT(
        "802.11v Support",
        "BSS transition management for seamless roaming",
    ),

    /**
     * 802.11r (Fast Roaming)
     * - Fast BSS transition
     * - Pre-authentication
     * - Reduces roaming latency
     * - Critical for voice/video applications
     */
    FAST_BSS_TRANSITION(
        "802.11r Support",
        "Fast roaming for latency-sensitive applications",
    ),
}
