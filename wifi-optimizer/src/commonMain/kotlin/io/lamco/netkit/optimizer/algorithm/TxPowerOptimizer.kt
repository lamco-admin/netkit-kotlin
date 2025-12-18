package io.lamco.netkit.optimizer.algorithm

import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.topology.ApCluster
import io.lamco.netkit.optimizer.model.CoverageGoals
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * TX power optimizer for coverage and interference balance
 *
 * Optimizes transmit power levels for WiFi access points to achieve coverage
 * goals while minimizing interference. Uses RF propagation models to estimate
 * coverage area and interference zones.
 *
 * Key principles:
 * - Higher power = More coverage but also more interference
 * - Lower power = Less interference but coverage gaps possible
 * - Optimal power depends on: environment, AP density, coverage goals
 *
 * Implements algorithms based on:
 * - Free-space path loss (FSPL) model
 * - Log-distance path loss model for indoor environments
 * - Cell planning techniques from cellular network optimization
 *
 * References:
 * - ITU-R P.1238: Indoor propagation models
 * - 3GPP TS 36.942: Cell planning guidelines
 */
class TxPowerOptimizer {
    /**
     * Optimize TX power for multi-AP network
     *
     * Analyzes the network topology and coverage requirements to determine
     * optimal transmit power for each AP. Balances coverage goals with
     * interference minimization.
     *
     * Algorithm:
     * 1. Calculate power needed for each AP to achieve coverage goals
     * 2. Estimate interference at those power levels
     * 3. If interference exceeds threshold, reduce power while maintaining minimum coverage
     * 4. For multi-AP: coordinate power levels to minimize overlap interference
     *
     * @param apClusters AP clusters to optimize
     * @param coverageGoals Target coverage characteristics
     * @param currentPower Current TX power per BSSID (dBm)
     * @return Optimization result with per-AP recommendations
     */
    fun optimizePower(
        apClusters: List<ApCluster>,
        coverageGoals: CoverageGoals,
        currentPower: Map<String, Int>,
    ): PowerOptimizationResult {
        require(apClusters.isNotEmpty()) {
            "Cannot optimize power for empty AP cluster list"
        }

        val recommendations = mutableMapOf<String, PowerRecommendation>()
        var totalCoverageChange = 0.0
        var totalInterferenceChange = 0.0
        var apCount = 0

        for (cluster in apClusters) {
            for (bss in cluster.bssids) {
                val current = currentPower[bss.bssid] ?: estimateCurrentPower(bss.rssiDbm)

                val recommendation =
                    optimizePowerForAp(
                        bssid = bss.bssid,
                        band = bss.band,
                        currentPower = current,
                        coverageGoals = coverageGoals,
                        neighboringAps = cluster.bssids.filter { it.bssid != bss.bssid },
                    )

                recommendations[bss.bssid] = recommendation

                // Accumulate changes for overall metrics
                if (recommendation.change != 0) {
                    totalCoverageChange += estimateCoverageChange(recommendation.change)
                    totalInterferenceChange += estimateInterferenceChange(recommendation.change)
                    apCount++
                }
            }
        }

        // Calculate average changes
        val avgCoverageChange = if (apCount > 0) totalCoverageChange / apCount else 0.0
        val avgInterferenceChange = if (apCount > 0) totalInterferenceChange / apCount else 0.0

        // Estimate energy savings (if reducing power)
        val energySavings = calculateEnergySavings(recommendations)

        return PowerOptimizationResult(
            recommendations = recommendations,
            expectedCoverageChange = avgCoverageChange,
            expectedInterferenceChange = avgInterferenceChange,
            energySavings = energySavings,
        )
    }

