package io.lamco.netkit.optimizer.model

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for ChannelPlanningConstraints
 *
 * Tests:
 * - Validation logic for all constraints
 * - Regulatory domain channel allocation
 * - Channel usability checks
 * - Band-specific width validation
 */
class ChannelPlanningConstraintsTest {
    // ========================================
    // Constructor Validation Tests
    // ========================================

    @Test
    fun `create constraints with valid parameters succeeds`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                supportsDfs = true,
                minChannelSeparation = 5,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ, ChannelWidth.WIDTH_40MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertEquals(WiFiBand.BAND_5GHZ, constraints.band)
        assertTrue(constraints.supportsDfs)
        assertEquals(5, constraints.minChannelSeparation)
    }

    @Test
    fun `create constraints with UNKNOWN band throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.UNKNOWN,
                preferredWidths = listOf(ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `create constraints with zero minChannelSeparation throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                minChannelSeparation = 0,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `create constraints with negative minChannelSeparation throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                minChannelSeparation = -1,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `create constraints with empty preferredWidths throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = emptyList(),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `create constraints with UNKNOWN in preferredWidths throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ, ChannelWidth.UNKNOWN),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `create constraints with zero maxApCountPerChannel throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                maxApCountPerChannel = 0,
            )
        }
    }

    @Test
    fun `create constraints with empty allowedChannels throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                allowedChannels = emptySet(),
            )
        }
    }

    @Test
    fun `create constraints with overlapping allowed and excluded channels throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                allowedChannels = setOf(36, 40, 44),
                excludedChannels = setOf(40, 48),
            )
        }
    }

    // ========================================
    // Band-Specific Width Validation Tests
    // ========================================

    @Test
    fun `2_4 GHz with 320 MHz width throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_320MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `2_4 GHz with 160 MHz width throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_160MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `2_4 GHz with 80 MHz width throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `2_4 GHz with 20 MHz width succeeds`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertEquals(ChannelWidth.WIDTH_20MHZ, constraints.preferredWidth)
    }

    @Test
    fun `2_4 GHz with 40 MHz width succeeds`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_40MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertEquals(ChannelWidth.WIDTH_40MHZ, constraints.preferredWidth)
        assertTrue(constraints.allows40MHzIn24GHz)
    }

    @Test
    fun `5 GHz with all valid widths succeeds`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths =
                    listOf(
                        ChannelWidth.WIDTH_160MHZ,
                        ChannelWidth.WIDTH_80MHZ,
                        ChannelWidth.WIDTH_40MHZ,
                        ChannelWidth.WIDTH_20MHZ,
                    ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertEquals(ChannelWidth.WIDTH_160MHZ, constraints.preferredWidth)
        assertTrue(constraints.prefersWideChannels)
    }

    @Test
    fun `5 GHz with 320 MHz width throws exception`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_320MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )
        }
    }

    @Test
    fun `6 GHz with 320 MHz width succeeds`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_6GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_320MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertEquals(ChannelWidth.WIDTH_320MHZ, constraints.preferredWidth)
        assertTrue(constraints.prefersWideChannels)
    }

    // ========================================
    // Regulatory Domain Channel Tests
    // ========================================

    @Test
    fun `FCC 2_4 GHz channels are 1-11`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        val channels = constraints.getAvailableChannels()
        assertEquals(11, channels.size)
        assertTrue(channels.containsAll(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)))
        assertFalse(channels.contains(12))
        assertFalse(channels.contains(13))
        assertFalse(channels.contains(14))
    }

    @Test
    fun `ETSI 2_4 GHz channels are 1-13`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.ETSI,
            )

        val channels = constraints.getAvailableChannels()
        assertEquals(13, channels.size)
        assertTrue(channels.containsAll(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)))
        assertFalse(channels.contains(14))
    }

    @Test
    fun `MKK 2_4 GHz channels are 1-14`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.MKK,
            )

        val channels = constraints.getAvailableChannels()
        assertEquals(14, channels.size)
        assertTrue(channels.containsAll(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)))
    }

    @Test
    fun `FCC 5 GHz includes DFS channels when supported`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                supportsDfs = true,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        val channels = constraints.getAvailableChannels()
        // FCC has 9 non-DFS + 16 DFS = 25 channels
        assertTrue(channels.size >= 20)
        assertTrue(channels.contains(36)) // U-NII-1
        assertTrue(channels.contains(52)) // U-NII-2A (DFS)
        assertTrue(channels.contains(100)) // U-NII-2C (DFS)
        assertTrue(channels.contains(149)) // U-NII-3
    }

    @Test
    fun `FCC 5 GHz excludes DFS channels when not supported`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                supportsDfs = false,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        val channels = constraints.getAvailableChannels()
        assertEquals(9, channels.size)
        assertTrue(channels.contains(36))
        assertTrue(channels.contains(149))
        assertFalse(channels.contains(52)) // DFS channel
        assertFalse(channels.contains(100)) // DFS channel
    }

    @Test
    fun `FCC 6 GHz has many channels available`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_6GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_160MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        val channels = constraints.getAvailableChannels()
        // FCC 6 GHz PSC channels
        assertTrue(channels.size >= 50)
        assertTrue(channels.contains(1))
        assertTrue(channels.contains(5))
    }

    @Test
    fun `ETSI 6 GHz has limited channels`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_6GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.ETSI,
            )

        val channels = constraints.getAvailableChannels()
        // ETSI has limited 6 GHz
        assertTrue(channels.size < 100)
    }

    @Test
    fun `CN 6 GHz has no channels`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_6GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.CN,
            )

        val channels = constraints.getAvailableChannels()
        assertTrue(channels.isEmpty())
    }

    // ========================================
    // Channel Filtering Tests
    // ========================================

    @Test
    fun `allowedChannels filters channels correctly`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                allowedChannels = setOf(36, 40, 44, 48),
            )

        val channels = constraints.getAvailableChannels()
        assertEquals(4, channels.size)
        assertTrue(channels.containsAll(listOf(36, 40, 44, 48)))
        assertFalse(channels.contains(149))
    }

    @Test
    fun `excludedChannels removes channels correctly`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                excludedChannels = setOf(1, 6, 11),
            )

        val channels = constraints.getAvailableChannels()
        assertEquals(8, channels.size)
        assertFalse(channels.contains(1))
        assertFalse(channels.contains(6))
        assertFalse(channels.contains(11))
        assertTrue(channels.contains(2))
        assertTrue(channels.contains(7))
    }

    @Test
    fun `isChannelUsable returns true for available channel`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                allowedChannels = setOf(36, 40, 44),
            )

        assertTrue(constraints.isChannelUsable(36))
        assertTrue(constraints.isChannelUsable(40))
        assertFalse(constraints.isChannelUsable(48))
        assertFalse(constraints.isChannelUsable(149))
    }

    // ========================================
    // Computed Property Tests
    // ========================================

    @Test
    fun `allows40MHzIn24GHz is true when 40 MHz included`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_40MHZ, ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertTrue(constraints.allows40MHzIn24GHz)
    }

    @Test
    fun `allows40MHzIn24GHz is false when only 20 MHz`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_2_4GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertFalse(constraints.allows40MHzIn24GHz)
    }

    @Test
    fun `preferredWidth returns first width in list`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths =
                    listOf(
                        ChannelWidth.WIDTH_160MHZ,
                        ChannelWidth.WIDTH_80MHZ,
                        ChannelWidth.WIDTH_40MHZ,
                    ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertEquals(ChannelWidth.WIDTH_160MHZ, constraints.preferredWidth)
    }

    @Test
    fun `prefersWideChannels is true for 80 MHz and above`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertTrue(constraints.prefersWideChannels)
    }

    @Test
    fun `prefersWideChannels is false for 40 MHz only`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_40MHZ, ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        assertFalse(constraints.prefersWideChannels)
    }

    @Test
    fun `summary contains key information`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                supportsDfs = true,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ, ChannelWidth.WIDTH_40MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
            )

        val summary = constraints.summary
        assertTrue(summary.contains("5 GHz"))
        assertTrue(summary.contains("DFS: true"))
        assertTrue(summary.contains("80MHz"))
        assertTrue(summary.contains("FCC"))
    }

    // ========================================
    // Regulatory Domain TX Power Tests
    // ========================================

    @Test
    fun `FCC max TX power is 30 dBm for all bands`() {
        assertEquals(30, RegulatoryDomain.FCC.getMaxTxPower(WiFiBand.BAND_2_4GHZ))
        assertEquals(30, RegulatoryDomain.FCC.getMaxTxPower(WiFiBand.BAND_5GHZ))
        assertEquals(30, RegulatoryDomain.FCC.getMaxTxPower(WiFiBand.BAND_6GHZ))
    }

    @Test
    fun `ETSI max TX power varies by band`() {
        assertEquals(20, RegulatoryDomain.ETSI.getMaxTxPower(WiFiBand.BAND_2_4GHZ))
        assertEquals(23, RegulatoryDomain.ETSI.getMaxTxPower(WiFiBand.BAND_5GHZ))
        assertEquals(23, RegulatoryDomain.ETSI.getMaxTxPower(WiFiBand.BAND_6GHZ))
    }

    @Test
    fun `MKK max TX power is 20 dBm for all bands`() {
        assertEquals(20, RegulatoryDomain.MKK.getMaxTxPower(WiFiBand.BAND_2_4GHZ))
        assertEquals(20, RegulatoryDomain.MKK.getMaxTxPower(WiFiBand.BAND_5GHZ))
    }

    @Test
    fun `requiresDfs is true for FCC and ETSI`() {
        assertTrue(RegulatoryDomain.FCC.requiresDfs)
        assertTrue(RegulatoryDomain.ETSI.requiresDfs)
        assertTrue(RegulatoryDomain.MKK.requiresDfs)
        assertFalse(RegulatoryDomain.CN.requiresDfs)
        assertFalse(RegulatoryDomain.ROW.requiresDfs)
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    @Test
    fun `constraints with prioritizeStability flag works`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                prioritizeStability = true,
            )

        assertTrue(constraints.prioritizeStability)
    }

    @Test
    fun `constraints with high maxApCountPerChannel works`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_80MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                maxApCountPerChannel = 10,
            )

        assertEquals(10, constraints.maxApCountPerChannel)
    }

    @Test
    fun `constraints with minChannelSeparation 1 works`() {
        val constraints =
            ChannelPlanningConstraints(
                band = WiFiBand.BAND_5GHZ,
                preferredWidths = listOf(ChannelWidth.WIDTH_20MHZ),
                regulatoryDomain = RegulatoryDomain.FCC,
                minChannelSeparation = 1,
            )

        assertEquals(1, constraints.minChannelSeparation)
    }
}
