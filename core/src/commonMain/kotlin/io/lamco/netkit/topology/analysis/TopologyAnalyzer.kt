package io.lamco.netkit.topology.analysis

import io.lamco.netkit.model.topology.*

/**
 * High-level topology analysis for multi-AP deployments
 *
 * Analyzes AP relationships, deployment types, and overall network topology.
 * Builds topology maps showing primary/secondary AP relationships and coverage patterns.
 */
class TopologyAnalyzer {
    /**
     * Analyze complete network topology
     *
     * @param clusters List of AP clusters to analyze
     * @return Topology analysis results
     */
    fun analyzeTopology(clusters: List<ApCluster>): TopologyAnalysis {
        val totalAps = clusters.sumOf { it.apCount }
        val deploymentTypes = clusters.map { it.deploymentType }.distinct()

        val hasEnterprise = clusters.any { it.deploymentType == ClusterDeploymentType.ENTERPRISE }
        val hasMesh = clusters.any { it.deploymentType == ClusterDeploymentType.CONSUMER_MESH }
        val hasWiFi7 = clusters.any { it.supportsWiFi7 }
        val hasWiFi6E = clusters.any { it.supportsWiFi6E }

        val fastRoamingSupport = clusters.count { it.supportsFastRoaming }
        val fastRoamingPercentage =
            if (clusters.isNotEmpty()) {
                (fastRoamingSupport.toDouble() / clusters.size * 100).toInt()
            } else {
                0
            }

        return TopologyAnalysis(
            totalClusters = clusters.size,
            totalAccessPoints = totalAps,
            deploymentTypes = deploymentTypes,
            hasEnterpriseDeployment = hasEnterprise,
            hasMeshDeployment = hasMesh,
            supportsWiFi7 = hasWiFi7,
            supportsWiFi6E = hasWiFi6E,
            fastRoamingCoverage = fastRoamingPercentage,
            averageClusterSize = if (clusters.isNotEmpty()) totalAps.toDouble() / clusters.size else 0.0,
            overallQuality = calculateOverallQuality(clusters),
        )
    }

    /**
     * Identify primary and secondary APs in a cluster
     *
     * Primary AP is typically:
     * - Strongest signal (if RSSI available)
     * - Lowest band (2.4 GHz preferred for range)
     * - Most capable (WiFi 7 > 6E > 6 > 5 > 4)
     */
    fun identifyPrimaryAp(cluster: ApCluster): ClusteredBss? {
        if (cluster.bssids.isEmpty()) return null
        if (cluster.bssids.size == 1) return cluster.bssids.first()

        // Prefer strongest signal if available
        val strongest = cluster.strongestBssid
        if (strongest != null && strongest.rssiDbm != null && strongest.rssiDbm!! >= -60) {
            return strongest
        }

        // Otherwise, prefer 2.4 GHz for range (primary gateway)
        val twoFourGhz = cluster.getBssidsByBand(io.lamco.netkit.model.network.WiFiBand.BAND_2_4GHZ)
        if (twoFourGhz.isNotEmpty()) {
            return twoFourGhz.maxByOrNull { it.wifiStandard.generation }
        }

        // Fall back to most capable AP
        return cluster.bssids.maxByOrNull { it.wifiStandard.generation }
    }

    /**
     * Calculate coverage overlap between clusters
     *
     * Useful for identifying interference and roaming zones.
     */
    fun calculateClusterOverlap(
        cluster1: ApCluster,
        cluster2: ApCluster,
    ): ClusterOverlap {
        // Check if clusters share any bands
        val sharedBands = cluster1.usedBands.intersect(cluster2.usedBands)

        val hasOverlap = sharedBands.isNotEmpty()
        val overlapBands = sharedBands.toList()

        return ClusterOverlap(
            cluster1Id = cluster1.id,
            cluster2Id = cluster2.id,
            hasOverlap = hasOverlap,
            overlappingBands = overlapBands,
            potentialInterference = hasOverlap && cluster1.ssid != cluster2.ssid,
        )
    }

    private fun calculateOverallQuality(clusters: List<ApCluster>): Int {
        if (clusters.isEmpty()) return 0
        return clusters.map { it.qualityScore }.average().toInt()
    }
}

/**
 * Results of topology analysis
 */
data class TopologyAnalysis(
    val totalClusters: Int,
    val totalAccessPoints: Int,
    val deploymentTypes: List<ClusterDeploymentType>,
    val hasEnterpriseDeployment: Boolean,
    val hasMeshDeployment: Boolean,
    val supportsWiFi7: Boolean,
    val supportsWiFi6E: Boolean,
    val fastRoamingCoverage: Int, // Percentage of clusters with fast roaming
    val averageClusterSize: Double,
    val overallQuality: Int, // 0-100
) {
    /**
     * Whether this is a complex multi-AP environment
     */
    val isComplexEnvironment: Boolean
        get() = totalAccessPoints >= 5 || hasEnterpriseDeployment

    /**
     * Whether this is a modern deployment
     */
    val isModernDeployment: Boolean
        get() = supportsWiFi6E || supportsWiFi7 || fastRoamingCoverage >= 50
}

/**
 * Overlap analysis between two clusters
 */
data class ClusterOverlap(
    val cluster1Id: String,
    val cluster2Id: String,
    val hasOverlap: Boolean,
    val overlappingBands: List<io.lamco.netkit.model.network.WiFiBand>,
    val potentialInterference: Boolean, // Different SSIDs on same band
)
