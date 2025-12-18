package io.lamco.netkit.optimizer.model

import io.lamco.netkit.model.network.WiFiBand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for MeshTopology and related models
 */
class MeshTopologyTest {

    // ========================================
    // MeshNode Tests
    // ========================================

    @Test
    fun `create valid ROOT node succeeds`() {
        val node = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.ROOT,
            hopCount = 0,
            backhaulType = BackhaulType.WIRED_ETHERNET
        )

        assertEquals(MeshRole.ROOT, node.role)
        assertEquals(0, node.hopCount)
        assertTrue(node.isGateway)
    }

    @Test
    fun `create valid RELAY node succeeds`() {
        val node = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.RELAY,
            hopCount = 1,
            backhaulType = BackhaulType.WIRELESS_5GHZ,
            signalStrength = -65,
            parentBssid = "11:22:33:44:55:66"
        )

        assertEquals(MeshRole.RELAY, node.role)
        assertEquals(1, node.hopCount)
        assertFalse(node.isGateway)
    }

    @Test
    fun `create valid LEAF node succeeds`() {
        val node = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.LEAF,
            hopCount = 2,
            backhaulType = BackhaulType.WIRELESS_5GHZ,
            signalStrength = -70,
            parentBssid = "11:22:33:44:55:66"
        )

        assertEquals(MeshRole.LEAF, node.role)
        assertEquals(2, node.hopCount)
    }

    @Test
    fun `create node with blank BSSID throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshNode(
                bssid = "",
                role = MeshRole.ROOT,
                hopCount = 0,
                backhaulType = BackhaulType.WIRED_ETHERNET
            )
        }
    }

    @Test
    fun `create node with negative hop count throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshNode(
                bssid = "AA:BB:CC:DD:EE:FF",
                role = MeshRole.RELAY,
                hopCount = -1,
                backhaulType = BackhaulType.WIRELESS_5GHZ,
                parentBssid = "11:22:33:44:55:66"
            )
        }
    }

    @Test
    fun `create ROOT node with non-zero hop count throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshNode(
                bssid = "AA:BB:CC:DD:EE:FF",
                role = MeshRole.ROOT,
                hopCount = 1,
                backhaulType = BackhaulType.WIRED_ETHERNET
            )
        }
    }

    @Test
    fun `create non-ROOT node with zero hop count throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshNode(
                bssid = "AA:BB:CC:DD:EE:FF",
                role = MeshRole.RELAY,
                hopCount = 0,
                backhaulType = BackhaulType.WIRELESS_5GHZ,
                parentBssid = "11:22:33:44:55:66"
            )
        }
    }

    @Test
    fun `create node with invalid signal strength throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshNode(
                bssid = "AA:BB:CC:DD:EE:FF",
                role = MeshRole.RELAY,
                hopCount = 1,
                backhaulType = BackhaulType.WIRELESS_5GHZ,
                signalStrength = 10,  // Invalid: positive
                parentBssid = "11:22:33:44:55:66"
            )
        }
    }

    @Test
    fun `create ROOT node with parent throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshNode(
                bssid = "AA:BB:CC:DD:EE:FF",
                role = MeshRole.ROOT,
                hopCount = 0,
                backhaulType = BackhaulType.WIRED_ETHERNET,
                parentBssid = "11:22:33:44:55:66"
            )
        }
    }

    @Test
    fun `create non-ROOT node without parent throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshNode(
                bssid = "AA:BB:CC:DD:EE:FF",
                role = MeshRole.RELAY,
                hopCount = 1,
                backhaulType = BackhaulType.WIRELESS_5GHZ
            )
        }
    }

    @Test
    fun `hasGoodBackhaul is true for wired connections`() {
        val node = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.ROOT,
            hopCount = 0,
            backhaulType = BackhaulType.WIRED_ETHERNET
        )

        assertTrue(node.hasGoodBackhaul)
    }

    @Test
    fun `hasGoodBackhaul is true for strong wireless signal`() {
        val node = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.RELAY,
            hopCount = 1,
            backhaulType = BackhaulType.WIRELESS_5GHZ,
            signalStrength = -60,
            parentBssid = "11:22:33:44:55:66"
        )

        assertTrue(node.hasGoodBackhaul)
    }

    @Test
    fun `hasGoodBackhaul is false for weak wireless signal`() {
        val node = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.RELAY,
            hopCount = 1,
            backhaulType = BackhaulType.WIRELESS_5GHZ,
            signalStrength = -80,
            parentBssid = "11:22:33:44:55:66"
        )

        assertFalse(node.hasGoodBackhaul)
    }

    @Test
    fun `qualityScore is high for ROOT with wired backhaul`() {
        val node = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.ROOT,
            hopCount = 0,
            backhaulType = BackhaulType.WIRED_ETHERNET
        )

        assertTrue(node.qualityScore >= 90)
    }

    @Test
    fun `qualityScore decreases with hop count`() {
        val node1Hop = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.RELAY,
            hopCount = 1,
            backhaulType = BackhaulType.WIRELESS_5GHZ,
            signalStrength = -60,
            parentBssid = "11:22:33:44:55:66"
        )

        val node3Hop = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.LEAF,
            hopCount = 3,
            backhaulType = BackhaulType.WIRELESS_5GHZ,
            signalStrength = -60,
            parentBssid = "11:22:33:44:55:66"
        )

        assertTrue(node1Hop.qualityScore > node3Hop.qualityScore)
    }

    // ========================================
    // MeshLink Tests
    // ========================================

    @Test
    fun `create valid mesh link succeeds`() {
        val link = MeshLink(
            sourceBssid = "AA:BB:CC:DD:EE:FF",
            targetBssid = "11:22:33:44:55:66",
            linkType = BackhaulType.WIRELESS_5GHZ,
            quality = 0.8,
            throughputMbps = 500.0
        )

        assertEquals(0.8, link.quality)
        assertEquals(500.0, link.throughputMbps)
    }

    @Test
    fun `create link with blank source throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshLink(
                sourceBssid = "",
                targetBssid = "11:22:33:44:55:66",
                linkType = BackhaulType.WIRELESS_5GHZ,
                quality = 0.8,
                throughputMbps = 500.0
            )
        }
    }

    @Test
    fun `create link with blank target throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshLink(
                sourceBssid = "AA:BB:CC:DD:EE:FF",
                targetBssid = "",
                linkType = BackhaulType.WIRELESS_5GHZ,
                quality = 0.8,
                throughputMbps = 500.0
            )
        }
    }

    @Test
    fun `create link with invalid quality throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshLink(
                sourceBssid = "AA:BB:CC:DD:EE:FF",
                targetBssid = "11:22:33:44:55:66",
                linkType = BackhaulType.WIRELESS_5GHZ,
                quality = 1.5,  // Invalid: > 1.0
                throughputMbps = 500.0
            )
        }
    }

    @Test
    fun `create link with negative throughput throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshLink(
                sourceBssid = "AA:BB:CC:DD:EE:FF",
                targetBssid = "11:22:33:44:55:66",
                linkType = BackhaulType.WIRELESS_5GHZ,
                quality = 0.8,
                throughputMbps = -100.0
            )
        }
    }

    @Test
    fun `create link with negative latency throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshLink(
                sourceBssid = "AA:BB:CC:DD:EE:FF",
                targetBssid = "11:22:33:44:55:66",
                linkType = BackhaulType.WIRELESS_5GHZ,
                quality = 0.8,
                throughputMbps = 500.0,
                latencyMs = -1.0
            )
        }
    }

    @Test
    fun `create link with invalid packet loss throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshLink(
                sourceBssid = "AA:BB:CC:DD:EE:FF",
                targetBssid = "11:22:33:44:55:66",
                linkType = BackhaulType.WIRELESS_5GHZ,
                quality = 0.8,
                throughputMbps = 500.0,
                packetLoss = 101.0
            )
        }
    }

    @Test
    fun `isHealthy is true for good link`() {
        val link = MeshLink(
            sourceBssid = "AA:BB:CC:DD:EE:FF",
            targetBssid = "11:22:33:44:55:66",
            linkType = BackhaulType.WIRELESS_5GHZ,
            quality = 0.8,
            throughputMbps = 500.0,
            packetLoss = 0.5
        )

        assertTrue(link.isHealthy)
    }

    @Test
    fun `isHealthy is false for poor quality link`() {
        val link = MeshLink(
            sourceBssid = "AA:BB:CC:DD:EE:FF",
            targetBssid = "11:22:33:44:55:66",
            linkType = BackhaulType.WIRELESS_5GHZ,
            quality = 0.5,
            throughputMbps = 500.0
        )

        assertFalse(link.isHealthy)
    }

    @Test
    fun `isBottleneck is true for low throughput`() {
        val link = MeshLink(
            sourceBssid = "AA:BB:CC:DD:EE:FF",
            targetBssid = "11:22:33:44:55:66",
            linkType = BackhaulType.WIRELESS_5GHZ,
            quality = 0.8,
            throughputMbps = 50.0
        )

        assertTrue(link.isBottleneck)
    }

    @Test
    fun `qualityCategory EXCELLENT for high quality link`() {
        val link = MeshLink(
            sourceBssid = "AA:BB:CC:DD:EE:FF",
            targetBssid = "11:22:33:44:55:66",
            linkType = BackhaulType.WIRED_ETHERNET,
            quality = 0.9,
            throughputMbps = 1000.0,
            packetLoss = 0.1
        )

        assertEquals(LinkQuality.EXCELLENT, link.qualityCategory)
    }

    @Test
    fun `qualityCategory POOR for low quality link`() {
        val link = MeshLink(
            sourceBssid = "AA:BB:CC:DD:EE:FF",
            targetBssid = "11:22:33:44:55:66",
            linkType = BackhaulType.WIRELESS_5GHZ,
            quality = 0.3,
            throughputMbps = 100.0,
            packetLoss = 5.0
        )

        assertEquals(LinkQuality.POOR, link.qualityCategory)
    }

    // ========================================
    // MeshTopology Tests
    // ========================================

    @Test
    fun `create valid mesh topology succeeds`() {
        val root = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.ROOT,
            hopCount = 0,
            backhaulType = BackhaulType.WIRED_ETHERNET
        )

        val relay = MeshNode(
            bssid = "11:22:33:44:55:66",
            role = MeshRole.RELAY,
            hopCount = 1,
            backhaulType = BackhaulType.WIRELESS_5GHZ,
            signalStrength = -60,
            parentBssid = "AA:BB:CC:DD:EE:FF"
        )

        val link = MeshLink(
            sourceBssid = "AA:BB:CC:DD:EE:FF",
            targetBssid = "11:22:33:44:55:66",
            linkType = BackhaulType.WIRELESS_5GHZ,
            quality = 0.8,
            throughputMbps = 500.0
        )

        val topology = MeshTopology(
            nodes = listOf(root, relay),
            links = listOf(link)
        )

        assertEquals(2, topology.nodeCount)
        assertEquals(1, topology.rootCount)
        assertEquals(1, topology.relayCount)
    }

    @Test
    fun `create topology with no nodes throws exception`() {
        assertThrows<IllegalArgumentException> {
            MeshTopology(
                nodes = emptyList(),
                links = emptyList()
            )
        }
    }

    @Test
    fun `create topology with no ROOT throws exception`() {
        val relay = MeshNode(
            bssid = "11:22:33:44:55:66",
            role = MeshRole.RELAY,
            hopCount = 1,
            backhaulType = BackhaulType.WIRELESS_5GHZ,
            signalStrength = -60,
            parentBssid = "AA:BB:CC:DD:EE:FF"
        )

        assertThrows<IllegalArgumentException> {
            MeshTopology(
                nodes = listOf(relay),
                links = emptyList()
            )
        }
    }

    @Test
    fun `create topology with duplicate BSSIDs throws exception`() {
        val root1 = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.ROOT,
            hopCount = 0,
            backhaulType = BackhaulType.WIRED_ETHERNET
        )

        val root2 = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",  // Duplicate
            role = MeshRole.ROOT,
            hopCount = 0,
            backhaulType = BackhaulType.WIRED_ETHERNET
        )

        assertThrows<IllegalArgumentException> {
            MeshTopology(
                nodes = listOf(root1, root2),
                links = emptyList()
            )
        }
    }

    @Test
    fun `create topology with link to non-existent node throws exception`() {
        val root = MeshNode(
            bssid = "AA:BB:CC:DD:EE:FF",
            role = MeshRole.ROOT,
            hopCount = 0,
            backhaulType = BackhaulType.WIRED_ETHERNET
        )

        val link = MeshLink(
            sourceBssid = "AA:BB:CC:DD:EE:FF",
            targetBssid = "INVALID",  // Non-existent
            linkType = BackhaulType.WIRELESS_5GHZ,
            quality = 0.8,
            throughputMbps = 500.0
        )

        assertThrows<IllegalArgumentException> {
            MeshTopology(
                nodes = listOf(root),
                links = listOf(link)
            )
        }
    }

    @Test
    fun `maxHopCount returns correct value`() {
        val nodes = listOf(
            MeshNode("AA:BB:CC:DD:EE:FF", MeshRole.ROOT, 0, BackhaulType.WIRED_ETHERNET),
            MeshNode("11:22:33:44:55:66", MeshRole.RELAY, 1, BackhaulType.WIRELESS_5GHZ, parentBssid = "AA:BB:CC:DD:EE:FF", signalStrength = -60),
            MeshNode("77:88:99:AA:BB:CC", MeshRole.LEAF, 3, BackhaulType.WIRELESS_5GHZ, parentBssid = "11:22:33:44:55:66", signalStrength = -70)
        )

        val topology = MeshTopology(nodes, emptyList())
        assertEquals(3, topology.maxHopCount)
    }

    @Test
    fun `allWiredBackhaul is true when all nodes wired`() {
        val nodes = listOf(
            MeshNode("AA:BB:CC:DD:EE:FF", MeshRole.ROOT, 0, BackhaulType.WIRED_ETHERNET),
            MeshNode("11:22:33:44:55:66", MeshRole.RELAY, 1, BackhaulType.WIRED_ETHERNET, parentBssid = "AA:BB:CC:DD:EE:FF")
        )

        val topology = MeshTopology(nodes, emptyList())
        assertTrue(topology.allWiredBackhaul)
    }

    @Test
    fun `allWiredBackhaul is false when any node wireless`() {
        val nodes = listOf(
            MeshNode("AA:BB:CC:DD:EE:FF", MeshRole.ROOT, 0, BackhaulType.WIRED_ETHERNET),
            MeshNode("11:22:33:44:55:66", MeshRole.RELAY, 1, BackhaulType.WIRELESS_5GHZ, parentBssid = "AA:BB:CC:DD:EE:FF", signalStrength = -60)
        )

        val topology = MeshTopology(nodes, emptyList())
        assertFalse(topology.allWiredBackhaul)
    }

    @Test
    fun `getNode returns correct node`() {
        val root = MeshNode("AA:BB:CC:DD:EE:FF", MeshRole.ROOT, 0, BackhaulType.WIRED_ETHERNET)
        val topology = MeshTopology(listOf(root), emptyList())

        assertNotNull(topology.getNode("AA:BB:CC:DD:EE:FF"))
        assertNull(topology.getNode("INVALID"))
    }

    @Test
    fun `healthScore is high for optimal topology`() {
        val nodes = listOf(
            MeshNode("AA:BB:CC:DD:EE:FF", MeshRole.ROOT, 0, BackhaulType.WIRED_ETHERNET),
            MeshNode("11:22:33:44:55:66", MeshRole.LEAF, 1, BackhaulType.WIRED_ETHERNET, parentBssid = "AA:BB:CC:DD:EE:FF")
        )

        val link = MeshLink("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66", BackhaulType.WIRED_ETHERNET, 1.0, 1000.0)

        val topology = MeshTopology(nodes, listOf(link))
        assertTrue(topology.healthScore >= 80)
    }

    @Test
    fun `summary contains key information`() {
        val nodes = listOf(
            MeshNode("AA:BB:CC:DD:EE:FF", MeshRole.ROOT, 0, BackhaulType.WIRED_ETHERNET),
            MeshNode("11:22:33:44:55:66", MeshRole.RELAY, 1, BackhaulType.WIRELESS_5GHZ, parentBssid = "AA:BB:CC:DD:EE:FF", signalStrength = -60),
            MeshNode("77:88:99:AA:BB:CC", MeshRole.LEAF, 2, BackhaulType.WIRELESS_5GHZ, parentBssid = "11:22:33:44:55:66", signalStrength = -70)
        )

        val topology = MeshTopology(nodes, emptyList())
        val summary = topology.summary

        assertTrue(summary.contains("3 nodes"))
        assertTrue(summary.contains("1R"))
        assertTrue(summary.contains("1Re"))
        assertTrue(summary.contains("1L"))
    }
}
