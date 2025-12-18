package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import kotlin.math.abs

/**
 * Comparative analysis engine for network benchmarking
 *
 * Provides tools for comparing current network performance against baselines,
 * historical data, and optimal benchmarks. Identifies performance gaps and
 * generates actionable recommendations.
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = ComparativeAnalyzer()
 *
 * // Compare to baseline
 * val comparison = analyzer.compareToBaseline(
 *     current = currentMetrics,
 *     baseline = historicalBaseline
 * )
 *
 * // Benchmark against optimal
 * val benchmark = analyzer.benchmarkAgainstOptimal(
 *     network = currentMetrics,
 *     optimalProfile = OptimalNetworkProfile.ENTERPRISE_WIFI
 * )
 *
 * // Compare time periods
 * val temporal = analyzer.compareTimePeriods(lastWeek, thisWeek)
 * ```
 */
class ComparativeAnalyzer {
    /**
     * Compare current metrics to baseline
     *
     * Analyzes all metrics and determines change direction, magnitude, and significance.
     *
     * @param current Current metric values
     * @param baseline Baseline metric values for comparison
     * @param higherIsBetter Map of metric name to whether higher values are better (default: true for all)
     * @return List of comparison results, one per metric
     */
    fun compareToBaseline(
        current: Map<String, Double>,
        baseline: Map<String, Double>,
        higherIsBetter: Map<String, Boolean> = emptyMap(),
    ): List<ComparisonResult> {
        require(current.isNotEmpty()) {
            "Current metrics cannot be empty"
        }
        require(baseline.isNotEmpty()) {
            "Baseline metrics cannot be empty"
        }

        // Find common metrics
        val commonMetrics = current.keys.intersect(baseline.keys)
        require(commonMetrics.isNotEmpty()) {
            "No common metrics between current and baseline"
        }

        return commonMetrics.map { metric ->
            val currentValue = current[metric]!!
            val baselineValue = baseline[metric]!!
            val isHigherBetter = higherIsBetter[metric] ?: true

            ComparisonResult.from(
                metric = metric,
                current = currentValue,
                baseline = baselineValue,
                higherIsBetter = isHigherBetter,
            )
        }
    }

    /**
     * Benchmark network against optimal performance profile
     *
     * Compares current metrics to best-in-class or theoretical optimal values.
     * Identifies gaps and provides recommendations.
     *
     * @param network Current network metrics
     * @param optimalProfile Optimal performance profile to compare against
     * @return Benchmark result with score, gaps, and recommendations
     */
    fun benchmarkAgainstOptimal(
        network: Map<String, Double>,
        optimalProfile: OptimalNetworkProfile,
    ): BenchmarkResult {
        require(network.isNotEmpty()) {
            "Network metrics cannot be empty"
        }

        val gaps = mutableListOf<PerformanceGap>()
        var scoreSum = 0.0
        var metricCount = 0

        optimalProfile.optimalValues.forEach { (metric, optimalValue) ->
            val currentValue = network[metric] ?: return@forEach

            val higherIsBetter = optimalProfile.higherIsBetter[metric] ?: true
            val achieved = calculateAchievement(currentValue, optimalValue, higherIsBetter)

            scoreSum += achieved * 100.0
            metricCount++

            // Identify gap if performance is below threshold
            if (achieved < 0.9) {
                val gapSize = abs(optimalValue - currentValue)
                val priority =
                    when {
                        achieved < 0.5 -> Priority.CRITICAL
                        achieved < 0.7 -> Priority.HIGH
                        achieved < 0.85 -> Priority.MEDIUM
                        else -> Priority.LOW
                    }

                gaps.add(
                    PerformanceGap(
                        metric = metric,
                        current = currentValue,
                        optimal = optimalValue,
                        gapSize = gapSize,
                        priority = priority,
                    ),
                )
            }
        }

        val overallScore = if (metricCount > 0) scoreSum / metricCount else 0.0

        val strengths = identifyStrengths(network, optimalProfile)
        val recommendations = generateRecommendations(gaps, optimalProfile)

        return BenchmarkResult(
            score = overallScore,
            gaps = gaps.sortedByDescending { it.priority.ordinal },
            strengths = strengths,
            recommendations = recommendations,
        )
    }

