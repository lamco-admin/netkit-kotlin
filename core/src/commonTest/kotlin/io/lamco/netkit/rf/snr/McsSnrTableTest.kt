package io.lamco.netkit.rf.snr

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.rf.model.McsLevel
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for McsSnrTable
 *
 * Covers:
 * - SNR requirements for different MCS levels
 * - Channel width penalties
 * - NSS penalties
 * - WiFi standard variations (WiFi 4/5/6/7)
 */
class McsSnrTableTest {
    private val table = StandardMcsSnrTable()

    // ========== WiFi 6 Base SNR Tests ==========

    @Test
    fun `WiFi 6 MCS 0 requires lowest SNR`() {
        val snr =
            table.requiredSnrFor(
                mcs = McsLevel(0),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertTrue(snr < 10.0, "MCS 0 (BPSK 1/2) should require minimal SNR")
    }

    @Test
    fun `WiFi 6 MCS increases require higher SNR`() {
        val snr0 = table.requiredSnrFor(McsLevel(0), WifiStandard.WIFI_6, ChannelWidth.WIDTH_20MHZ, 1)
        val snr3 = table.requiredSnrFor(McsLevel(3), WifiStandard.WIFI_6, ChannelWidth.WIDTH_20MHZ, 1)
        val snr7 = table.requiredSnrFor(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_20MHZ, 1)
        val snr11 = table.requiredSnrFor(McsLevel(11), WifiStandard.WIFI_6, ChannelWidth.WIDTH_20MHZ, 1)

        assertTrue(snr3 > snr0, "MCS 3 should require more SNR than MCS 0")
        assertTrue(snr7 > snr3, "MCS 7 should require more SNR than MCS 3")
        assertTrue(snr11 > snr7, "MCS 11 should require more SNR than MCS 7")
    }

    @Test
    fun `WiFi 6 MCS 11 requires high SNR for 1024-QAM`() {
        val snr =
            table.requiredSnrFor(
                mcs = McsLevel(11),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        assertTrue(snr > 30.0, "MCS 11 (1024-QAM 5/6) should require high SNR (>30 dB)")
    }

    // ========== WiFi 7 MCS Tests ==========

    @Test
    fun `WiFi 7 MCS 12-13 require very high SNR for 4096-QAM`() {
        val snr12 =
            table.requiredSnrFor(
                mcs = McsLevel(12),
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        val snr13 =
            table.requiredSnrFor(
                mcs = McsLevel(13),
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )

        assertTrue(snr12 > 35.0, "MCS 12 (4096-QAM) should require very high SNR")
        assertTrue(snr13 > snr12, "MCS 13 should require even higher SNR than MCS 12")
        assertTrue(snr13 > 40.0, "MCS 13 should require >40 dB SNR")
    }

    // ========== Channel Width Penalty Tests ==========

    @Test
    fun `wider channels require higher SNR - 20MHz vs 40MHz`() {
        val snr20 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )
        val snr40 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 1,
            )

        assertTrue(snr40 > snr20, "40 MHz channel should require higher SNR than 20 MHz")
        val penalty = snr40 - snr20
        assertTrue(penalty in 1.0..3.0, "40 MHz penalty should be 1-3 dB")
    }

    @Test
    fun `wider channels require higher SNR - 40MHz vs 80MHz`() {
        val snr40 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 1,
            )
        val snr80 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 1,
            )

