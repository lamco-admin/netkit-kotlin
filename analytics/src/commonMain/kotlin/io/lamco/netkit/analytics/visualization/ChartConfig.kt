package io.lamco.netkit.analytics.visualization

/**
 * Line chart configuration
 *
 * @property title Chart title
 * @property xAxisLabel X-axis label
 * @property yAxisLabel Y-axis label
 * @property showGrid Whether to show grid lines
 * @property showLegend Whether to show legend
 * @property lineColor Line color hex code
 * @property lineWidth Line width in pixels
 * @property fillArea Whether to fill area under line
 * @property markers Whether to show data point markers
 */
data class LineChartConfig(
    val title: String,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val showGrid: Boolean = true,
    val showLegend: Boolean = true,
    val lineColor: String = "#2196F3",
    val lineWidth: Float = 2.0f,
    val fillArea: Boolean = false,
    val markers: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
        require(lineWidth > 0f) { "Line width must be positive" }
        require(lineColor.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            "Invalid line color: $lineColor"
        }
    }
}

/**
 * Area chart configuration
 *
 * @property title Chart title
 * @property xAxisLabel X-axis label
 * @property yAxisLabel Y-axis label
 * @property fillColor Fill color with opacity (e.g., "#2196F350")
 * @property lineColor Outline color
 * @property showGrid Whether to show grid
 */
data class AreaChartConfig(
    val title: String,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val fillColor: String = "#2196F350",
    val lineColor: String = "#2196F3",
    val showGrid: Boolean = true,
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
    }
}

/**
 * Multi-series chart configuration
 *
 * @property title Chart title
 * @property xAxisLabel X-axis label
 * @property yAxisLabel Y-axis label
 * @property colorScheme Color scheme for series
 * @property showLegend Whether to show legend
 * @property showGrid Whether to show grid
 */
data class MultiSeriesConfig(
    val title: String,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val colorScheme: ColorScheme = ColorScheme.DEFAULT,
    val showLegend: Boolean = true,
    val showGrid: Boolean = true,
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
    }
}

/**
 * Bar chart configuration
 *
 * @property title Chart title
 * @property orientation Vertical or horizontal bars
 * @property stacked Whether bars are stacked
 * @property colorScheme Color scheme for bars
 * @property showValues Whether to show value labels on bars
 * @property barWidth Bar width ratio (0.0-1.0)
 */
data class BarChartConfig(
    val title: String,
    val orientation: Orientation = Orientation.VERTICAL,
    val stacked: Boolean = false,
    val colorScheme: ColorScheme = ColorScheme.DEFAULT,
    val showValues: Boolean = true,
    val barWidth: Float = 0.8f,
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
        require(barWidth in 0.1f..1.0f) {
            "Bar width must be in range 0.1-1.0, got: $barWidth"
        }
    }
}

/**
 * Pie chart configuration
 *
 * @property title Chart title
 * @property showPercentages Whether to show percentages
 * @property showLegend Whether to show legend
 * @property donutMode Whether to render as donut chart
 * @property donutHoleSize Donut hole size ratio (0.0-0.8)
 */
data class PieChartConfig(
    val title: String,
    val showPercentages: Boolean = true,
    val showLegend: Boolean = true,
    val donutMode: Boolean = false,
    val donutHoleSize: Float = 0.5f,
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
        if (donutMode) {
            require(donutHoleSize in 0.1f..0.8f) {
                "Donut hole size must be in range 0.1-0.8"
            }
        }
    }
}

/**
 * Scatter plot configuration
 *
 * @property title Chart title
 * @property xAxisLabel X-axis label
 * @property yAxisLabel Y-axis label
 * @property pointSize Point size in pixels
 * @property pointColor Point color
 * @property showTrendLine Whether to show regression line
 * @property showGrid Whether to show grid
 */
data class ScatterPlotConfig(
    val title: String,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val pointSize: Float = 4.0f,
    val pointColor: String = "#2196F3",
    val showTrendLine: Boolean = false,
    val showGrid: Boolean = true,
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
        require(pointSize > 0f) { "Point size must be positive" }
    }
}

