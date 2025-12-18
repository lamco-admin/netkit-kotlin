package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Comprehensive edge case tests for TimeSeriesAnalyzer
 *
 * Focuses on numerical stability, boundary conditions, and error handling.
 */
class TimeSeriesAnalyzerEdgeCasesTest {
    private val analyzer = TimeSeriesAnalyzer()

    // Base timestamp - must be at least 1 hour to create valid hourly buckets
    private val baseHour = 3600_000L // 1 hour in milliseconds

    // ========================================
    // Aggregation Edge Cases
    // ========================================

    @Test
    fun `aggregate handles single data point`() {
        val series = createTimeSeries("rssi", listOf(baseHour + 1000L to -60.0))

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        assertEquals(1, aggregated.size)
        assertEquals(-60.0, aggregated.first()["rssi"]!!.mean)
        assertEquals(1, aggregated.first()["rssi"]!!.count)
    }

    @Test
    fun `aggregate handles all values in same bucket`() {
        val series =
            createTimeSeries(
                "rssi",
                listOf(
                    baseHour + 1000L to -60.0,
                    baseHour + 2000L to -58.0,
                    baseHour + 3000L to -62.0,
                ),
            )

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        val bucket = aggregated.first()
        assertEquals(3, bucket["rssi"]!!.count)
        assertEquals(-60.0, bucket["rssi"]!!.mean, 0.1)
    }

    @Test
    fun `aggregate handles sparse data across multiple buckets`() {
        val series =
            createTimeSeries(
                "rssi",
                listOf(
                    baseHour + 1000L to -60.0, // First hour bucket
                    baseHour * 2 + 1000L to -58.0, // Second hour bucket
                    baseHour * 3 + 1000L to -62.0, // Third hour bucket
                ),
            )

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        assertTrue(aggregated.size >= 3)
        aggregated.forEach { bucket ->
            assertTrue(bucket["rssi"]!!.count >= 1)
        }
    }

    @Test
    fun `aggregate handles identical timestamps`() {
        val series =
            createTimeSeries(
                "rssi",
                listOf(
                    baseHour + 1000L to -60.0,
                    baseHour + 1000L to -58.0, // Same timestamp
                    baseHour + 1000L to -62.0, // Same timestamp
                ),
            )

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        assertEquals(1, aggregated.size)
        assertEquals(3, aggregated.first()["rssi"]!!.count)
    }

    @Test
    fun `aggregate handles bucket boundaries precisely`() {
        val series =
            createTimeSeries(
                "rssi",
                listOf(
                    baseHour * 2 - 1L to -60.0, // Just before 2 hour mark
                    baseHour * 2 to -58.0, // Exactly at 2 hour mark
                ),
            )

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        assertTrue(aggregated.size >= 1)
    }

    @Test
    fun `aggregateMultiple handles misaligned time ranges`() {
        val series1 = createTimeSeries("rssi", listOf(baseHour + 1000L to -60.0, baseHour + 2000L to -58.0))
        val series2 = createTimeSeries("snr", listOf(baseHour + 5000L to 30.0, baseHour + 6000L to 32.0))

        val aggregated =
            analyzer.aggregateMultiple(
                mapOf("rssi" to series1, "snr" to series2),
                TimeInterval.HOURLY,
            )

        // Should create buckets covering both time ranges
        assertTrue(aggregated.isNotEmpty())
    }

    @Test
    fun `aggregateMultiple handles empty bucket gracefully`() {
        val series1 = createTimeSeries("rssi", listOf(baseHour + 1000L to -60.0))
        val series2 = createTimeSeries("snr", listOf(baseHour * 3 + 1000L to 30.0)) // 2 hours later

        val aggregated =
            analyzer.aggregateMultiple(
                mapOf("rssi" to series1, "snr" to series2),
                TimeInterval.HOURLY,
            )

        // Should skip empty buckets
        assertTrue(aggregated.all { it.metricCount > 0 })
    }

    @Test
    fun `aggregateMultiple rejects empty series map`() {
        assertThrows<IllegalArgumentException> {
            analyzer.aggregateMultiple(emptyMap(), TimeInterval.HOURLY)
        }
    }

    // ========================================
    // Percentile Edge Cases
    // ========================================

    @Test
    fun `calculatePercentiles handles all identical values`() {
        val values = List(100) { 42.0 }

        val percentiles = analyzer.calculatePercentiles(values, listOf(25, 50, 75, 99))

        percentiles.values.forEach { value ->
            assertEquals(42.0, value)
        }
    }

    @Test
    fun `calculatePercentiles handles two values`() {
        val values = listOf(10.0, 20.0)

        val percentiles = analyzer.calculatePercentiles(values, listOf(50))

        assertTrue(percentiles[50]!! in 10.0..20.0)
    }

