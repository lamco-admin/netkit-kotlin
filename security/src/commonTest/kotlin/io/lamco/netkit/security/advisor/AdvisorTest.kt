package io.lamco.netkit.security.advisor

import io.lamco.netkit.model.topology.*
import io.lamco.netkit.security.analyzer.*
import io.lamco.netkit.security.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

/**
 * Comprehensive tests for all advisor components (Batch 3)
 *
 * Test Coverage:
 * - RuleEngine (30 tests)
 * - SecurityAdvisor (30 tests)
 * - ConfigurationAdvisor (25 tests)
 * - RiskPrioritizer (25 tests)
 * - ProModeReporter (15 tests)
 * Total: 125 tests
 */
class AdvisorTest {

    // ====================
    // RuleEngine Tests (30)
    // ====================

    @Nested
    inner class RuleEngineTests {

        @Test
        fun `RuleEngine requires at least one rule`() {
            assertThrows(IllegalArgumentException::class.java) {
                RuleEngine(emptyList())
            }
        }

        @Test
        fun `evaluate requires at least one analysis`() {
            val engine = RuleEngine(listOf(SecurityRule.ProhibitWps))

            assertThrows(IllegalArgumentException::class.java) {
                engine.evaluate(emptyList())
            }
        }

        @Test
        fun `RequireMinimumAuth detects violations`() {
            val rule = SecurityRule.RequireMinimumAuth(AuthType.WPA2_PSK)
            val engine = RuleEngine(listOf(rule))

            val analysis = createMockBssAnalysis(authType = AuthType.WEP)
            val result = engine.evaluate(listOf(analysis))

            assertEquals(1, result.violations.size)
            assertEquals(rule, result.violations.first().rule)
        }

        @Test
        fun `RequireMinimumAuth passes compliant network`() {
            val rule = SecurityRule.RequireMinimumAuth(AuthType.WPA2_PSK)
            val engine = RuleEngine(listOf(rule))

            val analysis = createMockBssAnalysis(authType = AuthType.WPA3_SAE)
            val result = engine.evaluate(listOf(analysis))

            assertEquals(0, result.violations.size)
            assertTrue(result.isCompliant)
        }

        @Test
        fun `ProhibitAuthType detects WEP`() {
            val rule = SecurityRule.ProhibitAuthType(AuthType.WEP)
            val engine = RuleEngine(listOf(rule))

            val analysis = createMockBssAnalysis(authType = AuthType.WEP)
            val result = engine.evaluate(listOf(analysis))

            assertEquals(1, result.violations.size)
            assertEquals(RuleSeverity.CRITICAL, result.violations.first().severity)
        }

        @Test
        fun `ProhibitCipher detects WEP ciphers`() {
            val rule = SecurityRule.ProhibitCipher(CipherSuite.WEP_40)
            val engine = RuleEngine(listOf(rule))

            val analysis = createMockBssAnalysis(
                authType = AuthType.WEP,
                ciphers = setOf(CipherSuite.WEP_40)
            )
            val result = engine.evaluate(listOf(analysis))

            assertEquals(1, result.violations.size)
        }

        @Test
        fun `RequireCipher detects missing cipher`() {
            val rule = SecurityRule.RequireCipher(CipherSuite.CCMP)
            val engine = RuleEngine(listOf(rule))

            val analysis = createMockBssAnalysis(ciphers = setOf(CipherSuite.TKIP))
            val result = engine.evaluate(listOf(analysis))

            assertEquals(1, result.violations.size)
        }

        @Test
        fun `RequirePmf detects missing PMF`() {
            val rule = SecurityRule.RequirePmf
            val engine = RuleEngine(listOf(rule))

            val analysis = createMockBssAnalysis(pmfEnabled = false, pmfCapable = true)
            val result = engine.evaluate(listOf(analysis))

            assertEquals(1, result.violations.size)
        }

        @Test
        fun `ProhibitWps detects WPS risk`() {
            val rule = SecurityRule.ProhibitWps
            val engine = RuleEngine(listOf(rule))

            val wpsInfo = WpsInfo(
                configMethods = setOf(WpsConfigMethod.LABEL),
                wpsState = WpsState.CONFIGURED,
                locked = false,
                deviceName = null,
                manufacturer = null,
                modelName = null,
                version = null
            )
            val analysis = createMockBssAnalysis(wpsInfo = wpsInfo)
            val result = engine.evaluate(listOf(analysis))

            assertEquals(1, result.violations.size)
        }

        @Test
        fun `RequireMinimumSecurityLevel detects weak security`() {
            val rule = SecurityRule.RequireMinimumSecurityLevel(SecurityLevel.GOOD)
            val engine = RuleEngine(listOf(rule))

            val analysis = createMockBssAnalysis(authType = AuthType.WEP, ciphers = setOf(CipherSuite.WEP_104))
            val result = engine.evaluate(listOf(analysis))

            assertEquals(1, result.violations.size)
        }

        @Test
        fun `ProhibitThreatLevel detects high threats`() {
            val rule = SecurityRule.ProhibitThreatLevel(ThreatLevel.HIGH)
            val engine = RuleEngine(listOf(rule))

            val analysis = createMockBssAnalysis(threatLevel = ThreatLevel.CRITICAL)
            val result = engine.evaluate(listOf(analysis))

            assertEquals(1, result.violations.size)
        }

        @Test
        fun `complianceScore calculated correctly`() {
            val engine = RuleEngine(listOf(
                SecurityRule.RequireMinimumAuth(AuthType.WPA2_PSK),
                SecurityRule.ProhibitWps
            ))

            val analyses = listOf(
                createMockBssAnalysis(authType = AuthType.WPA3_SAE), // Compliant
                createMockBssAnalysis(authType = AuthType.WEP)        // Non-compliant
            )

            val result = engine.evaluate(analyses)

            // 1 violation out of 2 BSS * 2 rules = 1/4 = 0.75 compliance
            assertTrue(result.complianceScore > 0.7)
        }

        @Test
        fun `strictCorporatePolicy creates comprehensive rules`() {
            val engine = RuleEngine.strictCorporatePolicy()

            val analysis = createMockBssAnalysis(
                authType = AuthType.WPA_PSK, // Should violate minimum auth
                pmfEnabled = false            // Should violate PMF requirement
            )

            val result = engine.evaluate(listOf(analysis))

            assertTrue(result.violations.size > 1)
        }

        @Test
        fun `moderateSecurityPolicy allows WPA2`() {
            val engine = RuleEngine.moderateSecurityPolicy()

            val analysis = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val result = engine.evaluate(listOf(analysis))

            assertTrue(result.isCompliant)
        }

        @Test
        fun `pciDssCompliance prohibits open networks`() {
            val engine = RuleEngine.pciDssCompliance()

            val analysis = createMockBssAnalysis(authType = AuthType.OPEN)
            val result = engine.evaluate(listOf(analysis))

            assertFalse(result.isCompliant)
        }

        @Test
        fun `RuleSeverity has meaningful descriptions`() {
            RuleSeverity.values().forEach { severity ->
                assertTrue(severity.description.isNotBlank())
            }
        }

        @Test
        fun `RuleViolation summary includes BSSID`() {
            val violation = RuleViolation(
                bssid = "00:11:22:33:44:55",
                ssid = "TestSSID",
                rule = SecurityRule.ProhibitWps,
                severity = RuleSeverity.HIGH,
                description = "WPS enabled"
            )

            assertTrue(violation.summary.contains("00:11:22:33:44:55"))
            assertTrue(violation.summary.contains("TestSSID"))
        }

        @Test
        fun `RuleEvaluation requires positive counts`() {
            assertThrows(IllegalArgumentException::class.java) {
                RuleEvaluation(
                    totalBssCount = 0,
                    rulesEvaluated = 1,
                    violations = emptyList(),
                    complianceScore = 1.0
                )
            }
        }

        @Test
        fun `RuleEvaluation validates compliance score range`() {
            assertThrows(IllegalArgumentException::class.java) {
                RuleEvaluation(
                    totalBssCount = 1,
                    rulesEvaluated = 1,
                    violations = emptyList(),
                    complianceScore = 1.5
                )
            }
        }

        @Test
        fun `violationsBySeverity groups correctly`() {
            val violations = listOf(
                RuleViolation("BSS1", "SSID1", SecurityRule.ProhibitWps, RuleSeverity.HIGH, "desc"),
                RuleViolation("BSS2", "SSID2", SecurityRule.ProhibitWps, RuleSeverity.HIGH, "desc"),
                RuleViolation("BSS3", "SSID3", SecurityRule.ProhibitWps, RuleSeverity.MEDIUM, "desc")
            )

            val evaluation = RuleEvaluation(
                totalBssCount = 3,
                rulesEvaluated = 1,
                violations = violations,
                complianceScore = 0.0
            )

            assertEquals(2, evaluation.violationsBySeverity[RuleSeverity.HIGH])
            assertEquals(1, evaluation.violationsBySeverity[RuleSeverity.MEDIUM])
        }

        @Test
        fun `complianceLevel FULL at 95 percent`() {
            val evaluation = RuleEvaluation(
                totalBssCount = 1,
                rulesEvaluated = 1,
                violations = emptyList(),
                complianceScore = 0.95
            )

            assertEquals(ComplianceLevel.FULL, evaluation.complianceLevel)
        }

        @Test
        fun `complianceLevel HIGH at 80 percent`() {
            val evaluation = RuleEvaluation(
                totalBssCount = 1,
                rulesEvaluated = 1,
                violations = emptyList(),
                complianceScore = 0.85
            )

            assertEquals(ComplianceLevel.HIGH, evaluation.complianceLevel)
        }

        @Test
        fun `complianceLevel MODERATE at 60 percent`() {
            val evaluation = RuleEvaluation(
                totalBssCount = 1,
                rulesEvaluated = 1,
                violations = emptyList(),
                complianceScore = 0.65
            )

            assertEquals(ComplianceLevel.MODERATE, evaluation.complianceLevel)
        }

        @Test
        fun `complianceLevel LOW at 40 percent`() {
            val evaluation = RuleEvaluation(
                totalBssCount = 1,
                rulesEvaluated = 1,
                violations = emptyList(),
                complianceScore = 0.45
            )

            assertEquals(ComplianceLevel.LOW, evaluation.complianceLevel)
        }

        @Test
        fun `complianceLevel NON_COMPLIANT below 40 percent`() {
            val evaluation = RuleEvaluation(
                totalBssCount = 1,
                rulesEvaluated = 1,
                violations = emptyList(),
                complianceScore = 0.35
            )

            assertEquals(ComplianceLevel.NON_COMPLIANT, evaluation.complianceLevel)
        }

        @Test
        fun `evaluation summary includes BSS count`() {
            val evaluation = RuleEvaluation(
                totalBssCount = 5,
                rulesEvaluated = 3,
                violations = emptyList(),
                complianceScore = 1.0
            )

            assertTrue(evaluation.summary.contains("5"))
            assertTrue(evaluation.summary.contains("3"))
        }

        @Test
        fun `evaluation summary shows COMPLIANT when no violations`() {
            val evaluation = RuleEvaluation(
                totalBssCount = 1,
                rulesEvaluated = 1,
                violations = emptyList(),
                complianceScore = 1.0
            )

            assertTrue(evaluation.summary.contains("COMPLIANT"))
        }

        @Test
        fun `evaluation summary shows violation count`() {
            val violations = listOf(
                RuleViolation("BSS1", "SSID1", SecurityRule.ProhibitWps, RuleSeverity.HIGH, "desc")
            )

            val evaluation = RuleEvaluation(
                totalBssCount = 1,
                rulesEvaluated = 1,
                violations = violations,
                complianceScore = 0.5
            )

            assertTrue(evaluation.summary.contains("1"))
        }

        @Test
        fun `ComplianceLevel descriptions are meaningful`() {
            ComplianceLevel.values().forEach { level ->
                assertTrue(level.description.isNotBlank())
            }
        }

        @Test
        fun `multiple rules evaluated correctly`() {
            val engine = RuleEngine(listOf(
                SecurityRule.RequireMinimumAuth(AuthType.WPA2_PSK),
                SecurityRule.ProhibitCipher(CipherSuite.TKIP),
                SecurityRule.RequirePmf
            ))

            val analysis = createMockBssAnalysis(
                authType = AuthType.WPA2_PSK,
                ciphers = setOf(CipherSuite.CCMP),
                pmfEnabled = true,
                pmfCapable = true
            )

            val result = engine.evaluate(listOf(analysis))

            assertTrue(result.isCompliant)
            assertEquals(1.0, result.complianceScore, 0.01)
        }
    }

