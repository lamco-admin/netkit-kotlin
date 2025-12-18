package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents the result of a DNS (Domain Name System) resolution test.
 *
 * DNS testing evaluates the performance and reliability of domain name resolution,
 * which is critical for all internet connectivity. Slow or failing DNS resolution
 * can make a network appear broken even when connectivity is otherwise fine.
 *
 * This model follows RFC 1035 (Domain Names - Implementation and Specification)
 * and modern DNS best practices.
 *
 * ## DNS Query Types
 *
 * Common DNS record types:
 * - **A**: IPv4 address (most common)
 * - **AAAA**: IPv6 address
 * - **CNAME**: Canonical name (alias)
 * - **MX**: Mail exchange server
 * - **TXT**: Text records
 * - **NS**: Name server
 * - **PTR**: Reverse DNS lookup
 *
 * ## Performance Benchmarks
 *
 * - **Excellent**: <20ms resolution time
 * - **Good**: 20-50ms resolution time
 * - **Fair**: 50-100ms resolution time
 * - **Poor**: 100-200ms resolution time
 * - **Critical**: >200ms resolution time
 *
 * ## Usage Example
 *
 * ```kotlin
 * val dnsTest = DnsTest(
 *     hostname = "example.com",
 *     recordType = DnsRecordType.A,
 *     resolvedAddresses = listOf("93.184.216.34"),
 *     resolutionTime = 15.milliseconds,
 *     dnsServer = "8.8.8.8",
 *     ttl = 3600
 * )
 *
 * println("Resolved: ${dnsTest.resolvedAddresses}")
 * println("Resolution time: ${dnsTest.resolutionTime}")
 * println("Quality: ${dnsTest.resolutionQuality}")
 * ```
 *
 * @property hostname The hostname that was queried
 * @property recordType The DNS record type queried
 * @property resolvedAddresses List of resolved addresses/records (empty if failed)
 * @property resolutionTime Time taken to complete DNS resolution
 * @property dnsServer The DNS server used for resolution (e.g., "8.8.8.8")
 * @property ttl Time-to-live in seconds (how long result can be cached)
 * @property timestamp When the test was performed (epoch millis)
 * @property failureReason Optional reason if resolution failed
 *
 * @see DnsRecordType
 * @see DnsResolutionQuality
 * @see RFC1035
 */
