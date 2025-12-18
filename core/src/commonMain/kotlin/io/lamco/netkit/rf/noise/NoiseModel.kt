package io.lamco.netkit.rf.noise

import io.lamco.netkit.model.network.WiFiBand

/**
 * Interface for noise floor estimation models
 *
 * Noise floor is the ambient RF noise level that limits the minimum detectable signal.
 * Different models provide trade-offs between accuracy and complexity:
 * - **Static**: Simple band-based defaults, always available
 * - **Calibrated**: Environment-specific values from empirical data
 * - **Adaptive**: Real-time adjustment based on scan history
 *
 * Noise floor affects:
 * - SNR calculation (SNR = RSSI - Noise)
 * - Link margin estimation
 * - Maximum achievable MCS level
 * - Distance estimation accuracy
 *
 * Typical noise floor values:
 * - 2.4 GHz: -92 dBm (higher interference from Bluetooth, microwaves)
 * - 5 GHz: -95 dBm (cleaner spectrum)
 * - 6 GHz: -96 dBm (cleanest spectrum, WiFi 6E exclusive)
 *
 * Standards:
 * - IEEE 802.11-2020 Section 17.3.10.6 (Receiver minimum input sensitivity)
 * - Thermal noise: N = -174 dBm/Hz + 10*log10(bandwidth) + NF
 *
 * @see StaticNoiseModel for simple default values
 * @see CalibratedNoiseModel for environment-specific calibration
 */
interface NoiseModel {
    /**
     * Get estimated noise floor for a specific band
     *
     * @param band WiFi frequency band (2.4/5/6 GHz)
     * @return Estimated noise floor in dBm (typically -90 to -100 dBm)
     */
    fun noiseFloorDbm(band: WiFiBand): Double

    /**
     * Get estimated noise floor for a specific frequency
     *
     * Allows for more granular noise estimation within a band.
     * Default implementation delegates to band-based estimation.
     *
     * @param freqMHz Center frequency in MHz
     * @param band WiFi frequency band
     * @return Estimated noise floor in dBm
     */
    fun noiseFloorDbm(
        freqMHz: Int,
        band: WiFiBand,
    ): Double = noiseFloorDbm(band)

    /**
     * Whether this model has been calibrated with empirical data
     */
    val isCalibrated: Boolean
        get() = false

    /**
     * Confidence level of noise floor estimate (0.0 = low, 1.0 = high)
     * - Static models: 0.5 (educated guess)
     * - Calibrated models: 0.7-0.9 (empirical data)
     * - Adaptive models: 0.6-0.8 (real-time adjustment)
     */
    val confidence: Double
        get() = 0.5
}
