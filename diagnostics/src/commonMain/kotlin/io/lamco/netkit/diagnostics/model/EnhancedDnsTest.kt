package io.lamco.netkit.diagnostics.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Enhanced DNS test with multiple query types and advanced analysis.
 *
 * Extends basic DNS testing with:
 * - Multiple query types (A, AAAA, CNAME, MX, TXT, etc.)
 * - DNSSEC validation support detection
 * - Cache hit/miss analysis
 * - DNS hijacking detection (captive portal, malicious DNS)
 * - IPv6 support validation
 *
 * ## Query Types Tested
 *
 * - **A**: IPv4 address resolution (standard)
 * - **AAAA**: IPv6 address resolution (for IPv6 support validation)
 * - **CNAME**: Canonical name (for CDN analysis)
 * - **MX**: Mail exchange (for email infrastructure)
 * - **TXT**: Text records (for SPF, DKIM, domain verification)
 * - **NS**: Name servers (for DNS infrastructure)
 * - **SOA**: Start of Authority (for zone information)
 *
 * ## DNSSEC Support
 *
 * Tests whether DNS server supports DNSSEC validation by querying
 * a known DNSSEC-signed domain and checking for authenticated data (AD) flag.
 *
 * ## Cache Detection
 *
 * Measures resolution time for:
 * 1. First query (cache miss)
 * 2. Second query (cache hit)
 * Compare times to determine cache effectiveness.
 *
 * ## Hijacking Detection
 *
 * Tests for DNS hijacking by:
 * 1. Querying known non-existent domain (should return NXDOMAIN)
 * 2. Querying known good domain (should return correct IP)
 * 3. Detecting captive portal DNS redirection
 *
 * @property domain Domain name tested
 * @property server DNS server tested
 * @property queryResults Results for each query type
 * @property totalTime Total test duration
 * @property dnssecSupported Whether DNSSEC validation is supported
 * @property cacheStatus Cache hit/miss status
 * @property hijackingDetected Whether DNS hijacking detected
 * @property captivePortalDetected Whether captive portal present
 * @property ipv6Supported Whether IPv6 resolution works
 *
 * @since 1.1.0
 */
data class EnhancedDnsTest(
    val domain: String,
    val server: String,
    val queryResults: Map<DnsQueryType, DnsQueryResult>,
    val totalTime: Duration,
    val dnssecSupported: Boolean,
    val cacheStatus: CacheStatus,
    val hijackingDetected: Boolean,
    val captivePortalDetected: Boolean,
    val ipv6Supported: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
) {
    init {
        require(domain.isNotBlank()) { "Domain cannot be blank" }
        require(server.isNotBlank()) { "Server cannot be blank" }
        require(!totalTime.isNegative()) { "Total time cannot be negative" }
    }

    /**
     * Number of successful queries.
     */
    val successfulQueries: Int = queryResults.values.count { it.success }

    /**
     * Number of failed queries.
     */
    val failedQueries: Int = queryResults.values.count { !it.success }

    /**
     * Whether any queries succeeded.
     */
    val isSuccessful: Boolean = successfulQueries > 0

    /**
     * Average resolution time across successful queries.
     */
    val averageResolutionTime: Duration? =
        if (successfulQueries > 0) {
            val totalMs =
                queryResults.values
                    .filter { it.success }
                    .sumOf { it.resolutionTime.inWholeMilliseconds }
            (totalMs / successfulQueries).milliseconds
        } else {
            null
        }

    /**
     * Whether DNS is functioning properly.
     */
    val isHealthy: Boolean = isSuccessful && !hijackingDetected && !captivePortalDetected

    /**
     * Recommendations based on DNS test results.
     */
    fun recommendations(): List<String> =
        buildList {
            if (hijackingDetected) {
                add("⚠ DNS hijacking detected!")
                add("Your DNS queries may be intercepted or redirected")
                add("Recommended: Use secure DNS (DNS-over-HTTPS, DNS-over-TLS)")
                add("Consider: 1.1.1.1 (Cloudflare), 8.8.8.8 (Google), 9.9.9.9 (Quad9)")
            }

            if (captivePortalDetected) {
                add("ℹ Captive portal detected (WiFi login required)")
                add("You may need to authenticate before accessing internet")
            }

            if (!dnssecSupported) {
                add("ℹ DNSSEC not supported by this DNS server")
                add("For enhanced security, consider DNSSEC-capable server")
                add("Recommended: 1.1.1.1 (Cloudflare) with DNSSEC")
            }

            if (!ipv6Supported && queryResults[DnsQueryType.AAAA]?.success == false) {
                add("ℹ IPv6 not supported")
                add("DNS server cannot resolve IPv6 addresses (AAAA records)")
                add("Consider IPv6-capable DNS if your network supports IPv6")
            }

            if (cacheStatus == CacheStatus.INEFFECTIVE) {
                add("⚠ DNS caching not working effectively")
                add("Repeated queries are slow (cache miss)")
                add("May indicate DNS server issues or short TTLs")
            }

            if (failedQueries > successfulQueries) {
                add("⚠ Most DNS queries failed")
                add("DNS server may be unreliable or misconfigured")
                add("Consider using alternative DNS servers")
            }
        }
}

/**
 * DNS query types for comprehensive testing.
 */
enum class DnsQueryType {
    /** IPv4 address (A record) */
    A,

    /** IPv6 address (AAAA record) */
    AAAA,

    /** Canonical name (CNAME record) */
    CNAME,

    /** Mail exchange (MX record) */
    MX,

    /** Text record (TXT record) - SPF, DKIM, verification */
    TXT,

    /** Name server (NS record) */
    NS,

    /** Start of Authority (SOA record) */
    SOA,

    ;

    val description: String
        get() =
            when (this) {
                A -> "IPv4 Address"
                AAAA -> "IPv6 Address"
                CNAME -> "Canonical Name"
                MX -> "Mail Exchange"
                TXT -> "Text Record"
                NS -> "Name Server"
                SOA -> "Start of Authority"
            }
}

/**
 * Result for a single DNS query.
 *
 * @property type Query type
 * @property resolutionTime Time to resolve
 * @property recordCount Number of records returned
 * @property answers List of answer values
 * @property success Whether query succeeded
 * @property errorMessage Error message if failed
 */
data class DnsQueryResult(
    val type: DnsQueryType,
    val resolutionTime: Duration,
    val recordCount: Int,
    val answers: List<String>,
    val success: Boolean,
    val errorMessage: String? = null,
) {
    init {
        require(!resolutionTime.isNegative()) { "Resolution time cannot be negative" }
        require(recordCount >= 0) { "Record count must be >= 0" }
        if (success) {
            require(recordCount > 0) { "Successful query must have records" }
            require(answers.isNotEmpty()) { "Successful query must have answers" }
        }
    }
}

/**
 * DNS cache status.
 */
enum class CacheStatus {
    /** Query served from cache (fast) */
    HIT,

    /** Query required authoritative lookup (slower) */
    MISS,

    /** Cache present but not effective (similar times) */
    INEFFECTIVE,

    /** Cannot determine cache status */
    UNKNOWN,

    ;

    val description: String
        get() =
            when (this) {
                HIT -> "DNS cache hit - query served from cache"
                MISS -> "DNS cache miss - authoritative lookup required"
                INEFFECTIVE -> "DNS cache not effective - similar resolution times"
                UNKNOWN -> "Cannot determine cache status"
            }
}
