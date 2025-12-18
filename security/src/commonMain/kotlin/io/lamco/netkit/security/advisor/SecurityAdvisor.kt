package io.lamco.netkit.security.advisor

import io.lamco.netkit.security.analyzer.*
import io.lamco.netkit.security.model.SecurityLevel

/**
 * High-level security advisor providing comprehensive recommendations
 *
 * The SecurityAdvisor integrates all security analyzers to provide actionable
 * advice for improving WiFi network security. It prioritizes issues and generates
 * a security improvement roadmap.
 *
 * Integration:
 * - SecurityAnalyzer: Overall posture and threat assessment
 * - WpsAnalyzer: WPS vulnerability detection
 * - CipherAnalyzer: Cipher suite analysis
 * - PmfAnalyzer: PMF compliance checking
 * - ConfigAnalyzer: Configuration consistency
 *
 * Use Cases:
 * - Home network security assessment
 * - Small business network auditing
 * - Enterprise security consultation
 * - Automated security recommendations
 *
 * Example Usage:
 * ```kotlin
 * val advisor = SecurityAdvisor(
 *     securityAnalyzer = SecurityAnalyzer(),
 *     wpsAnalyzer = WpsAnalyzer(),
 *     cipherAnalyzer = CipherAnalyzer(),
 *     pmfAnalyzer = PmfAnalyzer(),
 *     configAnalyzer = ConfigAnalyzer()
 * )
 *
 * val advice = advisor.analyze(networkAnalysis)
 * advice.prioritizedActions.forEach { println(it.description) }
 * ```
 *
 * @property securityAnalyzer Overall security analyzer
 * @property wpsAnalyzer WPS vulnerability analyzer
 * @property cipherAnalyzer Cipher suite analyzer
 * @property pmfAnalyzer PMF compliance analyzer
 * @property configAnalyzer Configuration consistency analyzer
 *
 * @see SecurityAdvice
 * @see ActionItem
 */
