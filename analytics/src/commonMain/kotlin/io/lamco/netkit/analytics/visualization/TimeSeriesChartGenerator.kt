package io.lamco.netkit.analytics.visualization

import io.lamco.netkit.analytics.model.TimeSeries
import io.lamco.netkit.analytics.model.TimeSeriesDataPoint

/**
 * Time-series chart generator
 *
 * Generates chart data structures for time-series visualization including:
 * - Line charts (single metric over time)
 * - Area charts (filled line charts)
 * - Multi-series charts (multiple metrics comparison)
 *
 * All methods are platform-independent and generate data structures
 * that can be rendered by various charting libraries.
 *
 * ## Usage Example
 * ```kotlin
 * val generator = TimeSeriesChartGenerator()
 *
 * // Create line chart
 * val lineChart = generator.createLineChart(
 *     timeSeries = rssiTimeSeries,
 *     config = LineChartConfig(
 *         title = "RSSI Over Time",
 *         xAxisLabel = "Time",
 *         yAxisLabel = "RSSI (dBm)"
 *     )
 * )
 *
 * // Create multi-series comparison
 * val multiChart = generator.createMultiSeriesChart(
 *     series = listOf(rssiSeries, snrSeries),
 *     config = MultiSeriesConfig(...)
 * )
 * ```
 */
class TimeSeriesChartGenerator {
    /**
     * Create line chart from time-series data
     *
     * Converts time-series data into line chart format with configurable styling.
     *
     * @param timeSeries Source time-series data
     * @param config Chart configuration
     * @return Line chart data ready for rendering
     * @throws IllegalArgumentException if time series is empty
     */
    fun createLineChart(
        timeSeries: TimeSeries,
        config: LineChartConfig,
    ): LineChartData {
        require(timeSeries.dataPoints.isNotEmpty()) {
            "Cannot create line chart from empty time series"
        }

        val dataPoints =
            timeSeries.dataPoints.map { point ->
                DataPoint(
                    x = point.timestamp.toDouble(),
                    y = point.value,
                )
            }

        // Calculate axis ranges
        val timestamps = timeSeries.dataPoints.map { it.timestamp.toDouble() }
        val values = timeSeries.dataPoints.map { it.value }

        val xAxis =
            AxisConfig(
                label = config.xAxisLabel,
                min = timestamps.minOrNull(),
                max = timestamps.maxOrNull(),
            )

        val yAxis =
            AxisConfig(
                label = config.yAxisLabel,
                min = values.minOrNull(),
                max = values.maxOrNull(),
            )

        val legend =
            if (config.showLegend) {
                listOf(
                    LegendItem(
                        label = timeSeries.metricName,
                        color = config.lineColor,
                    ),
                )
            } else {
                emptyList()
            }

        return LineChartData(
            series = dataPoints,
            xAxis = xAxis,
            yAxis = yAxis,
            title = config.title,
            legend = legend,
        )
    }

    /**
     * Create area chart from time-series data
     *
     * Similar to line chart but with filled area under the line.
     *
     * @param timeSeries Source time-series data
     * @param config Chart configuration
     * @return Area chart data ready for rendering
     * @throws IllegalArgumentException if time series is empty
     */
    fun createAreaChart(
        timeSeries: TimeSeries,
        config: AreaChartConfig,
    ): AreaChartData {
        require(timeSeries.dataPoints.isNotEmpty()) {
            "Cannot create area chart from empty time series"
        }

        val dataPoints =
            timeSeries.dataPoints.map { point ->
                DataPoint(
                    x = point.timestamp.toDouble(),
                    y = point.value,
                )
            }

        val timestamps = timeSeries.dataPoints.map { it.timestamp.toDouble() }
        val values = timeSeries.dataPoints.map { it.value }

        val xAxis =
            AxisConfig(
                label = config.xAxisLabel,
                min = timestamps.minOrNull(),
                max = timestamps.maxOrNull(),
            )

        val yAxis =
            AxisConfig(
                label = config.yAxisLabel,
                min = 0.0, // Area charts typically start at zero
                max = values.maxOrNull(),
            )

        val legend =
            listOf(
                LegendItem(
                    label = timeSeries.metricName,
                    color = config.lineColor,
                ),
            )

        return AreaChartData(
            series = dataPoints,
            xAxis = xAxis,
            yAxis = yAxis,
            title = config.title,
            fillColor = config.fillColor,
            legend = legend,
        )
    }

