package io.lamco.netkit.topology.survey

import io.lamco.netkit.model.topology.*

/**
 * Site survey data collection and management engine
 *
 * Coordinates structured WiFi site surveys for coverage analysis,
 * AP placement optimization, and network planning. Manages survey
 * sessions, validates measurement quality, and organizes spatial data.
 *
 * A site survey involves systematically measuring WiFi signals at
 * multiple locations to create a comprehensive coverage map.
 *
 * @property minMeasurementsPerLocation Minimum measurements per point for reliability
 * @property measurementTimeoutMillis Max time between measurements at same location
 * @property spatialResolutionMeters Minimum distance between survey points
 */
class SiteSurveyCollector(
    val minMeasurementsPerLocation: Int = 3,
    val measurementTimeoutMillis: Long = 60_000,
    val spatialResolutionMeters: Double = 2.0,
) {
    init {
        require(minMeasurementsPerLocation >= 1) {
            "Minimum measurements per location must be >= 1, got $minMeasurementsPerLocation"
        }
        require(measurementTimeoutMillis > 0) {
            "Measurement timeout must be positive, got $measurementTimeoutMillis"
        }
        require(spatialResolutionMeters > 0) {
            "Spatial resolution must be positive, got $spatialResolutionMeters"
        }
    }

    /**
     * Create new site survey session
     */
    fun createSurvey(
        name: String,
        ssid: String,
        description: String? = null,
    ): SiteSurveySession {
        require(name.isNotBlank()) {
            "Survey name must not be blank"
        }
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }

        return SiteSurveySession(
            id = "survey_${System.currentTimeMillis()}",
            name = name,
            ssid = ssid,
            description = description,
            startTimestamp = System.currentTimeMillis(),
            measurements = emptyList(),
            status = SurveyStatus.IN_PROGRESS,
        )
    }

    /**
     * Add measurement to survey session
     *
     * Validates measurement quality and spatial placement.
     */
    fun addMeasurement(
        session: SiteSurveySession,
        snapshot: ScanSnapshot,
        location: SurveyLocation,
    ): SiteSurveySession {
        require(session.status == SurveyStatus.IN_PROGRESS) {
            "Cannot add measurements to ${session.status} survey"
        }

        // Validate snapshot contains target SSID
        val targetCluster = snapshot.clusters.find { it.ssid == session.ssid }
        require(targetCluster != null) {
            "Snapshot does not contain target SSID ${session.ssid}"
        }

        // Check spatial resolution
        val isTooClose =
            session.measurements.any { existing ->
                existing.location.distanceMeters(location) < spatialResolutionMeters
            }

        if (isTooClose) {
            // Merge with existing measurement at similar location
            return mergeMeasurement(session, snapshot, location)
        }

        // Create new measurement
        val measurement =
            SurveyMeasurement(
                location = location,
                timestamp = snapshot.timestampMillis,
                snapshot = snapshot,
                measurementCount = 1,
            )

        val updatedMeasurements = session.measurements + measurement

        return session.copy(
            measurements = updatedMeasurements,
            lastUpdateTimestamp = System.currentTimeMillis(),
        )
    }

    /**
     * Merge measurement with existing nearby measurement
     */
    private fun mergeMeasurement(
        session: SiteSurveySession,
        snapshot: ScanSnapshot,
        location: SurveyLocation,
    ): SiteSurveySession {
        val nearestMeasurement =
            session.measurements
                .minByOrNull { it.location.distanceMeters(location) }
                ?: return session

        // Average the location coordinates
        val avgLocation = nearestMeasurement.location.averageWith(location)

        val updatedMeasurement =
            nearestMeasurement.copy(
                location = avgLocation,
                timestamp = snapshot.timestampMillis,
                snapshot = snapshot,
                measurementCount = nearestMeasurement.measurementCount + 1,
            )

        val updatedMeasurements =
            session.measurements
                .filterNot { it == nearestMeasurement } +
                updatedMeasurement

        return session.copy(
            measurements = updatedMeasurements,
            lastUpdateTimestamp = System.currentTimeMillis(),
        )
    }

    /**
     * Complete survey session
     *
     * Validates survey completeness and marks as complete.
     */
    fun completeSurvey(session: SiteSurveySession): SiteSurveySession {
        require(session.status == SurveyStatus.IN_PROGRESS) {
            "Survey is already ${session.status}"
        }

        val quality = assessSurveyQuality(session)

        return session.copy(
            status = SurveyStatus.COMPLETED,
            endTimestamp = System.currentTimeMillis(),
            quality = quality,
        )
    }

    /**
     * Assess survey quality based on coverage and measurement density
     */
    fun assessSurveyQuality(session: SiteSurveySession): SurveyQuality {
        if (session.measurements.isEmpty()) {
            return SurveyQuality.INSUFFICIENT_DATA
        }

        val avgMeasurementCount =
            session.measurements
                .map { it.measurementCount }
                .average()

        val hasGoodDensity = avgMeasurementCount >= minMeasurementsPerLocation
        val hasGoodCoverage = session.measurements.size >= 20
        val hasExcellentCoverage = session.measurements.size >= 50

        return when {
            hasExcellentCoverage && avgMeasurementCount >= minMeasurementsPerLocation * 2 ->
                SurveyQuality.EXCELLENT

            hasGoodCoverage && hasGoodDensity ->
                SurveyQuality.GOOD

            session.measurements.size >= 10 && avgMeasurementCount >= minMeasurementsPerLocation ->
                SurveyQuality.FAIR

            session.measurements.size >= 5 ->
                SurveyQuality.POOR

            else ->
                SurveyQuality.INSUFFICIENT_DATA
        }
    }

    /**
     * Get survey coverage bounds
     *
     * Returns the geographic bounding box of all measurements.
     */
    fun getSurveyBounds(session: SiteSurveySession): SurveyBounds? {
        if (session.measurements.isEmpty()) return null

        val locations = session.measurements.map { it.location }

        // Find min/max coordinates
        val minX = locations.minOf { it.x }
        val maxX = locations.maxOf { it.x }
        val minY = locations.minOf { it.y }
        val maxY = locations.maxOf { it.y }

        return SurveyBounds(
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            widthMeters = maxX - minX,
            heightMeters = maxY - minY,
        )
    }

    /**
     * Get measurements in a specific area
     */
    fun getMeasurementsInArea(
        session: SiteSurveySession,
        bounds: SurveyBounds,
    ): List<SurveyMeasurement> =
        session.measurements.filter { measurement ->
            val loc = measurement.location
            loc.x in bounds.minX..bounds.maxX &&
                loc.y in bounds.minY..bounds.maxY
        }

    /**
     * Get AP signal statistics across survey
     */
    fun getApStatistics(
        session: SiteSurveySession,
        bssid: String,
    ): ApSurveyStatistics? {
        val measurements =
            session.measurements.mapNotNull { measurement ->
                val bss = measurement.snapshot.allBssids.find { it.bssid == bssid }
                if (bss?.rssiDbm != null) {
                    measurement.location to bss.rssiDbm
                } else {
                    null
                }
            }

        if (measurements.isEmpty()) return null

        val rssiValues = measurements.map { it.second }

        val rssiInts = rssiValues.mapNotNull { it }
        return ApSurveyStatistics(
            bssid = bssid,
            measurementCount = measurements.size,
            averageRssiDbm = if (rssiInts.isNotEmpty()) rssiInts.average().toInt() else 0,
            minRssiDbm = rssiInts.minOrNull() ?: 0,
            maxRssiDbm = rssiInts.maxOrNull() ?: 0,
            coveragePercentage = (measurements.size.toDouble() / session.measurements.size) * 100.0,
            locations = measurements.map { it.first },
        )
    }

    /**
     * Generate survey summary report
     */
    fun generateSummary(session: SiteSurveySession): SurveySummary {
        val bounds = getSurveyBounds(session)
        val allBssids =
            session.measurements
                .flatMap { it.snapshot.allBssids.map { bss -> bss.bssid } }
                .distinct()

        val apStatistics =
            allBssids.mapNotNull { bssid ->
                getApStatistics(session, bssid)
            }

        val avgSignal =
            apStatistics
                .map { it.averageRssiDbm }
                .average()
                .takeIf { it.isFinite() }

        return SurveySummary(
            sessionId = session.id,
            name = session.name,
            ssid = session.ssid,
            measurementCount = session.measurements.size,
            uniqueApCount = allBssids.size,
            bounds = bounds,
            quality = session.quality,
            averageSignalDbm = avgSignal?.toInt(),
            apStatistics = apStatistics,
            durationMillis = (session.endTimestamp ?: System.currentTimeMillis()) - session.startTimestamp,
        )
    }
}

