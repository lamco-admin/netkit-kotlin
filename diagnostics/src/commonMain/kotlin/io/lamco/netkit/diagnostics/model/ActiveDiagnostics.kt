package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Comprehensive container for all active network diagnostic test results.
 *
 * ActiveDiagnostics aggregates results from multiple diagnostic tests (ping, traceroute,
 * bandwidth, DNS) to provide a complete picture of network performance and health.
 *
 * This is the primary model used when running a full diagnostic suite, combining
 * connectivity testing, path analysis, throughput measurement, and DNS resolution.
 *
 * ## Diagnostic Categories
 *
 * 1. **Connectivity**: Ping tests to verify reachability and measure latency
 * 2. **Routing**: Traceroute to analyze network path and identify bottlenecks
 * 3. **Throughput**: Bandwidth tests to measure actual data transfer rates
 * 4. **DNS**: Name resolution tests to verify DNS server performance
 *
 * ## Usage Example
 *
 * ```kotlin
 * val diagnostics = ActiveDiagnostics(
 *     pingTests = listOf(pingToGateway, pingToGoogle),
 *     tracerouteResults = listOf(traceToGoogle),
 *     bandwidthTests = listOf(speedTest),
 *     dnsTests = listOf(dnsToGoogle, dnsToCloudflare),
 *     testSuiteName = "Full Network Diagnostic",
 *     testDuration = 45.seconds
 * )
 *
 * println("Overall health: ${diagnostics.overallHealth}")
 * println("Issues found: ${diagnostics.identifyIssues().size}")
 * println(diagnostics.executiveSummary())
 * ```
 *
 * @property pingTests List of ping test results
 * @property tracerouteResults List of traceroute test results
 * @property bandwidthTests List of bandwidth test results
 * @property dnsTests List of DNS test results
 * @property testSuiteName Name of this diagnostic test suite
 * @property testDuration Total time to complete all tests
 * @property timestamp When the diagnostic suite was run (epoch millis)
 * @property metadata Additional metadata about the test environment
 *
 * @see PingTest
 * @see TracerouteResult
 * @see BandwidthTest
 * @see DnsTest
 * @see DiagnosticHealth
 * @see DiagnosticIssue
 */
