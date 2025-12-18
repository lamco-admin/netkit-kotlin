package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.*
import io.lamco.netkit.logging.NetKit
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Bufferbloat test executor - measures latency under network load.
 *
 * This executor implements the bufferbloat testing methodology from RFC 6349 Section 4
 * and bufferbloat.net recommendations. It measures how much latency increases when
 * the network is under sustained load (bandwidth testing).
 *
 * ## Algorithm
 *
 * 1. **Baseline Measurement**: Ping target with idle network (10 packets)
 * 2. **Load Generation**: Start sustained bandwidth test (upload/download)
 * 3. **Loaded Measurement**: Continuously ping during load (every 1 second)
 * 4. **Analysis**: Compare baseline vs. maximum loaded latency
 * 5. **Scoring**: Calculate bufferbloat severity and score
 *
 * ## Why This Matters
 *
 * Bufferbloat is one of the most common yet hidden network issues:
 * - Affects 40-60% of consumer routers
 * - Not detected by simple ping or speed test alone
 * - Causes "my Zoom call is fine until someone downloads" issues
 * - Explains gaming lag during household downloads
 *
 * ## Implementation Notes
 *
 * - Uses coroutines for parallel ping + bandwidth execution
 * - Upload load typically causes worse bufferbloat than download
 * - Test duration should be 20-30 seconds for accurate results
 * - First 2 seconds of load ignored (ramp-up time)
 *
 * ## References
 * - RFC 6349 Section 4: Latency measurement during throughput testing
 * - Bufferbloat.net: https://www.bufferbloat.net/projects/bloat/wiki/Tests_for_Bufferbloat/
 * - DSLReports Speed Test (pioneered consumer bufferbloat testing)
 * - Fast.com (Netflix): Latency under load feature
 *
 * @property pingExecutor Executor for ping tests
 * @property bandwidthTester Executor for bandwidth tests
 *
 * @since 1.1.0
 */
