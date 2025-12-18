package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.TracerouteHop
import io.lamco.netkit.diagnostics.model.TracerouteResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Interface for executing traceroute network path analysis.
 *
 * TracerouteExecutor provides an abstraction for running traceroute tests across different
 * platforms. Implementations handle platform-specific command execution while this interface
 * defines the contract and provides common parsing utilities.
 *
 * ## Platform Implementations
 *
 * - **Android**: Uses `traceroute` command if available, falls back to TCP/UDP probes
 * - **Linux**: Uses `traceroute` or `tracepath`
 * - **macOS**: Uses `traceroute` (BSD variant)
 * - **Windows**: Uses `tracert`
 *
 * ## Usage Example
 *
 * ```kotlin
 * val executor = AndroidTracerouteExecutor()
 * val result = executor.executeTraceroute(
 *     targetHost = "example.com",
 *     maxHops = 30,
 *     timeout = 60.seconds
 * )
 * println("Total hops: ${result.totalHops}")
 * ```
 *
 * @see TracerouteResult
 * @see TracerouteParser
 */
interface TracerouteExecutor {
    /**
     * Execute a traceroute to the specified host.
     *
     * @param targetHost The hostname or IP address to trace
     * @param maxHops Maximum number of hops to probe (default: 30)
     * @param timeout Maximum time to wait for the trace to complete
     * @param probeCount Number of probes per hop (default: 3)
     * @param waitTime Maximum wait time per hop
     * @return TracerouteResult with hop-by-hop path information
     * @throws TracerouteExecutionException if execution fails
     */
    suspend fun executeTraceroute(
        targetHost: String,
        maxHops: Int = 30,
        timeout: Duration = 60.seconds,
        probeCount: Int = 3,
        waitTime: Duration = 5.seconds,
    ): TracerouteResult

    /**
     * Check if traceroute functionality is available on this platform.
     *
     * @return true if traceroute can be executed
     */
    fun isTracerouteAvailable(): Boolean
}

/**
 * Parser for traceroute command output.
 *
 * Supports parsing output from common traceroute implementations:
 * - Linux `traceroute` (iputils)
 * - Linux `tracepath`
 * - macOS `traceroute` (BSD)
 * - Windows `tracert`
 *
 * ## Output Format Examples
 *
 * **Linux traceroute**:
 * ```
 * traceroute to example.com (93.184.216.34), 30 hops max, 60 byte packets
 *  1  192.168.1.1 (192.168.1.1)  1.234 ms  1.456 ms  1.789 ms
 *  2  10.0.0.1 (10.0.0.1)  5.123 ms  5.456 ms  5.789 ms
 *  3  * * *
 *  4  93.184.216.34 (93.184.216.34)  12.345 ms  12.678 ms  12.901 ms
 * ```
 *
 * **Windows tracert**:
 * ```
 * Tracing route to example.com [93.184.216.34] over a maximum of 30 hops:
 *   1     1 ms     1 ms     1 ms  192.168.1.1
 *   2     5 ms     5 ms     5 ms  10.0.0.1
 *   3     *        *        *     Request timed out.
 *   4    12 ms    12 ms    12 ms  93.184.216.34
 * ```
 */
object TracerouteParser {
    /**
     * Parse traceroute command output into a TracerouteResult.
     *
     * @param output The raw output from traceroute command
     * @param targetHost The host that was traced
     * @param maxHops Maximum hops configured
     * @param testDuration How long the test took
     * @return Parsed TracerouteResult
     */
    fun parse(
        output: String,
        targetHost: String,
        maxHops: Int = 30,
        testDuration: Duration = Duration.ZERO,
    ): TracerouteResult =
        try {
            when {
                isLinuxFormat(output) -> parseLinuxFormat(output, targetHost, maxHops, testDuration)
                isWindowsFormat(output) -> parseWindowsFormat(output, targetHost, maxHops, testDuration)
                else -> TracerouteResult.failed(targetHost, "Unknown traceroute output format")
            }
        } catch (e: TracerouteExecutionException) {
            TracerouteResult.failed(targetHost, "Parse error: ${e.message}")
        }

    /**
     * Check if output is Linux/Unix/macOS format.
     */
    private fun isLinuxFormat(output: String): Boolean = output.contains("traceroute to") || output.contains("tracepath to")

    /**
     * Check if output is Windows format.
     */
    private fun isWindowsFormat(output: String): Boolean = output.contains("Tracing route to") || output.contains("over a maximum of")

