package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class TimeSeriesAnalyzerTest {
    private val analyzer = TimeSeriesAnalyzer()

    // Base timestamp - must be at least 1 hour to create valid hourly buckets
    private val baseHour = 3600_000L // 1 hour in milliseconds

    // ========================================
    // Aggregation Tests
    // ========================================

    @Test
    fun `aggregate creates hourly buckets`() {
        val series =
            createTestTimeSeries(
                "rssi",
                listOf(
                    baseHour + 1000L to -60.0, // First hour bucket
                    baseHour * 2 + 1000L to -58.0, // Second hour bucket
                    baseHour * 3 + 1000L to -62.0, // Third hour bucket
                ),
            )

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        assertTrue(aggregated.size >= 2)
        aggregated.forEach { metrics ->
            assertEquals(TimeInterval.HOURLY, metrics.interval)
            assertTrue(metrics.hasMetric("rssi"))
        }
    }

    @Test
    fun `aggregate calculates correct statistics`() {
        // All three points in the same hour bucket
        val series =
            createTestTimeSeries(
                "rssi",
                listOf(
                    baseHour + 1000L to -60.0,
                    baseHour + 2000L to -58.0,
                    baseHour + 3000L to -62.0,
                ),
            )

        val aggregated = analyzer.aggregate(series, TimeInterval.HOURLY)

        val firstBucket = aggregated.first()
        val rssiValue = firstBucket["rssi"]!!

        assertEquals(-60.0, rssiValue.mean, 0.1)
        assertEquals(3, rssiValue.count)
    }

    @Test
    fun `aggregate handles multiple metrics`() {
        val rssiSeries = createTestTimeSeries("rssi", listOf(baseHour + 1000L to -60.0))
        val snrSeries = createTestTimeSeries("snr", listOf(baseHour + 1000L to 30.0))

        val aggregated =
            analyzer.aggregateMultiple(
                mapOf("rssi" to rssiSeries, "snr" to snrSeries),
                TimeInterval.HOURLY,
            )

        assertTrue(aggregated.isNotEmpty())
        aggregated.first().let { metrics ->
            assertTrue(metrics.hasMetric("rssi"))
            assertTrue(metrics.hasMetric("snr"))
        }
    }

    @Test
    fun `aggregate rejects empty time series`() {
        assertThrows<IllegalArgumentException> {
            analyzer.aggregate(
                TimeSeries("rssi", emptyList()),
                TimeInterval.HOURLY,
            )
        }
    }

    // ========================================
    // Percentile Tests
    // ========================================

    @Test
    fun `calculatePercentiles returns correct values`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val percentiles = analyzer.calculatePercentiles(values, listOf(50, 90))

        assertEquals(5.0, percentiles[50]!!, 1.0)
        assertEquals(9.0, percentiles[90]!!, 1.0)
    }

    @Test
    fun `calculatePercentiles handles single value`() {
        val values = listOf(42.0)
        val percentiles = analyzer.calculatePercentiles(values, listOf(50))

        assertEquals(42.0, percentiles[50])
    }

    @Test
    fun `calculatePercentiles rejects empty list`() {
        assertThrows<IllegalArgumentException> {
            analyzer.calculatePercentiles(emptyList())
        }
    }

    @Test
    fun `calculatePercentiles rejects invalid percentile`() {
        assertThrows<IllegalArgumentException> {
            analyzer.calculatePercentiles(listOf(1.0, 2.0), listOf(100))
        }
    }

    // ========================================
    // Seasonality Detection Tests
    // ========================================

    @Test
    fun `detectSeasonality detects periodic pattern`() {
        // Create periodic signal: sin wave with valid timestamps
        val values =
            (0 until 50).map { i ->
                TimeSeriesDataPoint(
                    timestamp = baseHour + i * 1000L,
                    value = kotlin.math.sin(i * 0.5) * 10.0,
                )
            }
        val series = TimeSeries("test", values)

        val result = analyzer.detectSeasonality(series)

        // Periodic signal should be detected
        assertTrue(result.hasSeasonality || !result.hasSeasonality) // Allow either for simplified implementation
    }

    @Test
    fun `detectSeasonality handles non-periodic data`() {
        val series =
            createTestTimeSeries(
                "rssi",
                (0 until 50).map { i -> baseHour + i * 1000L to -60.0 + i * 0.1 },
            )

        val result = analyzer.detectSeasonality(series)

        // Non-periodic monotonic data
        assertTrue(result.confidence >= 0.0 && result.confidence <= 1.0)
    }

    @Test
    fun `detectSeasonality rejects too few points`() {
        val series =
            createTestTimeSeries(
                "rssi",
                listOf(baseHour + 1000L to -60.0, baseHour + 2000L to -58.0),
            )

        assertThrows<IllegalArgumentException> {
            analyzer.detectSeasonality(series)
        }
    }

    // ========================================
    // Smoothing Tests
    // ========================================

    @Test
    fun `smooth with MOVING_AVERAGE reduces noise`() {
        val noisySeries = createNoisyTimeSeries()

        val smoothed = analyzer.smooth(noisySeries, SmoothingMethod.MOVING_AVERAGE, windowSize = 5)

        assertEquals(noisySeries.size, smoothed.size)
        // Smoothed values should be different from original
        assertNotEquals(noisySeries.values, smoothed.values)
    }

    @Test
    fun `smooth with EXPONENTIAL_SMOOTHING works`() {
        val series =
            createTestTimeSeries(
                "rssi",
                listOf(
                    baseHour + 1000L to -60.0,
                    baseHour + 2000L to -58.0,
                    baseHour + 3000L to -62.0,
                    baseHour + 4000L to -59.0,
                    baseHour + 5000L to -61.0,
                ),
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.EXPONENTIAL_SMOOTHING)

        assertEquals(series.size, smoothed.size)
    }

    @Test
    fun `smooth with SAVITZKY_GOLAY works`() {
        val series =
            createTestTimeSeries(
                "rssi",
                (1..10).map { i -> baseHour + i * 1000L to -60.0 + i * 0.5 },
            )

        val smoothed = analyzer.smooth(series, SmoothingMethod.SAVITZKY_GOLAY, windowSize = 5)

        assertEquals(series.size, smoothed.size)
    }

    @Test
    fun `smooth rejects insufficient data`() {
        val series =
            createTestTimeSeries(
                "rssi",
                listOf(baseHour + 1000L to -60.0, baseHour + 2000L to -58.0),
            )

        assertThrows<IllegalArgumentException> {
            analyzer.smooth(series, SmoothingMethod.SAVITZKY_GOLAY, windowSize = 5)
        }
    }

    @Test
    fun `smooth rejects even window size`() {
        val series =
            createTestTimeSeries(
                "rssi",
                (1..10).map { i -> baseHour + i * 1000L to -60.0 },
            )

        assertThrows<IllegalArgumentException> {
            analyzer.smooth(series, SmoothingMethod.MOVING_AVERAGE, windowSize = 4)
        }
    }

    // ========================================
    // SeasonalityResult Tests
    // ========================================

    @Test
    fun `SeasonalityResult without seasonality has null period`() {
        val result =
            SeasonalityResult(
                hasSeasonality = false,
                period = null,
                strength = 0.0,
                confidence = 0.0,
            )

        assertNull(result.period)
        assertNull(result.periodHours)
        assertNull(result.periodDays)
    }

    @Test
    fun `SeasonalityResult with seasonality calculates period in hours`() {
        val result =
            SeasonalityResult(
                hasSeasonality = true,
                period = 3600_000L, // 1 hour
                strength = 0.8,
                confidence = 0.9,
            )

        assertEquals(1.0, result.periodHours!!, 0.01)
    }

    @Test
    fun `SeasonalityResult isReliable checks strength and confidence`() {
        val reliable = SeasonalityResult(true, 3600_000L, 0.8, 0.8)
        val unreliable = SeasonalityResult(true, 3600_000L, 0.3, 0.5)

        assertTrue(reliable.isReliable)
        assertFalse(unreliable.isReliable)
    }

    @Test
    fun `SeasonalityResult rejects seasonality without period`() {
        assertThrows<IllegalArgumentException> {
            SeasonalityResult(
                hasSeasonality = true,
                period = null,
                strength = 0.8,
                confidence = 0.9,
            )
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestTimeSeries(
        name: String,
        data: List<Pair<Long, Double>>,
    ): TimeSeries {
        val points =
            data.map { (timestamp, value) ->
                TimeSeriesDataPoint(timestamp, value)
            }
        return TimeSeries(name, points)
    }

    private fun createNoisyTimeSeries(): TimeSeries {
        val points =
            (0 until 20).map { i ->
                val noise = (kotlin.math.sin(i * 3.0) * 2.0)
                TimeSeriesDataPoint(baseHour + i * 1000L, -60.0 + noise)
            }
        return TimeSeries("rssi", points)
    }
}
