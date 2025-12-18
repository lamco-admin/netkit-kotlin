package io.lamco.netkit.rf.model

import io.lamco.netkit.model.network.ChannelWidth
import io.lamco.netkit.model.network.WiFiBand

/**
 * Capacity and throughput estimation for a BSS
 *
 * Estimates maximum and effective throughput based on WiFi standard, channel width,
 * spatial streams, MCS level, and environmental factors.
 *
 * Key metrics:
 * - **Max PHY Rate**: Theoretical maximum from MCS tables
 * - **Effective Throughput**: Realistic throughput accounting for protocol overhead
 * - **Utilization-Adjusted**: Throughput accounting for channel congestion
 *
 * @property bssid MAC address of the AP
 * @property band WiFi frequency band
 * @property channelWidth Channel bandwidth (20/40/80/160/320 MHz)
 * @property nss Number of spatial streams (1-8 for WiFi 5/6, up to 16 for WiFi 7)
 * @property maxMcs Maximum MCS level achievable with current SNR
 * @property maxPhyRateMbps Maximum PHY layer data rate in Mbps
 * @property estimatedEffectiveDownlinkMbps Effective downlink throughput (accounting for overhead)
 * @property estimatedEffectiveUplinkMbps Effective uplink throughput (typically lower than downlink)
 * @property utilizationAdjustedDownlinkMbps Downlink throughput adjusted for channel utilization
 */
data class CapacityMetrics(
    val bssid: String,
    val band: WiFiBand,
    val channelWidth: ChannelWidth,
    val nss: Int?,
    val maxMcs: McsLevel?,
    val maxPhyRateMbps: Double?,
    val estimatedEffectiveDownlinkMbps: Double?,
    val estimatedEffectiveUplinkMbps: Double?,
    val utilizationAdjustedDownlinkMbps: Double?,
) {
    init {
        nss?.let { require(it in 1..16) { "NSS must be 1-16: $it" } }
        maxPhyRateMbps?.let { require(it > 0.0) { "PHY rate must be positive: $it" } }
        estimatedEffectiveDownlinkMbps?.let { require(it >= 0.0) { "Effective downlink cannot be negative: $it" } }
        estimatedEffectiveUplinkMbps?.let { require(it >= 0.0) { "Effective uplink cannot be negative: $it" } }
        utilizationAdjustedDownlinkMbps?.let { require(it >= 0.0) { "Adjusted downlink cannot be negative: $it" } }
    }

    /**
     * Protocol overhead factor (PHY → MAC)
     * - WiFi 4/5: ~50% overhead (0.5 efficiency)
     * - WiFi 6: ~40% overhead (0.6 efficiency)
     * - WiFi 7: ~30% overhead (0.7 efficiency)
     */
    val protocolOverheadFactor: Double
        get() =
            when (channelWidth) {
                ChannelWidth.WIDTH_320MHZ -> 0.70 // WiFi 7
                ChannelWidth.WIDTH_160MHZ, ChannelWidth.WIDTH_80MHZ -> 0.60 // WiFi 6/5
                else -> 0.50 // WiFi 4
            }

    /**
     * Whether this BSS supports gigabit-class throughput (>= 1000 Mbps effective)
     */
    val isGigabitCapable: Boolean
        get() = estimatedEffectiveDownlinkMbps?.let { it >= 1000.0 } ?: false

    /**
     * Whether this BSS supports multi-gigabit throughput (>= 2000 Mbps effective)
     */
    val isMultiGigabitCapable: Boolean
        get() = estimatedEffectiveDownlinkMbps?.let { it >= 2000.0 } ?: false

    /**
     * Capacity utilization percentage (current usage / max capacity)
     * null if utilization-adjusted throughput not available
     */
    val utilizationPct: Double?
        get() =
            if (estimatedEffectiveDownlinkMbps != null && utilizationAdjustedDownlinkMbps != null) {
                val utilized = estimatedEffectiveDownlinkMbps - utilizationAdjustedDownlinkMbps
                (utilized / estimatedEffectiveDownlinkMbps * 100.0).coerceIn(0.0, 100.0)
            } else {
                null
            }

    /**
     * Available capacity in Mbps (remaining after current utilization)
     */
    val availableCapacityMbps: Double?
        get() = utilizationAdjustedDownlinkMbps

    /**
     * Capacity category based on effective throughput
     */
    val capacityCategory: CapacityClass
        get() =
            when {
                estimatedEffectiveDownlinkMbps == null -> CapacityClass.UNKNOWN
                estimatedEffectiveDownlinkMbps >= 5000.0 -> CapacityClass.ULTRA_HIGH // 5+ Gbps (WiFi 7)
                estimatedEffectiveDownlinkMbps >= 2000.0 -> CapacityClass.VERY_HIGH // 2-5 Gbps (WiFi 6E/7)
                estimatedEffectiveDownlinkMbps >= 1000.0 -> CapacityClass.HIGH // 1-2 Gbps (WiFi 6)
                estimatedEffectiveDownlinkMbps >= 500.0 -> CapacityClass.MEDIUM // 500-1000 Mbps (WiFi 5)
                estimatedEffectiveDownlinkMbps >= 100.0 -> CapacityClass.LOW // 100-500 Mbps (WiFi 4)
                else -> CapacityClass.VERY_LOW // < 100 Mbps
            }

    /**
     * Whether uplink/downlink asymmetry is significant (> 20% difference)
     */
    val hasAsymmetricCapacity: Boolean
        get() =
            if (estimatedEffectiveDownlinkMbps != null && estimatedEffectiveUplinkMbps != null) {
                val ratio = estimatedEffectiveUplinkMbps / estimatedEffectiveDownlinkMbps
                ratio < 0.8 // Uplink < 80% of downlink
            } else {
                false
            }
}

/**
 * Capacity classification categories
 *
 * @property displayName Human-readable name
 * @property description Capacity description with typical use cases
 */
enum class CapacityClass(
    val displayName: String,
    val description: String,
) {
    ULTRA_HIGH(
        displayName = "Ultra-High (5+ Gbps)",
        description = "WiFi 7, ideal for 8K streaming, VR/AR, multi-device households",
    ),
    VERY_HIGH(
        displayName = "Very High (2-5 Gbps)",
        description = "WiFi 6E/7, excellent for 4K streaming, gaming, video conferencing",
    ),
    HIGH(
        displayName = "High (1-2 Gbps)",
        description = "WiFi 6, great for HD/4K streaming, gaming, home office",
    ),
    MEDIUM(
        displayName = "Medium (500 Mbps - 1 Gbps)",
        description = "WiFi 5, good for HD streaming, browsing, light gaming",
    ),
    LOW(
        displayName = "Low (100-500 Mbps)",
        description = "WiFi 4, adequate for browsing, email, SD streaming",
    ),
    VERY_LOW(
        displayName = "Very Low (< 100 Mbps)",
        description = "Legacy WiFi, suitable for basic browsing and email only",
    ),
    UNKNOWN(
        displayName = "Unknown",
        description = "Capacity cannot be estimated",
    ),
    ;

    /**
     * Minimum typical throughput for this class in Mbps
     */
    val minTypicalMbps: Double
        get() =
            when (this) {
                ULTRA_HIGH -> 5000.0
                VERY_HIGH -> 2000.0
                HIGH -> 1000.0
                MEDIUM -> 500.0
                LOW -> 100.0
                VERY_LOW -> 10.0
                UNKNOWN -> 0.0
            }
}
