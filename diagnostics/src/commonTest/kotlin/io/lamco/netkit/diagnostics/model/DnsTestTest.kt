package io.lamco.netkit.diagnostics.model

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class DnsTestTest {
    @Test
    fun `create valid DNS test`() {
        val test =
            DnsTest(
                hostname = "example.com",
                recordType = DnsRecordType.A,
                resolvedAddresses = listOf("93.184.216.34"),
                resolutionTime = 15.milliseconds,
                dnsServer = "8.8.8.8",
                ttl = 3600,
            )

        assertEquals("example.com", test.hostname)
        assertEquals(DnsRecordType.A, test.recordType)
        assertEquals(listOf("93.184.216.34"), test.resolvedAddresses)
        assertEquals(15.milliseconds, test.resolutionTime)
        assertEquals("8.8.8.8", test.dnsServer)
        assertEquals(3600, test.ttl)
        assertTrue(test.isSuccessful)
    }

    @Test
    fun `reject blank hostname`() {
        assertFailsWith<IllegalArgumentException> {
            DnsTest(
                hostname = "",
                recordType = DnsRecordType.A,
                resolvedAddresses = emptyList(),
                resolutionTime = 15.milliseconds,
                dnsServer = "8.8.8.8",
            )
        }
    }

    @Test
    fun `reject negative resolution time`() {
        assertFailsWith<IllegalArgumentException> {
            DnsTest(
                hostname = "example.com",
                recordType = DnsRecordType.A,
                resolvedAddresses = emptyList(),
                resolutionTime = (-5).milliseconds,
                dnsServer = "8.8.8.8",
            )
        }
    }

    @Test
    fun `resolution quality EXCELLENT for under 20ms`() {
        val test =
            DnsTest(
                hostname = "example.com",
                recordType = DnsRecordType.A,
                resolvedAddresses = listOf("93.184.216.34"),
                resolutionTime = 15.milliseconds,
                dnsServer = "8.8.8.8",
            )

        assertEquals(DnsResolutionQuality.EXCELLENT, test.resolutionQuality)
    }

    @Test
    fun `resolution quality CRITICAL for 200ms+`() {
        val test =
            DnsTest(
                hostname = "example.com",
                recordType = DnsRecordType.A,
                resolvedAddresses = listOf("93.184.216.34"),
                resolutionTime = 250.milliseconds,
                dnsServer = "8.8.8.8",
            )

        assertEquals(DnsResolutionQuality.CRITICAL, test.resolutionQuality)
    }

    @Test
    fun `resolution quality FAILED when no addresses resolved`() {
        val test =
            DnsTest(
                hostname = "nonexistent.invalid",
                recordType = DnsRecordType.A,
                resolvedAddresses = emptyList(),
                resolutionTime = 50.milliseconds,
                dnsServer = "8.8.8.8",
                failureReason = "NXDOMAIN",
            )

        assertEquals(DnsResolutionQuality.FAILED, test.resolutionQuality)
        assertTrue(test.isFailed)
    }

    @Test
    fun `primary address is first result`() {
        val test =
            DnsTest(
                hostname = "example.com",
                recordType = DnsRecordType.A,
                resolvedAddresses = listOf("93.184.216.34", "93.184.216.35"),
                resolutionTime = 15.milliseconds,
                dnsServer = "8.8.8.8",
            )

        assertEquals("93.184.216.34", test.primaryAddress)
        assertEquals(2, test.resultCount)
    }

    @Test
    fun `check acceptable speed`() {
        val test =
            DnsTest(
                hostname = "example.com",
                recordType = DnsRecordType.A,
                resolvedAddresses = listOf("93.184.216.34"),
                resolutionTime = 50.milliseconds,
                dnsServer = "8.8.8.8",
            )

        assertTrue(test.isAcceptableSpeed(maxResolutionTime = 100.milliseconds))
        assertFalse(test.isAcceptableSpeed(maxResolutionTime = 25.milliseconds))
    }

    @Test
    fun `check reasonable TTL`() {
        val test =
            DnsTest(
                hostname = "example.com",
                recordType = DnsRecordType.A,
                resolvedAddresses = listOf("93.184.216.34"),
                resolutionTime = 15.milliseconds,
                dnsServer = "8.8.8.8",
                ttl = 3600,
            )

        assertTrue(test.hasReasonableTtl())
        assertFalse(test.hasReasonableTtl(minTtl = 7200))
    }

    @Test
    fun `failed factory creates failed DNS test`() {
        val test =
            DnsTest.failed(
                hostname = "nonexistent.invalid",
                recordType = DnsRecordType.A,
                dnsServer = "8.8.8.8",
                reason = "NXDOMAIN",
            )

        assertEquals("nonexistent.invalid", test.hostname)
        assertTrue(test.resolvedAddresses.isEmpty())
        assertEquals("NXDOMAIN", test.failureReason)
        assertTrue(test.isFailed)
    }

    // ===== DnsTestSuite Tests =====

    @Test
    fun `DNS test suite aggregates multiple tests`() {
        val tests =
            listOf(
                DnsTest(
                    hostname = "example.com",
                    recordType = DnsRecordType.A,
                    resolvedAddresses = listOf("93.184.216.34"),
                    resolutionTime = 15.milliseconds,
                    dnsServer = "8.8.8.8",
                ),
                DnsTest(
                    hostname = "google.com",
                    recordType = DnsRecordType.A,
                    resolvedAddresses = listOf("142.250.185.46"),
                    resolutionTime = 12.milliseconds,
                    dnsServer = "8.8.8.8",
                ),
                DnsTest.failed(
                    hostname = "nonexistent.invalid",
                    dnsServer = "8.8.8.8",
                    reason = "NXDOMAIN",
                ),
            )

        val suite = DnsTestSuite(tests)

        assertEquals(3, suite.tests.size)
        assertEquals(2, suite.successfulTests)
        assertEquals(1, suite.failedTests)
        assertEquals(66.67, suite.successRate, 0.01)
    }

    @Test
    fun `DNS test suite calculates average resolution time`() {
        val tests =
            listOf(
                DnsTest(
                    hostname = "example.com",
                    recordType = DnsRecordType.A,
                    resolvedAddresses = listOf("93.184.216.34"),
                    resolutionTime = 10.milliseconds,
                    dnsServer = "8.8.8.8",
                ),
                DnsTest(
                    hostname = "google.com",
                    recordType = DnsRecordType.A,
                    resolvedAddresses = listOf("142.250.185.46"),
                    resolutionTime = 20.milliseconds,
                    dnsServer = "8.8.8.8",
                ),
            )

        val suite = DnsTestSuite(tests)

        assertEquals(15.milliseconds, suite.averageResolutionTime)
        assertEquals(10.milliseconds, suite.fastestResolutionTime)
        assertEquals(20.milliseconds, suite.slowestResolutionTime)
    }

    @Test
    fun `DNS test suite overall quality based on success rate and speed`() {
        val tests =
            listOf(
                DnsTest(
                    hostname = "example.com",
                    recordType = DnsRecordType.A,
                    resolvedAddresses = listOf("93.184.216.34"),
                    resolutionTime = 15.milliseconds,
                    dnsServer = "8.8.8.8",
                ),
                DnsTest(
                    hostname = "google.com",
                    recordType = DnsRecordType.A,
                    resolvedAddresses = listOf("142.250.185.46"),
                    resolutionTime = 18.milliseconds,
                    dnsServer = "8.8.8.8",
                ),
            )

        val suite = DnsTestSuite(tests)

        assertEquals(DnsResolutionQuality.EXCELLENT, suite.overallQuality)
    }

    @Test
    fun `DNS test suite groups by server`() {
        val tests =
            listOf(
                DnsTest(
                    hostname = "example.com",
                    recordType = DnsRecordType.A,
                    resolvedAddresses = listOf("93.184.216.34"),
                    resolutionTime = 15.milliseconds,
                    dnsServer = "8.8.8.8",
                ),
                DnsTest(
                    hostname = "example.com",
                    recordType = DnsRecordType.A,
                    resolvedAddresses = listOf("93.184.216.34"),
                    resolutionTime = 25.milliseconds,
                    dnsServer = "1.1.1.1",
                ),
            )

        val suite = DnsTestSuite(tests)
        val byServer = suite.testsByServer()

        assertEquals(2, byServer.size)
        assertEquals(1, byServer["8.8.8.8"]?.size)
        assertEquals(1, byServer["1.1.1.1"]?.size)
    }
}
