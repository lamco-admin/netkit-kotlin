package io.lamco.netkit.analytics.model

import kotlin.math.abs

/**
 * Comparison result between two metric values
 *
 * Represents the difference between a current metric value and a baseline,
 * with change magnitude, direction, and significance assessment.
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = ComparativeAnalyzer()
 * val result = analyzer.compareToBaseline(
 *     current = currentMetrics,
 *     baseline = historicalBaseline
 * )
 * result.forEach { comparison ->
 *     println("${comparison.metric}: ${comparison.direction}")
 *     println("  Change: ${comparison.changePercent}%")
 *     if (comparison.isSignificant) {
 *         println("  **Significant change detected**")
 *     }
 * }
 * ```
 *
 * @property metric Name of the metric being compared (e.g., "rssi", "throughput")
 * @property current Current value of the metric
 * @property baseline Baseline value for comparison
 * @property change Absolute change (current - baseline)
 * @property changePercent Percentage change ((current - baseline) / baseline * 100)
 * @property direction Change direction (improved/degraded/stable)
 * @property significance Statistical/practical significance level
 */
data class ComparisonResult(
    val metric: String,
    val current: Double,
    val baseline: Double,
    val change: Double,
    val changePercent: Double,
    val direction: ChangeDirection,
    val significance: SignificanceLevel,
) {
    init {
        require(metric.isNotBlank()) {
            "Metric name cannot be blank"
        }
        require(current.isFinite()) {
            "Current value must be finite, got: $current"
        }
        require(baseline.isFinite()) {
            "Baseline value must be finite, got: $baseline"
        }
        require(change.isFinite()) {
            "Change must be finite, got: $change"
        }
        // Verify change calculation is correct
        val expectedChange = current - baseline
        require(abs(change - expectedChange) < 0.001) {
            "Change ($change) doesn't match current - baseline ($expectedChange)"
        }
    }

    /**
     * Absolute value of change
     */
    val absoluteChange: Double
        get() = abs(change)

    /**
     * Absolute percentage change
     */
    val absoluteChangePercent: Double
        get() = abs(changePercent)

    /**
     * Whether this change is considered significant
     *
     * Returns true for MODERATE, HIGH, or VERY_HIGH significance levels.
     */
    val isSignificant: Boolean
        get() = significance.isSignificant

    /**
     * Whether this change is highly significant
     *
     * Returns true for HIGH or VERY_HIGH significance levels.
     */
    val isHighlySignificant: Boolean
        get() = significance in listOf(SignificanceLevel.HIGH, SignificanceLevel.VERY_HIGH)

    /**
     * Whether the metric improved
     */
    val isImprovement: Boolean
        get() = direction == ChangeDirection.IMPROVED

    /**
     * Whether the metric degraded
     */
    val isDegradation: Boolean
        get() = direction == ChangeDirection.DEGRADED

    /**
     * Whether the metric remained stable
     */
    val isStable: Boolean
        get() = direction == ChangeDirection.STABLE

    /**
     * Ratio of current to baseline
     *
     * Values:
     * - > 1.0: Current is higher than baseline
     * - = 1.0: No change
     * - < 1.0: Current is lower than baseline
     *
     * Returns null if baseline is zero.
     */
    val ratio: Double?
        get() = if (baseline != 0.0) current / baseline else null

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("$metric: ${direction.displayName}")
                append(" (%.2f → %.2f, %+.1f%%)".format(baseline, current, changePercent))
                if (isSignificant) {
                    append(" [${significance.displayName}]")
                }
            }

    /**
     * Short description for display
     */
    val shortDescription: String
        get() = "%+.1f%% (%s)".format(changePercent, direction.displayName)

    companion object {
        /**
         * Create ComparisonResult from current and baseline values
         *
         * Automatically calculates change, percentage change, direction, and significance.
         *
         * @param metric Name of the metric
         * @param current Current value
         * @param baseline Baseline value
         * @param higherIsBetter Whether higher values indicate improvement (default: true)
         * @param significanceThresholdPercent Minimum change % for significance (default: 10%)
         * @return ComparisonResult with computed attributes
         */
        fun from(
            metric: String,
            current: Double,
            baseline: Double,
            higherIsBetter: Boolean = true,
            significanceThresholdPercent: Double = 10.0,
        ): ComparisonResult {
            val change = current - baseline
            val changePercent =
                if (baseline != 0.0) {
                    (change / abs(baseline)) * 100.0
                } else if (current != 0.0) {
                    if (current > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
                } else {
                    0.0
                }

            val direction =
                determineDirection(
                    change = change,
                    higherIsBetter = higherIsBetter,
                    threshold = abs(baseline) * 0.05, // 5% of baseline
                )

            val significance =
                determineSignificance(
                    changePercent = abs(changePercent),
                    threshold = significanceThresholdPercent,
                )

            return ComparisonResult(
                metric = metric,
                current = current,
                baseline = baseline,
                change = change,
                changePercent = changePercent,
                direction = direction,
                significance = significance,
            )
        }

        /**
         * Determine change direction
         */
        private fun determineDirection(
            change: Double,
            higherIsBetter: Boolean,
            threshold: Double,
        ): ChangeDirection =
            when {
                abs(change) < threshold -> ChangeDirection.STABLE
                change > 0 && higherIsBetter -> ChangeDirection.IMPROVED
                change > 0 && !higherIsBetter -> ChangeDirection.DEGRADED
                change < 0 && higherIsBetter -> ChangeDirection.DEGRADED
                change < 0 && !higherIsBetter -> ChangeDirection.IMPROVED
                else -> ChangeDirection.UNKNOWN
            }

        /**
         * Determine significance level
         */
        private fun determineSignificance(
            changePercent: Double,
            threshold: Double,
        ): SignificanceLevel =
            when {
                changePercent >= threshold * 5.0 -> SignificanceLevel.VERY_HIGH // >= 50% change
                changePercent >= threshold * 3.0 -> SignificanceLevel.HIGH // >= 30% change
                changePercent >= threshold * 1.5 -> SignificanceLevel.MODERATE // >= 15% change
                changePercent >= threshold -> SignificanceLevel.LOW // >= 10% change
                else -> SignificanceLevel.NONE // < 10% change
            }
    }
}

