package io.lamco.netkit.topology.visualization

import io.lamco.netkit.model.topology.*
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Signal strength heatmap generator
 *
 * Generates signal strength heatmaps from site survey measurements or AP topology.
 * Supports multiple interpolation methods and provides coverage predictions.
 *
 * @property gridResolution Grid resolution in meters (default: 1.0m)
 * @property interpolationMethod Interpolation algorithm to use
 */
class SignalStrengthMapper(
    private val gridResolution: Double = 1.0,
    private val interpolationMethod: InterpolationMethod = InterpolationMethod.INVERSE_DISTANCE_WEIGHTED,
) {
    init {
        require(gridResolution > 0.0) { "Grid resolution must be positive, got: $gridResolution" }
    }

    /**
     * Generate signal strength heatmap from survey measurements
     *
     * @param measurements Site survey measurements with coordinates and RSSI
     * @param bounds Bounding box for the heatmap area
     * @return Heatmap data with grid coordinates and signal values
     */
    fun generateHeatmap(
        measurements: List<SurveyMeasurement>,
        bounds: AreaBounds,
    ): SignalHeatmap {
        require(measurements.isNotEmpty()) { "Measurements list cannot be empty" }

        val grid = buildGrid(bounds)
        val signalGrid = interpolateSignalStrength(measurements, grid)

        return SignalHeatmap(
            bounds = bounds,
            gridResolution = gridResolution,
            gridWidth = grid.width,
            gridHeight = grid.height,
            signalValues = signalGrid,
            measurementCount = measurements.size,
            interpolationMethod = interpolationMethod,
        )
    }

    /**
     * Generate coverage map showing signal quality zones
     *
     * @param measurements Site survey measurements
     * @param bounds Bounding box for the coverage area
     * @return Coverage map with quality zones
     */
    fun generateCoverageMap(
        measurements: List<SurveyMeasurement>,
        bounds: AreaBounds,
    ): CoverageMap {
        val heatmap = generateHeatmap(measurements, bounds)
        val qualityZones = categorizeSignalQuality(heatmap)

        return CoverageMap(
            heatmap = heatmap,
            excellentCoverage = qualityZones.count { it == SignalQuality.EXCELLENT },
            goodCoverage = qualityZones.count { it == SignalQuality.GOOD },
            fairCoverage = qualityZones.count { it == SignalQuality.FAIR },
            poorCoverage = qualityZones.count { it == SignalQuality.POOR },
            noCoverage = qualityZones.count { it == SignalQuality.NONE },
            totalGridPoints = qualityZones.size,
            coveragePercentage = calculateCoveragePercentage(qualityZones),
        )
    }

    /**
     * Predict signal strength at a specific coordinate
     *
     * @param measurements Known measurements
     * @param coordinate Target coordinate for prediction
     * @return Predicted RSSI value
     */
    fun predictSignalStrength(
        measurements: List<SurveyMeasurement>,
        coordinate: LocalSurveyCoord,
    ): Int {
        require(measurements.isNotEmpty()) { "Measurements list cannot be empty" }

        return when (interpolationMethod) {
            InterpolationMethod.NEAREST_NEIGHBOR -> nearestNeighborInterpolation(measurements, coordinate)
            InterpolationMethod.INVERSE_DISTANCE_WEIGHTED -> idwInterpolation(measurements, coordinate)
            InterpolationMethod.BILINEAR -> bilinearInterpolation(measurements, coordinate)
        }
    }

    /**
     * Identify dead zones in coverage area
     *
     * @param measurements Site survey measurements
     * @param bounds Coverage area bounds
     * @param threshold RSSI threshold for dead zone (default: -85 dBm)
     * @return List of dead zone regions
     */
    fun identifyDeadZones(
        measurements: List<SurveyMeasurement>,
        bounds: AreaBounds,
        threshold: Int = -85,
    ): List<DeadZoneRegion> {
        val heatmap = generateHeatmap(measurements, bounds)
        val deadZones = mutableListOf<DeadZoneRegion>()

        // Find contiguous regions below threshold
        val visited = Array(heatmap.gridHeight) { BooleanArray(heatmap.gridWidth) }

        for (y in 0 until heatmap.gridHeight) {
            for (x in 0 until heatmap.gridWidth) {
                if (!visited[y][x] && heatmap.signalValues[y][x] < threshold) {
                    val region = floodFill(heatmap, x, y, threshold, visited)
                    if (region.gridPoints.size >= 4) { // Minimum size threshold
                        deadZones.add(region)
                    }
                }
            }
        }

        return deadZones.sortedByDescending { it.gridPoints.size }
    }

    // Private helper methods

    private fun buildGrid(bounds: AreaBounds): Grid {
        val width = ((bounds.maxX - bounds.minX) / gridResolution).toInt() + 1
        val height = ((bounds.maxY - bounds.minY) / gridResolution).toInt() + 1

        return Grid(
            width = width,
            height = height,
            originX = bounds.minX,
            originY = bounds.minY,
            resolution = gridResolution,
        )
    }

    private fun interpolateSignalStrength(
        measurements: List<SurveyMeasurement>,
        grid: Grid,
    ): Array<IntArray> {
        val signalGrid = Array(grid.height) { IntArray(grid.width) }

        // Calculate bounds extents for normalization
        val boundsWidth = (grid.width - 1) * grid.resolution
        val boundsHeight = (grid.height - 1) * grid.resolution

        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                // Normalize coordinates to [0, 1] range for LocalSurveyCoord
                val normalizedX = if (boundsWidth > 0) (x * grid.resolution) / boundsWidth else 0.5
                val normalizedY = if (boundsHeight > 0) (y * grid.resolution) / boundsHeight else 0.5

                val coord =
                    LocalSurveyCoord(
                        x = normalizedX.coerceIn(0.0, 1.0),
                        y = normalizedY.coerceIn(0.0, 1.0),
                    )
                signalGrid[y][x] = predictSignalStrength(measurements, coord)
            }
        }

        return signalGrid
    }

    private fun nearestNeighborInterpolation(
        measurements: List<SurveyMeasurement>,
        target: LocalSurveyCoord,
    ): Int {
        val nearest =
            measurements.minByOrNull { measurement ->
                val localCoord = measurement.localCoordinates ?: return@minByOrNull Double.MAX_VALUE
                distance(localCoord, target)
            } ?: return -100

        return nearest.strongestRssiDbm
    }

    private fun idwInterpolation(
        measurements: List<SurveyMeasurement>,
        target: LocalSurveyCoord,
        power: Double = 2.0,
    ): Int {
        var weightedSum = 0.0
        var weightSum = 0.0

        measurements.forEach { measurement ->
            val localCoord = measurement.localCoordinates ?: return@forEach
            val dist = distance(localCoord, target)
            if (dist < 0.01) { // Very close to measurement point
                return measurement.strongestRssiDbm
            }

            val weight = 1.0 / dist.pow(power)
            weightedSum += weight * measurement.strongestRssiDbm
            weightSum += weight
        }

        return if (weightSum > 0) {
            (weightedSum / weightSum).toInt()
        } else {
            -100
        }
    }

    private fun bilinearInterpolation(
        measurements: List<SurveyMeasurement>,
        target: LocalSurveyCoord,
    ): Int {
        // Simplified bilinear - use IDW for now
        // Full bilinear would require structured grid of measurements
        return idwInterpolation(measurements, target)
    }

    private fun distance(
        coord1: LocalSurveyCoord,
        coord2: LocalSurveyCoord,
    ): Double {
        val dx = coord1.x - coord2.x
        val dy = coord1.y - coord2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun categorizeSignalQuality(heatmap: SignalHeatmap): List<SignalQuality> {
        val qualities = mutableListOf<SignalQuality>()

        for (y in 0 until heatmap.gridHeight) {
            for (x in 0 until heatmap.gridWidth) {
                val rssi = heatmap.signalValues[y][x]
                qualities.add(SignalQuality.fromRssi(rssi))
            }
        }

        return qualities
    }

    private fun calculateCoveragePercentage(qualities: List<SignalQuality>): Double {
        if (qualities.isEmpty()) return 0.0

        val usableCoverage =
            qualities.count {
                it != SignalQuality.NONE && it != SignalQuality.POOR
            }

        return (usableCoverage.toDouble() / qualities.size) * 100.0
    }

    private fun floodFill(
        heatmap: SignalHeatmap,
        startX: Int,
        startY: Int,
        threshold: Int,
        visited: Array<BooleanArray>,
    ): DeadZoneRegion {
        val points = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))
        visited[startY][startX] = true

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeAt(0) // Android 15 compatibility fix
            points.add(Pair(x, y))

            // Check 4-connected neighbors
            val neighbors =
                listOf(
                    Pair(x - 1, y),
                    Pair(x + 1, y),
                    Pair(x, y - 1),
                    Pair(x, y + 1),
                )

            neighbors.forEach { (nx, ny) ->
                if (nx in 0 until heatmap.gridWidth &&
                    ny in 0 until heatmap.gridHeight &&
                    !visited[ny][nx] &&
                    heatmap.signalValues[ny][nx] < threshold
                ) {
                    visited[ny][nx] = true
                    queue.add(Pair(nx, ny))
                }
            }
        }

        val rssiValues = points.map { (x, y) -> heatmap.signalValues[y][x] }
        return DeadZoneRegion(
            gridPoints = points,
            averageRssi = rssiValues.average().toInt(),
            worstRssi = rssiValues.minOrNull() ?: -100,
            areaSquareMeters = points.size * heatmap.gridResolution * heatmap.gridResolution,
        )
    }
}

