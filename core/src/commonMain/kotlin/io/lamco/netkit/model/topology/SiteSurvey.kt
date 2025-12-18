package io.lamco.netkit.model.topology

import io.lamco.netkit.logging.NetKit

/**
 * Geographic coordinates (latitude/longitude)
 *
 * Used for outdoor or GPS-based site surveys. Optional since:
 * - Requires location permission from user
 * - Not always relevant (indoor surveys use local coordinates)
 * - Privacy-sensitive data
 *
 * @property latitude Latitude in degrees (-90 to +90)
 * @property longitude Longitude in degrees (-180 to +180)
 * @property accuracyMeters GPS accuracy in meters
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
) {
    init {
        require(latitude in -90.0..90.0) {
            "Latitude must be in range [-90, +90], got $latitude"
        }
        require(longitude in -180.0..180.0) {
            "Longitude must be in range [-180, +180], got $longitude"
        }
        if (accuracyMeters != null) {
            require(accuracyMeters >= 0.0) {
                "Accuracy must be non-negative, got $accuracyMeters"
            }
        }
    }

    /**
     * Whether this is a high-accuracy GPS fix (<= 10m)
     */
    val isHighAccuracy: Boolean
        get() = accuracyMeters != null && accuracyMeters <= 10.0

    /**
     * Whether this is a low-accuracy GPS fix (> 50m)
     */
    val isLowAccuracy: Boolean
        get() = accuracyMeters != null && accuracyMeters > 50.0

    /**
     * Human-readable coordinate string
     */
    val coordinateString: String
        get() = String.format("%.6f, %.6f", latitude, longitude)

    /**
     * Calculate distance to another point in meters (Haversine formula)
     */
    fun distanceToMeters(other: GeoPoint): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)

        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)

        val a =
            kotlin.math.sin(dLat / 2) *
                kotlin.math.sin(dLat / 2) +
                kotlin.math.sin(dLon / 2) *
                kotlin.math.sin(dLon / 2) *
                kotlin.math.cos(lat1) *
                kotlin.math.cos(lat2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadiusKm * c * 1000.0 // Convert to meters
    }

    companion object {
        /**
         * Create GeoPoint from coordinate string "lat, lon"
         */
        fun fromString(coordString: String): GeoPoint? {
            val parts = coordString.split(",").map { it.trim() }
            if (parts.size != 2) return null

            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null

            return try {
                GeoPoint(lat, lon)
            } catch (e: IllegalArgumentException) {
                // GeoPoint validation failed (lat/lon out of range)
                NetKit.logger.warn("Failed to parse GeoPoint from string: $coordString", e)
                null
            }
        }
    }
}

/**
 * Local survey coordinates (relative positioning)
 *
 * Used for indoor site surveys where absolute GPS coordinates are not
 * available or practical. Coordinates are normalized to [0, 1] range
 * and mapped to a floor plan or area.
 *
 * This allows:
 * - Privacy-preserving surveys (no GPS required)
 * - Indoor positioning where GPS doesn't work
 * - User-defined measurement grids
 *
 * @property x X coordinate (0.0 to 1.0, typically left to right)
 * @property y Y coordinate (0.0 to 1.0, typically top to bottom)
 * @property floorLevel Floor level (0 = ground, 1 = first floor, etc.)
 * @property referenceName Optional reference point name (e.g., "Living Room Corner")
 */