/**
 * Change direction categories
 *
 * Indicates whether a metric change represents improvement, degradation, or stability.
 * Interpretation depends on whether higher values are better for the specific metric.
 *
 * @property displayName Human-readable direction description
 */
enum class ChangeDirection(
    val displayName: String,
) {
    /**
     * Metric improved
     *
     * Value changed in a favorable direction.
     * Examples:
     * - RSSI increased (stronger signal)
     * - Packet loss decreased
     * - Throughput increased
     */
    IMPROVED("Improved"),

    /**
     * Metric degraded
     *
     * Value changed in an unfavorable direction.
     * Examples:
     * - RSSI decreased (weaker signal)
     * - Packet loss increased
     * - Throughput decreased
     */
    DEGRADED("Degraded"),

    /**
     * Metric stable
     *
     * Value remained essentially unchanged (within threshold).
     * Small variations that aren't practically significant.
     */
    STABLE("Stable"),

    /**
     * Direction unknown
     *
     * Unable to determine if change is favorable or unfavorable.
     */
    UNKNOWN("Unknown"),
    ;

    /**
     * Whether this direction indicates improvement
     */
    val isPositive: Boolean
        get() = this == IMPROVED

    /**
     * Whether this direction indicates degradation
     */
    val isNegative: Boolean
        get() = this == DEGRADED

    /**
     * Whether this indicates no meaningful change
     */
    val isNeutral: Boolean
        get() = this in listOf(STABLE, UNKNOWN)
}

/**
 * Significance level categories
 *
 * Indicates the magnitude and importance of a change.
 * Based on percentage change thresholds.
 *
 * @property displayName Human-readable significance description
 * @property minChangePercent Minimum absolute % change for this level
 */
