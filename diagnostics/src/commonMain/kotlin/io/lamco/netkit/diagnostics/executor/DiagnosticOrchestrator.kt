package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.*
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Orchestrates execution of multiple diagnostic tests in parallel.
 *
 * DiagnosticOrchestrator coordinates ping, traceroute, bandwidth, and DNS tests,
 * running them efficiently using coroutines. It provides progress callbacks,
 * error handling, and aggregates results into a comprehensive ActiveDiagnostics report.
 *
 * ## Features
 *
 * - **Parallel Execution**: Runs independent tests concurrently for speed
 * - **Progress Tracking**: Reports progress via callback interface
 * - **Error Handling**: Gracefully handles individual test failures
 * - **Configurable**: Flexible test suite configuration
 * - **Timeout Support**: Per-test and overall timeout handling
 *
 * ## Usage Example
 *
 * ```kotlin
 * val orchestrator = DiagnosticOrchestrator(
 *     pingExecutor = AndroidPingExecutor(),
 *     tracerouteExecutor = AndroidTracerouteExecutor(),
 *     bandwidthTester = HttpBandwidthTester(),
 *     dnsTester = AndroidDnsTester()
 * )
 *
 * val config = DiagnosticConfig(
 *     pingTargets = listOf("8.8.8.8", "1.1.1.1"),
 *     tracerouteTargets = listOf("example.com"),
 *     bandwidthServers = listOf("speedtest.example.com"),
 *     dnsHosts = listOf("example.com", "google.com")
 * )
 *
 * val results = orchestrator.executeDiagnostics(
 *     config = config,
 *     progressCallback = { progress ->
 *         println("${progress.completedTests}/${progress.totalTests} tests complete")
 *     }
 * )
 *
 * println("Overall health: ${results.overallHealth}")
 * ```
 *
 * @property pingExecutor Executor for ping tests (optional)
 * @property tracerouteExecutor Executor for traceroute tests (optional)
 * @property bandwidthTester Tester for bandwidth measurements (optional)
 * @property dnsTester Tester for DNS resolution (optional)
 *
 * @see ActiveDiagnostics
 * @see DiagnosticConfig
 * @see DiagnosticProgress
 */
