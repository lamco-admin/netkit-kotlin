package io.lamco.netkit.model.topology

/**
 * Group of BSSIDs that belong to the same logical AP deployment
 *
 * Represents a multi-AP network where multiple access points work together
 * to provide coverage under the same SSID. This could be:
 * - Enterprise deployment with multiple managed APs
 * - Consumer mesh system (Google WiFi, eero, Orbi, etc.)
 * - Traditional AP + extender setup
 * - WiFi 7 MLO deployment (multiple bands on same AP)
 *
 * BSSIDs are clustered based on:
 * - Same SSID
 * - Same security configuration (SecurityFingerprint)
 * - Same OUI or vendor (for consumer products)
 * - Vendor-specific mesh indicators (if detectable)
 *
 * @property id Unique identifier for this cluster
 * @property ssid Network SSID (Service Set Identifier)
 * @property securityFingerprint Security configuration fingerprint
 * @property routerVendor Detected router manufacturer/vendor
 * @property bssids List of BSSIDs in this cluster
 */
data class ApCluster(
    val id: String,
    val ssid: String,
    val securityFingerprint: SecurityFingerprint,
    val routerVendor: RouterVendor?,
    val bssids: List<ClusteredBss>,
) {
    init {
        require(id.isNotBlank()) {
            "Cluster ID must not be blank"
        }
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
        require(bssids.isNotEmpty()) {
            "Cluster must have at least one BSSID"
        }
    }

    /**
     * Number of access points in this cluster
     */
    val apCount: Int
        get() = bssids.size

    /**
     * Whether this is a multi-AP deployment
     */
    val isMultiAp: Boolean
        get() = bssids.size > 1

    /**
     * Whether this is a single-band deployment (all APs on same band)
     */
    val isSingleBand: Boolean
        get() = bssids.map { it.band }.distinct().size == 1

    /**
     * Whether this is a dual-band deployment
     */
    val isDualBand: Boolean
        get() = bssids.map { it.band }.distinct().size == 2

    /**
     * Whether this is a tri-band deployment
     */
    val isTriBand: Boolean
        get() = bssids.map { it.band }.distinct().size >= 3

    /**
     * All bands used by this cluster
     */
    val usedBands: Set<io.lamco.netkit.model.network.WiFiBand>
        get() = bssids.map { it.band }.toSet()

    /**
     * Whether this cluster supports WiFi 6 or newer
     */
    val supportsWiFi6: Boolean
        get() = bssids.any { it.supportsWiFi6 }

    /**
     * Whether this cluster supports WiFi 6E (6 GHz)
     */
    val supportsWiFi6E: Boolean
        get() = bssids.any { it.isWiFi6E }

    /**
     * Whether this cluster supports WiFi 7 (MLO)
     */
    val supportsWiFi7: Boolean
        get() = bssids.any { it.isWiFi7 }

    /**
     * Whether this is an enterprise deployment (based on roaming capabilities)
     */
    val isEnterprise: Boolean
        get() = bssids.any { it.isEnterprise }

    /**
     * Whether this is a mesh deployment
     */
    val isMesh: Boolean
        get() = bssids.any { it.isMesh }

    /**
     * Whether any AP in this cluster supports fast roaming
     */
    val supportsFastRoaming: Boolean
        get() = bssids.any { it.roamingCapabilities.hasFastRoaming }

    /**
     * Best roaming capabilities across all APs
     * (Returns the AP with highest roaming score)
     */
    val bestRoamingCapabilities: RoamingCapabilities
        get() =
            bssids
                .map { it.roamingCapabilities }
                .maxByOrNull { it.roamingScore } ?: RoamingCapabilities.none()

    /**
     * Cluster deployment type
     */
    val deploymentType: ClusterDeploymentType
        get() =
            when {
                supportsWiFi7 && bssids.any { it.mloInfo != null } -> ClusterDeploymentType.WIFI7_MLO
                routerVendor?.deploymentType == DeploymentType.ENTERPRISE -> ClusterDeploymentType.ENTERPRISE
                routerVendor?.deploymentType == DeploymentType.CONSUMER_MESH && isMultiAp -> ClusterDeploymentType.CONSUMER_MESH
                isMultiAp -> ClusterDeploymentType.MULTI_AP_UNKNOWN
                else -> ClusterDeploymentType.SINGLE_AP
            }

    /**
     * Active BSSIDs (seen within last 60 seconds)
     */
    fun getActiveBssids(currentTimeMillis: Long = System.currentTimeMillis()): List<ClusteredBss> =
        bssids.filter { it.isActive(currentTimeMillis) }

    /**
     * BSSIDs on a specific band
     */
    fun getBssidsByBand(band: io.lamco.netkit.model.network.WiFiBand): List<ClusteredBss> = bssids.filter { it.band == band }

    /**
     * BSSID with strongest signal (highest RSSI)
     */
    val strongestBssid: ClusteredBss?
        get() =
            bssids
                .filter { it.rssiDbm != null }
                .maxByOrNull { it.rssiDbm ?: -999 }

    /**
     * Average RSSI across all BSSIDs with signal data
     */
    val averageRssiDbm: Double?
        get() {
            val rssiValues = bssids.mapNotNull { it.rssiDbm }
            return if (rssiValues.isEmpty()) null else rssiValues.average()
        }

    /**
     * Cluster quality score (0-100)
     * Based on: multi-AP coverage, fast roaming, modern WiFi standards, security
     */
    val qualityScore: Int
        get() {
            var score = 0

            // Multi-AP coverage (0-25 points)
            score +=
                when {
                    isTriBand -> 25
                    isDualBand -> 20
                    isMultiAp -> 15
                    else -> 10
                }

            // Fast roaming support (0-25 points)
            score += bestRoamingCapabilities.roamingScore / 4

            // WiFi standards (0-25 points)
            score +=
                when {
                    supportsWiFi7 -> 25
                    supportsWiFi6E -> 20
                    bssids.any { it.wifiStandard.generation >= 6 } -> 15
                    bssids.any { it.wifiStandard.generation >= 5 } -> 10
                    else -> 5
                }

            // Security (0-25 points)
            score += securityFingerprint.securityScore / 4

            return score.coerceIn(0, 100)
        }

    /**
     * Overall cluster quality category
     */
    val qualityCategory: ClusterQuality
        get() =
            when {
                qualityScore >= 80 -> ClusterQuality.EXCELLENT
                qualityScore >= 65 -> ClusterQuality.GOOD
                qualityScore >= 50 -> ClusterQuality.FAIR
                else -> ClusterQuality.POOR
            }

    /**
     * Human-readable cluster summary
     */
    val summary: String
        get() =
            buildString {
                append("\"$ssid\" - ")
                append("${deploymentType.displayName} ")
                append("($apCount ${if (apCount == 1) "AP" else "APs"}")
                if (isMultiAp) {
                    append(", ${usedBands.joinToString("+") { it.displayName }}")
                }
                append(")")
                if (routerVendor != null && routerVendor != RouterVendor.UNKNOWN) {
                    append(" - ${routerVendor.displayName}")
                }
            }

    /**
     * Find BSSID by MAC address
     */
    fun findBssid(bssidMac: String): ClusteredBss? = bssids.firstOrNull { it.bssid.equals(bssidMac, ignoreCase = true) }
}

