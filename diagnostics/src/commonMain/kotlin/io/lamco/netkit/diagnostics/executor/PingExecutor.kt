package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.PingTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Interface for executing ICMP ping tests.
 *
 * PingExecutor provides an abstraction for running ping tests across different platforms
 * (Android, JVM, native). Implementations handle platform-specific command execution
 * while this interface defines the contract and provides common parsing utilities.
 *
 * ## Platform Implementations
 *
 * - **Android**: Uses `ping` command via ProcessBuilder or Runtime.exec()
 * - **JVM**: Uses native `ping` command via ProcessBuilder
 * - **iOS/Native**: Uses platform-specific APIs
 *
 * ## Usage Example
 *
 * ```kotlin
 * val executor = AndroidPingExecutor()
 * val result = executor.executePing(
 *     targetHost = "8.8.8.8",
 *     packetCount = 10,
 *     timeout = 5.seconds
 * )
 * println("Latency: ${result.avgRtt}")
 * ```
 *
 * @see PingTest
 * @see PingParser
 */
interface PingExecutor {
    /**
     * Execute a ping test to the specified host.
     *
     * @param targetHost The hostname or IP address to ping
     * @param packetCount Number of ICMP packets to send (default: 10)
     * @param packetSize Size of each packet in bytes (default: 56, total 64 with ICMP header)
     * @param timeout Maximum time to wait for all responses
     * @param interval Interval between packets (default: 1 second)
     * @return PingTest result with statistics
     * @throws PingExecutionException if execution fails
     */
    suspend fun executePing(
        targetHost: String,
        packetCount: Int = 10,
        packetSize: Int = 56,
        timeout: Duration = Duration.INFINITE,
        interval: Duration = 1000.milliseconds,
    ): PingTest

    /**
     * Check if ping functionality is available on this platform.
     *
     * @return true if ping can be executed
     */
    fun isPingAvailable(): Boolean
}

/**
 * Parser for ping command output.
 *
 * Supports parsing output from common ping implementations:
 * - Linux `ping` (iputils)
 * - macOS `ping` (BSD)
 * - Android `ping`
 * - Windows `ping` (different output format)
 *
 * ## Output Format Examples
 *
 * **Linux/Android**:
 * ```
 * PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
 * 64 bytes from 8.8.8.8: icmp_seq=1 ttl=119 time=8.23 ms
 * ...
 * --- 8.8.8.8 ping statistics ---
 * 10 packets transmitted, 10 received, 0% packet loss, time 9012ms
 * rtt min/avg/max/mdev = 5.123/8.456/12.789/2.345 ms
 * ```
 *
 * **Windows**:
 * ```
 * Pinging 8.8.8.8 with 32 bytes of data:
 * Reply from 8.8.8.8: bytes=32 time=8ms TTL=119
 * ...
 * Ping statistics for 8.8.8.8:
 *     Packets: Sent = 10, Received = 10, Lost = 0 (0% loss),
 * Approximate round trip times in milli-seconds:
 *     Minimum = 5ms, Maximum = 12ms, Average = 8ms
 * ```
 */
object PingParser {
    /**
     * Parse ping command output into a PingTest result.
     *
     * @param output The raw output from ping command
     * @param targetHost The host that was pinged
     * @param testDuration How long the test took
     * @return Parsed PingTest result
     * @throws PingParseException if output cannot be parsed
     */
    fun parse(
        output: String,
        targetHost: String,
        testDuration: Duration = Duration.ZERO,
    ): PingTest =
        try {
            if (isLinuxFormat(output)) {
                parseLinuxFormat(output, targetHost, testDuration)
            } else if (isWindowsFormat(output)) {
                parseWindowsFormat(output, targetHost, testDuration)
            } else {
                throw PingParseException("Unknown ping output format")
            }
        } catch (e: PingParseException) {
            PingTest.failed(
                targetHost = targetHost,
                reason = "Parse error: ${e.message}",
            )
        }

    /**
     * Check if output is Linux/Unix/Android format.
     */
    private fun isLinuxFormat(output: String): Boolean =
        output.contains("packets transmitted") ||
            output.contains("rtt min/avg/max")

    /**
     * Check if output is Windows format.
     */
    private fun isWindowsFormat(output: String): Boolean =
        output.contains("Packets: Sent =") ||
            output.contains("Approximate round trip times")