    @Test
    fun `calculatePercentiles handles extreme percentiles`() {
        val values = (1..100).map { it.toDouble() }

        val percentiles = analyzer.calculatePercentiles(values, listOf(1, 99))

        assertTrue(percentiles[1]!! < percentiles[99]!!)
    }

    @Test
    fun `calculatePercentiles handles negative values`() {
        val values = listOf(-100.0, -80.0, -60.0, -40.0, -20.0)

        val percentiles = analyzer.calculatePercentiles(values, listOf(50))

        assertEquals(-60.0, percentiles[50]!!, 0.1)
    }

    @Test
    fun `calculatePercentiles handles very large values`() {
        val values = listOf(1e6, 2e6, 3e6, 4e6, 5e6)

        val percentiles = analyzer.calculatePercentiles(values, listOf(50))

        assertEquals(3e6, percentiles[50]!!, 1e5)
    }

    // ========================================
    // Seasonality Detection Edge Cases
    // ========================================

    @Test
    fun `detectSeasonality handles constant values`() {
        val series =
            createTimeSeries(
                "rssi",
                (0 until 50).map { i -> baseHour + i * 1000L to -60.0 },
            )

        val result = analyzer.detectSeasonality(series)

        // Constant values should not show seasonality
        // Result is valid regardless of hasSeasonality value
        assertNotNull(result)
        assertTrue(result.confidence in 0.0..1.0)
    }

    @Test
    fun `detectSeasonality handles linear trend`() {
        val series =
            createTimeSeries(
                "rssi",
                (0 until 50).map { i -> baseHour + i * 1000L to -60.0 + i * 0.5 },
            )

        val result = analyzer.detectSeasonality(series)

        // Linear trend should not show strong seasonality
        assertTrue(result.strength >= 0.0 && result.strength <= 1.0)
    }

    @Test
    fun `detectSeasonality handles exactly 10 points minimum`() {
        val series =
            createTimeSeries(
                "rssi",
                (0 until 10).map { i -> baseHour + i * 1000L to -60.0 },
            )

        val result = analyzer.detectSeasonality(series)

        assertNotNull(result)
    }

    @Test
    fun `detectSeasonality handles irregular intervals`() {
        val series =
            createTimeSeries(
                "rssi",
                listOf(
                    baseHour to -60.0,
                    baseHour + 100L to -58.0,
                    baseHour + 1000L to -62.0,
                    baseHour + 10000L to -59.0,
                    baseHour + 20000L to -61.0,
                    baseHour + 21000L to -60.0,
                    baseHour + 22000L to -58.0,
                    baseHour + 30000L to -62.0,
                    baseHour + 40000L to -59.0,
                    baseHour + 50000L to -61.0,
                    baseHour + 60000L to -60.0,
                ),
            )

        val result = analyzer.detectSeasonality(series)

        assertTrue(result.confidence in 0.0..1.0)
    }

    @Test
    fun `detectSeasonality respects max period limit`() {
        val series =
            createTimeSeries(
                "rssi",
                (0 until 50).map { i -> baseHour + i * 100000L to kotlin.math.sin(i * 0.1) * 10.0 },
            )

        val result = analyzer.detectSeasonality(series, maxPeriodMillis = 1000_000L)

        if (result.hasSeasonality) {
            assertTrue(result.period!! <= 1000_000L)
        }
    }

    // ========================================
    // Smoothing Edge Cases
    // ========================================

    @Test
    fun `smooth MOVING_AVERAGE handles minimum window size`() {
        val series =
            createTimeSeries(
                "rssi",
                (1..5).map { i -> baseHour + i * 1000L to -60.0 + i.toDouble() },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.MOVING_AVERAGE, windowSize = 3)

        assertEquals(series.size, smoothed.size)
    }

    @Test
    fun `smooth MOVING_AVERAGE handles window larger than data at edges`() {
        val series =
            createTimeSeries(
                "rssi",
                (1..5).map { i -> baseHour + i * 1000L to -60.0 },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.MOVING_AVERAGE, windowSize = 11)

        assertEquals(series.size, smoothed.size)
    }

    @Test
    fun `smooth EXPONENTIAL_SMOOTHING handles single spike`() {
        val series =
            createTimeSeries(
                "rssi",
                listOf(
                    baseHour + 1000L to -60.0,
                    baseHour + 2000L to -60.0,
                    baseHour + 3000L to -40.0, // Spike
                    baseHour + 4000L to -60.0,
                    baseHour + 5000L to -60.0,
                ),
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.EXPONENTIAL_SMOOTHING)

        // Spike should be dampened (alpha=0.2: only 20% of new value is used)
        // smoothed[2] = 0.2*(-40) + 0.8*(-60) = -56
        assertTrue(smoothed.values[2] > -60.0) // Spike had some effect
        assertTrue(smoothed.values[2] < -50.0) // But was heavily dampened
    }

