package io.lamco.netkit.advisor.intelligent

import io.lamco.netkit.advisor.model.NetworkType
import io.lamco.netkit.advisor.model.UseCase

/**
 * Interactive wizard guidance system
 *
 * Provides step-by-step guided setup for:
 * - Initial network configuration
 * - Troubleshooting workflows
 * - Security hardening
 * - Performance optimization
 * - Guest network setup
 * - Mesh network deployment
 *
 * **Approach**: Decision tree-based wizards with branching logic
 *
 * Example Usage:
 * ```kotlin
 * val guidance = InteractiveGuidance()
 *
 * // Start network setup wizard
 * val session = guidance.startWizard(WizardType.NETWORK_SETUP)
 *
 * // Process user responses
 * var current = session
 * while (!current.isComplete) {
 *     val step = current.current!!
 *     println("${step.title}: ${step.prompt}")
 *     val response = getUserInput()
 *     current = current.next(response)
 * }
 *
 * // Get final configuration
 * val result = guidance.completeWizard(current)
 * ```
 */
class InteractiveGuidance {

    /**
     * Start an interactive wizard
     *
     * @param wizardType Type of wizard to start
     * @param initialContext Optional initial context
     * @return Wizard session
     */
    fun startWizard(
        wizardType: WizardType,
        initialContext: Map<String, Any> = emptyMap()
    ): WizardSession {
        val steps = when (wizardType) {
            WizardType.NETWORK_SETUP -> createNetworkSetupSteps()
            WizardType.TROUBLESHOOTING -> createTroubleshootingSteps()
            WizardType.SECURITY_SETUP -> createSecuritySetupSteps()
            WizardType.PERFORMANCE_TUNING -> createPerformanceTuningSteps()
            WizardType.GUEST_NETWORK -> createGuestNetworkSteps()
            WizardType.MESH_SETUP -> createMeshSetupSteps()
        }

        return WizardSession(
            wizardType = wizardType,
            currentStep = 0,
            steps = steps,
            context = initialContext.toMutableMap()
        )
    }

    /**
     * Complete wizard and get result
     *
     * @param session Completed wizard session
     * @return Wizard result with recommendations
     */
    fun completeWizard(session: WizardSession): WizardResult {
        require(session.isComplete) { "Wizard not complete" }

        val recommendations = generateRecommendations(session)
        val nextSteps = generateNextSteps(session)

        return WizardResult(
            wizardType = session.wizardType,
            responses = session.responses,
            context = session.context,
            recommendations = recommendations,
            nextSteps = nextSteps
        )
    }

    /**
     * Get help for current wizard step
     *
     * @param session Current wizard session
     * @return Help text with examples
     */
    fun getHelpForStep(session: WizardSession): String {
        val step = session.current ?: return "Wizard complete"

        return buildString {
            appendLine("Help: ${step.title}")
            appendLine()
            appendLine(step.description)
            appendLine()
            if (step.isChoice && step.options.isNotEmpty()) {
                appendLine("Available options:")
                step.options.forEach { option ->
                    appendLine("  - $option")
                }
            }
            if (step.defaultValue != null) {
                appendLine()
                appendLine("Suggested: ${step.defaultValue}")
            }
            if (step.validation != null) {
                appendLine()
                appendLine("Requirements: ${step.validation}")
            }
        }
    }

    // ========================================
    // Wizard Step Generators
    // ========================================

    /**
     * Network setup wizard steps
     */
    private fun createNetworkSetupSteps(): List<WizardStep> {
        return listOf(
            WizardStep(
                step = 1,
                title = "Network Type",
                description = "What type of network are you setting up?",
                prompt = "Select your network type:",
                options = listOf("Home", "Small Office", "Enterprise", "Retail", "Hospitality"),
                defaultValue = "Home"
            ),
            WizardStep(
                step = 2,
                title = "Coverage Area",
                description = "How large is the area you need to cover?",
                prompt = "Enter coverage area in square meters:",
                defaultValue = "100",
                validation = "Must be a positive number"
            ),
            WizardStep(
                step = 3,
                title = "Device Count",
                description = "How many devices will connect to this network?",
                prompt = "Enter expected number of devices:",
                defaultValue = "10",
                validation = "Must be between 1 and 1000"
            ),
            WizardStep(
                step = 4,
                title = "Security Requirements",
                description = "What level of security do you need?",
                prompt = "Select security level:",
                options = listOf("Basic (WPA2)", "High (WPA3)", "Very High (Enterprise with 802.1X)"),
                defaultValue = "High (WPA3)"
            ),
            WizardStep(
                step = 5,
                title = "Guest Network",
                description = "Do you need a separate guest network?",
                prompt = "Enable guest network?",
                options = listOf("Yes", "No"),
                defaultValue = "No"
            ),
            WizardStep(
                step = 6,
                title = "IoT Devices",
                description = "Will you have IoT devices (smart home, cameras, etc.)?",
                prompt = "Enable separate IoT network?",
                options = listOf("Yes", "No"),
                defaultValue = "No"
            )
        )
    }

