package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration

/**
 * Type of network bottleneck detected.
 */
enum class BottleneckType {
    /** No bottleneck - good scaling */
    NONE,

    /** Server-side limitation (per-connection limit) */
    SERVER_LIMIT,

    /** Network congestion (router, ISP) */
    NETWORK_CONGESTION,

    /** ISP traffic shaping/throttling */
    ISP_THROTTLING,

    /** Router CPU overload */
    ROUTER_OVERLOAD,

    ;

    val description: String
        get() =
            when (this) {
                NONE -> "No bottleneck detected"
                SERVER_LIMIT -> "Server limiting per-connection throughput"
                NETWORK_CONGESTION -> "Network congestion reducing throughput"
                ISP_THROTTLING -> "ISP may be throttling connections"
                ROUTER_OVERLOAD -> "Router CPU may be overloaded"
            }
}

/**
 * Individual stream result from multi-stream test.
 *
 * @property streamId Stream identifier (0-based)
 * @property throughputMbps Throughput for this stream
 * @property bytesTransferred Bytes transferred by this stream
 * @property retransmissions Number of retransmissions (if available)
 */
data class StreamResult(
    val streamId: Int,
    val throughputMbps: Double,
    val bytesTransferred: Long,
    val retransmissions: Int?,
) {
    init {
        require(streamId >= 0) { "Stream ID must be >= 0" }
        require(throughputMbps >= 0.0) { "Throughput must be >= 0" }
        require(bytesTransferred >= 0) { "Bytes transferred must be >= 0" }
        retransmissions?.let {
            require(it >= 0) { "Retransmissions must be >= 0" }
        }
    }
}

/**
 * Multi-stream bandwidth test result.
 *
 * Tests network throughput using multiple parallel TCP connections to identify
 * congestion control issues and measure parallelization efficiency. This follows
 * iperf3 standard practice of testing with 1, 4, and 8 parallel streams.
 *
 * ## Why Multi-Stream Testing
 *
 * Single TCP connection may not saturate high-bandwidth links due to:
 * - TCP congestion control conservativeness
 * - Packet loss recovery overhead
 * - Window scaling limitations
 * - Server-side per-connection limits
 *
 * Multiple parallel streams can reveal:
 * - **Good parallelization**: 8 streams ≈ 8× single stream throughput
 * - **Congestion**: 8 streams ≈ single stream (bottleneck present)
 * - **Fairness issues**: Unequal bandwidth distribution across streams
 *
 * ## Typical Results
 *
 * - **Gigabit WiFi 6**: Single stream 400Mbps, 4 streams 800Mbps, 8 streams 900Mbps
 * - **With congestion**: Single stream 100Mbps, 4 streams 120Mbps, 8 streams 130Mbps
 * - **Server limited**: Single stream 50Mbps, 4 streams 50Mbps, 8 streams 50Mbps
 *
 * ## References
 * - iperf3 documentation: Multi-stream testing with -P flag
 * - RFC 5681: TCP Congestion Control
 *
 * @property singleStreamMbps Throughput with 1 TCP connection
 * @property fourStreamMbps Throughput with 4 parallel TCP connections
 * @property eightStreamMbps Throughput with 8 parallel TCP connections
 * @property parallelizationEfficiency How well throughput scales with streams (0-100%)
 * @property congestionDetected Whether network congestion was detected
 * @property bottleneckType Type of bottleneck if detected
 * @property fairnessIndex Jain's fairness index across streams (0-1)
 * @property perStreamResults Individual stream results (if available)
 * @property testDuration Duration of each stream test
 *
 * @since 1.1.0
 */
data class MultiStreamBandwidthTest(
    val singleStreamMbps: Double,
    val fourStreamMbps: Double,
    val eightStreamMbps: Double,
    val parallelizationEfficiency: Double,
    val congestionDetected: Boolean,
    val bottleneckType: BottleneckType?,
    val fairnessIndex: Double,
    val perStreamResults: List<StreamResult> = emptyList(),
    val testDuration: Duration,
    val timestamp: Long = System.currentTimeMillis(),
) {
    init {
        require(singleStreamMbps >= 0.0) { "Single stream Mbps must be >= 0" }
        require(fourStreamMbps >= 0.0) { "Four stream Mbps must be >= 0" }
        require(eightStreamMbps >= 0.0) { "Eight stream Mbps must be >= 0" }
        require(parallelizationEfficiency in 0.0..100.0) {
            "Parallelization efficiency must be 0-100%"
        }
        require(fairnessIndex in 0.0..1.0) {
            "Fairness index must be 0-1"
        }
        require(!testDuration.isNegative()) { "Test duration cannot be negative" }
    }

    /**
     * Parallelization scaling factor (how well it scales).
     *
     * Ideal: 8 streams = 8× single stream (factor = 8.0)
     * Reality: Usually 4-6× due to overhead and congestion
     */
    val scalingFactor: Double =
        if (singleStreamMbps > 0.0) {
            eightStreamMbps / singleStreamMbps
        } else {
            0.0
        }

    /**
     * Whether parallelization is effective (good scaling).
     */
    val effectiveParallelization: Boolean = parallelizationEfficiency >= 60.0

    /**
     * Whether throughput is limited by single-connection TCP behavior.
     */
    val singleConnectionLimited: Boolean = scalingFactor >= 2.0

    /**
     * Recommendations based on multi-stream results.
     */
    fun recommendations(): List<String> =
        buildList {
            when {
                parallelizationEfficiency >= 80.0 -> {
                    add("✓ Excellent parallelization - network not congested")
                    add("Single connection performance may improve with TCP tuning")
                }
                parallelizationEfficiency >= 60.0 -> {
                    add("Good parallelization - network handling load well")
                    if (singleStreamMbps < 100.0) {
                        add("Single stream limited - consider TCP window tuning")
                    }
                }
                parallelizationEfficiency >= 40.0 -> {
                    add("⚠ Moderate congestion detected")
                    add("Bottleneck type: ${bottleneckType?.description ?: "Unknown"}")
                    add("Network struggles with multiple connections")
                    add("Check: Other devices, QoS settings, ISP throttling")
                }
                else -> {
                    add("⚠ Severe congestion or bottleneck detected")
                    add("Bottleneck: ${bottleneckType?.description ?: "Unknown"}")
                    add("Adding connections does NOT improve throughput")
                    add("Likely causes:")
                    add("  - Network congestion (too many devices)")
                    add("  - ISP throttling")
                    add("  - Router CPU overload")
                    add("  - Bufferbloat (check latency under load)")
                }
            }
        }

    companion object {
        /**
         * Calculate parallelization efficiency.
         *
         * Efficiency = (8-stream throughput / single-stream throughput) / 8 × 100%
         *
         * Perfect scaling: 100% (8 streams = 8× single)
         * Good scaling: 60-80%
         * Poor scaling: <60%
         *
         * @param singleStream Single connection throughput
         * @param eightStream Eight connection throughput
         * @return Efficiency percentage (0-100%)
         */
        fun calculateEfficiency(
            singleStream: Double,
            eightStream: Double,
        ): Double {
            if (singleStream == 0.0) return 0.0

            val actualScaling = eightStream / singleStream
            val idealScaling = 8.0
            val efficiency = (actualScaling / idealScaling) * 100.0

            return efficiency.coerceIn(0.0, 100.0)
        }
    }
}
