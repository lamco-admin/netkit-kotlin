package io.lamco.netkit.topology.temporal

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Helper functions for creating test data
private fun createTestObservation(
    bssid: String = "AA:BB:CC:DD:EE:FF",
    timestampMillis: Long = 1000,
    rssiDbm: Int = -60,
) = SignalObservation(bssid, timestampMillis, rssiDbm)

private fun createTestHistory(
    bssid: String = "AA:BB:CC:DD:EE:FF",
    ssid: String = "TestNetwork",
    observations: List<SignalObservation> = listOf(createTestObservation(bssid = bssid)),
) = ApHistory(
    bssid = bssid,
    ssid = ssid,
    firstSeenTimestamp = observations.minOf { it.timestampMillis },
    lastSeenTimestamp = observations.maxOf { it.timestampMillis },
    observations = observations,
)

/**
 * Tests for TrendAnalyzer
 */
class TrendAnalyzerTest {
    @Test
    fun `TrendAnalyzer requires valid parameters`() {
        assertThrows<IllegalArgumentException> {
            TrendAnalyzer(minObservationsForTrend = 2)
        }
        assertThrows<IllegalArgumentException> {
            TrendAnalyzer(trendWindowMillis = 0)
        }
        assertThrows<IllegalArgumentException> {
            TrendAnalyzer(significantChangeThresholdDb = 0)
        }
    }

    @Test
    fun `analyzeApSignalTrend returns INSUFFICIENT_DATA for too few observations`() {
        val analyzer = TrendAnalyzer(minObservationsForTrend = 10)
        val obs = List(5) { i -> createTestObservation(timestampMillis = (i + 1) * 1000L) }
        val history = createTestHistory(observations = obs)

        val analysis = analyzer.analyzeApSignalTrend(history)

        assertEquals(SignalTrend.INSUFFICIENT_DATA, analysis.trend)
        assertEquals(TrendConfidence.LOW, analysis.confidence)
    }

    @Test
    fun `analyzeApSignalTrend detects improving signal`() {
        val analyzer = TrendAnalyzer(minObservationsForTrend = 5)
        val obs =
            listOf(
                createTestObservation(timestampMillis = 1000, rssiDbm = -70),
                createTestObservation(timestampMillis = 2000, rssiDbm = -65),
                createTestObservation(timestampMillis = 3000, rssiDbm = -60),
                createTestObservation(timestampMillis = 4000, rssiDbm = -55),
                createTestObservation(timestampMillis = 5000, rssiDbm = -50),
            )
        val history = createTestHistory(observations = obs)

        val analysis = analyzer.analyzeApSignalTrend(history, currentTimeMillis = 10_000)

        assertTrue(analysis.trend.isPositive)
        assertTrue(analysis.changeRateDbPerHour > 0)
    }

    @Test
    fun `analyzeApSignalTrend detects degrading signal`() {
        val analyzer = TrendAnalyzer(minObservationsForTrend = 5)
        val obs =
            listOf(
                createTestObservation(timestampMillis = 1000, rssiDbm = -50),
                createTestObservation(timestampMillis = 2000, rssiDbm = -55),
                createTestObservation(timestampMillis = 3000, rssiDbm = -60),
                createTestObservation(timestampMillis = 4000, rssiDbm = -65),
                createTestObservation(timestampMillis = 5000, rssiDbm = -70),
            )
        val history = createTestHistory(observations = obs)

        val analysis = analyzer.analyzeApSignalTrend(history, currentTimeMillis = 10_000)

        assertTrue(analysis.trend.indicatesProblem)
        assertTrue(analysis.changeRateDbPerHour < 0)
    }

    @Test
    fun `analyzeApSignalTrend detects stable signal`() {
        val analyzer = TrendAnalyzer(minObservationsForTrend = 5)
        val obs = List(10) { i -> createTestObservation(timestampMillis = (i + 1) * 1000L, rssiDbm = -60) }
        val history = createTestHistory(observations = obs)

        val analysis = analyzer.analyzeApSignalTrend(history, currentTimeMillis = 15_000)

        assertEquals(SignalTrend.STABLE, analysis.trend)
    }

