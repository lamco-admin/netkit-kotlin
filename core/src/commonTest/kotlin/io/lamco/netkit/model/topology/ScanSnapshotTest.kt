package io.lamco.netkit.model.topology

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanSnapshotTest {
    private fun createTestBss(
        bssid: String = "00:11:22:33:44:55",
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
        id = "cluster_${ssid.hashCode()}",
        ssid = ssid,
        securityFingerprint = SecurityFingerprint.wpa3Personal(),
        routerVendor = RouterVendor.UBIQUITI,
        bssids = bssids,
    )

    private fun createTestSnapshot(
        id: String = "snap_123",
        timestampMillis: Long = 1000000,
        clusters: List<ApCluster> = listOf(createTestCluster()),
        connectedBssid: String? = null,
        scannedBands: Set<WiFiBand> = setOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ),
    ) = ScanSnapshot(
        id = id,
        timestampMillis = timestampMillis,
        clusters = clusters,
        connectedBssid = connectedBssid,
        scanDurationMillis = 1000,
        scannedBands = scannedBands,
    )

    @Test
    fun `create snapshot with valid parameters succeeds`() {
        val snapshot = createTestSnapshot()

        assertEquals("snap_123", snapshot.id)
        assertEquals(1000000, snapshot.timestampMillis)
        assertEquals(1, snapshot.clusters.size)
    }

    @Test
    fun `blank snapshot ID throws exception`() {
        assertThrows<IllegalArgumentException> {
            createTestSnapshot(id = "")
        }
    }

    @Test
    fun `zero timestamp throws exception`() {
        assertThrows<IllegalArgumentException> {
            createTestSnapshot(timestampMillis = 0)
        }
    }

    @Test
    fun `negative timestamp throws exception`() {
        assertThrows<IllegalArgumentException> {
            createTestSnapshot(timestampMillis = -1)
        }
    }

    @Test
    fun `negative scan duration throws exception`() {
        assertThrows<IllegalArgumentException> {
            ScanSnapshot(
                id = "snap_1",
                timestampMillis = 1000,
                clusters = listOf(createTestCluster()),
                scanDurationMillis = -1,
            )
        }
    }

    @Test
    fun `allBssids returns all BSSIDs from all clusters`() {
        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01")
        val bss2 = createTestBss(bssid = "AA:BB:CC:DD:EE:02")
        val cluster1 = createTestCluster(ssid = "Network1", bssids = listOf(bss1))
        val cluster2 = createTestCluster(ssid = "Network2", bssids = listOf(bss2))

        val snapshot = createTestSnapshot(clusters = listOf(cluster1, cluster2))

        assertEquals(2, snapshot.allBssids.size)
        assertTrue(snapshot.allBssids.any { it.bssid == "AA:BB:CC:DD:EE:01" })
        assertTrue(snapshot.allBssids.any { it.bssid == "AA:BB:CC:DD:EE:02" })
    }

    @Test
    fun `totalApCount returns correct count`() {
        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01")
        val bss2 = createTestBss(bssid = "AA:BB:CC:DD:EE:02")
        val bss3 = createTestBss(bssid = "AA:BB:CC:DD:EE:03")
        val cluster = createTestCluster(bssids = listOf(bss1, bss2, bss3))

        val snapshot = createTestSnapshot(clusters = listOf(cluster))

        assertEquals(3, snapshot.totalApCount)
    }

    @Test
    fun `uniqueSsidCount returns correct count`() {
        val cluster1 = createTestCluster(ssid = "Network1")
        val cluster2 = createTestCluster(ssid = "Network2")
        val cluster3 = createTestCluster(ssid = "Network1") // Duplicate SSID

        val snapshot = createTestSnapshot(clusters = listOf(cluster1, cluster2, cluster3))

        assertEquals(2, snapshot.uniqueSsidCount)
    }

    @Test
    fun `isCompleteScan returns true when 2_4 and 5 GHz scanned`() {
        val snapshot =
            createTestSnapshot(
                scannedBands = setOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ),
            )

        assertTrue(snapshot.isCompleteScan)
    }

    @Test
    fun `isCompleteScan returns false when only 2_4 GHz scanned`() {
        val snapshot =
            ScanSnapshot(
                id = "snap_1",
                timestampMillis = 1000,
                clusters = listOf(createTestCluster()),
                scannedBands = setOf(WiFiBand.BAND_2_4GHZ),
            )

        assertFalse(snapshot.isCompleteScan)
    }

    @Test
    fun `isConnected returns true when connectedBssid is set`() {
        val snapshot = createTestSnapshot(connectedBssid = "AA:BB:CC:DD:EE:FF")

        assertTrue(snapshot.isConnected)
    }

    @Test
    fun `isConnected returns false when connectedBssid is null`() {
        val snapshot = createTestSnapshot(connectedBssid = null)

        assertFalse(snapshot.isConnected)
    }

    @Test
    fun `connectedCluster returns correct cluster when connected`() {
        val bss = createTestBss(bssid = "AA:BB:CC:DD:EE:FF")
        val cluster = createTestCluster(ssid = "ConnectedNetwork", bssids = listOf(bss))
        val snapshot =
            createTestSnapshot(
                clusters = listOf(cluster),
                connectedBssid = "AA:BB:CC:DD:EE:FF",
            )

        val connectedCluster = snapshot.connectedCluster

        assertNotNull(connectedCluster)
        assertEquals("ConnectedNetwork", connectedCluster.ssid)
    }

    @Test
    fun `connectedCluster returns null when not connected`() {
        val snapshot = createTestSnapshot(connectedBssid = null)

        assertNull(snapshot.connectedCluster)
    }

    @Test
    fun `connectedBss returns correct BSS when connected`() {
        val bss = createTestBss(bssid = "AA:BB:CC:DD:EE:FF")
        val cluster = createTestCluster(bssids = listOf(bss))
        val snapshot =
            createTestSnapshot(
                clusters = listOf(cluster),
                connectedBssid = "AA:BB:CC:DD:EE:FF",
            )

        val connectedBss = snapshot.connectedBss

        assertNotNull(connectedBss)
        assertEquals("AA:BB:CC:DD:EE:FF", connectedBss.bssid)
    }

    @Test
    fun `connectedBss returns null when not connected`() {
        val snapshot = createTestSnapshot(connectedBssid = null)

        assertNull(snapshot.connectedBss)
    }

    @Test
    fun `clustersByBand returns only clusters with specified band`() {
        val bss24 = createTestBss(bssid = "AA:BB:CC:DD:EE:01", band = WiFiBand.BAND_2_4GHZ)
        val bss5 = createTestBss(bssid = "AA:BB:CC:DD:EE:02", band = WiFiBand.BAND_5GHZ)
        val cluster24 = createTestCluster(ssid = "Network24", bssids = listOf(bss24))
        val cluster5 = createTestCluster(ssid = "Network5", bssids = listOf(bss5))

        val snapshot = createTestSnapshot(clusters = listOf(cluster24, cluster5))

        val clusters5GHz = snapshot.clustersByBand(WiFiBand.BAND_5GHZ)

        assertEquals(1, clusters5GHz.size)
        assertEquals("Network5", clusters5GHz.first().ssid)
    }

    @Test
    fun `strongSignals returns BSSIDs above threshold`() {
        val bssStrong = createTestBss(bssid = "AA:BB:CC:DD:EE:01", rssi = -50)
        val bssWeak = createTestBss(bssid = "AA:BB:CC:DD:EE:02", rssi = -80)
        val cluster = createTestCluster(bssids = listOf(bssStrong, bssWeak))

        val snapshot = createTestSnapshot(clusters = listOf(cluster))

        val strong = snapshot.strongSignals(thresholdDbm = -70)

        assertEquals(1, strong.size)
        assertEquals("AA:BB:CC:DD:EE:01", strong.first().bssid)
    }

    @Test
    fun `enterpriseClusters returns clusters with fast roaming support`() {
        val bss = createTestBss()
        val enterpriseCluster =
            ApCluster(
                id = "cluster_enterprise",
                ssid = "Enterprise",
                securityFingerprint = SecurityFingerprint.wpa3Personal(),
                routerVendor = RouterVendor.CISCO,
                bssids = listOf(bss.copy(roamingCapabilities = RoamingCapabilities.fullSuite(AuthType.WPA3_SAE))),
            )
        val consumerCluster = createTestCluster(ssid = "Consumer")

        val snapshot = createTestSnapshot(clusters = listOf(enterpriseCluster, consumerCluster))

        val enterprise = snapshot.enterpriseClusters

        assertEquals(1, enterprise.size)
        assertEquals("Enterprise", enterprise.first().ssid)
    }

    @Test
    fun `modernWiFiClusters returns WiFi 6E and 7 clusters`() {
        val bss6e = createTestBss().copy(wifiStandard = WifiStandard.WIFI_6E)
        val bss5 = createTestBss().copy(wifiStandard = WifiStandard.WIFI_5)
        val cluster6e = createTestCluster(ssid = "WiFi6E", bssids = listOf(bss6e))
        val cluster5 = createTestCluster(ssid = "WiFi5", bssids = listOf(bss5))

        val snapshot = createTestSnapshot(clusters = listOf(cluster6e, cluster5))

        val modern = snapshot.modernWiFiClusters

        assertEquals(1, modern.size)
        assertEquals("WiFi6E", modern.first().ssid)
    }

    @Test
    fun `insecureClusters returns clusters with weak security`() {
        val secureCluster = createTestCluster(ssid = "Secure")
        val insecureCluster =
            ApCluster(
                id = "cluster_open",
                ssid = "Open",
                securityFingerprint = SecurityFingerprint.open(),
                routerVendor = RouterVendor.UNKNOWN,
                bssids = listOf(createTestBss()),
            )

        val snapshot = createTestSnapshot(clusters = listOf(secureCluster, insecureCluster))

        val insecure = snapshot.insecureClusters

        assertEquals(1, insecure.size)
        assertEquals("Open", insecure.first().ssid)
    }

    @Test
    fun `averageRssiDbm calculates correct average`() {
        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01", rssi = -50)
        val bss2 = createTestBss(bssid = "AA:BB:CC:DD:EE:02", rssi = -70)
        val cluster = createTestCluster(bssids = listOf(bss1, bss2))

        val snapshot = createTestSnapshot(clusters = listOf(cluster))

        assertEquals(-60.0, snapshot.averageRssiDbm)
    }

    @Test
    fun `averageRssiDbm returns null when no RSSI values`() {
        val bss = createTestBss(rssi = -60).copy(rssiDbm = null)
        val cluster = createTestCluster(bssids = listOf(bss))

        val snapshot = createTestSnapshot(clusters = listOf(cluster))

        assertNull(snapshot.averageRssiDbm)
    }

    @Test
    fun `strongestSignalDbm returns maximum RSSI`() {
        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01", rssi = -50)
        val bss2 = createTestBss(bssid = "AA:BB:CC:DD:EE:02", rssi = -70)
        val bss3 = createTestBss(bssid = "AA:BB:CC:DD:EE:03", rssi = -40)
        val cluster = createTestCluster(bssids = listOf(bss1, bss2, bss3))

        val snapshot = createTestSnapshot(clusters = listOf(cluster))

        assertEquals(-40, snapshot.strongestSignalDbm)
    }

    @Test
    fun `weakestSignalDbm returns minimum RSSI`() {
        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01", rssi = -50)
        val bss2 = createTestBss(bssid = "AA:BB:CC:DD:EE:02", rssi = -70)
        val bss3 = createTestBss(bssid = "AA:BB:CC:DD:EE:03", rssi = -40)
        val cluster = createTestCluster(bssids = listOf(bss1, bss2, bss3))

        val snapshot = createTestSnapshot(clusters = listOf(cluster))

        assertEquals(-70, snapshot.weakestSignalDbm)
    }

    @Test
    fun `ageMillis calculates correct age`() {
        val snapshot = createTestSnapshot(timestampMillis = 1000)
        val currentTime = 5000L

        assertEquals(4000, snapshot.ageMillis(currentTime))
    }

    @Test
    fun `isStale returns true when older than threshold`() {
        val snapshot = createTestSnapshot(timestampMillis = 1000)
        val currentTime = 400_000L

        assertTrue(snapshot.isStale(thresholdMillis = 300_000, currentTimeMillis = currentTime))
    }

    @Test
    fun `isStale returns false when within threshold`() {
        val snapshot = createTestSnapshot(timestampMillis = 1000)
        val currentTime = 200_000L

        assertFalse(snapshot.isStale(thresholdMillis = 300_000, currentTimeMillis = currentTime))
    }

    @Test
    fun `compareWith detects added BSSIDs`() {
        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01")
        val bss2 = createTestBss(bssid = "AA:BB:CC:DD:EE:02")

        val snapshot1 =
            createTestSnapshot(
                id = "snap_1",
                timestampMillis = 1000,
                clusters = listOf(createTestCluster(bssids = listOf(bss1))),
            )
        val snapshot2 =
            createTestSnapshot(
                id = "snap_2",
                timestampMillis = 2000,
                clusters = listOf(createTestCluster(bssids = listOf(bss1, bss2))),
            )

        val comparison = snapshot2.compareWith(snapshot1)

        assertEquals(1, comparison.addedBssids.size)
        assertTrue(comparison.addedBssids.contains("AA:BB:CC:DD:EE:02"))
    }

    @Test
    fun `compareWith detects removed BSSIDs`() {
        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01")
        val bss2 = createTestBss(bssid = "AA:BB:CC:DD:EE:02")

        val snapshot1 =
            createTestSnapshot(
                id = "snap_1",
                timestampMillis = 1000,
                clusters = listOf(createTestCluster(bssids = listOf(bss1, bss2))),
            )
        val snapshot2 =
            createTestSnapshot(
                id = "snap_2",
                timestampMillis = 2000,
                clusters = listOf(createTestCluster(bssids = listOf(bss1))),
            )

        val comparison = snapshot2.compareWith(snapshot1)

        assertEquals(1, comparison.removedBssids.size)
        assertTrue(comparison.removedBssids.contains("AA:BB:CC:DD:EE:02"))
    }

    @Test
    fun `compareWith detects persistent BSSIDs`() {
        val bss1 = createTestBss(bssid = "AA:BB:CC:DD:EE:01")

        val snapshot1 =
            createTestSnapshot(
                id = "snap_1",
                timestampMillis = 1000,
                clusters = listOf(createTestCluster(bssids = listOf(bss1))),
            )
        val snapshot2 =
            createTestSnapshot(
                id = "snap_2",
                timestampMillis = 2000,
                clusters = listOf(createTestCluster(bssids = listOf(bss1))),
            )

        val comparison = snapshot2.compareWith(snapshot1)

        assertEquals(1, comparison.persistentBssids.size)
        assertTrue(comparison.persistentBssids.contains("AA:BB:CC:DD:EE:01"))
    }

    @Test
    fun `summary generates readable description`() {
        val snapshot = createTestSnapshot()

        val summary = snapshot.summary

        assertTrue(summary.contains("1 APs"))
        assertTrue(summary.contains("1 networks"))
    }

    @Test
    fun `createId generates valid ID with timestamp only`() {
        val id = ScanSnapshot.createId(1234567890)

        assertEquals("snap_1234567890", id)
    }

    @Test
    fun `createId generates valid ID with timestamp and suffix`() {
        val id = ScanSnapshot.createId(1234567890, "home")

        assertEquals("snap_1234567890_home", id)
    }
}