/**
 * Site survey session
 *
 * @property id Unique session identifier
 * @property name Survey name
 * @property ssid Target network SSID
 * @property description Optional survey description
 * @property startTimestamp When survey started
 * @property endTimestamp When survey completed
 * @property lastUpdateTimestamp Last measurement timestamp
 * @property measurements All survey measurements
 * @property status Current survey status
 * @property quality Survey quality assessment
 */
data class SiteSurveySession(
    val id: String,
    val name: String,
    val ssid: String,
    val description: String? = null,
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val lastUpdateTimestamp: Long = startTimestamp,
    val measurements: List<SurveyMeasurement>,
    val status: SurveyStatus,
    val quality: SurveyQuality = SurveyQuality.INSUFFICIENT_DATA,
) {
    init {
        require(id.isNotBlank()) {
            "Session ID must not be blank"
        }
        require(name.isNotBlank()) {
            "Survey name must not be blank"
        }
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
        require(startTimestamp > 0) {
            "Start timestamp must be positive, got $startTimestamp"
        }
    }

    /**
     * Survey duration in milliseconds
     */
    val durationMillis: Long
        get() = (endTimestamp ?: System.currentTimeMillis()) - startTimestamp

    /**
     * Number of measurement points
     */
    val measurementCount: Int
        get() = measurements.size

    /**
     * Whether survey is complete
     */
    val isComplete: Boolean
        get() = status == SurveyStatus.COMPLETED

    /**
     * Average measurements per location
     */
    val averageMeasurementsPerLocation: Double
        get() =
            if (measurements.isNotEmpty()) {
                measurements.map { it.measurementCount }.average()
            } else {
                0.0
            }
}

