package io.lamco.netkit.analytics.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class AggregatedMetricsTest {
    @Test
    fun `creates valid AggregatedMetrics`() {
        val metrics =
            AggregatedMetrics(
                timestamp = 1000L,
                interval = TimeInterval.HOURLY,
                metrics = mapOf("rssi" to createTestAggregatedValue()),
            )

        assertEquals(1000L, metrics.timestamp)
        assertEquals(TimeInterval.HOURLY, metrics.interval)
        assertEquals(1, metrics.metricCount)
    }

    @Test
    fun `rejects zero timestamp`() {
        assertThrows<IllegalArgumentException> {
            AggregatedMetrics(
                timestamp = 0,
                interval = TimeInterval.HOURLY,
                metrics = mapOf("rssi" to createTestAggregatedValue()),
            )
        }
    }

    @Test
    fun `rejects empty metrics map`() {
        assertThrows<IllegalArgumentException> {
            AggregatedMetrics(
                timestamp = 1000L,
                interval = TimeInterval.HOURLY,
                metrics = emptyMap(),
            )
        }
    }

    @Test
    fun `get operator returns metric value`() {
        val value = createTestAggregatedValue()
        val metrics =
            AggregatedMetrics(
                timestamp = 1000L,
                interval = TimeInterval.HOURLY,
                metrics = mapOf("rssi" to value),
            )

        assertEquals(value, metrics["rssi"])
    }

    @Test
    fun `get operator returns null for missing metric`() {
        val metrics =
            AggregatedMetrics(
                timestamp = 1000L,
                interval = TimeInterval.HOURLY,
                metrics = mapOf("rssi" to createTestAggregatedValue()),
            )

        assertNull(metrics["snr"])
    }

    @Test
    fun `hasMetric returns true for existing metric`() {
        val metrics =
            AggregatedMetrics(
                timestamp = 1000L,
                interval = TimeInterval.HOURLY,
                metrics = mapOf("rssi" to createTestAggregatedValue()),
            )

        assertTrue(metrics.hasMetric("rssi"))
    }

    @Test
    fun `hasMetric returns false for missing metric`() {
        val metrics =
            AggregatedMetrics(
                timestamp = 1000L,
                interval = TimeInterval.HOURLY,
                metrics = mapOf("rssi" to createTestAggregatedValue()),
            )

        assertFalse(metrics.hasMetric("snr"))
    }

    @Test
    fun `metricCount returns correct count`() {
        val metrics =
            AggregatedMetrics(
                timestamp = 1000L,
                interval = TimeInterval.HOURLY,
                metrics =
                    mapOf(
                        "rssi" to createTestAggregatedValue(),
                        "snr" to createTestAggregatedValue(),
                        "throughput" to createTestAggregatedValue(),
                    ),
            )

        assertEquals(3, metrics.metricCount)
    }

    @Test
    fun `totalDataPoints sums counts correctly`() {
        val metrics =
            AggregatedMetrics(
                timestamp = 1000L,
                interval = TimeInterval.HOURLY,
                metrics =
                    mapOf(
                        "rssi" to createTestAggregatedValue(count = 10),
                        "snr" to createTestAggregatedValue(count = 20),
                    ),
            )

        assertEquals(30, metrics.totalDataPoints)
    }

    private fun createTestAggregatedValue(count: Int = 10) =
        AggregatedValue(
            mean = -60.0,
            median = -60.0,
            stdDev = 5.0,
            min = -70.0,
            max = -50.0,
            count = count,
            percentiles = mapOf(50 to -60.0, 95 to -52.0),
        )
}

class AggregatedValueTest {
    @Test
    fun `creates valid AggregatedValue`() {
        val value =
            AggregatedValue(
                mean = -60.0,
                median = -60.0,
                stdDev = 5.0,
                min = -70.0,
                max = -50.0,
                count = 100,
            )

        assertEquals(-60.0, value.mean)
        assertEquals(-60.0, value.median)
        assertEquals(5.0, value.stdDev)
        assertEquals(100, value.count)
    }

