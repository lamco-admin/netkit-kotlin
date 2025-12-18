package io.lamco.netkit.rf.distance

import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.rf.model.DistanceSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Unit tests for RssiDistanceEstimator and PathLossModel
 *
 * Covers:
 * - RSSI-to-distance conversion using path loss models
 * - Indoor/outdoor/heavy obstruction models
 * - Distance uncertainty estimation
 * - Edge cases and validation
 */
class RssiDistanceEstimatorTest {
    // ========== Path Loss Model Tests ==========

    @Test
    fun `free space model has path loss exponent 2_0`() {
        val model = PathLossModel.freeSpace(WiFiBand.BAND_5GHZ)
        assertEquals(2.0, model.pathLossExponent, 0.01)
    }

    @Test
    fun `indoor model has path loss exponent 3_0`() {
        val model = PathLossModel.indoor(WiFiBand.BAND_5GHZ)
        assertEquals(3.0, model.pathLossExponent, 0.01)
    }

    @Test
    fun `indoor heavy model has path loss exponent 4_0`() {
        val model = PathLossModel.indoorHeavy(WiFiBand.BAND_5GHZ)
        assertEquals(4.0, model.pathLossExponent, 0.01)
    }

    @Test
    fun `free space has lowest shadowing (minimal obstruction)`() {
        val freeSpace = PathLossModel.freeSpace(WiFiBand.BAND_5GHZ)
        val indoor = PathLossModel.indoor(WiFiBand.BAND_5GHZ)

        assertTrue(
            freeSpace.shadowingStdDevDb < indoor.shadowingStdDevDb,
            "Free space should have less shadowing than indoor",
        )
    }

    @Test
    fun `indoor heavy has highest shadowing`() {
        val indoor = PathLossModel.indoor(WiFiBand.BAND_5GHZ)
        val indoorHeavy = PathLossModel.indoorHeavy(WiFiBand.BAND_5GHZ)

        assertTrue(
            indoorHeavy.shadowingStdDevDb > indoor.shadowingStdDevDb,
            "Indoor heavy should have more shadowing than indoor",
        )
    }

    @Test
    fun `distance from RSSI increases as RSSI decreases`() {
        val model = PathLossModel.indoor(WiFiBand.BAND_5GHZ)

        val distance50 = model.distanceFromRssi(-50)
        val distance60 = model.distanceFromRssi(-60)
        val distance70 = model.distanceFromRssi(-70)

        assertTrue(distance60 > distance50, "Weaker signal should indicate greater distance")
        assertTrue(distance70 > distance60, "Even weaker signal should indicate even greater distance")
    }

    @Test
    fun `distance from RSSI is positive`() {
        val model = PathLossModel.indoor(WiFiBand.BAND_5GHZ)

        for (rssi in listOf(-40, -50, -60, -70, -80, -90)) {
            val distance = model.distanceFromRssi(rssi)
            assertTrue(distance > 0.0, "Distance should be positive for RSSI $rssi dBm")
        }
    }

    @Test
    fun `stronger signal (higher RSSI) yields shorter distance`() {
        val model = PathLossModel.indoor(WiFiBand.BAND_5GHZ)

        val distanceStrong = model.distanceFromRssi(-40)
        val distanceWeak = model.distanceFromRssi(-80)

        assertTrue(distanceStrong < distanceWeak, "Strong signal should yield shorter distance")
    }

    @Test
    fun `outdoor model yields longer distances than indoor for same RSSI`() {
        val outdoor = PathLossModel.freeSpace(WiFiBand.BAND_5GHZ)
        val indoor = PathLossModel.indoor(WiFiBand.BAND_5GHZ)

        val rssi = -70

        val distanceOutdoor = outdoor.distanceFromRssi(rssi)
        val distanceIndoor = indoor.distanceFromRssi(rssi)

        // With same RSSI, outdoor (lower path loss exponent) means further distance
        assertTrue(
            distanceOutdoor > distanceIndoor,
            "Outdoor model should yield longer distance for same RSSI",
        )
    }

    @Test
    fun `distance std dev increases with distance`() {
        val model = PathLossModel.indoor(WiFiBand.BAND_5GHZ)

        val stdDev5m = model.distanceStdDev(5.0)
        val stdDev10m = model.distanceStdDev(10.0)
        val stdDev20m = model.distanceStdDev(20.0)

        assertTrue(stdDev10m > stdDev5m, "Uncertainty should increase with distance")
        assertTrue(stdDev20m > stdDev10m, "Uncertainty should continue increasing")
    }

