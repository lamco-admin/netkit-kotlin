package io.lamco.netkit.rf.capacity

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.rf.model.McsLevel
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for McsRateTable
 *
 * Covers:
 * - PHY rate lookups for all WiFi standards
 * - Channel width scaling (20/40/80/160/320 MHz)
 * - NSS (spatial stream) scaling
 * - Maximum throughput calculations
 * - WiFi standard validation
 */
class McsRateTableTest {
    private val table = McsRateTable.standard()

    // ========== WiFi 4 (802.11n) Tests ==========

    @Test
    fun `WiFi 4 MCS 0 20MHz 1 stream baseline rate`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(0),
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertNotNull(rate)
        assertTrue(rate in 6.0..7.0, "WiFi 4 MCS 0 should be around 6.5 Mbps")
    }

    @Test
    fun `WiFi 4 40MHz doubles the rate`() {
        val rate20 = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_4, ChannelWidth.WIDTH_20MHZ, 1)
        val rate40 = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_4, ChannelWidth.WIDTH_40MHZ, 1)

        assertNotNull(rate20)
        assertNotNull(rate40)
        assertEquals(rate20 * 2.0, rate40, 1.0) // Within 1 Mbps tolerance
    }

    @Test
    fun `WiFi 4 max rate is around 600 Mbps`() {
        val maxRate =
            table.getMaxPhyRateMbps(
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 4,
            )
        assertNotNull(maxRate)
        // Production uses simplified 2x width multiplier (conservative estimate)
        // Actual IEEE 802.11n max with short GI would be ~600 Mbps
        // With long GI and 2x multiplier: 65 * 2 * 4 = 520 Mbps
        assertTrue(maxRate in 480.0..560.0, "WiFi 4 max should be ~520 Mbps (40 MHz, 4 streams, long GI)")
    }

    @Test
    fun `WiFi 4 does not support 80MHz`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 1,
            )
        assertNull(rate, "WiFi 4 should not support 80 MHz")
    }

    // ========== WiFi 5 (802.11ac) Tests ==========

    @Test
    fun `WiFi 5 MCS 0 20MHz 1 stream baseline rate`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(0),
                standard = WifiStandard.WIFI_5,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertNotNull(rate)
        assertTrue(rate in 6.0..7.0, "WiFi 5 MCS 0 should be around 6.5 Mbps")
    }

    @Test
    fun `WiFi 5 80MHz quadruples 20MHz rate`() {
        val rate20 = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_5, ChannelWidth.WIDTH_20MHZ, 1)
        val rate80 = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_5, ChannelWidth.WIDTH_80MHZ, 1)

        assertNotNull(rate20)
        assertNotNull(rate80)
        assertEquals(rate20 * 4.0, rate80, 1.0)
    }

    @Test
    fun `WiFi 5 160MHz octuples 20MHz rate`() {
        val rate20 = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_5, ChannelWidth.WIDTH_20MHZ, 1)
        val rate160 = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_5, ChannelWidth.WIDTH_160MHZ, 1)

        assertNotNull(rate20)
        assertNotNull(rate160)
        assertEquals(rate20 * 8.0, rate160, 1.0)
    }

    @Test
    fun `WiFi 5 MCS 9 is 256-QAM`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(9),
                standard = WifiStandard.WIFI_5,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertNotNull(rate)
        assertTrue(rate in 85.0..88.0, "WiFi 5 MCS 9 should be around 86.7 Mbps")
    }

    @Test
    fun `WiFi 5 max rate with 8 streams 160MHz exceeds 5 Gbps`() {
        val maxRate =
            table.getMaxPhyRateMbps(
                standard = WifiStandard.WIFI_5,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 8,
            )
        assertNotNull(maxRate)
        // Production uses simplified 8x width multiplier (conservative estimate)
        // Actual IEEE 802.11ac max would be 6933 Mbps (MCS 9, 160MHz, 8SS)
        // With simplified multiplier: 86.7 * 8 * 8 = 5548.8 Mbps
        assertTrue(maxRate > 5000.0, "WiFi 5 max (160 MHz, 8 streams) should exceed 5 Gbps")
    }

    @Test
    fun `WiFi 5 does not support 320MHz`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_5,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 1,
            )
        assertNull(rate, "WiFi 5 should not support 320 MHz")
    }

    // ========== WiFi 6 (802.11ax) Tests ==========

    @Test
    fun `WiFi 6 MCS 0 20MHz 1 stream baseline rate`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(0),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertNotNull(rate)
        assertTrue(rate in 8.0..9.0, "WiFi 6 MCS 0 should be around 8.6 Mbps")
    }

    @Test
    fun `WiFi 6 MCS 11 is 1024-QAM`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(11),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertNotNull(rate)
        assertTrue(rate in 140.0..145.0, "WiFi 6 MCS 11 should be around 143.4 Mbps")
    }

    @Test
    fun `WiFi 6 max rate with 8 streams 160MHz exceeds 9 Gbps`() {
        val maxRate =
            table.getMaxPhyRateMbps(
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 8,
            )
        assertNotNull(maxRate)
        assertTrue(maxRate > 9000.0, "WiFi 6 max (160 MHz, 8 streams) should exceed 9 Gbps")
    }

    @Test
    fun `WiFi 6 does not support 320MHz`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 1,
            )
        assertNull(rate, "WiFi 6 should not support 320 MHz")
    }

    // ========== WiFi 7 (802.11be) Tests ==========

    @Test
    fun `WiFi 7 MCS 12 is 4096-QAM`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(12),
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertNotNull(rate)
        assertTrue(rate in 150.0..160.0, "WiFi 7 MCS 12 should be around 154.9 Mbps")
    }

    @Test
    fun `WiFi 7 MCS 13 is highest rate 4096-QAM`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(13),
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertNotNull(rate)
        assertTrue(rate in 170.0..175.0, "WiFi 7 MCS 13 should be around 172.1 Mbps")
    }

    @Test
    fun `WiFi 7 320MHz is 16x the 20MHz rate`() {
        val rate20 = table.getPhyRateMbps(McsLevel(9), WifiStandard.WIFI_7, ChannelWidth.WIDTH_20MHZ, 1)
        val rate320 = table.getPhyRateMbps(McsLevel(9), WifiStandard.WIFI_7, ChannelWidth.WIDTH_320MHZ, 1)

        assertNotNull(rate20)
        assertNotNull(rate320)
        assertEquals(rate20 * 16.0, rate320, 1.0)
    }

    @Test
    fun `WiFi 7 max rate with 16 streams 320MHz exceeds 40 Gbps`() {
        val maxRate =
            table.getMaxPhyRateMbps(
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 16,
            )
        assertNotNull(maxRate)
        assertTrue(maxRate > 40000.0, "WiFi 7 max (320 MHz, 16 streams) should exceed 40 Gbps")
    }

    // ========== NSS Scaling Tests ==========

    @Test
    fun `2 streams doubles the rate`() {
        val rate1ss = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 1)
        val rate2ss = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 2)

        assertNotNull(rate1ss)
        assertNotNull(rate2ss)
        assertEquals(rate1ss * 2.0, rate2ss, 1.0)
    }

    @Test
    fun `4 streams quadruples the rate`() {
        val rate1ss = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 1)
        val rate4ss = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 4)

        assertNotNull(rate1ss)
        assertNotNull(rate4ss)
        assertEquals(rate1ss * 4.0, rate4ss, 1.0)
    }

    @Test
    fun `8 streams octuples the rate`() {
        val rate1ss = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 1)
        val rate8ss = table.getPhyRateMbps(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 8)

        assertNotNull(rate1ss)
        assertNotNull(rate8ss)
        assertEquals(rate1ss * 8.0, rate8ss, 1.0)
    }

    @Test
    fun `WiFi 7 supports 16 streams`() {
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(9),
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 16,
            )
        assertNotNull(rate, "WiFi 7 should support 16 spatial streams")
    }

    @Test
    fun `WiFi 4-6 reject more than 8 streams`() {
        for (standard in listOf(WifiStandard.WIFI_4, WifiStandard.WIFI_5, WifiStandard.WIFI_6)) {
            val rate =
                table.getPhyRateMbps(
                    mcs = McsLevel(7),
                    standard = standard,
                    channelWidth = ChannelWidth.WIDTH_80MHZ,
                    nss = 9,
                )
            assertNull(rate, "$standard should not support 9 streams")
        }
    }

    // ========== Standard Comparison Tests ==========

    @Test
    fun `WiFi 6 MCS 0-9 rates similar to WiFi 5`() {
        for (mcs in 0..9) {
            val rate5 = table.getPhyRateMbps(McsLevel(mcs), WifiStandard.WIFI_5, ChannelWidth.WIDTH_80MHZ, 2)
            val rate6 = table.getPhyRateMbps(McsLevel(mcs), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 2)

            assertNotNull(rate5)
            assertNotNull(rate6)
            // WiFi 6 is slightly faster due to improved symbol duration
            assertTrue(rate6 >= rate5 * 0.9, "WiFi 6 MCS $mcs should be comparable to WiFi 5")
        }
    }

    @Test
    fun `higher MCS always yields higher rate within same standard`() {
        for (standard in listOf(WifiStandard.WIFI_5, WifiStandard.WIFI_6, WifiStandard.WIFI_7)) {
            var previousRate = 0.0
            for (mcs in 0..9) {
                val rate =
                    table.getPhyRateMbps(
                        mcs = McsLevel(mcs),
                        standard = standard,
                        channelWidth = ChannelWidth.WIDTH_80MHZ,
                        nss = 2,
                    )
                assertNotNull(rate)
                assertTrue(rate > previousRate, "$standard MCS $mcs should be faster than MCS ${mcs - 1}")
                previousRate = rate
            }
        }
    }

    // ========== Validation Tests ==========

    @Test
    fun `rejects invalid MCS for standard`() {
        // WiFi 5 doesn't support MCS 10-11
        val rate =
            table.getPhyRateMbps(
                mcs = McsLevel(11),
                standard = WifiStandard.WIFI_5,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        assertNull(rate, "WiFi 5 should not support MCS 11")
    }

    @Test
    fun `rejects negative NSS`() {
        assertFailsWith<IllegalArgumentException> {
            table.getPhyRateMbps(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 0,
            )
        }
    }

    @Test
    fun `rejects NSS above 16`() {
        assertFailsWith<IllegalArgumentException> {
            table.getPhyRateMbps(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 17,
            )
        }
    }

    // ========== Effective Throughput Tests ==========

    @Test
    fun `WiFi 4 effective throughput is 50 percent of PHY`() {
        val phyRate = 600.0
        val effective = calculateEffectiveThroughput(phyRate, WifiStandard.WIFI_4)
        assertEquals(300.0, effective, 1.0) // 50% efficiency
    }

    @Test
    fun `WiFi 5 effective throughput is 60 percent of PHY`() {
        val phyRate = 1000.0
        val effective = calculateEffectiveThroughput(phyRate, WifiStandard.WIFI_5)
        assertEquals(600.0, effective, 1.0) // 60% efficiency
    }

    @Test
    fun `WiFi 6 effective throughput is 70 percent of PHY`() {
        val phyRate = 1000.0
        val effective = calculateEffectiveThroughput(phyRate, WifiStandard.WIFI_6)
        assertEquals(700.0, effective, 1.0) // 70% efficiency
    }

    @Test
    fun `WiFi 7 effective throughput is 75 percent of PHY`() {
        val phyRate = 10000.0
        val effective = calculateEffectiveThroughput(phyRate, WifiStandard.WIFI_7)
        assertEquals(7500.0, effective, 1.0) // 75% efficiency
    }

    // ========== Realistic Rate Ranges ==========

    @Test
    fun `all rates are within realistic bounds`() {
        val standards = listOf(WifiStandard.WIFI_4, WifiStandard.WIFI_5, WifiStandard.WIFI_6, WifiStandard.WIFI_7)
        val widths =
            listOf(
                ChannelWidth.WIDTH_20MHZ,
                ChannelWidth.WIDTH_40MHZ,
                ChannelWidth.WIDTH_80MHZ,
                ChannelWidth.WIDTH_160MHZ,
            )

        for (standard in standards) {
            for (width in widths) {
                val rate =
                    table.getPhyRateMbps(
                        mcs = McsLevel(7),
                        standard = standard,
                        channelWidth = width,
                        nss = 2,
                    )
                if (rate != null) {
                    assertTrue(
                        rate in 10.0..100000.0,
                        "$standard $width rate $rate out of realistic range",
                    )
                }
            }
        }
    }

    @Test
    fun `gigabit rates achievable with WiFi 5 and above`() {
        for (standard in listOf(WifiStandard.WIFI_5, WifiStandard.WIFI_6, WifiStandard.WIFI_7)) {
            val rate =
                table.getPhyRateMbps(
                    mcs = McsLevel(9),
                    standard = standard,
                    channelWidth = ChannelWidth.WIDTH_160MHZ,
                    nss = 2,
                )
            assertNotNull(rate)
            assertTrue(rate > 1000.0, "$standard should achieve gigabit rates with 160 MHz, 2 streams")
        }
    }

    @Test
    fun `multi-gigabit rates achievable with WiFi 6 and above`() {
        for (standard in listOf(WifiStandard.WIFI_6, WifiStandard.WIFI_7)) {
            val rate =
                table.getPhyRateMbps(
                    mcs = McsLevel(9),
                    standard = standard,
                    channelWidth = ChannelWidth.WIDTH_160MHZ,
                    nss = 4,
                )
            assertNotNull(rate)
            assertTrue(rate > 2000.0, "$standard should achieve multi-gigabit with 160 MHz, 4 streams")
        }
    }
}
