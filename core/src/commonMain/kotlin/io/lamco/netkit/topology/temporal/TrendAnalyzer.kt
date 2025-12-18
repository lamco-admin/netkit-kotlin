package io.lamco.netkit.topology.temporal

import io.lamco.netkit.model.topology.*

/**
 * Trend analysis engine for detecting patterns in WiFi network behavior
 *
 * Analyzes historical data to identify:
 * - Signal strength trends (degradation/improvement)
 * - AP churn patterns (appearance/disappearance)
 * - Coverage quality changes over time
 * - Roaming efficiency trends
 * - Network stability patterns
 *
 * This analyzer focuses on gradual, sustained patterns rather than
 * sudden anomalies (which are handled by AnomalyDetector).
 *
 * @property minObservationsForTrend Minimum observations required for reliable trend detection
 * @property trendWindowMillis Time window for trend calculation (default: 1 hour)
 * @property significantChangeThresholdDb RSSI change considered significant (default: 5 dB)
 */
class TrendAnalyzer(
    val minObservationsForTrend: Int = 10,
    val trendWindowMillis: Long = 3600_000, // 1 hour
    val significantChangeThresholdDb: Int = 5,
) {
    init {
        require(minObservationsForTrend >= 3) {
            "Minimum observations for trend must be >= 3, got $minObservationsForTrend"
        }
        require(trendWindowMillis > 0) {
            "Trend window must be positive, got $trendWindowMillis"
        }
        require(significantChangeThresholdDb > 0) {
            "Significant change threshold must be positive, got $significantChangeThresholdDb"
        }
    }

    /**
     * Analyze signal strength trend for a specific AP
     *
     * Returns comprehensive trend analysis including direction, rate of change,
     * and confidence level based on data quality.
     */
    fun analyzeApSignalTrend(
        history: ApHistory,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): ApSignalTrendAnalysis {
        val recentObs = history.recentObservations(trendWindowMillis, currentTimeMillis)

        if (recentObs.size < minObservationsForTrend) {
            return ApSignalTrendAnalysis(
                bssid = history.bssid,
                trend = SignalTrend.INSUFFICIENT_DATA,
                changeRateDbPerHour = 0.0,
                confidence = TrendConfidence.LOW,
                observations = recentObs.size,
                isSignificant = false,
            )
        }

        // Calculate linear regression slope
        val changeRateDbPerMs = calculateSignalSlope(recentObs)
        val changeRateDbPerHour = changeRateDbPerMs * 3600_000.0

        val trend = classifyTrend(changeRateDbPerHour)
        val confidence = calculateConfidence(recentObs, history.rssiStandardDeviation)
        val isSignificant = kotlin.math.abs(changeRateDbPerHour) >= significantChangeThresholdDb

        return ApSignalTrendAnalysis(
            bssid = history.bssid,
            trend = trend,
            changeRateDbPerHour = changeRateDbPerHour,
            confidence = confidence,
            observations = recentObs.size,
            isSignificant = isSignificant,
        )
    }

    /**
     * Analyze network-wide signal trends
     *
     * Aggregates trends across all APs to provide network-level insight.
     */
    fun analyzeNetworkSignalTrend(
        networkTrend: NetworkTrend,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): NetworkSignalTrendAnalysis {
        val apTrends =
            networkTrend.apHistories.map { history ->
                analyzeApSignalTrend(history, currentTimeMillis)
            }

        val degradingCount = apTrends.count { it.trend.indicatesProblem }
        val improvingCount = apTrends.count { it.trend.isPositive }
        val stableCount = apTrends.count { it.trend == SignalTrend.STABLE }

        val averageChangeRate =
            apTrends
                .filter { it.confidence != TrendConfidence.LOW }
                .map { it.changeRateDbPerHour }
                .average()
                .takeIf { it.isFinite() } ?: 0.0

        val overallTrend =
            when {
                degradingCount > improvingCount && degradingCount > stableCount ->
                    NetworkTrendDirection.DEGRADING
                improvingCount > degradingCount && improvingCount > stableCount ->
                    NetworkTrendDirection.IMPROVING
                else -> NetworkTrendDirection.STABLE
            }

        return NetworkSignalTrendAnalysis(
            ssid = networkTrend.ssid,
            overallTrend = overallTrend,
            averageChangeRateDbPerHour = averageChangeRate,
            degradingApCount = degradingCount,
            improvingApCount = improvingCount,
            stableApCount = stableCount,
            apTrends = apTrends,
        )
    }

    /**
     * Analyze AP churn patterns (appearance/disappearance)
     */
    fun analyzeApChurn(
        snapshots: List<ScanSnapshot>,
        windowMillis: Long = trendWindowMillis,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): ApChurnAnalysis {
        if (snapshots.size < 2) {
            return ApChurnAnalysis(
                churnRate = 0.0,
                averageAdditionsPerHour = 0.0,
                averageRemovalsPerHour = 0.0,
                stability = EnvironmentStability.VERY_STABLE,
                pattern = ChurnPattern.UNKNOWN,
            )
        }

        val recentSnapshots =
            snapshots
                .filter {
                    it.timestampMillis >= currentTimeMillis - windowMillis
                }.sortedBy { it.timestampMillis }

        if (recentSnapshots.size < 2) {
            return ApChurnAnalysis(
                churnRate = 0.0,
                averageAdditionsPerHour = 0.0,
                averageRemovalsPerHour = 0.0,
                stability = EnvironmentStability.VERY_STABLE,
                pattern = ChurnPattern.UNKNOWN,
            )
        }

        // compareWith() expects: this=newer, other=older (newer.compareWith(older))
        val comparisons =
            recentSnapshots.zipWithNext().map { (older, newer) ->
                newer.compareWith(older)
            }

        val totalAdditions = comparisons.sumOf { it.addedBssids.size }
        val totalRemovals = comparisons.sumOf { it.removedBssids.size }
        val totalChurn = totalAdditions + totalRemovals

        val durationHours = windowMillis / 3600_000.0
        val churnRate = totalChurn / durationHours
        val additionsPerHour = totalAdditions / durationHours
        val removalsPerHour = totalRemovals / durationHours

        val stability =
            when {
                churnRate < 0.5 -> EnvironmentStability.VERY_STABLE
                churnRate < 2.0 -> EnvironmentStability.STABLE
                churnRate < 5.0 -> EnvironmentStability.MODERATE
                churnRate < 10.0 -> EnvironmentStability.UNSTABLE
                else -> EnvironmentStability.VERY_UNSTABLE
            }

        val pattern =
            when {
                additionsPerHour > removalsPerHour * 2 -> ChurnPattern.RAPID_GROWTH
                removalsPerHour > additionsPerHour * 2 -> ChurnPattern.RAPID_DECLINE
                churnRate > 5.0 -> ChurnPattern.HIGH_VOLATILITY
                churnRate < 1.0 -> ChurnPattern.STABLE
                else -> ChurnPattern.MODERATE_CHANGE
            }

        return ApChurnAnalysis(
            churnRate = churnRate,
            averageAdditionsPerHour = additionsPerHour,
            averageRemovalsPerHour = removalsPerHour,
            stability = stability,
            pattern = pattern,
        )
    }

    /**
     * Analyze roaming efficiency trends
     */
    fun analyzeRoamingTrend(
        networkTrend: NetworkTrend,
        windowMillis: Long = trendWindowMillis,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): RoamingTrendAnalysis {
        val cutoffTime = currentTimeMillis - windowMillis
        val recentEvents =
            networkTrend.apHistories
                .flatMap { it.roamingEvents }
                .filter { it.timestampMillis >= cutoffTime && it.isActualRoam }

        if (recentEvents.isEmpty()) {
            return RoamingTrendAnalysis(
                eventCount = 0,
                averageLatencyMs = null,
                seamlessPercentage = 0.0,
                appropriateRoamPercentage = 0.0,
                stickyClientPercentage = 0.0,
                trend = RoamingQualityTrend.UNKNOWN,
            )
        }

        val averageLatency =
            recentEvents
                .filter { it.durationMillis >= 0 }
                .map { it.durationMillis }
                .average()
                .takeIf { it.isFinite() }

        val seamlessCount = recentEvents.count { it.has11r || it.roamingQuality == RoamingEventQuality.EXCELLENT }
        val seamlessPercent = (seamlessCount.toDouble() / recentEvents.size) * 100.0

        val appropriateCount = recentEvents.count { it.wasAppropriateRoam }
        val appropriatePercent = (appropriateCount.toDouble() / recentEvents.size) * 100.0

        val stickyCount = recentEvents.count { it.indicatesStickyClient }
        val stickyPercent = (stickyCount.toDouble() / recentEvents.size) * 100.0

        val trend =
            when {
                seamlessPercent >= 80.0 && appropriatePercent >= 80.0 -> RoamingQualityTrend.EXCELLENT
                seamlessPercent >= 50.0 && appropriatePercent >= 60.0 -> RoamingQualityTrend.GOOD
                stickyPercent >= 40.0 -> RoamingQualityTrend.POOR
                else -> RoamingQualityTrend.FAIR
            }

        return RoamingTrendAnalysis(
            eventCount = recentEvents.size,
            averageLatencyMs = averageLatency,
            seamlessPercentage = seamlessPercent,
            appropriateRoamPercentage = appropriatePercent,
            stickyClientPercentage = stickyPercent,
            trend = trend,
        )
    }

    /**
     * Calculate signal slope using linear regression
     */
    private fun calculateSignalSlope(observations: List<SignalObservation>): Double {
        if (observations.size < 2) return 0.0

        val n = observations.size
        val x = observations.mapIndexed { index, _ -> index.toDouble() }
        val y = observations.map { it.rssiDbm.toDouble() }

        val xMean = x.average()
        val yMean = y.average()

        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - xMean) * (yi - yMean) }
        val denominator = x.sumOf { xi -> (xi - xMean) * (xi - xMean) }

        if (denominator == 0.0) return 0.0

        val slope = numerator / denominator

        // Convert from per-observation to per-millisecond
        if (observations.size > 1) {
            val timeSpan = observations.last().timestampMillis - observations.first().timestampMillis
            val avgInterval = timeSpan.toDouble() / (observations.size - 1)
            return slope / avgInterval
        }

        return 0.0
    }

    /**
     * Classify trend based on change rate
     */
    private fun classifyTrend(changeRateDbPerHour: Double): SignalTrend =
        when {
            changeRateDbPerHour > 5.0 -> SignalTrend.RAPIDLY_IMPROVING
            changeRateDbPerHour > 1.0 -> SignalTrend.IMPROVING
            changeRateDbPerHour > -1.0 -> SignalTrend.STABLE
            changeRateDbPerHour > -5.0 -> SignalTrend.DEGRADING
            else -> SignalTrend.RAPIDLY_DEGRADING
        }

    /**
     * Calculate trend confidence based on data quality
     */
    private fun calculateConfidence(
        observations: List<SignalObservation>,
        stdDev: Double,
    ): TrendConfidence =
        when {
            observations.size < minObservationsForTrend -> TrendConfidence.LOW
            observations.size >= 50 && stdDev < 5.0 -> TrendConfidence.HIGH
            observations.size >= 20 && stdDev < 10.0 -> TrendConfidence.MEDIUM
            else -> TrendConfidence.LOW
        }
}

