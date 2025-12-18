package io.lamco.netkit.advisor.knowledge

import io.lamco.netkit.advisor.model.*

/**
 * Vendor-specific knowledge database
 *
 * Provides vendor-specific best practices, known issues, optimal settings,
 * and feature information for major WiFi equipment manufacturers.
 *
 * This knowledge base helps provide targeted advice that accounts for:
 * - Vendor-specific feature sets
 * - Known hardware/firmware limitations
 * - Optimal configuration for specific platforms
 * - Vendor terminology and naming conventions
 *
 * Example Usage:
 * ```kotlin
 * val vendorKB = VendorSpecificKnowledgeBase.create()
 *
 * // Get Cisco-specific knowledge
 * val ciscoInfo = vendorKB.getVendorKnowledge(RouterVendor.CISCO)
 *
 * // Check for known issues
 * val issues = vendorKB.getKnownIssues(
 *     vendor = RouterVendor.CISCO,
 *     model = "Aironet 2800"
 * )
 *
 * // Get vendor best practices
 * val practices = vendorKB.getBestPractices(RouterVendor.UBIQUITI)
 * ```
 *
 * @property knowledgeBase Map of vendor to their knowledge entries
 */
class VendorSpecificKnowledgeBase(
    private val knowledgeBase: Map<RouterVendor, VendorKnowledge>
) {
    init {
        require(knowledgeBase.isNotEmpty()) { "Vendor knowledge base cannot be empty" }
    }

    /**
     * Get vendor-specific knowledge
     *
     * @param vendor Router vendor
     * @return Vendor knowledge, or null if not available
     */
    fun getVendorKnowledge(vendor: RouterVendor): VendorKnowledge? {
        return knowledgeBase[vendor]
    }

    /**
     * Get known issues for vendor
     *
     * @param vendor Router vendor
     * @param model Optional model filter
     * @param minimumSeverity Optional minimum severity filter
     * @return List of known issues
     */
    fun getKnownIssues(
        vendor: RouterVendor,
        model: String? = null,
        minimumSeverity: IssueSeverity = IssueSeverity.INFO
    ): List<VendorIssue> {
        val knowledge = getVendorKnowledge(vendor) ?: return emptyList()

        return knowledge.knownIssues
            .filter { it.severity.ordinal >= minimumSeverity.ordinal }
            .filter { issue ->
                model == null || issue.affectsAllModels ||
                issue.affectedModels.any { it.contains(model, ignoreCase = true) }
            }
    }

    /**
     * Get critical issues for vendor
     *
     * @param vendor Router vendor
     * @return List of critical issues
     */
    fun getCriticalIssues(vendor: RouterVendor): List<VendorIssue> {
        return getKnownIssues(vendor, minimumSeverity = IssueSeverity.CRITICAL)
    }

    /**
     * Get vendor best practices
     *
     * @param vendor Router vendor
     * @return List of best practice recommendations
     */
    fun getBestPractices(vendor: RouterVendor): List<String> {
        return getVendorKnowledge(vendor)?.bestPractices ?: emptyList()
    }

    /**
     * Get optimal settings for vendor
     *
     * @param vendor Router vendor
     * @return Map of setting names to recommended values
     */
    fun getOptimalSettings(vendor: RouterVendor): Map<String, String> {
        return getVendorKnowledge(vendor)?.optimalSettings ?: emptyMap()
    }

    /**
     * Get vendor-specific features
     *
     * @param vendor Router vendor
     * @return List of vendor features
     */
    fun getVendorFeatures(vendor: RouterVendor): List<VendorFeature> {
        return getVendorKnowledge(vendor)?.features ?: emptyList()
    }

    /**
     * Check if vendor has specific feature
     *
     * @param vendor Router vendor
     * @param featureName Feature name to check
     * @return true if vendor has the feature
     */
    fun hasFeature(vendor: RouterVendor, featureName: String): Boolean {
        return getVendorFeatures(vendor)
            .any { it.name.contains(featureName, ignoreCase = true) }
    }

    /**
     * Get summary of all vendors in knowledge base
     */
    fun getSummary(): VendorKnowledgeSummary {
        return VendorKnowledgeSummary(
            totalVendors = knowledgeBase.size,
            totalIssues = knowledgeBase.values.sumOf { it.knownIssues.size },
            criticalIssues = knowledgeBase.values.sumOf { it.criticalIssues.size },
            vendorsWithIssues = knowledgeBase.count { it.value.knownIssues.isNotEmpty() }
        )
    }

    companion object {
        /**
         * Create vendor knowledge base with default data
         *
         * Includes knowledge for major vendors: Cisco, Aruba, Ubiquiti, etc.
         *
         * @return Vendor knowledge base
         */
        fun create(): VendorSpecificKnowledgeBase {
            return VendorSpecificKnowledgeBase(DEFAULT_VENDOR_KNOWLEDGE)
        }
    }
}

