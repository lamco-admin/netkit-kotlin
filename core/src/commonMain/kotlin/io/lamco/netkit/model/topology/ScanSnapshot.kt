package io.lamco.netkit.model.topology

import io.lamco.netkit.model.network.WiFiBand

/**
 * Point-in-time snapshot of the WiFi environment
 *
 * Captures the complete state of all detected networks, access points,
 * and their metrics at a specific moment. Used for time-series analysis,
 * trend detection, and historical comparisons.
 *
 * A snapshot is considered "complete" when it includes scan results from
 * all available bands (2.4/5/6 GHz). Partial snapshots may occur during
 * active scanning or when certain bands are unavailable.
 *
 * @property id Unique identifier for this snapshot
 * @property timestampMillis When this snapshot was captured (epoch millis)
 * @property clusters All AP clusters detected in this scan
 * @property connectedBssid BSSID currently connected to (if any)
 * @property scanDurationMillis Time taken to complete the scan
 * @property scannedBands Frequency bands included in this scan
 * @property deviceLocation Optional device location when snapshot was taken
 */
data class ScanSnapshot(
    val id: String,
    val timestampMillis: Long,
    val clusters: List<ApCluster>,
    val connectedBssid: String? = null,
    val scanDurationMillis: Long = 0,
    val scannedBands: Set<WiFiBand> = emptySet(),
    val deviceLocation: GeoPoint? = null,
) {
    init {
        require(id.isNotBlank()) {
            "Snapshot ID must not be blank"
        }
        require(timestampMillis > 0) {
            "Timestamp must be positive, got $timestampMillis"
        }
        require(scanDurationMillis >= 0) {
            "Scan duration must be non-negative, got $scanDurationMillis"
        }
    }

    /**
     * All individual BSSIDs detected across all clusters
     */
    val allBssids: List<ClusteredBss>
        get() = clusters.flatMap { it.bssids }

    /**
     * Total number of unique access points detected
     */
    val totalApCount: Int
        get() = allBssids.size

    /**
     * Total number of unique SSIDs (networks) detected
     */
    val uniqueSsidCount: Int
        get() = clusters.map { it.ssid }.distinct().size

    /**
     * Whether this is a complete scan (includes all common bands)
     */
    val isCompleteScan: Boolean
        get() =
            scannedBands.containsAll(
                listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ),
            )

    /**
     * Whether device was connected to a network during this scan
     */
    val isConnected: Boolean
        get() = connectedBssid != null

    /**
     * The cluster containing the currently connected BSSID (if connected)
     */
    val connectedCluster: ApCluster?
        get() =
            if (connectedBssid != null) {
                clusters.find { cluster ->
                    cluster.bssids.any { it.bssid == connectedBssid }
                }
            } else {
                null
            }

    /**
     * The specific BSS currently connected to (if connected)
     */
    val connectedBss: ClusteredBss?
        get() =
            if (connectedBssid != null) {
                allBssids.find { it.bssid == connectedBssid }
            } else {
                null
            }

    /**
     * Clusters by frequency band
     */
    fun clustersByBand(band: WiFiBand): List<ApCluster> =
        clusters.filter { cluster ->
            cluster.bssids.any { it.band == band }
        }

    /**
     * BSSIDs with signal strength above threshold
     */
    fun strongSignals(thresholdDbm: Int = -70): List<ClusteredBss> =
        allBssids.filter { bss ->
            bss.rssiDbm != null && bss.rssiDbm >= thresholdDbm
        }

    /**
     * Enterprise-grade clusters (supporting 802.11k/r/v)
     */
    val enterpriseClusters: List<ApCluster>
        get() =
            clusters.filter { cluster ->
                cluster.bssids.any { it.roamingCapabilities.hasFastRoaming }
            }

    /**
     * WiFi 6E/7 capable clusters
     */
    val modernWiFiClusters: List<ApCluster>
        get() =
            clusters.filter { cluster ->
                cluster.supportsWiFi6E || cluster.supportsWiFi7
            }

    /**
     * Clusters using insecure authentication (Open, WEP)
     */
    val insecureClusters: List<ApCluster>
        get() =
            clusters.filter { cluster ->
                !cluster.securityFingerprint.isSecure
            }

    /**
     * Average RSSI across all detected BSSIDs
     */
    val averageRssiDbm: Double?
        get() {
            val rssiValues = allBssids.mapNotNull { it.rssiDbm }
            return if (rssiValues.isNotEmpty()) {
                rssiValues.average()
            } else {
                null
            }
        }

    /**
     * Strongest signal detected in this snapshot
     */
    val strongestSignalDbm: Int?
        get() = allBssids.mapNotNull { it.rssiDbm }.maxOrNull()

    /**
     * Weakest signal detected in this snapshot
     */
    val weakestSignalDbm: Int?
        get() = allBssids.mapNotNull { it.rssiDbm }.minOrNull()

    /**
     * Snapshot age in milliseconds relative to current time
     */
    fun ageMillis(currentTimeMillis: Long = System.currentTimeMillis()): Long = currentTimeMillis - timestampMillis

    /**
     * Whether this snapshot is stale (older than threshold)
     */
    fun isStale(
        thresholdMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Boolean = ageMillis(currentTimeMillis) > thresholdMillis

    /**
     * Compare this snapshot to another to detect changes
     */
    fun compareWith(other: ScanSnapshot): SnapshotComparison {
        val addedBssids = allBssids.map { it.bssid }.toSet() - other.allBssids.map { it.bssid }.toSet()
        val removedBssids = other.allBssids.map { it.bssid }.toSet() - allBssids.map { it.bssid }.toSet()
        val persistentBssids = allBssids.map { it.bssid }.toSet().intersect(other.allBssids.map { it.bssid }.toSet())

        return SnapshotComparison(
            olderSnapshot = other,
            newerSnapshot = this,
            addedBssids = addedBssids,
            removedBssids = removedBssids,
            persistentBssids = persistentBssids,
            timeElapsedMillis = timestampMillis - other.timestampMillis,
        )
    }

    /**
     * Human-readable snapshot summary
     */
    val summary: String
        get() =
            buildString {
                append("Scan @ $timestampMillis: ")
                append("$totalApCount APs, $uniqueSsidCount networks")
                if (isConnected) {
                    append(" [Connected: $connectedBssid]")
                }
                if (scannedBands.isNotEmpty()) {
                    append(" [${scannedBands.joinToString(", ") { it.displayName }}]")
                }
            }

    companion object {
        /**
         * Create a snapshot ID from timestamp and optional suffix
         */
        fun createId(
            timestampMillis: Long,
            suffix: String = "",
        ): String =
            if (suffix.isNotBlank()) {
                "snap_${timestampMillis}_$suffix"
            } else {
                "snap_$timestampMillis"
            }
    }
}