    @Test
    fun `analyzeApSignalTrend calculates confidence correctly`() {
        val analyzer = TrendAnalyzer(minObservationsForTrend = 10)
        val obs = List(100) { i -> createTestObservation(timestampMillis = (i + 1) * 1000L, rssiDbm = -60) }
        val history = createTestHistory(observations = obs)

        val analysis = analyzer.analyzeApSignalTrend(history, currentTimeMillis = 150_000)

        assertEquals(TrendConfidence.HIGH, analysis.confidence)
        assertTrue(analysis.isReliable)
    }

    @Test
    fun `analyzeApSignalTrend identifies significant changes`() {
        val analyzer = TrendAnalyzer(minObservationsForTrend = 5, significantChangeThresholdDb = 5)
        val obs =
            listOf(
                createTestObservation(timestampMillis = 1000, rssiDbm = -70),
                createTestObservation(timestampMillis = 2000, rssiDbm = -65),
                createTestObservation(timestampMillis = 3000, rssiDbm = -60),
                createTestObservation(timestampMillis = 4000, rssiDbm = -55),
                createTestObservation(timestampMillis = 5000, rssiDbm = -50),
            )
        val history = createTestHistory(observations = obs)

        val analysis = analyzer.analyzeApSignalTrend(history, currentTimeMillis = 10_000)

        assertTrue(analysis.isSignificant)
    }

    @Test
    fun `ApSignalTrendAnalysis summary is readable`() {
        val analysis =
            ApSignalTrendAnalysis(
                bssid = "AA:BB:CC:DD:EE:FF",
                trend = SignalTrend.IMPROVING,
                changeRateDbPerHour = 5.5,
                confidence = TrendConfidence.HIGH,
                observations = 50,
                isSignificant = true,
            )

        val summary = analysis.summary

        assertTrue(summary.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(summary.contains("Improving"))
    }

    @Test
    fun `analyzeNetworkSignalTrend aggregates AP trends correctly`() {
        val analyzer = TrendAnalyzer(minObservationsForTrend = 3)

        val degradingObs =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 1000, rssiDbm = -50),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 2000, rssiDbm = -60),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 3000, rssiDbm = -70),
            )
        val improvingObs =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", timestampMillis = 1000, rssiDbm = -70),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", timestampMillis = 2000, rssiDbm = -60),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", timestampMillis = 3000, rssiDbm = -50),
            )

        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = degradingObs)
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = improvingObs)

        val networkTrend = NetworkTrend.create("TestNetwork", listOf(ap1, ap2))

        val analysis = analyzer.analyzeNetworkSignalTrend(networkTrend, currentTimeMillis = 5_000)

        assertEquals(1, analysis.degradingApCount)
        assertEquals(1, analysis.improvingApCount)
        assertEquals(2, analysis.totalApCount)
    }

    @Test
    fun `analyzeApChurn detects stable environment`() {
        val analyzer = TrendAnalyzer()
        val bss =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )
        val cluster =
            ApCluster(
                id = "cluster-test",
                ssid = "TestNetwork",
                securityFingerprint = SecurityFingerprint.wpa3Personal(),
                routerVendor = RouterVendor.UBIQUITI,
                bssids = listOf(bss),
            )

        val snapshots =
            List(5) { i ->
                ScanSnapshot(
                    id = "snap_$i",
                    timestampMillis = (i + 1) * 1000L,
                    clusters = listOf(cluster),
                )
            }

        val churnAnalysis = analyzer.analyzeApChurn(snapshots, currentTimeMillis = 10_000)

        assertEquals(EnvironmentStability.VERY_STABLE, churnAnalysis.stability)
        assertTrue(churnAnalysis.isStableEnvironment)
    }

    @Test
    fun `analyzeRoamingTrend detects excellent roaming quality`() {
        val analyzer = TrendAnalyzer()
        val roamingEvents =
            listOf(
                RoamingEvent(
                    timestampMillis = 1000,
                    fromBssid = "AA:BB:CC:DD:EE:01",
                    toBssid = "AA:BB:CC:DD:EE:02",
                    ssid = "TestNetwork",
                    durationMillis = 40,
                    rssiBeforeDbm = -75,
                    rssiAfterDbm = -60,
                    wasForcedDisconnect = false,
                    has11r = true,
                    has11k = true,
                    has11v = true,
                ),
            )

        val ap = createTestHistory().copy(roamingEvents = roamingEvents)
        val networkTrend = NetworkTrend.create("TestNetwork", listOf(ap))

        val roamingAnalysis = analyzer.analyzeRoamingTrend(networkTrend, currentTimeMillis = 5_000)

        assertTrue(roamingAnalysis.isHealthy)
        assertTrue(roamingAnalysis.seamlessPercentage >= 80.0)
    }
}

