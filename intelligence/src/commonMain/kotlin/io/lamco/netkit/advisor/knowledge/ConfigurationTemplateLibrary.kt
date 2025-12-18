package io.lamco.netkit.advisor.knowledge

import io.lamco.netkit.advisor.model.*

/**
 * Library of pre-configured WiFi network templates
 *
 * Provides proven, optimized configuration templates for common deployment scenarios.
 * Templates are based on:
 * - Industry best practices
 * - Vendor reference architectures
 * - Real-world performance testing
 * - Security compliance requirements
 *
 * Features:
 * - **Pre-built templates**: Ready-to-use configs for common use cases
 * - **Custom generation**: Create configs based on specific requirements
 * - **Vendor-specific**: Templates optimized for specific hardware
 * - **Compliance-ready**: Templates meeting PCI-DSS, HIPAA, etc.
 *
 * Example Usage:
 * ```kotlin
 * val library = ConfigurationTemplateLibrary.create()
 *
 * // Get template for enterprise deployment
 * val template = library.getTemplate(
 *     useCase = UseCase.ENTERPRISE,
 *     wifiGeneration = WifiGeneration.WIFI_6
 * )
 *
 * // Generate custom configuration
 * val custom = library.generateConfiguration(
 *     NetworkRequirements(
 *         networkType = NetworkType.SMALL_OFFICE,
 *         expectedDeviceCount = 25,
 *         coverageArea = 500,
 *         primaryUse = UseCase.SMALL_OFFICE,
 *         securityLevel = SecurityRequirement.STANDARD,
 *         budgetLevel = BudgetLevel.SMALL_BUSINESS
 *     )
 * )
 * ```
 *
 * @property templates Collection of configuration templates
 */