    /**
     * Assess current power level appropriateness
     *
     * Evaluates whether the current TX power is appropriate for the
     * coverage area and interference environment.
     *
     * @param bssid AP BSSID to assess
     * @param currentPower Current TX power (dBm)
     * @param coverage Current coverage metrics
     * @return Power assessment with recommendations
     */
    fun assessCurrentPower(
        bssid: String,
        currentPower: Int,
        coverage: CoverageMetrics,
    ): PowerAssessment {
        require(currentPower in 0..30) {
            "TX power must be 0-30 dBm, got $currentPower"
        }

        // Assess if power is appropriate for coverage
        val coverageStatus =
            when {
                coverage.minRssi < -75 -> PowerStatus.TOO_LOW
                coverage.minRssi > -55 -> PowerStatus.TOO_HIGH
                else -> PowerStatus.OPTIMAL
            }

        // Assess if power causes excessive interference
        val interferenceStatus =
            when {
                coverage.interferenceLevel > 0.4 && currentPower > 15 -> PowerStatus.TOO_HIGH
                coverage.interferenceLevel < 0.1 && currentPower < 20 -> PowerStatus.TOO_LOW
                else -> PowerStatus.OPTIMAL
            }

        // Overall assessment
        val overallStatus =
            when {
                coverageStatus == PowerStatus.TOO_LOW -> PowerStatus.TOO_LOW
                interferenceStatus == PowerStatus.TOO_HIGH -> PowerStatus.TOO_HIGH
                coverageStatus == PowerStatus.OPTIMAL && interferenceStatus == PowerStatus.OPTIMAL -> PowerStatus.OPTIMAL
                else -> PowerStatus.SUBOPTIMAL
            }

        val recommendation =
            buildPowerAssessmentRecommendation(
                overallStatus,
                currentPower,
                coverage,
            )

        return PowerAssessment(
            bssid = bssid,
            currentPower = currentPower,
            status = overallStatus,
            coverageStatus = coverageStatus,
            interferenceStatus = interferenceStatus,
            recommendation = recommendation,
        )
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Optimize power for a single AP
     */
    private fun optimizePowerForAp(
        bssid: String,
        band: WiFiBand,
        currentPower: Int,
        coverageGoals: CoverageGoals,
        neighboringAps: List<io.lamco.netkit.model.topology.ClusteredBss>,
    ): PowerRecommendation {
        // Calculate power needed for target coverage
        val powerForCoverage =
            calculatePowerForCoverage(
                band = band,
                targetRssi = coverageGoals.targetRssi,
                coverageArea = coverageGoals.coverageArea,
            )

        // Calculate interference at that power level
        val interferenceAtTargetPower =
            estimateInterferenceAtPower(
                powerDbm = powerForCoverage,
                band = band,
                neighborCount = neighboringAps.size,
            )

        // Adjust power if interference exceeds threshold
        val recommendedPower =
            if (interferenceAtTargetPower > coverageGoals.maxInterference) {
                // Reduce power to meet interference constraint while maintaining minimum coverage
                reducePowerForInterference(
                    initialPower = powerForCoverage,
                    band = band,
                    minRssi = coverageGoals.minRssi,
                    maxInterference = coverageGoals.maxInterference,
                    neighborCount = neighboringAps.size,
                )
            } else {
                powerForCoverage
            }.coerceIn(0, 30) // Legal power limits

        val change = recommendedPower - currentPower

        return PowerRecommendation(
            bssid = bssid,
            currentPower = currentPower,
            recommendedPower = recommendedPower,
            change = change,
            rationale =
                buildPowerRationale(
                    change = change,
                    coverageGoals = coverageGoals,
                    interference = interferenceAtTargetPower,
                ),
            impact =
                PowerChangeImpact(
                    coverageChange = formatCoverageChange(change),
                    interferenceChange = formatInterferenceChange(change),
                    clientsAffected = estimateAffectedClients(change),
                ),
        )
    }

    /**
     * Calculate TX power needed to achieve target RSSI at coverage edge
     *
     * Uses path loss model:
     * RSSI = TxPower - PathLoss
     * PathLoss = FSPL + ObstacleLoss
     * FSPL = 20*log10(distance) + 20*log10(frequency) + 20*log10(4π/c)
     *
     * For indoor:
     * PathLoss = 40 + 20*log10(distance) + N*log10(distance)  (N = path loss exponent)
     */
    private fun calculatePowerForCoverage(
        band: WiFiBand,
        targetRssi: Int,
        coverageArea: Double?,
    ): Int {
        // Estimate range from coverage area (assume circular coverage)
        val targetRange =
            if (coverageArea != null) {
                sqrt(coverageArea / Math.PI)
            } else {
                // Use typical range for band
                when (band) {
                    WiFiBand.BAND_2_4GHZ -> 50.0 // ~50m indoor
                    WiFiBand.BAND_5GHZ -> 30.0 // ~30m indoor
                    WiFiBand.BAND_6GHZ -> 20.0 // ~20m indoor
                    else -> 30.0
                }
            }

        // Calculate path loss at target range
        val pathLoss =
            calculateIndoorPathLoss(
                distanceMeters = targetRange,
                band = band,
            )

        // Required TX power = Target RSSI + Path Loss
        val requiredPower = targetRssi + pathLoss

        return requiredPower.toInt().coerceIn(10, 25) // Typical range
    }

    /**
     * Calculate indoor path loss using log-distance model
     *
     * PL(d) = PL(d0) + 10*n*log10(d/d0) + Xσ
     * Where:
     * - d0 = 1m (reference distance)
     * - n = path loss exponent (2.0 free space, 2.5-3.5 indoor)
     * - Xσ = shadow fading (ignored for planning)
     */
    private fun calculateIndoorPathLoss(
        distanceMeters: Double,
        band: WiFiBand,
    ): Double {
        val frequency =
            when (band) {
                WiFiBand.BAND_2_4GHZ -> 2437 // MHz (channel 6)
                WiFiBand.BAND_5GHZ -> 5180 // MHz (channel 36)
                WiFiBand.BAND_6GHZ -> 5955 // MHz (channel 1)
                else -> 2437
            }

        // Free space path loss at 1m
        val fspl1m = 20 * log10(frequency.toDouble()) + 20 * log10(4 * Math.PI / 299.792458) - 27.55

        // Path loss exponent (higher for higher frequencies, more obstacles)
        val pathLossExponent =
            when (band) {
                WiFiBand.BAND_2_4GHZ -> 2.8
                WiFiBand.BAND_5GHZ -> 3.2
                WiFiBand.BAND_6GHZ -> 3.5
                else -> 3.0
            }

        // Log-distance path loss
        val pathLoss = fspl1m + 10 * pathLossExponent * log10(distanceMeters.coerceAtLeast(1.0))

        return pathLoss
    }

    /**
     * Estimate interference level at given power
     *
     * Simplified model: interference increases with TX power and neighbor count
     */
    private fun estimateInterferenceAtPower(
        powerDbm: Int,
        band: WiFiBand,
        neighborCount: Int,
    ): Double {
        // Base interference from TX power (normalized to 20 dBm)
        val powerFactor = (powerDbm - 20) / 10.0 // -1 to +1 for 10-30 dBm

        // Neighbor interference (more neighbors = more interference)
        val neighborFactor = neighborCount / 10.0 // Normalize to typical 10 neighbors

        // Band factor (2.4 GHz has more ambient interference)
        val bandFactor =
            when (band) {
                WiFiBand.BAND_2_4GHZ -> 1.5
                WiFiBand.BAND_5GHZ -> 1.0
                WiFiBand.BAND_6GHZ -> 0.7
                else -> 1.0
            }

        // Combined interference (0.0 - 1.0 scale)
        val interference = (0.2 + powerFactor * 0.3 + neighborFactor * 0.3) * bandFactor

        return interference.coerceIn(0.0, 1.0)
    }

    /**
     * Reduce power to meet interference constraint
     */
    private fun reducePowerForInterference(
        initialPower: Int,
        band: WiFiBand,
        minRssi: Int,
        maxInterference: Double,
        neighborCount: Int,
    ): Int {
        var testPower = initialPower

        // Iteratively reduce power until interference is acceptable
        while (testPower > 10) { // Minimum 10 dBm
            val interference = estimateInterferenceAtPower(testPower, band, neighborCount)

            if (interference <= maxInterference) {
                break
            }

            testPower -= 3 // Reduce by 3 dB steps
        }

        // Ensure we still meet minimum coverage
        val minPowerForCoverage =
            calculatePowerForCoverage(
                band = band,
                targetRssi = minRssi,
                coverageArea = null, // Use default range
            )

        return testPower.coerceAtLeast(minPowerForCoverage)
    }

    /**
     * Estimate current TX power from RSSI (rough estimate)
     */
    private fun estimateCurrentPower(rssiDbm: Int?): Int {
        // Typical: RSSI = TxPower - PathLoss
        // Assume 40-60 dB path loss for typical scenarios
        return when {
            rssiDbm == null -> 20 // Default
            rssiDbm > -40 -> 15 // Close/strong signal
            rssiDbm > -60 -> 20 // Medium signal
            else -> 25 // Weak signal (higher power)
        }
    }

    private fun estimateCoverageChange(powerChange: Int): Double {
        // Rule of thumb: +3 dB = +40% range, -3 dB = -30% range
        // Area scales with range²
        val rangeChange =
            when {
                powerChange >= 3 -> 40.0
                powerChange <= -3 -> -30.0
                else -> powerChange * 13.0 // Linear approximation
            }

        // Area change (approximately range² for small changes)
        return rangeChange * (1 + abs(rangeChange) / 100.0)
    }

    private fun estimateInterferenceChange(powerChange: Int): Double {
        // Interference increases/decreases roughly linearly with power in dB
        return powerChange * 10.0 // Percentage change
    }

    private fun calculateEnergySavings(recommendations: Map<String, PowerRecommendation>): Double? {
        val powerReductions =
            recommendations.values
                .map { it.change }
                .filter { it < 0 }

        if (powerReductions.isEmpty()) return null

        // Rough estimate: each 3 dB reduction = 50% power savings
        // Average AP consumes ~10W, transmit portion ~5W
        val avgReduction = powerReductions.average()
        val savingsPercent = (abs(avgReduction) / 3.0) * 50.0
        val wattsPerAp = 5.0 * (savingsPercent / 100.0)

        return wattsPerAp * powerReductions.size
    }

    private fun buildPowerRationale(
        change: Int,
        coverageGoals: CoverageGoals,
        interference: Double,
    ): String =
        when {
            change > 0 -> "Increase power by ${change}dB to achieve target coverage (${coverageGoals.targetRssi}dBm)"
            change < 0 -> {
                val currentPct = (interference * 100).toInt()
                val targetPct = (coverageGoals.maxInterference * 100).toInt()
                "Reduce power by ${abs(change)}dB to minimize interference " +
                    "(current: $currentPct%, target: <$targetPct%)"
            }
            else -> "Current power is optimal for coverage and interference goals"
        }

    private fun formatCoverageChange(powerChange: Int): String {
        val change = estimateCoverageChange(powerChange)
        return when {
            change > 5 -> "+${change.toInt()}% coverage"
            change < -5 -> "${change.toInt()}% coverage"
            else -> "~0% coverage change"
        }
    }

    private fun formatInterferenceChange(powerChange: Int): String {
        val change = estimateInterferenceChange(powerChange)
        return when {
            change > 5 -> "+${change.toInt()}% interference"
            change < -5 -> "${change.toInt()}% interference"
            else -> "~0% interference change"
        }
    }

    private fun estimateAffectedClients(powerChange: Int): Int? {
        // Estimate based on coverage change
        val coverageChange = estimateCoverageChange(powerChange)
        return if (abs(coverageChange) > 10) {
            // Rough estimate: assume 5 clients per AP, scale by coverage change
            (5 * abs(coverageChange) / 100.0).toInt()
        } else {
            null
        }
    }

    private fun buildPowerAssessmentRecommendation(
        status: PowerStatus,
        currentPower: Int,
        coverage: CoverageMetrics,
    ): String =
        when (status) {
            PowerStatus.TOO_LOW ->
                "Increase power from ${currentPower}dBm to improve coverage " +
                    "(current min RSSI: ${coverage.minRssi}dBm)"
            PowerStatus.TOO_HIGH -> {
                val interferencePct = (coverage.interferenceLevel * 100).toInt()
                "Reduce power from ${currentPower}dBm to minimize interference " +
                    "(current level: $interferencePct%)"
            }
            PowerStatus.OPTIMAL -> "Current power (${currentPower}dBm) is optimal"
            PowerStatus.SUBOPTIMAL -> "Power could be optimized for better coverage/interference balance"
        }
}

// ========================================
// Data Classes
// ========================================

/**
 * Power optimization result for network
 */
data class PowerOptimizationResult(
    val recommendations: Map<String, PowerRecommendation>,
    val expectedCoverageChange: Double, // Percentage
    val expectedInterferenceChange: Double, // Percentage
    val energySavings: Double?, // Watts saved
) {
    val apCount: Int get() = recommendations.size
    val hasChanges: Boolean get() = recommendations.values.any { it.change != 0 }
}

/**
 * Power recommendation for single AP
 */
data class PowerRecommendation(
    val bssid: String,
    val currentPower: Int,
    val recommendedPower: Int,
    val change: Int,
    val rationale: String,
    val impact: PowerChangeImpact,
) {
    val shouldChange: Boolean get() = change != 0
}

/**
 * Impact of power change
 */
data class PowerChangeImpact(
    val coverageChange: String,
    val interferenceChange: String,
    val clientsAffected: Int?,
)

/**
 * Coverage metrics for power assessment
 */
data class CoverageMetrics(
    val minRssi: Int,
    val averageRssi: Int,
    val maxRssi: Int,
    val interferenceLevel: Double, // 0.0-1.0
    val clientCount: Int = 0,
)

/**
 * Power level assessment
 */
data class PowerAssessment(
    val bssid: String,
    val currentPower: Int,
    val status: PowerStatus,
    val coverageStatus: PowerStatus,
    val interferenceStatus: PowerStatus,
    val recommendation: String,
)

/**
 * Power status evaluation
 */
enum class PowerStatus {
    TOO_LOW,
    OPTIMAL,
    TOO_HIGH,
    SUBOPTIMAL,
}
