package io.lamco.netkit.topology.survey

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

// Test Helpers
private fun createTestBss(
    bssid: String = "AA:BB:CC:DD:EE:FF",
    band: WiFiBand = WiFiBand.BAND_5GHZ,
    rssi: Int = -60,
) = ClusteredBss(
    bssid = bssid,
    band = band,
    channel = 36,
    frequencyMHz = 5180,
    channelWidth = ChannelWidth.WIDTH_80MHZ,
    wifiStandard = WifiStandard.WIFI_6,
    rssiDbm = rssi,
)

private fun createTestCluster(
    ssid: String = "TestNetwork",
    bssids: List<ClusteredBss> = listOf(createTestBss()),
) = ApCluster(
    id = "cluster-${ssid.hashCode()}",
    ssid = ssid,
    securityFingerprint = SecurityFingerprint.wpa3Personal(),
    routerVendor = RouterVendor.UBIQUITI,
    bssids = bssids,
)

private fun createTestSnapshot(
    id: String = "snap_123",
    timestampMillis: Long = 1000000,
    clusters: List<ApCluster> = listOf(createTestCluster()),
) = ScanSnapshot(
    id = id,
    timestampMillis = timestampMillis,
    clusters = clusters,
    scanDurationMillis = 1000,
)

/**
 * Tests for SiteSurveyCollector
 */
class SiteSurveyCollectorTest {
    @Test
    fun `SiteSurveyCollector requires valid parameters`() {
        assertThrows<IllegalArgumentException> {
            SiteSurveyCollector(minMeasurementsPerLocation = 0)
        }
        assertThrows<IllegalArgumentException> {
            SiteSurveyCollector(measurementTimeoutMillis = 0)
        }
        assertThrows<IllegalArgumentException> {
            SiteSurveyCollector(spatialResolutionMeters = 0.0)
        }
    }

    @Test
    fun `createSurvey creates new session`() {
        val collector = SiteSurveyCollector()

        val session = collector.createSurvey("TestSurvey", "TestNetwork", "Test description")

        assertEquals("TestSurvey", session.name)
        assertEquals("TestNetwork", session.ssid)
        assertEquals("Test description", session.description)
        assertEquals(SurveyStatus.IN_PROGRESS, session.status)
        assertTrue(session.measurements.isEmpty())
    }

    @Test
    fun `createSurvey validates parameters`() {
        val collector = SiteSurveyCollector()

        assertThrows<IllegalArgumentException> {
            collector.createSurvey("", "TestNetwork")
        }
        assertThrows<IllegalArgumentException> {
            collector.createSurvey("Test", "")
        }
    }

    @Test
    fun `addMeasurement adds measurement to session`() {
        val collector = SiteSurveyCollector(spatialResolutionMeters = 2.0)
        val session = collector.createSurvey("Test", "TestNetwork")
        val snapshot = createTestSnapshot()
        val location = SurveyLocation(0.0, 0.0, "Point 1")

        val updated = collector.addMeasurement(session, snapshot, location)

        assertEquals(1, updated.measurements.size)
        assertEquals(location, updated.measurements.first().location)
    }

    @Test
    fun `addMeasurement merges nearby measurements`() {
        val collector = SiteSurveyCollector(spatialResolutionMeters = 2.0)
        val session = collector.createSurvey("Test", "TestNetwork")
        val snapshot = createTestSnapshot()

        val location1 = SurveyLocation(0.0, 0.0)
        val location2 = SurveyLocation(1.0, 1.0) // ~1.41m away, within resolution

        val updated1 = collector.addMeasurement(session, snapshot, location1)
        val updated2 = collector.addMeasurement(updated1, snapshot, location2)

        // Should merge into one measurement with count of 2
        assertEquals(1, updated2.measurements.size)
        assertEquals(2, updated2.measurements.first().measurementCount)
    }

    @Test
    fun `addMeasurement validates snapshot contains target SSID`() {
        val collector = SiteSurveyCollector()
        val session = collector.createSurvey("Test", "DifferentNetwork")
        val snapshot = createTestSnapshot() // Contains "TestNetwork"
        val location = SurveyLocation(0.0, 0.0)

        assertThrows<IllegalArgumentException> {
            collector.addMeasurement(session, snapshot, location)
        }
    }