    /**
     * Parse Linux/Unix/Android ping output.
     *
     * Expected format:
     * ```
     * 10 packets transmitted, 10 received, 0% packet loss, time 9012ms
     * rtt min/avg/max/mdev = 5.123/8.456/12.789/2.345 ms
     * ```
     */
    private fun parseLinuxFormat(
        output: String,
        targetHost: String,
        testDuration: Duration,
    ): PingTest {
        val lines = output.lines()

        // Find statistics line
        val statsLine =
            lines.find { it.contains("packets transmitted") }
                ?: throw PingParseException("Statistics line not found")

        // Validate and parse packet statistics
        val stats = parseLinuxPacketStats(statsLine)
        val (transmitted, received) = stats

        val packetLoss =
            extractDouble(statsLine, """(\d+(?:\.\d+)?)%\s+packet loss""")
                ?: 0.0

        // Find RTT line
        val rttLine = lines.find { it.contains("rtt min/avg/max") }

        if (rttLine != null && received > 0) {
            // Parse RTT statistics: "rtt min/avg/max/mdev = 5.123/8.456/12.789/2.345 ms"
            val rttPattern = """rtt min/avg/max/(?:mdev|stddev) = ([\d.]+)/([\d.]+)/([\d.]+)/([\d.]+)""".toRegex()
            val match = rttPattern.find(rttLine)

            if (match != null) {
                val (min, avg, max, stddev) = match.destructured
                return PingTest(
                    targetHost = targetHost,
                    packetsTransmitted = transmitted,
                    packetsReceived = received,
                    packetLossPercent = packetLoss,
                    minRtt = min.toDouble().milliseconds,
                    avgRtt = avg.toDouble().milliseconds,
                    maxRtt = max.toDouble().milliseconds,
                    stdDevRtt = stddev.toDouble().milliseconds,
                    testDuration = testDuration,
                )
            }
        }

        // No RTT data (all packets lost or no RTT line)
        return PingTest(
            targetHost = targetHost,
            packetsTransmitted = transmitted,
            packetsReceived = received,
            packetLossPercent = packetLoss,
            minRtt = null,
            avgRtt = null,
            maxRtt = null,
            stdDevRtt = null,
            testDuration = testDuration,
        )
    }

    /**
     * Parse Windows ping output.
     *
     * Expected format:
     * ```
     * Packets: Sent = 10, Received = 10, Lost = 0 (0% loss),
     * Minimum = 5ms, Maximum = 12ms, Average = 8ms
     * ```
     */
    private fun parseWindowsFormat(
        output: String,
        targetHost: String,
        testDuration: Duration,
    ): PingTest {
        val lines = output.lines()

        // Find packet statistics line
        val packetsLine =
            lines.find { it.contains("Packets: Sent =") }
                ?: throw PingParseException("Packets statistics not found")

        // Validate and parse packet statistics
        val stats = parseWindowsPacketStats(packetsLine)
        val (transmitted, received) = stats

        val packetLoss =
            extractDouble(packetsLine, """\((\d+)% loss\)""")
                ?: 0.0

        // Find RTT line
        val rttLine = lines.find { it.contains("Minimum =") && it.contains("Maximum =") }

        if (rttLine != null && received > 0) {
            val min = extractDouble(rttLine, """Minimum = (\d+)ms""")
            val max = extractDouble(rttLine, """Maximum = (\d+)ms""")
            val avg = extractDouble(rttLine, """Average = (\d+)ms""")

            if (min != null && max != null && avg != null) {
                // Windows doesn't provide stddev, estimate as (max-min)/4
                val stddev = (max - min) / 4.0

                return PingTest(
                    targetHost = targetHost,
                    packetsTransmitted = transmitted,
                    packetsReceived = received,
                    packetLossPercent = packetLoss,
                    minRtt = min.milliseconds,
                    avgRtt = avg.milliseconds,
                    maxRtt = max.milliseconds,
                    stdDevRtt = stddev.milliseconds,
                    testDuration = testDuration,
                )
            }
        }

        // No RTT data
        return PingTest(
            targetHost = targetHost,
            packetsTransmitted = transmitted,
            packetsReceived = received,
            packetLossPercent = packetLoss,
            minRtt = null,
            avgRtt = null,
            maxRtt = null,
            stdDevRtt = null,
            testDuration = testDuration,
        )
    }