class SecurityAdvisor(
    private val securityAnalyzer: SecurityAnalyzer = SecurityAnalyzer(),
    private val wpsAnalyzer: WpsAnalyzer = WpsAnalyzer(),
    private val cipherAnalyzer: CipherAnalyzer = CipherAnalyzer(),
    private val pmfAnalyzer: PmfAnalyzer = PmfAnalyzer(),
    private val configAnalyzer: ConfigAnalyzer = ConfigAnalyzer(),
) {
    /**
     * Analyze network and provide comprehensive security advice
     *
     * @param networkAnalysis Network security analysis
     * @return Security advice with prioritized recommendations
     */
    fun analyze(networkAnalysis: NetworkSecurityAnalysis): SecurityAdvice {
        val actions = mutableListOf<ActionItem>()

        networkAnalysis.bssAnalyses.forEach { bss ->
            actions.addAll(analyzeCriticalIssues(bss))
        }

        val wpsActions = analyzeWpsVulnerabilities(networkAnalysis.bssAnalyses)
        actions.addAll(wpsActions)

        val cipherActions = analyzeCipherIssues(networkAnalysis.bssAnalyses)
        actions.addAll(cipherActions)

        val pmfActions = analyzePmfCompliance(networkAnalysis.bssAnalyses)
        actions.addAll(pmfActions)

        if (networkAnalysis.bssAnalyses.size > 1) {
            val configActions = analyzeConfigurationConsistency(networkAnalysis.bssAnalyses)
            actions.addAll(configActions)
        }

        val prioritized =
            actions.sortedWith(
                compareByDescending<ActionItem> { it.priority.ordinal }
                    .thenBy { it.effort },
            )

        return SecurityAdvice(
            networkAnalysis = networkAnalysis,
            prioritizedActions = prioritized,
            estimatedImprovementScore = calculatePotentialImprovement(networkAnalysis, prioritized),
        )
    }

    /**
     * Analyze critical issues from BSS analysis
     *
     * @param bss BSS security analysis
     * @return List of action items for critical issues
     */
    private fun analyzeCriticalIssues(bss: BssSecurityAnalysis): List<ActionItem> {
        val actions = mutableListOf<ActionItem>()

        bss.criticalIssues.forEach { issue ->
            val action =
                when (issue.category) {
                    CriticalIssueCategory.SECURITY -> {
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Critical Security Issue",
                            description = issue.description,
                            recommendation = issue.recommendation,
                            priority = ActionPriority.CRITICAL,
                            effort = EffortLevel.HIGH,
                            category = ActionCategory.SECURITY,
                        )
                    }
                    CriticalIssueCategory.WPS -> {
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "WPS Vulnerability",
                            description = issue.description,
                            recommendation = issue.recommendation,
                            priority = ActionPriority.HIGH,
                            effort = EffortLevel.LOW,
                            category = ActionCategory.WPS,
                        )
                    }
                    CriticalIssueCategory.PMF -> {
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "PMF Not Enabled",
                            description = issue.description,
                            recommendation = issue.recommendation,
                            priority = ActionPriority.MEDIUM,
                            effort = EffortLevel.MEDIUM,
                            category = ActionCategory.PMF,
                        )
                    }
                    CriticalIssueCategory.CIPHER -> {
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Weak Cipher Configuration",
                            description = issue.description,
                            recommendation = issue.recommendation,
                            priority = ActionPriority.HIGH,
                            effort = EffortLevel.MEDIUM,
                            category = ActionCategory.CIPHER,
                        )
                    }
                    CriticalIssueCategory.CONFIGURATION -> {
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Configuration Issue",
                            description = issue.description,
                            recommendation = issue.recommendation,
                            priority = ActionPriority.MEDIUM,
                            effort = EffortLevel.MEDIUM,
                            category = ActionCategory.CONFIGURATION,
                        )
                    }
                    CriticalIssueCategory.COMPLIANCE -> {
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Compliance Violation",
                            description = issue.description,
                            recommendation = issue.recommendation,
                            priority = ActionPriority.HIGH,
                            effort = EffortLevel.HIGH,
                            category = ActionCategory.SECURITY,
                        )
                    }
                }
            actions.add(action)
        }

        return actions
    }

    /**
     * Analyze WPS vulnerabilities across network
     *
     * @param bssAnalyses List of BSS analyses
     * @return List of WPS-related action items
     */
    private fun analyzeWpsVulnerabilities(bssAnalyses: List<BssSecurityAnalysis>): List<ActionItem> {
        val actions = mutableListOf<ActionItem>()

        bssAnalyses.forEach { bss ->
            val wpsRisk = bss.wpsRisk
            if (wpsRisk != null && wpsRisk.riskScore > 0.3) {
                actions.add(
                    ActionItem(
                        bssid = bss.bssid,
                        ssid = bss.ssid,
                        title = "Disable WPS",
                        description = "WPS is enabled with risk score ${String.format("%.2f", wpsRisk.riskScore)}",
                        recommendation = "Disable WPS in router settings to prevent brute force attacks",
                        priority =
                            when {
                                wpsRisk.riskScore >= 0.9 -> ActionPriority.CRITICAL
                                wpsRisk.riskScore >= 0.6 -> ActionPriority.HIGH
                                else -> ActionPriority.MEDIUM
                            },
                        effort = EffortLevel.LOW,
                        category = ActionCategory.WPS,
                    ),
                )
            }
        }

        return actions
    }

    /**
     * Analyze cipher configuration issues
     *
     * @param bssAnalyses List of BSS analyses
     * @return List of cipher-related action items
     */
    private fun analyzeCipherIssues(bssAnalyses: List<BssSecurityAnalysis>): List<ActionItem> {
        val actions = mutableListOf<ActionItem>()

        bssAnalyses.forEach { bss ->
            val cipherScore = bss.securityScore.cipherStrengthScore

            when {
                cipherScore < 0.3 -> {
                    actions.add(
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Replace Insecure Ciphers",
                            description = "Network uses critically weak encryption (WEP/TKIP)",
                            recommendation = "Upgrade to WPA2 or WPA3 with AES-CCMP or AES-GCMP",
                            priority = ActionPriority.CRITICAL,
                            effort = EffortLevel.HIGH,
                            category = ActionCategory.CIPHER,
                        ),
                    )
                }
                cipherScore < 0.6 -> {
                    actions.add(
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Upgrade Cipher Suite",
                            description = "Network uses deprecated encryption",
                            recommendation = "Switch to modern AES-based ciphers (CCMP-128 or GCMP-256)",
                            priority = ActionPriority.HIGH,
                            effort = EffortLevel.MEDIUM,
                            category = ActionCategory.CIPHER,
                        ),
                    )
                }
                cipherScore < 0.8 -> {
                    actions.add(
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Optimize Cipher Configuration",
                            description = "Cipher configuration could be improved",
                            recommendation = "Consider upgrading to WPA3 with GCMP-256 for maximum security",
                            priority = ActionPriority.LOW,
                            effort = EffortLevel.MEDIUM,
                            category = ActionCategory.CIPHER,
                        ),
                    )
                }
            }
        }

        return actions
    }

    /**
     * Analyze PMF compliance
     *
     * @param bssAnalyses List of BSS analyses
     * @return List of PMF-related action items
     */
    private fun analyzePmfCompliance(bssAnalyses: List<BssSecurityAnalysis>): List<ActionItem> {
        val actions = mutableListOf<ActionItem>()

        bssAnalyses.forEach { bss ->
            val pmfScore = bss.securityScore.managementProtectionScore

            when {
                !bss.pmfEnabled && bss.pmfCapable && bss.securityScore.authType.requiresPmf -> {
                    actions.add(
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Enable PMF (Required)",
                            description = "PMF is required for ${bss.securityScore.authType.displayName} but not enabled",
                            recommendation = "Enable Protected Management Frames (802.11w) in router settings",
                            priority = ActionPriority.HIGH,
                            effort = EffortLevel.LOW,
                            category = ActionCategory.PMF,
                        ),
                    )
                }
                !bss.pmfEnabled && bss.pmfCapable && pmfScore < 0.5 -> {
                    actions.add(
                        ActionItem(
                            bssid = bss.bssid,
                            ssid = bss.ssid,
                            title = "Enable PMF (Recommended)",
                            description = "PMF is supported but not enabled, leaving network vulnerable to deauth attacks",
                            recommendation = "Enable Protected Management Frames (802.11w) for improved security",
                            priority = ActionPriority.MEDIUM,
                            effort = EffortLevel.LOW,
                            category = ActionCategory.PMF,
                        ),
                    )
                }
            }
        }

        return actions
    }

    /**
     * Analyze configuration consistency across multiple APs
     *
     * @param bssAnalyses List of BSS analyses
     * @return List of configuration-related action items
     */
    private fun analyzeConfigurationConsistency(bssAnalyses: List<BssSecurityAnalysis>): List<ActionItem> {
        val actions = mutableListOf<ActionItem>()

        // Group by SSID
        val bySSID = bssAnalyses.groupBy { it.ssid }

        bySSID.forEach { (ssid, analyses) ->
            if (analyses.size > 1) {
                // Check security consistency
                val uniqueSecurityLevels = analyses.map { it.securityScore.securityLevel }.distinct()
                if (uniqueSecurityLevels.size > 1) {
                    actions.add(
                        ActionItem(
                            bssid = "NETWORK",
                            ssid = ssid,
                            title = "Inconsistent Security Configuration",
                            description = "APs in '$ssid' have different security levels: ${uniqueSecurityLevels.joinToString()}",
                            recommendation = "Ensure all APs use identical security configuration for consistent user experience",
                            priority = ActionPriority.MEDIUM,
                            effort = EffortLevel.MEDIUM,
                            category = ActionCategory.CONFIGURATION,
                        ),
                    )
                }

                // Check PMF consistency
                val pmfEnabled = analyses.filter { it.pmfEnabled }.size
                if (pmfEnabled > 0 && pmfEnabled < analyses.size) {
                    actions.add(
                        ActionItem(
                            bssid = "NETWORK",
                            ssid = ssid,
                            title = "Inconsistent PMF Configuration",
                            description = "$pmfEnabled of ${analyses.size} APs have PMF enabled",
                            recommendation = "Enable PMF on all APs for consistent roaming behavior",
                            priority = ActionPriority.LOW,
                            effort = EffortLevel.LOW,
                            category = ActionCategory.CONFIGURATION,
                        ),
                    )
                }
            }
        }

        return actions
    }

    /**
     * Calculate potential security improvement from implementing actions
     *
     * @param networkAnalysis Current network analysis
     * @param actions Recommended actions
     * @return Estimated improvement in average security score (0.0-1.0)
     */
    private fun calculatePotentialImprovement(
        networkAnalysis: NetworkSecurityAnalysis,
        actions: List<ActionItem>,
    ): Double {
        val criticalActions = actions.count { it.priority == ActionPriority.CRITICAL }
        val highActions = actions.count { it.priority == ActionPriority.HIGH }
        val mediumActions = actions.count { it.priority == ActionPriority.MEDIUM }

        val currentScore = networkAnalysis.averageSecurityScore

        // Estimate improvement based on action priorities
        val estimatedImprovement =
            (criticalActions * 0.15) +
                (highActions * 0.10) +
                (mediumActions * 0.05)

        return (currentScore + estimatedImprovement).coerceIn(0.0, 1.0)
    }
}

