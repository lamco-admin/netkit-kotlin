package io.lamco.netkit.analytics.visualization

/**
 * Base interface for all chart data structures
 *
 * All chart data models implement this marker interface to enable
 * polymorphic handling across different chart types.
 */
sealed interface ChartData

/**
 * Data point for chart series
 *
 * Represents a single point on a chart with optional labeling.
 *
 * @property x X-axis value (timestamp, category index, or continuous value)
 * @property y Y-axis value (metric measurement)
 * @property label Optional label for this specific point
 */
data class DataPoint(
    val x: Double,
    val y: Double,
    val label: String? = null,
) {
    init {
        require(x.isFinite()) { "X value must be finite, got: $x" }
        require(y.isFinite()) { "Y value must be finite, got: $y" }
    }
}

/**
 * Axis configuration for charts
 *
 * Defines how an axis should be displayed including scale, range, and labeling.
 *
 * @property label Axis label (e.g., "Time", "RSSI (dBm)")
 * @property min Minimum value (null for auto-scale)
 * @property max Maximum value (null for auto-scale)
 * @property unit Unit suffix (e.g., "dBm", "ms", "%")
 * @property logarithmic Whether to use logarithmic scale
 */
data class AxisConfig(
    val label: String,
    val min: Double? = null,
    val max: Double? = null,
    val unit: String? = null,
    val logarithmic: Boolean = false,
) {
    init {
        require(label.isNotBlank()) { "Axis label cannot be blank" }
        if (min != null && max != null) {
            // Allow min == max for single data points or constant values
            require(min <= max) { "Min must be less than or equal to max: min=$min, max=$max" }
        }
    }
}

/**
 * Legend item for chart series
 *
 * @property label Series name
 * @property color Color hex code (e.g., "#2196F3")
 * @property style Line/marker style identifier
 */
data class LegendItem(
    val label: String,
    val color: String,
    val style: String = "solid",
) {
    init {
        require(label.isNotBlank()) { "Legend label cannot be blank" }
        require(color.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            "Color must be valid hex code, got: $color"
        }
    }
}

/**
 * Chart annotation (marker, threshold line, region)
 *
 * @property type Type of annotation
 * @property value Value or position for annotation
 * @property label Annotation label
 * @property color Annotation color
 */
data class ChartAnnotation(
    val type: AnnotationType,
    val value: Double,
    val label: String = "",
    val color: String = "#FF5722",
)

/**
 * Annotation types for charts
 */
enum class AnnotationType {
    /** Horizontal threshold line */
    THRESHOLD_LINE,

    /** Vertical marker at specific point */
    MARKER,

    /** Highlighted region */
    REGION,

    /** Text annotation */
    TEXT,
}

// ========================================
// Line Chart Models
// ========================================

/**
 * Line chart data
 *
 * Represents time-series or continuous data as a line chart.
 *
 * @property series List of data points forming the line
 * @property xAxis X-axis configuration
 * @property yAxis Y-axis configuration
 * @property title Chart title
 * @property legend Legend items for series
 * @property annotations Optional annotations
 */
data class LineChartData(
    val series: List<DataPoint>,
    val xAxis: AxisConfig,
    val yAxis: AxisConfig,
    val title: String,
    val legend: List<LegendItem> = emptyList(),
    val annotations: List<ChartAnnotation> = emptyList(),
) : ChartData {
    init {
        require(series.isNotEmpty()) { "Line chart requires at least one data point" }
        require(title.isNotBlank()) { "Chart title cannot be blank" }
    }
}

/**
 * Area chart data (filled line chart)
 *
 * Similar to line chart but with filled area under the line.
 *
 * @property series List of data points
 * @property xAxis X-axis configuration
 * @property yAxis Y-axis configuration
 * @property title Chart title
 * @property fillColor Fill color hex code
 * @property legend Legend items
 */
data class AreaChartData(
    val series: List<DataPoint>,
    val xAxis: AxisConfig,
    val yAxis: AxisConfig,
    val title: String,
    val fillColor: String = "#2196F350", // 50% opacity blue
    val legend: List<LegendItem> = emptyList(),
) : ChartData {
    init {
        require(series.isNotEmpty()) { "Area chart requires at least one data point" }
        require(title.isNotBlank()) { "Chart title cannot be blank" }
    }
}

/**
 * Multi-series chart data (multiple lines/areas)
 *
 * @property seriesList List of named series
 * @property xAxis X-axis configuration
 * @property yAxis Y-axis configuration
 * @property title Chart title
 * @property legend Legend items for each series
 */
