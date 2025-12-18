package io.lamco.netkit.analytics.model

/**
 * Aggregated metrics for a specific time bucket
 *
 * Represents statistical aggregations of WiFi network metrics over a defined time interval.
 * Provides comprehensive summary statistics including central tendency, dispersion,
 * and distribution characteristics.
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = TimeSeriesAnalyzer()
 * val dailyMetrics = analyzer.aggregate(snapshots, TimeInterval.DAILY)
 * dailyMetrics.forEach { bucket ->
 *     println("Date: ${bucket.timestamp}")
 *     println("Avg RSSI: ${bucket.metrics["rssi"]?.mean}dBm")
 *     println("P95 RSSI: ${bucket.metrics["rssi"]?.percentiles?.get(95)}dBm")
 * }
 * ```
 *
 * @property timestamp Start timestamp of this time bucket (milliseconds since epoch)
 * @property interval Time interval type (hourly, daily, weekly, monthly)
 * @property metrics Map of metric name to aggregated value statistics
 */
data class AggregatedMetrics(
    val timestamp: Long,
    val interval: TimeInterval,
    val metrics: Map<String, AggregatedValue>,
) {
    init {
        require(timestamp > 0) {
            "Timestamp must be positive, got: $timestamp"
        }
        require(metrics.isNotEmpty()) {
            "Metrics map cannot be empty"
        }
    }

    /**
     * Get aggregated value for a specific metric
     *
     * @param metricName Name of the metric (e.g., "rssi", "snr", "throughput")
     * @return AggregatedValue or null if metric not found
     */
    operator fun get(metricName: String): AggregatedValue? = metrics[metricName]

    /**
     * Check if a specific metric exists in this aggregation
     *
     * @param metricName Name of the metric to check
     * @return true if metric exists, false otherwise
     */
    fun hasMetric(metricName: String): Boolean = metricName in metrics

    /**
     * Number of unique metrics aggregated
     */
    val metricCount: Int
        get() = metrics.size

    /**
     * Total number of data points across all metrics
     *
     * Returns the sum of counts from all aggregated metrics.
     */
    val totalDataPoints: Int
        get() = metrics.values.sumOf { it.count }

    /**
     * Human-readable summary of this time bucket
     */
    val summary: String
        get() =
            buildString {
                append("${interval.displayName} bucket at $timestamp")
                append(" ($metricCount metrics, $totalDataPoints total points)")
            }
}

/**
 * Aggregated statistical values for a single metric
 *
 * Contains comprehensive statistical summary of a metric's values within a time bucket.
 * Includes measures of central tendency (mean, median), dispersion (std dev, min, max),
 * and distribution (percentiles).
 *
 * ## Statistical Definitions
 * - **Mean**: Average value (sum / count)
 * - **Median**: Middle value when sorted (50th percentile)
 * - **Std Dev**: Standard deviation (measure of spread)
 * - **Min/Max**: Minimum and maximum observed values
 * - **Percentiles**: Values below which N% of observations fall
 *
 * @property mean Arithmetic mean of values
 * @property median Middle value (50th percentile)
 * @property stdDev Standard deviation (population std dev)
 * @property min Minimum value observed
 * @property max Maximum value observed
 * @property count Number of observations
 * @property percentiles Map of percentile rank to value (e.g., 95 -> value at P95)
 */