data class DnsTest(
    val hostname: String,
    val recordType: DnsRecordType,
    val resolvedAddresses: List<String>,
    val resolutionTime: Duration,
    val dnsServer: String,
    val ttl: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val failureReason: String? = null,
) {
    init {
        require(hostname.isNotBlank()) { "Hostname cannot be blank" }
        require(dnsServer.isNotBlank()) { "DNS server cannot be blank" }
        require(!resolutionTime.isNegative()) { "Resolution time cannot be negative" }

        if (ttl != null) {
            require(ttl >= 0) { "TTL must be >= 0, got $ttl" }
        }
    }

    /**
     * Whether the DNS resolution was successful.
     */
    val isSuccessful: Boolean = resolvedAddresses.isNotEmpty() && failureReason == null

    /**
     * Whether the DNS resolution failed.
     */
    val isFailed: Boolean = !isSuccessful

    /**
     * Number of addresses/records returned.
     */
    val resultCount: Int = resolvedAddresses.size

    /**
     * Primary resolved address (first result).
     */
    val primaryAddress: String? = resolvedAddresses.firstOrNull()

    /**
     * Quality assessment for DNS resolution speed.
     */
    val resolutionQuality: DnsResolutionQuality
        get() =
            when {
                isFailed -> DnsResolutionQuality.FAILED
                resolutionTime < 20.milliseconds -> DnsResolutionQuality.EXCELLENT
                resolutionTime < 50.milliseconds -> DnsResolutionQuality.GOOD
                resolutionTime < 100.milliseconds -> DnsResolutionQuality.FAIR
                resolutionTime < 200.milliseconds -> DnsResolutionQuality.POOR
                else -> DnsResolutionQuality.CRITICAL
            }

    /**
     * Check if resolution time is acceptable.
     *
     * @param maxResolutionTime Maximum acceptable resolution time (default: 100ms)
     * @return true if resolution time is within acceptable range
     */
    fun isAcceptableSpeed(maxResolutionTime: Duration = 100.milliseconds): Boolean = isSuccessful && resolutionTime <= maxResolutionTime

    /**
     * Check if TTL is reasonable (not too short, not too long).
     *
     * @param minTtl Minimum recommended TTL in seconds (default: 60)
     * @param maxTtl Maximum recommended TTL in seconds (default: 86400 = 24 hours)
     * @return true if TTL is in reasonable range
     */
    fun hasReasonableTtl(
        minTtl: Int = 60,
        maxTtl: Int = 86400,
    ): Boolean = ttl != null && ttl in minTtl..maxTtl

    /**
     * Human-readable summary of the DNS test results.
     */
    fun summary(): String =
        buildString {
            appendLine("DNS Resolution Test: $hostname")
            appendLine("  Query type: $recordType")
            appendLine("  DNS server: $dnsServer")

            if (isSuccessful) {
                appendLine("  Resolved to: ${resolvedAddresses.joinToString(", ")}")
                appendLine("  Resolution time: $resolutionTime ($resolutionQuality)")
                ttl?.let { appendLine("  TTL: ${it}s") }
            } else {
                appendLine("  Resolution failed: ${failureReason ?: "Unknown reason"}")
            }
        }

    companion object {
        /**
         * Create a DnsTest representing a failed resolution.
         *
         * @param hostname The hostname that failed to resolve
         * @param recordType The record type that was queried
         * @param dnsServer The DNS server used
         * @param reason Why resolution failed
         * @param resolutionTime How long the attempt took before failing
         * @return Failed DnsTest instance
         */
        fun failed(
            hostname: String,
            recordType: DnsRecordType = DnsRecordType.A,
            dnsServer: String,
            reason: String,
            resolutionTime: Duration = Duration.ZERO,
        ): DnsTest =
            DnsTest(
                hostname = hostname,
                recordType = recordType,
                resolvedAddresses = emptyList(),
                resolutionTime = resolutionTime,
                dnsServer = dnsServer,
                failureReason = reason,
            )
    }
}

/**
 * DNS record types for queries.
 *
 * Based on RFC 1035 and subsequent DNS RFCs.
 */
enum class DnsRecordType {
    /** IPv4 address (most common) */
    A,

    /** IPv6 address */
    AAAA,

    /** Canonical name (alias) */
    CNAME,

    /** Mail exchange server */
    MX,

    /** Text record */
    TXT,

    /** Name server */
    NS,

    /** Pointer (reverse DNS) */
    PTR,

    /** Service locator */
    SRV,

    /** Start of authority */
    SOA,

    /** Any record type */
    ANY,

    ;

    /**
     * Human-readable description of the record type.
     */
    val description: String
        get() =
            when (this) {
                A -> "IPv4 address"
                AAAA -> "IPv6 address"
                CNAME -> "Canonical name (alias)"
                MX -> "Mail exchange server"
                TXT -> "Text record"
                NS -> "Name server"
                PTR -> "Pointer (reverse DNS)"
                SRV -> "Service locator"
                SOA -> "Start of authority"
                ANY -> "Any record type"
            }
}

/**
 * Quality classification for DNS resolution performance.
 *
 * Based on resolution time benchmarks.
 */
enum class DnsResolutionQuality {
    /** <20ms - Excellent DNS performance */
    EXCELLENT,

    /** 20-49ms - Good DNS performance */
    GOOD,

    /** 50-99ms - Fair DNS performance */
    FAIR,

    /** 100-199ms - Poor DNS performance */
    POOR,

