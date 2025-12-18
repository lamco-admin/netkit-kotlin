package io.lamco.netkit.optimizer.model

import io.lamco.netkit.model.network.WiFiBand

/**
 * Mesh network topology representation
 *
 * Models the structure and connectivity of a mesh WiFi network, including:
 * - Mesh nodes (APs) and their roles
 * - Mesh links (backhaul connections) between nodes
 * - Topology metrics (depth, redundancy, bottlenecks)
 *
 * Supports both standard mesh protocols (IEEE 802.11s) and vendor-specific
 * implementations (Google WiFi, eero, Orbi, etc.)
 *
 * @property nodes List of mesh nodes in the network
 * @property links List of mesh links connecting nodes
 * @property protocol Mesh protocol in use (802.11s or vendor-specific)
 *
 * @throws IllegalArgumentException if topology is invalid
 */
data class MeshTopology(
    val nodes: List<MeshNode>,
    val links: List<MeshLink>,
    val protocol: MeshProtocol = MeshProtocol.VENDOR_SPECIFIC,
) {
    init {
        require(nodes.isNotEmpty()) {
            "Mesh topology must have at least one node"
        }
        require(nodes.count { it.role == MeshRole.ROOT } > 0) {
            "Mesh topology must have at least one ROOT node"
        }
        require(nodes.map { it.bssid }.distinct().size == nodes.size) {
            "All mesh nodes must have unique BSSIDs"
        }

        // Validate links reference valid nodes
        val bssids = nodes.map { it.bssid }.toSet()
        links.forEach { link ->
            require(link.sourceBssid in bssids) {
                "Link source ${link.sourceBssid} not found in nodes"
            }
            require(link.targetBssid in bssids) {
                "Link target ${link.targetBssid} not found in nodes"
            }
            require(link.sourceBssid != link.targetBssid) {
                "Link cannot connect node to itself: ${link.sourceBssid}"
            }
        }
    }

    /**
     * Total number of nodes in mesh
     */
    val nodeCount: Int
        get() = nodes.size

    /**
     * Number of ROOT (gateway) nodes
     */
    val rootCount: Int
        get() = nodes.count { it.role == MeshRole.ROOT }

    /**
     * Number of RELAY (intermediate) nodes
     */
    val relayCount: Int
        get() = nodes.count { it.role == MeshRole.RELAY }

    /**
     * Number of LEAF (edge) nodes
     */
    val leafCount: Int
        get() = nodes.count { it.role == MeshRole.LEAF }

    /**
     * Maximum hop count in topology (network depth)
     */
    val maxHopCount: Int
        get() = nodes.maxOfOrNull { it.hopCount } ?: 0

    /**
     * Whether topology has redundant paths (multiple roots or mesh loops)
     */
    val hasRedundancy: Boolean
        get() = rootCount > 1 || hasAlternatePaths()

    /**
     * Average link quality across all backhaul links
     */
    val averageLinkQuality: Double
        get() = links.map { it.quality }.average()

    /**
     * Weakest link in the mesh (bottleneck)
     */
    val weakestLink: MeshLink?
        get() = links.minByOrNull { it.quality }

    /**
     * Whether all nodes use wired backhaul
     */
    val allWiredBackhaul: Boolean
        get() = nodes.all { it.backhaulType.isWired }

    /**
     * Whether any nodes use dedicated wireless backhaul
     */
    val hasDedicatedBackhaul: Boolean
        get() = nodes.any { it.backhaulType == BackhaulType.WIRELESS_DEDICATED }

    /**
     * Percentage of nodes using wired backhaul
     */
    val wiredBackhaulPercent: Int
        get() = (nodes.count { it.backhaulType.isWired } * 100) / nodes.size

    /**
     * Get node by BSSID
     */
    fun getNode(bssid: String): MeshNode? = nodes.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }

    /**
     * Get all links for a specific node
     */
    fun getLinksForNode(bssid: String): List<MeshLink> =
        links.filter {
            it.sourceBssid.equals(bssid, ignoreCase = true) ||
                it.targetBssid.equals(bssid, ignoreCase = true)
        }

    /**
     * Get nodes at a specific hop count
     */
    fun getNodesAtHop(hopCount: Int): List<MeshNode> = nodes.filter { it.hopCount == hopCount }

    /**
     * Check if topology has alternate paths (redundancy)
     */
    private fun hasAlternatePaths(): Boolean {
        // Simple check: if any non-root node has multiple incoming links
        val incomingLinkCounts =
            nodes
                .groupingBy { node ->
                    links.count { it.targetBssid == node.bssid }
                }.eachCount()

        return incomingLinkCounts.any { (count, _) -> count > 1 }
    }

    /**
     * Topology health score (0-100)
     * Based on: link quality, redundancy, hop count, backhaul type
     */
    val healthScore: Int
        get() {
            var score = 0

            // Link quality (0-40 points)
            score += (averageLinkQuality * 40).toInt()

            // Redundancy (0-20 points)
            if (hasRedundancy) {
                score += 20
            } else if (rootCount == 1) {
                score += 10
            }

            // Hop count (0-20 points) - lower is better
            val hopPenalty = maxHopCount.coerceAtMost(5)
            score += 20 - (hopPenalty * 4)

            // Backhaul type (0-20 points)
            score +=
                when {
                    allWiredBackhaul -> 20
                    hasDedicatedBackhaul -> 15
                    wiredBackhaulPercent >= 50 -> 10
                    else -> 5
                }

            return score.coerceIn(0, 100)
        }

    /**
     * Human-readable topology summary
     */
    val summary: String
        get() =
            buildString {
                append("$nodeCount nodes (")
                append("${rootCount}R/${relayCount}Re/${leafCount}L), ")
                append("max $maxHopCount hops, ")
                append("$wiredBackhaulPercent% wired, ")
                append("health: $healthScore/100")
            }
}

