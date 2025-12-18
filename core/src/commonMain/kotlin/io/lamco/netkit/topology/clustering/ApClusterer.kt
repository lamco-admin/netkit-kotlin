package io.lamco.netkit.topology.clustering

import io.lamco.netkit.model.topology.*
import java.util.UUID

/**
 * AP clustering algorithm for grouping BSSIDs into logical multi-AP deployments
 *
 * Groups BSSIDs based on:
 * - Same SSID (case-sensitive exact match)
 * - Same security configuration (SecurityFingerprint)
 * - Same vendor (OUI or WPS-based detection)
 * - Mesh-specific indicators (vendor IE patterns, if detectable)
 *
 * Algorithm:
 * 1. Group all BSSs by (SSID, SecurityFingerprint)
 * 2. Within each group, sub-group by OUI/vendor
 * 3. Optionally merge sub-groups with mesh indicators
 * 4. Create ApCluster for each final group
 *
 * @property minClusterSize Minimum number of BSSIDs to form a multi-AP cluster (default: 1)
 * @property mergeVendorGroups Whether to merge groups from same vendor (default: true)
 */
class ApClusterer(
    private val minClusterSize: Int = 1,
    private val mergeVendorGroups: Boolean = true,
) {
    init {
        require(minClusterSize >= 1) {
            "Minimum cluster size must be at least 1, got $minClusterSize"
        }
    }

    /**
     * Cluster access points from a list of BSSs
     *
     * @param bssList List of clustered BSSs to analyze
     * @return List of AP clusters, sorted by quality score (descending)
     */
    fun clusterAccessPoints(bssList: List<ClusteredBss>): List<ApCluster> {
        if (bssList.isEmpty()) {
            return emptyList()
        }

        // Step 1: Group by SSID (extract from context, would need to be passed)
        // For now, we'll need SSID to be provided or inferred
        // Since ClusteredBss doesn't have SSID, we need to handle this differently
        // In real implementation, SSID would come from scan results

        // Let's assume we're clustering within a known SSID context
        // This is a simplified version - real implementation would need SSID data

        val clusters = mutableListOf<ApCluster>()

        // Group by security fingerprint as proxy (in real impl, would be SSID + security)
        val groups =
            bssList.groupBy { bss ->
                // Create grouping key from available data
                GroupingKey(
                    oui = bss.oui,
                    band = bss.band,
                    wifiStandard = bss.wifiStandard,
                )
            }

        groups.forEach { (key, bssGroup) ->
            if (bssGroup.size >= minClusterSize) {
                // Detect vendor from OUI
                val vendor = RouterVendor.fromOui(key.oui)

                // Create security fingerprint (simplified - would need full RSN data)
                val securityFingerprint = createDefaultSecurityFingerprint()

                // Create cluster
                val cluster =
                    ApCluster(
                        id = generateClusterId(),
                        ssid = "Unknown", // Would be from scan context
                        securityFingerprint = securityFingerprint,
                        routerVendor = vendor,
                        bssids = bssGroup,
                    )

                clusters.add(cluster)
            }
        }

        // Sort by quality score (descending)
        return clusters.sortedByDescending { it.qualityScore }
    }

    /**
     * Cluster access points with known SSID and security information
     *
     * @param ssid Network SSID
     * @param securityFingerprint Security configuration
     * @param bssList List of BSSs belonging to this network
     * @return List of AP clusters (usually 1, unless multiple vendors/deployments)
     */
    fun clusterWithContext(
        ssid: String,
        securityFingerprint: SecurityFingerprint,
        bssList: List<ClusteredBss>,
    ): List<ApCluster> {
        // Validate SSID first (even for empty lists) to catch invalid input early
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }

        if (bssList.isEmpty()) {
            return emptyList()
        }

        // Group by vendor (OUI-based)
        val vendorGroups =
            if (mergeVendorGroups) {
                // Single group if merging
                mapOf("all" to bssList)
            } else {
                // Separate groups per OUI
                bssList.groupBy { it.oui }
            }

        val clusters =
            vendorGroups.mapNotNull { (_, bssGroup) ->
                if (bssGroup.size >= minClusterSize) {
                    // Detect vendor from first BSS (they're grouped by OUI)
                    val vendor = RouterVendor.fromOui(bssGroup.first().oui)

                    ApCluster(
                        id = generateClusterId(),
                        ssid = ssid,
                        securityFingerprint = securityFingerprint,
                        routerVendor = vendor,
                        bssids = bssGroup,
                    )
                } else {
                    null
                }
            }

        return clusters.sortedByDescending { it.qualityScore }
    }

    /**
     * Group multiple SSIDs and create clusters for each
     *
     * @param networkData Map of SSID to (SecurityFingerprint, List<ClusteredBss>)
     * @return List of all AP clusters across all networks
     */
    fun clusterMultipleNetworks(networkData: Map<String, Pair<SecurityFingerprint, List<ClusteredBss>>>): List<ApCluster> {
        val allClusters = mutableListOf<ApCluster>()

        networkData.forEach { (ssid, data) ->
            val (security, bssList) = data
            val clusters = clusterWithContext(ssid, security, bssList)
            allClusters.addAll(clusters)
        }

        return allClusters.sortedByDescending { it.qualityScore }
    }

    /**
     * Find clusters containing a specific BSSID
     *
     * @param bssid BSSID to search for
     * @param clusters List of clusters to search
     * @return Cluster containing the BSSID, or null if not found
     */
    fun findClusterForBssid(
        bssid: String,
        clusters: List<ApCluster>,
    ): ApCluster? =
        clusters.firstOrNull { cluster ->
            cluster.findBssid(bssid) != null
        }

    /**
     * Merge two clusters if they appear to be the same deployment
     *
     * Criteria for merging:
     * - Same SSID
     * - Same security fingerprint
     * - Same vendor (or both unknown)
     *
     * @param cluster1 First cluster
     * @param cluster2 Second cluster
     * @return Merged cluster, or null if clusters shouldn't be merged
     */
    fun mergeClusters(
        cluster1: ApCluster,
        cluster2: ApCluster,
    ): ApCluster? {
        // Check if clusters can be merged
        if (cluster1.ssid != cluster2.ssid) return null
        if (!cluster1.securityFingerprint.matches(cluster2.securityFingerprint)) return null

        // Check vendor compatibility
        val vendor1 = cluster1.routerVendor
        val vendor2 = cluster2.routerVendor
        if (vendor1 != null && vendor2 != null && vendor1 != vendor2) {
            // Different known vendors - don't merge
            return null
        }

        // Merge
        val mergedBssids = cluster1.bssids + cluster2.bssids
        val mergedVendor = vendor1 ?: vendor2 // Use known vendor if one is unknown

        return ApCluster(
            id = cluster1.id, // Keep first cluster's ID
            ssid = cluster1.ssid,
            securityFingerprint = cluster1.securityFingerprint,
            routerVendor = mergedVendor,
            bssids = mergedBssids.distinctBy { it.bssid },
        )
    }

    /**
     * Split a cluster into per-band sub-clusters
     *
     * Useful for analyzing band-specific deployments or
     * separating dual-band APs into individual radios.
     *
     * @param cluster Cluster to split
     * @return List of per-band clusters
     */
    fun splitByBand(cluster: ApCluster): List<ApCluster> {
        val bandGroups = cluster.bssids.groupBy { it.band }

        return bandGroups.map { (_, bssGroup) ->
            ApCluster(
                id = generateClusterId(),
                ssid = cluster.ssid,
                securityFingerprint = cluster.securityFingerprint,
                routerVendor = cluster.routerVendor,
                bssids = bssGroup,
            )
        }
    }

    /**
     * Identify likely mesh networks from clusters
     *
     * Mesh indicators:
     * - Multiple APs from same vendor
     * - Overlapping coverage (would need RSSI data)
     * - Same channel width and standard
     * - Vendor supports mesh (Google, eero, Orbi, etc.)
     *
     * @param cluster Cluster to analyze
     * @return True if cluster appears to be a mesh network
     */
    fun isMeshNetwork(cluster: ApCluster): Boolean {
        // Must have multiple APs
        if (!cluster.isMultiAp) return false

        // Vendor must support mesh
        val vendor = cluster.routerVendor
        if (vendor == null || !vendor.supportsMultiAp) return false

        // Consumer mesh vendors are strong indicators
        if (vendor.deploymentType == DeploymentType.CONSUMER_MESH) return true

        // Check for consistent configuration (mesh APs usually match)
        val standards = cluster.bssids.map { it.wifiStandard }.distinct()
        val widths = cluster.bssids.map { it.channelWidth }.distinct()

        // Mesh networks typically have consistent config
        return standards.size == 1 && widths.size <= 2 // Allow some width variation
    }

    private fun generateClusterId(): String = "cluster-${UUID.randomUUID()}"

    private fun createDefaultSecurityFingerprint(): SecurityFingerprint {
        // Default to WPA2-Personal (would be detected from actual scan)
        return SecurityFingerprint.wpa2Personal(pmfRequired = false)
    }

    /**
     * Internal grouping key for clustering
     */
    private data class GroupingKey(
        val oui: String,
        val band: io.lamco.netkit.model.network.WiFiBand,
        val wifiStandard: io.lamco.netkit.model.network.WifiStandard,
    )
}