        assertTrue(snr80 > snr40, "80 MHz channel should require higher SNR than 40 MHz")
        val penalty = snr80 - snr40
        assertTrue(penalty in 1.0..3.0, "80 MHz penalty should be 1-3 dB")
    }

    @Test
    fun `wider channels require higher SNR - 80MHz vs 160MHz`() {
        val snr80 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 1,
            )
        val snr160 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 1,
            )

        assertTrue(snr160 > snr80, "160 MHz channel should require higher SNR than 80 MHz")
        val penalty = snr160 - snr80
        assertTrue(penalty in 1.0..3.0, "160 MHz penalty should be 1-3 dB")
    }

    @Test
    fun `WiFi 7 320MHz channel has highest SNR requirement`() {
        val snr160 =
            table.requiredSnrFor(
                mcs = McsLevel(9),
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 1,
            )
        val snr320 =
            table.requiredSnrFor(
                mcs = McsLevel(9),
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 1,
            )

        assertTrue(snr320 > snr160, "320 MHz should require higher SNR than 160 MHz")
    }

    // ========== NSS Penalty Tests ==========

    @Test
    fun `more spatial streams require higher SNR`() {
        val snr1ss =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 1,
            )
        val snr2ss =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        val snr4ss =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 4,
            )

        assertTrue(snr2ss > snr1ss, "2 streams should require higher SNR than 1 stream")
        assertTrue(snr4ss > snr2ss, "4 streams should require higher SNR than 2 streams")
    }

    @Test
    fun `NSS penalty is reasonable - 1dB per stream`() {
        val snr1 = table.requiredSnrFor(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 1)
        val snr2 = table.requiredSnrFor(McsLevel(7), WifiStandard.WIFI_6, ChannelWidth.WIDTH_80MHZ, 2)

        val penalty = snr2 - snr1
        assertTrue(penalty in 0.5..1.5, "NSS penalty should be approximately 1 dB per stream")
    }

    // ========== WiFi Standard Comparison Tests ==========

    @Test
    fun `WiFi 5 and WiFi 6 have similar base SNR requirements`() {
        // WiFi 6 is slightly better due to improved coding
        val snrWiFi5 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_5,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        val snrWiFi6 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        // Should be within 1-2 dB
        assertTrue(kotlin.math.abs(snrWiFi5 - snrWiFi6) < 2.0, "WiFi 5/6 SNR requirements should be similar")
    }

    @Test
    fun `WiFi 4 has slightly higher SNR requirements`() {
        // WiFi 4 (802.11n) has less efficient coding
        val snrWiFi4 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 2,
            )
        val snrWiFi6 =
            table.requiredSnrFor(
                mcs = McsLevel(7),
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 2,
            )

        assertTrue(snrWiFi4 >= snrWiFi6, "WiFi 4 should require same or higher SNR than WiFi 6")
    }

    // ========== Realistic SNR Range Tests ==========

    @Test
    fun `all SNR requirements are within realistic range`() {
        val standards = listOf(WifiStandard.WIFI_4, WifiStandard.WIFI_5, WifiStandard.WIFI_6, WifiStandard.WIFI_7)
        val widths = listOf(ChannelWidth.WIDTH_20MHZ, ChannelWidth.WIDTH_40MHZ, ChannelWidth.WIDTH_80MHZ)
        val mcsLevels = listOf(0, 3, 7, 9)

        for (standard in standards) {
            for (width in widths) {
                for (mcsLevel in mcsLevels) {
                    if (standard == WifiStandard.WIFI_4 && mcsLevel > 7) continue
                    if (standard == WifiStandard.WIFI_5 && mcsLevel > 9) continue

                    val snr =
                        table.requiredSnrFor(
                            mcs = McsLevel(mcsLevel),
                            standard = standard,
                            channelWidth = width,
                            nss = 1,
                        )

                    assertTrue(
                        snr in 0.0..50.0,
                        "SNR requirement $snr out of realistic range for $standard MCS $mcsLevel $width",
                    )
                }
            }
        }
    }

    @Test
    fun `low MCS requires SNR below 15 dB`() {
        for (standard in listOf(WifiStandard.WIFI_5, WifiStandard.WIFI_6, WifiStandard.WIFI_7)) {
            val snr =
                table.requiredSnrFor(
                    mcs = McsLevel(0),
                    standard = standard,
                    channelWidth = ChannelWidth.WIDTH_20MHZ,
                    nss = 1,
                )
            assertTrue(snr < 15.0, "$standard MCS 0 should require SNR < 15 dB")
        }
    }

    @Test
    fun `high MCS requires SNR above 25 dB`() {
        for (standard in listOf(WifiStandard.WIFI_5, WifiStandard.WIFI_6, WifiStandard.WIFI_7)) {
            val snr =
                table.requiredSnrFor(
                    mcs = McsLevel(9),
                    standard = standard,
                    channelWidth = ChannelWidth.WIDTH_20MHZ,
                    nss = 1,
                )
            assertTrue(snr > 25.0, "$standard MCS 9 should require SNR > 25 dB")
        }
    }

    // ========== Edge Cases ==========

    @Test
    fun `WiFi 4 MCS 0-7 are supported`() {
        for (mcs in 0..7) {
            val snr =
                table.requiredSnrFor(
                    mcs = McsLevel(mcs),
                    standard = WifiStandard.WIFI_4,
                    channelWidth = ChannelWidth.WIDTH_20MHZ,
                    nss = 1,
                )
            assertTrue(snr > 0.0, "WiFi 4 MCS $mcs should be supported")
        }
    }

    @Test
    fun `WiFi 5 MCS 0-9 are supported`() {
        for (mcs in 0..9) {
            val snr =
                table.requiredSnrFor(
                    mcs = McsLevel(mcs),
                    standard = WifiStandard.WIFI_5,
                    channelWidth = ChannelWidth.WIDTH_80MHZ,
                    nss = 1,
                )
            assertTrue(snr > 0.0, "WiFi 5 MCS $mcs should be supported")
        }
    }

    @Test
    fun `WiFi 6 MCS 0-11 are supported`() {
        for (mcs in 0..11) {
            val snr =
                table.requiredSnrFor(
                    mcs = McsLevel(mcs),
                    standard = WifiStandard.WIFI_6,
                    channelWidth = ChannelWidth.WIDTH_80MHZ,
                    nss = 1,
                )
            assertTrue(snr > 0.0, "WiFi 6 MCS $mcs should be supported")
        }
    }

    @Test
    fun `WiFi 7 MCS 0-13 are supported`() {
        for (mcs in 0..13) {
            val snr =
                table.requiredSnrFor(
                    mcs = McsLevel(mcs),
                    standard = WifiStandard.WIFI_7,
                    channelWidth = ChannelWidth.WIDTH_160MHZ,
                    nss = 1,
                )
            assertTrue(snr > 0.0, "WiFi 7 MCS $mcs should be supported")
        }
    }
}
