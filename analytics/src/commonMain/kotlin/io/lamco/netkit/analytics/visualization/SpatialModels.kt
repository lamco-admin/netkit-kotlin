package io.lamco.netkit.analytics.visualization

/**
 * Spatial location in 2D/3D space
 *
 * Represents a physical location for spatial analysis and heatmap generation.
 *
 * @property x X-coordinate (meters or arbitrary units)
 * @property y Y-coordinate (meters or arbitrary units)
 * @property z Z-coordinate for elevation/floor (default: 0.0)
 */
data class Location(
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
) {
    init {
        require(x.isFinite()) { "X coordinate must be finite" }
        require(y.isFinite()) { "Y coordinate must be finite" }
        require(z.isFinite()) { "Z coordinate must be finite" }
    }

    /**
     * Calculate Euclidean distance to another location (2D)
     */
    fun distanceTo(other: Location): Double {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate 3D distance to another location
     */
    fun distance3DTo(other: Location): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * WiFi scan point with signal strength measurement
 *
 * Represents a single WiFi measurement at a specific location.
 *
 * @property location Physical location of measurement
 * @property rssi Signal strength in dBm
 * @property bssid Access point BSSID
 * @property timestamp Measurement timestamp (Unix epoch ms)
 */
data class ScanPoint(
    val location: Location,
    val rssi: Int,
    val bssid: String,
    val timestamp: Long,
) {
    init {
        require(rssi in -100..0) {
            "RSSI must be in range -100 to 0 dBm, got: $rssi"
        }
        require(bssid.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) {
            "Invalid BSSID format: $bssid"
        }
        require(timestamp > 0) { "Timestamp must be positive" }
    }
}

/**
 * 2D grid of values
 *
 * Generic grid structure for heatmaps and coverage maps.
 *
 * @property width Grid width in cells
 * @property height Grid height in cells
 * @property resolution Cell size (meters or units)
 * @property values Grid values (row-major order: values[y][x])
 */
data class Grid2D<T>(
    val width: Int,
    val height: Int,
    val resolution: Double,
    val values: List<List<T>>,
) {
    init {
        require(width > 0) { "Width must be positive, got: $width" }
        require(height > 0) { "Height must be positive, got: $height" }
        require(resolution > 0.0) { "Resolution must be positive, got: $resolution" }
        require(values.size == height) {
            "Values must have $height rows, got: ${values.size}"
        }
        require(values.all { it.size == width }) {
            "All rows must have $width columns"
        }
    }

    /**
     * Get value at grid coordinates
     */
    operator fun get(
        x: Int,
        y: Int,
    ): T {
        require(x in 0 until width) { "X out of bounds: $x" }
        require(y in 0 until height) { "Y out of bounds: $y" }
        return values[y][x]
    }

    /**
     * Total number of cells
     */
    val cellCount: Int = width * height

    /**
     * Physical dimensions
     */
    val physicalWidth: Double = width * resolution
    val physicalHeight: Double = height * resolution
}

/**
 * Rectangular bounds for spatial data
 *
 * @property minX Minimum X coordinate
 * @property maxX Maximum X coordinate
 * @property minY Minimum Y coordinate
 * @property maxY Maximum Y coordinate
 */
data class Bounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
) {
    init {
        require(minX <= maxX) { "minX must be <= maxX" }
        require(minY <= maxY) { "minY must be <= maxY" }
    }

    /** Width of bounds */
    val width: Double = maxX - minX

    /** Height of bounds */
    val height: Double = maxY - minY

    /** Area of bounds */
    val area: Double = width * height

    /** Center point */
    val center: Location =
        Location(
            x = (minX + maxX) / 2.0,
            y = (minY + maxY) / 2.0,
        )

    /**
     * Check if location is within bounds
     */
    fun contains(location: Location): Boolean =
        location.x >= minX &&
            location.x <= maxX &&
            location.y >= minY &&
            location.y <= maxY

    companion object {
        /**
         * Create bounds from list of locations
         */
        fun fromLocations(locations: List<Location>): Bounds {
            require(locations.isNotEmpty()) { "Cannot create bounds from empty list" }

            val xs = locations.map { it.x }
            val ys = locations.map { it.y }

            return Bounds(
                minX = xs.minOrNull()!!,
                maxX = xs.maxOrNull()!!,
                minY = ys.minOrNull()!!,
                maxY = ys.maxOrNull()!!,
            )
        }
    }
}

// ========================================
// Heatmap Models
// ========================================

/**
 * Heatmap data structure
 *
 * Represents signal strength as a 2D heatmap.
 *
 * @property grid 2D grid of signal strength values
 * @property bounds Physical bounds of the heatmap
 * @property interpolationMethod Interpolation method used
 * @property colorScale Color scale for visualization
 */
data class HeatmapData(
    val grid: Grid2D<Double>,
    val bounds: Bounds,
    val interpolationMethod: InterpolationMethod,
    val colorScale: ColorScale,
) : ChartData

/**
 * Interpolation methods for heatmap generation
 */
enum class InterpolationMethod {
    /** Nearest neighbor (simple, fast) */
    NEAREST_NEIGHBOR,

    /** Bilinear interpolation (smooth) */
    BILINEAR,

    /** Inverse Distance Weighting */
    IDW,

    /** Kriging (optimal but computationally expensive) */
    KRIGING,
}

/**
 * Color scales for heatmap visualization
 */
enum class ColorScale {
    /** Perceptually uniform blue-yellow */
    VIRIDIS,

    /** Perceptually uniform purple-yellow */
    PLASMA,

    /** Perceptually uniform black-red-yellow */
    INFERNO,

    /** Rainbow (not recommended for accessibility) */
    JET,

    /** Grayscale */
    GRAYSCALE,

    /** Red-green (for signal quality) */
    RED_GREEN,

    /** Custom color mapping */
    CUSTOM,
}

// ========================================
// Coverage Map Models
// ========================================

/**
 * Coverage map data
 *
 * Shows areas categorized by signal quality.
 *
 * @property regions Map of signal quality to coverage regions
 * @property bounds Physical bounds
 * @property totalCoveredArea Total area with coverage (m²)
 * @property qualityBreakdown Percentage breakdown by quality
 */
data class CoverageMapData(
    val regions: Map<SignalQuality, List<CoverageRegion>>,
    val bounds: Bounds,
    val totalCoveredArea: Double,
    val qualityBreakdown: Map<SignalQuality, Double>,
) : ChartData {
    init {
        require(totalCoveredArea >= 0.0) { "Total area must be non-negative" }
        require(qualityBreakdown.values.all { it >= 0.0 && it <= 100.0 }) {
            "Quality percentages must be in range 0-100"
        }
    }
}

/**
 * Coverage region polygon
 *
 * @property vertices Polygon vertices in order
 * @property quality Signal quality for this region
 * @property area Region area (m²)
 */
data class CoverageRegion(
    val vertices: List<Location>,
    val quality: SignalQuality,
    val area: Double,
) {
    init {
        require(vertices.size >= 3) {
            "Region must have at least 3 vertices, got: ${vertices.size}"
        }
        require(area >= 0.0) { "Area must be non-negative" }
    }
}

/**
 * Signal quality levels
 */
enum class SignalQuality(
    val displayName: String,
    val color: String,
) {
    EXCELLENT("Excellent", "#4CAF50"), // Green
    GOOD("Good", "#8BC34A"), // Light green
    FAIR("Fair", "#FFC107"), // Amber
    POOR("Poor", "#FF9800"), // Orange
    VERY_POOR("Very Poor", "#F44336"), // Red
    NO_SIGNAL("No Signal", "#9E9E9E"), // Gray
    ;

    companion object {
        /**
         * Determine signal quality from RSSI value
         */
        fun fromRssi(rssi: Int): SignalQuality =
            when {
                rssi >= -50 -> EXCELLENT
                rssi >= -60 -> GOOD
                rssi >= -70 -> FAIR
                rssi >= -80 -> POOR
                rssi >= -90 -> VERY_POOR
                else -> NO_SIGNAL
            }
    }
}

// ========================================
// Network Topology Models
// ========================================

/**
 * Network topology graph data
 *
 * @property nodes Network nodes (APs, clients, routers)
 * @property edges Connections between nodes
 * @property layout Graph layout algorithm used
 */
data class GraphData(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val layout: GraphLayout,
) : ChartData {
    init {
        require(nodes.isNotEmpty()) { "Graph must have at least one node" }
    }
}

/**
 * Graph node (network device)
 *
 * @property id Unique node identifier
 * @property label Display label
 * @property type Node type (AP, client, etc.)
 * @property position Optional fixed position
 * @property size Visual size
 * @property color Color hex code
 * @property metadata Additional node properties
 */
data class GraphNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val position: Position? = null,
    val size: Double = 1.0,
    val color: String = "#2196F3",
    val metadata: Map<String, Any> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "Node ID cannot be blank" }
        require(label.isNotBlank()) { "Node label cannot be blank" }
        require(size > 0.0) { "Node size must be positive" }
    }
}

