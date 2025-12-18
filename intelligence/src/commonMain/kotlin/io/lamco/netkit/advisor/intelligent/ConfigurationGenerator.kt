package io.lamco.netkit.advisor.intelligent

import io.lamco.netkit.advisor.knowledge.*
import io.lamco.netkit.advisor.model.*

/**
 * Automated configuration generator
 *
 * Generates complete, optimized network configurations based on:
 * - Network requirements and constraints
 * - Best practice templates
 * - Vendor-specific optimizations
 * - Use case patterns
 *
 * **Approach**: Template-based generation with rule-based customization
 *
 * Example Usage:
 * ```kotlin
 * val generator = ConfigurationGenerator()
 *
 * val config = generator.generateConfiguration(
 *     requirements = NetworkRequirements(
 *         useCase = UseCase.SMALL_OFFICE,
 *         networkType = NetworkType.SMALL_OFFICE,
 *         targetDevices = 25,
 *         coverageArea = 200.0,
 *         securityLevel = SecurityLevel.HIGH
 *     )
 * )
 *
 * println("SSID Configuration:")
 * config.ssidConfigs.forEach { println(it) }
 * ```
 */
class ConfigurationGenerator {

    private val templateLibrary = ConfigurationTemplateLibrary.create()
    private val knowledgeBase = BestPracticesKnowledgeBase.create()
    private val vendorKnowledge = VendorSpecificKnowledgeBase.create()

    /**
     * Generate complete network configuration
     *
     * @param requirements Network requirements
     * @param vendor Optional vendor for vendor-specific optimizations
     * @return Complete configuration
     */
    fun generateConfiguration(
        requirements: NetworkRequirements,
        vendor: String? = null
    ): GeneratedConfiguration {
        val template = templateLibrary.generateConfiguration(requirements)

        val ssidConfigs = generateSSIDConfigurations(requirements, template)

        val channelPlan = generateChannelPlan(requirements, template)

        val securityConfig = generateSecurityConfiguration(requirements, template)

        val qosConfig = generateQoSConfiguration(requirements, template)

        val optimizedConfig = if (vendor != null) {
            applyVendorOptimizations(
                GeneratedConfiguration(
                    ssidConfigs = ssidConfigs,
                    channelPlan = channelPlan,
                    securityConfig = securityConfig,
                    qosConfig = qosConfig,
                    recommendations = emptyList()
                ),
                vendor
            )
        } else {
            GeneratedConfiguration(
                ssidConfigs = ssidConfigs,
                channelPlan = channelPlan,
                securityConfig = securityConfig,
                qosConfig = qosConfig,
                recommendations = emptyList()
            )
        }

        val recommendations = generateImplementationRecommendations(requirements, optimizedConfig)

        return optimizedConfig.copy(recommendations = recommendations)
    }

    /**
     * Generate optimized channel plan
     *
     * @param apCount Number of APs to configure
     * @param band Frequency band (2.4GHz or 5GHz)
     * @return Channel assignment plan
     */
    fun generateChannelPlan(
        apCount: Int,
        band: String = "5GHz"
    ): ChannelPlanConfig {
        val channels = if (band == "2.4GHz") {
            // Non-overlapping channels for 2.4GHz
            listOf(1, 6, 11)
        } else {
            // DFS and non-DFS channels for 5GHz
            listOf(36, 40, 44, 48, 149, 153, 157, 161)
        }

        val assignments = mutableMapOf<Int, Int>()
        for (apIndex in 0 until apCount) {
            // Assign channels in round-robin to minimize interference
            assignments[apIndex + 1] = channels[apIndex % channels.size]
        }

        val bandwidth = if (band == "2.4GHz") "20MHz" else "80MHz"

        return ChannelPlanConfig(
            band = band,
            bandwidth = bandwidth,
            channelAssignments = assignments,
            dfsEnabled = band == "5GHz",
            autoChannelEnabled = false,
            recommendations = listOf(
                "Monitor channel utilization and adjust if needed",
                "Consider enabling auto-channel for dynamic environments"
            )
        )
    }

    /**
     * Validate generated configuration against best practices
     *
     * @param config Generated configuration
     * @param networkType Network type
     * @return Validation result
     */
    fun validateConfiguration(
        config: GeneratedConfiguration,
        networkType: NetworkType
    ): ConfigurationValidationResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        config.ssidConfigs.forEach { ssid ->
            if (ssid.encryptionType !in listOf("WPA3", "WPA2/WPA3")) {
                issues.add("SSID '${ssid.name}': Weak encryption (${ssid.encryptionType})")
            }
        }

        if (config.channelPlan.bandwidth == "20MHz" && config.channelPlan.band == "5GHz") {
            warnings.add("Consider using 80MHz bandwidth on 5GHz for better performance")
        }

        if (config.qosConfig == null && networkType in listOf(
                NetworkType.ENTERPRISE,
                NetworkType.HEALTHCARE,
                NetworkType.EDUCATION
            )
        ) {
            warnings.add("QoS recommended for ${networkType.displayName} networks")
        }

        val isValid = issues.isEmpty()

