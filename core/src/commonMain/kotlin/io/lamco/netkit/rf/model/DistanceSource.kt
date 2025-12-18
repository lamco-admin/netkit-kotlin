package io.lamco.netkit.rf.model

/**
 * Source of distance estimation
 *
 * Different methods have different accuracy and bias characteristics:
 * - **RSSI**: High variance, environment-dependent bias, always available
 * - **RTT**: Low bias, moderate variance, requires WiFi RTT support (API 28+)
 * - **HYBRID**: Combines RSSI and RTT using Kalman filter, best accuracy
 * - **UNKNOWN**: No distance estimate available
 *
 * @property displayName Human-readable name
 * @property accuracy Relative accuracy (0.0 = poor, 1.0 = excellent)
 */
enum class DistanceSource(
    val displayName: String,
    val accuracy: Double,
) {
    /**
     * Distance estimated from RSSI using path loss model
     * - Typical error: ±5-10 meters
     * - Affected by obstacles, multipath, interference
     * - Always available with WiFi scan
     */
    RSSI_ONLY("RSSI-based", accuracy = 0.5),

    /**
     * Distance measured using WiFi RTT (Round Trip Time)
     * - Typical error: ±1-2 meters
     * - Requires Android 9+ (API 28)
     * - Requires AP support for 802.11mc FTM
     */
    RTT_ONLY("RTT-based", accuracy = 0.9),

    /**
     * Hybrid fusion of RSSI and RTT measurements
     * - Best accuracy: ±1-3 meters
     * - Uses Kalman filter to combine both sources
     * - Requires RTT support
     */
    HYBRID("Hybrid (RSSI + RTT)", accuracy = 1.0),

    /**
     * No distance estimate available
     * - Insufficient data
     * - Out of range
     * - Unsupported hardware
     */
    UNKNOWN("Unknown", accuracy = 0.0),
    ;

    /**
     * Whether this source provides usable distance data
     */
    val isValid: Boolean
        get() = this != UNKNOWN

    /**
     * Whether RTT was used in the estimate
     */
    val usesRtt: Boolean
        get() = this == RTT_ONLY || this == HYBRID
}