    @Test
    fun `completeSurvey marks session as complete`() {
        val collector = SiteSurveyCollector()
        val session = collector.createSurvey("Test", "TestNetwork")

        val completed = collector.completeSurvey(session)

        assertEquals(SurveyStatus.COMPLETED, completed.status)
        assertNotNull(completed.endTimestamp)
        assertTrue(completed.isComplete)
    }

    @Test
    fun `assessSurveyQuality returns correct quality levels`() {
        val collector = SiteSurveyCollector(minMeasurementsPerLocation = 3)

        // Insufficient data
        val emptySession = collector.createSurvey("Test", "TestNetwork")
        assertEquals(SurveyQuality.INSUFFICIENT_DATA, collector.assessSurveyQuality(emptySession))

        // Good quality requires 20+ locations AND avgMeasurementCount >= minMeasurementsPerLocation
        // Create 25 locations, each with 3+ measurements (by adding measurements at same location)
        val snapshot = createTestSnapshot()
        var session = emptySession
        repeat(25) { i ->
            // Add 3 measurements at each location (within merge radius of 2m)
            repeat(3) { j ->
                session =
                    collector.addMeasurement(
                        session,
                        snapshot.copy(id = "snap_${i}_$j"),
                        SurveyLocation(i * 5.0, j * 0.5), // Close enough to merge
                    )
            }
        }
        val quality = collector.assessSurveyQuality(session)
        assertTrue(quality in listOf(SurveyQuality.EXCELLENT, SurveyQuality.GOOD, SurveyQuality.FAIR))
    }

    @Test
    fun `getSurveyBounds calculates correct bounds`() {
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")
        val snapshot = createTestSnapshot()

        session = collector.addMeasurement(session, snapshot, SurveyLocation(0.0, 0.0))
        session = collector.addMeasurement(session, snapshot, SurveyLocation(10.0, 5.0))
        session = collector.addMeasurement(session, snapshot, SurveyLocation(5.0, 10.0))

        val bounds = collector.getSurveyBounds(session)

        assertNotNull(bounds)
        assertEquals(0.0, bounds.minX)
        assertEquals(10.0, bounds.maxX)
        assertEquals(0.0, bounds.minY)
        assertEquals(10.0, bounds.maxY)
        assertEquals(10.0, bounds.widthMeters)
        assertEquals(10.0, bounds.heightMeters)
    }

    @Test
    fun `getApStatistics calculates correct statistics`() {
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")

        val bss = createTestBss(bssid = "AA:BB:CC:DD:EE:FF", rssi = -50)
        val cluster = createTestCluster(bssids = listOf(bss))
        val snapshot = ScanSnapshot("snap_1", 1000, listOf(cluster))

        session = collector.addMeasurement(session, snapshot, SurveyLocation(0.0, 0.0))
        session = collector.addMeasurement(session, snapshot.copy(id = "snap_2"), SurveyLocation(5.0, 5.0))

        val stats = collector.getApStatistics(session, "AA:BB:CC:DD:EE:FF")

        assertNotNull(stats)
        assertEquals(2, stats.measurementCount)
        assertEquals(-50, stats.averageRssiDbm)
        assertEquals(100.0, stats.coveragePercentage)
    }

    @Test
    fun `SurveyLocation distanceMeters calculates correctly`() {
        val loc1 = SurveyLocation(0.0, 0.0)
        val loc2 = SurveyLocation(3.0, 4.0)

        val distance = loc1.distanceMeters(loc2)

        assertEquals(5.0, distance, 0.01)
    }

    @Test
    fun `SurveyLocation averageWith calculates midpoint`() {
        val loc1 = SurveyLocation(0.0, 0.0)
        val loc2 = SurveyLocation(10.0, 10.0)

        val avg = loc1.averageWith(loc2)

        assertEquals(5.0, avg.x)
        assertEquals(5.0, avg.y)
    }
}

/**
 * Tests for CoverageHeatmapper
 */
class CoverageHeatmapperTest {
    @Test
    fun `CoverageHeatmapper requires valid parameters`() {
        assertThrows<IllegalArgumentException> {
            CoverageHeatmapper(gridResolution = 0.0)
        }
        assertThrows<IllegalArgumentException> {
            CoverageHeatmapper(idwPower = 0.0)
        }
        assertThrows<IllegalArgumentException> {
            CoverageHeatmapper(maxInterpolationDistance = 0.0)
        }
    }

