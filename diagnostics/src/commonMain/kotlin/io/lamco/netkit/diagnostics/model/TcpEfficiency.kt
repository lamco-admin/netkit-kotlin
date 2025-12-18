package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration

/**
 * TCP efficiency level classification.
 */
enum class EfficiencyLevel {
    /** 90-100% - Excellent TCP utilization */
    EXCELLENT,

    /** 70-89% - Good TCP utilization */
    GOOD,

    /** 50-69% - Fair TCP utilization, room for improvement */
    FAIR,

    /** 30-49% - Poor TCP utilization, investigation needed */
    POOR,

    /** <30% - Critical TCP inefficiency, serious issues */
    CRITICAL,

    ;

    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent TCP efficiency - fully utilizing available bandwidth"
                GOOD -> "Good TCP efficiency - minor optimization possible"
                FAIR -> "Fair TCP efficiency - moderate improvements possible"
                POOR -> "Poor TCP efficiency - significant issues present"
                CRITICAL -> "Critical TCP inefficiency - serious network or TCP stack issues"
            }
}

/**
 * Primary reason for TCP inefficiency.
 */
enum class TcpInefficiencyReason {
    /** TCP window size too small for bandwidth-delay product */
    SMALL_WINDOW,

    /** Packet loss causing retransmissions and reduced throughput */
    PACKET_LOSS,

    /** Network congestion throttling throughput */
    CONGESTION,

    /** Server-side throughput limitation */
    SERVER_LIMIT,

    /** Client-side throughput limitation (CPU, disk, NIC) */
    CLIENT_LIMIT,

    ;

    val description: String
        get() =
            when (this) {
                SMALL_WINDOW -> "TCP window size insufficient for network capacity"
                PACKET_LOSS -> "Packet loss reducing effective throughput"
                CONGESTION -> "Network congestion limiting throughput"
                SERVER_LIMIT -> "Server cannot send faster (server-side limitation)"
                CLIENT_LIMIT -> "Client cannot receive faster (client-side limitation)"
            }
}

/**
 * TCP efficiency metrics per RFC 6349.
 *
 * TCP efficiency measures how effectively TCP is utilizing the available bandwidth
 * by comparing actual throughput against the theoretical maximum based on TCP window
 * size and round-trip time (RTT).
 *
 * ## Bandwidth-Delay Product (BDP)
 *
 * The theoretical maximum TCP throughput is determined by:
 * ```
 * Max Throughput (bps) = TCP Window Size (bytes) × 8 / RTT (seconds)
 * ```
 *
 * This is the Bandwidth-Delay Product - the maximum amount of unacknowledged data
 * that can be in flight on the network.
 *
 * ## Efficiency Calculation
 *
 * ```
 * Efficiency (%) = (Actual Throughput / Theoretical Max Throughput) × 100
 * ```
 *
 * ## Interpretation
 *
 * - **90-100%**: Excellent - TCP utilizing available bandwidth fully
 * - **70-89%**: Good - Minor inefficiency, possibly transient packet loss
 * - **50-69%**: Fair - Moderate inefficiency, investigate window size or packet loss
 * - **30-49%**: Poor - Significant inefficiency, TCP stack or network issues
 * - **<30%**: Critical - Severe inefficiency, requires investigation
 *
 * ## Common Inefficiency Causes
 *
 * 1. **Small TCP Window**: Window size < BDP (increase window)
 * 2. **Packet Loss**: Retransmissions reduce effective throughput
 * 3. **Network Congestion**: Congestion control throttling throughput
 * 4. **Server Limitation**: Server cannot send faster
 * 5. **Client Limitation**: Client cannot receive faster
 *
 * ## References
 * - RFC 6349 Section 4.1: TCP Efficiency Metric
 * - RFC 7413: TCP Fast Open
 * - RFC 7323: TCP Window Scaling
 *
 * @property theoreticalMaxMbps Maximum throughput based on window/RTT (Mbps)
 * @property measuredMbps Actual measured throughput (Mbps)
 * @property efficiency Efficiency percentage (0-100%)
 * @property windowSizeBytes TCP window size in bytes
 * @property rtt Round-trip time
 * @property bdp Bandwidth-Delay Product in bytes
 * @property inefficiencyReason Primary reason for inefficiency (if any)
 * @property recommendedWindowSize Recommended window size for this network
 *
 * @since 1.1.0
 */
