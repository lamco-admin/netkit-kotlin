package io.lamco.netkit.advisor.knowledge

import io.lamco.netkit.advisor.model.*

/**
 * Network type profile definitions
 *
 * Provides comprehensive profiles for different network deployment types,
 * including typical characteristics, requirements, and recommendations.
 *
 * Profiles help the intelligent advisor understand:
 * - Expected device counts and types
 * - Security requirements
 * - Performance expectations
 * - Typical use cases
 * - Recommended configurations
 *
 * Example Usage:
 * ```kotlin
 * val profiles = NetworkTypeProfiles.create()
 *
 * // Get enterprise profile
 * val enterprise = profiles.getProfile(NetworkType.ENTERPRISE)
 *
 * // Check if network matches profile
 * val matchScore = profiles.matchProfile(
 *     deviceCount = 75,
 *     apCount = 8,
 *     securityType = "WPA2-Enterprise"
 * )
 * ```
 *
 * @property profiles Map of network types to their profiles
 */
class NetworkTypeProfiles(
    private val profiles: Map<NetworkType, NetworkProfile>,
) {
    init {
        require(profiles.isNotEmpty()) { "Network type profiles cannot be empty" }
    }

    /**
     * Get profile for network type
     *
     * @param networkType Type of network
     * @return Network profile, or null if not defined
     */
    fun getProfile(networkType: NetworkType): NetworkProfile? = profiles[networkType]

    /**
     * Get all profiles
     *
     * @return List of all network profiles
     */
    fun getAllProfiles(): List<NetworkProfile> = profiles.values.toList()

    /**
     * Match current network to profile
     *
     * Analyzes network characteristics and returns matching profiles
     * with confidence scores.
     *
     * @param characteristics Current network characteristics
     * @return List of matching profiles with scores (0.0-1.0)
     */
    fun matchProfile(characteristics: NetworkCharacteristics): List<ProfileMatch> =
        profiles
            .map { (type, profile) ->
                ProfileMatch(
                    networkType = type,
                    profile = profile,
                    matchScore = calculateMatchScore(characteristics, profile),
                )
            }.sortedByDescending { it.matchScore }

    /**
     * Get recommended profile based on requirements
     *
     * @param requirements Network requirements
     * @return Best matching profile
     */
    fun recommendProfile(requirements: NetworkRequirements): NetworkProfile? = getProfile(requirements.networkType)

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Calculate how well characteristics match a profile
     */
    private fun calculateMatchScore(
        characteristics: NetworkCharacteristics,
        profile: NetworkProfile,
    ): Double {
        var score = 0.0
        var factors = 0

        // Device count match
        if (characteristics.deviceCount in profile.typicalDeviceRange) {
            score += 1.0
        } else if (characteristics.deviceCount < profile.typicalDeviceRange.first) {
            score += 0.5 // Under capacity
        }
        factors++

        // AP count match
        if (characteristics.apCount in profile.recommendedApRange) {
            score += 1.0
        } else if (characteristics.apCount < profile.recommendedApRange.first &&
            profile.recommendedApRange.first == 1
        ) {
            score += 0.8 // Single AP acceptable for small networks
        }
        factors++

        // Security match
        val securityMatches =
            profile.recommendedSecurity.any {
                characteristics.securityType.contains(it.name, ignoreCase = true)
            }
        if (securityMatches) {
            score += 1.0
        }
        factors++

        return score / factors
    }

    companion object {
        /**
         * Create network type profiles with default definitions
         *
         * @return Network type profiles database
         */
        fun create(): NetworkTypeProfiles = NetworkTypeProfiles(DEFAULT_PROFILES)
    }
}

/**
 * Network profile definition
 *
 * @property networkType Network type classification
 * @property description Profile description
 * @property typicalDeviceRange Expected device count range
 * @property recommendedApRange Recommended number of APs
 * @property coverageAreaRange Typical coverage area in square meters
 * @property typicalUseCases Common use cases for this network type
 * @property securityRequirement Security requirement level
 * @property recommendedSecurity Recommended security configurations
 * @property performanceExpectations Performance characteristics
 * @property typicalChallenges Common deployment challenges
 * @property keyRecommendations Top recommendations for this network type
 */
data class NetworkProfile(
    val networkType: NetworkType,
    val description: String,
    val typicalDeviceRange: IntRange,
    val recommendedApRange: IntRange,
    val coverageAreaRange: IntRange,
    val typicalUseCases: List<String>,
    val securityRequirement: SecurityRequirement,
    val recommendedSecurity: List<SecurityTemplate>,
    val performanceExpectations: PerformanceExpectations,
    val typicalChallenges: List<String>,
    val keyRecommendations: List<String>,
)