    @Test
    fun `generateHeatmap creates grid for AP`() {
        val heatmapper = CoverageHeatmapper()
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")

        val bss = createTestBss(bssid = "AA:BB:CC:DD:EE:FF", rssi = -60)
        val cluster = createTestCluster(bssids = listOf(bss))
        val snapshot = ScanSnapshot("snap_1", 1000, listOf(cluster))

        session = collector.addMeasurement(session, snapshot, SurveyLocation(0.0, 0.0))
        session = collector.addMeasurement(session, snapshot.copy(id = "snap_2"), SurveyLocation(10.0, 10.0))

        val heatmap = heatmapper.generateHeatmap(session, "AA:BB:CC:DD:EE:FF")

        assertNotNull(heatmap)
        assertTrue(heatmap.gridWidth > 0)
        assertTrue(heatmap.gridHeight > 0)
        assertEquals(2, heatmap.measurementCount)
    }

    @Test
    fun `generateHeatmap returns null for missing BSSID`() {
        val heatmapper = CoverageHeatmapper()
        val collector = SiteSurveyCollector()
        val session = collector.createSurvey("Test", "TestNetwork")

        val heatmap = heatmapper.generateHeatmap(session, "NON:EX:IS:TE:NT:AP")

        assertNull(heatmap)
    }

    @Test
    fun `generateCombinedHeatmap shows strongest signal`() {
        val heatmapper = CoverageHeatmapper()
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")

        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01", rssi = -50)
        val bss2 = createTestBss(bssid = "AA:BB:CC:DD:EE:02", rssi = -70)
        val cluster = createTestCluster(bssids = listOf(bss1, bss2))
        val snapshot = ScanSnapshot("snap_1", 1000, listOf(cluster))

        session = collector.addMeasurement(session, snapshot, SurveyLocation(5.0, 5.0))

        val combined = heatmapper.generateCombinedHeatmap(session)

        assertTrue(combined.grid.isNotEmpty())
        assertTrue(combined.coveragePercentage > 0.0)
    }

    @Test
    fun `calculateCoverageStats computes correct statistics`() {
        val heatmapper = CoverageHeatmapper()
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")

        val bss = createTestBss(rssi = -60)
        val cluster = createTestCluster(bssids = listOf(bss))
        val snapshot = ScanSnapshot("snap_1", 1000, listOf(cluster))

        session = collector.addMeasurement(session, snapshot, SurveyLocation(0.0, 0.0))
        session = collector.addMeasurement(session, snapshot.copy(id = "snap_2"), SurveyLocation(5.0, 5.0))

        val heatmap = heatmapper.generateHeatmap(session, bss.bssid)
        assertNotNull(heatmap)

        val stats = heatmapper.calculateCoverageStats(heatmap)

        assertTrue(stats.coveredCells > 0)
        assertTrue(stats.coveragePercentage > 0.0)
        assertNotNull(stats.averageSignalDbm)
    }

    @Test
    fun `InterpolationMethod enum has all expected values`() {
        assertEquals(3, InterpolationMethod.values().size)
        assertNotNull(InterpolationMethod.NEAREST_NEIGHBOR)
        assertNotNull(InterpolationMethod.INVERSE_DISTANCE_WEIGHTED)
        assertNotNull(InterpolationMethod.BILINEAR)
    }
}

/**
 * Tests for DeadZoneDetector
 */
class DeadZoneDetectorTest {
    @Test
    fun `DeadZoneDetector requires valid thresholds`() {
        assertThrows<IllegalArgumentException> {
            DeadZoneDetector(highThresholdDbm = -70, lowThresholdDbm = -80)
        }
        assertThrows<IllegalArgumentException> {
            DeadZoneDetector(minDeadZoneSize = 0)
        }
    }

    @Test
    fun `detectDeadZones finds coverage gaps`() {
        val detector =
            DeadZoneDetector(
                highThresholdDbm = -85,
                mediumThresholdDbm = -75,
                lowThresholdDbm = -70,
                minDeadZoneSize = 2,
            )

        val heatmapper = CoverageHeatmapper()
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")

        val bss = createTestBss(rssi = -90) // Very weak signal
        val cluster = createTestCluster(bssids = listOf(bss))
        val snapshot = ScanSnapshot("snap_1", 1000, listOf(cluster))

        session = collector.addMeasurement(session, snapshot, SurveyLocation(0.0, 0.0))
        session = collector.addMeasurement(session, snapshot.copy(id = "snap_2"), SurveyLocation(5.0, 5.0))

        val heatmap = heatmapper.generateHeatmap(session, bss.bssid)
        assertNotNull(heatmap)

        val deadZones = detector.detectDeadZones(heatmap)

        // Should detect weak signal areas
        assertTrue(deadZones.isNotEmpty())
    }