class ConfigurationTemplateLibrary(
    private val templates: List<ConfigurationTemplate>,
) {
    init {
        require(templates.isNotEmpty()) { "Template library must contain at least one template" }
    }

    /**
     * Get configuration template for use case
     *
     * Returns the most appropriate template for the specified use case,
     * vendor, and WiFi generation. If multiple templates match, returns
     * the most recent/advanced one.
     *
     * @param useCase Primary use case (home, office, enterprise, etc.)
     * @param vendor Optional router vendor for vendor-specific template
     * @param wifiGeneration WiFi generation (defaults to WiFi 6)
     * @return Configuration template, or null if no match found
     */
    fun getTemplate(
        useCase: UseCase,
        vendor: RouterVendor? = null,
        wifiGeneration: WifiGeneration = WifiGeneration.WIFI_6,
    ): ConfigurationTemplate? {
        var matches = templates.filter { it.useCase == useCase }

        if (vendor != null) {
            val vendorMatches = matches.filter { it.vendor == vendor }
            if (vendorMatches.isNotEmpty()) {
                matches = vendorMatches
            }
        }

        // Return first match (templates should be ordered by preference)
        return matches.firstOrNull()
    }

    /**
     * Get all templates for network type
     *
     * @param networkType Network type classification
     * @return List of applicable templates
     */
    fun getTemplatesForNetworkType(networkType: NetworkType): List<ConfigurationTemplate> =
        templates.filter { it.networkType == networkType }

    /**
     * Get templates matching security requirement
     *
     * @param minimumSecurity Minimum required security level
     * @return List of templates meeting security requirement
     */
    fun getSecureTemplates(minimumSecurity: SecurityTemplate): List<ConfigurationTemplate> =
        templates.filter {
            it.securityRecommendation.securityScore >= minimumSecurity.securityScore
        }

    /**
     * Generate custom configuration based on requirements
     *
     * Analyzes network requirements and generates an optimized configuration.
     * Uses template matching and requirement-based customization.
     *
     * @param requirements Network requirements specification
     * @return Generated configuration template
     */
    fun generateConfiguration(requirements: NetworkRequirements): ConfigurationTemplate {
        val baseTemplate =
            getTemplate(
                useCase = requirements.primaryUse,
                vendor = requirements.vendor,
            )

        if (baseTemplate == null) {
            return createFromRequirements(requirements)
        }

        return customizeTemplate(baseTemplate, requirements)
    }

    /**
     * Get template summary statistics
     */
    fun getSummary(): TemplateLibrarySummary =
        TemplateLibrarySummary(
            totalTemplates = templates.size,
            byUseCase =
                templates
                    .groupBy { it.useCase }
                    .mapValues { it.value.size },
            byNetworkType =
                templates
                    .groupBy { it.networkType }
                    .mapValues { it.value.size },
            vendorSpecificCount = templates.count { it.vendor != null },
        )

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Create configuration from scratch based on requirements
     */
    private fun createFromRequirements(requirements: NetworkRequirements): ConfigurationTemplate =
        ConfigurationTemplate(
            name = "Custom ${requirements.primaryUse.displayName} Configuration",
            description = "Auto-generated configuration for ${requirements.networkType.displayName}",
            useCase = requirements.primaryUse,
            networkType = requirements.networkType,
            channelRecommendation = generateChannelRecommendation(requirements),
            securityRecommendation = selectSecurity(requirements.securityLevel),
            powerSettings = generatePowerSettings(requirements),
            qosSettings = generateQosSettings(requirements),
            additionalSettings = generateAdditionalSettings(requirements),
            vendor = requirements.vendor,
        )

    /**
     * Customize existing template based on requirements
     */
    private fun customizeTemplate(
        template: ConfigurationTemplate,
        requirements: NetworkRequirements,
    ): ConfigurationTemplate {
        val channels =
            if (requirements.expectedDeviceCount > 50) {
                // High density - prefer narrower channels
                ChannelRecommendation(
                    band24GHz = null, // Disable 2.4 GHz
                    band5GHz = ChannelPlan(channel = 36, width = 20),
                    band6GHz = ChannelPlan(channel = 37, width = 40),
                    rationale = "High-density deployment: disabled 2.4GHz, using narrow channels",
                )
            } else {
                template.channelRecommendation
            }

        val security =
            if (requirements.securityLevel.ordinal > SecurityRequirement.STANDARD.ordinal) {
                selectSecurity(requirements.securityLevel)
            } else {
                template.securityRecommendation
            }

        return template.copy(
            name = "${template.name} (Customized)",
            channelRecommendation = channels,
            securityRecommendation = security,
            additionalSettings =
                template.additionalSettings +
                    mapOf("Device Count Target" to "${requirements.expectedDeviceCount}"),
        )
    }

    /**
     * Generate channel recommendation based on requirements
     */
    private fun generateChannelRecommendation(requirements: NetworkRequirements): ChannelRecommendation =
        when {
            // High density - 5/6 GHz only, narrow channels
            requirements.expectedDeviceCount > 50 ->
                ChannelRecommendation(
                    band24GHz = null,
                    band5GHz = ChannelPlan(channel = 36, width = 20),
                    band6GHz = ChannelPlan(channel = 37, width = 40),
                    rationale = "High-density: 5/6GHz only with narrow channels for maximum AP density",
                )

            // Medium density - prefer 5 GHz, enable 2.4 for compatibility
            requirements.expectedDeviceCount > 20 ->
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 1, width = 20),
                    band5GHz = ChannelPlan(channel = 36, width = 40),
                    band6GHz = ChannelPlan(channel = 37, width = 80),
                    rationale = "Medium density: Multi-band with 5GHz preference",
                )

            // Low density - can use wider channels
            else ->
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 1, width = 20),
                    band5GHz = ChannelPlan(channel = 36, width = 80),
                    band6GHz = ChannelPlan(channel = 37, width = 160),
                    rationale = "Low density: Wide channels for maximum throughput",
                )
        }

    /**
     * Select security template based on requirement
     */
    private fun selectSecurity(requirement: SecurityRequirement): SecurityTemplate =
        when (requirement) {
            SecurityRequirement.BASIC -> SecurityTemplate.WPA2_PERSONAL
            SecurityRequirement.STANDARD -> SecurityTemplate.WPA3_PERSONAL
            SecurityRequirement.HIGH -> SecurityTemplate.WPA2_ENTERPRISE
            SecurityRequirement.MAXIMUM -> SecurityTemplate.WPA3_ENTERPRISE
        }

    /**
     * Generate power settings recommendation
     */
    private fun generatePowerSettings(requirements: NetworkRequirements): String =
        when {
            requirements.expectedDeviceCount > 50 ->
                "Reduce to 50-60% to minimize inter-AP interference"

            requirements.coverageArea > 1000 ->
                "Increase to 80-100% for extended coverage"

            else ->
                "Set to 70-80% (moderate coverage)"
        }

    /**
     * Generate QoS settings recommendation
     */
    private fun generateQosSettings(requirements: NetworkRequirements): String? =
        when (requirements.primaryUse) {
            UseCase.GAMING_OPTIMIZED ->
                "Enable WMM, prioritize voice/video, minimize buffering"

            UseCase.STREAMING_OPTIMIZED ->
                "Enable WMM, prioritize video traffic, large buffers"

            UseCase.ENTERPRISE, UseCase.HIGH_DENSITY ->
                "Enable WMM, prioritize voice, standard video priority"

            else -> null
        }

    /**
     * Generate additional settings based on requirements
     */
    private fun generateAdditionalSettings(requirements: NetworkRequirements): Map<String, String> {
        val settings = mutableMapOf<String, String>()

        settings["Expected Devices"] = "${requirements.expectedDeviceCount}"
        settings["Coverage Area"] = "${requirements.coverageArea} sq m"

        when (requirements.networkType) {
            NetworkType.ENTERPRISE -> {
                settings["802.11k"] = "Enable"
                settings["802.11r"] = "Enable"
                settings["802.11v"] = "Enable"
                settings["RRM"] = "Enable Radio Resource Management"
            }
            NetworkType.GUEST_NETWORK -> {
                settings["Client Isolation"] = "Enable"
                settings["Captive Portal"] = "Recommended"
                settings["Bandwidth Limit"] = "Consider per-client limit"
            }
            NetworkType.HIGH_DENSITY -> {
                settings["Client Limit"] = "20-25 per AP"
                settings["Band Steering"] = "Aggressive"
                settings["Airtime Fairness"] = "Enable"
            }
            else -> {}
        }

        return settings
    }

    companion object {
        /**
         * Create library with default templates
         *
         * Includes templates for all common use cases.
         *
         * @return Template library with comprehensive coverage
         */
        fun create(): ConfigurationTemplateLibrary = ConfigurationTemplateLibrary(DEFAULT_TEMPLATES)

        /**
         * Create library with vendor-specific templates
         *
         * @param vendor Router vendor
         * @return Template library with vendor-optimized configs
         */
        fun forVendor(vendor: RouterVendor): ConfigurationTemplateLibrary {
            val vendorTemplates =
                when (vendor) {
                    RouterVendor.CISCO -> CISCO_TEMPLATES
                    RouterVendor.UBIQUITI -> UBIQUITI_TEMPLATES
                    RouterVendor.ARUBA -> ARUBA_TEMPLATES
                    else -> emptyList()
                }
            return ConfigurationTemplateLibrary(DEFAULT_TEMPLATES + vendorTemplates)
        }
    }
}

