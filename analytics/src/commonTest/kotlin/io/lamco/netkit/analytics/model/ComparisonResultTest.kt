package io.lamco.netkit.analytics.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class ComparisonResultTest {
    @Test
    fun `creates valid ComparisonResult`() {
        val result =
            ComparisonResult(
                metric = "rssi",
                current = -55.0,
                baseline = -60.0,
                change = 5.0,
                changePercent = 8.33,
                direction = ChangeDirection.IMPROVED,
                significance = SignificanceLevel.NONE,
            )

        assertEquals("rssi", result.metric)
        assertEquals(-55.0, result.current)
        assertEquals(-60.0, result.baseline)
    }

    @Test
    fun `rejects blank metric name`() {
        assertThrows<IllegalArgumentException> {
            ComparisonResult("", -55.0, -60.0, 5.0, 8.33, ChangeDirection.IMPROVED, SignificanceLevel.NONE)
        }
    }

    @Test
    fun `rejects incorrect change calculation`() {
        assertThrows<IllegalArgumentException> {
            ComparisonResult(
                "rssi",
                -55.0,
                -60.0,
                change = 10.0,
                8.33,
                ChangeDirection.IMPROVED,
                SignificanceLevel.NONE,
            )
        }
    }

    @Test
    fun `absoluteChange returns absolute value`() {
        // current=-60, baseline=-55 → change = -60 - (-55) = -5 (RSSI got worse)
        val result =
            ComparisonResult("rssi", -60.0, -55.0, -5.0, -9.09, ChangeDirection.DEGRADED, SignificanceLevel.NONE)
        assertEquals(5.0, result.absoluteChange, 0.001)
    }

    @Test
    fun `isSignificant returns true for moderate or higher significance`() {
        val result =
            ComparisonResult("rssi", -55.0, -60.0, 5.0, 16.0, ChangeDirection.IMPROVED, SignificanceLevel.MODERATE)
        assertTrue(result.isSignificant)
    }

    @Test
    fun `isImprovement returns true for IMPROVED direction`() {
        val result = ComparisonResult("rssi", -55.0, -60.0, 5.0, 8.33, ChangeDirection.IMPROVED, SignificanceLevel.NONE)
        assertTrue(result.isImprovement)
    }

    @Test
    fun `isDegradation returns true for DEGRADED direction`() {
        val result =
            ComparisonResult("rssi", -65.0, -60.0, -5.0, -8.33, ChangeDirection.DEGRADED, SignificanceLevel.NONE)
        assertTrue(result.isDegradation)
    }

    @Test
    fun `from factory creates result with calculated values for higher is better metric`() {
        val result = ComparisonResult.from("rssi", current = -55.0, baseline = -60.0, higherIsBetter = true)

        assertEquals(5.0, result.change, 0.001)
        assertEquals(ChangeDirection.IMPROVED, result.direction)
    }

    @Test
    fun `from factory creates result with calculated values for lower is better metric`() {
        val result = ComparisonResult.from("latency", current = 15.0, baseline = 10.0, higherIsBetter = false)

        assertEquals(5.0, result.change, 0.001)
        assertEquals(ChangeDirection.DEGRADED, result.direction)
    }

    @Test
    fun `ratio calculates correctly`() {
        val result = ComparisonResult.from("throughput", current = 100.0, baseline = 50.0)
        assertEquals(2.0, result.ratio!!, 0.001)
    }

    @Test
    fun `ratio returns null for zero baseline`() {
        val result = ComparisonResult.from("throughput", current = 100.0, baseline = 0.0)
        assertNull(result.ratio)
    }
}

class ChangeDirectionTest {
    @Test
    fun `IMPROVED is positive`() {
        assertTrue(ChangeDirection.IMPROVED.isPositive)
        assertFalse(ChangeDirection.IMPROVED.isNegative)
    }

    @Test
    fun `DEGRADED is negative`() {
        assertTrue(ChangeDirection.DEGRADED.isNegative)
        assertFalse(ChangeDirection.DEGRADED.isPositive)
    }

    @Test
    fun `STABLE is neutral`() {
        assertTrue(ChangeDirection.STABLE.isNeutral)
    }
}

class SignificanceLevelTest {
    @Test
    fun `fromChangePercent returns VERY_HIGH for 50 percent`() {
        assertEquals(SignificanceLevel.VERY_HIGH, SignificanceLevel.fromChangePercent(50.0))
    }

    @Test
    fun `fromChangePercent returns HIGH for 30 percent`() {
        assertEquals(SignificanceLevel.HIGH, SignificanceLevel.fromChangePercent(30.0))
    }

    @Test
    fun `fromChangePercent returns MODERATE for 15 percent`() {
        assertEquals(SignificanceLevel.MODERATE, SignificanceLevel.fromChangePercent(15.0))
    }

    @Test
    fun `fromChangePercent returns LOW for 10 percent`() {
        assertEquals(SignificanceLevel.LOW, SignificanceLevel.fromChangePercent(10.0))
    }

    @Test
    fun `fromChangePercent returns NONE for 5 percent`() {
        assertEquals(SignificanceLevel.NONE, SignificanceLevel.fromChangePercent(5.0))
    }