data class LocalSurveyCoord(
    val x: Double,
    val y: Double,
    val floorLevel: Int = 0,
    val referenceName: String? = null,
) {
    init {
        require(x in 0.0..1.0) {
            "X coordinate must be in range [0, 1], got $x"
        }
        require(y in 0.0..1.0) {
            "Y coordinate must be in range [0, 1], got $y"
        }
    }

    /**
     * Distance to another local coordinate point (normalized 0-1 range)
     */
    fun distanceTo(other: LocalSurveyCoord): Double {
        if (floorLevel != other.floorLevel) {
            // Different floors - include vertical separation
            val horizontalDist =
                kotlin.math.sqrt(
                    (other.x - x) *
                        (other.x - x) +
                        (other.y - y) *
                        (other.y - y),
                )
            val verticalDist = kotlin.math.abs(other.floorLevel - floorLevel) * 0.3 // Floor height factor
            return kotlin.math.sqrt(horizontalDist * horizontalDist + verticalDist * verticalDist)
        } else {
            // Same floor - simple Euclidean distance
            return kotlin.math.sqrt(
                (other.x - x) *
                    (other.x - x) +
                    (other.y - y) *
                    (other.y - y),
            )
        }
    }

    /**
     * Whether this coordinate is in the center region (0.4-0.6 on both axes)
     */
    val isCenter: Boolean
        get() = x in 0.4..0.6 && y in 0.4..0.6

    /**
     * Whether this coordinate is on the edge (within 0.1 of boundary)
     */
    val isEdge: Boolean
        get() = x <= 0.1 || x >= 0.9 || y <= 0.1 || y >= 0.9

    /**
     * Quadrant of this coordinate (NW, NE, SW, SE)
     */
    val quadrant: Quadrant
        get() =
            when {
                x < 0.5 && y < 0.5 -> Quadrant.NORTH_WEST
                x >= 0.5 && y < 0.5 -> Quadrant.NORTH_EAST
                x < 0.5 && y >= 0.5 -> Quadrant.SOUTH_WEST
                else -> Quadrant.SOUTH_EAST
            }

    /**
     * Convert to pixel coordinates given image dimensions
     */
    fun toPixelCoordinates(
        widthPx: Int,
        heightPx: Int,
    ): Pair<Int, Int> =
        Pair(
            (x * widthPx).toInt().coerceIn(0, widthPx - 1),
            (y * heightPx).toInt().coerceIn(0, heightPx - 1),
        )

    companion object {
        /**
         * Create coordinate from pixel position and image dimensions
         */
        fun fromPixelCoordinates(
            pixelX: Int,
            pixelY: Int,
            widthPx: Int,
            heightPx: Int,
            floorLevel: Int = 0,
        ): LocalSurveyCoord =
            LocalSurveyCoord(
                x = (pixelX.toDouble() / widthPx).coerceIn(0.0, 1.0),
                y = (pixelY.toDouble() / heightPx).coerceIn(0.0, 1.0),
                floorLevel = floorLevel,
            )
    }
}

/**
 * Coordinate quadrants
 */
enum class Quadrant {
    NORTH_WEST,
    NORTH_EAST,
    SOUTH_WEST,
    SOUTH_EAST,
}

/**
 * Site survey measurement point
 *
 * Captures WiFi conditions at a specific location for heatmap generation
 * and coverage analysis.
 *
 * @property measurementId Unique identifier for this measurement
 * @property timestampMillis When this measurement was taken
 * @property userProvidedLabel Optional user label (e.g., "Living Room", "Bedroom 2")
 * @property geoPoint GPS coordinates (if available and user consented)
 * @property localCoordinates Local survey coordinates (for indoor mapping)
 * @property visibleBssids BSSIDs visible at this location with their RSSI
 * @property connectedBssid Which BSSID was connected (if any)
 * @property connectedRssiDbm RSSI of connected AP
 * @property downloadSpeedMbps Download speed test result (if measured)
 * @property uploadSpeedMbps Upload speed test result (if measured)
 * @property latencyMs Latency to internet (if measured)
 */
