package io.lamco.netkit.model.topology

import org.junit.jupiter.api.Test
import kotlin.test.*

class RoamingCapabilitiesTest {
    @Test
    fun `creates roaming capabilities with all standards`() {
        val capabilities =
            RoamingCapabilities(
                kSupported = true,
                vSupported = true,
                rSupported = true,
                ftPskSupported = true,
                ftEapSupported = true,
                ftSaeSupported = true,
            )

        assertTrue(capabilities.kSupported)
        assertTrue(capabilities.vSupported)
        assertTrue(capabilities.rSupported)
        assertTrue(capabilities.ftPskSupported)
        assertTrue(capabilities.ftEapSupported)
        assertTrue(capabilities.ftSaeSupported)
    }

    @Test
    fun `none() creates capabilities with no support`() {
        val capabilities = RoamingCapabilities.none()

        assertFalse(capabilities.hasFastRoaming)
        assertFalse(capabilities.hasFullSuite)
        assertFalse(capabilities.supportsFastTransition)
    }

    @Test
    fun `basic() creates capabilities with 11k only`() {
        val capabilities = RoamingCapabilities.basic()

        assertTrue(capabilities.kSupported)
        assertFalse(capabilities.vSupported)
        assertFalse(capabilities.rSupported)
        assertTrue(capabilities.hasFastRoaming)
    }

    @Test
    fun `fullSuite creates appropriate FT support for WPA2-PSK`() {
        val capabilities = RoamingCapabilities.fullSuite(AuthType.WPA2_PSK)

        assertTrue(capabilities.hasFullSuite)
        assertTrue(capabilities.ftPskSupported)
        assertFalse(capabilities.ftEapSupported)
        assertFalse(capabilities.ftSaeSupported)
    }

    @Test
    fun `fullSuite creates appropriate FT support for WPA3-SAE`() {
        val capabilities = RoamingCapabilities.fullSuite(AuthType.WPA3_SAE)

        assertTrue(capabilities.hasFullSuite)
        assertTrue(capabilities.ftSaeSupported)
        assertFalse(capabilities.ftPskSupported)
        assertFalse(capabilities.ftEapSupported)
    }

    @Test
    fun `fullSuite creates appropriate FT support for Enterprise`() {
        val capabilities = RoamingCapabilities.fullSuite(AuthType.WPA2_ENTERPRISE)

        assertTrue(capabilities.hasFullSuite)
        assertTrue(capabilities.ftEapSupported)
        assertFalse(capabilities.ftPskSupported)
        assertFalse(capabilities.ftSaeSupported)
    }

    @Test
    fun `hasFastRoaming is true when any standard supported`() {
        assertTrue(RoamingCapabilities(kSupported = true).hasFastRoaming)
        assertTrue(RoamingCapabilities(vSupported = true).hasFastRoaming)
        assertTrue(RoamingCapabilities(rSupported = true).hasFastRoaming)
    }

    @Test
    fun `hasFullSuite requires all three standards`() {
        assertTrue(
            RoamingCapabilities(
                kSupported = true,
                vSupported = true,
                rSupported = true,
            ).hasFullSuite,
        )

        assertFalse(
            RoamingCapabilities(
                kSupported = true,
                vSupported = true,
                rSupported = false,
            ).hasFullSuite,
        )
    }

    @Test
    fun `supportsFastTransition is true when any FT variant supported`() {
        assertTrue(RoamingCapabilities(ftPskSupported = true).supportsFastTransition)
        assertTrue(RoamingCapabilities(ftEapSupported = true).supportsFastTransition)
        assertTrue(RoamingCapabilities(ftSaeSupported = true).supportsFastTransition)
    }

    @Test
    fun `roaming score calculation`() {
        val none = RoamingCapabilities.none()
        val kOnly = RoamingCapabilities.basic()
        val fullSuite = RoamingCapabilities.fullSuite(AuthType.WPA2_PSK)

        assertEquals(0, none.roamingScore)
        assertEquals(25, kOnly.roamingScore)
        assertTrue(fullSuite.roamingScore >= 80)
    }

