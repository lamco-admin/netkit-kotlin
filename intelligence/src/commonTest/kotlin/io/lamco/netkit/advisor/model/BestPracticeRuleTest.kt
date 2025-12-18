package io.lamco.netkit.advisor.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BestPracticeRuleTest {
    @Test
    fun `create best practice rule with all fields`() {
        val rule =
            BestPracticeRule(
                category = RuleCategory.SECURITY,
                title = "Use WPA3",
                description = "WPA3 provides enhanced security",
                rationale = "WPA2 has vulnerabilities",
                severity = RuleSeverity.SHOULD,
                source = "WiFi Alliance",
                applicability = RuleApplicability(),
            )

        assertEquals(RuleCategory.SECURITY, rule.category)
        assertEquals("Use WPA3", rule.title)
        assertEquals(RuleSeverity.SHOULD, rule.severity)
    }

    @Test
    fun `blank title throws exception`() {
        assertThrows<IllegalArgumentException> {
            BestPracticeRule(
                category = RuleCategory.SECURITY,
                title = "",
                description = "Description",
                rationale = "Rationale",
                severity = RuleSeverity.MUST,
                source = "Source",
                applicability = RuleApplicability(),
            )
        }
    }

    @Test
    fun `blank description throws exception`() {
        assertThrows<IllegalArgumentException> {
            BestPracticeRule(
                category = RuleCategory.SECURITY,
                title = "Title",
                description = "",
                rationale = "Rationale",
                severity = RuleSeverity.MUST,
                source = "Source",
                applicability = RuleApplicability(),
            )
        }
    }

    @Test
    fun `rule applies to specific network type`() {
        val rule =
            BestPracticeRule(
                category = RuleCategory.SECURITY,
                title = "Enterprise Security",
                description = "Use WPA3-Enterprise",
                rationale = "Best security for enterprise",
                severity = RuleSeverity.MUST,
                source = "IEEE",
                applicability =
                    RuleApplicability(
                        networkTypes = listOf(NetworkType.ENTERPRISE),
                    ),
            )

        assertTrue(rule.appliesTo(NetworkType.ENTERPRISE, wifiGeneration = WifiGeneration.WIFI_6))
        assertFalse(rule.appliesTo(NetworkType.HOME_BASIC, wifiGeneration = WifiGeneration.WIFI_6))
    }

    @Test
    fun `rule applies to specific vendor`() {
        val rule =
            BestPracticeRule(
                category = RuleCategory.CONFIGURATION,
                title = "Cisco RRM",
                description = "Enable RRM",
                rationale = "Automatic optimization",
                severity = RuleSeverity.SHOULD,
                source = "Cisco",
                applicability =
                    RuleApplicability(
                        vendors = listOf(RouterVendor.CISCO),
                    ),
            )

        assertTrue(
            rule.appliesTo(
                NetworkType.ENTERPRISE,
                vendor = RouterVendor.CISCO,
                wifiGeneration = WifiGeneration.WIFI_6,
            ),
        )
        assertFalse(
            rule.appliesTo(
                NetworkType.ENTERPRISE,
                vendor = RouterVendor.UBIQUITI,
                wifiGeneration = WifiGeneration.WIFI_6,
            ),
        )
    }

    @Test
    fun `rule applies to specific wifi generation`() {
        val rule =
            BestPracticeRule(
                category = RuleCategory.PERFORMANCE,
                title = "Use 6 GHz",
                description = "Utilize 6 GHz band",
                rationale = "More spectrum",
                severity = RuleSeverity.MAY,
                source = "WiFi Alliance",
                applicability =
                    RuleApplicability(
                        wifiGenerations = listOf(WifiGeneration.WIFI_6E, WifiGeneration.WIFI_7),
                    ),
            )

        assertTrue(rule.appliesTo(NetworkType.ENTERPRISE, wifiGeneration = WifiGeneration.WIFI_6E))
        assertFalse(rule.appliesTo(NetworkType.ENTERPRISE, wifiGeneration = WifiGeneration.WIFI_6))
    }

    @Test
    fun `universal rule applies to all contexts`() {
        val rule =
            BestPracticeRule(
                category = RuleCategory.SECURITY,
                title = "Disable WEP",
                description = "Never use WEP",
                rationale = "WEP is broken",
                severity = RuleSeverity.MUST,
                source = "IEEE",
                applicability = RuleApplicability(), // Empty = universal
            )

        assertTrue(rule.appliesTo(NetworkType.HOME_BASIC, wifiGeneration = WifiGeneration.WIFI_4))
        assertTrue(rule.appliesTo(NetworkType.ENTERPRISE, wifiGeneration = WifiGeneration.WIFI_7))
        assertTrue(rule.appliesTo(NetworkType.GUEST_NETWORK, vendor = RouterVendor.CISCO, wifiGeneration = WifiGeneration.WIFI_6))
    }

    @Test
    fun `summary includes severity and title`() {
        val rule =
            BestPracticeRule(
                category = RuleCategory.SECURITY,
                title = "Use WPA3",
                description = "WPA3 is better",
                rationale = "Improved security",
                severity = RuleSeverity.SHOULD,
                source = "WiFi Alliance",
                applicability = RuleApplicability(),
            )

        val summary = rule.summary
        assertTrue(summary.contains("SHOULD"))
        assertTrue(summary.contains("Use WPA3"))
    }

    @Test
    fun `rule severity priorities are correct`() {
        assertEquals(3, RuleSeverity.MUST.priority)
        assertEquals(2, RuleSeverity.SHOULD.priority)
        assertEquals(1, RuleSeverity.MAY.priority)
    }

    @Test
    fun `rule category display names`() {
        assertEquals("Security", RuleCategory.SECURITY.displayName)
        assertEquals("Performance", RuleCategory.PERFORMANCE.displayName)
        assertEquals("Reliability", RuleCategory.RELIABILITY.displayName)
        assertEquals("Configuration", RuleCategory.CONFIGURATION.displayName)
        assertEquals("Compliance", RuleCategory.COMPLIANCE.displayName)
    }

    @Test
    fun `network type display names`() {
        assertEquals("Home (Basic)", NetworkType.HOME_BASIC.displayName)
        assertEquals("Enterprise", NetworkType.ENTERPRISE.displayName)
        assertEquals("High-Density", NetworkType.HIGH_DENSITY.displayName)
    }

    @Test
    fun `router vendor display names`() {
        assertEquals("Cisco", RouterVendor.CISCO.displayName)
        assertEquals("Ubiquiti", RouterVendor.UBIQUITI.displayName)
        assertEquals("ASUS", RouterVendor.ASUS.displayName)
    }

    @Test
    fun `wifi generation display names`() {
        assertEquals("WiFi 4 (802.11n)", WifiGeneration.WIFI_4.displayName)
        assertEquals("WiFi 6 (802.11ax)", WifiGeneration.WIFI_6.displayName)
        assertEquals("WiFi 6E (6GHz)", WifiGeneration.WIFI_6E.displayName)
    }

    @Test
    fun `wifi generation standard names`() {
        assertEquals("802.11n", WifiGeneration.WIFI_4.standardName)
        assertEquals("802.11ac", WifiGeneration.WIFI_5.standardName)
        assertEquals("802.11ax", WifiGeneration.WIFI_6.standardName)
    }

    @Test
    fun `rule applicability is universal when empty`() {
        val applicability = RuleApplicability()
        assertTrue(applicability.isUniversal)

        val specific =
            RuleApplicability(
                networkTypes = listOf(NetworkType.ENTERPRISE),
            )
        assertFalse(specific.isUniversal)
    }
}