    /**
     * Parse Linux/Unix/macOS traceroute output.
     *
     * Format: `1  192.168.1.1 (192.168.1.1)  1.234 ms  1.456 ms  1.789 ms`
     */
    private fun parseLinuxFormat(
        output: String,
        targetHost: String,
        maxHops: Int,
        testDuration: Duration,
    ): TracerouteResult {
        val lines = output.lines()

        // Extract resolved IP from header if available
        val resolvedIp = extractResolvedIp(lines.firstOrNull() ?: "")

        // Parse hop lines (skip header)
        val hops = mutableListOf<TracerouteHop>()

        for (line in lines.drop(1)) {
            val hop = parseLinuxHopLine(line) ?: continue
            hops.add(hop)
        }

        // Determine if trace completed (reached target)
        val completed =
            hops.lastOrNull()?.ipAddress == resolvedIp ||
                hops.lastOrNull()?.hostname?.contains(targetHost) == true

        return TracerouteResult(
            targetHost = targetHost,
            resolvedIp = resolvedIp,
            hops = hops,
            maxHops = maxHops,
            completed = completed,
            testDuration = testDuration,
        )
    }

    /**
     * Parse a single hop line from Linux traceroute output.
     *
     * Formats:
     * - `1  192.168.1.1 (192.168.1.1)  1.234 ms  1.456 ms  1.789 ms`
     * - `2  gateway.local (192.168.1.1)  2.123 ms  2.456 ms  2.789 ms`
     * - `3  * * *` (timeout)
     */
    private fun parseLinuxHopLine(line: String): TracerouteHop? {
        // Skip empty lines
        if (line.isBlank()) return null

        // Extract hop number
        val hopNumberPattern = """^\s*(\d+)\s+""".toRegex()
        val hopMatch = hopNumberPattern.find(line) ?: return null
        val hopNumber = hopMatch.groupValues[1].toInt()

        // Check for timeout: "3  * * *"
        if (line.contains("* * *") || line.contains("***")) {
            return TracerouteHop.timeout(hopNumber, "*")
        }

        // Parse hostname and IP
        // Formats: "hostname (ip)" or just "ip"
        val hostPattern = """([\w.-]+)\s+\(([\d.]+)\)""".toRegex()
        val ipOnlyPattern = """([\d.]+)""".toRegex()

        val hostname: String?
        val ipAddress: String

        val hostMatch = hostPattern.find(line)
        if (hostMatch != null) {
            hostname = hostMatch.groupValues[1]
            ipAddress = hostMatch.groupValues[2]
        } else {
            val ipMatch = ipOnlyPattern.find(line) ?: return null
            hostname = null
            ipAddress = ipMatch.groupValues[1]
        }

        // Parse RTT values (may have 1-3 values)
        val rttPattern = """([\d.]+)\s*ms""".toRegex()
        val rttMatches = rttPattern.findAll(line).toList()

        if (rttMatches.isEmpty()) {
            // No RTT = timeout
            return TracerouteHop.timeout(hopNumber, ipAddress)
        }

        // Use first RTT value
        val rtt =
            rttMatches
                .first()
                .groupValues[1]
                .toDouble()
                .milliseconds

        // Count how many probes got responses
        val probesReceived = rttMatches.size

        return TracerouteHop(
            hopNumber = hopNumber,
            ipAddress = ipAddress,
            hostname = hostname,
            rtt = rtt,
            probesSent = 3,
            probesReceived = probesReceived,
        )
    }

    /**
     * Parse Windows tracert output.
     *
     * Format: `  1     1 ms     1 ms     1 ms  192.168.1.1`
     */
    private fun parseWindowsFormat(
        output: String,
        targetHost: String,
        maxHops: Int,
        testDuration: Duration,
    ): TracerouteResult {
        val lines = output.lines()

        // Extract resolved IP from header
        val resolvedIp = extractWindowsResolvedIp(lines.firstOrNull() ?: "")

        // Parse hop lines (skip header and empty lines)
        val hops = mutableListOf<TracerouteHop>()

        for (line in lines) {
            val hop = parseWindowsHopLine(line) ?: continue
            hops.add(hop)
        }

        // Determine if trace completed
        val completed = hops.lastOrNull()?.ipAddress == resolvedIp

        return TracerouteResult(
            targetHost = targetHost,
            resolvedIp = resolvedIp,
            hops = hops,
            maxHops = maxHops,
            completed = completed,
            testDuration = testDuration,
        )
    }