data class MultiSeriesChartData(
    val seriesList: Map<String, List<DataPoint>>,
    val xAxis: AxisConfig,
    val yAxis: AxisConfig,
    val title: String,
    val legend: List<LegendItem>,
) : ChartData {
    init {
        require(seriesList.isNotEmpty()) { "Multi-series chart requires at least one series" }
        require(title.isNotBlank()) { "Chart title cannot be blank" }
        require(seriesList.all { it.value.isNotEmpty() }) {
            "All series must have at least one data point"
        }
    }
}

// ========================================
// Statistical Chart Models
// ========================================

/**
 * Bar chart data
 *
 * @property categories Category labels (e.g., network names, time periods)
 * @property values Values for each category
 * @property title Chart title
 * @property orientation Vertical or horizontal bars
 * @property colors Optional color for each bar
 */
data class BarChartData(
    val categories: List<String>,
    val values: List<Double>,
    val title: String,
    val orientation: Orientation = Orientation.VERTICAL,
    val colors: List<String>? = null,
) : ChartData {
    init {
        require(categories.isNotEmpty()) { "Bar chart requires at least one category" }
        require(categories.size == values.size) {
            "Categories and values must have same size: ${categories.size} vs ${values.size}"
        }
        if (colors != null) {
            require(colors.size == categories.size) {
                "Colors list must match categories size"
            }
        }
        require(title.isNotBlank()) { "Chart title cannot be blank" }
    }
}

/**
 * Pie chart data
 *
 * @property slices Map of slice label to value
 * @property title Chart title
 * @property colors Optional colors for each slice
 */
data class PieChartData(
    val slices: Map<String, Double>,
    val title: String,
    val colors: Map<String, String>? = null,
) : ChartData {
    init {
        require(slices.isNotEmpty()) { "Pie chart requires at least one slice" }
        require(slices.all { it.value >= 0.0 }) {
            "Pie chart values must be non-negative"
        }
        require(title.isNotBlank()) { "Chart title cannot be blank" }
    }

    /** Total value of all slices */
    val total: Double = slices.values.sum()

    /** Percentage for each slice */
    val percentages: Map<String, Double> =
        if (total > 0.0) {
            slices.mapValues { (it.value / total) * 100.0 }
        } else {
            slices.mapValues { 0.0 }
        }
}

/**
 * Scatter plot data
 *
 * @property points List of (x, y) coordinate pairs
 * @property title Chart title
 * @property xAxis X-axis configuration
 * @property yAxis Y-axis configuration
 * @property labels Optional labels for points
 */
data class ScatterPlotData(
    val points: List<Pair<Double, Double>>,
    val title: String,
    val xAxis: AxisConfig,
    val yAxis: AxisConfig,
    val labels: List<String>? = null,
) : ChartData {
    init {
        require(points.isNotEmpty()) { "Scatter plot requires at least one point" }
        require(title.isNotBlank()) { "Chart title cannot be blank" }
        if (labels != null) {
            require(labels.size == points.size) {
                "Labels must match points size"
            }
        }
    }
}

/**
 * Box plot data (distribution visualization)
 *
 * @property distributions Map of category name to value list
 * @property title Chart title
 * @property yAxis Y-axis configuration
 */
data class BoxPlotData(
    val distributions: Map<String, BoxPlotStats>,
    val title: String,
    val yAxis: AxisConfig,
) : ChartData {
    init {
        require(distributions.isNotEmpty()) { "Box plot requires at least one distribution" }
        require(title.isNotBlank()) { "Chart title cannot be blank" }
    }
}

/**
 * Box plot statistics for a single distribution
 *
 * @property min Minimum value
 * @property q1 First quartile (25th percentile)
 * @property median Median (50th percentile)
 * @property q3 Third quartile (75th percentile)
 * @property max Maximum value
 * @property outliers List of outlier values
 */
data class BoxPlotStats(
    val min: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val max: Double,
    val outliers: List<Double> = emptyList(),
) {
    init {
        require(min <= q1) { "min must be <= q1" }
        require(q1 <= median) { "q1 must be <= median" }
        require(median <= q3) { "median must be <= q3" }
        require(q3 <= max) { "q3 must be <= max" }
    }

    /** Interquartile range */
    val iqr: Double = q3 - q1
}

// ========================================
// Supporting Enums
// ========================================

/**
 * Chart orientation
 */
enum class Orientation {
    VERTICAL,
    HORIZONTAL,
}
