package io.lamco.netkit.topology.scoring

import io.lamco.netkit.model.topology.*

/**
 * Roaming candidate scorer
 *
 * Scores potential roaming targets based on:
 * - RSSI differential (signal improvement)
 * - Fast roaming support (802.11k/r/v)
 * - Band preference (5/6 GHz over 2.4 GHz)
 * - Load balancing (avoid congested APs)
 */
class RoamingScorer {
    /**
     * Score all APs in cluster as roaming candidates
     *
     * @param currentBssid Current connected BSSID
     * @param cluster AP cluster
     * @param currentRssi Current RSSI in dBm
     * @return List of scored candidates, sorted by score (descending)
     */
    fun scoreRoamingCandidates(
        currentBssid: String,
        cluster: ApCluster,
        currentRssi: Int,
    ): List<RoamingCandidate> =
        cluster.bssids
            .filter { it.bssid != currentBssid } // Exclude current AP
            .map { candidate ->
                val score = calculateRoamingScore(candidate, currentRssi, cluster)
                RoamingCandidate(
                    bssid = candidate.bssid,
                    band = candidate.band,
                    channel = candidate.channel,
                    rssi = candidate.rssiDbm ?: -100,
                    rssiImprovement = (candidate.rssiDbm ?: -100) - currentRssi,
                    roamingCapabilities = candidate.roamingCapabilities,
                    score = score,
                    reason = generateReason(candidate, currentRssi),
                )
            }.sortedByDescending { it.score }

    /**
     * Find best roaming candidate
     */
    fun findBestCandidate(
        currentBssid: String,
        cluster: ApCluster,
        currentRssi: Int,
    ): RoamingCandidate? = scoreRoamingCandidates(currentBssid, cluster, currentRssi).firstOrNull()

    private fun calculateRoamingScore(
        candidate: ClusteredBss,
        currentRssi: Int,
        cluster: ApCluster,
    ): Double {
        var score = 50.0

        // RSSI improvement (most important)
        val targetRssi = candidate.rssiDbm ?: -100
        val improvement = targetRssi - currentRssi
        score += improvement * 2.0 // +2 points per dB improvement

        if (candidate.roamingCapabilities.hasFastRoaming) {
            score += 20.0
        }
        if (candidate.roamingCapabilities.hasFullSuite) {
            score += 10.0
        }

        score +=
            when (candidate.band) {
                io.lamco.netkit.model.network.WiFiBand.BAND_6GHZ -> 15.0
                io.lamco.netkit.model.network.WiFiBand.BAND_5GHZ -> 10.0
                io.lamco.netkit.model.network.WiFiBand.BAND_2_4GHZ -> 0.0
                else -> 0.0
            }

        score +=
            when (candidate.wifiStandard.generation) {
                7 -> 15.0
                6 -> 10.0
                5 -> 5.0
                else -> 0.0
            }

        if (targetRssi < -75) {
            score -= 20.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun generateReason(
        candidate: ClusteredBss,
        currentRssi: Int,
    ): String {
        val rssiDiff = (candidate.rssiDbm ?: -100) - currentRssi
        return buildString {
            if (rssiDiff > 10) {
                append("Much better signal (+${rssiDiff}dB)")
            } else if (rssiDiff > 5) {
                append("Better signal (+${rssiDiff}dB)")
            } else {
                append("Similar signal")
            }

            if (candidate.roamingCapabilities.hasFastRoaming) {
                append(", supports fast roaming")
            }

            if (candidate.band == io.lamco.netkit.model.network.WiFiBand.BAND_5GHZ ||
                candidate.band == io.lamco.netkit.model.network.WiFiBand.BAND_6GHZ
            ) {
                append(", ${candidate.band.displayName}")
            }
        }
    }
}

/**
 * Roaming candidate with score
 */
data class RoamingCandidate(
    val bssid: String,
    val band: io.lamco.netkit.model.network.WiFiBand,
    val channel: Int,
    val rssi: Int,
    val rssiImprovement: Int,
    val roamingCapabilities: RoamingCapabilities,
    val score: Double, // 0-100
    val reason: String,
)
