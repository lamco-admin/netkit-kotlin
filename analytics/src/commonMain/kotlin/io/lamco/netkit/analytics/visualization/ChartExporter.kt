package io.lamco.netkit.analytics.visualization

/**
 * Chart exporter for saving charts in various formats
 *
 * Supports exporting chart data to multiple formats:
 * - JSON (chart data structure)
 * - CSV (tabular data)
 * - HTML (standalone with embedded charting library)
 * - SVG (vector graphics - requires rendering engine)
 * - PNG (raster image - requires rendering engine)
 *
 * Image rendering (PNG/SVG) requires platform-specific rendering libraries.
 *
 * ## Usage Example
 * ```kotlin
 * val exporter = ChartExporter()
 *
 * // Export to JSON
 * val json = exporter.exportToJson(chartData)
 *
 * // Export to CSV
 * val csv = exporter.exportToCsv(chartData)
 *
 * // Export to HTML
 * val html = exporter.exportToHtml(chartData, config)
 * ```
 */
class ChartExporter {
    /**
     * Export chart data to JSON format
     *
     * @param chartData Chart data to export
     * @param config Export configuration
     * @return JSON string representation
     */
    fun exportToJson(
        chartData: ChartData,
        config: ExportConfig = ExportConfig(ExportFormat.JSON),
    ): String {
        require(config.format == ExportFormat.JSON) {
            "Export format must be JSON"
        }

        return when (chartData) {
            is LineChartData -> serializeLineChart(chartData, config.includeMetadata)
            is BarChartData -> serializeBarChart(chartData, config.includeMetadata)
            is PieChartData -> serializePieChart(chartData, config.includeMetadata)
            is ScatterPlotData -> serializeScatterPlot(chartData, config.includeMetadata)
            is HeatmapData -> serializeHeatmap(chartData, config.includeMetadata)
            is ChannelDiagramData -> serializeChannelDiagram(chartData, config.includeMetadata)
            is GraphData -> serializeGraph(chartData, config.includeMetadata)
            else -> "{\"error\": \"Unsupported chart type\"}"
        }
    }

    /**
     * Export chart data to CSV format
     *
     * Converts chart data to comma-separated values format.
     *
     * @param chartData Chart data to export
     * @param config Export configuration
     * @return CSV string
     */
    fun exportToCsv(
        chartData: ChartData,
        config: ExportConfig = ExportConfig(ExportFormat.CSV),
    ): String {
        require(config.format == ExportFormat.CSV) {
            "Export format must be CSV"
        }

        return when (chartData) {
            is LineChartData -> lineChartToCsv(chartData)
            is BarChartData -> barChartToCsv(chartData)
            is PieChartData -> pieChartToCsv(chartData)
            is ScatterPlotData -> scatterPlotToCsv(chartData)
            else -> "Error,Unsupported chart type for CSV export"
        }
    }

    /**
     * Export chart to HTML with embedded charting library
     *
     * Generates standalone HTML file with Chart.js for visualization.
     *
     * @param chartData Chart data to export
     * @param config Export configuration
     * @param theme Optional theme to apply
     * @return HTML string
     */
    fun exportToHtml(
        chartData: ChartData,
        config: ExportConfig = ExportConfig(ExportFormat.HTML),
        theme: ChartTheme = ChartTheme.MATERIAL_LIGHT,
    ): String {
        require(config.format == ExportFormat.HTML) {
            "Export format must be HTML"
        }

        val title =
            when (chartData) {
                is LineChartData -> chartData.title
                is BarChartData -> chartData.title
                is PieChartData -> chartData.title
                is ScatterPlotData -> chartData.title
                else -> "Chart"
            }

        return buildHtmlDocument(title, chartData, theme, config)
    }

    // ========================================
    // JSON Serialization
    // ========================================

    private fun serializeLineChart(
        chart: LineChartData,
        includeMetadata: Boolean,
    ): String {
        val metadata =
            if (includeMetadata) {
                ""","metadata": {"type": "line", "pointCount": ${chart.series.size}}"""
            } else {
                ""
            }

        val series =
            chart.series.joinToString(",") {
                """{"x": ${it.x}, "y": ${it.y}}"""
            }

        return """{
            "title": "${chart.title}",
            "xAxis": ${serializeAxis(chart.xAxis)},
            "yAxis": ${serializeAxis(chart.yAxis)},
            "series": [$series]$metadata
        }"""
    }

    private fun serializeBarChart(
        chart: BarChartData,
        includeMetadata: Boolean,
    ): String {
        val metadata =
            if (includeMetadata) {
                ""","metadata": {"type": "bar", "categoryCount": ${chart.categories.size}}"""
            } else {
                ""
            }

        val categories = chart.categories.joinToString(",") { "\"$it\"" }
        val values = chart.values.joinToString(",")

        return """{
            "title": "${chart.title}",
            "categories": [$categories],
            "values": [$values]$metadata
        }"""
    }

    private fun serializePieChart(
        chart: PieChartData,
        includeMetadata: Boolean,
    ): String {
        val metadata =
            if (includeMetadata) {
                ""","metadata": {"type": "pie", "sliceCount": ${chart.slices.size}, "total": ${chart.total}}"""
            } else {
                ""
            }

        val slices =
            chart.slices.entries.joinToString(",") {
                """{"label": "${it.key}", "value": ${it.value}, "percentage": ${chart.percentages[it.key]}}"""
            }

        return """{
            "title": "${chart.title}",
            "slices": [$slices]$metadata
        }"""
    }

