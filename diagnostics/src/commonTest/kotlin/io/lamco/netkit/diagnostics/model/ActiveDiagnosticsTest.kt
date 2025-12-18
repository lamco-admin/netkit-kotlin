package io.lamco.netkit.diagnostics.model

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ActiveDiagnosticsTest {
    @Test
    fun `create comprehensive diagnostics with all test types`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests = listOf(createSuccessfulPing()),
                tracerouteResults = listOf(createSuccessfulTraceroute()),
                bandwidthTests = listOf(createSuccessfulBandwidth()),
                dnsTests = listOf(createSuccessfulDns()),
                testSuiteName = "Full Network Diagnostic",
                testDuration = 45.seconds,
            )

        assertEquals("Full Network Diagnostic", diagnostics.testSuiteName)
        assertTrue(diagnostics.hasAnyTests)
        assertEquals(4, diagnostics.totalTestCount)
        assertTrue(diagnostics.isSuccessful)
    }

    @Test
    fun `reject blank test suite name`() {
        assertFailsWith<IllegalArgumentException> {
            ActiveDiagnostics(
                testSuiteName = "",
                testDuration = 10.seconds,
            )
        }
    }

    @Test
    fun `reject negative test duration`() {
        assertFailsWith<IllegalArgumentException> {
            ActiveDiagnostics(
                testSuiteName = "Test",
                testDuration = (-5).seconds,
            )
        }
    }

    @Test
    fun `success rate calculation`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests =
                    listOf(
                        createSuccessfulPing(),
                        createFailedPing(),
                    ),
                tracerouteResults =
                    listOf(
                        createSuccessfulTraceroute(),
                    ),
                bandwidthTests =
                    listOf(
                        createSuccessfulBandwidth(),
                        createFailedBandwidth(),
                    ),
                testDuration = 30.seconds,
            )

        assertEquals(5, diagnostics.totalTestCount)
        assertEquals(3, diagnostics.successfulTestCount)
        assertEquals(2, diagnostics.failedTestCount)
        assertEquals(60.0, diagnostics.successRate)
    }

    @Test
    fun `connectivity quality from best ping`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests =
                    listOf(
                        createExcellentPing(),
                        createPoorPing(),
                    ),
                testDuration = 20.seconds,
            )

        // Should be EXCELLENT from the best ping
        assertEquals(PingQuality.EXCELLENT, diagnostics.connectivityQuality)
    }

    @Test
    fun `best latency is minimum across all pings`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests =
                    listOf(
                        createPingWithLatency(15.milliseconds),
                        createPingWithLatency(5.milliseconds),
                        createPingWithLatency(25.milliseconds),
                    ),
                testDuration = 20.seconds,
            )

        assertEquals(5.milliseconds, diagnostics.bestLatency)
    }

    @Test
    fun `worst packet loss is maximum across all pings`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests =
                    listOf(
                        createPingWithPacketLoss(0.0),
                        createPingWithPacketLoss(15.0),
                        createPingWithPacketLoss(5.0),
                    ),
                testDuration = 20.seconds,
            )

        assertEquals(15.0, diagnostics.worstPacketLoss)
    }

    @Test
    fun `healthy gateway check across traceroutes`() {
        val diagnostics =
            ActiveDiagnostics(
                tracerouteResults =
                    listOf(
                        createTracerouteWithGateway(2.milliseconds),
                        createTracerouteWithGateway(3.milliseconds),
                    ),
                testDuration = 30.seconds,
            )

        assertTrue(diagnostics.hasHealthyGateway)
    }

    @Test
    fun `best hop count is minimum across traceroutes`() {
        val diagnostics =
            ActiveDiagnostics(
                tracerouteResults =
                    listOf(
                        createTracerouteWithHops(5),
                        createTracerouteWithHops(3),
                        createTracerouteWithHops(8),
                    ),
                testDuration = 30.seconds,
            )

        assertEquals(3, diagnostics.bestHopCount)
    }

    @Test
    fun `best download and upload speeds`() {
        val diagnostics =
            ActiveDiagnostics(
                bandwidthTests =
                    listOf(
                        createBandwidthTest(download = 50.0, upload = 25.0),
                        createBandwidthTest(download = 100.0, upload = 50.0),
                        createBandwidthTest(download = 75.0, upload = 40.0),
                    ),
                testDuration = 30.seconds,
            )

        assertEquals(100.0, diagnostics.bestDownloadSpeed)
        assertEquals(50.0, diagnostics.bestUploadSpeed)
    }

    @Test
    fun `DNS success rate and average resolution time`() {
        val diagnostics =
            ActiveDiagnostics(
                dnsTests =
                    listOf(
                        createDnsTest(15.milliseconds, successful = true),
                        createDnsTest(20.milliseconds, successful = true),
                        createDnsTest(0.milliseconds, successful = false),
                    ),
                testDuration = 5.seconds,
            )

        assertEquals(66.67, diagnostics.dnsSuccessRate, 0.01)
        assertEquals(17.milliseconds, diagnostics.avgDnsResolutionTime)
    }

    @Test
    fun `overall health EXCELLENT when all categories excellent`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests = listOf(createExcellentPing()),
                tracerouteResults = listOf(createExcellentTraceroute()),
                bandwidthTests = listOf(createExcellentBandwidth()),
                dnsTests = listOf(createExcellentDns()),
                testDuration = 45.seconds,
            )

        assertEquals(DiagnosticHealth.EXCELLENT, diagnostics.overallHealth)
    }

    @Test
    fun `overall health degraded by worst category`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests = listOf(createExcellentPing()),
                tracerouteResults = listOf(createExcellentTraceroute()),
                bandwidthTests = listOf(createPoorBandwidth()), // Poor bandwidth
                dnsTests = listOf(createExcellentDns()),
                testDuration = 45.seconds,
            )

        // Should be degraded due to poor bandwidth
        assertTrue(diagnostics.overallHealth != DiagnosticHealth.EXCELLENT)
    }

    @Test
    fun `identify high packet loss issue`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests = listOf(createPingWithPacketLoss(15.0)),
                testDuration = 10.seconds,
            )

        val issues = diagnostics.identifyIssues()
        assertTrue(
            issues.any { it.category == DiagnosticCategory.CONNECTIVITY && it.severity == IssueSeverity.CRITICAL },
        )
    }

    @Test
    fun `identify high latency issue`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests = listOf(createPingWithLatency(150.milliseconds)),
                testDuration = 10.seconds,
            )

        val issues = diagnostics.identifyIssues()
        assertTrue(issues.any { it.category == DiagnosticCategory.CONNECTIVITY })
    }

    @Test
    fun `identify low bandwidth issue`() {
        val diagnostics =
            ActiveDiagnostics(
                bandwidthTests = listOf(createBandwidthTest(download = 5.0, upload = 2.0)),
                testDuration = 20.seconds,
            )

        val issues = diagnostics.identifyIssues()
        assertTrue(issues.any { it.category == DiagnosticCategory.THROUGHPUT })
    }

    @Test
    fun `identify DNS resolution failures`() {
        val diagnostics =
            ActiveDiagnostics(
                dnsTests =
                    listOf(
                        createDnsTest(15.milliseconds, successful = true),
                        createDnsTest(0.milliseconds, successful = false),
                        createDnsTest(0.milliseconds, successful = false),
                    ),
                testDuration = 5.seconds,
            )

        val issues = diagnostics.identifyIssues()
        assertTrue(issues.any { it.category == DiagnosticCategory.DNS })
    }

    @Test
    fun `issues sorted by severity`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests = listOf(createPingWithPacketLoss(15.0)), // CRITICAL
                bandwidthTests = listOf(createBandwidthTest(download = 8.0, upload = 4.0)), // HIGH
                testDuration = 20.seconds,
            )

        val issues = diagnostics.identifyIssues()
        assertTrue(issues.isNotEmpty())
        // First issue should be highest severity
        assertTrue(issues.first().severity.priority >= issues.last().severity.priority)
    }

    @Test
    fun `executive summary includes all categories`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests = listOf(createSuccessfulPing()),
                tracerouteResults = listOf(createSuccessfulTraceroute()),
                bandwidthTests = listOf(createSuccessfulBandwidth()),
                dnsTests = listOf(createSuccessfulDns()),
                testSuiteName = "Complete Diagnostic",
                testDuration = 45.seconds,
            )

        val summary = diagnostics.executiveSummary()
        assertTrue(summary.contains("Complete Diagnostic"))
        assertTrue(summary.contains("Overall Health"))
        assertTrue(summary.contains("Connectivity"))
        assertTrue(summary.contains("Routing"))
        assertTrue(summary.contains("Throughput"))
        assertTrue(summary.contains("DNS"))
    }

    @Test
    fun `detailed report includes all test results`() {
        val diagnostics =
            ActiveDiagnostics(
                pingTests = listOf(createSuccessfulPing()),
                tracerouteResults = listOf(createSuccessfulTraceroute()),
                testDuration = 30.seconds,
            )

        val report = diagnostics.detailedReport()
        assertTrue(report.contains("Ping Tests"))
        assertTrue(report.contains("Traceroute Results"))
    }

    // ===== Helper Functions =====

    private fun createSuccessfulPing() =
        PingTest(
            targetHost = "8.8.8.8",
            packetsTransmitted = 10,
            packetsReceived = 10,
            packetLossPercent = 0.0,
            minRtt = 5.milliseconds,
            avgRtt = 8.milliseconds,
            maxRtt = 12.milliseconds,
            stdDevRtt = 2.milliseconds,
        )

    private fun createFailedPing() =
        PingTest.failed(
            targetHost = "unreachable.host",
            reason = "Network unreachable",
        )

    private fun createExcellentPing() =
        PingTest(
            targetHost = "8.8.8.8",
            packetsTransmitted = 10,
            packetsReceived = 10,
            packetLossPercent = 0.0,
            minRtt = 5.milliseconds,
            avgRtt = 10.milliseconds,
            maxRtt = 15.milliseconds,
            stdDevRtt = 2.milliseconds,
        )

    private fun createPoorPing() =
        PingTest(
            targetHost = "slow.server",
            packetsTransmitted = 10,
            packetsReceived = 7,
            packetLossPercent = 30.0,
            minRtt = 100.milliseconds,
            avgRtt = 150.milliseconds,
            maxRtt = 200.milliseconds,
            stdDevRtt = 30.milliseconds,
        )

    private fun createPingWithLatency(latency: kotlin.time.Duration) =
        PingTest(
            targetHost = "8.8.8.8",
            packetsTransmitted = 10,
            packetsReceived = 10,
            packetLossPercent = 0.0,
            minRtt = latency - 2.milliseconds,
            avgRtt = latency,
            maxRtt = latency + 2.milliseconds,
            stdDevRtt = 1.milliseconds,
        )

    private fun createPingWithPacketLoss(lossPercent: Double) =
        PingTest(
            targetHost = "8.8.8.8",
            packetsTransmitted = 100,
            packetsReceived = (100 - lossPercent).toInt(),
            packetLossPercent = lossPercent,
            minRtt = 5.milliseconds,
            avgRtt = 10.milliseconds,
            maxRtt = 15.milliseconds,
            stdDevRtt = 2.milliseconds,
        )

    private fun createSuccessfulTraceroute() =
        TracerouteResult(
            targetHost = "example.com",
            hops =
                listOf(
                    TracerouteHop(1, "192.168.1.1", rtt = 2.milliseconds),
                    TracerouteHop(2, "10.0.0.1", rtt = 5.milliseconds),
                    TracerouteHop(3, "93.184.216.34", rtt = 12.milliseconds),
                ),
            completed = true,
            testDuration = 10.seconds,
        )

    private fun createExcellentTraceroute() =
        TracerouteResult(
            targetHost = "example.com",
            hops =
                listOf(
                    TracerouteHop(1, "192.168.1.1", rtt = 1.milliseconds),
                    TracerouteHop(2, "10.0.0.1", rtt = 3.milliseconds),
                    TracerouteHop(3, "93.184.216.34", rtt = 8.milliseconds),
                ),
            completed = true,
            testDuration = 5.seconds,
        )

    private fun createTracerouteWithGateway(gatewayLatency: kotlin.time.Duration) =
        TracerouteResult(
            targetHost = "example.com",
            hops =
                listOf(
                    TracerouteHop(1, "192.168.1.1", rtt = gatewayLatency),
                    TracerouteHop(2, "10.0.0.1", rtt = 5.milliseconds),
                ),
            completed = false,
            testDuration = 5.seconds,
        )

    private fun createTracerouteWithHops(hopCount: Int) =
        TracerouteResult(
            targetHost = "example.com",
            hops = (1..hopCount).map { TracerouteHop(it, "10.0.0.$it", rtt = (it * 2).milliseconds) },
            completed = true,
            testDuration = 10.seconds,
        )

    private fun createSuccessfulBandwidth() =
        BandwidthTest(
            downloadMbps = 85.0,
            uploadMbps = 42.0,
            testDuration = 15.seconds,
            serverHost = "speedtest.example.com",
        )

    private fun createFailedBandwidth() =
        BandwidthTest.failed(
            serverHost = "unreachable.server",
            reason = "Connection timeout",
        )

    private fun createExcellentBandwidth() =
        BandwidthTest(
            downloadMbps = 150.0,
            uploadMbps = 75.0,
            testDuration = 15.seconds,
            serverHost = "speedtest.example.com",
        )

    private fun createPoorBandwidth() =
        BandwidthTest(
            downloadMbps = 8.0,
            uploadMbps = 4.0,
            testDuration = 15.seconds,
            serverHost = "speedtest.example.com",
        )

    private fun createBandwidthTest(
        download: Double,
        upload: Double,
    ) = BandwidthTest(
        downloadMbps = download,
        uploadMbps = upload,
        testDuration = 15.seconds,
        serverHost = "speedtest.example.com",
    )

    private fun createSuccessfulDns() =
        DnsTest(
            hostname = "example.com",
            recordType = DnsRecordType.A,
            resolvedAddresses = listOf("93.184.216.34"),
            resolutionTime = 15.milliseconds,
            dnsServer = "8.8.8.8",
        )

    private fun createExcellentDns() =
        DnsTest(
            hostname = "example.com",
            recordType = DnsRecordType.A,
            resolvedAddresses = listOf("93.184.216.34"),
            resolutionTime = 10.milliseconds,
            dnsServer = "8.8.8.8",
        )

    private fun createDnsTest(
        resolutionTime: kotlin.time.Duration,
        successful: Boolean,
    ) = if (successful) {
        DnsTest(
            hostname = "example.com",
            recordType = DnsRecordType.A,
            resolvedAddresses = listOf("93.184.216.34"),
            resolutionTime = resolutionTime,
            dnsServer = "8.8.8.8",
        )
    } else {
        DnsTest.failed(
            hostname = "nonexistent.invalid",
            dnsServer = "8.8.8.8",
            reason = "NXDOMAIN",
            resolutionTime = resolutionTime,
        )
    }
}
