package io.lamco.netkit.topology.survey

import io.lamco.netkit.model.topology.SignalStrength
import kotlin.math.pow

/**
 * Coverage heatmap generator from site survey data
 *
 * Creates signal strength heatmaps using spatial interpolation algorithms.
 * Supports multiple interpolation methods (Nearest Neighbor, IDW, Bilinear)
 * for accurate coverage visualization.
 *
 * Heatmaps are generated as 2D grids where each cell contains predicted
 * signal strength based on nearby measurements.
 *
 * @property gridResolution Number of cells per meter (default: 0.5 = 2m cells)
 * @property interpolationMethod Interpolation algorithm to use
 * @property idwPower Power parameter for IDW interpolation (default: 2.0)
 * @property maxInterpolationDistance Max distance for interpolation (meters)
 */
class CoverageHeatmapper(
    val gridResolution: Double = 0.5,
    val interpolationMethod: InterpolationMethod = InterpolationMethod.INVERSE_DISTANCE_WEIGHTED,
    val idwPower: Double = 2.0,
    val maxInterpolationDistance: Double = 20.0,
) {
    init {
        require(gridResolution > 0) {
            "Grid resolution must be positive, got $gridResolution"
        }
        require(idwPower > 0) {
            "IDW power must be positive, got $idwPower"
        }
        require(maxInterpolationDistance > 0) {
            "Max interpolation distance must be positive, got $maxInterpolationDistance"
        }
    }

    /**
     * Generate heatmap for specific AP
     *
     * Creates 2D grid of predicted signal strengths.
     */
    fun generateHeatmap(
        session: SiteSurveySession,
        bssid: String,
    ): SignalHeatmap? {
        val measurements: List<Pair<SurveyLocation, Int>> =
            session.measurements.mapNotNull { measurement ->
                val bss = measurement.snapshot.allBssids.find { it.bssid == bssid }
                val rssi = bss?.rssiDbm
                if (rssi != null) {
                    measurement.location to rssi
                } else {
                    null
                }
            }

        if (measurements.isEmpty()) return null

        val collector = SiteSurveyCollector()
        val bounds = collector.getSurveyBounds(session) ?: return null

        // Create grid
        val gridWidth = (bounds.widthMeters * gridResolution).toInt().coerceAtLeast(1)
        val gridHeight = (bounds.heightMeters * gridResolution).toInt().coerceAtLeast(1)

        val grid =
            Array(gridHeight) { row ->
                Array(gridWidth) { col ->
                    val x = bounds.minX + (col / gridResolution)
                    val y = bounds.minY + (row / gridResolution)
                    val location = SurveyLocation(x, y)

                    interpolateSignal(location, measurements)
                }
            }

        return SignalHeatmap(
            bssid = bssid,
            bounds = bounds,
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            grid = grid,
            interpolationMethod = interpolationMethod,
            measurementCount = measurements.size,
        )
    }

    /**
     * Interpolate signal strength at a specific location
     */
    private fun interpolateSignal(
        location: SurveyLocation,
        measurements: List<Pair<SurveyLocation, Int>>,
    ): Int? =
        when (interpolationMethod) {
            InterpolationMethod.NEAREST_NEIGHBOR ->
                nearestNeighborInterpolation(location, measurements)

            InterpolationMethod.INVERSE_DISTANCE_WEIGHTED ->
                inverseDistanceWeightedInterpolation(location, measurements)

            InterpolationMethod.BILINEAR ->
                bilinearInterpolation(location, measurements)
        }

    /**
     * Nearest neighbor interpolation
     *
     * Uses signal from closest measurement point.
     */
    private fun nearestNeighborInterpolation(
        location: SurveyLocation,
        measurements: List<Pair<SurveyLocation, Int>>,
    ): Int? {
        val nearest =
            measurements
                .map { (loc, rssi) -> loc.distanceMeters(location) to rssi }
                .filter { it.first <= maxInterpolationDistance }
                .minByOrNull { it.first }

        return nearest?.second
    }

    /**
     * Inverse Distance Weighted (IDW) interpolation
     *
     * Weighted average based on inverse distance.
     */
    private fun inverseDistanceWeightedInterpolation(
        location: SurveyLocation,
        measurements: List<Pair<SurveyLocation, Int>>,
    ): Int? {
        val nearbyMeasurements =
            measurements
                .map { (loc, rssi) -> Triple(loc, rssi, loc.distanceMeters(location)) }
                .filter { it.third <= maxInterpolationDistance }

        if (nearbyMeasurements.isEmpty()) return null

        // Check if we're exactly at a measurement point
        val exactMatch = nearbyMeasurements.find { it.third < 0.1 }
        if (exactMatch != null) return exactMatch.second

        // Calculate weighted average
        var numerator = 0.0
        var denominator = 0.0

        nearbyMeasurements.forEach { (_, rssi, distance) ->
            val weight = 1.0 / distance.pow(idwPower)
            numerator += rssi * weight
            denominator += weight
        }

        return if (denominator > 0) {
            (numerator / denominator).toInt()
        } else {
            null
        }
    }

    /**
     * Bilinear interpolation
     *
     * Interpolates using 4 nearest points in grid pattern.
     */
    private fun bilinearInterpolation(
        location: SurveyLocation,
        measurements: List<Pair<SurveyLocation, Int>>,
    ): Int? {
        // Find 4 nearest measurements forming a quad around the point
        val nearby =
            measurements
                .map { (loc, rssi) -> Triple(loc, rssi, loc.distanceMeters(location)) }
                .filter { it.third <= maxInterpolationDistance }
                .sortedBy { it.third }
                .take(4)

        if (nearby.size < 3) {
            // Fall back to IDW for fewer points
            return inverseDistanceWeightedInterpolation(location, measurements)
        }

        // Simple bilinear: weighted average of 4 nearest points
        val totalDistance = nearby.sumOf { it.third }
        if (totalDistance == 0.0) return nearby.first().second

        val weightedSum =
            nearby.sumOf { (_, rssi, distance) ->
                rssi * (1.0 - distance / totalDistance)
            }
        val weightSum =
            nearby.sumOf { (_, _, distance) ->
                1.0 - distance / totalDistance
            }

        return if (weightSum > 0) {
            (weightedSum / weightSum).toInt()
        } else {
            null
        }
    }

    /**
     * Generate combined heatmap for all APs
     *
     * Shows strongest signal at each point.
     */
    fun generateCombinedHeatmap(session: SiteSurveySession): CombinedHeatmap {
        val allBssids =
            session.measurements
                .flatMap { it.snapshot.allBssids.map { bss -> bss.bssid } }
                .distinct()

        val heatmaps =
            allBssids.mapNotNull { bssid ->
                generateHeatmap(session, bssid)
            }

        val collector = SiteSurveyCollector()
        val bounds =
            collector.getSurveyBounds(session)
                ?: return CombinedHeatmap(emptyMap(), null, 0, 0)

        val gridWidth = (bounds.widthMeters * gridResolution).toInt().coerceAtLeast(1)
        val gridHeight = (bounds.heightMeters * gridResolution).toInt().coerceAtLeast(1)

        // Create combined grid showing strongest signal at each point
        val grid = mutableMapOf<Pair<Int, Int>, HeatmapCell>()

        for (row in 0 until gridHeight) {
            for (col in 0 until gridWidth) {
                val signals =
                    heatmaps.mapNotNull { heatmap ->
                        heatmap.grid.getOrNull(row)?.getOrNull(col)?.let { rssi ->
                            heatmap.bssid to rssi
                        }
                    }

                if (signals.isNotEmpty()) {
                    val strongest = signals.maxByOrNull { it.second }!!
                    grid[row to col] =
                        HeatmapCell(
                            rssiDbm = strongest.second,
                            dominantBssid = strongest.first,
                            apCount = signals.size,
                        )
                }
            }
        }

        return CombinedHeatmap(grid, bounds, gridWidth, gridHeight)
    }

    /**
     * Calculate coverage statistics from heatmap
     */
    fun calculateCoverageStats(heatmap: SignalHeatmap): CoverageStatistics {
        val allValues = heatmap.grid.flatten().filterNotNull()

        if (allValues.isEmpty()) {
            return CoverageStatistics(
                totalCells = heatmap.gridWidth * heatmap.gridHeight,
                coveredCells = 0,
                coveragePercentage = 0.0,
                averageSignalDbm = null,
                strongSignalPercentage = 0.0,
                weakSignalPercentage = 0.0,
            )
        }

        val totalCells = heatmap.gridWidth * heatmap.gridHeight
        val coveredCells = allValues.size
        val coveragePercentage = (coveredCells.toDouble() / totalCells) * 100.0

        val averageSignal = allValues.average()

        val strongCount = allValues.count { it >= -60 }
        val weakCount = allValues.count { it < -75 }

        val strongPercentage = (strongCount.toDouble() / allValues.size) * 100.0
        val weakPercentage = (weakCount.toDouble() / allValues.size) * 100.0

        return CoverageStatistics(
            totalCells = totalCells,
            coveredCells = coveredCells,
            coveragePercentage = coveragePercentage,
            averageSignalDbm = averageSignal.toInt(),
            strongSignalPercentage = strongPercentage,
            weakSignalPercentage = weakPercentage,
        )
    }
}

