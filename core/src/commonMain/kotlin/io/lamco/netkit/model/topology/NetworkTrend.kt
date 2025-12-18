package io.lamco.netkit.model.topology

/**
 * Network-level performance and signal trends over time
 *
 * Aggregates historical data across all access points in a network (SSID)
 * to identify patterns, predict future performance, and detect anomalies.
 * Provides high-level metrics for network health assessment and optimization.
 *
 * Unlike ApHistory which tracks individual BSSIDs, NetworkTrend focuses on
 * the overall network experience across all APs in an ESS (Extended Service Set).
 *
 * @property ssid Network name
 * @property startTimestamp Start of trend analysis period (epoch millis)
 * @property endTimestamp End of trend analysis period (epoch millis)
 * @property apHistories Historical data for all APs in this network
 * @property snapshots Network snapshots over the trend period
 */
data class NetworkTrend(
    val ssid: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val apHistories: List<ApHistory>,
    val snapshots: List<ScanSnapshot>,
) {
    init {
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
        require(startTimestamp > 0) {
            "Start timestamp must be positive, got $startTimestamp"
        }
        require(endTimestamp >= startTimestamp) {
            "End timestamp must be >= start timestamp, got end=$endTimestamp, start=$startTimestamp"
        }
        require(apHistories.all { it.ssid == ssid }) {
            "All AP histories must belong to SSID $ssid"
        }
    }

    /**
     * Duration of the trend analysis period (milliseconds)
     */
    val durationMillis: Long
        get() = endTimestamp - startTimestamp

    /**
     * Number of unique access points detected in this network
     */
    val uniqueApCount: Int
        get() = apHistories.size

    /**
     * Total number of observations across all APs
     */
    val totalObservationCount: Int
        get() = apHistories.sumOf { it.observationCount }

    /**
     * Number of snapshots in the trend period
     */
    val snapshotCount: Int
        get() = snapshots.size

    /**
     * Average number of APs visible per snapshot
     */
    val averageVisibleApCount: Double
        get() =
            if (snapshots.isNotEmpty()) {
                snapshots
                    .map { snapshot ->
                        snapshot.allBssids.count { bss ->
                            apHistories.any { it.bssid == bss.bssid }
                        }
                    }.average()
            } else {
                0.0
            }

    /**
     * Overall network signal strength (average across all AP averages)
     */
    val averageNetworkRssiDbm: Double
        get() =
            if (apHistories.isNotEmpty()) {
                apHistories.map { it.averageRssiDbm }.average()
            } else {
                0.0
            }

    /**
     * Best signal strength ever observed in this network
     */
    val peakNetworkRssiDbm: Int?
        get() = apHistories.mapNotNull { it.peakRssiDbm }.maxOrNull()

    /**
     * Worst signal strength ever observed in this network
     */
    val worstNetworkRssiDbm: Int?
        get() = apHistories.mapNotNull { it.minRssiDbm }.minOrNull()

    /**
     * Network signal stability (average across all APs)
     */
    val networkStability: SignalStability
        get() {
            val avgStdDev =
                if (apHistories.isNotEmpty()) {
                    apHistories.map { it.rssiStandardDeviation }.average()
                } else {
                    0.0
                }
            return SignalStability.fromStdDev(avgStdDev)
        }

    /**
     * Most stable AP in the network
     */
    val mostStableAp: ApHistory?
        get() = apHistories.minByOrNull { it.rssiStandardDeviation }

    /**
     * Least stable AP in the network
     */
    val leastStableAp: ApHistory?
        get() = apHistories.maxByOrNull { it.rssiStandardDeviation }

    /**
     * APs that are currently visible
     */
    fun currentlyVisibleAps(
        thresholdMillis: Long = 60_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<ApHistory> = apHistories.filter { it.isCurrentlyVisible(thresholdMillis, currentTimeMillis) }

    /**
     * APs that have disappeared recently
     */
    fun recentlyDisappearedAps(
        windowMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<ApHistory> {
        val cutoffTime = currentTimeMillis - windowMillis
        return apHistories.filter {
            it.lastSeenTimestamp in cutoffTime until (currentTimeMillis - 60_000)
        }
    }

    /**
     * AP churn rate (APs appearing/disappearing per hour)
     */
    val apChurnRate: Double
        get() {
            if (snapshots.size < 2) return 0.0

            val comparisons =
                snapshots.zipWithNext().map { (older, newer) ->
                    older.compareWith(newer)
                }

            val totalChurn = comparisons.sumOf { it.addedBssids.size + it.removedBssids.size }
            val durationHours = durationMillis / 3600_000.0

            return if (durationHours > 0) totalChurn / durationHours else 0.0
        }

    /**
     * Network environment stability based on AP churn
     */
    val environmentStability: EnvironmentStability
        get() =
            when {
                apChurnRate < 0.5 -> EnvironmentStability.VERY_STABLE
                apChurnRate < 2.0 -> EnvironmentStability.STABLE
                apChurnRate < 5.0 -> EnvironmentStability.MODERATE
                apChurnRate < 10.0 -> EnvironmentStability.UNSTABLE
                else -> EnvironmentStability.VERY_UNSTABLE
            }

    /**
     * Total number of roaming events in this network
     */
    val totalRoamingEvents: Int
        get() = apHistories.sumOf { it.roamingEventCount }

    /**
     * Average roaming quality across all events
     */
    val averageRoamingQuality: RoamingEventQuality?
        get() {
            val allQualities =
                apHistories
                    .flatMap { it.roamingEvents }
                    .filter { it.isActualRoam }
                    .map { it.roamingQuality }

            return if (allQualities.isNotEmpty()) {
                // Return median quality
                allQualities.sorted()[allQualities.size / 2]
            } else {
                null
            }
        }

    /**
     * Percentage of roaming events that were seamless (802.11r)
     */
    val seamlessRoamingPercentage: Double
        get() {
            val allRoamEvents = apHistories.flatMap { it.roamingEvents }.filter { it.isActualRoam }
            if (allRoamEvents.isEmpty()) return 0.0

            val seamlessCount = allRoamEvents.count { it.has11r }
            return (seamlessCount.toDouble() / allRoamEvents.size) * 100.0
        }

    /**
     * Total connection time across all APs (milliseconds)
     */
    val totalConnectionTimeMillis: Long
        get() = apHistories.sumOf { it.totalConnectionTimeMillis }

    /**
     * Overall connection success rate
     */
    val connectionSuccessRate: Double
        get() {
            val totalConnections = apHistories.sumOf { it.connectionCount }
            val totalEvents = apHistories.sumOf { it.connectionEvents.size }

            return if (totalEvents > 0) {
                totalConnections.toDouble() / totalEvents
            } else {
                0.0
            }
        }

    /**
     * Recent network trend (last 5 minutes)
     */
    fun recentNetworkTrend(
        windowMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): NetworkTrendDirection {
        val recentAverages =
            apHistories
                .mapNotNull { it.recentAverageRssi(windowMillis, currentTimeMillis) }

        val overallAverages = apHistories.map { it.averageRssiDbm }

        if (recentAverages.isEmpty() || overallAverages.isEmpty()) {
            return NetworkTrendDirection.INSUFFICIENT_DATA
        }

        val recentAvg = recentAverages.average()
        val overallAvg = overallAverages.average()
        val difference = recentAvg - overallAvg

        return when {
            difference > 5.0 -> NetworkTrendDirection.STRONGLY_IMPROVING
            difference > 2.0 -> NetworkTrendDirection.IMPROVING
            difference > -2.0 -> NetworkTrendDirection.STABLE
            difference > -5.0 -> NetworkTrendDirection.DEGRADING
            else -> NetworkTrendDirection.STRONGLY_DEGRADING
        }
    }

    /**
     * Individual AP trends
     */
    fun apTrends(
        windowMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<ApTrendSummary> =
        apHistories.map { history ->
            ApTrendSummary(
                bssid = history.bssid,
                trend = history.signalTrend(windowMillis, currentTimeMillis),
                recentAverageRssi = history.recentAverageRssi(windowMillis, currentTimeMillis),
                overallAverageRssi = history.averageRssiDbm,
                stability = history.signalStability,
            )
        }

    /**
     * APs with degrading signal trends
     */
    fun degradingAps(
        windowMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<ApHistory> =
        apHistories.filter {
            it.signalTrend(windowMillis, currentTimeMillis).indicatesProblem
        }

    /**
     * APs with improving signal trends
     */
    fun improvingAps(
        windowMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<ApHistory> =
        apHistories.filter {
            it.signalTrend(windowMillis, currentTimeMillis).isPositive
        }

    /**
     * Recent configuration changes across all APs
     */
    fun recentConfigChanges(
        windowMillis: Long = 86400_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<ConfigurationChange> {
        val cutoffTime = currentTimeMillis - windowMillis
        return apHistories
            .flatMap { it.configurationChanges }
            .filter { it.timestampMillis >= cutoffTime }
            .sortedByDescending { it.timestampMillis }
    }

    /**
     * Network health score (0-100)
     *
     * Considers:
     * - Signal strength (40%)
     * - Signal stability (30%)
     * - Roaming quality (20%)
     * - Connection success rate (10%)
     */
    val healthScore: Int
        get() {
            // Signal strength component (0-40 points)
            val avgRssi = averageNetworkRssiDbm
            val signalScore =
                when {
                    avgRssi >= -50 -> 40.0
                    avgRssi >= -60 -> 35.0
                    avgRssi >= -70 -> 25.0
                    avgRssi >= -80 -> 15.0
                    else -> 5.0
                }

            // Stability component (0-30 points)
            val stabilityScore =
                when (networkStability) {
                    SignalStability.VERY_STABLE -> 30.0
                    SignalStability.STABLE -> 25.0
                    SignalStability.MODERATE -> 15.0
                    SignalStability.UNSTABLE -> 8.0
                    SignalStability.VERY_UNSTABLE -> 2.0
                }

            // Roaming quality component (0-20 points)
            val roamingScore =
                when (averageRoamingQuality) {
                    RoamingEventQuality.EXCELLENT -> 20.0
                    RoamingEventQuality.VERY_GOOD -> 17.0
                    RoamingEventQuality.GOOD -> 13.0
                    RoamingEventQuality.FAIR -> 8.0
                    RoamingEventQuality.POOR -> 3.0
                    else -> 10.0 // Unknown - neutral score
                }

            // Connection success component (0-10 points)
            val connectionScore = connectionSuccessRate * 10.0

            return (signalScore + stabilityScore + roamingScore + connectionScore).toInt()
        }

    /**
     * Network health category
     */
    val health: NetworkHealth
        get() =
            when {
                healthScore >= 85 -> NetworkHealth.EXCELLENT
                healthScore >= 70 -> NetworkHealth.GOOD
                healthScore >= 50 -> NetworkHealth.FAIR
                healthScore >= 30 -> NetworkHealth.POOR
                else -> NetworkHealth.CRITICAL
            }

    /**
     * Performance prediction for next time window
     */
    fun predictPerformance(horizonMillis: Long = 300_000): PerformancePrediction {
        val currentTrend = recentNetworkTrend(300_000)
        val currentHealth = healthScore

        // Simple linear extrapolation
        val predictedHealthChange =
            when (currentTrend) {
                NetworkTrendDirection.STRONGLY_IMPROVING -> +10
                NetworkTrendDirection.IMPROVING -> +5
                NetworkTrendDirection.STABLE -> 0
                NetworkTrendDirection.DEGRADING -> -5
                NetworkTrendDirection.STRONGLY_DEGRADING -> -10
                NetworkTrendDirection.INSUFFICIENT_DATA -> 0
            }

        val predictedHealth = (currentHealth + predictedHealthChange).coerceIn(0, 100)
        val confidence =
            if (totalObservationCount >= 100) {
                PredictionConfidence.HIGH
            } else if (totalObservationCount >= 30) {
                PredictionConfidence.MEDIUM
            } else {
                PredictionConfidence.LOW
            }

        return PerformancePrediction(
            horizonMillis = horizonMillis,
            predictedHealthScore = predictedHealth,
            currentHealthScore = currentHealth,
            trend = currentTrend,
            confidence = confidence,
        )
    }

    /**
     * Human-readable trend summary
     */
    val summary: String
        get() =
            buildString {
                append("$ssid: ")
                append("$uniqueApCount APs, ")
                append("avg ${averageNetworkRssiDbm.toInt()}dBm, ")
                append("${health.displayName} health ($healthScore/100)")
            }

    companion object {
        /**
         * Create network trend from AP histories
         */
        fun create(
            ssid: String,
            apHistories: List<ApHistory>,
            snapshots: List<ScanSnapshot> = emptyList(),
        ): NetworkTrend {
            require(apHistories.isNotEmpty()) {
                "AP histories must not be empty"
            }

            val startTime = apHistories.minOf { it.firstSeenTimestamp }
            val endTime = apHistories.maxOf { it.lastSeenTimestamp }

            return NetworkTrend(
                ssid = ssid,
                startTimestamp = startTime,
                endTimestamp = endTime,
                apHistories = apHistories,
                snapshots = snapshots.filter { it.clusters.any { cluster -> cluster.ssid == ssid } },
            )
        }
    }
}

/**
 * Summary of an AP's trend information
 *
 * @property bssid Access point MAC address
 * @property trend Signal trend direction
 * @property recentAverageRssi Recent average signal strength
 * @property overallAverageRssi Overall average signal strength
 * @property stability Signal stability category
 */
data class ApTrendSummary(
    val bssid: String,
    val trend: SignalTrend,
    val recentAverageRssi: Double?,
    val overallAverageRssi: Double,
    val stability: SignalStability,
) {
    /**
     * Whether this AP is performing well
     */
    val isHealthy: Boolean
        get() = stability.isAcceptable && !trend.indicatesProblem

    /**
     * Whether this AP needs attention
     */
    val needsAttention: Boolean
        get() = trend.indicatesProblem || !stability.isAcceptable

    /**
     * Human-readable summary
     */
    val summary: String
        get() = "$bssid: ${trend.displayName}, ${stability.displayName}"
}

/**
 * Network-level trend direction
 */
enum class NetworkTrendDirection(
    val displayName: String,
) {
    /** Strongly improving (> 5 dB improvement) */
    STRONGLY_IMPROVING("Strongly Improving"),

    /** Improving (> 2 dB improvement) */
    IMPROVING("Improving"),

    /** Stable (-2 to +2 dB) */
    STABLE("Stable"),

    /** Degrading (> 2 dB degradation) */
    DEGRADING("Degrading"),

    /** Strongly degrading (> 5 dB degradation) */
    STRONGLY_DEGRADING("Strongly Degrading"),

    /** Insufficient data for trend */
    INSUFFICIENT_DATA("Insufficient Data"),
    ;

    /**
     * Whether this trend is positive
     */
    val isPositive: Boolean
        get() = this in listOf(STRONGLY_IMPROVING, IMPROVING)

    /**
     * Whether this trend indicates a problem
     */
    val indicatesProblem: Boolean
        get() = this in listOf(DEGRADING, STRONGLY_DEGRADING)
}

/**
 * Network health categories
 */
enum class NetworkHealth(
    val displayName: String,
    val minScore: Int,
) {
    /** Excellent health (>= 85) */
    EXCELLENT("Excellent", 85),

    /** Good health (>= 70) */
    GOOD("Good", 70),

    /** Fair health (>= 50) */
    FAIR("Fair", 50),

    /** Poor health (>= 30) */
    POOR("Poor", 30),

    /** Critical health (< 30) */
    CRITICAL("Critical", 0),
    ;

    /**
     * Whether this health level is acceptable
     */
    val isAcceptable: Boolean
        get() = this in listOf(EXCELLENT, GOOD, FAIR)

    companion object {
        /**
         * Determine health from score
         */
        fun fromScore(score: Int): NetworkHealth =
            when {
                score >= 85 -> EXCELLENT
                score >= 70 -> GOOD
                score >= 50 -> FAIR
                score >= 30 -> POOR
                else -> CRITICAL
            }
    }
}

/**
 * Performance prediction
 *
 * @property horizonMillis Time horizon for prediction (milliseconds)
 * @property predictedHealthScore Predicted health score (0-100)
 * @property currentHealthScore Current health score (0-100)
 * @property trend Current trend direction
 * @property confidence Prediction confidence level
 */
data class PerformancePrediction(
    val horizonMillis: Long,
    val predictedHealthScore: Int,
    val currentHealthScore: Int,
    val trend: NetworkTrendDirection,
    val confidence: PredictionConfidence,
) {
    init {
        require(horizonMillis > 0) {
            "Horizon must be positive, got $horizonMillis"
        }
        require(predictedHealthScore in 0..100) {
            "Predicted health score must be in range [0, 100], got $predictedHealthScore"
        }
        require(currentHealthScore in 0..100) {
            "Current health score must be in range [0, 100], got $currentHealthScore"
        }
    }

    /**
     * Expected health change
     */
    val expectedChange: Int
        get() = predictedHealthScore - currentHealthScore

    /**
     * Whether performance is expected to improve
     */
    val expectsImprovement: Boolean
        get() = expectedChange > 0

    /**
     * Whether performance is expected to degrade
     */
    val expectsDegradation: Boolean
        get() = expectedChange < 0

    /**
     * Predicted health category
     */
    val predictedHealth: NetworkHealth
        get() = NetworkHealth.fromScore(predictedHealthScore)

    /**
     * Whether this prediction is reliable
     */
    val isReliable: Boolean
        get() = confidence in listOf(PredictionConfidence.HIGH, PredictionConfidence.MEDIUM)

    /**
     * Human-readable prediction summary
     */
    val summary: String
        get() =
            buildString {
                append("Predicted: ${predictedHealth.displayName} ($predictedHealthScore/100)")
                if (expectedChange != 0) {
                    val sign = if (expectedChange > 0) "+" else ""
                    append(" [${sign}$expectedChange]")
                }
                append(" - ${confidence.displayName} confidence")
            }
}

/**
 * Prediction confidence levels
 */
enum class PredictionConfidence(
    val displayName: String,
) {
    /** High confidence (>= 100 observations) */
    HIGH("High"),

    /** Medium confidence (>= 30 observations) */
    MEDIUM("Medium"),

    /** Low confidence (< 30 observations) */
    LOW("Low"),
    ;

    /**
     * Whether this confidence level is acceptable for decisions
     */
    val isAcceptable: Boolean
        get() = this in listOf(HIGH, MEDIUM)
}
