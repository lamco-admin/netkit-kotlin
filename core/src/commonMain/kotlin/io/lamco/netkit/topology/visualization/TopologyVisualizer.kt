package io.lamco.netkit.topology.visualization

import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.model.topology.*

/**
 * Topology graph visualizer
 *
 * Generates graph data structures for visualizing WiFi network topology.
 * Supports multiple output formats suitable for common visualization libraries.
 */
class TopologyVisualizer {
    /**
     * Generate topology graph from AP cluster
     *
     * @param cluster AP cluster to visualize
     * @param includeSignalStrength Whether to include signal strength in edges
     * @return Topology graph structure
     */
    fun generateTopologyGraph(
        cluster: ApCluster,
        includeSignalStrength: Boolean = true,
    ): TopologyGraph {
        val nodes = generateNodes(cluster)
        val edges =
            if (includeSignalStrength) {
                generateSignalEdges(cluster)
            } else {
                emptyList()
            }

        return TopologyGraph(
            id = cluster.id,
            ssid = cluster.ssid,
            nodes = nodes,
            edges = edges,
            metadata =
                GraphMetadata(
                    apCount = cluster.apCount,
                    clusterDeploymentType = cluster.deploymentType,
                    supportsFastRoaming = cluster.supportsFastRoaming,
                    isTriBand = cluster.isTriBand,
                ),
        )
    }

    /**
     * Generate roaming path visualization from events
     *
     * @param events Roaming events to visualize
     * @param cluster Optional cluster for additional context
     * @return Roaming path graph
     */
    fun generateRoamingPath(
        events: List<RoamingEvent>,
        cluster: ApCluster? = null,
    ): RoamingPathGraph {
        val nodes = mutableMapOf<String, PathNode>()
        val edges = mutableListOf<PathEdge>()

        // Create nodes from unique BSSIDs in events
        events.forEach { event ->
            event.fromBssid?.let { fromBssid ->
                if (!nodes.containsKey(fromBssid)) {
                    nodes[fromBssid] =
                        PathNode(
                            id = fromBssid,
                            label = formatBssid(fromBssid),
                            isSource = true,
                        )
                }
            }
            event.toBssid?.let { toBssid ->
                if (!nodes.containsKey(toBssid)) {
                    nodes[toBssid] =
                        PathNode(
                            id = toBssid,
                            label = formatBssid(toBssid),
                            isSource = false,
                        )
                }
            }

            // Create edge (only for actual roaming events)
            val fromBssid = event.fromBssid
            val toBssid = event.toBssid
            if (fromBssid != null && toBssid != null) {
                edges.add(
                    PathEdge(
                        from = fromBssid,
                        to = toBssid,
                        timestamp = event.timestampMillis,
                        duration = event.durationMs,
                        successful = event.isSuccessful,
                        rssiImprovement = event.rssiImprovement ?: 0,
                    ),
                )
            }
        }

        return RoamingPathGraph(
            nodes = nodes.values.toList(),
            edges = edges,
            totalEvents = events.size,
            successfulEvents = events.count { it.isSuccessful },
        )
    }

    /**
     * Generate coverage heatmap visualization data
     *
     * @param heatmap Signal heatmap
     * @param colorScheme Color scheme for visualization
     * @return Heatmap visualization data
     */
    fun generateHeatmapVisualization(
        heatmap: SignalHeatmap,
        colorScheme: ColorScheme = ColorScheme.VIRIDIS,
    ): HeatmapVisualization {
        val cells = mutableListOf<HeatmapCell>()

        for (y in 0 until heatmap.gridHeight) {
            for (x in 0 until heatmap.gridWidth) {
                val rssi = heatmap.signalValues[y][x]
                val color = colorScheme.getColor(rssi)
                val quality = SignalQuality.fromRssi(rssi)

                cells.add(
                    HeatmapCell(
                        x = x,
                        y = y,
                        value = rssi,
                        color = color,
                        quality = quality,
                    ),
                )
            }
        }

        return HeatmapVisualization(
            width = heatmap.gridWidth,
            height = heatmap.gridHeight,
            cells = cells,
            bounds = heatmap.bounds,
            resolution = heatmap.gridResolution,
            colorScheme = colorScheme,
        )
    }