    /**
     * Troubleshooting wizard steps
     */
    private fun createTroubleshootingSteps(): List<WizardStep> {
        return listOf(
            WizardStep(
                step = 1,
                title = "Primary Issue",
                description = "What problem are you experiencing?",
                prompt = "Select the main issue:",
                options = listOf(
                    "Slow speeds",
                    "Cannot connect",
                    "Frequent disconnects",
                    "Poor coverage in some areas",
                    "High latency"
                )
            ),
            WizardStep(
                step = 2,
                title = "When It Occurs",
                description = "When does this problem happen?",
                prompt = "Select timing:",
                options = listOf("Always", "During specific times", "Intermittently", "With certain devices")
            ),
            WizardStep(
                step = 3,
                title = "Affected Devices",
                description = "Which devices are affected?",
                prompt = "Select devices:",
                options = listOf("All devices", "Some devices", "Specific device types", "One device")
            ),
            WizardStep(
                step = 4,
                title = "Recent Changes",
                description = "Have you made any recent changes?",
                prompt = "Select any recent changes:",
                options = listOf(
                    "No changes",
                    "Added new devices",
                    "Changed settings",
                    "Moved equipment",
                    "Updated firmware"
                )
            )
        )
    }

    /**
     * Security setup wizard steps
     */
    private fun createSecuritySetupSteps(): List<WizardStep> {
        return listOf(
            WizardStep(
                step = 1,
                title = "Current Security",
                description = "What security are you currently using?",
                prompt = "Select current encryption:",
                options = listOf("WPA3", "WPA2", "WPA/WPA2 Mixed", "Open (None)", "Don't know")
            ),
            WizardStep(
                step = 2,
                title = "Compliance Needs",
                description = "Do you need to meet compliance requirements?",
                prompt = "Select applicable compliance standards:",
                options = listOf("None", "PCI-DSS", "HIPAA", "GDPR", "Multiple standards")
            ),
            WizardStep(
                step = 3,
                title = "Authentication",
                description = "How should users authenticate?",
                prompt = "Select authentication method:",
                options = listOf(
                    "Shared password (PSK)",
                    "Individual user accounts (802.1X)",
                    "Captive portal",
                    "MAC address filtering"
                )
            ),
            WizardStep(
                step = 4,
                title = "Additional Features",
                description = "What additional security features do you need?",
                prompt = "Select features to enable:",
                options = listOf(
                    "Rogue AP detection",
                    "Intrusion detection",
                    "Content filtering",
                    "Client isolation",
                    "MAC filtering"
                )
            )
        )
    }

    /**
     * Performance tuning wizard steps
     */
    private fun createPerformanceTuningSteps(): List<WizardStep> {
        return listOf(
            WizardStep(
                step = 1,
                title = "Current Performance",
                description = "What speeds are you currently getting?",
                prompt = "Enter current WiFi speed (Mbps):",
                validation = "Must be a positive number"
            ),
            WizardStep(
                step = 2,
                title = "Expected Performance",
                description = "What speeds do you expect?",
                prompt = "Enter expected/ISP speed (Mbps):",
                validation = "Must be greater than current speed"
            ),
            WizardStep(
                step = 3,
                title = "Primary Use Case",
                description = "What is the network primarily used for?",
                prompt = "Select primary usage:",
                options = listOf(
                    "Video calls/conferencing",
                    "Streaming video",
                    "Gaming",
                    "General browsing",
                    "File transfers",
                    "Mixed usage"
                )
            ),
            WizardStep(
                step = 4,
                title = "Environment",
                description = "Describe your environment:",
                prompt = "Select environment type:",
                options = listOf(
                    "Dense urban (many neighbors)",
                    "Suburban",
                    "Rural",
                    "Office building",
                    "Industrial"
                )
            )
        )
    }

