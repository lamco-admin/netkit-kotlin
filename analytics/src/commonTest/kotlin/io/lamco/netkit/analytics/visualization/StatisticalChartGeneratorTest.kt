package io.lamco.netkit.analytics.visualization

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class StatisticalChartGeneratorTest {
    private val generator = StatisticalChartGenerator()

    // ========================================
    // Bar Chart Tests
    // ========================================

    @Test
    fun `createBarChart generates chart from categories and values`() {
        val categories = listOf("Network A", "Network B", "Network C")
        val values = listOf(85.0, 92.0, 78.0)
        val config = BarChartConfig(title = "Network Performance")

        val chart = generator.createBarChart(categories, values, config)

        assertEquals("Network Performance", chart.title)
        assertEquals(3, chart.categories.size)
        assertEquals(3, chart.values.size)
        assertEquals("Network A", chart.categories[0])
        assertEquals(85.0, chart.values[0])
    }

    @Test
    fun `createBarChart assigns colors to bars`() {
        val categories = listOf("A", "B", "C")
        val values = listOf(10.0, 20.0, 30.0)
        val config = BarChartConfig(title = "Test")

        val chart = generator.createBarChart(categories, values, config)

        assertNotNull(chart.colors)
        assertEquals(3, chart.colors!!.size)
        assertTrue(chart.colors!!.all { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) })
    }

    @Test
    fun `createBarChart respects orientation`() {
        val categories = listOf("A", "B")
        val values = listOf(10.0, 20.0)
        val config =
            BarChartConfig(
                title = "Test",
                orientation = Orientation.HORIZONTAL,
            )

        val chart = generator.createBarChart(categories, values, config)

        assertEquals(Orientation.HORIZONTAL, chart.orientation)
    }

    @Test
    fun `createBarChart rejects mismatched categories and values`() {
        val categories = listOf("A", "B", "C")
        val values = listOf(10.0, 20.0)
        val config = BarChartConfig(title = "Test")

        assertThrows<IllegalArgumentException> {
            generator.createBarChart(categories, values, config)
        }
    }

    @Test
    fun `createBarChart rejects empty categories`() {
        val config = BarChartConfig(title = "Test")

        assertThrows<IllegalArgumentException> {
            generator.createBarChart(emptyList(), emptyList(), config)
        }
    }

    @Test
    fun `createBarChart handles single category`() {
        val categories = listOf("Only One")
        val values = listOf(42.0)
        val config = BarChartConfig(title = "Test")

        val chart = generator.createBarChart(categories, values, config)

        assertEquals(1, chart.categories.size)
        assertEquals(1, chart.values.size)
    }

    @Test
    fun `createBarChart handles negative values`() {
        val categories = listOf("A", "B", "C")
        val values = listOf(-10.0, 20.0, -30.0)
        val config = BarChartConfig(title = "Test")

        val chart = generator.createBarChart(categories, values, config)

        assertTrue(chart.values.contains(-10.0))
        assertTrue(chart.values.contains(-30.0))
    }

    // ========================================
    // Pie Chart Tests
    // ========================================

    @Test
    fun `createPieChart generates chart from slices`() {
        val slices =
            mapOf(
                "Slice A" to 30.0,
                "Slice B" to 50.0,
                "Slice C" to 20.0,
            )
        val config = PieChartConfig(title = "Distribution")

        val chart = generator.createPieChart(slices, config)

        assertEquals("Distribution", chart.title)
        assertEquals(3, chart.slices.size)
        assertEquals(100.0, chart.total)
    }

    @Test
    fun `createPieChart calculates percentages correctly`() {
        val slices =
            mapOf(
                "A" to 25.0,
                "B" to 50.0,
                "C" to 25.0,
            )
        val config = PieChartConfig(title = "Test")

        val chart = generator.createPieChart(slices, config)

        assertEquals(25.0, chart.percentages["A"])
        assertEquals(50.0, chart.percentages["B"])
        assertEquals(25.0, chart.percentages["C"])
    }

    @Test
    fun `createPieChart handles zero total`() {
        val slices =
            mapOf(
                "A" to 0.0,
                "B" to 0.0,
            )
        val config = PieChartConfig(title = "Test")

        val chart = generator.createPieChart(slices, config)

        assertEquals(0.0, chart.total)
        // Percentages should be 0 when total is 0
        assertEquals(0.0, chart.percentages["A"])
        assertEquals(0.0, chart.percentages["B"])
    }

    @Test
    fun `createPieChart rejects negative values`() {
        val slices = mapOf("A" to -10.0)
        val config = PieChartConfig(title = "Test")

        assertThrows<IllegalArgumentException> {
            generator.createPieChart(slices, config)
        }
    }

    @Test
    fun `createPieChart rejects empty slices`() {
        val config = PieChartConfig(title = "Test")

        assertThrows<IllegalArgumentException> {
            generator.createPieChart(emptyMap(), config)
        }
    }

    @Test
    fun `createPieChart assigns colors when showLegend is true`() {
        val slices = mapOf("A" to 30.0, "B" to 70.0)
        val config = PieChartConfig(title = "Test", showLegend = true)

        val chart = generator.createPieChart(slices, config)

        assertNotNull(chart.colors)
        assertEquals(2, chart.colors!!.size)
    }

    @Test
    fun `createPieChart handles single slice`() {
        val slices = mapOf("Only One" to 100.0)
        val config = PieChartConfig(title = "Test")

        val chart = generator.createPieChart(slices, config)

        assertEquals(1, chart.slices.size)
        assertEquals(100.0, chart.percentages["Only One"])
    }

    // ========================================
    // Scatter Plot Tests
    // ========================================

    @Test
    fun `createScatterPlot generates chart from points`() {
        val points =
            listOf(
                Pair(-60.0, 85.0),
                Pair(-55.0, 92.0),
                Pair(-70.0, 75.0),
            )
        val config =
            ScatterPlotConfig(
                title = "RSSI vs Throughput",
                xAxisLabel = "RSSI (dBm)",
                yAxisLabel = "Throughput (Mbps)",
            )

        val chart = generator.createScatterPlot(points, config)

        assertEquals("RSSI vs Throughput", chart.title)
        assertEquals(3, chart.points.size)
        assertEquals("RSSI (dBm)", chart.xAxis.label)
        assertEquals("Throughput (Mbps)", chart.yAxis.label)
    }

    @Test
    fun `createScatterPlot calculates axis ranges`() {
        val points =
            listOf(
                Pair(1.0, 10.0),
                Pair(5.0, 50.0),
                Pair(10.0, 100.0),
            )
        val config = ScatterPlotConfig("Test", "X", "Y")

        val chart = generator.createScatterPlot(points, config)

        assertEquals(1.0, chart.xAxis.min)
        assertEquals(10.0, chart.xAxis.max)
        assertEquals(10.0, chart.yAxis.min)
        assertEquals(100.0, chart.yAxis.max)
    }

    @Test
    fun `createScatterPlot handles single point`() {
        val points = listOf(Pair(5.0, 10.0))
        val config = ScatterPlotConfig("Test", "X", "Y")

        val chart = generator.createScatterPlot(points, config)

        assertEquals(1, chart.points.size)
        assertEquals(5.0, chart.xAxis.min)
        assertEquals(5.0, chart.xAxis.max)
    }

    @Test
    fun `createScatterPlot rejects empty points`() {
        val config = ScatterPlotConfig("Test", "X", "Y")

        assertThrows<IllegalArgumentException> {
            generator.createScatterPlot(emptyList(), config)
        }
    }

    @Test
    fun `createScatterPlot handles negative coordinates`() {
        val points =
            listOf(
                Pair(-10.0, -20.0),
                Pair(-5.0, -10.0),
            )
        val config = ScatterPlotConfig("Test", "X", "Y")

        val chart = generator.createScatterPlot(points, config)

        assertTrue(chart.xAxis.min!! < 0.0)
        assertTrue(chart.yAxis.min!! < 0.0)
    }

    // ========================================
    // Box Plot Tests
    // ========================================

    @Test
    fun `createBoxPlot generates chart from distributions`() {
        val distributions =
            mapOf(
                "Network A" to listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0),
                "Network B" to listOf(15.0, 25.0, 35.0, 45.0, 55.0, 65.0, 75.0, 85.0),
            )
        val config =
            BoxPlotConfig(
                title = "Performance Distribution",
                yAxisLabel = "Score",
            )

        val chart = generator.createBoxPlot(distributions, config)

        assertEquals("Performance Distribution", chart.title)
        assertEquals(2, chart.distributions.size)
        assertTrue(chart.distributions.containsKey("Network A"))
        assertTrue(chart.distributions.containsKey("Network B"))
    }

    @Test
    fun `createBoxPlot calculates quartiles correctly`() {
        val distributions =
            mapOf(
                "Test" to listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0),
            )
        val config = BoxPlotConfig("Test", "Value")

        val chart = generator.createBoxPlot(distributions, config)

        val stats = chart.distributions["Test"]!!
        assertTrue(stats.q1 > stats.min)
        assertTrue(stats.median > stats.q1)
        assertTrue(stats.q3 > stats.median)
        assertTrue(stats.max > stats.q3)
    }

    @Test
    fun `createBoxPlot identifies outliers`() {
        val distributions =
            mapOf(
                "Test" to listOf(10.0, 12.0, 14.0, 15.0, 16.0, 18.0, 100.0), // 100 is outlier
            )
        val config = BoxPlotConfig("Test", "Value")

        val chart = generator.createBoxPlot(distributions, config)

        val stats = chart.distributions["Test"]!!
        assertTrue(stats.outliers.contains(100.0))
    }

    @Test
    fun `createBoxPlot rejects distribution with too few values`() {
        val distributions =
            mapOf(
                "Test" to listOf(1.0, 2.0, 3.0), // Only 3 values, need 5+
            )
        val config = BoxPlotConfig("Test", "Value")

        assertThrows<IllegalArgumentException> {
            generator.createBoxPlot(distributions, config)
        }
    }

    @Test
    fun `createBoxPlot rejects empty distributions`() {
        val config = BoxPlotConfig("Test", "Value")

        assertThrows<IllegalArgumentException> {
            generator.createBoxPlot(emptyMap(), config)
        }
    }

    @Test
    fun `createBoxPlot handles distributions with no outliers`() {
        val distributions =
            mapOf(
                "Test" to listOf(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0),
            )
        val config = BoxPlotConfig("Test", "Value")

        val chart = generator.createBoxPlot(distributions, config)

        val stats = chart.distributions["Test"]!!
        assertTrue(stats.outliers.isEmpty())
    }

    @Test
    fun `createBoxPlot calculates overall y-axis range`() {
        val distributions =
            mapOf(
                "A" to listOf(10.0, 20.0, 30.0, 40.0, 50.0),
                "B" to listOf(60.0, 70.0, 80.0, 90.0, 100.0),
            )
        val config = BoxPlotConfig("Test", "Value")

        val chart = generator.createBoxPlot(distributions, config)

        assertEquals(10.0, chart.yAxis.min)
        assertEquals(100.0, chart.yAxis.max)
    }

    // ========================================
    // Grouped Bar Chart Tests
    // ========================================

    @Test
    fun `createGroupedBarChart generates grouped bars`() {
        val categories = listOf("Network A", "Network B")
        val groups = listOf("RSSI", "SNR", "Throughput")
        val values =
            listOf(
                listOf(85.0, 30.0, 100.0),
                listOf(92.0, 35.0, 120.0),
            )
        val config = BarChartConfig(title = "Grouped Metrics")

        val chart = generator.createGroupedBarChart(categories, groups, values, config)

        assertEquals("Grouped Metrics", chart.title)
        assertEquals(6, chart.categories.size) // 2 categories × 3 groups
        assertEquals(6, chart.values.size)
    }

    @Test
    fun `createGroupedBarChart creates correct category labels`() {
        val categories = listOf("A")
        val groups = listOf("G1", "G2")
        val values = listOf(listOf(10.0, 20.0))
        val config = BarChartConfig(title = "Test")

        val chart = generator.createGroupedBarChart(categories, groups, values, config)

        assertTrue(chart.categories[0].contains("A"))
        assertTrue(chart.categories[0].contains("G1"))
        assertTrue(chart.categories[1].contains("G2"))
    }

    @Test
    fun `createGroupedBarChart rejects empty categories`() {
        val groups = listOf("G1")
        val values = emptyList<List<Double>>()
        val config = BarChartConfig(title = "Test")

        assertThrows<IllegalArgumentException> {
            generator.createGroupedBarChart(emptyList(), groups, values, config)
        }
    }

    @Test
    fun `createGroupedBarChart rejects empty groups`() {
        val categories = listOf("A")
        val values = listOf(emptyList<Double>())
        val config = BarChartConfig(title = "Test")

        assertThrows<IllegalArgumentException> {
            generator.createGroupedBarChart(categories, emptyList(), values, config)
        }
    }

    @Test
    fun `createGroupedBarChart rejects mismatched values`() {
        val categories = listOf("A", "B")
        val groups = listOf("G1", "G2")
        val values =
            listOf(
                listOf(10.0, 20.0),
                listOf(30.0), // Wrong size
            )
        val config = BarChartConfig(title = "Test")

        assertThrows<IllegalArgumentException> {
            generator.createGroupedBarChart(categories, groups, values, config)
        }
    }

    // ========================================
    // Color Scheme Tests
    // ========================================

    @Test
    fun `createBarChart uses DEFAULT color scheme`() {
        val categories = listOf("A", "B", "C")
        val values = listOf(10.0, 20.0, 30.0)
        val config = BarChartConfig(title = "Test", colorScheme = ColorScheme.DEFAULT)

        val chart = generator.createBarChart(categories, values, config)

        assertNotNull(chart.colors)
        assertEquals(3, chart.colors!!.size)
    }

    @Test
    fun `createBarChart uses MATERIAL color scheme`() {
        val categories = listOf("A", "B")
        val values = listOf(10.0, 20.0)
        val config = BarChartConfig(title = "Test", colorScheme = ColorScheme.MATERIAL)

        val chart = generator.createBarChart(categories, values, config)

        assertNotNull(chart.colors)
        assertTrue(chart.colors!!.all { it.startsWith("#") })
    }

    @Test
    fun `createBarChart cycles colors for many bars`() {
        val categories = (1..20).map { "Category $it" }
        val values = (1..20).map { it.toDouble() }
        val config = BarChartConfig(title = "Test")

        val chart = generator.createBarChart(categories, values, config)

        assertEquals(20, chart.colors!!.size)
        // Colors should cycle
        assertTrue(chart.colors!!.distinct().size < 20)
    }
}
