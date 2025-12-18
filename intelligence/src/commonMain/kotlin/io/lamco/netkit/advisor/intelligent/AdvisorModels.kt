package io.lamco.netkit.advisor.intelligent

import io.lamco.netkit.advisor.knowledge.NetworkContext
import io.lamco.netkit.advisor.model.*
import io.lamco.netkit.advisor.troubleshooting.*

/**
 * User expertise level for context-aware recommendations
 */
enum class UserExpertiseLevel {
    /** Novice user with minimal technical knowledge */
    BEGINNER,

    /** Intermediate user with basic networking knowledge */
    INTERMEDIATE,

    /** Advanced user comfortable with technical concepts */
    ADVANCED,

    /** Expert/professional with deep networking expertise */
    EXPERT;

    /**
     * Display name
     */
    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    /**
     * Maximum recommended solution difficulty
     */
    val maxDifficulty: Difficulty
        get() = when (this) {
            BEGINNER -> Difficulty.EASY
            INTERMEDIATE -> Difficulty.MEDIUM
            ADVANCED -> Difficulty.HARD
            EXPERT -> Difficulty.HARD
        }

    /**
     * Whether technical details should be shown
     */
    val showTechnicalDetails: Boolean
        get() = this != BEGINNER
}

/**
 * Comprehensive advice from intelligent advisor
 *
 * @property recommendations List of prioritized recommendations
 * @property bestPracticeViolations Best practice violations detected
 * @property optimizationSuggestions Performance optimization suggestions
 * @property securityIssues Security concerns identified
 * @property quickWins Easy improvements with high impact
 * @property longTermImprovements Strategic improvements
 */
data class AdvisorRecommendations(
    val recommendations: List<Recommendation>,
    val bestPracticeViolations: List<String> = emptyList(),
    val optimizationSuggestions: List<String> = emptyList(),
    val securityIssues: List<String> = emptyList(),
    val quickWins: List<Recommendation> = emptyList(),
    val longTermImprovements: List<Recommendation> = emptyList()
) {
    /**
     * Top priority recommendation
     */
    val topRecommendation: Recommendation?
        get() = recommendations.firstOrNull()

    /**
     * Count of all issues and suggestions
     */
    val totalIssuesFound: Int
        get() = bestPracticeViolations.size + securityIssues.size

    /**
     * Summary of recommendations
     */
    val summary: String
        get() = "${recommendations.size} recommendation(s), ${totalIssuesFound} issue(s) found"
}

/**
 * Individual recommendation
 *
 * @property title Recommendation title
 * @property description Detailed description
 * @property category Category (Security, Performance, Reliability, Configuration)
 * @property priority Priority level (1-10, 10 = highest)
 * @property impact Expected impact
 * @property effort Required effort
 * @property steps Implementation steps
 */
data class Recommendation(
    val title: String,
    val description: String,
    val category: RecommendationCategory,
    val priority: Int,
    val impact: Impact,
    val effort: Difficulty,
    val steps: List<String> = emptyList()
) {
    init {
        require(priority in 1..10) { "Priority must be 1-10" }
    }

    /**
     * Summary of recommendation
     */
    val summary: String
        get() = "[$category] $title (Priority: $priority, Impact: $impact, Effort: $effort)"
}

/**
 * Recommendation category
 */
enum class RecommendationCategory {
    SECURITY,
    PERFORMANCE,
    RELIABILITY,
    CONFIGURATION,
    OPTIMIZATION,
    TROUBLESHOOTING;