/**
 * Signal trend analysis result for a single AP
 *
 * @property bssid Access point MAC address
 * @property trend Trend direction (improving/stable/degrading)
 * @property changeRateDbPerHour Rate of change in dB per hour
 * @property confidence Confidence level in this trend
 * @property observations Number of observations used
 * @property isSignificant Whether the trend is statistically significant
 */
data class ApSignalTrendAnalysis(
    val bssid: String,
    val trend: SignalTrend,
    val changeRateDbPerHour: Double,
    val confidence: TrendConfidence,
    val observations: Int,
    val isSignificant: Boolean,
) {
    /**
     * Whether this trend is reliable for decision-making
     */
    val isReliable: Boolean
        get() = confidence != TrendConfidence.LOW && observations >= 10

    /**
     * Whether this trend indicates a potential problem
     */
    val indicatesProblem: Boolean
        get() = trend.indicatesProblem && isSignificant && isReliable

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("$bssid: ${trend.displayName}")
                if (isSignificant) {
                    val sign = if (changeRateDbPerHour >= 0) "+" else ""
                    append(" (${sign}${changeRateDbPerHour.toInt()}dB/hr)")
                }
                append(" [${confidence.displayName}]")
            }
}

/**
 * Network-wide signal trend analysis
 *
 * @property ssid Network name
 * @property overallTrend Overall network trend direction
 * @property averageChangeRateDbPerHour Average change rate across all APs
 * @property degradingApCount Number of APs with degrading signal
 * @property improvingApCount Number of APs with improving signal
 * @property stableApCount Number of APs with stable signal
 * @property apTrends Individual AP trend analyses
 */
