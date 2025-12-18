package io.lamco.netkit.diagnostics.executor

import io.lamco.netkit.diagnostics.model.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PingParserTest {
    @Test
    fun `parse Linux ping output successfully`() {
        val output =
            """
            PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
            64 bytes from 8.8.8.8: icmp_seq=1 ttl=119 time=8.23 ms
            64 bytes from 8.8.8.8: icmp_seq=2 ttl=119 time=7.45 ms

            --- 8.8.8.8 ping statistics ---
            10 packets transmitted, 10 received, 0% packet loss, time 9012ms
            rtt min/avg/max/mdev = 5.123/8.456/12.789/2.345 ms
            """.trimIndent()

        val result = PingParser.parse(output, "8.8.8.8")

        assertEquals("8.8.8.8", result.targetHost)
        assertEquals(10, result.packetsTransmitted)
        assertEquals(10, result.packetsReceived)
        assertEquals(0.0, result.packetLossPercent)
        assertEquals(5.123.milliseconds, result.minRtt)
        assertEquals(8.456.milliseconds, result.avgRtt)
        assertEquals(12.789.milliseconds, result.maxRtt)
        assertEquals(2.345.milliseconds, result.stdDevRtt)
        assertTrue(result.isSuccessful)
    }

    @Test
    fun `parse Linux ping with packet loss`() {
        val output =
            """
            --- 8.8.8.8 ping statistics ---
            10 packets transmitted, 7 received, 30% packet loss, time 9015ms
            rtt min/avg/max/mdev = 5.1/9.2/15.3/3.1 ms
            """.trimIndent()

        val result = PingParser.parse(output, "8.8.8.8")

        assertEquals(10, result.packetsTransmitted)
        assertEquals(7, result.packetsReceived)
        assertEquals(30.0, result.packetLossPercent)
        assertTrue(result.isSuccessful)
    }

    @Test
    fun `parse Linux ping total failure`() {
        val output =
            """
            --- 192.168.99.99 ping statistics ---
            5 packets transmitted, 0 received, 100% packet loss, time 4089ms
            """.trimIndent()

        val result = PingParser.parse(output, "192.168.99.99")

        assertEquals(5, result.packetsTransmitted)
        assertEquals(0, result.packetsReceived)
        assertEquals(100.0, result.packetLossPercent)
        assertNull(result.avgRtt)
        assertTrue(result.isFailed)
    }

    @Test
    fun `parse Windows ping output successfully`() {
        val output =
            """
            Pinging 8.8.8.8 with 32 bytes of data:
            Reply from 8.8.8.8: bytes=32 time=8ms TTL=119
            Reply from 8.8.8.8: bytes=32 time=7ms TTL=119

            Ping statistics for 8.8.8.8:
                Packets: Sent = 10, Received = 10, Lost = 0 (0% loss),
            Approximate round trip times in milli-seconds:
                Minimum = 5ms, Maximum = 12ms, Average = 8ms
            """.trimIndent()

        val result = PingParser.parse(output, "8.8.8.8")

        assertEquals(10, result.packetsTransmitted)
        assertEquals(10, result.packetsReceived)
        assertEquals(0.0, result.packetLossPercent)
        assertEquals(5.milliseconds, result.minRtt)
        assertEquals(8.milliseconds, result.avgRtt)
        assertEquals(12.milliseconds, result.maxRtt)
        assertNotNull(result.stdDevRtt) // Windows estimates stddev
        assertTrue(result.isSuccessful)
    }

    @Test
    fun `parse Windows ping with packet loss`() {
        val output =
            """
            Ping statistics for 192.168.1.1:
                Packets: Sent = 10, Received = 7, Lost = 3 (30% loss),
            Approximate round trip times in milli-seconds:
                Minimum = 1ms, Maximum = 5ms, Average = 3ms
            """.trimIndent()

        val result = PingParser.parse(output, "192.168.1.1")

        assertEquals(10, result.packetsTransmitted)
        assertEquals(7, result.packetsReceived)
        assertEquals(30.0, result.packetLossPercent)
        assertTrue(result.isSuccessful)
    }
}

