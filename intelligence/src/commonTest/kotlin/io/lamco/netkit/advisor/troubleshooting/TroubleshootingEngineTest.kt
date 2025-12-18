package io.lamco.netkit.advisor.troubleshooting

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class TroubleshootingEngineTest {
    private val engine = TroubleshootingEngine()

    @Test
    fun `diagnose requires at least one symptom`() {
        assertThrows<IllegalArgumentException> {
            engine.diagnose(emptyList())
        }
    }

    @Test
    fun `diagnose slow speed with weak signal`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
                context = DiagnosticContext(signalStrength = -75),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.WEAK_SIGNAL })
    }

    @Test
    fun `diagnose slow speed with high channel utilization`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(20.0, 100.0)),
                context = DiagnosticContext(channelUtilization = 85),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.CHANNEL_CONGESTION })
    }

    @Test
    fun `diagnose slow speed with many clients`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(15.0, 100.0)),
                context = DiagnosticContext(connectedClients = 45),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.AP_OVERLOADED })
    }

    @Test
    fun `diagnose slow speed with good wifi metrics suggests ISP issue`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
                context =
                    DiagnosticContext(
                        signalStrength = -60,
                        channelUtilization = 30,
                    ),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.ISP_ISSUE })
    }

    @Test
    fun `diagnose frequent disconnects with weak signal`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.FrequentDisconnects(8)),
                context = DiagnosticContext(signalStrength = -78),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.WEAK_SIGNAL })
    }

    @Test
    fun `diagnose frequent disconnects on 2_4GHz suggests interference`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.FrequentDisconnects(6)),
                context = DiagnosticContext(band = "2.4GHz"),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.INTERFERENCE })
    }

    @Test
    fun `diagnose frequent disconnects suggests roaming failure`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.FrequentDisconnects(5, listOf("iPhone", "Laptop"))),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.CLIENT_ROAMING_FAILURE })
    }

    @Test
    fun `diagnose very frequent disconnects suggests hardware failure`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.FrequentDisconnects(15)),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.FAILING_HARDWARE })
    }

    @Test
    fun `diagnose poor coverage returns coverage issues`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.PoorCoverage(-82, "bedroom")),
            )

        assertTrue(
            result.rootCauses.any {
                it.cause == NetworkIssue.INSUFFICIENT_COVERAGE ||
                    it.cause == NetworkIssue.POOR_AP_PLACEMENT
            },
        )
    }

    @Test
    fun `diagnose high latency to gateway suggests channel congestion`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.HighLatency(200, "gateway")),
                context = DiagnosticContext(channelUtilization = 85),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.CHANNEL_CONGESTION })
    }

    @Test
    fun `diagnose high latency to internet suggests ISP issue`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.HighLatency(300, "google.com")),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.ISP_ISSUE })
    }

    @Test
    fun `diagnose cannot connect with password error`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.CannotConnect("Authentication failed: wrong password")),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.WRONG_PASSWORD })
        assertTrue(result.rootCauses.first().probability > 0.9)
    }

    @Test
    fun `diagnose cannot connect with very weak signal`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.CannotConnect()),
                context = DiagnosticContext(signalStrength = -85),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.WEAK_SIGNAL })
    }

    @Test
    fun `diagnose cannot connect suggests compatibility issue`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.CannotConnect(deviceType = "Old Android Phone")),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.DEVICE_COMPATIBILITY })
    }

    @Test
    fun `diagnose intermittent issues suggests interference`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.IntermittentIssues("hourly", "minutes")),
            )

        assertTrue(result.rootCauses.any { it.cause == NetworkIssue.INTERFERENCE })
    }

    @Test
    fun `diagnose interference detected on 2_4GHz`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.InterferenceDetected(0.8, "2.4GHz")),
            )

        val primaryCause = result.rootCauses.first()
        assertEquals(NetworkIssue.INTERFERENCE, primaryCause.cause)
        assertTrue(primaryCause.probability > 0.8)
    }

    @Test
    fun `diagnosis includes troubleshooting steps`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
            )

        assertTrue(result.troubleshootingSteps.isNotEmpty())
    }

    @Test
    fun `diagnosis includes estimated resolution time`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.CannotConnect("Wrong password")),
            )

        assertNotNull(result.estimatedResolutionTime)
        assertTrue(result.estimatedResolutionTime.isNotEmpty())
    }

    @Test
    fun `diagnosis has valid confidence range`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
            )

        assertTrue(result.confidence in 0.0..1.0)
    }

    @Test
    fun `diagnosis with context has higher confidence`() {
        val noContext =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
            )

        val withContext =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
                context =
                    DiagnosticContext(
                        signalStrength = -72,
                        channelUtilization = 85,
                        connectedClients = 40,
                    ),
            )

        assertTrue(withContext.confidence >= noContext.confidence)
    }

    @Test
    fun `get troubleshooting steps for weak signal`() {
        val steps = engine.getTroubleshootingSteps(NetworkIssue.WEAK_SIGNAL)

        assertTrue(steps.isNotEmpty())
        assertTrue(steps.any { it.action.contains("signal", ignoreCase = true) })
    }

    @Test
    fun `get troubleshooting steps for channel congestion`() {
        val steps = engine.getTroubleshootingSteps(NetworkIssue.CHANNEL_CONGESTION)

        assertTrue(steps.isNotEmpty())
        assertTrue(steps.any { it.action.contains("channel", ignoreCase = true) })
    }

    @Test
    fun `get troubleshooting steps for wrong password`() {
        val steps = engine.getTroubleshootingSteps(NetworkIssue.WRONG_PASSWORD)

        assertTrue(steps.isNotEmpty())
        assertTrue(steps.size <= 3) // Wrong password should be quick fix
    }

    @Test
    fun `diagnosis summary includes primary cause`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(5.0, 100.0)),
                context = DiagnosticContext(signalStrength = -78),
            )

        assertTrue(result.summary.contains("cause", ignoreCase = true))
    }

    @Test
    fun `multiple symptoms produce merged diagnosis`() {
        val result =
            engine.diagnose(
                symptoms =
                    listOf(
                        Symptom.SlowSpeed(10.0, 100.0),
                        Symptom.HighLatency(200, "gateway"),
                        Symptom.PoorCoverage(-75, "office"),
                    ),
                context =
                    DiagnosticContext(
                        signalStrength = -75,
                        channelUtilization = 60,
                    ),
            )

        assertTrue(result.rootCauses.size >= 2) // Multiple potential causes
    }

    @Test
    fun `root causes are sorted by probability`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
                context =
                    DiagnosticContext(
                        signalStrength = -72,
                        channelUtilization = 85,
                        connectedClients = 45,
                    ),
            )

        val probabilities = result.rootCauses.map { it.probability }
        assertEquals(probabilities, probabilities.sortedDescending())
    }

    @Test
    fun `each root cause has evidence`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
                context = DiagnosticContext(signalStrength = -75),
            )

        result.rootCauses.forEach { cause ->
            assertTrue(cause.evidence.isNotEmpty(), "Cause ${cause.cause} missing evidence")
        }
    }

    @Test
    fun `each root cause has fix suggestion`() {
        val result =
            engine.diagnose(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
                context = DiagnosticContext(signalStrength = -75),
            )

        result.rootCauses.forEach { cause ->
            assertTrue(cause.fixSuggestion.isNotBlank(), "Cause ${cause.cause} missing fix")
        }
    }
}
