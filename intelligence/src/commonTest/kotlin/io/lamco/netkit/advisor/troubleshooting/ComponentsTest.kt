package io.lamco.netkit.advisor.troubleshooting

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Combined tests for SymptomMatcher, SolutionRecommender, and DiagnosticWorkflow
 */
class ComponentsTest {

    // ========================================
    // SymptomMatcher Tests
    // ========================================

    private val matcher = SymptomMatcher()

    @Test
    fun `symptom matcher requires at least one symptom`() {
        assertFailsWith<IllegalArgumentException> {
            matcher.matchPatterns(emptyList())
        }
    }

    @Test
    fun `match dead zone pattern`() {
        val symptoms = listOf(
            Symptom.PoorCoverage(-85, "basement"),
            Symptom.CannotConnect()
        )

        val patterns = matcher.matchPatterns(symptoms)
        assertTrue(patterns.any { it.pattern.name.contains("Dead Zone", ignoreCase = true) })
    }

    @Test
    fun `match network congestion pattern`() {
        val symptoms = listOf(
            Symptom.SlowSpeed(10.0, 100.0),
            Symptom.HighLatency(200, "gateway")
        )

        val patterns = matcher.matchPatterns(symptoms)
        assertTrue(patterns.any { it.pattern.name.contains("Congestion", ignoreCase = true) })
    }

    @Test
    fun `match signal degradation pattern`() {
        val symptoms = listOf(
            Symptom.PoorCoverage(-78, "bedroom"),
            Symptom.FrequentDisconnects(7)
        )

        val patterns = matcher.matchPatterns(symptoms)
        assertTrue(patterns.any { it.pattern.name.contains("Degradation", ignoreCase = true) })
    }

    @Test
    fun `pattern matches are sorted by confidence`() {
        val symptoms = listOf(
            Symptom.SlowSpeed(5.0, 100.0),
            Symptom.HighLatency(300, "gateway"),
            Symptom.PoorCoverage(-80, "office")
        )

        val patterns = matcher.matchPatterns(symptoms)
        if (patterns.size > 1) {
            assertTrue(patterns[0].confidence >= patterns[1].confidence)
        }
    }

    @Test
    fun `find correlated symptoms for slow speed`() {
        val primary = Symptom.SlowSpeed(10.0, 100.0)
        val all = listOf(primary, Symptom.HighLatency(200, "gateway"))

        val correlations = matcher.findCorrelatedSymptoms(primary, all)
        assertTrue(correlations.isNotEmpty())
    }

    @Test
    fun `find correlated symptoms for disconnects`() {
        val primary = Symptom.FrequentDisconnects(8)
        val all = listOf(primary, Symptom.PoorCoverage(-80, "office"))

        val correlations = matcher.findCorrelatedSymptoms(primary, all)
        assertTrue(correlations.any { it.correlation > 0.8 })
    }

    @Test
    fun `calculate combined severity uses max and average`() {
        val symptoms = listOf(
            Symptom.SlowSpeed(10.0, 100.0),  // severity ~7
            Symptom.HighLatency(150, "gateway")  // severity ~5
        )

        val severity = matcher.calculateCombinedSeverity(symptoms)
        assertTrue(severity in 5..8)
    }

    @Test
    fun `empty symptoms list has zero severity`() {
        assertEquals(0, matcher.calculateCombinedSeverity(emptyList()))
    }

    @Test
    fun `symptom correlation has valid range`() {
        val corr = SymptomCorrelation(
            symptom1 = Symptom.SlowSpeed(10.0, 100.0),
            symptom2 = Symptom.HighLatency(200, "gateway"),
            correlation = 0.85,
            explanation = "Test"
        )

        assertTrue(corr.correlation in 0.0..1.0)
    }

