package io.lamco.netkit.analytics.visualization

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class TopologyDiagramGeneratorTest {
    private val generator = TopologyDiagramGenerator()

    // ========================================
    // Topology Graph Tests
    // ========================================

    @Test
    fun `generateTopologyGraph creates graph from nodes and edges`() {
        val nodes =
            listOf(
                GraphNode("ap1", "AP 1", NodeType.ACCESS_POINT),
                GraphNode("ap2", "AP 2", NodeType.ACCESS_POINT),
                GraphNode("router1", "Router", NodeType.ROUTER),
            )
        val edges =
            listOf(
                GraphEdge("ap1", "router1", EdgeType.BACKHAUL_LINK),
                GraphEdge("ap2", "router1", EdgeType.BACKHAUL_LINK),
            )

        val graph = generator.generateTopologyGraph(nodes, edges)

        assertEquals(3, graph.nodes.size)
        assertEquals(2, graph.edges.size)
    }

    @Test
    fun `generateTopologyGraph validates edge references`() {
        val nodes =
            listOf(
                GraphNode("ap1", "AP 1", NodeType.ACCESS_POINT),
            )
        val invalidEdges =
            listOf(
                GraphEdge("ap1", "nonexistent", EdgeType.BACKHAUL_LINK),
            )

        assertThrows<IllegalArgumentException> {
            generator.generateTopologyGraph(nodes, invalidEdges)
        }
    }

    @Test
    fun `generateTopologyGraph rejects empty nodes`() {
        val edges =
            listOf(
                GraphEdge("ap1", "ap2", EdgeType.BACKHAUL_LINK),
            )

        assertThrows<IllegalArgumentException> {
            generator.generateTopologyGraph(emptyList(), edges)
        }
    }

    @Test
    fun `generateTopologyGraph handles graph with no edges`() {
        val nodes =
            listOf(
                GraphNode("ap1", "AP 1", NodeType.ACCESS_POINT),
            )

        val graph = generator.generateTopologyGraph(nodes, emptyList())

        assertEquals(1, graph.nodes.size)
        assertEquals(0, graph.edges.size)
    }

    @Test
    fun `generateTopologyGraph respects config layout`() {
        val nodes =
            listOf(
                GraphNode("ap1", "AP 1", NodeType.ACCESS_POINT),
            )
        val config = TopologyGraphConfig(layout = GraphLayout.CIRCULAR)

        val graph = generator.generateTopologyGraph(nodes, emptyList(), config)

        assertEquals(GraphLayout.CIRCULAR, graph.layout)
    }

    // ========================================
    // Roaming Path Graph Tests
    // ========================================

    @Test
    fun `generateRoamingPathGraph creates path from events`() {
        val events =
            listOf(
                RoamingEvent(
                    timestamp = 1000L,
                    fromBssid = "AA:BB:CC:DD:EE:01",
                    toBssid = "AA:BB:CC:DD:EE:02",
                    reason = RoamingReason.LOW_RSSI,
                    duration = 100L,
                ),
                RoamingEvent(
                    timestamp = 2000L,
                    fromBssid = "AA:BB:CC:DD:EE:02",
                    toBssid = "AA:BB:CC:DD:EE:03",
                    reason = RoamingReason.BETTER_AP_AVAILABLE,
                    duration = 150L,
                ),
            )

        val pathGraph = generator.generateRoamingPathGraph(events)

        assertEquals(3, pathGraph.path.size) // 3 unique BSSIDs
        assertEquals(2, pathGraph.events.size)
        assertEquals(250L, pathGraph.totalDuration)
    }

    @Test
    fun `generateRoamingPathGraph handles single event`() {
        val events =
            listOf(
                RoamingEvent(
                    timestamp = 1000L,
                    fromBssid = "AA:BB:CC:DD:EE:01",
                    toBssid = "AA:BB:CC:DD:EE:02",
                    reason = RoamingReason.LOW_RSSI,
                    duration = 100L,
                ),
            )

        val pathGraph = generator.generateRoamingPathGraph(events)

        assertEquals(2, pathGraph.path.size)
        assertEquals(100L, pathGraph.totalDuration)
    }

    @Test
    fun `generateRoamingPathGraph rejects empty events`() {
        assertThrows<IllegalArgumentException> {
            generator.generateRoamingPathGraph(emptyList())
        }
    }

    @Test
    fun `generateRoamingPathGraph handles repeated roaming between same APs`() {
        val events =
            listOf(
                RoamingEvent(1000L, "AP1", "AP2", RoamingReason.LOW_RSSI, 100L),
                RoamingEvent(2000L, "AP2", "AP1", RoamingReason.LOAD_BALANCING, 100L),
                RoamingEvent(3000L, "AP1", "AP2", RoamingReason.LOW_RSSI, 100L),
            )

        val pathGraph = generator.generateRoamingPathGraph(events)

        assertEquals(2, pathGraph.path.size) // Only 2 unique APs
        assertEquals(3, pathGraph.events.size)
    }

    // ========================================
    // Simple Graph Tests
    // ========================================

    @Test
    fun `createSimpleGraph creates graph with specified nodes and edges`() {
        val graph = generator.createSimpleGraph(nodeCount = 5, edgeCount = 4)

        assertEquals(5, graph.nodes.size)
        assertEquals(4, graph.edges.size)
    }

    @Test
    fun `createSimpleGraph creates connected nodes`() {
        val graph = generator.createSimpleGraph(nodeCount = 3, edgeCount = 2)

        // All edges should reference valid nodes
        val nodeIds = graph.nodes.map { it.id }.toSet()
        assertTrue(graph.edges.all { it.from in nodeIds && it.to in nodeIds })
    }

    @Test
    fun `createSimpleGraph handles zero edges`() {
        val graph = generator.createSimpleGraph(nodeCount = 5, edgeCount = 0)

        assertEquals(5, graph.nodes.size)
        assertEquals(0, graph.edges.size)
    }

    @Test
    fun `createSimpleGraph rejects zero nodes`() {
        assertThrows<IllegalArgumentException> {
            generator.createSimpleGraph(nodeCount = 0, edgeCount = 0)
        }
    }

    @Test
    fun `createSimpleGraph rejects negative nodes`() {
        assertThrows<IllegalArgumentException> {
            generator.createSimpleGraph(nodeCount = -1, edgeCount = 0)
        }
    }

    @Test
    fun `createSimpleGraph rejects negative edges`() {
        assertThrows<IllegalArgumentException> {
            generator.createSimpleGraph(nodeCount = 5, edgeCount = -1)
        }
    }

    @Test
    fun `createSimpleGraph rejects impossible edge count`() {
        assertThrows<IllegalArgumentException> {
            // 3 nodes can have max 6 edges (3 * 2)
            generator.createSimpleGraph(nodeCount = 3, edgeCount = 10)
        }
    }
}

