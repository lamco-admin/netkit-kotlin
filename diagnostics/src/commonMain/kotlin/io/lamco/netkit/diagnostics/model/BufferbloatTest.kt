package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// ============================================
// SUPPORTING TYPES (must be defined first)
// ============================================

/**
 * Bufferbloat severity levels based on latency increase.
 *
 * Thresholds based on bufferbloat.net testing and RFC 6349 recommendations.
 */
enum class BufferbloatSeverity {
    /** <25ms increase - No bufferbloat, excellent QoS */
    NONE,

    /** 25-50ms increase - Minor bufferbloat, slight impact on real-time apps */
    MINIMAL,

    /** 50-100ms increase - Moderate bufferbloat, noticeable lag in VoIP/gaming */
    MODERATE,

    /** 100-200ms increase - Severe bufferbloat, significant degradation */
    SEVERE,

    /** >200ms increase - Critical bufferbloat, network unusable for real-time apps */
    CRITICAL,

    ;

    /**
     * Convert severity to description.
     */
    val description: String
        get() =
            when (this) {
                NONE -> "No bufferbloat detected - network handles load excellently"
                MINIMAL -> "Minimal bufferbloat - slight impact on competitive gaming"
                MODERATE -> "Moderate bufferbloat - noticeable impact on VoIP and gaming"
                SEVERE -> "Severe bufferbloat - real-time applications significantly degraded"
                CRITICAL -> "Critical bufferbloat - network unusable for VoIP/gaming/video calls during load"
            }
}

/**
 * Direction of network load for bufferbloat testing.
 *
 * Upload typically causes worse bufferbloat than download due to
 * asymmetric bandwidth and smaller upload buffers filling faster.
 */
enum class LoadDirection {
    /** Download load only */
    DOWNLOAD,

    /** Upload load only (typically worse bufferbloat) */
    UPLOAD,

    /** Both download and upload simultaneously */
    BIDIRECTIONAL,
}

/**
 * Application types affected by bufferbloat.
 *
 * Ordered by sensitivity (most sensitive first).
 */
enum class ApplicationType {
    /** Voice calls - most sensitive (Zoom voice, Teams, phone calls) */
    VOIP,

    /** Video conferencing (Zoom, Teams, Meet) - very sensitive */
    VIDEO_CONFERENCE,

    /** Online gaming - very sensitive to latency spikes */
    ONLINE_GAMING,

    /** Remote desktop (VNC, RDP, SSH) - sensitive to latency */
    REMOTE_DESKTOP,

    /** Video streaming (YouTube, Netflix) - less sensitive, may cause buffering */
    STREAMING_VIDEO,

    /** Web browsing - slightly affected (page load delays) */
    WEB_BROWSING,

    /** File downloads - not affected (throughput-focused) */
    FILE_DOWNLOAD,
}

/**
 * Single latency measurement during bufferbloat test.
 *
 * Used to build time-series of latency under load for detailed analysis.
 *
 * @property timestamp Epoch milliseconds when measurement taken
 * @property latency Measured round-trip time
 * @property loadActive Whether network load was active at this measurement
 */
data class LatencyMeasurement(
    val timestamp: Long,
    val latency: Duration,
    val loadActive: Boolean,
) {
    init {
        require(!latency.isNegative()) { "Latency cannot be negative" }
    }

    /**
     * Time offset from start of test.
     */
    fun offsetFrom(testStart: Long): Duration = (timestamp - testStart).milliseconds
}

/**
 * Exception thrown when bufferbloat test fails.
 */
class BufferbloatTestException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

// ============================================
// MAIN BUFFERBLOAT TEST DATA CLASS
// ============================================

