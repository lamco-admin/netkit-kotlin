package io.lamco.netkit.optimizer.model

/**
 * Coverage optimization goals and constraints
 *
 * Defines the target coverage characteristics for TX power optimization
 * and network planning. The optimizer will attempt to achieve these goals
 * while balancing interference, power consumption, and regulatory limits.
 *
 * Coverage goals must balance competing objectives:
 * - Maximum coverage area vs. minimum interference
 * - Strong signal strength vs. power efficiency
 * - Cell overlap for roaming vs. co-channel interference
 *
 * @property minRssi Minimum acceptable RSSI at coverage edge (dBm)
 * @property targetRssi Target RSSI for good coverage (dBm)
 * @property maxInterference Maximum acceptable interference level (0.0-1.0)
 * @property coverageArea Target coverage area in square meters (null = maximize coverage)
 * @property minOverlapPercent Minimum overlap between adjacent cells for roaming (0-100%)
 * @property maxOverlapPercent Maximum overlap to avoid excessive interference (0-100%)
 * @property prioritizeRoaming Prioritize roaming-friendly overlap over interference reduction
 *
 * @throws IllegalArgumentException if goals are invalid or contradictory
 */
data class CoverageGoals(
    val minRssi: Int = -70,
    val targetRssi: Int = -60,
    val maxInterference: Double = 0.3,
    val coverageArea: Double? = null,
    val minOverlapPercent: Int = 10,
    val maxOverlapPercent: Int = 40,
    val prioritizeRoaming: Boolean = false
) {
    init {
        require(minRssi in -100..-40) {
            "Minimum RSSI must be in range [-100, -40] dBm, got $minRssi"
        }
        require(targetRssi in -100..-40) {
            "Target RSSI must be in range [-100, -40] dBm, got $targetRssi"
        }
        require(targetRssi >= minRssi) {
            "Target RSSI ($targetRssi dBm) must be >= minimum RSSI ($minRssi dBm)"
        }
        require(maxInterference in 0.0..1.0) {
            "Max interference must be in range [0.0, 1.0], got $maxInterference"
        }
        if (coverageArea != null) {
            require(coverageArea > 0) {
                "Coverage area must be positive, got $coverageArea m²"
            }
        }
        require(minOverlapPercent in 0..100) {
            "Minimum overlap must be 0-100%, got $minOverlapPercent"
        }
        require(maxOverlapPercent in 0..100) {
            "Maximum overlap must be 0-100%, got $maxOverlapPercent"
        }
        require(maxOverlapPercent >= minOverlapPercent) {
            "Max overlap ($maxOverlapPercent%) must be >= min overlap ($minOverlapPercent%)"
        }
    }

    /**
     * RSSI margin between minimum and target
     * Provides buffer for signal variation
     */
    val rssiMargin: Int
        get() = targetRssi - minRssi

    /**
     * Whether coverage area is specified (constrained optimization)
     */
    val hasCoverageConstraint: Boolean
        get() = coverageArea != null

    /**
     * Overlap tolerance range
     */
    val overlapRange: IntRange
        get() = minOverlapPercent..maxOverlapPercent

    /**
     * Whether overlap goals are strict (narrow range)
     */
    val hasStrictOverlapRequirement: Boolean
        get() = (maxOverlapPercent - minOverlapPercent) <= 15

    /**
     * Coverage quality tier based on RSSI targets
     */
    val qualityTier: CoverageQualityTier
        get() = when {
            targetRssi >= -50 -> CoverageQualityTier.PREMIUM
            targetRssi >= -60 -> CoverageQualityTier.STANDARD
            targetRssi >= -70 -> CoverageQualityTier.BASIC
            else -> CoverageQualityTier.MINIMAL
        }

    /**
     * Interference tolerance level
     */
    val interferenceTolerance: InterferenceTolerance
        get() = when {
            maxInterference <= 0.2 -> InterferenceTolerance.STRICT
            maxInterference <= 0.4 -> InterferenceTolerance.MODERATE
            else -> InterferenceTolerance.PERMISSIVE
        }

    /**
     * Get human-readable summary of coverage goals
     */
    val summary: String
        get() = buildString {
            append("RSSI: ${minRssi}/${targetRssi} dBm, ")
            append("Interference: <${(maxInterference * 100).toInt()}%, ")
            if (coverageArea != null) {
                append("Area: ${coverageArea.toInt()} m², ")
            }
            append("Overlap: $minOverlapPercent-$maxOverlapPercent%")
            if (prioritizeRoaming) {
                append(" (roaming priority)")
            }
        }

    companion object {
        /**
         * Default coverage goals for residential deployments
         * - Moderate signal strength
         * - Moderate interference tolerance
         * - Roaming-friendly overlap
         */
        fun residential(): CoverageGoals {
            return CoverageGoals(
                minRssi = -70,
                targetRssi = -60,
                maxInterference = 0.3,
                coverageArea = null,
                minOverlapPercent = 15,
                maxOverlapPercent = 35,
                prioritizeRoaming = true
            )
        }

        /**
         * Coverage goals for enterprise deployments
         * - Strong signal strength
         * - Low interference tolerance
         * - Precise overlap control for capacity
         */
        fun enterprise(): CoverageGoals {
            return CoverageGoals(
                minRssi = -67,
                targetRssi = -50,
                maxInterference = 0.2,
                coverageArea = null,
                minOverlapPercent = 10,
                maxOverlapPercent = 25,
                prioritizeRoaming = false
            )
        }

        /**
         * Coverage goals for high-density deployments
         * - Very strong signal required
         * - Very low interference tolerance
         * - Minimal overlap to maximize capacity
         */
        fun highDensity(): CoverageGoals {
            return CoverageGoals(
                minRssi = -65,
                targetRssi = -50,
                maxInterference = 0.15,
                coverageArea = null,
                minOverlapPercent = 5,
                maxOverlapPercent = 20,
                prioritizeRoaming = false
            )
        }

        /**
         * Coverage goals for outdoor/long-range deployments
         * - Weaker signal acceptable
         * - Higher interference tolerance
         * - Larger overlap for mobility
         */
        fun outdoor(): CoverageGoals {
            return CoverageGoals(
                minRssi = -75,
                targetRssi = -65,
                maxInterference = 0.5,
                coverageArea = null,
                minOverlapPercent = 20,
                maxOverlapPercent = 50,
                prioritizeRoaming = true
            )
        }
    }
}

