package io.lamco.netkit.advisor.intelligent

import io.lamco.netkit.advisor.knowledge.NetworkContext
import io.lamco.netkit.advisor.model.NetworkType
import io.lamco.netkit.advisor.troubleshooting.Difficulty
import io.lamco.netkit.advisor.troubleshooting.Impact

/**
 * Context-aware recommendation engine
 *
 * Provides personalized recommendations based on:
 * - User expertise level (beginner to expert)
 * - Network type and requirements
 * - Current usage patterns
 * - User goals and constraints
 *
 * **Approach**: Rule-based heuristics (NOT machine learning)
 *
 * Example Usage:
 * ```kotlin
 * val recommender = ContextAwareRecommender()
 *
 * val advice = recommender.getPersonalizedAdvice(
 *     context = AdviceContext(
 *         userLevel = UserExpertiseLevel.BEGINNER,
 *         networkType = NetworkType.HOME,
 *         goals = listOf("Better security", "Easy setup")
 *     )
 * )
 *
 * advice.recommendations.forEach { rec ->
 *     println("${rec.title}: ${rec.description}")
 * }
 * ```
 */
class ContextAwareRecommender {
    /**
     * Get personalized advice based on context
     *
     * @param context Advice context with user preferences
     * @return Personalized recommendations
     */
    fun getPersonalizedAdvice(context: AdviceContext): AdvisorRecommendations {
        val recommendations = mutableListOf<Recommendation>()

        recommendations.addAll(getExpertiseLevelRecommendations(context))
        recommendations.addAll(getNetworkTypeRecommendations(context))

        if (context.goals.isNotEmpty()) {
            recommendations.addAll(getGoalBasedRecommendations(context))
        }

        val filteredRecommendations = applyConstraints(recommendations, context.constraints)

        val sortedRecommendations =
            filteredRecommendations
                .filter { it.effort.ordinal <= context.userLevel.maxDifficulty.ordinal }
                .sortedByDescending { it.priority }

        val quickWins =
            sortedRecommendations.filter {
                it.effort == Difficulty.EASY && it.impact in listOf(Impact.MEDIUM, Impact.HIGH)
            }

        val longTerm =
            sortedRecommendations.filter {
                it.effort == Difficulty.HARD && it.impact == Impact.HIGH
            }

        return AdvisorRecommendations(
            recommendations = sortedRecommendations,
            quickWins = quickWins.take(3),
            longTermImprovements = longTerm.take(3),
        )
    }

    /**
     * Get recommendations tailored to user expertise level
     */
    private fun getExpertiseLevelRecommendations(context: AdviceContext): List<Recommendation> =
        when (context.userLevel) {
            UserExpertiseLevel.BEGINNER -> getBeginnerRecommendations(context)
            UserExpertiseLevel.INTERMEDIATE -> getIntermediateRecommendations(context)
            UserExpertiseLevel.ADVANCED -> getAdvancedRecommendations(context)
            UserExpertiseLevel.EXPERT -> getExpertRecommendations(context)
        }

