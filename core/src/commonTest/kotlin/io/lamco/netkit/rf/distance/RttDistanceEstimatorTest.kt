package io.lamco.netkit.rf.distance

import io.lamco.netkit.model.network.WiFiBand
import io.lamco.netkit.rf.model.DistanceEstimate
import io.lamco.netkit.rf.model.DistanceSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Unit tests for RttDistanceEstimator
 *
 * Covers:
 * - RTT-based distance estimation (802.11mc FTM)
 * - Fallback behavior when RTT unavailable
 * - Distance validation
 * - Accuracy improvements over RSSI
 */
class RttDistanceEstimatorTest {
    private val estimator = RttDistanceEstimator()

    // ========== RTT Distance Estimation Tests ==========

    @Test
    fun `returns RTT distance when available`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 12.5,
                rttStdDevMeters = 1.2,
            )

        assertEquals(12.5, estimate.distanceMeters, 0.01)
        assertEquals(1.2, estimate.stdDevMeters, 0.01)
        assertEquals(DistanceSource.RTT_ONLY, estimate.method)
    }

    @Test
    fun `returns unknown when RTT distance is null`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = null,
                rttStdDevMeters = 1.0,
            )

        assertEquals(DistanceEstimate.unknown(), estimate)
    }

    @Test
    fun `returns unknown when RTT std dev is null`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 10.0,
                rttStdDevMeters = null,
            )

        assertEquals(DistanceEstimate.unknown(), estimate)
    }

    @Test
    fun `returns unknown when both RTT parameters are null`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = null,
                rttStdDevMeters = null,
            )

        assertEquals(DistanceEstimate.unknown(), estimate)
    }

    // ========== RTT Accuracy Tests ==========

    @Test
    fun `RTT has lower uncertainty than RSSI`() {
        val rttEstimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 10.0,
                rttStdDevMeters = 1.2,
            )

        // RTT std dev should be around 1-2 meters (much better than RSSI's 5-10m)
        assertTrue(
            rttEstimate.stdDevMeters < 3.0,
            "RTT uncertainty ${rttEstimate.stdDevMeters}m should be low",
        )
    }

    @Test
    fun `RTT works at short distances`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -40,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 2.0,
                rttStdDevMeters = 0.5,
            )

        assertEquals(2.0, estimate.distanceMeters, 0.01)
        assertTrue(estimate.stdDevMeters < 1.0, "RTT should be accurate at short range")
    }

    @Test
    fun `RTT works at medium distances`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 15.0,
                rttStdDevMeters = 1.5,
            )

        assertEquals(15.0, estimate.distanceMeters, 0.01)
        assertEquals(DistanceSource.RTT_ONLY, estimate.method)
    }

    @Test
    fun `RTT works at long distances`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -75,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 50.0,
                rttStdDevMeters = 2.0,
            )

        assertEquals(50.0, estimate.distanceMeters, 0.01)
        assertEquals(DistanceSource.RTT_ONLY, estimate.method)
    }

    // ========== RTT Validation Tests ==========

    @Test
    fun `rejects negative RTT distance`() {
        assertThrows<IllegalArgumentException> {
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = -5.0,
                rttStdDevMeters = 1.0,
            )
        }
    }

    @Test
    fun `rejects negative RTT std dev`() {
        assertThrows<IllegalArgumentException> {
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 10.0,
                rttStdDevMeters = -1.0,
            )
        }
    }

    @Test
    fun `accepts zero RTT distance`() {
        // Edge case: device essentially at the AP
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -30,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 0.0,
                rttStdDevMeters = 0.1,
            )

        assertEquals(0.0, estimate.distanceMeters, 0.01)
        assertEquals(DistanceSource.RTT_ONLY, estimate.method)
    }

    @Test
    fun `accepts zero RTT std dev`() {
        // Perfect measurement (theoretical)
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -50,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 10.0,
                rttStdDevMeters = 0.0,
            )

        assertEquals(10.0, estimate.distanceMeters, 0.01)
        assertEquals(0.0, estimate.stdDevMeters, 0.01)
    }

    // ========== RTT Realistic Scenarios ==========

    @Test
    fun `typical RTT accuracy - 1 to 2 meters std dev`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 12.0,
                rttStdDevMeters = 1.5,
            )

        assertTrue(
            estimate.stdDevMeters in 0.5..2.5,
            "Typical RTT std dev ${estimate.stdDevMeters}m should be 1-2 meters",
        )
    }

    @Test
    fun `RTT best case accuracy - sub-meter std dev`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -50,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 8.0,
                rttStdDevMeters = 0.5,
            )

        assertTrue(
            estimate.stdDevMeters < 1.0,
            "Best case RTT should achieve sub-meter accuracy",
        )
    }

    @Test
    fun `RTT handles very short range - same room`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -35,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 3.0,
                rttStdDevMeters = 0.8,
            )

        assertTrue(estimate.distanceMeters < 5.0, "Same room should be < 5m")
        assertEquals(DistanceSource.RTT_ONLY, estimate.method)
    }

    @Test
    fun `RTT handles indoor range - different rooms`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -60,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 18.0,
                rttStdDevMeters = 1.5,
            )

        assertTrue(estimate.distanceMeters in 10.0..30.0, "Different rooms: 10-30m")
    }

    @Test
    fun `RTT handles outdoor range`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -70,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 45.0,
                rttStdDevMeters = 2.0,
            )

        assertTrue(estimate.distanceMeters in 30.0..100.0, "Outdoor: 30-100m")
        assertTrue(estimate.stdDevMeters < 3.0, "RTT maintains accuracy outdoors")
    }

    // ========== RSSI Ignored by RTT Estimator ==========

    @Test
    fun `RSSI value does not affect RTT distance`() {
        val estimate1 =
            estimator.estimateDistance(
                rssiDbm = -40, // Strong signal
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 15.0,
                rttStdDevMeters = 1.0,
            )
        val estimate2 =
            estimator.estimateDistance(
                rssiDbm = -80, // Weak signal
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 15.0,
                rttStdDevMeters = 1.0,
            )

        // RTT should produce same result regardless of RSSI
        assertEquals(estimate1.distanceMeters, estimate2.distanceMeters, 0.01)
        assertEquals(estimate1.stdDevMeters, estimate2.stdDevMeters, 0.01)
    }

    @Test
    fun `band does not affect RTT distance`() {
        val estimate24 =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_2_4GHZ,
                rttDistanceMeters = 12.0,
                rttStdDevMeters = 1.2,
            )
        val estimate5 =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 12.0,
                rttStdDevMeters = 1.2,
            )

        // RTT is frequency-independent
        assertEquals(estimate24.distanceMeters, estimate5.distanceMeters, 0.01)
        assertEquals(estimate24.stdDevMeters, estimate5.stdDevMeters, 0.01)
    }

    // ========== RTT vs RSSI Comparison ==========

    @Test
    fun `RTT source has higher accuracy than RSSI source`() {
        val rttAccuracy = DistanceSource.RTT_ONLY.accuracy
        val rssiAccuracy = DistanceSource.RSSI_ONLY.accuracy

        assertTrue(rttAccuracy > rssiAccuracy, "RTT should have higher accuracy score than RSSI")
    }

    @Test
    fun `RTT estimates are marked as valid`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 10.0,
                rttStdDevMeters = 1.0,
            )

        assertTrue(estimate.method.isValid, "RTT estimates should be marked as valid")
    }

    @Test
    fun `unknown estimates are marked as invalid`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = null,
                rttStdDevMeters = null,
            )

        assertFalse(estimate.method.isValid, "Unknown source should be invalid")
    }

    @Test
    fun `RTT source indicates RTT usage`() {
        val estimate =
            estimator.estimateDistance(
                rssiDbm = -65,
                band = WiFiBand.BAND_5GHZ,
                rttDistanceMeters = 10.0,
                rttStdDevMeters = 1.0,
            )

        assertTrue(estimate.method.usesRtt, "RTT_ONLY source should indicate RTT usage")
    }
}