/**
 * Vendor knowledge summary statistics
 *
 * @property totalVendors Number of vendors in database
 * @property totalIssues Total number of known issues
 * @property criticalIssues Number of critical issues
 * @property vendorsWithIssues Vendors that have documented issues
 */
data class VendorKnowledgeSummary(
    val totalVendors: Int,
    val totalIssues: Int,
    val criticalIssues: Int,
    val vendorsWithIssues: Int
)

// ========================================
// Default Vendor Knowledge Database
// ========================================

/**
 * Default vendor knowledge for major manufacturers
 */
private val DEFAULT_VENDOR_KNOWLEDGE = mapOf(
    RouterVendor.CISCO to VendorKnowledge(
        vendor = RouterVendor.CISCO,
        bestPractices = listOf(
            "Enable RRM (Radio Resource Management) for automatic channel and power optimization",
            "Use FlexConnect for branch office deployments to reduce WAN dependency",
            "Enable Fast Secure Roaming (802.11r) for VoWiFi deployments",
            "Configure TPC (Transmit Power Control) for automatic power adjustment",
            "Enable Coverage Hole Detection to identify RF dead zones",
            "Use CleanAir where available for spectrum intelligence and interference mitigation",
            "Implement ClientLink for improved performance with legacy clients",
            "Set DCA (Dynamic Channel Assignment) interval to 600 seconds for stable channels"
        ),
        knownIssues = listOf(
            VendorIssue(
                description = "ClientLink may cause connectivity issues with some legacy 802.11b/g devices",
                affectedModels = listOf("Aironet 2800", "Aironet 3800"),
                workaround = "Disable ClientLink on 2.4GHz SSID if experiencing legacy device issues",
                severity = IssueSeverity.MEDIUM
            ),
            VendorIssue(
                description = "High CPU utilization when CleanAir and spectrum monitoring both enabled",
                affectedModels = listOf("Aironet 1800", "Aironet 2800"),
                workaround = "Choose either CleanAir OR spectrum monitoring, not both simultaneously",
                severity = IssueSeverity.LOW
            )
        ),
        optimalSettings = mapOf(
            "DCA Interval" to "600 seconds (10 minutes)",
            "TPC Threshold" to "-70 dBm",
            "Coverage Hole Detection" to "Enable with 3 dB failure threshold",
            "RRM Update Interval" to "600 seconds",
            "Client Limit" to "200 per AP (adjust based on model)",
            "Aggressive Load Balancing" to "Disable (use moderate)"
        ),
        features = listOf(
            VendorFeature("CleanAir", "Spectrum intelligence for interference identification and mitigation"),
            VendorFeature("ClientLink", "Beamforming technology for improved performance with legacy clients"),
            VendorFeature("FlexConnect", "Branch office mode for reduced WAN dependency"),
            VendorFeature("RRM", "Radio Resource Management for automatic RF optimization"),
            VendorFeature("HDX", "High Density Experience for optimization in crowded environments", requiresLicense = true)
        )
    ),

    RouterVendor.ARUBA to VendorKnowledge(
        vendor = RouterVendor.ARUBA,
        bestPractices = listOf(
            "Enable ARM (Adaptive Radio Management) for automatic RF optimization",
            "Use ClientMatch for intelligent client steering between APs and bands",
            "Enable AirMatch for AI-powered RF planning and optimization",
            "Configure AppRF for application visibility and control",
            "Use Aruba Central or Mobility Controller for centralized management",
            "Enable Fast Roaming (802.11r/k/v) for seamless mobility",
            "Implement Dynamic Segmentation for zero-trust security",
            "Use Air Monitor mode on dedicated radios for security scanning"
        ),
        knownIssues = listOf(
            VendorIssue(
                description = "ClientMatch may cause ping-pong roaming in dense deployments if too aggressive",
                workaround = "Tune ClientMatch thresholds: set to moderate or conservative in dense environments",
                severity = IssueSeverity.MEDIUM
            )
        ),
        optimalSettings = mapOf(
            "ARM Mode" to "Adaptive (not Aggressive)",
            "ClientMatch" to "Enable with moderate thresholds",
            "AirMatch" to "Enable for automatic channel/power planning",
            "DFS" to "Enable for 5GHz channel flexibility",
            "Minimum Basic Rate" to "12 Mbps (disables 802.11b)"
        ),
        features = listOf(
            VendorFeature("ARM", "Adaptive Radio Management for automatic RF optimization"),
            VendorFeature("ClientMatch", "Intelligent client steering for optimal AP association"),
            VendorFeature("AirMatch", "AI-powered RF optimization"),
            VendorFeature("AppRF", "Deep packet inspection and application control", requiresLicense = true),
            VendorFeature("Dynamic Segmentation", "Policy-based network access without VLANs")
        )
    ),

    RouterVendor.UBIQUITI to VendorKnowledge(
        vendor = RouterVendor.UBIQUITI,
        bestPractices = listOf(
            "Use UniFi Controller for centralized management of all APs",
            "Enable Auto-Optimize Network for automatic channel and power optimization",
            "Configure Minimum RSSI to force clients to roam to stronger APs",
            "Enable Fast Roaming (802.11r) for VoIP and video applications",
            "Use Band Steering to prefer 5 GHz for capable clients",
            "Enable DFS channels for additional 5 GHz spectrum",
            "Implement VLANs for guest network isolation",
            "Regular firmware updates via UniFi Controller"
        ),
        knownIssues = listOf(
            VendorIssue(
                description = "Aggressive Minimum RSSI can cause client disconnections during movement",
                workaround = "Set Minimum RSSI to -75 dBm or higher (less negative) for mobile environments",
                severity = IssueSeverity.MEDIUM
            ),
            VendorIssue(
                description = "Some older UniFi APs may reboot when DFS channels detect radar",
                workaround = "Use non-DFS channels (36-48, 149-165) if DFS radar detection causes issues",
                severity = IssueSeverity.LOW,
                affectedFirmware = listOf("< 4.0")
            )
        ),
        optimalSettings = mapOf(
            "Minimum RSSI" to "-70 dBm (adjust based on environment)",
            "Band Steering" to "Prefer 5 GHz",
            "Fast Roaming" to "Enable (802.11r)",
            "Auto-Optimize Network" to "Enable",
            "Multicast Enhancement" to "Enable for IPTV/streaming",
            "TX Power" to "Medium or Auto"
        ),
        features = listOf(
            VendorFeature("Auto-Optimize", "Automatic channel and power optimization"),
            VendorFeature("Fast Roaming", "802.11r implementation for seamless transitions"),
            VendorFeature("Guest Portal", "Built-in captive portal for guest networks"),
            VendorFeature("Deep Packet Inspection", "Traffic analysis and categorization", requiresLicense = true)
        )
    ),

    RouterVendor.RUCKUS to VendorKnowledge(
        vendor = RouterVendor.RUCKUS,
        bestPractices = listOf(
            "Enable BeamFlex+ adaptive antenna technology",
            "Use SmartCast for automatic QoS and multicast optimization",
            "Enable ChannelFly for automatic channel optimization",
            "Configure SmartMesh for wireless backhaul in mesh deployments",
            "Use DPSK (Dynamic Pre-Shared Key) for simplified secure guest access",
            "Enable Band Steering to move clients to 5 GHz",
            "Implement SmartRoam (802.11k/v) for improved roaming"
        ),
        knownIssues = emptyList(),
        optimalSettings = mapOf(
            "ChannelFly" to "Enable for automatic channel selection",
            "BeamFlex+" to "Enable (always on)",
            "SmartCast" to "Enable for multicast optimization",
            "Background Scanning" to "Enable",
            "Client Load Balancing" to "Enable"
        ),
        features = listOf(
            VendorFeature("BeamFlex+", "Adaptive antenna technology for optimal signal direction"),
            VendorFeature("ChannelFly", "Machine learning-based channel optimization"),
            VendorFeature("SmartMesh", "Wireless backhaul for mesh deployments"),
            VendorFeature("DPSK", "Dynamic Pre-Shared Keys for secure simplified guest access")
        )
    ),

    RouterVendor.NETGEAR to VendorKnowledge(
        vendor = RouterVendor.NETGEAR,
        bestPractices = listOf(
            "Enable Smart Connect for automatic band steering",
            "Use Beamforming+ for improved range and performance",
            "Configure QoS for traffic prioritization (gaming, streaming)",
            "Enable MU-MIMO for simultaneous multi-device communication",
            "Use separate guest network with isolation",
            "Regular firmware updates for security patches",
            "Disable WPS after initial setup for security"
        ),
        knownIssues = listOf(
            VendorIssue(
                description = "Smart Connect band steering may be too aggressive for some clients",
                workaround = "Disable Smart Connect and use separate 2.4/5 GHz SSIDs if devices have connectivity issues",
                severity = IssueSeverity.LOW
            )
        ),
        optimalSettings = mapOf(
            "Smart Connect" to "Enable (test with your devices)",
            "Beamforming" to "Enable",
            "MU-MIMO" to "Enable",
            "TX Power" to "100% for Nighthawk series",
            "QoS" to "Enable for gaming/streaming"
        ),
        features = listOf(
            VendorFeature("Smart Connect", "Automatic band steering between 2.4/5 GHz"),
            VendorFeature("Beamforming+", "Enhanced beamforming for better range"),
            VendorFeature("Dynamic QoS", "Automatic traffic prioritization"),
            VendorFeature("Circle Smart Parental Controls", "Advanced parental control features")
        )
    ),

    RouterVendor.ASUS to VendorKnowledge(
        vendor = RouterVendor.ASUS,
        bestPractices = listOf(
            "Enable AiMesh for seamless mesh networking",
            "Use Adaptive QoS for automatic traffic prioritization",
            "Enable AiProtection for built-in security",
            "Configure Game Boost for gaming traffic prioritization",
            "Use Roaming Assistant to force clients to stronger APs",
            "Enable MU-MIMO and OFDMA (WiFi 6 models)",
            "Regular firmware updates via ASUS router app"
        ),
        knownIssues = emptyList(),
        optimalSettings = mapOf(
            "Roaming Assistant" to "Enable with -70 dBm threshold",
            "Universal Beamforming" to "Enable",
            "AiMesh" to "Enable for mesh deployments",
            "Adaptive QoS" to "Enable with appropriate profile",
            "TX Power" to "Adjust based on environment (start at 80%)"
        ),
        features = listOf(
            VendorFeature("AiMesh", "Whole-home mesh WiFi system"),
            VendorFeature("Adaptive QoS", "Automatic traffic prioritization"),
            VendorFeature("AiProtection", "Built-in security powered by Trend Micro"),
            VendorFeature("Game Boost", "Gaming traffic optimization")
        )
    )
)