class BufferbloatExecutor(
    private val pingExecutor: PingExecutor,
    private val bandwidthTester: BandwidthTester,
) {
    /**
     * Execute bufferbloat test.
     *
     * @param targetHost Host to ping (default: 8.8.8.8 for reliability)
     * @param bandwidthServer Server for bandwidth test
     * @param loadDuration Duration of sustained load (default: 30 seconds)
     * @param loadDirection Direction of load (UPLOAD typically worse)
     * @param pingInterval How often to ping during load (default: 1 second)
     * @return Bufferbloat test result
     * @throws BufferbloatTestException if test fails
     */
    suspend fun executeBufferbloatTest(
        targetHost: String = "8.8.8.8",
        bandwidthServer: String = "speedtest.example.com",
        loadDuration: Duration = 30.seconds,
        loadDirection: LoadDirection = LoadDirection.UPLOAD,
        pingInterval: Duration = 1.seconds,
    ): BufferbloatTest =
        coroutineScope {
            // Step 1: Baseline latency measurement (idle network)
            val baselinePing =
                pingExecutor.executePing(
                    targetHost = targetHost,
                    packetCount = 10,
                    interval = 1.seconds,
                )

            val baselineLatency =
                baselinePing.avgRtt
                    ?: throw BufferbloatTestException("Cannot determine baseline latency - no ping response")

            if (!baselinePing.isSuccessful) {
                throw BufferbloatTestException("Baseline ping failed: ${baselinePing.failureReason}")
            }

            // Step 2: Start bandwidth test in background (generates load)
            val testStartTime = System.currentTimeMillis()
            val latencyMeasurements = mutableListOf<LatencyMeasurement>()

            // Add baseline measurements
            latencyMeasurements.add(
                LatencyMeasurement(
                    timestamp = testStartTime,
                    latency = baselineLatency,
                    loadActive = false,
                ),
            )

            val bandwidthJob =
                async {
                    try {
                        when (loadDirection) {
                            LoadDirection.DOWNLOAD ->
                                bandwidthTester.executeBandwidthTest(
                                    serverHost = bandwidthServer,
                                    testType = BandwidthTestType.DOWNLOAD_ONLY,
                                    testDuration = loadDuration,
                                )
                            LoadDirection.UPLOAD ->
                                bandwidthTester.executeBandwidthTest(
                                    serverHost = bandwidthServer,
                                    testType = BandwidthTestType.UPLOAD_ONLY,
                                    testDuration = loadDuration,
                                )
                            LoadDirection.BIDIRECTIONAL ->
                                bandwidthTester.executeBandwidthTest(
                                    serverHost = bandwidthServer,
                                    testType = BandwidthTestType.BIDIRECTIONAL,
                                    testDuration = loadDuration,
                                )
                        }
                    } catch (e: CancellationException) {
                        throw e // Always propagate cancellation
                    } catch (e: BandwidthTestException) {
                        // Bandwidth test can fail, but we still want latency data
                        NetKit.logger.warn("Bandwidth test failed during bufferbloat test, continuing with latency data only", e)
                        null
                    }
                }

            // Step 3: Allow bandwidth test to ramp up (2 seconds)
            delay(2000)

            // Step 4: Measure latency continuously during load
            val pingJob =
                launch {
                    while (bandwidthJob.isActive) {
                        try {
                            val ping =
                                pingExecutor.executePing(
                                    targetHost = targetHost,
                                    packetCount = 1,
                                    timeout = 5.seconds,
                                )

                            ping.avgRtt?.let { rtt ->
                                latencyMeasurements.add(
                                    LatencyMeasurement(
                                        timestamp = System.currentTimeMillis(),
                                        latency = rtt,
                                        loadActive = true,
                                    ),
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e // Always propagate cancellation
                        } catch (e: PingExecutionException) {
                            // Ping can fail during heavy load - continue testing
                            NetKit.logger.warn("Ping failed during bufferbloat loaded test, continuing", e)
                        }

                        delay(pingInterval.inWholeMilliseconds)
                    }
                }

            // Step 5: Wait for bandwidth test to complete
            val bandwidthResult = bandwidthJob.await()
            pingJob.cancel() // Stop pinging

            // Step 6: Analyze latency measurements
            val loadedMeasurements = latencyMeasurements.filter { it.loadActive }

            if (loadedMeasurements.isEmpty()) {
                throw BufferbloatTestException("No latency measurements collected during load")
            }

            val maxLoadedLatency = loadedMeasurements.maxOf { it.latency }
            val avgLoadedLatency =
                loadedMeasurements
                    .map { it.latency.inWholeMilliseconds }
                    .average()
                    .toLong()
                    .milliseconds
            val latencyIncrease = maxLoadedLatency - baselineLatency
            val increasePercent =
                (
                    latencyIncrease.inWholeMilliseconds.toDouble() /
                        baselineLatency.inWholeMilliseconds.toDouble()
                ) *
                    100.0

            // Step 7: Calculate severity and score
            val severity = classifySeverity(latencyIncrease)
            val score = BufferbloatTest.calculateScore(latencyIncrease)
            val affectedApps = BufferbloatTest.determineAffectedApplications(severity)

            // Step 8: Return result
            BufferbloatTest(
                baselineLatency = baselineLatency,
                loadedLatency = maxLoadedLatency,
                latencyIncrease = latencyIncrease,
                latencyIncreasePercent = increasePercent,
                bufferbloatScore = score,
                severity = severity,
                affectedApplications = affectedApps,
                peakLatency = maxLoadedLatency,
                averageLoadedLatency = avgLoadedLatency,
                latencyHistory = latencyMeasurements,
                testDuration = loadDuration,
                loadDirection = loadDirection,
            )
        }

    /**
     * Classify bufferbloat severity from latency increase.
     */
    private fun classifySeverity(latencyIncrease: Duration): BufferbloatSeverity {
        val increaseMs = latencyIncrease.inWholeMilliseconds
        return when {
            increaseMs < 25 -> BufferbloatSeverity.NONE
            increaseMs < 50 -> BufferbloatSeverity.MINIMAL
            increaseMs < 100 -> BufferbloatSeverity.MODERATE
            increaseMs < 200 -> BufferbloatSeverity.SEVERE
            else -> BufferbloatSeverity.CRITICAL
        }
    }

    /**
     * Quick bufferbloat check (faster, less comprehensive).
     *
     * Uses shorter test duration for quick assessment.
     * For detailed analysis, use full executeBufferbloatTest().
     *
     * @param targetHost Host to ping
     * @param bandwidthServer Bandwidth test server
     * @return Quick bufferbloat assessment
     */
    suspend fun quickBufferbloatCheck(
        targetHost: String = "8.8.8.8",
        bandwidthServer: String = "speedtest.example.com",
    ): BufferbloatTest =
        executeBufferbloatTest(
            targetHost = targetHost,
            bandwidthServer = bandwidthServer,
            loadDuration = 10.seconds, // Shorter for quick check
            loadDirection = LoadDirection.UPLOAD, // Upload typically worse
            pingInterval = 1.seconds,
        )
}

/**
 * Bufferbloat test result builder for testing and manual construction.
 */
class BufferbloatTestBuilder {
    private var baselineLatency: Duration = 20.milliseconds
    private var loadedLatency: Duration = 50.milliseconds
    private var testDuration: Duration = 30.seconds
    private var loadDirection: LoadDirection = LoadDirection.UPLOAD
    private var latencyHistory: List<LatencyMeasurement> = emptyList()

    fun setBaseline(latency: Duration): BufferbloatTestBuilder {
        this.baselineLatency = latency
        return this
    }

    fun setLoaded(latency: Duration): BufferbloatTestBuilder {
        this.loadedLatency = latency
        return this
    }

    fun setDuration(duration: Duration): BufferbloatTestBuilder {
        this.testDuration = duration
        return this
    }

    fun setLoadDirection(direction: LoadDirection): BufferbloatTestBuilder {
        this.loadDirection = direction
        return this
    }

    fun setLatencyHistory(history: List<LatencyMeasurement>): BufferbloatTestBuilder {
        this.latencyHistory = history
        return this
    }

    fun build(): BufferbloatTest {
        val increase = loadedLatency - baselineLatency
        val increasePercent =
            (
                increase.inWholeMilliseconds.toDouble() /
                    baselineLatency.inWholeMilliseconds.toDouble()
            ) *
                100.0

        val severity =
            when {
                increase < 25.milliseconds -> BufferbloatSeverity.NONE
                increase < 50.milliseconds -> BufferbloatSeverity.MINIMAL
                increase < 100.milliseconds -> BufferbloatSeverity.MODERATE
                increase < 200.milliseconds -> BufferbloatSeverity.SEVERE
                else -> BufferbloatSeverity.CRITICAL
            }

        val score = BufferbloatTest.calculateScore(increase)
        val affected = BufferbloatTest.determineAffectedApplications(severity)

        return BufferbloatTest(
            baselineLatency = baselineLatency,
            loadedLatency = loadedLatency,
            latencyIncrease = increase,
            latencyIncreasePercent = increasePercent,
            bufferbloatScore = score,
            severity = severity,
            affectedApplications = affected,
            peakLatency = loadedLatency,
            averageLoadedLatency = loadedLatency, // Simplified for builder
            latencyHistory = latencyHistory,
            testDuration = testDuration,
            loadDirection = loadDirection,
        )
    }
}
