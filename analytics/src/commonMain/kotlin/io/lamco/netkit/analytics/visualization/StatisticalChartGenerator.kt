package io.lamco.netkit.analytics.visualization

/**
 * Statistical chart generator
 *
 * Generates chart data structures for statistical visualizations including:
 * - Bar charts (categorical comparisons)
 * - Pie charts (proportion visualization)
 * - Scatter plots (correlation visualization)
 * - Box plots (distribution analysis)
 *
 * All methods are platform-independent and generate data structures
 * that can be rendered by various charting libraries.
 *
 * ## Usage Example
 * ```kotlin
 * val generator = StatisticalChartGenerator()
 *
 * // Create bar chart
 * val barChart = generator.createBarChart(
 *     categories = listOf("Network A", "Network B", "Network C"),
 *     values = listOf(85.0, 92.0, 78.0),
 *     config = BarChartConfig(title = "Network Scores")
 * )
 *
 * // Create scatter plot
 * val scatter = generator.createScatterPlot(
 *     points = listOf(Pair(-60.0, 85.0), Pair(-55.0, 92.0)),
 *     config = ScatterPlotConfig(...)
 * )
 * ```
 */
class StatisticalChartGenerator {
    /**
     * Create bar chart for categorical data comparison
     *
     * @param categories Category labels (e.g., network names, time periods)
     * @param values Values for each category
     * @param config Chart configuration
     * @return Bar chart data ready for rendering
     * @throws IllegalArgumentException if categories and values have different sizes
     */
    fun createBarChart(
        categories: List<String>,
        values: List<Double>,
        config: BarChartConfig,
    ): BarChartData {
        require(categories.isNotEmpty()) { "Categories cannot be empty" }
        require(categories.size == values.size) {
            "Categories and values must have same size: ${categories.size} vs ${values.size}"
        }

        // Generate colors if not provided in config
        val colors = generateBarColors(values.size, config.colorScheme)

        return BarChartData(
            categories = categories,
            values = values,
            title = config.title,
            orientation = config.orientation,
            colors = colors,
        )
    }

    /**
     * Create pie chart for proportion visualization
     *
     * @param slices Map of slice label to value
     * @param config Chart configuration
     * @return Pie chart data ready for rendering
     * @throws IllegalArgumentException if slices is empty or contains negative values
     */
    fun createPieChart(
        slices: Map<String, Double>,
        config: PieChartConfig,
    ): PieChartData {
        require(slices.isNotEmpty()) { "Slices cannot be empty" }
        require(slices.all { it.value >= 0.0 }) {
            "Pie chart values must be non-negative"
        }

        // Generate colors for slices
        val colors =
            if (config.showLegend) {
                val colorList = generatePieColors(slices.size, ColorScheme.DEFAULT)
                slices.keys.zip(colorList).toMap()
            } else {
                null
            }

        return PieChartData(
            slices = slices,
            title = config.title,
            colors = colors,
        )
    }

    /**
     * Create scatter plot for correlation visualization
     *
     * @param points List of (x, y) coordinate pairs
     * @param config Chart configuration
     * @return Scatter plot data ready for rendering
     * @throws IllegalArgumentException if points list is empty
     */
    fun createScatterPlot(
        points: List<Pair<Double, Double>>,
        config: ScatterPlotConfig,
    ): ScatterPlotData {
        require(points.isNotEmpty()) { "Points cannot be empty" }

        val xValues = points.map { it.first }
        val yValues = points.map { it.second }

        val xAxis =
            AxisConfig(
                label = config.xAxisLabel,
                min = xValues.minOrNull(),
                max = xValues.maxOrNull(),
            )

        val yAxis =
            AxisConfig(
                label = config.yAxisLabel,
                min = yValues.minOrNull(),
                max = yValues.maxOrNull(),
            )

        return ScatterPlotData(
            points = points,
            title = config.title,
            xAxis = xAxis,
            yAxis = yAxis,
        )
    }