/**
 * Template library summary statistics
 *
 * @property totalTemplates Total number of templates
 * @property byUseCase Count of templates per use case
 * @property byNetworkType Count of templates per network type
 * @property vendorSpecificCount Number of vendor-specific templates
 */
data class TemplateLibrarySummary(
    val totalTemplates: Int,
    val byUseCase: Map<UseCase, Int>,
    val byNetworkType: Map<NetworkType, Int>,
    val vendorSpecificCount: Int,
)

// ========================================
// Default Templates Database
// ========================================

/**
 * Default configuration templates
 */
private val DEFAULT_TEMPLATES =
    listOf(
        // Home Basic
        ConfigurationTemplate(
            name = "Home Basic WiFi",
            description = "Simple home network for streaming, browsing, and smart devices",
            useCase = UseCase.HOME_BASIC,
            networkType = NetworkType.HOME_BASIC,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 1, width = 20),
                    band5GHz = ChannelPlan(channel = 36, width = 80),
                    rationale = "Dual-band for device compatibility and performance",
                ),
            securityRecommendation = SecurityTemplate.WPA2_WPA3_TRANSITION,
            powerSettings = "75-100% for full home coverage",
            qosSettings = "Enable WMM for streaming priority",
            additionalSettings =
                mapOf(
                    "SSID Broadcasting" to "Enabled",
                    "Guest Network" to "Optional",
                    "Smart Connect" to "Enable band steering",
                ),
        ),
        // Small Office
        ConfigurationTemplate(
            name = "Small Office WiFi",
            description = "Small business network for 10-25 users with moderate security",
            useCase = UseCase.SMALL_OFFICE,
            networkType = NetworkType.SMALL_OFFICE,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 1, width = 20),
                    band5GHz = ChannelPlan(channel = 36, width = 40),
                    rationale = "Dual-band with 40MHz on 5GHz for business applications",
                ),
            securityRecommendation = SecurityTemplate.WPA3_PERSONAL,
            powerSettings = "60-80% to cover office space",
            qosSettings = "Enable WMM, prioritize voice for VoIP",
            additionalSettings =
                mapOf(
                    "802.11k" to "Enable for improved roaming",
                    "Guest Network" to "Separate VLAN recommended",
                    "MAC Filtering" to "Consider for critical devices",
                ),
        ),
        // Enterprise
        ConfigurationTemplate(
            name = "Enterprise Corporate WiFi",
            description = "Enterprise network with high security and management features",
            useCase = UseCase.ENTERPRISE,
            networkType = NetworkType.ENTERPRISE,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 1, width = 20),
                    band5GHz = ChannelPlan(channel = 36, width = 40),
                    band6GHz = ChannelPlan(channel = 37, width = 80),
                    rationale = "Multi-band with moderate channel widths for enterprise density",
                ),
            securityRecommendation = SecurityTemplate.WPA3_ENTERPRISE,
            powerSettings = "50-70% to minimize inter-AP interference",
            qosSettings = "Enable WMM, prioritize voice and video conferencing",
            additionalSettings =
                mapOf(
                    "802.11k" to "Enable",
                    "802.11r" to "Enable fast roaming",
                    "802.11v" to "Enable BSS transition management",
                    "PMF" to "Required (802.11w)",
                    "Client Limit" to "30-40 per AP",
                    "Load Balancing" to "Enable",
                    "Band Steering" to "Moderate",
                ),
        ),
        // High Density
        ConfigurationTemplate(
            name = "High-Density Conference WiFi",
            description = "Optimized for 100+ concurrent users in confined space",
            useCase = UseCase.HIGH_DENSITY,
            networkType = NetworkType.HIGH_DENSITY,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = null, // Disable 2.4 GHz
                    band5GHz = ChannelPlan(channel = 36, width = 20),
                    band6GHz = ChannelPlan(channel = 37, width = 40),
                    rationale = "5/6GHz only with narrow channels for maximum AP density",
                ),
            securityRecommendation = SecurityTemplate.WPA2_ENTERPRISE,
            powerSettings = "Reduce to 40-50% to minimize co-channel interference",
            qosSettings = "Enable WMM, aggressive airtime fairness",
            additionalSettings =
                mapOf(
                    "Client Limit" to "20-25 per AP",
                    "Band Steering" to "Aggressive to 5/6 GHz",
                    "RRM" to "Enable for automatic optimization",
                    "Probe Suppression" to "Enable to reduce overhead",
                    "Basic Rates" to "12 Mbps minimum to discourage legacy clients",
                ),
        ),
        // Guest Network
        ConfigurationTemplate(
            name = "Guest/Public WiFi",
            description = "Isolated guest network with captive portal support",
            useCase = UseCase.GUEST_NETWORK,
            networkType = NetworkType.GUEST_NETWORK,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 6, width = 20),
                    band5GHz = ChannelPlan(channel = 40, width = 40),
                    rationale = "Dual-band for maximum device compatibility",
                ),
            securityRecommendation = SecurityTemplate.ENHANCED_OPEN,
            powerSettings = "60-80% for public area coverage",
            qosSettings = "Enable WMM, limit bandwidth per client",
            additionalSettings =
                mapOf(
                    "Client Isolation" to "Enable (prevents client-to-client communication)",
                    "Captive Portal" to "Recommended for terms acceptance",
                    "Bandwidth Limit" to "5-10 Mbps per client",
                    "Session Timeout" to "4-8 hours",
                    "VLAN Isolation" to "Separate from corporate network",
                ),
        ),
        // Gaming Optimized
        ConfigurationTemplate(
            name = "Gaming-Optimized WiFi",
            description = "Low-latency configuration for online gaming",
            useCase = UseCase.GAMING_OPTIMIZED,
            networkType = NetworkType.HOME_ADVANCED,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = null, // Disable 2.4 for consistency
                    band5GHz = ChannelPlan(channel = 36, width = 80, dfsAllowed = true),
                    band6GHz = ChannelPlan(channel = 37, width = 160),
                    rationale = "5/6GHz only for lowest latency and interference",
                ),
            securityRecommendation = SecurityTemplate.WPA3_PERSONAL,
            powerSettings = "80-100% for strong, stable signal",
            qosSettings = "Enable WMM, highest priority for gaming traffic, minimize buffering",
            additionalSettings =
                mapOf(
                    "Beamforming" to "Enable for focused signal",
                    "MU-MIMO" to "Enable",
                    "OFDMA" to "Enable (WiFi 6+)",
                    "Target Wake Time" to "Disable for gaming devices",
                    "Airtime Fairness" to "Enable to prevent slow devices from degrading performance",
                ),
        ),
    )

