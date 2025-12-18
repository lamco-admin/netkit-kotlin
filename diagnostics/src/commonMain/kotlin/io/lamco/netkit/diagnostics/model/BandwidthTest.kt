package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the results of a network bandwidth (throughput) test.
 *
 * Bandwidth testing measures the data transfer rate between the device and a test server,
 * providing insight into actual network performance beyond link-layer capabilities.
 *
 * This model follows RFC 6349 (Framework for TCP Throughput Testing) principles and
 * common industry practices for bandwidth measurement.
 *
 * ## Test Types
 *
 * - **Download**: Measures data transfer from server to device
 * - **Upload**: Measures data transfer from device to server
 * - **Bidirectional**: Simultaneous download and upload (optional)
 *
 * ## Quality Assessment
 *
 * Bandwidth quality depends on:
 * - **Connection type**: WiFi generation (4/5/6/6E/7)
 * - **Link speed**: Theoretical maximum from RF metrics
 * - **Actual throughput**: Measured data transfer rate
 * - **Efficiency**: Actual vs theoretical percentage
 *
 * ## Usage Example
 *
 * ```kotlin
 * val bandwidthTest = BandwidthTest(
 *     downloadMbps = 85.3,
 *     uploadMbps = 42.7,
 *     testDuration = 10.seconds,
 *     serverHost = "speedtest.example.com",
 *     downloadBytes = 106_625_000,
 *     uploadBytes = 53_375_000
 * )
 *
 * println("Download: ${bandwidthTest.downloadMbps} Mbps")
 * println("Upload: ${bandwidthTest.uploadMbps} Mbps")
 * println("Quality: ${bandwidthTest.overallQuality}")
 * ```
 *
 * @property downloadMbps Download speed in megabits per second (null if not tested)
 * @property uploadMbps Upload speed in megabits per second (null if not tested)
 * @property downloadBytes Total bytes downloaded during test
 * @property uploadBytes Total bytes uploaded during test
 * @property testDuration Total duration of the bandwidth test
 * @property serverHost The test server hostname or IP
 * @property serverLocation Geographic location of server (if known)
 * @property testType Type of bandwidth test performed
 * @property timestamp When the test was performed (epoch millis)
 * @property failureReason Optional reason if the test failed
 *
 * @see BandwidthQuality
 * @see BandwidthTestType
 * @see RFC6349
 */
