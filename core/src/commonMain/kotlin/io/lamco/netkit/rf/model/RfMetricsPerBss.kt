package io.lamco.netkit.rf.model

import io.lamco.netkit.model.network.WiFiBand

/**
 * RF metrics for a specific BSS (Access Point)
 *
 * Aggregates all radio frequency measurements and derived metrics for a single AP.
 * These metrics enable signal quality analysis, capacity estimation, and distance calculation.
 *
 * Key metrics:
 * - **RSSI**: Received Signal Strength Indicator (dBm)
 * - **Noise**: Estimated noise floor for the band
 * - **SNR**: Signal-to-Noise Ratio (dB)
 * - **Link Margin**: Additional SNR beyond minimum required for current MCS
 * - **Distance**: Estimated distance to AP
 * - **Capacity**: Maximum achievable throughput
 *
 * @property bssid MAC address of the AP (BSSID)
 * @property band WiFi frequency band (2.4/5/6 GHz)
 * @property channel Primary channel number
 * @property freqMHz Center frequency in MHz
 * @property rssiDbm Received Signal Strength in dBm
 * @property estimatedNoiseDbm Estimated noise floor in dBm
 * @property snrDb Signal-to-Noise Ratio in dB
 * @property linkMarginDb Link margin above minimum required SNR (null if unknown)
 * @property distanceMeters Estimated distance in meters (null if unavailable)
 * @property distanceSource Method used for distance estimation
 * @property estimatedMaxMcs Highest achievable MCS level (null if unknown)
 * @property estimatedMaxPhyMbps Maximum PHY layer data rate in Mbps (null if unknown)
 */
data class RfMetricsPerBss(
    val bssid: String,
    val band: WiFiBand,
    val channel: Int,
    val freqMHz: Int,
    val rssiDbm: Int,
    val estimatedNoiseDbm: Double,
    val snrDb: Double,
    val linkMarginDb: Double? = null,
    val distanceMeters: Double? = null,
    val distanceSource: DistanceSource = DistanceSource.UNKNOWN,
    val estimatedMaxMcs: McsLevel? = null,
    val estimatedMaxPhyMbps: Double? = null,
) {
    init {
        require(rssiDbm in -120..0) { "RSSI must be between -120 and 0 dBm: $rssiDbm" }
        require(estimatedNoiseDbm in -120.0..0.0) { "Noise must be between -120 and 0 dBm: $estimatedNoiseDbm" }
        require(channel > 0) { "Channel must be positive: $channel" }
        require(freqMHz > 0) { "Frequency must be positive: $freqMHz" }
        distanceMeters?.let { require(it >= 0.0) { "Distance cannot be negative: $it" } }
        estimatedMaxPhyMbps?.let { require(it > 0.0) { "PHY rate must be positive: $it" } }
    }

    /**
     * Signal quality category based on RSSI
     * - Excellent: >= -50 dBm
     * - Good: >= -60 dBm
     * - Fair: >= -70 dBm
     * - Poor: >= -80 dBm
     * - Very Poor: < -80 dBm
     */
    val signalQuality: SignalQuality
        get() =
            when {
                rssiDbm >= -50 -> SignalQuality.EXCELLENT
                rssiDbm >= -60 -> SignalQuality.GOOD
                rssiDbm >= -70 -> SignalQuality.FAIR
                rssiDbm >= -80 -> SignalQuality.POOR
                else -> SignalQuality.VERY_POOR
            }

    /**
     * Whether signal quality is acceptable for reliable connectivity
     * Requires RSSI >= -80 dBm and SNR >= 10 dB
     */
    val isReliableSignal: Boolean
        get() = rssiDbm >= -80 && snrDb >= 10.0

    /**
     * Whether signal supports high-performance WiFi (MCS 8+)
     * Requires RSSI >= -60 dBm and SNR >= 25 dB
     */
    val supportsHighPerformance: Boolean
        get() = rssiDbm >= -60 && snrDb >= 25.0

    /**
     * Estimated maximum throughput as percentage of ideal PHY rate
     * Accounts for realistic overhead (30-50%)
     */
    val realisticMaxThroughputPct: Double
        get() =
            when (signalQuality) {
                SignalQuality.EXCELLENT -> 0.70 // 70% efficiency
                SignalQuality.GOOD -> 0.60 // 60% efficiency
                SignalQuality.FAIR -> 0.45 // 45% efficiency
                SignalQuality.POOR -> 0.30 // 30% efficiency
                SignalQuality.VERY_POOR -> 0.15 // 15% efficiency
            }

    /**
     * Estimated effective throughput in Mbps (accounting for overhead)
     */
    val estimatedEffectiveMbps: Double?
        get() = estimatedMaxPhyMbps?.let { it * realisticMaxThroughputPct }
}

/**
 * Signal quality categories based on RSSI thresholds
 *
 * @property minRssiDbm Minimum RSSI for this quality level
 * @property displayName Human-readable name
 * @property description Quality description
 */
enum class SignalQuality(
    val minRssiDbm: Int,
    val displayName: String,
    val description: String,
) {
    EXCELLENT(
        minRssiDbm = -50,
        displayName = "Excellent",
        description = "Maximum performance, lowest latency, optimal for all applications",
    ),
    GOOD(
        minRssiDbm = -60,
        displayName = "Good",
        description = "High performance, suitable for 4K streaming, gaming, video calls",
    ),
    FAIR(
        minRssiDbm = -70,
        displayName = "Fair",
        description = "Adequate performance, suitable for HD streaming, browsing, email",
    ),
    POOR(
        minRssiDbm = -80,
        displayName = "Poor",
        description = "Marginal performance, frequent buffering, high latency",
    ),
    VERY_POOR(
        minRssiDbm = Int.MIN_VALUE,
        displayName = "Very Poor",
        description = "Unreliable connectivity, frequent disconnections",
    ),
    ;

    /**
     * Numeric score (0-100) for this quality level
     */
    val score: Int
        get() =
            when (this) {
                EXCELLENT -> 100
                GOOD -> 80
                FAIR -> 60
                POOR -> 40
                VERY_POOR -> 20
            }
}
