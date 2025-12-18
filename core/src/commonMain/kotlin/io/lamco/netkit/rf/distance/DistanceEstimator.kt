package io.lamco.netkit.rf.distance

import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.rf.model.DistanceEstimate
import kotlin.math.pow

/**
 * Interface for distance estimation methods
 *
 * Different methods provide different trade-offs:
 * - **RSSI-based**: Always available, high variance (±5-10m), environment-dependent
 * - **RTT-based**: Requires 802.11mc FTM support, low variance (±1-2m), API 28+
 * - **Hybrid**: Combines both using Kalman filter, best accuracy
 *
 * Distance estimation enables:
 * - AP proximity detection
 * - Indoor positioning (when combined with multiple APs)
 * - Path loss model calibration
 * - Coverage analysis
 *
 * @see RssiDistanceEstimator for path loss model-based estimation
 * @see RttDistanceEstimator for round-trip-time based estimation
 * @see HybridDistanceEstimator for fused RSSI+RTT estimation
 */
interface DistanceEstimator {
    /**
     * Estimate distance to AP
     *
     * @param rssiDbm Received signal strength in dBm
     * @param band WiFi frequency band
     * @param rttDistanceMeters Optional RTT-measured distance (if available)
     * @param rttStdDevMeters Optional RTT standard deviation (if available)
     * @return Distance estimate with uncertainty
     */
    fun estimateDistance(
        rssiDbm: Int,
        band: WiFiBand,
        rttDistanceMeters: Double? = null,
        rttStdDevMeters: Double? = null,
    ): DistanceEstimate

    /**
     * Estimate distance with frequency-specific parameters
     *
     * @param rssiDbm Received signal strength in dBm
     * @param freqMHz Center frequency in MHz
     * @param band WiFi frequency band
     * @param rttDistanceMeters Optional RTT-measured distance
     * @param rttStdDevMeters Optional RTT standard deviation
     * @return Distance estimate with uncertainty
     */
    fun estimateDistance(
        rssiDbm: Int,
        freqMHz: Int,
        band: WiFiBand,
        rttDistanceMeters: Double? = null,
        rttStdDevMeters: Double? = null,
    ): DistanceEstimate = estimateDistance(rssiDbm, band, rttDistanceMeters, rttStdDevMeters)
}

/**
 * Path loss model for RSSI-based distance estimation
 *
 * Represents the log-distance path loss model:
 * ```
 * PL(d) = PL(d0) + 10 * n * log10(d / d0) + X_σ
 * ```
 *
 * Where:
 * - PL(d) = Path loss at distance d
 * - PL(d0) = Path loss at reference distance d0 (typically 1m)
 * - n = Path loss exponent (2.0-4.5 depending on environment)
 * - X_σ = Gaussian random variable (shadowing, std dev σ)
 *
 * Solving for distance:
 * ```
 * d = d0 * 10^((PL(d) - PL(d0)) / (10 * n))
 * ```
 *
 * @property referenceDistanceMeters Reference distance d0 (typically 1.0m)
 * @property referenceLossDb Path loss at reference distance PL(d0)
 * @property pathLossExponent Path loss exponent n (environment-dependent)
 * @property shadowingStdDevDb Standard deviation of shadowing σ
 * @property txPowerDbm Assumed transmit power of AP (default: 20 dBm)
 */
