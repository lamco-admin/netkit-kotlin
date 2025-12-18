package io.lamco.netkit.model.security

/**
 * Protected Management Frames (802.11w) status
 *
 * PMF protects management frames (deauth, disassoc, etc.) from forgery and eavesdropping.
 * - Required for WPA3
 * - Optional for WPA2
 * - Not available for WPA/WEP/Open
 *
 * Without PMF, networks are vulnerable to:
 * - Deauthentication attacks (forced disconnection)
 * - Disassociation attacks
 * - Management frame injection
 *
 * @property displayName Human-readable status
 * @property description Detailed explanation
 */
enum class PMFStatus(
    val displayName: String,
    val description: String,
) {
    /**
     * PMF is required (802.11w mandatory)
     * - All management frames are encrypted and authenticated
     * - Clients must support PMF to connect
     * - Mandatory for WPA3
     * - Provides best protection against deauth attacks
     */
    REQUIRED(
        "PMF Required",
        "Management frames are protected. Mandatory for WPA3.",
    ),

    /**
     * PMF is capable (802.11w optional)
     * - Management frames can be protected if both sides support it
     * - Backward compatible with non-PMF clients
     * - Optional for WPA2
     * - Partial protection (depends on client support)
     */
    CAPABLE(
        "PMF Capable",
        "Management frame protection available but not required.",
    ),

    /**
     * PMF is disabled (no 802.11w)
     * - Management frames are unprotected
     * - Vulnerable to deauth/disassoc attacks
     * - Should be enabled if supported
     */
    DISABLED(
        "PMF Disabled",
        "No management frame protection. Vulnerable to deauth attacks.",
    ),

    /**
     * PMF status cannot be determined
     * - Missing RSN information element
     * - Incomplete beacon/probe data
     */
    UNKNOWN(
        "Unknown",
        "Unable to determine PMF status.",
    ),
    ;

    /**
     * Determines if this PMF status provides adequate protection
     * Only REQUIRED provides full protection
     */
    val isSecure: Boolean
        get() = this == REQUIRED

    /**
     * Determines if PMF provides any level of protection
     * CAPABLE provides partial protection, REQUIRED provides full
     */
    val hasProtection: Boolean
        get() = this in listOf(REQUIRED, CAPABLE)

    /**
     * Security score contribution (0-10 points)
     */
    val securityPoints: Int
        get() =
            when (this) {
                REQUIRED -> 10
                CAPABLE -> 5
                DISABLED -> 0
                UNKNOWN -> 0
            }
}