    @Test
    fun `isSignificant returns true for LOW and above`() {
        assertTrue(SignificanceLevel.LOW.isSignificant)
        assertTrue(SignificanceLevel.MODERATE.isSignificant)
        assertFalse(SignificanceLevel.NONE.isSignificant)
    }

    @Test
    fun `isActionable returns true for MODERATE and above`() {
        assertTrue(SignificanceLevel.MODERATE.isActionable)
        assertTrue(SignificanceLevel.HIGH.isActionable)
        assertFalse(SignificanceLevel.LOW.isActionable)
    }
}

class BenchmarkResultTest {
    @Test
    fun `creates valid BenchmarkResult`() {
        val result =
            BenchmarkResult(
                score = 85.0,
                gaps = emptyList(),
                strengths = listOf("High throughput"),
                recommendations = listOf("Reduce latency"),
            )

        assertEquals(85.0, result.score)
        assertEquals(1, result.strengths.size)
    }

    @Test
    fun `rejects score outside range`() {
        assertThrows<IllegalArgumentException> {
            BenchmarkResult(105.0, emptyList(), emptyList(), emptyList())
        }
    }

    @Test
    fun `grade returns EXCELLENT for high score`() {
        val result = BenchmarkResult(95.0, emptyList(), emptyList(), emptyList())
        assertEquals(BenchmarkGrade.EXCELLENT, result.grade)
    }

    @Test
    fun `grade returns GOOD for 75 percent score`() {
        val result = BenchmarkResult(80.0, emptyList(), emptyList(), emptyList())
        assertEquals(BenchmarkGrade.GOOD, result.grade)
    }

    @Test
    fun `grade returns FAIR for 60 percent score`() {
        val result = BenchmarkResult(65.0, emptyList(), emptyList(), emptyList())
        assertEquals(BenchmarkGrade.FAIR, result.grade)
    }

    @Test
    fun `isAcceptable returns true for score above 60`() {
        val result = BenchmarkResult(70.0, emptyList(), emptyList(), emptyList())
        assertTrue(result.isAcceptable)
    }

    @Test
    fun `isExcellent returns true for score above 90`() {
        val result = BenchmarkResult(95.0, emptyList(), emptyList(), emptyList())
        assertTrue(result.isExcellent)
    }
}

class PerformanceGapTest {
    @Test
    fun `creates valid PerformanceGap`() {
        val gap = PerformanceGap("throughput", 50.0, 100.0, 50.0, Priority.HIGH)

        assertEquals("throughput", gap.metric)
        assertEquals(50.0, gap.current)
        assertEquals(100.0, gap.optimal)
    }

    @Test
    fun `gapPercent calculates correctly`() {
        val gap = PerformanceGap("throughput", 50.0, 100.0, 50.0, Priority.HIGH)
        assertEquals(50.0, gap.gapPercent, 0.001)
    }

    @Test
    fun `achievedPercent calculates correctly`() {
        val gap = PerformanceGap("throughput", 75.0, 100.0, 25.0, Priority.MEDIUM)
        assertEquals(75.0, gap.achievedPercent, 0.001)
    }
}

class PriorityTest {
    @Test
    fun `CRITICAL is critical`() {
        assertTrue(Priority.CRITICAL.isCritical)
        assertTrue(Priority.CRITICAL.isUrgent)
    }

    @Test
    fun `HIGH is urgent but not critical`() {
        assertFalse(Priority.HIGH.isCritical)
        assertTrue(Priority.HIGH.isUrgent)
    }

    @Test
    fun `MEDIUM is not urgent`() {
        assertFalse(Priority.MEDIUM.isUrgent)
    }
}

class TemporalComparisonTest {
    @Test
    fun `creates valid TemporalComparison`() {
        val comparison =
            TemporalComparison(
                period1Label = "Last Week",
                period2Label = "This Week",
                comparisons =
                    listOf(
                        ComparisonResult.from("rssi", -55.0, -60.0),
                    ),
            )

        assertEquals("Last Week", comparison.period1Label)
        assertEquals("This Week", comparison.period2Label)
        assertEquals(1, comparison.comparisons.size)
    }

    @Test
    fun `improvedCount calculates correctly`() {
        val comparison =
            TemporalComparison(
                "Week 1",
                "Week 2",
                comparisons =
                    listOf(
                        ComparisonResult.from("rssi", -55.0, -60.0), // improved
                        ComparisonResult.from("snr", 25.0, 20.0), // improved
                    ),
            )

        assertEquals(2, comparison.improvedCount)
    }

    @Test
    fun `overallTrend returns IMPROVED when more metrics improved`() {
        val comparison =
            TemporalComparison(
                "Week 1",
                "Week 2",
                comparisons =
                    listOf(
                        ComparisonResult.from("rssi", -55.0, -60.0), // improved
                        ComparisonResult.from("snr", 25.0, 20.0), // improved
                        ComparisonResult.from("latency", 15.0, 10.0, higherIsBetter = false), // degraded
                    ),
            )

        assertEquals(ChangeDirection.IMPROVED, comparison.overallTrend)
    }

    @Test
    fun `hasSignificantChanges returns true when any comparison is significant`() {
        val comparison =
            TemporalComparison(
                "Week 1",
                "Week 2",
                comparisons =
                    listOf(
                        ComparisonResult.from("rssi", -40.0, -60.0), // 33% change - significant
                    ),
            )

        assertTrue(comparison.hasSignificantChanges)
    }
}