enum class SignificanceLevel(
    val displayName: String,
    val minChangePercent: Double,
) {
    /**
     * Very high significance (>= 50% change)
     *
     * Extremely large change that definitely requires attention.
     */
    VERY_HIGH(
        displayName = "Very High",
        minChangePercent = 50.0,
    ),

    /**
     * High significance (>= 30% change)
     *
     * Large change that warrants investigation and action.
     */
    HIGH(
        displayName = "High",
        minChangePercent = 30.0,
    ),

    /**
     * Moderate significance (>= 15% change)
     *
     * Noticeable change that may need attention.
     */
    MODERATE(
        displayName = "Moderate",
        minChangePercent = 15.0,
    ),

    /**
     * Low significance (>= 10% change)
     *
     * Detectable change but may not be practically important.
     */
    LOW(
        displayName = "Low",
        minChangePercent = 10.0,
    ),

    /**
     * No significance (< 10% change)
     *
     * Negligible change, likely due to normal variation.
     */
    NONE(
        displayName = "None",
        minChangePercent = 0.0,
    ),
    ;

    /**
     * Whether this level indicates significant change
     *
     * Returns true for LOW, MODERATE, HIGH, or VERY_HIGH.
     */
    val isSignificant: Boolean
        get() = this != NONE

    /**
     * Whether this level indicates actionable change
     *
     * Returns true for MODERATE, HIGH, or VERY_HIGH.
     */
    val isActionable: Boolean
        get() = this in listOf(MODERATE, HIGH, VERY_HIGH)

    companion object {
        /**
         * Determine significance level from percentage change
         *
         * @param changePercent Absolute percentage change
         * @return Corresponding SignificanceLevel
         */
        fun fromChangePercent(changePercent: Double): SignificanceLevel {
            val absChange = abs(changePercent)
            return entries.first { absChange >= it.minChangePercent }
        }
    }
}

/**
 * Benchmark result against optimal performance profile
 *
 * Compares network performance to best-in-class or theoretical optimal values.
 * Identifies performance gaps and areas for improvement.
 *
 * @property score Overall benchmark score (0-100)
 * @property gaps List of specific performance gaps
 * @property strengths List of strong performance areas
 * @property recommendations List of improvement recommendations
 */
data class BenchmarkResult(
    val score: Double,
    val gaps: List<PerformanceGap>,
    val strengths: List<String>,
    val recommendations: List<String>,
) {
    init {
        require(score in 0.0..100.0) {
            "Score must be in [0, 100], got: $score"
        }
    }

    /**
     * Benchmark grade based on score
     */
    val grade: BenchmarkGrade
        get() =
            when {
                score >= 90.0 -> BenchmarkGrade.EXCELLENT
                score >= 75.0 -> BenchmarkGrade.GOOD
                score >= 60.0 -> BenchmarkGrade.FAIR
                score >= 40.0 -> BenchmarkGrade.POOR
                else -> BenchmarkGrade.CRITICAL
            }

    /**
     * Number of significant performance gaps
     */
    val criticalGapCount: Int
        get() = gaps.count { it.priority.isCritical }

    /**
     * Whether performance is acceptable
     */
    val isAcceptable: Boolean
        get() = score >= 60.0

    /**
     * Whether performance is excellent
     */
    val isExcellent: Boolean
        get() = score >= 90.0

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("Benchmark: ${score.toInt()}/100 (${grade.displayName})")
                if (gaps.isNotEmpty()) {
                    append(" - ${gaps.size} gaps")
                }
                if (criticalGapCount > 0) {
                    append(" [$criticalGapCount critical]")
                }
            }
}

/**
 * Performance gap identified in benchmarking
 *
 * Represents a specific area where current performance falls short of optimal.
 *
 * @property metric Name of the metric
 * @property current Current value
 * @property optimal Optimal/target value
 * @property gapSize Magnitude of gap (optimal - current, or percentage)
 * @property priority Priority level for addressing this gap
 */