/**
 * Coverage quality tiers based on signal strength targets
 */
enum class CoverageQualityTier(val displayName: String, val description: String) {
    /**
     * Premium coverage (-50 dBm or better)
     * - Excellent performance
     * - Maximum throughput
     * - Suitable for high-density, latency-sensitive applications
     */
    PREMIUM(
        "Premium",
        "Excellent signal strength for maximum performance"
    ),

    /**
     * Standard coverage (-60 dBm or better)
     * - Good performance
     * - High throughput
     * - Suitable for most applications
     */
    STANDARD(
        "Standard",
        "Good signal strength for reliable performance"
    ),

    /**
     * Basic coverage (-70 dBm or better)
     * - Acceptable performance
     * - Moderate throughput
     * - Suitable for basic connectivity
     */
    BASIC(
        "Basic",
        "Acceptable signal strength for basic connectivity"
    ),

    /**
     * Minimal coverage (weaker than -70 dBm)
     * - Poor performance expected
     * - Low throughput
     * - Connection stability may be an issue
     */
    MINIMAL(
        "Minimal",
        "Weak signal strength, poor performance expected"
    )
}

/**
 * Interference tolerance levels
 */
enum class InterferenceTolerance(val displayName: String, val description: String) {
    /**
     * Strict interference control (< 20%)
     * - Minimize co-channel interference
     * - Optimize for capacity and throughput
     * - May require more APs or careful placement
     */
    STRICT(
        "Strict",
        "Minimal interference tolerance for maximum capacity"
    ),

    /**
     * Moderate interference tolerance (20-40%)
     * - Balance between coverage and capacity
     * - Acceptable for most deployments
     * - Good cost-performance trade-off
     */
    MODERATE(
        "Moderate",
        "Moderate interference tolerance for balanced performance"
    ),

    /**
     * Permissive interference tolerance (> 40%)
     * - Prioritize coverage over capacity
     * - Acceptable for low-density or budget-constrained deployments
     * - May impact peak throughput
     */
    PERMISSIVE(
        "Permissive",
        "Higher interference tolerance prioritizing coverage"
    )
}
