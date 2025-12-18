package io.lamco.netkit.advisor.troubleshooting

/**
 * Network symptom classification
 *
 * Represents observable issues reported by users or detected by the system.
 * Symptoms are the starting point for troubleshooting workflows.
 *
 * Symptom Categories:
 * - **Connectivity**: Cannot connect, frequent disconnects
 * - **Performance**: Slow speeds, high latency
 * - **Coverage**: Weak signal, dead zones
 * - **Stability**: Intermittent issues, periodic failures
 *
 * Example Usage:
 * ```kotlin
 * val symptom = Symptom.SlowSpeed(
 *     reportedSpeed = 10.0,  // Mbps
 *     expectedSpeed = 100.0,
 *     affectedDevices = listOf("Laptop", "Phone")
 * )
 * ```
 */
sealed class Symptom {
    /**
     * Human-readable description of the symptom
     */
    abstract val description: String

    /**
     * Severity of the symptom (1-10, 10 = critical)
     */
    abstract val severity: Int

    /**
     * Slow network speed
     */
    data class SlowSpeed(
        val reportedSpeed: Double, // Mbps
        val expectedSpeed: Double, // Mbps
        val affectedDevices: List<String> = emptyList(),
    ) : Symptom() {
        override val description = "Slow network speed: ${reportedSpeed}Mbps (expected ${expectedSpeed}Mbps)"
        override val severity =
            when {
                reportedSpeed < expectedSpeed * 0.1 -> 9 // <10% of expected
                reportedSpeed < expectedSpeed * 0.25 -> 7 // <25% of expected
                reportedSpeed < expectedSpeed * 0.5 -> 5 // <50% of expected
                else -> 3
            }
    }

    /**
     * Frequent disconnections
     */
    data class FrequentDisconnects(
        val disconnectsPerHour: Int,
        val affectedDevices: List<String> = emptyList(),
    ) : Symptom() {
        override val description = "Frequent disconnects: $disconnectsPerHour per hour"
        override val severity =
            when {
                disconnectsPerHour >= 10 -> 10
                disconnectsPerHour >= 5 -> 8
                disconnectsPerHour >= 2 -> 6
                else -> 4
            }
    }

    /**
     * Poor WiFi coverage / weak signal
     */
    data class PoorCoverage(
        val signalStrength: Int, // dBm
        val location: String,
    ) : Symptom() {
        override val description = "Poor coverage in $location: ${signalStrength}dBm"
        override val severity =
            when {
                signalStrength < -80 -> 8
                signalStrength < -75 -> 6
                signalStrength < -70 -> 4
                else -> 2
            }
    }

    /**
     * High latency / ping issues
     */
    data class HighLatency(
        val latencyMs: Int,
        val targetHost: String = "gateway",
    ) : Symptom() {
        override val description = "High latency to $targetHost: ${latencyMs}ms"
        override val severity =
            when {
                latencyMs > 500 -> 9
                latencyMs > 200 -> 7
                latencyMs > 100 -> 5
                else -> 3
            }
    }

    /**
     * Cannot connect to network
     */
    data class CannotConnect(
        val errorMessage: String? = null,
        val deviceType: String? = null,
    ) : Symptom() {
        override val description = "Cannot connect${deviceType?.let { " ($it)" } ?: ""}${errorMessage?.let { ": $it" } ?: ""}"
        override val severity = 10 // Critical - no connectivity
    }

    /**
     * Intermittent connectivity issues
     */
    data class IntermittentIssues(
        val frequency: String, // "hourly", "daily", "random"
        val duration: String, // "seconds", "minutes", "hours"
    ) : Symptom() {
        override val description = "Intermittent issues ($frequency, lasting $duration)"
        override val severity = 7
    }

    /**
     * Interference detected
     */
    data class InterferenceDetected(
        val interferenceLevel: Double, // 0.0-1.0
        val band: String, // "2.4GHz", "5GHz"
    ) : Symptom() {
        override val description = "Interference on $band: ${(interferenceLevel * 100).toInt()}%"
        override val severity = (interferenceLevel * 10).toInt().coerceIn(1, 10)
    }

    /**
     * Custom symptom
     */
    data class Custom(
        val symptomDescription: String,
        val symptomSeverity: Int = 5,
    ) : Symptom() {
        override val description = symptomDescription
        override val severity = symptomSeverity.coerceIn(1, 10)
    }
}

/**
 * Network issue classification
 *
 * Represents identified problems with root causes.
 * Issues are diagnosed from symptoms through troubleshooting.
 */
enum class NetworkIssue {
    // Connectivity Issues
    WRONG_PASSWORD,
    WEAK_SIGNAL,
    AP_OVERLOADED,
    CHANNEL_CONGESTION,
    INTERFERENCE,

    // Performance Issues
    BANDWIDTH_SATURATION,
    HIGH_LATENCY,
    PACKET_LOSS,
    SLOW_DNS,
    ISP_ISSUE,

    // Configuration Issues
    INCORRECT_CHANNEL,
    SUBOPTIMAL_POWER,
    POOR_AP_PLACEMENT,
    MISSING_BAND_STEERING,
    OUTDATED_FIRMWARE,