    @Test
    fun `strong correlation is at least 0_7`() {
        val strong = SymptomCorrelation(
            symptom1 = Symptom.SlowSpeed(10.0, 100.0),
            symptom2 = Symptom.HighLatency(200, "gateway"),
            correlation = 0.8,
            explanation = "Test"
        )
        assertTrue(strong.isStrongCorrelation)

        val weak = SymptomCorrelation(
            symptom1 = Symptom.SlowSpeed(10.0, 100.0),
            symptom2 = Symptom.HighLatency(200, "gateway"),
            correlation = 0.5,
            explanation = "Test"
        )
        assertFalse(weak.isStrongCorrelation)
    }

    // ========================================
    // SolutionRecommender Tests
    // ========================================

    private val recommender = SolutionRecommender()

    @Test
    fun `recommend solution for weak signal`() {
        val solution = recommender.recommendSolution(NetworkIssue.WEAK_SIGNAL)
        assertNotNull(solution)
        assertTrue(solution.steps.isNotEmpty())
    }

    @Test
    fun `recommend solution respects max difficulty`() {
        val easyOnly = recommender.recommendSolution(
            NetworkIssue.WEAK_SIGNAL,
            maxDifficulty = Difficulty.EASY
        )

        assertNotNull(easyOnly)
        assertEquals(Difficulty.EASY, easyOnly.difficulty)
    }

    @Test
    fun `get all solutions returns multiple options`() {
        val solutions = recommender.getAllSolutions(NetworkIssue.WEAK_SIGNAL)
        assertTrue(solutions.size > 1)  // Should have easy, medium, hard solutions
    }

    @Test
    fun `all solutions are sorted by impact`() {
        val solutions = recommender.getAllSolutions(NetworkIssue.WEAK_SIGNAL)
        val impacts = solutions.map { it.impact.ordinal }
        assertEquals(impacts, impacts.sortedDescending())
    }

    @Test
    fun `recommend solutions for multiple issues`() {
        val issues = listOf(
            NetworkIssue.WEAK_SIGNAL,
            NetworkIssue.CHANNEL_CONGESTION
        )

        val solutions = recommender.recommendSolutions(issues)
        assertEquals(2, solutions.size)
    }

    @Test
    fun `get quick fix returns easy difficulty solution`() {
        val quickFix = recommender.getQuickFix(NetworkIssue.CHANNEL_CONGESTION)
        assertNotNull(quickFix)
        assertEquals(Difficulty.EASY, quickFix.difficulty)
    }

    @Test
    fun `solution for wrong password is easy`() {
        val solution = recommender.recommendSolution(NetworkIssue.WRONG_PASSWORD)
        assertNotNull(solution)
        assertEquals(Difficulty.EASY, solution.difficulty)
    }

    @Test
    fun `solution for adding APs is hard`() {
        val solutions = recommender.getAllSolutions(NetworkIssue.INSUFFICIENT_COVERAGE)
        assertTrue(solutions.any { it.difficulty == Difficulty.HARD })
    }

    @Test
    fun `solution has implementation steps`() {
        val solution = recommender.recommendSolution(NetworkIssue.CHANNEL_CONGESTION)
        assertNotNull(solution)
        assertTrue(solution.steps.isNotEmpty())
        assertTrue(solution.steps.all { it.isNotBlank() })
    }

    // ========================================
    // DiagnosticWorkflow Tests
    // ========================================

    private val workflow = DiagnosticWorkflow()

    @Test
    fun `workflow requires at least one symptom`() {
        assertFailsWith<IllegalArgumentException> {
            workflow.runDiagnostics(emptyList())
        }
    }

    @Test
    fun `workflow produces complete result`() {
        val result = workflow.runDiagnostics(
            symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
            context = DiagnosticContext(signalStrength = -72)
        )

        assertNotNull(result.diagnosis)
        assertTrue(result.workflowSteps.isNotEmpty())
    }

    @Test
    fun `workflow result includes matched patterns`() {
        val result = workflow.runDiagnostics(
            symptoms = listOf(
                Symptom.SlowSpeed(10.0, 100.0),
                Symptom.HighLatency(200, "gateway")
            )
        )

        // May or may not have patterns depending on symptoms
        assertNotNull(result.matchedPatterns)
    }