/**
 * Tests for AnomalyDetector
 */
class AnomalyDetectorTest {
    @Test
    fun `AnomalyDetector requires valid parameters`() {
        assertThrows<IllegalArgumentException> {
            AnomalyDetector(suddenDropThresholdDb = 0)
        }
        assertThrows<IllegalArgumentException> {
            AnomalyDetector(churnThresholdPercentage = 150.0)
        }
    }

    @Test
    fun `detectSnapshotAnomalies detects massive AP churn`() {
        val detector = AnomalyDetector(churnThresholdPercentage = 50.0)

        val bss1 =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:01",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )
        val bss2 =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:02",
                band = WiFiBand.BAND_5GHZ,
                channel = 40,
                frequencyMHz = 5200,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )

        val cluster1 =
            ApCluster(
                id = "cluster-1",
                ssid = "TestNetwork",
                securityFingerprint = SecurityFingerprint.wpa3Personal(),
                routerVendor = RouterVendor.UBIQUITI,
                bssids = listOf(bss1),
            )
        val cluster2 =
            ApCluster(
                id = "cluster-2",
                ssid = "TestNetwork",
                securityFingerprint = SecurityFingerprint.wpa3Personal(),
                routerVendor = RouterVendor.UBIQUITI,
                bssids = listOf(bss2),
            )

        val snapshot1 =
            ScanSnapshot(
                id = "snap_1",
                timestampMillis = 1000,
                clusters = listOf(cluster1),
            )
        val snapshot2 =
            ScanSnapshot(
                id = "snap_2",
                timestampMillis = 2000,
                clusters = listOf(cluster2),
            )

        val comparison = snapshot2.compareWith(snapshot1)
        val anomalies = detector.detectSnapshotAnomalies(comparison)