/**
 * Single measurement point in site survey
 *
 * @property location Where measurement was taken
 * @property timestamp When measurement was taken
 * @property snapshot Network snapshot at this location
 * @property measurementCount Number of measurements averaged at this point
 */
data class SurveyMeasurement(
    val location: SurveyLocation,
    val timestamp: Long,
    val snapshot: ScanSnapshot,
    val measurementCount: Int = 1,
) {
    init {
        require(timestamp > 0) {
            "Timestamp must be positive, got $timestamp"
        }
        require(measurementCount >= 1) {
            "Measurement count must be >= 1, got $measurementCount"
        }
    }
}

/**
 * Survey location (normalized coordinates)
 *
 * Uses normalized coordinates (0-1) relative to survey area.
 * Can also store absolute meters from origin.
 *
 * @property x X coordinate (normalized 0-1 or meters)
 * @property y Y coordinate (normalized 0-1 or meters)
 * @property label Optional location label
 * @property floorLevel Floor level (for multi-floor surveys)
 */
data class SurveyLocation(
    val x: Double,
    val y: Double,
    val label: String? = null,
    val floorLevel: Int = 0,
) {
    init {
        require(x.isFinite()) {
            "X coordinate must be finite, got $x"
        }
        require(y.isFinite()) {
            "Y coordinate must be finite, got $y"
        }
    }

    /**
     * Calculate distance to another location (meters)
     */
    fun distanceMeters(other: SurveyLocation): Double {
        if (floorLevel != other.floorLevel) {
            return Double.MAX_VALUE // Different floors
        }
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Average location with another point
     */
    fun averageWith(other: SurveyLocation): SurveyLocation {
        require(floorLevel == other.floorLevel) {
            "Cannot average locations on different floors"
        }
        return SurveyLocation(
            x = (x + other.x) / 2.0,
            y = (y + other.y) / 2.0,
            label = label ?: other.label,
            floorLevel = floorLevel,
        )
    }

    /**
     * Check if within bounds
     */
    fun isWithin(bounds: SurveyBounds): Boolean =
        x in bounds.minX..bounds.maxX &&
            y in bounds.minY..bounds.maxY
}

/**
 * Survey area bounds
 *
 * @property minX Minimum X coordinate
 * @property maxX Maximum X coordinate
 * @property minY Minimum Y coordinate
 * @property maxY Maximum Y coordinate
 * @property widthMeters Width in meters
 * @property heightMeters Height in meters
 */
data class SurveyBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val widthMeters: Double,
    val heightMeters: Double,
) {
    /**
     * Survey area in square meters
     */
    val areaSquareMeters: Double
        get() = widthMeters * heightMeters

    /**
     * Center point of survey area
     */
    val center: SurveyLocation
        get() =
            SurveyLocation(
                x = (minX + maxX) / 2.0,
                y = (minY + maxY) / 2.0,
            )
}

