package io.lamco.netkit.ieparser.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EHTCapabilities Tests")
class EHTCapabilitiesTest {
    @Test fun maxChannelWidth_160MHz() {
        val eht = EHTCapabilities(supports320MHz = false)
        assertEquals(160, eht.maxChannelWidthMHz)
    }

    @Test fun maxChannelWidth_320MHz() {
        val eht = EHTCapabilities(supports320MHz = true)
        assertEquals(320, eht.maxChannelWidthMHz)
    }

    @Test fun maxPhyRate_4Stream320MHz() {
        val eht = EHTCapabilities(maxSpatialStreams = 4, supports320MHz = true)
        assertEquals(11530, eht.maxPhyRateMbps)
    }

    @Test fun maxPhyRate_16Stream320MHz() {
        val eht = EHTCapabilities(maxSpatialStreams = 16, supports320MHz = true)
        assertEquals(46120, eht.maxPhyRateMbps)
    }

    @Test fun maxMLOPhyRate_noMLO() {
        val eht = EHTCapabilities(maxSpatialStreams = 4, supports320MHz = true, multiLinkOperation = false)
        assertEquals(eht.maxPhyRateMbps, eht.maxMLOPhyRateMbps)
    }

    @Test fun maxMLOPhyRate_3Links() {
        val eht =
            EHTCapabilities(maxSpatialStreams = 4, supports320MHz = true, multiLinkOperation = true, mloMaxLinks = 3)
        assertTrue(eht.maxMLOPhyRateMbps > eht.maxPhyRateMbps)
        assertTrue(eht.maxMLOPhyRateMbps <= eht.maxPhyRateMbps * 2)
    }

    @Test fun hasAdvancedFeatures_true() {
        val eht = EHTCapabilities(multiRUSupport = true, puncturingSupport = true)
        assertTrue(eht.hasAdvancedFeatures)
    }

    @Test fun hasAdvancedFeatures_false() {
        val eht = EHTCapabilities(multiRUSupport = false)
        assertFalse(eht.hasAdvancedFeatures)
    }

    @Test fun isUltraLowLatency_true() {
        val eht = EHTCapabilities(multiLinkOperation = true, multiRUSupport = true, puncturingSupport = true)
        assertTrue(eht.isUltraLowLatency)
    }

    @Test fun isHighPerformance_true() {
        val eht =
            EHTCapabilities(
                supports320MHz = true,
                maxSpatialStreams = 4,
                multiLinkOperation = true,
                supports4096QAM = true,
            )
        assertTrue(eht.isHighPerformance)
    }
}