        assertTrue(anomalies.any { it.type == AnomalyType.MASSIVE_AP_CHURN })
    }

    @Test
    fun `detectSnapshotAnomalies detects sudden signal drop`() {
        val detector = AnomalyDetector(suddenDropThresholdDb = 15)

        val bss1 =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
                rssiDbm = -50,
            )
        val bss2 = bss1.copy(rssiDbm = -80)

        val cluster1 =
            ApCluster(
                id = "cluster-signal-test",
                ssid = "TestNetwork",
                securityFingerprint = SecurityFingerprint.wpa3Personal(),
                routerVendor = RouterVendor.UBIQUITI,
                bssids = listOf(bss1),
            )
        val cluster2 = cluster1.copy(bssids = listOf(bss2))

        val snapshot1 = ScanSnapshot(id = "snap_1", timestampMillis = 1000, clusters = listOf(cluster1))
        val snapshot2 = ScanSnapshot(id = "snap_2", timestampMillis = 2000, clusters = listOf(cluster2))

        val comparison = snapshot2.compareWith(snapshot1)
        val anomalies = detector.detectSnapshotAnomalies(comparison)

        assertTrue(anomalies.any { it.type == AnomalyType.SUDDEN_SIGNAL_DROP })
    }

    @Test
    fun `detectApHistoryAnomalies detects extreme variance`() {
        val detector = AnomalyDetector()

        val obs =
            listOf(
                createTestObservation(rssiDbm = -40),
                createTestObservation(rssiDbm = -90),
                createTestObservation(rssiDbm = -45),
                createTestObservation(rssiDbm = -85),
                createTestObservation(rssiDbm = -40),
                createTestObservation(rssiDbm = -90),
                createTestObservation(rssiDbm = -45),
                createTestObservation(rssiDbm = -85),
                createTestObservation(rssiDbm = -40),
                createTestObservation(rssiDbm = -90),
            )
        val history = createTestHistory(observations = obs)

        val anomalies = detector.detectApHistoryAnomalies(history)

        assertTrue(anomalies.any { it.type == AnomalyType.EXTREME_SIGNAL_VARIANCE })
    }

    @Test
    fun `detectRoamingAnomalies detects excessive latency`() {
        val detector = AnomalyDetector(roamingLatencyAnomalyMs = 3000)

        val roamingEvent =
            RoamingEvent(
                timestampMillis = 1000,
                fromBssid = "AA:BB:CC:DD:EE:01",
                toBssid = "AA:BB:CC:DD:EE:02",
                ssid = "TestNetwork",
                durationMillis = 5000,
                rssiBeforeDbm = -70,
                rssiAfterDbm = -65,
                wasForcedDisconnect = false,
                has11r = false,
                has11k = false,
                has11v = false,
            )

        val anomalies = detector.detectRoamingAnomalies(listOf(roamingEvent))

        assertTrue(anomalies.any { it.type == AnomalyType.EXCESSIVE_ROAMING_LATENCY })
    }

    @Test
    fun `detectRoamingPingPong detects rapid alternation`() {
        val detector = AnomalyDetector()

        val events =
            listOf(
                RoamingEvent(
                    1000,
                    "AA:BB:CC:DD:EE:01",
                    "AA:BB:CC:DD:EE:02",
                    "Test",
                    100,
                    -70,
                    -65,
                    false,
                    false,
                    false,
                    false,
                ),
                RoamingEvent(
                    2000,
                    "AA:BB:CC:DD:EE:02",
                    "AA:BB:CC:DD:EE:01",
                    "Test",
                    100,
                    -70,
                    -65,
                    false,
                    false,
                    false,
                    false,
                ),
                RoamingEvent(
                    3000,
                    "AA:BB:CC:DD:EE:01",
                    "AA:BB:CC:DD:EE:02",
                    "Test",
                    100,
                    -70,
                    -65,
                    false,
                    false,
                    false,
                    false,
                ),
            )

        val anomalies = detector.detectRoamingPingPong(events, windowMillis = 10_000, minOccurrences = 3)

        assertTrue(anomalies.any { it.type == AnomalyType.ROAMING_PING_PONG })
    }

    @Test
    fun `detectNetworkAnomalies detects network-wide degradation`() {
        val detector = AnomalyDetector()

        // Use recent timestamps so signalTrend() considers them within the window
        val now = System.currentTimeMillis()
        val degradingObs =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = now - 200_000, rssiDbm = -50),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = now - 100_000, rssiDbm = -60),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = now, rssiDbm = -70),
            )

        val aps =
            List(5) { i ->
                createTestHistory(bssid = "AA:BB:CC:DD:EE:0$i", observations = degradingObs)
            }

        val networkTrend = NetworkTrend.create("TestNetwork", aps)

        val anomalies = detector.detectNetworkAnomalies(networkTrend)

        assertTrue(anomalies.isNotEmpty())
    }

    @Test
    fun `AnomalyReport calculates counts correctly`() {
        val anomalies =
            listOf(
                Anomaly(AnomalyType.SUDDEN_SIGNAL_DROP, AnomalySeverity.CRITICAL, 1000, "Test", setOf("AA")),
                Anomaly(AnomalyType.EXCESSIVE_ROAMING_LATENCY, AnomalySeverity.HIGH, 2000, "Test", setOf("BB")),
                Anomaly(AnomalyType.INAPPROPRIATE_ROAMING, AnomalySeverity.MEDIUM, 3000, "Test", setOf("CC")),
                Anomaly(AnomalyType.SUDDEN_SIGNAL_SPIKE, AnomalySeverity.LOW, 4000, "Test", setOf("DD")),
            )

        val report =
            AnomalyReport(
                ssid = "TestNetwork",
                timestampMillis = 5000,
                anomalies = anomalies,
                criticalCount = 1,
                highCount = 1,
                mediumCount = 1,
                lowCount = 1,
            )

        assertEquals(4, report.totalCount)
        assertTrue(report.hasCriticalIssues)
        assertTrue(report.hasHighSeverityIssues)
        assertFalse(report.isHealthy)
        assertEquals(2, report.urgentAnomalies.size)
    }
}

