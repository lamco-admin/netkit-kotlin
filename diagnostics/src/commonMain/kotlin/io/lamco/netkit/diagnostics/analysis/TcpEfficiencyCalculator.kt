package io.lamco.netkit.diagnostics.analysis

import io.lamco.netkit.diagnostics.model.TcpEfficiency
import io.lamco.netkit.diagnostics.model.TcpInefficiencyReason
import kotlin.time.Duration

/**
 * TCP efficiency calculator per RFC 6349.
 *
 * Calculates TCP efficiency metrics by comparing actual measured throughput
 * against the theoretical maximum throughput based on TCP window size and RTT.
 *
 * ## Calculation Method
 *
 * 1. Calculate theoretical max: `Window Size (bits) / RTT (seconds)`
 * 2. Calculate efficiency: `(Measured / Theoretical) × 100%`
 * 3. Calculate BDP: `Bandwidth × RTT` (optimal window size)
 * 4. Diagnose inefficiency reason
 * 5. Recommend optimal window size
 *
 * ## RFC 6349 Compliance
 *
 * Implements Section 4.1 (TCP Efficiency Metric) of RFC 6349:
 * - Theoretical throughput calculation
 * - Efficiency percentage
 * - BDP calculation
 * - Window size recommendations
 *
 * @since 1.1.0
 */
object TcpEfficiencyCalculator {
    /**
     * Calculate TCP efficiency from bandwidth test and ping results.
     *
     * @param measuredMbps Actual measured throughput (from bandwidth test)
     * @param windowSizeBytes TCP window size in bytes
     * @param rtt Round-trip time (from ping test)
     * @param packetLossPercent Packet loss percentage (from ping test)
     * @return TCP efficiency analysis
     */
    fun calculate(
        measuredMbps: Double,
        windowSizeBytes: Int,
        rtt: Duration,
        packetLossPercent: Double = 0.0,
    ): TcpEfficiency {
        require(measuredMbps >= 0.0) { "Measured Mbps must be >= 0" }
        require(windowSizeBytes > 0) { "Window size must be > 0" }
        require(!rtt.isNegative()) { "RTT cannot be negative" }
        require(packetLossPercent in 0.0..100.0) { "Packet loss must be 0-100%" }

        // Calculate theoretical maximum throughput
        // Max Throughput (Mbps) = (Window Size in bits) / (RTT in seconds) / 1,000,000
        val windowBits = windowSizeBytes * 8.0
        val rttSeconds = rtt.inWholeMilliseconds / 1000.0

        if (rttSeconds == 0.0) {
            throw IllegalArgumentException("RTT cannot be zero")
        }

        val theoreticalMaxMbps = (windowBits / rttSeconds) / 1_000_000.0

        // Calculate efficiency percentage
        val efficiency =
            if (theoreticalMaxMbps > 0.0) {
                (measuredMbps / theoreticalMaxMbps) * 100.0
            } else {
                0.0
            }.coerceIn(0.0, 100.0)

        // Calculate bandwidth-delay product (optimal window size)
        val bdp = TcpEfficiency.calculateBdp(measuredMbps, rtt)

        // Diagnose inefficiency reason
        val inefficiencyReason =
            diagnoseInefficiency(
                efficiency = efficiency,
                windowSizeBytes = windowSizeBytes,
                bdp = bdp,
                packetLossPercent = packetLossPercent,
                measuredMbps = measuredMbps,
                theoreticalMaxMbps = theoreticalMaxMbps,
            )

        // Recommend optimal window size (BDP with safety margin)
        val recommendedWindowSize = calculateRecommendedWindowSize(bdp, rtt)

        return TcpEfficiency(
            theoreticalMaxMbps = theoreticalMaxMbps,
            measuredMbps = measuredMbps,
            efficiency = efficiency,
            windowSizeBytes = windowSizeBytes,
            rtt = rtt,
            bdp = bdp,
            inefficiencyReason = inefficiencyReason,
            recommendedWindowSize = recommendedWindowSize,
        )
    }

    /**
     * Diagnose primary reason for TCP inefficiency.
     */
    private fun diagnoseInefficiency(
        efficiency: Double,
        windowSizeBytes: Int,
        bdp: Long,
        packetLossPercent: Double,
        measuredMbps: Double,
        theoreticalMaxMbps: Double,
    ): TcpInefficiencyReason? {
        // If efficiency is good, no significant inefficiency
        if (efficiency >= 70.0) {
            return null
        }

        // Check for small window (most common issue)
        if (windowSizeBytes < bdp * 0.5) {
            return TcpInefficiencyReason.SMALL_WINDOW
        }

        // Check for packet loss (causes retransmissions)
        if (packetLossPercent > 1.0) {
            return TcpInefficiencyReason.PACKET_LOSS
        }

        // Check for congestion (measured much less than window allows)
        if (measuredMbps < theoreticalMaxMbps * 0.3) {
            return TcpInefficiencyReason.CONGESTION
        }

        // If measured is close to a common limit, might be server/client limit
        val commonLimits = listOf(10.0, 100.0, 1000.0) // 10Mbps, 100Mbps, 1Gbps
        if (commonLimits.any { kotlin.math.abs(measuredMbps - it) < 5.0 }) {
            // Hard to distinguish server vs. client - default to server
            return TcpInefficiencyReason.SERVER_LIMIT
        }

        // Default: likely congestion or unidentified issue
        return TcpInefficiencyReason.CONGESTION
    }

    /**
     * Calculate recommended TCP window size.
     *
     * Uses BDP plus 20% safety margin to account for variance.
     *
     * @param bdp Bandwidth-Delay Product
     * @param rtt Round-trip time
     * @return Recommended window size in bytes
     */
    private fun calculateRecommendedWindowSize(
        bdp: Long,
        rtt: Duration,
    ): Int {
        // Add 20% safety margin
        val recommendedBdp = (bdp * 1.2).toLong()

        // Clamp to reasonable values
        // Min: 64KB (minimum for modern networks)
        // Max: 16MB (practical maximum for most systems)
        val minWindow = 64 * 1024
        val maxWindow = 16 * 1024 * 1024

        return recommendedBdp.toInt().coerceIn(minWindow, maxWindow)
    }

    /**
     * Estimate TCP window size from measured throughput and RTT.
     *
     * When actual window size is unknown, estimate from observed throughput.
     * This gives the "effective" window size.
     *
     * @param throughputMbps Measured throughput
     * @param rtt Round-trip time
     * @return Estimated window size in bytes
     */
    fun estimateWindowSize(
        throughputMbps: Double,
        rtt: Duration,
    ): Int {
        // Window (bytes) = Throughput (bps) × RTT (seconds) / 8
        val throughputBps = throughputMbps * 1_000_000.0
        val rttSeconds = rtt.inWholeMilliseconds / 1000.0
        val windowBytes = (throughputBps * rttSeconds / 8.0).toLong()

        return windowBytes.toInt().coerceAtLeast(1024)
    }
}
