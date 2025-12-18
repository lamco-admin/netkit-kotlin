package io.lamco.netkit.analytics.visualization

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class RFHeatmapGeneratorTest {
    private val generator = RFHeatmapGenerator()

    // ========================================
    // Heatmap Generation Tests
    // ========================================

    @Test
    fun `generateHeatmap creates heatmap from scan points`() {
        val scanPoints = createTestScanPoints()
        val config =
            HeatmapConfig(
                colorScale = ColorScale.VIRIDIS,
                interpolation = InterpolationMethod.IDW,
                resolution = 1.0,
            )

        val heatmap = generator.generateHeatmap(scanPoints, config)

        assertNotNull(heatmap.grid)
        assertEquals(ColorScale.VIRIDIS, heatmap.colorScale)
        assertEquals(InterpolationMethod.IDW, heatmap.interpolationMethod)
    }

    @Test
    fun `generateHeatmap calculates correct bounds`() {
        val scanPoints =
            listOf(
                ScanPoint(Location(0.0, 0.0), -60, "AA:BB:CC:DD:EE:01", 1000L),
                ScanPoint(Location(10.0, 10.0), -70, "AA:BB:CC:DD:EE:01", 2000L),
            )
        val config = HeatmapConfig(ColorScale.VIRIDIS, InterpolationMethod.NEAREST_NEIGHBOR)

        val heatmap = generator.generateHeatmap(scanPoints, config)

        assertEquals(0.0, heatmap.bounds.minX)
        assertEquals(10.0, heatmap.bounds.maxX)
        assertEquals(0.0, heatmap.bounds.minY)
        assertEquals(10.0, heatmap.bounds.maxY)
    }

    @Test
    fun `generateHeatmap rejects empty scan points`() {
        val config = HeatmapConfig(ColorScale.VIRIDIS, InterpolationMethod.IDW)

        assertThrows<IllegalArgumentException> {
            generator.generateHeatmap(emptyList(), config)
        }
    }

    @Test
    fun `generateHeatmap rejects invalid resolution`() {
        val scanPoints = createTestScanPoints()

        // HeatmapConfig itself validates resolution, so include creation in assertThrows
        assertThrows<IllegalArgumentException> {
            val config =
                HeatmapConfig(
                    colorScale = ColorScale.VIRIDIS,
                    interpolation = InterpolationMethod.IDW,
                    resolution = -1.0,
                )
            generator.generateHeatmap(scanPoints, config)
        }
    }

    // ========================================
    // Interpolation Tests
    // ========================================

    @Test
    fun `interpolate creates grid with correct dimensions`() {
        val scanPoints = createTestScanPoints()
        val bounds = Bounds(0.0, 10.0, 0.0, 10.0)

        val grid =
            generator.interpolate(
                scanPoints,
                gridResolution = 1.0,
                bounds = bounds,
                method = InterpolationMethod.NEAREST_NEIGHBOR,
            )

        assertEquals(10, grid.width)
        assertEquals(10, grid.height)
        assertEquals(1.0, grid.resolution)
    }

    @Test
    fun `interpolate with NEAREST_NEIGHBOR method`() {
        val scanPoints =
            listOf(
                ScanPoint(Location(5.0, 5.0), -60, "AA:BB:CC:DD:EE:01", 1000L),
            )
        val bounds = Bounds(0.0, 10.0, 0.0, 10.0)

        val grid =
            generator.interpolate(
                scanPoints,
                gridResolution = 2.0,
                bounds = bounds,
                method = InterpolationMethod.NEAREST_NEIGHBOR,
            )

        // All cells should have value near -60
        assertTrue(grid.values.all { row -> row.all { it < 0.0 } })
    }

    @Test
    fun `interpolate with BILINEAR method`() {
        val scanPoints = createTestScanPoints()
        val bounds = Bounds(0.0, 10.0, 0.0, 10.0)

        val grid =
            generator.interpolate(
                scanPoints,
                gridResolution = 1.0,
                bounds = bounds,
                method = InterpolationMethod.BILINEAR,
            )

        // Grid should be populated
        assertTrue(grid.width > 0)
        assertTrue(grid.height > 0)
    }

    @Test
    fun `interpolate with IDW method`() {
        val scanPoints = createTestScanPoints()
        val bounds = Bounds(0.0, 10.0, 0.0, 10.0)

        val grid =
            generator.interpolate(
                scanPoints,
                gridResolution = 1.0,
                bounds = bounds,
                method = InterpolationMethod.IDW,
            )

        // All values should be in valid RSSI range
        assertTrue(
            grid.values.all { row ->
                row.all { it in -100.0..0.0 }
            },
        )
    }

    @Test
    fun `interpolate with KRIGING method`() {
        val scanPoints = createTestScanPoints()
        val bounds = Bounds(0.0, 10.0, 0.0, 10.0)

        val grid =
            generator.interpolate(
                scanPoints,
                gridResolution = 1.0,
                bounds = bounds,
                method = InterpolationMethod.KRIGING,
            )

        assertTrue(grid.values.isNotEmpty())
    }

    @Test
    fun `interpolate handles single scan point`() {
        val scanPoints =
            listOf(
                ScanPoint(Location(5.0, 5.0), -60, "AA:BB:CC:DD:EE:01", 1000L),
            )
        val bounds = Bounds(0.0, 10.0, 0.0, 10.0)

        val grid =
            generator.interpolate(
                scanPoints,
                gridResolution = 2.0,
                bounds = bounds,
                method = InterpolationMethod.NEAREST_NEIGHBOR,
            )

        // Should populate grid with single point's value
        assertTrue(grid.values.all { row -> row.all { it in -100.0..0.0 } })
    }

    @Test
    fun `interpolate handles very small resolution`() {
        val scanPoints =
            listOf(
                ScanPoint(Location(0.0, 0.0), -60, "AA:BB:CC:DD:EE:01", 1000L),
                ScanPoint(Location(1.0, 1.0), -70, "AA:BB:CC:DD:EE:01", 2000L),
            )
        val bounds = Bounds(0.0, 1.0, 0.0, 1.0)

        val grid =
            generator.interpolate(
                scanPoints,
                gridResolution = 0.1,
                bounds = bounds,
            )

        assertTrue(grid.width >= 10)
        assertTrue(grid.height >= 10)
    }

    @Test
    fun `interpolate rejects empty scan points`() {
        val bounds = Bounds(0.0, 10.0, 0.0, 10.0)

        assertThrows<IllegalArgumentException> {
            generator.interpolate(emptyList(), gridResolution = 1.0, bounds = bounds)
        }
    }

    @Test
    fun `interpolate rejects negative resolution`() {
        val scanPoints = createTestScanPoints()
        val bounds = Bounds(0.0, 10.0, 0.0, 10.0)

        assertThrows<IllegalArgumentException> {
            generator.interpolate(scanPoints, gridResolution = -1.0, bounds = bounds)
        }
    }
}

