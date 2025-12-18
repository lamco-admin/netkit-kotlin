package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents the result of a traceroute network diagnostic test.
 *
 * Traceroute identifies the network path taken to reach a destination host by
 * measuring the route and transit delays of packets across an IP network. Each
 * hop represents a router or gateway along the path.
 *
 * This implementation follows RFC 1393 (Traceroute Using an IP Option) and
 * common traceroute behavior across platforms.
 *
 * ## Path Analysis
 *
 * Traceroute results can identify:
 * - **Gateway**: First hop (typically home router)
 * - **ISP routing**: Intermediate hops within ISP network
 * - **Internet backbone**: Major carrier hops
 * - **Destination network**: Final hops to target
 * - **Bottlenecks**: Hops with significant latency increases
 *
 * ## Quality Indicators
 *
 * - **Total hops**: <10 is typical for good routing
 * - **Completion**: Whether target was reached
 * - **Timeouts**: Hops that don't respond (may be normal firewall behavior)
 * - **Latency spikes**: Large RTT increases between hops indicate bottlenecks
 *
 * ## Usage Example
 *
 * ```kotlin
 * val traceroute = TracerouteResult(
 *     targetHost = "example.com",
 *     resolvedIp = "93.184.216.34",
 *     hops = listOf(
 *         TracerouteHop(1, "192.168.1.1", "gateway", 1.2.milliseconds),
 *         TracerouteHop(2, "10.0.0.1", "isp-router-1", 5.8.milliseconds),
 *         TracerouteHop(3, "93.184.216.34", "example.com", 12.4.milliseconds)
 *     ),
 *     maxHops = 30,
 *     completed = true
 * )
 *
 * println("Total hops: ${traceroute.totalHops}")
 * println("Gateway latency: ${traceroute.firstHopLatency}")
 * println("Route quality: ${traceroute.routeQuality}")
 * ```
 *
 * @property targetHost The hostname or IP address to trace
 * @property resolvedIp The resolved IP address of the target (if DNS resolution succeeded)
 * @property hops List of network hops along the path (in order)
 * @property maxHops Maximum hop count attempted (typically 30)
 * @property completed Whether the trace reached the target host
 * @property testDuration Total time to complete the traceroute
 * @property timestamp When the test was performed (epoch millis)
 * @property failureReason Optional reason if the test failed
 *
 * @see TracerouteHop
 * @see RouteQuality
 * @see RFC1393
 */