    /**
     * Export graph to DOT format (Graphviz)
     *
     * @param graph Topology graph to export
     * @return DOT format string
     */
    fun exportToDot(graph: TopologyGraph): String {
        val sb = StringBuilder()
        sb.appendLine("digraph \"${graph.ssid}\" {")
        sb.appendLine("  rankdir=LR;")
        sb.appendLine("  node [shape=box, style=filled];")
        sb.appendLine()

        // Add nodes
        graph.nodes.forEach { node ->
            val color =
                when (node.band) {
                    WiFiBand.BAND_2_4GHZ -> "lightblue"
                    WiFiBand.BAND_5GHZ -> "lightgreen"
                    WiFiBand.BAND_6GHZ -> "lightyellow"
                    else -> "lightgray"
                }
            sb.appendLine("  \"${node.id}\" [label=\"${node.label}\", fillcolor=\"$color\"];")
        }

        sb.appendLine()

        // Add edges
        graph.edges.forEach { edge ->
            val weight =
                if (edge.signalStrength != null) {
                    (100 + edge.signalStrength) / 10.0 // Normalize to 1-10
                } else {
                    5.0
                }
            sb.appendLine("  \"${edge.from}\" -> \"${edge.to}\" [weight=$weight];")
        }

        sb.appendLine("}")
        return sb.toString()
    }

    /**
     * Export graph to JSON format
     *
     * @param graph Topology graph to export
     * @return JSON string
     */
    fun exportToJson(graph: TopologyGraph): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"id\": \"${graph.id}\",")
        sb.appendLine("  \"ssid\": \"${graph.ssid}\",")
        sb.appendLine("  \"nodes\": [")

        graph.nodes.forEachIndexed { index, node ->
            sb.appendLine("    {")
            sb.appendLine("      \"id\": \"${node.id}\",")
            sb.appendLine("      \"label\": \"${node.label}\",")
            sb.appendLine("      \"band\": \"${node.band}\",")
            sb.appendLine("      \"rssi\": ${node.rssi},")
            sb.appendLine("      \"channel\": ${node.channel}")
            sb.append("    }")
            if (index < graph.nodes.size - 1) {
                sb.appendLine(",")
            } else {
                sb.appendLine()
            }
        }

        sb.appendLine("  ],")
        sb.appendLine("  \"edges\": [")

        graph.edges.forEachIndexed { index, edge ->
            sb.appendLine("    {")
            sb.appendLine("      \"from\": \"${edge.from}\",")
            sb.appendLine("      \"to\": \"${edge.to}\",")
            sb.appendLine("      \"signalStrength\": ${edge.signalStrength}")
            sb.append("    }")
            if (index < graph.edges.size - 1) {
                sb.appendLine(",")
            } else {
                sb.appendLine()
            }
        }

        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    // Private helper methods

    private fun generateNodes(cluster: ApCluster): List<TopologyNode> =
        cluster.bssids.map { bss ->
            TopologyNode(
                id = bss.bssid,
                label = formatBssid(bss.bssid),
                band = bss.band,
                channel = bss.channel,
                rssi = bss.rssiDbm ?: -100,
                wifiStandard = bss.wifiStandard,
                supportsFastRoaming = bss.roamingCapabilities.hasFastRoaming,
            )
        }

    private fun generateSignalEdges(cluster: ApCluster): List<TopologyEdge> {
        val edges = mutableListOf<TopologyEdge>()
        val bssList = cluster.bssids

        // Create edges between APs on same band
        for (i in bssList.indices) {
            for (j in (i + 1) until bssList.size) {
                val bss1 = bssList[i]
                val bss2 = bssList[j]

                if (bss1.band == bss2.band) {
                    // Calculate edge weight based on signal overlap
                    val rssi1 = bss1.rssiDbm ?: -100
                    val rssi2 = bss2.rssiDbm ?: -100
                    val avgSignal = (rssi1 + rssi2) / 2

                    edges.add(
                        TopologyEdge(
                            from = bss1.bssid,
                            to = bss2.bssid,
                            signalStrength = avgSignal,
                        ),
                    )
                }
            }
        }

        return edges
    }

    private fun formatBssid(bssid: String): String {
        // Shorten BSSID for display (last 3 octets)
        val parts = bssid.split(":")
        return if (parts.size >= 3) {
            parts.takeLast(3).joinToString(":")
        } else {
            bssid
        }
    }
}