/**
 * Comparison between two snapshots
 *
 * Identifies what changed between two point-in-time network scans,
 * useful for detecting AP churn, roaming events, and network stability.
 *
 * @property olderSnapshot Earlier snapshot in time
 * @property newerSnapshot Later snapshot in time
 * @property addedBssids BSSIDs present in newer but not older
 * @property removedBssids BSSIDs present in older but not newer
 * @property persistentBssids BSSIDs present in both snapshots
 * @property timeElapsedMillis Time between the two snapshots
 */
data class SnapshotComparison(
    val olderSnapshot: ScanSnapshot,
    val newerSnapshot: ScanSnapshot,
    val addedBssids: Set<String>,
    val removedBssids: Set<String>,
    val persistentBssids: Set<String>,
    val timeElapsedMillis: Long,
) {
    init {
        require(timeElapsedMillis >= 0) {
            "Time elapsed must be non-negative, got $timeElapsedMillis"
        }
    }

    /**
     * Whether any BSSIDs were added
     */
    val hasAdditions: Boolean
        get() = addedBssids.isNotEmpty()

    /**
     * Whether any BSSIDs were removed
     */
    val hasRemovals: Boolean
        get() = removedBssids.isNotEmpty()

    /**
     * Whether the network environment changed
     */
    val hasChanges: Boolean
        get() = hasAdditions || hasRemovals

    /**
     * AP churn rate (additions + removals per second)
     */
    val churnRate: Double
        get() {
            val churnCount = addedBssids.size + removedBssids.size
            val timeSeconds = timeElapsedMillis / 1000.0
            return if (timeSeconds > 0) churnCount / timeSeconds else 0.0
        }

    /**
     * Stability score (0-1, higher = more stable)
     * Based on ratio of persistent to total BSSIDs
     */
    val stabilityScore: Double
        get() {
            val totalBssids = persistentBssids.size + addedBssids.size + removedBssids.size
            return if (totalBssids > 0) {
                persistentBssids.size.toDouble() / totalBssids
            } else {
                1.0
            }
        }

    /**
     * Environment stability category
     */
    val stability: EnvironmentStability
        get() =
            when {
                stabilityScore >= 0.95 -> EnvironmentStability.VERY_STABLE
                stabilityScore >= 0.85 -> EnvironmentStability.STABLE
                stabilityScore >= 0.70 -> EnvironmentStability.MODERATE
                stabilityScore >= 0.50 -> EnvironmentStability.UNSTABLE
                else -> EnvironmentStability.VERY_UNSTABLE
            }

    /**
     * Signal changes for persistent BSSIDs
     */
    fun signalChanges(): List<SignalChange> =
        persistentBssids.mapNotNull { bssid ->
            val oldBss = olderSnapshot.allBssids.find { it.bssid == bssid }
            val newBss = newerSnapshot.allBssids.find { it.bssid == bssid }

            if (oldBss != null &&
                newBss != null &&
                oldBss.rssiDbm != null &&
                newBss.rssiDbm != null
            ) {
                SignalChange(
                    bssid = bssid,
                    oldRssiDbm = oldBss.rssiDbm,
                    newRssiDbm = newBss.rssiDbm,
                    changeDbm = newBss.rssiDbm - oldBss.rssiDbm,
                    timeElapsedMillis = timeElapsedMillis,
                )
            } else {
                null
            }
        }

    /**
     * Human-readable comparison summary
     */
    val summary: String
        get() =
            buildString {
                append("Δ${timeElapsedMillis}ms: ")
                if (hasAdditions) append("+${addedBssids.size} ")
                if (hasRemovals) append("-${removedBssids.size} ")
                append("=${persistentBssids.size} ")
                append("(${stability.displayName})")
            }
}