data class TcpEfficiency(
    val theoreticalMaxMbps: Double,
    val measuredMbps: Double,
    val efficiency: Double,
    val windowSizeBytes: Int,
    val rtt: Duration,
    val bdp: Long,
    val inefficiencyReason: TcpInefficiencyReason?,
    val recommendedWindowSize: Int,
) {
    init {
        require(theoreticalMaxMbps >= 0.0) { "Theoretical max must be >= 0" }
        require(measuredMbps >= 0.0) { "Measured mbps must be >= 0" }
        require(efficiency in 0.0..100.0) { "Efficiency must be 0-100%, got: $efficiency" }
        require(windowSizeBytes > 0) { "Window size must be > 0" }
        require(!rtt.isNegative()) { "RTT cannot be negative" }
        require(bdp >= 0) { "BDP must be >= 0" }
        require(recommendedWindowSize > 0) { "Recommended window must be > 0" }
    }

    /**
     * Efficiency quality level.
     */
    val efficiencyLevel: EfficiencyLevel
        get() =
            when {
                efficiency >= 90.0 -> EfficiencyLevel.EXCELLENT
                efficiency >= 70.0 -> EfficiencyLevel.GOOD
                efficiency >= 50.0 -> EfficiencyLevel.FAIR
                efficiency >= 30.0 -> EfficiencyLevel.POOR
                else -> EfficiencyLevel.CRITICAL
            }

    /**
     * Whether TCP is operating efficiently.
     */
    val isEfficient: Boolean = efficiency >= 70.0

    /**
     * Whether window size is adequate for this network.
     */
    val windowSizeAdequate: Boolean = windowSizeBytes >= bdp

    /**
     * Throughput deficit (how much throughput is being lost).
     */
    val throughputDeficit: Double = theoreticalMaxMbps - measuredMbps

    /**
     * Recommendations to improve TCP efficiency.
     */
    fun recommendations(): List<String> =
        buildList {
            when (inefficiencyReason) {
                TcpInefficiencyReason.SMALL_WINDOW -> {
                    add("TCP window size ($windowSizeBytes bytes) is smaller than BDP ($bdp bytes)")
                    add("Recommended window size: $recommendedWindowSize bytes")
                    add("Enable TCP window scaling (RFC 7323)")
                    add("Expected improvement: +${"%.1f".format(throughputDeficit)} Mbps")
                }
                TcpInefficiencyReason.PACKET_LOSS -> {
                    add("Packet loss is reducing TCP efficiency")
                    add("Investigate: WiFi signal quality, interference, congestion")
                    add("Check: Packet loss rate in ping test")
                    add("Consider: Different WiFi channel, closer to AP, 5GHz band")
                }
                TcpInefficiencyReason.CONGESTION -> {
                    add("Network congestion detected")
                    add("Too many active connections or heavy traffic")
                    add("Consider: QoS/traffic shaping, limit concurrent connections")
                }
                TcpInefficiencyReason.SERVER_LIMIT -> {
                    add("Server-side throughput limitation")
                    add("Server cannot send faster than $measuredMbps Mbps")
                    add("This is not a local network issue")
                }
                TcpInefficiencyReason.CLIENT_LIMIT -> {
                    add("Client-side throughput limitation")
                    add("Device CPU, disk, or network adapter limiting throughput")
                    add("Check: Device capabilities, background processes")
                }
                null -> {
                    add("✓ TCP operating at ${"%.1f".format(efficiency)}% efficiency")
                    if (efficiency < 90.0) {
                        add("Minor inefficiency detected but within acceptable range")
                    }
                }
            }
        }

    companion object {
        /**
         * Calculate bandwidth-delay product.
         *
         * BDP = Bandwidth (bps) × RTT (seconds)
         * Represents the optimal TCP window size.
         *
         * @param bandwidthMbps Available bandwidth in Mbps
         * @param rtt Round-trip time
         * @return BDP in bytes
         */
        fun calculateBdp(
            bandwidthMbps: Double,
            rtt: Duration,
        ): Long {
            val bandwidthBps = bandwidthMbps * 1_000_000.0
            val rttSeconds = rtt.inWholeMilliseconds / 1000.0
            val bdpBits = bandwidthBps * rttSeconds
            return (bdpBits / 8.0).toLong()
        }
    }
}