/**
 * Signal strength heatmap
 *
 * @property bssid Access point MAC address
 * @property bounds Survey area bounds
 * @property gridWidth Number of grid cells horizontally
 * @property gridHeight Number of grid cells vertically
 * @property grid 2D array of signal strengths (null = no coverage)
 * @property interpolationMethod Method used for interpolation
 * @property measurementCount Number of actual measurements
 */
data class SignalHeatmap(
    val bssid: String,
    val bounds: SurveyBounds,
    val gridWidth: Int,
    val gridHeight: Int,
    val grid: Array<Array<Int?>>,
    val interpolationMethod: InterpolationMethod,
    val measurementCount: Int,
) {
    /**
     * Get signal at specific grid coordinates
     */
    fun getSignalAt(
        row: Int,
        col: Int,
    ): Int? = grid.getOrNull(row)?.getOrNull(col)

    /**
     * Get signal strength category at coordinates
     */
    fun getSignalStrengthAt(
        row: Int,
        col: Int,
    ): SignalStrength {
        val rssi = getSignalAt(row, col) ?: return SignalStrength.UNKNOWN
        return SignalStrength.fromRssi(rssi)
    }

    /**
     * Convert grid coordinates to physical location
     */
    fun gridToLocation(
        row: Int,
        col: Int,
        gridResolution: Double = 0.5,
    ): SurveyLocation =
        SurveyLocation(
            x = bounds.minX + (col / gridResolution),
            y = bounds.minY + (row / gridResolution),
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalHeatmap) return false

        if (bssid != other.bssid) return false
        if (bounds != other.bounds) return false
        if (gridWidth != other.gridWidth) return false
        if (gridHeight != other.gridHeight) return false
        if (interpolationMethod != other.interpolationMethod) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bssid.hashCode()
        result = 31 * result + bounds.hashCode()
        result = 31 * result + gridWidth
        result = 31 * result + gridHeight
        result = 31 * result + interpolationMethod.hashCode()
        return result
    }
}

