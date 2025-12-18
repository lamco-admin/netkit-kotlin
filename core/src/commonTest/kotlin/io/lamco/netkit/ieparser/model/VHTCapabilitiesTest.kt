package io.lamco.netkit.ieparser.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("VHTCapabilities Tests")
class VHTCapabilitiesTest {
    @Test fun maxChannelWidth_80MHz() {
        val vht = VHTCapabilities(supports80MHz = true, supports160MHz = false)
        assertEquals(80, vht.maxChannelWidthMHz)
    }

    @Test fun maxChannelWidth_160MHz() {
        val vht = VHTCapabilities(supports160MHz = true)
        assertEquals(160, vht.maxChannelWidthMHz)
    }

    @Test fun maxPhyRate_2Stream80MHzSGI() {
        val vht = VHTCapabilities(maxSpatialStreams = 2, supports80MHz = true, shortGI80MHz = true)
        assertEquals(867, vht.maxPhyRateMbps)
    }

    @Test fun maxPhyRate_4Stream160MHzSGI() {
        val vht = VHTCapabilities(maxSpatialStreams = 4, supports160MHz = true, shortGI160MHz = true)
        assertEquals(3467, vht.maxPhyRateMbps)
    }

    @Test fun maxPhyRate_8Stream160MHzSGI() {
        val vht = VHTCapabilities(maxSpatialStreams = 8, supports160MHz = true, shortGI160MHz = true)
        assertEquals(6933, vht.maxPhyRateMbps)
    }

    @Test fun isHighPerformance_true() {
        val vht = VHTCapabilities(maxSpatialStreams = 2, supports80MHz = true, muMimoCapable = true)
        assertTrue(vht.isHighPerformance)
    }

    @Test fun isHighPerformance_false() {
        val vht = VHTCapabilities(maxSpatialStreams = 1, supports80MHz = false)
        assertFalse(vht.isHighPerformance)
    }

    @Test fun isGigabitCapable_true() {
        val vht = VHTCapabilities(maxSpatialStreams = 2, supports80MHz = true, shortGI80MHz = false)
        assertFalse(vht.isGigabitCapable) // 780 Mbps < 1000
    }

    @Test fun isGigabitCapable_4Stream80MHz() {
        val vht = VHTCapabilities(maxSpatialStreams = 4, supports80MHz = true, shortGI80MHz = true)
        assertTrue(vht.isGigabitCapable) // 1733 Mbps > 1000
    }

    @Test fun calculateSpatialStreams_2Streams() {
        val mcsMap = 0xFFFA // Stream 1-2: 10 (MCS 0-9), 3-4+: 11 (not supported)
        assertEquals(2, VHTCapabilities.calculateSpatialStreams(mcsMap))
    }

    @Test fun calculateSpatialStreams_4Streams() {
        // VHT MCS map: 2 bits per stream (00=MCS0-7, 01=MCS0-8, 10=MCS0-9, 11=not supported)
        // Streams 1-4 = 10 (MCS 0-9), Streams 5-8 = 11 (not supported)
        // bits 0-7: 10101010 = 0xAA, bits 8-15: 11111111 = 0xFF → 0xFFAA
        val mcsMap = 0xFFAA
        assertEquals(4, VHTCapabilities.calculateSpatialStreams(mcsMap))
    }
}
