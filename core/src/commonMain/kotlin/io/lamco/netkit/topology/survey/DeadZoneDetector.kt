package io.lamco.netkit.topology.survey

/**
 * Dead zone detection engine for identifying coverage gaps
 *
 * Analyzes heatmaps and survey data to identify areas with poor or no
 * WiFi coverage. Uses flood-fill algorithms and threshold analysis to
 * detect contiguous regions of weak signal.
 *
 * Dead zones are classified by severity:
 * - CRITICAL: No coverage (no signal)
 * - HIGH: Very weak coverage (<= -85 dBm)
 * - MEDIUM: Weak coverage (-85 to -75 dBm)
 * - LOW: Marginal coverage (-75 to -70 dBm)
 *
 * @property criticalThresholdDbm Signal below this is critical (default: none)
 * @property highThresholdDbm Signal below this is high severity (default: -85)
 * @property mediumThresholdDbm Signal below this is medium severity (default: -75)
 * @property lowThresholdDbm Signal below this is low severity (default: -70)
 * @property minDeadZoneSize Minimum area (cells) to classify as dead zone
 */
class DeadZoneDetector(
    val criticalThresholdDbm: Int? = null,
    val highThresholdDbm: Int = -85,
    val mediumThresholdDbm: Int = -75,
    val lowThresholdDbm: Int = -70,
    val minDeadZoneSize: Int = 4,
) {
    init {
        require(highThresholdDbm < lowThresholdDbm) {
            "High threshold must be less than low threshold"
        }
        require(mediumThresholdDbm < lowThresholdDbm) {
            "Medium threshold must be less than low threshold"
        }
        require(mediumThresholdDbm > highThresholdDbm) {
            "Medium threshold must be greater than high threshold"
        }
        require(minDeadZoneSize >= 1) {
            "Minimum dead zone size must be >= 1, got $minDeadZoneSize"
        }
    }

    /**
     * Detect all dead zones in heatmap
     *
     * Uses flood-fill algorithm to identify contiguous weak signal regions.
     */
    fun detectDeadZones(heatmap: SignalHeatmap): List<DeadZone> {
        val visited = Array(heatmap.gridHeight) { BooleanArray(heatmap.gridWidth) }
        val deadZones = mutableListOf<DeadZone>()

        for (row in 0 until heatmap.gridHeight) {
            for (col in 0 until heatmap.gridWidth) {
                if (!visited[row][col]) {
                    val signal = heatmap.getSignalAt(row, col)
                    val severity = classifySeverity(signal)

                    if (severity != null) {
                        // Found a dead zone cell - flood fill to find entire zone
                        val cells = floodFill(heatmap, row, col, severity, visited)

                        if (cells.size >= minDeadZoneSize) {
                            val zone = createDeadZone(heatmap, cells, severity)
                            deadZones.add(zone)
                        }
                    } else {
                        visited[row][col] = true
                    }
                }
            }
        }

        return deadZones.sortedByDescending { it.severity }
    }

    /**
     * Detect dead zones in combined heatmap
     */
    fun detectDeadZonesInCombined(heatmap: CombinedHeatmap): List<DeadZone> {
        if (heatmap.bounds == null) return emptyList()

        val visited = Array(heatmap.gridHeight) { BooleanArray(heatmap.gridWidth) }
        val deadZones = mutableListOf<DeadZone>()

        for (row in 0 until heatmap.gridHeight) {
            for (col in 0 until heatmap.gridWidth) {
                if (!visited[row][col]) {
                    val cell = heatmap.getCellAt(row, col)
                    val signal = cell?.rssiDbm
                    val severity = classifySeverity(signal)

                    if (severity != null) {
                        val cells = floodFillCombined(heatmap, row, col, severity, visited)

                        if (cells.size >= minDeadZoneSize) {
                            val zone = createDeadZoneFromCombined(heatmap, cells, severity)
                            deadZones.add(zone)
                        }
                    } else {
                        visited[row][col] = true
                    }
                }
            }
        }

        return deadZones.sortedByDescending { it.severity }
    }

    /**
     * Classify signal severity
     */
    private fun classifySeverity(signalDbm: Int?): DeadZoneSeverity? =
        when {
            signalDbm == null && criticalThresholdDbm != null -> DeadZoneSeverity.CRITICAL
            signalDbm == null -> null // No coverage but not tracking as critical
            signalDbm <= highThresholdDbm -> DeadZoneSeverity.HIGH
            signalDbm < mediumThresholdDbm -> DeadZoneSeverity.MEDIUM
            signalDbm < lowThresholdDbm -> DeadZoneSeverity.LOW
            else -> null // Adequate signal
        }

    /**
     * Flood fill to find contiguous dead zone
     */
    private fun floodFill(
        heatmap: SignalHeatmap,
        startRow: Int,
        startCol: Int,
        targetSeverity: DeadZoneSeverity,
        visited: Array<BooleanArray>,
    ): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        val stack = mutableListOf(startRow to startCol)

        while (stack.isNotEmpty()) {
            val (row, col) = stack.removeAt(stack.lastIndex)

            // Skip if out of bounds or already visited
            if (!isValidCell(row, col, heatmap.gridHeight, heatmap.gridWidth, visited)) {
                continue
            }

            val signal = heatmap.getSignalAt(row, col)
            val severity = classifySeverity(signal)

            visited[row][col] = true

            // Add to results if severity matches
            if (severity == targetSeverity) {
                cells.add(row to col)
                // Add neighbors to stack
                stack.add((row - 1) to col) // Up
                stack.add((row + 1) to col) // Down
                stack.add(row to (col - 1)) // Left
                stack.add(row to (col + 1)) // Right
            }
        }

        return cells
    }

    /**
     * Check if cell is within bounds and not visited
     */
    private fun isValidCell(
        row: Int,
        col: Int,
        height: Int,
        width: Int,
        visited: Array<BooleanArray>,
    ): Boolean =
        row in 0 until height &&
            col in 0 until width &&
            !visited[row][col]

    /**
     * Flood fill for combined heatmap
     */
    private fun floodFillCombined(
        heatmap: CombinedHeatmap,
        startRow: Int,
        startCol: Int,
        targetSeverity: DeadZoneSeverity,
        visited: Array<BooleanArray>,
    ): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        val stack = mutableListOf(startRow to startCol)

        while (stack.isNotEmpty()) {
            val (row, col) = stack.removeAt(stack.lastIndex)

            // Skip if out of bounds or already visited
            if (!isValidCell(row, col, heatmap.gridHeight, heatmap.gridWidth, visited)) {
                continue
            }

            val cell = heatmap.getCellAt(row, col)
            val severity = classifySeverity(cell?.rssiDbm)

            visited[row][col] = true

            // Add to results if severity matches
            if (severity == targetSeverity) {
                cells.add(row to col)
                // Add neighbors
                stack.add((row - 1) to col)
                stack.add((row + 1) to col)
                stack.add(row to (col - 1))
                stack.add(row to (col + 1))
            }
        }

        return cells
    }

    /**
     * Create dead zone object from cells
     */
    private fun createDeadZone(
        heatmap: SignalHeatmap,
        cells: List<Pair<Int, Int>>,
        severity: DeadZoneSeverity,
    ): DeadZone {
        val minRow = cells.minOf { it.first }
        val maxRow = cells.maxOf { it.first }
        val minCol = cells.minOf { it.second }
        val maxCol = cells.maxOf { it.second }

        val centerRow = (minRow + maxRow) / 2
        val centerCol = (minCol + maxCol) / 2
        val centerLocation = heatmap.gridToLocation(centerRow, centerCol)

        val signals =
            cells.mapNotNull { (row, col) ->
                heatmap.getSignalAt(row, col)
            }
        val averageSignal = signals.average().takeIf { it.isFinite() }?.toInt()

        return DeadZone(
            severity = severity,
            cellCount = cells.size,
            centerLocation = centerLocation,
            minRow = minRow,
            maxRow = maxRow,
            minCol = minCol,
            maxCol = maxCol,
            averageSignalDbm = averageSignal,
            affectedBssid = heatmap.bssid,
        )
    }

    /**
     * Create dead zone from combined heatmap
     */
    private fun createDeadZoneFromCombined(
        heatmap: CombinedHeatmap,
        cells: List<Pair<Int, Int>>,
        severity: DeadZoneSeverity,
    ): DeadZone {
        val minRow = cells.minOf { it.first }
        val maxRow = cells.maxOf { it.first }
        val minCol = cells.minOf { it.second }
        val maxCol = cells.maxOf { it.second }

        val centerRow = (minRow + maxRow) / 2
        val centerCol = (minCol + maxCol) / 2

        // Convert grid coords to physical location
        val bounds = heatmap.bounds!!
        val gridResolution = 0.5 // Default from CoverageHeatmapper
        val centerLocation =
            SurveyLocation(
                x = bounds.minX + (centerCol / gridResolution),
                y = bounds.minY + (centerRow / gridResolution),
            )

        val signals =
            cells.mapNotNull { (row, col) ->
                heatmap.getCellAt(row, col)?.rssiDbm
            }
        val averageSignal = signals.average().takeIf { it.isFinite() }?.toInt()

        return DeadZone(
            severity = severity,
            cellCount = cells.size,
            centerLocation = centerLocation,
            minRow = minRow,
            maxRow = maxRow,
            minCol = minCol,
            maxCol = maxCol,
            averageSignalDbm = averageSignal,
            affectedBssid = null,
        )
    }

    /**
     * Generate dead zone report
     */
    fun generateReport(deadZones: List<DeadZone>): DeadZoneReport {
        val criticalCount = deadZones.count { it.severity == DeadZoneSeverity.CRITICAL }
        val highCount = deadZones.count { it.severity == DeadZoneSeverity.HIGH }
        val mediumCount = deadZones.count { it.severity == DeadZoneSeverity.MEDIUM }
        val lowCount = deadZones.count { it.severity == DeadZoneSeverity.LOW }

        val totalAffectedCells = deadZones.sumOf { it.cellCount }
        val largestZone = deadZones.maxByOrNull { it.cellCount }

        return DeadZoneReport(
            totalDeadZones = deadZones.size,
            criticalCount = criticalCount,
            highCount = highCount,
            mediumCount = mediumCount,
            lowCount = lowCount,
            totalAffectedCells = totalAffectedCells,
            largestZone = largestZone,
            deadZones = deadZones,
        )
    }

    /**
     * Suggest improvements for dead zones
     */
    fun suggestImprovements(deadZones: List<DeadZone>): List<ImprovementSuggestion> =
        deadZones
            .filter { it.severity in listOf(DeadZoneSeverity.CRITICAL, DeadZoneSeverity.HIGH) }
            .map { zone ->
                val suggestion =
                    when (zone.severity) {
                        DeadZoneSeverity.CRITICAL ->
                            "Add new AP at or near ${zone.centerLocation.label ?: "center of dead zone"}"

                        DeadZoneSeverity.HIGH ->
                            "Consider adding AP or moving existing AP closer to this area"

                        else ->
                            "Adjust AP power or antenna orientation"
                    }

                ImprovementSuggestion(
                    deadZone = zone,
                    priority = zone.severity.priority,
                    suggestion = suggestion,
                    estimatedImpact =
                        when (zone.severity) {
                            DeadZoneSeverity.CRITICAL -> ImprovementImpact.HIGH
                            DeadZoneSeverity.HIGH -> ImprovementImpact.MEDIUM
                            else -> ImprovementImpact.LOW
                        },
                )
            }.sortedByDescending { it.priority }
}