class ChannelDiagramGeneratorTest {
    private val generator = ChannelDiagramGenerator()

    // ========================================
    // Channel Diagram Tests
    // ========================================

    @Test
    fun `generateChannelDiagram creates diagram from networks`() {
        val networks =
            listOf(
                NetworkInfo("Network A", "AA:BB:CC:DD:EE:01", 6, 20, -60),
                NetworkInfo("Network B", "AA:BB:CC:DD:EE:02", 11, 20, -65),
                NetworkInfo("Network C", "AA:BB:CC:DD:EE:03", 1, 20, -70),
            )

        val diagram = generator.generateChannelDiagram(WiFiBand.BAND_2_4GHZ, networks)

        assertEquals(WiFiBand.BAND_2_4GHZ, diagram.band)
        assertEquals(3, diagram.channels.size)
    }

    @Test
    fun `generateChannelDiagram calculates center frequencies`() {
        val networks =
            listOf(
                NetworkInfo("Network", "AA:BB:CC:DD:EE:01", 6, 20, -60),
            )

        val diagram = generator.generateChannelDiagram(WiFiBand.BAND_2_4GHZ, networks)

        val allocation = diagram.channels.first()
        assertEquals(6, allocation.channel)
        assertTrue(allocation.centerFrequency > 2400) // 2.4 GHz band
    }

    @Test
    fun `generateChannelDiagram detects overlaps`() {
        val networks =
            listOf(
                NetworkInfo("Network A", "AA:BB:CC:DD:EE:01", 6, 40, -60),
                NetworkInfo("Network B", "AA:BB:CC:DD:EE:02", 8, 40, -65),
            )
        val config = ChannelDiagramConfig(showOverlaps = true)

        val diagram = generator.generateChannelDiagram(WiFiBand.BAND_2_4GHZ, networks, config)

        assertTrue(diagram.overlaps.isNotEmpty())
    }

    @Test
    fun `generateChannelDiagram handles no overlaps`() {
        val networks =
            listOf(
                NetworkInfo("Network A", "AA:BB:CC:DD:EE:01", 1, 20, -60),
                NetworkInfo("Network B", "AA:BB:CC:DD:EE:02", 6, 20, -65),
                NetworkInfo("Network C", "AA:BB:CC:DD:EE:03", 11, 20, -70),
            )
        val config = ChannelDiagramConfig(showOverlaps = true)

        val diagram = generator.generateChannelDiagram(WiFiBand.BAND_2_4GHZ, networks, config)

        // Channels 1, 6, 11 should not overlap (non-overlapping channels)
        assertTrue(diagram.overlaps.isEmpty() || diagram.overlaps.all { it.severity < 50.0 })
    }

    @Test
    fun `generateChannelDiagram rejects empty networks`() {
        assertThrows<IllegalArgumentException> {
            generator.generateChannelDiagram(WiFiBand.BAND_2_4GHZ, emptyList())
        }
    }

    @Test
    fun `generateChannelDiagram handles 5GHz band`() {
        val networks =
            listOf(
                NetworkInfo("Network", "AA:BB:CC:DD:EE:01", 36, 80, -60),
            )

        val diagram = generator.generateChannelDiagram(WiFiBand.BAND_5GHZ, networks)

        assertEquals(WiFiBand.BAND_5GHZ, diagram.band)
        assertTrue(diagram.channels.first().centerFrequency > 5000)
    }

