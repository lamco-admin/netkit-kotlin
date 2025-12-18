package io.lamco.netkit.advisor.knowledge

import io.lamco.netkit.advisor.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class ConfigurationTemplateLibraryTest {

    @Test
    fun `create library with templates`() {
        val template = createTestTemplate()
        val library = ConfigurationTemplateLibrary(listOf(template))

        assertNotNull(library)
    }

    @Test
    fun `empty templates throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationTemplateLibrary(emptyList())
        }
    }

    @Test
    fun `get template by use case`() {
        val library = ConfigurationTemplateLibrary.create()
        val template = library.getTemplate(UseCase.ENTERPRISE)

        assertNotNull(template)
        assertEquals(UseCase.ENTERPRISE, template.useCase)
    }

    @Test
    fun `get template for non-existent use case returns null`() {
        val template = createTestTemplate(useCase = UseCase.HOME_BASIC)
        val library = ConfigurationTemplateLibrary(listOf(template))

        val result = library.getTemplate(UseCase.ENTERPRISE)
        assertNull(result)
    }

    @Test
    fun `get templates for network type`() {
        val library = ConfigurationTemplateLibrary.create()
        val templates = library.getTemplatesForNetworkType(NetworkType.ENTERPRISE)

        assertTrue(templates.isNotEmpty())
        assertTrue(templates.all { it.networkType == NetworkType.ENTERPRISE })
    }

    @Test
    fun `get secure templates filters by security level`() {
        val library = ConfigurationTemplateLibrary.create()
        val secure = library.getSecureTemplates(SecurityTemplate.WPA3_ENTERPRISE)

        assertTrue(secure.all {
            it.securityRecommendation.securityScore >= SecurityTemplate.WPA3_ENTERPRISE.securityScore
        })
    }

    @Test
    fun `generate configuration from requirements`() {
        val library = ConfigurationTemplateLibrary.create()
        val requirements = NetworkRequirements(
            networkType = NetworkType.SMALL_OFFICE,
            expectedDeviceCount = 25,
            coverageArea = 500,
            primaryUse = UseCase.SMALL_OFFICE,
            securityLevel = SecurityRequirement.STANDARD,
            budgetLevel = BudgetLevel.SMALL_BUSINESS
        )

        val config = library.generateConfiguration(requirements)
        assertNotNull(config)
        assertEquals(UseCase.SMALL_OFFICE, config.useCase)
    }

    @Test
    fun `get summary returns statistics`() {
        val library = ConfigurationTemplateLibrary.create()
        val summary = library.getSummary()

        assertTrue(summary.totalTemplates > 0)
        assertTrue(summary.byUseCase.isNotEmpty())
    }

    @Test
    fun `create vendor-specific library`() {
        val library = ConfigurationTemplateLibrary.forVendor(RouterVendor.CISCO)
        val summary = library.getSummary()

        assertTrue(summary.totalTemplates > 0)
    }

    private fun createTestTemplate(
        useCase: UseCase = UseCase.HOME_BASIC
    ): ConfigurationTemplate {
        return ConfigurationTemplate(
            name = "Test Template",
            description = "Test",
            useCase = useCase,
            networkType = NetworkType.HOME_BASIC,
            channelRecommendation = ChannelRecommendation(
                band24GHz = ChannelPlan(1, 20),
                band5GHz = null
            ),
            securityRecommendation = SecurityTemplate.WPA2_PERSONAL,
            powerSettings = "100%",
            qosSettings = null
        )
    }
}