    /**
     * Guest network wizard steps
     */
    private fun createGuestNetworkSteps(): List<WizardStep> {
        return listOf(
            WizardStep(
                step = 1,
                title = "Guest Access Type",
                description = "How should guests access the network?",
                prompt = "Select access method:",
                options = listOf(
                    "Shared password",
                    "Captive portal with terms",
                    "Voucher/ticket system",
                    "Self-registration"
                )
            ),
            WizardStep(
                step = 2,
                title = "Session Duration",
                description = "How long should guest sessions last?",
                prompt = "Select session timeout:",
                options = listOf("1 hour", "4 hours", "24 hours", "7 days", "No limit"),
                defaultValue = "24 hours"
            ),
            WizardStep(
                step = 3,
                title = "Bandwidth Limits",
                description = "Should you limit guest bandwidth?",
                prompt = "Select bandwidth limit per guest:",
                options = listOf("No limit", "5 Mbps", "10 Mbps", "20 Mbps", "Custom"),
                defaultValue = "10 Mbps"
            ),
            WizardStep(
                step = 4,
                title = "Isolation",
                description = "Security and isolation settings:",
                prompt = "Enable client isolation?",
                options = listOf("Yes", "No"),
                defaultValue = "Yes"
            )
        )
    }

    /**
     * Mesh setup wizard steps
     */
    private fun createMeshSetupSteps(): List<WizardStep> {
        return listOf(
            WizardStep(
                step = 1,
                title = "Mesh Nodes",
                description = "How many mesh nodes will you deploy?",
                prompt = "Enter number of mesh nodes:",
                validation = "Must be at least 2"
            ),
            WizardStep(
                step = 2,
                title = "Backhaul",
                description = "How will nodes connect to each other?",
                prompt = "Select backhaul type:",
                options = listOf("Wireless", "Wired (Ethernet)", "Mixed"),
                defaultValue = "Wireless"
            ),
            WizardStep(
                step = 3,
                title = "Placement",
                description = "Have you planned node placement?",
                prompt = "Node placement strategy:",
                options = listOf(
                    "Need help with placement",
                    "Already planned",
                    "Will test and adjust"
                ),
                defaultValue = "Need help with placement"
            ),
            WizardStep(
                step = 4,
                title = "SSID Configuration",
                description = "Should all nodes use the same SSID?",
                prompt = "Unified SSID across all nodes?",
                options = listOf("Yes (recommended)", "No (separate SSIDs)"),
                defaultValue = "Yes (recommended)"
            )
        )
    }

    // ========================================
    // Result Generation
    // ========================================

    /**
     * Generate recommendations based on wizard responses
     */
    private fun generateRecommendations(session: WizardSession): List<String> =
        when (session.wizardType) {
            WizardType.NETWORK_SETUP -> generateNetworkSetupRecommendations(session)
            WizardType.TROUBLESHOOTING -> generateTroubleshootingRecommendations(session)
            WizardType.SECURITY_SETUP -> generateSecuritySetupRecommendations(session)
            WizardType.PERFORMANCE_TUNING -> generatePerformanceTuningRecommendations()
            WizardType.GUEST_NETWORK -> generateGuestNetworkRecommendations(session)
            WizardType.MESH_SETUP -> generateMeshSetupRecommendations(session)
        }

    /**
     * Generate network setup recommendations.
     */
    private fun generateNetworkSetupRecommendations(session: WizardSession): List<String> {
        val recommendations = mutableListOf<String>()
        val networkType = session.responses[0] ?: "Home"
        val coverage = session.responses[1]?.toDoubleOrNull() ?: 100.0
        val devices = session.responses[2]?.toIntOrNull() ?: 10

        recommendations.add("Deploy ${(coverage / 150).toInt().coerceAtLeast(1)} access point(s) for $coverage m²")
        recommendations.add("Configure capacity for $devices devices")

        val security = session.responses[3] ?: ""
        if (security.contains("WPA3")) {
            recommendations.add("Enable WPA3 encryption on all SSIDs")
        }

        if (session.responses[4] == "Yes") {
            recommendations.add("Configure separate guest network with client isolation")
        }

        if (session.responses[5] == "Yes") {
            recommendations.add("Create dedicated IoT network (2.4GHz, WPA2 for compatibility)")
        }

        return recommendations
    }