    /**
     * Display name
     */
    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Network assessment result
 *
 * @property networkType Detected network type
 * @property overallScore Overall health score (0-100)
 * @property scores Detailed category scores
 * @property strengths Identified strengths
 * @property weaknesses Identified weaknesses
 * @property risks Potential risks
 */
data class NetworkAssessment(
    val networkType: NetworkType,
    val overallScore: Int,
    val scores: CategoryScores,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val risks: List<String>
) {
    init {
        require(overallScore in 0..100) { "Overall score must be 0-100" }
    }

    /**
     * Health level classification
     */
    val healthLevel: HealthLevel
        get() = when {
            overallScore >= 90 -> HealthLevel.EXCELLENT
            overallScore >= 75 -> HealthLevel.GOOD
            overallScore >= 60 -> HealthLevel.FAIR
            overallScore >= 40 -> HealthLevel.POOR
            else -> HealthLevel.CRITICAL
        }

    /**
     * Summary of assessment
     */
    val summary: String
        get() = "$networkType network: $healthLevel ($overallScore/100)"
}

/**
 * Category scores for assessment
 *
 * @property security Security score (0-100)
 * @property performance Performance score (0-100)
 * @property reliability Reliability score (0-100)
 * @property coverage Coverage score (0-100)
 */
data class CategoryScores(
    val security: Int,
    val performance: Int,
    val reliability: Int,
    val coverage: Int
) {
    init {
        require(security in 0..100) { "Security score must be 0-100" }
        require(performance in 0..100) { "Performance score must be 0-100" }
        require(reliability in 0..100) { "Reliability score must be 0-100" }
        require(coverage in 0..100) { "Coverage score must be 0-100" }
    }

    /**
     * Average score across all categories
     */
    val average: Int
        get() = (security + performance + reliability + coverage) / 4
}

/**
 * Network health level
 */
enum class HealthLevel {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    CRITICAL;

    /**
     * Display name
     */
    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Guided setup wizard step
 *
 * @property step Step number
 * @property title Step title
 * @property description Step description
 * @property prompt User prompt/question
 * @property options Available options (if applicable)
 * @property defaultValue Suggested default value
 * @property validation Validation rules
 */
data class WizardStep(
    val step: Int,
    val title: String,
    val description: String,
    val prompt: String,
    val options: List<String> = emptyList(),
    val defaultValue: String? = null,
    val validation: String? = null
) {
    /**
     * Whether this is a choice step (multiple options)
     */
    val isChoice: Boolean
        get() = options.isNotEmpty()

    /**
     * Summary of step
     */
    val summary: String
        get() = "Step $step: $title"
}

/**
 * Interactive wizard session state
 *
 * @property wizardType Type of wizard
 * @property currentStep Current step index
 * @property steps All wizard steps
 * @property responses User responses so far
 * @property context Accumulated context
 */
data class WizardSession(
    val wizardType: WizardType,
    val currentStep: Int,
    val steps: List<WizardStep>,
    val responses: Map<Int, String> = emptyMap(),
    val context: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Current wizard step
     */
    val current: WizardStep?
        get() = steps.getOrNull(currentStep)

    /**
     * Whether wizard is complete
     */
    val isComplete: Boolean
        get() = currentStep >= steps.size

    /**
     * Progress percentage
     */
    val progress: Int
        get() = if (steps.isEmpty()) 100 else ((currentStep.toDouble() / steps.size) * 100).toInt()

    /**
     * Advance to next step with response
     */
    fun next(response: String): WizardSession {
        return copy(
            currentStep = currentStep + 1,
            responses = responses + (currentStep to response)
        )
    }

    /**
     * Go back to previous step
     */
    fun previous(): WizardSession {
        return if (currentStep > 0) {
            copy(currentStep = currentStep - 1)
        } else {
            this
        }
    }
}

/**
 * Wizard type classification
 */
enum class WizardType {
    /** Initial network setup wizard */
    NETWORK_SETUP,

    /** Troubleshooting wizard */
    TROUBLESHOOTING,

    /** Security configuration wizard */
    SECURITY_SETUP,

    /** Performance optimization wizard */
    PERFORMANCE_TUNING,

    /** Guest network setup wizard */
    GUEST_NETWORK,

    /** Mesh network setup wizard */
    MESH_SETUP;

    /**
     * Display name
     */
    val displayName: String
        get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * Advice context for personalized recommendations
 *
 * @property userLevel User expertise level
 * @property networkType Network type
 * @property currentIssues Current network issues (if any)
 * @property goals User goals and priorities
 * @property constraints Constraints and limitations
 */
data class AdviceContext(
    val userLevel: UserExpertiseLevel,
    val networkType: NetworkType,
    val currentIssues: List<Symptom> = emptyList(),
    val goals: List<String> = emptyList(),
    val constraints: List<String> = emptyList()
)
