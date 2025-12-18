package io.lamco.netkit.rf.capacity

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.rf.noise.StaticNoiseModel
import io.lamco.netkit.rf.snr.SnrCalculator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Unit tests for CapacityEstimator
 *
 * Covers:
 * - Capacity estimation from RSSI/SNR
 * - PHY rate to effective throughput conversion
 * - Channel utilization adjustment
 * - Uplink/downlink asymmetry
 * - NSS estimation heuristics
 * - Multi-BSS comparison
 */
class CapacityEstimatorTest {
    private val noiseModel = StaticNoiseModel.default()
    private val snrCalculator = SnrCalculator(noiseModel)
    private val estimator = CapacityEstimator(snrCalculator)

    // ========== Basic Capacity Estimation Tests ==========

    @Test
    fun `estimateCapacity returns valid metrics for good signal`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -50,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        assertEquals("00:11:22:33:44:55", capacity.bssid)
        assertEquals(WiFiBand.BAND_5GHZ, capacity.band)
        assertEquals(ChannelWidth.WIDTH_80MHZ, capacity.channelWidth)
        assertEquals(2, capacity.nss)
        assertNotNull(capacity.maxMcs, "Good signal should support some MCS")
        assertNotNull(capacity.maxPhyRateMbps)
        assertNotNull(capacity.estimatedEffectiveDownlinkMbps)
    }

    @Test
    fun `stronger signal yields higher capacity`() {
        val capacityWeak =
            estimator.estimateCapacity(
                bssid = "AA:BB:CC:DD:EE:FF",
                rssiDbm = -75,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        val capacityStrong =
            estimator.estimateCapacity(
                bssid = "AA:BB:CC:DD:EE:FF",
                rssiDbm = -50,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        if (capacityWeak.estimatedEffectiveDownlinkMbps != null &&
            capacityStrong.estimatedEffectiveDownlinkMbps != null
        ) {
            assertTrue(
                capacityStrong.estimatedEffectiveDownlinkMbps!! > capacityWeak.estimatedEffectiveDownlinkMbps!!,
                "Stronger signal should yield higher capacity",
            )
        }
    }

    @Test
    fun `very weak signal may not support any MCS`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -95, // Very weak signal
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        // Very weak signal may not support any MCS with default margin
        // This is acceptable behavior
        assertTrue(capacity.maxMcs == null || capacity.maxMcs!!.level <= 1)
    }

    // ========== Protocol Overhead Tests ==========

    @Test
    fun `WiFi 6 effective throughput is 70 percent of PHY`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -50,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        if (capacity.maxPhyRateMbps != null && capacity.estimatedEffectiveDownlinkMbps != null) {
            val efficiency = capacity.estimatedEffectiveDownlinkMbps!! / capacity.maxPhyRateMbps!!
            assertTrue(efficiency in 0.65..0.75, "WiFi 6 efficiency should be ~70%")
        }
    }

    @Test
    fun `WiFi 7 has highest efficiency`() {
        val capacity7 =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -50,
                band = WiFiBand.BAND_6GHZ,
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 4,
            )
        val capacity6 =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -50,
                band = WiFiBand.BAND_6GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 4,
            )

        if (capacity7.maxPhyRateMbps != null &&
            capacity7.estimatedEffectiveDownlinkMbps != null &&
            capacity6.maxPhyRateMbps != null &&
            capacity6.estimatedEffectiveDownlinkMbps != null
        ) {
            val efficiency7 = capacity7.estimatedEffectiveDownlinkMbps!! / capacity7.maxPhyRateMbps!!
            val efficiency6 = capacity6.estimatedEffectiveDownlinkMbps!! / capacity6.maxPhyRateMbps!!
            assertTrue(efficiency7 > efficiency6, "WiFi 7 should have higher efficiency than WiFi 6")
        }
    }

    @Test
    fun `WiFi 4 has lowest efficiency`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -50,
                band = WiFiBand.BAND_2_4GHZ,
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 2,
            )

        if (capacity.maxPhyRateMbps != null && capacity.estimatedEffectiveDownlinkMbps != null) {
            val efficiency = capacity.estimatedEffectiveDownlinkMbps!! / capacity.maxPhyRateMbps!!
            assertTrue(efficiency in 0.45..0.55, "WiFi 4 efficiency should be ~50%")
        }
    }

    // ========== Channel Utilization Tests ==========

    @Test
    fun `channel utilization reduces available capacity`() {
        val noUtil =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                channelUtilizationPct = 0.0,
            )
        val highUtil =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                channelUtilizationPct = 50.0,
            )

        assertNotNull(noUtil.utilizationAdjustedDownlinkMbps)
        assertNotNull(highUtil.utilizationAdjustedDownlinkMbps)
        assertTrue(
            highUtil.utilizationAdjustedDownlinkMbps!! < noUtil.utilizationAdjustedDownlinkMbps!!,
            "High utilization should reduce available capacity",
        )
    }

    @Test
    fun `100 percent utilization yields zero available capacity`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                channelUtilizationPct = 100.0,
            )

        assertNotNull(capacity.utilizationAdjustedDownlinkMbps)
        assertEquals(
            0.0,
            capacity.utilizationAdjustedDownlinkMbps!!,
            0.1,
            "100% utilization should leave no available capacity",
        )
    }

    @Test
    fun `50 percent utilization leaves 50 percent capacity`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                channelUtilizationPct = 50.0,
            )

        if (capacity.estimatedEffectiveDownlinkMbps != null &&
            capacity.utilizationAdjustedDownlinkMbps != null
        ) {
            assertEquals(
                capacity.estimatedEffectiveDownlinkMbps!! * 0.5,
                capacity.utilizationAdjustedDownlinkMbps!!,
                1.0,
            )
        }
    }

    @Test
    fun `null utilization returns full effective capacity as available`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                channelUtilizationPct = null,
            )

        assertEquals(
            capacity.estimatedEffectiveDownlinkMbps,
            capacity.utilizationAdjustedDownlinkMbps,
        )
    }

    // ========== Uplink/Downlink Asymmetry Tests ==========

    @Test
    fun `uplink is 75 percent of downlink by default`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        if (capacity.estimatedEffectiveDownlinkMbps != null &&
            capacity.estimatedEffectiveUplinkMbps != null
        ) {
            assertEquals(
                capacity.estimatedEffectiveDownlinkMbps!! * 0.75,
                capacity.estimatedEffectiveUplinkMbps!!,
                1.0,
            )
        }
    }

    @Test
    fun `custom uplink ratio works correctly`() {
        val customEstimator = CapacityEstimator(snrCalculator, uplinkRatio = 0.9)
        val capacity =
            customEstimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        if (capacity.estimatedEffectiveDownlinkMbps != null &&
            capacity.estimatedEffectiveUplinkMbps != null
        ) {
            assertEquals(
                capacity.estimatedEffectiveDownlinkMbps!! * 0.9,
                capacity.estimatedEffectiveUplinkMbps!!,
                1.0,
            )
        }
    }

    // ========== NSS Estimation Tests ==========

    @Test
    fun `WiFi 6 80MHz estimates 2 streams when NSS not provided`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = null, // Let estimator decide
            )

        assertEquals(2, capacity.nss, "WiFi 6 80 MHz should estimate 2 streams")
    }

    @Test
    fun `WiFi 6 160MHz estimates 4 streams when NSS not provided`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = null,
            )

        assertEquals(4, capacity.nss, "WiFi 6 160 MHz should estimate 4 streams")
    }

    @Test
    fun `WiFi 7 320MHz estimates 8 streams when NSS not provided`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_6GHZ,
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = null,
            )

        assertEquals(8, capacity.nss, "WiFi 7 320 MHz should estimate 8 streams")
    }

    @Test
    fun `WiFi 4 20MHz estimates 1 stream when NSS not provided`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_2_4GHZ,
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = null,
            )

        assertEquals(1, capacity.nss, "WiFi 4 20 MHz should estimate 1 stream")
    }

    // ========== Multi-BSS Comparison Tests ==========

    @Test
    fun `estimateCapacityForMultipleBss sorts by downlink capacity`() {
        val bssDataList =
            listOf(
                BssCapacityData(
                    bssid = "AA:AA:AA:AA:AA:AA",
                    rssiDbm = -75, // Weak
                    band = WiFiBand.BAND_5GHZ,
                    standard = WifiStandard.WIFI_5,
                    channelWidth = ChannelWidth.WIDTH_80MHZ,
                    nss = 2,
                ),
                BssCapacityData(
                    bssid = "BB:BB:BB:BB:BB:BB",
                    rssiDbm = -50, // Strong
                    band = WiFiBand.BAND_5GHZ,
                    standard = WifiStandard.WIFI_6,
                    channelWidth = ChannelWidth.WIDTH_160MHZ,
                    nss = 4,
                ),
                BssCapacityData(
                    bssid = "CC:CC:CC:CC:CC:CC",
                    rssiDbm = -65, // Medium
                    band = WiFiBand.BAND_5GHZ,
                    standard = WifiStandard.WIFI_6,
                    channelWidth = ChannelWidth.WIDTH_80MHZ,
                    nss = 2,
                ),
            )

        val capacities = estimator.estimateCapacityForMultipleBss(bssDataList)

        // Should be sorted by effective downlink (highest first)
        for (i in 0 until capacities.size - 1) {
            val current = capacities[i].estimatedEffectiveDownlinkMbps ?: 0.0
            val next = capacities[i + 1].estimatedEffectiveDownlinkMbps ?: 0.0
            assertTrue(current >= next, "Should be sorted by downlink capacity (descending)")
        }

        // Best BSS should be BB (strong signal, WiFi 6, 160 MHz, 4 streams)
        assertEquals("BB:BB:BB:BB:BB:BB", capacities.first().bssid)
    }

    @Test
    fun `findBestCapacityBss returns highest capacity BSS`() {
        val bssDataList =
            listOf(
                BssCapacityData(
                    bssid = "AA:AA:AA:AA:AA:AA",
                    rssiDbm = -70,
                    band = WiFiBand.BAND_2_4GHZ,
                    standard = WifiStandard.WIFI_4,
                    channelWidth = ChannelWidth.WIDTH_20MHZ,
                    nss = 1,
                ),
                BssCapacityData(
                    bssid = "BB:BB:BB:BB:BB:BB",
                    rssiDbm = -55,
                    band = WiFiBand.BAND_5GHZ,
                    standard = WifiStandard.WIFI_6,
                    channelWidth = ChannelWidth.WIDTH_80MHZ,
                    nss = 2,
                ),
            )

        val best = estimator.findBestCapacityBss(bssDataList)

        assertEquals("BB:BB:BB:BB:BB:BB", best.bssid, "WiFi 6 5GHz should have higher capacity")
    }

    // ========== Gigabit Capability Tests ==========

    @Test
    fun `WiFi 6 with good signal achieves gigabit capacity`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -50,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 2,
            )

        assertTrue(capacity.isGigabitCapable, "WiFi 6 160 MHz 2 streams should achieve gigabit")
    }

    @Test
    fun `WiFi 7 with good signal achieves multi-gigabit capacity`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -50,
                band = WiFiBand.BAND_6GHZ,
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 4,
            )

        assertTrue(capacity.isMultiGigabitCapable, "WiFi 7 320 MHz 4 streams should achieve multi-gigabit")
    }

    @Test
    fun `WiFi 4 does not achieve gigabit capacity`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -50,
                band = WiFiBand.BAND_2_4GHZ,
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 2,
            )

        assertFalse(capacity.isGigabitCapable, "WiFi 4 should not achieve gigabit effective throughput")
    }

    // ========== Capacity Category Tests ==========

    @Test
    fun `WiFi 7 320MHz achieves ultra-high capacity`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -45,
                band = WiFiBand.BAND_6GHZ,
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 8,
            )

        // CapacityClass enum: ULTRA_HIGH=0, VERY_HIGH=1, HIGH=2, MEDIUM=3, LOW=4, VERY_LOW=5
        assertTrue(
            capacity.capacityCategory.ordinal <= 1, // ULTRA_HIGH (0) or VERY_HIGH (1)
            "WiFi 7 max config should achieve very high/ultra-high capacity",
        )
    }

    @Test
    fun `WiFi 4 achieves low to medium capacity`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_2_4GHZ,
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 2,
            )

        // CapacityClass enum: ULTRA_HIGH=0, VERY_HIGH=1, HIGH=2, MEDIUM=3, LOW=4, VERY_LOW=5
        assertTrue(
            capacity.capacityCategory.ordinal in 3..4, // MEDIUM (3) or LOW (4)
            "WiFi 4 should achieve low to medium capacity",
        )
    }

    // ========== Validation Tests ==========

    @Test
    fun `rejects RSSI below -120 dBm`() {
        assertThrows<IllegalArgumentException> {
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -121,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        }
    }

    @Test
    fun `rejects RSSI above 0 dBm`() {
        assertThrows<IllegalArgumentException> {
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = 1,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        }
    }

    @Test
    fun `rejects utilization below 0 percent`() {
        assertThrows<IllegalArgumentException> {
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                channelUtilizationPct = -1.0,
            )
        }
    }

    @Test
    fun `rejects utilization above 100 percent`() {
        assertThrows<IllegalArgumentException> {
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                channelUtilizationPct = 101.0,
            )
        }
    }

    @Test
    fun `rejects empty BSS list for multi-BSS estimation`() {
        assertThrows<IllegalArgumentException> {
            estimator.estimateCapacityForMultipleBss(emptyList())
        }
    }

    // ========== Capacity Comparison Tests ==========

    @Test
    fun `compareCapacity identifies better BSS`() {
        val capacity1 =
            estimator.estimateCapacity(
                bssid = "AA:AA:AA:AA:AA:AA",
                rssiDbm = -70,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_5,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        val capacity2 =
            estimator.estimateCapacity(
                bssid = "BB:BB:BB:BB:BB:BB",
                rssiDbm = -50,
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 4,
            )

        val comparison = compareCapacity(capacity1, capacity2)

        assertEquals("BB:BB:BB:BB:BB:BB", comparison.preferredBssid)
        assertFalse(comparison.bss1IsBetter)
        assertTrue(comparison.capacityDifferenceMbps > 0.0)
    }

    @Test
    fun `compareCapacity detects significant differences`() {
        val capacity1 =
            estimator.estimateCapacity(
                bssid = "AA:AA:AA:AA:AA:AA",
                rssiDbm = -80, // Weak signal
                band = WiFiBand.BAND_2_4GHZ,
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        val capacity2 =
            estimator.estimateCapacity(
                bssid = "BB:BB:BB:BB:BB:BB",
                rssiDbm = -50, // Strong signal
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 4,
            )

        val comparison = compareCapacity(capacity1, capacity2)

        assertTrue(
            comparison.isSignificantDifference,
            "Should detect significant difference (>= 20%)",
        )
    }

    // ========== Realistic Scenarios ==========

    @Test
    fun `typical home WiFi 6 router scenario`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -55, // Good signal in same room
                band = WiFiBand.BAND_5GHZ,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                channelUtilizationPct = 20.0,
            )

        // Should achieve 600-900 Mbps effective downlink
        if (capacity.estimatedEffectiveDownlinkMbps != null) {
            assertTrue(
                capacity.estimatedEffectiveDownlinkMbps!! in 500.0..1200.0,
                "Typical WiFi 6 should achieve 500-1200 Mbps",
            )
        }
    }

    @Test
    fun `legacy WiFi 4 scenario`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -65,
                band = WiFiBand.BAND_2_4GHZ,
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )

        // Should achieve 10-30 Mbps effective downlink
        if (capacity.estimatedEffectiveDownlinkMbps != null) {
            assertTrue(
                capacity.estimatedEffectiveDownlinkMbps!! in 5.0..50.0,
                "WiFi 4 should achieve modest throughput",
            )
        }
    }

    @Test
    fun `high-performance WiFi 7 scenario`() {
        val capacity =
            estimator.estimateCapacity(
                bssid = "00:11:22:33:44:55",
                rssiDbm = -45, // Excellent signal
                band = WiFiBand.BAND_6GHZ,
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 8,
                channelUtilizationPct = 10.0,
            )

        // Should achieve multi-gigabit throughput
        if (capacity.estimatedEffectiveDownlinkMbps != null) {
            assertTrue(
                capacity.estimatedEffectiveDownlinkMbps!! > 5000.0,
                "WiFi 7 max config should exceed 5 Gbps",
            )
        }
    }
}