class DiagnosticOrchestrator(
    private val pingExecutor: PingExecutor? = null,
    private val tracerouteExecutor: TracerouteExecutor? = null,
    private val bandwidthTester: BandwidthTester? = null,
    private val dnsTester: DnsTester? = null,
) {
    /**
     * Execute a comprehensive diagnostic test suite.
     *
     * @param config Diagnostic test configuration
     * @param progressCallback Optional callback for progress updates
     * @param timeout Maximum time for all tests (default: 5 minutes)
     * @return ActiveDiagnostics with all test results
     * @throws DiagnosticTimeoutException if overall timeout is exceeded
     */
    suspend fun executeDiagnostics(
        config: DiagnosticConfig,
        progressCallback: ((DiagnosticProgress) -> Unit)? = null,
        timeout: Duration = 300.seconds,
    ): ActiveDiagnostics =
        withTimeout(timeout.inWholeMilliseconds) {
            val startTime = System.currentTimeMillis()
            val totalTests = config.calculateTotalTests()
            val progressTracker = ProgressTracker(totalTests, progressCallback)

            // Report initial progress
            progressTracker.reportProgress(DiagnosticPhase.STARTING)

            coroutineScope {
                // Execute test phases
                val pingResults = executePingTests(config, progressTracker)
                val dnsResults = executeDnsTests(config, progressTracker)
                val tracerouteResults = executeTracerouteTests(config, progressTracker)
                val bandwidthResults = executeBandwidthTests(config, progressTracker)

                // Report completion
                progressTracker.reportProgress(DiagnosticPhase.COMPLETE)

                // Build comprehensive result
                ActiveDiagnostics(
                    pingTests = pingResults,
                    tracerouteResults = tracerouteResults,
                    bandwidthTests = bandwidthResults,
                    dnsTests = dnsResults,
                    testSuiteName = config.suiteName,
                    testDuration = (System.currentTimeMillis() - startTime).milliseconds,
                    metadata = config.metadata,
                )
            }
        }

    /**
     * Execute ping tests.
     */
    private suspend fun CoroutineScope.executePingTests(
        config: DiagnosticConfig,
        progressTracker: ProgressTracker,
    ): List<PingTest> {
        if (pingExecutor == null || config.pingTargets.isEmpty()) return emptyList()

        progressTracker.reportProgress(DiagnosticPhase.CONNECTIVITY)

        val results =
            config.pingTargets.map { target ->
                async {
                    try {
                        val result =
                            pingExecutor.executePing(
                                targetHost = target,
                                packetCount = config.pingPacketCount,
                                timeout = config.pingTimeout,
                            )
                        progressTracker.incrementAndReport(DiagnosticPhase.CONNECTIVITY)
                        result
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: PingExecutionException) {
                        progressTracker.incrementAndReport(DiagnosticPhase.CONNECTIVITY)
                        PingTest.failed(target, reason = e.message ?: "Ping execution failed")
                    } catch (e: PingParseException) {
                        progressTracker.incrementAndReport(DiagnosticPhase.CONNECTIVITY)
                        PingTest.failed(target, reason = e.message ?: "Ping parse failed")
                    }
                }
            }

        return results.awaitAll()
    }

    /**
     * Execute DNS tests.
     */
    private suspend fun CoroutineScope.executeDnsTests(
        config: DiagnosticConfig,
        progressTracker: ProgressTracker,
    ): List<DnsTest> {
        if (dnsTester == null || config.dnsHosts.isEmpty()) return emptyList()

        progressTracker.reportProgress(DiagnosticPhase.CONNECTIVITY)

        val results =
            config.dnsHosts.flatMap { host ->
                config.dnsServers.map { server ->
                    async {
                        try {
                            val result =
                                dnsTester.executeDnsTest(
                                    hostname = host,
                                    recordType = config.dnsRecordType,
                                    dnsServer = server,
                                    timeout = config.dnsTimeout,
                                )
                            progressTracker.incrementAndReport(DiagnosticPhase.CONNECTIVITY)
                            result
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: DnsTestException) {
                            progressTracker.incrementAndReport(DiagnosticPhase.CONNECTIVITY)
                            DnsTest.failed(host, dnsServer = server, reason = e.message ?: "DNS test failed")
                        }
                    }
                }
            }

        return results.awaitAll()
    }

    /**
     * Execute traceroute tests.
     */
    private suspend fun CoroutineScope.executeTracerouteTests(
        config: DiagnosticConfig,
        progressTracker: ProgressTracker,
    ): List<TracerouteResult> {
        if (tracerouteExecutor == null || config.tracerouteTargets.isEmpty()) return emptyList()

        progressTracker.reportProgress(DiagnosticPhase.ROUTING)

        val results =
            config.tracerouteTargets.map { target ->
                async {
                    try {
                        val result =
                            tracerouteExecutor.executeTraceroute(
                                targetHost = target,
                                maxHops = config.tracerouteMaxHops,
                                timeout = config.tracerouteTimeout,
                            )
                        progressTracker.incrementAndReport(DiagnosticPhase.ROUTING)
                        result
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: TracerouteExecutionException) {
                        progressTracker.incrementAndReport(DiagnosticPhase.ROUTING)
                        TracerouteResult.failed(target, reason = e.message ?: "Traceroute failed")
                    }
                }
            }

        return results.awaitAll()
    }

    /**
     * Execute bandwidth tests.
     */
    private suspend fun CoroutineScope.executeBandwidthTests(
        config: DiagnosticConfig,
        progressTracker: ProgressTracker,
    ): List<BandwidthTest> {
        if (bandwidthTester == null || config.bandwidthServers.isEmpty()) return emptyList()

        progressTracker.reportProgress(DiagnosticPhase.THROUGHPUT)

        val results =
            config.bandwidthServers.map { server ->
                async {
                    try {
                        val result =
                            bandwidthTester.executeBandwidthTest(
                                serverHost = server,
                                testType = config.bandwidthTestType,
                                testDuration = config.bandwidthTestDuration,
                            )
                        progressTracker.incrementAndReport(DiagnosticPhase.THROUGHPUT)
                        result
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: BandwidthTestException) {
                        progressTracker.incrementAndReport(DiagnosticPhase.THROUGHPUT)
                        BandwidthTest.failed(server, reason = e.message ?: "Bandwidth test failed")
                    }
                }
            }

        return results.awaitAll()
    }

    /**
     * Helper class for tracking and reporting progress.
     */
    private class ProgressTracker(
        private val totalTests: Int,
        private val progressCallback: ((DiagnosticProgress) -> Unit)?,
    ) {
        private var completedTests = 0

        fun incrementAndReport(phase: DiagnosticPhase) {
            completedTests++
            reportProgress(phase)
        }

        fun reportProgress(phase: DiagnosticPhase) {
            progressCallback?.invoke(
                DiagnosticProgress(
                    totalTests = totalTests,
                    completedTests = completedTests,
                    currentPhase = phase,
                ),
            )
        }
    }

    /**
     * Execute a quick diagnostic test (ping + DNS only).
     *
     * @param targets Hosts to test
     * @param progressCallback Optional progress callback
     * @return ActiveDiagnostics with ping and DNS results
     */
    suspend fun executeQuickDiagnostic(
        targets: List<String> = listOf("8.8.8.8", "1.1.1.1"),
        progressCallback: ((DiagnosticProgress) -> Unit)? = null,
    ): ActiveDiagnostics {
        val config =
            DiagnosticConfig(
                suiteName = "Quick Diagnostic",
                pingTargets = targets,
                dnsHosts = targets.take(2),
                tracerouteTargets = emptyList(),
                bandwidthServers = emptyList(),
            )
        return executeDiagnostics(config, progressCallback, timeout = 30.seconds)
    }

    /**
     * Execute a full diagnostic test suite.
     *
     * @param progressCallback Optional progress callback
     * @return ActiveDiagnostics with all test types
     */
    suspend fun executeFullDiagnostic(progressCallback: ((DiagnosticProgress) -> Unit)? = null): ActiveDiagnostics {
        val config =
            DiagnosticConfig(
                suiteName = "Full Network Diagnostic",
                pingTargets = listOf("8.8.8.8", "1.1.1.1", "9.9.9.9"),
                tracerouteTargets = listOf("google.com", "cloudflare.com"),
                bandwidthServers = listOf("speedtest.example.com"),
                dnsHosts = listOf("example.com", "google.com"),
                dnsServers = listOf("8.8.8.8", "1.1.1.1"),
            )
        return executeDiagnostics(config, progressCallback)
    }
}