/**
 * Cisco-specific templates
 */
private val CISCO_TEMPLATES =
    listOf(
        ConfigurationTemplate(
            name = "Cisco Enterprise with FlexConnect",
            description = "Cisco enterprise deployment with branch office FlexConnect",
            useCase = UseCase.ENTERPRISE,
            networkType = NetworkType.ENTERPRISE,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 0, width = 20), // Auto
                    band5GHz = ChannelPlan(channel = 0, width = 40), // Auto
                    rationale = "DCA (Dynamic Channel Assignment) for automatic optimization",
                ),
            securityRecommendation = SecurityTemplate.WPA3_ENTERPRISE,
            powerSettings = "TPC (Transmit Power Control) automatic",
            qosSettings = "Enable Platinum/Gold QoS profiles",
            additionalSettings =
                mapOf(
                    "RRM" to "Enable Radio Resource Management",
                    "DCA" to "Enable Dynamic Channel Assignment",
                    "TPC" to "Enable Transmit Power Control",
                    "Coverage Hole Detection" to "Enable",
                    "CleanAir" to "Enable spectrum intelligence",
                    "ClientLink" to "Enable beamforming",
                ),
            vendor = RouterVendor.CISCO,
        ),
    )

/**
 * Ubiquiti-specific templates
 */