/**
 * Box plot configuration
 *
 * @property title Chart title
 * @property yAxisLabel Y-axis label
 * @property showOutliers Whether to show outlier points
 * @property showMean Whether to show mean marker
 * @property colorScheme Color scheme
 */
data class BoxPlotConfig(
    val title: String,
    val yAxisLabel: String,
    val showOutliers: Boolean = true,
    val showMean: Boolean = true,
    val colorScheme: ColorScheme = ColorScheme.DEFAULT,
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
    }
}

/**
 * Heatmap configuration
 *
 * @property colorScale Color scale for heatmap
 * @property interpolation Interpolation method
 * @property showGrid Whether to show grid overlay
 * @property showContours Whether to show contour lines
 * @property resolution Grid resolution (meters or units)
 * @property contourLevels Number of contour levels (if enabled)
 */
data class HeatmapConfig(
    val colorScale: ColorScale,
    val interpolation: InterpolationMethod,
    val showGrid: Boolean = false,
    val showContours: Boolean = false,
    val resolution: Double = 1.0,
    val contourLevels: Int = 5,
) {
    init {
        require(resolution > 0.0) { "Resolution must be positive" }
        require(contourLevels > 0) { "Contour levels must be positive" }
    }
}

/**
 * Coverage map configuration
 *
 * @property qualityThresholds RSSI thresholds for signal quality levels
 * @property showLabels Whether to show quality labels
 * @property showPercentages Whether to show coverage percentages
 */
data class CoverageMapConfig(
    val qualityThresholds: Map<SignalQuality, Int> = defaultQualityThresholds,
    val showLabels: Boolean = true,
    val showPercentages: Boolean = true,
) {
    companion object {
        val defaultQualityThresholds =
            mapOf(
                SignalQuality.EXCELLENT to -50,
                SignalQuality.GOOD to -60,
                SignalQuality.FAIR to -70,
                SignalQuality.POOR to -80,
                SignalQuality.VERY_POOR to -90,
            )
    }
}

/**
 * Topology graph configuration
 *
 * @property layout Graph layout algorithm
 * @property nodeSize Default node size
 * @property showLabels Whether to show node labels
 * @property showEdgeLabels Whether to show edge labels
 * @property animated Whether to animate layout
 */
data class TopologyGraphConfig(
    val layout: GraphLayout = GraphLayout.FORCE_DIRECTED,
    val nodeSize: Double = 10.0,
    val showLabels: Boolean = true,
    val showEdgeLabels: Boolean = false,
    val animated: Boolean = true,
) {
    init {
        require(nodeSize > 0.0) { "Node size must be positive" }
    }
}

/**
 * Channel diagram configuration
 *
 * @property showUtilization Whether to show utilization
 * @property showOverlaps Whether to highlight overlaps
 * @property highlightOptimal Whether to highlight optimal channels
 */
data class ChannelDiagramConfig(
    val showUtilization: Boolean = true,
    val showOverlaps: Boolean = true,
    val highlightOptimal: Boolean = false,
)

/**
 * Color schemes for charts
 */
enum class ColorScheme {
    /** Default blue theme */
    DEFAULT,

    /** Material design colors */
    MATERIAL,

    /** Pastel colors */
    PASTEL,

    /** High contrast */
    HIGH_CONTRAST,

    /** Colorblind-friendly */
    COLORBLIND_SAFE,

    /** Monochrome grayscale */
    MONOCHROME,
}

/**
 * Export format for charts
 */
enum class ExportFormat {
    /** PNG raster image */
    PNG,

    /** SVG vector image */
    SVG,

    /** HTML with embedded JavaScript chart */
    HTML,

    /** JSON chart data */
    JSON,

    /** CSV data export */
    CSV,
}

/**
 * Export configuration
 *
 * @property format Export format
 * @property width Image width (pixels)
 * @property height Image height (pixels)
 * @property dpi Image DPI (for PNG)
 * @property includeMetadata Whether to include metadata
 */
data class ExportConfig(
    val format: ExportFormat,
    val width: Int = 800,
    val height: Int = 600,
    val dpi: Int = 96,
    val includeMetadata: Boolean = true,
) {
    init {
        require(width > 0) { "Width must be positive" }
        require(height > 0) { "Height must be positive" }
        require(dpi > 0) { "DPI must be positive" }
    }
}
