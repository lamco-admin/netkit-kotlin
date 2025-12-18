package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents the result of an ICMP ping test to a network host.
 *
 * This model captures comprehensive ping test results including packet statistics,
 * round-trip time (RTT) measurements, packet loss, and jitter analysis. It follows
 * RFC 792 (ICMP) and RFC 2681 (Round-Trip Time Measurement) standards.
 *
 * ## Quality Assessment
 *
 * Ping quality is assessed based on:
 * - **Latency**: Average RTT (lower is better)
 * - **Packet Loss**: Percentage of lost packets (0% is ideal)
 * - **Jitter**: RTT variance/standard deviation (lower is better)
 *
 * Quality levels:
 * - **EXCELLENT**: <20ms avg RTT, 0% loss, <5ms jitter
 * - **GOOD**: <50ms avg RTT, <1% loss, <10ms jitter
 * - **FAIR**: <100ms avg RTT, <5% loss, <20ms jitter
 * - **POOR**: <200ms avg RTT, <10% loss, <50ms jitter
 * - **CRITICAL**: >=200ms avg RTT or >=10% loss or >=50ms jitter
 *
 * ## Usage Example
 *
 * ```kotlin
 * val pingResult = PingTest(
 *     targetHost = "8.8.8.8",
 *     packetsTransmitted = 10,
 *     packetsReceived = 10,
 *     packetLossPercent = 0.0,
 *     minRtt = 5.2.milliseconds,
 *     avgRtt = 8.7.milliseconds,
 *     maxRtt = 15.3.milliseconds,
 *     stdDevRtt = 2.1.milliseconds
 * )
 *
 * println("Latency Quality: ${pingResult.latencyQuality}")
 * println("Overall Quality: ${pingResult.overallQuality}")
 * println("Jitter: ${pingResult.jitter}")
 * ```
 *
 * @property targetHost The hostname or IP address that was pinged
 * @property packetsTransmitted Total number of ICMP packets sent
 * @property packetsReceived Number of ICMP packets successfully received
 * @property packetLossPercent Percentage of packets lost (0.0-100.0)
 * @property minRtt Minimum round-trip time observed
 * @property avgRtt Average round-trip time
 * @property maxRtt Maximum round-trip time observed
 * @property stdDevRtt Standard deviation of round-trip times (jitter indicator)
 * @property testDuration Total duration of the ping test
 * @property timestamp When the test was performed (epoch millis)
 * @property failureReason Optional reason if the test failed completely
 *
 * @see LatencyQuality
 * @see PingQuality
 * @see RFC792
 * @see RFC2681
 */
