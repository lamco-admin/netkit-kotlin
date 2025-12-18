package io.lamco.netkit.advisor.intelligent

import io.lamco.netkit.advisor.model.NetworkType
import io.lamco.netkit.advisor.troubleshooting.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class ContextAwareRecommenderTest {

    private val recommender = ContextAwareRecommender()

    // ========================================
    // Personalized Advice Tests
    // ========================================

    @Test
    fun `get personalized advice for beginner`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.BEGINNER,
            networkType = NetworkType.HOME_BASIC
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.isNotEmpty())
        assertTrue(advice.recommendations.all { it.effort == Difficulty.EASY })
    }

    @Test
    fun `get personalized advice for intermediate`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.SMALL_OFFICE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.isNotEmpty())
        assertTrue(advice.recommendations.all {
            it.effort.ordinal <= Difficulty.MEDIUM.ordinal
        })
    }

    @Test
    fun `get personalized advice for advanced`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.ENTERPRISE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.isNotEmpty())
        // Advanced can get hard recommendations
        assertTrue(advice.recommendations.any { it.effort == Difficulty.HARD })
    }

    @Test
    fun `get personalized advice for expert`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.EXPERT,
            networkType = NetworkType.ENTERPRISE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.isNotEmpty())
        // Expert should get cutting-edge recommendations
        assertTrue(advice.recommendations.any { it.effort == Difficulty.HARD })
    }

    // ========================================
    // Expertise Level Tests
    // ========================================

    @Test
    fun `beginner gets simple security recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.BEGINNER,
            networkType = NetworkType.HOME_BASIC
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("WPA3", ignoreCase = true) ||
            it.title.contains("password", ignoreCase = true)
        })
    }

    @Test
    fun `intermediate gets channel optimization recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("channel", ignoreCase = true) ||
            it.title.contains("band steering", ignoreCase = true)
        })
    }

    @Test
    fun `advanced gets VLAN recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.ENTERPRISE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("VLAN", ignoreCase = true) ||
            it.title.contains("802.1X", ignoreCase = true)
        })
    }

    @Test
    fun `expert gets WiFi 6E recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.EXPERT,
            networkType = NetworkType.ENTERPRISE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("WiFi 6", ignoreCase = true) ||
            it.title.contains("RRM", ignoreCase = true)
        })
    }

    // ========================================
    // Network Type Tests
    // ========================================

    @Test
    fun `home network gets parental control recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("parental", ignoreCase = true) ||
            it.category == RecommendationCategory.CONFIGURATION
        })
    }

    @Test
    fun `small office gets guest network recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.SMALL_OFFICE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("guest", ignoreCase = true)
        })
    }

    @Test
    fun `enterprise gets NAC recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.ENTERPRISE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("NAC", ignoreCase = true) ||
            it.title.contains("Network Access Control", ignoreCase = true)
        })
    }

    @Test
    fun `retail gets PCI-DSS recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.RETAIL
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("PCI", ignoreCase = true) ||
            it.title.contains("POS", ignoreCase = true)
        })
    }

    @Test
    fun `hospitality gets captive portal recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOSPITALITY
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("portal", ignoreCase = true) ||
            it.title.contains("guest", ignoreCase = true)
        })
    }

    @Test
    fun `education gets CIPA recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.EDUCATION
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("CIPA", ignoreCase = true) ||
            it.title.contains("content filter", ignoreCase = true)
        })
    }

    @Test
    fun `healthcare gets HIPAA recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.HEALTHCARE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("HIPAA", ignoreCase = true)
        })
    }

    @Test
    fun `public venue gets high density recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.PUBLIC_VENUE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("density", ignoreCase = true) ||
            it.title.contains("capacity", ignoreCase = true)
        })
    }

    // ========================================
    // Goal-Based Recommendations Tests
    // ========================================

    @Test
    fun `security goal provides security recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC,
            goals = listOf("Improve security")
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.category == RecommendationCategory.SECURITY ||
            it.title.contains("security", ignoreCase = true)
        })
    }

    @Test
    fun `coverage goal provides coverage recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC,
            goals = listOf("Better coverage")
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("coverage", ignoreCase = true) ||
            it.title.contains("placement", ignoreCase = true)
        })
    }

    @Test
    fun `speed goal provides performance recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC,
            goals = listOf("Faster speeds")
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.category == RecommendationCategory.PERFORMANCE ||
            it.title.contains("performance", ignoreCase = true) ||
            it.title.contains("speed", ignoreCase = true)
        })
    }

    @Test
    fun `guest network goal provides guest setup recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.SMALL_OFFICE,
            goals = listOf("Set up guest network")
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.recommendations.any {
            it.title.contains("guest", ignoreCase = true)
        })
    }

    // ========================================
    // Constraint Tests
    // ========================================

    @Test
    fun `budget constraint filters out expensive recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.ENTERPRISE,
            constraints = listOf("Limited budget")
        )

        val advice = recommender.getPersonalizedAdvice(context)

        // Should filter out hard (expensive) recommendations
        assertTrue(advice.recommendations.none { it.effort == Difficulty.HARD })
    }

    @Test
    fun `time constraint filters appropriately`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC,
            constraints = listOf("Limited time")
        )

        val advice = recommender.getPersonalizedAdvice(context)

        // Should prefer easy recommendations
        assertTrue(advice.recommendations.any { it.effort == Difficulty.EASY })
    }

    @Test
    fun `no hardware constraint filters hardware recommendations`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC,
            constraints = listOf("No hardware changes")
        )

        val advice = recommender.getPersonalizedAdvice(context)

        // Should not recommend adding hardware
        assertFalse(advice.recommendations.any {
            it.description.contains("add", ignoreCase = true) &&
            it.description.contains("AP", ignoreCase = true)
        })
    }

    // ========================================
    // Prioritization Tests
    // ========================================

    @Test
    fun `recommendations are sorted by priority`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC
        )

        val advice = recommender.getPersonalizedAdvice(context)

        val priorities = advice.recommendations.map { it.priority }
        assertEquals(priorities, priorities.sortedDescending())
    }

    @Test
    fun `quick wins are high impact and easy`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC
        )

        val advice = recommender.getPersonalizedAdvice(context)

        advice.quickWins.forEach { quickWin ->
            assertEquals(Difficulty.EASY, quickWin.effort)
            assertTrue(quickWin.impact in listOf(Impact.MEDIUM, Impact.HIGH))
        }
    }

    @Test
    fun `long term improvements are hard and high impact`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.ADVANCED,
            networkType = NetworkType.ENTERPRISE
        )

        val advice = recommender.getPersonalizedAdvice(context)

        advice.longTermImprovements.forEach { improvement ->
            assertEquals(Difficulty.HARD, improvement.effort)
            assertEquals(Impact.HIGH, improvement.impact)
        }
    }

    @Test
    fun `quick wins limited to 3 items`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.INTERMEDIATE,
            networkType = NetworkType.HOME_BASIC
        )

        val advice = recommender.getPersonalizedAdvice(context)

        assertTrue(advice.quickWins.size <= 3)
    }

    @Test
    fun `recommendations have implementation steps`() {
        val context = AdviceContext(
            userLevel = UserExpertiseLevel.BEGINNER,
            networkType = NetworkType.HOME_BASIC
        )

        val advice = recommender.getPersonalizedAdvice(context)

        advice.recommendations.forEach { rec ->
            assertTrue(rec.steps.isNotEmpty(), "Recommendation '${rec.title}' missing steps")
        }
    }
}
