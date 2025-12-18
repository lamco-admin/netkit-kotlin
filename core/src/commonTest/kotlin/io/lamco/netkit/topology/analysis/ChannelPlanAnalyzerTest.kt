package io.lamco.netkit.topology.analysis

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class ChannelPlanAnalyzerTest {
    // ============================================
    // Single Cluster Analysis Tests
    // ============================================

    @Test
    fun `analyzeChannelPlan returns report for single AP`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        assertEquals(cluster.ssid, report.ssid)
        assertEquals(1, report.clusters.size)
        assertNotNull(report.channelPlanScore)
    }

    @Test
    fun `analyzeChannelPlan detects no issues for optimal 2-4 GHz plan`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(6, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(11, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        val overlappingIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.OVERLAPPING_CHANNELS
            }
        assertTrue(overlappingIssues.isEmpty())
    }

    @Test
    fun `analyzeChannelPlan detects overlapping 2-4 GHz channels`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(3, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ), // Overlaps with channel 1
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        val overlappingIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.OVERLAPPING_CHANNELS
            }
        assertEquals(1, overlappingIssues.size)
        assertEquals(IssueSeverity.HIGH, overlappingIssues[0].severity)
        assertTrue(overlappingIssues[0].recommendation.contains("1, 6"))
    }

    @Test
    fun `analyzeChannelPlan detects wide channels in 2-4 GHz`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        val wideChannelIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.WIDE_CHANNEL_24GHZ
            }
        assertEquals(1, wideChannelIssues.size)
        assertEquals(IssueSeverity.MEDIUM, wideChannelIssues[0].severity)
        assertTrue(wideChannelIssues[0].recommendation.contains("20 MHz"))
    }

    @Test
    fun `analyzeChannelPlan allows wide channels in 5 GHz`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(36, WiFiBand.BAND_5GHZ, ChannelWidth.WIDTH_80MHZ),
                    Triple(149, WiFiBand.BAND_5GHZ, ChannelWidth.WIDTH_160MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        val wideChannelIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.WIDE_CHANNEL_24GHZ
            }
        assertEquals(0, wideChannelIssues.size)
    }

    @Test
    fun `analyzeChannelPlan detects channel reuse`() {
        val analyzer = ChannelPlanAnalyzer()
        // Create cluster with multiple APs on same channel
        val bss1 = createBss("AA:BB:CC:11:11:11", channel = 36, band = WiFiBand.BAND_5GHZ)
        val bss2 = createBss("AA:BB:CC:22:22:22", channel = 36, band = WiFiBand.BAND_5GHZ)
        val cluster = createClusterWithBssids(listOf(bss1, bss2))

        val report = analyzer.analyzeChannelPlan(cluster)

        val reuseIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.CHANNEL_REUSE
            }
        assertEquals(1, reuseIssues.size)
        assertEquals(IssueSeverity.LOW, reuseIssues[0].severity)
        assertEquals(2, reuseIssues[0].affectedBssids.size)
    }

    @Test
    fun `analyzeChannelPlan builds channel usage map correctly`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(36, WiFiBand.BAND_5GHZ, ChannelWidth.WIDTH_80MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        assertEquals(2, report.perBandChannelUsage.size)
        assertTrue(report.perBandChannelUsage.any { it.band == WiFiBand.BAND_2_4GHZ })
        assertTrue(report.perBandChannelUsage.any { it.band == WiFiBand.BAND_5GHZ })
    }

    @Test
    fun `analyzeChannelPlan calculates high score for optimal plan`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(6, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(11, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        assertTrue(report.channelPlanScore >= 0.8)
    }

    @Test
    fun `analyzeChannelPlan calculates low score for problematic plan`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                    Triple(2, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                    Triple(3, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        assertTrue(report.channelPlanScore < 0.8)
    }

    // ============================================
    // Multiple Network Analysis Tests
    // ============================================

    @Test
    fun `analyzeMultipleNetworks handles empty list`() {
        val analyzer = ChannelPlanAnalyzer()

        val report = analyzer.analyzeMultipleNetworks(emptyList())

        assertEquals("All Networks", report.ssid)
        assertEquals(0, report.clusters.size)
        assertEquals(0.0, report.channelPlanScore)
    }

    @Test
    fun `analyzeMultipleNetworks combines multiple clusters`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster1 =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
                ssid = "Network1",
            )
        val cluster2 =
            createClusterWithChannels(
                listOf(
                    Triple(6, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
                ssid = "Network2",
            )

        val report = analyzer.analyzeMultipleNetworks(listOf(cluster1, cluster2))

        assertEquals("All Networks", report.ssid)
        assertEquals(2, report.clusters.size)
        assertEquals(2, report.perBandChannelUsage.size)
    }

    @Test
    fun `analyzeMultipleNetworks aggregates issues from all clusters`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster1 =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ), // Wide channel issue
                ),
                ssid = "Network1",
            )
        val cluster2 =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(2, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ), // Overlapping issue
                ),
                ssid = "Network2",
            )

        val report = analyzer.analyzeMultipleNetworks(listOf(cluster1, cluster2))

        // Should have issues from both clusters
        assertTrue(report.issues.size >= 2)
    }

    @Test
    fun `analyzeMultipleNetworks calculates average score`() {
        val analyzer = ChannelPlanAnalyzer()
        val goodCluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
            )
        val badCluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                    Triple(2, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                ),
            )

        val report = analyzer.analyzeMultipleNetworks(listOf(goodCluster, badCluster))

        // Average should be between individual scores
        assertTrue(report.channelPlanScore > 0.0)
        assertTrue(report.channelPlanScore < 1.0)
    }

    // ============================================
    // Channel Issue Detection Tests
    // ============================================

    @Test
    fun `detects European non-overlapping channels correctly`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(5, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(9, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        val overlappingIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.OVERLAPPING_CHANNELS
            }
        // Should not detect overlap for valid European channel set
        assertTrue(overlappingIssues.isEmpty())
    }

    @Test
    fun `detects congested channels`() {
        val analyzer = ChannelPlanAnalyzer()
        // Create cluster with many APs on same channel
        val bssList =
            (1..5).map { i ->
                createBss("AA:BB:CC:11:11:${String.format("%02X", i)}", channel = 36, band = WiFiBand.BAND_5GHZ)
            }
        val cluster = createClusterWithBssids(bssList)

        val report = analyzer.analyzeChannelPlan(cluster)

        val congestionIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.CHANNEL_CONGESTION
            }
        assertEquals(1, congestionIssues.size)
        assertEquals(IssueSeverity.MEDIUM, congestionIssues[0].severity)
    }

    @Test
    fun `channel issues include affected BSSIDs`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        val issues = report.issues.filter { it.issueType == ChannelIssueType.WIDE_CHANNEL_24GHZ }
        assertEquals(1, issues.size)
        assertTrue(issues[0].affectedBssids.isNotEmpty())
    }

    @Test
    fun `channel issues include recommendations`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(3, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        val overlappingIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.OVERLAPPING_CHANNELS
            }
        assertEquals(1, overlappingIssues.size)
        assertTrue(overlappingIssues[0].recommendation.isNotBlank())
    }

    // ============================================
    // Channel Plan Scoring Tests
    // ============================================

    @Test
    fun `channel plan score decreases with critical issues`() {
        val analyzer = ChannelPlanAnalyzer()

        val noIssues =
            createClusterWithChannels(
                listOf(
                    Triple(36, WiFiBand.BAND_5GHZ, ChannelWidth.WIDTH_80MHZ),
                ),
            )

        val withIssues =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                    Triple(2, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                ),
            )

        val cleanScore = analyzer.analyzeChannelPlan(noIssues).channelPlanScore
        val problematicScore = analyzer.analyzeChannelPlan(withIssues).channelPlanScore

        assertTrue(cleanScore > problematicScore)
    }

    @Test
    fun `channel plan score is bounded 0-1`() {
        val analyzer = ChannelPlanAnalyzer()

        // Best case
        val excellent =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(6, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(11, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
            )

        // Worst case
        val terrible =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                    Triple(2, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                    Triple(3, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                    Triple(4, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_40MHZ),
                ),
            )

        val excellentScore = analyzer.analyzeChannelPlan(excellent).channelPlanScore
        val terribleScore = analyzer.analyzeChannelPlan(terrible).channelPlanScore

        assertTrue(excellentScore in 0.0..1.0)
        assertTrue(terribleScore in 0.0..1.0)
        assertTrue(excellentScore > terribleScore)
    }

    @Test
    fun `high severity issues reduce score more than low severity`() {
        val analyzer = ChannelPlanAnalyzer()

        // High severity: overlapping channels
        val highSeverity =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(2, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                ),
            )

        // Low severity: channel reuse in 5 GHz
        val bss1 = createBss("AA:BB:CC:11:11:11", channel = 36, band = WiFiBand.BAND_5GHZ)
        val bss2 = createBss("AA:BB:CC:22:22:22", channel = 36, band = WiFiBand.BAND_5GHZ)
        val lowSeverity = createClusterWithBssids(listOf(bss1, bss2))

        val highScore = analyzer.analyzeChannelPlan(highSeverity).channelPlanScore
        val lowScore = analyzer.analyzeChannelPlan(lowSeverity).channelPlanScore

        assertTrue(lowScore > highScore)
    }

    // ============================================
    // Channel Usage Map Tests
    // ============================================

    @Test
    fun `channel usage map groups by channel and band`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ), // Same channel
                    Triple(36, WiFiBand.BAND_5GHZ, ChannelWidth.WIDTH_80MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        // Should have 2 channel usages: channel 1 (with 2 APs) and channel 36
        assertEquals(2, report.perBandChannelUsage.size)

        val channel1Usage = report.perBandChannelUsage.find { it.channel == 1 }
        assertNotNull(channel1Usage)
        assertEquals(2, channel1Usage.bssids.size)
    }

    @Test
    fun `channel usage map calculates average RSSI`() {
        val analyzer = ChannelPlanAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", channel = 36, rssi = -60)
        val bss2 = createBss("AA:BB:CC:22:22:22", channel = 36, rssi = -70)
        val cluster = createClusterWithBssids(listOf(bss1, bss2))

        val report = analyzer.analyzeChannelPlan(cluster)

        val channelUsage = report.perBandChannelUsage.first()
        assertEquals(-65.0, channelUsage.averageRssiDbm)
    }

    @Test
    fun `channel usage map handles null RSSI values`() {
        val analyzer = ChannelPlanAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", channel = 36, rssi = -60)
        val bss2 = createBss("AA:BB:CC:22:22:22", channel = 36, rssi = null)
        val cluster = createClusterWithBssids(listOf(bss1, bss2))

        val report = analyzer.analyzeChannelPlan(cluster)

        val channelUsage = report.perBandChannelUsage.first()
        // Should calculate average only from non-null values
        assertEquals(-60.0, channelUsage.averageRssiDbm)
    }

    // ============================================
    // Edge Cases and Boundary Tests
    // ============================================

    @Test
    fun `handles single AP with no issues`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(36, WiFiBand.BAND_5GHZ, ChannelWidth.WIDTH_80MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        assertEquals(0, report.issues.size)
        assertEquals(1.0, report.channelPlanScore)
    }

    @Test
    fun `handles 6 GHz band correctly`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_6GHZ, ChannelWidth.WIDTH_160MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        // Should not generate wide channel warnings for 6 GHz
        val wideChannelIssues =
            report.issues.filter {
                it.issueType == ChannelIssueType.WIDE_CHANNEL_24GHZ
            }
        assertEquals(0, wideChannelIssues.size)
    }

    @Test
    fun `handles mixed band deployment`() {
        val analyzer = ChannelPlanAnalyzer()
        val cluster =
            createClusterWithChannels(
                listOf(
                    Triple(1, WiFiBand.BAND_2_4GHZ, ChannelWidth.WIDTH_20MHZ),
                    Triple(36, WiFiBand.BAND_5GHZ, ChannelWidth.WIDTH_80MHZ),
                    Triple(1, WiFiBand.BAND_6GHZ, ChannelWidth.WIDTH_160MHZ),
                ),
            )

        val report = analyzer.analyzeChannelPlan(cluster)

        assertEquals(3, report.perBandChannelUsage.size)
        assertTrue(report.perBandChannelUsage.any { it.band == WiFiBand.BAND_2_4GHZ })
        assertTrue(report.perBandChannelUsage.any { it.band == WiFiBand.BAND_5GHZ })
        assertTrue(report.perBandChannelUsage.any { it.band == WiFiBand.BAND_6GHZ })
    }

    // ============================================
    // Helper Functions
    // ============================================

    private fun createBss(
        bssid: String,
        channel: Int = 36,
        band: WiFiBand = WiFiBand.BAND_5GHZ,
        width: ChannelWidth = ChannelWidth.WIDTH_80MHZ,
        rssi: Int? = -65,
    ) = ClusteredBss(
        bssid = bssid,
        band = band,
        channel = channel,
        frequencyMHz = 5180,
        channelWidth = width,
        wifiStandard = WifiStandard.WIFI_6,
        rssiDbm = rssi,
        roamingCapabilities = RoamingCapabilities.none(),
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

    private fun createClusterWithChannels(
        channels: List<Triple<Int, WiFiBand, ChannelWidth>>,
        ssid: String = "TestNetwork",
    ): ApCluster {
        val bssList =
            channels.mapIndexed { index, (channel, band, width) ->
                createBss(
                    bssid = "AA:BB:CC:DD:EE:${String.format("%02X", index + 1)}",
                    channel = channel,
                    band = band,
                    width = width,
                )
            }
        return createClusterWithBssids(bssList, ssid)
    }
}
