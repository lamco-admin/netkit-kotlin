package io.lamco.netkit.topology.analysis

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CoverageAnalyzerTest {
    // ============================================
    // Coverage Overlap Tests
    // ============================================

    @Test
    fun `calculateCoverageOverlap returns empty map for single AP`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -60),
                ),
            )

        val overlaps = analyzer.calculateCoverageOverlap(cluster)

        assertTrue(overlaps.isEmpty())
    }

    @Test
    fun `calculateCoverageOverlap returns zero for different bands`() {
        val analyzer = CoverageAnalyzer()
        val bss24 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_2_4GHZ, rssi = -60)
        val bss5 = createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -60)
        val cluster = createClusterWithBssids(listOf(bss24, bss5))

        val overlaps = analyzer.calculateCoverageOverlap(cluster)

        assertEquals(1, overlaps.size)
        assertEquals(0.0, overlaps[Pair("AA:BB:CC:11:11:11", "AA:BB:CC:22:22:22")])
    }

    @Test
    fun `calculateCoverageOverlap returns high overlap for strong signals on same band`() {
        val analyzer = CoverageAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ, rssi = -55)
        val bss2 = createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -55)
        val cluster = createClusterWithBssids(listOf(bss1, bss2))

        val overlaps = analyzer.calculateCoverageOverlap(cluster)

        assertEquals(1, overlaps.size)
        val overlap = overlaps[Pair("AA:BB:CC:11:11:11", "AA:BB:CC:22:22:22")]!!
        assertTrue(overlap >= 0.5, "Strong signals should have significant overlap")
    }

    @Test
    fun `calculateCoverageOverlap returns medium overlap for moderate signals`() {
        val analyzer = CoverageAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ, rssi = -65)
        val bss2 = createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -68)
        val cluster = createClusterWithBssids(listOf(bss1, bss2))

        val overlaps = analyzer.calculateCoverageOverlap(cluster)

        val overlap = overlaps[Pair("AA:BB:CC:11:11:11", "AA:BB:CC:22:22:22")]!!
        assertTrue(overlap in 0.3..0.7, "Moderate signals should have medium overlap")
    }

    @Test
    fun `calculateCoverageOverlap returns low overlap for weak signals`() {
        val analyzer = CoverageAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ, rssi = -85)
        val bss2 = createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -88)
        val cluster = createClusterWithBssids(listOf(bss1, bss2))

        val overlaps = analyzer.calculateCoverageOverlap(cluster)

        val overlap = overlaps[Pair("AA:BB:CC:11:11:11", "AA:BB:CC:22:22:22")]!!
        assertTrue(overlap < 0.3, "Weak signals should have minimal overlap")
    }

    @Test
    fun `calculateCoverageOverlap handles three APs correctly`() {
        val analyzer = CoverageAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", rssi = -60)
        val bss2 = createBss("AA:BB:CC:22:22:22", rssi = -60)
        val bss3 = createBss("AA:BB:CC:33:33:33", rssi = -60)
        val cluster = createClusterWithBssids(listOf(bss1, bss2, bss3))

        val overlaps = analyzer.calculateCoverageOverlap(cluster)

        // Should have 3 pairs: (1,2), (1,3), (2,3)
        assertEquals(3, overlaps.size)
        assertTrue(overlaps.containsKey(Pair("AA:BB:CC:11:11:11", "AA:BB:CC:22:22:22")))
        assertTrue(overlaps.containsKey(Pair("AA:BB:CC:11:11:11", "AA:BB:CC:33:33:33")))
        assertTrue(overlaps.containsKey(Pair("AA:BB:CC:22:22:22", "AA:BB:CC:33:33:33")))
    }

    @Test
    fun `calculateCoverageOverlap handles null RSSI gracefully`() {
        val analyzer = CoverageAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", rssi = null)
        val bss2 = createBss("AA:BB:CC:22:22:22", rssi = null)
        val cluster = createClusterWithBssids(listOf(bss1, bss2))

        val overlaps = analyzer.calculateCoverageOverlap(cluster)

        assertNotNull(overlaps)
        assertEquals(1, overlaps.size)
    }

    // ============================================
    // Dead Zone Detection Tests
    // ============================================

    @Test
    fun `identifyDeadZones returns empty for strong signals`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -60),
                    createBss("AA:BB:CC:22:22:22", rssi = -55),
                ),
            )

        val deadZones = analyzer.identifyDeadZones(cluster)

        assertTrue(deadZones.isEmpty())
    }

    @Test
    fun `identifyDeadZones detects moderate dead zone for some weak APs`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -60),
                    createBss("AA:BB:CC:22:22:22", rssi = -85),
                ),
            )

        val deadZones = analyzer.identifyDeadZones(cluster, minRssi = -80)

        assertEquals(1, deadZones.size)
        assertEquals(DeadZoneSeverity.MODERATE, deadZones[0].severity)
        assertEquals(1, deadZones[0].affectedBssids.size)
        assertTrue(deadZones[0].affectedBssids.contains("AA:BB:CC:22:22:22"))
    }

    @Test
    fun `identifyDeadZones detects critical zone when all APs weak`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -85),
                    createBss("AA:BB:CC:22:22:22", rssi = -88),
                ),
            )

        val deadZones = analyzer.identifyDeadZones(cluster, minRssi = -80)

        assertEquals(1, deadZones.size)
        assertEquals(DeadZoneSeverity.CRITICAL, deadZones[0].severity)
        assertEquals(2, deadZones[0].affectedBssids.size)
    }

    @Test
    fun `identifyDeadZones uses custom threshold correctly`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -72),
                ),
            )

        // With strict threshold, should detect dead zone
        val deadZones1 = analyzer.identifyDeadZones(cluster, minRssi = -70)
        assertEquals(1, deadZones1.size)

        // With lenient threshold, should not detect dead zone
        val deadZones2 = analyzer.identifyDeadZones(cluster, minRssi = -75)
        assertEquals(0, deadZones2.size)
    }

    @Test
    fun `identifyDeadZones calculates average RSSI of weak APs`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -84),
                    createBss("AA:BB:CC:22:22:22", rssi = -86),
                ),
            )

        val deadZones = analyzer.identifyDeadZones(cluster, minRssi = -80)

        assertEquals(1, deadZones.size)
        assertEquals(-85, deadZones[0].estimatedRssi)
    }

    @Test
    fun `identifyDeadZones includes descriptive message`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -85),
                ),
            )

        val deadZones = analyzer.identifyDeadZones(cluster, minRssi = -80)

        assertEquals(1, deadZones.size)
        assertTrue(deadZones[0].description.isNotBlank())
        assertTrue(deadZones[0].description.contains("1"))
    }

    @Test
    fun `identifyDeadZones ignores null RSSI values`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = null),
                    createBss("AA:BB:CC:22:22:22", rssi = -60),
                ),
            )

        val deadZones = analyzer.identifyDeadZones(cluster, minRssi = -80)

        assertEquals(0, deadZones.size)
    }

    // ============================================
    // Coverage Quality Score Tests
    // ============================================

    @Test
    fun `calculateCoverageQuality scores single AP lower than multi-AP`() {
        val analyzer = CoverageAnalyzer()
        val singleAp =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -60),
                ),
            )
        val multiAp =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -60),
                    createBss("AA:BB:CC:22:22:22", rssi = -60),
                ),
            )

        val score1 = analyzer.calculateCoverageQuality(singleAp)
        val score2 = analyzer.calculateCoverageQuality(multiAp)

        assertTrue(score2.score > score1.score)
    }

    @Test
    fun `calculateCoverageQuality gives bonus for tri-band`() {
        val analyzer = CoverageAnalyzer()
        val triBand =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_2_4GHZ, rssi = -60),
                    createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -60),
                    createBss("AA:BB:CC:33:33:33", band = WiFiBand.BAND_6GHZ, rssi = -60),
                ),
            )
        val dualBand =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_2_4GHZ, rssi = -60),
                    createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -60),
                ),
            )

        val triScore = analyzer.calculateCoverageQuality(triBand)
        val dualScore = analyzer.calculateCoverageQuality(dualBand)

        assertTrue(triScore.score >= dualScore.score)
        assertEquals(3, triScore.bandDiversity)
        assertEquals(2, dualScore.bandDiversity)
    }

    @Test
    fun `calculateCoverageQuality rewards strong signal strength`() {
        val analyzer = CoverageAnalyzer()
        val strong =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -45),
                ),
            )
        val weak =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:22:22:22", rssi = -85),
                ),
            )

        val strongScore = analyzer.calculateCoverageQuality(strong)
        val weakScore = analyzer.calculateCoverageQuality(weak)

        assertTrue(strongScore.score > weakScore.score)
    }

    @Test
    fun `calculateCoverageQuality gives bonus for fast roaming`() {
        val analyzer = CoverageAnalyzer()
        val withRoaming = createBss("AA:BB:CC:11:11:11", roaming = true, rssi = -60)
        val withoutRoaming = createBss("AA:BB:CC:22:22:22", roaming = false, rssi = -60)

        val cluster1 = createClusterWithBssids(listOf(withRoaming, createBss("AA:BB:CC:33:33:33", roaming = true)))
        val cluster2 = createClusterWithBssids(listOf(withoutRoaming, createBss("AA:BB:CC:44:44:44", roaming = false)))

        val score1 = analyzer.calculateCoverageQuality(cluster1)
        val score2 = analyzer.calculateCoverageQuality(cluster2)

        assertTrue(score1.hasFastRoaming)
        assertFalse(score2.hasFastRoaming)
        assertTrue(score1.score > score2.score)
    }

    @Test
    fun `calculateCoverageQuality score is bounded 0-100`() {
        val analyzer = CoverageAnalyzer()

        // Best case: many APs, tri-band, strong signal, fast roaming
        val excellent =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_2_4GHZ, roaming = true, rssi = -40),
                    createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, roaming = true, rssi = -40),
                    createBss("AA:BB:CC:33:33:33", band = WiFiBand.BAND_6GHZ, roaming = true, rssi = -40),
                    createBss("AA:BB:CC:44:44:44", band = WiFiBand.BAND_5GHZ, roaming = true, rssi = -40),
                ),
            )

        // Worst case: single AP, single band, weak signal, no roaming
        val poor =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", roaming = false, rssi = -90),
                ),
            )

        val excellentScore = analyzer.calculateCoverageQuality(excellent)
        val poorScore = analyzer.calculateCoverageQuality(poor)

        assertTrue(excellentScore.score in 0..100)
        assertTrue(poorScore.score in 0..100)
    }

    @Test
    fun `calculateCoverageQuality handles null RSSI gracefully`() {
        val analyzer = CoverageAnalyzer()
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = null),
                ),
            )

        val score = analyzer.calculateCoverageQuality(cluster)

        assertNotNull(score)
        assertNull(score.averageRssi)
        assertTrue(score.score > 0)
    }

    @Test
    fun `CoverageQualityScore quality property maps correctly`() {
        val excellent = CoverageQualityScore(90, 4, 3, true, -50.0)
        val good = CoverageQualityScore(70, 3, 2, true, -60.0)
        val fair = CoverageQualityScore(50, 2, 2, false, -70.0)
        val poor = CoverageQualityScore(30, 1, 1, false, -85.0)

        assertEquals(CoverageQuality.EXCELLENT, excellent.quality)
        assertEquals(CoverageQuality.GOOD, good.quality)
        assertEquals(CoverageQuality.FAIR, fair.quality)
        assertEquals(CoverageQuality.POOR, poor.quality)
    }

    // ============================================
    // Coverage Radius Estimation Tests
    // ============================================

    @Test
    fun `estimateCoverageRadius returns larger radius for stronger signal`() {
        val analyzer = CoverageAnalyzer()
        val strong = createBss("AA:BB:CC:11:11:11", rssi = -45)
        val weak = createBss("AA:BB:CC:22:22:22", rssi = -85)

        val strongRadius = analyzer.estimateCoverageRadius(strong)
        val weakRadius = analyzer.estimateCoverageRadius(weak)

        assertTrue(strongRadius > weakRadius)
    }

    @Test
    fun `estimateCoverageRadius returns default for null RSSI`() {
        val analyzer = CoverageAnalyzer()
        val bss = createBss("AA:BB:CC:11:11:11", rssi = null)

        val radius = analyzer.estimateCoverageRadius(bss)

        assertEquals(50.0, radius)
    }

    @Test
    fun `estimateCoverageRadius uses RSSI thresholds correctly`() {
        val analyzer = CoverageAnalyzer()

        val excellent = createBss("AA:BB:CC:11:11:11", rssi = -45)
        val veryGood = createBss("AA:BB:CC:22:22:22", rssi = -55)
        val good = createBss("AA:BB:CC:33:33:33", rssi = -65)
        val fair = createBss("AA:BB:CC:44:44:44", rssi = -75)
        val poor = createBss("AA:BB:CC:55:55:55", rssi = -85)

        assertEquals(100.0, analyzer.estimateCoverageRadius(excellent))
        assertEquals(75.0, analyzer.estimateCoverageRadius(veryGood))
        assertEquals(50.0, analyzer.estimateCoverageRadius(good))
        assertEquals(25.0, analyzer.estimateCoverageRadius(fair))
        assertEquals(10.0, analyzer.estimateCoverageRadius(poor))
    }

    // ============================================
    // Constructor and Configuration Tests
    // ============================================

    @Test
    fun `CoverageAnalyzer uses default minRssiThreshold`() {
        val analyzer = CoverageAnalyzer()

        // Create cluster with RSSI at -79 (above default -80)
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -79),
                ),
            )

        val deadZones = analyzer.identifyDeadZones(cluster)

        // Should not detect dead zone with default threshold
        assertEquals(0, deadZones.size)
    }

    @Test
    fun `CoverageAnalyzer uses custom minRssiThreshold`() {
        val analyzer = CoverageAnalyzer(minRssiThreshold = -70)

        // Create cluster with RSSI at -75 (below custom -70)
        val cluster =
            createClusterWithBssids(
                listOf(
                    createBss("AA:BB:CC:11:11:11", rssi = -75),
                ),
            )

        val deadZones = analyzer.identifyDeadZones(cluster)

        // Should detect dead zone with custom threshold
        assertEquals(1, deadZones.size)
    }

    // ============================================
    // Helper Functions
    // ============================================

    private fun createBss(
        bssid: String,
        band: WiFiBand = WiFiBand.BAND_5GHZ,
        standard: WifiStandard = WifiStandard.WIFI_6,
        rssi: Int? = -65,
        roaming: Boolean = false,
    ) = ClusteredBss(
        bssid = bssid,
        band = band,
        channel = 36,
        frequencyMHz = 5180,
        channelWidth = ChannelWidth.WIDTH_80MHZ,
        wifiStandard = standard,
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
        ssid: String = "TestNetwork",
    ) = ApCluster(
        id = "test-cluster",
        ssid = ssid,
        securityFingerprint = SecurityFingerprint.wpa2Personal(),
        routerVendor = null,
        bssids = bssList,
    )
}