/**
 * Interpolation method for signal strength prediction
 */
enum class InterpolationMethod {
    /** Nearest neighbor - uses closest measurement */
    NEAREST_NEIGHBOR,

    /** Inverse Distance Weighted - weighted average based on distance */
    INVERSE_DISTANCE_WEIGHTED,

    /** Bilinear interpolation - for structured grids */
    BILINEAR,
}

/**
 * Signal quality categories
 */
enum class SignalQuality {
    /** Excellent signal (>= -50 dBm) */
    EXCELLENT,

    /** Good signal (-60 to -50 dBm) */
    GOOD,

    /** Fair signal (-70 to -60 dBm) */
    FAIR,

    /** Poor signal (-85 to -70 dBm) */
    POOR,

    /** No usable signal (< -85 dBm) */
    NONE,

    ;

    companion object {
        /**
         * Categorize signal quality from RSSI value
         */
        fun fromRssi(rssi: Int): SignalQuality =
            when {
                rssi >= -50 -> EXCELLENT
                rssi >= -60 -> GOOD
                rssi >= -70 -> FAIR
                rssi >= -85 -> POOR
                else -> NONE
            }
    }
}

/**
 * Area bounds for heatmap generation
 */
data class AreaBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    init {
        require(maxX > minX) { "maxX must be greater than minX" }
        require(maxY > minY) { "maxY must be greater than minY" }
    }

    /** Area width in coordinate units */
    val width: Double
        get() = maxX - minX

    /** Area height in coordinate units */
    val height: Double
        get() = maxY - minY

    /** Area in square units */
    val area: Double
        get() = width * height
}

