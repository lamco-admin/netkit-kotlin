package io.lamco.netkit.rf.snr

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.model.network.WifiStandard
import io.lamco.netkit.rf.model.McsLevel
import io.lamco.netkit.rf.noise.StaticNoiseModel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Unit tests for SnrCalculator
 *
 * Covers:
 * - SNR calculation (RSSI - Noise)
 * - Link margin calculation
 * - Maximum achievable MCS determination
 * - Edge cases and boundary conditions
 */
class SnrCalculatorTest {
    private val noiseModel = StaticNoiseModel.default()
    private val calculator = SnrCalculator(noiseModel)
    private val mcsTable = McsSnrTable.default() // For test access to SNR requirements

    // ========== SNR Calculation Tests ==========

    @Test
    fun `calculateSnr computes correct SNR for 2_4GHz`() {
        // Default noise floor for 2.4 GHz: -92 dBm
        val rssi = -65
        val snr = calculator.calculateSnr(rssi, WiFiBand.BAND_2_4GHZ)
        assertEquals(27.0, snr, 0.1) // -65 - (-92) = 27 dB
    }

    @Test
    fun `calculateSnr computes correct SNR for 5GHz`() {
        // Default noise floor for 5 GHz: -95 dBm
        val rssi = -60
        val snr = calculator.calculateSnr(rssi, WiFiBand.BAND_5GHZ)
        assertEquals(35.0, snr, 0.1) // -60 - (-95) = 35 dB
    }

    @Test
    fun `calculateSnr computes correct SNR for 6GHz`() {
        // Default noise floor for 6 GHz: -96 dBm
        val rssi = -55
        val snr = calculator.calculateSnr(rssi, WiFiBand.BAND_6GHZ)
        assertEquals(41.0, snr, 0.1) // -55 - (-96) = 41 dB
    }

    @Test
    fun `calculateSnr with frequency delegates to band calculation`() {
        val rssi = -70
        val snr1 = calculator.calculateSnr(rssi, WiFiBand.BAND_5GHZ)
        val snr2 = calculator.calculateSnr(rssi, 5180, WiFiBand.BAND_5GHZ)
        assertEquals(snr1, snr2, 0.1)
    }

    @Test
    fun `calculateSnr handles poor signal correctly`() {
        val rssi = -90 // Very poor signal
        val snr = calculator.calculateSnr(rssi, WiFiBand.BAND_5GHZ)
        assertEquals(5.0, snr, 0.1) // -90 - (-95) = 5 dB (minimal SNR)
    }

    @Test
    fun `calculateSnr handles excellent signal correctly`() {
        val rssi = -40 // Excellent signal
        val snr = calculator.calculateSnr(rssi, WiFiBand.BAND_5GHZ)
        assertEquals(55.0, snr, 0.1) // -40 - (-95) = 55 dB (very high SNR)
    }

