package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import kotlin.math.*

/**
 * Correlation analysis engine for multi-dimensional WiFi data
 *
 * Analyzes relationships between different WiFi metrics using various correlation methods.
 * Supports Pearson (linear), Spearman (monotonic), and Kendall (rank-based) correlations.
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = CorrelationAnalyzer()
 *
 * // Calculate correlation between RSSI and throughput
 * val result = analyzer.calculateCorrelation(
 *     x = rssiValues,
 *     y = throughputValues,
 *     method = CorrelationMethod.PEARSON
 * )
 * println("Correlation: ${result.coefficient} (p=${result.pValue})")
 *
 * // Build correlation matrix for multiple metrics
 * val matrix = analyzer.buildCorrelationMatrix(allMetrics)
 * val significant = analyzer.findSignificantCorrelations(matrix)
 * ```
 */
class CorrelationAnalyzer {
    /**
     * Calculate correlation between two variables
     *
     * @param x First variable values
     * @param y Second variable values (must be same length as x)
     * @param method Correlation method (PEARSON, SPEARMAN, KENDALL)
     * @return Correlation result with coefficient, p-value, and interpretation
     */
    fun calculateCorrelation(
        x: List<Double>,
        y: List<Double>,
        method: CorrelationMethod = CorrelationMethod.PEARSON,
    ): CorrelationResult {
        require(x.size == y.size) {
            "Variables must have same length: x=${x.size}, y=${y.size}"
        }
        require(x.size >= method.requiresMinimumSampleSize) {
            "Need at least ${method.requiresMinimumSampleSize} samples for ${method.displayName}, got: ${x.size}"
        }

        val coefficient =
            when (method) {
                CorrelationMethod.PEARSON -> calculatePearsonCorrelation(x, y)
                CorrelationMethod.SPEARMAN -> calculateSpearmanCorrelation(x, y)
                CorrelationMethod.KENDALL -> calculateKendallCorrelation(x, y)
            }

        val pValue = calculatePValue(coefficient, x.size, method)

        return CorrelationResult.from(coefficient, pValue, method)
    }

    /**
     * Build correlation matrix for all metric pairs
     *
     * Computes pairwise correlations between all metrics and returns a matrix.
     *
     * @param data Map of metric name to values
     * @param method Correlation method to use for all pairs
     * @return Correlation matrix containing all pairwise correlations
     */
    fun buildCorrelationMatrix(
        data: Map<String, List<Double>>,
        method: CorrelationMethod = CorrelationMethod.PEARSON,
    ): CorrelationMatrix {
        require(data.size >= 2) {
            "Need at least 2 metrics for correlation matrix, got: ${data.size}"
        }

        val sampleSizes = data.values.map { it.size }.toSet()
        require(sampleSizes.size == 1) {
            "All metrics must have same sample size, got: $sampleSizes"
        }

        val metricNames = data.keys.toList()
        val correlations = mutableMapOf<Pair<String, String>, CorrelationResult>()

        for (i in metricNames.indices) {
            for (j in i + 1 until metricNames.size) {
                val metric1 = metricNames[i]
                val metric2 = metricNames[j]

                val result =
                    calculateCorrelation(
                        x = data[metric1]!!,
                        y = data[metric2]!!,
                        method = method,
                    )

                correlations[Pair(metric1, metric2)] = result
            }
        }

        return CorrelationMatrix(
            metricNames = metricNames,
            correlations = correlations,
            method = method,
        )
    }

    /**
     * Find significant correlations in a matrix
     *
     * Filters correlation matrix to return only statistically significant correlations.
     *
     * @param matrix Correlation matrix to analyze
     * @param threshold P-value threshold for significance (default: 0.05)
     * @return List of significant correlation pairs
     */
    fun findSignificantCorrelations(
        matrix: CorrelationMatrix,
        threshold: Double = 0.05,
    ): List<CorrelationPair> {
        require(threshold in 0.0..1.0) {
            "Threshold must be in [0, 1], got: $threshold"
        }

        return matrix.correlations
            .filter { (_, result) -> result.pValue < threshold }
            .map { (pair, result) ->
                CorrelationPair(
                    metric1 = pair.first,
                    metric2 = pair.second,
                    result = result,
                )
            }.sortedBy { it.result.pValue }
    }

    /**
     * Find strong correlations in a matrix
     *
     * Filters correlation matrix to return only strong correlations (|r| >= threshold).
     *
     * @param matrix Correlation matrix to analyze
     * @param strengthThreshold Minimum absolute correlation coefficient (default: 0.7)
     * @return List of strong correlation pairs
     */
    fun findStrongCorrelations(
        matrix: CorrelationMatrix,
        strengthThreshold: Double = 0.7,
    ): List<CorrelationPair> {
        require(strengthThreshold in 0.0..1.0) {
            "Threshold must be in [0, 1], got: $strengthThreshold"
        }

        return matrix.correlations
            .filter { (_, result) -> abs(result.coefficient) >= strengthThreshold }
            .map { (pair, result) ->
                CorrelationPair(
                    metric1 = pair.first,
                    metric2 = pair.second,
                    result = result,
                )
            }.sortedByDescending { abs(it.result.coefficient) }
    }

    // ========================================
    // Private Correlation Calculations
    // ========================================

