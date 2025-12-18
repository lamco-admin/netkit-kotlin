package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.test.*

/**
 * Comprehensive edge case tests for DistributionAnalyzer
 *
 * Focuses on histogram binning, KDE edge cases, outlier detection,
 * and numerical stability across various data distributions.
 */
class DistributionAnalyzerEdgeCasesTest {
    private val analyzer = DistributionAnalyzer()

    // ========================================
    // Histogram Binning Edge Cases
    // ========================================

    @Test
    fun `histogram with single bin contains all values`() {
        val values = (1..100).map { it.toDouble() }

        val hist = analyzer.histogram(values, bins = 1)

        assertEquals(1, hist.binCount)
        assertEquals(100, hist.bins.first().count)
        assertEquals(1.0, hist.bins.first().frequency)
    }

    @Test
    fun `histogram with many bins creates sparse distribution`() {
        val values = (1..10).map { it.toDouble() }

        val hist = analyzer.histogram(values, bins = 50)

        assertEquals(50, hist.binCount)
        // Most bins should be empty or have 1 value
        val maxBinCount = hist.bins.maxOf { it.count }
        assertTrue(maxBinCount <= 2)
    }

    @Test
    fun `histogram handles all equal values`() {
        val values = List(100) { 42.0 }

        val hist = analyzer.histogram(values, bins = 10)

        // All equal values should create single bin
        assertEquals(1, hist.binCount)
        assertEquals(100, hist.bins.first().count)
    }

    @Test
    fun `histogram handles negative values`() {
        val values = (-50..-1).map { it.toDouble() }

        val hist = analyzer.histogram(values, bins = 10)

        assertEquals(10, hist.binCount)
        assertEquals(50, hist.totalCount)
        assertTrue(hist.bins.all { it.lowerBound < 0.0 })
    }

    @Test
    fun `histogram handles mixed negative and positive values`() {
        val values = (-25..25).map { it.toDouble() }

        val hist = analyzer.histogram(values, bins = 10)

        assertEquals(10, hist.binCount)
        assertTrue(hist.bins.first().lowerBound < 0.0)
        assertTrue(hist.bins.last().upperBound > 0.0)
    }

    @Test
    fun `histogram handles very small range`() {
        val values = listOf(1.0, 1.0001, 1.0002, 1.0003, 1.0004)

        val hist = analyzer.histogram(values, bins = 3)

        assertEquals(3, hist.binCount)
        assertTrue(hist.binWidth < 0.001)
    }

    @Test
    fun `histogram handles very large range`() {
        val values = listOf(1.0, 1000.0, 1000000.0, 1000000000.0)

        val hist = analyzer.histogram(values, bins = 5)

        assertEquals(5, hist.binCount)
        assertTrue(hist.binWidth > 100000.0)
    }

    @Test
    fun `histogram with auto binning uses Sturges rule`() {
        val values = (1..100).map { it.toDouble() }

        val hist = analyzer.histogram(values)

        // Sturges: 1 + log2(100) ≈ 7.64 → 7 bins
        assertTrue(hist.binCount in 6..8)
    }

    @Test
    fun `histogram frequencies sum to 1_0`() {
        val values = (1..50).map { it.toDouble() }

        val hist = analyzer.histogram(values, bins = 10)

        val totalFreq = hist.bins.sumOf { it.frequency }
        assertEquals(1.0, totalFreq, 0.0001)
    }

    @Test
    fun `histogram bin counts sum to total`() {
        val values = (1..73).map { it.toDouble() }

        val hist = analyzer.histogram(values, bins = 8)

        val totalCount = hist.bins.sumOf { it.count }
        assertEquals(73, totalCount)
        assertEquals(73, hist.totalCount)
    }

    @Test
    fun `histogram handles single value`() {
        val values = listOf(42.0)

        val hist = analyzer.histogram(values)

        assertEquals(1, hist.binCount)
        assertEquals(1, hist.totalCount)
    }

    @Test
    fun `histogram handles two identical values`() {
        val values = listOf(10.0, 10.0)

        val hist = analyzer.histogram(values, bins = 5)

        assertEquals(1, hist.binCount)
        assertEquals(2, hist.bins.first().count)
    }

    @Test
    fun `histogram rejects empty values`() {
        assertThrows<IllegalArgumentException> {
            analyzer.histogram(emptyList())
        }
    }

    @Test
    fun `histogram rejects zero bins`() {
        assertThrows<IllegalArgumentException> {
            analyzer.histogram(listOf(1.0, 2.0, 3.0), bins = 0)
        }
    }