    @Test
    fun `calculateSnr rejects invalid RSSI below -120 dBm`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculateSnr(-121, WiFiBand.BAND_5GHZ)
        }
    }

    @Test
    fun `calculateSnr rejects invalid RSSI above 0 dBm`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculateSnr(1, WiFiBand.BAND_5GHZ)
        }
    }

    // ========== Link Margin Tests ==========

    @Test
    fun `calculateLinkMargin returns positive margin for good signal`() {
        val snr = 30.0 // Good SNR
        val mcs = McsLevel(7) // 64-QAM requires ~22 dB SNR
        val margin =
            calculator.calculateLinkMargin(
                snrDb = snr,
                mcs = mcs,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        assertTrue(margin > 0.0, "Link margin should be positive for good signal")
    }

    @Test
    fun `calculateLinkMargin returns negative margin for insufficient SNR`() {
        val snr = 15.0 // Insufficient SNR
        val mcs = McsLevel(9) // High MCS requires high SNR
        val margin =
            calculator.calculateLinkMargin(
                snrDb = snr,
                mcs = mcs,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        assertTrue(margin < 0.0, "Link margin should be negative for insufficient SNR")
    }

    @Test
    fun `calculateLinkMargin is approximately zero at threshold`() {
        // Find the required SNR for MCS 0 (lowest)
        val mcs = McsLevel(0)
        val requiredSnr =
            mcsTable.requiredSnrFor(
                mcs = mcs,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )

        val margin =
            calculator.calculateLinkMargin(
                snrDb = requiredSnr,
                mcs = mcs,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                nss = 1,
            )

        assertEquals(0.0, margin, 0.5) // Should be approximately zero
    }

    // ========== Maximum Achievable MCS Tests ==========

    @Test
    fun `maxAchievableMcs returns null for insufficient SNR`() {
        val snr = 3.0 // Very low SNR, below minimum for any MCS
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )
        assertNull(maxMcs, "Should return null when SNR too low for any MCS")
    }

    @Test
    fun `maxAchievableMcs returns low MCS for poor SNR`() {
        // 12 dB SNR with 20MHz, 1 stream, 3dB margin:
        // MCS 0 requires: 6.0 (base) + 0 (width) + 0 (nss) = 6.0 dB
        // Margin: 12.0 - 6.0 = 6.0 dB >= 3.0 ✓
        // MCS 1 requires: 8.0 dB, margin = 4.0 dB >= 3.0 ✓
        // MCS 2 requires: 10.0 dB, margin = 2.0 dB < 3.0 ✗
        val snr = 12.0 // Low SNR, supports only basic MCS
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_20MHZ, // 20MHz to avoid width penalty
                nss = 1, // 1 stream to avoid NSS penalty
                minMargin = 3.0,
            )
        assertNotNull(maxMcs)
        assertTrue(maxMcs.level <= 2, "Should return low MCS (0-2) for poor SNR with 20MHz 1SS")
    }

    @Test
    fun `maxAchievableMcs returns medium MCS for moderate SNR`() {
        val snr = 25.0 // Moderate SNR
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                minMargin = 3.0,
            )
        assertNotNull(maxMcs)
        assertTrue(maxMcs.level in 4..7, "Should return medium MCS (4-7) for moderate SNR")
    }

    @Test
    fun `maxAchievableMcs returns high MCS for excellent SNR`() {
        val snr = 40.0 // Excellent SNR
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                minMargin = 3.0,
            )
        assertNotNull(maxMcs)
        assertTrue(maxMcs.level >= 8, "Should return high MCS (8+) for excellent SNR")
    }

    @Test
    fun `maxAchievableMcs respects minimum link margin`() {
        val snr = 25.0
        val maxMcsWithMargin3 =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                minMargin = 3.0,
            )
        val maxMcsWithMargin5 =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                minMargin = 5.0,
            )

        // Higher margin requirement should yield lower or equal MCS
        if (maxMcsWithMargin3 != null && maxMcsWithMargin5 != null) {
            assertTrue(
                maxMcsWithMargin5.level <= maxMcsWithMargin3.level,
                "Higher margin requirement should yield lower MCS",
            )
        }
    }

    @Test
    fun `maxAchievableMcs handles WiFi 4 MCS range`() {
        val snr = 30.0
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_4,
                channelWidth = ChannelWidth.WIDTH_40MHZ,
                nss = 2,
                minMargin = 3.0,
            )
        assertNotNull(maxMcs)
        assertTrue(maxMcs.level <= 31, "WiFi 4 MCS should not exceed 31")
    }

    @Test
    fun `maxAchievableMcs handles WiFi 5 MCS range`() {
        val snr = 35.0
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_5,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
                minMargin = 3.0,
            )
        assertNotNull(maxMcs)
        assertTrue(maxMcs.level <= 9, "WiFi 5 MCS should not exceed 9")
    }

    @Test
    fun `maxAchievableMcs handles WiFi 6 MCS range`() {
        val snr = 40.0
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_160MHZ,
                nss = 4,
                minMargin = 3.0,
            )
        assertNotNull(maxMcs)
        assertTrue(maxMcs.level <= 11, "WiFi 6 MCS should not exceed 11")
    }

    @Test
    fun `maxAchievableMcs handles WiFi 7 MCS range`() {
        val snr = 45.0
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_7,
                channelWidth = ChannelWidth.WIDTH_320MHZ,
                nss = 8,
                minMargin = 3.0,
            )
        assertNotNull(maxMcs)
        assertTrue(maxMcs.level <= 13, "WiFi 7 MCS should not exceed 13")
    }

    // ========== Integration Tests ==========

    @Test
    fun `full workflow - weak signal yields low MCS`() {
        val rssi = -85 // Weak signal
        val snr = calculator.calculateSnr(rssi, WiFiBand.BAND_5GHZ)
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        assertTrue(snr < 15.0, "Weak signal should produce low SNR")
        if (maxMcs != null) {
            assertTrue(maxMcs.level <= 3, "Weak signal should yield low MCS")
        }
    }

    @Test
    fun `full workflow - strong signal yields high MCS`() {
        val rssi = -50 // Strong signal
        val snr = calculator.calculateSnr(rssi, WiFiBand.BAND_5GHZ)
        val maxMcs =
            calculator.maxAchievableMcs(
                snrDb = snr,
                standard = WifiStandard.WIFI_6,
                channelWidth = ChannelWidth.WIDTH_80MHZ,
                nss = 2,
            )

        assertTrue(snr > 40.0, "Strong signal should produce high SNR")
        assertNotNull(maxMcs, "Strong signal should support some MCS")
        assertTrue(maxMcs.level >= 8, "Strong signal should yield high MCS")
    }

    @Test
    fun `SNR increases with better (more negative) noise floor`() {
        val rssi = -70
        val defaultCalc = SnrCalculator(StaticNoiseModel.default())
        val optimisticCalc = SnrCalculator(StaticNoiseModel.optimistic())

        val defaultSnr = defaultCalc.calculateSnr(rssi, WiFiBand.BAND_5GHZ)
        val optimisticSnr = optimisticCalc.calculateSnr(rssi, WiFiBand.BAND_5GHZ)

        assertTrue(
            optimisticSnr > defaultSnr,
            "Optimistic (lower) noise floor should yield higher SNR",
        )
    }

    @Test
    fun `SNR decreases with worse (less negative) noise floor`() {
        val rssi = -70
        val defaultCalc = SnrCalculator(StaticNoiseModel.default())
        val conservativeCalc = SnrCalculator(StaticNoiseModel.conservative())

        val defaultSnr = defaultCalc.calculateSnr(rssi, WiFiBand.BAND_5GHZ)
        val conservativeSnr = conservativeCalc.calculateSnr(rssi, WiFiBand.BAND_5GHZ)

        assertTrue(
            conservativeSnr < defaultSnr,
            "Conservative (higher) noise floor should yield lower SNR",
        )
    }
}
