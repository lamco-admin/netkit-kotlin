package io.lamco.netkit.analytics.visualization

import kotlin.math.*

/**
 * RF signal heatmap generator
 *
 * Generates signal strength heatmaps from WiFi scan measurements using
 * various interpolation methods to estimate signal strength between
 * measurement points.
 *
 * Supports multiple interpolation methods:
 * - Nearest Neighbor (fast, blocky)
 * - Bilinear (smooth, fast)
 * - IDW - Inverse Distance Weighting (balanced)
 * - Kriging (optimal but slow)
 *
 * ## Usage Example
 * ```kotlin
 * val generator = RFHeatmapGenerator()
 *
 * val heatmap = generator.generateHeatmap(
 *     scanPoints = listOf(...),
 *     config = HeatmapConfig(
 *         colorScale = ColorScale.VIRIDIS,
 *         interpolation = InterpolationMethod.IDW,
 *         resolution = 0.5 // 0.5 meter cells
 *     )
 * )
 * ```
 */
class RFHeatmapGenerator {
    /**
     * Generate signal strength heatmap from scan points
     *
     * @param scanPoints List of WiFi measurements with locations
     * @param config Heatmap configuration
     * @return Heatmap data with interpolated signal values
     * @throws IllegalArgumentException if scanPoints is empty or resolution is invalid
     */
    fun generateHeatmap(
        scanPoints: List<ScanPoint>,
        config: HeatmapConfig,
    ): HeatmapData {
        require(scanPoints.isNotEmpty()) { "Scan points cannot be empty" }
        require(config.resolution > 0.0) { "Resolution must be positive" }

        // Calculate bounds from scan points
        val bounds = Bounds.fromLocations(scanPoints.map { it.location })

        // Generate interpolated grid
        val grid = interpolate(scanPoints, config.resolution, bounds, config.interpolation)

        return HeatmapData(
            grid = grid,
            bounds = bounds,
            interpolationMethod = config.interpolation,
            colorScale = config.colorScale,
        )
    }

    /**
     * Interpolate signal strength across grid
     *
     * @param scanPoints Known measurement points
     * @param gridResolution Grid cell size (meters)
     * @param bounds Spatial bounds for the grid
     * @param method Interpolation method to use
     * @return 2D grid of interpolated RSSI values
     */
    fun interpolate(
        scanPoints: List<ScanPoint>,
        gridResolution: Double,
        bounds: Bounds = Bounds.fromLocations(scanPoints.map { it.location }),
        method: InterpolationMethod = InterpolationMethod.IDW,
    ): Grid2D<Double> {
        require(scanPoints.isNotEmpty()) { "Scan points cannot be empty" }
        require(gridResolution > 0.0) { "Grid resolution must be positive" }

        // Ensure minimum dimensions of 1 for degenerate cases (all points at same location)
        val width = maxOf(1, ceil(bounds.width / gridResolution).toInt())
        val height = maxOf(1, ceil(bounds.height / gridResolution).toInt())

        val values =
            (0 until height).map { y ->
                (0 until width).map { x ->
                    val location =
                        Location(
                            x = bounds.minX + x * gridResolution,
                            y = bounds.minY + y * gridResolution,
                        )
                    estimateSignalStrength(location, scanPoints, method)
                }
            }

        return Grid2D(
            width = width,
            height = height,
            resolution = gridResolution,
            values = values,
        )
    }

    // ========================================
    // Private Interpolation Methods
    // ========================================

    /**
     * Estimate signal strength at a location
     */
    private fun estimateSignalStrength(
        location: Location,
        scanPoints: List<ScanPoint>,
        method: InterpolationMethod,
    ): Double =
        when (method) {
            InterpolationMethod.NEAREST_NEIGHBOR -> nearestNeighbor(location, scanPoints)
            InterpolationMethod.BILINEAR -> bilinearInterpolation(location, scanPoints)
            InterpolationMethod.IDW -> inverseDistanceWeighting(location, scanPoints)
            InterpolationMethod.KRIGING -> krigingInterpolation(location, scanPoints)
        }

    /**
     * Nearest neighbor interpolation (simplest)
     */
    private fun nearestNeighbor(
        location: Location,
        scanPoints: List<ScanPoint>,
    ): Double =
        scanPoints.minByOrNull { it.location.distanceTo(location) }?.rssi?.toDouble()
            ?: -100.0

    /**
     * Bilinear interpolation
     */
    private fun bilinearInterpolation(
        location: Location,
        scanPoints: List<ScanPoint>,
    ): Double {
        // Find 4 nearest points (simplified implementation)
        val nearest =
            scanPoints
                .sortedBy { it.location.distanceTo(location) }
                .take(4)

        if (nearest.isEmpty()) return -100.0

        // Weight by inverse distance
        val totalWeight = nearest.sumOf { 1.0 / (it.location.distanceTo(location) + 0.001) }
        val weightedSum =
            nearest.sumOf {
                it.rssi * (1.0 / (it.location.distanceTo(location) + 0.001))
            }

        return weightedSum / totalWeight
    }

    /**
     * Inverse Distance Weighting (IDW) interpolation
     *
     * Most commonly used for RF heatmaps. Provides good balance of
     * accuracy and performance.
     */
    private fun inverseDistanceWeighting(
        location: Location,
        scanPoints: List<ScanPoint>,
        power: Double = 2.0,
        maxDistance: Double = 50.0,
    ): Double {
        // Filter to points within max distance
        val nearby =
            scanPoints.filter {
                it.location.distanceTo(location) <= maxDistance
            }

        if (nearby.isEmpty()) return -100.0

        // Check if location coincides with a measurement
        nearby.find { it.location.distanceTo(location) < 0.01 }?.let {
            return it.rssi.toDouble()
        }

        // Calculate IDW
        var weightedSum = 0.0
        var totalWeight = 0.0

        nearby.forEach { point ->
            val distance = location.distanceTo(point.location)
            val weight = 1.0 / distance.pow(power)
            weightedSum += point.rssi * weight
            totalWeight += weight
        }

        return if (totalWeight > 0.0) weightedSum / totalWeight else -100.0
    }