/**
 * Bufferbloat test result - measures latency under network load.
 *
 * Bufferbloat is excessive buffering in network equipment (routers, modems) that causes
 * high latency when the network is under load (uploading/downloading). This severely
 * impacts real-time applications like VoIP, video calls, and gaming while appearing
 * fine during idle periods.
 *
 * ## Problem Description
 *
 * Traditional network equipment uses large buffers to prevent packet loss. However,
 * excessively large buffers cause packets to queue for hundreds of milliseconds during
 * high traffic, creating latency spikes. This is called "bufferbloat."
 *
 * **Symptoms**:
 * - VoIP calls sound fine normally, choppy during downloads
 * - Gaming lag spikes when someone else downloads
 * - Video calls freeze during file uploads
 * - Normal ping times but terrible experience under load
 *
 * ## Testing Methodology
 *
 * Based on RFC 6349 Section 4 and bufferbloat.net recommendations:
 * 1. Measure baseline latency (idle network) - typically 10-50ms
 * 2. Start sustained bandwidth test (generate network load)
 * 3. Continuously measure latency during load
 * 4. Compare baseline vs. maximum loaded latency
 * 5. Calculate latency increase (the "bloat")
 *
 * ## Quality Thresholds
 *
 * Based on bufferbloat.net and RFC 6349:
 * - **NONE**: <25ms increase - No bufferbloat, excellent QoS
 * - **MINIMAL**: 25-50ms - Minor impact, acceptable for most use
 * - **MODERATE**: 50-100ms - Noticeable lag in VoIP/gaming
 * - **SEVERE**: 100-200ms - Significant degradation, unusable for real-time
 * - **CRITICAL**: >200ms - Network essentially unusable during load
 *
 * ## Affected Applications
 *
 * Bufferbloat primarily affects latency-sensitive applications:
 * - **VoIP**: Requires <150ms one-way delay (ITU-T G.114)
 * - **Video Calls**: <200ms for acceptable quality
 * - **Gaming**: <50ms for competitive gaming, <100ms casual
 * - **Remote Desktop**: <100ms for responsive feel
 *
 * ## Solutions
 *
 * - Enable SQM (Smart Queue Management) in router
 * - Use fq_codel or CAKE queue discipline
 * - Update router firmware
 * - Replace router with SQM-capable model
 * - Limit concurrent transfers
 *
 * ## References
 * - RFC 6349: Framework for TCP Throughput Testing
 * - Bufferbloat.net: Tests for Bufferbloat
 * - IETF ICCRG: Reducing Internet Bufferbloat
 * - Fast.com: Latency under load testing
 *
 * @property baselineLatency Average latency with idle network (unloaded ping)
 * @property loadedLatency Maximum latency observed during bandwidth test
 * @property latencyIncrease Absolute latency increase (loaded - baseline)
 * @property latencyIncreasePercent Percentage increase over baseline
 * @property bufferbloatScore 0-100 score (100 = no bufferbloat, 0 = severe)
 * @property severity Bufferbloat severity classification
 * @property affectedApplications Application types impacted by this level
 * @property peakLatency Highest latency spike observed
 * @property averageLoadedLatency Average latency during load period
 * @property latencyHistory Time-series latency measurements during test
 * @property testDuration How long the load was sustained
 * @property loadDirection Direction of load (download/upload/both)
 * @property timestamp When the test was performed
 *
 * @since 1.1.0
 */
