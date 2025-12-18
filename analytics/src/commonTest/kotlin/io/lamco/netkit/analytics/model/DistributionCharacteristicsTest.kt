package io.lamco.netkit.analytics.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class DistributionCharacteristicsTest {
    @Test
    fun `creates valid DistributionCharacteristics`() {
        val dist =
            DistributionCharacteristics(
                mean = 50.0,
                median = 50.0,
                mode = 50.0,
                variance = 25.0,
                skewness = 0.0,
                kurtosis = 3.0,
                shape = DistributionShape.NORMAL,
            )

        assertEquals(50.0, dist.mean)
        assertEquals(50.0, dist.median)
        assertEquals(25.0, dist.variance)
    }

    @Test
    fun `rejects negative variance`() {
        assertThrows<IllegalArgumentException> {
            DistributionCharacteristics(50.0, 50.0, null, variance = -25.0, 0.0, 3.0, DistributionShape.NORMAL)
        }
    }

    @Test
    fun `standardDeviation calculates correctly`() {
        val dist = DistributionCharacteristics(50.0, 50.0, null, 25.0, 0.0, 3.0, DistributionShape.NORMAL)
        assertEquals(5.0, dist.standardDeviation, 0.001)
    }

    @Test
    fun `isSymmetric returns true for low skewness`() {
        val dist = DistributionCharacteristics(50.0, 50.0, null, 25.0, skewness = 0.3, 3.0, DistributionShape.NORMAL)
        assertTrue(dist.isSymmetric)
    }

    @Test
    fun `isSkewedLeft returns true for negative skewness`() {
        val dist =
            DistributionCharacteristics(50.0, 50.0, null, 25.0, skewness = -1.0, 3.0, DistributionShape.SKEWED_LEFT)
        assertTrue(dist.isSkewedLeft)
    }

    @Test
    fun `isSkewedRight returns true for positive skewness`() {
        val dist =
            DistributionCharacteristics(50.0, 50.0, null, 25.0, skewness = 1.0, 3.0, DistributionShape.SKEWED_RIGHT)
        assertTrue(dist.isSkewedRight)
    }

    @Test
    fun `excessKurtosis calculates correctly`() {
        val dist = DistributionCharacteristics(50.0, 50.0, null, 25.0, 0.0, kurtosis = 5.0, DistributionShape.NORMAL)
        assertEquals(2.0, dist.excessKurtosis, 0.001)
    }

    @Test
    fun `hasHeavyTails returns true for high excess kurtosis`() {
        val dist = DistributionCharacteristics(50.0, 50.0, null, 25.0, 0.0, kurtosis = 5.0, DistributionShape.NORMAL)
        assertTrue(dist.hasHeavyTails)
    }

    @Test
    fun `appearsNormal returns true for normal characteristics`() {
        val dist =
            DistributionCharacteristics(
                50.0,
                median = 50.0,
                null,
                10.0,
                skewness = 0.1,
                kurtosis = 3.0,
                DistributionShape.NORMAL,
            )
        assertTrue(dist.appearsNormal)
    }

    @Test
    fun `fromValues creates DistributionCharacteristics from data`() {
        val values = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0)
        val dist = DistributionCharacteristics.fromValues(values)

        assertEquals(50.0, dist.mean, 0.001)
        assertEquals(50.0, dist.median, 0.001)
    }

    @Test
    fun `fromValues rejects small samples`() {
        assertThrows<IllegalArgumentException> {
            DistributionCharacteristics.fromValues(listOf(10.0, 20.0))
        }
    }
}

class DistributionShapeTest {
    @Test
    fun `NORMAL is symmetric`() {
        assertTrue(DistributionShape.NORMAL.isSymmetric)
    }

    @Test
    fun `SKEWED_RIGHT is skewed`() {
        assertTrue(DistributionShape.SKEWED_RIGHT.isSkewed)
    }

    @Test
    fun `SKEWED_LEFT is skewed`() {
        assertTrue(DistributionShape.SKEWED_LEFT.isSkewed)
    }
}