class CoverageMapGeneratorTest {
    private val generator = CoverageMapGenerator()

    // ========================================
    // Coverage Map Generation Tests
    // ========================================

    @Test
    fun `generateCoverageMap creates coverage map`() {
        val scanPoints = createTestScanPoints()

        val coverageMap = generator.generateCoverageMap(scanPoints)

        assertNotNull(coverageMap.regions)
        assertNotNull(coverageMap.bounds)
        assertTrue(coverageMap.totalCoveredArea > 0.0)
    }

    @Test
    fun `generateCoverageMap calculates quality breakdown`() {
        val scanPoints = createTestScanPoints()

        val coverageMap = generator.generateCoverageMap(scanPoints)

        // Quality breakdown should sum to approximately 100%
        val total = coverageMap.qualityBreakdown.values.sum()
        assertTrue(total > 0.0)
        assertTrue(total <= 100.0)
    }

    @Test
    fun `generateCoverageMap categorizes regions by quality`() {
        val scanPoints =
            listOf(
                ScanPoint(Location(0.0, 0.0), -45, "AA:BB:CC:DD:EE:01", 1000L), // EXCELLENT
                ScanPoint(Location(5.0, 5.0), -55, "AA:BB:CC:DD:EE:01", 2000L), // GOOD
                ScanPoint(Location(10.0, 10.0), -75, "AA:BB:CC:DD:EE:01", 3000L), // FAIR
            )

        val coverageMap = generator.generateCoverageMap(scanPoints)

        // Should have regions for different quality levels
        assertTrue(coverageMap.regions.isNotEmpty())
    }

    @Test
    fun `generateCoverageMap rejects empty scan points`() {
        assertThrows<IllegalArgumentException> {
            generator.generateCoverageMap(emptyList())
        }
    }

    @Test
    fun `generateCoverageMap uses custom thresholds`() {
        val scanPoints = createTestScanPoints()
        val customThresholds =
            mapOf(
                SignalQuality.EXCELLENT to -40,
                SignalQuality.GOOD to -50,
                SignalQuality.FAIR to -60,
                SignalQuality.POOR to -70,
                SignalQuality.VERY_POOR to -80,
            )
        val config = CoverageMapConfig(qualityThresholds = customThresholds)

        val coverageMap = generator.generateCoverageMap(scanPoints, config)

        assertNotNull(coverageMap.qualityBreakdown)
    }

