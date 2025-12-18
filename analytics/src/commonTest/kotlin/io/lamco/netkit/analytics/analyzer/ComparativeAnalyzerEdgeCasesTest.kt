package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Comprehensive edge case tests for ComparativeAnalyzer
 *
 * Focuses on baseline comparison edge cases, benchmarking extremes,
 * temporal analysis boundary conditions, and health score calculations.
 */
class ComparativeAnalyzerEdgeCasesTest {
    private val analyzer = ComparativeAnalyzer()

    // ========================================
    // Baseline Comparison Edge Cases
    // ========================================

    @Test
    fun `compareToBaseline handles zero baseline values`() {
        val current = mapOf("rssi" to -50.0)
        val baseline = mapOf("rssi" to 0.0)

        val results = analyzer.compareToBaseline(current, baseline)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(-50.0, result.change)
    }

    @Test
    fun `compareToBaseline handles identical values`() {
        val current = mapOf("rssi" to -60.0, "snr" to 30.0)
        val baseline = mapOf("rssi" to -60.0, "snr" to 30.0)

        val results = analyzer.compareToBaseline(current, baseline)

        results.forEach { result ->
            assertEquals(0.0, result.change)
            assertEquals(0.0, result.changePercent)
            assertEquals(ChangeDirection.STABLE, result.direction)
        }
    }

    @Test
    fun `compareToBaseline handles all negative values`() {
        val current = mapOf("rssi" to -55.0, "latency" to -10.0)
        val baseline = mapOf("rssi" to -60.0, "latency" to -15.0)

        val results = analyzer.compareToBaseline(current, baseline)

        assertEquals(2, results.size)
        assertTrue(results.all { it.change != 0.0 })
    }

    @Test
    fun `compareToBaseline handles extreme positive change`() {
        val current = mapOf("throughput" to 1000.0)
        val baseline = mapOf("throughput" to 10.0)

        val results = analyzer.compareToBaseline(current, baseline)

        val result = results.first()
        assertEquals(990.0, result.change)
        assertTrue(result.changePercent > 9000.0) // 9900% increase
    }

    @Test
    fun `compareToBaseline handles extreme negative change`() {
        val current = mapOf("throughput" to 10.0)
        val baseline = mapOf("throughput" to 1000.0)

        val results = analyzer.compareToBaseline(current, baseline)

        val result = results.first()
        assertEquals(-990.0, result.change)
        assertTrue(result.changePercent < -90.0) // ~99% decrease
    }

    @Test
    fun `compareToBaseline handles lower is better metrics correctly`() {
        val current = mapOf("latency" to 20.0, "packet_loss" to 2.0)
        val baseline = mapOf("latency" to 10.0, "packet_loss" to 1.0)
        val higherIsBetter = mapOf("latency" to false, "packet_loss" to false)

        val results = analyzer.compareToBaseline(current, baseline, higherIsBetter)

        results.forEach { result ->
            assertEquals(ChangeDirection.DEGRADED, result.direction)
        }
    }

    @Test
    fun `compareToBaseline ignores metrics only in current`() {
        val current = mapOf("rssi" to -55.0, "extra" to 100.0, "another" to 50.0)
        val baseline = mapOf("rssi" to -60.0)

        val results = analyzer.compareToBaseline(current, baseline)

        assertEquals(1, results.size)
        assertEquals("rssi", results.first().metric)
    }

    @Test
    fun `compareToBaseline ignores metrics only in baseline`() {
        val current = mapOf("rssi" to -55.0)
        val baseline = mapOf("rssi" to -60.0, "old_metric" to 100.0)

        val results = analyzer.compareToBaseline(current, baseline)

        assertEquals(1, results.size)
        assertEquals("rssi", results.first().metric)
    }

    @Test
    fun `compareToBaseline handles many metrics`() {
        val metrics = (1..50).associate { "metric$it" to it.toDouble() }
        val baseline = (1..50).associate { "metric$it" to (it * 0.9) }

        val results = analyzer.compareToBaseline(metrics, baseline)

        assertEquals(50, results.size)
    }

    @Test
    fun `compareToBaseline rejects empty current metrics`() {
        assertThrows<IllegalArgumentException> {
            analyzer.compareToBaseline(
                emptyMap(),
                mapOf("rssi" to -60.0),
            )
        }
    }

    @Test
    fun `compareToBaseline rejects empty baseline metrics`() {
        assertThrows<IllegalArgumentException> {
            analyzer.compareToBaseline(
                mapOf("rssi" to -60.0),
                emptyMap(),
            )
        }
    }

    @Test
    fun `compareToBaseline rejects no common metrics`() {
        assertThrows<IllegalArgumentException> {
            analyzer.compareToBaseline(
                mapOf("rssi" to -60.0),
                mapOf("snr" to 30.0),
            )
        }
    }