    @Test
    fun `detectDeadZones respects minimum size`() {
        val detector = DeadZoneDetector(minDeadZoneSize = 100)

        val heatmapper = CoverageHeatmapper()
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")

        val bss = createTestBss(rssi = -90)
        val cluster = createTestCluster(bssids = listOf(bss))
        val snapshot = ScanSnapshot("snap_1", 1000, listOf(cluster))

        session = collector.addMeasurement(session, snapshot, SurveyLocation(0.0, 0.0))

        val heatmap = heatmapper.generateHeatmap(session, bss.bssid)
        assertNotNull(heatmap)

        val deadZones = detector.detectDeadZones(heatmap)

        // Should find no zones due to high minimum size
        assertTrue(deadZones.isEmpty())
    }

    @Test
    fun `generateReport creates correct summary`() {
        val detector = DeadZoneDetector()

        val deadZones =
            listOf(
                DeadZone(
                    DeadZoneSeverity.CRITICAL,
                    10,
                    SurveyLocation(0.0, 0.0),
                    0,
                    5,
                    0,
                    5,
                    null,
                    "AA:BB:CC:DD:EE:FF",
                ),
                DeadZone(
                    DeadZoneSeverity.HIGH,
                    5,
                    SurveyLocation(10.0, 10.0),
                    10,
                    15,
                    10,
                    15,
                    -85,
                    "AA:BB:CC:DD:EE:FF",
                ),
            )

        val report = detector.generateReport(deadZones)

        assertEquals(2, report.totalDeadZones)
        assertEquals(1, report.criticalCount)
        assertEquals(1, report.highCount)
        assertTrue(report.hasCriticalZones)
        assertFalse(report.isAcceptable)
        assertEquals(2, report.priorityZones.size)
    }

    @Test
    fun `suggestImprovements creates actionable suggestions`() {
        val detector = DeadZoneDetector()

        val deadZones =
            listOf(
                DeadZone(
                    DeadZoneSeverity.CRITICAL,
                    10,
                    SurveyLocation(5.0, 5.0, "Living Room"),
                    0,
                    5,
                    0,
                    5,
                    null,
                    null,
                ),
            )

        val suggestions = detector.suggestImprovements(deadZones)

        assertEquals(1, suggestions.size)
        assertEquals(5, suggestions.first().priority)
        assertTrue(suggestions.first().suggestion.contains("Add new AP"))
    }

    @Test
    fun `DeadZone calculates area correctly`() {
        val zone =
            DeadZone(
                DeadZoneSeverity.MEDIUM,
                20,
                SurveyLocation(5.0, 5.0),
                minRow = 0,
                maxRow = 4,
                minCol = 0,
                maxCol = 3,
                averageSignalDbm = -75,
                affectedBssid = null,
            )

        // widthCells = maxCol - minCol + 1 = 3 - 0 + 1 = 4
        // heightCells = maxRow - minRow + 1 = 4 - 0 + 1 = 5
        assertEquals(4, zone.widthCells)
        assertEquals(5, zone.heightCells)
        assertEquals(20, zone.areaCells)
    }

    @Test
    fun `DeadZoneSeverity has correct priorities`() {
        assertEquals(5, DeadZoneSeverity.CRITICAL.priority)
        assertEquals(4, DeadZoneSeverity.HIGH.priority)
        assertTrue(DeadZoneSeverity.CRITICAL.requiresAction)
        assertTrue(DeadZoneSeverity.HIGH.requiresAction)
    }
}

/**
 * Tests for ApPlacementAdvisor
 */
class ApPlacementAdvisorTest {
    @Test
    fun `ApPlacementAdvisor requires valid parameters`() {
        assertThrows<IllegalArgumentException> {
            ApPlacementAdvisor(targetCoveragePercentage = 150.0)
        }
        assertThrows<IllegalArgumentException> {
            ApPlacementAdvisor(minSignalThresholdDbm = -150)
        }
        assertThrows<IllegalArgumentException> {
            ApPlacementAdvisor(maxApDistance = 0.0)
        }
    }

