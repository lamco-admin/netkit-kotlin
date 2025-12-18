package io.lamco.netkit.analytics.analyzer

import io.lamco.netkit.analytics.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.test.*

/**
 * Comprehensive edge case tests for CorrelationAnalyzer
 *
 * Focuses on numerical stability, tied ranks, zero variance, and boundary conditions.
 */
class CorrelationAnalyzerEdgeCasesTest {
    private val analyzer = CorrelationAnalyzer()

    // ========================================
    // Perfect Correlation Edge Cases
    // ========================================

    @Test
    fun `calculateCorrelation handles perfect positive correlation`() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val y = x.map { it * 2.0 }

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.PEARSON)

        assertTrue(result.coefficient > 0.999)
        assertEquals(CorrelationStrength.VERY_STRONG, result.strength)
        assertEquals(CorrelationDirection.POSITIVE, result.direction)
    }

    @Test
    fun `calculateCorrelation handles perfect negative correlation`() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val y = x.map { -it }

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.PEARSON)

        assertTrue(result.coefficient < -0.999)
        assertEquals(CorrelationDirection.NEGATIVE, result.direction)
    }

    @Test
    fun `calculateCorrelation handles exact linear relationship`() {
        // Need at least 10 samples for Pearson
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val y = x.map { 3.0 * it + 7.0 } // y = 3x + 7

        val result = analyzer.calculateCorrelation(x, y)

        assertTrue(abs(result.coefficient - 1.0) < 0.001)
    }

    // ========================================
    // Zero Variance Edge Cases
    // ========================================

    @Test
    fun `calculateCorrelation handles constant x values`() {
        val x = List(10) { 5.0 }
        val y = (1..10).map { it.toDouble() }

        val result = analyzer.calculateCorrelation(x, y)

        assertEquals(0.0, result.coefficient, 0.001)
        assertEquals(CorrelationDirection.NONE, result.direction)
    }

    @Test
    fun `calculateCorrelation handles constant y values`() {
        val x = (1..10).map { it.toDouble() }
        val y = List(10) { 42.0 }

        val result = analyzer.calculateCorrelation(x, y)

        assertEquals(0.0, result.coefficient, 0.001)
    }

    @Test
    fun `calculateCorrelation handles both constant`() {
        val x = List(10) { 5.0 }
        val y = List(10) { 7.0 }

        val result = analyzer.calculateCorrelation(x, y)

        assertEquals(0.0, result.coefficient)
        assertEquals(CorrelationStrength.NONE, result.strength)
    }

    // ========================================
    // Tied Ranks Edge Cases (Spearman/Kendall)
    // ========================================

    @Test
    fun `calculateCorrelation Spearman handles all tied ranks in x`() {
        // Need at least 10 samples for Spearman
        val x = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        val y = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.SPEARMAN)

        assertEquals(0.0, result.coefficient, 0.001)
    }

    @Test
    fun `calculateCorrelation Spearman handles partial ties`() {
        // Need at least 10 samples for Spearman
        val x = listOf(1.0, 2.0, 2.0, 3.0, 4.0, 5.0, 5.0, 6.0, 7.0, 8.0)
        val y = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.SPEARMAN)

        assertTrue(result.coefficient > 0.8)
    }

    @Test
    fun `calculateCorrelation Kendall handles all concordant pairs`() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = listOf(2.0, 4.0, 6.0, 8.0, 10.0)

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.KENDALL)

        assertEquals(1.0, result.coefficient, 0.01)
    }

    @Test
    fun `calculateCorrelation Kendall handles all discordant pairs`() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = listOf(10.0, 8.0, 6.0, 4.0, 2.0)

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.KENDALL)

        assertEquals(-1.0, result.coefficient, 0.01)
    }

    @Test
    fun `calculateCorrelation Kendall handles ties correctly`() {
        // Need at least 5 samples for Kendall
        val x = listOf(1.0, 2.0, 2.0, 3.0, 4.0)
        val y = listOf(1.0, 2.0, 2.0, 3.0, 4.0)

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.KENDALL)

        // Ties don't count as concordant or discordant
        assertTrue(result.coefficient >= 0.0)
    }

    // ========================================
    // Minimum Sample Size Edge Cases
    // ========================================

    @Test
    fun `calculateCorrelation handles exactly minimum sample for Pearson`() {
        val x = (1..10).map { it.toDouble() }
        val y = x.map { it * 2.0 }

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.PEARSON)

        assertTrue(abs(result.coefficient) > 0.9)
    }

    @Test
    fun `calculateCorrelation handles exactly minimum sample for Kendall`() {
        val x = (1..5).map { it.toDouble() }
        val y = x.map { it * 2.0 }

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.KENDALL)

        assertTrue(result.coefficient > 0.8)
    }

    @Test
    fun `calculateCorrelation rejects too few samples for Pearson`() {
        val x = (1..5).map { it.toDouble() }
        val y = x.map { it * 2.0 }

        assertThrows<IllegalArgumentException> {
            analyzer.calculateCorrelation(x, y, CorrelationMethod.PEARSON)
        }
    }

    @Test
    fun `calculateCorrelation rejects too few samples for Kendall`() {
        val x = (1..3).map { it.toDouble() }
        val y = x.map { it * 2.0 }

        assertThrows<IllegalArgumentException> {
            analyzer.calculateCorrelation(x, y, CorrelationMethod.KENDALL)
        }
    }

    // ========================================
    // Non-linear Relationships
    // ========================================

    @Test
    fun `calculateCorrelation Pearson detects no correlation in quadratic`() {
        val x = (-5..5).map { it.toDouble() }
        val y = x.map { it * it } // Parabola

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.PEARSON)

        // Pearson should show weak correlation for symmetric parabola
        assertTrue(abs(result.coefficient) < 0.5)
    }

    @Test
    fun `calculateCorrelation Spearman detects monotonic relationship`() {
        val x = (1..10).map { it.toDouble() }
        val y = x.map { it * it } // Quadratic but monotonic

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.SPEARMAN)

        // Spearman should show strong correlation for monotonic data
        assertTrue(result.coefficient > 0.95)
    }

    @Test
    fun `calculateCorrelation handles exponential relationship`() {
        val x = (1..10).map { it.toDouble() }
        val y = x.map { kotlin.math.exp(it / 10.0) }

        val pearson = analyzer.calculateCorrelation(x, y, CorrelationMethod.PEARSON)
        val spearman = analyzer.calculateCorrelation(x, y, CorrelationMethod.SPEARMAN)

        // Spearman should be stronger than Pearson for exponential
        assertTrue(spearman.coefficient >= pearson.coefficient)
    }

    // ========================================
    // Correlation Matrix Edge Cases
    // ========================================

    @Test
    fun `buildCorrelationMatrix handles exactly 2 metrics`() {
        val data =
            mapOf(
                "x" to (1..10).map { it.toDouble() },
                "y" to (1..10).map { it * 2.0 },
            )

        val matrix = analyzer.buildCorrelationMatrix(data)

        assertEquals(2, matrix.metricCount)
        assertEquals(1, matrix.pairCount)
    }

    @Test
    fun `buildCorrelationMatrix rejects single metric`() {
        val data = mapOf("x" to (1..10).map { it.toDouble() })

        assertThrows<IllegalArgumentException> {
            analyzer.buildCorrelationMatrix(data)
        }
    }

    @Test
    fun `buildCorrelationMatrix rejects mismatched sample sizes`() {
        val data =
            mapOf(
                "x" to (1..10).map { it.toDouble() },
                "y" to (1..5).map { it.toDouble() },
            )

        assertThrows<IllegalArgumentException> {
            analyzer.buildCorrelationMatrix(data)
        }
    }

    @Test
    fun `buildCorrelationMatrix handles many metrics`() {
        val data =
            (1..10).associate { i ->
                "metric$i" to (1..20).map { it.toDouble() + i }
            }

        val matrix = analyzer.buildCorrelationMatrix(data)

        assertEquals(10, matrix.metricCount)
        assertEquals(45, matrix.pairCount) // n*(n-1)/2 = 10*9/2 = 45
    }

    @Test
    fun `buildCorrelationMatrix allows getCorrelation in any order`() {
        val data =
            mapOf(
                "x" to (1..10).map { it.toDouble() },
                "y" to (1..10).map { it * 2.0 },
                "z" to (1..10).map { it * 3.0 },
            )

        val matrix = analyzer.buildCorrelationMatrix(data)

        val xy1 = matrix.getCorrelation("x", "y")
        val xy2 = matrix.getCorrelation("y", "x")

        assertEquals(xy1, xy2)
        assertNotNull(xy1)
    }

    @Test
    fun `buildCorrelationMatrix returns null for non-existent pair`() {
        val data =
            mapOf(
                "x" to (1..10).map { it.toDouble() },
                "y" to (1..10).map { it * 2.0 },
            )

        val matrix = analyzer.buildCorrelationMatrix(data)

        assertNull(matrix.getCorrelation("x", "z"))
        assertNull(matrix.getCorrelation("nonexistent", "y"))
    }

    // ========================================
    // Filtering Edge Cases
    // ========================================

    @Test
    fun `findSignificantCorrelations with threshold 1 returns all`() {
        // p-value threshold of 1.0 means all correlations are significant
        val data =
            mapOf(
                "x" to (1..10).map { it.toDouble() },
                "y" to (1..10).map { it * 2.0 },
            )

        val matrix = analyzer.buildCorrelationMatrix(data)
        val significant = analyzer.findSignificantCorrelations(matrix, threshold = 1.0)

        // With threshold=1.0 (accept any p-value), all should be included
        assertTrue(significant.isNotEmpty())
    }

    @Test
    fun `findSignificantCorrelations with threshold 0 returns none`() {
        // p-value threshold of 0.0 means no correlations are significant
        val data =
            mapOf(
                "x" to (1..10).map { it.toDouble() },
                "y" to (1..10).map { it * 2.0 },
            )

        val matrix = analyzer.buildCorrelationMatrix(data)
        val significant = analyzer.findSignificantCorrelations(matrix, threshold = 0.0)

        // With threshold=0.0, no p-value can be less than 0
        assertTrue(significant.isEmpty())
    }

    @Test
    fun `findStrongCorrelations with threshold 0 returns all`() {
        val data =
            mapOf(
                "x" to (1..10).map { it.toDouble() },
                "y" to (1..10).map { it * 2.0 },
                "z" to (1..10).map { 5.0 },
            )

        val matrix = analyzer.buildCorrelationMatrix(data)
        val strong = analyzer.findStrongCorrelations(matrix, strengthThreshold = 0.0)

        assertEquals(matrix.pairCount, strong.size)
    }

    @Test
    fun `findStrongCorrelations with threshold 1 returns only perfect`() {
        val data =
            mapOf(
                "x" to (1..10).map { it.toDouble() },
                "y" to (1..10).map { it * 2.0 },
            )

        val matrix = analyzer.buildCorrelationMatrix(data)
        val strong = analyzer.findStrongCorrelations(matrix, strengthThreshold = 0.99)

        assertTrue(strong.size <= 1) // Only perfect or near-perfect correlations
    }

    @Test
    fun `findStrongCorrelations sorts by coefficient descending`() {
        val data =
            mapOf(
                "a" to (1..10).map { it.toDouble() },
                "b" to (1..10).map { it * 2.0 }, // Strong positive
                "c" to (1..10).map { it * 0.5 + 5.0 }, // Moderate positive
                "d" to (1..10).map { 10.0 - it * 0.1 }, // Weak negative
            )

        val matrix = analyzer.buildCorrelationMatrix(data)
        val strong = analyzer.findStrongCorrelations(matrix, strengthThreshold = 0.0)

        // Should be sorted by absolute value descending
        strong.zipWithNext().forEach { (first, second) ->
            assertTrue(abs(first.result.coefficient) >= abs(second.result.coefficient))
        }
    }

    // ========================================
    // Numerical Stability Tests
    // ========================================

    @Test
    fun `calculateCorrelation handles very small values`() {
        val x = (1..10).map { it * 1e-10 }
        val y = x.map { it * 2.0 }

        val result = analyzer.calculateCorrelation(x, y)

        assertTrue(result.coefficient > 0.99)
    }

    @Test
    fun `calculateCorrelation handles very large values`() {
        val x = (1..10).map { it * 1e10 }
        val y = x.map { it * 2.0 }

        val result = analyzer.calculateCorrelation(x, y)

        assertTrue(result.coefficient > 0.99)
    }

    @Test
    fun `calculateCorrelation handles mixed scale values`() {
        val x = listOf(1e-5, 1e-3, 1e-1, 1.0, 10.0, 100.0, 1000.0, 1e4, 1e5, 1e6)
        val y = x.map { kotlin.math.log10(it) }

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.SPEARMAN)

        assertTrue(abs(result.coefficient) > 0.9) // Monotonic relationship
    }

    @Test
    fun `calculateCorrelation handles negative and positive values`() {
        val x = listOf(-5.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
        val y = x.map { it * it } // All positive parabola

        val result = analyzer.calculateCorrelation(x, y, CorrelationMethod.PEARSON)

        // Should show low correlation due to symmetry
        assertTrue(abs(result.coefficient) < 0.5)
    }

    @Test
    fun `calculateCorrelation handles values near zero`() {
        val x = listOf(0.0001, 0.0002, 0.0003, 0.0004, 0.0005, 0.0006, 0.0007, 0.0008, 0.0009, 0.001)
        val y = x.map { it * 1.5 }

        val result = analyzer.calculateCorrelation(x, y)

        assertTrue(result.coefficient > 0.99)
    }
}