    // ========================================
    // Benchmark Edge Cases
    // ========================================

    @Test
    fun `benchmarkAgainstOptimal handles perfect score`() {
        val network =
            mapOf(
                "rssi" to -50.0,
                "snr" to 50.0,
                "throughput" to 500.0,
                "latency" to 5.0,
                "packet_loss" to 0.1,
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertEquals(100.0, result.score, 0.1)
        assertEquals(BenchmarkGrade.EXCELLENT, result.grade)
        assertTrue(result.gaps.isEmpty())
    }

    @Test
    fun `benchmarkAgainstOptimal handles zero score case`() {
        val network =
            mapOf(
                "rssi" to -200.0, // Extremely poor
                "snr" to 0.0,
                "throughput" to 0.0,
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertTrue(result.score < 10.0)
        assertEquals(BenchmarkGrade.CRITICAL, result.grade)
        assertTrue(result.gaps.isNotEmpty())
    }

    @Test
    fun `benchmarkAgainstOptimal handles partial metric coverage`() {
        val network =
            mapOf(
                "rssi" to -55.0,
                "snr" to 45.0,
                // Missing throughput, latency, packet_loss
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertTrue(result.score in 0.0..100.0)
        // Only scores metrics that exist in both
    }

    @Test
    fun `benchmarkAgainstOptimal handles no matching metrics`() {
        val network =
            mapOf(
                "custom_metric1" to 100.0,
                "custom_metric2" to 200.0,
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertEquals(0.0, result.score) // No metrics to score
        assertTrue(result.gaps.isEmpty()) // No gaps if no common metrics
    }

    @Test
    fun `benchmarkAgainstOptimal identifies critical gaps`() {
        val network =
            mapOf(
                "rssi" to -90.0, // Much worse than -50 optimal
                "throughput" to 50.0, // Much worse than 500 optimal
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        val criticalGaps = result.gaps.filter { it.priority == Priority.CRITICAL }
        assertTrue(criticalGaps.isNotEmpty())
    }

    @Test
    fun `benchmarkAgainstOptimal identifies all priority levels`() {
        val network =
            mapOf(
                "rssi" to -90.0, // CRITICAL (achieved < 0.5)
                "snr" to 30.0, // MEDIUM (achieved ~0.6)
                "throughput" to 450.0, // LOW (achieved ~0.9)
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertTrue(result.gaps.any { it.priority == Priority.CRITICAL })
        assertTrue(result.gaps.any { it.priority.isUrgent })
    }

    @Test
    fun `benchmarkAgainstOptimal generates recommendations for gaps`() {
        val network =
            mapOf(
                "rssi" to -80.0,
                "snr" to 20.0,
                "throughput" to 50.0,
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertTrue(result.recommendations.isNotEmpty())
        assertTrue(result.recommendations.any { it.contains("signal strength", ignoreCase = true) })
    }

    @Test
    fun `benchmarkAgainstOptimal identifies strengths`() {
        val network =
            mapOf(
                "rssi" to -50.0, // Excellent
                "snr" to 48.0, // Excellent
                "throughput" to 100.0, // Poor
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertTrue(result.strengths.isNotEmpty())
        assertTrue(result.strengths.any { it.contains("rssi", ignoreCase = true) })
    }

    @Test
    fun `benchmarkAgainstOptimal uses correct higherIsBetter logic`() {
        val network =
            mapOf(
                "latency" to 20.0, // Higher is worse
                "packet_loss" to 5.0, // Higher is worse
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        // Both should show as gaps since higher is worse
        assertTrue(result.gaps.any { it.metric == "latency" })
        assertTrue(result.gaps.any { it.metric == "packet_loss" })
    }

    @Test
    fun `benchmarkAgainstOptimal rejects empty metrics`() {
        assertThrows<IllegalArgumentException> {
            analyzer.benchmarkAgainstOptimal(
                emptyMap(),
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )
        }
    }

    @Test
    fun `benchmarkAgainstOptimal sorts gaps by priority`() {
        val network =
            mapOf(
                "rssi" to -90.0, // CRITICAL
                "snr" to 30.0, // MEDIUM
                "throughput" to 400.0, // LOW
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        // Gaps should be sorted by priority descending
        result.gaps.zipWithNext().forEach { (first, second) ->
            assertTrue(first.priority.ordinal >= second.priority.ordinal)
        }
    }

    // ========================================
    // Temporal Comparison Edge Cases
    // ========================================

    @Test
    fun `compareTimePeriods handles all improved metrics`() {
        val period1 = mapOf("rssi" to -70.0, "snr" to 20.0, "throughput" to 50.0)
        val period2 = mapOf("rssi" to -60.0, "snr" to 30.0, "throughput" to 100.0)

        val comparison = analyzer.compareTimePeriods(period1, period2)

        assertEquals(3, comparison.improvedCount)
        assertEquals(0, comparison.degradedCount)
        assertEquals(ChangeDirection.IMPROVED, comparison.overallTrend)
    }

    @Test
    fun `compareTimePeriods handles all degraded metrics`() {
        val period1 = mapOf("rssi" to -60.0, "snr" to 30.0, "throughput" to 100.0)
        val period2 = mapOf("rssi" to -70.0, "snr" to 20.0, "throughput" to 50.0)

        val comparison = analyzer.compareTimePeriods(period1, period2)

        assertEquals(0, comparison.improvedCount)
        assertEquals(3, comparison.degradedCount)
        assertEquals(ChangeDirection.DEGRADED, comparison.overallTrend)
    }

    @Test
    fun `compareTimePeriods handles mixed results`() {
        val period1 = mapOf("rssi" to -60.0, "snr" to 30.0, "throughput" to 100.0)
        val period2 = mapOf("rssi" to -55.0, "snr" to 25.0, "throughput" to 110.0)

        val comparison = analyzer.compareTimePeriods(period1, period2)

        assertTrue(comparison.improvedCount > 0)
        assertTrue(comparison.degradedCount > 0)
    }

    @Test
    fun `compareTimePeriods handles all unchanged metrics`() {
        val period1 = mapOf("rssi" to -60.0, "snr" to 30.0)
        val period2 = mapOf("rssi" to -60.0, "snr" to 30.0)

        val comparison = analyzer.compareTimePeriods(period1, period2)

        assertEquals(0, comparison.improvedCount)
        assertEquals(0, comparison.degradedCount)
        assertEquals(2, comparison.stableCount)
        assertEquals(ChangeDirection.STABLE, comparison.overallTrend)
    }

    @Test
    fun `compareTimePeriods uses custom labels`() {
        val period1 = mapOf("rssi" to -60.0)
        val period2 = mapOf("rssi" to -55.0)

        val comparison =
            analyzer.compareTimePeriods(
                period1,
                period2,
                "Last Month",
                "This Month",
            )

        assertEquals("Last Month", comparison.period1Label)
        assertEquals("This Month", comparison.period2Label)
    }

    @Test
    fun `compareTimePeriods uses default labels`() {
        val period1 = mapOf("rssi" to -60.0)
        val period2 = mapOf("rssi" to -55.0)

        val comparison = analyzer.compareTimePeriods(period1, period2)

        assertEquals("Period 1", comparison.period1Label)
        assertEquals("Period 2", comparison.period2Label)
    }

    @Test
    fun `compareTimePeriods respects higherIsBetter flag`() {
        val period1 = mapOf("latency" to 10.0)
        val period2 = mapOf("latency" to 20.0)
        val higherIsBetter = mapOf("latency" to false)

        val comparison =
            analyzer.compareTimePeriods(
                period1,
                period2,
                higherIsBetter = higherIsBetter,
            )

        assertEquals(0, comparison.improvedCount)
        assertEquals(1, comparison.degradedCount)
    }

    // ========================================
    // Health Score Edge Cases
    // ========================================

    @Test
    fun `calculateHealthScore handles perfect health`() {
        val metrics =
            mapOf(
                "rssi" to -30.0, // Excellent
                "snr" to 60.0, // Excellent
                "throughput" to 100.0, // Excellent
            )

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score >= 90.0)
    }

    @Test
    fun `calculateHealthScore handles critical health`() {
        val metrics =
            mapOf(
                "rssi" to -90.0, // Very poor
                "snr" to 5.0, // Very poor
                "throughput" to 1.0, // Very poor
            )

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score <= 20.0)
    }

    @Test
    fun `calculateHealthScore handles single metric`() {
        val metrics = mapOf("rssi" to -60.0)

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score in 0.0..100.0)
    }

    @Test
    fun `calculateHealthScore handles many metrics`() {
        val metrics = (1..20).associate { "metric$it" to 50.0 }

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score in 0.0..100.0)
    }

    @Test
    fun `calculateHealthScore with equal weights`() {
        val metrics = mapOf("rssi" to -60.0, "snr" to 30.0)

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score in 0.0..100.0)
    }

    @Test
    fun `calculateHealthScore with custom weights`() {
        val metrics = mapOf("rssi" to -60.0, "snr" to 30.0)
        val weights = mapOf("rssi" to 3.0, "snr" to 1.0)

        val score = analyzer.calculateHealthScore(metrics, weights)

        assertTrue(score in 0.0..100.0)
    }

    @Test
    fun `calculateHealthScore with partial weights defaults missing to 1_0`() {
        val metrics = mapOf("rssi" to -60.0, "snr" to 30.0, "throughput" to 50.0)
        val weights = mapOf("rssi" to 2.0) // Only rssi has custom weight

        val score = analyzer.calculateHealthScore(metrics, weights)

        assertTrue(score in 0.0..100.0)
    }

    @Test
    fun `calculateHealthScore handles negative metric values`() {
        val metrics = mapOf("rssi" to -75.0, "latency" to -10.0)

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score in 0.0..100.0)
    }

    @Test
    fun `calculateHealthScore rejects empty metrics`() {
        assertThrows<IllegalArgumentException> {
            analyzer.calculateHealthScore(emptyMap())
        }
    }

    @Test
    fun `calculateHealthScore normalizes rssi correctly`() {
        val excellent = mapOf("rssi" to -30.0)
        val poor = mapOf("rssi" to -90.0)

        val excellentScore = analyzer.calculateHealthScore(excellent)
        val poorScore = analyzer.calculateHealthScore(poor)

        assertTrue(excellentScore > poorScore)
        assertTrue(excellentScore > 90.0)
        assertTrue(poorScore < 20.0)
    }

    @Test
    fun `calculateHealthScore normalizes snr correctly`() {
        val excellent = mapOf("snr" to 60.0)
        val poor = mapOf("snr" to 5.0)

        val excellentScore = analyzer.calculateHealthScore(excellent)
        val poorScore = analyzer.calculateHealthScore(poor)

        assertTrue(excellentScore > poorScore)
    }

    @Test
    fun `calculateHealthScore normalizes throughput correctly`() {
        val excellent = mapOf("throughput" to 100.0)
        val poor = mapOf("throughput" to 10.0)

        val excellentScore = analyzer.calculateHealthScore(excellent)
        val poorScore = analyzer.calculateHealthScore(poor)

        assertTrue(excellentScore > poorScore)
    }

    @Test
    fun `calculateHealthScore caps at 100`() {
        val metrics =
            mapOf(
                "rssi" to -20.0, // Beyond optimal
                "snr" to 100.0, // Beyond optimal
                "throughput" to 500.0, // Beyond typical
            )

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score <= 100.0)
    }

    @Test
    fun `calculateHealthScore floors at 0`() {
        val metrics =
            mapOf(
                "rssi" to -200.0, // Extreme poor
                "snr" to -50.0, // Impossible but test edge
                "throughput" to -100.0, // Impossible but test edge
            )

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score >= 0.0)
    }

    // ========================================
    // OptimalNetworkProfile Edge Cases
    // ========================================

    @Test
    fun `OptimalNetworkProfile ENTERPRISE_WIFI_6E has all expected metrics`() {
        val profile = OptimalNetworkProfile.ENTERPRISE_WIFI_6E

        assertTrue(profile.optimalValues.containsKey("rssi"))
        assertTrue(profile.optimalValues.containsKey("snr"))
        assertTrue(profile.optimalValues.containsKey("throughput"))
        assertTrue(profile.optimalValues.containsKey("latency"))
        assertTrue(profile.optimalValues.containsKey("packet_loss"))
    }

    @Test
    fun `OptimalNetworkProfile CONSUMER_WIFI_5 has lower thresholds`() {
        val enterprise = OptimalNetworkProfile.ENTERPRISE_WIFI_6E
        val consumer = OptimalNetworkProfile.CONSUMER_WIFI_5

        // Consumer should have more lenient (lower) optimal values
        assertTrue(consumer.optimalValues["throughput"]!! < enterprise.optimalValues["throughput"]!!)
    }

    @Test
    fun `OptimalNetworkProfile custom profile can be created`() {
        val custom =
            OptimalNetworkProfile(
                name = "Custom IoT Profile",
                optimalValues =
                    mapOf(
                        "rssi" to -70.0,
                        "latency" to 50.0,
                    ),
                higherIsBetter =
                    mapOf(
                        "rssi" to true,
                        "latency" to false,
                    ),
            )

        assertEquals("Custom IoT Profile", custom.name)
        assertEquals(2, custom.optimalValues.size)
    }

    @Test
    fun `OptimalNetworkProfile rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            OptimalNetworkProfile(
                name = "",
                optimalValues = mapOf("rssi" to -60.0),
            )
        }
    }

    @Test
    fun `OptimalNetworkProfile rejects empty optimal values`() {
        assertThrows<IllegalArgumentException> {
            OptimalNetworkProfile(
                name = "Test",
                optimalValues = emptyMap(),
            )
        }
    }
}