    /**
     * Beginner-level recommendations (simple, high-impact)
     */
    private fun getBeginnerRecommendations(context: AdviceContext): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Enable WPA3 Encryption",
                description = "Protect your network with the latest security standard. This prevents unauthorized access.",
                category = RecommendationCategory.SECURITY,
                priority = 10,
                impact = Impact.HIGH,
                effort = Difficulty.EASY,
                steps =
                    listOf(
                        "Open your router's admin page",
                        "Navigate to Wireless Security settings",
                        "Select WPA3 (or WPA2/WPA3 mixed mode)",
                        "Save settings and reconnect devices",
                    ),
            ),
            Recommendation(
                title = "Change Default Password",
                description = "Default passwords are publicly known. Create a strong, unique password.",
                category = RecommendationCategory.SECURITY,
                priority = 10,
                impact = Impact.HIGH,
                effort = Difficulty.EASY,
                steps =
                    listOf(
                        "Access router admin page",
                        "Go to Administration > Password",
                        "Create strong password (12+ characters, mixed case, numbers, symbols)",
                        "Save and write down the new password",
                    ),
            ),
            Recommendation(
                title = "Update Router Firmware",
                description = "Keep your router secure with the latest software updates.",
                category = RecommendationCategory.SECURITY,
                priority = 8,
                impact = Impact.MEDIUM,
                effort = Difficulty.EASY,
                steps =
                    listOf(
                        "Check current firmware version in admin page",
                        "Download latest firmware from manufacturer",
                        "Upload and install via admin interface",
                        "Router will reboot automatically",
                    ),
            ),
            Recommendation(
                title = "Position Router Centrally",
                description = "Place your router in the center of your home for best coverage.",
                category = RecommendationCategory.CONFIGURATION,
                priority = 7,
                impact = Impact.MEDIUM,
                effort = Difficulty.EASY,
                steps =
                    listOf(
                        "Find central location in your home",
                        "Place router elevated (on shelf, not floor)",
                        "Keep away from walls and metal objects",
                        "Test signal strength in different rooms",
                    ),
            ),
        )

    /**
     * Intermediate-level recommendations (moderate complexity)
     */
    private fun getIntermediateRecommendations(context: AdviceContext): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Optimize Channel Selection",
                description = "Reduce interference by selecting the least congested WiFi channel.",
                category = RecommendationCategory.PERFORMANCE,
                priority = 8,
                impact = Impact.HIGH,
                effort = Difficulty.MEDIUM,
                steps =
                    listOf(
                        "Use WiFi analyzer app to scan channels",
                        "Identify least congested channel",
                        "Access router settings > Wireless",
                        "Change to optimal channel (1, 6, or 11 for 2.4GHz)",
                        "Save and test performance",
                    ),
            ),
            Recommendation(
                title = "Enable Band Steering",
                description = "Automatically direct capable devices to 5GHz for better performance.",
                category = RecommendationCategory.PERFORMANCE,
                priority = 7,
                impact = Impact.MEDIUM,
                effort = Difficulty.MEDIUM,
                steps =
                    listOf(
                        "Access router advanced wireless settings",
                        "Enable Band Steering (may be called Smart Connect)",
                        "Configure preference for 5GHz band",
                        "Save and monitor device connections",
                    ),
            ),
            Recommendation(
                title = "Configure QoS for Priority Traffic",
                description = "Ensure critical applications get bandwidth priority.",
                category = RecommendationCategory.CONFIGURATION,
                priority = 6,
                impact = Impact.MEDIUM,
                effort = Difficulty.MEDIUM,
                steps =
                    listOf(
                        "Navigate to QoS settings in router",
                        "Enable QoS/Traffic Prioritization",
                        "Set priorities: Video calls > Streaming > Web > Downloads",
                        "Apply device-specific rules if needed",
                    ),
            ),
            Recommendation(
                title = "Set Up Guest Network",
                description = "Isolate guest devices from your main network for security.",
                category = RecommendationCategory.SECURITY,
                priority = 6,
                impact = Impact.MEDIUM,
                effort = Difficulty.MEDIUM,
                steps =
                    listOf(
                        "Enable Guest Network feature",
                        "Use different SSID and password",
                        "Enable client isolation",
                        "Set bandwidth limits if desired",
                    ),
            ),
        )

    /**
     * Advanced-level recommendations (complex, technical)
     */
    private fun getAdvancedRecommendations(context: AdviceContext): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Implement VLAN Segmentation",
                description = "Separate network traffic by type for enhanced security and performance.",
                category = RecommendationCategory.SECURITY,
                priority = 8,
                impact = Impact.HIGH,
                effort = Difficulty.HARD,
                steps =
                    listOf(
                        "Plan VLAN structure (management, user, guest, IoT)",
                        "Configure VLANs on managed switch",
                        "Create SSID per VLAN on controller",
                        "Configure inter-VLAN routing rules",
                        "Test connectivity and isolation",
                    ),
            ),
            Recommendation(
                title = "Deploy 802.1X Authentication",
                description = "Enterprise-grade authentication for individual user credentials.",
                category = RecommendationCategory.SECURITY,
                priority = 9,
                impact = Impact.HIGH,
                effort = Difficulty.HARD,
                steps =
                    listOf(
                        "Set up RADIUS server (FreeRADIUS or cloud service)",
                        "Configure user accounts and certificates",
                        "Enable 802.1X on wireless network",
                        "Configure supplicant on client devices",
                        "Test authentication and fallback scenarios",
                    ),
            ),
            Recommendation(
                title = "Optimize Roaming with 802.11r/k/v",
                description = "Enable fast roaming standards for seamless handoffs between APs.",
                category = RecommendationCategory.PERFORMANCE,
                priority = 7,
                impact = Impact.MEDIUM,
                effort = Difficulty.HARD,
                steps =
                    listOf(
                        "Verify AP firmware supports 802.11r/k/v",
                        "Enable 802.11r (Fast Transition) on SSID",
                        "Enable 802.11k (Neighbor Reports)",
                        "Enable 802.11v (BSS Transition Management)",
                        "Tune RSSI thresholds for handoff",
                    ),
            ),
            Recommendation(
                title = "Implement Airtime Fairness",
                description = "Prevent slow devices from degrading overall network performance.",
                category = RecommendationCategory.PERFORMANCE,
                priority = 6,
                impact = Impact.MEDIUM,
                effort = Difficulty.HARD,
                steps =
                    listOf(
                        "Access advanced wireless settings",
                        "Enable Airtime Fairness feature",
                        "Configure fairness algorithm (per-client or per-SSID)",
                        "Monitor client performance metrics",
                        "Adjust as needed for environment",
                    ),
            ),
        )

    /**
     * Expert-level recommendations (cutting-edge, complex)
     */
    private fun getExpertRecommendations(context: AdviceContext): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Deploy WiFi 6E with Optimized RRM",
                description = "Leverage 6GHz spectrum with dynamic radio resource management.",
                category = RecommendationCategory.OPTIMIZATION,
                priority = 8,
                impact = Impact.HIGH,
                effort = Difficulty.HARD,
                steps =
                    listOf(
                        "Assess client device WiFi 6E support",
                        "Deploy WiFi 6E capable APs",
                        "Configure 6GHz SSID with WPA3-only",
                        "Enable dynamic channel allocation (DCA)",
                        "Tune transmit power control (TPC) algorithms",
                        "Monitor spectrum utilization and optimize",
                    ),
            ),
            Recommendation(
                title = "Implement AI-Driven RF Optimization",
                description = "Use controller ML features for automatic channel/power optimization.",
                category = RecommendationCategory.OPTIMIZATION,
                priority = 7,
                impact = Impact.HIGH,
                effort = Difficulty.HARD,
                steps =
                    listOf(
                        "Enable RRM (Radio Resource Management) on controller",
                        "Configure optimization parameters and constraints",
                        "Set optimization schedule (off-hours recommended)",
                        "Monitor optimization decisions and override if needed",
                        "Fine-tune algorithm weights based on environment",
                    ),
            ),
            Recommendation(
                title = "Configure Advanced Client Steering",
                description = "Implement sophisticated load balancing and band steering logic.",
                category = RecommendationCategory.PERFORMANCE,
                priority = 7,
                impact = Impact.MEDIUM,
                effort = Difficulty.HARD,
                steps =
                    listOf(
                        "Define client steering policy (RSSI-based, load-based, band-based)",
                        "Configure sticky client detection and mitigation",
                        "Set probe response suppression rules",
                        "Enable association control and thresholds",
                        "Monitor and log steering decisions",
                        "Adjust algorithms based on client behavior",
                    ),
            ),
        )

    /**
     * Get recommendations based on network type
     */
    private fun getNetworkTypeRecommendations(context: AdviceContext): List<Recommendation> =
        when (context.networkType) {
            NetworkType.HOME_BASIC, NetworkType.HOME_ADVANCED -> getHomeNetworkRecommendations()
            NetworkType.SMALL_OFFICE, NetworkType.MEDIUM_BUSINESS -> getSmallOfficeRecommendations()
            NetworkType.ENTERPRISE -> getEnterpriseRecommendations()
            NetworkType.RETAIL -> getRetailRecommendations()
            NetworkType.HOSPITALITY -> getHospitalityRecommendations()
            NetworkType.EDUCATION -> getEducationRecommendations()
            NetworkType.HEALTHCARE -> getHealthcareRecommendations()
            NetworkType.PUBLIC_VENUE, NetworkType.HIGH_DENSITY -> getPublicVenueRecommendations()
            NetworkType.GUEST_NETWORK -> getHospitalityRecommendations() // Guest networks similar to hospitality
            NetworkType.OUTDOOR -> getPublicVenueRecommendations() // Outdoor similar to public venue
            NetworkType.MESH_NETWORK -> getHomeNetworkRecommendations() // Mesh networks often for home
        }

    private fun getHomeNetworkRecommendations(): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Enable Parental Controls",
                description = "Manage internet access for family members and devices.",
                category = RecommendationCategory.CONFIGURATION,
                priority = 5,
                impact = Impact.MEDIUM,
                effort = Difficulty.EASY,
                steps = listOf("Enable parental controls in router", "Set schedules and content filters"),
            ),
        )

    private fun getSmallOfficeRecommendations(): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Separate Guest and Business Networks",
                description = "Isolate visitor traffic from business operations.",
                category = RecommendationCategory.SECURITY,
                priority = 8,
                impact = Impact.HIGH,
                effort = Difficulty.MEDIUM,
                steps = listOf("Create separate SSIDs", "Enable client isolation on guest network"),
            ),
        )

    private fun getEnterpriseRecommendations(): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Implement NAC (Network Access Control)",
                description = "Enforce device compliance before granting network access.",
                category = RecommendationCategory.SECURITY,
                priority = 9,
                impact = Impact.HIGH,
                effort = Difficulty.HARD,
                steps = listOf("Deploy NAC solution", "Define compliance policies", "Configure enforcement"),
            ),
        )

    private fun getRetailRecommendations(): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Segment POS Network with PCI-DSS Compliance",
                description = "Isolate payment systems to meet security standards.",
                category = RecommendationCategory.SECURITY,
                priority = 10,
                impact = Impact.HIGH,
                effort = Difficulty.HARD,
                steps = listOf("Create dedicated POS VLAN", "Implement strict firewall rules", "Enable encryption"),
            ),
        )

    private fun getHospitalityRecommendations(): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Enable Captive Portal for Guest Access",
                description = "Provide branded guest WiFi experience with terms acceptance.",
                category = RecommendationCategory.CONFIGURATION,
                priority = 7,
                impact = Impact.MEDIUM,
                effort = Difficulty.MEDIUM,
                steps = listOf("Configure captive portal", "Customize branding", "Set session timeout"),
            ),
        )

    private fun getEducationRecommendations(): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Implement CIPA-Compliant Content Filtering",
                description = "Filter inappropriate content to meet education regulations.",
                category = RecommendationCategory.SECURITY,
                priority = 9,
                impact = Impact.HIGH,
                effort = Difficulty.MEDIUM,
                steps = listOf("Deploy content filter", "Configure CIPA categories", "Enable reporting"),
            ),
        )

    private fun getHealthcareRecommendations(): List<Recommendation> =
        listOf(
            Recommendation(
                title = "HIPAA-Compliant Network Segmentation",
                description = "Separate medical devices and PHI data from general network.",
                category = RecommendationCategory.SECURITY,
                priority = 10,
                impact = Impact.HIGH,
                effort = Difficulty.HARD,
                steps = listOf("Create medical device VLAN", "Implement access controls", "Enable audit logging"),
            ),
        )

    private fun getPublicVenueRecommendations(): List<Recommendation> =
        listOf(
            Recommendation(
                title = "Deploy High-Density AP Configuration",
                description = "Optimize for large numbers of concurrent users.",
                category = RecommendationCategory.PERFORMANCE,
                priority = 8,
                impact = Impact.HIGH,
                effort = Difficulty.HARD,
                steps = listOf("Increase AP density", "Lower transmit power", "Enable load balancing"),
            ),
        )

    /**
     * Get recommendations based on user goals
     */
    private fun getGoalBasedRecommendations(context: AdviceContext): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()

        context.goals.forEach { goal ->
            when {
                goal.contains("security", ignoreCase = true) -> {
                    recommendations.add(
                        Recommendation(
                            title = "Security Audit",
                            description = "Review all security settings and configurations.",
                            category = RecommendationCategory.SECURITY,
                            priority = 9,
                            impact = Impact.HIGH,
                            effort = Difficulty.MEDIUM,
                            steps = listOf("Run security scan", "Review findings", "Apply fixes"),
                        ),
                    )
                }
                goal.contains("coverage", ignoreCase = true) || goal.contains("range", ignoreCase = true) -> {
                    recommendations.add(
                        Recommendation(
                            title = "Coverage Optimization",
                            description = "Perform site survey and optimize AP placement.",
                            category = RecommendationCategory.CONFIGURATION,
                            priority = 8,
                            impact = Impact.HIGH,
                            effort = Difficulty.MEDIUM,
                            steps = listOf("Conduct site survey", "Identify dead zones", "Add APs or adjust placement"),
                        ),
                    )
                }
                goal.contains("speed", ignoreCase = true) || goal.contains("performance", ignoreCase = true) -> {
                    recommendations.add(
                        Recommendation(
                            title = "Performance Tuning",
                            description = "Optimize for maximum throughput and low latency.",
                            category = RecommendationCategory.PERFORMANCE,
                            priority = 8,
                            impact = Impact.HIGH,
                            effort = Difficulty.MEDIUM,
                            steps = listOf("Enable 80MHz channels on 5GHz", "Optimize QoS", "Enable MU-MIMO"),
                        ),
                    )
                }
                goal.contains("guest", ignoreCase = true) -> {
                    recommendations.add(
                        Recommendation(
                            title = "Guest Network Setup",
                            description = "Configure isolated guest network with portal.",
                            category = RecommendationCategory.CONFIGURATION,
                            priority = 6,
                            impact = Impact.MEDIUM,
                            effort = Difficulty.MEDIUM,
                            steps = listOf("Create guest SSID", "Enable isolation", "Configure captive portal"),
                        ),
                    )
                }
            }
        }

        return recommendations
    }

    /**
     * Apply constraints to filter recommendations
     */
    private fun applyConstraints(
        recommendations: List<Recommendation>,
        constraints: List<String>,
    ): List<Recommendation> {
        if (constraints.isEmpty()) return recommendations

        return recommendations.filter { rec ->
            constraints.none { constraint ->
                when {
                    constraint.contains("budget", ignoreCase = true) -> rec.effort == Difficulty.HARD
                    constraint.contains("time", ignoreCase = true) -> rec.effort != Difficulty.EASY
                    constraint.contains("no hardware", ignoreCase = true) ->
                        rec.description.contains("add", ignoreCase = true) ||
                            rec.description.contains("deploy", ignoreCase = true)
                    else -> false
                }
            }
        }
    }
}
