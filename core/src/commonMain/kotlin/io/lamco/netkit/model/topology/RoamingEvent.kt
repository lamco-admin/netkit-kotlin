package io.lamco.netkit.model.topology

/**
 * Roaming event - transition between access points
 *
 * Represents a roaming handoff where a client device switches from one
 * BSSID to another while maintaining the same SSID connection.
 *
 * Roaming quality depends on:
 * - 802.11k/r/v support on both APs
 * - Signal strength before/after roam
 * - Time taken for handoff (durationMillis)
 * - Whether disconnect occurred
 *
 * @property timestampMillis When the roaming event occurred (epoch millis)
 * @property fromBssid Source BSSID (null if initial connection)
 * @property toBssid Destination BSSID (null if disconnection)
 * @property ssid Network SSID
 * @property durationMillis Roaming handoff duration in milliseconds
 * @property rssiBeforeDbm RSSI before roaming (at fromBssid)
 * @property rssiAfterDbm RSSI after roaming (at toBssid)
 * @property wasForcedDisconnect Whether disconnect occurred (not seamless roam)
 * @property has11r Whether 802.11r Fast BSS Transition was used
 * @property has11k Whether 802.11k Radio Resource Management was available
 * @property has11v Whether 802.11v BSS Transition Management was available
 */
data class RoamingEvent(
    val timestampMillis: Long,
    val fromBssid: String?,
    val toBssid: String?,
    val ssid: String,
    val durationMillis: Long,
    val rssiBeforeDbm: Int?,
    val rssiAfterDbm: Int?,
    val wasForcedDisconnect: Boolean,
    val has11r: Boolean,
    val has11k: Boolean,
    val has11v: Boolean,
) {
    init {
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
        require(durationMillis >= 0) {
            "Duration must be non-negative, got $durationMillis"
        }
        if (rssiBeforeDbm != null) {
            require(rssiBeforeDbm in -120..0) {
                "RSSI before must be in range [-120, 0] dBm, got $rssiBeforeDbm"
            }
        }
        if (rssiAfterDbm != null) {
            require(rssiAfterDbm in -120..0) {
                "RSSI after must be in range [-120, 0] dBm, got $rssiAfterDbm"
            }
        }
    }

    /**
     * Whether this was an initial connection (not a roam)
     */
    val isInitialConnection: Boolean
        get() = fromBssid == null && toBssid != null

    /**
     * Whether this was a disconnection
     */
    val isDisconnection: Boolean
        get() = fromBssid != null && toBssid == null

    /**
     * Whether this was an actual roaming event (BSSID change)
     */
    val isActualRoam: Boolean
        get() = fromBssid != null && toBssid != null && fromBssid != toBssid

    /**
     * RSSI improvement from roaming (positive = better signal)
     */
    val rssiImprovementDb: Int?
        get() =
            if (rssiBeforeDbm != null && rssiAfterDbm != null) {
                rssiAfterDbm - rssiBeforeDbm
            } else {
                null
            }

    /**
     * Alias for rssiImprovementDb for compatibility
     */
    val rssiImprovement: Int?
        get() = rssiImprovementDb

    /**
     * Whether fast roaming standards were available
     */
    val hasFastRoamingSupport: Boolean
        get() = has11k || has11r || has11v

    /**
     * Alias for hasFastRoamingSupport for compatibility
     */
    val usesFastRoaming: Boolean
        get() = hasFastRoamingSupport

    /**
     * Alias for indicatesStickyClient for compatibility
     */
    val isStickyRoam: Boolean
        get() = indicatesStickyClient

    /**
     * Duration in milliseconds (alias for compatibility)
     */
    val durationMs: Long
        get() = durationMillis

    /**
     * Whether the roaming was successful (completed without forced disconnect)
     */
    val isSuccessful: Boolean
        get() = isActualRoam && !wasForcedDisconnect

    /**
     * Whether full fast roaming suite was available
     */
    val hasFullFastRoamingSuite: Boolean
        get() = has11k && has11r && has11v

    /**
     * Roaming quality based on duration and capabilities
     */
    val roamingQuality: RoamingEventQuality
        get() =
            when {
                wasForcedDisconnect -> RoamingEventQuality.POOR
                !isActualRoam -> RoamingEventQuality.UNKNOWN
                durationMillis <= 50 && has11r -> RoamingEventQuality.EXCELLENT
                durationMillis <= 100 && has11r -> RoamingEventQuality.VERY_GOOD
                durationMillis <= 300 && hasFastRoamingSupport -> RoamingEventQuality.GOOD
                durationMillis <= 500 -> RoamingEventQuality.FAIR
                else -> RoamingEventQuality.POOR
            }

    /**
     * Whether the roaming decision was appropriate
     * (Should only roam if signal improved or was weak)
     */
    val wasAppropriateRoam: Boolean
        get() {
            if (!isActualRoam) return false

            val improvement = rssiImprovementDb
            val beforeSignal = rssiBeforeDbm

            return when {
                // Good: Roamed away from weak signal
                beforeSignal != null && beforeSignal < -75 -> true
                // Good: Roamed to significantly better signal
                improvement != null && improvement >= 10 -> true
                // Neutral: Moderate improvement
                improvement != null && improvement >= 5 -> true
                // Bad: Roamed to worse or similar signal
                else -> false
            }
        }

    /**
     * Whether this indicates a sticky client problem
     * (Should have roamed earlier but didn't)
     */
    val indicatesStickyClient: Boolean
        get() = rssiBeforeDbm != null && rssiBeforeDbm < -80 && isActualRoam

    /**
     * Human-readable event summary
     */
    val summary: String
        get() =
            buildString {
                when {
                    isInitialConnection -> append("Connected to $toBssid")
                    isDisconnection -> append("Disconnected from $fromBssid")
                    isActualRoam -> {
                        append("Roamed $fromBssid → $toBssid")
                        append(" (${durationMillis}ms)")
                        rssiImprovementDb?.let { improvement ->
                            val sign = if (improvement >= 0) "+" else ""
                            append(" [${sign}${improvement}dB]")
                        }
                    }
                }

                if (has11r) append(" [11r]")
                if (wasForcedDisconnect) append(" [DISCONNECT]")
            }

    /**
     * Event type for categorization
     */
    val eventType: RoamingEventType
        get() =
            when {
                isInitialConnection -> RoamingEventType.INITIAL_CONNECTION
                isDisconnection -> RoamingEventType.DISCONNECTION
                wasForcedDisconnect -> RoamingEventType.FORCED_ROAM
                has11r -> RoamingEventType.FAST_ROAM_11R
                hasFastRoamingSupport -> RoamingEventType.ASSISTED_ROAM
                else -> RoamingEventType.BASIC_ROAM
            }
}

