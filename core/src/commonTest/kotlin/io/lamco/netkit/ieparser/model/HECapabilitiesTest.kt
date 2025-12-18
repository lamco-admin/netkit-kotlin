package io.lamco.netkit.ieparser.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HECapabilities Tests")
class HECapabilitiesTest {
    @Test fun maxChannelWidth_20MHz() {
        val he = HECapabilities()
        assertEquals(20, he.maxChannelWidthMHz)
    }

    @Test fun maxChannelWidth_40MHz() {
        val he = HECapabilities(supports40MHzIn24GHz = true)
        assertEquals(40, he.maxChannelWidthMHz)
    }

    @Test fun maxChannelWidth_80MHz() {
        val he = HECapabilities(supports80MHzIn5GHz = true)
        assertEquals(80, he.maxChannelWidthMHz)
    }

    @Test fun maxChannelWidth_160MHz() {
        val he = HECapabilities(supports160MHz = true)
        assertEquals(160, he.maxChannelWidthMHz)
    }

    @Test fun maxPhyRate_1Stream80MHz() {
        val he = HECapabilities(maxSpatialStreams = 1, supports80MHzIn5GHz = true)
        assertEquals(600, he.maxPhyRateMbps)
    }

    @Test fun maxPhyRate_2Stream160MHz() {
        val he = HECapabilities(maxSpatialStreams = 2, supports160MHz = true)
        assertEquals(2402, he.maxPhyRateMbps)
    }

    @Test fun maxPhyRate_8Stream160MHz() {
        val he = HECapabilities(maxSpatialStreams = 8, supports160MHz = true)
        assertEquals(9608, he.maxPhyRateMbps)
    }

    @Test fun hasEfficiencyFeatures_true() {
        val he = HECapabilities(ofdmaSupport = true, twtRequesterSupport = true)
        assertTrue(he.hasEfficiencyFeatures)
    }

    @Test fun hasEfficiencyFeatures_false() {
        val he = HECapabilities(ofdmaSupport = true, twtRequesterSupport = false, twtResponderSupport = false)
        assertFalse(he.hasEfficiencyFeatures)
    }

    @Test fun isDenseEnvironmentOptimized_true() {
        val he = HECapabilities(ofdmaSupport = true, muMimoDownlink = true, beamformeeCapable = true)
        assertTrue(he.isDenseEnvironmentOptimized)
    }

    @Test fun isDenseEnvironmentOptimized_false() {
        val he = HECapabilities(ofdmaSupport = true)
        assertFalse(he.isDenseEnvironmentOptimized)
    }
}
