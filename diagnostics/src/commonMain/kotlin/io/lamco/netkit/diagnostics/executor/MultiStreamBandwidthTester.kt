package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.*
import io.lamco.netkit.logging.NetKit
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Multi-stream bandwidth tester following iperf3 methodology.
 *
 * Tests network throughput using multiple parallel TCP connections to measure
 * parallelization efficiency and detect congestion or bottlenecks.
 *
 * ## Testing Approach
 *
 * 1. Test with 1 stream (baseline, single TCP connection)
 * 2. Test with 4 streams (moderate parallelization)
 * 3. Test with 8 streams (high parallelization)
 * 4. Compare results to identify scaling characteristics
 *
 * ## Interpretation
 *
 * - **Good**: 8 streams ≈ 6-8× single stream (75-100% efficiency)
 * - **Fair**: 8 streams ≈ 4-6× single stream (50-75% efficiency)
 * - **Poor**: 8 streams ≈ 2-4× single stream (25-50% efficiency)
 * - **Congested**: 8 streams ≈ single stream (<25% efficiency)
 *
 * ## References
 * - iperf3: Multi-stream testing with -P flag
 * - RFC 5681: TCP Congestion Control
 *
 * @property bandwidthTester Single-stream bandwidth tester
 *
 * @since 1.1.0
 */
