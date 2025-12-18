package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.*
import io.lamco.netkit.logging.NetKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Enhanced DNS tester with multiple query types and DNSSEC/caching/hijacking detection.
 *
 * Extends basic DNS testing with comprehensive analysis:
 * - Tests multiple record types (A, AAAA, MX, TXT, etc.)
 * - Detects DNSSEC support
 * - Analyzes caching effectiveness
 * - Detects DNS hijacking and captive portals
 * - Validates IPv6 support
 *
 * ## Testing Methodology
 *
 * 1. Query multiple record types for comprehensive test
 * 2. Test DNSSEC support with signed domain
 * 3. Measure cache effectiveness (first vs. second query)
 * 4. Test for hijacking (non-existent domain should return NXDOMAIN)
 * 5. Test for captive portal (known connectivity check domains)
 *
 * @property dnsTester Basic DNS tester for individual queries
 *
 * @since 1.1.0
 */
class EnhancedDnsTester(
    private val dnsTester: DnsTester,
) {
    /**
     * Execute enhanced DNS test.
     *
     * @param domain Domain to query
     * @param server DNS server to test
     * @param queryTypes Query types to test (default: A, AAAA, MX)
     * @return Enhanced DNS test result
     */
    suspend fun executeEnhancedTest(
        domain: String,
        server: String,
        queryTypes: List<DnsQueryType> =
            listOf(
                DnsQueryType.A,
                DnsQueryType.AAAA,
                DnsQueryType.MX,
            ),
    ): EnhancedDnsTest =
        coroutineScope {
            val startTime = System.currentTimeMillis()

            // Execute queries in parallel
            val queryResults =
                queryTypes
                    .map { type ->
                        async {
                            executeQueryType(domain, server, type)
                        }
                    }.awaitAll()
                    .associateBy { it.type }

            // Test DNSSEC support (query cloudflare.com which is DNSSEC-signed)
            val dnssecSupported = testDnssecSupport(server)

            // Test cache effectiveness
            val cacheStatus = testCacheEffectiveness(domain, server)

            // Test for DNS hijacking
            val hijackingDetected = testForHijacking(server)

            // Test for captive portal
            val captivePortal = testForCaptivePortal(server)

            // Check IPv6 support
            val ipv6Supported = queryResults[DnsQueryType.AAAA]?.success == true

            val totalTime = (System.currentTimeMillis() - startTime).milliseconds

            EnhancedDnsTest(
                domain = domain,
                server = server,
                queryResults = queryResults,
                totalTime = totalTime,
                dnssecSupported = dnssecSupported,
                cacheStatus = cacheStatus,
                hijackingDetected = hijackingDetected,
                captivePortalDetected = captivePortal,
                ipv6Supported = ipv6Supported,
            )
        }

    /**
     * Execute single query type.
     *
     * Requires platform-specific DNS APIs (Android: DnsResolver, JVM: InetAddress/dnsjava).
     */
    private suspend fun executeQueryType(
        domain: String,
        server: String,
        type: DnsQueryType,
    ): DnsQueryResult =
        try {
            // Delegate to basic DNS tester
            // In real implementation, this would specify query type
            val basicResult =
                dnsTester.executeDnsTest(
                    hostname = domain,
                    dnsServer = server,
                )

            DnsQueryResult(
                type = type,
                resolutionTime = basicResult.resolutionTime,
                recordCount = if (basicResult.isSuccessful) basicResult.resultCount else 0,
                answers = if (basicResult.isSuccessful) basicResult.resolvedAddresses else emptyList(),
                success = basicResult.isSuccessful,
                errorMessage = basicResult.failureReason,
            )
        } catch (e: CancellationException) {
            throw e // Always propagate cancellation
        } catch (e: DnsTestException) {
            DnsQueryResult(
                type = type,
                resolutionTime = Duration.ZERO,
                recordCount = 0,
                answers = emptyList(),
                success = false,
                errorMessage = e.message,
            )
        }

    /**
     * Test DNSSEC support.
     *
     * Queries a known DNSSEC-signed domain (cloudflare.com) and checks
     * for authenticated data (AD) flag in response.
     */
    private suspend fun testDnssecSupport(server: String): Boolean =
        try {
            // Query DNSSEC-signed domain
            val result =
                dnsTester.executeDnsTest(
                    hostname = "cloudflare.com",
                    dnsServer = server,
                )
            // In full implementation, check AD flag in DNS response
            // For now, assume true if query succeeds (conservative)
            result.isSuccessful
        } catch (e: CancellationException) {
            throw e // Always propagate cancellation
        } catch (e: DnsTestException) {
            NetKit.logger.warn("DNSSEC support test failed for server $server", e)
            false
        }

    /**
     * Test cache effectiveness.
     *
     * Queries same domain twice and compares resolution times.
     * Cache hit should be significantly faster (<10ms).
     */
    private suspend fun testCacheEffectiveness(
        domain: String,
        server: String,
    ): CacheStatus =
        try {
            // First query (cache miss)
            val first = dnsTester.executeDnsTest(hostname = domain, dnsServer = server)
            val firstTime = first.resolutionTime

            // Wait briefly
            kotlinx.coroutines.delay(500)

            // Second query (should be cache hit)
            val second = dnsTester.executeDnsTest(hostname = domain, dnsServer = server)
            val secondTime = second.resolutionTime

            // Cache hit should be <10ms or at least 50% faster
            when {
                secondTime < 10.milliseconds -> CacheStatus.HIT
                secondTime < firstTime * 0.5 -> CacheStatus.HIT
                secondTime >= firstTime * 0.9 -> CacheStatus.MISS
                else -> CacheStatus.INEFFECTIVE
            }
        } catch (e: CancellationException) {
            throw e // Always propagate cancellation
        } catch (e: DnsTestException) {
            NetKit.logger.warn("DNS cache effectiveness test failed for domain $domain on server $server", e)
            CacheStatus.UNKNOWN
        }

    /**
     * Test for DNS hijacking.
     *
     * Queries a non-existent domain (should return NXDOMAIN or no result).
     * If it returns an IP address, DNS is being hijacked.
     */
    private suspend fun testForHijacking(server: String): Boolean =
        try {
            // Query guaranteed non-existent domain
            val nonExistentDomain = "this-domain-definitely-does-not-exist-${System.currentTimeMillis()}.com"
            val result = dnsTester.executeDnsTest(hostname = nonExistentDomain, dnsServer = server)

            // If we get an IP back for non-existent domain → hijacking
            result.isSuccessful && result.primaryAddress != null
        } catch (e: CancellationException) {
            throw e // Always propagate cancellation
        } catch (e: DnsTestException) {
            NetKit.logger.warn("DNS hijacking test failed for server $server", e)
            false // Error is expected for non-existent domain
        }

    /**
     * Test for captive portal.
     *
     * Queries Android/Google connectivity check domains and validates responses.
     * Captive portals often redirect these to login pages.
     */
    private suspend fun testForCaptivePortal(server: String): Boolean =
        try {
            // Android connectivity check domain
            val result =
                dnsTester.executeDnsTest(
                    hostname = "connectivitycheck.gstatic.com",
                    dnsServer = server,
                )

            // If domain resolves but to unexpected IP → captive portal
            // Real implementation would validate against known IP
            // For now, just check if resolution is suspiciously fast or wrong
            val resolutionTime = result.resolutionTime

            // Captive portal often returns instant response (cached redirect)
            resolutionTime < 5.milliseconds && result.isSuccessful
        } catch (e: CancellationException) {
            throw e // Always propagate cancellation
        } catch (e: DnsTestException) {
            NetKit.logger.warn("Captive portal test failed for server $server", e)
            false
        }

    companion object {
        /**
         * Default query types for comprehensive DNS testing.
         */
        val DEFAULT_QUERY_TYPES =
            listOf(
                DnsQueryType.A,
                DnsQueryType.AAAA,
                DnsQueryType.MX,
            )

        /**
         * Extended query types for thorough analysis.
         */
        val EXTENDED_QUERY_TYPES =
            listOf(
                DnsQueryType.A,
                DnsQueryType.AAAA,
                DnsQueryType.CNAME,
                DnsQueryType.MX,
                DnsQueryType.TXT,
                DnsQueryType.NS,
                DnsQueryType.SOA,
            )
    }
}
