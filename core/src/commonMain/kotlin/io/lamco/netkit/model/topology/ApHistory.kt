package io.lamco.netkit.model.topology

/**
 * Historical tracking data for a specific access point
 *
 * Maintains time-series data for a single BSSID, enabling trend analysis,
 * anomaly detection, and performance prediction. Tracks signal strength,
 * connection events, roaming behavior, and configuration changes over time.
 *
 * This class is designed for efficient querying of temporal patterns:
 * - Signal strength trends (improving/degrading)
 * - Availability patterns (when AP appears/disappears)
 * - Connection success rates
 * - Roaming quality metrics
 *
 * @property bssid MAC address of the access point
 * @property ssid Network name (SSID)
 * @property firstSeenTimestamp When this AP was first detected (epoch millis)
 * @property lastSeenTimestamp When this AP was most recently detected (epoch millis)
 * @property observations Signal strength measurements over time
 * @property connectionEvents Connection and disconnection history
 * @property roamingEvents Roaming events involving this AP
 * @property configurationChanges Security or capability changes detected
 */
data class ApHistory(
    val bssid: String,
    val ssid: String,
    val firstSeenTimestamp: Long,
    val lastSeenTimestamp: Long,
    val observations: List<SignalObservation>,
    val connectionEvents: List<ConnectionEvent> = emptyList(),
    val roamingEvents: List<RoamingEvent> = emptyList(),
    val configurationChanges: List<ConfigurationChange> = emptyList(),
) {
    init {
        require(bssid.isNotBlank()) {
            "BSSID must not be blank"
        }
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
        require(firstSeenTimestamp > 0) {
            "First seen timestamp must be positive, got $firstSeenTimestamp"
        }
        require(lastSeenTimestamp >= firstSeenTimestamp) {
            "Last seen must be >= first seen, got last=$lastSeenTimestamp, first=$firstSeenTimestamp"
        }
        require(observations.isNotEmpty()) {
            "Observations must not be empty"
        }
    }

    /**
     * Total duration this AP has been tracked (milliseconds)
     */
    val trackingDurationMillis: Long
        get() = lastSeenTimestamp - firstSeenTimestamp

    /**
     * Total number of observations recorded
     */
    val observationCount: Int
        get() = observations.size

    /**
     * Average time between observations (milliseconds)
     */
    val averageObservationIntervalMillis: Long
        get() =
            if (observations.size > 1) {
                trackingDurationMillis / (observations.size - 1)
            } else {
                0
            }

    /**
     * Whether this AP is currently visible (seen recently)
     */
    fun isCurrentlyVisible(
        thresholdMillis: Long = 60_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Boolean = (currentTimeMillis - lastSeenTimestamp) < thresholdMillis

    /**
     * Average RSSI across all observations
     */
    val averageRssiDbm: Double
        get() = observations.map { it.rssiDbm }.average()

    /**
     * Median RSSI (more robust to outliers than average)
     */
    val medianRssiDbm: Int
        get() {
            val sorted = observations.map { it.rssiDbm }.sorted()
            return sorted[sorted.size / 2]
        }

    /**
     * Strongest signal ever observed
     */
    val peakRssiDbm: Int
        get() = observations.maxOf { it.rssiDbm }

    /**
     * Weakest signal ever observed
     */
    val minRssiDbm: Int
        get() = observations.minOf { it.rssiDbm }

    /**
     * RSSI range (difference between peak and minimum)
     */
    val rssiRangeDb: Int
        get() = peakRssiDbm - minRssiDbm

    /**
     * Standard deviation of RSSI (signal stability metric)
     */
    val rssiStandardDeviation: Double
        get() {
            val mean = averageRssiDbm
            val variance = observations.map { (it.rssiDbm - mean) * (it.rssiDbm - mean) }.average()
            return kotlin.math.sqrt(variance)
        }

    /**
     * Signal stability category based on standard deviation
     */
    val signalStability: SignalStability
        get() =
            when {
                rssiStandardDeviation < 2.0 -> SignalStability.VERY_STABLE
                rssiStandardDeviation < 5.0 -> SignalStability.STABLE
                rssiStandardDeviation < 10.0 -> SignalStability.MODERATE
                rssiStandardDeviation < 15.0 -> SignalStability.UNSTABLE
                else -> SignalStability.VERY_UNSTABLE
            }

    /**
     * Recent observations (within specified time window)
     */
    fun recentObservations(
        windowMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<SignalObservation> {
        val cutoffTime = currentTimeMillis - windowMillis
        return observations.filter { it.timestampMillis >= cutoffTime }
    }

    /**
     * Recent average RSSI
     */
    fun recentAverageRssi(
        windowMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Double? {
        val recent = recentObservations(windowMillis, currentTimeMillis)
        return if (recent.isNotEmpty()) {
            recent.map { it.rssiDbm }.average()
        } else {
            null
        }
    }

    /**
     * Signal trend over time (improving, stable, or degrading)
     */
    fun signalTrend(
        windowMillis: Long = 300_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): SignalTrend {
        val recent = recentObservations(windowMillis, currentTimeMillis)
        if (recent.size < 3) return SignalTrend.INSUFFICIENT_DATA

        // Simple linear regression slope
        val slope = calculateSignalSlope(recent)

        return when {
            slope > 0.5 -> SignalTrend.RAPIDLY_IMPROVING
            slope > 0.1 -> SignalTrend.IMPROVING
            slope > -0.1 -> SignalTrend.STABLE
            slope > -0.5 -> SignalTrend.DEGRADING
            else -> SignalTrend.RAPIDLY_DEGRADING
        }
    }

    /**
     * Calculate signal strength slope (dB per millisecond)
     */
    private fun calculateSignalSlope(observations: List<SignalObservation>): Double {
        if (observations.size < 2) return 0.0

        val n = observations.size
        val firstTime = observations.first().timestampMillis.toDouble()

        // Normalize time to avoid numerical issues
        val x = observations.mapIndexed { index, _ -> index.toDouble() }
        val y = observations.map { it.rssiDbm.toDouble() }

        val xMean = x.average()
        val yMean = y.average()

        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - xMean) * (yi - yMean) }
        val denominator = x.sumOf { xi -> (xi - xMean) * (xi - xMean) }

        return if (denominator != 0.0) numerator / denominator else 0.0
    }

    /**
     * Total number of connections to this AP
     */
    val connectionCount: Int
        get() = connectionEvents.count { it.isConnection }

    /**
     * Total number of disconnections from this AP
     */
    val disconnectionCount: Int
        get() = connectionEvents.count { it.isDisconnection }

    /**
     * Connection success rate (connections / total events)
     */
    val connectionSuccessRate: Double
        get() {
            val total = connectionEvents.size
            return if (total > 0) {
                connectionCount.toDouble() / total
            } else {
                0.0
            }
        }

    /**
     * Average connection duration (milliseconds)
     */
    val averageConnectionDurationMillis: Long?
        get() {
            val durations =
                connectionEvents
                    .filter { it.isConnection && it.durationMillis != null }
                    .mapNotNull { it.durationMillis }

            return if (durations.isNotEmpty()) {
                durations.average().toLong()
            } else {
                null
            }
        }

    /**
     * Total time connected to this AP (milliseconds)
     */
    val totalConnectionTimeMillis: Long
        get() =
            connectionEvents
                .filter { it.isConnection }
                .mapNotNull { it.durationMillis }
                .sum()

    /**
     * Number of roaming events involving this AP
     */
    val roamingEventCount: Int
        get() = roamingEvents.filter { it.isActualRoam }.size

    /**
     * Roaming events where this was the source (roamed away from)
     */
    val roamingFromCount: Int
        get() = roamingEvents.count { it.fromBssid == bssid && it.isActualRoam }

    /**
     * Roaming events where this was the target (roamed to)
     */
    val roamingToCount: Int
        get() = roamingEvents.count { it.toBssid == bssid && it.isActualRoam }

    /**
     * Average roaming quality for events involving this AP
     */
    val averageRoamingQuality: RoamingEventQuality?
        get() {
            val qualities =
                roamingEvents
                    .filter { it.isActualRoam }
                    .map { it.roamingQuality }

            return if (qualities.isNotEmpty()) {
                // Return median quality
                qualities.sorted()[qualities.size / 2]
            } else {
                null
            }
        }

    /**
     * Number of configuration changes detected
     */
    val configurationChangeCount: Int
        get() = configurationChanges.size

    /**
     * Most recent configuration change
     */
    val lastConfigurationChange: ConfigurationChange?
        get() = configurationChanges.maxByOrNull { it.timestampMillis }

    /**
     * Whether this AP has had recent configuration changes
     */
    fun hasRecentConfigChange(
        windowMillis: Long = 86400_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val cutoffTime = currentTimeMillis - windowMillis
        return configurationChanges.any { it.timestampMillis >= cutoffTime }
    }

    /**
     * Observations within a specific time range
     */
    fun observationsInRange(
        startMillis: Long,
        endMillis: Long,
    ): List<SignalObservation> = observations.filter { it.timestampMillis in startMillis..endMillis }

    /**
     * Estimate RSSI at a specific time using linear interpolation
     */
    fun estimateRssiAt(timestampMillis: Long): Int? {
        if (observations.isEmpty()) return null
        if (timestampMillis < firstSeenTimestamp || timestampMillis > lastSeenTimestamp) return null

        // Find surrounding observations
        val before = observations.filter { it.timestampMillis <= timestampMillis }.maxByOrNull { it.timestampMillis }
        val after = observations.filter { it.timestampMillis >= timestampMillis }.minByOrNull { it.timestampMillis }

        return when {
            before == null -> after?.rssiDbm
            after == null -> before.rssiDbm
            before.timestampMillis == after.timestampMillis -> before.rssiDbm
            else -> {
                // Linear interpolation
                val timeDiff = after.timestampMillis - before.timestampMillis
                val rssiDiff = after.rssiDbm - before.rssiDbm
                val ratio = (timestampMillis - before.timestampMillis).toDouble() / timeDiff
                (before.rssiDbm + (rssiDiff * ratio)).toInt()
            }
        }
    }

    /**
     * Add a new observation to the history
     */
    fun addObservation(observation: SignalObservation): ApHistory {
        require(observation.bssid == bssid) {
            "Observation BSSID ${observation.bssid} does not match history BSSID $bssid"
        }

        val newObservations = (observations + observation).sortedBy { it.timestampMillis }
        val newLastSeen = maxOf(lastSeenTimestamp, observation.timestampMillis)

        return copy(
            lastSeenTimestamp = newLastSeen,
            observations = newObservations,
        )
    }

    /**
     * Add a connection event to the history
     */
    fun addConnectionEvent(event: ConnectionEvent): ApHistory {
        require(event.bssid == bssid) {
            "Connection event BSSID ${event.bssid} does not match history BSSID $bssid"
        }

        return copy(
            connectionEvents = (connectionEvents + event).sortedBy { it.timestampMillis },
        )
    }

    /**
     * Add a roaming event to the history
     */
    fun addRoamingEvent(event: RoamingEvent): ApHistory {
        require(event.fromBssid == bssid || event.toBssid == bssid) {
            "Roaming event does not involve this BSSID $bssid"
        }

        return copy(
            roamingEvents = (roamingEvents + event).sortedBy { it.timestampMillis },
        )
    }

    /**
     * Human-readable history summary
     */
    val summary: String
        get() =
            buildString {
                append("$bssid ($ssid): ")
                append("$observationCount observations, ")
                append("avg ${averageRssiDbm.toInt()}dBm, ")
                append("${signalStability.displayName}")
                if (connectionCount > 0) {
                    append(", $connectionCount connections")
                }
            }

    companion object {
        /**
         * Create initial history from first observation
         */
        fun create(
            observation: SignalObservation,
            ssid: String,
        ): ApHistory =
            ApHistory(
                bssid = observation.bssid,
                ssid = ssid,
                firstSeenTimestamp = observation.timestampMillis,
                lastSeenTimestamp = observation.timestampMillis,
                observations = listOf(observation),
            )
    }
}

/**
 * Single signal strength observation at a point in time
 *
 * @property bssid MAC address of the access point
 * @property timestampMillis When this observation was made (epoch millis)
 * @property rssiDbm Signal strength in dBm
 * @property location Optional device location during observation
 */
data class SignalObservation(
    val bssid: String,
    val timestampMillis: Long,
    val rssiDbm: Int,
    val location: GeoPoint? = null,
) {
    init {
        require(bssid.isNotBlank()) {
            "BSSID must not be blank"
        }
        require(timestampMillis > 0) {
            "Timestamp must be positive, got $timestampMillis"
        }
        require(rssiDbm in -120..0) {
            "RSSI must be in range [-120, 0] dBm, got $rssiDbm"
        }
    }

    /**
     * Signal strength category
     */
    val strength: SignalStrength
        get() = SignalStrength.fromRssi(rssiDbm)

    /**
     * Age of this observation
     */
    fun ageMillis(currentTimeMillis: Long = System.currentTimeMillis()): Long = currentTimeMillis - timestampMillis

    /**
     * Whether this observation is recent
     */
    fun isRecent(
        thresholdMillis: Long = 60_000,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Boolean = ageMillis(currentTimeMillis) < thresholdMillis
}

/**
 * Connection or disconnection event
 *
 * @property bssid MAC address of the access point
 * @property timestampMillis When this event occurred (epoch millis)
 * @property isConnection true if connection, false if disconnection
 * @property durationMillis Duration of connection (if disconnection event)
 * @property rssiAtEventDbm Signal strength when event occurred
 * @property reason Reason code or description (if available)
 */
data class ConnectionEvent(
    val bssid: String,
    val timestampMillis: Long,
    val isConnection: Boolean,
    val durationMillis: Long? = null,
    val rssiAtEventDbm: Int? = null,
    val reason: String? = null,
) {
    init {
        require(bssid.isNotBlank()) {
            "BSSID must not be blank"
        }
        require(timestampMillis > 0) {
            "Timestamp must be positive, got $timestampMillis"
        }
        if (durationMillis != null) {
            require(durationMillis >= 0) {
                "Duration must be non-negative, got $durationMillis"
            }
        }
        if (rssiAtEventDbm != null) {
            require(rssiAtEventDbm in -120..0) {
                "RSSI must be in range [-120, 0] dBm, got $rssiAtEventDbm"
            }
        }
    }

    /**
     * Whether this is a disconnection event
     */
    val isDisconnection: Boolean
        get() = !isConnection

    /**
     * Event type description
     */
    val eventType: String
        get() = if (isConnection) "Connection" else "Disconnection"

    /**
     * Human-readable event summary
     */
    val summary: String
        get() =
            buildString {
                append("$eventType @ $timestampMillis")
                if (rssiAtEventDbm != null) {
                    append(" (${rssiAtEventDbm}dBm)")
                }
                if (durationMillis != null) {
                    append(" [${durationMillis}ms]")
                }
                if (reason != null) {
                    append(" - $reason")
                }
            }
}

/**
 * Configuration change detected for an AP
 *
 * @property timestampMillis When the change was detected (epoch millis)
 * @property changeType Type of configuration change
 * @property oldValue Previous configuration value (if applicable)
 * @property newValue New configuration value (if applicable)
 * @property description Human-readable description of the change
 */
data class ConfigurationChange(
    val timestampMillis: Long,
    val changeType: ConfigChangeType,
    val oldValue: String? = null,
    val newValue: String? = null,
    val description: String,
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
     * Whether this change affects security
     */
    val isSecurityChange: Boolean
        get() =
            changeType in
                listOf(
                    ConfigChangeType.SECURITY_UPGRADE,
                    ConfigChangeType.SECURITY_DOWNGRADE,
                    ConfigChangeType.ENCRYPTION_CHANGE,
                )

    /**
     * Human-readable change summary
     */
    val summary: String
        get() =
            buildString {
                append(changeType.displayName)
                if (oldValue != null && newValue != null) {
                    append(": $oldValue → $newValue")
                }
            }
}

/**
 * Types of configuration changes
 */
enum class ConfigChangeType(
    val displayName: String,
) {
    /** Security protocol upgraded (e.g., WPA2 → WPA3) */
    SECURITY_UPGRADE("Security Upgrade"),

    /** Security protocol downgraded */
    SECURITY_DOWNGRADE("Security Downgrade"),

    /** Encryption cipher changed */
    ENCRYPTION_CHANGE("Encryption Change"),

    /** Channel changed */
    CHANNEL_CHANGE("Channel Change"),

    /** Channel width changed */
    CHANNEL_WIDTH_CHANGE("Channel Width Change"),

    /** WiFi standard upgraded (e.g., WiFi 5 → WiFi 6) */
    STANDARD_UPGRADE("WiFi Standard Upgrade"),

    /** Fast roaming support added/removed */
    ROAMING_CAPABILITY_CHANGE("Roaming Capability Change"),

    /** Other configuration change */
    OTHER("Other Change"),
}

/**
 * Signal stability categories based on standard deviation
 */
enum class SignalStability(
    val displayName: String,
    val maxStdDev: Double,
) {
    /** Very stable (< 2 dB std dev) */
    VERY_STABLE("Very Stable", 2.0),

    /** Stable (< 5 dB std dev) */
    STABLE("Stable", 5.0),

    /** Moderate (< 10 dB std dev) */
    MODERATE("Moderate", 10.0),

    /** Unstable (< 15 dB std dev) */
    UNSTABLE("Unstable", 15.0),

    /** Very unstable (>= 15 dB std dev) */
    VERY_UNSTABLE("Very Unstable", Double.MAX_VALUE),
    ;

    /**
     * Whether this stability level is acceptable
     */
    val isAcceptable: Boolean
        get() = this in listOf(VERY_STABLE, STABLE, MODERATE)

    companion object {
        /**
         * Determine stability from standard deviation
         */
        fun fromStdDev(stdDev: Double): SignalStability =
            when {
                stdDev < 2.0 -> VERY_STABLE
                stdDev < 5.0 -> STABLE
                stdDev < 10.0 -> MODERATE
                stdDev < 15.0 -> UNSTABLE
                else -> VERY_UNSTABLE
            }
    }
}

/**
 * Signal trend categories
 */
enum class SignalTrend(
    val displayName: String,
) {
    /** Rapidly improving (> 0.5 dB/observation) */
    RAPIDLY_IMPROVING("Rapidly Improving"),

    /** Improving (> 0.1 dB/observation) */
    IMPROVING("Improving"),

    /** Stable (-0.1 to 0.1 dB/observation) */
    STABLE("Stable"),

    /** Degrading (< -0.1 dB/observation) */
    DEGRADING("Degrading"),

    /** Rapidly degrading (< -0.5 dB/observation) */
    RAPIDLY_DEGRADING("Rapidly Degrading"),

    /** Insufficient data to determine trend */
    INSUFFICIENT_DATA("Insufficient Data"),
    ;

    /**
     * Whether this trend indicates a problem
     */
    val indicatesProblem: Boolean
        get() = this in listOf(DEGRADING, RAPIDLY_DEGRADING)

    /**
     * Whether this trend is positive
     */
    val isPositive: Boolean
        get() = this in listOf(IMPROVING, RAPIDLY_IMPROVING)
}