data class NetworkSignalTrendAnalysis(
    val ssid: String,
    val overallTrend: NetworkTrendDirection,
    val averageChangeRateDbPerHour: Double,
    val degradingApCount: Int,
    val improvingApCount: Int,
    val stableApCount: Int,
    val apTrends: List<ApSignalTrendAnalysis>,
) {
    /**
     * Total number of APs analyzed
     */
    val totalApCount: Int
        get() = apTrends.size

    /**
     * Percentage of APs degrading
     */
    val degradingPercentage: Double
        get() =
            if (totalApCount > 0) {
                (degradingApCount.toDouble() / totalApCount) * 100.0
            } else {
                0.0
            }

    /**
     * Whether network health is declining
     */
    val isNetworkDeclining: Boolean
        get() = degradingPercentage > 50.0 || overallTrend == NetworkTrendDirection.STRONGLY_DEGRADING

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("$ssid: ${overallTrend.displayName}")
                if (averageChangeRateDbPerHour != 0.0) {
                    val sign = if (averageChangeRateDbPerHour >= 0) "+" else ""
                    append(" (${sign}${averageChangeRateDbPerHour.toInt()}dB/hr avg)")
                }
                append(" - $degradingApCount degrading, $improvingApCount improving")
            }
}

/**
 * AP churn analysis result
 *
 * @property churnRate Total churn rate (additions + removals per hour)
 * @property averageAdditionsPerHour Average new APs per hour
 * @property averageRemovalsPerHour Average disappearing APs per hour
 * @property stability Environment stability category
 * @property pattern Detected churn pattern
 */