/**
 * 2D position for graph nodes
 */
data class Position(
    val x: Double,
    val y: Double,
)

/**
 * Network node types
 */
enum class NodeType(
    val displayName: String,
    val defaultColor: String,
) {
    ACCESS_POINT("Access Point", "#2196F3"), // Blue
    ROUTER("Router", "#FF9800"), // Orange
    CLIENT("Client", "#4CAF50"), // Green
    SWITCH("Switch", "#9C27B0"), // Purple
    GATEWAY("Gateway", "#F44336"), // Red
}

/**
 * Graph edge (connection)
 *
 * @property from Source node ID
 * @property to Target node ID
 * @property type Edge type
 * @property weight Optional weight (e.g., signal strength)
 * @property label Optional label
 * @property style Visual style
 */
data class GraphEdge(
    val from: String,
    val to: String,
    val type: EdgeType,
    val weight: Double? = null,
    val label: String? = null,
    val style: EdgeStyle = EdgeStyle.SOLID,
) {
    init {
        require(from.isNotBlank()) { "Source node ID cannot be blank" }
        require(to.isNotBlank()) { "Target node ID cannot be blank" }
    }
}

/**
 * Edge types for network topology
 */
enum class EdgeType {
    /** Client roaming path */
    ROAMING_PATH,

