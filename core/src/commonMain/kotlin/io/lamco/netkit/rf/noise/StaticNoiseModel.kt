package io.lamco.netkit.rf.noise

import io.lamco.netkit.model.network.WiFiBand

/**
 * Static noise floor model using fixed values per band
 *
 * This is the simplest and most portable noise model. It uses literature-based
 * default values that are reasonable for typical indoor environments.
 *
 * Default values (configurable):
 * - **2.4 GHz**: -92 dBm (crowded spectrum, Bluetooth/microwave interference)
 * - **5 GHz**: -95 dBm (less crowded, better isolation)
 * - **6 GHz**: -96 dBm (cleanest spectrum, WiFi 6E exclusive)
 * - **60 GHz**: -90 dBm (very short range, different propagation)
 *
 * These values represent typical noise floors in residential/office environments.
 * Industrial or outdoor environments may have different characteristics.
 *
 * Advantages:
 * - Always available (no calibration needed)
 * - Consistent and predictable
 * - Simple to understand and debug
 *
 * Limitations:
 * - Not environment-specific
 * - Cannot adapt to local interference
 * - May over/underestimate in atypical environments
 *
 * Usage:
 * ```kotlin
 * val noiseModel = StaticNoiseModel.default()
 * val noise24 = noiseModel.noiseFloorDbm(WiFiBand.BAND_2_4GHZ)
 * // Returns: -92.0 dBm
 * ```
 *
 * @property bandFloors Map of WiFi bands to noise floor values in dBm
 * @property defaultFloor Fallback noise floor for unknown bands
 */
class StaticNoiseModel(
    private val bandFloors: Map<WiFiBand, Double>,
    private val defaultFloor: Double = DEFAULT_NOISE_FLOOR,
) : NoiseModel {
    init {
        bandFloors.values.forEach { noise ->
            require(noise in -120.0..0.0) {
                "Noise floor must be between -120 and 0 dBm: $noise"
            }
        }
        require(defaultFloor in -120.0..0.0) {
            "Default noise floor must be between -120 and 0 dBm: $defaultFloor"
        }
    }

    override fun noiseFloorDbm(band: WiFiBand): Double = bandFloors[band] ?: defaultFloor

    override val isCalibrated: Boolean = false

    override val confidence: Double = 0.5 // Moderate confidence (educated defaults)

    /**
     * Create a copy with updated noise floor for a specific band
     */
    fun withNoise(
        band: WiFiBand,
        noiseDbm: Double,
    ): StaticNoiseModel {
        require(noiseDbm in -120.0..0.0) {
            "Noise floor must be between -120 and 0 dBm: $noiseDbm"
        }
        return StaticNoiseModel(
            bandFloors = bandFloors + (band to noiseDbm),
            defaultFloor = defaultFloor,
        )
    }

    companion object {
        /**
         * Default noise floor for unknown bands (typical indoor environment)
         */
        const val DEFAULT_NOISE_FLOOR = -95.0

        /**
         * Standard noise floor values from IEEE 802.11 and empirical data
         */
        private val STANDARD_FLOORS =
            mapOf(
                WiFiBand.BAND_2_4GHZ to -92.0, // Crowded, Bluetooth/microwave interference
                WiFiBand.BAND_5GHZ to -95.0, // Cleaner spectrum
                WiFiBand.BAND_6GHZ to -96.0, // Cleanest, WiFi 6E exclusive
            )

        /**
         * Conservative noise floor values (assume worse environment)
         * Use for pessimistic SNR estimation or when interference is expected
         */
        private val CONSERVATIVE_FLOORS =
            mapOf(
                WiFiBand.BAND_2_4GHZ to -88.0, // 4 dB worse than standard
                WiFiBand.BAND_5GHZ to -92.0, // 3 dB worse
                WiFiBand.BAND_6GHZ to -93.0, // 3 dB worse
            )

        /**
         * Optimistic noise floor values (assume clean environment)
         * Use for optimistic SNR estimation or in known-clean environments
         */
        private val OPTIMISTIC_FLOORS =
            mapOf(
                WiFiBand.BAND_2_4GHZ to -95.0, // 3 dB better than standard
                WiFiBand.BAND_5GHZ to -98.0, // 3 dB better
                WiFiBand.BAND_6GHZ to -99.0, // 3 dB better
            )

        /**
         * Create model with standard (typical) noise floor values
         *
         * Recommended for general use in residential/office environments.
         */
        fun default(): StaticNoiseModel = StaticNoiseModel(STANDARD_FLOORS)

        /**
         * Create model with conservative noise floor values
         *
         * Use when:
         * - High interference expected (dense urban, industrial)
         * - Pessimistic SNR estimation desired
         * - Worst-case scenario planning
         */
        fun conservative(): StaticNoiseModel = StaticNoiseModel(CONSERVATIVE_FLOORS)

        /**
         * Create model with optimistic noise floor values
         *
         * Use when:
         * - Clean environment known (rural, low-density)
         * - Optimistic SNR estimation desired
         * - Best-case scenario planning
         */
        fun optimistic(): StaticNoiseModel = StaticNoiseModel(OPTIMISTIC_FLOORS)

        /**
         * Create custom model with user-specified values
         */
        fun custom(
            noise24GHz: Double = STANDARD_FLOORS[WiFiBand.BAND_2_4GHZ]!!,
            noise5GHz: Double = STANDARD_FLOORS[WiFiBand.BAND_5GHZ]!!,
            noise6GHz: Double = STANDARD_FLOORS[WiFiBand.BAND_6GHZ]!!,
        ): StaticNoiseModel =
            StaticNoiseModel(
                mapOf(
                    WiFiBand.BAND_2_4GHZ to noise24GHz,
                    WiFiBand.BAND_5GHZ to noise5GHz,
                    WiFiBand.BAND_6GHZ to noise6GHz,
                ),
            )
    }
}
