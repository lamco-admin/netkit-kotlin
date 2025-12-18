package io.lamco.netkit.analytics.model

import kotlin.math.abs

/**
 * Correlation analysis result between two metrics
 *
 * Represents the statistical relationship between two variables (e.g., RSSI vs throughput,
 * SNR vs packet loss). Includes correlation coefficient, statistical significance, and
 * interpretation helpers.
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = CorrelationAnalyzer()
 * val result = analyzer.calculateCorrelation(
 *     x = rssiValues,
 *     y = throughputValues,
 *     method = CorrelationMethod.PEARSON
 * )
 * println("Correlation: ${result.coefficient} (${result.strength})")
 * if (result.isSignificant) {
 *     println("Statistically significant at p < 0.05")
 * }
 * ```
 *
 * @property coefficient Correlation coefficient (-1 to +1)
 * @property pValue Statistical significance (probability this correlation is random)
 * @property method Correlation method used (Pearson, Spearman, Kendall)
 * @property strength Qualitative correlation strength
 * @property direction Correlation direction (positive, negative, none)
 */
data class CorrelationResult(
    val coefficient: Double,
    val pValue: Double,
    val method: CorrelationMethod,
    val strength: CorrelationStrength,
    val direction: CorrelationDirection,
) {
    init {
        require(coefficient in -1.0..1.0) {
            "Correlation coefficient must be in [-1, 1], got: $coefficient"
        }
        require(pValue in 0.0..1.0) {
            "P-value must be in [0, 1], got: $pValue"
        }
    }

    /**
     * Whether this correlation is statistically significant at α = 0.05 level
     *
     * Returns true if p-value < 0.05, indicating less than 5% probability
     * that the observed correlation is due to random chance.
     */
    val isSignificant: Boolean
        get() = pValue < 0.05

    /**
     * Whether this correlation is statistically significant at α = 0.01 level
     *
     * Stricter significance threshold (1% probability of random chance).
     */
    val isHighlySignificant: Boolean
        get() = pValue < 0.01

    /**
     * Whether there is a meaningful relationship between variables
     *
     * Returns true if correlation is both statistically significant and
     * at least moderately strong (|r| >= 0.5).
     */
    val isMeaningful: Boolean
        get() = isSignificant && strength.isMeaningful

    /**
     * Absolute value of correlation coefficient
     */
    val absoluteCoefficient: Double
        get() = abs(coefficient)

    /**
     * Coefficient of determination (R²)
     *
     * Proportion of variance in one variable that is predictable from the other.
     * For example, R² = 0.64 means 64% of variance is explained by the relationship.
     */
    val rSquared: Double
        get() = coefficient * coefficient

    /**
     * Percentage of variance explained (R² as percentage)
     */
    val varianceExplainedPercent: Double
        get() = rSquared * 100.0

    /**
     * Human-readable interpretation of correlation
     */
    val interpretation: String
        get() =
            buildString {
                append("${strength.displayName} ${direction.displayName} correlation")
                append(" (${method.displayName}: r=%.3f, p=%.4f)".format(coefficient, pValue))
                if (isHighlySignificant) {
                    append(" **Highly Significant**")
                } else if (isSignificant) {
                    append(" *Significant*")
                } else {
                    append(" (Not Significant)")
                }
            }

    /**
     * Short summary for display
     */
    val summary: String
        get() = "r=%.3f (%s, p=%.4f)".format(coefficient, strength.displayName, pValue)

    companion object {
        /**
         * Create CorrelationResult from coefficient and p-value
         *
         * Automatically determines strength and direction from coefficient.
         *
         * @param coefficient Correlation coefficient (-1 to +1)
         * @param pValue Statistical significance level
         * @param method Correlation method used
         * @return CorrelationResult with inferred strength and direction
         */
        fun from(
            coefficient: Double,
            pValue: Double,
            method: CorrelationMethod,
        ): CorrelationResult {
            val strength = CorrelationStrength.fromCoefficient(coefficient)
            val direction = CorrelationDirection.fromCoefficient(coefficient)

            return CorrelationResult(
                coefficient = coefficient,
                pValue = pValue,
                method = method,
                strength = strength,
                direction = direction,
            )
        }
    }
}

/**
 * Correlation analysis method
 *
 * Different methods are appropriate for different data types:
 * - **Pearson**: Linear relationships, continuous data
 * - **Spearman**: Monotonic relationships, ordinal data, non-linear
 * - **Kendall**: Robust to outliers, small sample sizes
 *
 * @property displayName Human-readable method name
 * @property requiresMinimumSampleSize Minimum sample size for reliable results
 */
