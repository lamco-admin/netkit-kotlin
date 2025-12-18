package io.lamco.netkit.advisor.knowledge

import io.lamco.netkit.advisor.model.*

/**
 * Knowledge base of WiFi best practices
 *
 * Stores and provides access to expert-level WiFi configuration best practices,
 * organized by category, severity, and applicability. Rules are based on:
 * - IEEE 802.11 standards
 * - WiFi Alliance recommendations
 * - Vendor best practice guides
 * - Industry compliance requirements (PCI-DSS, HIPAA, etc.)
 * - Real-world deployment experience
 *
 * The knowledge base supports:
 * - **Filtering**: Get rules for specific contexts (network type, vendor, WiFi generation)
 * - **Validation**: Check if a configuration complies with best practices
 * - **Prioritization**: Get most critical rules first
 * - **Custom rules**: Add organization-specific rules
 *
 * Example Usage:
 * ```kotlin
 * val knowledgeBase = BestPracticesKnowledgeBase.create()
 *
 * // Get rules for enterprise WiFi 6 deployment
 * val rules = knowledgeBase.getBestPractices(
 *     networkType = NetworkType.ENTERPRISE,
 *     wifiGeneration = WifiGeneration.WIFI_6
 * )
 *
 * // Validate configuration
 * val result = knowledgeBase.validate(
 *     networkType = NetworkType.ENTERPRISE,
 *     currentSecurity = "WPA2-PSK",
 *     currentChannel = 6,
 *     clientCount = 50
 * )
 *
 * // Get critical rules only
 * val critical = knowledgeBase.getCriticalRules(NetworkType.ENTERPRISE)
 * ```
 *
 * @property rules Complete set of best practice rules
 *
 * @see BestPracticeRule
 * @see BestPracticeValidationResult
 */