/**
 * Performance expectations for network type
 *
 * @property expectedThroughput Expected throughput in Mbps
 * @property maxLatency Maximum acceptable latency in ms
 * @property reliabilityTarget Uptime target percentage
 * @property concurrentUserSupport Number of concurrent active users
 */
data class PerformanceExpectations(
    val expectedThroughput: String,
    val maxLatency: String,
    val reliabilityTarget: String,
    val concurrentUserSupport: String,
)

/**
 * Network characteristics for profile matching
 *
 * @property deviceCount Current device count
 * @property apCount Number of access points
 * @property securityType Current security type
 * @property coverageArea Coverage area in square meters
 */
data class NetworkCharacteristics(
    val deviceCount: Int,
    val apCount: Int,
    val securityType: String,
    val coverageArea: Int? = null,
)

/**
 * Profile match result
 *
 * @property networkType Matched network type
 * @property profile Network profile
 * @property matchScore Match confidence (0.0-1.0)
 */
data class ProfileMatch(
    val networkType: NetworkType,
    val profile: NetworkProfile,
    val matchScore: Double,
) {
    /**
     * Whether this is a good match (score >= 0.7)
     */
    val isGoodMatch: Boolean
        get() = matchScore >= 0.7
}

// ========================================
// Default Network Profiles
// ========================================

/**
 * Default network type profiles
 */
