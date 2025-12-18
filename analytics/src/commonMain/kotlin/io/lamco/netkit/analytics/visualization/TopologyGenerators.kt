package io.lamco.netkit.analytics.visualization

/**
 * Network topology diagram generator
 *
 * Generates network topology graphs showing relationships between
 * access points, clients, routers, and other network devices.
 *
 * ## Usage Example
 * ```kotlin
 * val generator = TopologyDiagramGenerator()
 *
 * val topology = generator.generateTopologyGraph(
 *     clusters = apClusters,
 *     config = TopologyGraphConfig()
 * )
 * ```
 */
class TopologyDiagramGenerator {
    /**
     * Generate topology graph from AP clusters
     *
     * @param nodes List of network nodes
     * @param edges List of connections between nodes
     * @param config Graph configuration
     * @return Graph data ready for visualization
     */
    fun generateTopologyGraph(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        config: TopologyGraphConfig = TopologyGraphConfig(),
    ): GraphData {
        require(nodes.isNotEmpty()) { "Nodes cannot be empty" }

        // Validate edges reference existing nodes
        val nodeIds = nodes.map { it.id }.toSet()
        edges.forEach { edge ->
            require(edge.from in nodeIds) { "Edge references non-existent source node: ${edge.from}" }
            require(edge.to in nodeIds) { "Edge references non-existent target node: ${edge.to}" }
        }

        return GraphData(
            nodes = nodes,
            edges = edges,
            layout = config.layout,
        )
    }

    /**
     * Generate roaming path graph from roaming events
     *
     * Visualizes client movement between access points.
     *
     * @param roamingEvents List of roaming events in chronological order
     * @return Path graph data
     */
    fun generateRoamingPathGraph(roamingEvents: List<RoamingEvent>): PathGraphData {
        require(roamingEvents.isNotEmpty()) { "Roaming events cannot be empty" }

        // Create nodes for each unique BSSID
        val bssids = roamingEvents.flatMap { listOf(it.fromBssid, it.toBssid) }.distinct()
        val nodes =
            bssids.mapIndexed { index, bssid ->
                GraphNode(
                    id = bssid,
                    label = "AP ${index + 1}",
                    type = NodeType.ACCESS_POINT,
                    position = Position(index.toDouble() * 100.0, 0.0),
                )
            }

        val totalDuration = roamingEvents.sumOf { it.duration }

        return PathGraphData(
            path = nodes,
            events = roamingEvents,
            totalDuration = totalDuration,
        )
    }

    /**
     * Create simple network graph from node and edge counts
     *
     * Useful for testing or visualization previews.
     *
     * @param nodeCount Number of nodes to create
     * @param edgeCount Number of edges to create
     * @return Simple graph data
     */
    fun createSimpleGraph(
        nodeCount: Int,
        edgeCount: Int,
    ): GraphData {
        require(nodeCount > 0) { "Node count must be positive" }
        require(edgeCount >= 0) { "Edge count must be non-negative" }
        require(edgeCount <= nodeCount * (nodeCount - 1)) {
            "Edge count cannot exceed maximum possible edges"
        }

        val nodes =
            (1..nodeCount).map { i ->
                GraphNode(
                    id = "node$i",
                    label = "Node $i",
                    type = NodeType.ACCESS_POINT,
                )
            }

        val edges =
            (1..edgeCount).map { i ->
                val from = "node${((i - 1) % nodeCount) + 1}"
                val to = "node${(i % nodeCount) + 1}"
                GraphEdge(
                    from = from,
                    to = to,
                    type = EdgeType.BACKHAUL_LINK,
                )
            }

        return GraphData(
            nodes = nodes,
            edges = edges,
            layout = GraphLayout.FORCE_DIRECTED,
        )
    }
}

/**
 * Channel allocation diagram generator
 *
 * Generates channel allocation visualizations showing which networks
 * are using which channels and identifying potential interference.
 *
 * ## Usage Example
 * ```kotlin
 * val generator = ChannelDiagramGenerator()
 *
 * val diagram = generator.generateChannelDiagram(
 *     band = WiFiBand.BAND_2_4GHZ,
 *     networks = networkList
 * )
 * ```
 */