    /**
     * Extract integer from text using regex pattern.
     */
    private fun extractInt(
        text: String,
        pattern: String,
    ): Int? =
        pattern
            .toRegex()
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    /**
     * Extract double from text using regex pattern.
     */
    private fun extractDouble(
        text: String,
        pattern: String,
    ): Double? =
        pattern
            .toRegex()
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

    /**
     * Parse Linux packet statistics from stats line.
     * Throws PingParseException if required fields cannot be parsed.
     */
    private fun parseLinuxPacketStats(statsLine: String): Pair<Int, Int> {
        val transmitted =
            extractInt(statsLine, """(\d+)\s+packets transmitted""")
        val received =
            extractInt(statsLine, """(\d+)\s+received""")

        if (transmitted == null || received == null) {
            throw PingParseException(
                "Cannot parse packet statistics from line: $statsLine",
            )
        }

        return transmitted to received
    }

    /**
     * Parse Windows packet statistics from packets line.
     * Throws PingParseException if required fields cannot be parsed.
     */
    private fun parseWindowsPacketStats(packetsLine: String): Pair<Int, Int> {
        val transmitted = extractInt(packetsLine, """Sent = (\d+)""")
        val received = extractInt(packetsLine, """Received = (\d+)""")

        if (transmitted == null || received == null) {
            throw PingParseException(
                "Cannot parse packet statistics from line: $packetsLine",
            )
        }

        return transmitted to received
    }
}

/**
 * Exception thrown when ping execution fails.
 *
 * @property message Description of the failure
 * @property cause Optional underlying cause
 */
class PingExecutionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Exception thrown when ping output cannot be parsed.
 *
 * @property message Description of the parse error
 */
class PingParseException(
    message: String,
) : Exception(message)

/**
 * Builder for constructing PingTest results programmatically.
 *
 * Useful for testing or when constructing results from non-standard sources.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val ping = PingResultBuilder(targetHost = "8.8.8.8")
 *     .setPackets(transmitted = 10, received = 10)
 *     .setPacketLoss(0.0)
 *     .setRtt(min = 5.0, avg = 8.5, max = 12.0, stddev = 2.0)
 *     .build()
 * ```
 */
class PingResultBuilder(
    private val targetHost: String,
) {
    private var packetsTransmitted: Int = 0
    private var packetsReceived: Int = 0
    private var packetLossPercent: Double = 0.0
    private var minRtt: Duration? = null
    private var avgRtt: Duration? = null
    private var maxRtt: Duration? = null
    private var stdDevRtt: Duration? = null
    private var testDuration: Duration = Duration.ZERO
    private var failureReason: String? = null

    /**
     * Set packet statistics.
     */
    fun setPackets(
        transmitted: Int,
        received: Int,
    ): PingResultBuilder {
        this.packetsTransmitted = transmitted
        this.packetsReceived = received
        return this
    }

    /**
     * Set packet loss percentage.
     */
    fun setPacketLoss(percent: Double): PingResultBuilder {
        this.packetLossPercent = percent
        return this
    }

    /**
     * Set RTT statistics (in milliseconds).
     */
    fun setRtt(
        min: Double,
        avg: Double,
        max: Double,
        stddev: Double,
    ): PingResultBuilder {
        this.minRtt = min.milliseconds
        this.avgRtt = avg.milliseconds
        this.maxRtt = max.milliseconds
        this.stdDevRtt = stddev.milliseconds
        return this
    }

    /**
     * Set test duration.
     */
    fun setDuration(duration: Duration): PingResultBuilder {
        this.testDuration = duration
        return this
    }

    /**
     * Set failure reason.
     */
    fun setFailure(reason: String): PingResultBuilder {
        this.failureReason = reason
        return this
    }

    /**
     * Build the PingTest result.
     */
    fun build(): PingTest =
        PingTest(
            targetHost = targetHost,
            packetsTransmitted = packetsTransmitted,
            packetsReceived = packetsReceived,
            packetLossPercent = packetLossPercent,
            minRtt = minRtt,
            avgRtt = avgRtt,
            maxRtt = maxRtt,
            stdDevRtt = stdDevRtt,
            testDuration = testDuration,
            failureReason = failureReason,
        )
}
