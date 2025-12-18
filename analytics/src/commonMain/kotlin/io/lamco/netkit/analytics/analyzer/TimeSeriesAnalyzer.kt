package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import kotlin.math.*

/**
 * Time-series analysis engine for WiFi metrics
 *
 * Provides comprehensive time-series analysis capabilities including:
 * - Temporal aggregation (hourly, daily, weekly, monthly rollups)
 * - Statistical smoothing (moving average, exponential, Savitzky-Golay)
 * - Percentile calculations
 * - Seasonality detection
 * - Trend extraction
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = TimeSeriesAnalyzer()
 *
 * // Aggregate to daily buckets
 * val dailyMetrics = analyzer.aggregate(snapshots, TimeInterval.DAILY)
 *
 * // Smooth noisy data
 * val smoothed = analyzer.smooth(timeSeries, SmoothingMethod.MOVING_AVERAGE, windowSize = 5)
 *
 * // Detect seasonality
 * val seasonality = analyzer.detectSeasonality(timeSeries)
 * ```
 *
 * @property defaultPercentiles Percentiles to calculate by default (50, 75, 90, 95, 99)
 */
class TimeSeriesAnalyzer(
    private val defaultPercentiles: List<Int> = listOf(50, 75, 90, 95, 99),
) {
    init {
        require(defaultPercentiles.all { it in 1..99 }) {
            "All percentiles must be in range [1, 99]"
        }
    }

    /**
     * Aggregate time-series data into time buckets
     *
     * Groups raw data points into fixed time intervals (hourly, daily, etc.) and
     * computes statistical summaries for each bucket.
     *
     * @param timeSeries Input time series data
     * @param interval Time bucket interval
     * @return List of aggregated metrics, one per time bucket
     */
    fun aggregate(
        timeSeries: TimeSeries,
        interval: TimeInterval,
    ): List<AggregatedMetrics> {
        require(timeSeries.size > 0) {
            "Time series cannot be empty"
        }

        val buckets = groupIntoBuckets(timeSeries, interval)

        return buckets.map { (timestamp, points) ->
            val values = points.map { it.value }
            val aggregatedValue = AggregatedValue.fromValues(values, computePercentiles = true)

            AggregatedMetrics(
                timestamp = timestamp,
                interval = interval,
                metrics = mapOf(timeSeries.metricName to aggregatedValue),
            )
        }
    }

    /**
     * Aggregate multiple metrics into time buckets
     *
     * Processes multiple time series together, aligning them to the same time buckets.
     *
     * @param series Map of metric name to time series
     * @param interval Time bucket interval
     * @return List of aggregated metrics containing all metrics per bucket
     */
    fun aggregateMultiple(
        series: Map<String, TimeSeries>,
        interval: TimeInterval,
    ): List<AggregatedMetrics> {
        require(series.isNotEmpty()) {
            "Must provide at least one time series"
        }

        val allPoints = series.values.flatMap { it.dataPoints }
        if (allPoints.isEmpty()) return emptyList()

        val minTime = allPoints.minOf { it.timestamp }
        val maxTime = allPoints.maxOf { it.timestamp }

        val bucketTimestamps = generateBucketTimestamps(minTime, maxTime, interval)

        return bucketTimestamps
            .map { bucketStart ->
                val bucketEnd = bucketStart + interval.durationMillis

                val metrics =
                    series
                        .mapNotNull { (metricName, timeSeries) ->
                            val pointsInBucket =
                                timeSeries.dataPoints.filter {
                                    it.timestamp >= bucketStart && it.timestamp < bucketEnd
                                }

                            if (pointsInBucket.isEmpty()) {
                                null
                            } else {
                                val values = pointsInBucket.map { it.value }
                                metricName to AggregatedValue.fromValues(values)
                            }
                        }.toMap()

                if (metrics.isEmpty()) {
                    null
                } else {
                    AggregatedMetrics(
                        timestamp = bucketStart,
                        interval = interval,
                        metrics = metrics,
                    )
                }
            }.filterNotNull()
    }

    /**
     * Calculate percentiles for a set of values
     *
     * @param values Data values
     * @param percentiles Desired percentiles (1-99)
     * @return Map of percentile rank to value
     */
    fun calculatePercentiles(
        values: List<Double>,
        percentiles: List<Int> = defaultPercentiles,
    ): Map<Int, Double> {
        require(values.isNotEmpty()) {
            "Values list cannot be empty"
        }
        require(percentiles.all { it in 1..99 }) {
            "All percentiles must be in range [1, 99]"
        }

        val sorted = values.sorted()

        return percentiles.associateWith { p ->
            val index = ((p / 100.0) * (sorted.size - 1)).toInt()
            sorted[index.coerceIn(0, sorted.size - 1)]
        }
    }

    /**
     * Detect seasonality in time-series data
     *
     * Uses autocorrelation analysis to identify periodic patterns.
     *
     * @param timeSeries Time series to analyze
     * @param maxPeriodMillis Maximum period to search for (default: 7 days)
     * @return Seasonality detection result
     */
    fun detectSeasonality(
        timeSeries: TimeSeries,
        maxPeriodMillis: Long = 604800_000L, // 7 days
    ): SeasonalityResult {
        require(timeSeries.size >= 10) {
            "Need at least 10 data points for seasonality detection"
        }

        val values = timeSeries.values
        val avgInterval =
            if (timeSeries.size > 1) {
                timeSeries.timeSpan / (timeSeries.size - 1)
            } else {
                return SeasonalityResult(false, null, 0.0, 0.0)
            }

        val maxLag = (maxPeriodMillis / avgInterval).toInt().coerceIn(2, values.size / 2)
        val autocorrelations =
            (1..maxLag).map { lag ->
                lag to calculateAutocorrelation(values, lag)
            }

        // Find peaks in autocorrelation (indicates periodicity)
        val significantPeaks =
            autocorrelations.filter { (lag, corr) ->
                corr > 0.3 && lag > 2 // Significant correlation at non-trivial lag
            }

        return if (significantPeaks.isEmpty()) {
            SeasonalityResult(
                hasSeasonality = false,
                period = null,
                strength = 0.0,
                confidence = 1.0,
            )
        } else {
            val (bestLag, strength) = significantPeaks.maxByOrNull { it.second }!!
            val period = bestLag * avgInterval

            SeasonalityResult(
                hasSeasonality = true,
                period = period,
                strength = strength,
                confidence = min(1.0, strength * 1.5),
            )
        }
    }

    /**
     * Smooth time-series data using specified method
     *
     * Reduces noise while preserving underlying trends.
     *
     * @param timeSeries Input time series
     * @param method Smoothing method to use
     * @param windowSize Window size for smoothing (default: 5)
     * @return Smoothed time series
     */
    fun smooth(
        timeSeries: TimeSeries,
        method: SmoothingMethod,
        windowSize: Int = 5,
    ): TimeSeries {
        require(timeSeries.size >= method.requiresMinimumPoints) {
            "Need at least ${method.requiresMinimumPoints} points for ${method.displayName}"
        }
        require(windowSize >= method.requiresMinimumPoints) {
            "Window size must be >= ${method.requiresMinimumPoints} for ${method.displayName}"
        }
        require(windowSize % 2 == 1) {
            "Window size must be odd, got: $windowSize"
        }

        val smoothedPoints =
            when (method) {
                SmoothingMethod.MOVING_AVERAGE -> applyMovingAverage(timeSeries, windowSize)
                SmoothingMethod.EXPONENTIAL_SMOOTHING -> applyExponentialSmoothing(timeSeries, alpha = 0.2)
                SmoothingMethod.SAVITZKY_GOLAY -> applySavitzkyGolay(timeSeries, windowSize)
            }

        return timeSeries.copy(dataPoints = smoothedPoints)
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Group data points into time buckets
     */
    private fun groupIntoBuckets(
        timeSeries: TimeSeries,
        interval: TimeInterval,
    ): Map<Long, List<TimeSeriesDataPoint>> {
        val buckets = mutableMapOf<Long, MutableList<TimeSeriesDataPoint>>()

        timeSeries.dataPoints.forEach { point ->
            val bucketTimestamp = alignToBucket(point.timestamp, interval)
            buckets.getOrPut(bucketTimestamp) { mutableListOf() }.add(point)
        }

        return buckets
    }

    /**
     * Align timestamp to bucket start time
     */
    private fun alignToBucket(
        timestamp: Long,
        interval: TimeInterval,
    ): Long =
        when (interval) {
            TimeInterval.HOURLY -> (timestamp / interval.durationMillis) * interval.durationMillis
            TimeInterval.DAILY -> (timestamp / interval.durationMillis) * interval.durationMillis
            TimeInterval.WEEKLY -> (timestamp / interval.durationMillis) * interval.durationMillis
            TimeInterval.MONTHLY -> {
                // Simplified: align to 30-day buckets from epoch
                (timestamp / interval.durationMillis) * interval.durationMillis
            }
        }

    /**
     * Generate bucket timestamps for a time range
     */
    private fun generateBucketTimestamps(
        startTime: Long,
        endTime: Long,
        interval: TimeInterval,
    ): List<Long> {
        val timestamps = mutableListOf<Long>()
        var current = alignToBucket(startTime, interval)

        while (current <= endTime) {
            timestamps.add(current)
            current += interval.durationMillis
        }

        return timestamps
    }

    /**
     * Calculate autocorrelation at specified lag
     */
    private fun calculateAutocorrelation(
        values: List<Double>,
        lag: Int,
    ): Double {
        if (lag >= values.size) return 0.0

        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()

        if (variance == 0.0) return 0.0

        val covariance =
            (0 until values.size - lag)
                .map { i ->
                    (values[i] - mean) * (values[i + lag] - mean)
                }.average()

        return covariance / variance
    }

    /**
     * Apply simple moving average smoothing
     */
    private fun applyMovingAverage(
        timeSeries: TimeSeries,
        windowSize: Int,
    ): List<TimeSeriesDataPoint> {
        val halfWindow = windowSize / 2

        return timeSeries.dataPoints.mapIndexed { index, point ->
            val start = max(0, index - halfWindow)
            val end = min(timeSeries.size - 1, index + halfWindow)

            val windowValues = (start..end).map { timeSeries.dataPoints[it].value }
            val smoothedValue = windowValues.average()

            TimeSeriesDataPoint(point.timestamp, smoothedValue)
        }
    }

    /**
     * Apply exponential smoothing
     */
    private fun applyExponentialSmoothing(
        timeSeries: TimeSeries,
        alpha: Double = 0.2,
    ): List<TimeSeriesDataPoint> {
        require(alpha in 0.0..1.0) {
            "Alpha must be in [0, 1], got: $alpha"
        }

        val smoothed = mutableListOf<TimeSeriesDataPoint>()
        var previousSmoothed = timeSeries.dataPoints.first().value

        timeSeries.dataPoints.forEach { point ->
            val smoothedValue = alpha * point.value + (1 - alpha) * previousSmoothed
            smoothed.add(TimeSeriesDataPoint(point.timestamp, smoothedValue))
            previousSmoothed = smoothedValue
        }

        return smoothed
    }

    /**
     * Apply Savitzky-Golay smoothing (simplified polynomial smoothing)
     */
    private fun applySavitzkyGolay(
        timeSeries: TimeSeries,
        windowSize: Int,
    ): List<TimeSeriesDataPoint> {
        // Simplified implementation: use weighted moving average
        // Full SG filter requires matrix operations for polynomial fitting
        val halfWindow = windowSize / 2

        // Gaussian-like weights
        val weights =
            (-halfWindow..halfWindow).map { offset ->
                exp(-(offset * offset).toDouble() / (2.0 * (windowSize / 3.0).pow(2)))
            }
        val weightSum = weights.sum()
        val normalizedWeights = weights.map { it / weightSum }

        return timeSeries.dataPoints.mapIndexed { index, point ->
            val start = max(0, index - halfWindow)
            val end = min(timeSeries.size - 1, index + halfWindow)

            var smoothedValue = 0.0
            var actualWeightSum = 0.0

            for (i in start..end) {
                val weightIndex = i - index + halfWindow
                if (weightIndex in normalizedWeights.indices) {
                    smoothedValue += timeSeries.dataPoints[i].value * normalizedWeights[weightIndex]
                    actualWeightSum += normalizedWeights[weightIndex]
                }
            }

            TimeSeriesDataPoint(point.timestamp, smoothedValue / actualWeightSum)
        }
    }
}

/**
 * Seasonality detection result
 *
 * @property hasSeasonality Whether seasonal pattern was detected
 * @property period Period duration in milliseconds (null if no seasonality)
 * @property strength Strength of seasonality (0-1, higher is stronger)
 * @property confidence Confidence in detection (0-1, higher is more confident)
 */
data class SeasonalityResult(
    val hasSeasonality: Boolean,
    val period: Long?,
    val strength: Double,
    val confidence: Double,
) {
    init {
        require(strength in 0.0..1.0) {
            "Strength must be in [0, 1], got: $strength"
        }
        require(confidence in 0.0..1.0) {
            "Confidence must be in [0, 1], got: $confidence"
        }
        if (hasSeasonality) {
            requireNotNull(period) {
                "Period must be provided when seasonality is detected"
            }
            require(period > 0) {
                "Period must be positive, got: $period"
            }
        }
    }

    /**
     * Period in hours (if seasonality detected)
     */
    val periodHours: Double?
        get() = period?.let { it / 3600_000.0 }

    /**
     * Period in days (if seasonality detected)
     */
    val periodDays: Double?
        get() = period?.let { it / 86400_000.0 }

    /**
     * Whether seasonality is strong and reliable
     */
    val isReliable: Boolean
        get() = hasSeasonality && strength >= 0.5 && confidence >= 0.7

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            if (hasSeasonality) {
                "Seasonality detected: ${periodHours?.let { "%.1f hours".format(it) }} " +
                    "(strength: ${(strength * 100).toInt()}%, confidence: ${(confidence * 100).toInt()}%)"
            } else {
                "No seasonality detected"
            }
}
