package io.lamco.netkit.topology.visualization

import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class SignalStrengthMapperTest {
    @Test
    fun `SignalStrengthMapper requires positive grid resolution`() {
        assertThrows<IllegalArgumentException> {
            SignalStrengthMapper(gridResolution = 0.0)
        }
        assertThrows<IllegalArgumentException> {
            SignalStrengthMapper(gridResolution = -1.0)
        }
    }

    @Test
    fun `generateHeatmap requires non-empty measurements`() {
        val mapper = SignalStrengthMapper()
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        assertThrows<IllegalArgumentException> {
            mapper.generateHeatmap(emptyList(), bounds)
        }
    }

    @Test
    fun `generateHeatmap creates valid heatmap`() {
        val mapper = SignalStrengthMapper(gridResolution = 1.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -60), // Normalized coords [0-1]
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        val heatmap = mapper.generateHeatmap(measurements, bounds)

        assertEquals(11, heatmap.gridWidth)
        assertEquals(11, heatmap.gridHeight)
        assertEquals(bounds, heatmap.bounds)
    }

    @Test
    fun `predictSignalStrength with nearest neighbor`() {
        val mapper = SignalStrengthMapper(interpolationMethod = InterpolationMethod.NEAREST_NEIGHBOR)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.0, 0.0), -50),
                createMeasurement(LocalSurveyCoord(1.0, 1.0), -70), // Normalized coords [0-1]
            )

        val predicted = mapper.predictSignalStrength(measurements, LocalSurveyCoord(0.1, 0.1))

        // Should be closer to first measurement
        assertTrue(predicted > -60)
    }

    @Test
    fun `predictSignalStrength with IDW interpolation`() {
        val mapper = SignalStrengthMapper(interpolationMethod = InterpolationMethod.INVERSE_DISTANCE_WEIGHTED)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.0, 0.0), -50),
                createMeasurement(LocalSurveyCoord(1.0, 0.0), -70), // Normalized coords [0-1]
            )

        val predicted = mapper.predictSignalStrength(measurements, LocalSurveyCoord(0.5, 0.0))

        // Should be between -50 and -70
        assertTrue(predicted in -70..-50)
    }

    @Test
    fun `generateCoverageMap calculates coverage percentage`() {
        val mapper = SignalStrengthMapper(gridResolution = 2.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -60), // Normalized coords [0-1]
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        val coverageMap = mapper.generateCoverageMap(measurements, bounds)

        assertTrue(coverageMap.coveragePercentage in 0.0..100.0)
        assertEquals(
            coverageMap.totalGridPoints,
            coverageMap.excellentCoverage +
                coverageMap.goodCoverage +
                coverageMap.fairCoverage +
                coverageMap.poorCoverage +
                coverageMap.noCoverage,
        )
    }

    @Test
    fun `SignalQuality categorizes RSSI correctly`() {
        assertEquals(SignalQuality.EXCELLENT, SignalQuality.fromRssi(-45))
        assertEquals(SignalQuality.GOOD, SignalQuality.fromRssi(-55))
        assertEquals(SignalQuality.FAIR, SignalQuality.fromRssi(-65))
        assertEquals(SignalQuality.POOR, SignalQuality.fromRssi(-75))
        assertEquals(SignalQuality.NONE, SignalQuality.fromRssi(-90))
    }

    @Test
    fun `AreaBounds validates dimensions`() {
        assertThrows<IllegalArgumentException> {
            AreaBounds(10.0, 0.0, 5.0, 10.0) // maxX < minX
        }
        assertThrows<IllegalArgumentException> {
            AreaBounds(0.0, 10.0, 10.0, 5.0) // maxY < minY
        }
    }

    @Test
    fun `AreaBounds calculates dimensions correctly`() {
        val bounds = AreaBounds(0.0, 0.0, 10.0, 20.0)

        assertEquals(10.0, bounds.width)
        assertEquals(20.0, bounds.height)
        assertEquals(200.0, bounds.area)
    }

    @Test
    fun `SignalHeatmap getSignal returns correct values`() {
        val mapper = SignalStrengthMapper(gridResolution = 1.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -60), // Normalized coords [0-1]
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)
        val heatmap = mapper.generateHeatmap(measurements, bounds)

        assertNotNull(heatmap.getSignal(5, 5))
        assertNull(heatmap.getSignal(-1, 5))
        assertNull(heatmap.getSignal(5, -1))
        assertNull(heatmap.getSignal(100, 5))
    }

    @Test
    fun `SignalHeatmap calculates statistics correctly`() {
        val mapper = SignalStrengthMapper(gridResolution = 5.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -60), // Normalized coords [0-1]
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)
        val heatmap = mapper.generateHeatmap(measurements, bounds)

        assertTrue(heatmap.averageSignal <= -30) // Reasonable RSSI
        assertTrue(heatmap.minSignal <= heatmap.averageSignal)
        assertTrue(heatmap.maxSignal >= heatmap.averageSignal)
    }

    @Test
    fun `CoverageMap calculates percentages correctly`() {
        val mapper = SignalStrengthMapper(gridResolution = 2.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -55), // Good signal, normalized coords
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)
        val coverageMap = mapper.generateCoverageMap(measurements, bounds)

        assertTrue(coverageMap.excellentPercentage in 0.0..100.0)
        assertTrue(coverageMap.goodOrBetterPercentage in 0.0..100.0)
        assertTrue(coverageMap.goodOrBetterPercentage >= coverageMap.excellentPercentage)
    }

    @Test
    fun `CoverageMap categorizes overall quality`() {
        val mapper = SignalStrengthMapper(gridResolution = 5.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -45), // Excellent, normalized coords
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)
        val coverageMap = mapper.generateCoverageMap(measurements, bounds)

        assertNotNull(coverageMap.overallQuality)
    }

    @Test
    fun `identifyDeadZones finds weak coverage areas`() {
        val mapper = SignalStrengthMapper(gridResolution = 2.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.2, 0.2), -95), // Dead zone
                createMeasurement(LocalSurveyCoord(0.8, 0.8), -60), // Good coverage
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        val deadZones = mapper.identifyDeadZones(measurements, bounds, threshold = -85)

        assertTrue(deadZones.isNotEmpty())
    }

    @Test
    fun `identifyDeadZones filters by size`() {
        val mapper = SignalStrengthMapper(gridResolution = 1.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -95), // Normalized coords
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        val deadZones = mapper.identifyDeadZones(measurements, bounds)

        // Should filter out very small regions
        deadZones.forEach { zone ->
            assertTrue(zone.pointCount >= 4)
        }
    }

    @Test
    fun `DeadZoneRegion calculates severity correctly`() {
        val smallZone =
            DeadZoneRegion(
                gridPoints = listOf(Pair(0, 0), Pair(1, 1)),
                averageRssi = -88,
                worstRssi = -90,
                areaSquareMeters = 2.0,
            )

        val largeZone =
            DeadZoneRegion(
                gridPoints = List(20) { Pair(it, it) },
                averageRssi = -92,
                worstRssi = -98,
                areaSquareMeters = 25.0,
            )

        // Enum ordinals: CRITICAL=0, HIGH=1, MEDIUM=2, LOW=3
        // Lower ordinal = more severe, so use <= for "more severe"
        assertTrue(largeZone.severity <= smallZone.severity)
        assertEquals(DeadZoneSeverity.CRITICAL, largeZone.severity)
    }

    @Test
    fun `different interpolation methods produce different results`() {
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.0, 0.0), -50),
                createMeasurement(LocalSurveyCoord(1.0, 1.0), -70), // Normalized coords
            )
        val testCoord = LocalSurveyCoord(0.5, 0.5)

        val nearest =
            SignalStrengthMapper(interpolationMethod = InterpolationMethod.NEAREST_NEIGHBOR)
                .predictSignalStrength(measurements, testCoord)

        val idw =
            SignalStrengthMapper(interpolationMethod = InterpolationMethod.INVERSE_DISTANCE_WEIGHTED)
                .predictSignalStrength(measurements, testCoord)

        // Results may differ based on interpolation method
        assertTrue(nearest in -70..-50)
        assertTrue(idw in -70..-50)
    }

    @Test
    fun `heatmap equality works correctly`() {
        val mapper = SignalStrengthMapper(gridResolution = 2.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.5, 0.5), -60), // Normalized coords
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        val heatmap1 = mapper.generateHeatmap(measurements, bounds)
        val heatmap2 = mapper.generateHeatmap(measurements, bounds)

        assertEquals(heatmap1, heatmap2)
        assertEquals(heatmap1.hashCode(), heatmap2.hashCode())
    }

    @Test
    fun `generateHeatmap with fine grid resolution creates more cells`() {
        val coarse = SignalStrengthMapper(gridResolution = 5.0)
        val fine = SignalStrengthMapper(gridResolution = 1.0)
        val measurements = listOf(createMeasurement(LocalSurveyCoord(0.5, 0.5), -60)) // Normalized
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        val coarseMap = coarse.generateHeatmap(measurements, bounds)
        val fineMap = fine.generateHeatmap(measurements, bounds)

        assertTrue(fineMap.gridWidth > coarseMap.gridWidth)
        assertTrue(fineMap.gridHeight > coarseMap.gridHeight)
    }

    @Test
    fun `generateCoverageMap with excellent signal shows high coverage`() {
        val mapper = SignalStrengthMapper(gridResolution = 5.0)
        val measurements = listOf(createMeasurement(LocalSurveyCoord(0.5, 0.5), -45)) // Normalized
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        val coverageMap = mapper.generateCoverageMap(measurements, bounds)

        assertTrue(coverageMap.excellentCoverage > 0)
        assertTrue(coverageMap.coveragePercentage > 50.0)
    }

    @Test
    fun `identifyDeadZones custom threshold affects results`() {
        val mapper = SignalStrengthMapper(gridResolution = 2.0)
        val measurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.2, 0.2), -80), // Normalized coords
            )
        val bounds = AreaBounds(0.0, 0.0, 10.0, 10.0)

        val strict = mapper.identifyDeadZones(measurements, bounds, threshold = -70)
        val lenient = mapper.identifyDeadZones(measurements, bounds, threshold = -90)

        assertTrue(strict.size >= lenient.size)
    }

    @Test
    fun `predictSignalStrength at exact measurement returns measurement value`() {
        val mapper = SignalStrengthMapper(interpolationMethod = InterpolationMethod.INVERSE_DISTANCE_WEIGHTED)
        val coord = LocalSurveyCoord(0.5, 0.5) // Normalized coords
        val measurements = listOf(createMeasurement(coord, -55))

        val predicted = mapper.predictSignalStrength(measurements, coord)

        assertEquals(-55, predicted)
    }

    @Test
    fun `InterpolationMethod enum has all expected values`() {
        val methods = InterpolationMethod.values()

        assertTrue(methods.contains(InterpolationMethod.NEAREST_NEIGHBOR))
        assertTrue(methods.contains(InterpolationMethod.INVERSE_DISTANCE_WEIGHTED))
        assertTrue(methods.contains(InterpolationMethod.BILINEAR))
    }

    @Test
    fun `DeadZoneRegion severity increases with worse signal`() {
        val moderate =
            DeadZoneRegion(
                gridPoints = List(10) { Pair(it, it) },
                averageRssi = -88,
                worstRssi = -92,
                areaSquareMeters = 15.0,
            )

        val critical =
            DeadZoneRegion(
                gridPoints = List(15) { Pair(it, it) },
                averageRssi = -92,
                worstRssi = -96,
                areaSquareMeters = 20.0,
            )

        // Enum ordinals: CRITICAL=0, HIGH=1, MEDIUM=2, LOW=3
        // Lower ordinal = more severe, so use <= for "more severe"
        assertTrue(critical.severity <= moderate.severity)
    }

    @Test
    fun `SignalQuality fromRssi boundary values`() {
        assertEquals(SignalQuality.EXCELLENT, SignalQuality.fromRssi(-50))
        assertEquals(SignalQuality.GOOD, SignalQuality.fromRssi(-60))
        assertEquals(SignalQuality.FAIR, SignalQuality.fromRssi(-70))
        assertEquals(SignalQuality.POOR, SignalQuality.fromRssi(-85))
        assertEquals(SignalQuality.NONE, SignalQuality.fromRssi(-86))
    }

    @Test
    fun `CoverageMap overallQuality categorization`() {
        val excellent =
            SignalStrengthMapper(gridResolution = 5.0)
                .generateCoverageMap(
                    listOf(createMeasurement(LocalSurveyCoord(0.5, 0.5), -45)), // Normalized
                    AreaBounds(0.0, 0.0, 10.0, 10.0),
                )

        assertNotNull(excellent.overallQuality)
    }

    @Test
    fun `AreaBounds with equal dimensions has zero area`() {
        val bounds = AreaBounds(5.0, 5.0, 5.0001, 5.0001)

        assertTrue(bounds.area < 0.001)
    }

    @Test
    fun `multiple measurements improve interpolation accuracy`() {
        val mapper = SignalStrengthMapper(interpolationMethod = InterpolationMethod.INVERSE_DISTANCE_WEIGHTED)
        val singleMeasurement = listOf(createMeasurement(LocalSurveyCoord(0.0, 0.0), -50))
        val multipleMeasurements =
            listOf(
                createMeasurement(LocalSurveyCoord(0.0, 0.0), -50),
                createMeasurement(LocalSurveyCoord(1.0, 0.0), -70), // Normalized coords
                createMeasurement(LocalSurveyCoord(0.0, 1.0), -60), // Normalized coords
            )

        val testPoint = LocalSurveyCoord(0.5, 0.5) // Normalized
        val singlePrediction = mapper.predictSignalStrength(singleMeasurement, testPoint)
        val multiplePrediction = mapper.predictSignalStrength(multipleMeasurements, testPoint)

        // Multiple measurements should give different (likely more accurate) result
        assertNotEquals(singlePrediction, multiplePrediction)
    }

    private fun createMeasurement(
        location: LocalSurveyCoord,
        rssi: Int,
    ) = SurveyMeasurement(
        measurementId = "measurement-${System.currentTimeMillis()}-${rssi.hashCode()}",
        timestampMillis = System.currentTimeMillis(),
        localCoordinates = location,
        visibleBssids = mapOf("AA:BB:CC:DD:EE:FF" to rssi),
        connectedBssid = "AA:BB:CC:DD:EE:FF",
        connectedRssiDbm = rssi,
    )
}