data class SurveyMeasurement(
    val measurementId: String,
    val timestampMillis: Long,
    val userProvidedLabel: String? = null,
    val geoPoint: GeoPoint? = null,
    val localCoordinates: LocalSurveyCoord? = null,
    val visibleBssids: Map<String, Int>, // BSSID -> RSSI (dBm)
    val connectedBssid: String? = null,
    val connectedRssiDbm: Int? = null,
    val downloadSpeedMbps: Double? = null,
    val uploadSpeedMbps: Double? = null,
    val latencyMs: Int? = null,
) {
    init {
        require(measurementId.isNotBlank()) {
            "Measurement ID must not be blank"
        }
        require(geoPoint != null || localCoordinates != null) {
            "Either geographic or local coordinates must be provided"
        }
        require(visibleBssids.isNotEmpty()) {
            "Must have at least one visible BSSID"
        }
        visibleBssids.values.forEach { rssi ->
            require(rssi in -120..0) {
                "RSSI must be in range [-120, 0] dBm, got $rssi"
            }
        }
        if (connectedRssiDbm != null) {
            require(connectedRssiDbm in -120..0) {
                "Connected RSSI must be in range [-120, 0] dBm, got $connectedRssiDbm"
            }
        }
    }

    /**
     * Number of visible APs
     */
    val visibleApCount: Int
        get() = visibleBssids.size

    /**
     * Strongest RSSI among visible APs
     */
    val strongestRssiDbm: Int
        get() = visibleBssids.values.maxOrNull() ?: -120

    /**
     * Weakest RSSI among visible APs
     */
    val weakestRssiDbm: Int
        get() = visibleBssids.values.minOrNull() ?: -120

    /**
     * Average RSSI across all visible APs
     */
    val averageRssiDbm: Double
        get() = visibleBssids.values.average()

    /**
     * Whether device was connected during this measurement
     */
    val wasConnected: Boolean
        get() = connectedBssid != null

    /**
     * Whether performance tests were run
     */
    val hasPerformanceData: Boolean
        get() = downloadSpeedMbps != null || uploadSpeedMbps != null || latencyMs != null

    /**
     * Coverage quality at this location
     */
    val coverageQuality: CoverageQuality
        get() =
            when {
                strongestRssiDbm >= -50 -> CoverageQuality.EXCELLENT
                strongestRssiDbm >= -60 -> CoverageQuality.VERY_GOOD
                strongestRssiDbm >= -70 -> CoverageQuality.GOOD
                strongestRssiDbm >= -80 -> CoverageQuality.FAIR
                strongestRssiDbm >= -90 -> CoverageQuality.POOR
                else -> CoverageQuality.DEAD_ZONE
            }

    /**
     * Whether this location is a dead zone (very poor coverage)
     */
    val isDeadZone: Boolean
        get() = coverageQuality == CoverageQuality.DEAD_ZONE

    /**
     * Location description (label, coordinates, or ID)
     */
    val locationDescription: String
        get() =
            userProvidedLabel
                ?: localCoordinates?.referenceName
                ?: geoPoint?.coordinateString
                ?: measurementId

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append(locationDescription)
                append(" - $visibleApCount APs visible")
                append(", best: ${strongestRssiDbm}dBm")
                if (wasConnected) {
                    append(", connected: ${connectedRssiDbm}dBm")
                }
            }
}

/**
 * Coverage quality categories for site survey
 */
enum class CoverageQuality(
    val displayName: String,
    val minRssiDbm: Int,
    val colorHex: String,
) {
    /** Excellent coverage (>= -50 dBm) */
    EXCELLENT("Excellent", -50, "#00FF00"),

    /** Very good coverage (-60 to -50 dBm) */
    VERY_GOOD("Very Good", -60, "#7FFF00"),

    /** Good coverage (-70 to -60 dBm) */
    GOOD("Good", -70, "#FFFF00"),

    /** Fair coverage (-80 to -70 dBm) */
    FAIR("Fair", -80, "#FFA500"),

    /** Poor coverage (-90 to -80 dBm) */
    POOR("Poor", -90, "#FF4500"),

    /** Dead zone (< -90 dBm) */
    DEAD_ZONE("Dead Zone", -120, "#FF0000"),
    ;

    /**
     * Whether this coverage quality is acceptable
     */
    val isAcceptable: Boolean
        get() = this in listOf(EXCELLENT, VERY_GOOD, GOOD)
}