    /**
     * Create box plot for distribution analysis
     *
     * Calculates quartiles and outliers for each distribution.
     *
     * @param distributions Map of category name to value list
     * @param config Chart configuration
     * @return Box plot data ready for rendering
     * @throws IllegalArgumentException if distributions is empty or any distribution has < 5 values
     */
    fun createBoxPlot(
        distributions: Map<String, List<Double>>,
        config: BoxPlotConfig,
    ): BoxPlotData {
        require(distributions.isNotEmpty()) { "Distributions cannot be empty" }
        require(distributions.all { it.value.size >= 5 }) {
            "Each distribution must have at least 5 values for box plot"
        }

        val boxPlotStats =
            distributions.mapValues { (_, values) ->
                calculateBoxPlotStats(values)
            }

        // Calculate overall y-axis range
        val allValues = distributions.values.flatten()
        val yAxis =
            AxisConfig(
                label = config.yAxisLabel,
                min = allValues.minOrNull(),
                max = allValues.maxOrNull(),
            )

        return BoxPlotData(
            distributions = boxPlotStats,
            title = config.title,
            yAxis = yAxis,
        )
    }

    /**
     * Create grouped bar chart for multi-category comparison
     *
     * @param categories Main categories (e.g., networks)
     * @param groups Subcategories (e.g., metrics)
     * @param values 2D array: values[categoryIndex][groupIndex]
     * @param config Chart configuration
     * @return Bar chart data with grouped bars
     */
    fun createGroupedBarChart(
        categories: List<String>,
        groups: List<String>,
        values: List<List<Double>>,
        config: BarChartConfig,
    ): BarChartData {
        require(categories.isNotEmpty()) { "Categories cannot be empty" }
        require(groups.isNotEmpty()) { "Groups cannot be empty" }
        require(values.size == categories.size) {
            "Values must have entry for each category"
        }
        require(values.all { it.size == groups.size }) {
            "Each category must have values for all groups"
        }

        // Flatten values for grouped display
        val flatValues = values.flatten()
        val flatCategories =
            categories.flatMap { category ->
                groups.map { group -> "$category - $group" }
            }

        return createBarChart(flatCategories, flatValues, config)
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Calculate box plot statistics from values
     */
    private fun calculateBoxPlotStats(values: List<Double>): BoxPlotStats {
        val sorted = values.sorted()
        val n = sorted.size

        val q1Index = (n * 0.25).toInt()
        val q2Index = (n * 0.50).toInt()
        val q3Index = (n * 0.75).toInt()

        val q1 = sorted[q1Index]
        val median = sorted[q2Index]
        val q3 = sorted[q3Index]

        val iqr = q3 - q1
        val lowerBound = q1 - 1.5 * iqr
        val upperBound = q3 + 1.5 * iqr

        // Find min/max excluding outliers
        val nonOutliers = sorted.filter { it >= lowerBound && it <= upperBound }
        val min = nonOutliers.minOrNull() ?: sorted.first()
        val max = nonOutliers.maxOrNull() ?: sorted.last()

        // Identify outliers
        val outliers = sorted.filter { it < lowerBound || it > upperBound }

        return BoxPlotStats(
            min = min,
            q1 = q1,
            median = median,
            q3 = q3,
            max = max,
            outliers = outliers,
        )
    }

    /**
     * Generate colors for bar chart
     */
    private fun generateBarColors(
        count: Int,
        scheme: ColorScheme,
    ): List<String> {
        val baseColors = COLOR_SCHEME_PALETTES[scheme] ?: COLOR_SCHEME_PALETTES[ColorScheme.DEFAULT]!!
        return (0 until count).map { baseColors[it % baseColors.size] }
    }

    companion object {
        /**
         * Color palettes for each color scheme.
         * Each palette contains 8 colors that are cycled for chart visualization.
         */
        private val COLOR_SCHEME_PALETTES =
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
                        "#FF0000",
                        "#00FF00",
                        "#0000FF",
                        "#FFFF00",
                        "#FF00FF",
                        "#00FFFF",
                        "#FFFFFF",
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
     * Generate colors for pie chart slices
     */
    private fun generatePieColors(
        count: Int,
        scheme: ColorScheme,
    ): List<String> {
        // Use same logic as bar colors
        return generateBarColors(count, scheme)
    }
}
