package io.lamco.netkit.model.topology

/**
 * Fast roaming capabilities detected from Information Elements
 *
 * These capabilities enable seamless roaming between access points
 * in multi-AP deployments. Standards:
 * - 802.11k: Radio Resource Management (neighbor reports)
 * - 802.11r: Fast BSS Transition (pre-authentication, reduces handoff time)
 * - 802.11v: BSS Transition Management (network-assisted roaming suggestions)
 *
 * Fast Transition (FT) variants:
 * - FT-PSK: Fast transition for WPA2/WPA3-Personal
 * - FT-EAP: Fast transition for WPA2/WPA3-Enterprise
 * - FT-SAE: Fast transition for WPA3-Personal (SAE)
 *
 * @property kSupported 802.11k Radio Resource Management
 * @property vSupported 802.11v BSS Transition Management
 * @property rSupported 802.11r Fast BSS Transition
 * @property ftPskSupported Fast Transition for PSK networks
 * @property ftEapSupported Fast Transition for Enterprise (802.1X) networks
 * @property ftSaeSupported Fast Transition for WPA3-SAE networks
 */
data class RoamingCapabilities(
    val kSupported: Boolean = false,
    val vSupported: Boolean = false,
    val rSupported: Boolean = false,
    val ftPskSupported: Boolean = false,
    val ftEapSupported: Boolean = false,
    val ftSaeSupported: Boolean = false,
) {
    /**
     * Whether any fast roaming capabilities are supported
     */
    val hasFastRoaming: Boolean
        get() = kSupported || vSupported || rSupported

    /**
     * Whether this AP supports the full fast roaming suite (802.11k/r/v)
     */
    val hasFullSuite: Boolean
        get() = kSupported && vSupported && rSupported

    /**
     * Whether Fast Transition (FT) is supported for any authentication type
     */
    val supportsFastTransition: Boolean
        get() = ftPskSupported || ftEapSupported || ftSaeSupported

    /**
     * Fast roaming score (0-100)
     * Based on which capabilities are present
     */
    val roamingScore: Int
        get() {
            var score = 0
            if (kSupported) score += 25 // Neighbor reports are fundamental
            if (vSupported) score += 25 // BSS transition management helps
            if (rSupported) score += 30 // Fast BSS transition is most impactful
            if (supportsFastTransition) score += 20 // FT variants add value
            return score.coerceAtMost(100)
        }

    /**
     * Roaming quality category
     */
    val roamingQuality: RoamingQuality
        get() =
            when {
                hasFullSuite && supportsFastTransition -> RoamingQuality.EXCELLENT
                hasFullSuite -> RoamingQuality.VERY_GOOD
                (kSupported || vSupported) && rSupported -> RoamingQuality.GOOD
                kSupported || vSupported || rSupported -> RoamingQuality.FAIR
                else -> RoamingQuality.POOR
            }

    /**
     * Expected roaming latency range in milliseconds
     */
    val expectedRoamingLatencyMs: IntRange
        get() =
            when {
                rSupported && supportsFastTransition -> 20..50 // 802.11r with FT: very fast
                rSupported -> 50..100 // 802.11r alone: fast
                kSupported && vSupported -> 100..300 // 11k+11v: moderate
                hasFastRoaming -> 200..500 // Some support: acceptable
                else -> 500..3000 // No support: slow
            }

    /**
     * Human-readable summary of roaming capabilities
     */
    val capabilitiesSummary: String
        get() =
            buildString {
                val features = mutableListOf<String>()
                if (kSupported) features.add("802.11k")
                if (vSupported) features.add("802.11v")
                if (rSupported) features.add("802.11r")
                if (ftPskSupported) features.add("FT-PSK")
                if (ftEapSupported) features.add("FT-EAP")
                if (ftSaeSupported) features.add("FT-SAE")

                if (features.isEmpty()) {
                    append("No fast roaming support")
                } else {
                    append(features.joinToString(", "))
                }
            }

    /**
     * Whether this AP supports fast roaming for the given authentication type
     */
    fun supportsFastRoamingFor(authType: AuthType): Boolean =
        when {
            !hasFastRoaming -> false
            authType == AuthType.WPA2_PSK && ftPskSupported -> true
            authType == AuthType.WPA3_SAE && ftSaeSupported -> true
            authType.isEnterprise && ftEapSupported -> true
            // k/v without FT still helps but is slower
            kSupported || vSupported -> true
            else -> false
        }

    companion object {
        /**
         * No roaming support (single AP or basic multi-AP)
         */
        fun none(): RoamingCapabilities = RoamingCapabilities()

        /**
         * Basic roaming (802.11k only)
         */
        fun basic(): RoamingCapabilities = RoamingCapabilities(kSupported = true)

        /**
         * Full enterprise roaming suite
         */
        fun fullSuite(authType: AuthType): RoamingCapabilities =
            RoamingCapabilities(
                kSupported = true,
                vSupported = true,
                rSupported = true,
                ftPskSupported = authType == AuthType.WPA2_PSK,
                ftEapSupported = authType.isEnterprise,
                ftSaeSupported = authType == AuthType.WPA3_SAE,
            )
    }
}

/**
 * Roaming quality categories
 */
enum class RoamingQuality(
    val displayName: String,
    val description: String,
) {
    /**
     * Excellent roaming (802.11k/r/v + FT)
     * - Seamless handoffs (20-50ms)
     * - No noticeable disruption
     * - VoIP and video calls unaffected
     */
    EXCELLENT(
        "Excellent",
        "Seamless roaming with minimal disruption (20-50ms handoffs)",
    ),

    /**
     * Very good roaming (802.11k/r/v)
     * - Fast handoffs (50-100ms)
     * - Minor disruption possible
     * - Suitable for most real-time applications
     */
    VERY_GOOD(
        "Very Good",
        "Fast roaming with brief disruption (50-100ms handoffs)",
    ),

    /**
     * Good roaming (some k/r/v support)
     * - Moderate handoffs (100-300ms)
     * - Noticeable but acceptable disruption
     * - May affect real-time applications
     */
    GOOD(
        "Good",
        "Moderate roaming with noticeable disruption (100-300ms handoffs)",
    ),

    /**
     * Fair roaming (minimal support)
     * - Slow handoffs (200-500ms)
     * - Significant disruption
     * - Not ideal for real-time applications
     */
    FAIR(
        "Fair",
        "Slow roaming with significant disruption (200-500ms handoffs)",
    ),

    /**
     * Poor roaming (no fast roaming support)
     * - Very slow handoffs (500-3000ms)
     * - Connection may drop
     * - "Sticky client" behavior common
     */
    POOR(
        "Poor",
        "No fast roaming support - slow handoffs and possible disconnections",
    ),
    ;

    /**
     * Whether this roaming quality is acceptable for real-time applications
     * (VoIP, video calls, gaming).
     *
     * EXCELLENT, VERY_GOOD, and GOOD are acceptable.
     * FAIR and POOR are not - latency will cause noticeable disruption.
     */
    val isAcceptableForRealTime: Boolean
        get() = this in listOf(EXCELLENT, VERY_GOOD, GOOD)
}
