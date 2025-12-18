package io.lamco.netkit.advisor.knowledge

import io.lamco.netkit.advisor.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class BestPracticesKnowledgeBaseTest {

    private fun createTestRule(
        category: RuleCategory = RuleCategory.SECURITY,
        severity: RuleSeverity = RuleSeverity.MUST,
        networkTypes: List<NetworkType> = emptyList()
    ): BestPracticeRule {
        return BestPracticeRule(
            category = category,
            title = "Test Rule",
            description = "Test description",
            rationale = "Test rationale",
            severity = severity,
            source = "Test",
            applicability = RuleApplicability(networkTypes = networkTypes)
        )
    }

    @Test
    fun `create knowledge base with rules`() {
        val rules = listOf(
            createTestRule(),
            createTestRule(category = RuleCategory.PERFORMANCE)
        )

        val kb = BestPracticesKnowledgeBase(rules)
        assertNotNull(kb)
    }

    @Test
    fun `empty rules list throws exception`() {
        assertThrows<IllegalArgumentException> {
            BestPracticesKnowledgeBase(emptyList())
        }
    }

    @Test
    fun `get best practices for network type`() {
        val rules = listOf(
            createTestRule(networkTypes = listOf(NetworkType.ENTERPRISE)),
            createTestRule(networkTypes = listOf(NetworkType.HOME_BASIC)),
            createTestRule(networkTypes = emptyList())  // Universal
        )

        val kb = BestPracticesKnowledgeBase(rules)
        val enterpriseRules = kb.getBestPractices(NetworkType.ENTERPRISE)

        assertEquals(2, enterpriseRules.size)  // Enterprise-specific + universal
    }

    @Test
    fun `rules sorted by severity`() {
        val rules = listOf(
            createTestRule(severity = RuleSeverity.MAY),
            createTestRule(severity = RuleSeverity.MUST),
            createTestRule(severity = RuleSeverity.SHOULD)
        )

        val kb = BestPracticesKnowledgeBase(rules)
        val sorted = kb.getBestPractices(NetworkType.ENTERPRISE)

        assertEquals(RuleSeverity.MUST, sorted[0].severity)
        assertEquals(RuleSeverity.SHOULD, sorted[1].severity)
        assertEquals(RuleSeverity.MAY, sorted[2].severity)
    }

    @Test
    fun `get rules by category`() {
        val rules = listOf(
            createTestRule(category = RuleCategory.SECURITY),
            createTestRule(category = RuleCategory.SECURITY),
            createTestRule(category = RuleCategory.PERFORMANCE)
        )

        val kb = BestPracticesKnowledgeBase(rules)
        val securityRules = kb.getRulesByCategory(RuleCategory.SECURITY)

        assertEquals(2, securityRules.size)
    }

    @Test
    fun `get critical rules returns only MUST severity`() {
        val rules = listOf(
            createTestRule(severity = RuleSeverity.MUST),
            createTestRule(severity = RuleSeverity.SHOULD),
            createTestRule(severity = RuleSeverity.MAY)
        )

        val kb = BestPracticesKnowledgeBase(rules)
        val critical = kb.getCriticalRules(NetworkType.ENTERPRISE)

        assertEquals(1, critical.size)
        assertEquals(RuleSeverity.MUST, critical[0].severity)
    }

    @Test
    fun `get recommended rules returns only SHOULD severity`() {
        val rules = listOf(
            createTestRule(severity = RuleSeverity.MUST),
            createTestRule(severity = RuleSeverity.SHOULD),
            createTestRule(severity = RuleSeverity.MAY)
        )

        val kb = BestPracticesKnowledgeBase(rules)
        val recommended = kb.getRecommendedRules(NetworkType.ENTERPRISE)

        assertEquals(1, recommended.size)
        assertEquals(RuleSeverity.SHOULD, recommended[0].severity)
    }

    @Test
    fun `get optional rules returns only MAY severity`() {
        val rules = listOf(
            createTestRule(severity = RuleSeverity.MUST),
            createTestRule(severity = RuleSeverity.SHOULD),
            createTestRule(severity = RuleSeverity.MAY)
        )

        val kb = BestPracticesKnowledgeBase(rules)
        val optional = kb.getOptionalRules(NetworkType.ENTERPRISE)

        assertEquals(1, optional.size)
        assertEquals(RuleSeverity.MAY, optional[0].severity)
    }

    @Test
    fun `validate configuration returns result`() {
        val kb = BestPracticesKnowledgeBase.create()
        val context = NetworkContext(
            networkType = NetworkType.ENTERPRISE,
            wifiGeneration = WifiGeneration.WIFI_6,
            securityType = "WPA3-Enterprise"
        )

        val result = kb.validate(context)
        assertNotNull(result)
        assertTrue(result.complianceScore >= 0.0)
        assertTrue(result.complianceScore <= 100.0)
    }

    @Test
    fun `add custom rule creates new knowledge base`() {
        val kb = BestPracticesKnowledgeBase(listOf(createTestRule()))
        val customRule = createTestRule(category = RuleCategory.CONFIGURATION)

        val newKb = kb.addCustomRule(customRule)
        assertNotEquals(kb, newKb)
    }

    @Test
    fun `get summary returns statistics`() {
        val rules = listOf(
            createTestRule(category = RuleCategory.SECURITY),
            createTestRule(category = RuleCategory.PERFORMANCE),
            createTestRule(category = RuleCategory.SECURITY)
        )

        val kb = BestPracticesKnowledgeBase(rules)
        val summary = kb.getSummary()

        assertEquals(3, summary.totalRules)
        assertEquals(2, summary.rulesByCategory[RuleCategory.SECURITY])
        assertEquals(1, summary.rulesByCategory[RuleCategory.PERFORMANCE])
    }

    @Test
    fun `create default knowledge base has rules`() {
        val kb = BestPracticesKnowledgeBase.create()
        val summary = kb.getSummary()

        assertTrue(summary.totalRules > 0)
    }

    @Test
    fun `create PCI-DSS compliance knowledge base`() {
        val kb = BestPracticesKnowledgeBase.forCompliance(ComplianceStandard.PCI_DSS)
        val summary = kb.getSummary()

        assertTrue(summary.totalRules > 0)
    }

    @Test
    fun `validation result has compliance level`() {
        val kb = BestPracticesKnowledgeBase.create()
        val context = NetworkContext(
            networkType = NetworkType.ENTERPRISE,
            wifiGeneration = WifiGeneration.WIFI_6,
            securityType = "WPA3-Enterprise"
        )

        val result = kb.validate(context)
        assertNotNull(result.complianceLevel)
    }

    @Test
    fun `validation result summary includes network type`() {
        val kb = BestPracticesKnowledgeBase.create()
        val context = NetworkContext(
            networkType = NetworkType.ENTERPRISE,
            wifiGeneration = WifiGeneration.WIFI_6,
            securityType = "WPA2-PSK"
        )

        val result = kb.validate(context)
        assertTrue(result.summary.contains("ENTERPRISE") || result.summary.contains("Enterprise"))
    }

    @Test
    fun `compliance level display names`() {
        assertEquals("Excellent", ComplianceLevel.EXCELLENT.displayName)
        assertEquals("Good", ComplianceLevel.GOOD.displayName)
        assertEquals("Fair", ComplianceLevel.FAIR.displayName)
        assertEquals("Poor", ComplianceLevel.POOR.displayName)
        assertEquals("Failing", ComplianceLevel.FAILING.displayName)
    }

    @Test
    fun `compliance standard display names`() {
        assertEquals("PCI-DSS", ComplianceStandard.PCI_DSS.displayName)
        assertEquals("HIPAA", ComplianceStandard.HIPAA.displayName)
        assertEquals("GDPR", ComplianceStandard.GDPR.displayName)
    }
}
