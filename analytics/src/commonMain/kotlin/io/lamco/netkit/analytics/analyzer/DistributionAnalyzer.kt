package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import kotlin.math.*

/**
 * Statistical distribution analysis engine
 *
 * Analyzes the statistical distribution of WiFi metrics including:
 * - Histogram generation with automatic or manual binning
 * - Probability density estimation using Kernel Density Estimation (KDE)
 * - Distribution shape detection (normal, bimodal, skewed, uniform)
 * - Comprehensive statistical characterization
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = DistributionAnalyzer()
 *
 * // Generate histogram
 * val hist = analyzer.histogram(rssiValues, bins = 20)
 *
 * // Characterize distribution
 * val dist = analyzer.characterizeDistribution(rssiValues)
 * println("Shape: ${dist.shape}, Skewness: ${dist.skewness}")
 *
 * // Estimate probability density
 * val pdf = analyzer.estimateDensity(rssiValues)
 * ```
 */
class DistributionAnalyzer {
    /**
     * Create histogram from data values
     *
     * Automatically determines optimal bin count using Sturges' rule if not specified.
     *
     * @param values Data values to bin
     * @param bins Number of bins (if null, calculated automatically)
     * @return Histogram with bins and frequencies
     */
    fun histogram(
        values: List<Double>,
        bins: Int? = null,
    ): Histogram {
        require(values.isNotEmpty()) {
            "Values list cannot be empty"
        }

        val binCount = bins ?: calculateOptimalBinCount(values.size)
        require(binCount > 0) {
            "Bin count must be positive, got: $binCount"
        }

        val sorted = values.sorted()
        val min = sorted.first()
        val max = sorted.last()

        // Handle case where all values are equal
        if (min == max) {
            return Histogram(
                bins =
                    listOf(
                        HistogramBin(
                            lowerBound = min - 0.5,
                            upperBound = max + 0.5,
                            count = values.size,
                            frequency = 1.0,
                        ),
                    ),
                binWidth = 1.0,
                totalCount = values.size,
            )
        }

        val range = max - min
        val binWidth = range / binCount

        // Create bins
        val binCounts = IntArray(binCount)
        values.forEach { value ->
            val binIndex = ((value - min) / binWidth).toInt().coerceIn(0, binCount - 1)
            binCounts[binIndex]++
        }

        val histogramBins =
            (0 until binCount).map { i ->
                val lowerBound = min + i * binWidth
                val upperBound = if (i == binCount - 1) max else lowerBound + binWidth
                val count = binCounts[i]
                val frequency = count.toDouble() / values.size

                HistogramBin(lowerBound, upperBound, count, frequency)
            }

        return Histogram(
            bins = histogramBins,
            binWidth = binWidth,
            totalCount = values.size,
        )
    }

    /**
     * Estimate probability density function using Kernel Density Estimation
     *
     * Uses Gaussian kernel for smooth density estimation.
     *
     * @param values Data values
     * @param bandwidth KDE bandwidth (if null, calculated using Silverman's rule)
     * @param points Number of points to evaluate density at (default: 100)
     * @return Probability density function
     */
    fun estimateDensity(
        values: List<Double>,
        bandwidth: Double? = null,
        points: Int = 100,
    ): ProbabilityDensity {
        require(values.isNotEmpty()) {
            "Values list cannot be empty"
        }
        require(points > 0) {
            "Number of points must be positive, got: $points"
        }

        val bw = bandwidth ?: calculateSilvermanBandwidth(values)
        require(bw > 0.0) {
            "Bandwidth must be positive, got: $bw"
        }

        val sorted = values.sorted()
        val min = sorted.first()
        val max = sorted.last()

        // Extend range slightly for smoother edges
        val range = max - min
        val extendedMin = min - range * 0.1
        val extendedMax = max + range * 0.1
        val step = (extendedMax - extendedMin) / (points - 1)

        // Evaluate density at each point
        val densityPoints =
            (0 until points).map { i ->
                val x = extendedMin + i * step
                val density = estimateDensityAt(x, values, bw)
                DensityPoint(x, density)
            }

        return ProbabilityDensity(
            points = densityPoints,
            bandwidth = bw,
        )
    }

    /**
     * Characterize statistical distribution
     *
     * Computes comprehensive statistics including central tendency, dispersion,
     * and shape characteristics.
     *
     * @param values Data values to analyze
     * @return Distribution characteristics
     */
    fun characterizeDistribution(values: List<Double>): DistributionCharacteristics = DistributionCharacteristics.fromValues(values)

    /**
     * Test if distribution appears normal (Gaussian)
     *
     * Performs multiple checks including skewness, kurtosis, and mean/median similarity.
     *
     * @param values Data values
     * @return True if distribution appears approximately normal
     */
    fun isNormallyDistributed(values: List<Double>): Boolean {
        val dist = characterizeDistribution(values)
        return dist.appearsNormal
    }

    /**
     * Detect outliers using IQR method
     *
     * Values beyond Q1 - 1.5*IQR or Q3 + 1.5*IQR are considered outliers.
     *
     * @param values Data values
     * @param iqrMultiplier IQR multiplier for outlier threshold (default: 1.5)
     * @return List of outlier values
     */
    fun detectOutliers(
        values: List<Double>,
        iqrMultiplier: Double = 1.5,
    ): List<Double> {
        require(values.size >= 4) {
            "Need at least 4 values for outlier detection, got: ${values.size}"
        }
        require(iqrMultiplier > 0.0) {
            "IQR multiplier must be positive, got: $iqrMultiplier"
        }

        val sorted = values.sorted()
        val q1Index = (sorted.size * 0.25).toInt()
        val q3Index = (sorted.size * 0.75).toInt()

        val q1 = sorted[q1Index]
        val q3 = sorted[q3Index]
        val iqr = q3 - q1

        val lowerBound = q1 - iqrMultiplier * iqr
        val upperBound = q3 + iqrMultiplier * iqr

        return values.filter { it < lowerBound || it > upperBound }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Calculate optimal bin count using Sturges' rule
     */
    private fun calculateOptimalBinCount(n: Int): Int = max(1, (1 + log2(n.toDouble())).toInt())

    /**
     * Calculate bandwidth using Silverman's rule of thumb
     *
     * h = 0.9 * min(σ, IQR/1.34) * n^(-1/5)
     */
    private fun calculateSilvermanBandwidth(values: List<Double>): Double {
        val n = values.size
        val sorted = values.sorted()

        // Calculate standard deviation
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        // Calculate IQR
        val q1Index = (n * 0.25).toInt()
        val q3Index = (n * 0.75).toInt()
        val iqr = sorted[q3Index] - sorted[q1Index]

        // Silverman's rule
        val minSigma = min(stdDev, iqr / 1.34)
        return 0.9 * minSigma * n.toDouble().pow(-0.2)
    }

    /**
     * Estimate density at a specific point using Gaussian kernel
     */
    private fun estimateDensityAt(
        x: Double,
        values: List<Double>,
        bandwidth: Double,
    ): Double {
        val n = values.size
        val sum =
            values.sumOf { xi ->
                gaussianKernel((x - xi) / bandwidth)
            }

        return sum / (n * bandwidth)
    }

    /**
     * Gaussian kernel function
     */
    private fun gaussianKernel(u: Double): Double = (1.0 / sqrt(2.0 * PI)) * exp(-0.5 * u * u)
}