    /**
     * Generate troubleshooting recommendations.
     */
    private fun generateTroubleshootingRecommendations(session: WizardSession): List<String> {
        val recommendations = mutableListOf<String>()
        val issue = session.responses[0] ?: ""

        when {
            issue.contains("Slow") -> {
                recommendations.add("Check WiFi channel congestion")
                recommendations.add("Verify signal strength at affected locations")
                recommendations.add("Test wired connection to rule out ISP issues")
            }
            issue.contains("Cannot connect") -> {
                recommendations.add("Verify password is correct")
                recommendations.add("Check device compatibility with security settings")
                recommendations.add("Restart router and try again")
            }
            issue.contains("disconnect") -> {
                recommendations.add("Check for interference sources")
                recommendations.add("Verify signal strength is adequate (-70 dBm or better)")
                recommendations.add("Update router firmware")
            }
        }

        return recommendations
    }

    /**
     * Generate security setup recommendations.
     */
    private fun generateSecuritySetupRecommendations(session: WizardSession): List<String> {
        val recommendations = mutableListOf<String>()
        val current = session.responses[0] ?: ""

        if (!current.contains("WPA3")) {
            recommendations.add("Upgrade to WPA3 encryption")
        }

        val compliance = session.responses[1] ?: ""
        if (compliance.contains("PCI")) {
            recommendations.add("Implement PCI-DSS requirements: network segmentation, strong encryption")
        }
        if (compliance.contains("HIPAA")) {
            recommendations.add("Configure HIPAA compliance: encryption, access controls, audit logging")
        }

        val auth = session.responses[2] ?: ""
        if (auth.contains("802.1X")) {
            recommendations.add("Deploy RADIUS server for 802.1X authentication")
        }

        return recommendations
    }

    /**
     * Generate performance tuning recommendations.
     */
    private fun generatePerformanceTuningRecommendations(): List<String> =
        listOf(
            "Run WiFi analyzer to find optimal channels",
            "Enable band steering to prefer 5GHz",
            "Configure QoS based on primary use case",
            "Update to WiFi 6 APs if possible for better performance",
        )

    /**
     * Generate guest network recommendations.
     */
    private fun generateGuestNetworkRecommendations(session: WizardSession): List<String> {
        val recommendations = mutableListOf(
            "Enable client isolation on guest network",
            "Configure chosen access method and session timeout",
        )

        val bandwidth = session.responses[2] ?: ""
        if (!bandwidth.contains("No limit")) {
            recommendations.add("Apply bandwidth limit: $bandwidth per client")
        }

        return recommendations
    }

    /**
     * Generate mesh setup recommendations.
     */
    private fun generateMeshSetupRecommendations(session: WizardSession): List<String> {
        val recommendations = mutableListOf<String>()
        val nodes = session.responses[0]?.toIntOrNull() ?: 2

        recommendations.add("Deploy $nodes mesh nodes with optimal spacing")

        val backhaul = session.responses[1] ?: ""
        if (backhaul.contains("Wired")) {
            recommendations.add("Use wired backhaul for best performance")
        } else {
            recommendations.add("Use dedicated 5GHz backhaul for wireless mesh")
        }

        recommendations.add("Use unified SSID for seamless roaming")
        recommendations.add("Enable 802.11r fast roaming for better handoffs")

        return recommendations
    }

    /**
     * Generate next steps
     */
    private fun generateNextSteps(session: WizardSession): List<String> {
        return listOf(
            "Review and implement the recommendations above",
            "Test the configuration in a staging environment if possible",
            "Monitor network performance after implementation",
            "Document the configuration for future reference"
        )
    }
}

/**
 * Wizard completion result
 */
data class WizardResult(
    val wizardType: WizardType,
    val responses: Map<Int, String>,
    val context: Map<String, Any>,
    val recommendations: List<String>,
    val nextSteps: List<String>
) {
    /**
     * Summary of wizard result
     */
    val summary: String
        get() = "${wizardType.displayName} complete: ${recommendations.size} recommendation(s)"
}