data class BandwidthTest(
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val downloadBytes: Long = 0L,
    val uploadBytes: Long = 0L,
    val testDuration: Duration,
    val serverHost: String,
    val serverLocation: String? = null,
    val testType: BandwidthTestType = BandwidthTestType.DOWNLOAD_UPLOAD,
    val timestamp: Long = System.currentTimeMillis(),
    val failureReason: String? = null,
) {
    init {
        require(serverHost.isNotBlank()) { "Server host cannot be blank" }
        require(!testDuration.isNegative() && testDuration > Duration.ZERO) {
            "Test duration must be positive, got $testDuration"
        }
        require(downloadBytes >= 0) { "Download bytes must be >= 0, got $downloadBytes" }
        require(uploadBytes >= 0) { "Upload bytes must be >= 0, got $uploadBytes" }

        if (downloadMbps != null) {
            require(downloadMbps >= 0.0) { "Download speed must be >= 0, got $downloadMbps" }
        }

        if (uploadMbps != null) {
            require(uploadMbps >= 0.0) { "Upload speed must be >= 0, got $uploadMbps" }
        }

        // Validate test type matches provided data (only for successful tests)
        if (failureReason == null) {
            when (testType) {
                BandwidthTestType.DOWNLOAD_ONLY -> {
                    require(downloadMbps != null) { "DOWNLOAD_ONLY requires downloadMbps" }
                }
                BandwidthTestType.UPLOAD_ONLY -> {
                    require(uploadMbps != null) { "UPLOAD_ONLY requires uploadMbps" }
                }
                BandwidthTestType.DOWNLOAD_UPLOAD -> {
                    require(downloadMbps != null || uploadMbps != null) {
                        "DOWNLOAD_UPLOAD requires at least one speed measurement"
                    }
                }
                BandwidthTestType.BIDIRECTIONAL -> {
                    require(downloadMbps != null && uploadMbps != null) {
                        "BIDIRECTIONAL requires both download and upload speeds"
                    }
                }
            }
        }
    }

    /**
     * Whether the bandwidth test completed successfully.
     */
    val isSuccessful: Boolean = (downloadMbps != null || uploadMbps != null) && failureReason == null

    /**
     * Whether the test failed.
     */
    val isFailed: Boolean = !isSuccessful

    /**
     * Download speed quality assessment.
     */
    val downloadQuality: BandwidthQuality
        get() = assessBandwidthQuality(downloadMbps)

    /**
     * Upload speed quality assessment.
     */
    val uploadQuality: BandwidthQuality
        get() = assessBandwidthQuality(uploadMbps)

    /**
     * Overall bandwidth quality (worst of download/upload).
     */
    val overallQuality: BandwidthQuality
        get() {
            if (isFailed) return BandwidthQuality.FAILED

            val downloadScore = downloadQuality.toScore()
            val uploadScore = uploadQuality.toScore()

            // Overall quality is the worst of the two
            val worstScore = minOf(downloadScore, uploadScore)

            return BandwidthQuality.fromScore(worstScore)
        }

    /**
     * Calculate efficiency percentage if theoretical maximum is known.
     *
     * @param theoreticalMaxMbps The theoretical maximum speed (e.g., from WiFi link rate)
     * @param direction Which direction to calculate (download or upload)
     * @return Efficiency percentage (0.0-100.0), or null if not applicable
     */
    fun calculateEfficiency(
        theoreticalMaxMbps: Double,
        direction: BandwidthDirection = BandwidthDirection.DOWNLOAD,
    ): Double? {
        val actualMbps =
            when (direction) {
                BandwidthDirection.DOWNLOAD -> downloadMbps
                BandwidthDirection.UPLOAD -> uploadMbps
            } ?: return null

        if (theoreticalMaxMbps <= 0.0) return null

        return (actualMbps / theoreticalMaxMbps) * 100.0
    }

    /**
     * Assess if bandwidth meets minimum requirements.
     *
     * @param minDownloadMbps Minimum required download speed
     * @param minUploadMbps Minimum required upload speed
     * @return true if requirements are met
     */
    fun meetsRequirements(
        minDownloadMbps: Double? = null,
        minUploadMbps: Double? = null,
    ): Boolean {
        if (isFailed) return false

        val downloadOk =
            if (minDownloadMbps != null && downloadMbps != null) {
                downloadMbps >= minDownloadMbps
            } else {
                true
            }

        val uploadOk =
            if (minUploadMbps != null && uploadMbps != null) {
                uploadMbps >= minUploadMbps
            } else {
                true
            }

        return downloadOk && uploadOk
    }

    /**
     * Calculate average speed across both directions.
     */
    val averageSpeedMbps: Double?
        get() {
            val speeds = listOfNotNull(downloadMbps, uploadMbps)
            return if (speeds.isEmpty()) null else speeds.average()
        }

    /**
     * Human-readable summary of the bandwidth test results.
     */
    fun summary(): String =
        buildString {
            appendLine("Bandwidth Test Results: $serverHost")
            serverLocation?.let { appendLine("  Location: $it") }

            if (downloadMbps != null) {
                appendLine("  Download: ${"%.2f".format(downloadMbps)} Mbps ($downloadQuality)")
            }

            if (uploadMbps != null) {
                appendLine("  Upload: ${"%.2f".format(uploadMbps)} Mbps ($uploadQuality)")
            }

            appendLine("  Test duration: $testDuration")
            appendLine("  Overall quality: $overallQuality")

            if (failureReason != null) {
                appendLine("  Failure: $failureReason")
            }
        }

    /**
     * Detailed report including data transferred.
     */
    fun detailedReport(): String =
        buildString {
            appendLine("Bandwidth Test Detailed Report")
            appendLine("================================")
            appendLine("Server: $serverHost")
            serverLocation?.let { appendLine("Location: $it") }
            appendLine("Test type: $testType")
            appendLine("Duration: $testDuration")
            appendLine()

            if (downloadMbps != null) {
                appendLine("Download:")
                appendLine("  Speed: ${"%.2f".format(downloadMbps)} Mbps")
                appendLine("  Data transferred: ${"%.2f".format(downloadBytes / 1_000_000.0)} MB")
                appendLine("  Quality: $downloadQuality")
            }

            if (uploadMbps != null) {
                appendLine("Upload:")
                appendLine("  Speed: ${"%.2f".format(uploadMbps)} Mbps")
                appendLine("  Data transferred: ${"%.2f".format(uploadBytes / 1_000_000.0)} MB")
                appendLine("  Quality: $uploadQuality")
            }

            appendLine()
            appendLine("Overall Quality: $overallQuality")
        }

    companion object {
        /**
         * Create a BandwidthTest representing a failed test.
         *
         * @param serverHost The server that was attempted
         * @param reason Why the test failed
         * @param testDuration How long the test ran before failing
         * @return Failed BandwidthTest instance
         */
        fun failed(
            serverHost: String,
            reason: String,
            testDuration: Duration = 1.seconds,
        ): BandwidthTest =
            BandwidthTest(
                downloadMbps = null,
                uploadMbps = null,
                testDuration = testDuration,
                serverHost = serverHost,
                failureReason = reason,
            )

        /**
         * Assess bandwidth quality based on speed.
         */
        private fun assessBandwidthQuality(speedMbps: Double?): BandwidthQuality =
            when {
                speedMbps == null -> BandwidthQuality.UNKNOWN
                speedMbps >= 100.0 -> BandwidthQuality.EXCELLENT // 100+ Mbps
                speedMbps >= 50.0 -> BandwidthQuality.GOOD // 50-99 Mbps
                speedMbps >= 25.0 -> BandwidthQuality.FAIR // 25-49 Mbps
                speedMbps >= 10.0 -> BandwidthQuality.POOR // 10-24 Mbps
                else -> BandwidthQuality.CRITICAL // <10 Mbps
            }
    }
}

