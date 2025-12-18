package io.lamco.netkit.diagnostics.model

/**
 * Enhanced ping metrics with duplicate detection, reordering, and TTL analysis.
 *
 * Extends basic ping testing with advanced metrics that help diagnose:
 * - Network path stability (TTL variance)
 * - Packet reordering (indicates buffering or multi-path issues)
 * - Duplicate packets (indicates network loops or misconfiguration)
 * - Route changes during test
 *
 * ## RFC 792 Requirements
 *
 * RFC 792 (ICMP) specifies that implementations should track:
 * - Duplicate echo replies
 * - Out-of-order replies
 * - Changes in TTL (indicating route changes)
 *
 * ## Diagnostic Value
 *
 * - **Duplicates**: Network loop, misconfigured router, or switch
 * - **Reordering**: Multi-path routing, excessive buffering
 * - **TTL variance**: Unstable routing, load-balanced paths
 * - **Sequence gaps**: Packet loss + recovery
 *
 * @property duplicatePackets Count of duplicate ICMP echo replies
 * @property reorderedPackets Count of out-of-order echo replies
 * @property sequenceGaps List of missing sequence numbers
 * @property ttlRange Range of TTL values observed (min to max)
 * @property ttlMode Most common TTL value
 * @property routeStability Routing stability assessment
 * @property icmpErrors Count of ICMP error messages received
 *
 * @since 1.1.0
 */
data class EnhancedPingMetrics(
    val duplicatePackets: Int,
    val reorderedPackets: Int,
    val sequenceGaps: List<Int>,
    val ttlRange: IntRange?,
    val ttlMode: Int?,
    val routeStability: RouteStability,
    val icmpErrors: Int,
) {
    init {
        require(duplicatePackets >= 0) { "Duplicate packets must be >= 0" }
        require(reorderedPackets >= 0) { "Reordered packets must be >= 0" }
        require(icmpErrors >= 0) { "ICMP errors must be >= 0" }
        ttlRange?.let {
            require(it.first > 0) { "TTL must be > 0" }
            require(it.last <= 255) { "TTL must be <= 255" }
            require(it.first <= it.last) { "TTL range invalid" }
        }
    }

    /**
     * Whether any network anomalies detected.
     */
    val hasAnomalies: Boolean =
        duplicatePackets > 0 ||
            reorderedPackets > 0 ||
            sequenceGaps.isNotEmpty() ||
            routeStability == RouteStability.UNSTABLE ||
            icmpErrors > 0

    /**
     * Severity of detected anomalies.
     */
    val anomalySeverity: AnomalySeverity
        get() =
            when {
                !hasAnomalies -> AnomalySeverity.NONE
                duplicatePackets > 5 || icmpErrors > 3 -> AnomalySeverity.CRITICAL
                reorderedPackets > 10 || routeStability == RouteStability.UNSTABLE -> AnomalySeverity.HIGH
                duplicatePackets > 0 || reorderedPackets > 0 -> AnomalySeverity.MEDIUM
                sequenceGaps.isNotEmpty() -> AnomalySeverity.LOW
                else -> AnomalySeverity.NONE
            }

    /**
     * TTL variance (max - min).
     */
    val ttlVariance: Int? = ttlRange?.let { it.last - it.first }

    /**
     * Diagnostic recommendations based on enhanced metrics.
     */
    fun recommendations(): List<String> =
        buildList {
            if (duplicatePackets > 0) {
                add("⚠ $duplicatePackets duplicate packets detected")
                add("Possible causes:")
                add("  - Network loop (check switch configuration)")
                add("  - Redundant paths without proper failover")
                add("  - Router misconfiguration")
                if (duplicatePackets > 5) {
                    add("URGENT: High duplicate count indicates serious network issue")
                }
            }

            if (reorderedPackets > 0) {
                add("⚠ $reorderedPackets packets arrived out of order")
                add("Possible causes:")
                add("  - Multi-path routing (packets taking different paths)")
                add("  - Excessive buffering causing reordering")
                add("  - Load balancing across multiple links")
                if (reorderedPackets > 10) {
                    add("High reordering can impact TCP performance")
                }
            }

            when (routeStability) {
                RouteStability.UNSTABLE -> {
                    add("⚠ Unstable routing detected")
                    add("TTL variance: $ttlVariance hops")
                    add("Route is changing during test")
                    add("Possible causes:")
                    add("  - Load balancing")
                    add("  - Routing flapping")
                    add("  - Network instability")
                }
                RouteStability.VARIABLE -> {
                    add("ℹ Variable routing detected (TTL variance: $ttlVariance)")
                    add("Multiple paths to destination")
                    add("This is normal for load-balanced networks")
                }
                RouteStability.STABLE -> {
                    add("✓ Stable routing (consistent TTL: $ttlMode)")
                }
            }

            if (sequenceGaps.isNotEmpty()) {
                add("ℹ ${sequenceGaps.size} sequence gaps detected")
                add("Indicates packet loss with some recovery")
            }

            if (icmpErrors > 0) {
                add("⚠ $icmpErrors ICMP error messages received")
                add("Network may have reachability or configuration issues")
            }
        }
}

/**
 * Route stability classification based on TTL variance.
 */
enum class RouteStability {
    /** Consistent TTL - stable single route */
    STABLE,

    /** TTL varies by 1-2 - minor path variation (acceptable) */
    VARIABLE,

    /** TTL varies by 3+ - unstable routing or frequent path changes */
    UNSTABLE,

    ;

    companion object {
        /**
         * Classify route stability from TTL range.
         */
        fun fromTtlRange(range: IntRange?): RouteStability {
            if (range == null) return STABLE

            val variance = range.last - range.first
            return when {
                variance == 0 -> STABLE
                variance <= 2 -> VARIABLE
                else -> UNSTABLE
            }
        }
    }
}

/**
 * Severity of ping anomalies.
 */
enum class AnomalySeverity {
    /** No anomalies detected */
    NONE,

    /** Minor anomalies (sequence gaps) */
    LOW,

    /** Moderate anomalies (some duplicates/reordering) */
    MEDIUM,

    /** Significant anomalies (frequent reordering) */
    HIGH,

    /** Critical anomalies (many duplicates, routing instability) */
    CRITICAL,
}