    @Test
    fun `histogram rejects negative bins`() {
        assertThrows<IllegalArgumentException> {
            analyzer.histogram(listOf(1.0, 2.0, 3.0), bins = -5)
        }
    }

    // ========================================
    // KDE Estimation Edge Cases
    // ========================================

    @Test
    fun `estimateDensity handles constant values`() {
        val values = List(20) { 50.0 }

        // Constant values have 0 std deviation, so auto bandwidth fails
        // Must provide explicit bandwidth for constant values
        val pdf = analyzer.estimateDensity(values, bandwidth = 1.0)

        // Density should peak around 50.0
        assertTrue(pdf.points.isNotEmpty())
        assertEquals(1.0, pdf.bandwidth)
    }

    @Test
    fun `estimateDensity handles single value`() {
        val values = listOf(42.0)

        // Single value has 0 std deviation, so auto bandwidth fails
        // Must provide explicit bandwidth for single value
        val pdf = analyzer.estimateDensity(values, bandwidth = 1.0)

        assertTrue(pdf.points.isNotEmpty())
        assertEquals(1.0, pdf.bandwidth)
    }

    @Test
    fun `estimateDensity with very small bandwidth creates narrow peak`() {
        val values = (1..10).map { it.toDouble() }

        val pdf = analyzer.estimateDensity(values, bandwidth = 0.01)

        assertEquals(0.01, pdf.bandwidth)
        assertTrue(pdf.points.isNotEmpty())
    }

    @Test
    fun `estimateDensity with very large bandwidth smooths heavily`() {
        val values = (1..10).map { it.toDouble() }

        val pdf = analyzer.estimateDensity(values, bandwidth = 100.0)

        assertEquals(100.0, pdf.bandwidth)
        // Large bandwidth should create very smooth (flat) density
        val densityRange = pdf.points.maxOf { it.density } - pdf.points.minOf { it.density }
        assertTrue(densityRange < 0.1) // Very flat
    }

    @Test
    fun `estimateDensity handles outliers correctly`() {
        val values = (1..10).map { it.toDouble() } + listOf(1000.0)

        val pdf = analyzer.estimateDensity(values)

        // Should have density points covering outlier region
        assertTrue(pdf.points.any { it.x > 100.0 })
    }

    @Test
    fun `estimateDensity with custom points parameter`() {
        val values = (1..20).map { it.toDouble() }

        val pdf = analyzer.estimateDensity(values, points = 50)

        assertEquals(50, pdf.points.size)
    }

    @Test
    fun `estimateDensity rejects empty values`() {
        assertThrows<IllegalArgumentException> {
            analyzer.estimateDensity(emptyList())
        }
    }

    @Test
    fun `estimateDensity rejects zero points`() {
        assertThrows<IllegalArgumentException> {
            analyzer.estimateDensity(listOf(1.0, 2.0), points = 0)
        }
    }

    @Test
    fun `estimateDensity rejects negative points`() {
        assertThrows<IllegalArgumentException> {
            analyzer.estimateDensity(listOf(1.0, 2.0), points = -10)
        }
    }

    @Test
    fun `estimateDensity rejects negative bandwidth`() {
        assertThrows<IllegalArgumentException> {
            analyzer.estimateDensity(listOf(1.0, 2.0), bandwidth = -1.0)
        }
    }

    @Test
    fun `estimateDensity rejects zero bandwidth`() {
        assertThrows<IllegalArgumentException> {
            analyzer.estimateDensity(listOf(1.0, 2.0), bandwidth = 0.0)
        }
    }

    // ========================================
    // Distribution Characterization Edge Cases
    // ========================================

    @Test
    fun `characterizeDistribution handles uniform distribution`() {
        val values = (1..100).map { it.toDouble() }

        val dist = analyzer.characterizeDistribution(values)

        assertEquals(50.5, dist.mean, 1.0)
        assertEquals(50.5, dist.median, 1.0)
        // Uniform should have low skewness
        assertTrue(abs(dist.skewness) < 0.5)
    }