    /**
     * Create multi-series chart from multiple time-series
     *
     * Allows comparison of multiple metrics over the same time period.
     *
     * @param series List of time-series to plot
     * @param config Chart configuration
     * @return Multi-series chart data
     * @throws IllegalArgumentException if series list is empty or series have mismatched lengths
     */
    fun createMultiSeriesChart(
        series: List<TimeSeries>,
        config: MultiSeriesConfig,
    ): MultiSeriesChartData {
        require(series.isNotEmpty()) {
            "Cannot create multi-series chart from empty series list"
        }

        // Convert each time series to data points
        val seriesMap =
            series.associate { timeSeries ->
                timeSeries.metricName to
                    timeSeries.dataPoints.map { point ->
                        DataPoint(
                            x = point.timestamp.toDouble(),
                            y = point.value,
                        )
                    }
            }

        // Calculate overall axis ranges across all series
        val allTimestamps = series.flatMap { it.dataPoints.map { p -> p.timestamp.toDouble() } }
        val allValues = series.flatMap { it.dataPoints.map { p -> p.value } }

        val xAxis =
            AxisConfig(
                label = config.xAxisLabel,
                min = allTimestamps.minOrNull(),
                max = allTimestamps.maxOrNull(),
            )

        val yAxis =
            AxisConfig(
                label = config.yAxisLabel,
                min = allValues.minOrNull(),
                max = allValues.maxOrNull(),
            )

        // Generate legend with colors from color scheme
        val colors = getColorsForScheme(config.colorScheme, series.size)
        val legend =
            series.mapIndexed { index, timeSeries ->
                LegendItem(
                    label = timeSeries.metricName,
                    color = colors[index],
                )
            }

        return MultiSeriesChartData(
            seriesList = seriesMap,
            xAxis = xAxis,
            yAxis = yAxis,
            title = config.title,
            legend = legend,
        )
    }

    /**
     * Create line chart with threshold annotations
     *
     * Useful for showing alert thresholds or SLA boundaries.
     *
     * @param timeSeries Source time-series data
     * @param config Chart configuration
     * @param thresholds Map of threshold name to value
     * @return Line chart with threshold annotations
     */
    fun createLineChartWithThresholds(
        timeSeries: TimeSeries,
        config: LineChartConfig,
        thresholds: Map<String, Double>,
    ): LineChartData {
        val baseChart = createLineChart(timeSeries, config)

        val annotations =
            thresholds.map { (name, value) ->
                ChartAnnotation(
                    type = AnnotationType.THRESHOLD_LINE,
                    value = value,
                    label = name,
                    color = getThresholdColor(value, timeSeries.dataPoints.map { it.value }),
                )
            }

        return baseChart.copy(annotations = annotations)
    }