enum class CorrelationMethod(
    val displayName: String,
    val requiresMinimumSampleSize: Int,
) {
    /**
     * Pearson product-moment correlation
     *
     * Measures linear relationship between two continuous variables.
     * Assumes:
     * - Data is continuous
     * - Relationship is linear
     * - Variables are normally distributed
     * - No significant outliers
     *
     * Range: -1 (perfect negative linear) to +1 (perfect positive linear)
     */
    PEARSON(
        displayName = "Pearson",
        requiresMinimumSampleSize = 10,
    ),

    /**
     * Spearman rank-order correlation
     *
     * Measures monotonic relationship (not necessarily linear) between variables.
     * Uses ranks instead of raw values, making it robust to outliers and
     * non-normal distributions.
     *
     * Appropriate for:
     * - Ordinal data
     * - Non-linear monotonic relationships
     * - Data with outliers
     *
     * Range: -1 (perfect negative monotonic) to +1 (perfect positive monotonic)
     */
    SPEARMAN(
        displayName = "Spearman",
        requiresMinimumSampleSize = 10,
    ),

    /**
     * Kendall tau rank correlation
     *
     * Alternative rank-based correlation that is more robust with small sample sizes.
     * Typically has smaller values than Spearman for same relationship.
     *
     * Advantages:
     * - Better for small samples (n < 30)
     * - More robust to outliers
     * - Has direct probabilistic interpretation
     *
     * Range: -1 to +1 (typically smaller absolute values than Spearman)
     */
    KENDALL(
        displayName = "Kendall's Tau",
        requiresMinimumSampleSize = 5,
    ),
    ;

    /**
     * Whether this method is parametric (assumes distribution)
     */
    val isParametric: Boolean
        get() = this == PEARSON

    /**
     * Whether this method is non-parametric (distribution-free)
     */
    val isNonParametric: Boolean
        get() = !isParametric

    companion object {
        /**
         * Recommend correlation method based on data characteristics
         *
         * @param sampleSize Number of paired observations
         * @param hasOutliers Whether data contains outliers
         * @param isNormallyDistributed Whether data appears normally distributed
         * @return Recommended CorrelationMethod
         */
        fun recommend(
            sampleSize: Int,
            hasOutliers: Boolean,
            isNormallyDistributed: Boolean,
        ): CorrelationMethod =
            when {
                sampleSize < 30 -> KENDALL
                hasOutliers -> SPEARMAN
                isNormallyDistributed -> PEARSON
                else -> SPEARMAN
            }
    }
}

/**
 * Correlation strength categories
 *
 * Qualitative interpretation of correlation coefficient absolute value.
 * Based on common statistical conventions (Cohen, 1988).
 *
 * @property displayName Human-readable strength description
 * @property minAbsCoefficient Minimum absolute coefficient for this category
 */
enum class CorrelationStrength(
    val displayName: String,
    val minAbsCoefficient: Double,
) {
    /**
     * Very strong correlation: |r| >= 0.9
     *
     * Variables are very highly related. One can be predicted accurately from the other.
     */
    VERY_STRONG(
        displayName = "Very Strong",
        minAbsCoefficient = 0.9,
    ),

    /**
     * Strong correlation: 0.7 <= |r| < 0.9
     *
     * Variables are strongly related. Good predictive power.
     */
    STRONG(
        displayName = "Strong",
        minAbsCoefficient = 0.7,
    ),

    /**
     * Moderate correlation: 0.5 <= |r| < 0.7
     *
     * Variables are moderately related. Useful but not highly predictive.
     */
    MODERATE(
        displayName = "Moderate",
        minAbsCoefficient = 0.5,
    ),

    /**
     * Weak correlation: 0.3 <= |r| < 0.5
     *
     * Variables have some relationship but it's weak. Limited predictive value.
     */
    WEAK(
        displayName = "Weak",
        minAbsCoefficient = 0.3,
    ),

    /**
     * Very weak correlation: 0.1 <= |r| < 0.3
     *
     * Very weak relationship. Minimal practical significance.
     */
    VERY_WEAK(
        displayName = "Very Weak",
        minAbsCoefficient = 0.1,
    ),

    /**
     * No correlation: |r| < 0.1
     *
     * Negligible relationship between variables.
     */
    NONE(
        displayName = "None",
        minAbsCoefficient = 0.0,
    ),
    ;

    /**
     * Whether this strength indicates a meaningful relationship
     *
     * Returns true for MODERATE, STRONG, or VERY_STRONG.
     */
    val isMeaningful: Boolean
        get() = this in listOf(MODERATE, STRONG, VERY_STRONG)

    /**
     * Whether this strength indicates a strong relationship
     *
     * Returns true for STRONG or VERY_STRONG.
     */
    val isStrong: Boolean
        get() = this in listOf(STRONG, VERY_STRONG)

    companion object {
        /**
         * Determine correlation strength from coefficient
         *
         * @param coefficient Correlation coefficient (-1 to +1)
         * @return Corresponding CorrelationStrength
         */
        fun fromCoefficient(coefficient: Double): CorrelationStrength {
            val absCoeff = abs(coefficient)
            return entries.first { absCoeff >= it.minAbsCoefficient }
        }
    }
}