    @Test
    fun `rejects zero count`() {
        assertThrows<IllegalArgumentException> {
            AggregatedValue(-60.0, -60.0, 5.0, -70.0, -50.0, count = 0)
        }
    }

    @Test
    fun `rejects min greater than max`() {
        assertThrows<IllegalArgumentException> {
            AggregatedValue(-60.0, -60.0, 5.0, min = -40.0, max = -50.0, count = 10)
        }
    }

    @Test
    fun `rejects negative std dev`() {
        assertThrows<IllegalArgumentException> {
            AggregatedValue(-60.0, -60.0, stdDev = -5.0, min = -70.0, max = -50.0, count = 10)
        }
    }

    @Test
    fun `range calculates correctly`() {
        val value = AggregatedValue(-60.0, -60.0, 5.0, -70.0, -50.0, 100)
        assertEquals(20.0, value.range)
    }

    @Test
    fun `coefficientOfVariation calculates correctly`() {
        val value = AggregatedValue(mean = 100.0, median = 100.0, stdDev = 15.0, min = 70.0, max = 130.0, count = 10)
        assertEquals(15.0, value.coefficientOfVariation!!, 0.001)
    }

    @Test
    fun `coefficientOfVariation returns null for zero mean`() {
        val value = AggregatedValue(0.0, 0.0, 5.0, -10.0, 10.0, 10)
        assertNull(value.coefficientOfVariation)
    }

    @Test
    fun `isStable returns true for low CV`() {
        val value = AggregatedValue(100.0, 100.0, stdDev = 10.0, 80.0, 120.0, 10)
        assertTrue(value.isStable) // CV = 10%
    }

    @Test
    fun `isVolatile returns true for high CV`() {
        val value = AggregatedValue(100.0, 100.0, stdDev = 35.0, 30.0, 170.0, 10)
        assertTrue(value.isVolatile) // CV = 35%
    }

    @Test
    fun `getPercentile returns correct value`() {
        val value =
            AggregatedValue(
                -60.0,
                -60.0,
                5.0,
                -70.0,
                -50.0,
                100,
                percentiles = mapOf(50 to -60.0, 95 to -52.0),
            )
        assertEquals(-52.0, value.getPercentile(95))
    }

    @Test
    fun `getPercentile returns null for missing percentile`() {
        val value = AggregatedValue(-60.0, -60.0, 5.0, -70.0, -50.0, 100)
        assertNull(value.getPercentile(95))
    }

    @Test
    fun `interquartileRange calculates correctly`() {
        val value =
            AggregatedValue(
                -60.0,
                -60.0,
                5.0,
                -70.0,
                -50.0,
                100,
                percentiles = mapOf(25 to -65.0, 75 to -55.0),
            )
        assertEquals(10.0, value.interquartileRange)
    }

    @Test
    fun `appearsNormal returns true when mean equals median`() {
        val value = AggregatedValue(-60.0, median = -60.0, stdDev = 5.0, -70.0, -50.0, 100)
        assertTrue(value.appearsNormal)
    }

    @Test
    fun `fromValues creates AggregatedValue from list`() {
        val values = listOf(-60.0, -65.0, -55.0, -70.0, -50.0)
        val agg = AggregatedValue.fromValues(values)

        assertTrue(agg.count == 5)
        assertTrue(agg.min == -70.0)
        assertTrue(agg.max == -50.0)
    }

    @Test
    fun `fromValues rejects empty list`() {
        assertThrows<IllegalArgumentException> {
            AggregatedValue.fromValues(emptyList())
        }
    }

    private fun createTestValue() = AggregatedValue(-60.0, -60.0, 5.0, -70.0, -50.0, 100)
}

