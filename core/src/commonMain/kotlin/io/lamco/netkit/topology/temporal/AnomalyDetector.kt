package io.lamco.netkit.topology.temporal

import io.lamco.netkit.model.topology.*
import kotlin.math.abs

/**
 * Anomaly detection engine for identifying unusual WiFi network behavior
 *
 * Detects sudden, unexpected changes that deviate from normal patterns:
 * - Sudden signal drops or spikes
 * - Unexpected AP disappearances
 * - Unusual configuration changes
 * - Abnormal roaming patterns
 * - Security downgrades
 *
 * Unlike TrendAnalyzer which focuses on gradual patterns, AnomalyDetector
 * identifies point-in-time deviations from expected behavior.
 *
 * @property suddenDropThresholdDb RSSI drop considered sudden anomaly (default: 15 dB)
 * @property suddenSpikeThresholdDb RSSI spike considered unusual (default: 20 dB)
 * @property churnThresholdPercentage AP churn percentage considered anomalous (default: 50%)
 * @property roamingLatencyAnomalyMs Roaming latency considered anomalous (default: 3000ms)
 */
class AnomalyDetector(
    val suddenDropThresholdDb: Int = 15,
    val suddenSpikeThresholdDb: Int = 20,
    val churnThresholdPercentage: Double = 50.0,
    val roamingLatencyAnomalyMs: Long = 3000,
) {
    init {
        require(suddenDropThresholdDb > 0) {
            "Sudden drop threshold must be positive, got $suddenDropThresholdDb"
        }
        require(suddenSpikeThresholdDb > 0) {
            "Sudden spike threshold must be positive, got $suddenSpikeThresholdDb"
        }
        require(churnThresholdPercentage in 0.0..100.0) {
            "Churn threshold percentage must be in [0, 100], got $churnThresholdPercentage"
        }
        require(roamingLatencyAnomalyMs > 0) {
            "Roaming latency anomaly threshold must be positive, got $roamingLatencyAnomalyMs"
        }
    }

    /**
     * Detect anomalies in a snapshot comparison
     *
     * Identifies unusual changes between two consecutive network scans.
     */
    fun detectSnapshotAnomalies(comparison: SnapshotComparison): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()

        val totalBssids =
            comparison.persistentBssids.size +
                comparison.addedBssids.size +
                comparison.removedBssids.size

        if (totalBssids > 0) {
            val churnPercentage =
                ((comparison.addedBssids.size + comparison.removedBssids.size).toDouble() / totalBssids) * 100.0

            if (churnPercentage >= churnThresholdPercentage) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.MASSIVE_AP_CHURN,
                        severity = if (churnPercentage >= 75.0) AnomalySeverity.CRITICAL else AnomalySeverity.HIGH,
                        timestampMillis = comparison.newerSnapshot.timestampMillis,
                        description = "Massive AP churn detected: ${churnPercentage.toInt()}% of network changed",
                        affectedBssids = comparison.addedBssids + comparison.removedBssids,
                        value = churnPercentage,
                    ),
                )
            }
        }

        val signalChanges = comparison.signalChanges()
        signalChanges.forEach { change ->
            if (change.isDegradation && abs(change.changeDbm) >= suddenDropThresholdDb) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.SUDDEN_SIGNAL_DROP,
                        severity =
                            when {
                                abs(change.changeDbm) >= 30 -> AnomalySeverity.CRITICAL
                                abs(change.changeDbm) >= 20 -> AnomalySeverity.HIGH
                                else -> AnomalySeverity.MEDIUM
                            },
                        timestampMillis = comparison.newerSnapshot.timestampMillis,
                        description = "Sudden signal drop: ${change.bssid} dropped ${abs(change.changeDbm)}dB",
                        affectedBssids = setOf(change.bssid),
                        value = change.changeDbm.toDouble(),
                    ),
                )
            }

            if (change.isImprovement && change.changeDbm >= suddenSpikeThresholdDb) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.SUDDEN_SIGNAL_SPIKE,
                        severity = AnomalySeverity.LOW,
                        timestampMillis = comparison.newerSnapshot.timestampMillis,
                        description = "Sudden signal spike: ${change.bssid} improved ${change.changeDbm}dB (possible measurement error)",
                        affectedBssids = setOf(change.bssid),
                        value = change.changeDbm.toDouble(),
                    ),
                )
            }
        }

        return anomalies.sortedByDescending { it.severity }
    }

    /**
     * Detect anomalies in AP history
     *
     * Identifies unusual patterns in a single AP's historical data.
     */
    fun detectApHistoryAnomalies(history: ApHistory): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()

        if (history.rssiStandardDeviation >= 20.0 && history.observationCount >= 10) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.EXTREME_SIGNAL_VARIANCE,
                    severity = AnomalySeverity.MEDIUM,
                    timestampMillis = history.lastSeenTimestamp,
                    description = "Extreme signal variance: ${history.bssid} has ${history.rssiStandardDeviation.toInt()}dB std dev",
                    affectedBssids = setOf(history.bssid),
                    value = history.rssiStandardDeviation,
                ),
            )
        }

        if (history.disconnectionCount >= 5 && history.connectionCount > 0) {
            val disconnectRate = history.disconnectionCount.toDouble() / history.connectionCount
            if (disconnectRate >= 0.5) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.FREQUENT_DISCONNECTIONS,
                        severity = AnomalySeverity.HIGH,
                        timestampMillis = history.lastSeenTimestamp,
                        description = "Frequent disconnections: ${history.bssid} has ${history.disconnectionCount} disconnects vs ${history.connectionCount} connections",
                        affectedBssids = setOf(history.bssid),
                        value = disconnectRate * 100.0,
                    ),
                )
            }
        }

        // Configuration changes may indicate security issues (especially downgrades)
        history.configurationChanges.forEach { change ->
            if (change.isSecurityChange) {
                val severity =
                    if (change.changeType == ConfigChangeType.SECURITY_DOWNGRADE) {
                        AnomalySeverity.CRITICAL
                    } else {
                        AnomalySeverity.LOW
                    }

                anomalies.add(
                    Anomaly(
                        type = AnomalyType.SECURITY_CONFIGURATION_CHANGE,
                        severity = severity,
                        timestampMillis = change.timestampMillis,
                        description = "${change.changeType.displayName}: ${change.description}",
                        affectedBssids = setOf(history.bssid),
                        value = null,
                    ),
                )
            }
        }

        return anomalies.sortedByDescending { it.severity }
    }

    /**
     * Detect anomalies in roaming events
     *
     * Identifies unusual roaming behavior.
     */
    fun detectRoamingAnomalies(events: List<RoamingEvent>): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()

        events.forEach { event ->
            if (event.isActualRoam && event.durationMillis >= roamingLatencyAnomalyMs) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.EXCESSIVE_ROAMING_LATENCY,
                        severity =
                            when {
                                event.durationMillis >= 10_000 -> AnomalySeverity.CRITICAL
                                event.durationMillis >= 5_000 -> AnomalySeverity.HIGH
                                else -> AnomalySeverity.MEDIUM
                            },
                        timestampMillis = event.timestampMillis,
                        description = "Excessive roaming latency: ${event.durationMillis}ms (${event.fromBssid} → ${event.toBssid})",
                        affectedBssids = setOfNotNull(event.fromBssid, event.toBssid),
                        value = event.durationMillis.toDouble(),
                    ),
                )
            }

            if (event.isActualRoam && !event.wasAppropriateRoam) {
                val beforeRssi = event.rssiBeforeDbm
                val afterRssi = event.rssiAfterDbm

                if (beforeRssi != null && beforeRssi >= -60) {
                    anomalies.add(
                        Anomaly(
                            type = AnomalyType.INAPPROPRIATE_ROAMING,
                            severity = AnomalySeverity.LOW,
                            timestampMillis = event.timestampMillis,
                            description = "Roamed from strong signal: ${beforeRssi}dBm → ${afterRssi}dBm",
                            affectedBssids = setOfNotNull(event.fromBssid, event.toBssid),
                            value = beforeRssi.toDouble(),
                        ),
                    )
                }
            }

            // Simplified ping-pong detection (full sequence analysis done in detectRoamingPingPong)
            if (event.wasForcedDisconnect) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.FORCED_DISCONNECT,
                        severity = AnomalySeverity.MEDIUM,
                        timestampMillis = event.timestampMillis,
                        description = "Forced disconnect during roaming: ${event.fromBssid} → ${event.toBssid}",
                        affectedBssids = setOfNotNull(event.fromBssid, event.toBssid),
                        value = null,
                    ),
                )
            }
        }

        return anomalies.sortedByDescending { it.severity }
    }

    /**
     * Detect roaming ping-pong (rapid back-and-forth between APs)
     *
     * Identifies when device repeatedly switches between same APs quickly.
     */
    fun detectRoamingPingPong(
        events: List<RoamingEvent>,
        windowMillis: Long = 60_000,
        minOccurrences: Int = 3,
    ): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()

        if (events.size < minOccurrences) return anomalies

        val sortedEvents = events.filter { it.isActualRoam }.sortedBy { it.timestampMillis }

        // Look for patterns of A→B→A→B within time window
        for (i in 0 until sortedEvents.size - minOccurrences + 1) {
            val windowEvents = sortedEvents.drop(i).take(minOccurrences)

            val windowDuration = windowEvents.last().timestampMillis - windowEvents.first().timestampMillis

            // Check if window is within time limit and has ping-pong pattern
            if (windowDuration <= windowMillis) {
                // Ping-pong requires exactly two BSSIDs with alternating pattern
                val bssids = windowEvents.flatMap { listOfNotNull(it.fromBssid, it.toBssid) }.distinct()
                if (bssids.size == 2) {
                    val pattern = windowEvents.map { it.toBssid }
                    val isAlternating = pattern.zipWithNext().all { (a, b) -> a != b }

                    if (isAlternating) {
                        anomalies.add(
                            Anomaly(
                                type = AnomalyType.ROAMING_PING_PONG,
                                severity = AnomalySeverity.HIGH,
                                timestampMillis = windowEvents.last().timestampMillis,
                                description = "Roaming ping-pong detected: $minOccurrences rapid switches between ${bssids.joinToString(
                                    " ↔ ",
                                )}",
                                affectedBssids = bssids.toSet(),
                                value = minOccurrences.toDouble(),
                            ),
                        )
                        break // Only report once per sequence
                    }
                }
            }
        }

        return anomalies
    }

    /**
     * Detect network-wide anomalies
     *
     * Analyzes entire network for systemic issues.
     */
    fun detectNetworkAnomalies(networkTrend: NetworkTrend): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()

        val degradingAps =
            networkTrend.apHistories.count { history ->
                history.signalTrend().indicatesProblem
            }

        if (degradingAps.toDouble() / networkTrend.uniqueApCount >= 0.5 && networkTrend.uniqueApCount >= 3) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.NETWORK_WIDE_DEGRADATION,
                    severity = AnomalySeverity.CRITICAL,
                    timestampMillis = networkTrend.endTimestamp,
                    description = "Network-wide signal degradation: $degradingAps/${networkTrend.uniqueApCount} APs degrading",
                    affectedBssids = networkTrend.apHistories.map { it.bssid }.toSet(),
                    value = (degradingAps.toDouble() / networkTrend.uniqueApCount) * 100.0,
                ),
            )
        }

        if (networkTrend.health == NetworkHealth.CRITICAL) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.CRITICAL_NETWORK_HEALTH,
                    severity = AnomalySeverity.CRITICAL,
                    timestampMillis = networkTrend.endTimestamp,
                    description = "Critical network health: score ${networkTrend.healthScore}/100",
                    affectedBssids = networkTrend.apHistories.map { it.bssid }.toSet(),
                    value = networkTrend.healthScore.toDouble(),
                ),
            )
        }

        return anomalies.sortedByDescending { it.severity }
    }

    /**
     * Analyze all available data for anomalies
     *
     * Comprehensive anomaly detection across all data sources.
     */
    fun detectAllAnomalies(
        networkTrend: NetworkTrend,
        snapshots: List<ScanSnapshot>,
    ): AnomalyReport {
        val allAnomalies = mutableListOf<Anomaly>()

        allAnomalies.addAll(detectNetworkAnomalies(networkTrend))

        networkTrend.apHistories.forEach { history ->
            allAnomalies.addAll(detectApHistoryAnomalies(history))
        }

        snapshots.sortedBy { it.timestampMillis }.zipWithNext().forEach { (older, newer) ->
            val comparison = newer.compareWith(older)
            allAnomalies.addAll(detectSnapshotAnomalies(comparison))
        }

        val allRoamingEvents = networkTrend.apHistories.flatMap { it.roamingEvents }
        allAnomalies.addAll(detectRoamingAnomalies(allRoamingEvents))
        allAnomalies.addAll(detectRoamingPingPong(allRoamingEvents))

        val uniqueAnomalies = allAnomalies.distinctBy { it.type to it.timestampMillis to it.affectedBssids }

        val criticalCount = uniqueAnomalies.count { it.severity == AnomalySeverity.CRITICAL }
        val highCount = uniqueAnomalies.count { it.severity == AnomalySeverity.HIGH }
        val mediumCount = uniqueAnomalies.count { it.severity == AnomalySeverity.MEDIUM }
        val lowCount = uniqueAnomalies.count { it.severity == AnomalySeverity.LOW }

        return AnomalyReport(
            ssid = networkTrend.ssid,
            timestampMillis = System.currentTimeMillis(),
            anomalies = uniqueAnomalies.sortedByDescending { it.severity },
            criticalCount = criticalCount,
            highCount = highCount,
            mediumCount = mediumCount,
            lowCount = lowCount,
        )
    }
}

