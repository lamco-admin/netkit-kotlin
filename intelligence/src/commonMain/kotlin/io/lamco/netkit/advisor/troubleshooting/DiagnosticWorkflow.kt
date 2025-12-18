package io.lamco.netkit.advisor.troubleshooting

/**
 * Automated diagnostic workflow orchestrator
 *
 * Orchestrates the complete troubleshooting process from symptom collection
 * to solution recommendation. Uses all troubleshooting components.
 *
 * Workflow Steps:
 * 1. **Symptom Collection**: Gather user symptoms and system metrics
 * 2. **Pattern Matching**: Identify known symptom patterns
 * 3. **Root Cause Analysis**: Use decision trees and inference
 * 4. **Solution Recommendation**: Suggest fixes with steps
 * 5. **Verification**: Check if solution resolved issue
 *
 * Example Usage:
 * ```kotlin
 * val workflow = DiagnosticWorkflow()
 *
 * val result = workflow.runDiagnostics(
 *     symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
 *     context = DiagnosticContext(
 *         signalStrength = -72,
 *         channelUtilization = 85
 *     )
 * )
 *
 * println("Diagnosis: ${result.diagnosis.summary}")
 * println("Solution: ${result.recommendedSolution?.solution}")
 * ```
 */
class DiagnosticWorkflow {
    private val troubleshootingEngine = TroubleshootingEngine()
    private val rootCauseAnalyzer = RootCauseAnalyzer()
    private val symptomMatcher = SymptomMatcher()
    private val solutionRecommender = SolutionRecommender()

    /**
     * Run complete diagnostic workflow
     *
     * @param symptoms List of observed symptoms
     * @param context Diagnostic context with network metrics
     * @param maxDifficulty Maximum acceptable solution difficulty
     * @return Complete diagnostic result with solutions
     */
    fun runDiagnostics(
        symptoms: List<Symptom>,
        context: DiagnosticContext = DiagnosticContext(),
        maxDifficulty: Difficulty = Difficulty.HARD,
    ): WorkflowResult {
        require(symptoms.isNotEmpty()) { "At least one symptom required" }

        // Step 1: Match symptom patterns
        val patterns = symptomMatcher.matchPatterns(symptoms)

        // Step 2: Diagnose using decision tree
        val diagnosis = troubleshootingEngine.diagnose(symptoms, context)

        // Step 3: Deep analysis with inference
        val deepAnalysis = rootCauseAnalyzer.analyze(symptoms, context = context)

        // Step 4: Merge results (prefer higher confidence causes)
        val allCauses =
            (diagnosis.rootCauses + deepAnalysis)
                .groupBy { it.cause }
                .map { (_, causes) ->
                    causes.maxByOrNull { it.probability }!!
                }.sortedByDescending { it.probability }

        // Step 5: Get solution for primary cause
        val primaryCause = allCauses.firstOrNull()
        val recommendedSolution =
            primaryCause?.let {
                solutionRecommender.recommendSolution(it.cause, maxDifficulty)
            }

        // Step 6: Get all solutions for all causes
        val allSolutions =
            allCauses.mapNotNull { cause ->
                solutionRecommender.recommendSolution(cause.cause, maxDifficulty)
            }

        return WorkflowResult(
            symptoms = symptoms,
            matchedPatterns = patterns,
            diagnosis = diagnosis.copy(rootCauses = allCauses),
            recommendedSolution = recommendedSolution,
            alternativeSolutions = allSolutions.drop(1), // Exclude primary solution
            workflowSteps = generateWorkflowReport(symptoms, patterns, allCauses, recommendedSolution),
        )
    }

    /**
     * Run quick diagnosis (fast, basic analysis)
     *
     * @param symptoms List of symptoms
     * @param context Diagnostic context
     * @return Quick diagnosis result
     */
    fun quickDiagnose(
        symptoms: List<Symptom>,
        context: DiagnosticContext = DiagnosticContext(),
    ): DiagnosisResult = troubleshootingEngine.diagnose(symptoms, context)

    /**
     * Get troubleshooting steps for known issue
     *
     * @param issue Network issue
     * @return Troubleshooting steps
     */
    fun getTroubleshootingSteps(issue: NetworkIssue): List<TroubleshootingStep> = troubleshootingEngine.getTroubleshootingSteps(issue)

