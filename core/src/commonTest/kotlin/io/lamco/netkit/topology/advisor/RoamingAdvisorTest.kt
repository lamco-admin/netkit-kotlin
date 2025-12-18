package io.lamco.netkit.topology.advisor

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoamingAdvisorTest {
    @Test
    fun `advise provides recommendation for strong signal`() {
        val advisor = RoamingAdvisor()
        val cluster = createTestCluster()

        val advice =
            advisor.advise(
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -55, // Strong signal
                cluster = cluster,
            )

        assertEquals(RecommendationType.STAY_CONNECTED, advice.recommendation)
        assertFalse(advice.currentSituation.isSticky)
    }

    @Test
    fun `advise recommends roaming for weak signal with better alternative`() {
        val advisor = RoamingAdvisor()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -85), // Current - weak
                    createBss("AA:BB:CC:22:22:22", rssi = -60), // Target - strong
                ),
            )

        val advice =
            advisor.advise(
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -85,
                cluster = cluster,
            )

        assertTrue(
            advice.recommendation in
                listOf(
                    RecommendationType.ROAM_IMMEDIATELY,
                    RecommendationType.ROAM_SOON,
                ),
        )
        assertNotNull(advice.bestCandidate)
    }

    @Test
    fun `advise detects sticky client behavior`() {
        val advisor = RoamingAdvisor()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -80), // Current - weak
                    createBss("AA:BB:CC:22:22:22", rssi = -55), // Much better available
                ),
            )

        val advice =
            advisor.advise(
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -80,
                cluster = cluster,
            )

        assertTrue(advice.currentSituation.isSticky)
        assertNotNull(advice.stickyMetrics)
    }

    @Test
    fun `advise provides suggestions`() {
        val advisor = RoamingAdvisor()
        val cluster = createTestCluster()

        val advice =
            advisor.advise(
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -75,
                cluster = cluster,
            )

        assertTrue(advice.suggestions.isNotEmpty())
    }

    @Test
    fun `advise calculates overall score`() {
        val advisor = RoamingAdvisor()
        val cluster = createTestCluster()

        val advice =
            advisor.advise(
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -60,
                cluster = cluster,
            )

        assertTrue(advice.overallScore in 0..100)
    }

    @Test
    fun `RoamingAdvice priority matches recommendation urgency`() {
        val critical =
            RoamingAdvice(
                recommendation = RecommendationType.ROAM_IMMEDIATELY,
                currentSituation = CurrentSituation("AP1", -85, true, true),
                bestCandidate = null,
                transitionZone = null,
                stickyMetrics = null,
                suggestions = emptyList(),
                overallScore = 20,
            )

        val low =
            RoamingAdvice(
                recommendation = RecommendationType.STAY_CONNECTED,
                currentSituation = CurrentSituation("AP1", -55, false, false),
                bestCandidate = null,
                transitionZone = null,
                stickyMetrics = null,
                suggestions = emptyList(),
                overallScore = 90,
            )

        assertEquals(RecommendationPriority.CRITICAL, critical.priority)
        assertEquals(RecommendationPriority.LOW, low.priority)
    }

    @Test
    fun `analyzeNetworkOptimization evaluates single AP deployment`() {
        val advisor = RoamingAdvisor()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11"),
                ),
            )

        val optimization = advisor.analyzeNetworkOptimization(cluster)

        assertTrue(optimization.issues.any { it.contains("Single AP") })
        assertTrue(optimization.recommendations.isNotEmpty())
        assertEquals(1, optimization.apCount)
    }

    @Test
    fun `analyzeNetworkOptimization recommends fast roaming`() {
        val advisor = RoamingAdvisor()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AP1", roaming = false),
                    createBss("AP2", roaming = false),
                ),
            )

        val optimization = advisor.analyzeNetworkOptimization(cluster)

        assertTrue(optimization.issues.any { it.contains("fast roaming") })
        assertTrue(optimization.recommendations.any { it.contains("802.11") })
        assertFalse(optimization.supportsFastRoaming)
    }

    @Test
    fun `analyzeNetworkOptimization grades deployment quality`() {
        val advisor = RoamingAdvisor()

        val excellent =
            createClusterWithBssids(
                List(5) { createBss("AP$it", roaming = true) },
                vendor = RouterVendor.CISCO,
            )

        val poor =
            createClusterWithBssids(
                listOf(
                    createBss("AP1", roaming = false, band = WiFiBand.BAND_2_4GHZ),
                ),
            )

        val excellentOpt = advisor.analyzeNetworkOptimization(excellent)
        val poorOpt = advisor.analyzeNetworkOptimization(poor)

        // Enum ordinals: EXCELLENT=0, GOOD=1, FAIR=2, POOR=3
        // Lower ordinal = better quality, so use <= for "better than"
        assertTrue(excellentOpt.deploymentQuality <= poorOpt.deploymentQuality)
    }

    @Test
    fun `evaluateRoamingPerformance handles empty events`() {
        val advisor = RoamingAdvisor()
        val evaluation = advisor.evaluateRoamingPerformance(emptyList())

        assertEquals(0, evaluation.totalEvents)
        assertEquals(PerformanceGrade.UNKNOWN, evaluation.grade)
        assertTrue(evaluation.improvementSuggestions.isNotEmpty())
    }

    @Test
    fun `evaluateRoamingPerformance grades performance correctly`() {
        val advisor = RoamingAdvisor()
        val excellentEvents =
            List(100) {
                createRoamingEvent(successful = true, durationMs = 50)
            }
        val poorEvents =
            List(100) {
                createRoamingEvent(successful = it % 2 == 0, durationMs = 1000)
            }

        val excellentEval = advisor.evaluateRoamingPerformance(excellentEvents)
        val poorEval = advisor.evaluateRoamingPerformance(poorEvents)

        assertTrue(excellentEval.grade < poorEval.grade) // A < F
        assertTrue(excellentEval.successRate > poorEval.successRate)
    }

    @Test
    fun `evaluateRoamingPerformance identifies strengths`() {
        val advisor = RoamingAdvisor()
        val events =
            List(100) {
                createRoamingEvent(successful = true, durationMs = 50, fastRoaming = true)
            }

        val evaluation = advisor.evaluateRoamingPerformance(events)

        assertTrue(evaluation.strengths.isNotEmpty())
        assertTrue(evaluation.weaknesses.isEmpty() || evaluation.weaknesses.size < evaluation.strengths.size)
    }

    @Test
    fun `evaluateRoamingPerformance provides improvement suggestions`() {
        val advisor = RoamingAdvisor()
        val events =
            List(100) {
                createRoamingEvent(successful = it % 3 != 0, durationMs = 500, fastRoaming = false)
            }

        val evaluation = advisor.evaluateRoamingPerformance(events)

        assertTrue(evaluation.improvementSuggestions.isNotEmpty())
        assertTrue(evaluation.improvementSuggestions.any { it.contains("802.11") || it.contains("fast roaming") })
    }

    @Test
    fun `PerformanceGrade ordering is correct`() {
        assertTrue(PerformanceGrade.A < PerformanceGrade.B)
        assertTrue(PerformanceGrade.B < PerformanceGrade.C)
        assertTrue(PerformanceGrade.C < PerformanceGrade.D)
        assertTrue(PerformanceGrade.D < PerformanceGrade.F)
    }

    // Helper functions
    private fun createBss(
        bssid: String,
        rssi: Int = -65,
        band: WiFiBand = WiFiBand.BAND_5GHZ,
        roaming: Boolean = false,
    ) = ClusteredBss(
        bssid = bssid,
        band = band,
        channel = 36,
        frequencyMHz = 5180,
        channelWidth = ChannelWidth.WIDTH_80MHZ,
        wifiStandard = WifiStandard.WIFI_6,
        rssiDbm = rssi,
        roamingCapabilities =
            if (roaming) {
                RoamingCapabilities.fullSuite(AuthType.WPA2_PSK)
            } else {
                RoamingCapabilities.none()
            },
    )

    private fun createClusterWithBssids(
        bssList: List<ClusteredBss>,
        vendor: RouterVendor? = null,
    ) = ApCluster(
        id = "test-cluster",
        ssid = "TestNetwork",
        securityFingerprint = SecurityFingerprint.wpa2Personal(),
        routerVendor = vendor,
        bssids = bssList,
    )

    private fun createTestCluster() =
        createClusterWithBssids(
            listOf(
                createBss("AA:BB:CC:11:11:11", rssi = -65),
                createBss("AA:BB:CC:22:22:22", rssi = -70),
                createBss("AA:BB:CC:33:33:33", rssi = -60),
            ),
        )

    private fun createRoamingEvent(
        successful: Boolean = true,
        durationMs: Long = 100,
        fastRoaming: Boolean = false,
    ) = RoamingEvent(
        timestampMillis = System.currentTimeMillis(),
        fromBssid = "AP1",
        toBssid = "AP2",
        ssid = "TestNetwork",
        durationMillis = durationMs,
        rssiBeforeDbm = -75,
        rssiAfterDbm = -60,
        wasForcedDisconnect = !successful,
        has11r = fastRoaming,
        has11k = false,
        has11v = false,
    )
}