/**
 * Tests for PerformancePredictor
 */
class PerformancePredictorTest {
    @Test
    fun `PerformancePredictor requires valid parameters`() {
        assertThrows<IllegalArgumentException> {
            PerformancePredictor(minHistoricalDataPoints = 2)
        }
        assertThrows<IllegalArgumentException> {
            PerformancePredictor(maxPredictionHorizonMillis = 0)
        }
        assertThrows<IllegalArgumentException> {
            PerformancePredictor(confidenceInterval = 1.5)
        }
    }

    @Test
    fun `predictApSignalStrength requires positive horizon`() {
        val predictor = PerformancePredictor()
        val history = createTestHistory()

        assertThrows<IllegalArgumentException> {
            predictor.predictApSignalStrength(history, horizonMillis = 0)
        }
    }

    @Test
    fun `predictApSignalStrength returns low confidence for insufficient data`() {
        val predictor = PerformancePredictor(minHistoricalDataPoints = 20)
        val obs = List(10) { i -> createTestObservation(timestampMillis = (i + 1) * 1000L) }
        val history = createTestHistory(observations = obs)

        val prediction = predictor.predictApSignalStrength(history, horizonMillis = 3600_000)

        assertEquals(PredictionConfidence.LOW, prediction.confidence)
    }

    @Test
    fun `predictApSignalStrength extrapolates improving trend`() {
        val predictor = PerformancePredictor(minHistoricalDataPoints = 10)
        val obs =
            List(50) { i ->
                createTestObservation(timestampMillis = (i + 1) * 10000L, rssiDbm = -70 + i)
            }
        val history = createTestHistory(observations = obs)

        val prediction = predictor.predictApSignalStrength(history, horizonMillis = 60000, currentTimeMillis = 600000)

        assertTrue(prediction.expectsImprovement)
    }

    @Test
    fun `predictApSignalStrength extrapolates degrading trend`() {
        val predictor = PerformancePredictor(minHistoricalDataPoints = 10)
        val obs =
            List(50) { i ->
                createTestObservation(timestampMillis = (i + 1) * 10000L, rssiDbm = -50 - i)
            }
        val history = createTestHistory(observations = obs)

        val prediction = predictor.predictApSignalStrength(history, horizonMillis = 60000, currentTimeMillis = 600000)

        assertTrue(prediction.expectsDegradation)
    }

    @Test
    fun `predictNetworkHealth forecasts improvement for improving network`() {
        val predictor = PerformancePredictor()

        val improvingObs =
            List(50) { i ->
                createTestObservation(
                    bssid = "AA:BB:CC:DD:EE:FF",
                    timestampMillis = (i + 1) * 10000L,
                    rssiDbm = -70 + i / 2,
                )
            }
        val history = createTestHistory(observations = improvingObs)
        val networkTrend = NetworkTrend.create("TestNetwork", listOf(history))

        val prediction = predictor.predictNetworkHealth(networkTrend, horizonMillis = 3600_000)

        assertTrue(prediction.expectsImprovement || prediction.predictedHealthScore >= prediction.currentHealthScore)
    }