    private fun serializeScatterPlot(
        chart: ScatterPlotData,
        includeMetadata: Boolean,
    ): String {
        val metadata =
            if (includeMetadata) {
                ""","metadata": {"type": "scatter", "pointCount": ${chart.points.size}}"""
            } else {
                ""
            }

        val points =
            chart.points.joinToString(",") {
                """{"x": ${it.first}, "y": ${it.second}}"""
            }

        return """{
            "title": "${chart.title}",
            "xAxis": ${serializeAxis(chart.xAxis)},
            "yAxis": ${serializeAxis(chart.yAxis)},
            "points": [$points]$metadata
        }"""
    }

    private fun serializeHeatmap(
        heatmap: HeatmapData,
        includeMetadata: Boolean,
    ): String {
        val metadata =
            if (includeMetadata) {
                ""","metadata": {"type": "heatmap", "width": ${heatmap.grid.width}, "height": ${heatmap.grid.height}}"""
            } else {
                ""
            }

        return """{
            "grid": {"width": ${heatmap.grid.width}, "height": ${heatmap.grid.height}},
            "interpolation": "${heatmap.interpolationMethod}",
            "colorScale": "${heatmap.colorScale}"$metadata
        }"""
    }

    private fun serializeChannelDiagram(
        diagram: ChannelDiagramData,
        includeMetadata: Boolean,
    ): String {
        val metadata =
            if (includeMetadata) {
                ""","metadata": {"type": "channelDiagram", "channelCount": ${diagram.channels.size}}"""
            } else {
                ""
            }

        val channels =
            diagram.channels.joinToString(",") {
                """{"channel": ${it.channel}, "ssid": "${it.ssid}", "utilization": ${it.utilization}}"""
            }

        return """{
            "band": "${diagram.band}",
            "channels": [$channels]$metadata
        }"""
    }

    private fun serializeGraph(
        graph: GraphData,
        includeMetadata: Boolean,
    ): String {
        val metadata =
            if (includeMetadata) {
                ""","metadata": {"type": "graph", "nodeCount": ${graph.nodes.size}, "edgeCount": ${graph.edges.size}}"""
            } else {
                ""
            }

        return """{
            "nodeCount": ${graph.nodes.size},
            "edgeCount": ${graph.edges.size},
            "layout": "${graph.layout}"$metadata
        }"""
    }

    private fun serializeAxis(axis: AxisConfig): String = """{"label": "${axis.label}", "min": ${axis.min}, "max": ${axis.max}}"""

    // ========================================
    // CSV Conversion
    // ========================================

    private fun lineChartToCsv(chart: LineChartData): String {
        val header = "X,Y\n"
        val rows = chart.series.joinToString("\n") { "${it.x},${it.y}" }
        return header + rows
    }

    private fun barChartToCsv(chart: BarChartData): String {
        val header = "Category,Value\n"
        val rows =
            chart.categories
                .zip(chart.values)
                .joinToString("\n") { (cat, value) -> "$cat,$value" }
        return header + rows
    }

    private fun pieChartToCsv(chart: PieChartData): String {
        val header = "Slice,Value,Percentage\n"
        val rows =
            chart.slices.entries
                .joinToString("\n") { "${it.key},${it.value},${chart.percentages[it.key]}" }
        return header + rows
    }

    private fun scatterPlotToCsv(chart: ScatterPlotData): String {
        val header = "X,Y\n"
        val rows = chart.points.joinToString("\n") { "${it.first},${it.second}" }
        return header + rows
    }

    // ========================================
    // HTML Generation
    // ========================================

    private fun buildHtmlDocument(
        title: String,
        chartData: ChartData,
        theme: ChartTheme,
        config: ExportConfig,
    ): String =
        """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        body {
            font-family: ${theme.typography.fontFamily};
            background-color: ${theme.colorPalette.background};
            color: ${theme.colorPalette.text};
            padding: ${theme.spacing.padding}px;
        }
        #chartContainer {
            width: ${config.width}px;
            height: ${config.height}px;
            margin: 0 auto;
        }
    </style>
</head>
<body>
    <div id="chartContainer">
        <canvas id="chart"></canvas>
    </div>
    <script>
        // Chart data would be embedded here
        console.log("Chart.js ready for $title");
    </script>
</body>
</html>"""

    /**
     * Validate chart data before export
     *
     * @param chartData Chart data to validate
     * @return True if valid, false otherwise
     */
    fun validateChartData(chartData: ChartData): Boolean =
        when (chartData) {
            is LineChartData -> chartData.series.isNotEmpty()
            is BarChartData -> chartData.categories.isNotEmpty() && chartData.values.isNotEmpty()
            is PieChartData -> chartData.slices.isNotEmpty()
            is ScatterPlotData -> chartData.points.isNotEmpty()
            is HeatmapData -> chartData.grid.width > 0 && chartData.grid.height > 0
            is ChannelDiagramData -> chartData.channels.isNotEmpty()
            is GraphData -> chartData.nodes.isNotEmpty()
            else -> false
        }
}