private val DEFAULT_PROFILES =
    mapOf(
        NetworkType.HOME_BASIC to
            NetworkProfile(
                networkType = NetworkType.HOME_BASIC,
                description = "Simple home network for family use with basic internet needs",
                typicalDeviceRange = 1..15,
                recommendedApRange = 1..1,
                coverageAreaRange = 50..150,
                typicalUseCases =
                    listOf(
                        "Web browsing and email",
                        "Streaming video (Netflix, YouTube)",
                        "Social media",
                        "Smart home devices (lights, thermostats)",
                        "Mobile devices (phones, tablets)",
                    ),
                securityRequirement = SecurityRequirement.BASIC,
                recommendedSecurity =
                    listOf(
                        SecurityTemplate.WPA2_WPA3_TRANSITION,
                        SecurityTemplate.WPA3_PERSONAL,
                    ),
                performanceExpectations =
                    PerformanceExpectations(
                        expectedThroughput = "50-200 Mbps per device",
                        maxLatency = "<50ms for local, <100ms for internet",
                        reliabilityTarget = "99% uptime",
                        concurrentUserSupport = "3-8 active users",
                    ),
                typicalChallenges =
                    listOf(
                        "Interference from neighboring networks",
                        "Coverage gaps in larger homes",
                        "IoT device compatibility (2.4 GHz only)",
                        "Guest network security",
                    ),
                keyRecommendations =
                    listOf(
                        "Enable WPA2/WPA3 transition mode for compatibility",
                        "Use dual-band (2.4 + 5 GHz) for device support",
                        "Enable guest network with isolation",
                        "Regular firmware updates for security",
                        "Change default admin password",
                    ),
            ),
        NetworkType.HOME_ADVANCED to
            NetworkProfile(
                networkType = NetworkType.HOME_ADVANCED,
                description = "Advanced home with smart home automation, NAS, and high bandwidth needs",
                typicalDeviceRange = 15..50,
                recommendedApRange = 2..4,
                coverageAreaRange = 150..400,
                typicalUseCases =
                    listOf(
                        "4K/8K streaming on multiple devices",
                        "Home automation (20+ IoT devices)",
                        "Network-attached storage (NAS)",
                        "Home office with VPN",
                        "Gaming consoles and PCs",
                        "Security cameras (WiFi)",
                    ),
                securityRequirement = SecurityRequirement.STANDARD,
                recommendedSecurity =
                    listOf(
                        SecurityTemplate.WPA3_PERSONAL,
                    ),
                performanceExpectations =
                    PerformanceExpectations(
                        expectedThroughput = "200-500 Mbps per device",
                        maxLatency = "<20ms for gaming, <50ms general",
                        reliabilityTarget = "99.5% uptime",
                        concurrentUserSupport = "8-20 active users",
                    ),
                typicalChallenges =
                    listOf(
                        "High device density",
                        "Mixed device capabilities (WiFi 4/5/6)",
                        "IoT device management and segmentation",
                        "Bandwidth contention",
                        "Whole-home coverage",
                    ),
                keyRecommendations =
                    listOf(
                        "Deploy mesh system or multiple APs for coverage",
                        "Separate 2.4 GHz network for IoT devices",
                        "Enable QoS for gaming and video",
                        "Use VLANs to isolate IoT from main network",
                        "Consider WiFi 6 for better multi-device performance",
                    ),
            ),
        NetworkType.SMALL_OFFICE to
            NetworkProfile(
                networkType = NetworkType.SMALL_OFFICE,
                description = "Small business office with 10-25 employees and moderate security needs",
                typicalDeviceRange = 10..40,
                recommendedApRange = 1..3,
                coverageAreaRange = 100..500,
                typicalUseCases =
                    listOf(
                        "Office productivity (email, documents)",
                        "VoIP phone system",
                        "Video conferencing",
                        "Cloud applications (Office 365, Google Workspace)",
                        "Guest WiFi for customers",
                        "Shared printers and scanners",
                    ),
                securityRequirement = SecurityRequirement.STANDARD,
                recommendedSecurity =
                    listOf(
                        SecurityTemplate.WPA3_PERSONAL,
                        SecurityTemplate.WPA2_ENTERPRISE,
                    ),
                performanceExpectations =
                    PerformanceExpectations(
                        expectedThroughput = "100-300 Mbps per user",
                        maxLatency = "<30ms for VoIP, <50ms general",
                        reliabilityTarget = "99.5% uptime during business hours",
                        concurrentUserSupport = "10-25 simultaneous users",
                    ),
                typicalChallenges =
                    listOf(
                        "VoIP quality during high usage",
                        "Guest network security and isolation",
                        "Video conference bandwidth management",
                        "Printer connectivity issues",
                        "Limited IT staff/expertise",
                    ),
                keyRecommendations =
                    listOf(
                        "Enable WPA3-Personal or WPA2-Enterprise",
                        "Separate guest network on different VLAN",
                        "Enable QoS with voice priority for VoIP",
                        "Configure 802.11k/r for seamless roaming",
                        "Regular password changes",
                        "Firewall and content filtering",
                    ),
            ),
        NetworkType.ENTERPRISE to
            NetworkProfile(
                networkType = NetworkType.ENTERPRISE,
                description = "Enterprise corporate network with 100+ users, high security, and managed infrastructure",
                typicalDeviceRange = 100..10000,
                recommendedApRange = 10..1000,
                coverageAreaRange = 1000..100000,
                typicalUseCases =
                    listOf(
                        "Corporate applications and services",
                        "VoIP and unified communications",
                        "Video conferencing (Zoom, Teams)",
                        "Mobile workforce (laptops, phones, tablets)",
                        "Guest and contractor access",
                        "BYOD (Bring Your Own Device)",
                        "Location-based services",
                    ),
                securityRequirement = SecurityRequirement.HIGH,
                recommendedSecurity =
                    listOf(
                        SecurityTemplate.WPA3_ENTERPRISE,
                        SecurityTemplate.WPA2_ENTERPRISE,
                    ),
                performanceExpectations =
                    PerformanceExpectations(
                        expectedThroughput = "100-500 Mbps per user",
                        maxLatency = "<20ms voice, <30ms general",
                        reliabilityTarget = "99.9% uptime",
                        concurrentUserSupport = "100-10000 simultaneous users",
                    ),
                typicalChallenges =
                    listOf(
                        "Large-scale deployment coordination",
                        "Seamless roaming across campus",
                        "Security policy enforcement",
                        "BYOD device management",
                        "Compliance requirements (PCI-DSS, HIPAA)",
                        "High-density areas (conference rooms, cafeterias)",
                        "RF planning and interference management",
                    ),
                keyRecommendations =
                    listOf(
                        "WPA3-Enterprise with 802.1X authentication",
                        "Enable 802.11k/r/v for enterprise mobility",
                        "Implement RADIUS server for authentication",
                        "Use network access control (NAC)",
                        "Separate SSIDs/VLANs for employees, guests, BYOD",
                        "Deploy centralized wireless controller",
                        "Regular site surveys and RF optimization",
                        "Enable client load balancing and band steering",
                        "Implement PMF (802.11w) for management frame protection",
                    ),
            ),
        NetworkType.HIGH_DENSITY to
            NetworkProfile(
                networkType = NetworkType.HIGH_DENSITY,
                description = "High-density deployment for conferences, stadiums, auditoriums with 100+ concurrent users",
                typicalDeviceRange = 100..5000,
                recommendedApRange = 10..200,
                coverageAreaRange = 500..50000,
                typicalUseCases =
                    listOf(
                        "Conference and convention centers",
                        "Stadiums and arenas",
                        "Lecture halls and auditoriums",
                        "Public venues with dense crowds",
                        "Temporary events",
                    ),
                securityRequirement = SecurityRequirement.STANDARD,
                recommendedSecurity =
                    listOf(
                        SecurityTemplate.WPA2_ENTERPRISE,
                        SecurityTemplate.ENHANCED_OPEN,
                    ),
                performanceExpectations =
                    PerformanceExpectations(
                        expectedThroughput = "10-50 Mbps per user (aggregate)",
                        maxLatency = "<100ms general use",
                        reliabilityTarget = "99% uptime during events",
                        concurrentUserSupport = "100-5000 simultaneous users",
                    ),
                typicalChallenges =
                    listOf(
                        "Extreme device density (100+ devices per AP)",
                        "Co-channel interference management",
                        "Fair bandwidth allocation",
                        "Broadcast/multicast traffic overhead",
                        "Client device diversity",
                        "Sudden traffic spikes",
                        "RF saturation",
                    ),
                keyRecommendations =
                    listOf(
                        "Disable 2.4 GHz band (use 5/6 GHz only)",
                        "Use 20 MHz channels for maximum channel availability",
                        "Reduce AP transmit power (40-50%)",
                        "Set client limit to 20-30 per AP",
                        "Enable aggressive band steering to 5/6 GHz",
                        "Enable airtime fairness",
                        "Increase minimum basic rate (12+ Mbps)",
                        "Disable SSID broadcast suppression",
                        "Use directional antennas",
                        "Implement per-client bandwidth limiting",
                    ),
            ),
        NetworkType.GUEST_NETWORK to
            NetworkProfile(
                networkType = NetworkType.GUEST_NETWORK,
                description = "Guest and public WiFi with isolation and access control",
                typicalDeviceRange = 5..500,
                recommendedApRange = 1..50,
                coverageAreaRange = 100..10000,
                typicalUseCases =
                    listOf(
                        "Coffee shops and restaurants",
                        "Hotel guest WiFi",
                        "Retail store customer WiFi",
                        "Office visitor access",
                        "Public library WiFi",
                    ),
                securityRequirement = SecurityRequirement.BASIC,
                recommendedSecurity =
                    listOf(
                        SecurityTemplate.ENHANCED_OPEN,
                        SecurityTemplate.WPA2_PERSONAL,
                    ),
                performanceExpectations =
                    PerformanceExpectations(
                        expectedThroughput = "5-25 Mbps per user",
                        maxLatency = "<200ms general browsing",
                        reliabilityTarget = "95% uptime",
                        concurrentUserSupport = "5-500 simultaneous users",
                    ),
                typicalChallenges =
                    listOf(
                        "Security isolation from corporate network",
                        "Bandwidth abuse prevention",
                        "Terms of service acceptance",
                        "Session management and timeouts",
                        "Legal liability for user actions",
                        "Content filtering requirements",
                    ),
                keyRecommendations =
                    listOf(
                        "Use Enhanced Open (OWE) or simple WPA2-PSK",
                        "Enable client isolation (prevents peer-to-peer)",
                        "Separate VLAN from corporate network",
                        "Implement captive portal for terms acceptance",
                        "Set per-client bandwidth limits (5-10 Mbps)",
                        "Configure session timeouts (2-4 hours)",
                        "Content filtering and logging",
                        "Rate limiting to prevent abuse",
                        "Disable access to local network resources",
                    ),
            ),
        NetworkType.MESH_NETWORK to
            NetworkProfile(
                networkType = NetworkType.MESH_NETWORK,
                description = "Mesh network with multiple APs and wireless backhaul",
                typicalDeviceRange = 10..100,
                recommendedApRange = 3..20,
                coverageAreaRange = 200..5000,
                typicalUseCases =
                    listOf(
                        "Large homes with difficult wiring",
                        "Retail stores with distributed coverage",
                        "Warehouses and industrial facilities",
                        "Outdoor campus environments",
                        "Temporary installations",
                    ),
                securityRequirement = SecurityRequirement.STANDARD,
                recommendedSecurity =
                    listOf(
                        SecurityTemplate.WPA3_PERSONAL,
                        SecurityTemplate.WPA2_ENTERPRISE,
                    ),
                performanceExpectations =
                    PerformanceExpectations(
                        expectedThroughput = "50-200 Mbps per user (varies by hop count)",
                        maxLatency = "<50ms for 1 hop, increases with distance",
                        reliabilityTarget = "99% uptime with self-healing",
                        concurrentUserSupport = "10-100 users depending on backhaul",
                    ),
                typicalChallenges =
                    listOf(
                        "Wireless backhaul bandwidth limitations",
                        "Multi-hop latency accumulation",
                        "Mesh topology optimization",
                        "Auto-healing after node failure",
                        "Loop prevention",
                        "Channel planning for backhaul vs. fronthaul",
                    ),
                keyRecommendations =
                    listOf(
                        "Use dedicated 5 GHz backhaul when possible",
                        "Enable 802.11s mesh standard support",
                        "Minimize hop count (prefer 1-2 hops maximum)",
                        "Use wired backhaul for key nodes",
                        "Enable fast roaming (802.11r)",
                        "Plan channel separation for backhaul",
                        "Deploy redundant paths for reliability",
                        "Monitor mesh link quality regularly",
                    ),
            ),
    )
