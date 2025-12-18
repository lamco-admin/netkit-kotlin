package io.lamco.netkit.model.topology

/**
 * Unique identifier for network security configuration
 *
 * Used to group BSSIDs that share identical security settings,
 * which typically indicates they belong to the same logical network.
 *
 * Two BSSIDs with the same SSID but different security fingerprints
 * are considered separate networks (e.g., a WPA2/WPA3 transition network
 * creates two distinct networks).
 *
 * @property authType Authentication method (PSK, SAE, Enterprise, etc.)
 * @property cipherSet Set of supported cipher suites
 * @property pmfRequired Whether PMF (Protected Management Frames) is mandatory
 * @property transitionMode Transition mode for mixed WPA2/WPA3 networks
 */
data class SecurityFingerprint(
    val authType: AuthType,
    val cipherSet: Set<CipherSuite>,
    val pmfRequired: Boolean,
    val transitionMode: TransitionMode?,
) {
    init {
        require(cipherSet.isNotEmpty()) {
            "Cipher set must not be empty (except for OPEN networks)"
        }
    }

    /**
     * Whether this fingerprint represents a secure configuration
     * Requires: secure auth type AND no weak ciphers
     */
    val isSecure: Boolean
        get() = authType.isSecure && !hasWeakCiphers

    /**
     * Whether this fingerprint represents a modern configuration
     * Modern = WPA3, OWE, or WPA2 with strong ciphers and PMF
     */
    val isModern: Boolean
        get() = authType.isModern || (authType == AuthType.WPA2_PSK && pmfRequired && !hasWeakCiphers)

    /**
     * Whether any weak/deprecated ciphers are present
     */
    val hasWeakCiphers: Boolean
        get() = cipherSet.any { it.isDeprecated }

    /**
     * Strongest cipher in the set (by strength rating)
     */
    val strongestCipher: CipherSuite
        get() = cipherSet.maxByOrNull { it.strength } ?: CipherSuite.NONE

    /**
     * Weakest cipher in the set (by strength rating)
     */
    val weakestCipher: CipherSuite
        get() = cipherSet.minByOrNull { it.strength } ?: CipherSuite.NONE

    /**
     * Overall security score (0-100)
     * Combines auth type security level with cipher strength
     */
    val securityScore: Int
        get() {
            val authScore = authType.securityLevel
            val cipherScore = strongestCipher.strength
            val pmfBonus = if (pmfRequired) 5 else 0
            val weakCipherPenalty = if (hasWeakCiphers) -20 else 0

            return (authScore * 0.6 + cipherScore * 0.4 + pmfBonus + weakCipherPenalty)
                .toInt()
                .coerceIn(0, 100)
        }

    /**
     * Human-readable security summary
     */
    val securitySummary: String
        get() =
            buildString {
                append(authType.displayName)
                if (cipherSet.isNotEmpty()) {
                    append(" with ${strongestCipher.displayName}")
                }
                if (pmfRequired) {
                    append(" (PMF Required)")
                }
                if (transitionMode != null) {
                    append(" - ${transitionMode.displayName}")
                }
            }

    /**
     * Whether this fingerprint matches another (identical security config)
     */
    fun matches(other: SecurityFingerprint): Boolean =
        authType == other.authType &&
            cipherSet == other.cipherSet &&
            pmfRequired == other.pmfRequired &&
            transitionMode == other.transitionMode

    /**
     * Whether this fingerprint is compatible with another
     * (client could connect to both with same credentials)
     */
    fun isCompatibleWith(other: SecurityFingerprint): Boolean {
        // Same auth type and at least one common cipher
        return authType == other.authType &&
            cipherSet.intersect(other.cipherSet).isNotEmpty()
    }

    companion object {
        /**
         * Create fingerprint for open network
         */
        fun open(): SecurityFingerprint =
            SecurityFingerprint(
                authType = AuthType.OPEN,
                cipherSet = setOf(CipherSuite.NONE),
                pmfRequired = false,
                transitionMode = null,
            )

        /**
         * Create fingerprint for Enhanced Open (OWE)
         */
        fun enhancedOpen(): SecurityFingerprint =
            SecurityFingerprint(
                authType = AuthType.OWE,
                cipherSet = setOf(CipherSuite.CCMP),
                pmfRequired = true,
                transitionMode = null,
            )

        /**
         * Create fingerprint for WPA2-Personal
         */
        fun wpa2Personal(pmfRequired: Boolean = false): SecurityFingerprint =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                pmfRequired = pmfRequired,
                transitionMode = null,
            )

        /**
         * Create fingerprint for WPA3-Personal
         */
        fun wpa3Personal(): SecurityFingerprint =
            SecurityFingerprint(
                authType = AuthType.WPA3_SAE,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP),
                pmfRequired = true, // PMF is mandatory for WPA3
                transitionMode = null,
            )

        /**
         * Create fingerprint for WPA2/WPA3 transition mode
         */
        fun wpa2Wpa3Transition(): SecurityFingerprint =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK, // Client sees WPA2
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP),
                pmfRequired = false, // Optional in transition mode
                transitionMode = TransitionMode.WPA2_WPA3,
            )
    }
}

/**
 * Transition modes for mixed-security networks
 *
 * Some networks operate in transition mode to support both legacy
 * and modern clients simultaneously.
 */
enum class TransitionMode(
    val displayName: String,
) {
    /**
     * WPA2/WPA3 transition mode
     * - Advertises both WPA2-PSK and WPA3-SAE
     * - WPA3 clients use SAE, WPA2 clients use PSK
     * - PMF is optional (required for WPA3, optional for WPA2)
     */
    WPA2_WPA3("WPA2/WPA3 Transition"),

    /**
     * Open/Enhanced Open transition
     * - Advertises both open and OWE
     * - OWE-capable clients use encryption
     * - Legacy clients connect without encryption
     */
    OPEN_OWE("Open/OWE Transition"),

    /**
     * WPA/WPA2 transition (legacy)
     * - Deprecated, should upgrade to WPA2 only
     */
    WPA_WPA2("WPA/WPA2 Transition"),
    ;

    /**
     * Whether this transition mode is recommended
     */
    val isRecommended: Boolean
        get() =
            when (this) {
                WPA2_WPA3 -> true // Good for gradual WPA3 migration
                OPEN_OWE -> true // Good for public networks
                WPA_WPA2 -> false // Deprecated
            }
}