/**
 * Detected network anomaly
 *
 * @property type Type of anomaly detected
 * @property severity Severity level
 * @property timestampMillis When the anomaly was detected
 * @property description Human-readable description
 * @property affectedBssids BSSIDs affected by this anomaly
 * @property value Numeric value associated with anomaly (if applicable)
 */
data class Anomaly(
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val timestampMillis: Long,
    val description: String,
    val affectedBssids: Set<String>,
    val value: Double? = null,
) {
    init {
        require(timestampMillis > 0) {
            "Timestamp must be positive, got $timestampMillis"
        }
        require(description.isNotBlank()) {
            "Description must not be blank"
        }
    }

    /**
     * Whether this anomaly requires immediate attention
     */
    val requiresImmediateAttention: Boolean
        get() = severity in listOf(AnomalySeverity.CRITICAL, AnomalySeverity.HIGH)

    /**
     * Human-readable summary
     */
    val summary: String
        get() = "[${severity.displayName}] ${type.displayName}: $description"
}

/**
 * Anomaly detection report
 *
 * @property ssid Network name
 * @property timestampMillis When report was generated
 * @property anomalies All detected anomalies
 * @property criticalCount Number of critical anomalies
 * @property highCount Number of high severity anomalies
 * @property mediumCount Number of medium severity anomalies
 * @property lowCount Number of low severity anomalies
 */