/**
 * Topology graph structure
 */
data class TopologyGraph(
    val id: String,
    val ssid: String,
    val nodes: List<TopologyNode>,
    val edges: List<TopologyEdge>,
    val metadata: GraphMetadata,
)

/**
 * Topology node (AP/BSSID)
 */
data class TopologyNode(
    val id: String,
    val label: String,
    val band: WiFiBand,
    val channel: Int,
    val rssi: Int,
    val wifiStandard: WifiStandard,
    val supportsFastRoaming: Boolean,
)

/**
 * Topology edge (connection/overlap between APs)
 */
data class TopologyEdge(
    val from: String,
    val to: String,
    val signalStrength: Int?,
)

/**
 * Graph metadata
 */
data class GraphMetadata(
    val apCount: Int,
    val clusterDeploymentType: ClusterDeploymentType,
    val supportsFastRoaming: Boolean,
    val isTriBand: Boolean,
)

/**
 * Roaming path graph
 */
data class RoamingPathGraph(
    val nodes: List<PathNode>,
    val edges: List<PathEdge>,
    val totalEvents: Int,
    val successfulEvents: Int,
) {
    /** Success rate percentage */
    val successRate: Double
        get() =
            if (totalEvents > 0) {
                (successfulEvents.toDouble() / totalEvents) * 100.0
            } else {
                0.0
            }
}

/**
 * Path node in roaming graph
 */
data class PathNode(
    val id: String,
    val label: String,
    val isSource: Boolean,
)

/**
 * Path edge in roaming graph
 */
data class PathEdge(
    val from: String,
    val to: String,
    val timestamp: Long,
    val duration: Long,
    val successful: Boolean,
    val rssiImprovement: Int,
)

/**
 * Heatmap visualization data
 */
data class HeatmapVisualization(
    val width: Int,
    val height: Int,
    val cells: List<HeatmapCell>,
    val bounds: AreaBounds,
    val resolution: Double,
    val colorScheme: ColorScheme,
)

/**
 * Heatmap cell with color and quality
 */
data class HeatmapCell(
    val x: Int,
    val y: Int,
    val value: Int,
    val color: String,
    val quality: SignalQuality,
)

/**
 * Color schemes for heatmap visualization
 */
enum class ColorScheme {
    /** Viridis - perceptually uniform */
    VIRIDIS,

    /** Red-Yellow-Green gradient */
    RYG,

    /** Grayscale */
    GRAYSCALE,

    ;

    /**
     * Get color for RSSI value
     *
     * @param rssi RSSI value
     * @return Hex color code
     */
    fun getColor(rssi: Int): String =
        when (this) {
            VIRIDIS -> getViridisColor(rssi)
            RYG -> getRygColor(rssi)
            GRAYSCALE -> getGrayscaleColor(rssi)
        }

    private fun getViridisColor(rssi: Int): String =
        when {
            rssi >= -50 -> "#fde724" // Yellow (excellent)
            rssi >= -60 -> "#5ec962" // Green (good)
            rssi >= -70 -> "#21918c" // Teal (fair)
            rssi >= -85 -> "#3b528b" // Blue (poor)
            else -> "#440154" // Purple (none)
        }

    private fun getRygColor(rssi: Int): String =
        when {
            rssi >= -50 -> "#00ff00" // Green (excellent)
            rssi >= -60 -> "#7fff00" // Yellow-green (good)
            rssi >= -70 -> "#ffff00" // Yellow (fair)
            rssi >= -85 -> "#ff7f00" // Orange (poor)
            else -> "#ff0000" // Red (none)
        }

    private fun getGrayscaleColor(rssi: Int): String {
        val normalized = ((rssi + 100) / 50.0).coerceIn(0.0, 1.0)
        val gray = (normalized * 255).toInt()
        return "#%02x%02x%02x".format(gray, gray, gray)
    }
}