/**
 * Detected dead zone (coverage gap)
 *
 * @property severity Dead zone severity level
 * @property cellCount Number of grid cells in this zone
 * @property centerLocation Geographic center of zone
 * @property minRow Minimum grid row
 * @property maxRow Maximum grid row
 * @property minCol Minimum grid column
 * @property maxCol Maximum grid column
 * @property averageSignalDbm Average signal in this zone
 * @property affectedBssid BSSID with poor coverage (if single-AP heatmap)
 */
data class DeadZone(
    val severity: DeadZoneSeverity,
    val cellCount: Int,
    val centerLocation: SurveyLocation,
    val minRow: Int,
    val maxRow: Int,
    val minCol: Int,
    val maxCol: Int,
    val averageSignalDbm: Int?,
    val affectedBssid: String?,
) {
    /**
     * Zone dimensions
     */
    val widthCells: Int
        get() = maxCol - minCol + 1

    val heightCells: Int
        get() = maxRow - minRow + 1

    /**
     * Zone area (cells)
     */
    val areaCells: Int
        get() = widthCells * heightCells

    /**
     * Whether this zone requires immediate attention
     */
    val requiresImmediateAttention: Boolean
        get() = severity in listOf(DeadZoneSeverity.CRITICAL, DeadZoneSeverity.HIGH)

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("${severity.displayName} dead zone: ")
                append("$cellCount cells")
                if (averageSignalDbm != null) {
                    append(", avg ${averageSignalDbm}dBm")
                }
                append(" at ${centerLocation.label ?: "(${centerLocation.x}, ${centerLocation.y})"}")
            }
}