data class ActiveDiagnostics(
    val pingTests: List<PingTest> = emptyList(),
    val tracerouteResults: List<TracerouteResult> = emptyList(),
    val bandwidthTests: List<BandwidthTest> = emptyList(),
    val dnsTests: List<DnsTest> = emptyList(),
    val testSuiteName: String = "Network Diagnostics",
    val testDuration: Duration,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(testSuiteName.isNotBlank()) { "Test suite name cannot be blank" }
        require(!testDuration.isNegative()) { "Test duration cannot be negative" }
    }

    /**
     * Whether any tests were performed.
     */
    val hasAnyTests: Boolean =
        pingTests.isNotEmpty() ||
            tracerouteResults.isNotEmpty() ||
            bandwidthTests.isNotEmpty() ||
            dnsTests.isNotEmpty()

    /**
     * Total number of individual tests performed.
     */
    val totalTestCount: Int =
        pingTests.size +
            tracerouteResults.size +
            bandwidthTests.size +
            dnsTests.size

    /**
     * Number of tests that completed successfully.
     */
    val successfulTestCount: Int =
        pingTests.count { it.isSuccessful } +
            tracerouteResults.count { it.isSuccessful } +
            bandwidthTests.count { it.isSuccessful } +
            dnsTests.count { it.isSuccessful }

    /**
     * Number of tests that failed.
     */
    val failedTestCount: Int = totalTestCount - successfulTestCount

    /**
     * Whether the diagnostic suite completed successfully with all tests passing.
     */
    val isSuccessful: Boolean = hasAnyTests && failedTestCount == 0

    /**
     * Test success rate as a percentage (0.0-100.0).
     */
    val successRate: Double =
        if (totalTestCount == 0) {
            0.0
        } else {
            (successfulTestCount.toDouble() / totalTestCount) * 100.0
        }

    // ===== Connectivity Analysis =====

    /**
     * Best (lowest) average latency from all ping tests.
     */
    val bestLatency: Duration? =
        pingTests
            .filter { it.isSuccessful }
            .mapNotNull { it.avgRtt }
            .minOrNull()

    /**
     * Worst (highest) packet loss from all ping tests.
     */
    val worstPacketLoss: Double =
        pingTests
            .maxOfOrNull { it.packetLossPercent }
            ?: 0.0

    /**
     * Overall connectivity quality (best ping test quality).
     * EXCELLENT has ordinal 0, FAILED has ordinal 5 - so best quality = minimum ordinal.
     */
    val connectivityQuality: PingQuality =
        pingTests
            .filter { it.isSuccessful }
            .mapNotNull { it.overallQuality }
            .minByOrNull { it.ordinal }
            ?: if (pingTests.any { it.isFailed }) PingQuality.FAILED else PingQuality.CRITICAL

    // ===== Routing Analysis =====

    /**
     * Whether gateway (first hop) is healthy across all traceroutes.
     */
    val hasHealthyGateway: Boolean =
        tracerouteResults
            .filter { it.isSuccessful }
            .all { it.hasHealthyGateway() }

    /**
     * Best (lowest) hop count from all traceroutes.
     */
    val bestHopCount: Int? =
        tracerouteResults
            .filter { it.completed }
            .mapNotNull { it.totalHops }
            .minOrNull()

    /**
     * Overall routing quality (best traceroute quality).
     */
    val routingQuality: RouteQuality =
        tracerouteResults
            .filter { it.isSuccessful }
            .mapNotNull { it.routeQuality }
            .maxByOrNull { it.ordinal }
            ?: if (tracerouteResults.any { !it.isSuccessful }) RouteQuality.FAILED else RouteQuality.CRITICAL

    // ===== Throughput Analysis =====

    /**
     * Best download speed from all bandwidth tests.
     */
    val bestDownloadSpeed: Double? =
        bandwidthTests
            .filter { it.isSuccessful }
            .mapNotNull { it.downloadMbps }
            .maxOrNull()

    /**
     * Best upload speed from all bandwidth tests.
     */
    val bestUploadSpeed: Double? =
        bandwidthTests
            .filter { it.isSuccessful }
            .mapNotNull { it.uploadMbps }
            .maxOrNull()

    /**
     * Overall bandwidth quality (best bandwidth test quality).
     */
    val throughputQuality: BandwidthQuality =
        bandwidthTests
            .filter { it.isSuccessful }
            .mapNotNull { it.overallQuality }
            .maxByOrNull { it.ordinal }
            ?: if (bandwidthTests.any { it.isFailed }) BandwidthQuality.FAILED else BandwidthQuality.UNKNOWN

    // ===== DNS Analysis =====

    /**
     * DNS resolution success rate.
     */
    val dnsSuccessRate: Double =
        if (dnsTests.isEmpty()) {
            0.0
        } else {
            (dnsTests.count { it.isSuccessful }.toDouble() / dnsTests.size) * 100.0
        }

    /**
     * Average DNS resolution time across successful tests.
     */
    val avgDnsResolutionTime: Duration? =
        if (dnsTests.none { it.isSuccessful }) {
            null
        } else {
            val totalMs = dnsTests.filter { it.isSuccessful }.sumOf { it.resolutionTime.inWholeMilliseconds }
            (totalMs / dnsTests.count { it.isSuccessful }).milliseconds
        }

    /**
     * Overall DNS quality.
     */
    val dnsQuality: DnsResolutionQuality =
        dnsTests
            .filter { it.isSuccessful }
            .mapNotNull { it.resolutionQuality }
            .maxByOrNull { it.ordinal }
            ?: if (dnsTests.any { it.isFailed }) DnsResolutionQuality.FAILED else DnsResolutionQuality.FAILED

    // ===== Overall Health Assessment =====

    /**
     * Overall network health based on all diagnostic results.
     *
     * Health is assessed by combining:
     * - Connectivity (ping tests)
     * - Routing (traceroute)
     * - Throughput (bandwidth)
     * - DNS resolution
     *
     * The overall health is determined by the worst-performing category.
     */
    val overallHealth: DiagnosticHealth
        get() {
            if (!hasAnyTests) return DiagnosticHealth.UNKNOWN

            // Convert each category to a score
            val connectivityScore =
                when (connectivityQuality) {
                    PingQuality.EXCELLENT -> 100
                    PingQuality.GOOD -> 80
                    PingQuality.FAIR -> 60
                    PingQuality.POOR -> 40
                    PingQuality.CRITICAL -> 20
                    PingQuality.FAILED -> 0
                }

            val routingScore =
                when (routingQuality) {
                    RouteQuality.EXCELLENT -> 100
                    RouteQuality.GOOD -> 80
                    RouteQuality.FAIR -> 60
                    RouteQuality.POOR -> 40
                    RouteQuality.CRITICAL -> 20
                    RouteQuality.FAILED -> 0
                }

            val throughputScore = throughputQuality.toScore()
            val dnsScore = dnsQuality.toScore()

            // Overall health is based on the weighted average
            // Connectivity and throughput are most important
            val scores =
                listOfNotNull(
                    connectivityScore.takeIf { pingTests.isNotEmpty() }?.let { it to 0.35 },
                    throughputScore.takeIf { bandwidthTests.isNotEmpty() }?.let { it to 0.35 },
                    routingScore.takeIf { tracerouteResults.isNotEmpty() }?.let { it to 0.15 },
                    dnsScore.takeIf { dnsTests.isNotEmpty() }?.let { it to 0.15 },
                )

            if (scores.isEmpty()) return DiagnosticHealth.UNKNOWN

            val totalWeight = scores.sumOf { it.second }
            val weightedScore = scores.sumOf { it.first * it.second } / totalWeight

            return when {
                weightedScore >= 85 -> DiagnosticHealth.EXCELLENT
                weightedScore >= 70 -> DiagnosticHealth.GOOD
                weightedScore >= 50 -> DiagnosticHealth.FAIR
                weightedScore >= 30 -> DiagnosticHealth.POOR
                else -> DiagnosticHealth.CRITICAL
            }
        }

    /**
     * Identify issues across all diagnostic tests.
     *
     * @return List of identified issues with severity and recommendations
     */
    fun identifyIssues(): List<DiagnosticIssue> {
        val issues = mutableListOf<DiagnosticIssue>()

        // Connectivity issues
        if (worstPacketLoss >= 10.0) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.CONNECTIVITY,
                    severity = IssueSeverity.CRITICAL,
                    description = "High packet loss detected ($worstPacketLoss%)",
                    recommendation = "Check WiFi signal strength, interference, and distance to access point",
                ),
            )
        } else if (worstPacketLoss >= 5.0) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.CONNECTIVITY,
                    severity = IssueSeverity.HIGH,
                    description = "Moderate packet loss detected ($worstPacketLoss%)",
                    recommendation = "Verify WiFi connection stability and reduce interference sources",
                ),
            )
        }

        if (bestLatency != null && bestLatency >= 100.milliseconds) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.CONNECTIVITY,
                    severity = IssueSeverity.MEDIUM,
                    description = "High latency detected ($bestLatency)",
                    recommendation = "Check for network congestion and optimize WiFi channel selection",
                ),
            )
        }

        // Routing issues
        if (!hasHealthyGateway && tracerouteResults.isNotEmpty()) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.ROUTING,
                    severity = IssueSeverity.HIGH,
                    description = "Gateway (first hop) has high latency or is unresponsive",
                    recommendation = "Check router health and restart if necessary",
                ),
            )
        }

        if (bestHopCount != null && bestHopCount > 15) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.ROUTING,
                    severity = IssueSeverity.MEDIUM,
                    description = "Excessive network hops ($bestHopCount) to reach destination",
                    recommendation = "This may indicate suboptimal routing by ISP",
                ),
            )
        }

        // Throughput issues
        if (bestDownloadSpeed != null && bestDownloadSpeed < 10.0) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.THROUGHPUT,
                    severity = IssueSeverity.HIGH,
                    description = "Very low download speed ($bestDownloadSpeed Mbps)",
                    recommendation = "Check WiFi signal quality, channel congestion, and ISP service tier",
                ),
            )
        }

        if (bestUploadSpeed != null && bestUploadSpeed < 5.0) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.THROUGHPUT,
                    severity = IssueSeverity.MEDIUM,
                    description = "Low upload speed ($bestUploadSpeed Mbps)",
                    recommendation = "Verify ISP plan upload limits and WiFi signal quality",
                ),
            )
        }

        // DNS issues
        if (dnsSuccessRate < 90.0 && dnsTests.isNotEmpty()) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.DNS,
                    severity = IssueSeverity.HIGH,
                    description = "DNS resolution failures (${100.0 - dnsSuccessRate}% failure rate)",
                    recommendation = "Consider switching to reliable public DNS servers (8.8.8.8, 1.1.1.1)",
                ),
            )
        }

        if (avgDnsResolutionTime != null && avgDnsResolutionTime >= 100.milliseconds) {
            issues.add(
                DiagnosticIssue(
                    category = DiagnosticCategory.DNS,
                    severity = IssueSeverity.MEDIUM,
                    description = "Slow DNS resolution (avg $avgDnsResolutionTime)",
                    recommendation = "Switch to faster DNS servers for improved browsing performance",
                ),
            )
        }

        return issues.sortedByDescending { it.severity.priority }
    }

    /**
     * Generate an executive summary of the diagnostic results.
     */
    fun executiveSummary(): String =
        buildString {
            appendLine("=== Network Diagnostic Report ===")
            appendLine("Suite: $testSuiteName")
            appendLine("Completed: ${java.time.Instant.ofEpochMilli(timestamp)}")
            appendLine("Duration: $testDuration")
            appendLine()
            appendLine("Overall Health: $overallHealth")
            appendLine("Tests Performed: $totalTestCount")
            appendLine("Success Rate: ${"%.1f".format(successRate)}%")
            appendLine()

            appendLine("Category Performance:")
            if (pingTests.isNotEmpty()) {
                appendLine("  Connectivity: $connectivityQuality (latency: ${bestLatency ?: "N/A"})")
            }
            if (tracerouteResults.isNotEmpty()) {
                appendLine("  Routing: $routingQuality (hops: ${bestHopCount ?: "N/A"})")
            }
            if (bandwidthTests.isNotEmpty()) {
                appendLine(
                    "  Throughput: $throughputQuality (${bestDownloadSpeed ?: "N/A"} / ${bestUploadSpeed ?: "N/A"} Mbps)",
                )
            }
            if (dnsTests.isNotEmpty()) {
                appendLine("  DNS: $dnsQuality (avg: ${avgDnsResolutionTime ?: "N/A"})")
            }

            val issues = identifyIssues()
            if (issues.isNotEmpty()) {
                appendLine()
                appendLine("Issues Identified (${issues.size}):")
                issues.take(5).forEach { issue ->
                    appendLine("  [${issue.severity}] ${issue.description}")
                }
                if (issues.size > 5) {
                    appendLine("  ... and ${issues.size - 5} more issues")
                }
            }
        }

    /**
     * Generate a detailed technical report.
     */
    fun detailedReport(): String =
        buildString {
            appendLine(executiveSummary())
            appendLine()
            appendLine("=== Detailed Results ===")
            appendLine()

            if (pingTests.isNotEmpty()) {
                appendLine("Ping Tests:")
                pingTests.forEach { ping ->
                    appendLine(ping.summary())
                    appendLine()
                }
            }

            if (tracerouteResults.isNotEmpty()) {
                appendLine("Traceroute Results:")
                tracerouteResults.forEach { trace ->
                    appendLine(trace.summary())
                    appendLine()
                }
            }

            if (bandwidthTests.isNotEmpty()) {
                appendLine("Bandwidth Tests:")
                bandwidthTests.forEach { bandwidth ->
                    appendLine(bandwidth.summary())
                    appendLine()
                }
            }

            if (dnsTests.isNotEmpty()) {
                appendLine("DNS Tests:")
                dnsTests.forEach { dns ->
                    appendLine(dns.summary())
                    appendLine()
                }
            }

            val issues = identifyIssues()
            if (issues.isNotEmpty()) {
                appendLine("=== Recommendations ===")
                issues.forEach { issue ->
                    appendLine("[${issue.severity}] ${issue.category}: ${issue.description}")
                    appendLine("    → ${issue.recommendation}")
                    appendLine()
                }
            }
        }
}