class MultiStreamBandwidthTester(
    private val bandwidthTester: BandwidthTester,
) {
    /**
     * Execute multi-stream bandwidth test.
     *
     * Tests with 1, 4, and 8 parallel streams to measure scaling.
     *
     * @param serverHost Bandwidth test server
     * @param serverPort Server port
     * @param testDuration Duration for each test
     * @param testType Download, upload, or both
     * @return Multi-stream test result
     */
    suspend fun executeMultiStreamTest(
        serverHost: String,
        serverPort: Int = 443,
        testDuration: Duration = 10.seconds,
        testType: BandwidthTestType = BandwidthTestType.DOWNLOAD_ONLY,
    ): MultiStreamBandwidthTest =
        coroutineScope {
            // Test 1: Single stream (baseline)
            val singleResult =
                bandwidthTester.executeBandwidthTest(
                    serverHost = serverHost,
                    serverPort = serverPort,
                    testType = testType,
                    testDuration = testDuration,
                )

            val singleThroughput =
                when (testType) {
                    BandwidthTestType.DOWNLOAD_ONLY -> singleResult.downloadMbps ?: 0.0
                    BandwidthTestType.UPLOAD_ONLY -> singleResult.uploadMbps ?: 0.0
                    BandwidthTestType.DOWNLOAD_UPLOAD, BandwidthTestType.BIDIRECTIONAL -> {
                        ((singleResult.downloadMbps ?: 0.0) + (singleResult.uploadMbps ?: 0.0)) / 2.0
                    }
                }

            // Test 2: Four streams (parallel)
            val fourStreamResults =
                (0 until 4)
                    .map { streamId ->
                        async {
                            try {
                                val result =
                                    bandwidthTester.executeBandwidthTest(
                                        serverHost = serverHost,
                                        serverPort = serverPort,
                                        testType = testType,
                                        testDuration = testDuration,
                                    )
                                val throughput =
                                    when (testType) {
                                        BandwidthTestType.DOWNLOAD_ONLY -> result.downloadMbps ?: 0.0
                                        BandwidthTestType.UPLOAD_ONLY -> result.uploadMbps ?: 0.0
                                        else -> ((result.downloadMbps ?: 0.0) + (result.uploadMbps ?: 0.0)) / 2.0
                                    }
                                StreamResult(
                                    streamId = streamId,
                                    throughputMbps = throughput,
                                    bytesTransferred = result.downloadBytes + result.uploadBytes,
                                    retransmissions = null,
                                )
                            } catch (e: CancellationException) {
                                throw e // Always propagate cancellation
                            } catch (e: BandwidthTestException) {
                                NetKit.logger.warn("Four-stream bandwidth test failed for stream $streamId", e)
                                StreamResult(streamId, 0.0, 0, null)
                            }
                        }
                    }.awaitAll()

            val fourTotalThroughput = fourStreamResults.sumOf { it.throughputMbps }

            // Test 3: Eight streams (high parallelization)
            val eightStreamResults =
                (0 until 8)
                    .map { streamId ->
                        async {
                            try {
                                val result =
                                    bandwidthTester.executeBandwidthTest(
                                        serverHost = serverHost,
                                        serverPort = serverPort,
                                        testType = testType,
                                        testDuration = testDuration,
                                    )
                                val throughput =
                                    when (testType) {
                                        BandwidthTestType.DOWNLOAD_ONLY -> result.downloadMbps ?: 0.0
                                        BandwidthTestType.UPLOAD_ONLY -> result.uploadMbps ?: 0.0
                                        else -> ((result.downloadMbps ?: 0.0) + (result.uploadMbps ?: 0.0)) / 2.0
                                    }
                                StreamResult(
                                    streamId = streamId,
                                    throughputMbps = throughput,
                                    bytesTransferred = result.downloadBytes + result.uploadBytes,
                                    retransmissions = null,
                                )
                            } catch (e: CancellationException) {
                                throw e // Always propagate cancellation
                            } catch (e: BandwidthTestException) {
                                NetKit.logger.warn("Eight-stream bandwidth test failed for stream $streamId", e)
                                StreamResult(streamId, 0.0, 0, null)
                            }
                        }
                    }.awaitAll()

            val eightTotalThroughput = eightStreamResults.sumOf { it.throughputMbps }

            // Calculate metrics
            val efficiency = MultiStreamBandwidthTest.calculateEfficiency(singleThroughput, eightTotalThroughput)
            val congestionDetected = efficiency < 50.0
            val bottleneck = classifyBottleneck(singleThroughput, fourTotalThroughput, eightTotalThroughput)
            val fairness = calculateJainsFairnessIndex(eightStreamResults.map { it.throughputMbps })

            MultiStreamBandwidthTest(
                singleStreamMbps = singleThroughput,
                fourStreamMbps = fourTotalThroughput,
                eightStreamMbps = eightTotalThroughput,
                parallelizationEfficiency = efficiency,
                congestionDetected = congestionDetected,
                bottleneckType = bottleneck,
                fairnessIndex = fairness,
                perStreamResults = eightStreamResults,
                testDuration = testDuration,
            )
        }

    /**
     * Classify type of bottleneck based on scaling pattern.
     */
    private fun classifyBottleneck(
        single: Double,
        four: Double,
        eight: Double,
    ): BottleneckType? {
        if (single == 0.0) return null

        val scalingTo4 = four / single
        val scalingTo8 = eight / single

        return when {
            scalingTo8 >= 5.0 -> BottleneckType.NONE // Good scaling
            scalingTo8 >= 3.0 -> null // Moderate scaling, no clear bottleneck
            scalingTo4 <= 1.2 && scalingTo8 <= 1.3 -> {
                // Adding streams doesn't help at all - likely server or ISP limit
                if (single < 100.0) {
                    BottleneckType.SERVER_LIMIT
                } else {
                    BottleneckType.ISP_THROTTLING
                }
            }
            scalingTo8 < 2.0 -> BottleneckType.NETWORK_CONGESTION
            else -> null
        }
    }

    /**
     * Calculate Jain's fairness index.
     *
     * Measures how fairly bandwidth is distributed across streams.
     * - 1.0 = perfectly fair (all streams equal)
     * - Approaching 0 = unfair (some streams starved)
     *
     * Formula: (∑xi)² / (n × ∑xi²)
     */
    private fun calculateJainsFairnessIndex(throughputs: List<Double>): Double {
        if (throughputs.isEmpty()) return 0.0

        val n = throughputs.size
        val sum = throughputs.sum()
        val sumOfSquares = throughputs.sumOf { it * it }

        if (sumOfSquares == 0.0) return 0.0

        return (sum * sum) / (n * sumOfSquares)
    }

    companion object {
        /**
         * Calculate parallelization efficiency.
         *
         * Efficiency = (8-stream / single-stream) / 8 × 100%
         *
         * Perfect: 100% (linear scaling)
         * Good: 60-80%
         * Poor: <60%
         */
        fun calculateEfficiency(
            singleStream: Double,
            eightStream: Double,
        ): Double {
            if (singleStream == 0.0) return 0.0

            val actualScaling = eightStream / singleStream
            val idealScaling = 8.0
            return (actualScaling / idealScaling * 100.0).coerceIn(0.0, 100.0)
        }
    }
}