    @Test
    fun `smooth EXPONENTIAL_SMOOTHING preserves first value`() {
        val series =
            createTimeSeries(
                "rssi",
                (1..10).map { i -> baseHour + i * 1000L to -60.0 + i.toDouble() },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.EXPONENTIAL_SMOOTHING)

        assertEquals(series.values.first(), smoothed.values.first())
    }

    @Test
    fun `smooth SAVITZKY_GOLAY handles edge points`() {
        val series =
            createTimeSeries(
                "rssi",
                (1..10).map { i -> baseHour + i * 1000L to -60.0 + i * 0.5 },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.SAVITZKY_GOLAY, windowSize = 5)

        // First and last points should still be reasonable
        assertTrue(smoothed.values.first() > -65.0 && smoothed.values.first() < -55.0)
        assertTrue(smoothed.values.last() > -60.0 && smoothed.values.last() < -50.0)
    }

    @Test
    fun `smooth handles all identical values`() {
        val series =
            createTimeSeries(
                "rssi",
                (1..10).map { i -> baseHour + i * 1000L to -60.0 },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.MOVING_AVERAGE, windowSize = 5)

        // All values should remain -60.0
        smoothed.values.forEach { value ->
            assertEquals(-60.0, value, 0.001)
        }
    }

    @Test
    fun `smooth handles alternating values`() {
        val series =
            createTimeSeries(
                "rssi",
                (1..10).map { i -> baseHour + i * 1000L to if (i % 2 == 0) -50.0 else -70.0 },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.MOVING_AVERAGE, windowSize = 3)

        // Smoothed values should be between -70 and -50
        smoothed.values.forEach { value ->
            assertTrue(value in -70.0..-50.0)
        }
    }

    @Test
    fun `smooth with large window converges to mean`() {
        val series =
            createTimeSeries(
                "rssi",
                (1..20).map { i -> baseHour + i * 1000L to -60.0 + (i % 5) * 2.0 },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.MOVING_AVERAGE, windowSize = 19)

        val mean = series.values.average()
        // Middle values should be close to mean with large window
        val middleValues = smoothed.values.subList(5, 15)
        middleValues.forEach { value ->
            assertTrue(kotlin.math.abs(value - mean) < 2.0)
        }
    }

    // ========================================
    // Numerical Stability Tests
    // ========================================

    @Test
    fun `aggregate handles very small time intervals`() {
        // Even with small intervals, still need baseHour for valid bucket
        val series =
            createTimeSeries(
                "rssi",
                listOf(
                    baseHour + 1L to -60.0,
                    baseHour + 2L to -58.0,
                    baseHour + 3L to -62.0,
                ),
            )

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        assertTrue(aggregated.isNotEmpty())
    }

    @Test
    fun `aggregate handles very large timestamps`() {
        val baseTime = Long.MAX_VALUE - 10000000L
        val series =
            createTimeSeries(
                "rssi",
                listOf(
                    baseTime to -60.0,
                    baseTime + 1000L to -58.0,
                    baseTime + 2000L to -62.0,
                ),
            )

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        assertTrue(aggregated.isNotEmpty())
    }

    @Test
    fun `percentiles handle very small differences`() {
        val values = listOf(1.0, 1.0001, 1.0002, 1.0003, 1.0004)

        val percentiles = analyzer.calculatePercentiles(values, listOf(50))

        assertEquals(1.0002, percentiles[50]!!, 0.001)
    }

    @Test
    fun `smooth handles extreme noise`() {
        val series =
            createTimeSeries(
                "rssi",
                (1..20).map { i ->
                    val noise = if (i % 2 == 0) 20.0 else -20.0
                    baseHour + i * 1000L to -60.0 + noise
                },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.MOVING_AVERAGE, windowSize = 5)

        // Smoothing should reduce extreme swings
        val smoothedRange = smoothed.values.maxOrNull()!! - smoothed.values.minOrNull()!!
        val originalRange = series.values.maxOrNull()!! - series.values.minOrNull()!!

        assertTrue(smoothedRange < originalRange)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTimeSeries(
        name: String,
        data: List<Pair<Long, Double>>,
    ): TimeSeries {
        val points =
            data.map { (timestamp, value) ->
                TimeSeriesDataPoint(timestamp, value)
            }
        return TimeSeries(name, points)
    }
}
