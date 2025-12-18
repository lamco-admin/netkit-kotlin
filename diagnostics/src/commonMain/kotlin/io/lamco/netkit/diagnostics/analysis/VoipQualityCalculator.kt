package io.lamco.netkit.diagnostics.analysis

import io.lamco.netkit.diagnostics.model.VoipCodec
import io.lamco.netkit.diagnostics.model.VoipQuality
import io.lamco.netkit.diagnostics.model.VoipQualityTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * VoIP quality calculator using ITU-T E-model (G.107).
 *
 * Calculates MOS (Mean Opinion Score) and R-Factor from network metrics
 * to predict voice call quality.
 *
 * ## E-Model Formula (Simplified)
 *
 * ```
 * R = R0 - Id - Ie + A
 * ```
 *
 * Where:
 * - **R0**: Base quality = 93.2 (ideal channel with no impairments)
 * - **Id**: Delay impairment (from latency)
 * - **Ie**: Equipment impairment (from codec + packet loss)
 * - **A**: Advantage factor (user tolerance, typically 0 for mobile)
 *
 * ## Delay Impairment (Id)
 *
 * Based on ITU-T G.107 Table B.1:
 * ```
 * If delay < 177ms: Id = 0.024 × delay + 0.11 × (delay - 177) × H(delay - 177)
 * If delay >= 177ms: Id increases non-linearly
 * ```
 *
 * Simplified: Id ≈ delay / 40 for delay < 200ms
 *
 * ## Equipment Impairment (Ie)
 *
 * Combines codec quality and packet loss:
 * - G.711: Ie_base = 0 (best quality)
 * - G.729: Ie_base = 11 (compression artifacts)
 * - Opus: Ie_base = 5 (good quality)
 *
 * Packet loss adds to Ie:
 * ```
 * Ie = Ie_codec + packet_loss_factor
 * ```
 *
 * ## MOS from R-Factor
 *
 * ```
 * If R < 0: MOS = 1.0
 * If R > 100: MOS = 4.5
 * Otherwise: MOS = 1 + 0.035×R + 7×10⁻⁶×R×(R-60)×(100-R)
 * ```
 *
 * ## References
 * - ITU-T G.107: E-model for voice transmission planning
 * - ITU-T G.114: One-way transmission time
 * - ITU-T P.800: Methods for subjective determination of transmission quality
 *
 * @since 1.1.0
 */
object VoipQualityCalculator {
    /**
     * Calculate VoIP quality from network metrics.
     *
     * @param latency Round-trip time (will be halved for one-way delay)
     * @param jitter Packet delay variation
     * @param packetLoss Packet loss percentage (0-100)
     * @param codec VoIP codec to simulate
     * @return VoIP quality assessment
     */
    fun calculate(
        latency: Duration,
        jitter: Duration,
        packetLoss: Double,
        codec: VoipCodec = VoipCodec.G711,
    ): VoipQualityTest {
        require(!latency.isNegative()) { "Latency cannot be negative" }
        require(!jitter.isNegative()) { "Jitter cannot be negative" }
        require(packetLoss in 0.0..100.0) { "Packet loss must be 0-100%" }

        // One-way delay = RTT / 2
        val oneWayDelay = latency.inWholeMilliseconds / 2.0

        // Add codec delay
        val totalDelay = oneWayDelay + codec.codecDelay.inWholeMilliseconds

        // Calculate R-Factor using E-model
        val r0 = 93.2 // Base quality (ideal channel)

        // Delay impairment (Id)
        val id = calculateDelayImpairment(totalDelay)

        // Equipment impairment (Ie) from codec and packet loss
        val ie = calculateEquipmentImpairment(codec, packetLoss)

        // Advantage factor (A) - typically 0 for mobile/consumer
        val a = 0.0

        // R-Factor
        val rFactor = (r0 - id - ie + a).coerceIn(0.0, 100.0)

        // Calculate MOS from R-Factor
        val mos = calculateMos(rFactor)

        // Classify quality
        val quality = VoipQuality.fromMos(mos)

        return VoipQualityTest(
            mos = mos,
            rFactor = rFactor,
            latency = totalDelay.milliseconds,
            jitter = jitter,
            packetLoss = packetLoss,
            codec = codec,
            quality = quality,
            delayImpairment = id,
            equipmentImpairment = ie,
        )
    }

    /**
     * Calculate delay impairment (Id) from total delay.
     *
     * Based on ITU-T G.107 formula (simplified).
     *
     * @param delayMs Total one-way delay in milliseconds
     * @return Id factor
     */
    private fun calculateDelayImpairment(delayMs: Double): Double =
        when {
            delayMs <= 177.0 -> {
                // Linear up to 177ms
                0.024 * delayMs
            }
            else -> {
                // Non-linear above 177ms
                val baseId = 0.024 * 177.0
                val excessDelay = delayMs - 177.0
                baseId + (excessDelay / 20.0) // Simplified non-linear term
            }
        }

    /**
     * Calculate equipment impairment (Ie) from codec and packet loss.
     *
     * @param codec VoIP codec
     * @param packetLoss Packet loss percentage
     * @return Ie factor
     */
    private fun calculateEquipmentImpairment(
        codec: VoipCodec,
        packetLoss: Double,
    ): Double {
        // Base codec impairment (Ie_codec)
        val codecImpairment =
            when (codec) {
                VoipCodec.G711 -> 0.0 // Uncompressed, no impairment
                VoipCodec.G729 -> 11.0 // Compression artifacts
                VoipCodec.OPUS -> 5.0 // Modern, good quality
            }

        // Packet loss impairment
        // Formula from ITU-T G.107 Appendix I (simplified)
        val plcQuality = codec.plcQuality
        val packetLossImpairment =
            if (packetLoss > 0.0) {
                // Higher PLC quality reduces impact of packet loss
                val plcFactor = (100.0 - plcQuality) / 100.0
                packetLoss * 2.5 * plcFactor // Simplified formula
            } else {
                0.0
            }

        return codecImpairment + packetLossImpairment
    }

    /**
     * Calculate MOS from R-Factor.
     *
     * Uses ITU-T G.107 formula for R-to-MOS conversion.
     *
     * @param rFactor R-Factor (0-100)
     * @return MOS (1.0-5.0)
     */
    private fun calculateMos(rFactor: Double): Double =
        when {
            rFactor < 0.0 -> 1.0
            rFactor > 100.0 -> 4.5
            else -> {
                // ITU-T G.107 formula
                1.0 + 0.035 * rFactor + 7.0e-6 * rFactor * (rFactor - 60.0) * (100.0 - rFactor)
            }
        }.coerceIn(1.0, 5.0)

    /**
     * Calculate R-Factor from MOS (inverse calculation).
     *
     * Useful for validating MOS values or setting targets.
     *
     * @param mos Mean Opinion Score (1.0-5.0)
     * @return Approximate R-Factor
     */
    fun mosToRFactor(mos: Double): Double {
        require(mos in 1.0..5.0) { "MOS must be 1.0-5.0" }

        // Simplified inverse (not exact, but close enough)
        return when {
            mos >= 4.3 -> 90.0 + (mos - 4.3) * 20.0 // 90-100
            mos >= 4.0 -> 80.0 + (mos - 4.0) / 0.3 * 10.0 // 80-90
            mos >= 3.6 -> 70.0 + (mos - 3.6) / 0.4 * 10.0 // 70-80
            mos >= 3.1 -> 60.0 + (mos - 3.1) / 0.5 * 10.0 // 60-70
            else -> maxOf(0.0, (mos - 1.0) / 2.1 * 60.0) // 0-60
        }
    }
}