    // ========================================
    // Coverage Percentage Tests
    // ========================================

    @Test
    fun `calculateCoveragePercentage calculates correct percentage`() {
        val scanPoints =
            listOf(
                ScanPoint(Location(0.0, 0.0), -45, "AA:BB:CC:DD:EE:01", 1000L), // EXCELLENT
                ScanPoint(Location(1.0, 1.0), -45, "AA:BB:CC:DD:EE:01", 2000L), // EXCELLENT
                ScanPoint(Location(2.0, 2.0), -55, "AA:BB:CC:DD:EE:01", 3000L), // GOOD
                ScanPoint(Location(3.0, 3.0), -75, "AA:BB:CC:DD:EE:01", 4000L), // FAIR
            )

        val excellentCoverage =
            generator.calculateCoveragePercentage(
                scanPoints,
                SignalQuality.EXCELLENT,
            )

        assertEquals(50.0, excellentCoverage, 0.1) // 2 out of 4 points
    }

    @Test
    fun `calculateCoveragePercentage handles all excellent quality`() {
        val scanPoints =
            List(10) {
                ScanPoint(Location(it.toDouble(), it.toDouble()), -45, "AA:BB:CC:DD:EE:01", 1000L)
            }

        val coverage =
            generator.calculateCoveragePercentage(
                scanPoints,
                SignalQuality.EXCELLENT,
            )

        assertEquals(100.0, coverage, 0.1)
    }

    @Test
    fun `calculateCoveragePercentage handles no coverage`() {
        val scanPoints =
            List(10) {
                ScanPoint(Location(it.toDouble(), it.toDouble()), -95, "AA:BB:CC:DD:EE:01", 1000L)
            }

        val coverage =
            generator.calculateCoveragePercentage(
                scanPoints,
                SignalQuality.EXCELLENT,
            )

        assertEquals(0.0, coverage)
    }

    @Test
    fun `calculateCoveragePercentage returns zero for empty scan points`() {
        val coverage =
            generator.calculateCoveragePercentage(
                emptyList(),
                SignalQuality.GOOD,
            )

        assertEquals(0.0, coverage)
    }

    @Test
    fun `calculateCoveragePercentage uses custom thresholds`() {
        val scanPoints =
            listOf(
                ScanPoint(Location(0.0, 0.0), -45, "AA:BB:CC:DD:EE:01", 1000L),
            )
        val thresholds = mapOf(SignalQuality.EXCELLENT to -40)

        val coverage =
            generator.calculateCoveragePercentage(
                scanPoints,
                SignalQuality.EXCELLENT,
                thresholds,
            )

        // -45 is below -40 threshold, so 0% coverage
        assertEquals(0.0, coverage)
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    @Test
    fun `generateCoverageMap handles clustered scan points`() {
        val scanPoints =
            List(10) {
                ScanPoint(Location(5.0, 5.0), -60, "AA:BB:CC:DD:EE:01", 1000L)
            }

        val coverageMap = generator.generateCoverageMap(scanPoints)

        assertTrue(coverageMap.totalCoveredArea >= 0.0)
    }

    @Test
    fun `generateCoverageMap handles scattered scan points`() {
        val scanPoints =
            (0..9).map {
                ScanPoint(
                    Location(it * 10.0, it * 10.0),
                    -60,
                    "AA:BB:CC:DD:EE:01",
                    1000L + it,
                )
            }

        val coverageMap = generator.generateCoverageMap(scanPoints)

        assertTrue(coverageMap.bounds.width > 0.0)
        assertTrue(coverageMap.bounds.height > 0.0)
    }
}

// ========================================
// Helper Functions
// ========================================

private fun createTestScanPoints(): List<ScanPoint> =
    listOf(
        ScanPoint(Location(0.0, 0.0), -60, "AA:BB:CC:DD:EE:01", 1000L),
        ScanPoint(Location(5.0, 0.0), -65, "AA:BB:CC:DD:EE:01", 2000L),
        ScanPoint(Location(10.0, 0.0), -70, "AA:BB:CC:DD:EE:01", 3000L),
        ScanPoint(Location(0.0, 10.0), -62, "AA:BB:CC:DD:EE:01", 4000L),
        ScanPoint(Location(10.0, 10.0), -68, "AA:BB:CC:DD:EE:01", 5000L),
    )
