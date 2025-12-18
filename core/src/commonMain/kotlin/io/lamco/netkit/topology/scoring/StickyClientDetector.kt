package io.lamco.netkit.topology.scoring

import io.lamco.netkit.model.topology.*

/**
 * Sticky client behavior detector
 *
 * Detects when a device remains connected to a weak AP despite better alternatives.
 * Tracks sticky duration, calculates performance impact, and generates recommendations.
 */
class StickyClientDetector(
    private val stickyRssiThreshold: Int = -75, // RSSI below this is "sticky-prone"
    private val betterApDifferential: Int = 10, // RSSI improvement needed to be "better"
) {
    /**
     * Detect sticky client behavior from connection history
     *
     * @param ssid Network SSID being monitored
     * @param currentBssid Currently connected BSSID
     * @param currentRssi Current RSSI
     * @param cluster AP cluster for this network
     * @return Sticky client metrics, or null if not currently sticky
     */
    fun detectCurrentSticky(
        ssid: String,
        currentBssid: String,
        currentRssi: Int,
        cluster: ApCluster,
    ): StickyClientMetrics? {
        // Check if current signal is weak
        if (currentRssi >= stickyRssiThreshold) {
            return null // Signal is fine
        }

        // Find better alternatives
        val betterAps =
            cluster.bssids
                .filter { it.bssid != currentBssid }
                .filter { (it.rssiDbm ?: -999) >= currentRssi + betterApDifferential }

        if (betterAps.isEmpty()) {
            return null // No better alternative available
        }

        // Calculate metrics
        val avgBetterRssi = betterAps.mapNotNull { it.rssiDbm }.average()

        return StickyClientMetrics(
            ssid = ssid,
            totalStickyDurationMillis = 0, // Instant detection
            stickyEventsCount = 1,
            avgStickyRssiDbm = currentRssi.toDouble(),
            avgBetterApRssiDbm = avgBetterRssi,
            worstStickyRssiDbm = currentRssi,
            currentlySticky = true,
        )
    }

    /**
     * Analyze connection history for sticky client patterns
     *
     * This would track historical sticky events over time.
     * Simplified version without full history tracking.
     */
    fun analyzeStickyPattern(
        ssid: String,
        recentStickyEvents: List<StickyEvent>,
    ): StickyClientMetrics {
        if (recentStickyEvents.isEmpty()) {
            return StickyClientMetrics(
                ssid = ssid,
                totalStickyDurationMillis = 0,
                stickyEventsCount = 0,
                avgStickyRssiDbm = 0.0,
                avgBetterApRssiDbm = 0.0,
                worstStickyRssiDbm = 0,
                currentlySticky = false,
            )
        }

        val totalDuration = recentStickyEvents.sumOf { it.durationMillis }
        val avgSticky = recentStickyEvents.map { it.stickyRssi }.average()
        val avgBetter = recentStickyEvents.map { it.betterApRssi }.average()
        val worst = recentStickyEvents.minOf { it.stickyRssi }

        return StickyClientMetrics(
            ssid = ssid,
            totalStickyDurationMillis = totalDuration,
            stickyEventsCount = recentStickyEvents.size,
            avgStickyRssiDbm = avgSticky,
            avgBetterApRssiDbm = avgBetter,
            worstStickyRssiDbm = worst,
            currentlySticky = recentStickyEvents.last().isOngoing,
        )
    }

    /**
     * Check if device should roam based on sticky detection
     *
     * @return True if device should roam now
     */
    fun shouldRoam(
        currentRssi: Int,
        currentBssid: String,
        cluster: ApCluster,
    ): Boolean {
        if (currentRssi >= stickyRssiThreshold) {
            return false // Current signal is acceptable
        }

        // Find significantly better AP
        val betterAp =
            cluster.bssids
                .filter { it.bssid != currentBssid }
                .filter { (it.rssiDbm ?: -999) >= currentRssi + betterApDifferential }
                .maxByOrNull { it.rssiDbm ?: -999 }

        return betterAp != null
    }
}

/**
 * Sticky client event (simplified tracking)
 */
data class StickyEvent(
    val timestampMillis: Long,
    val durationMillis: Long,
    val stickyRssi: Int,
    val betterApRssi: Int,
    val isOngoing: Boolean,
)