data class PathLossModel(
    val referenceDistanceMeters: Double = 1.0,
    val referenceLossDb: Double = 40.0,
    val pathLossExponent: Double = 3.0,
    val shadowingStdDevDb: Double = 5.0,
    val txPowerDbm: Double = 20.0,
) {
    init {
        require(referenceDistanceMeters > 0.0) { "Reference distance must be positive" }
        require(referenceLossDb >= 0.0) { "Reference loss cannot be negative" }
        require(pathLossExponent in 2.0..5.0) { "Path loss exponent must be 2.0-5.0" }
        require(shadowingStdDevDb >= 0.0) { "Shadowing std dev cannot be negative" }
        require(txPowerDbm in -10.0..30.0) { "TX power must be reasonable (-10 to 30 dBm)" }
    }

    /**
     * Calculate distance from RSSI using path loss model
     *
     * @param rssiDbm Received signal strength
     * @return Estimated distance in meters
     */
    fun distanceFromRssi(rssiDbm: Int): Double {
        // Path loss: PL = TX_power - RSSI
        val pathLoss = txPowerDbm - rssiDbm

        // Distance from path loss model
        val exponent = (pathLoss - referenceLossDb) / (10.0 * pathLossExponent)
        val distance = referenceDistanceMeters * 10.0.pow(exponent)

        return distance.coerceIn(0.1, 1000.0) // Clamp to reasonable range
    }

    /**
     * Calculate standard deviation of distance estimate
     *
     * Accounts for shadowing variance propagated through log calculation
     *
     * @param estimatedDistanceMeters Estimated distance
     * @return Standard deviation in meters
     */
    fun distanceStdDev(estimatedDistanceMeters: Double): Double {
        // σ_d ≈ d * ln(10) * σ_dB / (10 * n)
        val stdDev =
            estimatedDistanceMeters *
                kotlin.math.ln(10.0) *
                shadowingStdDevDb /
                (10.0 * pathLossExponent)

        return stdDev.coerceAtLeast(0.5) // Minimum 0.5m uncertainty
    }

    companion object {
        /**
         * Free space path loss model (ideal, no obstacles)
         * - Path loss exponent: 2.0 (inverse square law)
         * - Low shadowing: 2 dB
         * - Use for: outdoor line-of-sight
         */
        fun freeSpace(
            band: WiFiBand,
            txPowerDbm: Double = 20.0,
        ): PathLossModel {
            val refLoss =
                when (band) {
                    WiFiBand.BAND_2_4GHZ -> 40.0 // 2.4 GHz at 1m
                    WiFiBand.BAND_5GHZ -> 46.5 // 5 GHz at 1m (higher frequency = more loss)
                    WiFiBand.BAND_6GHZ -> 48.0 // 6 GHz at 1m
                    WiFiBand.UNKNOWN -> 46.5 // Default if unknown
                }
            return PathLossModel(
                referenceLossDb = refLoss,
                pathLossExponent = 2.0,
                shadowingStdDevDb = 2.0,
                txPowerDbm = txPowerDbm,
            )
        }

        /**
         * Indoor residential/office model (typical home/office)
         * - Path loss exponent: 3.0
         * - Moderate shadowing: 5 dB
         * - Use for: apartments, offices, light construction
         */
        fun indoor(
            band: WiFiBand,
            txPowerDbm: Double = 20.0,
        ): PathLossModel {
            val refLoss =
                when (band) {
                    WiFiBand.BAND_2_4GHZ -> 40.0
                    WiFiBand.BAND_5GHZ -> 46.5
                    WiFiBand.BAND_6GHZ -> 48.0
                    WiFiBand.UNKNOWN -> 46.5
                }
            return PathLossModel(
                referenceLossDb = refLoss,
                pathLossExponent = 3.0,
                shadowingStdDevDb = 5.0,
                txPowerDbm = txPowerDbm,
            )
        }

        /**
         * Indoor heavy obstruction model (dense walls, metal)
         * - Path loss exponent: 4.0
         * - High shadowing: 8 dB
         * - Use for: warehouses, factories, concrete buildings
         */
        fun indoorHeavy(
            band: WiFiBand,
            txPowerDbm: Double = 20.0,
        ): PathLossModel {
            val refLoss =
                when (band) {
                    WiFiBand.BAND_2_4GHZ -> 40.0
                    WiFiBand.BAND_5GHZ -> 46.5
                    WiFiBand.BAND_6GHZ -> 48.0
                    WiFiBand.UNKNOWN -> 46.5
                }
            return PathLossModel(
                referenceLossDb = refLoss,
                pathLossExponent = 4.0,
                shadowingStdDevDb = 8.0,
                txPowerDbm = txPowerDbm,
            )
        }
    }
}