    /**
     * Compare two time periods
     *
     * Analyzes changes between two time periods and determines overall trend.
     *
     * @param period1Metrics Metrics from first period
     * @param period2Metrics Metrics from second period
     * @param period1Label Label for first period (e.g., "Last Week")
     * @param period2Label Label for second period (e.g., "This Week")
     * @param higherIsBetter Map of metric to whether higher is better
     * @return Temporal comparison result
     */
    fun compareTimePeriods(
        period1Metrics: Map<String, Double>,
        period2Metrics: Map<String, Double>,
        period1Label: String = "Period 1",
        period2Label: String = "Period 2",
        higherIsBetter: Map<String, Boolean> = emptyMap(),
    ): TemporalComparison {
        val comparisons =
            compareToBaseline(
                current = period2Metrics,
                baseline = period1Metrics,
                higherIsBetter = higherIsBetter,
            )

        return TemporalComparison(
            period1Label = period1Label,
            period2Label = period2Label,
            comparisons = comparisons,
        )
    }

    /**
     * Calculate network health score
     *
     * Aggregates multiple metrics into a single health score (0-100).
     *
     * @param metrics Current network metrics
     * @param weights Optional weights for each metric (default: equal weight)
     * @return Health score from 0 (critical) to 100 (excellent)
     */
    fun calculateHealthScore(
        metrics: Map<String, Double>,
        weights: Map<String, Double> = emptyMap(),
    ): Double {
        require(metrics.isNotEmpty()) {
            "Metrics cannot be empty"
        }

        // Normalize each metric to 0-100 scale
        val normalizedScores =
            metrics.mapValues { (metric, value) ->
                normalizeMetricToScore(metric, value)
            }

        // Calculate weighted average
        val totalWeight =
            if (weights.isEmpty()) {
                metrics.size.toDouble()
            } else {
                weights.values.sum()
            }

        val weightedSum =
            normalizedScores
                .map { (metric, score) ->
                    val weight = weights[metric] ?: 1.0
                    score * weight
                }.sum()

        return (weightedSum / totalWeight).coerceIn(0.0, 100.0)
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Calculate achievement fraction (0.0-1.0) for a metric value vs optimal
     *
     * Handles both positive and negative values correctly (e.g., RSSI where
     * higher is better but values are negative).
     */
    private fun calculateAchievement(
        current: Double,
        optimal: Double,
        higherIsBetter: Boolean,
    ): Double =
        if (higherIsBetter) {
            if (current >= optimal) {
                1.0
            } else if (optimal > 0) {
                // Positive values: simple ratio
                (current / optimal)
            } else {
                // Negative values (e.g., RSSI): use reference point
                // Assume worst case is 2x the optimal value (e.g., -100 for optimal -50)
                val worstCase = optimal * 2
                (current - worstCase) / (optimal - worstCase)
            }
        } else {
            if (current <= optimal) {
                1.0
            } else if (current > 0) {
                (optimal / current)
            } else {
                0.0
            }
        }.coerceIn(0.0, 1.0)

    /**
     * Identify performance strengths
     */
    private fun identifyStrengths(
        network: Map<String, Double>,
        profile: OptimalNetworkProfile,
    ): List<String> {
        val strengths = mutableListOf<String>()

        profile.optimalValues.forEach { (metric, optimalValue) ->
            val currentValue = network[metric] ?: return@forEach
            val higherIsBetter = profile.higherIsBetter[metric] ?: true

            val achieved = calculateAchievement(currentValue, optimalValue, higherIsBetter)

            if (achieved >= 0.95) {
                strengths.add("Excellent $metric performance (${(achieved * 100).toInt()}% of optimal)")
            }
        }

        return strengths
    }

    /**
     * Generate improvement recommendations based on gaps
     */
    private fun generateRecommendations(
        gaps: List<PerformanceGap>,
        profile: OptimalNetworkProfile,
    ): List<String> {
        val recommendations = mutableListOf<String>()

        gaps.filter { it.priority.isUrgent }.forEach { gap ->
            val recommendation =
                when (gap.metric) {
                    "rssi" -> "Improve signal strength: reduce AP distance or add more APs"
                    "snr" -> "Reduce interference: check channel allocation and neighboring networks"
                    "throughput" -> "Optimize capacity: upgrade to WiFi 6/6E or add more bandwidth"
                    "latency" -> "Reduce latency: check backhaul connection and AP load"
                    "packet_loss" -> "Address packet loss: check RF conditions and AP stability"
                    else -> "Investigate ${gap.metric} performance gap"
                }
            recommendations.add(recommendation)
        }

        return recommendations.distinct()
    }

    /**
     * Normalize metric value to 0-100 score
     */
    private fun normalizeMetricToScore(
        metric: String,
        value: Double,
    ): Double {
        // Simplified normalization - in production, use metric-specific ranges
        return when {
            metric.contains("rssi", ignoreCase = true) -> {
                // RSSI: -90 (poor) to -30 (excellent)
                ((value + 90.0) / 60.0 * 100.0).coerceIn(0.0, 100.0)
            }
            metric.contains("snr", ignoreCase = true) -> {
                // SNR: 0 (poor) to 60 (excellent)
                (value / 60.0 * 100.0).coerceIn(0.0, 100.0)
            }
            metric.contains("throughput", ignoreCase = true) -> {
                // Throughput: assume 100 Mbps is 100%
                (value / 100.0 * 100.0).coerceIn(0.0, 100.0)
            }
            else -> {
                // Generic 0-100 normalization
                value.coerceIn(0.0, 100.0)
            }
        }
    }
}

/**
 * Optimal network performance profile
 *
 * Defines target values for various network metrics representing
 * best-in-class or theoretical optimal performance.
 *
 * @property name Profile name (e.g., "Enterprise WiFi 6E")
 * @property optimalValues Target values for each metric
 * @property higherIsBetter Whether higher values are better for each metric
 */
data class OptimalNetworkProfile(
    val name: String,
    val optimalValues: Map<String, Double>,
    val higherIsBetter: Map<String, Boolean> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) {
            "Profile name cannot be blank"
        }
        require(optimalValues.isNotEmpty()) {
            "Optimal values cannot be empty"
        }
    }

    companion object {
        /**
         * Enterprise WiFi 6E optimal profile
         */
        val ENTERPRISE_WIFI_6E =
            OptimalNetworkProfile(
                name = "Enterprise WiFi 6E",
                optimalValues =
                    mapOf(
                        "rssi" to -50.0, // dBm
                        "snr" to 50.0, // dB
                        "throughput" to 500.0, // Mbps
                        "latency" to 5.0, // ms
                        "packet_loss" to 0.1, // %
                    ),
                higherIsBetter =
                    mapOf(
                        "rssi" to true,
                        "snr" to true,
                        "throughput" to true,
                        "latency" to false,
                        "packet_loss" to false,
                    ),
            )

        /**
         * Consumer WiFi 5 optimal profile
         */
        val CONSUMER_WIFI_5 =
            OptimalNetworkProfile(
                name = "Consumer WiFi 5",
                optimalValues =
                    mapOf(
                        "rssi" to -60.0,
                        "snr" to 35.0,
                        "throughput" to 200.0,
                        "latency" to 10.0,
                        "packet_loss" to 0.5,
                    ),
                higherIsBetter =
                    mapOf(
                        "rssi" to true,
                        "snr" to true,
                        "throughput" to true,
                        "latency" to false,
                        "packet_loss" to false,
                    ),
            )
    }
}