    /** >=200ms - Critical DNS performance issues */
    CRITICAL,

    /** DNS resolution failed */
    FAILED,

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
        }

    /**
     * Human-readable description of the DNS quality.
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent DNS performance - very fast resolution"
                GOOD -> "Good DNS performance - fast resolution"
                FAIR -> "Fair DNS performance - acceptable resolution time"
                POOR -> "Poor DNS performance - slow resolution"
                CRITICAL -> "Critical DNS issues - very slow resolution"
                FAILED -> "DNS resolution failed"
            }
}

/**
 * Aggregate results from multiple DNS tests.
 *
 * Useful for testing multiple DNS servers or multiple hostnames.
 *
 * @property tests List of individual DNS tests performed
 * @property timestamp When the aggregate test was performed
 */
data class DnsTestSuite(
    val tests: List<DnsTest>,
    val timestamp: Long = System.currentTimeMillis(),
) {
    init {
        require(tests.isNotEmpty()) { "Test suite must contain at least one test" }
    }

    /**
     * Number of successful DNS resolutions.
     */
    val successfulTests: Int = tests.count { it.isSuccessful }

    /**
     * Number of failed DNS resolutions.
     */
    val failedTests: Int = tests.count { it.isFailed }

    /**
     * Success rate as a percentage (0.0-100.0).
     */
    val successRate: Double = (successfulTests.toDouble() / tests.size) * 100.0

    /**
     * Average resolution time across all successful tests.
     */
    val averageResolutionTime: Duration? =
        if (successfulTests == 0) {
            null
        } else {
            val totalMs = tests.filter { it.isSuccessful }.sumOf { it.resolutionTime.inWholeMilliseconds }
            (totalMs / successfulTests).milliseconds
        }

    /**
     * Fastest DNS resolution time.
     */
    val fastestResolutionTime: Duration? =
        tests
            .filter { it.isSuccessful }
            .minOfOrNull { it.resolutionTime }

    /**
     * Slowest DNS resolution time.
     */
    val slowestResolutionTime: Duration? =
        tests
            .filter { it.isSuccessful }
            .maxOfOrNull { it.resolutionTime }

    /**
     * Overall DNS quality based on success rate and average resolution time.
     */
    val overallQuality: DnsResolutionQuality
        get() {
            if (successRate < 50.0) return DnsResolutionQuality.FAILED

            val avgTime = averageResolutionTime ?: return DnsResolutionQuality.FAILED

            return when {
                avgTime < 20.milliseconds && successRate >= 95.0 -> DnsResolutionQuality.EXCELLENT
                avgTime < 50.milliseconds && successRate >= 90.0 -> DnsResolutionQuality.GOOD
                avgTime < 100.milliseconds && successRate >= 80.0 -> DnsResolutionQuality.FAIR
                avgTime < 200.milliseconds && successRate >= 70.0 -> DnsResolutionQuality.POOR
                else -> DnsResolutionQuality.CRITICAL
            }
        }

    /**
     * Group tests by DNS server.
     */
    fun testsByServer(): Map<String, List<DnsTest>> = tests.groupBy { it.dnsServer }

    /**
     * Group tests by hostname.
     */
    fun testsByHostname(): Map<String, List<DnsTest>> = tests.groupBy { it.hostname }

    /**
     * Human-readable summary of the test suite.
     */
    fun summary(): String =
        buildString {
            appendLine("DNS Test Suite Results")
            appendLine("  Total tests: ${tests.size}")
            appendLine("  Successful: $successfulTests (${"%.1f".format(successRate)}%)")
            appendLine("  Failed: $failedTests")

            if (averageResolutionTime != null) {
                appendLine("  Average resolution time: $averageResolutionTime")
                appendLine("  Fastest: $fastestResolutionTime")
                appendLine("  Slowest: $slowestResolutionTime")
            }

            appendLine("  Overall quality: $overallQuality")
        }
}
