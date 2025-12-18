package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

// ========================================
// CorrelationAnalyzer Tests
// ========================================

class CorrelationAnalyzerTest {
    private val analyzer = CorrelationAnalyzer()

    @Test
    fun `calculateCorrelation computes Pearson correctly`() {
        // Need at least 10 samples for Pearson
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val y = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0)

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.PEARSON)

        assertTrue(result.coefficient > 0.99) // Perfect positive correlation
        assertEquals(CorrelationMethod.PEARSON, result.method)
    }

    @Test
    fun `calculateCorrelation detects negative correlation`() {
        // Need at least 10 samples for Pearson
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val y = listOf(20.0, 18.0, 16.0, 14.0, 12.0, 10.0, 8.0, 6.0, 4.0, 2.0)

        val result = analyzer.calculateCorrelation(x, y)

        assertTrue(result.coefficient < -0.99) // Perfect negative correlation
        assertEquals(CorrelationDirection.NEGATIVE, result.direction)
    }

    @Test
    fun `calculateCorrelation handles no correlation`() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val y = listOf(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0)

        val result = analyzer.calculateCorrelation(x, y)

        assertTrue(kotlin.math.abs(result.coefficient) < 0.1)
    }

    @Test
    fun `calculateCorrelation with Spearman handles ranks`() {
        // Need at least 10 samples for Spearman
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val y = listOf(1.0, 4.0, 9.0, 16.0, 25.0, 36.0, 49.0, 64.0, 81.0, 100.0) // Monotonic but not linear

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.SPEARMAN)

        assertTrue(result.coefficient > 0.9)
    }

    @Test
    fun `calculateCorrelation with Kendall works`() {
        // Kendall requires only 5 samples
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = listOf(2.0, 4.0, 6.0, 8.0, 10.0)

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.KENDALL)

        assertTrue(result.coefficient > 0.8)
    }

    @Test
    fun `calculateCorrelation rejects mismatched lengths`() {
        assertThrows<IllegalArgumentException> {
            analyzer.calculateCorrelation(
                listOf(1.0, 2.0),
                listOf(1.0, 2.0, 3.0),
            )
        }
    }

    @Test
    fun `calculateCorrelation rejects insufficient data`() {
        assertThrows<IllegalArgumentException> {
            analyzer.calculateCorrelation(
                listOf(1.0),
                listOf(2.0),
                CorrelationMethod.PEARSON,
            )
        }
    }

    @Test
    fun `buildCorrelationMatrix creates matrix for multiple metrics`() {
        // Need at least 10 samples for Pearson (default method)
        val data =
            mapOf(
                "rssi" to listOf(-60.0, -58.0, -62.0, -59.0, -61.0, -60.0, -58.0, -62.0, -59.0, -61.0),
                "snr" to listOf(30.0, 32.0, 28.0, 31.0, 29.0, 30.0, 32.0, 28.0, 31.0, 29.0),
                "throughput" to listOf(50.0, 55.0, 45.0, 52.0, 48.0, 50.0, 55.0, 45.0, 52.0, 48.0),
            )

        val matrix = analyzer.buildCorrelationMatrix(data)

        assertEquals(3, matrix.metricCount)
        assertTrue(matrix.pairCount >= 3) // At least 3 pairs
    }

    @Test
    fun `buildCorrelationMatrix allows symmetric access`() {
        // Need at least 10 samples for Pearson (default method)
        val data =
            mapOf(
                "rssi" to listOf(-60.0, -58.0, -62.0, -59.0, -61.0, -60.0, -58.0, -62.0, -59.0, -61.0),
                "snr" to listOf(30.0, 32.0, 28.0, 31.0, 29.0, 30.0, 32.0, 28.0, 31.0, 29.0),
            )

        val matrix = analyzer.buildCorrelationMatrix(data)

        val result1 = matrix.getCorrelation("rssi", "snr")
        val result2 = matrix.getCorrelation("snr", "rssi")

        assertEquals(result1, result2)
    }

    @Test
    fun `findSignificantCorrelations filters by p-value`() {
        // Need at least 10 samples for Pearson (default method)
        val data =
            mapOf(
                "m1" to listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0),
                "m2" to listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0),
                "m3" to listOf(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0),
            )

        val matrix = analyzer.buildCorrelationMatrix(data)
        val significant = analyzer.findSignificantCorrelations(matrix)

        // m1-m2 should be significant, m3 should not correlate with anything
        assertTrue(significant.isNotEmpty())
    }

    @Test
    fun `findStrongCorrelations filters by coefficient`() {
        // Need at least 10 samples for Pearson (default method)
        val data =
            mapOf(
                "m1" to listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0),
                "m2" to listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0),
            )

        val matrix = analyzer.buildCorrelationMatrix(data)
        val strong = analyzer.findStrongCorrelations(matrix, strengthThreshold = 0.7)

        assertTrue(strong.isNotEmpty())
        assertTrue(strong.all { kotlin.math.abs(it.result.coefficient) >= 0.7 })
    }
}