    // Hardware Issues
    FAILING_HARDWARE,
    INSUFFICIENT_COVERAGE,
    DEVICE_COMPATIBILITY,

    // Security Issues
    WEAK_ENCRYPTION,
    UNAUTHORIZED_ACCESS,
    DOS_ATTACK,

    // Client Issues
    CLIENT_ROAMING_FAILURE,
    CLIENT_DRIVER_ISSUE,
    CLIENT_POWER_SAVE_ISSUE,
    ;

    /**
     * User-friendly display name
     */
    val displayName: String
        get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * Root cause with evidence and confidence
 *
 * @property cause Identified root cause
 * @property probability Confidence in diagnosis (0.0-1.0)
 * @property evidence Supporting evidence for this diagnosis
 * @property fixSuggestion How to resolve this issue
 */
data class RootCause(
    val cause: NetworkIssue,
    val probability: Double,
    val evidence: List<String>,
    val fixSuggestion: String,
) {
    init {
        require(probability in 0.0..1.0) { "Probability must be between 0.0 and 1.0" }
        require(evidence.isNotEmpty()) { "Root cause must have supporting evidence" }
    }

    /**
     * Summary of root cause
     */
    val summary: String
        get() = "${cause.displayName} (${(probability * 100).toInt()}% confidence)"
}

/**
 * Troubleshooting step in diagnostic workflow
 *
 * @property step Step number in sequence
 * @property action Action to perform
 * @property expectedOutcome What should happen if successful
 * @property ifSuccess Next step if this succeeds
 * @property ifFailure Next step if this fails
 * @property estimatedTime Estimated time to complete (optional)
 */
data class TroubleshootingStep(
    val step: Int,
    val action: String,
    val expectedOutcome: String,
    val ifSuccess: String,
    val ifFailure: String,
    val estimatedTime: String? = null,
) {
    /**
     * Summary of the step
     */
    val summary: String
        get() = "Step $step: $action"
}

/**
 * Diagnostic result from troubleshooting engine
 *
 * @property symptoms Symptoms analyzed
 * @property rootCauses Identified root causes (sorted by probability)
 * @property confidence Overall diagnostic confidence (0.0-1.0)
 * @property troubleshootingSteps Recommended troubleshooting steps
 * @property estimatedResolutionTime Estimated time to resolve
 */
data class DiagnosisResult(
    val symptoms: List<Symptom>,
    val rootCauses: List<RootCause>,
    val confidence: Double,
    val troubleshootingSteps: List<TroubleshootingStep>,
    val estimatedResolutionTime: String,
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
    }

    /**
     * Most likely root cause
     */
    val primaryCause: RootCause?
        get() = rootCauses.maxByOrNull { it.probability }

    /**
     * Whether diagnosis has high confidence (>= 0.7)
     */
    val isHighConfidence: Boolean
        get() = confidence >= 0.7

    /**
     * Summary of diagnosis
     */
    val summary: String
        get() =
            buildString {
                append("Diagnosed ${rootCauses.size} potential cause(s)")
                if (primaryCause != null) {
                    append(": ${primaryCause!!.cause.displayName}")
                }
                append(" (${(confidence * 100).toInt()}% confidence)")
            }
}

/**
 * Solution recommendation for resolving an issue
 *
 * @property issue The network issue to resolve
 * @property solution Solution description
 * @property steps Implementation steps
 * @property difficulty Solution difficulty (EASY, MEDIUM, HARD)
 * @property impact Expected impact (LOW, MEDIUM, HIGH)
 * @property prerequisites Prerequisites before applying solution
 */
data class SolutionRecommendation(
    val issue: NetworkIssue,
    val solution: String,
    val steps: List<String>,
    val difficulty: Difficulty,
    val impact: Impact,
    val prerequisites: List<String> = emptyList(),
) {
    /**
     * Summary of the solution
     */
    val summary: String
        get() = "$solution (${difficulty.displayName} difficulty, ${impact.displayName} impact)"
}

/**
 * Solution difficulty level
 */
enum class Difficulty {
    EASY, // Can be done by end user
    MEDIUM, // Requires some technical knowledge
    HARD, // Requires professional/IT staff
    ;

    /**
     * Display name
     */
    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Solution impact level
 */
enum class Impact {
    LOW, // Slight improvement
    MEDIUM, // Moderate improvement
    HIGH, // Significant improvement
    ;

    /**
     * Display name
     */
    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Network context for diagnostics
 *
 * @property signalStrength Current RSSI (dBm)
 * @property channelUtilization Channel utilization (0-100%)
 * @property connectedClients Number of connected clients
 * @property band Operating band (2.4GHz, 5GHz, 6GHz)
 * @property channel Current channel
 * @property bandwidth Current speed (Mbps)
 * @property latency Current latency (ms)
 * @property packetLoss Packet loss percentage (0-100%)
 */
data class DiagnosticContext(
    val signalStrength: Int? = null,
    val channelUtilization: Int? = null,
    val connectedClients: Int? = null,
    val band: String? = null,
    val channel: Int? = null,
    val bandwidth: Double? = null,
    val latency: Int? = null,
    val packetLoss: Double? = null,
)
