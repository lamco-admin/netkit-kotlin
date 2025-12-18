package io.lamco.netkit.advisor.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigurationTemplateTest {

    @Test
    fun `create configuration template with all fields`() {
        val template = ConfigurationTemplate(
            name = "Test Template",
            description = "Test Description",
            useCase = UseCase.ENTERPRISE,
            networkType = NetworkType.ENTERPRISE,
            channelRecommendation = ChannelRecommendation(
                band24GHz = ChannelPlan(channel = 1, width = 20),
                band5GHz = ChannelPlan(channel = 36, width = 40)
            ),
            securityRecommendation = SecurityTemplate.WPA3_ENTERPRISE,
            powerSettings = "50-70%",
            qosSettings = "Enable WMM"
        )

        assertEquals("Test Template", template.name)
        assertEquals(UseCase.ENTERPRISE, template.useCase)
        assertEquals(SecurityTemplate.WPA3_ENTERPRISE, template.securityRecommendation)
    }

    @Test
    fun `blank template name throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationTemplate(
                name = "",
                description = "Desc",
                useCase = UseCase.HOME_BASIC,
                networkType = NetworkType.HOME_BASIC,
                channelRecommendation = ChannelRecommendation(null, null),
                securityRecommendation = SecurityTemplate.WPA2_PERSONAL,
                powerSettings = "100%",
                qosSettings = null
            )
        }
    }

    @Test
    fun `template summary includes name and use case`() {
        val template = ConfigurationTemplate(
            name = "Enterprise WiFi",
            description = "Enterprise config",
            useCase = UseCase.ENTERPRISE,
            networkType = NetworkType.ENTERPRISE,
            channelRecommendation = ChannelRecommendation(null, null),
            securityRecommendation = SecurityTemplate.WPA3_ENTERPRISE,
            powerSettings = "50%",
            qosSettings = null
        )

        val summary = template.summary
        assertTrue(summary.contains("Enterprise WiFi"))
        assertTrue(summary.contains("Enterprise"))
    }

    @Test
    fun `channel plan validates positive channel`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlan(channel = -1, width = 20)
        }
    }

    @Test
    fun `channel plan validates channel width`() {
        assertThrows<IllegalArgumentException> {
            ChannelPlan(channel = 1, width = 30)  // Invalid width
        }
    }

    @Test
    fun `channel plan accepts valid widths`() {
        ChannelPlan(channel = 1, width = 20)  // OK
        ChannelPlan(channel = 36, width = 40)  // OK
        ChannelPlan(channel = 36, width = 80)  // OK
        ChannelPlan(channel = 36, width = 160)  // OK
        ChannelPlan(channel = 37, width = 320)  // OK for WiFi 7
    }

    @Test
    fun `channel plan description includes channel and width`() {
        val plan = ChannelPlan(channel = 36, width = 80, dfsAllowed = true)
        val desc = plan.description

        assertTrue(desc.contains("36"))
        assertTrue(desc.contains("80"))
        assertTrue(desc.contains("DFS"))
    }

    @Test
    fun `channel recommendation has enabled band check`() {
        val withBands = ChannelRecommendation(
            band24GHz = ChannelPlan(1, 20),
            band5GHz = ChannelPlan(36, 40)
        )
        assertTrue(withBands.hasEnabledBand)

        val noBands = ChannelRecommendation(
            band24GHz = null,
            band5GHz = null
        )
        assertFalse(noBands.hasEnabledBand)
    }

    @Test
    fun `security template security scores are ordered`() {
        assertTrue(SecurityTemplate.WPA3_ENTERPRISE.securityScore >
                SecurityTemplate.WPA2_ENTERPRISE.securityScore)
        assertTrue(SecurityTemplate.WPA2_ENTERPRISE.securityScore >
                SecurityTemplate.WPA3_PERSONAL.securityScore)
        assertTrue(SecurityTemplate.WPA3_PERSONAL.securityScore >
                SecurityTemplate.WPA2_PERSONAL.securityScore)
        assertTrue(SecurityTemplate.WPA2_PERSONAL.securityScore >
                SecurityTemplate.ENHANCED_OPEN.securityScore)
        assertEquals(0, SecurityTemplate.OPEN.securityScore)
    }

    @Test
    fun `use case display names`() {
        assertEquals("Home (Basic)", UseCase.HOME_BASIC.displayName)
        assertEquals("Enterprise", UseCase.ENTERPRISE.displayName)
        assertEquals("Gaming Optimized", UseCase.GAMING_OPTIMIZED.displayName)
    }

    @Test
    fun `network requirements validates positive device count`() {
        assertThrows<IllegalArgumentException> {
            NetworkRequirements(
                networkType = NetworkType.ENTERPRISE,
                expectedDeviceCount = -1,
                coverageArea = 1000,
                primaryUse = UseCase.ENTERPRISE,
                securityLevel = SecurityRequirement.HIGH,
                budgetLevel = BudgetLevel.ENTERPRISE
            )
        }
    }

    @Test
    fun `network requirements validates positive coverage area`() {
        assertThrows<IllegalArgumentException> {
            NetworkRequirements(
                networkType = NetworkType.ENTERPRISE,
                expectedDeviceCount = 100,
                coverageArea = 0,
                primaryUse = UseCase.ENTERPRISE,
                securityLevel = SecurityRequirement.HIGH,
                budgetLevel = BudgetLevel.ENTERPRISE
            )
        }
    }

    @Test
    fun `security requirement display names`() {
        assertEquals("Basic", SecurityRequirement.BASIC.displayName)
        assertEquals("Standard", SecurityRequirement.STANDARD.displayName)
        assertEquals("High", SecurityRequirement.HIGH.displayName)
        assertEquals("Maximum", SecurityRequirement.MAXIMUM.displayName)
    }

    @Test
    fun `budget level display names`() {
        assertEquals("Consumer", BudgetLevel.CONSUMER.displayName)
        assertEquals("Small Business", BudgetLevel.SMALL_BUSINESS.displayName)
        assertEquals("Enterprise", BudgetLevel.ENTERPRISE.displayName)
    }
}
