package io.lamco.netkit.analytics.visualization

/**
 * Visualization theme for consistent chart styling
 *
 * Provides comprehensive theming for all chart types including
 * color palettes, typography, spacing, and style presets.
 *
 * ## Usage Example
 * ```kotlin
 * val theme = ChartTheme.MATERIAL_LIGHT
 *
 * // Apply theme colors to chart
 * val config = LineChartConfig(
 *     title = "RSSI Over Time",
 *     xAxisLabel = "Time",
 *     yAxisLabel = "RSSI",
 *     lineColor = theme.colorPalette.primary
 * )
 * ```
 */
data class ChartTheme(
    val name: String,
    val colorPalette: ColorPalette,
    val typography: Typography = Typography(),
    val spacing: Spacing = Spacing(),
) {
    init {
        require(name.isNotBlank()) { "Theme name cannot be blank" }
    }

    companion object {
        /**
         * Material Design light theme
         */
        val MATERIAL_LIGHT =
            ChartTheme(
                name = "Material Light",
                colorPalette =
                    ColorPalette(
                        primary = "#2196F3",
                        secondary = "#FFC107",
                        background = "#FFFFFF",
                        text = "#212121",
                        grid = "#E0E0E0",
                        good = "#4CAF50",
                        warning = "#FF9800",
                        critical = "#F44336",
                    ),
            )

        /**
         * Material Design dark theme
         */
        val MATERIAL_DARK =
            ChartTheme(
                name = "Material Dark",
                colorPalette =
                    ColorPalette(
                        primary = "#64B5F6",
                        secondary = "#FFD54F",
                        background = "#121212",
                        text = "#FFFFFF",
                        grid = "#424242",
                        good = "#81C784",
                        warning = "#FFB74D",
                        critical = "#E57373",
                    ),
            )

        /**
         * High contrast theme for accessibility
         */
        val HIGH_CONTRAST =
            ChartTheme(
                name = "High Contrast",
                colorPalette =
                    ColorPalette(
                        primary = "#000000",
                        secondary = "#FFFF00",
                        background = "#FFFFFF",
                        text = "#000000",
                        grid = "#808080",
                        good = "#00FF00",
                        warning = "#FFFF00",
                        critical = "#FF0000",
                    ),
            )

        /**
         * Colorblind-safe theme
         */
        val COLORBLIND_SAFE =
            ChartTheme(
                name = "Colorblind Safe",
                colorPalette =
                    ColorPalette(
                        primary = "#0072B2",
                        secondary = "#E69F00",
                        background = "#FFFFFF",
                        text = "#000000",
                        grid = "#CCCCCC",
                        good = "#009E73",
                        warning = "#F0E442",
                        critical = "#D55E00",
                    ),
            )

        /**
         * Professional print theme
         */
        val PROFESSIONAL =
            ChartTheme(
                name = "Professional",
                colorPalette =
                    ColorPalette(
                        primary = "#1565C0",
                        secondary = "#5E35B1",
                        background = "#FFFFFF",
                        text = "#263238",
                        grid = "#CFD8DC",
                        good = "#2E7D32",
                        warning = "#EF6C00",
                        critical = "#C62828",
                    ),
            )
    }
}

/**
 * Color palette for theme
 *
 * @property primary Primary accent color
 * @property secondary Secondary accent color
 * @property background Background color
 * @property text Text color
 * @property grid Grid line color
 * @property good Color for positive/good values
 * @property warning Color for warning values
 * @property critical Color for critical/error values
 */
data class ColorPalette(
    val primary: String,
    val secondary: String,
    val background: String,
    val text: String,
    val grid: String,
    val good: String = "#4CAF50",
    val warning: String = "#FF9800",
    val critical: String = "#F44336",
) {
    init {
        listOf(primary, secondary, background, text, grid, good, warning, critical).forEach { color ->
            require(color.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                "Invalid color format: $color (must be #RRGGBB)"
            }
        }
    }

    /**
     * Get color for signal quality
     */
    fun getQualityColor(quality: SignalQuality): String =
        when (quality) {
            SignalQuality.EXCELLENT -> good
            SignalQuality.GOOD -> good
            SignalQuality.FAIR -> warning
            SignalQuality.POOR -> warning
            SignalQuality.VERY_POOR -> critical
            SignalQuality.NO_SIGNAL -> grid
        }

    /**
     * Get color for value in range
     *
     * @param value Current value
     * @param min Minimum value in range
     * @param max Maximum value in range
     * @return Color based on position in range
     */
    fun getGradientColor(
        value: Double,
        min: Double,
        max: Double,
    ): String {
        val normalized = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
        return when {
            normalized >= 0.7 -> good
            normalized >= 0.4 -> warning
            else -> critical
        }
    }
}

/**
 * Typography settings for charts
 *
 * @property titleSize Title font size (px)
 * @property labelSize Axis label font size (px)
 * @property valueSize Value label font size (px)
 * @property fontFamily Font family name
 */
data class Typography(
    val titleSize: Int = 18,
    val labelSize: Int = 14,
    val valueSize: Int = 12,
    val fontFamily: String = "sans-serif",
) {
    init {
        require(titleSize > 0) { "Title size must be positive" }
        require(labelSize > 0) { "Label size must be positive" }
        require(valueSize > 0) { "Value size must be positive" }
        require(fontFamily.isNotBlank()) { "Font family cannot be blank" }
    }
}

/**
 * Spacing settings for charts
 *
 * @property padding Chart padding (px)
 * @property margin Chart margin (px)
 * @property barGap Gap between bars (px)
 * @property lineGap Gap between lines (px)
 */
data class Spacing(
    val padding: Int = 16,
    val margin: Int = 8,
    val barGap: Int = 4,
    val lineGap: Int = 2,
) {
    init {
        require(padding >= 0) { "Padding must be non-negative" }
        require(margin >= 0) { "Margin must be non-negative" }
        require(barGap >= 0) { "Bar gap must be non-negative" }
        require(lineGap >= 0) { "Line gap must be non-negative" }
    }
}
