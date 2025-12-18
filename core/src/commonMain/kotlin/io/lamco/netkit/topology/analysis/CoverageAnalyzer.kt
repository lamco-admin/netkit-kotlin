package io.lamco.netkit.topology.analysis

import io.lamco.netkit.model.topology.*
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Coverage overlap and gap analysis
 *
 * Calculates coverage areas, overlap zones, and identifies gaps in WiFi coverage.
 * Uses RSSI-based coverage estimation and geometric overlap calculations.
 */
class CoverageAnalyzer(
    private val minRssiThreshold: Int = -80, // Minimum usable RSSI
) {
    /**
     * Calculate coverage overlap between all APs in a cluster
     *
     * Returns a map of AP pairs to their overlap percentage.
     *
     * @param cluster Cluster to analyze
     * @return Map of (BSSID1, BSSID2) to overlap percentage (0.0-1.0)
     */
    fun calculateCoverageOverlap(cluster: ApCluster): Map<Pair<String, String>, Double> {
        val overlapMap = mutableMapOf<Pair<String, String>, Double>()

        // Get all BSS pairs
        val bssList = cluster.bssids
        for (i in bssList.indices) {
            for (j in (i + 1) until bssList.size) {
                val bss1 = bssList[i]
                val bss2 = bssList[j]

                // Calculate overlap (requires RSSI or distance data)
                val overlap = estimateOverlap(bss1, bss2)
                overlapMap[Pair(bss1.bssid, bss2.bssid)] = overlap
            }
        }

        return overlapMap
    }

    /**
     * Identify coverage gaps (dead zones) in the cluster
     *
     * Returns list of identified dead zones based on RSSI coverage.
     *
     * @param cluster Cluster to analyze
     * @param minRssi Minimum acceptable RSSI (default: -80 dBm)
     * @return List of dead zone descriptors
     */
    fun identifyDeadZones(
        cluster: ApCluster,
        minRssi: Int = minRssiThreshold,
    ): List<DeadZone> {
        val deadZones = mutableListOf<DeadZone>()

        // Check if any AP has very weak signal
        val weakAps =
            cluster.bssids.filter { bss ->
                val rssi = bss.rssiDbm
                rssi != null && rssi < minRssi
            }

        if (weakAps.isNotEmpty()) {
            deadZones.add(
                DeadZone(
                    severity =
                        if (weakAps.size == cluster.apCount) {
                            DeadZoneSeverity.CRITICAL
                        } else {
                            DeadZoneSeverity.MODERATE
                        },
                    affectedBssids = weakAps.map { it.bssid },
                    estimatedRssi = weakAps.mapNotNull { it.rssiDbm }.average().toInt(),
                    description = "Weak coverage area with ${weakAps.size} AP(s) below threshold",
                ),
            )
        }

        return deadZones
    }

    /**
     * Calculate coverage quality score for a cluster
     *
     * Based on:
     * - Number of APs
     * - Signal strength distribution
     * - Coverage overlap
     * - Band diversity
     */
    fun calculateCoverageQuality(cluster: ApCluster): CoverageQualityScore {
        var score = 0

        // Multi-AP bonus (up to 30 points)
        score +=
            when (cluster.apCount) {
                1 -> 10
                2 -> 20
                3 -> 25
                else -> 30
            }

        // Band diversity bonus (up to 25 points)
        score +=
            when {
                cluster.isTriBand -> 25
                cluster.isDualBand -> 20
                else -> 10
            }

        // Signal strength bonus (up to 25 points)
        val avgRssi = cluster.averageRssiDbm
        if (avgRssi != null) {
            score +=
                when {
                    avgRssi >= -50 -> 25
                    avgRssi >= -60 -> 20
                    avgRssi >= -70 -> 15
                    avgRssi >= -80 -> 10
                    else -> 5
                }
        } else {
            score += 10 // No RSSI data
        }

        // Fast roaming bonus (up to 20 points)
        score += if (cluster.supportsFastRoaming) 20 else 0

        return CoverageQualityScore(
            score = score.coerceIn(0, 100),
            apCount = cluster.apCount,
            bandDiversity = cluster.usedBands.size,
            hasFastRoaming = cluster.supportsFastRoaming,
            averageRssi = avgRssi,
        )
    }

    /**
     * Estimate coverage radius for a BSS based on RSSI
     *
     * Uses simplified path loss model to estimate coverage area.
     */
    fun estimateCoverageRadius(bss: ClusteredBss): Double {
        val rssi = bss.rssiDbm ?: return 50.0 // Default 50m if no RSSI

        // Simple path loss model: range decreases with weaker signal
        // This is a simplified estimation
        return when {
            rssi >= -50 -> 100.0 // Excellent: ~100m
            rssi >= -60 -> 75.0 // Very good: ~75m
            rssi >= -70 -> 50.0 // Good: ~50m
            rssi >= -80 -> 25.0 // Fair: ~25m
            else -> 10.0 // Poor: ~10m
        }
    }

    /**
     * Estimate overlap percentage between two BSSs
     *
     * Uses geometric circle intersection if distances are known,
     * otherwise estimates based on RSSI and band.
     */
    private fun estimateOverlap(
        bss1: ClusteredBss,
        bss2: ClusteredBss,
    ): Double {
        // Same band increases overlap probability
        if (bss1.band != bss2.band) return 0.0

        val radius1 = estimateCoverageRadius(bss1)
        val radius2 = estimateCoverageRadius(bss2)

        // Without actual distance, estimate based on signal strength correlation
        val rssi1 = bss1.rssiDbm ?: -70
        val rssi2 = bss2.rssiDbm ?: -70

        // If both signals are strong, assume good overlap
        return when {
            rssi1 >= -60 && rssi2 >= -60 -> 0.7 // 70% overlap
            rssi1 >= -70 && rssi2 >= -70 -> 0.5 // 50% overlap
            rssi1 >= -80 && rssi2 >= -80 -> 0.3 // 30% overlap
            else -> 0.1 // 10% overlap
        }
    }

    /**
     * Calculate geometric overlap of two circles (coverage areas)
     *
     * @param r1 Radius of first circle
     * @param r2 Radius of second circle
     * @param d Distance between centers
     * @return Overlap area as percentage of smaller circle
     */
    private fun calculateCircleOverlap(
        r1: Double,
        r2: Double,
        d: Double,
    ): Double {
        // No overlap if circles don't intersect
        if (d >= r1 + r2) return 0.0

        // Complete overlap if one circle contains the other
        if (d <= kotlin.math.abs(r1 - r2)) {
            return 1.0
        }

        // Partial overlap - calculate intersection area
        val area1 = r1 * r1 * kotlin.math.acos((d * d + r1 * r1 - r2 * r2) / (2 * d * r1))
        val area2 = r2 * r2 * kotlin.math.acos((d * d + r2 * r2 - r1 * r1) / (2 * d * r2))
        val area3 = 0.5 * sqrt((-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2))

        val overlapArea = area1 + area2 - area3
        val smallerArea = PI * kotlin.math.min(r1, r2).pow(2)

        return (overlapArea / smallerArea).coerceIn(0.0, 1.0)
    }
}

/**
 * Dead zone descriptor
 */
data class DeadZone(
    val severity: DeadZoneSeverity,
    val affectedBssids: List<String>,
    val estimatedRssi: Int,
    val description: String,
)

/**
 * Dead zone severity levels
 */
enum class DeadZoneSeverity {
    /** Minor coverage gap */
    MINOR,

    /** Moderate coverage issues */
    MODERATE,

    /** Critical coverage failure */
    CRITICAL,
}

/**
 * Coverage quality score result
 */
data class CoverageQualityScore(
    val score: Int, // 0-100
    val apCount: Int,
    val bandDiversity: Int,
    val hasFastRoaming: Boolean,
    val averageRssi: Double?,
) {
    val quality: CoverageQuality
        get() =
            when {
                score >= 80 -> CoverageQuality.EXCELLENT
                score >= 60 -> CoverageQuality.GOOD
                score >= 40 -> CoverageQuality.FAIR
                else -> CoverageQuality.POOR
            }
}

/**
 * Coverage quality categories
 */
enum class CoverageQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}
