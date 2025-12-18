package io.lamco.netkit.topology.scoring

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoamingScorerTest {
    @Test
    fun `scoreRoamingCandidates excludes current BSSID`() {
        val scorer = RoamingScorer()
        val cluster = createTestCluster()
        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
                currentRssi = -70,
            )

        assertTrue(candidates.none { it.bssid == "AA:BB:CC:11:11:11" })
    }

    @Test
    fun `scoreRoamingCandidates sorts by score descending`() {
        val scorer = RoamingScorer()
        val cluster = createTestCluster()
        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
                currentRssi = -70,
            )

        for (i in 0 until candidates.size - 1) {
            assertTrue(candidates[i].score >= candidates[i + 1].score)
        }
    }

    @Test
    fun `scoreRoamingCandidates prefers stronger signal`() {
        val scorer = RoamingScorer()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val strong = createBss("AA:BB:CC:22:22:22", rssi = -50)
        val cluster = createClusterWithBssids(listOf(weak, strong))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
                currentRssi = -80,
            )

        assertEquals("AA:BB:CC:22:22:22", candidates.first().bssid)
    }

    @Test
    fun `scoreRoamingCandidates gives bonus for fast roaming`() {
        val scorer = RoamingScorer()
        val withRoaming =
            createBss(
                "AA:BB:CC:11:11:11",
                rssi = -65,
                roaming = true,
            )
        val withoutRoaming =
            createBss(
                "AA:BB:CC:22:22:22",
                rssi = -65,
                roaming = false,
            )
        val current = createBss("AA:BB:CC:33:33:33", rssi = -70)
        val cluster = createClusterWithBssids(listOf(current, withRoaming, withoutRoaming))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:33:33:33",
                cluster = cluster,
                currentRssi = -70,
            )

        val roamingCandidate = candidates.find { it.bssid == "AA:BB:CC:11:11:11" }!!
        val normalCandidate = candidates.find { it.bssid == "AA:BB:CC:22:22:22" }!!

        assertTrue(roamingCandidate.score > normalCandidate.score)
    }

    @Test
    fun `scoreRoamingCandidates prefers 5 GHz over 2-4 GHz`() {
        val scorer = RoamingScorer()
        val bss24 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_2_4GHZ, rssi = -65)
        val bss5 = createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ, rssi = -65)
        val current = createBss("AA:BB:CC:33:33:33", rssi = -70)
        val cluster = createClusterWithBssids(listOf(current, bss24, bss5))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:33:33:33",
                cluster = cluster,
                currentRssi = -70,
            )

        val candidate24 = candidates.find { it.bssid == "AA:BB:CC:11:11:11" }!!
        val candidate5 = candidates.find { it.bssid == "AA:BB:CC:22:22:22" }!!

        assertTrue(candidate5.score > candidate24.score)
    }

    @Test
    fun `scoreRoamingCandidates prefers 6 GHz over 5 GHz`() {
        val scorer = RoamingScorer()
        val bss5 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ, rssi = -65)
        val bss6 = createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_6GHZ, rssi = -65)
        val current = createBss("AA:BB:CC:33:33:33", rssi = -70)
        val cluster = createClusterWithBssids(listOf(current, bss5, bss6))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:33:33:33",
                cluster = cluster,
                currentRssi = -70,
            )

        val candidate5 = candidates.find { it.bssid == "AA:BB:CC:11:11:11" }!!
        val candidate6 = candidates.find { it.bssid == "AA:BB:CC:22:22:22" }!!

        assertTrue(candidate6.score > candidate5.score)
    }

    @Test
    fun `scoreRoamingCandidates gives bonus for newer WiFi standards`() {
        val scorer = RoamingScorer()
        val wifi5 = createBss("AA:BB:CC:11:11:11", standard = WifiStandard.WIFI_5, rssi = -65)
        val wifi6 = createBss("AA:BB:CC:22:22:22", standard = WifiStandard.WIFI_6, rssi = -65)
        val wifi7 = createBss("AA:BB:CC:33:33:33", standard = WifiStandard.WIFI_7, rssi = -65)
        val current = createBss("AA:BB:CC:44:44:44", rssi = -70)
        val cluster = createClusterWithBssids(listOf(current, wifi5, wifi6, wifi7))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:44:44:44",
                cluster = cluster,
                currentRssi = -70,
            )

        val candidate5 = candidates.find { it.bssid == "AA:BB:CC:11:11:11" }!!
        val candidate6 = candidates.find { it.bssid == "AA:BB:CC:22:22:22" }!!
        val candidate7 = candidates.find { it.bssid == "AA:BB:CC:33:33:33" }!!

        assertTrue(candidate7.score >= candidate6.score)
        assertTrue(candidate6.score >= candidate5.score)
    }

    @Test
    fun `scoreRoamingCandidates penalizes weak target signal`() {
        val scorer = RoamingScorer()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -85)
        val acceptable = createBss("AA:BB:CC:22:22:22", rssi = -65)
        val current = createBss("AA:BB:CC:33:33:33", rssi = -90)
        val cluster = createClusterWithBssids(listOf(current, weak, acceptable))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:33:33:33",
                cluster = cluster,
                currentRssi = -90,
            )

        val weakCandidate = candidates.find { it.bssid == "AA:BB:CC:11:11:11" }!!
        val acceptableCandidate = candidates.find { it.bssid == "AA:BB:CC:22:22:22" }!!

        assertTrue(acceptableCandidate.score > weakCandidate.score)
    }

    @Test
    fun `scoreRoamingCandidates scores are bounded 0-100`() {
        val scorer = RoamingScorer()
        val cluster = createTestCluster()
        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
                currentRssi = -70,
            )

        candidates.forEach { candidate ->
            assertTrue(candidate.score in 0.0..100.0)
        }
    }

    @Test
    fun `find BestCandidate returns highest scored candidate`() {
        val scorer = RoamingScorer()
        val cluster = createTestCluster()
        val best =
            scorer.findBestCandidate(
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
                currentRssi = -70,
            )

        assertNotNull(best)
    }

    @Test
    fun `findBestCandidate returns null for single-AP cluster`() {
        val scorer = RoamingScorer()
        val single = createBss("AA:BB:CC:11:11:11")
        val cluster = createClusterWithBssids(listOf(single))

        val best =
            scorer.findBestCandidate(
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
                currentRssi = -70,
            )

        assertNull(best)
    }

    @Test
    fun `RoamingCandidate has expected properties`() {
        val scorer = RoamingScorer()
        val cluster = createTestCluster()
        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
                currentRssi = -70,
            )

        val candidate = candidates.first()
        assertNotNull(candidate.bssid)
        assertNotNull(candidate.band)
        assertTrue(candidate.channel > 0)
        assertNotNull(candidate.reason)
    }

    @Test
    fun `RoamingCandidate calculates RSSI improvement correctly`() {
        val scorer = RoamingScorer()
        val better = createBss("AA:BB:CC:11:11:11", rssi = -60)
        val current = createBss("AA:BB:CC:22:22:22", rssi = -75)
        val cluster = createClusterWithBssids(listOf(current, better))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:22:22:22",
                cluster = cluster,
                currentRssi = -75,
            )

        assertEquals(15, candidates.first().rssiImprovement)
    }

    @Test
    fun `RoamingCandidate reason includes signal improvement`() {
        val scorer = RoamingScorer()
        val better = createBss("AA:BB:CC:11:11:11", rssi = -50)
        val current = createBss("AA:BB:CC:22:22:22", rssi = -70)
        val cluster = createClusterWithBssids(listOf(current, better))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:22:22:22",
                cluster = cluster,
                currentRssi = -70,
            )

        val reason = candidates.first().reason
        assertTrue(reason.contains("better signal") || reason.contains("Much better signal"))
    }

    @Test
    fun `RoamingCandidate reason mentions fast roaming when supported`() {
        val scorer = RoamingScorer()
        val withRoaming = createBss("AA:BB:CC:11:11:11", roaming = true, rssi = -65)
        val current = createBss("AA:BB:CC:22:22:22", rssi = -70)
        val cluster = createClusterWithBssids(listOf(current, withRoaming))

        val candidates =
            scorer.scoreRoamingCandidates(
                currentBssid = "AA:BB:CC:22:22:22",
                cluster = cluster,
                currentRssi = -70,
            )

        val reason = candidates.first().reason
        assertTrue(reason.contains("fast roaming"))
    }

    // Helper functions
    private fun createTestCluster(): ApCluster {
        val bss1 = createBss("AA:BB:CC:11:11:11", rssi = -75)
        val bss2 = createBss("AA:BB:CC:22:22:22", rssi = -65)
        val bss3 = createBss("AA:BB:CC:33:33:33", rssi = -60)
        return createClusterWithBssids(listOf(bss1, bss2, bss3))
    }

    private fun createBss(
        bssid: String,
        band: WiFiBand = WiFiBand.BAND_5GHZ,
        standard: WifiStandard = WifiStandard.WIFI_6,
        rssi: Int = -65,
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

    private fun createClusterWithBssids(bssList: List<ClusteredBss>) =
        ApCluster(
            id = "test-cluster",
            ssid = "TestNetwork",
            securityFingerprint = SecurityFingerprint.wpa2Personal(),
            routerVendor = null,
            bssids = bssList,
        )
}
