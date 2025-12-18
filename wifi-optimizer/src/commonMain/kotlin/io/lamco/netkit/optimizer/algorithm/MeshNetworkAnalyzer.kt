package io.lamco.netkit.optimizer.algorithm

import io.lamco.netkit.optimizer.model.BackhaulType
import io.lamco.netkit.optimizer.model.MeshLink
import io.lamco.netkit.optimizer.model.MeshNode
import io.lamco.netkit.optimizer.model.MeshRole
import io.lamco.netkit.optimizer.model.MeshTopology

/**
 * Mesh network analyzer for topology optimization
 *
 * Analyzes mesh WiFi deployments (IEEE 802.11s or vendor-specific) to identify
 * performance bottlenecks and recommend optimizations.
 *
 * Mesh-specific analysis:
 * - Backhaul quality and type (wired vs. wireless)
 * - Hop count optimization
 * - Mesh loop detection
 * - Self-healing capability assessment
 * - Topology redundancy analysis
 *
 * Supports:
 * - IEEE 802.11s standard mesh
 * - Vendor mesh systems (Google WiFi, eero, Orbi, Deco, etc.)
 */
class MeshNetworkAnalyzer {
    /**
     * Analyze mesh backhaul quality
     *
     * Evaluates the quality of backhaul connections in a mesh network,
     * identifying bottlenecks and poor-quality links.
     *
     * Backhaul quality factors:
     * - Link type (wired > dedicated wireless > shared wireless)
     * - Signal strength (for wireless backhaul)
     * - Throughput and latency
     * - Hop count (fewer hops = better)
     *
     * @param meshTopology Complete mesh topology with nodes and links
     * @return Backhaul analysis with bottlenecks and recommendations
     */
    fun analyzeBackhaul(meshTopology: MeshTopology): BackhaulAnalysisResult {
        require(meshTopology.nodes.isNotEmpty()) {
            "Cannot analyze empty mesh topology"
        }

        val overallQuality = assessOverallBackhaulQuality(meshTopology)

        val bottlenecks = identifyBottlenecks(meshTopology)

        val recommendations =
            buildBackhaulRecommendations(
                topology = meshTopology,
                quality = overallQuality,
                bottlenecks = bottlenecks,
            )

        return BackhaulAnalysisResult(
            quality = overallQuality,
            bottlenecks = bottlenecks,
            recommendations = recommendations,
            wiredPercent = meshTopology.wiredBackhaulPercent,
            averageLinkQuality = meshTopology.averageLinkQuality,
            maxHopCount = meshTopology.maxHopCount,
        )
    }

    /**
     * Detect mesh loops in topology
     *
     * Identifies redundant paths and loops in the mesh that could cause
     * routing issues or inefficiencies.
     *
     * Uses graph traversal to detect:
     * - Simple cycles (A → B → C → A)
     * - Redundant paths (multiple paths between same nodes)
     *
     * @param meshTopology Mesh topology to analyze
     * @return List of detected loops
     */
    fun detectLoops(meshTopology: MeshTopology): List<MeshLoop> {
        val loops = mutableListOf<MeshLoop>()

        val adjacency = buildAdjacencyList(meshTopology)

        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        for (node in meshTopology.nodes) {
            if (node.bssid !in visited) {
                val cycleNodes =
                    detectCycleDFS(
                        current = node.bssid,
                        visited = visited,
                        recursionStack = recursionStack,
                        adjacency = adjacency,
                        parent = null,
                    )

                if (cycleNodes.isNotEmpty()) {
                    loops.add(
                        MeshLoop(
                            nodes = cycleNodes,
                            type = MeshLoopType.SIMPLE_CYCLE,
                            severity = assessLoopSeverity(cycleNodes.size),
                            recommendation = "Review topology - cycle detected: ${cycleNodes.joinToString(" → ")}",
                        ),
                    )
                }
            }
        }

        val redundantPaths = detectRedundantPaths(meshTopology)
        loops.addAll(redundantPaths)

        return loops
    }