    @Test
    fun `roamingQuality reflects capabilities`() {
        val none = RoamingCapabilities.none()
        val basic = RoamingCapabilities.basic()
        val fullWithFt = RoamingCapabilities.fullSuite(AuthType.WPA2_PSK)
        val fullNoFt = RoamingCapabilities(kSupported = true, vSupported = true, rSupported = true)

        assertEquals(RoamingQuality.POOR, none.roamingQuality)
        assertEquals(RoamingQuality.FAIR, basic.roamingQuality)
        assertEquals(RoamingQuality.EXCELLENT, fullWithFt.roamingQuality)
        assertEquals(RoamingQuality.VERY_GOOD, fullNoFt.roamingQuality)
    }

    @Test
    fun `expectedRoamingLatencyMs reflects capabilities`() {
        val withRAndFt = RoamingCapabilities(rSupported = true, ftPskSupported = true)
        val withROnly = RoamingCapabilities(rSupported = true)
        val withKAndV = RoamingCapabilities(kSupported = true, vSupported = true)
        val none = RoamingCapabilities.none()

        assertTrue(withRAndFt.expectedRoamingLatencyMs.last <= 50)
        assertTrue(withROnly.expectedRoamingLatencyMs.last <= 100)
        assertTrue(withKAndV.expectedRoamingLatencyMs.last <= 300)
        assertTrue(none.expectedRoamingLatencyMs.last >= 500)
    }

    @Test
    fun `capabilitiesSummary shows supported standards`() {
        val full = RoamingCapabilities.fullSuite(AuthType.WPA2_PSK)
        val summary = full.capabilitiesSummary

        assertTrue(summary.contains("802.11k"))
        assertTrue(summary.contains("802.11v"))
        assertTrue(summary.contains("802.11r"))
        assertTrue(summary.contains("FT-PSK"))
    }

    @Test
    fun `capabilitiesSummary shows no support message when empty`() {
        val none = RoamingCapabilities.none()
        assertTrue(none.capabilitiesSummary.contains("No fast roaming"))
    }

    @Test
    fun `supportsFastRoamingFor returns true for matching auth type with FT`() {
        val pskCapabilities = RoamingCapabilities.fullSuite(AuthType.WPA2_PSK)
        assertTrue(pskCapabilities.supportsFastRoamingFor(AuthType.WPA2_PSK))
    }

    @Test
    fun `supportsFastRoamingFor returns true for SAE with FT-SAE`() {
        val saeCapabilities = RoamingCapabilities.fullSuite(AuthType.WPA3_SAE)
        assertTrue(saeCapabilities.supportsFastRoamingFor(AuthType.WPA3_SAE))
    }

    @Test
    fun `supportsFastRoamingFor returns true for Enterprise with FT-EAP`() {
        val enterpriseCapabilities = RoamingCapabilities.fullSuite(AuthType.WPA2_ENTERPRISE)
        assertTrue(enterpriseCapabilities.supportsFastRoamingFor(AuthType.WPA2_ENTERPRISE))
        assertTrue(enterpriseCapabilities.supportsFastRoamingFor(AuthType.WPA3_ENTERPRISE))
    }

    @Test
    fun `supportsFastRoamingFor returns true with k or v even without FT`() {
        val kvOnly = RoamingCapabilities(kSupported = true, vSupported = true)
        assertTrue(kvOnly.supportsFastRoamingFor(AuthType.WPA2_PSK))
    }

    @Test
    fun `supportsFastRoamingFor returns false with no roaming support`() {
        val none = RoamingCapabilities.none()
        assertFalse(none.supportsFastRoamingFor(AuthType.WPA2_PSK))
    }

    @Test
    fun `RoamingQuality EXCELLENT is acceptable for real-time`() {
        assertTrue(RoamingQuality.EXCELLENT.isAcceptableForRealTime)
    }

    @Test
    fun `RoamingQuality VERY_GOOD is acceptable for real-time`() {
        assertTrue(RoamingQuality.VERY_GOOD.isAcceptableForRealTime)
    }

    @Test
    fun `RoamingQuality GOOD is acceptable for real-time`() {
        assertTrue(RoamingQuality.GOOD.isAcceptableForRealTime)
    }

    @Test
    fun `RoamingQuality FAIR and POOR are not acceptable for real-time`() {
        assertFalse(RoamingQuality.FAIR.isAcceptableForRealTime)
        assertFalse(RoamingQuality.POOR.isAcceptableForRealTime)
    }
}