/**
 * Security advice with prioritized action items
 *
 * @property networkAnalysis Current network security analysis
 * @property prioritizedActions List of recommended actions sorted by priority
 * @property estimatedImprovementScore Estimated security score after implementing recommendations
 */
data class SecurityAdvice(
    val networkAnalysis: NetworkSecurityAnalysis,
    val prioritizedActions: List<ActionItem>,
    val estimatedImprovementScore: Double,
) {
    init {
        require(estimatedImprovementScore in 0.0..1.0) {
            "Estimated improvement score must be between 0.0 and 1.0"
        }
    }

    /**
     * Actions by priority level
     */
    val actionsByPriority: Map<ActionPriority, List<ActionItem>>
        get() = prioritizedActions.groupBy { it.priority }

    /**
     * Actions by category
     */
    val actionsByCategory: Map<ActionCategory, List<ActionItem>>
        get() = prioritizedActions.groupBy { it.category }

    /**
     * Estimated improvement delta
     */
    val improvementDelta: Double
        get() = estimatedImprovementScore - networkAnalysis.averageSecurityScore

    /**
     * Quick summary of top recommendations
     */
    val quickSummary: String
        get() =
            buildString {
                appendLine("Security Advice Summary:")
                appendLine("Current Score: ${String.format("%.1f%%", networkAnalysis.averageSecurityScore * 100)}")
                appendLine("Potential Score: ${String.format("%.1f%%", estimatedImprovementScore * 100)}")
                appendLine("Improvement: +${String.format("%.1f%%", improvementDelta * 100)}")
                appendLine()
                appendLine("Top Recommendations (${prioritizedActions.take(3).size}):")
                prioritizedActions.take(3).forEachIndexed { index, action ->
                    appendLine("${index + 1}. [${action.priority}] ${action.title}")
                }
            }
}