    /**
     * Assess self-healing capability
     *
     * Evaluates the mesh's ability to automatically recover from node
     * or link failures.
     *
     * @param meshTopology Current mesh topology
     * @param failureScenarios Failure scenarios to test
     * @return Self-healing assessment
     */
    fun assessSelfHealing(
        meshTopology: MeshTopology,
        failureScenarios: List<FailureScenario>,
    ): SelfHealingAssessment {
        if (failureScenarios.isEmpty()) {
            // Default scenarios: single node failure, single link failure
            val defaultScenarios =
                listOf(
                    FailureScenario.SingleNodeFailure(meshTopology.nodes.firstOrNull()?.bssid ?: ""),
                    FailureScenario.SingleLinkFailure(
                        meshTopology.links.firstOrNull()?.sourceBssid ?: "",
                        meshTopology.links.firstOrNull()?.targetBssid ?: "",
                    ),
                )
            return assessSelfHealing(meshTopology, defaultScenarios)
        }

        val results =
            failureScenarios.map { scenario ->
                testFailureScenario(meshTopology, scenario)
            }

        val canRecover = results.all { it.canRecover }
        val avgRecoveryTime = results.mapNotNull { it.estimatedRecoveryTime }.average()
        val avgServiceImpact = results.map { it.serviceImpact }.average()

        val capability =
            when {
                !canRecover -> SelfHealingCapability.NONE
                avgServiceImpact < 0.2 -> SelfHealingCapability.EXCELLENT
                avgServiceImpact < 0.5 -> SelfHealingCapability.GOOD
                else -> SelfHealingCapability.LIMITED
            }

        return SelfHealingAssessment(
            capability = capability,
            canRecoverFromSingleNodeFailure =
                results.any {
                    it.scenario is FailureScenario.SingleNodeFailure && it.canRecover
                },
            canRecoverFromSingleLinkFailure =
                results.any {
                    it.scenario is FailureScenario.SingleLinkFailure && it.canRecover
                },
            averageRecoveryTimeSeconds = avgRecoveryTime,
            recommendations = buildSelfHealingRecommendations(capability, meshTopology),
        )
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Assess overall backhaul quality based on topology metrics
     */
    private fun assessOverallBackhaulQuality(topology: MeshTopology): BackhaulQuality =
        when {
            // All wired = excellent
            topology.allWiredBackhaul -> BackhaulQuality.EXCELLENT

            // Mostly wired or has dedicated backhaul = good
            topology.wiredBackhaulPercent >= 75 || topology.hasDedicatedBackhaul ->
                BackhaulQuality.GOOD

            // Some wireless, moderate hops = fair
            topology.maxHopCount <= 2 && topology.averageLinkQuality >= 0.6 ->
                BackhaulQuality.FAIR

            // Multi-hop wireless with good quality = fair
            topology.averageLinkQuality >= 0.5 -> BackhaulQuality.FAIR

            // Poor link quality or many hops = poor
            topology.averageLinkQuality < 0.5 || topology.maxHopCount > 3 ->
                BackhaulQuality.POOR

            // Critical: very poor quality or excessive hops
            else -> BackhaulQuality.CRITICAL
        }

    /**
     * Identify bottleneck links in mesh
     */
    private fun identifyBottlenecks(topology: MeshTopology): List<BackhaulBottleneck> {
        val bottlenecks = mutableListOf<BackhaulBottleneck>()

        for (link in topology.links) {
            val isBottleneck =
                link.isBottleneck ||
                    link.throughputMbps < 100.0 ||
                    link.quality < 0.5 ||
                    link.latencyMs > 20.0

            if (isBottleneck) {
                bottlenecks.add(
                    BackhaulBottleneck(
                        link = link,
                        reason = identifyBottleneckReason(link),
                        impact = estimateBottleneckImpact(link, topology),
                        recommendation = recommendBottleneckFix(link),
                    ),
                )
            }
        }

        return bottlenecks.sortedByDescending { it.impact }
    }

    private fun identifyBottleneckReason(link: MeshLink): String =
        when {
            link.quality < 0.4 -> "Very poor link quality (${(link.quality * 100).toInt()}%)"
            link.throughputMbps < 50.0 -> "Low throughput (${link.throughputMbps.toInt()} Mbps)"
            link.latencyMs > 30.0 -> "High latency (${link.latencyMs.toInt()} ms)"
            link.packetLoss > 2.0 -> "Excessive packet loss (${link.packetLoss}%)"
            else -> "Suboptimal performance"
        }

    private fun estimateBottleneckImpact(
        link: MeshLink,
        topology: MeshTopology,
    ): Double {
        // Count how many downstream nodes depend on this link
        val downstreamNodes = countDownstreamNodes(link.targetBssid, topology)

        // Impact = (nodes affected / total nodes) * (1 - link quality)
        val nodeImpact = downstreamNodes.toDouble() / topology.nodeCount
        val qualityImpact = 1.0 - link.quality

        return (nodeImpact * 0.6 + qualityImpact * 0.4).coerceIn(0.0, 1.0)
    }

    private fun countDownstreamNodes(
        nodeBssid: String,
        topology: MeshTopology,
    ): Int {
        // Count nodes that have this node as parent or ancestor
        val node = topology.getNode(nodeBssid) ?: return 0

        return topology.nodes.count { it.hopCount > node.hopCount }
    }

    private fun recommendBottleneckFix(link: MeshLink): String =
        when (link.linkType) {
            BackhaulType.WIRELESS_5GHZ, BackhaulType.WIRELESS_6GHZ -> {
                when {
                    link.quality < 0.5 -> "Replace wireless backhaul with wired (Ethernet or powerline)"
                    link.throughputMbps < 100.0 -> "Reduce distance between nodes or add intermediate relay"
                    else -> "Consider upgrading to dedicated backhaul radio"
                }
            }
            BackhaulType.WIRED_POWERLINE -> {
                "Powerline quality is poor - use Ethernet cable if possible"
            }
            else -> "Investigate link quality issues"
        }

    private fun buildBackhaulRecommendations(
        topology: MeshTopology,
        quality: BackhaulQuality,
        bottlenecks: List<BackhaulBottleneck>,
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when (quality) {
            BackhaulQuality.EXCELLENT -> {
                recommendations.add("Mesh backhaul is optimal (${topology.wiredBackhaulPercent}% wired)")
            }
            BackhaulQuality.GOOD -> {
                recommendations.add("Mesh backhaul is good - consider wiring remaining ${100 - topology.wiredBackhaulPercent}% of nodes")
            }
            BackhaulQuality.FAIR -> {
                recommendations.add("Mesh backhaul is acceptable but could be improved")
                if (topology.maxHopCount > 2) {
                    recommendations.add("Reduce hop count by adding more root nodes or rearranging topology")
                }
            }
            BackhaulQuality.POOR -> {
                recommendations.add("PRIORITY: Mesh backhaul quality is poor")
                recommendations.add("Replace wireless backhaul with wired connections where possible")
            }
            BackhaulQuality.CRITICAL -> {
                recommendations.add("URGENT: Critical backhaul issues detected")
                recommendations.add("Network performance severely degraded - immediate action required")
            }
        }

        if (bottlenecks.isNotEmpty()) {
            recommendations.add("${bottlenecks.size} bottleneck link(s) detected - see detailed recommendations")
        }

        if (topology.maxHopCount > 3) {
            recommendations.add("Excessive hop count (${topology.maxHopCount}) - add more gateway nodes")
        }

        return recommendations
    }

    private fun buildAdjacencyList(topology: MeshTopology): Map<String, List<String>> {
        val adjacency = mutableMapOf<String, MutableList<String>>()

        for (link in topology.links) {
            adjacency.getOrPut(link.sourceBssid) { mutableListOf() }.add(link.targetBssid)
            // For undirected graph, add reverse edge
            adjacency.getOrPut(link.targetBssid) { mutableListOf() }.add(link.sourceBssid)
        }

        return adjacency
    }

    private fun detectCycleDFS(
        current: String,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>,
        adjacency: Map<String, List<String>>,
        parent: String?,
    ): List<String> {
        visited.add(current)
        recursionStack.add(current)

        for (neighbor in adjacency[current] ?: emptyList()) {
            if (neighbor == parent) continue // Skip parent in undirected graph

            if (neighbor in recursionStack) {
                // Cycle detected
                return listOf(current, neighbor)
            }

            if (neighbor !in visited) {
                val cycle = detectCycleDFS(neighbor, visited, recursionStack, adjacency, current)
                if (cycle.isNotEmpty()) {
                    return cycle + current
                }
            }
        }

        recursionStack.remove(current)
        return emptyList()
    }

    private fun detectRedundantPaths(topology: MeshTopology): List<MeshLoop> {
        // Simplified: check if multiple ROOT nodes provide redundancy
        val rootCount = topology.rootCount

        return if (rootCount > 1) {
            listOf(
                MeshLoop(
                    nodes = topology.nodes.filter { it.role == MeshRole.ROOT }.map { it.bssid },
                    type = MeshLoopType.REDUNDANT_PATHS,
                    severity = LoopSeverity.INFO,
                    recommendation = "Multiple gateway nodes provide redundancy (good for reliability)",
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun assessLoopSeverity(cycleLength: Int): LoopSeverity =
        when {
            cycleLength <= 3 -> LoopSeverity.WARNING
            cycleLength <= 5 -> LoopSeverity.MODERATE
            else -> LoopSeverity.SEVERE
        }

    private fun testFailureScenario(
        topology: MeshTopology,
        scenario: FailureScenario,
    ): FailureTestResult =
        when (scenario) {
            is FailureScenario.SingleNodeFailure -> {
                testNodeFailure(topology, scenario.nodeBssid)
            }
            is FailureScenario.SingleLinkFailure -> {
                testLinkFailure(topology, scenario.sourceBssid, scenario.targetBssid)
            }
        }

    private fun testNodeFailure(
        topology: MeshTopology,
        nodeBssid: String,
    ): FailureTestResult {
        val node = topology.getNode(nodeBssid)

        // If ROOT node fails, only recoverable if multiple roots
        val canRecover =
            if (node?.role == MeshRole.ROOT) {
                topology.rootCount > 1
            } else {
                // Non-root failure: recoverable if alternate paths exist
                topology.hasRedundancy
            }

        val downstreamNodes = countDownstreamNodes(nodeBssid, topology)
        val serviceImpact = downstreamNodes.toDouble() / topology.nodeCount

        return FailureTestResult(
            scenario = FailureScenario.SingleNodeFailure(nodeBssid),
            canRecover = canRecover,
            estimatedRecoveryTime = if (canRecover) 60.0 else null, // 60 seconds typical
            serviceImpact = if (canRecover) serviceImpact * 0.5 else serviceImpact, // Partial impact if recoverable
        )
    }

    private fun testLinkFailure(
        topology: MeshTopology,
        source: String,
        target: String,
    ): FailureTestResult {
        // Link failure is recoverable if alternate paths exist
        val canRecover = topology.hasRedundancy || topology.rootCount > 1

        // Impact depends on how many nodes use this link
        val downstreamNodes = countDownstreamNodes(target, topology)
        val serviceImpact = (downstreamNodes.toDouble() / topology.nodeCount) * 0.7

        return FailureTestResult(
            scenario = FailureScenario.SingleLinkFailure(source, target),
            canRecover = canRecover,
            estimatedRecoveryTime = if (canRecover) 30.0 else null, // 30 seconds for link reroute
            serviceImpact = if (canRecover) serviceImpact * 0.3 else serviceImpact,
        )
    }

    private fun buildSelfHealingRecommendations(
        capability: SelfHealingCapability,
        topology: MeshTopology,
    ): List<String> =
        when (capability) {
            SelfHealingCapability.EXCELLENT -> {
                listOf("Mesh has excellent self-healing capability with redundant paths")
            }
            SelfHealingCapability.GOOD -> {
                listOf("Mesh can recover from most failures", "Consider adding more redundancy for critical deployments")
            }
            SelfHealingCapability.LIMITED -> {
                listOf(
                    "Limited self-healing - some failures may cause service disruption",
                    "Add redundant paths by creating mesh between nodes",
                    "Consider adding additional gateway nodes",
                )
            }
            SelfHealingCapability.NONE -> {
                listOf(
                    "CRITICAL: No self-healing capability - single point of failure",
                    "Add redundant gateway nodes immediately",
                    "Create mesh connections between nodes for alternate paths",
                )
            }
        }
}

// ========================================
// Data Classes & Enums
// ========================================

data class BackhaulAnalysisResult(
    val quality: BackhaulQuality,
    val bottlenecks: List<BackhaulBottleneck>,
    val recommendations: List<String>,
    val wiredPercent: Int,
    val averageLinkQuality: Double,
    val maxHopCount: Int,
)

enum class BackhaulQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    CRITICAL,
}

data class BackhaulBottleneck(
    val link: MeshLink,
    val reason: String,
    val impact: Double, // 0.0-1.0
    val recommendation: String,
) {
    val isCritical: Boolean get() = impact > 0.7
}

data class MeshLoop(
    val nodes: List<String>,
    val type: MeshLoopType,
    val severity: LoopSeverity,
    val recommendation: String,
)

enum class MeshLoopType {
    SIMPLE_CYCLE,
    REDUNDANT_PATHS,
}

enum class LoopSeverity {
    INFO,
    WARNING,
    MODERATE,
    SEVERE,
}

sealed class FailureScenario {
    data class SingleNodeFailure(
        val nodeBssid: String,
    ) : FailureScenario()

    data class SingleLinkFailure(
        val sourceBssid: String,
        val targetBssid: String,
    ) : FailureScenario()
}

data class FailureTestResult(
    val scenario: FailureScenario,
    val canRecover: Boolean,
    val estimatedRecoveryTime: Double?, // Seconds
    val serviceImpact: Double, // 0.0-1.0 (percentage of network affected)
)

data class SelfHealingAssessment(
    val capability: SelfHealingCapability,
    val canRecoverFromSingleNodeFailure: Boolean,
    val canRecoverFromSingleLinkFailure: Boolean,
    val averageRecoveryTimeSeconds: Double,
    val recommendations: List<String>,
)

enum class SelfHealingCapability {
    EXCELLENT,
    GOOD,
    LIMITED,
    NONE,
}
