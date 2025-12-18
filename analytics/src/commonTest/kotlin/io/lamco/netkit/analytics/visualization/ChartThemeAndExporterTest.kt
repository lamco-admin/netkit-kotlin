package io.lamco.netkit.analytics.visualization

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class ChartThemeTest {
    // ========================================
    // Theme Creation Tests
    // ========================================

    @Test
    fun `ChartTheme can be created with valid parameters`() {
        val theme =
            ChartTheme(
                name = "Custom Theme",
                colorPalette =
                    ColorPalette(
                        primary = "#000000",
                        secondary = "#FFFFFF",
                        background = "#F0F0F0",
                        text = "#333333",
                        grid = "#CCCCCC",
                    ),
            )

        assertEquals("Custom Theme", theme.name)
        assertEquals("#000000", theme.colorPalette.primary)
    }

    @Test
    fun `ChartTheme rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            ChartTheme(
                name = "",
                colorPalette =
                    ColorPalette(
                        primary = "#000000",
                        secondary = "#FFFFFF",
                        background = "#F0F0F0",
                        text = "#333333",
                        grid = "#CCCCCC",
                    ),
            )
        }
    }

    // ========================================
    // Predefined Themes Tests
    // ========================================

    @Test
    fun `MATERIAL_LIGHT theme has correct properties`() {
        val theme = ChartTheme.MATERIAL_LIGHT

        assertEquals("Material Light", theme.name)
        assertEquals("#FFFFFF", theme.colorPalette.background)
        assertEquals("#212121", theme.colorPalette.text)
    }

    @Test
    fun `MATERIAL_DARK theme has correct properties`() {
        val theme = ChartTheme.MATERIAL_DARK

        assertEquals("Material Dark", theme.name)
        assertEquals("#121212", theme.colorPalette.background)
        assertEquals("#FFFFFF", theme.colorPalette.text)
    }

    @Test
    fun `HIGH_CONTRAST theme has correct properties`() {
        val theme = ChartTheme.HIGH_CONTRAST

        assertEquals("High Contrast", theme.name)
        assertTrue(theme.colorPalette.primary in listOf("#000000", "#FFFFFF"))
    }

    @Test
    fun `COLORBLIND_SAFE theme has correct properties`() {
        val theme = ChartTheme.COLORBLIND_SAFE

        assertEquals("Colorblind Safe", theme.name)
        assertNotNull(theme.colorPalette.primary)
    }

    @Test
    fun `PROFESSIONAL theme has correct properties`() {
        val theme = ChartTheme.PROFESSIONAL

        assertEquals("Professional", theme.name)
        assertNotNull(theme.colorPalette.primary)
    }

    // ========================================
    // ColorPalette Tests
    // ========================================

    @Test
    fun `ColorPalette validates hex color format`() {
        assertThrows<IllegalArgumentException> {
            ColorPalette(
                primary = "invalid",
                secondary = "#FFFFFF",
                background = "#F0F0F0",
                text = "#333333",
                grid = "#CCCCCC",
            )
        }
    }

    @Test
    fun `ColorPalette getQualityColor returns correct colors`() {
        val palette = ChartTheme.MATERIAL_LIGHT.colorPalette

        assertEquals(palette.good, palette.getQualityColor(SignalQuality.EXCELLENT))
        assertEquals(palette.good, palette.getQualityColor(SignalQuality.GOOD))
        assertEquals(palette.warning, palette.getQualityColor(SignalQuality.FAIR))
        assertEquals(palette.warning, palette.getQualityColor(SignalQuality.POOR))
        assertEquals(palette.critical, palette.getQualityColor(SignalQuality.VERY_POOR))
        assertEquals(palette.grid, palette.getQualityColor(SignalQuality.NO_SIGNAL))
    }

    @Test
    fun `ColorPalette getGradientColor returns correct color for value`() {
        val palette = ChartTheme.MATERIAL_LIGHT.colorPalette

        val highColor = palette.getGradientColor(value = 90.0, min = 0.0, max = 100.0)
        val midColor = palette.getGradientColor(value = 50.0, min = 0.0, max = 100.0)
        val lowColor = palette.getGradientColor(value = 10.0, min = 0.0, max = 100.0)

        assertEquals(palette.good, highColor)
        assertEquals(palette.warning, midColor)
        assertEquals(palette.critical, lowColor)
    }

    @Test
    fun `ColorPalette getGradientColor handles edge cases`() {
        val palette = ChartTheme.MATERIAL_LIGHT.colorPalette

        val minColor = palette.getGradientColor(value = 0.0, min = 0.0, max = 100.0)
        val maxColor = palette.getGradientColor(value = 100.0, min = 0.0, max = 100.0)

        assertNotNull(minColor)
        assertNotNull(maxColor)
    }

    // ========================================
    // Typography Tests
    // ========================================

    @Test
    fun `Typography has default values`() {
        val typography = Typography()

        assertEquals(18, typography.titleSize)
        assertEquals(14, typography.labelSize)
        assertEquals(12, typography.valueSize)
        assertEquals("sans-serif", typography.fontFamily)
    }

    @Test
    fun `Typography can be customized`() {
        val typography =
            Typography(
                titleSize = 24,
                labelSize = 16,
                valueSize = 14,
                fontFamily = "Arial",
            )

        assertEquals(24, typography.titleSize)
        assertEquals("Arial", typography.fontFamily)
    }

    @Test
    fun `Typography rejects negative sizes`() {
        assertThrows<IllegalArgumentException> {
            Typography(titleSize = -1)
        }
    }

    @Test
    fun `Typography rejects blank font family`() {
        assertThrows<IllegalArgumentException> {
            Typography(fontFamily = "")
        }
    }

    // ========================================
    // Spacing Tests
    // ========================================

    @Test
    fun `Spacing has default values`() {
        val spacing = Spacing()

        assertEquals(16, spacing.padding)
        assertEquals(8, spacing.margin)
        assertEquals(4, spacing.barGap)
        assertEquals(2, spacing.lineGap)
    }

    @Test
    fun `Spacing can be customized`() {
        val spacing =
            Spacing(
                padding = 24,
                margin = 12,
                barGap = 8,
                lineGap = 4,
            )

        assertEquals(24, spacing.padding)
        assertEquals(8, spacing.barGap)
    }

    @Test
    fun `Spacing rejects negative values`() {
        assertThrows<IllegalArgumentException> {
            Spacing(padding = -1)
        }
    }
}

class ChartExporterTest {
    private val exporter = ChartExporter()

    // ========================================
    // JSON Export Tests
    // ========================================

    @Test
    fun `exportToJson exports line chart`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0), DataPoint(2.0, 20.0)),
                xAxis = AxisConfig("X", 0.0, 10.0),
                yAxis = AxisConfig("Y", 0.0, 100.0),
                title = "Test Chart",
            )

        val json = exporter.exportToJson(chartData)

        assertTrue(json.contains("\"title\": \"Test Chart\""))
        assertTrue(json.contains("\"x\": 1.0"))
        assertTrue(json.contains("\"y\": 10.0"))
    }

    @Test
    fun `exportToJson exports bar chart`() {
        val chartData =
            BarChartData(
                categories = listOf("A", "B"),
                values = listOf(10.0, 20.0),
                title = "Bar Test",
            )

        val json = exporter.exportToJson(chartData)

        assertTrue(json.contains("\"title\": \"Bar Test\""))
        assertTrue(json.contains("\"A\""))
        assertTrue(json.contains("10.0"))
    }

    @Test
    fun `exportToJson exports pie chart`() {
        val chartData =
            PieChartData(
                slices = mapOf("Slice A" to 30.0, "Slice B" to 70.0),
                title = "Pie Test",
            )

        val json = exporter.exportToJson(chartData)

        assertTrue(json.contains("\"title\": \"Pie Test\""))
        assertTrue(json.contains("\"Slice A\""))
    }

    @Test
    fun `exportToJson includes metadata when requested`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )
        val config = ExportConfig(ExportFormat.JSON, includeMetadata = true)

        val json = exporter.exportToJson(chartData, config)

        assertTrue(json.contains("metadata"))
    }

    @Test
    fun `exportToJson rejects non-JSON format config`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )
        val config = ExportConfig(ExportFormat.CSV)

        assertThrows<IllegalArgumentException> {
            exporter.exportToJson(chartData, config)
        }
    }

    // ========================================
    // CSV Export Tests
    // ========================================

    @Test
    fun `exportToCsv exports line chart`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0), DataPoint(2.0, 20.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )

        val csv = exporter.exportToCsv(chartData)

        assertTrue(csv.contains("X,Y"))
        assertTrue(csv.contains("1.0,10.0"))
        assertTrue(csv.contains("2.0,20.0"))
    }

    @Test
    fun `exportToCsv exports bar chart`() {
        val chartData =
            BarChartData(
                categories = listOf("A", "B"),
                values = listOf(10.0, 20.0),
                title = "Test",
            )

        val csv = exporter.exportToCsv(chartData)

        assertTrue(csv.contains("Category,Value"))
        assertTrue(csv.contains("A,10.0"))
        assertTrue(csv.contains("B,20.0"))
    }

    @Test
    fun `exportToCsv exports pie chart`() {
        val chartData =
            PieChartData(
                slices = mapOf("A" to 30.0, "B" to 70.0),
                title = "Test",
            )

        val csv = exporter.exportToCsv(chartData)

        assertTrue(csv.contains("Slice,Value,Percentage"))
        assertTrue(csv.contains("A,30.0"))
    }

    @Test
    fun `exportToCsv rejects non-CSV format config`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )
        val config = ExportConfig(ExportFormat.JSON)

        assertThrows<IllegalArgumentException> {
            exporter.exportToCsv(chartData, config)
        }
    }

    // ========================================
    // HTML Export Tests
    // ========================================

    @Test
    fun `exportToHtml generates valid HTML`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test Chart",
            )

        val html = exporter.exportToHtml(chartData)

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>Test Chart</title>"))
        assertTrue(html.contains("</html>"))
    }

    @Test
    fun `exportToHtml includes Chart_js CDN`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )

        val html = exporter.exportToHtml(chartData)

        assertTrue(html.contains("chart.js"))
    }

    @Test
    fun `exportToHtml applies theme styling`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )
        val theme = ChartTheme.MATERIAL_DARK

        val html = exporter.exportToHtml(chartData, theme = theme)

        assertTrue(html.contains(theme.colorPalette.background))
        assertTrue(html.contains(theme.colorPalette.text))
    }

    @Test
    fun `exportToHtml respects dimensions from config`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )
        val config = ExportConfig(ExportFormat.HTML, width = 1200, height = 800)

        val html = exporter.exportToHtml(chartData, config)

        assertTrue(html.contains("width: 1200px"))
        assertTrue(html.contains("height: 800px"))
    }

    @Test
    fun `exportToHtml rejects non-HTML format config`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )
        val config = ExportConfig(ExportFormat.JSON)

        assertThrows<IllegalArgumentException> {
            exporter.exportToHtml(chartData, config)
        }
    }

    // ========================================
    // Validation Tests
    // ========================================

    @Test
    fun `validateChartData accepts valid line chart`() {
        val chartData =
            LineChartData(
                series = listOf(DataPoint(1.0, 10.0)),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )

        assertTrue(exporter.validateChartData(chartData))
    }

    @Test
    fun `validateChartData rejects empty line chart`() {
        // LineChartData itself rejects empty series, so include creation in assertThrows
        assertThrows<IllegalArgumentException> {
            LineChartData(
                series = emptyList(),
                xAxis = AxisConfig("X"),
                yAxis = AxisConfig("Y"),
                title = "Test",
            )
        }
    }

    @Test
    fun `validateChartData accepts valid bar chart`() {
        val chartData =
            BarChartData(
                categories = listOf("A"),
                values = listOf(10.0),
                title = "Test",
            )

        assertTrue(exporter.validateChartData(chartData))
    }

    @Test
    fun `validateChartData accepts valid pie chart`() {
        val chartData =
            PieChartData(
                slices = mapOf("A" to 100.0),
                title = "Test",
            )

        assertTrue(exporter.validateChartData(chartData))
    }

    @Test
    fun `validateChartData accepts valid heatmap`() {
        val chartData =
            HeatmapData(
                grid = Grid2D(10, 10, 1.0, List(10) { List(10) { -60.0 } }),
                bounds = Bounds(0.0, 10.0, 0.0, 10.0),
                interpolationMethod = InterpolationMethod.IDW,
                colorScale = ColorScale.VIRIDIS,
            )

        assertTrue(exporter.validateChartData(chartData))
    }
}