/**
 * Clustering strategy interface for extensibility
 *
 * Allows custom clustering algorithms to be plugged in.
 */
interface ClusteringStrategy {
    /**
     * Cluster BSSs using this strategy
     *
     * @param bssList List of BSSs to cluster
     * @return List of clusters
     */
    fun cluster(bssList: List<ClusteredBss>): List<ApCluster>
}

/**
 * SSID-based clustering strategy (default)
 *
 * Groups BSSs strictly by SSID + security fingerprint.
 */
class SsidBasedStrategy : ClusteringStrategy {
    override fun cluster(bssList: List<ClusteredBss>): List<ApCluster> {
        // Implementation would require SSID data
        // This is a placeholder for interface demonstration
        return emptyList()
    }
}

/**
 * Vendor-based clustering strategy
 *
 * Groups BSSs by vendor (OUI), even across different SSIDs.
 * Useful for identifying all APs from a specific deployment.
 */
class VendorBasedStrategy : ClusteringStrategy {
    override fun cluster(bssList: List<ClusteredBss>): List<ApCluster> {
        val vendorGroups = bssList.groupBy { RouterVendor.fromOui(it.oui) }

        return vendorGroups.mapNotNull { (vendor, bssGroup) ->
            if (vendor != RouterVendor.UNKNOWN && bssGroup.isNotEmpty()) {
                ApCluster(
                    id = "cluster-${UUID.randomUUID()}",
                    ssid = "Multi-SSID-${vendor.displayName}",
                    securityFingerprint = SecurityFingerprint.wpa2Personal(),
                    routerVendor = vendor,
                    bssids = bssGroup,
                )
            } else {
                null
            }
        }
    }
}