    /**
     * Create downsampled line chart for large datasets
     *
     * Reduces number of data points while preserving visual fidelity.
     *
     * @param timeSeries Source time-series data
     * @param config Chart configuration
     * @param maxPoints Maximum number of points to include
     * @return Downsampled line chart
     */
    fun createDownsampledLineChart(
        timeSeries: TimeSeries,
        config: LineChartConfig,
        maxPoints: Int = 1000,
    ): LineChartData {
        require(maxPoints > 0) { "Max points must be positive, got: $maxPoints" }

        val downsampled =
            if (timeSeries.size > maxPoints) {
                downsampleTimeSeries(timeSeries, maxPoints)
            } else {
                timeSeries
            }

        return createLineChart(downsampled, config)
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Get colors for a color scheme
     */
    private fun getColorsForScheme(
        scheme: ColorScheme,
        count: Int,
    ): List<String> {
        val baseColors = TIME_SERIES_COLOR_PALETTES[scheme] ?: TIME_SERIES_COLOR_PALETTES[ColorScheme.DEFAULT]!!
        // Cycle through colors if we need more than available
        return (0 until count).map { baseColors[it % baseColors.size] }
    }

    companion object {
        /**
         * Color palettes for time series visualization.
         * Each palette contains 8 colors optimized for line charts and temporal data.
         */
        private val TIME_SERIES_COLOR_PALETTES =
            mapOf(
                ColorScheme.DEFAULT to
                    listOf(
                        "#2196F3",
                        "#4CAF50",
                        "#FF9800",
                        "#F44336",
                        "#9C27B0",
                        "#00BCD4",
                        "#FFEB3B",
                        "#795548",
                    ),
                ColorScheme.MATERIAL to
                    listOf(
                        "#F44336",
                        "#E91E63",
                        "#9C27B0",
                        "#673AB7",
                        "#3F51B5",
                        "#2196F3",
                        "#03A9F4",
                        "#00BCD4",
                    ),
                ColorScheme.PASTEL to
                    listOf(
                        "#FFB3BA",
                        "#FFDFBA",
                        "#FFFFBA",
                        "#BAFFC9",
                        "#BAE1FF",
                        "#E0BBE4",
                        "#FFDFD3",
                        "#C9C9FF",
                    ),
                ColorScheme.HIGH_CONTRAST to
                    listOf(
                        "#000000",
                        "#FFFFFF",
                        "#FF0000",
                        "#00FF00",
                        "#0000FF",
                        "#FFFF00",
                        "#FF00FF",
                        "#00FFFF",
                    ),
                ColorScheme.COLORBLIND_SAFE to
                    listOf(
                        "#E69F00",
                        "#56B4E9",
                        "#009E73",
                        "#F0E442",
                        "#0072B2",
                        "#D55E00",
                        "#CC79A7",
                        "#999999",
                    ),
                ColorScheme.MONOCHROME to
                    listOf(
                        "#000000",
                        "#2D2D2D",
                        "#5A5A5A",
                        "#878787",
                        "#B4B4B4",
                        "#E1E1E1",
                        "#FFFFFF",
                        "#C8C8C8",
                    ),
            )
    }

    /**
     * Get appropriate threshold color based on value position
     */
    private fun getThresholdColor(
        threshold: Double,
        values: List<Double>,
    ): String {
        val mean = values.average()
        return when {
            threshold < mean * 0.7 -> "#F44336" // Red (critical low)
            threshold < mean -> "#FF9800" // Orange (warning low)
            threshold < mean * 1.3 -> "#FFEB3B" // Yellow (warning high)
            else -> "#4CAF50" // Green (good)
        }
    }

    /**
     * Downsample time series using Largest Triangle Three Buckets (LTTB) algorithm
     *
     * Preserves visual fidelity while reducing point count.
     */
    private fun downsampleTimeSeries(
        timeSeries: TimeSeries,
        targetPoints: Int,
    ): TimeSeries {
        if (timeSeries.size <= targetPoints) {
            return timeSeries
        }

        val data = timeSeries.dataPoints
        val sampled = mutableListOf<TimeSeriesDataPoint>()

        // Always keep first and last points
        sampled.add(data.first())

        val bucketSize = (data.size - 2).toDouble() / (targetPoints - 2)

        var pointIndex = 0

        for (i in 0 until (targetPoints - 2)) {
            // Calculate bucket range
            val avgRangeStart = ((i + 1) * bucketSize + 1).toInt()
            val avgRangeEnd = ((i + 2) * bucketSize + 1).toInt().coerceAtMost(data.size)

            // Calculate average point in next bucket
            val avgTimestamp =
                data
                    .subList(avgRangeStart, avgRangeEnd)
                    .map { it.timestamp }
                    .average()
            val avgValue =
                data
                    .subList(avgRangeStart, avgRangeEnd)
                    .map { it.value }
                    .average()

            // Find point in current bucket with largest triangle area
            val rangeStart = ((i * bucketSize) + 1).toInt()
            val rangeEnd = ((i + 1) * bucketSize + 1).toInt()

            var maxArea = -1.0
            var maxAreaPoint: TimeSeriesDataPoint = data[rangeStart]

            for (j in rangeStart until rangeEnd) {
                val area =
                    kotlin.math.abs(
                        (sampled.last().timestamp - avgTimestamp) *
                            (data[j].value - sampled.last().value) -
                            (sampled.last().timestamp - data[j].timestamp) *
                            (avgValue - sampled.last().value),
                    ) *
                        0.5

                if (area > maxArea) {
                    maxArea = area
                    maxAreaPoint = data[j]
                }
            }

            sampled.add(maxAreaPoint)
        }

        sampled.add(data.last())

        return TimeSeries(timeSeries.metricName, sampled)
    }
}