/**
 * Signal strength heatmap data
 */
data class SignalHeatmap(
    val bounds: AreaBounds,
    val gridResolution: Double,
    val gridWidth: Int,
    val gridHeight: Int,
    val signalValues: Array<IntArray>,
    val measurementCount: Int,
    val interpolationMethod: InterpolationMethod,
) {
    /** Get signal value at grid coordinates */
    fun getSignal(
        x: Int,
        y: Int,
    ): Int? =
        if (x in 0 until gridWidth && y in 0 until gridHeight) {
            signalValues[y][x]
        } else {
            null
        }

    /** Average signal strength across entire heatmap */
    val averageSignal: Double
        get() = signalValues.flatMap { it.toList() }.average()

    /** Minimum signal strength in heatmap */
    val minSignal: Int
        get() = signalValues.flatMap { it.toList() }.minOrNull() ?: -100

    /** Maximum signal strength in heatmap */
    val maxSignal: Int
        get() = signalValues.flatMap { it.toList() }.maxOrNull() ?: -100

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalHeatmap) return false
        return bounds == other.bounds &&
            gridResolution == other.gridResolution &&
            gridWidth == other.gridWidth &&
            gridHeight == other.gridHeight &&
            signalValues.contentDeepEquals(other.signalValues)
    }

    override fun hashCode(): Int {
        var result = bounds.hashCode()
        result = 31 * result + gridResolution.hashCode()
        result = 31 * result + gridWidth
        result = 31 * result + gridHeight
        result = 31 * result + signalValues.contentDeepHashCode()
        return result
    }
}

/**
 * Coverage map with quality zones
 */
data class CoverageMap(
    val heatmap: SignalHeatmap,
    val excellentCoverage: Int,
    val goodCoverage: Int,
    val fairCoverage: Int,
    val poorCoverage: Int,
    val noCoverage: Int,
    val totalGridPoints: Int,
    val coveragePercentage: Double,
) {
    /** Percentage of area with excellent coverage */
    val excellentPercentage: Double
        get() = (excellentCoverage.toDouble() / totalGridPoints) * 100.0

    /** Percentage of area with good or better coverage */
    val goodOrBetterPercentage: Double
        get() = ((excellentCoverage + goodCoverage).toDouble() / totalGridPoints) * 100.0

    /** Overall coverage quality assessment */
    val overallQuality: CoverageQuality
        get() =
            when {
                excellentPercentage >= 80 -> CoverageQuality.EXCELLENT
                goodOrBetterPercentage >= 70 -> CoverageQuality.GOOD
                coveragePercentage >= 60 -> CoverageQuality.FAIR
                else -> CoverageQuality.POOR
            }
}

/**
 * Coverage quality categories
 */
enum class CoverageQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}

/**
 * Dead zone severity levels
 */
enum class DeadZoneSeverity {
    /** Critical dead zone - very large area or extremely weak signal */
    CRITICAL,

    /** High severity - significant coverage gap */
    HIGH,

    /** Medium severity - moderate coverage gap */
    MEDIUM,

    /** Low severity - small coverage gap */
    LOW,
}

/**
 * Dead zone region identified in coverage area
 */
data class DeadZoneRegion(
    val gridPoints: List<Pair<Int, Int>>,
    val averageRssi: Int,
    val worstRssi: Int,
    val areaSquareMeters: Double,
) {
    /** Number of grid points in dead zone */
    val pointCount: Int
        get() = gridPoints.size

    /** Severity based on size and signal quality */
    val severity: DeadZoneSeverity
        get() =
            when {
                worstRssi < -95 && areaSquareMeters > 10 -> DeadZoneSeverity.CRITICAL
                worstRssi < -90 || areaSquareMeters > 20 -> DeadZoneSeverity.HIGH
                areaSquareMeters > 5 -> DeadZoneSeverity.MEDIUM
                else -> DeadZoneSeverity.LOW
            }
}

/**
 * Grid structure for signal mapping
 */
private data class Grid(
    val width: Int,
    val height: Int,
    val originX: Double,
    val originY: Double,
    val resolution: Double,
)