data class AggregatedValue(
    val mean: Double,
    val median: Double,
    val stdDev: Double,
    val min: Double,
    val max: Double,
    val count: Int,
    val percentiles: Map<Int, Double> = emptyMap(),
) {
    init {
        require(count > 0) {
            "Count must be positive, got: $count"
        }
        require(min <= max) {
            "Min ($min) must be <= max ($max)"
        }
        require(stdDev >= 0.0) {
            "Standard deviation must be non-negative, got: $stdDev"
        }
        percentiles.forEach { (percentile, value) ->
            require(percentile in 1..99) {
                "Percentile must be in [1, 99], got: $percentile"
            }
            require(value >= min && value <= max) {
                "Percentile value ($value) must be within [min=$min, max=$max]"
            }
        }
    }

    /**
     * Range (spread) of values
     *
     * Calculated as max - min, represents the total variation in the data.
     */
    val range: Double
        get() = max - min

    /**
     * Coefficient of variation (CV)
     *
     * Ratio of standard deviation to mean, expressed as a percentage.
     * Indicates relative variability: CV < 15% is low, CV > 30% is high.
     *
     * Returns null if mean is zero to avoid division by zero.
     */
    val coefficientOfVariation: Double?
        get() = if (mean != 0.0) (stdDev / kotlin.math.abs(mean)) * 100.0 else null

    /**
     * Whether the data has low variability
     *
     * Returns true if coefficient of variation < 15%, indicating consistent values.
     */
    val isStable: Boolean
        get() = (coefficientOfVariation ?: Double.MAX_VALUE) < 15.0

    /**
     * Whether the data has high variability
     *
     * Returns true if coefficient of variation >= 30%, indicating inconsistent values.
     */
    val isVolatile: Boolean
        get() = (coefficientOfVariation ?: 0.0) >= 30.0

    /**
     * Get value at specific percentile
     *
     * @param percentile Desired percentile (1-99)
     * @return Value at that percentile, or null if not computed
     */
    fun getPercentile(percentile: Int): Double? {
        require(percentile in 1..99) {
            "Percentile must be in [1, 99], got: $percentile"
        }
        return percentiles[percentile]
    }

    /**
     * Interquartile range (IQR)
     *
     * Difference between 75th and 25th percentiles (Q3 - Q1).
     * Robust measure of statistical dispersion.
     *
     * Returns null if P25 or P75 not available.
     */
    val interquartileRange: Double?
        get() {
            val p25 = percentiles[25]
            val p75 = percentiles[75]
            return if (p25 != null && p75 != null) p75 - p25 else null
        }

    /**
     * Whether the data is likely normally distributed
     *
     * Simplified check: mean ≈ median and std dev is reasonable.
     * For rigorous testing, use DistributionAnalyzer.
     */
    val appearsNormal: Boolean
        get() {
            val meanMedianDiff = kotlin.math.abs(mean - median)
            val tolerance = stdDev * 0.5
            return meanMedianDiff <= tolerance
        }

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("μ=%.2f, σ=%.2f, range=[%.2f, %.2f]".format(mean, stdDev, min, max))
                if (percentiles.isNotEmpty()) {
                    append(", percentiles: ${percentiles.size}")
                }
            }

    companion object {
        /**
         * Create AggregatedValue from a list of raw values
         *
         * Convenience factory method that computes all statistics from raw data.
         *
         * @param values List of raw metric values
         * @param computePercentiles Whether to compute percentiles (default: true)
         * @return AggregatedValue with computed statistics
         */
        fun fromValues(
            values: List<Double>,
            computePercentiles: Boolean = true,
        ): AggregatedValue {
            require(values.isNotEmpty()) {
                "Values list cannot be empty"
            }

            val sorted = values.sorted()
            val mean = values.average()
            val median = sorted[sorted.size / 2]
            val variance = values.map { (it - mean) * (it - mean) }.average()
            val stdDev = kotlin.math.sqrt(variance)
            val min = sorted.first()
            val max = sorted.last()

            val percentiles =
                if (computePercentiles) {
                    listOf(25, 50, 75, 90, 95, 99).associateWith { p ->
                        val index = ((p / 100.0) * (sorted.size - 1)).toInt()
                        sorted[index]
                    }
                } else {
                    emptyMap()
                }

            return AggregatedValue(
                mean = mean,
                median = median,
                stdDev = stdDev,
                min = min,
                max = max,
                count = values.size,
                percentiles = percentiles,
            )
        }
    }
}

/**
 * Time-series data structure
 *
 * Represents a sequence of time-stamped metric values for time-series analysis.
 * Used as input to TimeSeriesAnalyzer and smoothing functions.
 *
 * @property metricName Name of the metric being tracked
 * @property dataPoints List of time-stamped values, ordered by timestamp
 */
data class TimeSeries(
    val metricName: String,
    val dataPoints: List<TimeSeriesDataPoint>,
) {
    init {
        require(metricName.isNotBlank()) {
            "Metric name cannot be blank"
        }
        require(dataPoints.isNotEmpty()) {
            "Data points list cannot be empty"
        }
        // Verify chronological order
        dataPoints.zipWithNext().forEach { (earlier, later) ->
            require(earlier.timestamp <= later.timestamp) {
                "Data points must be in chronological order"
            }
        }
    }

    /**
     * Number of data points in this time series
     */
    val size: Int
        get() = dataPoints.size

    /**
     * Time span covered by this series (milliseconds)
     */
    val timeSpan: Long
        get() =
            if (dataPoints.size < 2) {
                0L
            } else {
                dataPoints.last().timestamp - dataPoints.first().timestamp
            }

    /**
     * Start timestamp (first data point)
     */
    val startTime: Long
        get() = dataPoints.first().timestamp

    /**
     * End timestamp (last data point)
     */
    val endTime: Long
        get() = dataPoints.last().timestamp

    /**
     * Get values only (without timestamps)
     */
    val values: List<Double>
        get() = dataPoints.map { it.value }

    /**
     * Get timestamps only (without values)
     */
    val timestamps: List<Long>
        get() = dataPoints.map { it.timestamp }

    /**
     * Get data points within a specific time range
     *
     * @param startMillis Start of time range (inclusive)
     * @param endMillis End of time range (inclusive)
     * @return Filtered TimeSeries containing only points in range
     */
    fun filterByTimeRange(
        startMillis: Long,
        endMillis: Long,
    ): TimeSeries {
        val filtered = dataPoints.filter { it.timestamp in startMillis..endMillis }
        return if (filtered.isNotEmpty()) {
            copy(dataPoints = filtered)
        } else {
            // Return empty series with single point if no data in range
            copy(dataPoints = listOf(dataPoints.first()))
        }
    }

    /**
     * Get most recent N data points
     *
     * @param n Number of recent points to retrieve
     * @return TimeSeries with last N points
     */
    fun takeLast(n: Int): TimeSeries {
        require(n > 0) { "n must be positive, got: $n" }
        return copy(dataPoints = dataPoints.takeLast(n))
    }
}

/**
 * Single data point in a time series
 *
 * @property timestamp Time when this value was recorded (milliseconds since epoch)
 * @property value Metric value at this timestamp
 */
data class TimeSeriesDataPoint(
    val timestamp: Long,
    val value: Double,
) {
    init {
        require(timestamp > 0) {
            "Timestamp must be positive, got: $timestamp"
        }
        require(value.isFinite()) {
            "Value must be finite, got: $value"
        }
    }
}