data class ApChurnAnalysis(
    val churnRate: Double,
    val averageAdditionsPerHour: Double,
    val averageRemovalsPerHour: Double,
    val stability: EnvironmentStability,
    val pattern: ChurnPattern,
) {
    /**
     * Whether the environment is stable enough for reliable analysis
     */
    val isStableEnvironment: Boolean
        get() = stability.isAcceptable

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("Churn: ${churnRate.format(1)}/hr")
                append(" (+${averageAdditionsPerHour.format(1)}, -${averageRemovalsPerHour.format(1)})")
                append(" - ${pattern.displayName}")
            }

    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}

/**
 * Roaming efficiency trend analysis
 *
 * @property eventCount Number of roaming events analyzed
 * @property averageLatencyMs Average roaming latency (milliseconds)
 * @property seamlessPercentage Percentage of seamless roams (802.11r)
 * @property appropriateRoamPercentage Percentage of appropriate roaming decisions
 * @property stickyClientPercentage Percentage indicating sticky client behavior
 * @property trend Overall roaming quality trend
 */
data class RoamingTrendAnalysis(
    val eventCount: Int,
    val averageLatencyMs: Double?,
    val seamlessPercentage: Double,
    val appropriateRoamPercentage: Double,
    val stickyClientPercentage: Double,
    val trend: RoamingQualityTrend,
) {
    /**
     * Whether roaming behavior is healthy
     */
    val isHealthy: Boolean
        get() = trend in listOf(RoamingQualityTrend.EXCELLENT, RoamingQualityTrend.GOOD)

    /**
     * Whether sticky client issues are present
     */
    val hasStickyClientIssues: Boolean
        get() = stickyClientPercentage > 30.0

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("Roaming: ${trend.displayName}")
                append(" ($eventCount events")
                if (averageLatencyMs != null) {
                    append(", ${averageLatencyMs.toInt()}ms avg")
                }
                append(")")
                if (hasStickyClientIssues) {
                    append(" [STICKY: ${stickyClientPercentage.toInt()}%]")
                }
            }
}

/**
 * Trend confidence levels
 */
enum class TrendConfidence(
    val displayName: String,
) {
    /** High confidence (>= 50 observations, low variance) */
    HIGH("High"),

    /** Medium confidence (>= 20 observations, moderate variance) */
    MEDIUM("Medium"),

    /** Low confidence (< minimum observations or high variance) */
    LOW("Low"),
    ;

    /**
     * Whether this confidence is acceptable for decisions
     */
    val isAcceptable: Boolean
        get() = this in listOf(HIGH, MEDIUM)
}

/**
 * AP churn patterns
 */
enum class ChurnPattern(
    val displayName: String,
) {
    /** Rapid growth (many new APs appearing) */
    RAPID_GROWTH("Rapid Growth"),

    /** Rapid decline (many APs disappearing) */
    RAPID_DECLINE("Rapid Decline"),

    /** High volatility (many changes in both directions) */
    HIGH_VOLATILITY("High Volatility"),

    /** Moderate change (some additions/removals) */
    MODERATE_CHANGE("Moderate Change"),

    /** Stable (minimal changes) */
    STABLE("Stable"),

    /** Unknown pattern */
    UNKNOWN("Unknown"),
}

/**
 * Roaming quality trend categories
 */
enum class RoamingQualityTrend(
    val displayName: String,
) {
    /** Excellent roaming (>= 80% seamless & appropriate) */
    EXCELLENT("Excellent"),

    /** Good roaming (>= 50% seamless & appropriate) */
    GOOD("Good"),

    /** Fair roaming (mixed results) */
    FAIR("Fair"),

    /** Poor roaming (>= 40% sticky client issues) */
    POOR("Poor"),

    /** Unknown (insufficient data) */
    UNKNOWN("Unknown"),
    ;

    /**
     * Whether this quality is acceptable
     */
    val isAcceptable: Boolean
        get() = this in listOf(EXCELLENT, GOOD)
}
