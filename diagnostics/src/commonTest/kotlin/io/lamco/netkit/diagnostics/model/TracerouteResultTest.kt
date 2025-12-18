package io.lamco.netkit.diagnostics.model

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class TracerouteResultTest {
    // ===== TracerouteHop Tests =====

    @Test
    fun `create valid traceroute hop`() {
        val hop =
            TracerouteHop(
                hopNumber = 1,
                ipAddress = "192.168.1.1",
                hostname = "gateway.local",
                rtt = 2.milliseconds,
                probesSent = 3,
                probesReceived = 3,
            )

        assertEquals(1, hop.hopNumber)
        assertEquals("192.168.1.1", hop.ipAddress)
        assertEquals("gateway.local", hop.hostname)
        assertEquals(2.milliseconds, hop.rtt)
        assertFalse(hop.isTimeout)
        assertTrue(hop.isFullyResponsive)
        assertEquals(0.0, hop.packetLossRate)
    }

    @Test
    fun `traceroute hop timeout has no RTT`() {
        val hop = TracerouteHop.timeout(hopNumber = 5, ipAddress = "*")

        assertEquals(5, hop.hopNumber)
        assertEquals("*", hop.ipAddress)
        assertNull(hop.rtt)
        assertTrue(hop.isTimeout)
        assertFalse(hop.isFullyResponsive)
        assertEquals(100.0, hop.packetLossRate)
    }

    @Test
    fun `reject negative hop number`() {
        assertFailsWith<IllegalArgumentException> {
            TracerouteHop(
                hopNumber = 0,
                ipAddress = "192.168.1.1",
                rtt = 2.milliseconds,
            )
        }
    }

    @Test
    fun `traceroute hop display name prefers hostname`() {
        val hop =
            TracerouteHop(
                hopNumber = 1,
                ipAddress = "192.168.1.1",
                hostname = "gateway.local",
                rtt = 2.milliseconds,
            )

        assertEquals("gateway.local", hop.displayName)
    }

    @Test
    fun `traceroute hop display name falls back to IP`() {
        val hop =
            TracerouteHop(
                hopNumber = 1,
                ipAddress = "192.168.1.1",
                hostname = null,
                rtt = 2.milliseconds,
            )

        assertEquals("192.168.1.1", hop.displayName)
    }

    // ===== TracerouteResult Tests =====

    @Test
    fun `create valid traceroute result`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", "gateway", 1.milliseconds),
                TracerouteHop(2, "10.0.0.1", "isp-router", 5.milliseconds),
                TracerouteHop(3, "93.184.216.34", "example.com", 12.milliseconds),
            )

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                resolvedIp = "93.184.216.34",
                hops = hops,
                maxHops = 30,
                completed = true,
            )

        assertEquals("example.com", traceroute.targetHost)
        assertEquals("93.184.216.34", traceroute.resolvedIp)
        assertEquals(3, traceroute.totalHops)
        assertTrue(traceroute.completed)
        assertTrue(traceroute.isSuccessful)
    }

    @Test
    fun `reject blank target host`() {
        assertFailsWith<IllegalArgumentException> {
            TracerouteResult(
                targetHost = "",
                hops = emptyList(),
                completed = false,
            )
        }
    }

    @Test
    fun `reject invalid max hops`() {
        assertFailsWith<IllegalArgumentException> {
            TracerouteResult(
                targetHost = "example.com",
                hops = emptyList(),
                maxHops = 0,
                completed = false,
            )
        }
    }

    @Test
    fun `reject out of order hops`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", rtt = 1.milliseconds),
                TracerouteHop(3, "10.0.0.1", rtt = 5.milliseconds), // Skip hop 2
                TracerouteHop(2, "93.184.216.34", rtt = 12.milliseconds), // Out of order
            )

        assertFailsWith<IllegalArgumentException> {
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = true,
            )
        }
    }

    @Test
    fun `first and last hop properties`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", rtt = 1.milliseconds),
                TracerouteHop(2, "10.0.0.1", rtt = 5.milliseconds),
                TracerouteHop(3, "93.184.216.34", rtt = 12.milliseconds),
            )

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = true,
            )

        assertEquals(hops[0], traceroute.firstHop)
        assertEquals(hops[2], traceroute.lastHop)
        assertEquals(1.milliseconds, traceroute.firstHopLatency)
        assertEquals(12.milliseconds, traceroute.totalLatency)
    }

    @Test
    fun `timeout rate calculation`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", rtt = 1.milliseconds),
                TracerouteHop.timeout(2),
                TracerouteHop(3, "10.0.0.1", rtt = 5.milliseconds),
                TracerouteHop.timeout(4),
            )

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = false,
            )

        assertEquals(4, traceroute.totalHops)
        assertEquals(2, traceroute.timedOutHops.size)
        assertEquals(2, traceroute.responsiveHops.size)
        assertEquals(50.0, traceroute.timeoutRate)
    }

    @Test
    fun `find bottlenecks with significant latency increase`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", rtt = 2.milliseconds),
                TracerouteHop(2, "10.0.0.1", rtt = 5.milliseconds),
                TracerouteHop(3, "93.184.216.1", rtt = 80.milliseconds), // Bottleneck
                TracerouteHop(4, "93.184.216.34", rtt = 85.milliseconds),
            )

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = true,
            )

        val bottlenecks = traceroute.findBottlenecks(threshold = 50.milliseconds)
        assertEquals(listOf(3), bottlenecks)
    }

    @Test
    fun `healthy gateway check`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", rtt = 2.milliseconds),
                TracerouteHop(2, "10.0.0.1", rtt = 5.milliseconds),
            )

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = false,
            )

        assertTrue(traceroute.hasHealthyGateway(maxGatewayLatency = 10.milliseconds))
        assertFalse(traceroute.hasHealthyGateway(maxGatewayLatency = 1.milliseconds))
    }

    @Test
    fun `route quality EXCELLENT for few hops and low timeouts`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", rtt = 1.milliseconds),
                TracerouteHop(2, "10.0.0.1", rtt = 5.milliseconds),
                TracerouteHop(3, "93.184.216.34", rtt = 12.milliseconds),
            )

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = true,
            )

        assertEquals(RouteQuality.EXCELLENT, traceroute.routeQuality)
    }

    @Test
    fun `route quality POOR for many hops`() {
        val hops =
            (1..18).map { hopNum ->
                TracerouteHop(hopNum, "10.0.0.$hopNum", rtt = (hopNum * 2).milliseconds)
            }

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = true,
            )

        assertTrue(traceroute.routeQuality == RouteQuality.POOR || traceroute.routeQuality == RouteQuality.CRITICAL)
    }

    @Test
    fun `route quality FAILED when not successful`() {
        val traceroute =
            TracerouteResult.failed(
                targetHost = "unreachable.host",
                reason = "Host unreachable",
            )

        assertEquals(RouteQuality.FAILED, traceroute.routeQuality)
    }

    @Test
    fun `failed factory creates failed traceroute`() {
        val traceroute =
            TracerouteResult.failed(
                targetHost = "unreachable.host",
                reason = "Network unreachable",
            )

        assertEquals("unreachable.host", traceroute.targetHost)
        assertTrue(traceroute.hops.isEmpty())
        assertFalse(traceroute.completed)
        assertEquals("Network unreachable", traceroute.failureReason)
        assertFalse(traceroute.isSuccessful)
    }

    @Test
    fun `summary includes key metrics`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", rtt = 1.milliseconds),
                TracerouteHop(2, "10.0.0.1", rtt = 5.milliseconds),
                TracerouteHop(3, "93.184.216.34", rtt = 12.milliseconds),
            )

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = true,
            )

        val summary = traceroute.summary()
        assertTrue(summary.contains("example.com"))
        assertTrue(summary.contains("3"))
        assertTrue(summary.contains("true") || summary.contains("Completed"))
    }

    @Test
    fun `path report shows all hops`() {
        val hops =
            listOf(
                TracerouteHop(1, "192.168.1.1", "gateway", 1.milliseconds),
                TracerouteHop.timeout(2),
                TracerouteHop(3, "93.184.216.34", "example.com", 12.milliseconds),
            )

        val traceroute =
            TracerouteResult(
                targetHost = "example.com",
                hops = hops,
                completed = true,
            )

        val report = traceroute.pathReport()
        assertTrue(report.contains("gateway"))
        assertTrue(report.contains("timeout"))
        assertTrue(report.contains("example.com"))
    }
}
