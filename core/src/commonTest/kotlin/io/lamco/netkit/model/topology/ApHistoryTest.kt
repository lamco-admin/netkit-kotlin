package io.lamco.netkit.model.topology

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApHistoryTest {
    private fun createTestObservation(
        bssid: String = "AA:BB:CC:DD:EE:FF",
        timestampMillis: Long = 1000,
        rssiDbm: Int = -60,
    ) = SignalObservation(
        bssid = bssid,
        timestampMillis = timestampMillis,
        rssiDbm = rssiDbm,
    )

    private fun createTestHistory(
        bssid: String = "AA:BB:CC:DD:EE:FF",
        ssid: String = "TestNetwork",
        observations: List<SignalObservation> = listOf(createTestObservation()),
    ) = ApHistory(
        bssid = bssid,
        ssid = ssid,
        firstSeenTimestamp = observations.minOf { it.timestampMillis },
        lastSeenTimestamp = observations.maxOf { it.timestampMillis },
        observations = observations,
    )

    @Test
    fun `create history with valid parameters succeeds`() {
        val history = createTestHistory()

        assertEquals("AA:BB:CC:DD:EE:FF", history.bssid)
        assertEquals("TestNetwork", history.ssid)
        assertEquals(1, history.observationCount)
    }

    @Test
    fun `blank BSSID throws exception`() {
        assertThrows<IllegalArgumentException> {
            ApHistory(
                bssid = "",
                ssid = "Test",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 2000,
                observations = listOf(createTestObservation()),
            )
        }
    }

    @Test
    fun `blank SSID throws exception`() {
        assertThrows<IllegalArgumentException> {
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 2000,
                observations = listOf(createTestObservation()),
            )
        }
    }

    @Test
    fun `zero first seen timestamp throws exception`() {
        assertThrows<IllegalArgumentException> {
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "Test",
                firstSeenTimestamp = 0,
                lastSeenTimestamp = 1000,
                observations = listOf(createTestObservation()),
            )
        }
    }

    @Test
    fun `last seen before first seen throws exception`() {
        assertThrows<IllegalArgumentException> {
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "Test",
                firstSeenTimestamp = 2000,
                lastSeenTimestamp = 1000,
                observations = listOf(createTestObservation()),
            )
        }
    }

    @Test
    fun `empty observations throws exception`() {
        assertThrows<IllegalArgumentException> {
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "Test",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 2000,
                observations = emptyList(),
            )
        }
    }

    @Test
    fun `trackingDurationMillis calculates correct duration`() {
        val history =
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "Test",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 5000,
                observations = listOf(createTestObservation(timestampMillis = 1000)),
            )

        assertEquals(4000, history.trackingDurationMillis)
    }

    @Test
    fun `observationCount returns correct count`() {
        val obs1 = createTestObservation(timestampMillis = 1000, rssiDbm = -60)
        val obs2 = createTestObservation(timestampMillis = 2000, rssiDbm = -65)
        val obs3 = createTestObservation(timestampMillis = 3000, rssiDbm = -70)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        assertEquals(3, history.observationCount)
    }

    @Test
    fun `averageObservationIntervalMillis calculates correctly`() {
        val obs1 = createTestObservation(timestampMillis = 1000)
        val obs2 = createTestObservation(timestampMillis = 3000)
        val obs3 = createTestObservation(timestampMillis = 5000)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        assertEquals(2000, history.averageObservationIntervalMillis)
    }

    @Test
    fun `averageObservationIntervalMillis returns 0 for single observation`() {
        val history =
            createTestHistory(
                observations = listOf(createTestObservation()),
            )

        assertEquals(0, history.averageObservationIntervalMillis)
    }

    @Test
    fun `isCurrentlyVisible returns true when recently seen`() {
        val history =
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "Test",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 50_000,
                observations = listOf(createTestObservation()),
            )

        assertTrue(history.isCurrentlyVisible(thresholdMillis = 60_000, currentTimeMillis = 100_000))
    }

    @Test
    fun `isCurrentlyVisible returns false when not recently seen`() {
        val history =
            ApHistory(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "Test",
                firstSeenTimestamp = 1000,
                lastSeenTimestamp = 10_000,
                observations = listOf(createTestObservation()),
            )

        assertFalse(history.isCurrentlyVisible(thresholdMillis = 60_000, currentTimeMillis = 100_000))
    }

    @Test
    fun `averageRssiDbm calculates correct average`() {
        val obs1 = createTestObservation(rssiDbm = -50)
        val obs2 = createTestObservation(rssiDbm = -60)
        val obs3 = createTestObservation(rssiDbm = -70)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        assertEquals(-60.0, history.averageRssiDbm)
    }

    @Test
    fun `medianRssiDbm returns correct median`() {
        val obs1 = createTestObservation(rssiDbm = -50)
        val obs2 = createTestObservation(rssiDbm = -60)
        val obs3 = createTestObservation(rssiDbm = -70)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        assertEquals(-60, history.medianRssiDbm)
    }

    @Test
    fun `peakRssiDbm returns maximum value`() {
        val obs1 = createTestObservation(rssiDbm = -50)
        val obs2 = createTestObservation(rssiDbm = -60)
        val obs3 = createTestObservation(rssiDbm = -40)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        assertEquals(-40, history.peakRssiDbm)
    }

    @Test
    fun `minRssiDbm returns minimum value`() {
        val obs1 = createTestObservation(rssiDbm = -50)
        val obs2 = createTestObservation(rssiDbm = -60)
        val obs3 = createTestObservation(rssiDbm = -70)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        assertEquals(-70, history.minRssiDbm)
    }

    @Test
    fun `rssiRangeDb calculates correct range`() {
        val obs1 = createTestObservation(rssiDbm = -40)
        val obs2 = createTestObservation(rssiDbm = -70)

        val history = createTestHistory(observations = listOf(obs1, obs2))

        assertEquals(30, history.rssiRangeDb)
    }

    @Test
    fun `rssiStandardDeviation calculates correctly for stable signal`() {
        val obs1 = createTestObservation(rssiDbm = -60)
        val obs2 = createTestObservation(rssiDbm = -60)
        val obs3 = createTestObservation(rssiDbm = -60)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        assertTrue(history.rssiStandardDeviation < 0.1)
    }

    @Test
    fun `rssiStandardDeviation is higher for variable signal`() {
        val obs1 = createTestObservation(rssiDbm = -40)
        val obs2 = createTestObservation(rssiDbm = -60)
        val obs3 = createTestObservation(rssiDbm = -80)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        assertTrue(history.rssiStandardDeviation > 10.0)
    }

    @Test
    fun `signalStability returns VERY_STABLE for low std dev`() {
        val obs = List(10) { createTestObservation(rssiDbm = -60) }
        val history = createTestHistory(observations = obs)

        assertEquals(SignalStability.VERY_STABLE, history.signalStability)
    }

    @Test
    fun `signalStability returns VERY_UNSTABLE for high std dev`() {
        val obs =
            listOf(
                createTestObservation(rssiDbm = -40),
                createTestObservation(rssiDbm = -90),
                createTestObservation(rssiDbm = -45),
                createTestObservation(rssiDbm = -85),
            )
        val history = createTestHistory(observations = obs)

        assertEquals(SignalStability.VERY_UNSTABLE, history.signalStability)
    }

    @Test
    fun `recentObservations filters by time window`() {
        val obs1 = createTestObservation(timestampMillis = 1000)
        val obs2 = createTestObservation(timestampMillis = 100_000)
        val obs3 = createTestObservation(timestampMillis = 200_000)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        val recent =
            history.recentObservations(
                windowMillis = 150_000,
                currentTimeMillis = 250_000,
            )

        assertEquals(2, recent.size)
        assertTrue(recent.any { it.timestampMillis == 100_000L })
        assertTrue(recent.any { it.timestampMillis == 200_000L })
    }

    @Test
    fun `recentAverageRssi calculates average for recent observations`() {
        val obs1 = createTestObservation(timestampMillis = 1000, rssiDbm = -80)
        val obs2 = createTestObservation(timestampMillis = 100_000, rssiDbm = -50)
        val obs3 = createTestObservation(timestampMillis = 200_000, rssiDbm = -60)

        val history = createTestHistory(observations = listOf(obs1, obs2, obs3))

        val recentAvg =
            history.recentAverageRssi(
                windowMillis = 150_000,
                currentTimeMillis = 250_000,
            )

        assertEquals(-55.0, recentAvg)
    }

    @Test
    fun `recentAverageRssi returns null when no recent observations`() {
        val obs = createTestObservation(timestampMillis = 1000)
        val history = createTestHistory(observations = listOf(obs))

        val recentAvg =
            history.recentAverageRssi(
                windowMillis = 10_000,
                currentTimeMillis = 100_000,
            )

        assertNull(recentAvg)
    }

    @Test
    fun `signalTrend returns IMPROVING for increasing signal`() {
        val observations =
            listOf(
                createTestObservation(timestampMillis = 1000, rssiDbm = -70),
                createTestObservation(timestampMillis = 2000, rssiDbm = -65),
                createTestObservation(timestampMillis = 3000, rssiDbm = -60),
                createTestObservation(timestampMillis = 4000, rssiDbm = -55),
                createTestObservation(timestampMillis = 5000, rssiDbm = -50),
            )
        val history = createTestHistory(observations = observations)

        val trend = history.signalTrend(windowMillis = 10_000, currentTimeMillis = 10_000)

        assertTrue(trend.isPositive)
    }

    @Test
    fun `signalTrend returns DEGRADING for decreasing signal`() {
        val observations =
            listOf(
                createTestObservation(timestampMillis = 1000, rssiDbm = -50),
                createTestObservation(timestampMillis = 2000, rssiDbm = -55),
                createTestObservation(timestampMillis = 3000, rssiDbm = -60),
                createTestObservation(timestampMillis = 4000, rssiDbm = -65),
                createTestObservation(timestampMillis = 5000, rssiDbm = -70),
            )
        val history = createTestHistory(observations = observations)

        val trend = history.signalTrend(windowMillis = 10_000, currentTimeMillis = 10_000)

        assertTrue(trend.indicatesProblem)
    }

    @Test
    fun `signalTrend returns STABLE for constant signal`() {
        val observations =
            List(5) { i ->
                createTestObservation(timestampMillis = (i + 1) * 1000L, rssiDbm = -60)
            }
        val history = createTestHistory(observations = observations)

        val trend = history.signalTrend(windowMillis = 10_000, currentTimeMillis = 10_000)

        assertEquals(SignalTrend.STABLE, trend)
    }

    @Test
    fun `signalTrend returns INSUFFICIENT_DATA for too few observations`() {
        val observations =
            listOf(
                createTestObservation(timestampMillis = 1000, rssiDbm = -60),
                createTestObservation(timestampMillis = 2000, rssiDbm = -65),
            )
        val history = createTestHistory(observations = observations)

        val trend = history.signalTrend(windowMillis = 10_000, currentTimeMillis = 10_000)

        assertEquals(SignalTrend.INSUFFICIENT_DATA, trend)
    }

    @Test
    fun `addObservation adds new observation and updates last seen`() {
        val initialObs = createTestObservation(timestampMillis = 1000, rssiDbm = -60)
        val history = createTestHistory(observations = listOf(initialObs))

        val newObs = createTestObservation(timestampMillis = 5000, rssiDbm = -55)
        val updated = history.addObservation(newObs)

        assertEquals(2, updated.observationCount)
        assertEquals(5000, updated.lastSeenTimestamp)
    }

    @Test
    fun `addObservation throws exception for different BSSID`() {
        val history = createTestHistory(bssid = "AA:BB:CC:DD:EE:FF")
        val wrongObs = createTestObservation(bssid = "11:22:33:44:55:66")

        assertThrows<IllegalArgumentException> {
            history.addObservation(wrongObs)
        }
    }

    @Test
    fun `estimateRssiAt returns null for timestamp before history`() {
        val obs = createTestObservation(timestampMillis = 1000)
        val history = createTestHistory(observations = listOf(obs))

        val estimate = history.estimateRssiAt(500)

        assertNull(estimate)
    }

    @Test
    fun `estimateRssiAt returns null for timestamp after history`() {
        val obs = createTestObservation(timestampMillis = 1000)
        val history = createTestHistory(observations = listOf(obs))

        val estimate = history.estimateRssiAt(5000)

        assertNull(estimate)
    }

    @Test
    fun `estimateRssiAt returns exact value at observation time`() {
        val obs = createTestObservation(timestampMillis = 1000, rssiDbm = -60)
        val history = createTestHistory(observations = listOf(obs))

        val estimate = history.estimateRssiAt(1000)

        assertEquals(-60, estimate)
    }

    @Test
    fun `estimateRssiAt interpolates between observations`() {
        val obs1 = createTestObservation(timestampMillis = 1000, rssiDbm = -60)
        val obs2 = createTestObservation(timestampMillis = 3000, rssiDbm = -40)
        val history = createTestHistory(observations = listOf(obs1, obs2))

        val estimate = history.estimateRssiAt(2000) // Midpoint

        assertEquals(-50, estimate) // Linear interpolation
    }

    @Test
    fun `create factory method creates history from observation`() {
        val obs =
            createTestObservation(
                bssid = "AA:BB:CC:DD:EE:FF",
                timestampMillis = 1000,
                rssiDbm = -60,
            )

        val history = ApHistory.create(obs, "TestNetwork")

        assertEquals("AA:BB:CC:DD:EE:FF", history.bssid)
        assertEquals("TestNetwork", history.ssid)
        assertEquals(1, history.observationCount)
        assertEquals(1000, history.firstSeenTimestamp)
        assertEquals(1000, history.lastSeenTimestamp)
    }

    @Test
    fun `summary generates readable description`() {
        val observations =
            List(5) { i ->
                createTestObservation(timestampMillis = (i + 1) * 1000L, rssiDbm = -60)
            }
        val history = createTestHistory(observations = observations)

        val summary = history.summary

        assertTrue(summary.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(summary.contains("TestNetwork"))
        assertTrue(summary.contains("5 observations"))
    }
}
