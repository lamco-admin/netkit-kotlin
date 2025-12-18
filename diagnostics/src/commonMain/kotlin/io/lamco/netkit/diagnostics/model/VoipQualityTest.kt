package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * VoIP quality classification based on MOS.
 */
enum class VoipQuality {
    /** MOS >= 4.3 - Excellent voice quality */
    EXCELLENT,

    /** MOS >= 4.0 - Good voice quality (toll quality) */
    GOOD,

    /** MOS >= 3.6 - Fair voice quality (acceptable) */
    FAIR,

    /** MOS >= 3.1 - Poor voice quality */
    POOR,

    /** MOS < 3.1 - Bad voice quality (unacceptable) */
    BAD,

    ;

    companion object {
        /**
         * Classify VoIP quality from MOS.
         */
        fun fromMos(mos: Double): VoipQuality =
            when {
                mos >= 4.3 -> EXCELLENT
                mos >= 4.0 -> GOOD
                mos >= 3.6 -> FAIR
                mos >= 3.1 -> POOR
                else -> BAD
            }
    }
}

/**
 * VoIP codec simulation.
 */
enum class VoipCodec {
    /** G.711 - 64kbps, uncompressed, 10ms codec delay */
    G711,

    /** G.729 - 8kbps, compressed, 25ms codec delay */
    G729,

    /** Opus - Variable bitrate, modern codec, 20ms delay */
    OPUS,

    ;

    /**
     * Codec delay contribution.
     */
    val codecDelay: Duration
        get() =
            when (this) {
                G711 -> 10.milliseconds
                G729 -> 25.milliseconds
                OPUS -> 20.milliseconds
            }

    /**
     * Packet loss concealment quality (0-100).
     */
    val plcQuality: Double
        get() =
            when (this) {
                G711 -> 60.0 // Basic PLC
                G729 -> 75.0 // Better PLC
                OPUS -> 90.0 // Excellent PLC
            }
}

/**
 * Primary VoIP quality issue.
 */
enum class VoipQualityIssue {
    /** Latency > 150ms one-way */
    HIGH_LATENCY,

    /** Jitter > 30ms */
    HIGH_JITTER,

    /** Packet loss > 3% */
    PACKET_LOSS,

    ;

    val description: String
        get() =
            when (this) {
                HIGH_LATENCY -> "High latency impacting conversation flow"
                HIGH_JITTER -> "High jitter causing choppy audio"
                PACKET_LOSS -> "Packet loss causing audio dropouts"
            }
}

/**
 * VoIP quality test using ITU-T E-model (G.107) for MOS calculation.
 *
 * Simulates voice call quality based on network metrics (latency, jitter, packet loss)
 * and calculates Mean Opinion Score (MOS) and R-Factor per ITU-T standards.
 *
 * ## MOS (Mean Opinion Score)
 *
 * MOS is a 1-5 scale representing perceived voice quality:
 * - **5.0**: Excellent (toll quality)
 * - **4.0**: Good (acceptable for most users)
 * - **3.0**: Fair (some users dissatisfied)
 * - **2.0**: Poor (many users dissatisfied)
 * - **1.0**: Bad (nearly all users dissatisfied)
 *
 * ## R-Factor (ITU-T G.107)
 *
 * R-Factor (0-100) represents transmission quality:
 * - **R >= 90**: Excellent (MOS ≈ 4.3-5.0)
 * - **R >= 80**: Good (MOS ≈ 4.0-4.3)
 * - **R >= 70**: Fair (MOS ≈ 3.6-4.0)
 * - **R >= 60**: Poor (MOS ≈ 3.1-3.6)
 * - **R < 60**: Bad (MOS < 3.1)
 *
 * ## E-Model Calculation
 *
 * ```
 * R = R0 - Id - Ie + A
 * ```
 * Where:
 * - R0: Base quality (93.2 for ideal channel)
 * - Id: Delay impairment (from latency)
 * - Ie: Equipment impairment (from codec + packet loss)
 * - A: Advantage factor (user tolerance)
 *
 * ## Codec Simulation
 *
 * Simulates common VoIP codecs:
 * - **G.711**: 64kbps, no compression, best quality, 10ms latency
 * - **G.729**: 8kbps, compressed, good quality, 25ms latency
 * - **Opus**: Variable bitrate, modern, adaptive
 *
 * ## References
 * - ITU-T G.107: E-model for voice quality
 * - ITU-T G.114: One-way transmission time recommendations
 * - ITU-T P.800: Methods for subjective MOS determination
 * - RFC 3550: RTP for real-time applications
 *
 * @property mos Mean Opinion Score (1.0-5.0)
 * @property rFactor R-Factor (0-100)
 * @property latency One-way delay (RTT / 2)
 * @property jitter Packet delay variation
 * @property packetLoss Packet loss percentage
 * @property codec Simulated VoIP codec
 * @property quality Overall quality classification
 * @property delayImpairment Id factor from E-model
 * @property equipmentImpairment Ie factor from E-model
 *
 * @since 1.1.0
 */
