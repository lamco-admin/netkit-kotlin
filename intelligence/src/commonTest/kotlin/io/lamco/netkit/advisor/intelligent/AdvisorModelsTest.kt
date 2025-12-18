package io.lamco.netkit.advisor.intelligent

import io.lamco.netkit.advisor.model.NetworkType
import io.lamco.netkit.advisor.troubleshooting.Difficulty
import io.lamco.netkit.advisor.troubleshooting.Impact
import io.lamco.netkit.advisor.troubleshooting.Symptom
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class AdvisorModelsTest {
    // ========================================
    // UserExpertiseLevel Tests
    // ========================================

    @Test
    fun `user expertise level has display name`() {
        assertEquals("Beginner", UserExpertiseLevel.BEGINNER.displayName)
        assertEquals("Intermediate", UserExpertiseLevel.INTERMEDIATE.displayName)
        assertEquals("Advanced", UserExpertiseLevel.ADVANCED.displayName)
        assertEquals("Expert", UserExpertiseLevel.EXPERT.displayName)
    }

    @Test
    fun `beginner max difficulty is easy`() {
        assertEquals(Difficulty.EASY, UserExpertiseLevel.BEGINNER.maxDifficulty)
    }

    @Test
    fun `intermediate max difficulty is medium`() {
        assertEquals(Difficulty.MEDIUM, UserExpertiseLevel.INTERMEDIATE.maxDifficulty)
    }

    @Test
    fun `advanced max difficulty is hard`() {
        assertEquals(Difficulty.HARD, UserExpertiseLevel.ADVANCED.maxDifficulty)
    }

    @Test
    fun `expert max difficulty is hard`() {
        assertEquals(Difficulty.HARD, UserExpertiseLevel.EXPERT.maxDifficulty)
    }

    @Test
    fun `beginner does not show technical details`() {
        assertFalse(UserExpertiseLevel.BEGINNER.showTechnicalDetails)
    }

    @Test
    fun `intermediate shows technical details`() {
        assertTrue(UserExpertiseLevel.INTERMEDIATE.showTechnicalDetails)
    }

    @Test
    fun `advanced shows technical details`() {
        assertTrue(UserExpertiseLevel.ADVANCED.showTechnicalDetails)
    }

    @Test
    fun `expert shows technical details`() {
        assertTrue(UserExpertiseLevel.EXPERT.showTechnicalDetails)
    }

    // ========================================
    // Recommendation Tests
    // ========================================

    @Test
    fun `recommendation requires valid priority`() {
        assertThrows<IllegalArgumentException> {
            Recommendation(
                title = "Test",
                description = "Test",
                category = RecommendationCategory.SECURITY,
                priority = 11, // Invalid
                impact = Impact.HIGH,
                effort = Difficulty.MEDIUM,
            )
        }
    }

    @Test
    fun `recommendation priority is 1-10`() {
        val rec =
            Recommendation(
                title = "Test",
                description = "Test desc",
                category = RecommendationCategory.PERFORMANCE,
                priority = 5,
                impact = Impact.MEDIUM,
                effort = Difficulty.EASY,
            )

        assertTrue(rec.priority in 1..10)
    }

    @Test
    fun `recommendation summary includes category and priority`() {
        val rec =
            Recommendation(
                title = "Enable WPA3",
                description = "Upgrade to WPA3 encryption",
                category = RecommendationCategory.SECURITY,
                priority = 9,
                impact = Impact.HIGH,
                effort = Difficulty.MEDIUM,
            )

        val summary = rec.summary
        assertTrue(summary.contains("Security", ignoreCase = true))
        assertTrue(summary.contains("9"))
    }

    @Test
    fun `recommendation can have implementation steps`() {
        val rec =
            Recommendation(
                title = "Configure QoS",
                description = "Set up Quality of Service",
                category = RecommendationCategory.PERFORMANCE,
                priority = 7,
                impact = Impact.MEDIUM,
                effort = Difficulty.MEDIUM,
                steps = listOf("Step 1", "Step 2", "Step 3"),
            )

        assertEquals(3, rec.steps.size)
    }

    // ========================================
    // RecommendationCategory Tests
    // ========================================

    @Test
    fun `recommendation category has display name`() {
        assertEquals("Security", RecommendationCategory.SECURITY.displayName)
        assertEquals("Performance", RecommendationCategory.PERFORMANCE.displayName)
        assertEquals("Reliability", RecommendationCategory.RELIABILITY.displayName)
        assertEquals("Configuration", RecommendationCategory.CONFIGURATION.displayName)
        assertEquals("Optimization", RecommendationCategory.OPTIMIZATION.displayName)
        assertEquals("Troubleshooting", RecommendationCategory.TROUBLESHOOTING.displayName)
    }

    // ========================================
    // AdvisorRecommendations Tests
    // ========================================

    @Test
    fun `advisor recommendations has top recommendation`() {
        val recs =
            listOf(
                Recommendation("Low", "desc", RecommendationCategory.SECURITY, 3, Impact.LOW, Difficulty.EASY),
                Recommendation("High", "desc", RecommendationCategory.SECURITY, 9, Impact.HIGH, Difficulty.HARD),
            )

        val advice =
            AdvisorRecommendations(
                recommendations = recs.sortedByDescending { it.priority },
            )

        assertEquals("High", advice.topRecommendation?.title)
    }

    @Test
    fun `advisor recommendations calculates total issues`() {
        val advice =
            AdvisorRecommendations(
                recommendations = emptyList(),
                bestPracticeViolations = listOf("Issue 1", "Issue 2"),
                securityIssues = listOf("Security 1"),
            )

        assertEquals(3, advice.totalIssuesFound)
    }

    @Test
    fun `advisor recommendations summary is informative`() {
        val advice =
            AdvisorRecommendations(
                recommendations =
                    listOf(
                        Recommendation("Test", "desc", RecommendationCategory.SECURITY, 8, Impact.HIGH, Difficulty.MEDIUM),
                    ),
                bestPracticeViolations = listOf("Issue 1"),
            )

        val summary = advice.summary
        assertTrue(summary.contains("1 recommendation"))
        assertTrue(summary.contains("1 issue"))
    }

    // ========================================
    // NetworkAssessment Tests
    // ========================================

    @Test
    fun `network assessment requires valid score`() {
        assertThrows<IllegalArgumentException> {
            NetworkAssessment(
                networkType = NetworkType.HOME_BASIC,
                overallScore = 101, // Invalid
                scores = CategoryScores(80, 75, 70, 85),
                strengths = emptyList(),
                weaknesses = emptyList(),
                risks = emptyList(),
            )
        }
    }

    @Test
    fun `network assessment score is 0-100`() {
        val assessment =
            NetworkAssessment(
                networkType = NetworkType.HOME_BASIC,
                overallScore = 85,
                scores = CategoryScores(90, 85, 80, 85),
                strengths = listOf("Good security"),
                weaknesses = emptyList(),
                risks = emptyList(),
            )

        assertTrue(assessment.overallScore in 0..100)
    }

    @Test
    fun `network assessment determines health level`() {
        val excellent =
            NetworkAssessment(
                networkType = NetworkType.HOME_BASIC,
                overallScore = 95,
                scores = CategoryScores(90, 90, 90, 90),
                strengths = emptyList(),
                weaknesses = emptyList(),
                risks = emptyList(),
            )
        assertEquals(HealthLevel.EXCELLENT, excellent.healthLevel)

        val poor =
            NetworkAssessment(
                networkType = NetworkType.HOME_BASIC,
                overallScore = 50,
                scores = CategoryScores(50, 50, 50, 50),
                strengths = emptyList(),
                weaknesses = emptyList(),
                risks = emptyList(),
            )
        assertEquals(HealthLevel.POOR, poor.healthLevel)
    }

    @Test
    fun `network assessment summary includes type and score`() {
        val assessment =
            NetworkAssessment(
                networkType = NetworkType.ENTERPRISE,
                overallScore = 80,
                scores = CategoryScores(85, 80, 75, 80),
                strengths = emptyList(),
                weaknesses = emptyList(),
                risks = emptyList(),
            )

        val summary = assessment.summary
        assertTrue(summary.contains("Enterprise", ignoreCase = true))
        assertTrue(summary.contains("80"))
    }

    // ========================================
    // CategoryScores Tests
    // ========================================

    @Test
    fun `category scores require valid ranges`() {
        assertThrows<IllegalArgumentException> {
            CategoryScores(
                security = 101, // Invalid
                performance = 80,
                reliability = 75,
                coverage = 85,
            )
        }
    }

    @Test
    fun `category scores all in 0-100`() {
        val scores =
            CategoryScores(
                security = 85,
                performance = 80,
                reliability = 75,
                coverage = 90,
            )

        assertTrue(scores.security in 0..100)
        assertTrue(scores.performance in 0..100)
        assertTrue(scores.reliability in 0..100)
        assertTrue(scores.coverage in 0..100)
    }

    @Test
    fun `category scores calculates average`() {
        val scores =
            CategoryScores(
                security = 80,
                performance = 80,
                reliability = 80,
                coverage = 80,
            )

        assertEquals(80, scores.average)
    }

    // ========================================
    // HealthLevel Tests
    // ========================================

    @Test
    fun `health level has display name`() {
        assertEquals("Excellent", HealthLevel.EXCELLENT.displayName)
        assertEquals("Good", HealthLevel.GOOD.displayName)
        assertEquals("Fair", HealthLevel.FAIR.displayName)
        assertEquals("Poor", HealthLevel.POOR.displayName)
        assertEquals("Critical", HealthLevel.CRITICAL.displayName)
    }

    // ========================================
    // WizardStep Tests
    // ========================================

    @Test
    fun `wizard step with options is choice step`() {
        val step =
            WizardStep(
                step = 1,
                title = "Choose option",
                description = "Select one",
                prompt = "Choose:",
                options = listOf("A", "B", "C"),
            )

        assertTrue(step.isChoice)
    }

    @Test
    fun `wizard step without options is not choice step`() {
        val step =
            WizardStep(
                step = 1,
                title = "Enter value",
                description = "Type value",
                prompt = "Enter:",
            )

        assertFalse(step.isChoice)
    }

    @Test
    fun `wizard step summary includes step number`() {
        val step =
            WizardStep(
                step = 3,
                title = "Security Settings",
                description = "Configure security",
                prompt = "Select:",
            )

        assertTrue(step.summary.contains("Step 3"))
        assertTrue(step.summary.contains("Security Settings"))
    }

    // ========================================
    // WizardSession Tests
    // ========================================

    @Test
    fun `wizard session starts at step 0`() {
        val session =
            WizardSession(
                wizardType = WizardType.NETWORK_SETUP,
                currentStep = 0,
                steps =
                    listOf(
                        WizardStep(1, "Step 1", "Desc", "Prompt"),
                    ),
            )

        assertEquals(0, session.currentStep)
    }

    @Test
    fun `wizard session current step is null when complete`() {
        val session =
            WizardSession(
                wizardType = WizardType.NETWORK_SETUP,
                currentStep = 2, // Beyond last step
                steps =
                    listOf(
                        WizardStep(1, "Step 1", "Desc", "Prompt"),
                    ),
            )

        assertNull(session.current)
        assertTrue(session.isComplete)
    }

    @Test
    fun `wizard session calculates progress`() {
        val session =
            WizardSession(
                wizardType = WizardType.NETWORK_SETUP,
                currentStep = 2,
                steps =
                    listOf(
                        WizardStep(1, "S1", "D", "P"),
                        WizardStep(2, "S2", "D", "P"),
                        WizardStep(3, "S3", "D", "P"),
                        WizardStep(4, "S4", "D", "P"),
                    ),
            )

        assertEquals(50, session.progress) // 2/4 = 50%
    }

    @Test
    fun `wizard session next advances step`() {
        val session =
            WizardSession(
                wizardType = WizardType.NETWORK_SETUP,
                currentStep = 0,
                steps =
                    listOf(
                        WizardStep(1, "S1", "D", "P"),
                        WizardStep(2, "S2", "D", "P"),
                    ),
            )

        val next = session.next("Response")

        assertEquals(1, next.currentStep)
        assertEquals("Response", next.responses[0])
    }

    @Test
    fun `wizard session previous goes back`() {
        val session =
            WizardSession(
                wizardType = WizardType.NETWORK_SETUP,
                currentStep = 2,
                steps =
                    listOf(
                        WizardStep(1, "S1", "D", "P"),
                        WizardStep(2, "S2", "D", "P"),
                        WizardStep(3, "S3", "D", "P"),
                    ),
            )

        val prev = session.previous()

        assertEquals(1, prev.currentStep)
    }

    @Test
    fun `wizard session cannot go before step 0`() {
        val session =
            WizardSession(
                wizardType = WizardType.NETWORK_SETUP,
                currentStep = 0,
                steps =
                    listOf(
                        WizardStep(1, "S1", "D", "P"),
                    ),
            )

        val prev = session.previous()

        assertEquals(0, prev.currentStep)
    }

    // ========================================
    // WizardType Tests
    // ========================================

    @Test
    fun `wizard type has display name`() {
        assertEquals("Network setup", WizardType.NETWORK_SETUP.displayName)
        assertEquals("Troubleshooting", WizardType.TROUBLESHOOTING.displayName)
        assertEquals("Security setup", WizardType.SECURITY_SETUP.displayName)
        assertEquals("Performance tuning", WizardType.PERFORMANCE_TUNING.displayName)
        assertEquals("Guest network", WizardType.GUEST_NETWORK.displayName)
        assertEquals("Mesh setup", WizardType.MESH_SETUP.displayName)
    }

    // ========================================
    // AdviceContext Tests
    // ========================================

    @Test
    fun `advice context with minimal data`() {
        val context =
            AdviceContext(
                userLevel = UserExpertiseLevel.BEGINNER,
                networkType = NetworkType.HOME_BASIC,
            )

        assertEquals(UserExpertiseLevel.BEGINNER, context.userLevel)
        assertEquals(NetworkType.HOME_BASIC, context.networkType)
        assertTrue(context.currentIssues.isEmpty())
        assertTrue(context.goals.isEmpty())
        assertTrue(context.constraints.isEmpty())
    }

    @Test
    fun `advice context with full data`() {
        val context =
            AdviceContext(
                userLevel = UserExpertiseLevel.ADVANCED,
                networkType = NetworkType.ENTERPRISE,
                currentIssues = listOf(Symptom.SlowSpeed(10.0, 100.0)),
                goals = listOf("Better security", "Higher performance"),
                constraints = listOf("Limited budget"),
            )

        assertEquals(UserExpertiseLevel.ADVANCED, context.userLevel)
        assertEquals(NetworkType.ENTERPRISE, context.networkType)
        assertEquals(1, context.currentIssues.size)
        assertEquals(2, context.goals.size)
        assertEquals(1, context.constraints.size)
    }
}