/**
 * Type of bandwidth test performed.
 */
enum class BandwidthTestType {
    /** Download speed only */
    DOWNLOAD_ONLY,

    /** Upload speed only */
    UPLOAD_ONLY,

    /** Sequential download then upload */
    DOWNLOAD_UPLOAD,

    /** Simultaneous download and upload */
    BIDIRECTIONAL,

    ;

    /**
     * Human-readable description.
     */
    val description: String
        get() =
            when (this) {
                DOWNLOAD_ONLY -> "Download speed test only"
                UPLOAD_ONLY -> "Upload speed test only"
                DOWNLOAD_UPLOAD -> "Sequential download and upload tests"
                BIDIRECTIONAL -> "Simultaneous bidirectional test"
            }
}

/**
 * Direction of bandwidth measurement.
 */
enum class BandwidthDirection {
    /** Download (server to device) */
    DOWNLOAD,

    /** Upload (device to server) */
    UPLOAD,
}

/**
 * Quality classification for bandwidth/throughput.
 *
 * Based on measured speeds in Mbps.
 */
enum class BandwidthQuality {
    /** >=100 Mbps - Excellent for all uses including 4K streaming */
    EXCELLENT,

    /** 50-99 Mbps - Good for HD streaming, large downloads */
    GOOD,

    /** 25-49 Mbps - Fair for general use, HD streaming */
    FAIR,

    /** 10-24 Mbps - Poor but usable for basic streaming */
    POOR,

    /** <10 Mbps - Critical, limited functionality */
    CRITICAL,

    /** Test failed */
    FAILED,

    /** Speed not measured */
    UNKNOWN,

    ;

    /**
     * Convert quality to a 0-100 score.
     */
    fun toScore(): Int =
        when (this) {
            EXCELLENT -> 100
            GOOD -> 80
            FAIR -> 60
            POOR -> 40
            CRITICAL -> 20
            FAILED -> 0
            UNKNOWN -> 0
        }

    /**
     * Human-readable description of the bandwidth quality.
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent bandwidth - suitable for 4K streaming, large transfers"
                GOOD -> "Good bandwidth - suitable for HD streaming, video calls"
                FAIR -> "Fair bandwidth - suitable for SD streaming, general browsing"
                POOR -> "Poor bandwidth - limited to basic streaming and browsing"
                CRITICAL -> "Critical bandwidth - very limited functionality"
                FAILED -> "Bandwidth test failed"
                UNKNOWN -> "Bandwidth not measured"
            }

    companion object {
        /**
         * Convert a score (0-100) back to a BandwidthQuality.
         */
        fun fromScore(score: Int): BandwidthQuality =
            when {
                score >= 90 -> EXCELLENT
                score >= 70 -> GOOD
                score >= 50 -> FAIR
                score >= 30 -> POOR
                score > 0 -> CRITICAL
                else -> FAILED
            }
    }
}