class ChannelDiagramGenerator {
    /**
     * Generate channel allocation diagram
     *
     * @param band WiFi band (2.4GHz, 5GHz, 6GHz)
     * @param networks List of networks with channel information
     * @param config Diagram configuration
     * @return Channel diagram data
     */
    fun generateChannelDiagram(
        band: WiFiBand,
        networks: List<NetworkInfo>,
        config: ChannelDiagramConfig = ChannelDiagramConfig(),
    ): ChannelDiagramData {
        require(networks.isNotEmpty()) { "Networks cannot be empty" }

        // Convert NetworkInfo to ChannelAllocation
        val channels =
            networks.map { network ->
                ChannelAllocation(
                    channel = network.channel,
                    centerFrequency = calculateCenterFrequency(band, network.channel),
                    width = network.channelWidth,
                    ssid = network.ssid,
                    bssid = network.bssid,
                    utilization = estimateUtilization(network.rssi),
                )
            }

        // Detect overlaps
        val overlaps =
            if (config.showOverlaps) {
                detectChannelOverlaps(channels)
            } else {
                emptyList()
            }

        return ChannelDiagramData(
            band = band,
            channels = channels,
            overlaps = overlaps,
        )
    }

    /**
     * Find optimal channel for a new network
     *
     * Analyzes existing channel allocations and recommends least congested channel.
     *
     * @param band WiFi band
     * @param existingNetworks Current networks
     * @return Recommended channel number
     */
    fun findOptimalChannel(
        band: WiFiBand,
        existingNetworks: List<NetworkInfo>,
    ): Int {
        val availableChannels =
            when (band) {
                WiFiBand.BAND_2_4GHZ -> listOf(1, 6, 11) // Non-overlapping channels
                WiFiBand.BAND_5GHZ -> (36..165 step 4).toList()
                WiFiBand.BAND_6GHZ -> (1..233 step 4).toList()
            }

        if (existingNetworks.isEmpty()) {
            return availableChannels.first()
        }

        // Count networks per channel
        val channelCounts = existingNetworks.groupingBy { it.channel }.eachCount()

        // Find channel with minimum networks
        return availableChannels.minByOrNull { channelCounts[it] ?: 0 }
            ?: availableChannels.first()
    }

    /**
     * Calculate channel utilization percentage
     *
     * @param networks Networks on a specific channel
     * @return Utilization percentage (0-100)
     */
    fun calculateChannelUtilization(networks: List<NetworkInfo>): Double {
        if (networks.isEmpty()) return 0.0

        // Simplified utilization based on network count and signal strengths
        val baseUtil = (networks.size * 10.0).coerceAtMost(50.0)
        val signalUtil = networks.map { (it.rssi + 100) / 100.0 * 50.0 }.average()

        return (baseUtil + signalUtil).coerceIn(0.0, 100.0)
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Calculate center frequency for a channel
     */
    private fun calculateCenterFrequency(
        band: WiFiBand,
        channel: Int,
    ): Int =
        when (band) {
            WiFiBand.BAND_2_4GHZ -> 2407 + (channel * 5)
            WiFiBand.BAND_5GHZ -> 5000 + (channel * 5)
            WiFiBand.BAND_6GHZ -> 5950 + (channel * 5)
        }

    /**
     * Estimate utilization from RSSI
     */
    private fun estimateUtilization(rssi: Int): Double {
        // Stronger signal = higher utilization estimate
        return ((rssi + 100) / 100.0 * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Detect channel overlaps
     */
    private fun detectChannelOverlaps(channels: List<ChannelAllocation>): List<ChannelOverlap> {
        val overlaps = mutableListOf<ChannelOverlap>()

        for (i in channels.indices) {
            for (j in (i + 1) until channels.size) {
                val ch1 = channels[i]
                val ch2 = channels[j]

                // Check if channels overlap based on center frequency and width
                val ch1Min = ch1.centerFrequency - ch1.width / 2
                val ch1Max = ch1.centerFrequency + ch1.width / 2
                val ch2Min = ch2.centerFrequency - ch2.width / 2
                val ch2Max = ch2.centerFrequency + ch2.width / 2

                val hasOverlap = (ch1Min <= ch2Max && ch2Min <= ch1Max)

                if (hasOverlap) {
                    // Calculate overlap severity (simplified)
                    val overlapRange = minOf(ch1Max, ch2Max) - maxOf(ch1Min, ch2Min)
                    val severity = (overlapRange.toDouble() / ch1.width * 100.0).coerceIn(0.0, 100.0)

                    overlaps.add(
                        ChannelOverlap(
                            channels = listOf(ch1.channel, ch2.channel),
                            severity = severity,
                        ),
                    )
                }
            }
        }

        return overlaps.distinct()
    }
}
