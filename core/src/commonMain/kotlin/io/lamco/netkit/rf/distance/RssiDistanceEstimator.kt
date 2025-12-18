package io.lamco.netkit.rf.distance

import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.rf.model.DistanceEstimate
import io.lamco.netkit.rf.model.DistanceSource

/**
 * RSSI-based distance estimator using path loss models
 *
 * Estimates distance from received signal strength (RSSI) using the log-distance
 * path loss propagation model. This method is always available with WiFi scans
 * but has moderate accuracy due to:
 * - Environmental variability (walls, furniture, people)
 * - Multipath propagation
 * - Interference and fading
 * - Unknown AP transmit power
 *
 * **Accuracy:**
 * - Typical error: ±5-10 meters
 * - Best case (line-of-sight): ±3 meters
 * - Worst case (heavy obstruction): ±15 meters
 *
 * **Advantages:**
 * - Always available (every WiFi scan provides RSSI)
 * - No special hardware or API requirements
 * - Works on all Android versions
 * - Simple and fast
 *
 * **Limitations:**
 * - High variance (environment-dependent)
 * - Assumes transmit power (may be incorrect)
 * - Affected by obstacles and multipath
 * - Cannot distinguish direct path from reflections
 *
 * **Usage:**
 * ```kotlin
 * val estimator = RssiDistanceEstimator.indoorResidential()
 * val distance = estimator.estimateDistance(rssiDbm = -65, band = WiFiBand.BAND_5GHZ)
 * println("Distance: ${distance.distanceMeters}m ± ${distance.stdDevMeters}m")
 * ```
 *
 * @property pathLossModel Path loss model for distance calculation
 */
class RssiDistanceEstimator(
    private val pathLossModel: PathLossModel,
) : DistanceEstimator {
    override fun estimateDistance(
        rssiDbm: Int,
        band: WiFiBand,
        rttDistanceMeters: Double?,
        rttStdDevMeters: Double?,
    ): DistanceEstimate {
        require(rssiDbm in -120..0) { "RSSI must be between -120 and 0 dBm: $rssiDbm" }

        // Calculate distance from RSSI using path loss model
        val distance = pathLossModel.distanceFromRssi(rssiDbm)

        // Calculate uncertainty (standard deviation)
        val stdDev = pathLossModel.distanceStdDev(distance)

        return DistanceEstimate(
            distanceMeters = distance,
            stdDevMeters = stdDev,
            method = DistanceSource.RSSI_ONLY,
        )
    }

    override fun estimateDistance(
        rssiDbm: Int,
        freqMHz: Int,
        band: WiFiBand,
        rttDistanceMeters: Double?,
        rttStdDevMeters: Double?,
    ): DistanceEstimate {
        // For RSSI-only, frequency doesn't significantly change the model
        // (path loss exponent is more important than precise frequency)
        return estimateDistance(rssiDbm, band, rttDistanceMeters, rttStdDevMeters)
    }

    companion object {
        /**
         * Create estimator for typical indoor residential/office environment
         *
         * Assumes:
         * - Path loss exponent: 3.0 (typical indoor)
         * - Shadowing: 5 dB std dev
         * - TX power: 20 dBm (typical consumer router)
         */
        fun indoorResidential(txPowerDbm: Double = 20.0): RssiDistanceEstimator =
            RssiDistanceEstimator(PathLossModel.indoor(WiFiBand.BAND_5GHZ, txPowerDbm))

        /**
         * Create estimator for outdoor/line-of-sight environment
         *
         * Assumes:
         * - Path loss exponent: 2.0 (free space)
         * - Shadowing: 2 dB std dev (minimal obstruction)
         * - TX power: 20 dBm
         */
        fun outdoor(txPowerDbm: Double = 20.0): RssiDistanceEstimator =
            RssiDistanceEstimator(PathLossModel.freeSpace(WiFiBand.BAND_5GHZ, txPowerDbm))

        /**
         * Create estimator for heavily obstructed indoor environment
         *
         * Assumes:
         * - Path loss exponent: 4.0 (dense walls, metal)
         * - Shadowing: 8 dB std dev (high variance)
         * - TX power: 20 dBm
         */
        fun indoorHeavy(txPowerDbm: Double = 20.0): RssiDistanceEstimator =
            RssiDistanceEstimator(PathLossModel.indoorHeavy(WiFiBand.BAND_5GHZ, txPowerDbm))

        /**
         * Create estimator with custom path loss model
         */
        fun custom(model: PathLossModel): RssiDistanceEstimator = RssiDistanceEstimator(model)
    }
}

/**
 * RTT-based distance estimator using WiFi Round-Trip Time (802.11mc FTM)
 *
 * Uses IEEE 802.11mc Fine Timing Measurement (FTM) protocol to measure
 * round-trip time of packets, providing accurate distance measurement.
 *
 * **Accuracy:**
 * - Typical error: ±1-2 meters
 * - Best case: ±0.5 meters
 * - Does not depend on signal strength
 *
 * **Advantages:**
 * - Much more accurate than RSSI
 * - Not affected by obstacles (measures time-of-flight)
 * - Known transmit power not required
 * - More reliable in dense environments
 *
 * **Limitations:**
 * - Requires Android 9+ (API 28)
 * - Requires AP support for 802.11mc FTM
 * - Not all APs support this feature
 * - Higher power consumption (active measurement)
 *
 * **Usage:**
 * ```kotlin
 * val estimator = RttDistanceEstimator()
 * val distance = estimator.estimateDistance(
 *     rssiDbm = -65, // Not used for RTT, but required for fallback
 *     band = WiFiBand.BAND_5GHZ,
 *     rttDistanceMeters = 12.5,
 *     rttStdDevMeters = 1.2
 * )
 * ```
 */
class RttDistanceEstimator : DistanceEstimator {
    override fun estimateDistance(
        rssiDbm: Int,
        band: WiFiBand,
        rttDistanceMeters: Double?,
        rttStdDevMeters: Double?,
    ): DistanceEstimate {
        // If RTT data not available, return unknown
        if (rttDistanceMeters == null || rttStdDevMeters == null) {
            return DistanceEstimate.unknown()
        }

        require(rttDistanceMeters >= 0.0) { "RTT distance cannot be negative: $rttDistanceMeters" }
        require(rttStdDevMeters >= 0.0) { "RTT std dev cannot be negative: $rttStdDevMeters" }

        return DistanceEstimate(
            distanceMeters = rttDistanceMeters,
            stdDevMeters = rttStdDevMeters,
            method = DistanceSource.RTT_ONLY,
        )
    }
}