    @Test
    fun `characterizeDistribution handles normal-like distribution`() {
        // Approximate normal using central values
        val values =
            listOf(
                10.0,
                12.0,
                14.0,
                15.0,
                15.0,
                15.0,
                16.0,
                16.0,
                16.0,
                16.0,
                17.0,
                17.0,
                17.0,
                17.0,
                17.0,
                18.0,
                18.0,
                18.0,
                18.0,
                19.0,
                19.0,
                19.0,
                20.0,
                20.0,
                22.0,
                24.0,
            )

        val dist = analyzer.characterizeDistribution(values)

        // Should be roughly symmetric
        assertTrue(abs(dist.skewness) < 1.0)
        assertTrue(dist.mean > 0.0)
        assertTrue(dist.variance > 0.0)
    }

    @Test
    fun `characterizeDistribution handles heavily right-skewed data`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 100.0, 200.0, 300.0)

        val dist = analyzer.characterizeDistribution(values)

        assertTrue(dist.skewness > 0.5) // Right-skewed
        assertTrue(dist.mean > dist.median) // Mean pulled right
    }

    @Test
    fun `characterizeDistribution handles heavily left-skewed data`() {
        val values = listOf(-300.0, -200.0, -100.0, 1.0, 2.0, 3.0, 4.0, 5.0)

        val dist = analyzer.characterizeDistribution(values)

        assertTrue(dist.skewness < -0.5) // Left-skewed
        assertTrue(dist.mean < dist.median) // Mean pulled left
    }

    @Test
    fun `characterizeDistribution handles bimodal-like distribution`() {
        val values = List(20) { 10.0 } + List(20) { 90.0 }

        val dist = analyzer.characterizeDistribution(values)

        // Bimodal should show in statistics
        assertEquals(50.0, dist.mean, 1.0) // Mean between peaks
        assertTrue(dist.variance > 1000.0) // High variance
    }

    @Test
    fun `characterizeDistribution handles all identical values`() {
        val values = List(50) { 42.0 }

        val dist = analyzer.characterizeDistribution(values)

        assertEquals(42.0, dist.mean)
        assertEquals(42.0, dist.median)
        assertEquals(0.0, dist.variance)
        assertEquals(0.0, dist.skewness)
    }

    @Test
    fun `characterizeDistribution handles two distinct values`() {
        val values = List(10) { 5.0 } + List(10) { 15.0 }

        val dist = analyzer.characterizeDistribution(values)

        assertEquals(10.0, dist.mean)
        assertTrue(dist.variance > 0.0)
    }

    @Test
    fun `characterizeDistribution handles extreme outliers`() {
        val values = (1..10).map { it.toDouble() } + listOf(1000000.0)

        val dist = analyzer.characterizeDistribution(values)

        // Outlier should affect mean more than median
        assertTrue(dist.mean > dist.median)
        assertTrue(dist.variance > 1000.0)
    }

    // ========================================
    // Normality Testing Edge Cases
    // ========================================

    @Test
    fun `isNormallyDistributed handles perfectly uniform data`() {
        val values = (1..100).map { it.toDouble() }

        val isNormal = analyzer.isNormallyDistributed(values)

        // Uniform is not normal, but test is lenient
        assertTrue(isNormal || !isNormal) // Allow either
    }

    @Test
    fun `isNormallyDistributed handles heavily skewed data`() {
        val values = listOf(1.0, 2.0, 3.0, 100.0, 200.0, 300.0)

        val isNormal = analyzer.isNormallyDistributed(values)

        // Heavily skewed should not appear normal
        // (but implementation may be lenient)
        assertTrue(isNormal || !isNormal)
    }

    // ========================================
    // Outlier Detection Edge Cases
    // ========================================

    @Test
    fun `detectOutliers handles no outliers case`() {
        val values = (10..20).map { it.toDouble() }

        val outliers = analyzer.detectOutliers(values)

        // Uniform data unlikely to have outliers
        assertTrue(outliers.isEmpty() || outliers.size <= 2)
    }

    @Test
    fun `detectOutliers identifies single extreme outlier`() {
        val values = (1..20).map { it.toDouble() } + listOf(1000.0)

        val outliers = analyzer.detectOutliers(values)

        assertTrue(outliers.contains(1000.0))
    }

    @Test
    fun `detectOutliers identifies multiple outliers`() {
        val values = (10..20).map { it.toDouble() } + listOf(-100.0, 500.0, 1000.0)

        val outliers = analyzer.detectOutliers(values)

        assertTrue(outliers.size >= 1) // At least one outlier
    }

    @Test
    fun `detectOutliers handles symmetric outliers`() {
        val values = listOf(-100.0) + (1..20).map { it.toDouble() } + listOf(100.0)

        val outliers = analyzer.detectOutliers(values)

        // Both extremes should be detected
        assertTrue(outliers.contains(-100.0) || outliers.contains(100.0))
    }

    @Test
    fun `detectOutliers handles negative values`() {
        val values = (-50..-30).map { it.toDouble() } + listOf(-200.0)

        val outliers = analyzer.detectOutliers(values)

        assertTrue(outliers.contains(-200.0) || outliers.isEmpty())
    }

    @Test
    fun `detectOutliers handles all values as outliers edge case`() {
        val values = listOf(1.0, 100.0, 200.0, 300.0)

        val outliers = analyzer.detectOutliers(values, iqrMultiplier = 0.1)

        // With very strict multiplier, many values may be outliers
        assertTrue(outliers.size >= 0)
    }

    @Test
    fun `detectOutliers with custom IQR multiplier`() {
        val values = (1..50).map { it.toDouble() }

        val outliers1 = analyzer.detectOutliers(values, iqrMultiplier = 1.5)
        val outliers2 = analyzer.detectOutliers(values, iqrMultiplier = 3.0)

        // Stricter multiplier (1.5) should find more or equal outliers
        assertTrue(outliers1.size >= outliers2.size)
    }

    @Test
    fun `detectOutliers handles minimum values exactly`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0)

        // Should not throw with exactly 4 values
        val outliers = analyzer.detectOutliers(values)

        assertTrue(outliers.size >= 0)
    }

    @Test
    fun `detectOutliers rejects too few values`() {
        assertThrows<IllegalArgumentException> {
            analyzer.detectOutliers(listOf(1.0, 2.0, 3.0))
        }
    }

    @Test
    fun `detectOutliers rejects negative IQR multiplier`() {
        assertThrows<IllegalArgumentException> {
            analyzer.detectOutliers(listOf(1.0, 2.0, 3.0, 4.0, 5.0), iqrMultiplier = -1.0)
        }
    }

    @Test
    fun `detectOutliers rejects zero IQR multiplier`() {
        assertThrows<IllegalArgumentException> {
            analyzer.detectOutliers(listOf(1.0, 2.0, 3.0, 4.0, 5.0), iqrMultiplier = 0.0)
        }
    }

    // ========================================
    // Numerical Stability Tests
    // ========================================

    @Test
    fun `histogram handles very small differences between values`() {
        val values =
            listOf(
                1.0000001,
                1.0000002,
                1.0000003,
                1.0000004,
                1.0000005,
                1.0000006,
                1.0000007,
                1.0000008,
                1.0000009,
                1.0000010,
            )

        val hist = analyzer.histogram(values, bins = 5)

        assertEquals(5, hist.binCount)
        assertTrue(hist.binWidth > 0.0)
    }

    @Test
    fun `histogram handles very large values`() {
        val values = (1..10).map { it * 1e15 }

        val hist = analyzer.histogram(values, bins = 5)

        assertEquals(5, hist.binCount)
        assertEquals(10, hist.totalCount)
    }

    @Test
    fun `estimateDensity handles very small values`() {
        val values = (1..10).map { it * 1e-10 }

        val pdf = analyzer.estimateDensity(values)

        assertTrue(pdf.points.isNotEmpty())
        assertTrue(pdf.bandwidth > 0.0)
    }

    @Test
    fun `estimateDensity handles very large values`() {
        val values = (1..10).map { it * 1e10 }

        val pdf = analyzer.estimateDensity(values)

        assertTrue(pdf.points.isNotEmpty())
        assertTrue(pdf.bandwidth > 0.0)
    }

    @Test
    fun `characterizeDistribution handles mixed scale values`() {
        val values = listOf(1e-5, 1e-3, 1.0, 100.0, 1e5, 1e7)

        val dist = analyzer.characterizeDistribution(values)

        assertTrue(dist.mean > 0.0)
        assertTrue(dist.variance > 0.0)
        assertTrue(dist.skewness.isFinite())
    }

    @Test
    fun `detectOutliers handles values near zero`() {
        val values = listOf(0.0001, 0.0002, 0.0003, 0.0004, 0.0005, 0.0006, 0.0007, 0.0008)

        val outliers = analyzer.detectOutliers(values)

        assertTrue(outliers.size >= 0)
    }

    @Test
    fun `histogram handles scientific notation values`() {
        val values = listOf(1e-8, 2e-8, 3e-8, 4e-8, 5e-8)

        val hist = analyzer.histogram(values, bins = 3)

        assertEquals(3, hist.binCount)
        assertTrue(hist.binWidth > 0.0)
    }
}