/**
 * Overall network diagnostic health assessment.
 */
enum class DiagnosticHealth {
    /** All tests passed with excellent performance */
    EXCELLENT,

    /** Tests passed with good performance */
    GOOD,

    /** Tests passed with acceptable performance */
    FAIR,

    /** Tests show performance issues */
    POOR,

    /** Critical network issues detected */
    CRITICAL,

    /** No diagnostic tests performed */
    UNKNOWN,

    ;

    /**
     * Human-readable description.
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent network health - all systems performing optimally"
                GOOD -> "Good network health - minor issues if any"
                FAIR -> "Fair network health - some performance degradation"
                POOR -> "Poor network health - significant issues affecting performance"
                CRITICAL -> "Critical network issues - immediate attention required"
                UNKNOWN -> "Network health unknown - no diagnostics performed"
            }
}

/**
 * Category of diagnostic issue.
 */
enum class DiagnosticCategory {
    CONNECTIVITY,
    ROUTING,
    THROUGHPUT,
    DNS,
    ;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Severity level for diagnostic issues.
 */
enum class IssueSeverity(
    val priority: Int,
) {
    CRITICAL(4),
    HIGH(3),
    MEDIUM(2),
    LOW(1),
    INFO(0),
}

/**
 * Represents an identified network issue from diagnostic tests.
 *
 * @property category The category of the issue
 * @property severity The severity level
 * @property description Human-readable description of the issue
 * @property recommendation Actionable recommendation to address the issue
 */
data class DiagnosticIssue(
    val category: DiagnosticCategory,
    val severity: IssueSeverity,
    val description: String,
    val recommendation: String,
)