    // ========================================
    // Optimal Channel Tests
    // ========================================

    @Test
    fun `findOptimalChannel returns first channel when no networks`() {
        val optimal = generator.findOptimalChannel(WiFiBand.BAND_2_4GHZ, emptyList())

        assertEquals(1, optimal)
    }

    @Test
    fun `findOptimalChannel avoids congested channels`() {
        val networks =
            listOf(
                NetworkInfo("N1", "AA:BB:CC:DD:EE:01", 1, 20, -60),
                NetworkInfo("N2", "AA:BB:CC:DD:EE:02", 1, 20, -65),
                NetworkInfo("N3", "AA:BB:CC:DD:EE:03", 6, 20, -70),
            )

        val optimal = generator.findOptimalChannel(WiFiBand.BAND_2_4GHZ, networks)

        assertEquals(11, optimal) // Channel 11 is empty
    }

    @Test
    fun `findOptimalChannel works for 5GHz band`() {
        val networks =
            listOf(
                NetworkInfo("N1", "AA:BB:CC:DD:EE:01", 36, 80, -60),
            )

        val optimal = generator.findOptimalChannel(WiFiBand.BAND_5GHZ, networks)

        assertTrue(optimal in listOf(40, 44, 48, 52)) // Next available channel
    }

    @Test
    fun `findOptimalChannel works for 6GHz band`() {
        val optimal = generator.findOptimalChannel(WiFiBand.BAND_6GHZ, emptyList())

        assertTrue(optimal > 0)
    }

    // ========================================
    // Channel Utilization Tests
    // ========================================

    @Test
    fun `calculateChannelUtilization returns zero for empty networks`() {
        val utilization = generator.calculateChannelUtilization(emptyList())

        assertEquals(0.0, utilization)
    }

    @Test
    fun `calculateChannelUtilization increases with network count`() {
        val oneNetwork =
            listOf(
                NetworkInfo("N1", "AA:BB:CC:DD:EE:01", 6, 20, -60),
            )
        val threeNetworks =
            listOf(
                NetworkInfo("N1", "AA:BB:CC:DD:EE:01", 6, 20, -60),
                NetworkInfo("N2", "AA:BB:CC:DD:EE:02", 6, 20, -65),
                NetworkInfo("N3", "AA:BB:CC:DD:EE:03", 6, 20, -70),
            )

        val util1 = generator.calculateChannelUtilization(oneNetwork)
        val util3 = generator.calculateChannelUtilization(threeNetworks)

        assertTrue(util3 > util1)
    }

    @Test
    fun `calculateChannelUtilization stays in range 0-100`() {
        val manyNetworks =
            (1..20).map {
                NetworkInfo("N$it", "AA:BB:CC:DD:EE:$it", 6, 20, -60)
            }

        val utilization = generator.calculateChannelUtilization(manyNetworks)

        assertTrue(utilization in 0.0..100.0)
    }

    @Test
    fun `calculateChannelUtilization considers signal strength`() {
        val strongSignal =
            listOf(
                NetworkInfo("N1", "AA:BB:CC:DD:EE:01", 6, 20, -40),
            )
        val weakSignal =
            listOf(
                NetworkInfo("N1", "AA:BB:CC:DD:EE:01", 6, 20, -90),
            )

        val utilStrong = generator.calculateChannelUtilization(strongSignal)
        val utilWeak = generator.calculateChannelUtilization(weakSignal)

        assertTrue(utilStrong > utilWeak)
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    @Test
    fun `generateChannelDiagram handles single network`() {
        val networks =
            listOf(
                NetworkInfo("Network", "AA:BB:CC:DD:EE:01", 6, 20, -60),
            )

        val diagram = generator.generateChannelDiagram(WiFiBand.BAND_2_4GHZ, networks)

        assertEquals(1, diagram.channels.size)
        assertTrue(diagram.overlaps.isEmpty())
    }

    @Test
    fun `generateChannelDiagram handles wide channels`() {
        val networks =
            listOf(
                NetworkInfo("Network", "AA:BB:CC:DD:EE:01", 36, 160, -60),
            )

        val diagram = generator.generateChannelDiagram(WiFiBand.BAND_5GHZ, networks)

        assertEquals(160, diagram.channels.first().width)
    }

    @Test
    fun `generateChannelDiagram calculates utilization from RSSI`() {
        val networks =
            listOf(
                NetworkInfo("Strong", "AA:BB:CC:DD:EE:01", 6, 20, -40),
                NetworkInfo("Weak", "AA:BB:CC:DD:EE:02", 11, 20, -90),
            )

        val diagram = generator.generateChannelDiagram(WiFiBand.BAND_2_4GHZ, networks)

        val strongUtil = diagram.channels.find { it.channel == 6 }?.utilization ?: 0.0
        val weakUtil = diagram.channels.find { it.channel == 11 }?.utilization ?: 0.0

        assertTrue(strongUtil > weakUtil)
    }
}