class BestPracticesKnowledgeBase(
    private val rules: List<BestPracticeRule>
) {
    init {
        require(rules.isNotEmpty()) { "Knowledge base must contain at least one rule" }
    }

    /**
     * Get best practices for specific context
     *
     * Returns all rules applicable to the given network type, vendor, and WiFi generation.
     * Rules are sorted by severity (MUST first, then SHOULD, then MAY).
     *
     * @param networkType Type of network (home, office, enterprise)
     * @param vendor Optional router vendor for vendor-specific rules
     * @param wifiGeneration WiFi generation (WiFi 4/5/6/6E/7)
     * @return List of applicable best practice rules, sorted by severity
     */
    fun getBestPractices(
        networkType: NetworkType,
        vendor: RouterVendor? = null,
        wifiGeneration: WifiGeneration = WifiGeneration.WIFI_6
    ): List<BestPracticeRule> {
        return rules
            .filter { it.appliesTo(networkType, vendor, wifiGeneration) }
            .sortedByDescending { it.severity.priority }
    }

    /**
     * Get rules by category
     *
     * @param category Rule category (Security, Performance, etc.)
     * @param networkType Optional network type filter
     * @return List of rules in the specified category
     */
    fun getRulesByCategory(
        category: RuleCategory,
        networkType: NetworkType? = null
    ): List<BestPracticeRule> {
        var filtered = rules.filter { it.category == category }

        if (networkType != null) {
            filtered = filtered.filter {
                it.applicability.networkTypes.isEmpty() ||
                networkType in it.applicability.networkTypes
            }
        }

        return filtered.sortedByDescending { it.severity.priority }
    }

    /**
     * Get critical rules (MUST severity) for network type
     *
     * Returns only absolute requirements that must be followed.
     *
     * @param networkType Type of network
     * @param wifiGeneration WiFi generation
     * @return List of critical (MUST) rules
     */
    fun getCriticalRules(
        networkType: NetworkType,
        wifiGeneration: WifiGeneration = WifiGeneration.WIFI_6
    ): List<BestPracticeRule> {
        return getBestPractices(networkType, wifiGeneration = wifiGeneration)
            .filter { it.severity == RuleSeverity.MUST }
    }

    /**
     * Get recommended rules (SHOULD severity)
     *
     * Returns strong recommendations that should be followed unless there's
     * a valid reason not to.
     *
     * @param networkType Type of network
     * @param wifiGeneration WiFi generation
     * @return List of recommended (SHOULD) rules
     */
    fun getRecommendedRules(
        networkType: NetworkType,
        wifiGeneration: WifiGeneration = WifiGeneration.WIFI_6
    ): List<BestPracticeRule> {
        return getBestPractices(networkType, wifiGeneration = wifiGeneration)
            .filter { it.severity == RuleSeverity.SHOULD }
    }

    /**
     * Get optional rules (MAY severity)
     *
     * Returns optional optimizations that may provide additional benefits.
     *
     * @param networkType Type of network
     * @param wifiGeneration WiFi generation
     * @return List of optional (MAY) rules
     */
    fun getOptionalRules(
        networkType: NetworkType,
        wifiGeneration: WifiGeneration = WifiGeneration.WIFI_6
    ): List<BestPracticeRule> {
        return getBestPractices(networkType, wifiGeneration = wifiGeneration)
            .filter { it.severity == RuleSeverity.MAY }
    }

    /**
     * Validate configuration against best practices
     *
     * Checks if the current configuration follows best practices and returns
     * a validation result with violations and recommendations.
     *
     * @param context Network context for validation
     * @return Validation result with violations and score
     */
    fun validate(context: NetworkContext): BestPracticeValidationResult {
        val applicableRules = getBestPractices(
            networkType = context.networkType,
            vendor = context.vendor,
            wifiGeneration = context.wifiGeneration
        )

        val violations = mutableListOf<RuleViolation>()

        for (rule in applicableRules) {
            val violation = checkRuleViolation(rule, context)
            if (violation != null) {
                violations.add(violation)
            }
        }

        val totalWeight = applicableRules.sumOf { it.severity.priority }
        val violationWeight = violations.sumOf { it.rule.severity.priority }
        val complianceScore = if (totalWeight > 0) {
            ((totalWeight - violationWeight).toDouble() / totalWeight) * 100.0
        } else {
            100.0
        }

        return BestPracticeValidationResult(
            networkType = context.networkType,
            totalRulesChecked = applicableRules.size,
            violations = violations,
            complianceScore = complianceScore,
            complianceLevel = determineComplianceLevel(complianceScore)
        )
    }

    /**
     * Add custom rule to knowledge base
     *
     * Allows organizations to add their own specific requirements.
     *
     * @param rule Custom best practice rule
     * @return New knowledge base with added rule
     */
    fun addCustomRule(rule: BestPracticeRule): BestPracticesKnowledgeBase {
        return BestPracticesKnowledgeBase(rules + rule)
    }

    /**
     * Get summary statistics about the knowledge base
     */
    fun getSummary(): KnowledgeBaseSummary {
        return KnowledgeBaseSummary(
            totalRules = rules.size,
            rulesByCategory = rules.groupBy { it.category }
                .mapValues { it.value.size },
            rulesBySeverity = rules.groupBy { it.severity }
                .mapValues { it.value.size },
            networkTypeCoverage = rules.flatMap { it.applicability.networkTypes }
                .distinct()
                .size
        )
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Check if a rule is violated in the given context
     */
    private fun checkRuleViolation(
        rule: BestPracticeRule,
        context: NetworkContext
    ): RuleViolation? {
        val isViolated = when (rule.category) {
            RuleCategory.SECURITY -> checkSecurityRule(rule, context)
            RuleCategory.PERFORMANCE -> checkPerformanceRule(rule, context)
            RuleCategory.RELIABILITY -> checkReliabilityRule(rule, context)
            RuleCategory.CONFIGURATION -> checkConfigurationRule(rule, context)
            RuleCategory.COMPLIANCE -> checkComplianceRule(rule, context)
        }

        return if (isViolated) {
            RuleViolation(
                rule = rule,
                description = "Violation: ${rule.title}",
                recommendation = rule.description
            )
        } else {
            null
        }
    }

    private fun checkSecurityRule(rule: BestPracticeRule, context: NetworkContext): Boolean {
        return when {
            rule.title.contains("WPA3", ignoreCase = true) ->
                !context.securityType.contains("WPA3", ignoreCase = true)

            rule.title.contains("WPA2", ignoreCase = true) ->
                !context.securityType.contains("WPA2", ignoreCase = true) &&
                !context.securityType.contains("WPA3", ignoreCase = true)

            rule.title.contains("Open", ignoreCase = true) && rule.severity == RuleSeverity.MUST ->
                context.securityType.contains("Open", ignoreCase = true)

            else -> false  // Unknown rule, assume compliant
        }
    }

    private fun checkPerformanceRule(rule: BestPracticeRule, context: NetworkContext): Boolean {
        return when {
            rule.title.contains("5 GHz", ignoreCase = true) ->
                context.band5GHzEnabled == false

            rule.title.contains("channel width", ignoreCase = true) ->
                context.channelWidth != null && context.channelWidth < 40  // Example threshold

            rule.title.contains("2.4 GHz.*high density", ignoreCase = true) ->
                context.networkType == NetworkType.HIGH_DENSITY && context.band24GHzEnabled == true

            else -> false
        }
    }

    private fun checkReliabilityRule(rule: BestPracticeRule, context: NetworkContext): Boolean {
        return when {
            rule.title.contains("redundant AP", ignoreCase = true) ->
                (context.networkType == NetworkType.ENTERPRISE || context.networkType == NetworkType.MEDIUM_BUSINESS) &&
                context.apCount < 2

            else -> false
        }
    }

    private fun checkConfigurationRule(rule: BestPracticeRule, context: NetworkContext): Boolean {
        return false  // Placeholder - most config rules need specific context
    }

    private fun checkComplianceRule(rule: BestPracticeRule, context: NetworkContext): Boolean {
        return when {
            rule.title.contains("PCI", ignoreCase = true) ->
                context.networkType == NetworkType.ENTERPRISE &&
                !context.securityType.contains("WPA3", ignoreCase = true) &&
                !context.securityType.contains("Enterprise", ignoreCase = true)

            else -> false
        }
    }

    private fun determineComplianceLevel(score: Double): ComplianceLevel {
        return when {
            score >= 95.0 -> ComplianceLevel.EXCELLENT
            score >= 80.0 -> ComplianceLevel.GOOD
            score >= 60.0 -> ComplianceLevel.FAIR
            score >= 40.0 -> ComplianceLevel.POOR
            else -> ComplianceLevel.FAILING
        }
    }

    companion object {
        /**
         * Create knowledge base with default best practices
         *
         * Includes industry-standard rules from IEEE, WiFi Alliance,
         * and vendor best practice guides.
         *
         * @return Knowledge base with comprehensive ruleset
         */
        fun create(): BestPracticesKnowledgeBase {
            return BestPracticesKnowledgeBase(DEFAULT_RULES)
        }

        /**
         * Create knowledge base for specific compliance standard
         *
         * @param standard Compliance standard (PCI-DSS, HIPAA, etc.)
         * @return Knowledge base with compliance-focused rules
         */
        fun forCompliance(standard: ComplianceStandard): BestPracticesKnowledgeBase {
            val complianceRules = when (standard) {
                ComplianceStandard.PCI_DSS -> PCI_DSS_RULES
                ComplianceStandard.HIPAA -> HIPAA_RULES
                ComplianceStandard.GDPR -> GDPR_RULES
            }
            return BestPracticesKnowledgeBase(DEFAULT_RULES + complianceRules)
        }
    }
}

/**
 * Network context for validation
 *
 * @property networkType Type of network
 * @property vendor Router vendor
 * @property wifiGeneration WiFi generation
 * @property securityType Current security type (e.g., "WPA2-PSK", "WPA3-Enterprise")
 * @property band24GHzEnabled Whether 2.4 GHz is enabled
 * @property band5GHzEnabled Whether 5 GHz is enabled
 * @property channelWidth Current channel width in MHz
 * @property apCount Number of access points
 */
data class NetworkContext(
    val networkType: NetworkType,
    val vendor: RouterVendor? = null,
    val wifiGeneration: WifiGeneration,
    val securityType: String,
    val band24GHzEnabled: Boolean? = null,
    val band5GHzEnabled: Boolean? = null,
    val channelWidth: Int? = null,
    val apCount: Int = 1
)

/**
 * Best practice validation result
 *
 * @property networkType Network type that was validated
 * @property totalRulesChecked Number of rules checked
 * @property violations List of rule violations found
 * @property complianceScore Compliance score (0-100)
 * @property complianceLevel Compliance level classification
 */
data class BestPracticeValidationResult(
    val networkType: NetworkType,
    val totalRulesChecked: Int,
    val violations: List<RuleViolation>,
    val complianceScore: Double,
    val complianceLevel: ComplianceLevel
) {
    /**
     * Whether configuration is fully compliant (no violations)
     */
    val isCompliant: Boolean
        get() = violations.isEmpty()

    /**
     * Count of violations by severity
     */
    val violationsBySeverity: Map<RuleSeverity, Int>
        get() = violations.groupingBy { it.rule.severity }.eachCount()

    /**
     * Summary of validation result
     */
    val summary: String
        get() = buildString {
            append("$networkType validation: ")
            if (isCompliant) {
                append("COMPLIANT")
            } else {
                append("${violations.size} violation(s) - $complianceLevel (${complianceScore.toInt()}%)")
            }
        }
}

/**
 * Rule violation
 *
 * @property rule Rule that was violated
 * @property description Violation description
 * @property recommendation How to fix the violation
 */
data class RuleViolation(
    val rule: BestPracticeRule,
    val description: String,
    val recommendation: String
)

/**
 * Compliance level classification
 */
enum class ComplianceLevel {
    /** Excellent compliance (95%+) */
    EXCELLENT,

    /** Good compliance (80-94%) */
    GOOD,

    /** Fair compliance (60-79%) */
    FAIR,

    /** Poor compliance (40-59%) */
    POOR,

    /** Failing compliance (<40%) */
    FAILING;

    /**
     * Display name
     */
    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Knowledge base summary statistics
 *
 * @property totalRules Total number of rules
 * @property rulesByCategory Count of rules per category
 * @property rulesBySeverity Count of rules per severity
 * @property networkTypeCoverage Number of network types covered
 */
data class KnowledgeBaseSummary(
    val totalRules: Int,
    val rulesByCategory: Map<RuleCategory, Int>,
    val rulesBySeverity: Map<RuleSeverity, Int>,
    val networkTypeCoverage: Int
)

/**
 * Compliance standard classification
 */
enum class ComplianceStandard {
    /** Payment Card Industry Data Security Standard */
    PCI_DSS,

    /** Health Insurance Portability and Accountability Act */
    HIPAA,

    /** General Data Protection Regulation */
    GDPR;

    /**
     * Display name
     */
    val displayName: String
        get() = when (this) {
            PCI_DSS -> "PCI-DSS"
            HIPAA -> "HIPAA"
            GDPR -> "GDPR"
        }
}

// ========================================
// Default Rules Database
// ========================================

/**
 * Default best practice rules
 */
private val DEFAULT_RULES = listOf(
    // Security rules
    BestPracticeRule(
        category = RuleCategory.SECURITY,
        title = "Use WPA3 or WPA2-Enterprise for enterprise networks",
        description = "Enterprise networks must use WPA3-Enterprise or at minimum WPA2-Enterprise with 802.1X authentication",
        rationale = "Personal/PSK authentication is insufficient for enterprise security. Shared passwords cannot be revoked per-user.",
        severity = RuleSeverity.MUST,
        source = "IEEE 802.11-2020, WiFi Alliance WPA3 Specification",
        applicability = RuleApplicability(
            networkTypes = listOf(NetworkType.ENTERPRISE, NetworkType.MEDIUM_BUSINESS)
        )
    ),

    BestPracticeRule(
        category = RuleCategory.SECURITY,
        title = "Disable WEP and WPA1",
        description = "WEP and WPA1 (TKIP) must not be used due to known security vulnerabilities",
        rationale = "WEP can be cracked in minutes. WPA1/TKIP is vulnerable to dictionary attacks and has been deprecated since 2012.",
        severity = RuleSeverity.MUST,
        source = "WiFi Alliance, IEEE 802.11-2020",
        applicability = RuleApplicability()  // Universal
    ),

    BestPracticeRule(
        category = RuleCategory.SECURITY,
        title = "Enable WPA3 for new deployments",
        description = "Use WPA3-Personal or WPA3-Enterprise for new network deployments",
        rationale = "WPA3 provides enhanced security with SAE authentication, forward secrecy, and stronger encryption",
        severity = RuleSeverity.SHOULD,
        source = "WiFi Alliance WPA3 Specification",
        applicability = RuleApplicability(
            wifiGenerations = listOf(WifiGeneration.WIFI_6, WifiGeneration.WIFI_6E, WifiGeneration.WIFI_7)
        )
    ),

    // Performance rules
    BestPracticeRule(
        category = RuleCategory.PERFORMANCE,
        title = "Disable 2.4 GHz for high-density deployments",
        description = "In high-density environments, disable 2.4 GHz and use only 5/6 GHz bands",
        rationale = "2.4 GHz has only 3 non-overlapping channels and suffers from interference. 5/6 GHz provides more channels and better capacity.",
        severity = RuleSeverity.SHOULD,
        source = "Cisco High-Density WiFi Design Guide",
        applicability = RuleApplicability(
            networkTypes = listOf(NetworkType.HIGH_DENSITY, NetworkType.ENTERPRISE)
        )
    ),

    BestPracticeRule(
        category = RuleCategory.PERFORMANCE,
        title = "Use 20 MHz channels in high-density",
        description = "Use 20 MHz channel width in high-density deployments to maximize channel availability",
        rationale = "Wider channels reduce the number of available non-overlapping channels, increasing interference in high-density scenarios",
        severity = RuleSeverity.SHOULD,
        source = "IEEE 802.11ac/ax Design Recommendations",
        applicability = RuleApplicability(
            networkTypes = listOf(NetworkType.HIGH_DENSITY)
        )
    ),

    // Reliability rules
    BestPracticeRule(
        category = RuleCategory.RELIABILITY,
        title = "Deploy redundant APs for enterprise",
        description = "Enterprise networks should have overlapping AP coverage for seamless roaming",
        rationale = "Redundant coverage prevents dead zones and enables fast roaming for mobile clients",
        severity = RuleSeverity.SHOULD,
        source = "Industry Best Practice",
        applicability = RuleApplicability(
            networkTypes = listOf(NetworkType.ENTERPRISE, NetworkType.MEDIUM_BUSINESS)
        )
    )
)

/**
 * PCI-DSS compliance rules
 */
private val PCI_DSS_RULES = listOf(
    BestPracticeRule(
        category = RuleCategory.COMPLIANCE,
        title = "PCI-DSS: Use strong encryption (WPA2/WPA3)",
        description = "Networks handling payment card data must use WPA2-Enterprise or WPA3-Enterprise",
        rationale = "PCI-DSS Requirement 4.1: Use strong cryptography and security protocols to safeguard sensitive cardholder data",
        severity = RuleSeverity.MUST,
        source = "PCI-DSS v4.0 Requirement 4.1",
        applicability = RuleApplicability(
            networkTypes = listOf(NetworkType.ENTERPRISE, NetworkType.MEDIUM_BUSINESS, NetworkType.SMALL_OFFICE)
        )
    )
)

/**
 * HIPAA compliance rules
 */
private val HIPAA_RULES = listOf(
    BestPracticeRule(
        category = RuleCategory.COMPLIANCE,
        title = "HIPAA: Encrypt PHI in transit",
        description = "Networks transmitting Protected Health Information must use WPA3-Enterprise or WPA2-Enterprise",
        rationale = "HIPAA Security Rule requires encryption of ePHI during transmission",
        severity = RuleSeverity.MUST,
        source = "HIPAA Security Rule 45 CFR § 164.312(e)(1)",
        applicability = RuleApplicability(
            networkTypes = listOf(NetworkType.ENTERPRISE)
        )
    )
)

/**
 * GDPR compliance rules
 */
private val GDPR_RULES = listOf(
    BestPracticeRule(
        category = RuleCategory.COMPLIANCE,
        title = "GDPR: Secure personal data transmission",
        description = "Networks handling personal data must implement appropriate technical security measures",
        rationale = "GDPR Article 32 requires appropriate security measures including encryption of personal data",
        severity = RuleSeverity.MUST,
        source = "GDPR Article 32",
        applicability = RuleApplicability(
            networkTypes = listOf(NetworkType.ENTERPRISE, NetworkType.MEDIUM_BUSINESS)
        )
    )
)