/**
 * Roaming event quality categories
 */
enum class RoamingEventQuality(
    val displayName: String,
    val maxLatencyMs: Int,
) {
    /** Excellent: 802.11r, <= 50ms */
    EXCELLENT("Excellent (Seamless)", 50),

    /** Very Good: 802.11r, <= 100ms */
    VERY_GOOD("Very Good (Fast)", 100),

    /** Good: k/r/v support, <= 300ms */
    GOOD("Good (Minor Disruption)", 300),

    /** Fair: Basic roaming, <= 500ms */
    FAIR("Fair (Noticeable Disruption)", 500),

    /** Poor: Slow or forced disconnect */
    POOR("Poor (Connection Lost)", 3000),

    /** Unknown: Cannot determine quality */
    UNKNOWN("Unknown", 999999),
    ;

    /**
     * Whether this quality is acceptable for real-time applications
     */
    val isAcceptableForRealTime: Boolean
        get() = this in listOf(EXCELLENT, VERY_GOOD, GOOD)
}

/**
 * Types of roaming events
 */
enum class RoamingEventType(
    val displayName: String,
) {
    /** Initial connection to network */
    INITIAL_CONNECTION("Initial Connection"),

    /** Disconnection from network */
    DISCONNECTION("Disconnection"),

    /** Fast roam using 802.11r */
    FAST_ROAM_11R("Fast Roam (802.11r)"),

    /** Assisted roam with 802.11k/v */
    ASSISTED_ROAM("Assisted Roam (802.11k/v)"),

    /** Basic roam without fast roaming support */
    BASIC_ROAM("Basic Roam"),

    /** Forced roam due to disconnect/reconnect */
    FORCED_ROAM("Forced Roam (Disconnect)"),
    ;

    /**
     * Whether this is a seamless roaming type
     */
    val isSeamless: Boolean
        get() = this in listOf(FAST_ROAM_11R, ASSISTED_ROAM)
}