data class PingTest(
    val targetHost: String,
    val packetsTransmitted: Int,
    val packetsReceived: Int,
    val packetLossPercent: Double,
    val minRtt: Duration?,
    val avgRtt: Duration?,
    val maxRtt: Duration?,
    val stdDevRtt: Duration?,
    val testDuration: Duration = Duration.ZERO,
    val timestamp: Long = System.currentTimeMillis(),
    val failureReason: String? = null,
) {
    init {
        require(targetHost.isNotBlank()) { "Target host cannot be blank" }
        require(packetsTransmitted >= 0) { "Packets transmitted must be >= 0, got $packetsTransmitted" }
        require(packetsReceived >= 0) { "Packets received must be >= 0, got $packetsReceived" }
        require(packetsReceived <= packetsTransmitted) {
            "Packets received ($packetsReceived) cannot exceed transmitted ($packetsTransmitted)"
        }
        require(packetLossPercent in 0.0..100.0) {
            "Packet loss percent must be in 0.0-100.0, got $packetLossPercent"
        }
        require(!testDuration.isNegative()) { "Test duration cannot be negative" }

        // RTT validation: if any RTT is non-null, avgRtt must be non-null
        if (minRtt != null || maxRtt != null || stdDevRtt != null) {
            requireNotNull(avgRtt) { "avgRtt must be non-null if any RTT values are provided" }
        }

        // RTT ordering validation
        if (minRtt != null && maxRtt != null && avgRtt != null) {
            require(minRtt <= maxRtt) { "minRtt ($minRtt) must be <= maxRtt ($maxRtt)" }
            require(avgRtt >= minRtt && avgRtt <= maxRtt) {
                "avgRtt ($avgRtt) must be between minRtt ($minRtt) and maxRtt ($maxRtt)"
            }
        }
    }

    /**
     * Whether the ping test completed successfully with at least one response.
     */
    val isSuccessful: Boolean = packetsReceived > 0 && failureReason == null

    /**
     * Whether the test failed completely (no packets received).
     */
    val isFailed: Boolean = packetsReceived == 0 || failureReason != null

    /**
     * Jitter measurement (RTT standard deviation).
     * Jitter indicates network stability - lower values mean more consistent latency.
     */
    val jitter: Duration? = stdDevRtt

    /**
     * Response rate as a percentage (0.0-100.0).
     * Complement of packet loss percentage.
     */
    val responseRate: Double = 100.0 - packetLossPercent

    /**
     * Latency quality assessment based on average RTT.
     */
    val latencyQuality: LatencyQuality
        get() =
            when {
                avgRtt == null -> LatencyQuality.UNKNOWN
                avgRtt < 20.milliseconds -> LatencyQuality.EXCELLENT
                avgRtt < 50.milliseconds -> LatencyQuality.GOOD
                avgRtt < 100.milliseconds -> LatencyQuality.FAIR
                avgRtt < 200.milliseconds -> LatencyQuality.POOR
                else -> LatencyQuality.CRITICAL
            }

    /**
     * Packet loss quality assessment.
     */
    val packetLossQuality: PacketLossQuality
        get() =
            when {
                packetLossPercent == 0.0 -> PacketLossQuality.NONE
                packetLossPercent < 1.0 -> PacketLossQuality.MINIMAL
                packetLossPercent < 5.0 -> PacketLossQuality.MODERATE
                packetLossPercent < 10.0 -> PacketLossQuality.HIGH
                else -> PacketLossQuality.SEVERE
            }

    /**
     * Jitter quality assessment based on RTT standard deviation.
     */
    val jitterQuality: JitterQuality
        get() =
            when {
                stdDevRtt == null -> JitterQuality.UNKNOWN
                stdDevRtt < 5.milliseconds -> JitterQuality.EXCELLENT
                stdDevRtt < 10.milliseconds -> JitterQuality.GOOD
                stdDevRtt < 20.milliseconds -> JitterQuality.FAIR
                stdDevRtt < 50.milliseconds -> JitterQuality.POOR
                else -> JitterQuality.CRITICAL
            }

    /**
     * Overall ping quality assessment combining latency, packet loss, and jitter.
     *
     * The overall quality is determined by the worst of the three metrics,
     * as network quality is limited by the weakest aspect.
     */
    val overallQuality: PingQuality
        get() {
            if (isFailed) return PingQuality.FAILED

            val latencyScore = latencyQuality.toScore()
            val packetLossScore = packetLossQuality.toScore()
            val jitterScore = jitterQuality.toScore()

            // Overall quality is worst of the three
            val worstScore = minOf(latencyScore, packetLossScore, jitterScore)

            return when {
                worstScore >= 90 -> PingQuality.EXCELLENT
                worstScore >= 70 -> PingQuality.GOOD
                worstScore >= 50 -> PingQuality.FAIR
                worstScore >= 30 -> PingQuality.POOR
                else -> PingQuality.CRITICAL
            }
        }

    /**
     * Human-readable summary of the ping test results.
     */
    fun summary(): String =
        buildString {
            appendLine("Ping Test Results: $targetHost")
            appendLine("  Packets: $packetsReceived/$packetsTransmitted received ($packetLossPercent% loss)")
            if (avgRtt != null) {
                appendLine("  Latency: min=$minRtt, avg=$avgRtt, max=$maxRtt")
                if (stdDevRtt != null) {
                    appendLine("  Jitter: $stdDevRtt")
                }
            }
            appendLine("  Quality: $overallQuality")
            if (failureReason != null) {
                appendLine("  Failure: $failureReason")
            }
        }

    /**
     * Check if the ping test meets minimum quality standards.
     *
     * @param maxLatency Maximum acceptable average latency
     * @param maxPacketLoss Maximum acceptable packet loss percentage
     * @param maxJitter Maximum acceptable jitter
     * @return true if all criteria are met
     */
    fun meetsQualityStandards(
        maxLatency: Duration = 100.milliseconds,
        maxPacketLoss: Double = 5.0,
        maxJitter: Duration = 20.milliseconds,
    ): Boolean {
        if (isFailed) return false
        if (avgRtt == null) return false

        val latencyOk = avgRtt <= maxLatency
        val packetLossOk = packetLossPercent <= maxPacketLoss
        val jitterOk = stdDevRtt == null || stdDevRtt <= maxJitter

        return latencyOk && packetLossOk && jitterOk
    }

    companion object {
        /**
         * Create a PingTest representing a completely failed test.
         *
         * @param targetHost The host that was attempted
         * @param packetsTransmitted How many packets were sent
         * @param reason Why the test failed
         * @return Failed PingTest instance
         */
        fun failed(
            targetHost: String,
            packetsTransmitted: Int = 0,
            reason: String,
        ): PingTest =
            PingTest(
                targetHost = targetHost,
                packetsTransmitted = packetsTransmitted,
                packetsReceived = 0,
                packetLossPercent = 100.0,
                minRtt = null,
                avgRtt = null,
                maxRtt = null,
                stdDevRtt = null,
                failureReason = reason,
            )
    }
}