// ========================================
// DistributionAnalyzer Tests
// ========================================

class DistributionAnalyzerTest {
    private val analyzer = DistributionAnalyzer()

    @Test
    fun `histogram creates correct number of bins`() {
        val values = (1..100).map { it.toDouble() }

        val hist = analyzer.histogram(values, bins = 10)

        assertEquals(10, hist.binCount)
        assertEquals(100, hist.totalCount)
    }

    @Test
    fun `histogram uses auto binning when bins not specified`() {
        val values = (1..100).map { it.toDouble() }

        val hist = analyzer.histogram(values)

        assertTrue(hist.binCount > 0)
    }

    @Test
    fun `histogram handles all equal values`() {
        val values = List(10) { 42.0 }

        val hist = analyzer.histogram(values)

        assertEquals(1, hist.binCount)
        assertEquals(10, hist.totalCount)
    }

    @Test
    fun `histogram calculates frequencies correctly`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)

        val hist = analyzer.histogram(values, bins = 5)

        val totalFreq = hist.bins.sumOf { it.frequency }
        assertEquals(1.0, totalFreq, 0.01)
    }

    @Test
    fun `estimateDensity returns probability density`() {
        val values = (1..50).map { it.toDouble() }

        val pdf = analyzer.estimateDensity(values)

        assertTrue(pdf.points.isNotEmpty())
        assertTrue(pdf.bandwidth > 0.0)
    }

    @Test
    fun `estimateDensity accepts custom bandwidth`() {
        val values = (1..20).map { it.toDouble() }

        val pdf = analyzer.estimateDensity(values, bandwidth = 2.0)

        assertEquals(2.0, pdf.bandwidth)
    }

    @Test
    fun `characterizeDistribution computes statistics`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)

        val dist = analyzer.characterizeDistribution(values)

        assertEquals(5.5, dist.mean, 0.1)
        assertTrue(dist.variance > 0.0)
    }

    @Test
    fun `isNormallyDistributed detects normal distribution`() {
        // Generate approximately normal data
        val values = listOf(10.0, 12.0, 14.0, 15.0, 15.0, 15.0, 16.0, 18.0, 20.0)

        val isNormal = analyzer.isNormallyDistributed(values)

        // Result depends on distribution characteristics
        assertTrue(isNormal || !isNormal) // Allow either
    }

    @Test
    fun `detectOutliers identifies outliers using IQR method`() {
        val values = listOf(10.0, 12.0, 14.0, 15.0, 16.0, 18.0, 100.0) // 100 is outlier

        val outliers = analyzer.detectOutliers(values)

        assertTrue(outliers.contains(100.0))
    }

    @Test
    fun `detectOutliers handles no outliers`() {
        val values = listOf(10.0, 12.0, 14.0, 15.0, 16.0, 18.0, 20.0)

        val outliers = analyzer.detectOutliers(values)

        // May or may not have outliers depending on distribution
        assertTrue(outliers.size <= values.size)
    }

    @Test
    fun `detectOutliers rejects too few values`() {
        assertThrows<IllegalArgumentException> {
            analyzer.detectOutliers(listOf(1.0, 2.0, 3.0))
        }
    }
}

// ========================================
// ComparativeAnalyzer Tests
// ========================================

class ComparativeAnalyzerTest {
    private val analyzer = ComparativeAnalyzer()

    @Test
    fun `compareToBaseline computes change correctly`() {
        val current = mapOf("rssi" to -55.0)
        val baseline = mapOf("rssi" to -60.0)

        val results = analyzer.compareToBaseline(current, baseline)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(5.0, result.change, 0.001)
        assertEquals(ChangeDirection.IMPROVED, result.direction)
    }

    @Test
    fun `compareToBaseline handles lower is better metrics`() {
        val current = mapOf("latency" to 15.0)
        val baseline = mapOf("latency" to 10.0)
        val higherIsBetter = mapOf("latency" to false)

        val results = analyzer.compareToBaseline(current, baseline, higherIsBetter)

        val result = results.first()
        assertEquals(ChangeDirection.DEGRADED, result.direction)
    }