class PingResultBuilderTest {
    @Test
    fun `builder creates valid ping result`() {
        val ping =
            PingResultBuilder(targetHost = "8.8.8.8")
                .setPackets(transmitted = 10, received = 10)
                .setPacketLoss(0.0)
                .setRtt(min = 5.0, avg = 8.5, max = 12.0, stddev = 2.0)
                .setDuration(10.seconds)
                .build()

        assertEquals("8.8.8.8", ping.targetHost)
        assertEquals(10, ping.packetsTransmitted)
        assertEquals(10, ping.packetsReceived)
        assertEquals(0.0, ping.packetLossPercent)
        assertEquals(5.milliseconds, ping.minRtt)
        assertEquals(8.5.milliseconds, ping.avgRtt)
        assertEquals(12.milliseconds, ping.maxRtt)
        assertEquals(2.milliseconds, ping.stdDevRtt)
        assertEquals(10.seconds, ping.testDuration)
        assertTrue(ping.isSuccessful)
    }

    @Test
    fun `builder creates failed ping result`() {
        val ping =
            PingResultBuilder(targetHost = "unreachable.host")
                .setPackets(transmitted = 5, received = 0)
                .setPacketLoss(100.0)
                .setFailure("Network unreachable")
                .build()

        assertTrue(ping.isFailed)
        assertEquals("Network unreachable", ping.failureReason)
    }
}

class TracerouteParserTest {
    @Test
    fun `parse Linux traceroute output successfully`() {
        val output =
            """
            traceroute to example.com (93.184.216.34), 30 hops max, 60 byte packets
             1  192.168.1.1 (192.168.1.1)  1.234 ms  1.456 ms  1.789 ms
             2  10.0.0.1 (10.0.0.1)  5.123 ms  5.456 ms  5.789 ms
             3  93.184.216.34 (93.184.216.34)  12.345 ms  12.678 ms  12.901 ms
            """.trimIndent()

        val result = TracerouteParser.parse(output, "example.com")

        assertEquals("example.com", result.targetHost)
        assertEquals("93.184.216.34", result.resolvedIp)
        assertEquals(3, result.totalHops)
        assertTrue(result.completed)
        assertTrue(result.isSuccessful)

        val firstHop = result.hops[0]
        assertEquals(1, firstHop.hopNumber)
        assertEquals("192.168.1.1", firstHop.ipAddress)
        assertEquals(1.234.milliseconds, firstHop.rtt)
        assertFalse(firstHop.isTimeout)
    }

    @Test
    fun `parse Linux traceroute with timeouts`() {
        val output =
            """
            traceroute to example.com (93.184.216.34), 30 hops max
             1  192.168.1.1  1.5 ms
             2  * * *
             3  10.0.0.1  5.2 ms
             4  ***
            """.trimIndent()

        val result = TracerouteParser.parse(output, "example.com")

        assertEquals(4, result.totalHops)
        assertEquals(2, result.timedOutHops.size)
        assertEquals(2, result.responsiveHops.size)

        val timeoutHop = result.hops[1]
        assertTrue(timeoutHop.isTimeout)
        assertEquals(2, timeoutHop.hopNumber)
    }

    @Test
    fun `parse Windows tracert output successfully`() {
        val output =
            """
            Tracing route to example.com [93.184.216.34] over a maximum of 30 hops:
              1     1 ms     1 ms     1 ms  192.168.1.1
              2     5 ms     5 ms     5 ms  10.0.0.1
              3    12 ms    12 ms    12 ms  93.184.216.34
            """.trimIndent()

        val result = TracerouteParser.parse(output, "example.com")

        assertEquals("example.com", result.targetHost)
        assertEquals("93.184.216.34", result.resolvedIp)
        assertEquals(3, result.totalHops)
        assertTrue(result.completed)

        val firstHop = result.hops[0]
        assertEquals(1, firstHop.hopNumber)
        assertEquals("192.168.1.1", firstHop.ipAddress)
        assertEquals(1.milliseconds, firstHop.rtt)
    }

    @Test
    fun `parse Windows tracert with timeouts`() {
        val output =
            """
            Tracing route to example.com over a maximum of 30 hops:
              1     1 ms     1 ms     1 ms  192.168.1.1
              2     *        *        *     Request timed out.
              3     5 ms     5 ms     5 ms  10.0.0.1
            """.trimIndent()

        val result = TracerouteParser.parse(output, "example.com")

        assertEquals(3, result.totalHops)
        assertEquals(1, result.timedOutHops.size)

        val timeoutHop = result.hops[1]
        assertTrue(timeoutHop.isTimeout)
        assertEquals(2, timeoutHop.hopNumber)
    }
}

