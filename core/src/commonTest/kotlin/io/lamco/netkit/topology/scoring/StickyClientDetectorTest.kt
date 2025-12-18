package io.lamco.netkit.topology.scoring

import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.topology.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class StickyClientDetectorTest {
    // ============================================
    // Current Sticky Detection Tests
    // ============================================

    @Test
    fun `detectCurrentSticky returns null when signal is strong`() {
        val detector = StickyClientDetector()
        val cluster = createTestCluster()

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -60, // Strong signal
                cluster = cluster,
            )

        assertNull(result)
    }

    @Test
    fun `detectCurrentSticky returns null when signal at threshold`() {
        val detector = StickyClientDetector(stickyRssiThreshold = -75)
        val cluster = createTestCluster()

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -75, // At threshold
                cluster = cluster,
            )

        assertNull(result)
    }

    @Test
    fun `detectCurrentSticky returns null when no better alternative`() {
        val detector = StickyClientDetector()
        val current = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val similar = createBss("AA:BB:CC:22:22:22", rssi = -82)
        val cluster = createClusterWithBssids(listOf(current, similar))

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -80,
                cluster = cluster,
            )

        assertNull(result)
    }

    @Test
    fun `detectCurrentSticky detects sticky when better AP exists`() {
        val detector = StickyClientDetector()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val strong = createBss("AA:BB:CC:22:22:22", rssi = -60)
        val cluster = createClusterWithBssids(listOf(weak, strong))

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -80,
                cluster = cluster,
            )

        assertNotNull(result)
        assertTrue(result.currentlySticky)
        assertEquals("TestNetwork", result.ssid)
    }

    @Test
    fun `detectCurrentSticky calculates metrics correctly`() {
        val detector = StickyClientDetector()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -85)
        val better1 = createBss("AA:BB:CC:22:22:22", rssi = -65)
        val better2 = createBss("AA:BB:CC:33:33:33", rssi = -70)
        val cluster = createClusterWithBssids(listOf(weak, better1, better2))

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -85,
                cluster = cluster,
            )

        assertNotNull(result)
        assertEquals(-85.0, result.avgStickyRssiDbm)
        assertEquals(-85, result.worstStickyRssiDbm)
        assertEquals(1, result.stickyEventsCount)
        // Average of better APs: (-65 + -70) / 2 = -67.5
        assertEquals(-67.5, result.avgBetterApRssiDbm)
    }

    @Test
    fun `detectCurrentSticky uses custom threshold`() {
        val detector = StickyClientDetector(stickyRssiThreshold = -70)
        val cluster = createTestCluster()

        // With custom threshold of -70, signal at -72 should be sticky
        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -72,
                cluster = cluster,
            )

        // Depends on whether better AP exists, but should evaluate with new threshold
        // If result is not null, it means sticky was detected with custom threshold
        // If null, no better AP was available
        assertNotNull(result)
    }

    @Test
    fun `detectCurrentSticky uses custom differential`() {
        val detector = StickyClientDetector(betterApDifferential = 15)
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val slightlyBetter = createBss("AA:BB:CC:22:22:22", rssi = -70) // Only 10 dB better
        val muchBetter = createBss("AA:BB:CC:33:33:33", rssi = -60) // 20 dB better
        val cluster = createClusterWithBssids(listOf(weak, slightlyBetter, muchBetter))

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -80,
                cluster = cluster,
            )

        assertNotNull(result)
        // Only muchBetter should qualify with 15 dB differential
        // Average should be -60 (only one qualifying AP)
        assertEquals(-60.0, result.avgBetterApRssiDbm)
    }

    @Test
    fun `detectCurrentSticky excludes current BSSID from alternatives`() {
        val detector = StickyClientDetector()
        val current = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val cluster = createClusterWithBssids(listOf(current))

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -80,
                cluster = cluster,
            )

        assertNull(result) // No alternatives besides self
    }

    @Test
    fun `detectCurrentSticky handles null RSSI in better APs`() {
        val detector = StickyClientDetector()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val noRssi = createBss("AA:BB:CC:22:22:22", rssi = null)
        val strong = createBss("AA:BB:CC:33:33:33", rssi = -60)
        val cluster = createClusterWithBssids(listOf(weak, noRssi, strong))

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -80,
                cluster = cluster,
            )

        assertNotNull(result)
        // Should only count strong AP in average (ignoring null)
        assertEquals(-60.0, result.avgBetterApRssiDbm)
    }

    // ============================================
    // Sticky Pattern Analysis Tests
    // ============================================

    @Test
    fun `analyzeStickyPattern handles empty event list`() {
        val detector = StickyClientDetector()

        val result =
            detector.analyzeStickyPattern(
                ssid = "TestNetwork",
                recentStickyEvents = emptyList(),
            )

        assertEquals("TestNetwork", result.ssid)
        assertEquals(0, result.stickyEventsCount)
        assertEquals(0L, result.totalStickyDurationMillis)
        assertEquals(0.0, result.avgStickyRssiDbm)
        assertEquals(0.0, result.avgBetterApRssiDbm)
        assertFalse(result.currentlySticky)
    }

    @Test
    fun `analyzeStickyPattern calculates totals correctly`() {
        val detector = StickyClientDetector()
        val events =
            listOf(
                StickyEvent(0, 5000, -80, -60, false),
                StickyEvent(10000, 3000, -85, -65, false),
                StickyEvent(20000, 2000, -75, -55, false),
            )

        val result =
            detector.analyzeStickyPattern(
                ssid = "TestNetwork",
                recentStickyEvents = events,
            )

        assertEquals(3, result.stickyEventsCount)
        assertEquals(10000L, result.totalStickyDurationMillis) // 5000 + 3000 + 2000
    }

    @Test
    fun `analyzeStickyPattern calculates averages correctly`() {
        val detector = StickyClientDetector()
        val events =
            listOf(
                StickyEvent(0, 5000, -80, -60, false),
                StickyEvent(10000, 3000, -70, -50, false),
            )

        val result =
            detector.analyzeStickyPattern(
                ssid = "TestNetwork",
                recentStickyEvents = events,
            )

        assertEquals(-75.0, result.avgStickyRssiDbm) // (-80 + -70) / 2
        assertEquals(-55.0, result.avgBetterApRssiDbm) // (-60 + -50) / 2
    }

    @Test
    fun `analyzeStickyPattern identifies worst RSSI`() {
        val detector = StickyClientDetector()
        val events =
            listOf(
                StickyEvent(0, 5000, -80, -60, false),
                StickyEvent(10000, 3000, -90, -65, false), // Worst
                StickyEvent(20000, 2000, -75, -55, false),
            )

        val result =
            detector.analyzeStickyPattern(
                ssid = "TestNetwork",
                recentStickyEvents = events,
            )

        assertEquals(-90, result.worstStickyRssiDbm)
    }

    @Test
    fun `analyzeStickyPattern detects ongoing sticky from last event`() {
        val detector = StickyClientDetector()
        val eventsOngoing =
            listOf(
                StickyEvent(0, 5000, -80, -60, false),
                StickyEvent(10000, 3000, -85, -65, true), // Ongoing
            )
        val eventsResolved =
            listOf(
                StickyEvent(0, 5000, -80, -60, false),
                StickyEvent(10000, 3000, -85, -65, false), // Resolved
            )

        val resultOngoing = detector.analyzeStickyPattern("TestNetwork", eventsOngoing)
        val resultResolved = detector.analyzeStickyPattern("TestNetwork", eventsResolved)

        assertTrue(resultOngoing.currentlySticky)
        assertFalse(resultResolved.currentlySticky)
    }

    @Test
    fun `analyzeStickyPattern handles single event`() {
        val detector = StickyClientDetector()
        val events =
            listOf(
                StickyEvent(0, 5000, -80, -60, true),
            )

        val result =
            detector.analyzeStickyPattern(
                ssid = "TestNetwork",
                recentStickyEvents = events,
            )

        assertEquals(1, result.stickyEventsCount)
        assertEquals(5000L, result.totalStickyDurationMillis)
        assertEquals(-80.0, result.avgStickyRssiDbm)
        assertEquals(-60.0, result.avgBetterApRssiDbm)
        assertEquals(-80, result.worstStickyRssiDbm)
        assertTrue(result.currentlySticky)
    }

    // ============================================
    // Roaming Recommendation Tests
    // ============================================

    @Test
    fun `shouldRoam returns false when signal is acceptable`() {
        val detector = StickyClientDetector()
        val cluster = createTestCluster()

        val shouldRoam =
            detector.shouldRoam(
                currentRssi = -60, // Good signal
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
            )

        assertFalse(shouldRoam)
    }

    @Test
    fun `shouldRoam returns false when signal at threshold`() {
        val detector = StickyClientDetector(stickyRssiThreshold = -75)
        val cluster = createTestCluster()

        val shouldRoam =
            detector.shouldRoam(
                currentRssi = -75, // At threshold
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
            )

        assertFalse(shouldRoam)
    }

    @Test
    fun `shouldRoam returns false when no better AP available`() {
        val detector = StickyClientDetector()
        val current = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val similar = createBss("AA:BB:CC:22:22:22", rssi = -82)
        val cluster = createClusterWithBssids(listOf(current, similar))

        val shouldRoam =
            detector.shouldRoam(
                currentRssi = -80,
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
            )

        assertFalse(shouldRoam)
    }

    @Test
    fun `shouldRoam returns true when better AP available`() {
        val detector = StickyClientDetector()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -85)
        val strong = createBss("AA:BB:CC:22:22:22", rssi = -60)
        val cluster = createClusterWithBssids(listOf(weak, strong))

        val shouldRoam =
            detector.shouldRoam(
                currentRssi = -85,
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
            )

        assertTrue(shouldRoam)
    }

    @Test
    fun `shouldRoam uses custom threshold`() {
        val detector = StickyClientDetector(stickyRssiThreshold = -70)
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -72)
        val strong = createBss("AA:BB:CC:22:22:22", rssi = -55)
        val cluster = createClusterWithBssids(listOf(weak, strong))

        // With threshold -70, signal at -72 should trigger roaming check
        val shouldRoam =
            detector.shouldRoam(
                currentRssi = -72,
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
            )

        assertTrue(shouldRoam)
    }

    @Test
    fun `shouldRoam uses custom differential`() {
        val detector = StickyClientDetector(betterApDifferential = 15)
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val slightlyBetter = createBss("AA:BB:CC:22:22:22", rssi = -70) // Only 10 dB better
        val cluster = createClusterWithBssids(listOf(weak, slightlyBetter))

        // With differential 15, the slightly better AP should not qualify
        val shouldRoam =
            detector.shouldRoam(
                currentRssi = -80,
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
            )

        assertFalse(shouldRoam)
    }

    @Test
    fun `shouldRoam handles null RSSI values`() {
        val detector = StickyClientDetector()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -85)
        val noRssi = createBss("AA:BB:CC:22:22:22", rssi = null)
        val strong = createBss("AA:BB:CC:33:33:33", rssi = -60)
        val cluster = createClusterWithBssids(listOf(weak, noRssi, strong))

        val shouldRoam =
            detector.shouldRoam(
                currentRssi = -85,
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
            )

        assertTrue(shouldRoam) // Should find the strong AP
    }

    @Test
    fun `shouldRoam excludes current BSSID from candidates`() {
        val detector = StickyClientDetector()
        val current = createBss("AA:BB:CC:11:11:11", rssi = -80)
        val cluster = createClusterWithBssids(listOf(current))

        val shouldRoam =
            detector.shouldRoam(
                currentRssi = -80,
                currentBssid = "AA:BB:CC:11:11:11",
                cluster = cluster,
            )

        assertFalse(shouldRoam) // No other APs
    }

    // ============================================
    // Constructor and Configuration Tests
    // ============================================

    @Test
    fun `StickyClientDetector uses default values`() {
        val detector = StickyClientDetector()
        val weak = createBss("AA:BB:CC:11:11:11", rssi = -76)
        val better = createBss("AA:BB:CC:22:22:22", rssi = -60)
        val cluster = createClusterWithBssids(listOf(weak, better))

        // Should detect sticky with default threshold -75
        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -76,
                cluster = cluster,
            )

        assertNotNull(result)
    }

    @Test
    fun `StickyClientDetector accepts custom configuration`() {
        val detector =
            StickyClientDetector(
                stickyRssiThreshold = -70,
                betterApDifferential = 15,
            )

        val weak = createBss("AA:BB:CC:11:11:11", rssi = -72)
        val better = createBss("AA:BB:CC:22:22:22", rssi = -50)
        val cluster = createClusterWithBssids(listOf(weak, better))

        val result =
            detector.detectCurrentSticky(
                ssid = "TestNetwork",
                currentBssid = "AA:BB:CC:11:11:11",
                currentRssi = -72,
                cluster = cluster,
            )

        assertNotNull(result)
    }

    // ============================================
    // StickyEvent Data Class Tests
    // ============================================

    @Test
    fun `StickyEvent creates valid event`() {
        val event =
            StickyEvent(
                timestampMillis = 1000L,
                durationMillis = 5000L,
                stickyRssi = -80,
                betterApRssi = -60,
                isOngoing = true,
            )

        assertEquals(1000L, event.timestampMillis)
        assertEquals(5000L, event.durationMillis)
        assertEquals(-80, event.stickyRssi)
        assertEquals(-60, event.betterApRssi)
        assertTrue(event.isOngoing)
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

    private fun createTestCluster(): ApCluster {
        val bss1 = createBss("AA:BB:CC:11:11:11", rssi = -75)
        val bss2 = createBss("AA:BB:CC:22:22:22", rssi = -65)
        val bss3 = createBss("AA:BB:CC:33:33:33", rssi = -60)
        return createClusterWithBssids(listOf(bss1, bss2, bss3))
    }
}
