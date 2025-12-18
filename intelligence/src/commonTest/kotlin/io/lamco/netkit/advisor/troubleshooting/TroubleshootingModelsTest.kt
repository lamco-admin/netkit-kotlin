package io.lamco.netkit.advisor.troubleshooting

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TroubleshootingModelsTest {

    @Test
    fun `slow speed symptom calculates severity based on speed ratio`() {
        val severe = Symptom.SlowSpeed(5.0, 100.0)
        assertTrue(severe.severity >= 7)  // <10% of expected

        val moderate = Symptom.SlowSpeed(30.0, 100.0)
        assertTrue(moderate.severity in 4..6)  // 30% of expected

        val mild = Symptom.SlowSpeed(60.0, 100.0)
        assertTrue(mild.severity <= 5)  // 60% of expected
    }

    @Test
    fun `slow speed description includes speeds`() {
        val symptom = Symptom.SlowSpeed(10.0, 100.0)
        assertTrue(symptom.description.contains("10"))
        assertTrue(symptom.description.contains("100"))
    }

    @Test
    fun `frequent disconnects severity based on rate`() {
        val critical = Symptom.FrequentDisconnects(15)
        assertEquals(10, critical.severity)

        val high = Symptom.FrequentDisconnects(7)
        assertEquals(8, high.severity)

        val moderate = Symptom.FrequentDisconnects(3)
        assertEquals(6, moderate.severity)
    }

    @Test
    fun `poor coverage severity based on signal strength`() {
        val severe = Symptom.PoorCoverage(-85, "bedroom")
        assertTrue(severe.severity >= 6)

        val moderate = Symptom.PoorCoverage(-72, "kitchen")
        assertTrue(moderate.severity in 3..5)
    }

    @Test
    fun `high latency severity based on latency value`() {
        val critical = Symptom.HighLatency(600, "gateway")
        assertTrue(critical.severity >= 8)

        val moderate = Symptom.HighLatency(150, "gateway")
        assertTrue(moderate.severity in 5..7)
    }

    @Test
    fun `cannot connect is critical severity`() {
        val symptom = Symptom.CannotConnect("Wrong password")
        assertEquals(10, symptom.severity)
    }

    @Test
    fun `interference detected severity based on level`() {
        val high = Symptom.InterferenceDetected(0.9, "2.4GHz")
        assertTrue(high.severity >= 8)

        val low = Symptom.InterferenceDetected(0.2, "5GHz")
        assertTrue(low.severity <= 3)
    }

    @Test
    fun `custom symptom uses provided severity`() {
        val symptom = Symptom.Custom("Test issue", 7)
        assertEquals(7, symptom.severity)
    }

    @Test
    fun `custom symptom severity is clamped to 1-10`() {
        val tooHigh = Symptom.Custom("Test", 15)
        assertEquals(10, tooHigh.severity)

        val tooLow = Symptom.Custom("Test", -5)
        assertEquals(1, tooLow.severity)
    }

    @Test
    fun `network issue display names are formatted`() {
        assertEquals("Weak signal", NetworkIssue.WEAK_SIGNAL.displayName)
        assertEquals("Channel congestion", NetworkIssue.CHANNEL_CONGESTION.displayName)
        assertEquals("Ap overloaded", NetworkIssue.AP_OVERLOADED.displayName)
    }

    @Test
    fun `root cause requires probability in range`() {
        assertThrows<IllegalArgumentException> {
            RootCause(
                cause = NetworkIssue.WEAK_SIGNAL,
                probability = 1.5,
                evidence = listOf("Test"),
                fixSuggestion = "Fix"
            )
        }

        assertThrows<IllegalArgumentException> {
            RootCause(
                cause = NetworkIssue.WEAK_SIGNAL,
                probability = -0.1,
                evidence = listOf("Test"),
                fixSuggestion = "Fix"
            )
        }
    }

    @Test
    fun `root cause requires non-empty evidence`() {
        assertThrows<IllegalArgumentException> {
            RootCause(
                cause = NetworkIssue.WEAK_SIGNAL,
                probability = 0.8,
                evidence = emptyList(),
                fixSuggestion = "Fix"
            )
        }
    }

    @Test
    fun `root cause summary includes cause and probability`() {
        val cause = RootCause(
            cause = NetworkIssue.WEAK_SIGNAL,
            probability = 0.85,
            evidence = listOf("Signal -80dBm"),
            fixSuggestion = "Move closer"
        )

        val summary = cause.summary
        assertTrue(summary.contains("Weak signal"))
        assertTrue(summary.contains("85"))
    }

    @Test
    fun `troubleshooting step summary includes step number`() {
        val step = TroubleshootingStep(
            step = 1,
            action = "Check signal",
            expectedOutcome = "See RSSI value",
            ifSuccess = "Continue",
            ifFailure = "Try again"
        )

        assertTrue(step.summary.contains("Step 1"))
        assertTrue(step.summary.contains("Check signal"))
    }

    @Test
    fun `diagnosis result requires valid confidence`() {
        assertThrows<IllegalArgumentException> {
            DiagnosisResult(
                symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
                rootCauses = emptyList(),
                confidence = 1.5,
                troubleshootingSteps = emptyList(),
                estimatedResolutionTime = "1 hour"
            )
        }
    }

    @Test
    fun `diagnosis result primary cause is max probability`() {
        val causes = listOf(
            RootCause(NetworkIssue.WEAK_SIGNAL, 0.6, listOf("A"), "Fix1"),
            RootCause(NetworkIssue.CHANNEL_CONGESTION, 0.9, listOf("B"), "Fix2"),
            RootCause(NetworkIssue.INTERFERENCE, 0.4, listOf("C"), "Fix3")
        )

        val result = DiagnosisResult(
            symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
            rootCauses = causes,
            confidence = 0.8,
            troubleshootingSteps = emptyList(),
            estimatedResolutionTime = "30 min"
        )

        assertEquals(NetworkIssue.CHANNEL_CONGESTION, result.primaryCause?.cause)
    }

    @Test
    fun `diagnosis result is high confidence when at least 0_7`() {
        val highConf = DiagnosisResult(
            symptoms = emptyList(),
            rootCauses = emptyList(),
            confidence = 0.75,
            troubleshootingSteps = emptyList(),
            estimatedResolutionTime = ""
        )
        assertTrue(highConf.isHighConfidence)

        val lowConf = DiagnosisResult(
            symptoms = emptyList(),
            rootCauses = emptyList(),
            confidence = 0.6,
            troubleshootingSteps = emptyList(),
            estimatedResolutionTime = ""
        )
        assertFalse(lowConf.isHighConfidence)
    }

    @Test
    fun `solution recommendation summary includes difficulty and impact`() {
        val solution = SolutionRecommendation(
            issue = NetworkIssue.WEAK_SIGNAL,
            solution = "Add AP",
            steps = listOf("Buy AP", "Install"),
            difficulty = Difficulty.HARD,
            impact = Impact.HIGH
        )

        val summary = solution.summary
        assertTrue(summary.contains("Hard"))
        assertTrue(summary.contains("High"))
    }

    @Test
    fun `difficulty display names are formatted`() {
        assertEquals("Easy", Difficulty.EASY.displayName)
        assertEquals("Medium", Difficulty.MEDIUM.displayName)
        assertEquals("Hard", Difficulty.HARD.displayName)
    }

    @Test
    fun `impact display names are formatted`() {
        assertEquals("Low", Impact.LOW.displayName)
        assertEquals("Medium", Impact.MEDIUM.displayName)
        assertEquals("High", Impact.HIGH.displayName)
    }

    @Test
    fun `diagnostic context with all fields`() {
        val context = DiagnosticContext(
            signalStrength = -65,
            channelUtilization = 75,
            connectedClients = 35,
            band = "5GHz",
            channel = 36,
            bandwidth = 150.0,
            latency = 25,
            packetLoss = 2.5
        )

        assertEquals(-65, context.signalStrength)
        assertEquals(75, context.channelUtilization)
        assertEquals(35, context.connectedClients)
    }

    @Test
    fun `diagnostic context with nullable fields`() {
        val context = DiagnosticContext()

        assertEquals(null, context.signalStrength)
        assertEquals(null, context.channelUtilization)
    }
}