/**
 * Dead zone analysis report
 *
 * @property totalDeadZones Total number of dead zones detected
 * @property criticalCount Critical severity zones
 * @property highCount High severity zones
 * @property mediumCount Medium severity zones
 * @property lowCount Low severity zones
 * @property totalAffectedCells Total cells with poor coverage
 * @property largestZone Largest dead zone by cell count
 * @property deadZones All detected dead zones
 */
data class DeadZoneReport(
    val totalDeadZones: Int,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val totalAffectedCells: Int,
    val largestZone: DeadZone?,
    val deadZones: List<DeadZone>,
) {
    /**
     * Whether any critical zones exist
     */
    val hasCriticalZones: Boolean
        get() = criticalCount > 0

    /**
     * Whether coverage is acceptable
     */
    val isAcceptable: Boolean
        get() = criticalCount == 0 && highCount <= 2

    /**
     * Priority zones needing attention
     */
    val priorityZones: List<DeadZone>
        get() = deadZones.filter { it.requiresImmediateAttention }

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("$totalDeadZones dead zones detected")
                if (criticalCount > 0) append(" ($criticalCount CRITICAL)")
                if (highCount > 0) append(" ($highCount HIGH)")
                if (totalAffectedCells > 0) append(", $totalAffectedCells cells affected")
            }
}

