package io.lamco.netkit.analytics.model

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Statistical distribution characteristics
 *
 * Comprehensive statistical description of a metric's distribution including measures
 * of central tendency, dispersion, and shape. Used to understand the underlying
 * distribution of WiFi network metrics.
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = DistributionAnalyzer()
 * val dist = analyzer.characterizeDistribution(rssiValues)
 * println("Distribution shape: ${dist.shape}")
 * println("Skewness: ${dist.skewness} (${dist.skewnessDescription})")
 * println("Is normal: ${dist.appearsNormal}")
 * ```
 *
 * @property mean Arithmetic mean (average)
 * @property median Middle value (50th percentile)
 * @property mode Most frequently occurring value (null if no clear mode)
 * @property variance Variance (average squared deviation from mean)
 * @property skewness Measure of asymmetry (-∞ to +∞)
 * @property kurtosis Measure of tail heaviness (-∞ to +∞)
 * @property shape Inferred distribution shape
 */
data class DistributionCharacteristics(
    val mean: Double,
    val median: Double,
    val mode: Double?,
    val variance: Double,
    val skewness: Double,
    val kurtosis: Double,
    val shape: DistributionShape,
) {
    init {
        require(variance >= 0.0) {
            "Variance must be non-negative, got: $variance"
        }
        require(mean.isFinite()) {
            "Mean must be finite, got: $mean"
        }
        require(median.isFinite()) {
            "Median must be finite, got: $median"
        }
        require(skewness.isFinite()) {
            "Skewness must be finite, got: $skewness"
        }
        require(kurtosis.isFinite()) {
            "Kurtosis must be finite, got: $kurtosis"
        }
    }

    /**
     * Standard deviation (square root of variance)
     */
    val standardDeviation: Double
        get() = sqrt(variance)

    /**
     * Whether the distribution appears symmetric
     *
     * Returns true if skewness is close to zero (|skewness| < 0.5).
     */
    val isSymmetric: Boolean
        get() = kotlin.math.abs(skewness) < 0.5

    /**
     * Whether the distribution is skewed to the left (negatively skewed)
     *
     * Returns true if skewness < -0.5, indicating longer left tail.
     * Example: Most values high, few very low values.
     */
    val isSkewedLeft: Boolean
        get() = skewness < -0.5

    /**
     * Whether the distribution is skewed to the right (positively skewed)
     *
     * Returns true if skewness > 0.5, indicating longer right tail.
     * Example: Most values low, few very high values.
     */
    val isSkewedRight: Boolean
        get() = skewness > 0.5

    /**
     * Excess kurtosis (kurtosis - 3)
     *
     * Normal distribution has excess kurtosis = 0.
     * - Positive: Heavy tails (leptokurtic) - more outliers than normal
     * - Negative: Light tails (platykurtic) - fewer outliers than normal
     */
    val excessKurtosis: Double
        get() = kurtosis - 3.0

    /**
     * Whether the distribution has heavy tails (more outliers than normal)
     *
     * Returns true if excess kurtosis > 1.0.
     */
    val hasHeavyTails: Boolean
        get() = excessKurtosis > 1.0

    /**
     * Whether the distribution has light tails (fewer outliers than normal)
     *
     * Returns true if excess kurtosis < -1.0.
     */
    val hasLightTails: Boolean
        get() = excessKurtosis < -1.0

    /**
     * Whether the distribution appears normal (Gaussian)
     *
     * Checks multiple criteria:
     * - Shape is NORMAL
     * - Symmetric (|skewness| < 0.5)
     * - Normal kurtosis (|excess kurtosis| < 1.0)
     * - Mean ≈ median
     */
    val appearsNormal: Boolean
        get() {
            val meanMedianClose = kotlin.math.abs(mean - median) < standardDeviation * 0.5
            return shape == DistributionShape.NORMAL &&
                isSymmetric &&
                !hasHeavyTails &&
                !hasLightTails &&
                meanMedianClose
        }

    /**
     * Coefficient of variation (CV) as percentage
     *
     * Ratio of standard deviation to mean, useful for comparing variability
     * across different metrics.
     *
     * Returns null if mean is zero.
     */
    val coefficientOfVariation: Double?
        get() =
            if (mean != 0.0) {
                (standardDeviation / kotlin.math.abs(mean)) * 100.0
            } else {
                null
            }

    /**
     * Human-readable description of skewness
     */
    val skewnessDescription: String
        get() =
            when {
                isSkewedLeft -> "Left-skewed (long left tail)"
                isSkewedRight -> "Right-skewed (long right tail)"
                else -> "Symmetric"
            }

    /**
     * Human-readable description of kurtosis
     */
    val kurtosisDescription: String
        get() =
            when {
                hasHeavyTails -> "Heavy tails (more outliers)"
                hasLightTails -> "Light tails (fewer outliers)"
                else -> "Normal tails"
            }

    /**
     * Comprehensive summary
     */
    val summary: String
        get() =
            buildString {
                append("${shape.displayName} distribution")
                append(": μ=%.2f, σ=%.2f, median=%.2f".format(mean, standardDeviation, median))
                if (mode != null) {
                    append(", mode=%.2f".format(mode))
                }
                append(", skew=%.2f (%s)".format(skewness, skewnessDescription))
                append(", kurt=%.2f (%s)".format(kurtosis, kurtosisDescription))
            }

    companion object {
        /**
         * Calculate distribution characteristics from raw values
         *
         * @param values List of raw data points
         * @return DistributionCharacteristics with computed statistics
         */
        fun fromValues(values: List<Double>): DistributionCharacteristics {
            require(values.size >= 3) {
                "Need at least 3 values for distribution analysis, got: ${values.size}"
            }

            val sorted = values.sorted()
            val n = values.size.toDouble()

            // Central tendency
            val mean = values.average()
            val median = sorted[sorted.size / 2]
            val mode = findMode(sorted)

            // Dispersion
            val variance = values.map { (it - mean).pow(2) }.average()
            val stdDev = sqrt(variance)

            // Shape
            val skewness =
                if (stdDev > 0.0) {
                    val thirdMoment = values.map { ((it - mean) / stdDev).pow(3) }.average()
                    thirdMoment
                } else {
                    0.0
                }

            val kurtosis =
                if (stdDev > 0.0) {
                    val fourthMoment = values.map { ((it - mean) / stdDev).pow(4) }.average()
                    fourthMoment
                } else {
                    3.0 // Normal kurtosis
                }

            // Infer shape
            val shape = inferShape(mean, median, skewness, kurtosis, sorted)

            return DistributionCharacteristics(
                mean = mean,
                median = median,
                mode = mode,
                variance = variance,
                skewness = skewness,
                kurtosis = kurtosis,
                shape = shape,
            )
        }

        /**
         * Find mode (most frequent value)
         *
         * Uses binning for continuous data. Returns null if no clear mode.
         */
        private fun findMode(sortedValues: List<Double>): Double? {
            if (sortedValues.isEmpty()) return null

            // For continuous data, use binning
            val range = sortedValues.last() - sortedValues.first()
            if (range == 0.0) return sortedValues.first()

            val binCount = kotlin.math.min(20, sortedValues.size / 5)
            if (binCount < 2) return sortedValues.first()

            val binWidth = range / binCount
            val bins = Array(binCount) { 0 }

            sortedValues.forEach { value ->
                val binIndex =
                    ((value - sortedValues.first()) / binWidth)
                        .toInt()
                        .coerceIn(0, binCount - 1)
                bins[binIndex]++
            }

            val maxCount = bins.maxOrNull() ?: return null
            val modalBinIndex = bins.indexOf(maxCount)

            // Return midpoint of modal bin
            val binStart = sortedValues.first() + modalBinIndex * binWidth
            return binStart + binWidth / 2
        }

        /**
         * Infer distribution shape from statistics
         */
        private fun inferShape(
            mean: Double,
            median: Double,
            skewness: Double,
            kurtosis: Double,
            sortedValues: List<Double>,
        ): DistributionShape {
            val isSymmetric = kotlin.math.abs(skewness) < 0.5
            val normalKurtosis = kotlin.math.abs(kurtosis - 3.0) < 1.0

            // Check for bimodal (simplified - look for gaps in data)
            val isBimodal = checkBimodal(sortedValues)
            if (isBimodal) return DistributionShape.BIMODAL

            // Check for uniform (low variance relative to range)
            val range = sortedValues.last() - sortedValues.first()
            val expectedUniformVar = (range * range) / 12.0
            val variance = sortedValues.map { (it - mean) * (it - mean) }.average()
            val isUniform = kotlin.math.abs(variance - expectedUniformVar) < expectedUniformVar * 0.3
            if (isUniform) return DistributionShape.UNIFORM

            // Check for normal
            if (isSymmetric && normalKurtosis) {
                val meanMedianClose = kotlin.math.abs(mean - median) < range * 0.05
                if (meanMedianClose) return DistributionShape.NORMAL
            }

            // Check for skewed
            if (skewness < -0.5) return DistributionShape.SKEWED_LEFT
            if (skewness > 0.5) return DistributionShape.SKEWED_RIGHT

            return DistributionShape.UNKNOWN
        }

        /**
         * Check if distribution appears bimodal
         *
         * Simplified check: look for large gap in middle of sorted values.
         */
        private fun checkBimodal(sortedValues: List<Double>): Boolean {
            if (sortedValues.size < 10) return false

            val gaps = sortedValues.zipWithNext { a, b -> b - a }
            val meanGap = gaps.average()
            val maxGap = gaps.maxOrNull() ?: return false

            // If largest gap is much larger than average, might be bimodal
            return maxGap > meanGap * 3.0
        }
    }
}

/**
 * Distribution shape categories
 *
 * Qualitative description of the overall shape of a data distribution.
 *
 * @property displayName Human-readable shape description
 */
enum class DistributionShape(
    val displayName: String,
) {
    /**
     * Normal (Gaussian) distribution
     *
     * Bell-shaped, symmetric, mean ≈ median ≈ mode.
     * Most values cluster around the mean.
     *
     * Examples:
     * - Human heights
     * - Measurement errors
     * - Many natural phenomena
     */
    NORMAL("Normal"),

    /**
     * Bimodal distribution
     *
     * Two distinct peaks (modes).
     * Indicates two different underlying populations or processes.
     *
     * Examples:
     * - WiFi RSSI in two different rooms
     * - Performance on two different channels
     */
    BIMODAL("Bimodal"),

    /**
     * Left-skewed (negatively skewed) distribution
     *
     * Long tail on the left side.
     * Mean < median, most values are high.
     *
     * Examples:
     * - Test scores (if easy test)
     * - Network uptime percentages
     */
    SKEWED_LEFT("Left-Skewed"),

    /**
     * Right-skewed (positively skewed) distribution
     *
     * Long tail on the right side.
     * Mean > median, most values are low.
     *
     * Examples:
     * - Wealth distribution
     * - Error rates (most low, few high)
     * - WiFi latency (most low, occasional spikes)
     */
    SKEWED_RIGHT("Right-Skewed"),

    /**
     * Uniform distribution
     *
     * All values equally likely.
     * Flat histogram.
     *
     * Examples:
     * - Random number generators
     * - Evenly distributed measurements
     */
    UNIFORM("Uniform"),

    /**
     * Unknown or complex distribution
     *
     * Doesn't fit standard patterns.
     * May be multimodal, heavily mixed, or irregular.
     */
    UNKNOWN("Unknown"),
    ;

    /**
     * Whether this shape is symmetric
     */
    val isSymmetric: Boolean
        get() = this in listOf(NORMAL, UNIFORM, BIMODAL)

    /**
     * Whether this shape indicates skewed data
     */
    val isSkewed: Boolean
        get() = this in listOf(SKEWED_LEFT, SKEWED_RIGHT)
}

/**
 * Histogram representation
 *
 * Divides data range into bins and counts observations in each bin.
 * Used for visualizing distributions and detecting patterns.
 *
 * @property bins List of histogram bins, ordered from lowest to highest
 * @property binWidth Width of each bin (all bins have same width)
 * @property totalCount Total number of observations
 */
data class Histogram(
    val bins: List<HistogramBin>,
    val binWidth: Double,
    val totalCount: Int,
) {
    init {
        require(bins.isNotEmpty()) {
            "Histogram must have at least one bin"
        }
        require(binWidth > 0.0) {
            "Bin width must be positive, got: $binWidth"
        }
        require(totalCount > 0) {
            "Total count must be positive, got: $totalCount"
        }
        require(bins.sumOf { it.count } == totalCount) {
            "Sum of bin counts (${bins.sumOf { it.count }}) must equal total count ($totalCount)"
        }
    }

    /**
     * Number of bins
     */
    val binCount: Int
        get() = bins.size

    /**
     * Data range covered by histogram
     */
    val range: Double
        get() = bins.last().upperBound - bins.first().lowerBound

    /**
     * Bin with highest count
     */
    val modalBin: HistogramBin
        get() = bins.maxByOrNull { it.count } ?: bins.first()

    /**
     * Mode value (midpoint of modal bin)
     */
    val mode: Double
        get() = modalBin.midpoint

    /**
     * Get bin containing a specific value
     *
     * @param value Value to locate
     * @return HistogramBin containing this value, or null if outside range
     */
    fun getBinForValue(value: Double): HistogramBin? = bins.firstOrNull { value >= it.lowerBound && value < it.upperBound }
}

/**
 * Single bin in a histogram
 *
 * @property lowerBound Lower bound of this bin (inclusive)
 * @property upperBound Upper bound of this bin (exclusive, except for last bin)
 * @property count Number of observations in this bin
 * @property frequency Relative frequency (proportion of total, 0-1)
 */
data class HistogramBin(
    val lowerBound: Double,
    val upperBound: Double,
    val count: Int,
    val frequency: Double,
) {
    init {
        require(lowerBound < upperBound) {
            "Lower bound ($lowerBound) must be < upper bound ($upperBound)"
        }
        require(count >= 0) {
            "Count must be non-negative, got: $count"
        }
        require(frequency in 0.0..1.0) {
            "Frequency must be in [0, 1], got: $frequency"
        }
    }

    /**
     * Bin width
     */
    val width: Double
        get() = upperBound - lowerBound

    /**
     * Midpoint of bin
     */
    val midpoint: Double
        get() = (lowerBound + upperBound) / 2.0

    /**
     * Density (count per unit width)
     *
     * Useful for comparing bins of different widths.
     */
    val density: Double
        get() = count.toDouble() / width

    /**
     * Whether this bin is empty
     */
    val isEmpty: Boolean
        get() = count == 0

    /**
     * Human-readable description
     */
    val description: String
        get() =
            "[%.2f, %.2f): %d (%.1f%%)".format(
                lowerBound,
                upperBound,
                count,
                frequency * 100.0,
            )
}

/**
 * Probability density function estimate
 *
 * Kernel Density Estimation (KDE) result providing smooth probability density
 * curve from discrete data points.
 *
 * @property points List of (x, density) points defining the PDF curve
 * @property bandwidth Smoothing bandwidth used in KDE
 */
data class ProbabilityDensity(
    val points: List<DensityPoint>,
    val bandwidth: Double,
) {
    init {
        require(points.isNotEmpty()) {
            "PDF must have at least one point"
        }
        require(bandwidth > 0.0) {
            "Bandwidth must be positive, got: $bandwidth"
        }
        // Verify points are sorted by x
        points.zipWithNext().forEach { (p1, p2) ->
            require(p1.x <= p2.x) {
                "Density points must be sorted by x coordinate"
            }
        }
    }

    /**
     * Peak density value
     */
    val maxDensity: Double
        get() = points.maxOfOrNull { it.density } ?: 0.0

    /**
     * Mode (x value with highest density)
     */
    val mode: Double
        get() = points.maxByOrNull { it.density }?.x ?: 0.0

    /**
     * Get density at a specific x value (linear interpolation)
     *
     * @param x Value to query
     * @return Estimated density at x, or 0 if outside range
     */
    fun densityAt(x: Double): Double {
        if (x < points.first().x || x > points.last().x) return 0.0

        val index = points.indexOfFirst { it.x >= x }
        if (index == -1) return points.last().density
        if (index == 0) return points.first().density

        // Linear interpolation
        val p1 = points[index - 1]
        val p2 = points[index]
        val fraction = (x - p1.x) / (p2.x - p1.x)
        return p1.density + fraction * (p2.density - p1.density)
    }
}

/**
 * Point on a probability density curve
 *
 * @property x X-axis value (data value)
 * @property density Estimated probability density at this x
 */
data class DensityPoint(
    val x: Double,
    val density: Double,
) {
    init {
        require(density >= 0.0) {
            "Density must be non-negative, got: $density"
        }
        require(x.isFinite()) {
            "X value must be finite, got: $x"
        }
    }
}