data class BufferbloatTest(
    val baselineLatency: Duration,
    val loadedLatency: Duration,
    val latencyIncrease: Duration,
    val latencyIncreasePercent: Double,
    val bufferbloatScore: Double,
    val severity: BufferbloatSeverity,
    val affectedApplications: List<ApplicationType>,
    val peakLatency: Duration,
    val averageLoadedLatency: Duration,
    val latencyHistory: List<LatencyMeasurement> = emptyList(),
    val testDuration: Duration,
    val loadDirection: LoadDirection,
    val timestamp: Long = System.currentTimeMillis(),
) {
    init {
        require(!baselineLatency.isNegative()) {
            "Baseline latency cannot be negative, got: $baselineLatency"
        }
        require(!loadedLatency.isNegative()) {
            "Loaded latency cannot be negative, got: $loadedLatency"
        }
        require(loadedLatency >= baselineLatency) {
            "Loaded latency ($loadedLatency) must be >= baseline ($baselineLatency)"
        }
        require(bufferbloatScore in 0.0..100.0) {
            "Bufferbloat score must be 0-100, got: $bufferbloatScore"
        }
        require(latencyIncreasePercent >= 0.0) {
            "Latency increase % must be >= 0, got: $latencyIncreasePercent"
        }
        require(!testDuration.isNegative()) {
            "Test duration cannot be negative"
        }
    }

    /**
     * Whether bufferbloat is present and actionable.
     */
    val hasBufferbloat: Boolean = severity != BufferbloatSeverity.NONE

    /**
     * Whether bufferbloat is severe enough to significantly impact user experience.
     */
    val isSevere: Boolean =
        severity in
            listOf(
                BufferbloatSeverity.SEVERE,
                BufferbloatSeverity.CRITICAL,
            )

    /**
     * Whether the network is usable for real-time applications during load.
     */
    val usableForRealTime: Boolean =
        severity in
            listOf(
                BufferbloatSeverity.NONE,
                BufferbloatSeverity.MINIMAL,
            )

    /**
     * Recommended actions to reduce bufferbloat.
     *
     * Returns prioritized list of actions user can take to improve
     * latency-under-load performance.
     */
    fun recommendations(): List<String> =
        buildList {
            when (severity) {
                BufferbloatSeverity.NONE -> {
                    add("✓ Excellent: Bufferbloat is under control")
                    add("Your network handles load well - no action needed")
                }
                BufferbloatSeverity.MINIMAL -> {
                    add("✓ Good: Minor bufferbloat detected")
                    add("Consider enabling SQM if available for further improvement")
                }
                BufferbloatSeverity.MODERATE -> {
                    add("⚠ Moderate bufferbloat detected (+${latencyIncrease.inWholeMilliseconds}ms under load)")
                    add("1. Enable SQM (Smart Queue Management) in router settings")
                    add("2. Update router firmware to latest version")
                    add("3. If router doesn't support SQM, consider upgrading")
                    add("4. Affected applications: ${affectedApplications.joinToString(", ")}")
                }
                BufferbloatSeverity.SEVERE -> {
                    add("⚠ SEVERE bufferbloat detected (+${latencyIncrease.inWholeMilliseconds}ms under load)")
                    add("URGENT ACTIONS REQUIRED:")
                    add("1. Enable SQM (Smart Queue Management) immediately")
                    add("2. Enable fq_codel or CAKE queue discipline if available")
                    add("3. Update router firmware")
                    add("4. If no SQM support, replace router with SQM-capable model")
                    add("5. Limit concurrent downloads/uploads until fixed")
                    add("Impact: ${affectedApplications.size} application types severely affected")
                }
                BufferbloatSeverity.CRITICAL -> {
                    add("🔴 CRITICAL bufferbloat (+${latencyIncrease.inWholeMilliseconds}ms under load)")
                    add("IMMEDIATE ACTION REQUIRED:")
                    add("1. Router has severe bufferbloat - real-time apps unusable during load")
                    add("2. ENABLE SQM/fq_codel/CAKE queue management NOW")
                    add("3. If router lacks SQM: REPLACE router immediately")
                    add("4. Stop simultaneous downloads/uploads")
                    add("5. Consider QoS-capable router (OpenWrt, pfSense, enterprise)")
                    add(
                        "This level of bufferbloat makes VoIP/gaming/video calls impossible during any network activity",
                    )
                }
            }
        }

    /**
     * Human-readable summary of bufferbloat test.
     */
    fun summary(): String =
        buildString {
            appendLine("Bufferbloat Test Results")
            appendLine("=".repeat(50))
            appendLine("Baseline Latency (idle): $baselineLatency")
            appendLine("Loaded Latency (under load): $loadedLatency")
            appendLine("Latency Increase: $latencyIncrease (+${"%.1f".format(latencyIncreasePercent)}%)")
            appendLine("Bufferbloat Score: ${"%.1f".format(bufferbloatScore)}/100")
            appendLine("Severity: $severity")
            appendLine("Load Direction: $loadDirection")
            appendLine()

            if (affectedApplications.isNotEmpty()) {
                appendLine("Affected Applications:")
                affectedApplications.forEach { app ->
                    appendLine("  - $app: ${getImpactDescription(app, severity)}")
                }
            }
        }

    /**
     * Get impact description for specific application type.
     */
    private fun getImpactDescription(
        app: ApplicationType,
        severity: BufferbloatSeverity,
    ): String =
        when (severity) {
            BufferbloatSeverity.NONE, BufferbloatSeverity.MINIMAL -> "No impact"
            BufferbloatSeverity.MODERATE -> "Noticeable lag"
            BufferbloatSeverity.SEVERE -> "Severely degraded"
            BufferbloatSeverity.CRITICAL -> "Unusable"
        }

    companion object {
        /**
         * Calculate bufferbloat score from latency increase.
         *
         * Score calculation:
         * - <25ms: 100-90 (excellent)
         * - 25-50ms: 85-60 (good to fair)
         * - 50-100ms: 60-35 (fair to poor)
         * - 100-200ms: 35-10 (poor to critical)
         * - >200ms: 10-0 (critical)
         *
         * @param latencyIncrease Latency increase under load
         * @return Bufferbloat score (0-100)
         */
        fun calculateScore(latencyIncrease: Duration): Double {
            val increaseMs = latencyIncrease.inWholeMilliseconds.toDouble()
            return when {
                increaseMs < 25.0 -> 100.0 - (increaseMs / 25.0 * 10.0) // 100-90
                increaseMs < 50.0 -> 85.0 - ((increaseMs - 25.0) / 25.0 * 25.0) // 85-60
                increaseMs < 100.0 -> 60.0 - ((increaseMs - 50.0) / 50.0 * 25.0) // 60-35
                increaseMs < 200.0 -> 35.0 - ((increaseMs - 100.0) / 100.0 * 25.0) // 35-10
                else -> maxOf(0.0, 10.0 - ((increaseMs - 200.0) / 100.0 * 10.0)) // 10-0
            }.coerceIn(0.0, 100.0)
        }

        /**
         * Determine affected applications for given severity.
         */
        fun determineAffectedApplications(severity: BufferbloatSeverity): List<ApplicationType> =
            when (severity) {
                BufferbloatSeverity.NONE -> emptyList()
                BufferbloatSeverity.MINIMAL ->
                    listOf(
                        ApplicationType.ONLINE_GAMING, // Competitive gaming affected
                    )
                BufferbloatSeverity.MODERATE ->
                    listOf(
                        ApplicationType.VOIP,
                        ApplicationType.ONLINE_GAMING,
                        ApplicationType.VIDEO_CONFERENCE,
                    )
                BufferbloatSeverity.SEVERE, BufferbloatSeverity.CRITICAL ->
                    listOf(
                        ApplicationType.VOIP,
                        ApplicationType.VIDEO_CONFERENCE,
                        ApplicationType.ONLINE_GAMING,
                        ApplicationType.REMOTE_DESKTOP,
                        ApplicationType.STREAMING_VIDEO, // Buffering issues
                    )
            }
    }
}