/**
 * Cluster deployment type categories
 */
enum class ClusterDeploymentType(
    val displayName: String,
) {
    /** WiFi 7 MLO deployment */
    WIFI7_MLO("WiFi 7 MLO"),

    /** Enterprise deployment (Cisco, Ubiquiti, Aruba, etc.) */
    ENTERPRISE("Enterprise"),

    /** Consumer mesh system (Google WiFi, eero, Orbi, etc.) */
    CONSUMER_MESH("Consumer Mesh"),

    /** Multi-AP but vendor/type unknown */
    MULTI_AP_UNKNOWN("Multi-AP Network"),

    /** Single access point */
    SINGLE_AP("Single AP"),
    ;

    /**
     * Whether this deployment type typically supports coordinated roaming
     */
    val supportsCoordinatedRoaming: Boolean
        get() =
            when (this) {
                WIFI7_MLO, ENTERPRISE, CONSUMER_MESH -> true
                else -> false
            }
}

/**
 * Cluster quality categories
 */
enum class ClusterQuality(
    val displayName: String,
) {
    /** Excellent: Modern standards, multi-band, fast roaming, strong security */
    EXCELLENT("Excellent"),

    /** Good: Multi-AP or good standards with adequate roaming */
    GOOD("Good"),

    /** Fair: Basic multi-AP or single AP with modern standards */
    FAIR("Fair"),

    /** Poor: Single-band, old standards, or weak security */
    POOR("Poor"),
}