/**
 * Individual mesh node (access point in mesh network)
 *
 * @property bssid Node BSSID (MAC address)
 * @property role Node role in mesh (ROOT/RELAY/LEAF)
 * @property hopCount Number of hops to gateway (0 for ROOT)
 * @property backhaulType Type of backhaul connection
 * @property band Primary operating band
 * @property signalStrength Signal strength to parent node (dBm), null for ROOT
 * @property parentBssid BSSID of parent node, null for ROOT
 *
 * @throws IllegalArgumentException if node configuration is invalid
 */
data class MeshNode(
    val bssid: String,
    val role: MeshRole,
    val hopCount: Int,
    val backhaulType: BackhaulType,
    val band: WiFiBand = WiFiBand.BAND_5GHZ,
    val signalStrength: Int? = null,
    val parentBssid: String? = null,
) {
    init {
        require(bssid.isNotBlank()) {
            "Node BSSID must not be blank"
        }
        require(hopCount >= 0) {
            "Hop count must be non-negative, got $hopCount"
        }
        require(role != MeshRole.ROOT || hopCount == 0) {
            "ROOT nodes must have hop count 0, got $hopCount"
        }
        require(role == MeshRole.ROOT || hopCount > 0) {
            "Non-ROOT nodes must have hop count > 0, got $hopCount"
        }
        if (signalStrength != null) {
            require(signalStrength in -100..0) {
                "Signal strength must be in range [-100, 0] dBm, got $signalStrength"
            }
        }
        require(role != MeshRole.ROOT || parentBssid == null) {
            "ROOT nodes cannot have parent"
        }
        require(role == MeshRole.ROOT || parentBssid != null) {
            "Non-ROOT nodes must have parent BSSID"
        }
    }

    /**
     * Whether this is the gateway node
     */
    val isGateway: Boolean
        get() = role == MeshRole.ROOT

    /**
     * Whether backhaul connection is good (> -70 dBm for wireless)
     */
    val hasGoodBackhaul: Boolean
        get() =
            when (backhaulType) {
                BackhaulType.WIRED_ETHERNET,
                BackhaulType.WIRED_POWERLINE,
                -> true
                BackhaulType.WIRELESS_DEDICATED -> signalStrength?.let { it >= -65 } ?: false
                BackhaulType.WIRELESS_5GHZ,
                BackhaulType.WIRELESS_6GHZ,
                -> signalStrength?.let { it >= -70 } ?: false
                BackhaulType.UNKNOWN -> false
            }

    /**
     * Node quality score (0-100)
     */
    val qualityScore: Int
        get() {
            var score = 100

            // Hop count penalty
            score -= hopCount * 10

            // Backhaul quality penalty
            if (!hasGoodBackhaul) score -= 20

            // Signal strength penalty (for wireless backhaul)
            signalStrength?.let { rssi ->
                when {
                    rssi < -80 -> score -= 30
                    rssi < -70 -> score -= 15
                    rssi < -60 -> score -= 5
                }
            }

            return score.coerceIn(0, 100)
        }
}

/**
 * Mesh node roles
 *
 * Based on position and function in mesh topology:
 * - ROOT: Gateway node with internet connection
 * - RELAY: Intermediate node forwarding traffic
 * - LEAF: Edge node with no children
 */
enum class MeshRole(
    val displayName: String,
) {
    /**
     * Gateway node (router)
     * - Has internet/WAN connection
     * - Hop count = 0
     * - Root of mesh tree
     */
    ROOT("Gateway/Root"),

    /**
     * Intermediate relay node
     * - Forwards traffic between nodes
     * - Has both parent and children
     * - Critical for multi-hop topologies
     */
    RELAY("Relay Node"),

    /**
     * Leaf/edge node
     * - No children (terminal node)
     * - Only serves clients
     * - Lowest priority for routing
     */
    LEAF("Leaf/Edge Node"),
}