class TimeSeriesTest {
    @Test
    fun `creates valid TimeSeries`() {
        val series =
            TimeSeries(
                metricName = "rssi",
                dataPoints =
                    listOf(
                        TimeSeriesDataPoint(1000L, -60.0),
                        TimeSeriesDataPoint(2000L, -58.0),
                    ),
            )

        assertEquals("rssi", series.metricName)
        assertEquals(2, series.size)
    }

    @Test
    fun `rejects blank metric name`() {
        assertThrows<IllegalArgumentException> {
            TimeSeries("", listOf(TimeSeriesDataPoint(1000L, -60.0)))
        }
    }

    @Test
    fun `rejects empty data points`() {
        assertThrows<IllegalArgumentException> {
            TimeSeries("rssi", emptyList())
        }
    }

    @Test
    fun `rejects non-chronological data points`() {
        assertThrows<IllegalArgumentException> {
            TimeSeries(
                "rssi",
                listOf(
                    TimeSeriesDataPoint(2000L, -60.0),
                    TimeSeriesDataPoint(1000L, -58.0), // Out of order
                ),
            )
        }
    }

    @Test
    fun `timeSpan calculates correctly`() {
        val series =
            TimeSeries(
                "rssi",
                listOf(
                    TimeSeriesDataPoint(1000L, -60.0),
                    TimeSeriesDataPoint(5000L, -58.0),
                ),
            )

        assertEquals(4000L, series.timeSpan)
    }

    @Test
    fun `values returns list of values only`() {
        val series =
            TimeSeries(
                "rssi",
                listOf(
                    TimeSeriesDataPoint(1000L, -60.0),
                    TimeSeriesDataPoint(2000L, -58.0),
                ),
            )

        assertEquals(listOf(-60.0, -58.0), series.values)
    }

    @Test
    fun `timestamps returns list of timestamps only`() {
        val series =
            TimeSeries(
                "rssi",
                listOf(
                    TimeSeriesDataPoint(1000L, -60.0),
                    TimeSeriesDataPoint(2000L, -58.0),
                ),
            )

        assertEquals(listOf(1000L, 2000L), series.timestamps)
    }

    @Test
    fun `filterByTimeRange returns filtered series`() {
        val series =
            TimeSeries(
                "rssi",
                listOf(
                    TimeSeriesDataPoint(1000L, -60.0),
                    TimeSeriesDataPoint(2000L, -58.0),
                    TimeSeriesDataPoint(3000L, -56.0),
                ),
            )

        val filtered = series.filterByTimeRange(1500L, 2500L)
        assertEquals(1, filtered.size)
        assertEquals(2000L, filtered.dataPoints.first().timestamp)
    }

    @Test
    fun `takeLast returns last N points`() {
        val series =
            TimeSeries(
                "rssi",
                listOf(
                    TimeSeriesDataPoint(1000L, -60.0),
                    TimeSeriesDataPoint(2000L, -58.0),
                    TimeSeriesDataPoint(3000L, -56.0),
                ),
            )

        val last = series.takeLast(2)
        assertEquals(2, last.size)
        assertEquals(2000L, last.dataPoints.first().timestamp)
    }
}

class TimeSeriesDataPointTest {
    @Test
    fun `creates valid TimeSeriesDataPoint`() {
        val point = TimeSeriesDataPoint(1000L, -60.0)
        assertEquals(1000L, point.timestamp)
        assertEquals(-60.0, point.value)
    }

    @Test
    fun `rejects zero timestamp`() {
        assertThrows<IllegalArgumentException> {
            TimeSeriesDataPoint(0L, -60.0)
        }
    }

    @Test
    fun `rejects infinite value`() {
        assertThrows<IllegalArgumentException> {
            TimeSeriesDataPoint(1000L, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `rejects NaN value`() {
        assertThrows<IllegalArgumentException> {
            TimeSeriesDataPoint(1000L, Double.NaN)
        }
    }
}
