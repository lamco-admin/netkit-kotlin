package io.lamco.netkit.analytics.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class CorrelationResultTest {
    @Test
    fun `creates valid CorrelationResult`() {
        val result =
            CorrelationResult(
                coefficient = 0.75,
                pValue = 0.01,
                method = CorrelationMethod.PEARSON,
                strength = CorrelationStrength.STRONG,
                direction = CorrelationDirection.POSITIVE,
            )

        assertEquals(0.75, result.coefficient)
        assertEquals(0.01, result.pValue)
    }

    @Test
    fun `rejects coefficient outside range`() {
        assertThrows<IllegalArgumentException> {
            CorrelationResult(
                1.5,
                0.01,
                CorrelationMethod.PEARSON,
                CorrelationStrength.STRONG,
                CorrelationDirection.POSITIVE,
            )
        }
    }

    @Test
    fun `rejects pValue outside range`() {
        assertThrows<IllegalArgumentException> {
            CorrelationResult(
                0.75,
                1.5,
                CorrelationMethod.PEARSON,
                CorrelationStrength.STRONG,
                CorrelationDirection.POSITIVE,
            )
        }
    }

    @Test
    fun `isSignificant returns true for p less than 0 point 05`() {
        val result =
            CorrelationResult(
                0.75,
                0.04,
                CorrelationMethod.PEARSON,
                CorrelationStrength.STRONG,
                CorrelationDirection.POSITIVE,
            )
        assertTrue(result.isSignificant)
    }

    @Test
    fun `isSignificant returns false for p greater than 0 point 05`() {
        val result =
            CorrelationResult(
                0.75,
                0.06,
                CorrelationMethod.PEARSON,
                CorrelationStrength.STRONG,
                CorrelationDirection.POSITIVE,
            )
        assertFalse(result.isSignificant)
    }

    @Test
    fun `isHighlySignificant returns true for p less than 0 point 01`() {
        val result =
            CorrelationResult(
                0.75,
                0.005,
                CorrelationMethod.PEARSON,
                CorrelationStrength.STRONG,
                CorrelationDirection.POSITIVE,
            )
        assertTrue(result.isHighlySignificant)
    }

    @Test
    fun `rSquared calculates correctly`() {
        val result =
            CorrelationResult(
                0.8,
                0.01,
                CorrelationMethod.PEARSON,
                CorrelationStrength.STRONG,
                CorrelationDirection.POSITIVE,
            )
        assertEquals(0.64, result.rSquared, 0.001)
    }

    @Test
    fun `from factory method creates result with inferred strength and direction`() {
        val result = CorrelationResult.from(0.75, 0.01, CorrelationMethod.PEARSON)
        assertEquals(CorrelationStrength.STRONG, result.strength)
        assertEquals(CorrelationDirection.POSITIVE, result.direction)
    }
}

class CorrelationMethodTest {
    @Test
    fun `PEARSON is parametric`() {
        assertTrue(CorrelationMethod.PEARSON.isParametric)
        assertFalse(CorrelationMethod.PEARSON.isNonParametric)
    }

    @Test
    fun `SPEARMAN is non-parametric`() {
        assertFalse(CorrelationMethod.SPEARMAN.isParametric)
        assertTrue(CorrelationMethod.SPEARMAN.isNonParametric)
    }

    @Test
    fun `recommend returns PEARSON for normal distributed data`() {
        val method = CorrelationMethod.recommend(100, false, true)
        assertEquals(CorrelationMethod.PEARSON, method)
    }

    @Test
    fun `recommend returns SPEARMAN for data with outliers`() {
        val method = CorrelationMethod.recommend(100, true, true)
        assertEquals(CorrelationMethod.SPEARMAN, method)
    }

    @Test
    fun `recommend returns KENDALL for small sample size`() {
        val method = CorrelationMethod.recommend(20, false, true)
        assertEquals(CorrelationMethod.KENDALL, method)
    }
}