/**
 * Mesh backhaul connection types
 *
 * Backhaul connects mesh nodes to gateway. Quality varies significantly:
 * - Wired: Best (stable, high throughput, low latency)
 * - Dedicated wireless: Good (separate radio for backhaul)
 * - Shared wireless: Fair (competes with client traffic)
 */
enum class BackhaulType(
    val displayName: String,
    val isWired: Boolean,
) {
    /**
     * Ethernet backhaul (best)
     * - Gigabit or faster wired connection
     * - No interference
     * - Lowest latency
     */
    WIRED_ETHERNET("Wired Ethernet", true),

    /**
     * Powerline backhaul (good)
     * - Ethernet over power lines
     * - Variable performance
     * - Better than wireless in many cases
     */
    WIRED_POWERLINE("Powerline Ethernet", true),

    /**
     * Dedicated 5 GHz wireless backhaul (good)
     * - Separate radio for backhaul
     * - No client interference
     * - Common in tri-band mesh systems
     */
    WIRELESS_DEDICATED("Dedicated Wireless", false),

    /**
     * Shared 5 GHz wireless backhaul (fair)
     * - Same radio for backhaul and clients
     * - Competes with client traffic
     * - Halves throughput per hop
     */
    WIRELESS_5GHZ("5 GHz Wireless", false),

    /**
     * Shared 6 GHz wireless backhaul (good)
     * - WiFi 6E backhaul
     * - Less interference than 5 GHz
     * - High throughput potential
     */
    WIRELESS_6GHZ("6 GHz Wireless", false),

    /**
     * Unknown backhaul type
     */
    UNKNOWN("Unknown", false),
}

/**
 * Link between two mesh nodes
 *
 * Represents the backhaul connection quality and characteristics
 * between parent and child nodes in the mesh.
 *
 * @property sourceBssid Source (parent) node BSSID
 * @property targetBssid Target (child) node BSSID
 * @property linkType Type of connection (wired/wireless)
 * @property quality Link quality (0.0-1.0, higher is better)
 * @property throughputMbps Estimated throughput (Mbps)
 * @property latencyMs Latency in milliseconds
 * @property packetLoss Packet loss percentage (0-100)
 *
 * @throws IllegalArgumentException if link parameters are invalid
 */
data class MeshLink(
    val sourceBssid: String,
    val targetBssid: String,
    val linkType: BackhaulType,
    val quality: Double,
    val throughputMbps: Double,
    val latencyMs: Double = 5.0,
    val packetLoss: Double = 0.0,
) {
    init {
        require(sourceBssid.isNotBlank()) {
            "Source BSSID must not be blank"
        }
        require(targetBssid.isNotBlank()) {
            "Target BSSID must not be blank"
        }
        require(quality in 0.0..1.0) {
            "Link quality must be 0.0-1.0, got $quality"
        }
        require(throughputMbps >= 0) {
            "Throughput must be non-negative, got $throughputMbps Mbps"
        }
        require(latencyMs >= 0) {
            "Latency must be non-negative, got $latencyMs ms"
        }
        require(packetLoss in 0.0..100.0) {
            "Packet loss must be 0-100%, got $packetLoss"
        }
    }

    /**
     * Whether link is healthy (quality >= 0.7, packet loss < 1%)
     */
    val isHealthy: Boolean
        get() = quality >= 0.7 && packetLoss < 1.0

    /**
     * Whether link is a bottleneck (quality < 0.5 or throughput < 100 Mbps)
     */
    val isBottleneck: Boolean
        get() = quality < 0.5 || throughputMbps < 100.0

    /**
     * Link quality category
     */
    val qualityCategory: LinkQuality
        get() =
            when {
                quality >= 0.8 && packetLoss < 0.5 -> LinkQuality.EXCELLENT
                quality >= 0.6 && packetLoss < 1.0 -> LinkQuality.GOOD
                quality >= 0.4 && packetLoss < 2.0 -> LinkQuality.FAIR
                else -> LinkQuality.POOR
            }
}

/**
 * Link quality categories
 */
enum class LinkQuality(
    val displayName: String,
) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
}

/**
 * Mesh protocol types
 */
enum class MeshProtocol(
    val displayName: String,
) {
    /**
     * IEEE 802.11s (standard mesh)
     */
    IEEE_802_11S("IEEE 802.11s"),

    /**
     * Vendor-specific mesh protocol
     * (Google WiFi, eero, Orbi, Deco, etc.)
     */
    VENDOR_SPECIFIC("Vendor-Specific"),

    /**
     * Unknown mesh protocol
     */
    UNKNOWN("Unknown"),
}