    /** Wired backhaul link */
    BACKHAUL_LINK,

    /** Wireless client connection */
    CLIENT_CONNECTION,

    /** Logical cluster grouping */
    LOGICAL_CLUSTER,
}

/**
 * Edge visual styles
 */
enum class EdgeStyle {
    SOLID,
    DASHED,
    DOTTED,
}

/**
 * Graph layout algorithms
 */
enum class GraphLayout {
    /** Force-directed layout */
    FORCE_DIRECTED,

    /** Hierarchical tree layout */
    HIERARCHICAL,

    /** Circular layout */
    CIRCULAR,

    /** Manual/fixed positions */
    MANUAL,
}

/**
 * Roaming path graph data
 *
 * @property path Ordered list of nodes representing roaming path
 * @property events Roaming events with timing
 * @property totalDuration Total roaming path duration (ms)
 */
data class PathGraphData(
    val path: List<GraphNode>,
    val events: List<RoamingEvent>,
    val totalDuration: Long,
) : ChartData {
    init {
        require(path.isNotEmpty()) { "Path must have at least one node" }
        require(totalDuration >= 0) { "Total duration must be non-negative" }
    }
}

/**
 * Roaming event during client movement
 *
 * @property timestamp Event timestamp
 * @property fromBssid Source AP BSSID
 * @property toBssid Target AP BSSID
 * @property reason Roaming reason
 * @property duration Roaming duration (ms)
 */
data class RoamingEvent(
    val timestamp: Long,
    val fromBssid: String,
    val toBssid: String,
    val reason: RoamingReason,
    val duration: Long,
) {
    init {
        require(timestamp > 0) { "Timestamp must be positive" }
        require(duration >= 0) { "Duration must be non-negative" }
    }
}

/**
 * Reasons for roaming
 */
enum class RoamingReason {
    LOW_RSSI,
    BETTER_AP_AVAILABLE,
    LOAD_BALANCING,
    AP_DISCONNECT,
    CLIENT_INITIATED,
    UNKNOWN,
}

// ========================================
// Channel Diagram Models
// ========================================

/**
 * Channel allocation diagram data
 *
 * Visualizes channel usage across WiFi bands.
 *
 * @property band WiFi band (2.4GHz, 5GHz, 6GHz)
 * @property channels Channel allocations
 * @property overlaps Channel overlap regions
 */
data class ChannelDiagramData(
    val band: WiFiBand,
    val channels: List<ChannelAllocation>,
    val overlaps: List<ChannelOverlap> = emptyList(),
) : ChartData {
    init {
        require(channels.isNotEmpty()) { "Channel diagram requires at least one channel" }
    }
}

/**
 * WiFi frequency bands
 */
enum class WiFiBand(
    val displayName: String,
    val frequencyRange: IntRange,
    val channelWidth: Int,
) {
    BAND_2_4GHZ("2.4 GHz", 2400..2500, 20),
    BAND_5GHZ("5 GHz", 5150..5850, 20),
    BAND_6GHZ("6 GHz", 5925..7125, 20),
}

/**
 * Channel allocation for a network
 *
 * @property channel Channel number
 * @property centerFrequency Center frequency (MHz)
 * @property width Channel width (MHz)
 * @property ssid Network SSID
 * @property bssid Network BSSID
 * @property utilization Channel utilization (0-100%)
 */
data class ChannelAllocation(
    val channel: Int,
    val centerFrequency: Int,
    val width: Int,
    val ssid: String,
    val bssid: String,
    val utilization: Double,
) {
    init {
        require(channel > 0) { "Channel must be positive" }
        require(centerFrequency > 0) { "Frequency must be positive" }
        require(width in listOf(20, 40, 80, 160)) {
            "Width must be 20, 40, 80, or 160 MHz"
        }
        require(utilization in 0.0..100.0) {
            "Utilization must be in range 0-100%"
        }
    }
}

/**
 * Channel overlap region
 *
 * @property channels Overlapping channels
 * @property severity Overlap severity (0-100%)
 */
data class ChannelOverlap(
    val channels: List<Int>,
    val severity: Double,
) {
    init {
        require(channels.size >= 2) { "Overlap requires at least 2 channels" }
        require(severity in 0.0..100.0) {
            "Severity must be in range 0-100%"
        }
    }
}

/**
 * Network information for channel diagrams
 *
 * @property ssid Network SSID
 * @property bssid Network BSSID
 * @property channel Primary channel
 * @property channelWidth Channel width (MHz)
 * @property rssi Signal strength
 */
data class NetworkInfo(
    val ssid: String,
    val bssid: String,
    val channel: Int,
    val channelWidth: Int,
    val rssi: Int,
)
