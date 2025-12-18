package io.lamco.netkit.rf.noise

import io.lamco.netkit.model.network.WiFiBand
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for StaticNoiseModel
 *
 * Covers:
 * - Default/conservative/optimistic presets
 * - Band-specific noise floors
 * - Frequency-specific lookups
 * - Custom noise floor configuration
 * - Calibration and confidence properties
 */
class StaticNoiseModelTest {
    @Test
    fun `default model returns standard noise floors`() {
        val model = StaticNoiseModel.default()

        assertEquals(-92.0, model.noiseFloorDbm(WiFiBand.BAND_2_4GHZ), 0.1)
        assertEquals(-95.0, model.noiseFloorDbm(WiFiBand.BAND_5GHZ), 0.1)
        assertEquals(-96.0, model.noiseFloorDbm(WiFiBand.BAND_6GHZ), 0.1)
    }

    @Test
    fun `conservative model returns higher (worse) noise floors`() {
        val model = StaticNoiseModel.conservative()

        // Conservative should be worse (higher dBm) than default
        assertTrue(model.noiseFloorDbm(WiFiBand.BAND_2_4GHZ) > -92.0)
        assertTrue(model.noiseFloorDbm(WiFiBand.BAND_5GHZ) > -95.0)
        assertTrue(model.noiseFloorDbm(WiFiBand.BAND_6GHZ) > -96.0)
    }

    @Test
    fun `optimistic model returns lower (better) noise floors`() {
        val model = StaticNoiseModel.optimistic()

        // Optimistic should be better (lower dBm) than default
        assertTrue(model.noiseFloorDbm(WiFiBand.BAND_2_4GHZ) < -92.0)
        assertTrue(model.noiseFloorDbm(WiFiBand.BAND_5GHZ) < -95.0)
        assertTrue(model.noiseFloorDbm(WiFiBand.BAND_6GHZ) < -96.0)
    }

    @Test
    fun `frequency-specific lookup delegates to band lookup`() {
        val model = StaticNoiseModel.default()

        // 2.4 GHz frequencies
        assertEquals(
            model.noiseFloorDbm(WiFiBand.BAND_2_4GHZ),
            model.noiseFloorDbm(2412, WiFiBand.BAND_2_4GHZ),
        )
        assertEquals(
            model.noiseFloorDbm(WiFiBand.BAND_2_4GHZ),
            model.noiseFloorDbm(2462, WiFiBand.BAND_2_4GHZ),
        )

        // 5 GHz frequencies
        assertEquals(
            model.noiseFloorDbm(WiFiBand.BAND_5GHZ),
            model.noiseFloorDbm(5180, WiFiBand.BAND_5GHZ),
        )
        assertEquals(
            model.noiseFloorDbm(WiFiBand.BAND_5GHZ),
            model.noiseFloorDbm(5825, WiFiBand.BAND_5GHZ),
        )
    }

    @Test
    fun `custom noise floors work correctly`() {
        val customFloors =
            mapOf(
                WiFiBand.BAND_2_4GHZ to -88.0,
                WiFiBand.BAND_5GHZ to -93.0,
                WiFiBand.BAND_6GHZ to -94.0,
            )
        val model = StaticNoiseModel(customFloors)

        assertEquals(-88.0, model.noiseFloorDbm(WiFiBand.BAND_2_4GHZ))
        assertEquals(-93.0, model.noiseFloorDbm(WiFiBand.BAND_5GHZ))
        assertEquals(-94.0, model.noiseFloorDbm(WiFiBand.BAND_6GHZ))
    }

    @Test
    fun `unknown band returns default noise floor`() {
        val model = StaticNoiseModel.default()

        // 60 GHz is defined, but let's test with explicit default
        val customModel =
            StaticNoiseModel(
                mapOf(WiFiBand.BAND_2_4GHZ to -92.0),
                defaultFloor = -100.0,
            )

        assertEquals(-100.0, customModel.noiseFloorDbm(WiFiBand.BAND_5GHZ))
        assertEquals(-100.0, customModel.noiseFloorDbm(WiFiBand.BAND_6GHZ))
    }

    @Test
    fun `model reports not calibrated`() {
        val model = StaticNoiseModel.default()
        assertFalse(model.isCalibrated)
    }

    @Test
    fun `model reports medium confidence`() {
        val model = StaticNoiseModel.default()
        assertEquals(0.5, model.confidence, 0.01)
    }

    @Test
    fun `2_4GHz has higher (worse) noise floor than 5GHz`() {
        val model = StaticNoiseModel.default()

        // 2.4 GHz is more congested (Bluetooth, microwaves, etc.)
        assertTrue(model.noiseFloorDbm(WiFiBand.BAND_2_4GHZ) > model.noiseFloorDbm(WiFiBand.BAND_5GHZ))
    }

    @Test
    fun `5GHz has higher (worse) noise floor than 6GHz`() {
        val model = StaticNoiseModel.default()

        // 6 GHz is cleanest (WiFi 6E exclusive)
        assertTrue(model.noiseFloorDbm(WiFiBand.BAND_5GHZ) > model.noiseFloorDbm(WiFiBand.BAND_6GHZ))
    }

    @Test
    fun `noise floor values are within realistic range`() {
        val model = StaticNoiseModel.default()

        // All noise floors should be between -100 dBm (very clean) and -85 dBm (very noisy)
        for (band in listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ, WiFiBand.BAND_6GHZ)) {
            val noiseFloor = model.noiseFloorDbm(band)
            assertTrue(noiseFloor in -100.0..-85.0, "Noise floor $noiseFloor out of realistic range for $band")
        }
    }

    @Test
    fun `conservative is within 3-4 dB worse than default`() {
        val defaultModel = StaticNoiseModel.default()
        val conservativeModel = StaticNoiseModel.conservative()

        for (band in listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ, WiFiBand.BAND_6GHZ)) {
            val diff = conservativeModel.noiseFloorDbm(band) - defaultModel.noiseFloorDbm(band)
            assertTrue(diff in 3.0..4.0, "Conservative difference $diff not in expected range for $band")
        }
    }

    @Test
    fun `optimistic is within 3 dB better than default`() {
        val defaultModel = StaticNoiseModel.default()
        val optimisticModel = StaticNoiseModel.optimistic()

        for (band in listOf(WiFiBand.BAND_2_4GHZ, WiFiBand.BAND_5GHZ, WiFiBand.BAND_6GHZ)) {
            val diff = defaultModel.noiseFloorDbm(band) - optimisticModel.noiseFloorDbm(band)
            assertTrue(diff in 2.5..3.5, "Optimistic difference $diff not in expected range for $band")
        }
    }
}
