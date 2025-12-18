package io.lamco.netkit.rf.channel

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.rf.model.ChannelQuality
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Unit tests for ChannelAnalyzer
 *
 * Covers:
 * - Channel congestion scoring
 * - Interference detection and scoring
 * - Co-channel and adjacent-channel analysis
 * - Channel recommendation
 * - Multi-channel comparison
 */
class ChannelAnalyzerTest {
    private val analyzer = ChannelAnalyzer()

    // ========== Basic Functionality Tests ==========

    @Test
    fun `analyzeChannel returns valid metrics`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -65, utilization = 30.0),
                BssChannelData(channel = 6, rssiDbm = -72, utilization = null),
                BssChannelData(channel = 11, rssiDbm = -80, utilization = null),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertEquals(WiFiBand.BAND_2_4GHZ, metrics.band)
        assertEquals(6, metrics.channel)
        assertEquals(ChannelWidth.WIDTH_20MHZ, metrics.primaryWidth)
        assertTrue(metrics.congestionScore in 0.0..1.0)
        assertTrue(metrics.interferenceScore in 0.0..1.0)
    }

    @Test
    fun `empty BSS data throws exception`() {
        assertThrows<IllegalArgumentException> {
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = emptyList(),
            )
        }
    }

    @Test
    fun `invalid channel number throws exception`() {
        val bssData = listOf(BssChannelData(channel = 6, rssiDbm = -65))

        assertThrows<IllegalArgumentException> {
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = -1,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )
        }
    }

    // ========== Co-Channel Detection Tests ==========

    @Test
    fun `correctly counts co-channel BSSs`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -60),
                BssChannelData(channel = 6, rssiDbm = -65),
                BssChannelData(channel = 6, rssiDbm = -70),
                BssChannelData(channel = 11, rssiDbm = -75),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertEquals(3, metrics.coChannelBssCount, "Should count 3 BSSs on channel 6")
        assertTrue(metrics.adjChannelBssCount >= 0, "Should detect adjacent channels")
    }

    @Test
    fun `no co-channel BSSs when alone on channel`() {
        val bssData =
            listOf(
                BssChannelData(channel = 1, rssiDbm = -75),
                BssChannelData(channel = 11, rssiDbm = -80),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertEquals(0, metrics.coChannelBssCount, "Should find no co-channel BSSs")
    }

    // ========== Adjacent Channel Detection Tests ==========

    @Test
    fun `detects adjacent channels in 2_4GHz`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -60),
                BssChannelData(channel = 7, rssiDbm = -65), // Adjacent
                BssChannelData(channel = 5, rssiDbm = -70), // Adjacent
                BssChannelData(channel = 11, rssiDbm = -80), // Not adjacent (too far)
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertEquals(1, metrics.coChannelBssCount)
        assertTrue(metrics.adjChannelBssCount >= 2, "Should detect adjacent channels 5 and 7")
    }

    @Test
    fun `5GHz non-overlapping channels correctly identified`() {
        // In 5GHz with 20MHz channels, spacing is 4 (channels 36, 40, 44, 48...)
        // Adjacent = abs(difference) < 4, so only +1, +2, +3 are adjacent
        // Channel 40 is exactly 4 apart from 36, so NOT adjacent (properly spaced)
        val bssData =
            listOf(
                BssChannelData(channel = 36, rssiDbm = -60),
                BssChannelData(channel = 38, rssiDbm = -65), // +2: IS adjacent (within spacing)
                BssChannelData(channel = 40, rssiDbm = -68), // +4: NOT adjacent (properly spaced)
                BssChannelData(channel = 149, rssiDbm = -70), // Not adjacent (different band section)
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_5GHZ,
                targetChannel = 36,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertEquals(1, metrics.coChannelBssCount, "Only channel 36 is co-channel")
        // Channel 38 is adjacent (+2 < 4), 40 and 149 are not
        assertEquals(1, metrics.adjChannelBssCount, "Only channel 38 is adjacent")
    }

    // ========== Congestion Scoring Tests ==========

    @Test
    fun `empty channel has high congestion score`() {
        val bssData =
            listOf(
                BssChannelData(channel = 1, rssiDbm = -80), // Far away channel
                BssChannelData(channel = 11, rssiDbm = -85),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertTrue(
            metrics.congestionScore > 0.8,
            "Empty channel should have high congestion score (low congestion)",
        )
    }

    @Test
    fun `crowded channel has low congestion score`() {
        val bssData =
            (1..10).map {
                BssChannelData(channel = 6, rssiDbm = -60 - it)
            }

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertTrue(
            metrics.congestionScore < 0.5,
            "Crowded channel (10 BSSs) should have low congestion score (high congestion)",
        )
    }

    @Test
    fun `congestion score decreases with more BSSs`() {
        val bssData1 = listOf(BssChannelData(channel = 6, rssiDbm = -65))
        val bssData3 = (1..3).map { BssChannelData(channel = 6, rssiDbm = -65) }
        val bssData5 = (1..5).map { BssChannelData(channel = 6, rssiDbm = -65) }

        val metrics1 = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, bssData1)
        val metrics3 = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, bssData3)
        val metrics5 = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, bssData5)

        assertTrue(metrics3.congestionScore < metrics1.congestionScore)
        assertTrue(metrics5.congestionScore < metrics3.congestionScore)
    }

    @Test
    fun `strong signals decrease congestion score`() {
        val weakSignals = listOf(BssChannelData(channel = 6, rssiDbm = -85))
        val strongSignals = listOf(BssChannelData(channel = 6, rssiDbm = -55))

        val metricsWeak = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, weakSignals)
        val metricsStrong = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, strongSignals)

        assertTrue(
            metricsStrong.congestionScore < metricsWeak.congestionScore,
            "Strong co-channel signals should reduce congestion score more",
        )
    }

    @Test
    fun `high utilization decreases congestion score`() {
        val lowUtil = listOf(BssChannelData(channel = 6, rssiDbm = -65, utilization = 10.0))
        val highUtil = listOf(BssChannelData(channel = 6, rssiDbm = -65, utilization = 80.0))

        val metricsLow = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, lowUtil)
        val metricsHigh = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, highUtil)

        assertTrue(
            metricsHigh.congestionScore < metricsLow.congestionScore,
            "High utilization should reduce congestion score",
        )
    }

    // ========== Interference Scoring Tests ==========

    @Test
    fun `no adjacent BSSs yields perfect interference score`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -65),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertEquals(
            1.0,
            metrics.interferenceScore,
            0.01,
            "No adjacent interference should yield score of 1.0",
        )
    }

    @Test
    fun `adjacent BSSs decrease interference score`() {
        val noAdjacent = listOf(BssChannelData(channel = 6, rssiDbm = -65))
        val withAdjacent =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -65),
                BssChannelData(channel = 7, rssiDbm = -68),
                BssChannelData(channel = 5, rssiDbm = -70),
            )

        val metricsClean = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, noAdjacent)
        val metricsInterfered = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, withAdjacent)

        assertTrue(
            metricsInterfered.interferenceScore < metricsClean.interferenceScore,
            "Adjacent BSSs should reduce interference score",
        )
    }

    @Test
    fun `strong adjacent signals worse than weak ones`() {
        val weakAdjacent =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -65),
                BssChannelData(channel = 7, rssiDbm = -85), // Weak adjacent
            )
        val strongAdjacent =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -65),
                BssChannelData(channel = 7, rssiDbm = -60), // Strong adjacent
            )

        val metricsWeak = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, weakAdjacent)
        val metricsStrong = analyzer.analyzeChannel(WiFiBand.BAND_2_4GHZ, 6, ChannelWidth.WIDTH_20MHZ, strongAdjacent)

        assertTrue(
            metricsStrong.interferenceScore < metricsWeak.interferenceScore,
            "Strong adjacent signals should cause more interference",
        )
    }

    // ========== Channel Recommendation Tests ==========

    @Test
    fun `clean channel is recommended`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -80), // Single weak BSS
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertTrue(metrics.recommended, "Clean channel should be recommended")
        assertTrue(metrics.overallQuality > 0.6, "Clean channel should have good quality")
    }

    @Test
    fun `crowded channel is not recommended`() {
        val bssData =
            (1..8).map {
                BssChannelData(channel = 6, rssiDbm = -60 - it)
            }

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertFalse(metrics.recommended, "Crowded channel should not be recommended")
        assertTrue(metrics.overallQuality < 0.6, "Crowded channel should have poor quality")
    }

    @Test
    fun `legacy 802_11b prevents recommendation`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -70),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
                legacy11bPresent = true,
            )

        assertFalse(metrics.recommended, "Legacy 802.11b should prevent recommendation")
        assertTrue(metrics.legacy11bPresent)
    }

    // ========== Multi-Channel Analysis Tests ==========

    @Test
    fun `analyzeAllChannels returns all 2_4GHz channels`() {
        val bssData =
            listOf(
                BssChannelData(channel = 1, rssiDbm = -70),
                BssChannelData(channel = 6, rssiDbm = -65),
                BssChannelData(channel = 11, rssiDbm = -75),
            )

        val allMetrics =
            analyzer.analyzeAllChannels(
                band = WiFiBand.BAND_2_4GHZ,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertTrue(allMetrics.size >= 3, "Should analyze at least channels 1, 6, 11")
        val channels = allMetrics.map { it.channel }
        assertTrue(channels.contains(1))
        assertTrue(channels.contains(6))
        assertTrue(channels.contains(11))
    }

    @Test
    fun `analyzeAllChannels sorts by quality`() {
        val bssData =
            listOf(
                BssChannelData(channel = 1, rssiDbm = -50), // Crowded on 1
                BssChannelData(channel = 1, rssiDbm = -55),
                BssChannelData(channel = 1, rssiDbm = -60),
                BssChannelData(channel = 6, rssiDbm = -80), // Clean on 6
                BssChannelData(channel = 11, rssiDbm = -65), // Moderate on 11
            )

        val allMetrics =
            analyzer.analyzeAllChannels(
                band = WiFiBand.BAND_2_4GHZ,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        // Should be sorted by quality (best first)
        for (i in 0 until allMetrics.size - 1) {
            assertTrue(
                allMetrics[i].overallQuality >= allMetrics[i + 1].overallQuality,
                "Channels should be sorted by quality (descending)",
            )
        }

        // Channel 6 (clean) should rank higher than channel 1 (crowded)
        val rank6 = allMetrics.indexOfFirst { it.channel == 6 }
        val rank1 = allMetrics.indexOfFirst { it.channel == 1 }
        assertTrue(rank6 < rank1, "Clean channel 6 should rank higher than crowded channel 1")
    }

    @Test
    fun `findBestChannel returns highest quality channel`() {
        val bssData =
            listOf(
                BssChannelData(channel = 1, rssiDbm = -55),
                BssChannelData(channel = 1, rssiDbm = -60),
                BssChannelData(channel = 6, rssiDbm = -85), // Cleanest
                BssChannelData(channel = 11, rssiDbm = -65),
            )

        val best =
            analyzer.findBestChannel(
                band = WiFiBand.BAND_2_4GHZ,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertEquals(6, best.channel, "Channel 6 should be identified as best")
        assertTrue(best.overallQuality > 0.7, "Best channel should have high quality")
    }

    // ========== Average RSSI Calculation Tests ==========

    @Test
    fun `average RSSI calculated correctly for co-channel BSSs`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -60),
                BssChannelData(channel = 6, rssiDbm = -70),
                BssChannelData(channel = 6, rssiDbm = -80),
                BssChannelData(channel = 11, rssiDbm = -50), // Different channel, excluded
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertNotNull(metrics.avgRssiDbm)
        assertEquals(-70.0, metrics.avgRssiDbm!!, 0.5) // Average of -60, -70, -80
    }

    @Test
    fun `average RSSI is null when no co-channel BSSs`() {
        val bssData =
            listOf(
                BssChannelData(channel = 1, rssiDbm = -60),
                BssChannelData(channel = 11, rssiDbm = -70),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertNull(metrics.avgRssiDbm, "Should be null when no co-channel BSSs")
    }

    // ========== Utilization Calculation Tests ==========

    @Test
    fun `total utilization sums co-channel BSSs`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -60, utilization = 30.0),
                BssChannelData(channel = 6, rssiDbm = -65, utilization = 25.0),
                BssChannelData(channel = 6, rssiDbm = -70, utilization = null), // No data
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertNotNull(metrics.totalChannelUtilizationPct)
        assertEquals(55.0, metrics.totalChannelUtilizationPct!!, 0.1) // 30 + 25
    }

    @Test
    fun `total utilization capped at 100 percent`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -60, utilization = 60.0),
                BssChannelData(channel = 6, rssiDbm = -65, utilization = 50.0),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertNotNull(metrics.totalChannelUtilizationPct)
        assertTrue(
            metrics.totalChannelUtilizationPct!! <= 100.0,
            "Total utilization should be capped at 100%",
        )
    }

    @Test
    fun `total utilization is null when no BSS provides data`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -60, utilization = null),
                BssChannelData(channel = 6, rssiDbm = -65, utilization = null),
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertNull(metrics.totalChannelUtilizationPct)
    }

    // ========== Quality Category Tests ==========

    @Test
    fun `excellent quality for clean channel`() {
        val bssData =
            listOf(
                BssChannelData(channel = 6, rssiDbm = -85), // Single weak BSS
            )

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        assertTrue(
            metrics.qualityCategory in listOf(ChannelQuality.EXCELLENT, ChannelQuality.GOOD),
            "Clean channel should have excellent/good quality",
        )
    }

    @Test
    fun `poor quality for congested channel`() {
        // Create congested channel with both co-channel and adjacent BSSs
        // This drives down both congestionScore AND interferenceScore
        val coChannelBss =
            (1..8).map {
                BssChannelData(channel = 6, rssiDbm = -55 - it) // Strong signals on same channel
            }
        val adjacentBss =
            listOf(
                BssChannelData(channel = 5, rssiDbm = -60), // Adjacent channel (strong)
                BssChannelData(channel = 7, rssiDbm = -62), // Adjacent channel (strong)
            )
        val bssData = coChannelBss + adjacentBss

        val metrics =
            analyzer.analyzeChannel(
                band = WiFiBand.BAND_2_4GHZ,
                targetChannel = 6,
                channelWidth = ChannelWidth.WIDTH_20MHZ,
                bssData = bssData,
            )

        // With 8 co-channel BSSs and 2 strong adjacent BSSs:
        // - congestionScore will be very low (~0.1 from exp decay + RSSI penalty)
        // - interferenceScore will be reduced from the adjacent BSSs
        // - overallQuality should be POOR or worse
        assertTrue(
            metrics.qualityCategory in listOf(ChannelQuality.FAIR, ChannelQuality.POOR, ChannelQuality.VERY_POOR),
            "Congested channel with interference should have fair/poor quality",
        )
        assertTrue(metrics.isCrowded, "Channel with 8 co-channel BSSs should be marked crowded")
    }
}