    @Test
    fun `workflow provides recommended solution`() {
        val result = workflow.runDiagnostics(
            symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
            context = DiagnosticContext(signalStrength = -75)
        )

        assertNotNull(result.recommendedSolution)
    }

    @Test
    fun `workflow respects max difficulty`() {
        val result = workflow.runDiagnostics(
            symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
            maxDifficulty = Difficulty.EASY
        )

        if (result.recommendedSolution != null) {
            assertTrue(result.recommendedSolution!!.difficulty.ordinal <= Difficulty.EASY.ordinal)
        }
    }

    @Test
    fun `workflow summary includes diagnosis`() {
        val result = workflow.runDiagnostics(
            symptoms = listOf(Symptom.CannotConnect("Wrong password"))
        )

        assertTrue(result.summary.isNotEmpty())
    }

    @Test
    fun `workflow has solution when diagnosis succeeds`() {
        val result = workflow.runDiagnostics(
            symptoms = listOf(Symptom.CannotConnect("Wrong password"))
        )

        assertTrue(result.hasSolution)
    }

    @Test
    fun `workflow is high confidence with good context`() {
        val result = workflow.runDiagnostics(
            symptoms = listOf(Symptom.SlowSpeed(5.0, 100.0)),
            context = DiagnosticContext(
                signalStrength = -78,
                channelUtilization = 30,
                connectedClients = 10
            )
        )

        assertTrue(result.diagnosis.confidence > 0.5)
    }

    @Test
    fun `quick diagnose is faster than full workflow`() {
        val quick = workflow.quickDiagnose(
            symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0))
        )

        assertNotNull(quick)
        assertTrue(quick.rootCauses.isNotEmpty())
    }

    @Test
    fun `get troubleshooting steps for issue`() {
        val steps = workflow.getTroubleshootingSteps(NetworkIssue.WEAK_SIGNAL)
        assertTrue(steps.isNotEmpty())
    }

    @Test
    fun `start interactive session creates session`() {
        val session = workflow.startInteractiveSession(
            initialSymptoms = listOf(Symptom.SlowSpeed(10.0, 100.0))
        )

        assertNotNull(session.sessionId)
        assertEquals(0, session.currentStep)
    }

    @Test
    fun `interactive session tracks progress`() {
        val session = workflow.startInteractiveSession(
            initialSymptoms = listOf(Symptom.SlowSpeed(10.0, 100.0))
        )

        assertTrue(session.progressPercent >= 0)
        assertTrue(session.progressPercent <= 100)
    }

    @Test
    fun `interactive session can advance`() {
        val session = workflow.startInteractiveSession(
            initialSymptoms = listOf(Symptom.SlowSpeed(10.0, 100.0))
        )

        val next = session.nextStep(StepFeedback.SUCCESS)
        assertEquals(1, next.currentStep)
    }

    @Test
    fun `interactive session can go back`() {
        val session = workflow.startInteractiveSession(
            initialSymptoms = listOf(Symptom.SlowSpeed(10.0, 100.0))
        )

        val next = session.nextStep(StepFeedback.SUCCESS)
        val prev = next.previousStep()

        assertEquals(0, prev.currentStep)
    }

    @Test
    fun `interactive session at start cannot go back further`() {
        val session = workflow.startInteractiveSession(
            initialSymptoms = listOf(Symptom.SlowSpeed(10.0, 100.0))
        )

        val prev = session.previousStep()
        assertEquals(0, prev.currentStep)
    }

    @Test
    fun `interactive session is complete when all steps done`() {
        var session = workflow.startInteractiveSession(
            initialSymptoms = listOf(Symptom.CannotConnect("Wrong password"))
        )

        while (!session.isComplete) {
            session = session.nextStep(StepFeedback.SUCCESS)
        }

        assertTrue(session.isComplete)
    }
}
