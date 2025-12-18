package io.lamco.netkit.model.topology

/**
 * Sticky client detection metrics
 *
 * "Sticky client" occurs when a device remains connected to an access point
 * despite a better AP being available. This causes:
 * - Poor performance (low signal strength)
 * - Reduced throughput
 * - Connection instability
 * - Inefficient roaming behavior
 *
 * Common causes:
 * - Client device roaming algorithm too conservative
 * - Lack of 802.11k/r/v support
 * - AP transmit power too high (appears strong from distance)
 * - Client driver issues
 *
 * @property ssid Network SSID being analyzed
 * @property totalStickyDurationMillis Total time spent in sticky state
 * @property stickyEventsCount Number of distinct sticky client events
 * @property avgStickyRssiDbm Average RSSI during sticky periods
 * @property avgBetterApRssiDbm Average RSSI of better AP that should have been used
 * @property worstStickyRssiDbm Worst RSSI observed during sticky period
 * @property currentlySticky Whether device is currently in sticky state
 */
data class StickyClientMetrics(
    val ssid: String,
    val totalStickyDurationMillis: Long,
    val stickyEventsCount: Int,
    val avgStickyRssiDbm: Double,
    val avgBetterApRssiDbm: Double,
    val worstStickyRssiDbm: Int,
    val currentlySticky: Boolean = false,
) {
    init {
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
        require(totalStickyDurationMillis >= 0) {
            "Total sticky duration must be non-negative, got $totalStickyDurationMillis"
        }
        require(stickyEventsCount >= 0) {
            "Sticky events count must be non-negative, got $stickyEventsCount"
        }
        require(avgStickyRssiDbm in -120.0..0.0) {
            "Average sticky RSSI must be in range [-120, 0] dBm, got $avgStickyRssiDbm"
        }
        require(avgBetterApRssiDbm in -120.0..0.0) {
            "Average better AP RSSI must be in range [-120, 0] dBm, got $avgBetterApRssiDbm"
        }
        require(worstStickyRssiDbm in -120..0) {
            "Worst sticky RSSI must be in range [-120, 0] dBm, got $worstStickyRssiDbm"
        }
    }

    /**
     * Whether sticky client behavior has been detected
     */
    val hasStickyBehavior: Boolean
        get() = stickyEventsCount > 0

    /**
     * Average duration of each sticky event
     */
    val avgStickyEventDurationMillis: Long
        get() =
            if (stickyEventsCount > 0) {
                totalStickyDurationMillis / stickyEventsCount
            } else {
                0
            }

    /**
     * RSSI differential between current AP and better AP
     * Positive value indicates how much better the alternative AP is
     */
    val avgRssiDifferentialDb: Double
        get() = avgBetterApRssiDbm - avgStickyRssiDbm

    /**
     * Sticky client severity
     */
    val severity: StickySeverity
        get() =
            when {
                !hasStickyBehavior -> StickySeverity.NONE
                worstStickyRssiDbm < -85 && avgRssiDifferentialDb >= 15 -> StickySeverity.CRITICAL
                avgStickyRssiDbm < -80 && avgRssiDifferentialDb >= 10 -> StickySeverity.HIGH
                avgStickyRssiDbm < -75 && avgRssiDifferentialDb >= 8 -> StickySeverity.MEDIUM
                avgRssiDifferentialDb >= 5 -> StickySeverity.LOW
                else -> StickySeverity.NONE
            }

    /**
     * Total sticky time in seconds
     */
    val totalStickyDurationSeconds: Long
        get() = totalStickyDurationMillis / 1000

    /**
     * Total sticky time in minutes
     */
    val totalStickyDurationMinutes: Long
        get() = totalStickyDurationSeconds / 60

    /**
     * Performance impact estimate (0.0-1.0)
     * Higher = more performance degradation
     */
    val performanceImpact: Double
        get() {
            val rssiImpact = ((-75 - avgStickyRssiDbm) / 30.0).coerceIn(0.0, 1.0)
            val differentialImpact = (avgRssiDifferentialDb / 20.0).coerceIn(0.0, 1.0)
            val frequencyImpact = (stickyEventsCount / 10.0).coerceIn(0.0, 1.0)

            return ((rssiImpact * 0.4) + (differentialImpact * 0.4) + (frequencyImpact * 0.2))
                .coerceIn(0.0, 1.0)
        }

    /**
     * Estimated throughput loss percentage due to sticky client
     */
    val estimatedThroughputLossPercent: Int
        get() = (performanceImpact * 100).toInt()

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                if (!hasStickyBehavior) {
                    append("No sticky client behavior detected")
                } else {
                    append("Sticky client detected: $stickyEventsCount events, ")
                    append("${totalStickyDurationMinutes}min total")
                    if (currentlySticky) {
                        append(" [CURRENTLY STICKY]")
                    }
                }
            }

    /**
     * Detailed diagnostic message
     */
    val diagnosticMessage: String
        get() =
            when (severity) {
                StickySeverity.CRITICAL -> {
                    "Critical sticky client issue: Device staying connected to very weak AP " +
                        "(${avgStickyRssiDbm.toInt()}dBm) when much better AP available " +
                        "(${avgBetterApRssiDbm.toInt()}dBm, +${avgRssiDifferentialDb.toInt()}dB). " +
                        "This is severely degrading performance."
                }

                StickySeverity.HIGH -> {
                    "High sticky client issue: Device often stays on weak AP " +
                        "(${avgStickyRssiDbm.toInt()}dBm) instead of roaming to stronger one " +
                        "(${avgBetterApRssiDbm.toInt()}dBm). Performance is significantly impacted."
                }

                StickySeverity.MEDIUM -> {
                    "Moderate sticky client behavior: Device sometimes stays on weaker AP " +
                        "(${avgStickyRssiDbm.toInt()}dBm) when better option exists " +
                        "(${avgBetterApRssiDbm.toInt()}dBm)."
                }

                StickySeverity.LOW -> {
                    "Minor sticky client tendency detected. Roaming is mostly working correctly."
                }

                StickySeverity.NONE -> {
                    "Roaming behavior is optimal. No sticky client issues detected."
                }
            }

    /**
     * Recommended actions to fix sticky client issues
     */
    val recommendations: List<String>
        get() =
            buildList {
                when (severity) {
                    StickySeverity.CRITICAL, StickySeverity.HIGH -> {
                        add("Enable 802.11k/r/v (fast roaming) on all access points")
                        add("Reduce AP transmit power to encourage roaming")
                        add("Update device WiFi driver/firmware")
                        add("Consider adjusting AP placement for better coverage overlap")
                    }

                    StickySeverity.MEDIUM -> {
                        add("Enable 802.11k/v if not already active")
                        add("Check AP transmit power settings")
                        add("Monitor for driver updates")
                    }

                    StickySeverity.LOW -> {
                        add("Enable 802.11k for improved roaming hints")
                    }

                    StickySeverity.NONE -> {
                        // No recommendations needed
                    }
                }
            }
}

/**
 * Sticky client severity levels
 */
enum class StickySeverity(
    val displayName: String,
    val minRssiDifferentialDb: Double,
) {
    /** No sticky client behavior */
    NONE("No Issue", 0.0),

    /** Low severity sticky client */
    LOW("Low Severity", 5.0),

    /** Medium severity sticky client */
    MEDIUM("Medium Severity", 8.0),

    /** High severity sticky client */
    HIGH("High Severity", 10.0),

    /** Critical sticky client issue */
    CRITICAL("Critical Issue", 15.0),
    ;

    /**
     * Whether this severity level requires action
     */
    val requiresAction: Boolean
        get() = this in listOf(MEDIUM, HIGH, CRITICAL)

    /**
     * Whether this severity level is urgent
     */
    val isUrgent: Boolean
        get() = this in listOf(HIGH, CRITICAL)
}