data class VoipQualityTest(
    val mos: Double,
    val rFactor: Double,
    val latency: Duration,
    val jitter: Duration,
    val packetLoss: Double,
    val codec: VoipCodec,
    val quality: VoipQuality,
    val delayImpairment: Double,
    val equipmentImpairment: Double,
    val timestamp: Long = System.currentTimeMillis(),
) {
    init {
        require(mos in 1.0..5.0) { "MOS must be 1.0-5.0, got: $mos" }
        require(rFactor in 0.0..100.0) { "R-Factor must be 0-100, got: $rFactor" }
        require(!latency.isNegative()) { "Latency cannot be negative" }
        require(!jitter.isNegative()) { "Jitter cannot be negative" }
        require(packetLoss in 0.0..100.0) { "Packet loss must be 0-100%" }
    }

    /**
     * Whether VoIP quality is acceptable (MOS >= 3.6).
     *
     * Based on ITU-T recommendations, MOS >= 3.6 is "good enough"
     * for most VoIP applications.
     */
    val isAcceptable: Boolean = mos >= 3.6

    /**
     * Whether quality meets toll-quality standards (MOS >= 4.0).
     */
    val isTollQuality: Boolean = mos >= 4.0

    /**
     * Primary quality issue (if any).
     */
    val primaryIssue: VoipQualityIssue?
        get() =
            when {
                latency.inWholeMilliseconds > 150 -> VoipQualityIssue.HIGH_LATENCY
                jitter.inWholeMilliseconds > 30 -> VoipQualityIssue.HIGH_JITTER
                packetLoss > 3.0 -> VoipQualityIssue.PACKET_LOSS
                else -> null
            }

    /**
     * Recommendations to improve VoIP quality.
     */
    fun recommendations(): List<String> =
        buildList {
            when (quality) {
                VoipQuality.EXCELLENT -> {
                    add("✓ Excellent VoIP quality (MOS: ${"%.2f".format(mos)})")
                    add("Network is ideal for voice calls")
                }
                VoipQuality.GOOD -> {
                    add("✓ Good VoIP quality (MOS: ${"%.2f".format(mos)})")
                    add("Voice calls should work well")
                }
                VoipQuality.FAIR -> {
                    add("⚠ Fair VoIP quality (MOS: ${"%.2f".format(mos)})")
                    when (primaryIssue) {
                        VoipQualityIssue.HIGH_LATENCY -> {
                            add("Primary issue: High latency (${latency.inWholeMilliseconds}ms)")
                            add("Recommended: Check network path, reduce WiFi hop count")
                        }
                        VoipQualityIssue.HIGH_JITTER -> {
                            add("Primary issue: High jitter (${jitter.inWholeMilliseconds}ms)")
                            add("Recommended: Enable QoS, check for interference")
                        }
                        VoipQualityIssue.PACKET_LOSS -> {
                            add("Primary issue: Packet loss (${"%.1f".format(packetLoss)}%)")
                            add("Recommended: Improve signal strength, check interference")
                        }
                        null -> {}
                    }
                }
                VoipQuality.POOR -> {
                    add("⚠ Poor VoIP quality (MOS: ${"%.2f".format(mos)})")
                    add("Voice calls may be choppy or have delays")
                    add("Issues:")
                    if (latency.inWholeMilliseconds > 150) {
                        add("  - High latency: ${latency.inWholeMilliseconds}ms (target: <150ms)")
                    }
                    if (jitter.inWholeMilliseconds > 30) {
                        add("  - High jitter: ${jitter.inWholeMilliseconds}ms (target: <30ms)")
                    }
                    if (packetLoss > 3.0) {
                        add("  - Packet loss: ${"%.1f".format(packetLoss)}% (target: <1%)")
                    }
                    add("Recommended: Enable QoS for VoIP traffic, check bufferbloat")
                }
                VoipQuality.BAD -> {
                    add("🔴 Bad VoIP quality (MOS: ${"%.2f".format(mos)})")
                    add("Voice calls will likely fail or be unintelligible")
                    add("Network unsuitable for VoIP - immediate action required")
                    add("Critical issues:")
                    add("  - Latency: ${latency.inWholeMilliseconds}ms (limit: 150ms)")
                    add("  - Jitter: ${jitter.inWholeMilliseconds}ms (limit: 30ms)")
                    add("  - Packet loss: ${"%.1f".format(packetLoss)}% (limit: 1%)")
                }
            }
        }
}
