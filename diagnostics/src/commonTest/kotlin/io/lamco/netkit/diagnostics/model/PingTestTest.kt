package io.lamco.netkit.diagnostics.model

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class PingTestTest {
    // ===== Construction and Validation =====

    @Test
    fun `create valid ping test with all RTT metrics`() {
        val ping =
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

        assertEquals("8.8.8.8", ping.targetHost)
        assertEquals(10, ping.packetsTransmitted)
        assertEquals(10, ping.packetsReceived)
        assertEquals(0.0, ping.packetLossPercent)
        assertEquals(5.milliseconds, ping.minRtt)
        assertEquals(8.milliseconds, ping.avgRtt)
        assertEquals(12.milliseconds, ping.maxRtt)
        assertEquals(2.milliseconds, ping.stdDevRtt)
    }

    @Test
    fun `reject blank target host`() {
        assertFailsWith<IllegalArgumentException> {
            PingTest(
                targetHost = "",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = null,
                avgRtt = null,
                maxRtt = null,
                stdDevRtt = null,
            )
        }
    }

    @Test
    fun `reject negative packets transmitted`() {
        assertFailsWith<IllegalArgumentException> {
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = -1,
                packetsReceived = 0,
                packetLossPercent = 0.0,
                minRtt = null,
                avgRtt = null,
                maxRtt = null,
                stdDevRtt = null,
            )
        }
    }

    @Test
    fun `reject packets received exceeding transmitted`() {
        assertFailsWith<IllegalArgumentException> {
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 5,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = null,
                avgRtt = null,
                maxRtt = null,
                stdDevRtt = null,
            )
        }
    }

    @Test
    fun `reject packet loss outside 0-100 range`() {
        assertFailsWith<IllegalArgumentException> {
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 0,
                packetLossPercent = 101.0,
                minRtt = null,
                avgRtt = null,
                maxRtt = null,
                stdDevRtt = null,
            )
        }
    }

    @Test
    fun `reject minRtt greater than maxRtt`() {
        assertFailsWith<IllegalArgumentException> {
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 20.milliseconds,
                avgRtt = 15.milliseconds,
                maxRtt = 10.milliseconds,
                stdDevRtt = 2.milliseconds,
            )
        }
    }

    @Test
    fun `reject avgRtt outside min-max range`() {
        assertFailsWith<IllegalArgumentException> {
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 5.milliseconds,
                avgRtt = 25.milliseconds,
                maxRtt = 10.milliseconds,
                stdDevRtt = 2.milliseconds,
            )
        }
    }

    @Test
    fun `require avgRtt if other RTT values provided`() {
        assertFailsWith<IllegalArgumentException> {
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 5.milliseconds,
                avgRtt = null,
                maxRtt = 10.milliseconds,
                stdDevRtt = null,
            )
        }
    }

    // ===== Success/Failure Status =====

    @Test
    fun `ping is successful when packets received and no failure reason`() {
        val ping =
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

        assertTrue(ping.isSuccessful)
        assertFalse(ping.isFailed)
    }

    @Test
    fun `ping fails when no packets received`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 0,
                packetLossPercent = 100.0,
                minRtt = null,
                avgRtt = null,
                maxRtt = null,
                stdDevRtt = null,
            )

        assertFalse(ping.isSuccessful)
        assertTrue(ping.isFailed)
    }

    @Test
    fun `ping fails when failure reason provided`() {
        val ping =
            PingTest(
                targetHost = "unreachable.host",
                packetsTransmitted = 10,
                packetsReceived = 0,
                packetLossPercent = 100.0,
                minRtt = null,
                avgRtt = null,
                maxRtt = null,
                stdDevRtt = null,
                failureReason = "Host unreachable",
            )

        assertFalse(ping.isSuccessful)
        assertTrue(ping.isFailed)
        assertEquals("Host unreachable", ping.failureReason)
    }

    // ===== Quality Assessment =====

    @Test
    fun `latency quality EXCELLENT for under 20ms`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 8.milliseconds,
                avgRtt = 12.milliseconds,
                maxRtt = 18.milliseconds,
                stdDevRtt = 3.milliseconds,
            )

        assertEquals(LatencyQuality.EXCELLENT, ping.latencyQuality)
    }

    @Test
    fun `latency quality GOOD for 20-49ms`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 20.milliseconds,
                avgRtt = 35.milliseconds,
                maxRtt = 45.milliseconds,
                stdDevRtt = 5.milliseconds,
            )

        assertEquals(LatencyQuality.GOOD, ping.latencyQuality)
    }

    @Test
    fun `latency quality CRITICAL for 200ms+`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 180.milliseconds,
                avgRtt = 220.milliseconds,
                maxRtt = 280.milliseconds,
                stdDevRtt = 30.milliseconds,
            )

        assertEquals(LatencyQuality.CRITICAL, ping.latencyQuality)
    }

    @Test
    fun `packet loss quality NONE for 0 percent`() {
        val ping =
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

        assertEquals(PacketLossQuality.NONE, ping.packetLossQuality)
    }

    @Test
    fun `packet loss quality SEVERE for 10 percent+`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 8,
                packetLossPercent = 20.0,
                minRtt = 5.milliseconds,
                avgRtt = 8.milliseconds,
                maxRtt = 12.milliseconds,
                stdDevRtt = 2.milliseconds,
            )

        assertEquals(PacketLossQuality.SEVERE, ping.packetLossQuality)
    }

    @Test
    fun `jitter quality EXCELLENT for under 5ms`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 8.milliseconds,
                avgRtt = 10.milliseconds,
                maxRtt = 12.milliseconds,
                stdDevRtt = 2.milliseconds,
            )

        assertEquals(JitterQuality.EXCELLENT, ping.jitterQuality)
        assertEquals(2.milliseconds, ping.jitter)
    }

    @Test
    fun `overall quality EXCELLENT when all metrics excellent`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 8.milliseconds,
                avgRtt = 12.milliseconds,
                maxRtt = 15.milliseconds,
                stdDevRtt = 2.milliseconds,
            )

        assertEquals(PingQuality.EXCELLENT, ping.overallQuality)
    }

    @Test
    fun `overall quality limited by worst metric`() {
        // Excellent latency but high packet loss
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 5,
                packetLossPercent = 50.0,
                minRtt = 8.milliseconds,
                avgRtt = 12.milliseconds,
                maxRtt = 15.milliseconds,
                stdDevRtt = 2.milliseconds,
            )

        // Should be POOR or CRITICAL due to severe packet loss
        assertTrue(ping.overallQuality == PingQuality.POOR || ping.overallQuality == PingQuality.CRITICAL)
    }

    @Test
    fun `overall quality FAILED when test failed`() {
        val ping =
            PingTest.failed(
                targetHost = "unreachable.host",
                packetsTransmitted = 10,
                reason = "Network unreachable",
            )

        assertEquals(PingQuality.FAILED, ping.overallQuality)
    }

    // ===== Helper Properties =====

    @Test
    fun `response rate is complement of packet loss`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 7,
                packetLossPercent = 30.0,
                minRtt = 5.milliseconds,
                avgRtt = 8.milliseconds,
                maxRtt = 12.milliseconds,
                stdDevRtt = 2.milliseconds,
            )

        assertEquals(70.0, ping.responseRate)
    }

    // ===== Quality Standards =====

    @Test
    fun `meets quality standards with good metrics`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 8.milliseconds,
                avgRtt = 15.milliseconds,
                maxRtt = 25.milliseconds,
                stdDevRtt = 5.milliseconds,
            )

        assertTrue(ping.meetsQualityStandards())
    }

    @Test
    fun `fails quality standards with high latency`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 10,
                packetLossPercent = 0.0,
                minRtt = 100.milliseconds,
                avgRtt = 150.milliseconds,
                maxRtt = 200.milliseconds,
                stdDevRtt = 20.milliseconds,
            )

        assertFalse(ping.meetsQualityStandards(maxLatency = 100.milliseconds))
    }

    @Test
    fun `fails quality standards with high packet loss`() {
        val ping =
            PingTest(
                targetHost = "8.8.8.8",
                packetsTransmitted = 10,
                packetsReceived = 5,
                packetLossPercent = 50.0,
                minRtt = 8.milliseconds,
                avgRtt = 15.milliseconds,
                maxRtt = 25.milliseconds,
                stdDevRtt = 5.milliseconds,
            )

        assertFalse(ping.meetsQualityStandards(maxPacketLoss = 5.0))
    }

    // ===== Factory Methods =====

    @Test
    fun `failed factory creates failed ping test`() {
        val ping =
            PingTest.failed(
                targetHost = "unreachable.host",
                packetsTransmitted = 5,
                reason = "Network unreachable",
            )

        assertEquals("unreachable.host", ping.targetHost)
        assertEquals(5, ping.packetsTransmitted)
        assertEquals(0, ping.packetsReceived)
        assertEquals(100.0, ping.packetLossPercent)
        assertNull(ping.avgRtt)
        assertEquals("Network unreachable", ping.failureReason)
        assertTrue(ping.isFailed)
    }

    // ===== Summary Generation =====

    @Test
    fun `summary includes key metrics`() {
        val ping =
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

        val summary = ping.summary()
        assertTrue(summary.contains("8.8.8.8"))
        assertTrue(summary.contains("10/10"))
        assertTrue(summary.contains("0.0%"))
        assertTrue(summary.contains("min="))
        assertTrue(summary.contains("avg="))
        assertTrue(summary.contains("max="))
    }

    @Test
    fun `summary includes failure reason when failed`() {
        val ping =
            PingTest.failed(
                targetHost = "unreachable.host",
                reason = "Destination host unreachable",
            )

        val summary = ping.summary()
        assertTrue(summary.contains("Destination host unreachable"))
    }
}