data class PerformanceGap(
    val metric: String,
    val current: Double,
    val optimal: Double,
    val gapSize: Double,
    val priority: Priority,
) {
    init {
        require(metric.isNotBlank()) {
            "Metric name cannot be blank"
        }
    }

    /**
     * Gap as percentage of optimal
     */
    val gapPercent: Double
        get() =
            if (optimal != 0.0) {
                abs(gapSize / optimal) * 100.0
            } else {
                0.0
            }

    /**
     * Current performance as percentage of optimal
     */
    val achievedPercent: Double
        get() =
            if (optimal != 0.0) {
                (current / optimal) * 100.0
            } else if (current == 0.0) {
                100.0
            } else {
                0.0
            }

    /**
     * Human-readable description
     */
    val description: String
        get() =
            buildString {
                append("$metric: %.2f vs optimal %.2f".format(current, optimal))
                append(" (%.1f%% gap) [${priority.displayName}]".format(gapPercent))
            }
}

/**
 * Benchmark grade categories
 *
 * @property displayName Human-readable grade description
 */
enum class BenchmarkGrade(
    val displayName: String,
) {
    /** Excellent performance (>= 90%) */
    EXCELLENT("Excellent"),

    /** Good performance (>= 75%) */
    GOOD("Good"),

    /** Fair performance (>= 60%) */
    FAIR("Fair"),

    /** Poor performance (>= 40%) */
    POOR("Poor"),

    /** Critical performance issues (< 40%) */
    CRITICAL("Critical"),
}

/**
 * Priority level for performance gaps
 *
 * Indicates urgency of addressing a performance gap.
 *
 * @property displayName Human-readable priority description
 */
enum class Priority(
    val displayName: String,
) {
    /** Critical - immediate action required */
    CRITICAL("Critical"),

    /** High - address soon */
    HIGH("High"),

    /** Medium - address when possible */
    MEDIUM("Medium"),

    /** Low - nice to have */
    LOW("Low"),
    ;

    /**
     * Whether this priority level is critical
     */
    val isCritical: Boolean
        get() = this == CRITICAL

    /**
     * Whether this priority requires urgent action
     */
    val isUrgent: Boolean
        get() = this in listOf(CRITICAL, HIGH)
}

/**
 * Temporal comparison between two time periods
 *
 * Compares network metrics between different time windows (e.g., this week vs last week).
 *
 * @property period1Label Label for first period (e.g., "Last Week")
 * @property period2Label Label for second period (e.g., "This Week")
 * @property comparisons List of metric comparisons between periods
 */
data class TemporalComparison(
    val period1Label: String,
    val period2Label: String,
    val comparisons: List<ComparisonResult>,
) {
    init {
        require(period1Label.isNotBlank()) {
            "Period 1 label cannot be blank"
        }
        require(period2Label.isNotBlank()) {
            "Period 2 label cannot be blank"
        }
        require(comparisons.isNotEmpty()) {
            "Comparisons list cannot be empty"
        }
    }

    /**
     * Number of metrics that improved
     */
    val improvedCount: Int
        get() = comparisons.count { it.isImprovement }

    /**
     * Number of metrics that degraded
     */
    val degradedCount: Int
        get() = comparisons.count { it.isDegradation }

    /**
     * Number of metrics that remained stable
     */
    val stableCount: Int
        get() = comparisons.count { it.isStable }

    /**
     * Overall trend (improved/degraded/stable)
     */
    val overallTrend: ChangeDirection
        get() =
            when {
                improvedCount > degradedCount && improvedCount > stableCount -> ChangeDirection.IMPROVED
                degradedCount > improvedCount && degradedCount > stableCount -> ChangeDirection.DEGRADED
                else -> ChangeDirection.STABLE
            }

    /**
     * Whether there are significant changes
     */
    val hasSignificantChanges: Boolean
        get() = comparisons.any { it.isSignificant }

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("$period1Label → $period2Label: ")
                append("$improvedCount improved, $degradedCount degraded, $stableCount stable")
                append(" (Overall: ${overallTrend.displayName})")
            }
}