    /**
     * Kriging interpolation (optimal but computationally expensive)
     *
     * Simplified implementation. Full kriging requires semivariogram modeling.
     */
    private fun krigingInterpolation(
        location: Location,
        scanPoints: List<ScanPoint>,
    ): Double {
        // Simplified kriging using IDW as approximation
        // Full kriging implementation would require semivariogram and matrix operations
        return inverseDistanceWeighting(location, scanPoints, power = 2.0)
    }
}

/**
 * Coverage map generator
 *
 * Generates coverage quality maps showing areas categorized by signal strength.
 *
 * ## Usage Example
 * ```kotlin
 * val generator = CoverageMapGenerator()
 *
 * val coverageMap = generator.generateCoverageMap(
 *     scanPoints = scanPoints,
 *     config = CoverageMapConfig()
 * )
 *
 * println("Excellent coverage: ${coverageMap.qualityBreakdown[SignalQuality.EXCELLENT]}%")
 * ```
 */
class CoverageMapGenerator {
    /**
     * Generate coverage map from scan points
     *
     * Creates regions categorized by signal quality levels.
     *
     * @param scanPoints WiFi scan measurements
     * @param config Coverage map configuration
     * @return Coverage map with quality regions
     */
    fun generateCoverageMap(
        scanPoints: List<ScanPoint>,
        config: CoverageMapConfig = CoverageMapConfig(),
    ): CoverageMapData {
        require(scanPoints.isNotEmpty()) { "Scan points cannot be empty" }

        // Generate heatmap first
        val heatmapGen = RFHeatmapGenerator()
        val heatmap =
            heatmapGen.generateHeatmap(
                scanPoints,
                HeatmapConfig(
                    colorScale = ColorScale.RED_GREEN,
                    interpolation = InterpolationMethod.IDW,
                    resolution = 1.0,
                ),
            )

        // Categorize grid cells by signal quality
        val regionsByQuality = categorizeByQuality(heatmap, config.qualityThresholds)

        // Calculate coverage statistics
        val totalCells = heatmap.grid.cellCount.toDouble()
        val qualityBreakdown =
            regionsByQuality.mapValues { (_, regions) ->
                (regions.sumOf { it.area } / totalCells) * 100.0
            }

        val totalCoveredArea = heatmap.bounds.area

        return CoverageMapData(
            regions = regionsByQuality,
            bounds = heatmap.bounds,
            totalCoveredArea = totalCoveredArea,
            qualityBreakdown = qualityBreakdown,
        )
    }

    /**
     * Calculate coverage percentage for quality level
     *
     * @param scanPoints Scan measurements
     * @param quality Target quality level
     * @param thresholds RSSI thresholds
     * @return Percentage of area meeting quality level
     */
    fun calculateCoveragePercentage(
        scanPoints: List<ScanPoint>,
        quality: SignalQuality,
        thresholds: Map<SignalQuality, Int> = CoverageMapConfig.defaultQualityThresholds,
    ): Double {
        if (scanPoints.isEmpty()) return 0.0

        val threshold = thresholds[quality] ?: return 0.0
        val meetingThreshold = scanPoints.count { it.rssi >= threshold }

        return (meetingThreshold.toDouble() / scanPoints.size) * 100.0
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Categorize heatmap cells by signal quality
     */
    private fun categorizeByQuality(
        heatmap: HeatmapData,
        thresholds: Map<SignalQuality, Int>,
    ): Map<SignalQuality, List<CoverageRegion>> {
        val regions = mutableMapOf<SignalQuality, MutableList<CoverageRegion>>()

        // Scan grid and create regions
        for (y in 0 until heatmap.grid.height) {
            for (x in 0 until heatmap.grid.width) {
                val rssi = heatmap.grid[x, y].toInt()
                val quality = determineQuality(rssi, thresholds)

                // Create simple rectangular region for this cell
                val cellRegion =
                    CoverageRegion(
                        vertices =
                            listOf(
                                Location(x.toDouble(), y.toDouble()),
                                Location((x + 1).toDouble(), y.toDouble()),
                                Location((x + 1).toDouble(), (y + 1).toDouble()),
                                Location(x.toDouble(), (y + 1).toDouble()),
                            ),
                        quality = quality,
                        area = heatmap.grid.resolution * heatmap.grid.resolution,
                    )

                regions.getOrPut(quality) { mutableListOf() }.add(cellRegion)
            }
        }

        return regions
    }

    /**
     * Determine signal quality from RSSI
     */
    private fun determineQuality(
        rssi: Int,
        thresholds: Map<SignalQuality, Int>,
    ): SignalQuality =
        when {
            rssi >= (thresholds[SignalQuality.EXCELLENT] ?: -50) -> SignalQuality.EXCELLENT
            rssi >= (thresholds[SignalQuality.GOOD] ?: -60) -> SignalQuality.GOOD
            rssi >= (thresholds[SignalQuality.FAIR] ?: -70) -> SignalQuality.FAIR
            rssi >= (thresholds[SignalQuality.POOR] ?: -80) -> SignalQuality.POOR
            rssi >= (thresholds[SignalQuality.VERY_POOR] ?: -90) -> SignalQuality.VERY_POOR
            else -> SignalQuality.NO_SIGNAL
        }
}