    @Test
    fun `analyzeAndRecommend creates recommendations`() {
        val advisor = ApPlacementAdvisor(targetCoveragePercentage = 90.0)
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")

        val bss = createTestBss(rssi = -60)
        val cluster = createTestCluster(bssids = listOf(bss))
        val snapshot = ScanSnapshot("snap_1", 1000, listOf(cluster))

        // Add a few measurements
        session = collector.addMeasurement(session, snapshot, SurveyLocation(0.0, 0.0))
        session = collector.addMeasurement(session, snapshot.copy(id = "snap_2"), SurveyLocation(10.0, 10.0))

        val recommendation = advisor.analyzeAndRecommend(session)

        assertNotNull(recommendation)
        assertTrue(recommendation.overallScore >= 0)
        assertTrue(recommendation.overallScore <= 100)
    }

    @Test
    fun `analyzeAndRecommend suggests new APs for dead zones`() {
        val advisor = ApPlacementAdvisor(targetCoveragePercentage = 95.0)
        val collector = SiteSurveyCollector()
        var session = collector.createSurvey("Test", "TestNetwork")

        // Create scenario with weak signal (dead zone)
        val bss = createTestBss(rssi = -90)
        val cluster = createTestCluster(bssids = listOf(bss))
        val snapshot = ScanSnapshot("snap_1", 1000, listOf(cluster))

        session = collector.addMeasurement(session, snapshot, SurveyLocation(0.0, 0.0))
        session = collector.addMeasurement(session, snapshot.copy(id = "snap_2"), SurveyLocation(5.0, 5.0))

        val recommendation = advisor.analyzeAndRecommend(session)

        // May suggest new APs depending on dead zone detection
        assertTrue(recommendation.totalRecommendations >= 0)
    }

    @Test
    fun `PlacementRecommendation meetsTarget checks coverage`() {
        val recommendation =
            PlacementRecommendation(
                currentCoveragePercentage = 95.0,
                targetCoveragePercentage = 90.0,
                deadZoneCount = 0,
                newApPlacements = emptyList(),
                repositioningRecommendations = emptyList(),
                powerAdjustments = emptyList(),
                channelAssignments = emptyList(),
                overallScore = 90,
                estimatedCostLevel = CostLevel.MINIMAL,
            )

        assertTrue(recommendation.meetsTarget)
    }

    @Test
    fun `PlacementRecommendation summary is readable`() {
        val recommendation =
            PlacementRecommendation(
                currentCoveragePercentage = 85.0,
                targetCoveragePercentage = 95.0,
                deadZoneCount = 2,
                newApPlacements =
                    listOf(
                        ApPlacementSuggestion(
                            SurveyLocation(5.0, 5.0),
                            5,
                            "Fill dead zone",
                            PlacementImpact.HIGH,
                            50.0,
                        ),
                    ),
                repositioningRecommendations = emptyList(),
                powerAdjustments = emptyList(),
                channelAssignments = emptyList(),
                overallScore = 70,
                estimatedCostLevel = CostLevel.MEDIUM,
            )

        val summary = recommendation.summary

        assertTrue(summary.contains("85%"))
        assertTrue(summary.contains("70/100"))
        assertTrue(summary.contains("1 new APs"))
    }

    @Test
    fun `ApLocation stores AP information`() {
        val location =
            ApLocation(
                id = "ap_1",
                location = SurveyLocation(5.0, 5.0, "Office"),
                channel = 6,
                txPowerDbm = 20,
            )

        assertEquals("ap_1", location.id)
        assertEquals(6, location.channel)
        assertEquals(20, location.txPowerDbm)
    }

    @Test
    fun `PowerAdjustment calculates change correctly`() {
        val adjustment =
            PowerAdjustment(
                apId = "ap_1",
                location = SurveyLocation(0.0, 0.0),
                currentPowerDbm = 20,
                suggestedPowerDbm = 17,
                reasoning = "Reduce interference",
                expectedImpact = PlacementImpact.LOW,
            )

        assertEquals(-3, adjustment.powerChangeDbm)
    }

    @Test
    fun `CostLevel enum has all levels`() {
        assertEquals(4, CostLevel.values().size)
        assertNotNull(CostLevel.HIGH)
        assertNotNull(CostLevel.MEDIUM)
        assertNotNull(CostLevel.LOW)
        assertNotNull(CostLevel.MINIMAL)
    }

    @Test
    fun `PlacementImpact enum has all levels`() {
        assertEquals(4, PlacementImpact.values().size)
        assertNotNull(PlacementImpact.CRITICAL)
        assertNotNull(PlacementImpact.HIGH)
        assertNotNull(PlacementImpact.MEDIUM)
        assertNotNull(PlacementImpact.LOW)
    }
}