        return ConfigurationValidationResult(
            isValid = isValid,
            issues = issues,
            warnings = warnings,
            score = calculateConfigScore(config, issues.size, warnings.size)
        )
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Generate SSID configurations
     */
    private fun generateSSIDConfigurations(
        requirements: NetworkRequirements,
        template: ConfigurationTemplate
    ): List<SSIDConfig> {
        val configs = mutableListOf<SSIDConfig>()

        // Primary SSID
        configs.add(
            SSIDConfig(
                name = generateSSIDName(requirements.networkType, "primary"),
                encryptionType = template.securityRecommendation.displayName,
                hiddenSSID = false,
                guestIsolation = false,
                bandSteering = true,
                fastRoaming = requirements.primaryUse in listOf(
                    UseCase.ENTERPRISE,
                    UseCase.HIGH_DENSITY,
                    UseCase.OUTDOOR
                ),
                maxClients = requirements.expectedDeviceCount
            )
        )

        // Guest SSID (if applicable)
        if (requirements.primaryUse in listOf(
                UseCase.SMALL_OFFICE,
                UseCase.ENTERPRISE
            ) || requirements.networkType == NetworkType.HOSPITALITY
        ) {
            configs.add(
                SSIDConfig(
                    name = generateSSIDName(requirements.networkType, "guest"),
                    encryptionType = "WPA2/WPA3",
                    hiddenSSID = false,
                    guestIsolation = true,
                    bandSteering = true,
                    fastRoaming = false,
                    maxClients = 50
                )
            )
        }

        // IoT SSID (if needed)
        if (requirements.expectedDeviceCount > 20) {
            configs.add(
                SSIDConfig(
                    name = generateSSIDName(requirements.networkType, "iot"),
                    encryptionType = "WPA2",  // Some IoT devices don't support WPA3
                    hiddenSSID = false,
                    guestIsolation = true,
                    bandSteering = false,  // Many IoT devices are 2.4GHz only
                    fastRoaming = false,
                    maxClients = 100
                )
            )
        }

        return configs
    }

    /**
     * Generate channel plan from requirements and template
     */
    private fun generateChannelPlan(
        requirements: NetworkRequirements,
        template: ConfigurationTemplate
    ): ChannelPlanConfig {
        val apCount = calculateRequiredAPs(requirements.coverageArea.toDouble())
        val preferredBand = if (requirements.primaryUse == UseCase.HIGH_DENSITY) "5GHz" else "5GHz"

        return generateChannelPlan(apCount, preferredBand)
    }

    /**
     * Generate security configuration
     */
    private fun generateSecurityConfiguration(
        requirements: NetworkRequirements,
        template: ConfigurationTemplate
    ): SecurityConfigurationDetails {
        val securityLevel = when {
            requirements.primaryUse == UseCase.ENTERPRISE || requirements.networkType == NetworkType.HEALTHCARE -> SecurityLevel.VERY_HIGH
            requirements.primaryUse == UseCase.SMALL_OFFICE || requirements.networkType == NetworkType.RETAIL -> SecurityLevel.HIGH
            else -> SecurityLevel.MEDIUM
        }

        return SecurityConfigurationDetails(
            encryptionStandard = template.securityRecommendation.displayName,
            authenticationMethod = if (template.securityRecommendation in listOf(SecurityTemplate.WPA2_ENTERPRISE, SecurityTemplate.WPA3_ENTERPRISE)) "802.1X" else "PSK",
            securityLevel = securityLevel,
            enabledFeatures = buildList {
                add("WPA3 encryption")
                add("PMF (Protected Management Frames)")
                if (securityLevel == SecurityLevel.VERY_HIGH) {
                    add("802.1X authentication")
                    add("MAC address filtering")
                }
                add("Rogue AP detection")
            },
            recommendations = listOf(
                "Enable WPA3 encryption for all SSIDs",
                "Use strong passphrase (16+ characters)",
                "Enable Protected Management Frames (PMF)",
                "Disable WPS for security",
                "Regular security audits recommended"
            )
        )
    }

    /**
     * Generate QoS configuration
     */
    private fun generateQoSConfiguration(
        requirements: NetworkRequirements,
        template: ConfigurationTemplate
    ): QoSConfigurationDetails? {
        // QoS not needed for basic home networks
        if (requirements.primaryUse == UseCase.HOME_BASIC) {
            return null
        }

        return QoSConfigurationDetails(
            enabled = true,
            priorityLevels = mapOf(
                "Voice/Video Calls" to 7,
                "Video Streaming" to 5,
                "Web Browsing" to 3,
                "File Downloads" to 1
            ),
            bandwidthLimits = if (requirements.networkType == NetworkType.HOSPITALITY) {
                mapOf("Guest Network" to 5.0)  // 5 Mbps per guest
            } else {
                emptyMap()
            },
            recommendations = listOf(
                "Prioritize real-time applications (voice, video)",
                "Monitor QoS effectiveness and adjust as needed"
            )
        )
    }