/**
 * Correlation direction categories
 *
 * Indicates whether variables increase together (positive), move opposite (negative),
 * or have no relationship (none).
 *
 * @property displayName Human-readable direction description
 */
enum class CorrelationDirection(
    val displayName: String,
) {
    /**
     * Positive correlation (r > 0.1)
     *
     * As one variable increases, the other tends to increase.
     * Example: Higher SNR → Higher throughput
     */
    POSITIVE("Positive"),

    /**
     * Negative correlation (r < -0.1)
     *
     * As one variable increases, the other tends to decrease.
     * Example: Higher interference → Lower throughput
     */
    NEGATIVE("Negative"),

    /**
     * No correlation (|r| <= 0.1)
     *
     * Variables are essentially independent.
     */
    NONE("None"),
    ;

    /**
     * Whether this direction indicates a positive relationship
     */
    val isPositive: Boolean
        get() = this == POSITIVE

    /**
     * Whether this direction indicates a negative (inverse) relationship
     */
    val isNegative: Boolean
        get() = this == NEGATIVE

    companion object {
        /**
         * Determine direction from correlation coefficient
         *
         * @param coefficient Correlation coefficient (-1 to +1)
         * @return Corresponding CorrelationDirection
         */
        fun fromCoefficient(coefficient: Double): CorrelationDirection =
            when {
                coefficient > 0.1 -> POSITIVE
                coefficient < -0.1 -> NEGATIVE
                else -> NONE
            }
    }
}

/**
 * Correlation matrix for multiple variables
 *
 * Represents pairwise correlations between all combinations of metrics.
 * Used to identify which metrics are related and how strongly.
 *
 * @property metricNames Names of all metrics in the matrix
 * @property correlations Map of metric pair to correlation result
 * @property method Correlation method used for all pairs
 */
data class CorrelationMatrix(
    val metricNames: List<String>,
    val correlations: Map<Pair<String, String>, CorrelationResult>,
    val method: CorrelationMethod,
) {
    init {
        require(metricNames.size >= 2) {
            "Need at least 2 metrics, got: ${metricNames.size}"
        }
        require(metricNames.distinct().size == metricNames.size) {
            "Metric names must be unique"
        }
    }

    /**
     * Get correlation between two specific metrics
     *
     * @param metric1 First metric name
     * @param metric2 Second metric name
     * @return CorrelationResult or null if pair not found
     */
    fun getCorrelation(
        metric1: String,
        metric2: String,
    ): CorrelationResult? {
        // Try both orderings since correlation is symmetric
        return correlations[Pair(metric1, metric2)] ?: correlations[Pair(metric2, metric1)]
    }

    /**
     * Number of unique metric pairs
     */
    val pairCount: Int
        get() = correlations.size

    /**
     * Total number of metrics
     */
    val metricCount: Int
        get() = metricNames.size

    /**
     * Find all significant correlations (p < 0.05)
     */
    val significantCorrelations: List<CorrelationPair>
        get() =
            correlations.filter { it.value.isSignificant }.map { (pair, result) ->
                CorrelationPair(
                    metric1 = pair.first,
                    metric2 = pair.second,
                    result = result,
                )
            }

    /**
     * Find all strong correlations (|r| >= 0.7)
     */
    val strongCorrelations: List<CorrelationPair>
        get() =
            correlations.filter { it.value.strength.isStrong }.map { (pair, result) ->
                CorrelationPair(
                    metric1 = pair.first,
                    metric2 = pair.second,
                    result = result,
                )
            }
}

/**
 * Correlation between a pair of metrics
 *
 * Convenience wrapper for presenting correlation results.
 *
 * @property metric1 First metric name
 * @property metric2 Second metric name
 * @property result Correlation analysis result
 */
data class CorrelationPair(
    val metric1: String,
    val metric2: String,
    val result: CorrelationResult,
) {
    /**
     * Human-readable description
     */
    val description: String
        get() = "$metric1 ↔ $metric2: ${result.summary}"
}