    /**
     * Run interactive troubleshooting session
     *
     * Provides step-by-step guidance with user feedback.
     *
     * @param initialSymptoms Initial symptoms
     * @param context Diagnostic context
     * @return Interactive session
     */
    fun startInteractiveSession(
        initialSymptoms: List<Symptom>,
        context: DiagnosticContext = DiagnosticContext(),
    ): InteractiveTroubleshootingSession {
        val diagnosis = troubleshootingEngine.diagnose(initialSymptoms, context)

        return InteractiveTroubleshootingSession(
            sessionId = generateSessionId(),
            symptoms = initialSymptoms,
            diagnosis = diagnosis,
            currentStep = 0,
            steps = diagnosis.troubleshootingSteps,
        )
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Generate workflow execution report
     */
    private fun generateWorkflowReport(
        symptoms: List<Symptom>,
        patterns: List<SymptomPatternMatch>,
        causes: List<RootCause>,
        solution: SolutionRecommendation?,
    ): List<String> {
        val report = mutableListOf<String>()

        report.add("Step 1: Analyzed ${symptoms.size} symptom(s)")
        if (patterns.isNotEmpty()) {
            report.add("Step 2: Matched ${patterns.size} known pattern(s): ${patterns.first().pattern.name}")
        }
        report.add("Step 3: Identified ${causes.size} potential root cause(s)")
        if (solution != null) {
            report.add("Step 4: Recommended solution: ${solution.solution}")
        }

        return report
    }

    /**
     * Generate unique session ID
     */
    private fun generateSessionId(): String = "session-${System.currentTimeMillis()}"
}

/**
 * Complete workflow result
 *
 * @property symptoms Original symptoms analyzed
 * @property matchedPatterns Matched symptom patterns
 * @property diagnosis Complete diagnosis with root causes
 * @property recommendedSolution Primary recommended solution
 * @property alternativeSolutions Alternative solutions
 * @property workflowSteps Workflow execution steps
 */
data class WorkflowResult(
    val symptoms: List<Symptom>,
    val matchedPatterns: List<SymptomPatternMatch>,
    val diagnosis: DiagnosisResult,
    val recommendedSolution: SolutionRecommendation?,
    val alternativeSolutions: List<SolutionRecommendation>,
    val workflowSteps: List<String>,
) {
    /**
     * Summary of workflow result
     */
    val summary: String
        get() =
            buildString {
                append("Diagnosed: ${diagnosis.primaryCause?.cause?.displayName ?: "Unknown"}")
                if (recommendedSolution != null) {
                    append(" | Solution: ${recommendedSolution.solution}")
                }
                append(" | Confidence: ${(diagnosis.confidence * 100).toInt()}%")
            }

    /**
     * Whether diagnosis has high confidence (>= 0.7)
     */
    val isHighConfidence: Boolean
        get() = diagnosis.isHighConfidence

    /**
     * Whether a solution is available
     */
    val hasSolution: Boolean
        get() = recommendedSolution != null
}

/**
 * Interactive troubleshooting session
 *
 * Provides step-by-step troubleshooting with user feedback.
 *
 * @property sessionId Unique session identifier
 * @property symptoms Symptoms being troubleshot
 * @property diagnosis Initial diagnosis
 * @property currentStep Current step index
 * @property steps Troubleshooting steps
 * @property completedSteps Steps completed
 * @property feedback User feedback on steps
 */
data class InteractiveTroubleshootingSession(
    val sessionId: String,
    val symptoms: List<Symptom>,
    val diagnosis: DiagnosisResult,
    val currentStep: Int,
    val steps: List<TroubleshootingStep>,
    val completedSteps: List<Int> = emptyList(),
    val feedback: Map<Int, StepFeedback> = emptyMap(),
) {
    /**
     * Get current troubleshooting step
     */
    val current: TroubleshootingStep?
        get() = steps.getOrNull(currentStep)

    /**
     * Whether session is complete
     */
    val isComplete: Boolean
        get() = currentStep >= steps.size

    /**
     * Progress percentage (0-100)
     */
    val progressPercent: Int
        get() = if (steps.isEmpty()) 100 else ((currentStep.toDouble() / steps.size) * 100).toInt()

    /**
     * Advance to next step
     */
    fun nextStep(feedback: StepFeedback): InteractiveTroubleshootingSession =
        copy(
            currentStep = currentStep + 1,
            completedSteps = completedSteps + currentStep,
            feedback = this.feedback + (currentStep to feedback),
        )

    /**
     * Go back to previous step
     */
    fun previousStep(): InteractiveTroubleshootingSession =
        if (currentStep > 0) {
            copy(currentStep = currentStep - 1)
        } else {
            this
        }
}

/**
 * User feedback on troubleshooting step
 */
data class StepFeedback(
    val success: Boolean,
    val notes: String = "",
) {
    companion object {
        val SUCCESS = StepFeedback(success = true)
        val FAILURE = StepFeedback(success = false)

        fun withNotes(
            success: Boolean,
            notes: String,
        ) = StepFeedback(success, notes)
    }
}