private val UBIQUITI_TEMPLATES =
    listOf(
        ConfigurationTemplate(
            name = "UniFi Enterprise",
            description = "Ubiquiti UniFi enterprise deployment",
            useCase = UseCase.ENTERPRISE,
            networkType = NetworkType.ENTERPRISE,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 0, width = 20),
                    band5GHz = ChannelPlan(channel = 0, width = 40, dfsAllowed = true),
                    rationale = "Auto channel with DFS for maximum flexibility",
                ),
            securityRecommendation = SecurityTemplate.WPA2_ENTERPRISE,
            powerSettings = "Auto (RF scanning enabled)",
            qosSettings = "Enable WMM with default profiles",
            additionalSettings =
                mapOf(
                    "Band Steering" to "Prefer 5 GHz",
                    "Fast Roaming" to "Enable (802.11r)",
                    "Minimum RSSI" to "-70 dBm",
                    "Auto-Optimize Network" to "Enable",
                ),
            vendor = RouterVendor.UBIQUITI,
        ),
    )

/**
 * Aruba-specific templates
 */
private val ARUBA_TEMPLATES =
    listOf(
        ConfigurationTemplate(
            name = "Aruba Enterprise with ARM",
            description = "Aruba enterprise with Adaptive Radio Management",
            useCase = UseCase.ENTERPRISE,
            networkType = NetworkType.ENTERPRISE,
            channelRecommendation =
                ChannelRecommendation(
                    band24GHz = ChannelPlan(channel = 0, width = 20),
                    band5GHz = ChannelPlan(channel = 0, width = 40),
                    rationale = "ARM (Adaptive Radio Management) for automatic optimization",
                ),
            securityRecommendation = SecurityTemplate.WPA3_ENTERPRISE,
            powerSettings = "ARM automatic power control",
            qosSettings = "Enable Unified Communication profiles",
            additionalSettings =
                mapOf(
                    "ARM" to "Enable Adaptive Radio Management",
                    "ClientMatch" to "Enable client steering",
                    "AirMatch" to "Enable AI-powered RF optimization",
                    "AppRF" to "Enable application visibility",
                ),
            vendor = RouterVendor.ARUBA,
        ),
    )