    @Test
    fun `typical distance std dev is reasonable`() {
        val model = PathLossModel.indoor(WiFiBand.BAND_5GHZ)

        val stdDev10m = model.distanceStdDev(10.0)

        // For indoor model at 10m, expect uncertainty around 3-8 meters
        assertTrue(
            stdDev10m in 2.0..10.0,
            "Distance std dev $stdDev10m should be in realistic range",
        )
    }

    // ========== RSSI Distance Estimator Tests ==========

    @Test
    fun `indoor residential estimator returns RSSI_ONLY source`() {
        val estimator = RssiDistanceEstimator.indoorResidential()
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
            )

        assertEquals(DistanceSource.RSSI_ONLY, estimate.method)
    }

    @Test
    fun `outdoor estimator returns RSSI_ONLY source`() {
        val estimator = RssiDistanceEstimator.outdoor()
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
            )

        assertEquals(DistanceSource.RSSI_ONLY, estimate.method)
    }

    @Test
    fun `estimateDistance returns positive distance`() {
        val estimator = RssiDistanceEstimator.indoorResidential()

        for (rssi in listOf(-40, -50, -60, -70, -80, -90)) {
            val estimate = estimator.estimateDistance(rssiDbm = rssi, band = WiFiBand.BAND_5GHZ)
            assertTrue(
                estimate.distanceMeters > 0.0,
                "Distance should be positive for RSSI $rssi dBm",
            )
        }
    }

    @Test
    fun `estimateDistance returns positive std dev`() {
        val estimator = RssiDistanceEstimator.indoorResidential()
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
            )

        assertTrue(estimate.stdDevMeters > 0.0, "Standard deviation should be positive")
    }

    @Test
    fun `strong signal yields short distance`() {
        val estimator = RssiDistanceEstimator.indoorResidential()
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -40, // Very strong signal
                band = WiFiBand.BAND_5GHZ,
            )

        assertTrue(estimate.distanceMeters < 10.0, "Strong signal should yield distance < 10m")
    }

    @Test
    fun `weak signal yields long distance`() {
        val estimator = RssiDistanceEstimator.indoorResidential()
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -85, // Weak signal
                band = WiFiBand.BAND_5GHZ,
            )

        assertTrue(estimate.distanceMeters > 20.0, "Weak signal should yield distance > 20m")
    }

    @Test
    fun `indoor residential uses reasonable TX power`() {
        val estimator = RssiDistanceEstimator.indoorResidential(txPowerDbm = 20.0)
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
            )

        // With 20 dBm TX power and -65 dBm RSSI, expect 5-20m indoors
        assertTrue(
            estimate.distanceMeters in 3.0..30.0,
            "Indoor distance ${estimate.distanceMeters}m should be in realistic range",
        )
    }

    @Test
    fun `outdoor estimator yields longer distances`() {
        val indoor = RssiDistanceEstimator.indoorResidential()
        val outdoor = RssiDistanceEstimator.outdoor()

        val rssi = -70

        val indoorEstimate = indoor.estimateDistance(rssi, WiFiBand.BAND_5GHZ)
        val outdoorEstimate = outdoor.estimateDistance(rssi, WiFiBand.BAND_5GHZ)

        assertTrue(
            outdoorEstimate.distanceMeters > indoorEstimate.distanceMeters,
            "Outdoor should estimate longer distance for same RSSI",
        )
    }

    @Test
    fun `indoor heavy yields shorter distances than indoor`() {
        val indoor = RssiDistanceEstimator.indoorResidential()
        val indoorHeavy = RssiDistanceEstimator.indoorHeavy()

        val rssi = -70

        val indoorEstimate = indoor.estimateDistance(rssi, WiFiBand.BAND_5GHZ)
        val heavyEstimate = indoorHeavy.estimateDistance(rssi, WiFiBand.BAND_5GHZ)

        assertTrue(
            heavyEstimate.distanceMeters < indoorEstimate.distanceMeters,
            "Heavy obstruction should estimate shorter distance for same RSSI",
        )
    }

    @Test
    fun `frequency-specific estimation delegates to band-based`() {
        val estimator = RssiDistanceEstimator.indoorResidential()

        val estimate1 =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
            )
        val estimate2 =
            estimator.estimateDistance(
                rssiDbm = -65,
                freqMHz = 5180,
                band = WiFiBand.BAND_5GHZ,
            )

        assertEquals(estimate1.distanceMeters, estimate2.distanceMeters, 0.1)
        assertEquals(estimate1.stdDevMeters, estimate2.stdDevMeters, 0.1)
    }

    @Test
    fun `RTT parameters are ignored for RSSI estimator`() {
        val estimator = RssiDistanceEstimator.indoorResidential()

        val estimate1 =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = null,
                rttStdDevMeters = null,
            )
        val estimate2 =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 10.0,
                rttStdDevMeters = 1.0,
            )

        // Should produce same result - RTT ignored by RSSI estimator
        assertEquals(estimate1.distanceMeters, estimate2.distanceMeters, 0.1)
        assertEquals(DistanceSource.RSSI_ONLY, estimate2.method)
    }

    // ========== Validation Tests ==========

    @Test
    fun `rejects RSSI below -120 dBm`() {
        val estimator = RssiDistanceEstimator.indoorResidential()

        assertThrows<IllegalArgumentException> {
            estimator.estimateDistance(rssiDbm = -121, band = WiFiBand.BAND_5GHZ)
        }
    }

    @Test
    fun `rejects RSSI above 0 dBm`() {
        val estimator = RssiDistanceEstimator.indoorResidential()

        assertThrows<IllegalArgumentException> {
            estimator.estimateDistance(rssiDbm = 1, band = WiFiBand.BAND_5GHZ)
        }
    }

    @Test
    fun `accepts RSSI at boundary -120 dBm`() {
        val estimator = RssiDistanceEstimator.indoorResidential()
        val estimate = estimator.estimateDistance(rssiDbm = -120, band = WiFiBand.BAND_5GHZ)

        assertTrue(estimate.distanceMeters > 0.0, "Should handle -120 dBm RSSI")
    }

    @Test
    fun `accepts RSSI at boundary 0 dBm`() {
        val estimator = RssiDistanceEstimator.indoorResidential()
        val estimate = estimator.estimateDistance(rssiDbm = 0, band = WiFiBand.BAND_5GHZ)

        assertTrue(estimate.distanceMeters > 0.0, "Should handle 0 dBm RSSI")
        assertTrue(estimate.distanceMeters < 1.0, "0 dBm RSSI should indicate very close proximity")
    }

    // ========== Realistic Distance Range Tests ==========

    @Test
    fun `typical home scenario - living room to router`() {
        val estimator = RssiDistanceEstimator.indoorResidential()
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -55, // Good signal
                band = WiFiBand.BAND_5GHZ,
            )

        // Expect 3-15m for good indoor signal
        assertTrue(
            estimate.distanceMeters in 1.0..20.0,
            "Living room distance ${estimate.distanceMeters}m should be in typical range",
        )
    }

    @Test
    fun `typical office scenario - desk to AP`() {
        val estimator = RssiDistanceEstimator.indoorResidential()
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
            )

        // Expect 5-25m for moderate indoor signal
        assertTrue(
            estimate.distanceMeters in 3.0..35.0,
            "Office distance ${estimate.distanceMeters}m should be in typical range",
        )
    }

    @Test
    fun `outdoor long range scenario`() {
        val estimator = RssiDistanceEstimator.outdoor()
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -75,
                band = WiFiBand.BAND_5GHZ,
            )

        // Outdoor long range: 50-200m
        assertTrue(
            estimate.distanceMeters in 20.0..300.0,
            "Outdoor long range distance ${estimate.distanceMeters}m should be in typical range",
        )
    }

    @Test
    fun `2_4GHz vs 5GHz distance estimation`() {
        val estimator = RssiDistanceEstimator.indoorResidential()

        val estimate24 =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_2_4GHZ,
            )
        val estimate5 =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
            )

        // Both should give reasonable distances (may differ slightly due to frequency-dependent path loss)
        assertTrue(estimate24.distanceMeters in 1.0..50.0, "2.4 GHz distance should be realistic")
        assertTrue(estimate5.distanceMeters in 1.0..50.0, "5 GHz distance should be realistic")
    }
}
