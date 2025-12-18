package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.BandwidthTest
import io.lamco.netkit.diagnostics.model.BandwidthTestType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Interface for executing bandwidth (throughput) tests.
 *
 * BandwidthTester provides an abstraction for measuring network throughput across
 * different platforms. Implementations can use various methods:
 * - HTTP/HTTPS download/upload to test servers
 * - Custom UDP/TCP throughput testing
 * - Integration with speed test services (Ookla, Fast.com, etc.)
 *
 * ## Platform Implementations
 *
 * - **Android**: Uses HTTP client with chunked transfer
 * - **JVM**: Uses OkHttp or Java 11+ HttpClient
 * - **Integration**: Can integrate with Ookla Speedtest SDK, LibreSpeed, etc.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val tester = HttpBandwidthTester()
 * val result = tester.executeBandwidthTest(
 *     serverHost = "speedtest.example.com",
 *     testType = BandwidthTestType.DOWNLOAD_UPLOAD,
 *     testDuration = 10.seconds
 * )
 * println("Download: ${result.downloadMbps} Mbps")
 * println("Upload: ${result.uploadMbps} Mbps")
 * ```
 *
 * @see BandwidthTest
 */
interface BandwidthTester {
    /**
     * Execute a bandwidth test.
     *
     * @param serverHost The test server hostname or IP
     * @param serverPort The test server port (default: 80 for HTTP, 443 for HTTPS)
     * @param testType Type of test (download, upload, or both)
     * @param testDuration Duration for each test direction
     * @param useHttps Whether to use HTTPS (default: true)
     * @return BandwidthTest result with throughput measurements
     * @throws BandwidthTestException if test fails
     */
    suspend fun executeBandwidthTest(
        serverHost: String,
        serverPort: Int = 443,
        testType: BandwidthTestType = BandwidthTestType.DOWNLOAD_UPLOAD,
        testDuration: Duration = 10.seconds,
        useHttps: Boolean = true,
    ): BandwidthTest

    /**
     * Check if bandwidth testing is available.
     *
     * @return true if bandwidth tests can be executed
     */
    fun isBandwidthTestAvailable(): Boolean
}

/**
 * Builder for constructing BandwidthTest results programmatically.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val test = BandwidthTestBuilder(serverHost = "speedtest.example.com")
 *     .setDownload(mbps = 85.3, bytes = 106_625_000)
 *     .setUpload(mbps = 42.7, bytes = 53_375_000)
 *     .setDuration(10.seconds)
 *     .build()
 * ```
 */
class BandwidthTestBuilder(
    private val serverHost: String,
) {
    private var downloadMbps: Double? = null
    private var uploadMbps: Double? = null
    private var downloadBytes: Long = 0L
    private var uploadBytes: Long = 0L
    private var testDuration: Duration = 10.seconds
    private var serverLocation: String? = null
    private var testType: BandwidthTestType = BandwidthTestType.DOWNLOAD_UPLOAD
    private var failureReason: String? = null

    /**
     * Set download measurements.
     */
    fun setDownload(
        mbps: Double,
        bytes: Long = 0L,
    ): BandwidthTestBuilder {
        this.downloadMbps = mbps
        this.downloadBytes = bytes
        return this
    }

    /**
     * Set upload measurements.
     */
    fun setUpload(
        mbps: Double,
        bytes: Long = 0L,
    ): BandwidthTestBuilder {
        this.uploadMbps = mbps
        this.uploadBytes = bytes
        return this
    }

    /**
     * Set test duration.
     */
    fun setDuration(duration: Duration): BandwidthTestBuilder {
        this.testDuration = duration
        return this
    }

    /**
     * Set server location.
     */
    fun setLocation(location: String): BandwidthTestBuilder {
        this.serverLocation = location
        return this
    }

    /**
     * Set test type.
     */
    fun setTestType(type: BandwidthTestType): BandwidthTestBuilder {
        this.testType = type
        return this
    }

    /**
     * Set failure reason.
     */
    fun setFailure(reason: String): BandwidthTestBuilder {
        this.failureReason = reason
        return this
    }

    /**
     * Build the BandwidthTest result.
     */
    fun build(): BandwidthTest =
        BandwidthTest(
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            downloadBytes = downloadBytes,
            uploadBytes = uploadBytes,
            testDuration = testDuration,
            serverHost = serverHost,
            serverLocation = serverLocation,
            testType = testType,
            failureReason = failureReason,
        )
}

/**
 * Exception thrown when bandwidth test fails.
 */
class BandwidthTestException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Utility for calculating throughput from bytes transferred and duration.
 */
object ThroughputCalculator {
    /**
     * Calculate throughput in Mbps from bytes and duration.
     *
     * @param bytes Number of bytes transferred
     * @param duration Time taken to transfer
     * @return Throughput in megabits per second
     */
    fun calculateMbps(
        bytes: Long,
        duration: Duration,
    ): Double {
        if (duration.inWholeMilliseconds == 0L) return 0.0

        val bits = bytes * 8.0
        val seconds = duration.inWholeMilliseconds / 1000.0
        val mbps = (bits / seconds) / 1_000_000.0

        return mbps
    }

    /**
     * Calculate bytes needed for target throughput and duration.
     *
     * @param mbps Target throughput in Mbps
     * @param duration Test duration
     * @return Number of bytes needed
     */
    fun calculateBytesNeeded(
        mbps: Double,
        duration: Duration,
    ): Long {
        val seconds = duration.inWholeMilliseconds / 1000.0
        val bits = mbps * 1_000_000.0 * seconds
        return (bits / 8.0).toLong()
    }
}
