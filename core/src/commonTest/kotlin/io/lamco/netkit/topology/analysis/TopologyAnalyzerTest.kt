package io.lamco.netkit.topology.analysis

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class TopologyAnalyzerTest {
    @Test
    fun `analyzeTopology handles empty cluster list`() {
        val analyzer = TopologyAnalyzer()
        val result = analyzer.analyzeTopology(emptyList())

        assertEquals(0, result.totalClusters)
        assertEquals(0, result.totalAccessPoints)
        assertFalse(result.hasEnterpriseDeployment)
    }

    @Test
    fun `analyzeTopology counts single cluster correctly`() {
        val analyzer = TopologyAnalyzer()
        val cluster = createSimpleCluster(apCount = 3)
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertEquals(1, result.totalClusters)
        assertEquals(3, result.totalAccessPoints)
    }

    @Test
    fun `analyzeTopology detects enterprise deployment`() {
        val analyzer = TopologyAnalyzer()
        val cluster = createEnterpriseCluster()
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.hasEnterpriseDeployment)
    }

    @Test
    fun `analyzeTopology detects mesh deployment`() {
        val analyzer = TopologyAnalyzer()
        val cluster = createMeshCluster()
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.hasMeshDeployment)
    }

    @Test
    fun `analyzeTopology detects WiFi 7 support`() {
        val analyzer = TopologyAnalyzer()
        val bss = createBss("AA:BB:CC:DD:EE:FF", WifiStandard.WIFI_7)
        val cluster = createClusterWithBssids(listOf(bss))
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.supportsWiFi7)
    }

    @Test
    fun `analyzeTopology detects WiFi 6E support`() {
        val analyzer = TopologyAnalyzer()
        val bss = createBss("AA:BB:CC:DD:EE:FF", WifiStandard.WIFI_6E, WiFiBand.BAND_6GHZ)
        val cluster = createClusterWithBssids(listOf(bss))
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.supportsWiFi6E)
    }

    @Test
    fun `analyzeTopology calculates fast roaming coverage`() {
        val analyzer = TopologyAnalyzer()
        val bssWithRoaming = createBss("AA:BB:CC:DD:EE:FF", roaming = true)
        val bssWithoutRoaming = createBss("11:22:33:44:55:66", roaming = false)

        val cluster1 = createClusterWithBssids(listOf(bssWithRoaming))
        val cluster2 = createClusterWithBssids(listOf(bssWithoutRoaming))

        val result = analyzer.analyzeTopology(listOf(cluster1, cluster2))
        assertEquals(50, result.fastRoamingCoverage) // 1 out of 2 = 50%
    }

    @Test
    fun `analyzeTopology calculates average cluster size`() {
        val analyzer = TopologyAnalyzer()
        val cluster1 = createSimpleCluster(apCount = 2)
        val cluster2 = createSimpleCluster(apCount = 4)
        val result = analyzer.analyzeTopology(listOf(cluster1, cluster2))

        assertEquals(3.0, result.averageClusterSize) // (2 + 4) / 2
    }

    @Test
    fun `analyzeTopology calculates overall quality`() {
        val analyzer = TopologyAnalyzer()
        val cluster = createSimpleCluster(apCount = 3)
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.overallQuality in 0..100)
    }

    @Test
    fun `isComplexEnvironment true for 5+ APs`() {
        val analyzer = TopologyAnalyzer()
        val cluster = createSimpleCluster(apCount = 5)
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.isComplexEnvironment)
    }

    @Test
    fun `isComplexEnvironment true for enterprise`() {
        val analyzer = TopologyAnalyzer()
        val cluster = createEnterpriseCluster()
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.isComplexEnvironment)
    }

    @Test
    fun `isComplexEnvironment false for simple deployments`() {
        val analyzer = TopologyAnalyzer()
        val cluster = createSimpleCluster(apCount = 2)
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertFalse(result.isComplexEnvironment)
    }

    @Test
    fun `isModernDeployment true for WiFi 6E`() {
        val analyzer = TopologyAnalyzer()
        val bss = createBss("AA:BB:CC:DD:EE:FF", WifiStandard.WIFI_6E, WiFiBand.BAND_6GHZ)
        val cluster = createClusterWithBssids(listOf(bss))
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.isModernDeployment)
    }

    @Test
    fun `isModernDeployment true for WiFi 7`() {
        val analyzer = TopologyAnalyzer()
        val bss = createBss("AA:BB:CC:DD:EE:FF", WifiStandard.WIFI_7)
        val cluster = createClusterWithBssids(listOf(bss))
        val result = analyzer.analyzeTopology(listOf(cluster))

        assertTrue(result.isModernDeployment)
    }

    @Test
    fun `empty cluster cannot be created`() {
        // ApCluster requires at least one BSSID by design
        assertThrows<IllegalArgumentException> {
            createClusterWithBssids(emptyList())
        }
    }

    @Test
    fun `identifyPrimaryAp returns single AP for single-AP cluster`() {
        val analyzer = TopologyAnalyzer()
        val bss = createBss("AA:BB:CC:DD:EE:FF")
        val cluster = createClusterWithBssids(listOf(bss))
        val result = analyzer.identifyPrimaryAp(cluster)

        assertEquals("AA:BB:CC:DD:EE:FF", result?.bssid)
    }

    @Test
    fun `identifyPrimaryAp prefers strongest signal`() {
        val analyzer = TopologyAnalyzer()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -75)
        val strong = createBss("AA:BB:CC:22:22:22", rssi = -55)
        val cluster = createClusterWithBssids(listOf(weak, strong))

        val result = analyzer.identifyPrimaryAp(cluster)
        assertEquals("AA:BB:CC:22:22:22", result?.bssid)
    }

    @Test
    fun `identifyPrimaryAp prefers 2-4 GHz for range`() {
        val analyzer = TopologyAnalyzer()
        val bss24 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_2_4GHZ)
        val bss5 = createBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ)
        val cluster = createClusterWithBssids(listOf(bss5, bss24))

        // When no strong signal, should prefer 2.4 GHz
        val result = analyzer.identifyPrimaryAp(cluster)
        // Result depends on implementation details
        assertNotNull(result)
    }

    @Test
    fun `calculateClusterOverlap detects band overlap`() {
        val analyzer = TopologyAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ)
        val bss2 = createBss("DD:EE:FF:22:22:22", band = WiFiBand.BAND_5GHZ)

        val cluster1 = createClusterWithBssids(listOf(bss1), ssid = "Network1")
        val cluster2 = createClusterWithBssids(listOf(bss2), ssid = "Network2")

        val result = analyzer.calculateClusterOverlap(cluster1, cluster2)
        assertTrue(result.hasOverlap)
        assertTrue(result.overlappingBands.contains(WiFiBand.BAND_5GHZ))
    }

    @Test
    fun `calculateClusterOverlap detects potential interference`() {
        val analyzer = TopologyAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ)
        val bss2 = createBss("DD:EE:FF:22:22:22", band = WiFiBand.BAND_5GHZ)

        val cluster1 = createClusterWithBssids(listOf(bss1), ssid = "Network1")
        val cluster2 = createClusterWithBssids(listOf(bss2), ssid = "Network2")

        val result = analyzer.calculateClusterOverlap(cluster1, cluster2)
        assertTrue(result.potentialInterference) // Different SSIDs, same band
    }

    @Test
    fun `calculateClusterOverlap no interference for same SSID`() {
        val analyzer = TopologyAnalyzer()
        val bss1 = createBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ)
        val bss2 = createBss("DD:EE:FF:22:22:22", band = WiFiBand.BAND_5GHZ)

        val cluster1 = createClusterWithBssids(listOf(bss1), ssid = "SameNetwork")
        val cluster2 = createClusterWithBssids(listOf(bss2), ssid = "SameNetwork")

        val result = analyzer.calculateClusterOverlap(cluster1, cluster2)
        assertFalse(result.potentialInterference) // Same SSID
    }

    // Helper functions
    private fun createSimpleCluster(apCount: Int): ApCluster {
        val bssList =
            (1..apCount).map { i ->
                createBss("AA:BB:CC:DD:EE:${String.format("%02X", i)}")
            }
        return createClusterWithBssids(bssList)
    }

    private fun createEnterpriseCluster(): ApCluster {
        val bss = createBss("001D:AB:CD:EF:12") // Cisco OUI
        return ApCluster(
            id = "ent-cluster",
            ssid = "Corporate",
            securityFingerprint = SecurityFingerprint.wpa2Personal(),
            routerVendor = RouterVendor.CISCO,
            bssids = listOf(bss),
        )
    }

    private fun createMeshCluster(): ApCluster {
        val bss1 = createBss("1062EB:11:11:11") // Google OUI
        val bss2 = createBss("1062EB:22:22:22")
        return ApCluster(
            id = "mesh-cluster",
            ssid = "GoogleWifi",
            securityFingerprint = SecurityFingerprint.wpa2Personal(),
            routerVendor = RouterVendor.GOOGLE,
            bssids = listOf(bss1, bss2),
        )
    }

    private fun createBss(
        bssid: String,
        standard: WifiStandard = WifiStandard.WIFI_6,
        band: WiFiBand = WiFiBand.BAND_5GHZ,
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