class CorrelationStrengthTest {
    @Test
    fun `fromCoefficient returns VERY_STRONG for 0 point 95`() {
        assertEquals(CorrelationStrength.VERY_STRONG, CorrelationStrength.fromCoefficient(0.95))
    }

    @Test
    fun `fromCoefficient returns STRONG for 0 point 75`() {
        assertEquals(CorrelationStrength.STRONG, CorrelationStrength.fromCoefficient(0.75))
    }

    @Test
    fun `fromCoefficient returns MODERATE for 0 point 55`() {
        assertEquals(CorrelationStrength.MODERATE, CorrelationStrength.fromCoefficient(0.55))
    }

    @Test
    fun `fromCoefficient returns WEAK for 0 point 35`() {
        assertEquals(CorrelationStrength.WEAK, CorrelationStrength.fromCoefficient(0.35))
    }

    @Test
    fun `fromCoefficient handles negative coefficients`() {
        assertEquals(CorrelationStrength.STRONG, CorrelationStrength.fromCoefficient(-0.75))
    }

    @Test
    fun `isMeaningful returns true for STRONG`() {
        assertTrue(CorrelationStrength.STRONG.isMeaningful)
    }

    @Test
    fun `isMeaningful returns false for WEAK`() {
        assertFalse(CorrelationStrength.WEAK.isMeaningful)
    }
}

class CorrelationDirectionTest {
    @Test
    fun `fromCoefficient returns POSITIVE for positive value`() {
        assertEquals(CorrelationDirection.POSITIVE, CorrelationDirection.fromCoefficient(0.5))
    }

    @Test
    fun `fromCoefficient returns NEGATIVE for negative value`() {
        assertEquals(CorrelationDirection.NEGATIVE, CorrelationDirection.fromCoefficient(-0.5))
    }

    @Test
    fun `fromCoefficient returns NONE for near-zero value`() {
        assertEquals(CorrelationDirection.NONE, CorrelationDirection.fromCoefficient(0.05))
    }
}

class CorrelationMatrixTest {
    @Test
    fun `creates valid CorrelationMatrix`() {
        val matrix =
            CorrelationMatrix(
                metricNames = listOf("rssi", "snr"),
                correlations =
                    mapOf(
                        Pair("rssi", "snr") to CorrelationResult.from(0.75, 0.01, CorrelationMethod.PEARSON),
                    ),
                method = CorrelationMethod.PEARSON,
            )

        assertEquals(2, matrix.metricCount)
        assertEquals(1, matrix.pairCount)
    }

    @Test
    fun `rejects fewer than 2 metrics`() {
        assertThrows<IllegalArgumentException> {
            CorrelationMatrix(
                metricNames = listOf("rssi"),
                correlations = emptyMap(),
                method = CorrelationMethod.PEARSON,
            )
        }
    }

    @Test
    fun `getCorrelation returns result for both orderings`() {
        val result = CorrelationResult.from(0.75, 0.01, CorrelationMethod.PEARSON)
        val matrix =
            CorrelationMatrix(
                metricNames = listOf("rssi", "snr"),
                correlations = mapOf(Pair("rssi", "snr") to result),
                method = CorrelationMethod.PEARSON,
            )

        assertEquals(result, matrix.getCorrelation("rssi", "snr"))
        assertEquals(result, matrix.getCorrelation("snr", "rssi"))
    }

    @Test
    fun `significantCorrelations filters correctly`() {
        val significant = CorrelationResult.from(0.75, 0.01, CorrelationMethod.PEARSON)
        val notSignificant = CorrelationResult.from(0.75, 0.1, CorrelationMethod.PEARSON)

        val matrix =
            CorrelationMatrix(
                metricNames = listOf("rssi", "snr", "throughput"),
                correlations =
                    mapOf(
                        Pair("rssi", "snr") to significant,
                        Pair("rssi", "throughput") to notSignificant,
                    ),
                method = CorrelationMethod.PEARSON,
            )

        assertEquals(1, matrix.significantCorrelations.size)
    }
}
