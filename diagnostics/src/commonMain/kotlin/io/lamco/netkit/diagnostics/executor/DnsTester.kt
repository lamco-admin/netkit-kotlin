package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.DnsRecordType
import io.lamco.netkit.diagnostics.model.DnsTest
import io.lamco.netkit.diagnostics.model.DnsTestSuite
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Interface for executing DNS resolution tests.
 *
 * DnsTester provides an abstraction for testing DNS resolution performance across
 * different platforms. Implementations can use:
 * - Native DNS APIs (Android DnsResolver, Java InetAddress)
 * - DNS libraries (dnsjava, MiniDNS)
 * - System commands (nslookup, dig, host)
 *
 * ## Platform Implementations
 *
 * - **Android**: Uses DnsResolver API (API 29+) or InetAddress
 * - **JVM**: Uses InetAddress or dnsjava library
 * - **Command-line**: Uses dig, nslookup, or host commands
 *
 * ## Usage Example
 *
 * ```kotlin
 * val tester = AndroidDnsTester()
 * val result = tester.executeDnsTest(
 *     hostname = "example.com",
 *     recordType = DnsRecordType.A,
 *     dnsServer = "8.8.8.8"
 * )
 * println("Resolved to: ${result.resolvedAddresses}")
 * println("Resolution time: ${result.resolutionTime}")
 * ```
 *
 * @see DnsTest
 * @see DnsTestSuite
 */
interface DnsTester {
    /**
     * Execute a DNS resolution test.
     *
     * @param hostname The hostname to resolve
     * @param recordType The DNS record type to query
     * @param dnsServer The DNS server to use (null = system default)
     * @param timeout Maximum time to wait for resolution
     * @return DnsTest result with resolution time and addresses
     * @throws DnsTestException if test fails
     */
    suspend fun executeDnsTest(
        hostname: String,
        recordType: DnsRecordType = DnsRecordType.A,
        dnsServer: String? = null,
        timeout: Duration = 5.seconds,
    ): DnsTest

    /**
     * Execute multiple DNS tests to compare servers.
     *
     * @param hostname The hostname to resolve
     * @param recordType The DNS record type to query
     * @param dnsServers List of DNS servers to test
     * @param timeout Maximum time per resolution
     * @return DnsTestSuite with all test results
     */
    suspend fun executeDnsComparison(
        hostname: String,
        recordType: DnsRecordType = DnsRecordType.A,
        dnsServers: List<String>,
        timeout: Duration = 5.seconds,
    ): DnsTestSuite {
        val tests =
            dnsServers.map { server ->
                try {
                    executeDnsTest(hostname, recordType, server, timeout)
                } catch (e: DnsTestException) {
                    DnsTest.failed(
                        hostname = hostname,
                        recordType = recordType,
                        dnsServer = server,
                        reason = e.message ?: "DNS test failed",
                    )
                }
            }
        return DnsTestSuite(tests)
    }

    /**
     * Check if DNS testing is available.
     *
     * @return true if DNS tests can be executed
     */
    fun isDnsTestAvailable(): Boolean
}

/**
 * Builder for constructing DnsTest results programmatically.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val test = DnsTestBuilder(
 *     hostname = "example.com",
 *     dnsServer = "8.8.8.8"
 * )
 *     .setRecordType(DnsRecordType.A)
 *     .addResolvedAddress("93.184.216.34")
 *     .setResolutionTime(15.milliseconds)
 *     .setTtl(3600)
 *     .build()
 * ```
 */
class DnsTestBuilder(
    private val hostname: String,
    private val dnsServer: String,
) {
    private var recordType: DnsRecordType = DnsRecordType.A
    private val resolvedAddresses = mutableListOf<String>()
    private var resolutionTime: Duration = Duration.ZERO
    private var ttl: Int? = null
    private var failureReason: String? = null

    /**
     * Set the DNS record type.
     */
    fun setRecordType(type: DnsRecordType): DnsTestBuilder {
        this.recordType = type
        return this
    }

    /**
     * Add a resolved address/record.
     */
    fun addResolvedAddress(address: String): DnsTestBuilder {
        this.resolvedAddresses.add(address)
        return this
    }

    /**
     * Set multiple resolved addresses.
     */
    fun setResolvedAddresses(addresses: List<String>): DnsTestBuilder {
        this.resolvedAddresses.clear()
        this.resolvedAddresses.addAll(addresses)
        return this
    }

    /**
     * Set resolution time.
     */
    fun setResolutionTime(time: Duration): DnsTestBuilder {
        this.resolutionTime = time
        return this
    }

    /**
     * Set TTL (time-to-live).
     */
    fun setTtl(ttl: Int): DnsTestBuilder {
        this.ttl = ttl
        return this
    }

    /**
     * Set failure reason.
     */
    fun setFailure(reason: String): DnsTestBuilder {
        this.failureReason = reason
        return this
    }

    /**
     * Build the DnsTest result.
     */
    fun build(): DnsTest =
        DnsTest(
            hostname = hostname,
            recordType = recordType,
            resolvedAddresses = resolvedAddresses,
            resolutionTime = resolutionTime,
            dnsServer = dnsServer,
            ttl = ttl,
            failureReason = failureReason,
        )
}

/**
 * Exception thrown when DNS test fails.
 */
class DnsTestException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Common DNS servers for testing and comparison.
 */
object CommonDnsServers {
    /** Google Public DNS (Primary) */
    const val GOOGLE_PRIMARY = "8.8.8.8"

    /** Google Public DNS (Secondary) */
    const val GOOGLE_SECONDARY = "8.8.4.4"

    /** Cloudflare DNS (Primary) */
    const val CLOUDFLARE_PRIMARY = "1.1.1.1"

    /** Cloudflare DNS (Secondary) */
    const val CLOUDFLARE_SECONDARY = "1.0.0.1"

    /** Quad9 DNS (Primary) */
    const val QUAD9_PRIMARY = "9.9.9.9"

    /** Quad9 DNS (Secondary) */
    const val QUAD9_SECONDARY = "149.112.112.112"

    /** OpenDNS (Primary) */
    const val OPENDNS_PRIMARY = "208.67.222.222"

    /** OpenDNS (Secondary) */
    const val OPENDNS_SECONDARY = "208.67.220.220"

    /**
     * List of popular public DNS servers for comparison testing.
     */
    val popularServers =
        listOf(
            GOOGLE_PRIMARY,
            CLOUDFLARE_PRIMARY,
            QUAD9_PRIMARY,
            OPENDNS_PRIMARY,
        )

    /**
     * Get DNS server display name.
     */
    fun getServerName(serverIp: String): String =
        when (serverIp) {
            GOOGLE_PRIMARY, GOOGLE_SECONDARY -> "Google DNS"
            CLOUDFLARE_PRIMARY, CLOUDFLARE_SECONDARY -> "Cloudflare DNS"
            QUAD9_PRIMARY, QUAD9_SECONDARY -> "Quad9 DNS"
            OPENDNS_PRIMARY, OPENDNS_SECONDARY -> "OpenDNS"
            else -> "Custom DNS"
        }
}