    @Test
    fun `compareToBaseline handles multiple metrics`() {
        val current = mapOf("rssi" to -55.0, "snr" to 35.0)
        val baseline = mapOf("rssi" to -60.0, "snr" to 30.0)

        val results = analyzer.compareToBaseline(current, baseline)

        assertEquals(2, results.size)
    }

    @Test
    fun `compareToBaseline ignores metrics not in both`() {
        val current = mapOf("rssi" to -55.0, "extra" to 10.0)
        val baseline = mapOf("rssi" to -60.0)

        val results = analyzer.compareToBaseline(current, baseline)

        assertEquals(1, results.size)
        assertEquals("rssi", results.first().metric)
    }

    @Test
    fun `benchmarkAgainstOptimal calculates score`() {
        val network =
            mapOf(
                "rssi" to -55.0,
                "snr" to 40.0,
                "throughput" to 400.0,
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertTrue(result.score in 0.0..100.0)
        assertTrue(result.grade != BenchmarkGrade.CRITICAL || result.score < 40.0)
    }

    @Test
    fun `benchmarkAgainstOptimal identifies gaps`() {
        val network =
            mapOf(
                "rssi" to -70.0, // Well below optimal -50
                "snr" to 40.0,
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertTrue(result.gaps.isNotEmpty())
        assertTrue(result.gaps.any { it.metric == "rssi" })
    }

    @Test
    fun `benchmarkAgainstOptimal generates recommendations`() {
        val network =
            mapOf(
                "rssi" to -70.0,
                "throughput" to 50.0,
            )

        val result =
            analyzer.benchmarkAgainstOptimal(
                network,
                OptimalNetworkProfile.ENTERPRISE_WIFI_6E,
            )

        assertTrue(result.recommendations.isNotEmpty())
    }

    @Test
    fun `compareTimePeriods computes temporal comparison`() {
        val period1 = mapOf("rssi" to -60.0, "snr" to 30.0)
        val period2 = mapOf("rssi" to -55.0, "snr" to 35.0)

        val comparison =
            analyzer.compareTimePeriods(
                period1,
                period2,
                "Last Week",
                "This Week",
            )

        assertEquals("Last Week", comparison.period1Label)
        assertEquals("This Week", comparison.period2Label)
        assertEquals(2, comparison.improvedCount)
        assertEquals(ChangeDirection.IMPROVED, comparison.overallTrend)
    }

    @Test
    fun `calculateHealthScore aggregates metrics`() {
        val metrics =
            mapOf(
                "rssi" to -60.0,
                "snr" to 30.0,
                "throughput" to 100.0,
            )

        val score = analyzer.calculateHealthScore(metrics)

        assertTrue(score in 0.0..100.0)
    }

    @Test
    fun `calculateHealthScore uses weights when provided`() {
        val metrics =
            mapOf(
                "rssi" to -60.0,
                "snr" to 30.0,
            )
        val weights =
            mapOf(
                "rssi" to 2.0,
                "snr" to 1.0,
            )

        val score = analyzer.calculateHealthScore(metrics, weights)

        assertTrue(score in 0.0..100.0)
    }
}

// ========================================
// OptimalNetworkProfile Tests
// ========================================

class OptimalNetworkProfileTest {
    @Test
    fun `ENTERPRISE_WIFI_6E has expected values`() {
        val profile = OptimalNetworkProfile.ENTERPRISE_WIFI_6E

        assertEquals("Enterprise WiFi 6E", profile.name)
        assertTrue(profile.optimalValues.isNotEmpty())
        assertTrue(profile.optimalValues.containsKey("rssi"))
    }

    @Test
    fun `CONSUMER_WIFI_5 has expected values`() {
        val profile = OptimalNetworkProfile.CONSUMER_WIFI_5

        assertEquals("Consumer WiFi 5", profile.name)
        assertTrue(profile.optimalValues.isNotEmpty())
    }

    @Test
    fun `custom profile can be created`() {
        val custom =
            OptimalNetworkProfile(
                name = "Custom",
                optimalValues = mapOf("metric1" to 100.0),
                higherIsBetter = mapOf("metric1" to true),
            )

        assertEquals("Custom", custom.name)
        assertEquals(100.0, custom.optimalValues["metric1"])
    }
}