class TracerouteResultBuilderTest {
    @Test
    fun `builder creates valid traceroute result`() {
        val result =
            TracerouteResultBuilder(targetHost = "example.com")
                .setResolvedIp("93.184.216.34")
                .addHop(1, "192.168.1.1", "gateway", 2.0)
                .addHop(2, "10.0.0.1", "isp-router", 5.0)
                .addHop(3, "93.184.216.34", "example.com", 12.0)
                .setCompleted(true)
                .setDuration(15.seconds)
                .build()

        assertEquals("example.com", result.targetHost)
        assertEquals("93.184.216.34", result.resolvedIp)
        assertEquals(3, result.totalHops)
        assertTrue(result.completed)
        assertTrue(result.isSuccessful)

        val firstHop = result.hops[0]
        assertEquals("gateway", firstHop.hostname)
        assertEquals(2.milliseconds, firstHop.rtt)
    }

    @Test
    fun `builder creates traceroute with timeouts`() {
        val result =
            TracerouteResultBuilder(targetHost = "example.com")
                .addHop(1, "192.168.1.1", rttMs = 2.0)
                .addTimeoutHop(2)
                .addHop(3, "10.0.0.1", rttMs = 5.0)
                .setCompleted(false)
                .build()

        assertEquals(3, result.totalHops)
        assertEquals(1, result.timedOutHops.size)
        assertTrue(result.hops[1].isTimeout)
    }
}

class BandwidthTestBuilderTest {
    @Test
    fun `builder creates valid bandwidth test`() {
        val test =
            BandwidthTestBuilder(serverHost = "speedtest.example.com")
                .setDownload(mbps = 85.3, bytes = 106_625_000)
                .setUpload(mbps = 42.7, bytes = 53_375_000)
                .setDuration(10.seconds)
                .setLocation("New York, US")
                .build()

        assertEquals("speedtest.example.com", test.serverHost)
        assertEquals(85.3, test.downloadMbps)
        assertEquals(42.7, test.uploadMbps)
        assertEquals(106_625_000L, test.downloadBytes)
        assertEquals(53_375_000L, test.uploadBytes)
        assertEquals("New York, US", test.serverLocation)
        assertTrue(test.isSuccessful)
    }

    @Test
    fun `builder creates download-only test`() {
        val test =
            BandwidthTestBuilder(serverHost = "test.server")
                .setDownload(mbps = 50.0)
                .setDuration(10.seconds)
                .setTestType(BandwidthTestType.DOWNLOAD_ONLY)
                .build()

        assertEquals(50.0, test.downloadMbps)
        assertNull(test.uploadMbps)
        assertEquals(BandwidthTestType.DOWNLOAD_ONLY, test.testType)
    }
}

class ThroughputCalculatorTest {
    @Test
    fun `calculate Mbps from bytes and duration`() {
        // 1 MB in 1 second = 8 Mbps
        val mbps =
            ThroughputCalculator.calculateMbps(
                bytes = 1_000_000,
                duration = 1.seconds,
            )

        assertEquals(8.0, mbps)
    }

    @Test
    fun `calculate bytes needed for target throughput`() {
        // 10 Mbps for 10 seconds = 12.5 MB
        val bytes =
            ThroughputCalculator.calculateBytesNeeded(
                mbps = 10.0,
                duration = 10.seconds,
            )

        assertEquals(12_500_000L, bytes)
    }

    @Test
    fun `calculate Mbps handles zero duration`() {
        val mbps =
            ThroughputCalculator.calculateMbps(
                bytes = 1_000_000,
                duration = 0.seconds,
            )

        assertEquals(0.0, mbps)
    }
}

class DnsTestBuilderTest {
    @Test
    fun `builder creates valid DNS test`() {
        val test =
            DnsTestBuilder(
                hostname = "example.com",
                dnsServer = "8.8.8.8",
            ).setRecordType(DnsRecordType.A)
                .addResolvedAddress("93.184.216.34")
                .setResolutionTime(15.milliseconds)
                .setTtl(3600)
                .build()

        assertEquals("example.com", test.hostname)
        assertEquals("8.8.8.8", test.dnsServer)
        assertEquals(DnsRecordType.A, test.recordType)
        assertEquals(listOf("93.184.216.34"), test.resolvedAddresses)
        assertEquals(15.milliseconds, test.resolutionTime)
        assertEquals(3600, test.ttl)
        assertTrue(test.isSuccessful)
    }