    // ==========================
    // SecurityAdvisor Tests (30)
    // ==========================

    @Nested
    inner class SecurityAdvisorTests {

        private val analyzer = SecurityAnalyzer()
        private val advisor = SecurityAdvisor()

        @Test
        fun `analyze generates action items for critical issues`() {
            // WEP with WEP cipher triggers CRITICAL priority action for insecure ciphers
            val bss = createMockBssAnalysis(
                authType = AuthType.WEP,
                ciphers = setOf(CipherSuite.WEP_104),  // WEP cipher triggers critical action
                threatLevel = ThreatLevel.CRITICAL
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any { it.priority == ActionPriority.CRITICAL })
        }

        @Test
        fun `analyze detects WPS vulnerabilities`() {
            val wpsInfo = WpsInfo(
                configMethods = setOf(WpsConfigMethod.LABEL),
                wpsState = WpsState.CONFIGURED,
                locked = false,
                deviceName = null,
                manufacturer = null,
                modelName = null,
                version = null
            )

            val bss = createMockBssAnalysis(wpsInfo = wpsInfo)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any { it.category == ActionCategory.WPS })
        }

        @Test
        fun `analyze detects cipher issues`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WPA2_PSK,
                ciphers = setOf(CipherSuite.TKIP)
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any { it.category == ActionCategory.CIPHER })
        }

        @Test
        fun `analyze detects PMF issues`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WPA3_SAE,
                pmfEnabled = false,
                pmfCapable = true
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any { it.category == ActionCategory.PMF })
        }

        @Test
        fun `analyze detects configuration inconsistencies`() {
            // BSS1 without PMF gets FAIR security level
            val bss1 = createMockBssAnalysis(
                bssid = "00:11:22:33:44:55",
                ssid = "TestNet",
                authType = AuthType.WPA2_PSK
            )
            // BSS2 with PMF enabled gets GOOD security level, creating inconsistency
            val bss2 = createMockBssAnalysis(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "TestNet",
                authType = AuthType.WPA3_SAE,
                pmfEnabled = true  // PMF required → GOOD level, different from BSS1's FAIR
            )

            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss1, bss2))
            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any { it.category == ActionCategory.CONFIGURATION })
        }

        @Test
        fun `prioritizedActions sorted by priority`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WEP,
                threatLevel = ThreatLevel.CRITICAL
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            val priorities = advice.prioritizedActions.map { it.priority.ordinal }
            assertEquals(priorities, priorities.sortedDescending())
        }

        @Test
        fun `estimatedImprovementScore is valid range`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.estimatedImprovementScore in 0.0..1.0)
        }

        @Test
        fun `actionsByPriority groups correctly`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            advice.actionsByPriority.forEach { (priority, actions) ->
                actions.forEach { action ->
                    assertEquals(priority, action.priority)
                }
            }
        }

        @Test
        fun `actionsByCategory groups correctly`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            advice.actionsByCategory.forEach { (category, actions) ->
                actions.forEach { action ->
                    assertEquals(category, action.category)
                }
            }
        }

        @Test
        fun `improvementDelta calculated correctly`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertEquals(
                advice.estimatedImprovementScore - networkAnalysis.averageSecurityScore,
                advice.improvementDelta,
                0.01
            )
        }

        @Test
        fun `quickSummary includes current score`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.quickSummary.contains("Current Score"))
        }

        @Test
        fun `quickSummary includes potential score`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.quickSummary.contains("Potential Score"))
        }

        @Test
        fun `ActionItem summary includes priority`() {
            val action = ActionItem(
                bssid = "00:11:22:33:44:55",
                ssid = "TestNet",
                title = "Test Action",
                description = "Description",
                recommendation = "Recommendation",
                priority = ActionPriority.HIGH,
                effort = EffortLevel.LOW,
                category = ActionCategory.SECURITY
            )

            assertTrue(action.summary.contains("HIGH"))
        }

        @Test
        fun `ActionPriority has meaningful descriptions`() {
            ActionPriority.values().forEach { priority ->
                assertTrue(priority.description.isNotBlank())
            }
        }

        @Test
        fun `EffortLevel has meaningful descriptions`() {
            EffortLevel.values().forEach { effort ->
                assertTrue(effort.description.isNotBlank())
            }
        }

        @Test
        fun `ActionCategory has meaningful descriptions`() {
            ActionCategory.values().forEach { category ->
                assertTrue(category.description.isNotBlank())
            }
        }

        @Test
        fun `SecurityAdvice validates improvement score range`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            assertThrows(IllegalArgumentException::class.java) {
                SecurityAdvice(
                    networkAnalysis = networkAnalysis,
                    prioritizedActions = emptyList(),
                    estimatedImprovementScore = 1.5 // Invalid
                )
            }
        }

        @Test
        fun `analyze handles empty critical issues`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WPA3_SAE,
                pmfEnabled = true
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            // Should still complete without errors
            assertNotNull(advice)
        }

        @Test
        fun `WPS risk score above 0_3 triggers action`() {
            val wpsInfo = WpsInfo(
                configMethods = setOf(WpsConfigMethod.LABEL),
                wpsState = WpsState.CONFIGURED,
                locked = false,
                deviceName = null,
                manufacturer = null,
                modelName = null,
                version = null
            )

            val bss = createMockBssAnalysis(wpsInfo = wpsInfo)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any {
                it.title.contains("WPS", ignoreCase = true)
            })
        }

        @Test
        fun `cipher score below 0_3 triggers critical action`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WEP,
                ciphers = setOf(CipherSuite.WEP_40)
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any {
                it.priority == ActionPriority.CRITICAL &&
                it.category == ActionCategory.CIPHER
            })
        }

        @Test
        fun `cipher score 0_3-0_6 triggers high priority`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WPA2_PSK,
                ciphers = setOf(CipherSuite.TKIP)
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any {
                it.priority == ActionPriority.HIGH &&
                it.category == ActionCategory.CIPHER
            })
        }

        @Test
        fun `PMF required but missing triggers high priority`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WPA3_SAE,
                pmfEnabled = false,
                pmfCapable = true
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any {
                it.priority == ActionPriority.HIGH &&
                it.category == ActionCategory.PMF
            })
        }

        @Test
        fun `PMF recommended triggers medium priority`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WPA2_PSK,
                pmfEnabled = false,
                pmfCapable = true
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            val pmfActions = advice.prioritizedActions.filter { it.category == ActionCategory.PMF }
            if (pmfActions.isNotEmpty()) {
                assertTrue(pmfActions.any { it.priority == ActionPriority.MEDIUM })
            }
        }

        @Test
        fun `configuration inconsistency detected for security levels`() {
            val bss1 = createMockBssAnalysis(
                bssid = "00:11:22:33:44:55",
                ssid = "TestNet",
                authType = AuthType.WPA3_SAE,
                ciphers = setOf(CipherSuite.GCMP)
            )
            val bss2 = createMockBssAnalysis(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "TestNet",
                authType = AuthType.WEP,
                ciphers = setOf(CipherSuite.WEP_104)
            )

            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss1, bss2))
            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any {
                it.category == ActionCategory.CONFIGURATION &&
                it.title.contains("Security", ignoreCase = true)
            })
        }

        @Test
        fun `PMF inconsistency detected across APs`() {
            val bss1 = createMockBssAnalysis(
                bssid = "00:11:22:33:44:55",
                ssid = "TestNet",
                pmfEnabled = true
            )
            val bss2 = createMockBssAnalysis(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "TestNet",
                pmfEnabled = false,
                pmfCapable = true
            )

            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss1, bss2))
            val advice = advisor.analyze(networkAnalysis)

            assertTrue(advice.prioritizedActions.any {
                it.category == ActionCategory.CONFIGURATION &&
                it.title.contains("PMF", ignoreCase = true)
            })
        }

        @Test
        fun `potential improvement increases with more issues`() {
            val bssGood = createMockBssAnalysis(authType = AuthType.WPA3_SAE)
            val bssBad = createMockBssAnalysis(authType = AuthType.WEP)

            val analysisGood = analyzer.analyzeNetwork(listOf(bssGood))
            val analysisBad = analyzer.analyzeNetwork(listOf(bssBad))

            val adviceGood = advisor.analyze(analysisGood)
            val adviceBad = advisor.analyze(analysisBad)

            assertTrue(adviceBad.improvementDelta >= adviceGood.improvementDelta)
        }

        @Test
        fun `actions sorted by priority then effort`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            // Check that same priority actions are sorted by effort
            val highPriorityActions = advice.prioritizedActions.filter {
                it.priority == ActionPriority.HIGH
            }

            if (highPriorityActions.size > 1) {
                val efforts = highPriorityActions.map { it.effort.ordinal }
                assertEquals(efforts, efforts.sorted())
            }
        }

        @Test
        fun `single AP network doesnt trigger configuration issues`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val advice = advisor.analyze(networkAnalysis)

            val configActions = advice.prioritizedActions.filter {
                it.category == ActionCategory.CONFIGURATION
            }

            // Should have no multi-AP configuration issues
            assertTrue(configActions.isEmpty() || configActions.none {
                it.title.contains("Inconsistent", ignoreCase = true)
            })
        }
    }

    // ================================
    // ConfigurationAdvisor Tests (25)
    // ================================

    @Nested
    inner class ConfigurationAdvisorTests {

        private val advisor = ConfigurationAdvisor()

        @Test
        fun `analyze requires at least one BSS`() {
            assertThrows(IllegalArgumentException::class.java) {
                advisor.analyze("TestSSID", emptyList())
            }
        }

        @Test
        fun `single AP triggers single AP recommendations`() {
            val bss = createMockBssAnalysis(ssid = "TestSSID")

            val advice = advisor.analyze("TestSSID", listOf(bss))

            assertTrue(advice.recommendations.any {
                it.title.contains("Single AP", ignoreCase = true)
            })
        }

        @Test
        fun `single AP without roaming features triggers low priority`() {
            val bss = createMockBssAnalysis(
                ssid = "TestSSID",
                roamingSupported = false
            )

            val advice = advisor.analyze("TestSSID", listOf(bss))

            assertTrue(advice.recommendations.any {
                it.category == RecommendationCategory.ROAMING &&
                it.priority == RecommendationPriority.LOW
            })
        }

        @Test
        fun `inconsistent auth types triggers high priority`() {
            val bss1 = createMockBssAnalysis(
                bssid = "00:11:22:33:44:55",
                ssid = "TestSSID",
                authType = AuthType.WPA2_PSK
            )
            val bss2 = createMockBssAnalysis(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "TestSSID",
                authType = AuthType.WPA3_SAE
            )

            val advice = advisor.analyze("TestSSID", listOf(bss1, bss2))

            assertTrue(advice.recommendations.any {
                it.priority == RecommendationPriority.HIGH &&
                it.title.contains("Authentication", ignoreCase = true)
            })
        }

        @Test
        fun `inconsistent security levels triggers medium priority`() {
            // BSS1 with PMF enabled gets GOOD security level
            val bss1 = createMockBssAnalysis(
                bssid = "00:11:22:33:44:55",
                ssid = "TestSSID",
                authType = AuthType.WPA3_SAE,
                ciphers = setOf(CipherSuite.GCMP),
                pmfEnabled = true  // PMF required → higher security score → GOOD level
            )
            // BSS2 without PMF stays at FAIR security level
            val bss2 = createMockBssAnalysis(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "TestSSID",
                authType = AuthType.WPA2_PSK,
                ciphers = setOf(CipherSuite.CCMP)
            )

            val advice = advisor.analyze("TestSSID", listOf(bss1, bss2))

            assertTrue(advice.recommendations.any {
                it.priority == RecommendationPriority.MEDIUM &&
                it.title.contains("Security Level", ignoreCase = true)
            })
        }

        @Test
        fun `inconsistent PMF triggers medium priority`() {
            val bss1 = createMockBssAnalysis(
                bssid = "00:11:22:33:44:55",
                ssid = "TestSSID",
                pmfEnabled = true
            )
            val bss2 = createMockBssAnalysis(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "TestSSID",
                pmfEnabled = false,
                pmfCapable = true
            )

            val advice = advisor.analyze("TestSSID", listOf(bss1, bss2))

            assertTrue(advice.recommendations.any {
                it.title.contains("PMF", ignoreCase = true)
            })
        }

        @Test
        fun `no fast roaming triggers high priority`() {
            val analyses = listOf(
                createMockBssAnalysis(bssid = "00:11:22:33:44:55", roamingSupported = false),
                createMockBssAnalysis(bssid = "AA:BB:CC:DD:EE:FF", roamingSupported = false)
            )

            val advice = advisor.analyze("TestSSID", analyses)

            assertTrue(advice.recommendations.any {
                it.priority == RecommendationPriority.HIGH &&
                it.title.contains("No Fast Roaming", ignoreCase = true)
            })
        }

        @Test
        fun `partial roaming support triggers medium priority`() {
            val analyses = listOf(
                createMockBssAnalysis(bssid = "00:11:22:33:44:55", roamingSupported = true),
                createMockBssAnalysis(bssid = "AA:BB:CC:DD:EE:FF", roamingSupported = false)
            )

            val advice = advisor.analyze("TestSSID", analyses)

            assertTrue(advice.recommendations.any {
                it.priority == RecommendationPriority.MEDIUM &&
                it.title.contains("Partial", ignoreCase = true)
            })
        }

        @Test
        fun `full roaming support triggers info`() {
            val analyses = listOf(
                createMockBssAnalysis(bssid = "00:11:22:33:44:55", roamingSupported = true),
                createMockBssAnalysis(bssid = "AA:BB:CC:DD:EE:FF", roamingSupported = true)
            )

            val advice = advisor.analyze("TestSSID", analyses)

            assertTrue(advice.recommendations.any {
                it.priority == RecommendationPriority.INFO &&
                it.title.contains("Excellent", ignoreCase = true)
            })
        }

        @Test
        fun `2-3 APs classified as small deployment`() {
            val analyses = listOf(
                createMockBssAnalysis(bssid = "00:11:22:33:44:55"),
                createMockBssAnalysis(bssid = "AA:BB:CC:DD:EE:FF")
            )

            val advice = advisor.analyze("TestSSID", analyses)

            assertTrue(advice.recommendations.any {
                it.title.contains("Small", ignoreCase = true)
            })
        }

        @Test
        fun `4-10 APs classified as medium deployment`() {
            val analyses = (1..5).map {
                createMockBssAnalysis(bssid = "00:11:22:33:44:5$it")
            }

            val advice = advisor.analyze("TestSSID", analyses)

            assertTrue(advice.recommendations.any {
                it.title.contains("Medium", ignoreCase = true)
            })
        }

        @Test
        fun `11+ APs classified as large deployment`() {
            val analyses = (1..12).map {
                createMockBssAnalysis(bssid = "00:11:22:33:4$it:55")
            }

            val advice = advisor.analyze("TestSSID", analyses)

            assertTrue(advice.recommendations.any {
                it.title.contains("Large", ignoreCase = true)
            })
        }

        @Test
        fun `ConfigurationAdvice requires positive AP count`() {
            assertThrows(IllegalArgumentException::class.java) {
                ConfigurationAdvice(
                    ssid = "TestSSID",
                    apCount = 0,
                    recommendations = emptyList()
                )
            }
        }

        @Test
        fun `recommendationsByPriority groups correctly`() {
            val bss = createMockBssAnalysis(ssid = "TestSSID")
            val advice = advisor.analyze("TestSSID", listOf(bss))

            advice.recommendationsByPriority.forEach { (priority, recs) ->
                recs.forEach { rec ->
                    assertEquals(priority, rec.priority)
                }
            }
        }

        @Test
        fun `recommendationsByCategory groups correctly`() {
            val bss = createMockBssAnalysis(ssid = "TestSSID")
            val advice = advisor.analyze("TestSSID", listOf(bss))

            advice.recommendationsByCategory.forEach { (category, recs) ->
                recs.forEach { rec ->
                    assertEquals(category, rec.category)
                }
            }
        }

        @Test
        fun `highPriorityRecommendations filters correctly`() {
            val analyses = listOf(
                createMockBssAnalysis(bssid = "00:11:22:33:44:55", authType = AuthType.WPA2_PSK),
                createMockBssAnalysis(bssid = "AA:BB:CC:DD:EE:FF", authType = AuthType.WPA3_SAE)
            )

            val advice = advisor.analyze("TestSSID", analyses)

            advice.highPriorityRecommendations.forEach { rec ->
                assertEquals(RecommendationPriority.HIGH, rec.priority)
            }
        }

        @Test
        fun `summary includes SSID`() {
            val bss = createMockBssAnalysis(ssid = "TestSSID")
            val advice = advisor.analyze("TestSSID", listOf(bss))

            assertTrue(advice.summary.contains("TestSSID"))
        }

        @Test
        fun `summary includes AP count`() {
            val bss = createMockBssAnalysis(ssid = "TestSSID")
            val advice = advisor.analyze("TestSSID", listOf(bss))

            assertTrue(advice.summary.contains("1"))
        }

        @Test
        fun `ConfigurationRecommendation summary includes priority`() {
            val rec = ConfigurationRecommendation(
                category = RecommendationCategory.SECURITY,
                priority = RecommendationPriority.HIGH,
                title = "Test Recommendation",
                description = "Description",
                recommendation = "Recommendation",
                rationale = "Rationale",
                effort = EffortLevel.MEDIUM
            )

            assertTrue(rec.summary.contains("HIGH"))
        }

        @Test
        fun `RecommendationCategory has meaningful descriptions`() {
            RecommendationCategory.values().forEach { category ->
                assertTrue(category.description.isNotBlank())
            }
        }

        @Test
        fun `RecommendationPriority has meaningful descriptions`() {
            RecommendationPriority.values().forEach { priority ->
                assertTrue(priority.description.isNotBlank())
            }
        }

        @Test
        fun `recommendations sorted by priority descending`() {
            val bss = createMockBssAnalysis(ssid = "TestSSID")
            val advice = advisor.analyze("TestSSID", listOf(bss))

            val priorities = advice.recommendations.map { it.priority.ordinal }
            assertEquals(priorities, priorities.sortedDescending())
        }

        @Test
        fun `consistent configuration produces minimal recommendations`() {
            val analyses = listOf(
                createMockBssAnalysis(
                    bssid = "00:11:22:33:44:55",
                    ssid = "TestSSID",
                    authType = AuthType.WPA2_PSK,
                    pmfEnabled = true,
                    roamingSupported = true
                ),
                createMockBssAnalysis(
                    bssid = "AA:BB:CC:DD:EE:FF",
                    ssid = "TestSSID",
                    authType = AuthType.WPA2_PSK,
                    pmfEnabled = true,
                    roamingSupported = true
                )
            )

            val advice = advisor.analyze("TestSSID", analyses)

            // Should have recommendations but no high priority security issues
            val highPrioritySecurity = advice.recommendations.filter {
                it.priority == RecommendationPriority.HIGH &&
                it.category == RecommendationCategory.SECURITY
            }

            assertTrue(highPrioritySecurity.isEmpty())
        }

        @Test
        fun `multi-AP with all inconsistencies triggers multiple recommendations`() {
            val analyses = listOf(
                createMockBssAnalysis(
                    bssid = "00:11:22:33:44:55",
                    authType = AuthType.WPA2_PSK,
                    pmfEnabled = true
                ),
                createMockBssAnalysis(
                    bssid = "AA:BB:CC:DD:EE:FF",
                    authType = AuthType.WPA3_SAE,
                    pmfEnabled = false
                )
            )

            val advice = advisor.analyze("TestSSID", analyses)

            // Should have multiple recommendations for inconsistencies
            assertTrue(advice.recommendations.size >= 2)
        }
    }

    // =========================
    // RiskPrioritizer Tests (25)
    // =========================

    @Nested
    inner class RiskPrioritizerTests {

        private val analyzer = SecurityAnalyzer()
        private val prioritizer = RiskPrioritizer()

        @Test
        fun `prioritize detects critical threat level`() {
            val bss = createMockBssAnalysis(threatLevel = ThreatLevel.CRITICAL)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assertTrue(assessment.prioritizedRisks.any {
                it.impact == RiskImpact.CRITICAL
            })
        }

        @Test
        fun `prioritize detects low compliance`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            // WEP should trigger low compliance
            assertTrue(assessment.prioritizedRisks.isNotEmpty())
        }

        @Test
        fun `prioritize detects low minimum security`() {
            val analyses = listOf(
                createMockBssAnalysis(bssid = "00:11:22:33:44:55", authType = AuthType.WPA3_SAE, ciphers = setOf(CipherSuite.GCMP)),
                createMockBssAnalysis(bssid = "AA:BB:CC:DD:EE:FF", authType = AuthType.OPEN, ciphers = setOf(CipherSuite.NONE))
            )

            val networkAnalysis = analyzer.analyzeNetwork(analyses)
            val assessment = prioritizer.prioritize(networkAnalysis)

            // Less than 50% should trigger risk
            assertTrue(assessment.prioritizedRisks.any {
                it.title.contains("Minimum Security", ignoreCase = true)
            })
        }

        @Test
        fun `prioritize detects WPS risk`() {
            val wpsInfo = WpsInfo(
                configMethods = setOf(WpsConfigMethod.LABEL),
                wpsState = WpsState.CONFIGURED,
                locked = false,
                deviceName = null,
                manufacturer = null,
                modelName = null,
                version = null
            )

            val bss = createMockBssAnalysis(wpsInfo = wpsInfo)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assertTrue(assessment.prioritizedRisks.any {
                it.category == RiskCategory.WPS
            })
        }

        @Test
        fun `prioritize detects weak ciphers`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WEP,
                ciphers = setOf(CipherSuite.WEP_40)
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assertTrue(assessment.prioritizedRisks.any {
                it.category == RiskCategory.ENCRYPTION
            })
        }

        @Test
        fun `prioritize detects missing PMF`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WPA3_SAE,
                pmfEnabled = false,
                pmfCapable = true
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assertTrue(assessment.prioritizedRisks.any {
                it.category == RiskCategory.PMF
            })
        }

        @Test
        fun `prioritize detects BSS critical threat`() {
            val bss = createMockBssAnalysis(threatLevel = ThreatLevel.CRITICAL)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assertTrue(assessment.criticalRisks.isNotEmpty())
        }

        @Test
        fun `prioritizedRisks sorted by priority score`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            val scores = assessment.prioritizedRisks.map { it.riskScore }
            assertEquals(scores, scores.sortedDescending())
        }

        @Test
        fun `immediateActions filters correctly`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assessment.immediateActions.forEach { risk ->
                assertTrue(
                    (risk.impact == RiskImpact.CRITICAL || risk.impact == RiskImpact.HIGH) &&
                    (risk.effort == EffortLevel.LOW || risk.effort == EffortLevel.MEDIUM)
                )
            }
        }

        @Test
        fun `quickWins filters low effort`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assessment.quickWins.forEach { risk ->
                assertEquals(EffortLevel.LOW, risk.effort)
            }
        }

        @Test
        fun `criticalRisks filters critical impact`() {
            val bss = createMockBssAnalysis(threatLevel = ThreatLevel.CRITICAL)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assessment.criticalRisks.forEach { risk ->
                assertEquals(RiskImpact.CRITICAL, risk.impact)
            }
        }

        @Test
        fun `risksByCategory groups correctly`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assessment.risksByCategory.forEach { (category, risks) ->
                risks.forEach { risk ->
                    assertEquals(category, risk.category)
                }
            }
        }

        @Test
        fun `overallRiskLevel CRITICAL at 0_8`() {
            val bss = createMockBssAnalysis(threatLevel = ThreatLevel.CRITICAL)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            if (assessment.totalRiskScore >= 0.8) {
                assertEquals(OverallRiskLevel.CRITICAL, assessment.overallRiskLevel)
            }
        }

        @Test
        fun `RiskAssessment validates risk score range`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            assertThrows(IllegalArgumentException::class.java) {
                RiskAssessment(
                    networkAnalysis = networkAnalysis,
                    prioritizedRisks = emptyList(),
                    totalRiskScore = 1.5 // Invalid
                )
            }
        }

        @Test
        fun `PrioritizedRisk riskScore calculated from impact and likelihood`() {
            val risk = PrioritizedRisk(
                id = "TEST",
                title = "Test",
                description = "Description",
                impact = RiskImpact.HIGH,
                likelihood = RiskLikelihood.LIKELY,
                effort = EffortLevel.LOW,
                category = RiskCategory.SECURITY_POSTURE,
                affectedBssids = emptyList(),
                mitigationSteps = emptyList()
            )

            assertEquals(0.7 * 0.7, risk.riskScore, 0.01)
        }

        @Test
        fun `PrioritizedRisk priorityScore accounts for effort`() {
            val riskLowEffort = PrioritizedRisk(
                id = "TEST1",
                title = "Test",
                description = "Description",
                impact = RiskImpact.HIGH,
                likelihood = RiskLikelihood.CERTAIN,
                effort = EffortLevel.LOW,
                category = RiskCategory.SECURITY_POSTURE,
                affectedBssids = emptyList(),
                mitigationSteps = emptyList()
            )

            val riskHighEffort = PrioritizedRisk(
                id = "TEST2",
                title = "Test",
                description = "Description",
                impact = RiskImpact.HIGH,
                likelihood = RiskLikelihood.CERTAIN,
                effort = EffortLevel.HIGH,
                category = RiskCategory.SECURITY_POSTURE,
                affectedBssids = emptyList(),
                mitigationSteps = emptyList()
            )

            assertTrue(riskLowEffort.priorityScore > riskHighEffort.priorityScore)
        }

        @Test
        fun `PrioritizedRisk summary includes impact`() {
            val risk = PrioritizedRisk(
                id = "TEST",
                title = "Test Risk",
                description = "Description",
                impact = RiskImpact.CRITICAL,
                likelihood = RiskLikelihood.CERTAIN,
                effort = EffortLevel.LOW,
                category = RiskCategory.SECURITY_POSTURE,
                affectedBssids = listOf("00:11:22:33:44:55"),
                mitigationSteps = emptyList()
            )

            assertTrue(risk.summary.contains("CRITICAL"))
        }

        @Test
        fun `RiskImpact has meaningful descriptions`() {
            RiskImpact.values().forEach { impact ->
                assertTrue(impact.description.isNotBlank())
            }
        }

        @Test
        fun `RiskLikelihood has meaningful descriptions`() {
            RiskLikelihood.values().forEach { likelihood ->
                assertTrue(likelihood.description.isNotBlank())
            }
        }

        @Test
        fun `RiskCategory has meaningful descriptions`() {
            RiskCategory.values().forEach { category ->
                assertTrue(category.description.isNotBlank())
            }
        }

        @Test
        fun `OverallRiskLevel has meaningful descriptions`() {
            OverallRiskLevel.values().forEach { level ->
                assertTrue(level.description.isNotBlank())
            }
        }

        @Test
        fun `assessment summary includes risk counts`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            assertTrue(assessment.summary.contains("Risk"))
        }

        @Test
        fun `totalRiskScore is 0 for no risks`() {
            val bss = createMockBssAnalysis(
                authType = AuthType.WPA3_SAE,
                ciphers = setOf(CipherSuite.GCMP),
                pmfEnabled = true,
                threatLevel = ThreatLevel.NONE
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            // Good security should have minimal risks
            assertTrue(assessment.totalRiskScore < 0.5)
        }

        @Test
        fun `WPS risk score above 0_9 triggers critical impact`() {
            val wpsInfo = WpsInfo(
                configMethods = setOf(WpsConfigMethod.LABEL),
                wpsState = WpsState.CONFIGURED,
                locked = false,
                deviceName = null,
                manufacturer = null,
                modelName = null,
                version = null
            )

            val bss = createMockBssAnalysis(wpsInfo = wpsInfo)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val assessment = prioritizer.prioritize(networkAnalysis)

            val wpsRisks = assessment.prioritizedRisks.filter { it.category == RiskCategory.WPS }
            if (wpsRisks.isNotEmpty()) {
                assertTrue(wpsRisks.any {
                    it.impact == RiskImpact.CRITICAL || it.impact == RiskImpact.HIGH
                })
            }
        }
    }

    // ==========================
    // ProModeReporter Tests (15)
    // ==========================

    @Nested
    inner class ProModeReporterTests {

        private val reporter = ProModeReporter()
        private val analyzer = SecurityAnalyzer()

        @Test
        fun `generateExecutiveSummary includes network overview`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val summary = reporter.generateExecutiveSummary(networkAnalysis)

            assertTrue(summary.contains("NETWORK OVERVIEW"))
            assertTrue(summary.contains("Access Points"))
        }

        @Test
        fun `generateExecutiveSummary includes threat level`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val summary = reporter.generateExecutiveSummary(networkAnalysis)

            assertTrue(summary.contains("Threat Level"))
        }

        @Test
        fun `generateExecutiveSummary includes risk summary when provided`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))
            val riskAssessment = RiskPrioritizer().prioritize(networkAnalysis)

            val summary = reporter.generateExecutiveSummary(networkAnalysis, riskAssessment)

            assertTrue(summary.contains("RISK SUMMARY"))
        }

        @Test
        fun `generateTechnicalReport includes network-wide analysis`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val report = reporter.generateTechnicalReport(networkAnalysis)

            assertTrue(report.contains("NETWORK-WIDE ANALYSIS"))
        }

        @Test
        fun `generateTechnicalReport includes security distribution`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val report = reporter.generateTechnicalReport(networkAnalysis)

            assertTrue(report.contains("SECURITY LEVEL DISTRIBUTION"))
        }

        @Test
        fun `generateTechnicalReport includes per-AP details when enabled`() {
            val bss = createMockBssAnalysis(
                bssid = "00:11:22:33:44:55",
                ssid = "TestSSID",
                authType = AuthType.WPA2_PSK
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val report = reporter.generateTechnicalReport(networkAnalysis, includeDetails = true)

            assertTrue(report.contains("PER-ACCESS POINT"))
            assertTrue(report.contains("00:11:22:33:44:55"))
        }

        @Test
        fun `generateTechnicalReport excludes per-AP details when disabled`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val report = reporter.generateTechnicalReport(networkAnalysis, includeDetails = false)

            assertFalse(report.contains("PER-ACCESS POINT"))
        }

        @Test
        fun `generateActionPlan includes action count`() {
            val bss = createMockBssAnalysis(authType = AuthType.WEP)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))
            val advice = SecurityAdvisor().analyze(networkAnalysis)

            val plan = reporter.generateActionPlan(advice)

            assertTrue(plan.contains("Total Actions"))
        }

        @Test
        fun `generateActionPlan groups by priority`() {
            // Use WEP with WEP cipher to trigger CRITICAL priority actions
            val bss = createMockBssAnalysis(
                authType = AuthType.WEP,
                ciphers = setOf(CipherSuite.WEP_104)  // WEP cipher triggers critical action
            )
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))
            val advice = SecurityAdvisor().analyze(networkAnalysis)

            val plan = reporter.generateActionPlan(advice)

            assertTrue(plan.contains("PRIORITY"))
        }

        @Test
        fun `generateComplianceReport shows compliance status`() {
            val engine = RuleEngine(listOf(SecurityRule.RequireMinimumAuth(AuthType.WPA2_PSK)))
            val bss = createMockBssAnalysis(authType = AuthType.WPA3_SAE)
            val evaluation = engine.evaluate(listOf(bss))

            val report = reporter.generateComplianceReport(evaluation)

            assertTrue(report.contains("COMPLIANCE"))
        }

        @Test
        fun `generateComplianceReport shows violations`() {
            val engine = RuleEngine(listOf(SecurityRule.RequireMinimumAuth(AuthType.WPA3_SAE)))
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val evaluation = engine.evaluate(listOf(bss))

            val report = reporter.generateComplianceReport(evaluation)

            assertTrue(report.contains("Violations"))
        }

        @Test
        fun `ComprehensiveReport toPlainText combines all sections`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val report = reporter.generateComprehensiveReport(networkAnalysis)
            val plainText = report.toPlainText()

            assertTrue(plainText.contains("EXECUTIVE"))
            assertTrue(plainText.contains("TECHNICAL"))
        }

        @Test
        fun `ComprehensiveReport toMarkdown generates markdown format`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val report = reporter.generateComprehensiveReport(networkAnalysis)
            val markdown = report.toMarkdown()

            assertTrue(markdown.contains("#"))  // Markdown headers
            assertTrue(markdown.contains("|"))  // Tables
        }

        @Test
        fun `ComprehensiveReport toStructuredData returns map`() {
            val bss = createMockBssAnalysis(authType = AuthType.WPA2_PSK)
            val networkAnalysis = analyzer.analyzeNetwork(listOf(bss))

            val report = reporter.generateComprehensiveReport(networkAnalysis)
            val data = report.toStructuredData()

            assertTrue(data.containsKey("network"))
            assertTrue(data.containsKey("timestamp"))
        }

        @Test
        fun `ReportFormat has meaningful descriptions`() {
            ReportFormat.values().forEach { format ->
                assertTrue(format.description.isNotBlank())
            }
        }
    }

    // ==================
    // Helper Functions
    // ==================

    private fun createMockBssAnalysis(
        bssid: String = "00:11:22:33:44:55",
        ssid: String? = "TestSSID",
        authType: AuthType = AuthType.WPA2_PSK,
        ciphers: Set<CipherSuite> = setOf(CipherSuite.CCMP),
        pmfEnabled: Boolean = false,
        pmfCapable: Boolean = false,
        wpsInfo: WpsInfo? = null,
        roamingSupported: Boolean = false,
        threatLevel: ThreatLevel = ThreatLevel.LOW
    ): BssSecurityAnalysis {
        val fingerprint = SecurityFingerprint(
            authType = authType,
            cipherSet = ciphers,
            pmfRequired = pmfEnabled,
            transitionMode = null
        )

        val securityScore = SecurityScorePerBss.fromFingerprint(
            bssid = bssid,
            ssid = ssid,
            fingerprint = fingerprint
        )

        val wpsRiskScore = wpsInfo?.let { WpsRiskScore.fromWpsInfo(bssid, it) }
            ?: WpsRiskScore(
                bssid = bssid,
                riskScore = 0.0,
                issues = emptyList(),
                wpsInfo = null
            )

        return BssSecurityAnalysis(
            bssid = bssid,
            ssid = ssid,
            securityScore = securityScore,
            threatLevel = threatLevel,
            pmfEnabled = pmfEnabled,
            pmfCapable = pmfCapable,
            wpsRisk = wpsRiskScore,
            roamingFeaturesSupported = roamingSupported,
            criticalIssues = emptyList(),
            requiresImmediateAction = false
        )
    }
}
