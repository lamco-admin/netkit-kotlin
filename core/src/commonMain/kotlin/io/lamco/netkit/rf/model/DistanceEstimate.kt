package io.lamco.netkit.rf.model

/**
 * Distance estimation result with uncertainty
 *
 * Combines distance estimate with standard deviation to represent measurement uncertainty.
 * Different estimation methods have different error characteristics:
 * - **RSSI**: High variance (±5-10m), environment-dependent
 * - **RTT**: Lower variance (±1-2m), more reliable
 * - **HYBRID**: Best accuracy by combining both methods
 *
 * @property distanceMeters Estimated distance in meters
 * @property stdDevMeters Standard deviation of estimate (uncertainty)
 * @property method Source of distance estimation
 *
 * @see DistanceSource for estimation method details
 */
data class DistanceEstimate(
    val distanceMeters: Double,
    val stdDevMeters: Double,
    val method: DistanceSource,
) {
    init {
        require(distanceMeters >= 0.0) { "Distance cannot be negative: $distanceMeters" }
        require(stdDevMeters >= 0.0) { "Standard deviation cannot be negative: $stdDevMeters" }
    }

    /**
     * 95% confidence interval (±2σ) for distance estimate
     * - Lower bound: max(0, distance - 2*stdDev)
     * - Upper bound: distance + 2*stdDev
     */
    val confidenceInterval95: Pair<Double, Double>
        get() {
            val margin = 2.0 * stdDevMeters
            return Pair(
                maxOf(0.0, distanceMeters - margin),
                distanceMeters + margin,
            )
        }

    /**
     * Relative uncertainty as percentage (stdDev / distance * 100)
     */
    val relativeUncertaintyPct: Double
        get() =
            if (distanceMeters > 0.0) {
                (stdDevMeters / distanceMeters) * 100.0
            } else {
                Double.POSITIVE_INFINITY
            }

    /**
     * Quality rating of distance estimate (0.0 = poor, 1.0 = excellent)
     * Based on method accuracy and relative uncertainty
     */
    val quality: Double
        get() {
            val methodQuality = method.accuracy
            val uncertaintyPenalty = minOf(relativeUncertaintyPct / 100.0, 1.0)
            return methodQuality * (1.0 - uncertaintyPenalty)
        }

    companion object {
        /**
         * Create unknown/invalid distance estimate
         */
        fun unknown(): DistanceEstimate =
            DistanceEstimate(
                distanceMeters = 0.0,
                stdDevMeters = Double.POSITIVE_INFINITY,
                method = DistanceSource.UNKNOWN,
            )
    }
}
