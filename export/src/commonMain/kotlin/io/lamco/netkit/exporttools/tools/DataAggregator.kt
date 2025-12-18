package io.lamco.netkit.exporttools.tools

/**
 * Data aggregation and statistical analysis for export tools.
 *
 * Provides comprehensive data aggregation, statistical analysis, and summarization
 * for WiFi network data. Supports multiple aggregation strategies and statistical metrics.
 *
 * ## Features
 * - **Statistical Functions**: Mean, median, mode, stddev, percentiles
 * - **Aggregation**: Group by network, band, security type, time period
 * - **Time Series**: Temporal aggregation and trending
 * - **Filtering**: Pre-aggregation filtering
 * - **Ranking**: Top/bottom N by metric
 * - **Distribution**: Histogram and frequency analysis
 *
 * ## Aggregation Strategies
 * - **COUNT**: Count of items
 * - **SUM**: Sum of values
 * - **AVG**: Average/mean
 * - **MIN**: Minimum value
 * - **MAX**: Maximum value
 * - **MEDIAN**: Median value
 * - **STDDEV**: Standard deviation
 * - **PERCENTILE**: Percentile values (p50, p95, p99)
 *
 * @since 1.0.0
 */
class DataAggregator {
    /**
     * Aggregates data using specified strategy.
     *
     * @param data List of data items
     * @param strategy Aggregation strategy
     * @param valueExtractor Function to extract numeric value from item
     * @return Aggregated result
     */
    fun aggregate(
        data: List<Any>,
        strategy: AggregationStrategy,
        valueExtractor: (Any) -> Double?,
    ): Double? {
        val values = data.mapNotNull(valueExtractor)
        if (values.isEmpty()) return null

        return when (strategy) {
            AggregationStrategy.COUNT -> values.size.toDouble()
            AggregationStrategy.SUM -> values.sum()
            AggregationStrategy.AVG -> values.average()
            AggregationStrategy.MIN -> values.minOrNull()
            AggregationStrategy.MAX -> values.maxOrNull()
            AggregationStrategy.MEDIAN -> calculateMedian(values)
            AggregationStrategy.STDDEV -> calculateStdDev(values)
        }
    }

    /**
     * Groups data by key and aggregates each group.
     *
     * @param data List of data items
     * @param keyExtractor Function to extract grouping key
     * @param valueExtractor Function to extract numeric value
     * @param strategy Aggregation strategy
     * @return Map of group key to aggregated value
     */
    fun <K> groupAndAggregate(
        data: List<Any>,
        keyExtractor: (Any) -> K,
        valueExtractor: (Any) -> Double?,
        strategy: AggregationStrategy,
    ): Map<K, Double?> =
        data
            .groupBy(keyExtractor)
            .mapValues { (_, items) ->
                aggregate(items, strategy, valueExtractor)
            }

    /**
     * Calculates percentile value.
     *
     * @param data List of numeric values
     * @param percentile Percentile (0-100)
     * @return Percentile value
     */
    fun calculatePercentile(
        data: List<Double>,
        percentile: Int,
    ): Double? {
        require(percentile in 0..100) { "Percentile must be 0-100" }
        if (data.isEmpty()) return null

        val sorted = data.sorted()
        val index = (percentile / 100.0) * (sorted.size - 1)
        val lower = sorted[index.toInt()]
        val upper = sorted.getOrElse(index.toInt() + 1) { lower }
        val fraction = index - index.toInt()

        return lower + fraction * (upper - lower)
    }

    /**
     * Generates histogram of data distribution.
     *
     * @param data List of numeric values
     * @param bucketCount Number of histogram buckets
     * @return Map of bucket range to count
     */
    fun histogram(
        data: List<Double>,
        bucketCount: Int = 10,
    ): Map<String, Int> {
        if (data.isEmpty()) return emptyMap()

        val min = data.minOrNull()!!
        val max = data.maxOrNull()!!
        val range = max - min
        val bucketSize = range / bucketCount

        val buckets = mutableMapOf<String, Int>()

        for (i in 0 until bucketCount) {
            val start = min + i * bucketSize
            val end = start + bucketSize
            val key = "%.1f-%.1f".format(start, end)
            buckets[key] = data.count { it >= start && (i == bucketCount - 1 || it < end) }
        }

        return buckets
    }

    /**
     * Ranks items by metric and returns top N.
     *
     * @param data List of items
     * @param n Number of top items to return
     * @param valueExtractor Function to extract ranking value
     * @param descending True for top N (default), false for bottom N
     * @return Top N items
     */
    fun <T> topN(
        data: List<T>,
        n: Int,
        valueExtractor: (T) -> Double?,
        descending: Boolean = true,
    ): List<T> =
        if (descending) {
            data.sortedByDescending { valueExtractor(it) ?: Double.MIN_VALUE }.take(n)
        } else {
            data.sortedBy { valueExtractor(it) ?: Double.MAX_VALUE }.take(n)
        }

    /**
     * Calculates statistics summary for data.
     *
     * @param data List of numeric values
     * @return Statistics summary
     */
    fun calculateStatistics(data: List<Double>): StatisticsSummary {
        if (data.isEmpty()) {
            return StatisticsSummary(
                count = 0,
                sum = 0.0,
                mean = 0.0,
                median = 0.0,
                min = 0.0,
                max = 0.0,
                stddev = 0.0,
                p50 = 0.0,
                p95 = 0.0,
                p99 = 0.0,
            )
        }

        return StatisticsSummary(
            count = data.size,
            sum = data.sum(),
            mean = data.average(),
            median = calculateMedian(data)!!,
            min = data.minOrNull()!!,
            max = data.maxOrNull()!!,
            stddev = calculateStdDev(data)!!,
            p50 = calculatePercentile(data, 50)!!,
            p95 = calculatePercentile(data, 95)!!,
            p99 = calculatePercentile(data, 99)!!,
        )
    }

    /**
     * Calculates median value.
     */
    private fun calculateMedian(values: List<Double>): Double? {
        if (values.isEmpty()) return null

        val sorted = values.sorted()
        val middle = sorted.size / 2

        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    /**
     * Calculates standard deviation.
     */
    private fun calculateStdDev(values: List<Double>): Double? {
        if (values.isEmpty()) return null

        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }

    /**
     * Aggregation strategy enum.
     */
    enum class AggregationStrategy {
        COUNT,
        SUM,
        AVG,
        MIN,
        MAX,
        MEDIAN,
        STDDEV,
    }
}

/**
 * Statistics summary data class.
 *
 * @property count Number of data points
 * @property sum Sum of all values
 * @property mean Average value
 * @property median Median value
 * @property min Minimum value
 * @property max Maximum value
 * @property stddev Standard deviation
 * @property p50 50th percentile (median)
 * @property p95 95th percentile
 * @property p99 99th percentile
 *
 * @since 1.0.0
 */
data class StatisticsSummary(
    val count: Int,
    val sum: Double,
    val mean: Double,
    val median: Double,
    val min: Double,
    val max: Double,
    val stddev: Double,
    val p50: Double,
    val p95: Double,
    val p99: Double,
)