/**
 * Combined heatmap showing all APs
 *
 * @property grid Map of (row, col) to heatmap cell
 * @property bounds Survey area bounds
 * @property gridWidth Grid width
 * @property gridHeight Grid height
 */
data class CombinedHeatmap(
    val grid: Map<Pair<Int, Int>, HeatmapCell>,
    val bounds: SurveyBounds?,
    val gridWidth: Int,
    val gridHeight: Int,
) {
    /**
     * Get cell at coordinates
     */
    fun getCellAt(
        row: Int,
        col: Int,
    ): HeatmapCell? = grid[row to col]

    /**
     * Coverage percentage
     */
    val coveragePercentage: Double
        get() {
            val totalCells = gridWidth * gridHeight
            return if (totalCells > 0) {
                (grid.size.toDouble() / totalCells) * 100.0
            } else {
                0.0
            }
        }
}

/**
 * Single cell in combined heatmap
 *
 * @property rssiDbm Signal strength at this cell
 * @property dominantBssid AP with strongest signal
 * @property apCount Number of APs providing coverage
 */
data class HeatmapCell(
    val rssiDbm: Int,
    val dominantBssid: String,
    val apCount: Int,
) {
    /**
     * Signal strength category
     */
    val signalStrength: SignalStrength
        get() = SignalStrength.fromRssi(rssiDbm)

    /**
     * Whether this cell has good coverage
     */
    val hasGoodCoverage: Boolean
        get() = rssiDbm >= -70 && apCount >= 1
}