    /**
     * Calculate Pearson product-moment correlation
     *
     * Measures linear relationship between two continuous variables.
     * Formula: r = Σ[(xi - x̄)(yi - ȳ)] / √[Σ(xi - x̄)² Σ(yi - ȳ)²]
     */
    private fun calculatePearsonCorrelation(
        x: List<Double>,
        y: List<Double>,
    ): Double {
        val n = x.size
        val xMean = x.average()
        val yMean = y.average()

        var numerator = 0.0
        var xVariance = 0.0
        var yVariance = 0.0

        for (i in x.indices) {
            val xDiff = x[i] - xMean
            val yDiff = y[i] - yMean
            numerator += xDiff * yDiff
            xVariance += xDiff * xDiff
            yVariance += yDiff * yDiff
        }

        val denominator = sqrt(xVariance * yVariance)

        return if (denominator > 0.0) {
            (numerator / denominator).coerceIn(-1.0, 1.0)
        } else {
            0.0
        }
    }

    /**
     * Calculate Spearman rank-order correlation
     *
     * Measures monotonic relationship using ranks instead of raw values.
     * More robust to outliers than Pearson.
     */
    private fun calculateSpearmanCorrelation(
        x: List<Double>,
        y: List<Double>,
    ): Double {
        val xRanks = assignRanks(x)
        val yRanks = assignRanks(y)

        return calculatePearsonCorrelation(xRanks, yRanks)
    }

    /**
     * Calculate Kendall tau rank correlation
     *
     * Counts concordant and discordant pairs.
     * More robust for small samples than Spearman.
     */
    private fun calculateKendallCorrelation(
        x: List<Double>,
        y: List<Double>,
    ): Double {
        val n = x.size
        var concordant = 0
        var discordant = 0

        for (i in 0 until n - 1) {
            for (j in i + 1 until n) {
                val xDiff = x[j] - x[i]
                val yDiff = y[j] - y[i]

                when {
                    xDiff * yDiff > 0 -> concordant++
                    xDiff * yDiff < 0 -> discordant++
                    // Ties don't count
                }
            }
        }

        val totalPairs = n * (n - 1) / 2
        return if (totalPairs > 0) {
            (concordant - discordant).toDouble() / totalPairs
        } else {
            0.0
        }
    }

    /**
     * Assign ranks to values (handling ties with average rank)
     */
    private fun assignRanks(values: List<Double>): List<Double> {
        val indexed = values.mapIndexed { index, value -> index to value }
        val sorted = indexed.sortedBy { it.second }

        val ranks = DoubleArray(values.size)

        var i = 0
        while (i < sorted.size) {
            val currentValue = sorted[i].second
            var j = i

            while (j < sorted.size && sorted[j].second == currentValue) {
                j++
            }

            val avgRank = (i + j - 1) / 2.0 + 1.0 // +1 because ranks start at 1
            for (k in i until j) {
                ranks[sorted[k].first] = avgRank
            }

            i = j
        }

        return ranks.toList()
    }

    /**
     * Calculate approximate p-value for correlation coefficient
     *
     * Uses Student's t-distribution for Pearson/Spearman.
     * Simplified approximation for demonstration purposes.
     */
    private fun calculatePValue(
        r: Double,
        n: Int,
        method: CorrelationMethod,
    ): Double {
        if (abs(r) >= 0.9999) {
            return if (abs(r) == 1.0) 0.0 else 0.0001
        }

        val t = r * sqrt((n - 2).toDouble() / (1 - r * r))

        // Approximate p-value using simplified t-distribution
        // This is a rough approximation; exact calculation requires t-distribution tables
        val pValue =
            when (method) {
                CorrelationMethod.PEARSON, CorrelationMethod.SPEARMAN -> {
                    // Two-tailed test
                    2.0 * (1.0 - approximateTCDF(abs(t), n - 2))
                }
                CorrelationMethod.KENDALL -> {
                    // Simplified normal approximation for Kendall
                    val z = r * sqrt(n * (n - 1).toDouble() / 2.0)
                    2.0 * (1.0 - approximateNormalCDF(abs(z)))
                }
            }

        return pValue.coerceIn(0.0, 1.0)
    }

    /**
     * Approximate cumulative distribution function for t-distribution
     *
     * Simplified approximation using normal distribution for large df.
     */
    private fun approximateTCDF(
        t: Double,
        df: Int,
    ): Double {
        // For large degrees of freedom, t-distribution approximates normal
        if (df > 30) {
            return approximateNormalCDF(t)
        }

        // Simplified approximation for smaller df
        val x = df / (df + t * t)
        return 1.0 - 0.5 * x.pow(df / 2.0)
    }

    /**
     * Approximate cumulative distribution function for standard normal
     *
     * Uses error function approximation.
     */
    private fun approximateNormalCDF(z: Double): Double {
        // Abramowitz and Stegun approximation
        val t = 1.0 / (1.0 + 0.2316419 * abs(z))
        val d = 0.3989423 * exp(-z * z / 2.0)

        val probability =
            d *
                t *
                (
                    0.3193815 +
                        t *
                        (
                            -0.3565638 +
                                t *
                                (
                                    1.781478 +
                                        t *
                                        (
                                            -1.821256 +
                                                t *
                                                1.330274
                                        )
                                )
                        )
                )

        return if (z > 0.0) 1.0 - probability else probability
    }
}