class HistogramTest {
    @Test
    fun `creates valid Histogram`() {
        val hist =
            Histogram(
                bins =
                    listOf(
                        HistogramBin(0.0, 10.0, 5, 0.5),
                        HistogramBin(10.0, 20.0, 5, 0.5),
                    ),
                binWidth = 10.0,
                totalCount = 10,
            )

        assertEquals(2, hist.binCount)
        assertEquals(10, hist.totalCount)
    }

    @Test
    fun `rejects empty bins`() {
        assertThrows<IllegalArgumentException> {
            Histogram(emptyList(), 10.0, 10)
        }
    }

    @Test
    fun `rejects mismatched total count`() {
        assertThrows<IllegalArgumentException> {
            Histogram(
                bins = listOf(HistogramBin(0.0, 10.0, 5, 0.5)),
                binWidth = 10.0,
                totalCount = 10, // Should be 5
            )
        }
    }

    @Test
    fun `range calculates correctly`() {
        val hist =
            Histogram(
                bins =
                    listOf(
                        HistogramBin(0.0, 10.0, 5, 0.5),
                        HistogramBin(10.0, 20.0, 5, 0.5),
                    ),
                binWidth = 10.0,
                totalCount = 10,
            )

        assertEquals(20.0, hist.range, 0.001)
    }

    @Test
    fun `modalBin returns bin with highest count`() {
        val bin1 = HistogramBin(0.0, 10.0, 3, 0.3)
        val bin2 = HistogramBin(10.0, 20.0, 7, 0.7)

        val hist = Histogram(listOf(bin1, bin2), 10.0, 10)

        assertEquals(bin2, hist.modalBin)
    }
}

class HistogramBinTest {
    @Test
    fun `creates valid HistogramBin`() {
        val bin = HistogramBin(0.0, 10.0, 5, 0.5)

        assertEquals(0.0, bin.lowerBound)
        assertEquals(10.0, bin.upperBound)
        assertEquals(5, bin.count)
    }

    @Test
    fun `rejects lower bound greater than or equal to upper bound`() {
        assertThrows<IllegalArgumentException> {
            HistogramBin(10.0, 10.0, 5, 0.5)
        }
    }

    @Test
    fun `width calculates correctly`() {
        val bin = HistogramBin(0.0, 10.0, 5, 0.5)
        assertEquals(10.0, bin.width)
    }

    @Test
    fun `midpoint calculates correctly`() {
        val bin = HistogramBin(0.0, 10.0, 5, 0.5)
        assertEquals(5.0, bin.midpoint)
    }

    @Test
    fun `isEmpty returns true for zero count`() {
        val bin = HistogramBin(0.0, 10.0, 0, 0.0)
        assertTrue(bin.isEmpty)
    }
}

class ProbabilityDensityTest {
    @Test
    fun `creates valid ProbabilityDensity`() {
        val pdf =
            ProbabilityDensity(
                points =
                    listOf(
                        DensityPoint(0.0, 0.1),
                        DensityPoint(10.0, 0.2),
                        DensityPoint(20.0, 0.1),
                    ),
                bandwidth = 5.0,
            )

        assertEquals(3, pdf.points.size)
        assertEquals(5.0, pdf.bandwidth)
    }

    @Test
    fun `maxDensity returns highest density value`() {
        val pdf =
            ProbabilityDensity(
                points =
                    listOf(
                        DensityPoint(0.0, 0.1),
                        DensityPoint(10.0, 0.3),
                        DensityPoint(20.0, 0.2),
                    ),
                bandwidth = 5.0,
            )

        assertEquals(0.3, pdf.maxDensity)
    }

    @Test
    fun `mode returns x at max density`() {
        val pdf =
            ProbabilityDensity(
                points =
                    listOf(
                        DensityPoint(0.0, 0.1),
                        DensityPoint(10.0, 0.3),
                        DensityPoint(20.0, 0.2),
                    ),
                bandwidth = 5.0,
            )

        assertEquals(10.0, pdf.mode)
    }

    @Test
    fun `densityAt returns interpolated value`() {
        val pdf =
            ProbabilityDensity(
                points =
                    listOf(
                        DensityPoint(0.0, 0.0),
                        DensityPoint(10.0, 1.0),
                    ),
                bandwidth = 5.0,
            )

        // At x=5.0, density should be interpolated between 0.0 and 1.0
        assertEquals(0.5, pdf.densityAt(5.0), 0.01)
    }
}