/**
 * Quality classification for network latency based on average RTT.
 *
 * Based on ITU-T G.1010 recommendations for IP network QoS.
 */
enum class LatencyQuality {
    /** <20ms - Excellent latency, suitable for real-time gaming */
    EXCELLENT,

    /** 20-49ms - Good latency, suitable for VoIP and video */
    GOOD,

    /** 50-99ms - Fair latency, acceptable for most applications */
    FAIR,

    /** 100-199ms - Poor latency, may impact user experience */
    POOR,

    /** >=200ms - Critical latency, significant delays noticeable */
    CRITICAL,

    /** Latency cannot be determined */
    UNKNOWN,

    ;

    /**
     * Convert latency quality to a 0-100 score.
     */
    fun toScore(): Int =
        when (this) {
            EXCELLENT -> 100
            GOOD -> 80
            FAIR -> 60
            POOR -> 40
            CRITICAL -> 20
            UNKNOWN -> 0
        }
}

/**
 * Quality classification for packet loss percentage.
 */
enum class PacketLossQuality {
    /** 0% packet loss - Perfect */
    NONE,

    /** <1% packet loss - Minimal impact */
    MINIMAL,

    /** 1-4% packet loss - Moderate impact */
    MODERATE,

    /** 5-9% packet loss - High impact */
    HIGH,

    /** >=10% packet loss - Severe issues */
    SEVERE,

    ;

    /**
     * Convert packet loss quality to a 0-100 score.
     */
    fun toScore(): Int =
        when (this) {
            NONE -> 100
            MINIMAL -> 85
            MODERATE -> 60
            HIGH -> 35
            SEVERE -> 10
        }
}

/**
 * Quality classification for jitter (RTT variance).
 *
 * Jitter is critical for real-time applications like VoIP and gaming.
 */
enum class JitterQuality {
    /** <5ms jitter - Excellent stability */
    EXCELLENT,

    /** 5-9ms jitter - Good stability */
    GOOD,

    /** 10-19ms jitter - Fair stability */
    FAIR,

    /** 20-49ms jitter - Poor stability */
    POOR,

    /** >=50ms jitter - Critical instability */
    CRITICAL,

    /** Jitter cannot be determined */
    UNKNOWN,

    ;

    /**
     * Convert jitter quality to a 0-100 score.
     */
    fun toScore(): Int =
        when (this) {
            EXCELLENT -> 100
            GOOD -> 80
            FAIR -> 60
            POOR -> 40
            CRITICAL -> 20
            UNKNOWN -> 0
        }
}

/**
 * Overall ping test quality assessment.
 *
 * Combines latency, packet loss, and jitter into a single quality metric.
 */
enum class PingQuality {
    /** All metrics excellent - Network performing optimally */
    EXCELLENT,

    /** Most metrics good - Network performing well */
    GOOD,

    /** Acceptable performance - Some issues may be present */
    FAIR,

    /** Poor performance - Issues likely impacting user experience */
    POOR,

    /** Critical issues - Network severely degraded */
    CRITICAL,

    /** Test failed completely */
    FAILED,

    ;

    /**
     * Human-readable description of the quality level.
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent network performance - suitable for all applications"
                GOOD -> "Good network performance - suitable for most applications"
                FAIR -> "Fair network performance - acceptable for general use"
                POOR -> "Poor network performance - may impact user experience"
                CRITICAL -> "Critical network issues - significant degradation"
                FAILED -> "Test failed - unable to reach target"
            }
}