    @Test
    fun `predictCoverageQuality estimates AP availability`() {
        val predictor = PerformancePredictor()

        val aps =
            List(5) { i ->
                val obs =
                    List(30) { j ->
                        createTestObservation(
                            bssid = "AA:BB:CC:DD:EE:0$i",
                            timestampMillis = (j + 1) * 10000L,
                            rssiDbm = -60,
                        )
                    }
                createTestHistory(bssid = "AA:BB:CC:DD:EE:0$i", observations = obs)
            }

        val networkTrend = NetworkTrend.create("TestNetwork", aps)

        // Last observation at 300000, use currentTime within 60s visibility window
        val prediction =
            predictor.predictCoverageQuality(
                networkTrend,
                horizonMillis = 3600_000,
                currentTimeMillis = 350000,
            )

        assertNotNull(prediction)
        assertTrue(prediction.currentApCount > 0)
    }

    @Test
    fun `recommendOptimalConnectionTime suggests immediate for good signal`() {
        val predictor = PerformancePredictor(minHistoricalDataPoints = 10)
        val obs =
            List(20) { i ->
                createTestObservation(timestampMillis = (i + 1) * 10000L, rssiDbm = -55)
            }
        val history = createTestHistory(observations = obs)

        val recommendation = predictor.recommendOptimalConnectionTime(history, lookAheadHours = 4)

        assertNotNull(recommendation)
        assertTrue(recommendation.currentSignalDbm >= -60)
    }

    @Test
    fun `predictConnectionIssues calculates risk correctly`() {
        val predictor = PerformancePredictor(minHistoricalDataPoints = 10)

        // Create unstable, poor signal history
        val obs =
            listOf(
                createTestObservation(timestampMillis = 1000, rssiDbm = -85),
                createTestObservation(timestampMillis = 2000, rssiDbm = -45),
                createTestObservation(timestampMillis = 3000, rssiDbm = -90),
                createTestObservation(timestampMillis = 4000, rssiDbm = -40),
                createTestObservation(timestampMillis = 5000, rssiDbm = -85),
                createTestObservation(timestampMillis = 6000, rssiDbm = -45),
                createTestObservation(timestampMillis = 7000, rssiDbm = -90),
                createTestObservation(timestampMillis = 8000, rssiDbm = -40),
                createTestObservation(timestampMillis = 9000, rssiDbm = -85),
                createTestObservation(timestampMillis = 10000, rssiDbm = -45),
            )
        val history = createTestHistory(observations = obs)

        val prediction = predictor.predictConnectionIssues(history, horizonMillis = 3600_000)

        assertTrue(prediction.issueProbability > 0.0)
        assertTrue(prediction.riskLevel != RiskLevel.MINIMAL)
    }

    @Test
    fun `SignalPrediction calculates expected change correctly`() {
        val prediction =
            SignalPrediction(
                bssid = "AA:BB:CC:DD:EE:FF",
                predictionTimeMillis = 5000,
                predictedRssiDbm = -50,
                lowerBoundRssiDbm = -55,
                upperBoundRssiDbm = -45,
                confidence = PredictionConfidence.HIGH,
                baselineRssiDbm = -60,
            )

        assertEquals(10, prediction.expectedChangeDbm)
        assertTrue(prediction.expectsImprovement)
        assertFalse(prediction.expectsDegradation)
    }

    @Test
    fun `NetworkHealthPrediction identifies improvement`() {
        val prediction =
            NetworkHealthPrediction(
                ssid = "TestNetwork",
                predictionTimeMillis = 5000,
                currentHealthScore = 60,
                predictedHealthScore = 80,
                currentHealth = NetworkHealth.FAIR,
                predictedHealth = NetworkHealth.GOOD,
                confidence = PredictionConfidence.HIGH,
                trend = NetworkTrendDirection.IMPROVING,
            )

        assertEquals(20, prediction.expectedChange)
        assertTrue(prediction.expectsImprovement)
    }
}
