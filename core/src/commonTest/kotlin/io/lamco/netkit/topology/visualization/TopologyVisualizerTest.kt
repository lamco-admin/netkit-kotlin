package io.lamco.netkit.topology.visualization

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class TopologyVisualizerTest {
    @Test
    fun `generateTopologyGraph creates valid graph`() {
        val visualizer = TopologyVisualizer()
        val cluster = createTestCluster()

        val graph = visualizer.generateTopologyGraph(cluster)

        assertEquals(cluster.id, graph.id)
        assertEquals(cluster.ssid, graph.ssid)
        assertEquals(3, graph.nodes.size)
        assertNotNull(graph.metadata)
    }

    @Test
    fun `generateTopologyGraph includes signal strength edges`() {
        val visualizer = TopologyVisualizer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AP1", band = WiFiBand.BAND_5GHZ),
                    createBss("AP2", band = WiFiBand.BAND_5GHZ),
                ),
            )

        val graph = visualizer.generateTopologyGraph(cluster, includeSignalStrength = true)

        assertTrue(graph.edges.isNotEmpty())
    }

    @Test
    fun `generateTopologyGraph excludes edges when requested`() {
        val visualizer = TopologyVisualizer()
        val cluster = createTestCluster()

        val graph = visualizer.generateTopologyGraph(cluster, includeSignalStrength = false)

        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `generateTopologyGraph creates edges only for same-band APs`() {
        val visualizer = TopologyVisualizer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AP1", band = WiFiBand.BAND_2_4GHZ),
                    createBss("AP2", band = WiFiBand.BAND_5GHZ),
                    createBss("AP3", band = WiFiBand.BAND_5GHZ),
                ),
            )

        val graph = visualizer.generateTopologyGraph(cluster, includeSignalStrength = true)

        // Should only have edge between AP2 and AP3 (both 5 GHz)
        assertEquals(1, graph.edges.size)
    }

    @Test
    fun `TopologyNode includes all required fields`() {
        val visualizer = TopologyVisualizer()
        val cluster = createTestCluster()
        val graph = visualizer.generateTopologyGraph(cluster)

        val node = graph.nodes.first()
        assertNotNull(node.id)
        assertNotNull(node.label)
        assertNotNull(node.band)
        assertTrue(node.channel > 0)
        assertTrue(node.rssi <= 0)
    }

    @Test
    fun `GraphMetadata captures cluster properties`() {
        val visualizer = TopologyVisualizer()
        val cluster =
            createClusterWithBssids(
                List(5) { createBss("AP$it", roaming = true) },
            )

        val graph = visualizer.generateTopologyGraph(cluster)

        assertEquals(5, graph.metadata.apCount)
        assertTrue(graph.metadata.supportsFastRoaming)
    }

    @Test
    fun `generateRoamingPath creates nodes from events`() {
        val visualizer = TopologyVisualizer()
        val events =
            listOf(
                createRoamingEvent("AP1", "AP2"),
                createRoamingEvent("AP2", "AP3"),
            )

        val pathGraph = visualizer.generateRoamingPath(events)

        assertEquals(3, pathGraph.nodes.size) // AP1, AP2, AP3
        assertEquals(2, pathGraph.edges.size)
    }

    @Test
    fun `generateRoamingPath calculates success rate`() {
        val visualizer = TopologyVisualizer()
        val events =
            listOf(
                createRoamingEvent("AP1", "AP2", successful = true),
                createRoamingEvent("AP2", "AP3", successful = true),
                createRoamingEvent("AP3", "AP1", successful = false),
            )

        val pathGraph = visualizer.generateRoamingPath(events)

        assertEquals(3, pathGraph.totalEvents)
        assertEquals(2, pathGraph.successfulEvents)
        assertEquals(66.666, pathGraph.successRate, 0.1)
    }

    @Test
    fun `generateRoamingPath handles empty events`() {
        val visualizer = TopologyVisualizer()
        val pathGraph = visualizer.generateRoamingPath(emptyList())

        assertTrue(pathGraph.nodes.isEmpty())
        assertTrue(pathGraph.edges.isEmpty())
        assertEquals(0, pathGraph.totalEvents)
        assertEquals(0.0, pathGraph.successRate)
    }

    @Test
    fun `PathEdge includes event details`() {
        val visualizer = TopologyVisualizer()
        val events =
            listOf(
                createRoamingEvent("AP1", "AP2", successful = true, durationMs = 50),
            )

        val pathGraph = visualizer.generateRoamingPath(events)
        val edge = pathGraph.edges.first()

        assertEquals("AP1", edge.from)
        assertEquals("AP2", edge.to)
        assertEquals(50L, edge.duration)
        assertTrue(edge.successful)
    }

    @Test
    fun `generateHeatmapVisualization creates cells`() {
        val visualizer = TopologyVisualizer()
        val mapper = SignalStrengthMapper(gridResolution = 5.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -60), // Normalized coords [0-1]
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)
        val heatmap = mapper.generateHeatmap(measurements, bounds)

        val viz = visualizer.generateHeatmapVisualization(heatmap)

        assertEquals(heatmap.gridWidth, viz.width)
        assertEquals(heatmap.gridHeight, viz.height)
        assertTrue(viz.cells.isNotEmpty())
    }

    @Test
    fun `HeatmapCell includes color and quality`() {
        val visualizer = TopologyVisualizer()
        val mapper = SignalStrengthMapper(gridResolution = 5.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -55), // Normalized coords [0-1]
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)
        val heatmap = mapper.generateHeatmap(measurements, bounds)

        val viz = visualizer.generateHeatmapVisualization(heatmap)
        val cell = viz.cells.first()

        assertNotNull(cell.color)
        assertTrue(cell.color.startsWith("#"))
        assertNotNull(cell.quality)
    }

    @Test
    fun `ColorScheme provides valid hex colors`() {
        val rssiValues = listOf(-45, -55, -65, -75, -90)

        rssiValues.forEach { rssi ->
            val viridis = ColorScheme.VIRIDIS.getColor(rssi)
            val ryg = ColorScheme.RYG.getColor(rssi)
            val gray = ColorScheme.GRAYSCALE.getColor(rssi)

            assertTrue(viridis.matches(Regex("#[0-9a-f]{6}")))
            assertTrue(ryg.matches(Regex("#[0-9a-f]{6}")))
            assertTrue(gray.matches(Regex("#[0-9a-f]{6}")))
        }
    }

    @Test
    fun `ColorScheme differentiates signal quality`() {
        val excellent = ColorScheme.VIRIDIS.getColor(-45)
        val poor = ColorScheme.VIRIDIS.getColor(-90)

        assertNotEquals(excellent, poor)
    }

    @Test
    fun `exportToDot generates valid Graphviz format`() {
        val visualizer = TopologyVisualizer()
        val cluster = createTestCluster()
        val graph = visualizer.generateTopologyGraph(cluster)

        val dot = visualizer.exportToDot(graph)

        assertTrue(dot.startsWith("digraph"))
        assertTrue(dot.contains("rankdir"))
        assertTrue(dot.contains("node"))
        assertTrue(dot.trimEnd().endsWith("}")) // trimEnd() for trailing newline
    }

    @Test
    fun `exportToDot includes all nodes`() {
        val visualizer = TopologyVisualizer()
        val cluster = createTestCluster()
        val graph = visualizer.generateTopologyGraph(cluster)

        val dot = visualizer.exportToDot(graph)

        graph.nodes.forEach { node ->
            assertTrue(dot.contains(node.id))
        }
    }

    @Test
    fun `exportToDot colors nodes by band`() {
        val visualizer = TopologyVisualizer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AP1", band = WiFiBand.BAND_2_4GHZ),
                    createBss("AP2", band = WiFiBand.BAND_5GHZ),
                ),
            )
        val graph = visualizer.generateTopologyGraph(cluster, includeSignalStrength = false)

        val dot = visualizer.exportToDot(graph)

        assertTrue(dot.contains("fillcolor"))
        assertTrue(dot.contains("lightblue") || dot.contains("lightgreen"))
    }

    @Test
    fun `exportToJson generates valid JSON structure`() {
        val visualizer = TopologyVisualizer()
        val cluster = createTestCluster()
        val graph = visualizer.generateTopologyGraph(cluster)

        val json = visualizer.exportToJson(graph)

        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("\"ssid\""))
        assertTrue(json.contains("\"nodes\""))
        assertTrue(json.contains("\"edges\""))
        assertTrue(json.trim().startsWith("{"))
        assertTrue(json.trim().endsWith("}")) // Handle potential whitespace
    }

    @Test
    fun `exportToJson includes node properties`() {
        val visualizer = TopologyVisualizer()
        val cluster = createTestCluster()
        val graph = visualizer.generateTopologyGraph(cluster)

        val json = visualizer.exportToJson(graph)

        assertTrue(json.contains("\"band\""))
        assertTrue(json.contains("\"rssi\""))
        assertTrue(json.contains("\"channel\""))
    }

    // Helper functions
    private fun createBss(
        bssid: String,
        band: WiFiBand = WiFiBand.BAND_5GHZ,
        rssi: Int = -65,
        roaming: Boolean = false,
    ) = ClusteredBss(
        bssid = bssid,
        band = band,
        channel = 36,
        frequencyMHz = 5180,
        channelWidth = ChannelWidth.WIDTH_80MHZ,
        wifiStandard = WifiStandard.WIFI_6,
        rssiDbm = rssi,
        roamingCapabilities =
            if (roaming) {
                RoamingCapabilities.fullSuite(AuthType.WPA2_PSK)
            } else {
                RoamingCapabilities.none()
            },
    )

    private fun createClusterWithBssids(bssList: List<ClusteredBss>) =
        ApCluster(
            id = "test-cluster",
            ssid = "TestNetwork",
            securityFingerprint = SecurityFingerprint.wpa2Personal(),
            routerVendor = null,
            bssids = bssList,
        )

    private fun createTestCluster() =
        createClusterWithBssids(
            listOf(
                createBss("AA:BB:CC:11:11:11", rssi = -65),
                createBss("AA:BB:CC:22:22:22", rssi = -70),
                createBss("AA:BB:CC:33:33:33", rssi = -60),
            ),
        )

    private fun createRoamingEvent(
        from: String,
        to: String,
        successful: Boolean = true,
        durationMs: Long = 100,
    ) = RoamingEvent(
        timestampMillis = System.currentTimeMillis(),
        fromBssid = from,
        toBssid = to,
        ssid = "TestNetwork",
        durationMillis = durationMs,
        rssiBeforeDbm = -75,
        rssiAfterDbm = -60,
        wasForcedDisconnect = !successful,
        has11r = false,
        has11k = false,
        has11v = false,
    )

    private fun createMeasurement(
        location: LocalSurveyCoord,
        rssi: Int,
    ) = SurveyMeasurement(
        measurementId = "measurement-${System.currentTimeMillis()}-${rssi.hashCode()}",
        timestampMillis = System.currentTimeMillis(),
        localCoordinates = location,
        visibleBssids = mapOf("AA:BB:CC:DD:EE:FF" to rssi),
        connectedBssid = "AA:BB:CC:DD:EE:FF",
        connectedRssiDbm = rssi,
    )
}
