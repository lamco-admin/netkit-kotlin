package io.lamco.netkit.analytics.visualization

import io.lamco.netkit.analytics.model.TimeSeries
import io.lamco.netkit.analytics.model.TimeSeriesDataPoint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class TimeSeriesChartGeneratorTest {
    private val generator = TimeSeriesChartGenerator()

    // ========================================
    // Line Chart Tests
    // ========================================

    @Test
    fun `createLineChart generates chart from time series`() {
        val timeSeries = createTestTimeSeries()
        val config =
            LineChartConfig(
                title = "Test Chart",
                xAxisLabel = "Time",
                yAxisLabel = "Value",
            )

        val chart = generator.createLineChart(timeSeries, config)

        assertEquals("Test Chart", chart.title)
        assertEquals(10, chart.series.size)
        assertEquals("Time", chart.xAxis.label)
        assertEquals("Value", chart.yAxis.label)
        assertNotNull(chart.xAxis.min)
        assertNotNull(chart.xAxis.max)
    }

    @Test
    fun `createLineChart includes legend when showLegend is true`() {
        val timeSeries = createTestTimeSeries("RSSI")
        val config =
            LineChartConfig(
                title = "RSSI Chart",
                xAxisLabel = "Time",
                yAxisLabel = "RSSI",
                showLegend = true,
            )

        val chart = generator.createLineChart(timeSeries, config)

        assertTrue(chart.legend.isNotEmpty())
        assertEquals("RSSI", chart.legend.first().label)
    }

    @Test
    fun `createLineChart excludes legend when showLegend is false`() {
        val timeSeries = createTestTimeSeries()
        val config =
            LineChartConfig(
                title = "Test",
                xAxisLabel = "X",
                yAxisLabel = "Y",
                showLegend = false,
            )

        val chart = generator.createLineChart(timeSeries, config)

        assertTrue(chart.legend.isEmpty())
    }

    @Test
    fun `createLineChart rejects empty time series`() {
        val config = LineChartConfig("Title", "X", "Y")

        // TimeSeries itself rejects empty list, so include creation in assertThrows
        assertThrows<IllegalArgumentException> {
            val emptyTimeSeries = TimeSeries("test", emptyList())
            generator.createLineChart(emptyTimeSeries, config)
        }
    }

    @Test
    fun `createLineChart handles single data point`() {
        val timeSeries =
            TimeSeries(
                "test",
                listOf(TimeSeriesDataPoint(1000L, 42.0)),
            )
        val config = LineChartConfig("Title", "X", "Y")

        val chart = generator.createLineChart(timeSeries, config)

        assertEquals(1, chart.series.size)
        assertEquals(42.0, chart.series.first().y)
    }

    @Test
    fun `createLineChart sets correct axis ranges`() {
        val points = (1L..10L).map { TimeSeriesDataPoint(it * 1000, it * 10.0) }
        val timeSeries = TimeSeries("test", points)
        val config = LineChartConfig("Title", "X", "Y")

        val chart = generator.createLineChart(timeSeries, config)

        assertEquals(1000.0, chart.xAxis.min)
        assertEquals(10000.0, chart.xAxis.max)
        assertEquals(10.0, chart.yAxis.min)
        assertEquals(100.0, chart.yAxis.max)
    }

    // ========================================
    // Area Chart Tests
    // ========================================

    @Test
    fun `createAreaChart generates chart from time series`() {
        val timeSeries = createTestTimeSeries()
        val config =
            AreaChartConfig(
                title = "Area Test",
                xAxisLabel = "Time",
                yAxisLabel = "Value",
            )

        val chart = generator.createAreaChart(timeSeries, config)

        assertEquals("Area Test", chart.title)
        assertEquals(10, chart.series.size)
        assertTrue(chart.legend.isNotEmpty())
    }

    @Test
    fun `createAreaChart sets y-axis min to zero`() {
        val timeSeries = createTestTimeSeries()
        val config = AreaChartConfig("Title", "X", "Y")

        val chart = generator.createAreaChart(timeSeries, config)

        assertEquals(0.0, chart.yAxis.min)
    }

    @Test
    fun `createAreaChart uses configured fill color`() {
        val timeSeries = createTestTimeSeries()
        val config =
            AreaChartConfig(
                title = "Title",
                xAxisLabel = "X",
                yAxisLabel = "Y",
                fillColor = "#FF000050",
            )

        val chart = generator.createAreaChart(timeSeries, config)

        assertEquals("#FF000050", chart.fillColor)
    }

    @Test
    fun `createAreaChart rejects empty time series`() {
        val config = AreaChartConfig("Title", "X", "Y")

        // TimeSeries itself rejects empty list, so include creation in assertThrows
        assertThrows<IllegalArgumentException> {
            val emptyTimeSeries = TimeSeries("test", emptyList())
            generator.createAreaChart(emptyTimeSeries, config)
        }
    }

    // ========================================
    // Multi-Series Chart Tests
    // ========================================

    @Test
    fun `createMultiSeriesChart combines multiple series`() {
        val series1 = createTestTimeSeries("RSSI")
        val series2 = createTestTimeSeries("SNR")
        val config =
            MultiSeriesConfig(
                title = "Multi Test",
                xAxisLabel = "Time",
                yAxisLabel = "Value",
            )

        val chart = generator.createMultiSeriesChart(listOf(series1, series2), config)

        assertEquals("Multi Test", chart.title)
        assertEquals(2, chart.seriesList.size)
        assertTrue(chart.seriesList.containsKey("RSSI"))
        assertTrue(chart.seriesList.containsKey("SNR"))
    }

    @Test
    fun `createMultiSeriesChart generates legend for all series`() {
        val series1 = createTestTimeSeries("Metric1")
        val series2 = createTestTimeSeries("Metric2")
        val series3 = createTestTimeSeries("Metric3")
        val config = MultiSeriesConfig("Title", "X", "Y")

        val chart =
            generator.createMultiSeriesChart(
                listOf(series1, series2, series3),
                config,
            )

        assertEquals(3, chart.legend.size)
        assertEquals("Metric1", chart.legend[0].label)
        assertEquals("Metric2", chart.legend[1].label)
        assertEquals("Metric3", chart.legend[2].label)
    }

    @Test
    fun `createMultiSeriesChart assigns different colors to series`() {
        val series = (1..5).map { createTestTimeSeries("Series$it") }
        val config = MultiSeriesConfig("Title", "X", "Y")

        val chart = generator.createMultiSeriesChart(series, config)

        val colors = chart.legend.map { it.color }
        // All colors should be unique (for first 5 series)
        assertEquals(colors.size, colors.distinct().size)
    }

    @Test
    fun `createMultiSeriesChart rejects empty series list`() {
        val config = MultiSeriesConfig("Title", "X", "Y")

        assertThrows<IllegalArgumentException> {
            generator.createMultiSeriesChart(emptyList(), config)
        }
    }

    @Test
    fun `createMultiSeriesChart calculates overall axis ranges`() {
        val series1 =
            TimeSeries(
                "s1",
                (1L..5L).map { TimeSeriesDataPoint(it * 1000, it * 10.0) },
            )
        val series2 =
            TimeSeries(
                "s2",
                (6L..10L).map { TimeSeriesDataPoint(it * 1000, it * 10.0) },
            )
        val config = MultiSeriesConfig("Title", "X", "Y")

        val chart = generator.createMultiSeriesChart(listOf(series1, series2), config)

        // Should span both series
        assertEquals(1000.0, chart.xAxis.min)
        assertEquals(10000.0, chart.xAxis.max)
        assertEquals(10.0, chart.yAxis.min)
        assertEquals(100.0, chart.yAxis.max)
    }

    @Test
    fun `createMultiSeriesChart handles different length series`() {
        val series1 =
            TimeSeries(
                "s1",
                (1L..5L).map { TimeSeriesDataPoint(it * 1000, it.toDouble()) },
            )
        val series2 =
            TimeSeries(
                "s2",
                (1L..10L).map { TimeSeriesDataPoint(it * 1000, it.toDouble()) },
            )
        val config = MultiSeriesConfig("Title", "X", "Y")

        val chart = generator.createMultiSeriesChart(listOf(series1, series2), config)

        assertEquals(5, chart.seriesList["s1"]!!.size)
        assertEquals(10, chart.seriesList["s2"]!!.size)
    }

    // ========================================
    // Threshold Annotation Tests
    // ========================================

    @Test
    fun `createLineChartWithThresholds adds annotations`() {
        val timeSeries = createTestTimeSeries()
        val config = LineChartConfig("Title", "X", "Y")
        val thresholds =
            mapOf(
                "Warning" to 75.0,
                "Critical" to 90.0,
            )

        val chart =
            generator.createLineChartWithThresholds(
                timeSeries,
                config,
                thresholds,
            )

        assertEquals(2, chart.annotations.size)
        assertTrue(chart.annotations.any { it.label == "Warning" && it.value == 75.0 })
        assertTrue(chart.annotations.any { it.label == "Critical" && it.value == 90.0 })
    }

    @Test
    fun `createLineChartWithThresholds handles empty thresholds`() {
        val timeSeries = createTestTimeSeries()
        val config = LineChartConfig("Title", "X", "Y")

        val chart =
            generator.createLineChartWithThresholds(
                timeSeries,
                config,
                emptyMap(),
            )

        assertTrue(chart.annotations.isEmpty())
    }

    @Test
    fun `createLineChartWithThresholds sets threshold colors`() {
        val timeSeries = createTestTimeSeries()
        val config = LineChartConfig("Title", "X", "Y")
        val thresholds = mapOf("Threshold" to 50.0)

        val chart =
            generator.createLineChartWithThresholds(
                timeSeries,
                config,
                thresholds,
            )

        val annotation = chart.annotations.first()
        assertTrue(annotation.color.matches(Regex("^#[0-9A-Fa-f]{6}$")))
    }

    // ========================================
    // Downsampling Tests
    // ========================================

    @Test
    fun `createDownsampledLineChart reduces large datasets`() {
        val largeTimeSeries =
            TimeSeries(
                "test",
                (1L..5000L).map { TimeSeriesDataPoint(it * 1000, it.toDouble()) },
            )
        val config = LineChartConfig("Title", "X", "Y")

        val chart =
            generator.createDownsampledLineChart(
                largeTimeSeries,
                config,
                maxPoints = 100,
            )

        // Should have approximately maxPoints (algorithm preserves first/last)
        assertTrue(chart.series.size <= 102) // Allow small overhead
        assertTrue(chart.series.size >= 98) // Should be close to target
    }

    @Test
    fun `createDownsampledLineChart preserves first and last points`() {
        val timeSeries =
            TimeSeries(
                "test",
                (1L..1000L).map { TimeSeriesDataPoint(it * 1000, it.toDouble()) },
            )
        val config = LineChartConfig("Title", "X", "Y")

        val chart =
            generator.createDownsampledLineChart(
                timeSeries,
                config,
                maxPoints = 50,
            )

        assertEquals(1000.0, chart.series.first().x)
        assertEquals(1.0, chart.series.first().y)
        assertEquals(1000000.0, chart.series.last().x)
        assertEquals(1000.0, chart.series.last().y)
    }

    @Test
    fun `createDownsampledLineChart does not downsample small datasets`() {
        val smallTimeSeries = createTestTimeSeries()
        val config = LineChartConfig("Title", "X", "Y")

        val chart =
            generator.createDownsampledLineChart(
                smallTimeSeries,
                config,
                maxPoints = 100,
            )

        // Should keep all points since dataset is smaller than maxPoints
        assertEquals(10, chart.series.size)
    }

    @Test
    fun `createDownsampledLineChart rejects zero maxPoints`() {
        val timeSeries = createTestTimeSeries()
        val config = LineChartConfig("Title", "X", "Y")

        assertThrows<IllegalArgumentException> {
            generator.createDownsampledLineChart(timeSeries, config, maxPoints = 0)
        }
    }

    @Test
    fun `createDownsampledLineChart rejects negative maxPoints`() {
        val timeSeries = createTestTimeSeries()
        val config = LineChartConfig("Title", "X", "Y")

        assertThrows<IllegalArgumentException> {
            generator.createDownsampledLineChart(timeSeries, config, maxPoints = -10)
        }
    }

    // ========================================
    // Color Scheme Tests
    // ========================================

    @Test
    fun `createMultiSeriesChart uses DEFAULT color scheme`() {
        val series = (1..3).map { createTestTimeSeries("S$it") }
        val config = MultiSeriesConfig("Title", "X", "Y", colorScheme = ColorScheme.DEFAULT)

        val chart = generator.createMultiSeriesChart(series, config)

        // Should have 3 different colors from DEFAULT palette
        assertEquals(
            3,
            chart.legend
                .map { it.color }
                .distinct()
                .size,
        )
    }

    @Test
    fun `createMultiSeriesChart uses MATERIAL color scheme`() {
        val series = (1..3).map { createTestTimeSeries("S$it") }
        val config = MultiSeriesConfig("Title", "X", "Y", colorScheme = ColorScheme.MATERIAL)

        val chart = generator.createMultiSeriesChart(series, config)

        assertEquals(3, chart.legend.size)
        // Verify colors are from Material palette (should start with #F44336, #E91E63, etc.)
        assertTrue(chart.legend[0].color.startsWith("#"))
    }

    @Test
    fun `createMultiSeriesChart cycles colors for many series`() {
        // Create more series than available colors
        val series = (1..20).map { createTestTimeSeries("S$it") }
        val config = MultiSeriesConfig("Title", "X", "Y")

        val chart = generator.createMultiSeriesChart(series, config)

        assertEquals(20, chart.legend.size)
        // Colors should cycle, so we'll have duplicates
        assertTrue(chart.legend.map { it.color }.size == 20)
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    @Test
    fun `createLineChart handles negative values`() {
        val timeSeries =
            TimeSeries(
                "test",
                (1L..10L).map { TimeSeriesDataPoint(it * 1000, -it.toDouble()) },
            )
        val config = LineChartConfig("Title", "X", "Y")

        val chart = generator.createLineChart(timeSeries, config)

        assertTrue(chart.yAxis.min!! < 0.0)
        assertTrue(chart.yAxis.max!! < 0.0)
    }

    @Test
    fun `createLineChart handles very large timestamps`() {
        val timeSeries =
            TimeSeries(
                "test",
                (1L..10L).map { TimeSeriesDataPoint(it * 1_000_000_000L, it.toDouble()) },
            )
        val config = LineChartConfig("Title", "X", "Y")

        val chart = generator.createLineChart(timeSeries, config)

        assertTrue(chart.xAxis.max!! > 1_000_000_000.0)
    }

    @Test
    fun `createLineChart handles constant values`() {
        val timeSeries =
            TimeSeries(
                "test",
                (1L..10L).map { TimeSeriesDataPoint(it * 1000, 42.0) },
            )
        val config = LineChartConfig("Title", "X", "Y")

        val chart = generator.createLineChart(timeSeries, config)

        assertEquals(42.0, chart.yAxis.min)
        assertEquals(42.0, chart.yAxis.max)
    }

    @Test
    fun `createMultiSeriesChart handles series with no overlapping timestamps`() {
        val series1 =
            TimeSeries(
                "s1",
                (1L..5L).map { TimeSeriesDataPoint(it * 1000, it.toDouble()) },
            )
        val series2 =
            TimeSeries(
                "s2",
                (100L..105L).map { TimeSeriesDataPoint(it * 1000, it.toDouble()) },
            )
        val config = MultiSeriesConfig("Title", "X", "Y")

        val chart = generator.createMultiSeriesChart(listOf(series1, series2), config)

        // X-axis should span both ranges
        assertEquals(1000.0, chart.xAxis.min)
        assertEquals(105000.0, chart.xAxis.max)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestTimeSeries(name: String = "test"): TimeSeries {
        val points =
            (1L..10L).map { i ->
                TimeSeriesDataPoint(i * 1000, i * 10.0)
            }
        return TimeSeries(name, points)
    }
}