/**
 * Configuration for diagnostic test suite.
 *
 * @property suiteName Name for this test suite
 * @property pingTargets List of hosts to ping
 * @property tracerouteTargets List of hosts to traceroute
 * @property bandwidthServers List of bandwidth test servers
 * @property dnsHosts List of hostnames to resolve
 * @property dnsServers List of DNS servers to test (default: Google, Cloudflare)
 * @property dnsRecordType DNS record type to query
 * @property pingPacketCount Packets per ping test
 * @property pingTimeout Timeout per ping test
 * @property tracerouteMaxHops Maximum hops for traceroute
 * @property tracerouteTimeout Timeout per traceroute test
 * @property bandwidthTestType Type of bandwidth test
 * @property bandwidthTestDuration Duration per bandwidth test direction
 * @property dnsTimeout Timeout per DNS test
 * @property metadata Additional metadata to include in results
 */
data class DiagnosticConfig(
    val suiteName: String = "Network Diagnostics",
    val pingTargets: List<String> = emptyList(),
    val tracerouteTargets: List<String> = emptyList(),
    val bandwidthServers: List<String> = emptyList(),
    val dnsHosts: List<String> = emptyList(),
    val dnsServers: List<String> = listOf("8.8.8.8", "1.1.1.1"),
    val dnsRecordType: DnsRecordType = DnsRecordType.A,
    val pingPacketCount: Int = 10,
    val pingTimeout: Duration = 30.seconds,
    val tracerouteMaxHops: Int = 30,
    val tracerouteTimeout: Duration = 60.seconds,
    val bandwidthTestType: BandwidthTestType = BandwidthTestType.DOWNLOAD_UPLOAD,
    val bandwidthTestDuration: Duration = 10.seconds,
    val dnsTimeout: Duration = 5.seconds,
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Calculate total number of tests that will be executed.
     */
    fun calculateTotalTests(): Int {
        val pingCount = pingTargets.size
        val tracerouteCount = tracerouteTargets.size
        val bandwidthCount = bandwidthServers.size
        val dnsCount = dnsHosts.size * dnsServers.size

        return pingCount + tracerouteCount + bandwidthCount + dnsCount
    }
}

/**
 * Progress information for diagnostic test execution.
 *
 * @property totalTests Total number of tests to execute
 * @property completedTests Number of tests completed so far
 * @property currentPhase Current phase of testing
 */
data class DiagnosticProgress(
    val totalTests: Int,
    val completedTests: Int,
    val currentPhase: DiagnosticPhase,
) {
    /**
     * Progress percentage (0-100).
     */
    val progressPercent: Double
        get() = if (totalTests == 0) 0.0 else (completedTests.toDouble() / totalTests) * 100.0

    /**
     * Human-readable progress description.
     */
    fun description(): String =
        buildString {
            append("${currentPhase.displayName}: ")
            append("$completedTests/$totalTests tests complete ")
            append("(${"%.1f".format(progressPercent)}%)")
        }
}

/**
 * Phases of diagnostic test execution.
 */
enum class DiagnosticPhase {
    STARTING,
    CONNECTIVITY,
    ROUTING,
    THROUGHPUT,
    COMPLETE,
    ;

    val displayName: String
        get() =
            when (this) {
                STARTING -> "Starting diagnostics"
                CONNECTIVITY -> "Testing connectivity"
                ROUTING -> "Analyzing routes"
                THROUGHPUT -> "Measuring throughput"
                COMPLETE -> "Diagnostics complete"
            }
}

/**
 * Exception thrown when diagnostic execution times out.
 */
class DiagnosticTimeoutException(
    message: String = "Diagnostic test suite timed out",
) : Exception(message)