data class AnomalyReport(
    val ssid: String,
    val timestampMillis: Long,
    val anomalies: List<Anomaly>,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
) {
    /**
     * Total number of anomalies detected
     */
    val totalCount: Int
        get() = anomalies.size

    /**
     * Whether any critical issues were found
     */
    val hasCriticalIssues: Boolean
        get() = criticalCount > 0

    /**
     * Whether any high-severity issues were found
     */
    val hasHighSeverityIssues: Boolean
        get() = highCount > 0

    /**
     * Whether the network appears healthy
     */
    val isHealthy: Boolean
        get() = criticalCount == 0 && highCount == 0 && mediumCount <= 2

    /**
     * Critical and high severity anomalies only
     */
    val urgentAnomalies: List<Anomaly>
        get() = anomalies.filter { it.requiresImmediateAttention }

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("$ssid Anomaly Report: ")
                append("$totalCount anomalies")
                if (criticalCount > 0) append(" ($criticalCount CRITICAL)")
                if (highCount > 0) append(" ($highCount HIGH)")
                if (isHealthy) append(" - Network healthy")
            }
}

/**
 * Types of network anomalies
 */
enum class AnomalyType(
    val displayName: String,
) {
    /** Sudden, significant signal strength drop */
    SUDDEN_SIGNAL_DROP("Sudden Signal Drop"),

    /** Sudden, significant signal strength increase (possible error) */
    SUDDEN_SIGNAL_SPIKE("Sudden Signal Spike"),

    /** Extreme signal variance (very unstable) */
    EXTREME_SIGNAL_VARIANCE("Extreme Signal Variance"),

    /** Massive AP churn (many APs appearing/disappearing) */
    MASSIVE_AP_CHURN("Massive AP Churn"),

    /** Frequent disconnections from AP */
    FREQUENT_DISCONNECTIONS("Frequent Disconnections"),

    /** Excessive roaming latency */
    EXCESSIVE_ROAMING_LATENCY("Excessive Roaming Latency"),

    /** Inappropriate roaming decision */
    INAPPROPRIATE_ROAMING("Inappropriate Roaming"),

    /** Roaming ping-pong (rapid back-and-forth) */
    ROAMING_PING_PONG("Roaming Ping-Pong"),

    /** Forced disconnect during roaming */
    FORCED_DISCONNECT("Forced Disconnect"),

    /** Security configuration change */
    SECURITY_CONFIGURATION_CHANGE("Security Configuration Change"),

    /** Network-wide signal degradation */
    NETWORK_WIDE_DEGRADATION("Network-Wide Degradation"),

    /** Critical network health */
    CRITICAL_NETWORK_HEALTH("Critical Network Health"),
}

/**
 * Anomaly severity levels
 */
enum class AnomalySeverity(
    val displayName: String,
) {
    /** Critical - immediate action required */
    CRITICAL("CRITICAL"),

    /** High - urgent attention needed */
    HIGH("HIGH"),

    /** Medium - should be addressed soon */
    MEDIUM("MEDIUM"),

    /** Low - informational, monitor */
    LOW("LOW"),
    ;

    /**
     * Whether this severity requires user notification
     */
    val requiresNotification: Boolean
        get() = this in listOf(CRITICAL, HIGH)
}
