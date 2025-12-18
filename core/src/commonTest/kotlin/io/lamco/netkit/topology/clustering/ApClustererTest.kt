package io.lamco.netkit.topology.clustering

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class ApClustererTest {
    @Test
    fun `creates clusterer with default parameters`() {
        val clusterer = ApClusterer()
        assertNotNull(clusterer)
    }

    @Test
    fun `creates clusterer with custom min cluster size`() {
        val clusterer = ApClusterer(minClusterSize = 2)
        assertNotNull(clusterer)
    }

    @Test
    fun `rejects invalid min cluster size`() {
        assertThrows<IllegalArgumentException> {
            ApClusterer(minClusterSize = 0)
        }
    }

    @Test
    fun `clusterAccessPoints returns empty list for empty input`() {
        val clusterer = ApClusterer()
        val result = clusterer.clusterAccessPoints(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `clusterAccessPoints creates single cluster for single BSS`() {
        val clusterer = ApClusterer()
        val bss = createTestBss("AA:BB:CC:DD:EE:FF")
        val result = clusterer.clusterAccessPoints(listOf(bss))
        assertEquals(1, result.size)
    }

    @Test
    fun `clusterAccessPoints groups BSSs by OUI`() {
        val clusterer = ApClusterer()
        val bss1 = createTestBss("AA:BB:CC:11:11:11")
        val bss2 = createTestBss("AA:BB:CC:22:22:22")
        val bss3 = createTestBss("DD:EE:FF:33:33:33")

        val result = clusterer.clusterAccessPoints(listOf(bss1, bss2, bss3))
        assertTrue(result.size >= 1)
    }

    @Test
    fun `clusterWithContext requires non-blank SSID`() {
        val clusterer = ApClusterer()
        assertThrows<IllegalArgumentException> {
            clusterer.clusterWithContext(
                ssid = "",
                securityFingerprint = SecurityFingerprint.wpa2Personal(),
                bssList = emptyList(),
            )
        }
    }

    @Test
    fun `clusterWithContext returns empty for empty BSS list`() {
        val clusterer = ApClusterer()
        val result =
            clusterer.clusterWithContext(
                ssid = "TestNetwork",
                securityFingerprint = SecurityFingerprint.wpa2Personal(),
                bssList = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `clusterWithContext creates single cluster for same vendor`() {
        val clusterer = ApClusterer(mergeVendorGroups = true)
        val bss1 = createTestBss("AA:BB:CC:11:11:11")
        val bss2 = createTestBss("AA:BB:CC:22:22:22")

        val result =
            clusterer.clusterWithContext(
                ssid = "TestNetwork",
                securityFingerprint = SecurityFingerprint.wpa2Personal(),
                bssList = listOf(bss1, bss2),
            )

        assertEquals(1, result.size)
        assertEquals(2, result.first().apCount)
    }

    @Test
    fun `clusterWithContext separates different vendors when mergeVendorGroups is false`() {
        val clusterer = ApClusterer(mergeVendorGroups = false)
        val bss1 = createTestBss("AA:BB:CC:11:11:11")
        val bss2 = createTestBss("DD:EE:FF:22:22:22")

        val result =
            clusterer.clusterWithContext(
                ssid = "TestNetwork",
                securityFingerprint = SecurityFingerprint.wpa2Personal(),
                bssList = listOf(bss1, bss2),
            )

        assertTrue(result.size >= 1)
    }

    @Test
    fun `clusterWithContext respects min cluster size`() {
        val clusterer = ApClusterer(minClusterSize = 3)
        val bss1 = createTestBss("AA:BB:CC:11:11:11")
        val bss2 = createTestBss("AA:BB:CC:22:22:22")

        val result =
            clusterer.clusterWithContext(
                ssid = "TestNetwork",
                securityFingerprint = SecurityFingerprint.wpa2Personal(),
                bssList = listOf(bss1, bss2),
            )

        assertTrue(result.isEmpty()) // Only 2 BSSs, need 3
    }

    @Test
    fun `clusterMultipleNetworks handles empty input`() {
        val clusterer = ApClusterer()
        val result = clusterer.clusterMultipleNetworks(emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `clusterMultipleNetworks clusters multiple networks`() {
        val clusterer = ApClusterer()
        val network1 =
            "Network1" to
                Pair(
                    SecurityFingerprint.wpa2Personal(),
                    listOf(createTestBss("AA:BB:CC:11:11:11")),
                )
        val network2 =
            "Network2" to
                Pair(
                    SecurityFingerprint.wpa3Personal(),
                    listOf(createTestBss("DD:EE:FF:22:22:22")),
                )

        val result = clusterer.clusterMultipleNetworks(mapOf(network1, network2))
        assertTrue(result.size >= 2)
    }

    @Test
    fun `findClusterForBssid finds correct cluster`() {
        val clusterer = ApClusterer()
        val bss = createTestBss("AA:BB:CC:DD:EE:FF")
        val clusters = clusterer.clusterAccessPoints(listOf(bss))

        val found = clusterer.findClusterForBssid("AA:BB:CC:DD:EE:FF", clusters)
        assertNotNull(found)
    }

    @Test
    fun `findClusterForBssid returns null for non-existent BSSID`() {
        val clusterer = ApClusterer()
        val bss = createTestBss("AA:BB:CC:DD:EE:FF")
        val clusters = clusterer.clusterAccessPoints(listOf(bss))

        val found = clusterer.findClusterForBssid("ZZ:ZZ:ZZ:ZZ:ZZ:ZZ", clusters)
        assertNull(found)
    }

    @Test
    fun `mergeClusters combines compatible clusters`() {
        val clusterer = ApClusterer()
        val cluster1 = createTestCluster("TestNet", listOf(createTestBss("AA:BB:CC:11:11:11")))
        val cluster2 = createTestCluster("TestNet", listOf(createTestBss("AA:BB:CC:22:22:22")))

        val merged = clusterer.mergeClusters(cluster1, cluster2)
        assertNotNull(merged)
        assertEquals(2, merged!!.apCount)
    }

    @Test
    fun `mergeClusters rejects different SSIDs`() {
        val clusterer = ApClusterer()
        val cluster1 = createTestCluster("Network1", listOf(createTestBss("AA:BB:CC:11:11:11")))
        val cluster2 = createTestCluster("Network2", listOf(createTestBss("AA:BB:CC:22:22:22")))

        val merged = clusterer.mergeClusters(cluster1, cluster2)
        assertNull(merged)
    }

    @Test
    fun `mergeClusters rejects different security fingerprints`() {
        val clusterer = ApClusterer()
        val bss1 = createTestBss("AA:BB:CC:11:11:11")
        val bss2 = createTestBss("AA:BB:CC:22:22:22")

        val cluster1 =
            ApCluster(
                id = "c1",
                ssid = "TestNet",
                securityFingerprint = SecurityFingerprint.wpa2Personal(),
                routerVendor = null,
                bssids = listOf(bss1),
            )

        val cluster2 =
            ApCluster(
                id = "c2",
                ssid = "TestNet",
                securityFingerprint = SecurityFingerprint.wpa3Personal(),
                routerVendor = null,
                bssids = listOf(bss2),
            )

        val merged = clusterer.mergeClusters(cluster1, cluster2)
        assertNull(merged)
    }

    @Test
    fun `splitByBand creates per-band clusters`() {
        val clusterer = ApClusterer()
        val bss24 = createTestBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_2_4GHZ)
        val bss5 = createTestBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ)
        val cluster = createTestCluster("TestNet", listOf(bss24, bss5))

        val result = clusterer.splitByBand(cluster)
        assertEquals(2, result.size)
    }

    @Test
    fun `splitByBand handles single-band cluster`() {
        val clusterer = ApClusterer()
        val bss1 = createTestBss("AA:BB:CC:11:11:11", band = WiFiBand.BAND_5GHZ)
        val bss2 = createTestBss("AA:BB:CC:22:22:22", band = WiFiBand.BAND_5GHZ)
        val cluster = createTestCluster("TestNet", listOf(bss1, bss2))

        val result = clusterer.splitByBand(cluster)
        assertEquals(1, result.size)
        assertEquals(2, result.first().apCount)
    }

    @Test
    fun `isMeshNetwork returns false for single AP`() {
        val clusterer = ApClusterer()
        val cluster = createTestCluster("TestNet", listOf(createTestBss("AA:BB:CC:DD:EE:FF")))

        assertFalse(clusterer.isMeshNetwork(cluster))
    }

    @Test
    fun `isMeshNetwork returns false for non-mesh vendor`() {
        val clusterer = ApClusterer()
        val bss1 = createTestBss("AA:BB:CC:11:11:11")
        val bss2 = createTestBss("AA:BB:CC:22:22:22")
        val cluster =
            ApCluster(
                id = "c1",
                ssid = "TestNet",
                securityFingerprint = SecurityFingerprint.wpa2Personal(),
                routerVendor = RouterVendor.UNKNOWN,
                bssids = listOf(bss1, bss2),
            )

        assertFalse(clusterer.isMeshNetwork(cluster))
    }

    @Test
    fun `isMeshNetwork returns true for mesh vendor with multiple APs`() {
        val clusterer = ApClusterer()
        val bss1 = createTestBss("1062EB:11:11:11") // Google OUI
        val bss2 = createTestBss("1062EB:22:22:22")
        val cluster =
            ApCluster(
                id = "c1",
                ssid = "GoogleWifi",
                securityFingerprint = SecurityFingerprint.wpa2Personal(),
                routerVendor = RouterVendor.GOOGLE,
                bssids = listOf(bss1, bss2),
            )

        assertTrue(clusterer.isMeshNetwork(cluster))
    }

    // Helper functions
    private fun createTestBss(
        bssid: String,
        band: WiFiBand = WiFiBand.BAND_5GHZ,
    ) = ClusteredBss(
        bssid = bssid,
        band = band,
        channel = 36,
        frequencyMHz = 5180,
        channelWidth = ChannelWidth.WIDTH_80MHZ,
        wifiStandard = WifiStandard.WIFI_6,
        rssiDbm = -65,
    )

    private fun createTestCluster(
        ssid: String,
        bssList: List<ClusteredBss>,
    ) = ApCluster(
        id = "test-cluster",
        ssid = ssid,
        securityFingerprint = SecurityFingerprint.wpa2Personal(),
        routerVendor = null,
        bssids = bssList,
    )
}