/**
 * Signal strength change for a specific BSSID between snapshots
 *
 * @property bssid The BSSID being tracked
 * @property oldRssiDbm RSSI in older snapshot
 * @property newRssiDbm RSSI in newer snapshot
 * @property changeDbm Signal change (positive = stronger, negative = weaker)
 * @property timeElapsedMillis Time between measurements
 */
data class SignalChange(
    val bssid: String,
    val oldRssiDbm: Int,
    val newRssiDbm: Int,
    val changeDbm: Int,
    val timeElapsedMillis: Long,
) {
    init {
        require(bssid.isNotBlank()) {
            "BSSID must not be blank"
        }
        require(oldRssiDbm in -120..0) {
            "Old RSSI must be in range [-120, 0] dBm, got $oldRssiDbm"
        }
        require(newRssiDbm in -120..0) {
            "New RSSI must be in range [-120, 0] dBm, got $newRssiDbm"
        }
        require(timeElapsedMillis >= 0) {
            "Time elapsed must be non-negative, got $timeElapsedMillis"
        }
    }

    /**
     * Whether signal improved (got stronger)
     */
    val isImprovement: Boolean
        get() = changeDbm > 0

    /**
     * Whether signal degraded (got weaker)
     */
    val isDegradation: Boolean
        get() = changeDbm < 0

    /**
     * Whether signal changed significantly (>= 5 dB change)
     */
    val isSignificantChange: Boolean
        get() = kotlin.math.abs(changeDbm) >= 5

    /**
     * Rate of signal change (dB per second)
     */
    val changeRateDbPerSecond: Double
        get() {
            val timeSeconds = timeElapsedMillis / 1000.0
            return if (timeSeconds > 0) changeDbm / timeSeconds else 0.0
        }

    /**
     * Change severity category
     */
    val severity: ChangeSeverity
        get() =
            when {
                kotlin.math.abs(changeDbm) >= 20 -> ChangeSeverity.CRITICAL
                kotlin.math.abs(changeDbm) >= 10 -> ChangeSeverity.HIGH
                kotlin.math.abs(changeDbm) >= 5 -> ChangeSeverity.MODERATE
                kotlin.math.abs(changeDbm) >= 2 -> ChangeSeverity.LOW
                else -> ChangeSeverity.MINIMAL
            }

    /**
     * Human-readable change summary
     */
    val summary: String
        get() {
            val sign = if (changeDbm >= 0) "+" else ""
            return "$bssid: ${sign}${changeDbm}dB ($oldRssiDbm → $newRssiDbm)"
        }
}

/**
 * Environment stability categories
 */
enum class EnvironmentStability(
    val displayName: String,
    val minStabilityScore: Double,
) {
    /** Very stable (>= 95% persistent APs) */
    VERY_STABLE("Very Stable", 0.95),

    /** Stable (>= 85% persistent APs) */
    STABLE("Stable", 0.85),

    /** Moderate (>= 70% persistent APs) */
    MODERATE("Moderate", 0.70),

    /** Unstable (>= 50% persistent APs) */
    UNSTABLE("Unstable", 0.50),

    /** Very unstable (< 50% persistent APs) */
    VERY_UNSTABLE("Very Unstable", 0.0),
    ;

    /**
     * Whether this stability level is acceptable for reliable operation
     */
    val isAcceptable: Boolean
        get() = this in listOf(VERY_STABLE, STABLE, MODERATE)

    companion object {
        /**
         * Determine stability from score
         */
        fun fromScore(score: Double): EnvironmentStability =
            when {
                score >= 0.95 -> VERY_STABLE
                score >= 0.85 -> STABLE
                score >= 0.70 -> MODERATE
                score >= 0.50 -> UNSTABLE
                else -> VERY_UNSTABLE
            }
    }
}

/**
 * Signal change severity categories
 */
enum class ChangeSeverity(
    val displayName: String,
    val minAbsChangeDb: Int,
) {
    /** Critical change (>= 20 dB) */
    CRITICAL("Critical", 20),

    /** High severity (>= 10 dB) */
    HIGH("High", 10),

    /** Moderate severity (>= 5 dB) */
    MODERATE("Moderate", 5),

    /** Low severity (>= 2 dB) */
    LOW("Low", 2),

    /** Minimal change (< 2 dB) */
    MINIMAL("Minimal", 0),
    ;

    companion object {
        /**
         * Determine severity from absolute change
         */
        fun fromAbsChange(absChangeDb: Int): ChangeSeverity =
            when {
                absChangeDb >= 20 -> CRITICAL
                absChangeDb >= 10 -> HIGH
                absChangeDb >= 5 -> MODERATE
                absChangeDb >= 2 -> LOW
                else -> MINIMAL
            }
    }
}