/**
 * AP statistics across survey
 *
 * @property bssid Access point MAC address
 * @property measurementCount Number of locations where AP was detected
 * @property averageRssiDbm Average signal strength
 * @property minRssiDbm Weakest signal detected
 * @property maxRssiDbm Strongest signal detected
 * @property coveragePercentage Percentage of survey points with signal
 * @property locations Where this AP was detected
 */
data class ApSurveyStatistics(
    val bssid: String,
    val measurementCount: Int,
    val averageRssiDbm: Int,
    val minRssiDbm: Int,
    val maxRssiDbm: Int,
    val coveragePercentage: Double,
    val locations: List<SurveyLocation>,
) {
    /**
     * Signal strength range
     */
    val signalRangeDb: Int
        get() = maxRssiDbm - minRssiDbm

    /**
     * Whether AP provides good coverage
     */
    val hasGoodCoverage: Boolean
        get() = coveragePercentage >= 80.0 && averageRssiDbm >= -70
}

/**
 * Survey summary report
 */
data class SurveySummary(
    val sessionId: String,
    val name: String,
    val ssid: String,
    val measurementCount: Int,
    val uniqueApCount: Int,
    val bounds: SurveyBounds?,
    val quality: SurveyQuality,
    val averageSignalDbm: Int?,
    val apStatistics: List<ApSurveyStatistics>,
    val durationMillis: Long,
) {
    val summary: String
        get() =
            buildString {
                append("$name: $measurementCount measurements")
                if (bounds != null) {
                    append(", ${bounds.widthMeters.toInt()}×${bounds.heightMeters.toInt()}m")
                }
                append(", $uniqueApCount APs")
                append(", ${quality.displayName}")
            }
}

/**
 * Survey status
 */
enum class SurveyStatus(
    val displayName: String,
) {
    /** Survey in progress */
    IN_PROGRESS("In Progress"),

    /** Survey completed */
    COMPLETED("Completed"),

    /** Survey cancelled */
    CANCELLED("Cancelled"),
}

/**
 * Survey quality levels
 */
enum class SurveyQuality(
    val displayName: String,
) {
    /** Excellent survey (50+ points, high density) */
    EXCELLENT("Excellent"),

    /** Good survey (20+ points, good density) */
    GOOD("Good"),

    /** Fair survey (10+ points, adequate density) */
    FAIR("Fair"),

    /** Poor survey (5+ points, low density) */
    POOR("Poor"),

    /** Insufficient data (< 5 points) */
    INSUFFICIENT_DATA("Insufficient Data"),
    ;

    /**
     * Whether quality is acceptable for analysis
     */
    val isAcceptable: Boolean
        get() = this in listOf(EXCELLENT, GOOD, FAIR)
}
