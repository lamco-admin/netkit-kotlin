package io.lamco.netkit.advisor.model

/**
 * WiFi network configuration template
 *
 * Provides pre-configured, proven optimal settings for specific use cases.
 * Templates are based on industry best practices, vendor recommendations,
 * and real-world deployment experience.
 *
 * Each template includes:
 * - **Channel recommendations**: Optimal channel selection for the use case
 * - **Security settings**: Appropriate authentication and encryption
 * - **Power settings**: Transmit power recommendations
 * - **QoS configuration**: Quality of Service priorities
 * - **Advanced settings**: Use-case specific optimizations
 *
 * Example Usage:
 * ```kotlin
 * val template = ConfigurationTemplate(
 *     name = "High-Density Conference WiFi",
 *     description = "Optimized for 100+ concurrent users in conference room",
 *     useCase = UseCase.HIGH_DENSITY,
 *     networkType = NetworkType.ENTERPRISE,
 *     channelRecommendation = ChannelRecommendation(
 *         band24GHz = ChannelPlan.DISABLE,
 *         band5GHz = ChannelPlan(channel = 36, width = 20),
 *         band6GHz = ChannelPlan(channel = 37, width = 40)
 *     ),
 *     securityRecommendation = SecurityTemplate.WPA3_ENTERPRISE,
 *     powerSettings = "Reduce to 50% to minimize interference",
 *     qosSettings = "Enable WMM, prioritize voice/video",
 *     additionalSettings = mapOf(
 *         "Client Limit" to "25 per AP",
 *         "Band Steering" to "Aggressive to 5/6 GHz",
 *         "RRM" to "Enable Radio Resource Management"
 *     )
 * )
 * ```
 *
 * @property name Template name
 * @property description Detailed description of use case
 * @property useCase Primary use case this template addresses
 * @property networkType Network type classification
 * @property channelRecommendation Per-band channel recommendations
 * @property securityRecommendation Security configuration
 * @property powerSettings Transmit power recommendations
 * @property qosSettings Quality of Service configuration
 * @property additionalSettings Use-case specific settings
 * @property vendor Optional vendor-specific template
 *
 * @see ChannelRecommendation
 * @see SecurityTemplate
 * @see UseCase
 */
data class ConfigurationTemplate(
    val name: String,
    val description: String,
    val useCase: UseCase,
    val networkType: NetworkType,
    val channelRecommendation: ChannelRecommendation,
    val securityRecommendation: SecurityTemplate,
    val powerSettings: String,
    val qosSettings: String?,
    val additionalSettings: Map<String, String> = emptyMap(),
    val vendor: RouterVendor? = null,
) {
    init {
        require(name.isNotBlank()) { "Template name cannot be blank" }
        require(description.isNotBlank()) { "Template description cannot be blank" }
        require(powerSettings.isNotBlank()) { "Power settings cannot be blank" }
    }

    /**
     * Summary of the template
     */
    val summary: String
        get() = "$name - ${useCase.displayName} ($networkType)"
}

/**
 * Use case classification for configuration templates
 */
enum class UseCase {
    /** Simple home network (1-10 devices, streaming, browsing) */
    HOME_BASIC,

    /** Advanced home (10-50 devices, smart home, IoT, NAS) */
    HOME_ADVANCED,

    /** Small office (<25 users, basic business apps) */
    SMALL_OFFICE,

    /** Enterprise corporate network (complex, managed) */
    ENTERPRISE,

    /** Guest/public WiFi (captive portal, isolation) */
    GUEST_NETWORK,

    /** High-density (conferences, stadiums, >100 users) */
    HIGH_DENSITY,

    /** Outdoor coverage (parks, campuses) */
    OUTDOOR,

    /** Mesh network (multiple APs, self-healing) */
    MESH_NETWORK,

    /** Gaming-optimized (low latency, QoS) */
    GAMING_OPTIMIZED,

    /** Streaming-optimized (high throughput, buffering) */
    STREAMING_OPTIMIZED,

    /** IoT-focused (many low-bandwidth devices) */
    IOT_FOCUSED,

    ;

    /**
     * User-friendly display name
     */
    val displayName: String
        get() =
            when (this) {
                HOME_BASIC -> "Home (Basic)"
                HOME_ADVANCED -> "Home (Advanced)"
                SMALL_OFFICE -> "Small Office"
                ENTERPRISE -> "Enterprise"
                GUEST_NETWORK -> "Guest Network"
                HIGH_DENSITY -> "High-Density"
                OUTDOOR -> "Outdoor"
                MESH_NETWORK -> "Mesh Network"
                GAMING_OPTIMIZED -> "Gaming Optimized"
                STREAMING_OPTIMIZED -> "Streaming Optimized"
                IOT_FOCUSED -> "IoT Focused"
            }
}

/**
 * Channel recommendation for all bands
 *
 * Provides optimal channel selection for 2.4 GHz, 5 GHz, and 6 GHz bands.
 *
 * @property band24GHz 2.4 GHz channel plan (or null if disabled)
 * @property band5GHz 5 GHz channel plan (or null if disabled)
 * @property band6GHz 6 GHz channel plan (or null if not available/disabled)
 * @property rationale Why these channels were selected
 */
