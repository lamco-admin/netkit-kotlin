package io.lamco.netkit.topology.analysis

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class HandoffDetectorTest {
    @Test
    fun `HandoffDetector validates roaming threshold range`() {
        assertThrows<IllegalArgumentException> {
            HandoffDetector(roamingThreshold = -20) // Too high
        }
        assertThrows<IllegalArgumentException> {
            HandoffDetector(roamingThreshold = -105) // Too low
        }
    }

    @Test
    fun `HandoffDetector validates hysteresis`() {
        assertThrows<IllegalArgumentException> {
            HandoffDetector(hysteresis = -1)
        }
    }

    @Test
    fun `detectTransitionZones returns empty for single AP`() {
        val detector = HandoffDetector()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -60),
                ),
            )

        val zones = detector.detectTransitionZones(cluster)

        assertTrue(zones.isEmpty())
    }

    @Test
    fun `detectTransitionZones finds zones between same-band APs`() {
        val detector = HandoffDetector()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ, rssi = -60),
                    createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -65),
                ),
            )

        val zones = detector.detectTransitionZones(cluster)

        assertTrue(zones.isNotEmpty())
    }

    @Test
    fun `detectTransitionZones ignores different bands`() {
        val detector = HandoffDetector()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_2_4GHZ, rssi = -60),
                    createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -60),
                ),
            )

        val zones = detector.detectTransitionZones(cluster)

        assertTrue(zones.isEmpty())
    }

    @Test
    fun `predictHandoffLikelihood returns zero for strong signal`() {
        val detector = HandoffDetector(roamingThreshold = -70)
        val likelihood =
            detector.predictHandoffLikelihood(
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -60, // Strong
                targetBssid = "AA:BB:CC:22:22:22",
                targetRssi = -50,
            )

        assertEquals(0.0, likelihood)
    }

    @Test
    fun `predictHandoffLikelihood returns zero for insufficient differential`() {
        val detector = HandoffDetector(hysteresis = 10)
        val likelihood =
            detector.predictHandoffLikelihood(
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -75,
                targetBssid = "AA:BB:CC:22:22:22",
                targetRssi = -70, // Only 5 dB better, need 10
            )

        assertEquals(0.0, likelihood)
    }

    @Test
    fun `predictHandoffLikelihood increases with differential`() {
        val detector = HandoffDetector()
        val small =
            detector.predictHandoffLikelihood(
                "AP1",
                -75,
                "AP2",
                -68, // 7 dB improvement
            )
        val large =
            detector.predictHandoffLikelihood(
                "AP1",
                -75,
                "AP2",
                -50, // 25 dB improvement
            )

        assertTrue(large > small)
        assertEquals(1.0, large) // Very likely with 25 dB improvement
    }

    @Test
    fun `analyzeRoamingPatterns handles empty events`() {
        val detector = HandoffDetector()
        val analysis = detector.analyzeRoamingPatterns(emptyList())

        assertEquals(0, analysis.totalEvents)
        assertEquals(0, analysis.successfulRoams)
        assertEquals(0.0, analysis.successRate)
    }

    @Test
    fun `analyzeRoamingPatterns calculates statistics correctly`() {
        val detector = HandoffDetector()
        val events =
            listOf(
                createRoamingEvent("AP1", "AP2", successful = true, durationMs = 50),
                createRoamingEvent("AP2", "AP3", successful = true, durationMs = 100),
                createRoamingEvent("AP3", "AP1", successful = false, durationMs = 500),
            )

        val analysis = detector.analyzeRoamingPatterns(events)

        assertEquals(3, analysis.totalEvents)
        assertEquals(2, analysis.successfulRoams)
        assertEquals(1, analysis.failedRoams)
        assertTrue(analysis.successRate > 60.0)
    }

    @Test
    fun `analyzeRoamingPatterns identifies common transitions`() {
        val detector = HandoffDetector()
        val events =
            List(5) {
                createRoamingEvent("AP1", "AP2", successful = true)
            } +
                List(2) {
                    createRoamingEvent("AP2", "AP3", successful = true)
                }

        val analysis = detector.analyzeRoamingPatterns(events)

        assertTrue(analysis.commonTransitions.isNotEmpty())
        val topTransition = analysis.commonTransitions.first()
        assertEquals(5, topTransition.count) // AP1->AP2 happened 5 times
    }

    @Test
    fun `detectCurrentTransitionZone returns null for strong signal`() {
        val detector = HandoffDetector(roamingThreshold = -70)
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AP1", rssi = -60),
                    createBss("AP2", rssi = -55),
                ),
            )

        val zone = detector.detectCurrentTransitionZone(cluster, "AP1", -60)

        assertNull(zone)
    }

    @Test
    fun `detectCurrentTransitionZone returns null without better alternative`() {
        val detector = HandoffDetector()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AP1", rssi = -75),
                    createBss("AP2", rssi = -80),
                ),
            )

        val zone = detector.detectCurrentTransitionZone(cluster, "AP1", -75)

        assertNull(zone)
    }

    @Test
    fun `detectCurrentTransitionZone finds zone with better alternative`() {
        val detector = HandoffDetector()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AP1", rssi = -80),
                    createBss("AP2", rssi = -60), // Much better
                ),
            )

        val zone = detector.detectCurrentTransitionZone(cluster, "AP1", -80)

        assertNotNull(zone)
        assertEquals("AP1", zone.fromBssid)
        assertEquals("AP2", zone.toBssid)
    }

    @Test
    fun `recommendHandoffTiming provides critical urgency for very weak signal`() {
        val detector = HandoffDetector()
        val recommendation =
            detector.recommendHandoffTiming(
                currentRssi = -85,
                targetRssi = -60,
                fastRoamingSupported = true,
            )

        assertEquals(HandoffUrgency.CRITICAL, recommendation.urgency)
        assertTrue(recommendation.shouldHandoff)
    }

    @Test
    fun `recommendHandoffTiming considers hysteresis`() {
        val detector = HandoffDetector(hysteresis = 10)
        val recommendation =
            detector.recommendHandoffTiming(
                currentRssi = -76,
                targetRssi = -70, // Only 6 dB better, need 10
                fastRoamingSupported = false,
            )

        assertFalse(recommendation.shouldHandoff)
    }

    @Test
    fun `recommendHandoffTiming estimates latency based on fast roaming`() {
        val detector = HandoffDetector()

        val withFastRoaming = detector.recommendHandoffTiming(-85, -60, true)
        val withoutFastRoaming = detector.recommendHandoffTiming(-85, -60, false)

        assertTrue(withFastRoaming.estimatedLatencyMs < withoutFastRoaming.estimatedLatencyMs)
    }

    @Test
    fun `TransitionZone quality categorizes correctly`() {
        val excellent = TransitionZone("AP1", "AP2", WiFiBand.BAND_5GHZ, 0.8, 0.9, 50, -75)
        val good = TransitionZone("AP1", "AP2", WiFiBand.BAND_5GHZ, 0.6, 0.7, 100, -75)
        val fair = TransitionZone("AP1", "AP2", WiFiBand.BAND_5GHZ, 0.4, 0.5, 200, -75)
        val poor = TransitionZone("AP1", "AP2", WiFiBand.BAND_5GHZ, 0.2, 0.3, 300, -75)

        assertEquals(TransitionQuality.EXCELLENT, excellent.quality)
        assertEquals(TransitionQuality.GOOD, good.quality)
        assertEquals(TransitionQuality.FAIR, fair.quality)
        assertEquals(TransitionQuality.POOR, poor.quality)
    }

    @Test
    fun `RoamingPatternAnalysis calculates success rate correctly`() {
        val analysis =
            RoamingPatternAnalysis(
                totalEvents = 100,
                successfulRoams = 95,
                failedRoams = 5,
                averageDurationMs = 50,
                averageRssiImprovement = 15,
                commonTransitions = emptyList(),
                peakRoamingHours = emptyList(),
            )

        assertEquals(95.0, analysis.successRate)
        assertEquals(RoamingQuality.EXCELLENT, analysis.overallQuality)
    }

    @Test
    fun `HandoffRecommendation isHighlyRecommended requires multiple criteria`() {
        val highlyRecommended =
            HandoffRecommendation(
                shouldHandoff = true,
                urgency = HandoffUrgency.HIGH,
                reason = "Strong differential",
                estimatedLatencyMs = 50,
                confidenceScore = 0.8,
            )

        val notRecommended =
            HandoffRecommendation(
                shouldHandoff = true,
                urgency = HandoffUrgency.LOW, // Not high enough
                reason = "Weak differential",
                estimatedLatencyMs = 300,
                confidenceScore = 0.5,
            )

        assertTrue(highlyRecommended.isHighlyRecommended)
        assertFalse(notRecommended.isHighlyRecommended)
    }

    // Helper functions
    private fun createBss(
        bssid: String,
        band: WiFiBand = WiFiBand.BAND_5GHZ,
        rssi: Int = -65,
    ) = ClusteredBss(
        bssid = bssid,
        band = band,
        channel = 36,
        frequencyMHz = 5180,
        channelWidth = ChannelWidth.WIDTH_80MHZ,
        wifiStandard = WifiStandard.WIFI_6,
        rssiDbm = rssi,
        roamingCapabilities = RoamingCapabilities.none(),
    )

    private fun createClusterWithBssids(bssList: List<ClusteredBss>) =
        ApCluster(
            id = "test-cluster",
            ssid = "TestNetwork",
            securityFingerprint = SecurityFingerprint.wpa2Personal(),
            routerVendor = null,
            bssids = bssList,
        )

    private fun createRoamingEvent(
        from: String,
        to: String,
        successful: Boolean = true,
        durationMs: Long = 100,
    ) = RoamingEvent(
        timestampMillis = System.currentTimeMillis(),
        fromBssid = from,
        toBssid = to,
        ssid = "TestNetwork",
        durationMillis = durationMs,
        rssiBeforeDbm = -75,
        rssiAfterDbm = -60,
        wasForcedDisconnect = !successful,
        has11r = false,
        has11k = false,
        has11v = false,
    )
}