/**
 * Improvement suggestion for dead zone
 *
 * @property deadZone The dead zone to address
 * @property priority Priority level (1-5, 5 = highest)
 * @property suggestion Suggested action
 * @property estimatedImpact Expected impact of implementing suggestion
 */
data class ImprovementSuggestion(
    val deadZone: DeadZone,
    val priority: Int,
    val suggestion: String,
    val estimatedImpact: ImprovementImpact,
) {
    /**
     * Human-readable summary
     */
    val summary: String
        get() = "Priority $priority: $suggestion (${estimatedImpact.displayName} impact)"
}

/**
 * Dead zone severity levels
 */
enum class DeadZoneSeverity(
    val displayName: String,
    val priority: Int,
) {
    /** Critical - no coverage */
    CRITICAL("Critical (No Coverage)", 5),

    /** High - very weak signal (<= -85 dBm) */
    HIGH("High (Very Weak)", 4),

    /** Medium - weak signal (-85 to -75 dBm) */
    MEDIUM("Medium (Weak)", 3),

    /** Low - marginal signal (-75 to -70 dBm) */
    LOW("Low (Marginal)", 2),
    ;

    /**
     * Whether this severity requires action
     */
    val requiresAction: Boolean
        get() = priority >= 3
}

/**
 * Improvement impact levels
 */
enum class ImprovementImpact(
    val displayName: String,
) {
    /** High impact - significant coverage improvement */
    HIGH("High"),

    /** Medium impact - moderate improvement */
    MEDIUM("Medium"),

    /** Low impact - minor improvement */
    LOW("Low"),
}
