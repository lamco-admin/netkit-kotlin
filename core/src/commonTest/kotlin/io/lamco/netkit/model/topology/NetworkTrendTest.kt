package io.lamco.netkit.model.topology

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkTrendTest {
    private fun createTestObservation(
        bssid: String = "AA:BB:CC:DD:EE:FF",
        timestampMillis: Long = 1000,
        rssiDbm: Int = -60,
    ) = SignalObservation(bssid, timestampMillis, rssiDbm)

    private fun createTestHistory(
        bssid: String = "AA:BB:CC:DD:EE:FF",
        ssid: String = "TestNetwork",
        observations: List<SignalObservation> = listOf(createTestObservation(bssid = bssid)),
    ) = ApHistory(
        bssid = bssid,
        ssid = ssid,
        firstSeenTimestamp = observations.minOf { it.timestampMillis },
        lastSeenTimestamp = observations.maxOf { it.timestampMillis },
        observations = observations,
    )

    private fun createTestTrend(
        ssid: String = "TestNetwork",
        apHistories: List<ApHistory> = listOf(createTestHistory()),
    ) = NetworkTrend(
        ssid = ssid,
        startTimestamp = apHistories.minOf { it.firstSeenTimestamp },
        endTimestamp = apHistories.maxOf { it.lastSeenTimestamp },
        apHistories = apHistories,
        snapshots = emptyList(),
    )

    @Test
    fun `create trend with valid parameters succeeds`() {
        val trend = createTestTrend()

        assertEquals("TestNetwork", trend.ssid)
        assertEquals(1, trend.uniqueApCount)
    }

    @Test
    fun `blank SSID throws exception`() {
        assertThrows<IllegalArgumentException> {
            NetworkTrend(
                ssid = "",
                startTimestamp = 1000,
                endTimestamp = 2000,
                apHistories = listOf(createTestHistory()),
                snapshots = emptyList(),
            )
        }
    }

    @Test
    fun `zero start timestamp throws exception`() {
        assertThrows<IllegalArgumentException> {
            NetworkTrend(
                ssid = "Test",
                startTimestamp = 0,
                endTimestamp = 2000,
                apHistories = listOf(createTestHistory()),
                snapshots = emptyList(),
            )
        }
    }

    @Test
    fun `end before start throws exception`() {
        assertThrows<IllegalArgumentException> {
            NetworkTrend(
                ssid = "Test",
                startTimestamp = 2000,
                endTimestamp = 1000,
                apHistories = listOf(createTestHistory()),
                snapshots = emptyList(),
            )
        }
    }

    @Test
    fun `mismatched SSID in history throws exception`() {
        val history = createTestHistory(ssid = "DifferentNetwork")

        assertThrows<IllegalArgumentException> {
            NetworkTrend(
                ssid = "TestNetwork",
                startTimestamp = 1000,
                endTimestamp = 2000,
                apHistories = listOf(history),
                snapshots = emptyList(),
            )
        }
    }

    @Test
    fun `durationMillis calculates correct duration`() {
        val trend =
            NetworkTrend(
                ssid = "TestNetwork",
                startTimestamp = 1000,
                endTimestamp = 5000,
                apHistories = listOf(createTestHistory()),
                snapshots = emptyList(),
            )

        assertEquals(4000, trend.durationMillis)
    }

    @Test
    fun `uniqueApCount returns correct count`() {
        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01")
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02")
        val ap3 = createTestHistory(bssid = "AA:BB:CC:DD:EE:03")

        val trend = createTestTrend(apHistories = listOf(ap1, ap2, ap3))

        assertEquals(3, trend.uniqueApCount)
    }

    @Test
    fun `totalObservationCount sums all observations`() {
        val obs1 =
            List(5) { i -> createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = (i + 1) * 1000L) }
        val obs2 =
            List(3) { i -> createTestObservation(bssid = "AA:BB:CC:DD:EE:02", timestampMillis = (i + 1) * 1000L) }

        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = obs1)
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = obs2)

        val trend = createTestTrend(apHistories = listOf(ap1, ap2))

        assertEquals(8, trend.totalObservationCount)
    }

    @Test
    fun `averageNetworkRssiDbm calculates average across APs`() {
        val obs1 = List(3) { createTestObservation(bssid = "AA:BB:CC:DD:EE:01", rssiDbm = -50) }
        val obs2 = List(3) { createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -70) }

        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = obs1)
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = obs2)

        val trend = createTestTrend(apHistories = listOf(ap1, ap2))

        assertEquals(-60.0, trend.averageNetworkRssiDbm)
    }

    @Test
    fun `peakNetworkRssiDbm returns maximum across all APs`() {
        val obs1 =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", rssiDbm = -50),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", rssiDbm = -60),
            )
        val obs2 =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -40),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -70),
            )

        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = obs1)
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = obs2)

        val trend = createTestTrend(apHistories = listOf(ap1, ap2))

        assertEquals(-40, trend.peakNetworkRssiDbm)
    }

    @Test
    fun `worstNetworkRssiDbm returns minimum across all APs`() {
        val obs1 =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", rssiDbm = -50),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", rssiDbm = -60),
            )
        val obs2 =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -40),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -80),
            )

        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = obs1)
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = obs2)

        val trend = createTestTrend(apHistories = listOf(ap1, ap2))

        assertEquals(-80, trend.worstNetworkRssiDbm)
    }

    @Test
    fun `networkStability returns correct category`() {
        // Very stable - all observations at same RSSI
        val obs = List(10) { createTestObservation(rssiDbm = -60) }
        val ap = createTestHistory(observations = obs)
        val trend = createTestTrend(apHistories = listOf(ap))

        assertEquals(SignalStability.VERY_STABLE, trend.networkStability)
    }

    @Test
    fun `mostStableAp returns AP with lowest std dev`() {
        val stableObs = List(5) { createTestObservation(bssid = "AA:BB:CC:DD:EE:01", rssiDbm = -60) }
        val unstableObs =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -40),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -80),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -45),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -85),
            )

        val stableAp = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = stableObs)
        val unstableAp = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = unstableObs)

        val trend = createTestTrend(apHistories = listOf(stableAp, unstableAp))

        assertEquals("AA:BB:CC:DD:EE:01", trend.mostStableAp?.bssid)
    }

    @Test
    fun `leastStableAp returns AP with highest std dev`() {
        val stableObs = List(5) { createTestObservation(bssid = "AA:BB:CC:DD:EE:01", rssiDbm = -60) }
        val unstableObs =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -40),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -80),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -45),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", rssiDbm = -85),
            )

        val stableAp = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = stableObs)
        val unstableAp = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = unstableObs)

        val trend = createTestTrend(apHistories = listOf(stableAp, unstableAp))

        assertEquals("AA:BB:CC:DD:EE:02", trend.leastStableAp?.bssid)
    }

    @Test
    fun `currentlyVisibleAps filters by visibility threshold`() {
        val recentAp =
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:01",
                ssid = "TestNetwork",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 95_000,
                observations = listOf(createTestObservation(bssid = "AA:BB:CC:DD:EE:01")),
            )
        val oldAp =
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:02",
                ssid = "TestNetwork",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 10_000,
                observations = listOf(createTestObservation(bssid = "AA:BB:CC:DD:EE:02")),
            )

        val trend = createTestTrend(apHistories = listOf(recentAp, oldAp))

        val visible =
            trend.currentlyVisibleAps(
                thresholdMillis = 60_000,
                currentTimeMillis = 100_000,
            )

        assertEquals(1, visible.size)
        assertEquals("AA:BB:CC:DD:EE:01", visible.first().bssid)
    }

    @Test
    fun `recentlyDisappearedAps finds APs that disappeared in window`() {
        val disappeared =
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:01",
                ssid = "TestNetwork",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 150_000,
                observations = listOf(createTestObservation(bssid = "AA:BB:CC:DD:EE:01")),
            )
        val stillVisible =
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:02",
                ssid = "TestNetwork",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 295_000,
                observations = listOf(createTestObservation(bssid = "AA:BB:CC:DD:EE:02")),
            )

        val trend = createTestTrend(apHistories = listOf(disappeared, stillVisible))

        val recent =
            trend.recentlyDisappearedAps(
                windowMillis = 300_000,
                currentTimeMillis = 300_000,
            )

        assertEquals(1, recent.size)
        assertEquals("AA:BB:CC:DD:EE:01", recent.first().bssid)
    }

    @Test
    fun `degradingAps returns APs with degrading signal trend`() {
        val degradingObs =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 1000, rssiDbm = -50),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 2000, rssiDbm = -55),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 3000, rssiDbm = -60),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 4000, rssiDbm = -65),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 5000, rssiDbm = -70),
            )
        val stableObs =
            List(5) { i ->
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", timestampMillis = (i + 1) * 1000L, rssiDbm = -60)
            }

        val degradingAp = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = degradingObs)
        val stableAp = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = stableObs)

        val trend = createTestTrend(apHistories = listOf(degradingAp, stableAp))

        val degrading = trend.degradingAps(windowMillis = 10_000, currentTimeMillis = 10_000)

        assertEquals(1, degrading.size)
        assertEquals("AA:BB:CC:DD:EE:01", degrading.first().bssid)
    }

    @Test
    fun `improvingAps returns APs with improving signal trend`() {
        val improvingObs =
            listOf(
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 1000, rssiDbm = -70),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 2000, rssiDbm = -65),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 3000, rssiDbm = -60),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 4000, rssiDbm = -55),
                createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 5000, rssiDbm = -50),
            )
        val stableObs =
            List(5) { i ->
                createTestObservation(bssid = "AA:BB:CC:DD:EE:02", timestampMillis = (i + 1) * 1000L, rssiDbm = -60)
            }

        val improvingAp = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = improvingObs)
        val stableAp = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = stableObs)

        val trend = createTestTrend(apHistories = listOf(improvingAp, stableAp))

        val improving = trend.improvingAps(windowMillis = 10_000, currentTimeMillis = 10_000)

        assertEquals(1, improving.size)
        assertEquals("AA:BB:CC:DD:EE:01", improving.first().bssid)
    }

    @Test
    fun `healthScore calculates correctly for excellent network`() {
        // Excellent signal, very stable
        val obs = List(10) { createTestObservation(rssiDbm = -45) }
        val ap = createTestHistory(observations = obs)
        val trend = createTestTrend(apHistories = listOf(ap))

        val score = trend.healthScore

        assertTrue(score >= 70) // Should be high
    }

    @Test
    fun `healthScore calculates correctly for poor network`() {
        // Poor signal, very unstable
        val obs =
            listOf(
                createTestObservation(rssiDbm = -85),
                createTestObservation(rssiDbm = -40),
                createTestObservation(rssiDbm = -90),
                createTestObservation(rssiDbm = -45),
            )
        val ap = createTestHistory(observations = obs)
        val trend = createTestTrend(apHistories = listOf(ap))

        val score = trend.healthScore

        assertTrue(score < 50) // Should be low
    }

    @Test
    fun `health returns EXCELLENT for high score`() {
        val obs = List(10) { createTestObservation(rssiDbm = -45) }
        val ap = createTestHistory(observations = obs)
        val trend = createTestTrend(apHistories = listOf(ap))

        val health = trend.health

        assertTrue(health == NetworkHealth.EXCELLENT || health == NetworkHealth.GOOD)
    }

    @Test
    fun `recentNetworkTrend returns IMPROVING when recent signal better`() {
        val oldObs =
            List(5) { i ->
                createTestObservation(timestampMillis = (i + 1) * 1000L, rssiDbm = -70)
            }
        val recentObs =
            List(5) { i ->
                createTestObservation(timestampMillis = (100_000 + i * 1000L), rssiDbm = -50)
            }

        val ap = createTestHistory(observations = oldObs + recentObs)
        val trend = createTestTrend(apHistories = listOf(ap))

        val direction =
            trend.recentNetworkTrend(
                windowMillis = 50_000,
                currentTimeMillis = 150_000,
            )

        assertTrue(direction.isPositive)
    }

    @Test
    fun `recentNetworkTrend returns DEGRADING when recent signal worse`() {
        val oldObs =
            List(5) { i ->
                createTestObservation(timestampMillis = (i + 1) * 1000L, rssiDbm = -50)
            }
        val recentObs =
            List(5) { i ->
                createTestObservation(timestampMillis = (100_000 + i * 1000L), rssiDbm = -70)
            }

        val ap = createTestHistory(observations = oldObs + recentObs)
        val trend = createTestTrend(apHistories = listOf(ap))

        val direction =
            trend.recentNetworkTrend(
                windowMillis = 50_000,
                currentTimeMillis = 150_000,
            )

        assertTrue(direction.indicatesProblem)
    }

    @Test
    fun `apTrends returns summary for all APs`() {
        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01")
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02")

        val trend = createTestTrend(apHistories = listOf(ap1, ap2))

        val trends = trend.apTrends(windowMillis = 10_000, currentTimeMillis = 10_000)

        assertEquals(2, trends.size)
        assertTrue(trends.any { it.bssid == "AA:BB:CC:DD:EE:01" })
        assertTrue(trends.any { it.bssid == "AA:BB:CC:DD:EE:02" })
    }

    @Test
    fun `predictPerformance returns prediction with confidence`() {
        val obs =
            List(100) { i ->
                createTestObservation(timestampMillis = (i + 1) * 1000L, rssiDbm = -60)
            }
        val ap = createTestHistory(observations = obs)
        val trend = createTestTrend(apHistories = listOf(ap))

        val prediction = trend.predictPerformance(horizonMillis = 300_000)

        assertNotNull(prediction)
        assertTrue(prediction.confidence == PredictionConfidence.HIGH)
        assertTrue(prediction.isReliable)
    }

    @Test
    fun `predictPerformance has low confidence with few observations`() {
        val obs =
            List(10) { i ->
                createTestObservation(timestampMillis = (i + 1) * 1000L, rssiDbm = -60)
            }
        val ap = createTestHistory(observations = obs)
        val trend = createTestTrend(apHistories = listOf(ap))

        val prediction = trend.predictPerformance(horizonMillis = 300_000)

        assertEquals(PredictionConfidence.LOW, prediction.confidence)
        assertFalse(prediction.isReliable)
    }

    @Test
    fun `create factory method constructs trend from histories`() {
        val obs1 = createTestObservation(bssid = "AA:BB:CC:DD:EE:01", timestampMillis = 1000)
        val obs2 = createTestObservation(bssid = "AA:BB:CC:DD:EE:02", timestampMillis = 5000)

        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01", observations = listOf(obs1))
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02", observations = listOf(obs2))

        val trend = NetworkTrend.create("TestNetwork", listOf(ap1, ap2))

        assertEquals("TestNetwork", trend.ssid)
        assertEquals(2, trend.uniqueApCount)
        assertEquals(1000, trend.startTimestamp)
        assertEquals(5000, trend.endTimestamp)
    }

    @Test
    fun `create factory method throws for empty histories`() {
        assertThrows<IllegalArgumentException> {
            NetworkTrend.create("TestNetwork", emptyList())
        }
    }

    @Test
    fun `summary generates readable description`() {
        val ap1 = createTestHistory(bssid = "AA:BB:CC:DD:EE:01")
        val ap2 = createTestHistory(bssid = "AA:BB:CC:DD:EE:02")

        val trend = createTestTrend(apHistories = listOf(ap1, ap2))

        val summary = trend.summary

        assertTrue(summary.contains("TestNetwork"))
        assertTrue(summary.contains("2 APs"))
    }
}
