package io.lamco.netkit.model.topology

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class ClusteredBssTest {
    @Test
    fun `creates valid clustered BSS`() {
        val bss =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
                roamingCapabilities = RoamingCapabilities.basic(),
                rssiDbm = -65,
            )

        assertEquals("AA:BB:CC:DD:EE:FF", bss.bssid)
        assertEquals(WiFiBand.BAND_5GHZ, bss.band)
        assertEquals(36, bss.channel)
        assertEquals(-65, bss.rssiDbm)
    }

    @Test
    fun `requires non-blank BSSID`() {
        assertThrows<IllegalArgumentException> {
            ClusteredBss(
                bssid = "",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )
        }
    }

    @Test
    fun `requires positive channel`() {
        assertThrows<IllegalArgumentException> {
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 0,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )
        }
    }

    @Test
    fun `requires positive frequency`() {
        assertThrows<IllegalArgumentException> {
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 0,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )
        }
    }

    @Test
    fun `validates RSSI range`() {
        assertThrows<IllegalArgumentException> {
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
                rssiDbm = -150, // Too low
            )
        }

        assertThrows<IllegalArgumentException> {
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
                rssiDbm = 10, // Too high
            )
        }
    }

    @Test
    fun `extracts OUI from BSSID`() {
        val bss =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )

        assertEquals("AA:BB:CC", bss.oui)
    }

    @Test
    fun `identifies WiFi 6E correctly`() {
        val wifi6e =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_6GHZ,
                channel = 1,
                frequencyMHz = 5955,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                wifiStandard = WifiStandard.WIFI_6E,
            )

        assertTrue(wifi6e.isWiFi6E)
    }

    @Test
    fun `identifies WiFi 7 correctly`() {
        val wifi7 =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_6GHZ,
                channel = 1,
                frequencyMHz = 5955,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                wifiStandard = WifiStandard.WIFI_7,
            )

        assertTrue(wifi7.isWiFi7)
    }

    @Test
    fun `identifies wide channels`() {
        val wide80 =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )

        val narrow20 =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                wifiStandard = WifiStandard.WIFI_6,
            )

        assertTrue(wide80.supportsWideChannels)
        assertFalse(narrow20.supportsWideChannels)
    }

    @Test
    fun `isActive detects recent activity`() {
        val now = System.currentTimeMillis()
        val recent =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
                lastSeenTimestamp = now,
            )

        assertTrue(recent.isActive(now))
    }

    @Test
    fun `isActive detects stale BSS`() {
        val now = System.currentTimeMillis()
        val stale =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
                lastSeenTimestamp = now - 120_000, // 2 minutes ago
            )

        assertFalse(stale.isActive(now))
    }

    @Test
    fun `signal strength categories are correct`() {
        val excellent = createBss(rssiDbm = -45)
        val veryGood = createBss(rssiDbm = -55)
        val good = createBss(rssiDbm = -65)
        val fair = createBss(rssiDbm = -75)
        val poor = createBss(rssiDbm = -85)

        assertEquals(SignalStrength.EXCELLENT, excellent.signalStrength)
        assertEquals(SignalStrength.VERY_GOOD, veryGood.signalStrength)
        assertEquals(SignalStrength.GOOD, good.signalStrength)
        assertEquals(SignalStrength.FAIR, fair.signalStrength)
        assertEquals(SignalStrength.POOR, poor.signalStrength)
    }

    @Test
    fun `signal strength is UNKNOWN when RSSI is null`() {
        val unknown = createBss(rssiDbm = null)
        assertEquals(SignalStrength.UNKNOWN, unknown.signalStrength)
    }

    @Test
    fun `good roaming candidate requires strong signal and fast roaming`() {
        val goodCandidate =
            ClusteredBss(
                bssid = "AA:BB:CC:DD:EE:FF",
                band = WiFiBand.BAND_5GHZ,
                channel = 36,
                frequencyMHz = 5180,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                wifiStandard = WifiStandard.WIFI_6,
                roamingCapabilities = RoamingCapabilities.fullSuite(AuthType.WPA2_PSK),
                rssiDbm = -65,
            )

        assertTrue(goodCandidate.isGoodRoamingCandidate)
    }

    @Test
    fun `weak signal is not good roaming candidate`() {
        val weak = createBss(rssiDbm = -85)
        assertFalse(weak.isGoodRoamingCandidate)
    }

    @Test
    fun `summary includes key information`() {
        val bss = createBss(rssiDbm = -65)
        val summary = bss.summary

        assertTrue(summary.contains("AA:BB:CC"))
        assertTrue(summary.contains("5 GHz"))
        assertTrue(summary.contains("-65"))
    }

    @Test
    fun `withUpdatedSignal creates new instance with updated RSSI`() {
        val original = createBss(rssiDbm = -70)
        val updated = original.withUpdatedSignal(-60)

        assertEquals(-70, original.rssiDbm)
        assertEquals(-60, updated.rssiDbm)
    }

    private fun createBss(rssiDbm: Int?) =
        ClusteredBss(
            bssid = "AA:BB:CC:DD:EE:FF",
            band = WiFiBand.BAND_5GHZ,
            channel = 36,
            frequencyMHz = 5180,
            channelWidth = ChannelWidth.WIDTH_80MHZ,
            wifiStandard = WifiStandard.WIFI_6,
            rssiDbm = rssiDbm,
        )
}