    /**
     * Apply vendor-specific optimizations
     */
    private fun applyVendorOptimizations(
        config: GeneratedConfiguration,
        vendor: String
    ): GeneratedConfiguration {
        val vendorEnum = RouterVendor.values().find { it.displayName.equals(vendor, ignoreCase = true) } ?: RouterVendor.GENERIC
        val vendorInfo = vendorKnowledge.getVendorKnowledge(vendorEnum)

        val optimizedRecommendations = config.recommendations.toMutableList()
        if (vendorInfo != null) {
            optimizedRecommendations.addAll(
                vendorInfo.bestPractices.take(3).map { "Vendor optimization: $it" }
            )
        }

        return config.copy(recommendations = optimizedRecommendations)
    }

    /**
     * Generate implementation recommendations
     */
    private fun generateImplementationRecommendations(
        requirements: NetworkRequirements,
        config: GeneratedConfiguration
    ): List<String> {
        val recommendations = mutableListOf<String>()

        recommendations.add("Deploy ${calculateRequiredAPs(requirements.coverageArea.toDouble())} AP(s) for ${requirements.coverageArea}m² coverage")
        recommendations.add("Configure SSIDs in this order: ${config.ssidConfigs.joinToString(", ") { it.name }}")
        recommendations.add("Use channel plan: ${config.channelPlan.bandwidth} bandwidth on ${config.channelPlan.band}")
        recommendations.add("Enable ${config.securityConfig.encryptionStandard} encryption on all SSIDs")

        if (config.qosConfig != null) {
            recommendations.add("Configure QoS with ${config.qosConfig.priorityLevels.size} priority levels")
        }

        recommendations.add("Test connectivity from all coverage areas before production deployment")
        recommendations.add("Monitor performance for first 48 hours and tune as needed")

        return recommendations
    }

    /**
     * Generate SSID name based on network type and purpose
     */
    private fun generateSSIDName(networkType: NetworkType, purpose: String): String {
        val prefix = networkType.displayName.replace(" ", "")
        return when (purpose) {
            "primary" -> "${prefix}-WiFi"
            "guest" -> "${prefix}-Guest"
            "iot" -> "${prefix}-IoT"
            else -> "${prefix}-${purpose}"
        }
    }

    /**
     * Calculate required number of APs for coverage
     */
    private fun calculateRequiredAPs(coverageArea: Double): Int {
        // Assume ~150m² per AP in typical office environment
        val areaPerAP = 150.0
        return (coverageArea / areaPerAP).toInt().coerceAtLeast(1)
    }

    /**
     * Calculate configuration quality score
     */
    private fun calculateConfigScore(
        config: GeneratedConfiguration,
        issueCount: Int,
        warningCount: Int
    ): Int {
        var score = 100

        score -= (issueCount * 15)
        score -= (warningCount * 5)

        if (config.securityConfig.encryptionStandard == "WPA3") {
            score += 10
        }

        if (config.qosConfig != null) {
            score += 5
        }

        return score.coerceIn(0, 100)
    }
}

/**
 * Generated network configuration
 */
data class GeneratedConfiguration(
    val ssidConfigs: List<SSIDConfig>,
    val channelPlan: ChannelPlanConfig,
    val securityConfig: SecurityConfigurationDetails,
    val qosConfig: QoSConfigurationDetails?,
    val recommendations: List<String>
) {
    /**
     * Configuration summary
     */
    val summary: String
        get() = "${ssidConfigs.size} SSID(s), ${channelPlan.band} @ ${channelPlan.bandwidth}, ${securityConfig.encryptionStandard}"
}

/**
 * SSID configuration details
 */
data class SSIDConfig(
    val name: String,
    val encryptionType: String,
    val hiddenSSID: Boolean,
    val guestIsolation: Boolean,
    val bandSteering: Boolean,
    val fastRoaming: Boolean,
    val maxClients: Int
)

/**
 * Channel plan configuration
 */
data class ChannelPlanConfig(
    val band: String,
    val bandwidth: String,
    val channelAssignments: Map<Int, Int>,  // AP ID -> Channel
    val dfsEnabled: Boolean,
    val autoChannelEnabled: Boolean,
    val recommendations: List<String>
)

/**
 * Security configuration details
 */
data class SecurityConfigurationDetails(
    val encryptionStandard: String,
    val authenticationMethod: String,
    val securityLevel: SecurityLevel,
    val enabledFeatures: List<String>,
    val recommendations: List<String>
)

/**
 * QoS configuration details
 */
data class QoSConfigurationDetails(
    val enabled: Boolean,
    val priorityLevels: Map<String, Int>,  // Traffic type -> Priority (1-7)
    val bandwidthLimits: Map<String, Double>,  // Network -> Limit (Mbps)
    val recommendations: List<String>
)

/**
 * Configuration validation result
 */
data class ConfigurationValidationResult(
    val isValid: Boolean,
    val issues: List<String>,
    val warnings: List<String>,
    val score: Int
) {
    init {
        require(score in 0..100) { "Score must be 0-100" }
    }

    val summary: String
        get() = if (isValid) {
            "Valid configuration (Score: $score/100)"
        } else {
            "Invalid: ${issues.size} issue(s), ${warnings.size} warning(s)"
        }
}

/**
 * Security level classification
 */
enum class SecurityLevel {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH;

    val displayName: String
        get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