    @Test
    fun `builder creates DNS test with multiple addresses`() {
        val test =
            DnsTestBuilder(
                hostname = "google.com",
                dnsServer = "1.1.1.1",
            ).setResolvedAddresses(listOf("142.250.185.46", "142.250.185.78"))
                .setResolutionTime(12.milliseconds)
                .build()

        assertEquals(2, test.resultCount)
        assertEquals("142.250.185.46", test.primaryAddress)
    }

    @Test
    fun `builder creates failed DNS test`() {
        val test =
            DnsTestBuilder(
                hostname = "nonexistent.invalid",
                dnsServer = "8.8.8.8",
            ).setFailure("NXDOMAIN")
                .build()

        assertTrue(test.isFailed)
        assertEquals("NXDOMAIN", test.failureReason)
    }
}

class CommonDnsServersTest {
    @Test
    fun `popular servers list contains major DNS providers`() {
        val servers = CommonDnsServers.popularServers

        assertTrue(servers.contains("8.8.8.8")) // Google
        assertTrue(servers.contains("1.1.1.1")) // Cloudflare
        assertTrue(servers.contains("9.9.9.9")) // Quad9
        assertTrue(servers.contains("208.67.222.222")) // OpenDNS
    }

    @Test
    fun `get server name for known servers`() {
        assertEquals("Google DNS", CommonDnsServers.getServerName("8.8.8.8"))
        assertEquals("Cloudflare DNS", CommonDnsServers.getServerName("1.1.1.1"))
        assertEquals("Quad9 DNS", CommonDnsServers.getServerName("9.9.9.9"))
        assertEquals("OpenDNS", CommonDnsServers.getServerName("208.67.222.222"))
        assertEquals("Custom DNS", CommonDnsServers.getServerName("192.168.1.1"))
    }
}

class DiagnosticConfigTest {
    @Test
    fun `calculate total tests correctly`() {
        val config =
            DiagnosticConfig(
                pingTargets = listOf("8.8.8.8", "1.1.1.1"), // 2 tests
                tracerouteTargets = listOf("example.com"), // 1 test
                bandwidthServers = listOf("speedtest.example.com"), // 1 test
                dnsHosts = listOf("example.com", "google.com"), // 2 hosts
                dnsServers = listOf("8.8.8.8", "1.1.1.1"), // 2 servers = 4 tests
            )

        assertEquals(8, config.calculateTotalTests())
    }

    @Test
    fun `calculate total tests with no DNS servers`() {
        val config =
            DiagnosticConfig(
                pingTargets = listOf("8.8.8.8"), // 1 test
                dnsHosts = listOf("example.com"), // 1 host
                dnsServers = emptyList(), // 0 tests
            )

        assertEquals(1, config.calculateTotalTests())
    }
}

class DiagnosticProgressTest {
    @Test
    fun `calculate progress percentage`() {
        val progress =
            DiagnosticProgress(
                totalTests = 10,
                completedTests = 7,
                currentPhase = DiagnosticPhase.CONNECTIVITY,
            )

        assertEquals(70.0, progress.progressPercent)
    }

    @Test
    fun `progress description includes phase and counts`() {
        val progress =
            DiagnosticProgress(
                totalTests = 10,
                completedTests = 5,
                currentPhase = DiagnosticPhase.ROUTING,
            )

        val desc = progress.description()
        assertTrue(desc.contains("Analyzing routes"))
        assertTrue(desc.contains("5/10"))
        assertTrue(desc.contains("50.0%"))
    }

    @Test
    fun `handle zero total tests`() {
        val progress =
            DiagnosticProgress(
                totalTests = 0,
                completedTests = 0,
                currentPhase = DiagnosticPhase.COMPLETE,
            )

        assertEquals(0.0, progress.progressPercent)
    }
}

class DiagnosticPhaseTest {
    @Test
    fun `all phases have display names`() {
        DiagnosticPhase.values().forEach { phase ->
            assertNotNull(phase.displayName)
            assertTrue(phase.displayName.isNotBlank())
        }
    }

    @Test
    fun `phase order is correct`() {
        val phases = DiagnosticPhase.values()
        assertEquals(DiagnosticPhase.STARTING, phases[0])
        assertEquals(DiagnosticPhase.CONNECTIVITY, phases[1])
        assertEquals(DiagnosticPhase.ROUTING, phases[2])
        assertEquals(DiagnosticPhase.THROUGHPUT, phases[3])
        assertEquals(DiagnosticPhase.COMPLETE, phases[4])
    }
}