data class ChannelRecommendation(
    val band24GHz: ChannelPlan?,
    val band5GHz: ChannelPlan?,
    val band6GHz: ChannelPlan? = null,
    val rationale: String = "",
) {
    /**
     * Whether any band is enabled
     */
    val hasEnabledBand: Boolean
        get() = band24GHz != null || band5GHz != null || band6GHz != null
}

/**
 * Channel plan for a specific band
 *
 * @property channel Primary channel number
 * @property width Channel width in MHz (20, 40, 80, 160)
 * @property dfsAllowed Whether DFS channels are allowed
 */
data class ChannelPlan(
    val channel: Int,
    val width: Int,
    val dfsAllowed: Boolean = false,
) {
    init {
        require(channel >= 0) { "Channel must be non-negative (0 means auto)" }
        require(width in listOf(20, 40, 80, 160, 320)) {
            "Channel width must be 20, 40, 80, 160, or 320 MHz"
        }
    }

    /** Whether this is auto channel selection */
    val isAuto: Boolean
        get() = channel == 0

    /**
     * Human-readable channel plan
     */
    val description: String
        get() =
            if (isAuto) {
                "Auto @ ${width}MHz${if (dfsAllowed) " (DFS)" else ""}"
            } else {
                "Channel $channel @ ${width}MHz${if (dfsAllowed) " (DFS)" else ""}"
            }

    companion object {
        /** Disable this band */
        val DISABLE: ChannelPlan? = null

        /** Auto channel selection */
        fun auto(
            width: Int,
            dfsAllowed: Boolean = false,
        ): ChannelPlan = ChannelPlan(channel = 0, width = width, dfsAllowed = dfsAllowed)
    }
}

/**
 * Security configuration template
 */
enum class SecurityTemplate {
    /** Open network (no security) - NOT RECOMMENDED */
    OPEN,

    /** WPA2-Personal (PSK) with AES */
    WPA2_PERSONAL,

    /** WPA3-Personal (SAE) */
    WPA3_PERSONAL,

    /** WPA2/WPA3 Transition mode */
    WPA2_WPA3_TRANSITION,

    /** WPA2-Enterprise (802.1X) */
    WPA2_ENTERPRISE,

    /** WPA3-Enterprise (192-bit mode) */
    WPA3_ENTERPRISE,

    /** Enhanced Open (OWE) for guest networks */
    ENHANCED_OPEN,

    ;

    /**
     * Display name
     */
    val displayName: String
        get() =
            when (this) {
                OPEN -> "Open (No Security)"
                WPA2_PERSONAL -> "WPA2-Personal"
                WPA3_PERSONAL -> "WPA3-Personal"
                WPA2_WPA3_TRANSITION -> "WPA2/WPA3 Transition"
                WPA2_ENTERPRISE -> "WPA2-Enterprise"
                WPA3_ENTERPRISE -> "WPA3-Enterprise"
                ENHANCED_OPEN -> "Enhanced Open (OWE)"
            }

    /**
     * Security level rating (0-100)
     */
    val securityScore: Int
        get() =
            when (this) {
                OPEN -> 0
                ENHANCED_OPEN -> 40
                WPA2_PERSONAL -> 70
                WPA2_WPA3_TRANSITION -> 75
                WPA3_PERSONAL -> 85
                WPA2_ENTERPRISE -> 90
                WPA3_ENTERPRISE -> 100
            }
}

/**
 * Network requirements for configuration generation
 *
 * Used to generate custom configurations based on specific needs.
 *
 * @property networkType Type of network
 * @property expectedDeviceCount Expected number of concurrent devices
 * @property coverageArea Coverage area in square meters
 * @property primaryUse Primary use case
 * @property securityLevel Required security level
 * @property budgetLevel Budget constraint (affects hardware recommendations)
 * @property vendor Preferred vendor (null = vendor-neutral)
 */
data class NetworkRequirements(
    val networkType: NetworkType,
    val expectedDeviceCount: Int,
    val coverageArea: Int,
    val primaryUse: UseCase,
    val securityLevel: SecurityRequirement,
    val budgetLevel: BudgetLevel,
    val vendor: RouterVendor? = null,
) {
    init {
        require(expectedDeviceCount > 0) { "Expected device count must be positive" }
        require(coverageArea > 0) { "Coverage area must be positive" }
    }
}

/**
 * Security requirement level
 */
enum class SecurityRequirement {
    /** Minimal security (home use) */
    BASIC,

    /** Standard security (small business) */
    STANDARD,

    /** High security (enterprise, compliance required) */
    HIGH,

    /** Maximum security (government, healthcare) */
    MAXIMUM,

    ;

    /**
     * Display name
     */
    val displayName: String
        get() =
            when (this) {
                BASIC -> "Basic"
                STANDARD -> "Standard"
                HIGH -> "High"
                MAXIMUM -> "Maximum"
            }
}

/**
 * Budget level classification
 */
enum class BudgetLevel {
    /** Consumer-grade equipment */
    CONSUMER,

    /** Small business grade */
    SMALL_BUSINESS,

    /** Enterprise grade */
    ENTERPRISE,

    ;

    /**
     * Display name
     */
    val displayName: String
        get() =
            when (this) {
                CONSUMER -> "Consumer"
                SMALL_BUSINESS -> "Small Business"
                ENTERPRISE -> "Enterprise"
            }
}