/**
 * Action item for security improvement
 *
 * @property bssid BSSID of the AP (or "NETWORK" for network-wide actions)
 * @property ssid SSID of the network
 * @property title Short title of the action
 * @property description Detailed description of the issue
 * @property recommendation Specific recommendation to resolve the issue
 * @property priority Priority level of the action
 * @property effort Estimated effort to implement
 * @property category Category of the action
 */
data class ActionItem(
    val bssid: String,
    val ssid: String?,
    val title: String,
    val description: String,
    val recommendation: String,
    val priority: ActionPriority,
    val effort: EffortLevel,
    val category: ActionCategory,
) {
    /**
     * Action summary
     */
    val summary: String
        get() = "[$priority] $title - $ssid"
}

/**
 * Action priority levels
 */
enum class ActionPriority {
    /** Critical - immediate action required */
    CRITICAL,

    /** High - action needed soon */
    HIGH,

    /** Medium - recommended action */
    MEDIUM,

    /** Low - optional improvement */
    LOW,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                CRITICAL -> "Critical - Immediate Action Required"
                HIGH -> "High - Action Needed Soon"
                MEDIUM -> "Medium - Recommended"
                LOW -> "Low - Optional Improvement"
            }
}

/**
 * Effort levels for implementing actions
 */
enum class EffortLevel {
    /** Low effort - quick fix (minutes) */
    LOW,

    /** Medium effort - moderate change (hours) */
    MEDIUM,

    /** High effort - significant change (days) */
    HIGH,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                LOW -> "Low - Quick fix"
                MEDIUM -> "Medium - Moderate change"
                HIGH -> "High - Significant change"
            }
}

/**
 * Action categories
 */
enum class ActionCategory {
    /** General security improvement */
    SECURITY,

    /** WPS-related action */
    WPS,

    /** Cipher suite optimization */
    CIPHER,

    /** PMF enablement */
    PMF,

    /** Configuration consistency */
    CONFIGURATION,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                SECURITY -> "Security"
                WPS -> "WPS (Wi-Fi Protected Setup)"
                CIPHER -> "Encryption"
                PMF -> "PMF (Protected Management Frames)"
                CONFIGURATION -> "Configuration"
            }
}