/**
 * Coverage statistics from heatmap
 *
 * @property totalCells Total grid cells
 * @property coveredCells Cells with coverage
 * @property coveragePercentage Percentage of area covered
 * @property averageSignalDbm Average signal across covered area
 * @property strongSignalPercentage Percentage with strong signal (>= -60 dBm)
 * @property weakSignalPercentage Percentage with weak signal (< -75 dBm)
 */
data class CoverageStatistics(
    val totalCells: Int,
    val coveredCells: Int,
    val coveragePercentage: Double,
    val averageSignalDbm: Int?,
    val strongSignalPercentage: Double,
    val weakSignalPercentage: Double,
) {
    /**
     * Whether coverage is adequate
     */
    val isAdequate: Boolean
        get() = coveragePercentage >= 90.0 && weakSignalPercentage < 10.0

    /**
     * Coverage quality assessment
     */
    val quality: CoverageQualityLevel
        get() =
            when {
                coveragePercentage >= 95.0 && strongSignalPercentage >= 70.0 ->
                    CoverageQualityLevel.EXCELLENT

                coveragePercentage >= 90.0 && strongSignalPercentage >= 50.0 ->
                    CoverageQualityLevel.GOOD

                coveragePercentage >= 80.0 && weakSignalPercentage < 20.0 ->
                    CoverageQualityLevel.FAIR

                coveragePercentage >= 70.0 ->
                    CoverageQualityLevel.POOR

                else ->
                    CoverageQualityLevel.VERY_POOR
            }
}

/**
 * Interpolation methods for heatmap generation
 */
enum class InterpolationMethod(
    val displayName: String,
) {
    /** Use nearest measurement point */
    NEAREST_NEIGHBOR("Nearest Neighbor"),

    /** Inverse distance weighted average */
    INVERSE_DISTANCE_WEIGHTED("Inverse Distance Weighted"),

    /** Bilinear interpolation */
    BILINEAR("Bilinear"),
}

/**
 * Coverage quality levels
 */
enum class CoverageQualityLevel(
    val displayName: String,
) {
    /** Excellent coverage (95%+, 70%+ strong) */
    EXCELLENT("Excellent"),

    /** Good coverage (90%+, 50%+ strong) */
    GOOD("Good"),

    /** Fair coverage (80%+, <20% weak) */
    FAIR("Fair"),

    /** Poor coverage (70%+) */
    POOR("Poor"),

    /** Very poor coverage (<70%) */
    VERY_POOR("Very Poor"),
    ;

    /**
     * Whether this quality is acceptable
     */
    val isAcceptable: Boolean
        get() = this in listOf(EXCELLENT, GOOD, FAIR)
}
