package io.lamco.netkit.ieparser.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HTCapabilities Tests")
class HTCapabilitiesTest {
    @Test fun calculateSpatialStreams_1Stream() {
        val bitmap = ByteArray(16)
        bitmap[0] = 0xFF.toByte()
        assertEquals(1, HTCapabilities.calculateSpatialStreams(bitmap))
    }

    @Test fun calculateSpatialStreams_2Streams() {
        val bitmap = ByteArray(16)
        bitmap[0] = 0xFF.toByte()
        bitmap[1] = 0xFF.toByte()
        assertEquals(2, HTCapabilities.calculateSpatialStreams(bitmap))
    }

    @Test fun calculateSpatialStreams_4Streams() {
        val bitmap = ByteArray(16)
        bitmap[0] = 0xFF.toByte()
        bitmap[1] = 0xFF.toByte()
        bitmap[2] = 0xFF.toByte()
        bitmap[3] = 0xFF.toByte()
        assertEquals(4, HTCapabilities.calculateSpatialStreams(bitmap))
    }

    @Test fun maxPhyRate_1Stream20MHz() {
        val ht = HTCapabilities(maxSpatialStreams = 1, supports40MHz = false)
        assertEquals(65, ht.maxPhyRateMbps)
    }

    @Test fun maxPhyRate_1Stream40MHzSGI() {
        val ht = HTCapabilities(maxSpatialStreams = 1, supports40MHz = true, shortGI40MHz = true)
        assertEquals(150, ht.maxPhyRateMbps)
    }

    @Test fun maxPhyRate_4Stream40MHzSGI() {
        val ht = HTCapabilities(maxSpatialStreams = 4, supports40MHz = true, shortGI40MHz = true)
        assertEquals(600, ht.maxPhyRateMbps)
    }

    @Test fun isHighPerformance_true() {
        val ht = HTCapabilities(maxSpatialStreams = 2, supports40MHz = true, shortGI40MHz = true)
        assertTrue(ht.isHighPerformance)
    }

    @Test fun isHighPerformance_false() {
        val ht = HTCapabilities(maxSpatialStreams = 1, supports40MHz = false)
        assertFalse(ht.isHighPerformance)
    }

    @Test fun recommendedFor24GHz_20MHz() {
        val ht = HTCapabilities(supports40MHz = false)
        assertTrue(ht.recommendedFor24GHz)
    }

    @Test fun recommendedFor24GHz_40MHz() {
        val ht = HTCapabilities(supports40MHz = true)
        assertFalse(ht.recommendedFor24GHz)
    }
}