data class TracerouteResult(
    val targetHost: String,
    val resolvedIp: String? = null,
    val hops: List<TracerouteHop>,
    val maxHops: Int = 30,
    val completed: Boolean,
    val testDuration: Duration = Duration.ZERO,
    val timestamp: Long = System.currentTimeMillis(),
    val failureReason: String? = null,
) {
    init {
        require(targetHost.isNotBlank()) { "Target host cannot be blank" }
        require(maxHops in 1..255) { "Max hops must be 1-255, got $maxHops" }
        require(!testDuration.isNegative()) { "Test duration cannot be negative" }
        require(hops.all { it.hopNumber > 0 }) { "All hop numbers must be positive" }

        // Validate hop ordering
        if (hops.isNotEmpty()) {
            val hopNumbers = hops.map { it.hopNumber }
            require(hopNumbers == hopNumbers.sorted()) { "Hops must be in sequential order" }
        }
    }

    /**
     * Total number of hops in the path.
     */
    val totalHops: Int = hops.size

    /**
     * Whether the traceroute test was successful (reached target or got reasonable path).
     */
    val isSuccessful: Boolean = hops.isNotEmpty() && failureReason == null

    /**
     * The first hop (typically the local gateway/router).
     */
    val firstHop: TracerouteHop? = hops.firstOrNull()

    /**
     * The last hop in the trace.
     */
    val lastHop: TracerouteHop? = hops.lastOrNull()

    /**
     * Latency to the first hop (local gateway).
     * This represents the local network quality.
     */
    val firstHopLatency: Duration? = firstHop?.rtt

    /**
     * Total latency to reach the target (last hop RTT).
     */
    val totalLatency: Duration? = lastHop?.rtt

    /**
     * List of hops that timed out (no response).
     * Some routers/firewalls don't respond to traceroute probes.
     */
    val timedOutHops: List<TracerouteHop> = hops.filter { it.isTimeout }

    /**
     * List of responsive hops (those that returned RTT).
     */
    val responsiveHops: List<TracerouteHop> = hops.filter { !it.isTimeout }

    /**
     * Percentage of hops that timed out (0.0-100.0).
     */
    val timeoutRate: Double =
        if (hops.isEmpty()) {
            0.0
        } else {
            (timedOutHops.size.toDouble() / hops.size) * 100.0
        }

    /**
     * Identify potential bottleneck hops where latency increases significantly.
     *
     * A bottleneck is defined as a hop where latency increases by more than the threshold
     * compared to the previous hop.
     *
     * @param threshold Minimum latency increase to consider a bottleneck (default: 50ms)
     * @return List of hop numbers that are potential bottlenecks
     */
    fun findBottlenecks(threshold: Duration = 50.milliseconds): List<Int> {
        val bottlenecks = mutableListOf<Int>()

        for (i in 1 until hops.size) {
            val prevHop = hops[i - 1]
            val currentHop = hops[i]

            if (!prevHop.isTimeout && !currentHop.isTimeout) {
                val latencyIncrease = currentHop.rtt!! - prevHop.rtt!!
                if (latencyIncrease >= threshold) {
                    bottlenecks.add(currentHop.hopNumber)
                }
            }
        }

        return bottlenecks
    }

    /**
     * Calculate average latency across all responsive hops.
     */
    val averageHopLatency: Duration? =
        if (responsiveHops.isEmpty()) {
            null
        } else {
            val totalMs = responsiveHops.mapNotNull { it.rtt }.sumOf { it.inWholeMilliseconds }
            (totalMs / responsiveHops.size).milliseconds
        }

    /**
     * Assess the quality of the network route.
     */
    val routeQuality: RouteQuality
        get() =
            when {
                !isSuccessful || !completed -> RouteQuality.FAILED
                totalHops <= 5 && timeoutRate < 10.0 -> RouteQuality.EXCELLENT
                totalHops <= 10 && timeoutRate < 20.0 -> RouteQuality.GOOD
                totalHops <= 15 && timeoutRate < 40.0 -> RouteQuality.FAIR
                totalHops <= 20 && timeoutRate < 60.0 -> RouteQuality.POOR
                else -> RouteQuality.CRITICAL
            }

    /**
     * Check if the first hop (gateway) is responsive and has acceptable latency.
     *
     * @param maxGatewayLatency Maximum acceptable latency to gateway (default: 10ms)
     * @return true if gateway is healthy
     */
    fun hasHealthyGateway(maxGatewayLatency: Duration = 10.milliseconds): Boolean {
        val first = firstHop ?: return false
        return !first.isTimeout && (first.rtt ?: Duration.INFINITE) <= maxGatewayLatency
    }

    /**
     * Human-readable summary of the traceroute results.
     */
    fun summary(): String =
        buildString {
            appendLine("Traceroute to $targetHost${resolvedIp?.let { " ($it)" } ?: ""}")
            appendLine("  Total hops: $totalHops (max: $maxHops)")
            appendLine("  Completed: $completed")
            appendLine("  Route quality: $routeQuality")

            if (firstHopLatency != null) {
                appendLine("  Gateway latency: $firstHopLatency")
            }

            if (totalLatency != null) {
                appendLine("  Total latency: $totalLatency")
            }

            if (timeoutRate > 0) {
                appendLine("  Timeout rate: ${"%.1f".format(timeoutRate)}%")
            }

            val bottlenecks = findBottlenecks()
            if (bottlenecks.isNotEmpty()) {
                appendLine("  Bottlenecks at hops: ${bottlenecks.joinToString(", ")}")
            }

            if (failureReason != null) {
                appendLine("  Failure: $failureReason")
            }
        }

    /**
     * Get a detailed path report showing each hop.
     */
    fun pathReport(): String =
        buildString {
            appendLine("Traceroute path to $targetHost:")
            hops.forEach { hop ->
                append("  ${hop.hopNumber}. ")
                if (hop.isTimeout) {
                    appendLine("* * * (timeout)")
                } else {
                    append("${hop.ipAddress}")
                    if (hop.hostname != null && hop.hostname != hop.ipAddress) {
                        append(" (${hop.hostname})")
                    }
                    appendLine("  ${hop.rtt}")
                }
            }
        }

    companion object {
        /**
         * Create a TracerouteResult representing a completely failed test.
         *
         * @param targetHost The host that was attempted
         * @param reason Why the test failed
         * @return Failed TracerouteResult instance
         */
        fun failed(
            targetHost: String,
            reason: String,
        ): TracerouteResult =
            TracerouteResult(
                targetHost = targetHost,
                hops = emptyList(),
                completed = false,
                failureReason = reason,
            )
    }
}