    /**
     * Parse a single hop line from Windows tracert output.
     *
     * Formats:
     * - `  1     1 ms     1 ms     1 ms  192.168.1.1`
     * - `  2     *        *        *     Request timed out.`
     * - `  3     5 ms     5 ms     5 ms  gateway.local [192.168.1.1]`
     */
    private fun parseWindowsHopLine(line: String): TracerouteHop? {
        // Skip lines that don't look like hop lines
        if (!line.trim().matches("""^\d+\s+.*""".toRegex())) return null

        // Extract hop number
        val hopNumberPattern = """^\s*(\d+)\s+""".toRegex()
        val hopMatch = hopNumberPattern.find(line) ?: return null
        val hopNumber = hopMatch.groupValues[1].toInt()

        // Check for timeout
        if (line.contains("Request timed out") || line.contains("* * *")) {
            return TracerouteHop.timeout(hopNumber, "*")
        }

        // Parse RTT values
        val rttPattern = """(\d+)\s*ms""".toRegex()
        val rttMatches = rttPattern.findAll(line).toList()

        if (rttMatches.isEmpty()) {
            return TracerouteHop.timeout(hopNumber, "*")
        }

        // Use first RTT value
        val rtt =
            rttMatches
                .first()
                .groupValues[1]
                .toDouble()
                .milliseconds
        val probesReceived = rttMatches.size

        // Parse hostname and IP
        // Formats: "hostname [ip]" or just "ip"
        val hostPattern = """([\w.-]+)\s+\[([\d.]+)\]""".toRegex()
        val ipOnlyPattern = """([\d.]+)$""".toRegex()

        val hostname: String?
        val ipAddress: String

        val hostMatch = hostPattern.find(line)
        if (hostMatch != null) {
            hostname = hostMatch.groupValues[1]
            ipAddress = hostMatch.groupValues[2]
        } else {
            val ipMatch = ipOnlyPattern.find(line) ?: return null
            hostname = null
            ipAddress = ipMatch.groupValues[1]
        }

        return TracerouteHop(
            hopNumber = hopNumber,
            ipAddress = ipAddress,
            hostname = hostname,
            rtt = rtt,
            probesSent = 3,
            probesReceived = probesReceived,
        )
    }

    /**
     * Extract resolved IP from Linux traceroute header.
     *
     * Format: `traceroute to example.com (93.184.216.34), 30 hops max`
     */
    private fun extractResolvedIp(headerLine: String): String? {
        val pattern = """\(([\d.]+)\)""".toRegex()
        return pattern.find(headerLine)?.groupValues?.get(1)
    }

    /**
     * Extract resolved IP from Windows tracert header.
     *
     * Format: `Tracing route to example.com [93.184.216.34]`
     */
    private fun extractWindowsResolvedIp(headerLine: String): String? {
        val pattern = """\[([\d.]+)\]""".toRegex()
        return pattern.find(headerLine)?.groupValues?.get(1)
    }
}

/**
 * Exception thrown when traceroute execution fails.
 */
class TracerouteExecutionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Builder for constructing TracerouteResult programmatically.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val result = TracerouteResultBuilder(targetHost = "example.com")
 *     .setResolvedIp("93.184.216.34")
 *     .addHop(1, "192.168.1.1", "gateway", 2.0)
 *     .addHop(2, "10.0.0.1", "isp-router", 5.0)
 *     .addHop(3, "93.184.216.34", "example.com", 12.0)
 *     .setCompleted(true)
 *     .build()
 * ```
 */
class TracerouteResultBuilder(
    private val targetHost: String,
) {
    private var resolvedIp: String? = null
    private val hops = mutableListOf<TracerouteHop>()
    private var maxHops: Int = 30
    private var completed: Boolean = false
    private var testDuration: Duration = Duration.ZERO
    private var failureReason: String? = null

    /**
     * Set the resolved IP address.
     */
    fun setResolvedIp(ip: String): TracerouteResultBuilder {
        this.resolvedIp = ip
        return this
    }

    /**
     * Add a hop to the route.
     */
    fun addHop(
        hopNumber: Int,
        ipAddress: String,
        hostname: String? = null,
        rttMs: Double? = null,
    ): TracerouteResultBuilder {
        val hop =
            if (rttMs != null) {
                TracerouteHop(
                    hopNumber = hopNumber,
                    ipAddress = ipAddress,
                    hostname = hostname,
                    rtt = rttMs.milliseconds,
                )
            } else {
                TracerouteHop.timeout(hopNumber, ipAddress)
            }
        hops.add(hop)
        return this
    }

    /**
     * Add a timeout hop.
     */
    fun addTimeoutHop(hopNumber: Int): TracerouteResultBuilder {
        hops.add(TracerouteHop.timeout(hopNumber))
        return this
    }

    /**
     * Set maximum hops.
     */
    fun setMaxHops(maxHops: Int): TracerouteResultBuilder {
        this.maxHops = maxHops
        return this
    }

    /**
     * Set completion status.
     */
    fun setCompleted(completed: Boolean): TracerouteResultBuilder {
        this.completed = completed
        return this
    }

    /**
     * Set test duration.
     */
    fun setDuration(duration: Duration): TracerouteResultBuilder {
        this.testDuration = duration
        return this
    }

    /**
     * Set failure reason.
     */
    fun setFailure(reason: String): TracerouteResultBuilder {
        this.failureReason = reason
        return this
    }

    /**
     * Build the TracerouteResult.
     */
    fun build(): TracerouteResult =
        TracerouteResult(
            targetHost = targetHost,
            resolvedIp = resolvedIp,
            hops = hops,
            maxHops = maxHops,
            completed = completed,
            testDuration = testDuration,
            failureReason = failureReason,
        )
}