/**
 * Represents a single hop in a traceroute path.
 *
 * Each hop corresponds to a router or gateway that forwarded the packet
 * toward the destination.
 *
 * @property hopNumber The hop sequence number (1 = first hop/gateway)
 * @property ipAddress The IP address of this hop
 * @property hostname The resolved hostname (if available)
 * @property rtt Round-trip time to this hop (null if timeout)
 * @property probesSent Number of probes sent to this hop (typically 3)
 * @property probesReceived Number of probes that got responses
 */
data class TracerouteHop(
    val hopNumber: Int,
    val ipAddress: String,
    val hostname: String? = null,
    val rtt: Duration? = null,
    val probesSent: Int = 3,
    val probesReceived: Int = if (rtt != null) probesSent else 0,
) {
    init {
        require(hopNumber > 0) { "Hop number must be positive, got $hopNumber" }
        require(ipAddress.isNotBlank()) { "IP address cannot be blank" }
        require(probesSent > 0) { "Probes sent must be positive, got $probesSent" }
        require(probesReceived in 0..probesSent) {
            "Probes received ($probesReceived) must be 0-$probesSent"
        }

        if (rtt != null) {
            require(!rtt.isNegative()) { "RTT cannot be negative" }
        }
    }

    /**
     * Whether this hop timed out (no response).
     */
    val isTimeout: Boolean = rtt == null

    /**
     * Whether this hop responded to all probes.
     */
    val isFullyResponsive: Boolean = probesReceived == probesSent

    /**
     * Packet loss rate for this hop (0.0-100.0).
     */
    val packetLossRate: Double = ((probesSent - probesReceived).toDouble() / probesSent) * 100.0

    /**
     * Display name for this hop (hostname if available, else IP).
     */
    val displayName: String = hostname ?: ipAddress

    companion object {
        /**
         * Create a TracerouteHop representing a timeout.
         *
         * @param hopNumber The hop sequence number
         * @param ipAddress The IP address (use "*" if unknown)
         * @return Timeout TracerouteHop instance
         */
        fun timeout(
            hopNumber: Int,
            ipAddress: String = "*",
        ): TracerouteHop =
            TracerouteHop(
                hopNumber = hopNumber,
                ipAddress = ipAddress,
                hostname = null,
                rtt = null,
                probesSent = 3,
                probesReceived = 0,
            )
    }
}

/**
 * Quality assessment for network routing path.
 *
 * Based on hop count and timeout rate.
 */
enum class RouteQuality {
    /** Optimal routing - few hops, minimal timeouts */
    EXCELLENT,

    /** Good routing - reasonable hops, low timeouts */
    GOOD,

    /** Fair routing - moderate hops or timeouts */
    FAIR,

    /** Poor routing - many hops or high timeouts */
    POOR,

    /** Critical routing issues - excessive hops or very high timeouts */
    CRITICAL,

    /** Traceroute failed */
    FAILED,

    ;

    /**
     * Human-readable description of the route quality.
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Optimal network routing with minimal hops"
                GOOD -> "Good network routing with reasonable path"
                FAIR -> "Fair network routing with moderate complexity"
                POOR -> "Poor network routing with excessive hops or timeouts"
                CRITICAL -> "Critical routing issues - significant path problems"
                FAILED -> "Traceroute failed to complete"
            }
}
