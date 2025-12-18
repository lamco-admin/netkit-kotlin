package io.lamco.netkit.security.advisor

import io.lamco.netkit.model.topology.AuthType
import io.lamco.netkit.model.topology.CipherSuite
import io.lamco.netkit.security.analyzer.BssSecurityAnalysis
import io.lamco.netkit.security.analyzer.ThreatLevel
import io.lamco.netkit.security.model.SecurityLevel

/**
 * Rule-based security policy evaluation engine
 *
 * The RuleEngine evaluates WiFi network security against a configurable set of rules,
 * providing policy compliance checking and violation detection.
 *
 * Use Cases:
 * - Corporate security policy enforcement
 * - Industry compliance validation (PCI-DSS, HIPAA, etc.)
 * - Custom security standards
 * - Network security auditing
 *
 * Rule Categories:
 * - Authentication requirements (minimum auth type)
 * - Cipher requirements (prohibited/required ciphers)
 * - PMF requirements (mandatory/optional)
 * - WPS policy (allowed/prohibited)
 * - Security level requirements (minimum acceptable level)
 *
 * Example Usage:
 * ```kotlin
 * val rules = listOf(
 *     SecurityRule.RequireMinimumAuth(AuthType.WPA3_SAE),
 *     SecurityRule.ProhibitCipher(CipherSuite.TKIP),
 *     SecurityRule.RequirePmf,
 *     SecurityRule.ProhibitWps
 * )
 *
 * val engine = RuleEngine(rules)
 * val evaluation = engine.evaluate(bssAnalyses)
 * ```
 *
 * @property rules List of security rules to evaluate
 *
 * @see SecurityRule
 * @see RuleEvaluation
 */
class RuleEngine(
    private val rules: List<SecurityRule>,
) {
    init {
        require(rules.isNotEmpty()) { "At least one security rule must be provided" }
    }

    /**
     * Evaluate security analyses against configured rules
     *
     * @param analyses List of BSS security analyses to evaluate
     * @return Rule evaluation results
     */
    fun evaluate(analyses: List<BssSecurityAnalysis>): RuleEvaluation {
        require(analyses.isNotEmpty()) { "At least one security analysis required for evaluation" }

        val violations = mutableListOf<RuleViolation>()

        // Evaluate each rule against each BSS
        for (rule in rules) {
            for (analysis in analyses) {
                val violation = evaluateRule(rule, analysis)
                if (violation != null) {
                    violations.add(violation)
                }
            }
        }

        return RuleEvaluation(
            totalBssCount = analyses.size,
            rulesEvaluated = rules.size,
            violations = violations,
            complianceScore = calculateComplianceScore(analyses.size, violations.size),
        )
    }

    /**
     * Evaluate a single rule against a BSS analysis
     *
     * @param rule Security rule to evaluate
     * @param analysis BSS security analysis
     * @return RuleViolation if rule is violated, null otherwise
     */
    private fun evaluateRule(
        rule: SecurityRule,
        analysis: BssSecurityAnalysis,
    ): RuleViolation? {
        val isViolated =
            when (rule) {
                is SecurityRule.RequireMinimumAuth -> {
                    analysis.securityScore.authType.securityStrength < rule.minimumAuthType.securityStrength
                }
                is SecurityRule.ProhibitAuthType -> {
                    analysis.securityScore.authType == rule.prohibitedAuthType
                }
                is SecurityRule.ProhibitCipher -> {
                    analysis.securityScore.cipherSet.contains(rule.prohibitedCipher)
                }
                is SecurityRule.RequireCipher -> {
                    !analysis.securityScore.cipherSet.contains(rule.requiredCipher)
                }
                is SecurityRule.RequirePmf -> {
                    !analysis.pmfEnabled || !analysis.pmfCapable
                }
                is SecurityRule.ProhibitWps -> {
                    analysis.wpsRisk?.riskScore ?: 0.0 > 0.0
                }
                is SecurityRule.RequireMinimumSecurityLevel -> {
                    analysis.securityScore.securityLevel.ordinal < rule.minimumLevel.ordinal
                }
                is SecurityRule.ProhibitThreatLevel -> {
                    analysis.threatLevel.ordinal >= rule.prohibitedLevel.ordinal
                }
            }

        return if (isViolated) {
            RuleViolation(
                bssid = analysis.bssid,
                ssid = analysis.ssid,
                rule = rule,
                severity = rule.severity,
                description = rule.generateViolationDescription(analysis),
            )
        } else {
            null
        }
    }

    /**
     * Calculate overall compliance score
     *
     * @param totalBss Total number of BSSs evaluated
     * @param violationCount Number of violations detected
     * @return Compliance score (0.0-1.0)
     */
    private fun calculateComplianceScore(
        totalBss: Int,
        violationCount: Int,
    ): Double {
        val maxPossibleViolations = totalBss * rules.size
        return if (maxPossibleViolations > 0) {
            1.0 - (violationCount.toDouble() / maxPossibleViolations)
        } else {
            1.0
        }
    }

    companion object {
        /**
         * Create a strict corporate security policy engine
         *
         * Requirements:
         * - WPA3 or WPA2-Enterprise only
         * - No WEP, TKIP, or WPS
         * - PMF required
         * - Minimum GOOD security level
         *
         * @return RuleEngine with strict corporate rules
         */
        fun strictCorporatePolicy(): RuleEngine =
            RuleEngine(
                listOf(
                    SecurityRule.RequireMinimumAuth(AuthType.WPA2_ENTERPRISE),
                    SecurityRule.ProhibitAuthType(AuthType.WEP),
                    SecurityRule.ProhibitAuthType(AuthType.WPA_PSK),
                    SecurityRule.ProhibitCipher(CipherSuite.WEP_40),
                    SecurityRule.ProhibitCipher(CipherSuite.WEP_104),
                    SecurityRule.ProhibitCipher(CipherSuite.TKIP),
                    SecurityRule.RequirePmf,
                    SecurityRule.ProhibitWps,
                    SecurityRule.RequireMinimumSecurityLevel(SecurityLevel.GOOD),
                ),
            )

        /**
         * Create a moderate security policy engine
         *
         * Requirements:
         * - WPA2-Personal or better
         * - No WEP
         * - Minimum FAIR security level
         *
         * @return RuleEngine with moderate security rules
         */
        fun moderateSecurityPolicy(): RuleEngine =
            RuleEngine(
                listOf(
                    SecurityRule.RequireMinimumAuth(AuthType.WPA2_PSK),
                    SecurityRule.ProhibitAuthType(AuthType.WEP),
                    SecurityRule.ProhibitCipher(CipherSuite.WEP_40),
                    SecurityRule.ProhibitCipher(CipherSuite.WEP_104),
                    SecurityRule.RequireMinimumSecurityLevel(SecurityLevel.FAIR),
                ),
            )

        /**
         * Create a PCI-DSS compliance policy engine
         *
         * Requirements based on PCI-DSS v4.0:
         * - Strong encryption (WPA2/WPA3)
         * - No default/weak passwords (enforced via minimum security level)
         * - Regular security assessment
         *
         * @return RuleEngine with PCI-DSS compliance rules
         */
        fun pciDssCompliance(): RuleEngine =
            RuleEngine(
                listOf(
                    SecurityRule.RequireMinimumAuth(AuthType.WPA2_PSK),
                    SecurityRule.ProhibitAuthType(AuthType.WEP),
                    SecurityRule.ProhibitAuthType(AuthType.OPEN),
                    SecurityRule.ProhibitCipher(CipherSuite.WEP_40),
                    SecurityRule.ProhibitCipher(CipherSuite.WEP_104),
                    SecurityRule.ProhibitCipher(CipherSuite.TKIP),
                    SecurityRule.RequireMinimumSecurityLevel(SecurityLevel.GOOD),
                    SecurityRule.ProhibitThreatLevel(ThreatLevel.HIGH),
                ),
            )
    }
}

/**
 * Security rule for policy evaluation
 *
 * Sealed class representing different types of security rules that can be
 * evaluated against WiFi network configurations.
 */
sealed class SecurityRule {
    /**
     * Rule severity level
     */
    abstract val severity: RuleSeverity

    /**
     * Rule description
     */
    abstract val description: String

    /**
     * Generate violation description for a specific BSS
     *
     * @param analysis BSS security analysis that violated the rule
     * @return Human-readable violation description
     */
    abstract fun generateViolationDescription(analysis: BssSecurityAnalysis): String

    /**
     * Require minimum authentication type
     *
     * @property minimumAuthType Minimum acceptable authentication type
     */
    data class RequireMinimumAuth(
        val minimumAuthType: AuthType,
    ) : SecurityRule() {
        override val severity = RuleSeverity.HIGH
        override val description = "Require minimum authentication: ${minimumAuthType.displayName}"

        override fun generateViolationDescription(analysis: BssSecurityAnalysis): String =
            "Authentication type ${analysis.securityScore.authType.displayName} " +
                "does not meet minimum requirement of ${minimumAuthType.displayName}"
    }

    /**
     * Prohibit specific authentication type
     *
     * @property prohibitedAuthType Authentication type that is not allowed
     */
    data class ProhibitAuthType(
        val prohibitedAuthType: AuthType,
    ) : SecurityRule() {
        override val severity =
            when (prohibitedAuthType) {
                AuthType.WEP -> RuleSeverity.CRITICAL
                AuthType.OPEN -> RuleSeverity.HIGH
                else -> RuleSeverity.MEDIUM
            }
        override val description = "Prohibit authentication type: ${prohibitedAuthType.displayName}"

        override fun generateViolationDescription(analysis: BssSecurityAnalysis): String =
            "Prohibited authentication type ${prohibitedAuthType.displayName} detected"
    }

    /**
     * Prohibit specific cipher suite
     *
     * @property prohibitedCipher Cipher suite that is not allowed
     */
    data class ProhibitCipher(
        val prohibitedCipher: CipherSuite,
    ) : SecurityRule() {
        override val severity =
            when (prohibitedCipher.category) {
                io.lamco.netkit.model.topology.CipherCategory.WEP -> RuleSeverity.CRITICAL
                io.lamco.netkit.model.topology.CipherCategory.TKIP -> RuleSeverity.HIGH
                else -> RuleSeverity.MEDIUM
            }
        override val description = "Prohibit cipher: ${prohibitedCipher.displayName}"

        override fun generateViolationDescription(analysis: BssSecurityAnalysis): String =
            "Prohibited cipher ${prohibitedCipher.displayName} detected"
    }

    /**
     * Require specific cipher suite
     *
     * @property requiredCipher Cipher suite that must be present
     */
    data class RequireCipher(
        val requiredCipher: CipherSuite,
    ) : SecurityRule() {
        override val severity = RuleSeverity.MEDIUM
        override val description = "Require cipher: ${requiredCipher.displayName}"

        override fun generateViolationDescription(analysis: BssSecurityAnalysis): String =
            "Required cipher ${requiredCipher.displayName} not found"
    }

    /**
     * Require Protected Management Frames (PMF)
     */
    object RequirePmf : SecurityRule() {
        override val severity = RuleSeverity.HIGH
        override val description = "Require Protected Management Frames (PMF / 802.11w)"

        override fun generateViolationDescription(analysis: BssSecurityAnalysis): String =
            if (!analysis.pmfCapable) {
                "PMF not capable on this device"
            } else {
                "PMF not enabled"
            }
    }

    /**
     * Prohibit WPS (Wi-Fi Protected Setup)
     */
    object ProhibitWps : SecurityRule() {
        override val severity = RuleSeverity.HIGH
        override val description = "Prohibit WPS (Wi-Fi Protected Setup)"

        override fun generateViolationDescription(analysis: BssSecurityAnalysis): String {
            val risk = analysis.wpsRisk?.riskScore ?: 0.0
            return "WPS enabled with risk score: ${String.format("%.2f", risk)}"
        }
    }

    /**
     * Require minimum security level
     *
     * @property minimumLevel Minimum acceptable security level
     */
    data class RequireMinimumSecurityLevel(
        val minimumLevel: SecurityLevel,
    ) : SecurityRule() {
        override val severity = RuleSeverity.MEDIUM
        override val description = "Require minimum security level: $minimumLevel"

        override fun generateViolationDescription(analysis: BssSecurityAnalysis): String =
            "Security level ${analysis.securityScore.securityLevel} " +
                "does not meet minimum requirement of $minimumLevel"
    }

    /**
     * Prohibit specific threat level or higher
     *
     * @property prohibitedLevel Threat level that triggers violation
     */
    data class ProhibitThreatLevel(
        val prohibitedLevel: ThreatLevel,
    ) : SecurityRule() {
        override val severity =
            when (prohibitedLevel) {
                ThreatLevel.CRITICAL -> RuleSeverity.CRITICAL
                ThreatLevel.HIGH -> RuleSeverity.HIGH
                ThreatLevel.MEDIUM -> RuleSeverity.MEDIUM
                else -> RuleSeverity.LOW
            }
        override val description = "Prohibit threat level: $prohibitedLevel or higher"

        override fun generateViolationDescription(analysis: BssSecurityAnalysis): String =
            "Threat level ${analysis.threatLevel} exceeds maximum allowed ($prohibitedLevel)"
    }
}

/**
 * Rule severity levels
 */
enum class RuleSeverity {
    /** Informational only */
    INFO,

    /** Low severity - minor policy violation */
    LOW,

    /** Medium severity - notable policy violation */
    MEDIUM,

    /** High severity - significant policy violation */
    HIGH,

    /** Critical severity - severe policy violation */
    CRITICAL,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                INFO -> "Informational"
                LOW -> "Low severity"
                MEDIUM -> "Medium severity"
                HIGH -> "High severity"
                CRITICAL -> "Critical severity"
            }
}

/**
 * Rule violation detected during evaluation
 *
 * @property bssid BSSID of the violating AP
 * @property ssid SSID of the network
 * @property rule Security rule that was violated
 * @property severity Severity of the violation
 * @property description Human-readable violation description
 */
data class RuleViolation(
    val bssid: String,
    val ssid: String?,
    val rule: SecurityRule,
    val severity: RuleSeverity,
    val description: String,
) {
    /**
     * Summary of the violation
     */
    val summary: String
        get() = "$bssid${ssid?.let { " ($it)" } ?: ""}: $description"
}

/**
 * Rule evaluation result
 *
 * @property totalBssCount Total number of BSSs evaluated
 * @property rulesEvaluated Number of rules evaluated
 * @property violations List of rule violations detected
 * @property complianceScore Overall compliance score (0.0-1.0)
 */
data class RuleEvaluation(
    val totalBssCount: Int,
    val rulesEvaluated: Int,
    val violations: List<RuleViolation>,
    val complianceScore: Double,
) {
    init {
        require(totalBssCount > 0) { "Total BSS count must be positive" }
        require(rulesEvaluated > 0) { "Rules evaluated must be positive" }
        require(complianceScore in 0.0..1.0) { "Compliance score must be between 0.0 and 1.0" }
    }

    /**
     * Whether the evaluation is fully compliant (no violations)
     */
    val isCompliant: Boolean
        get() = violations.isEmpty()

    /**
     * Number of violations by severity
     */
    val violationsBySeverity: Map<RuleSeverity, Int>
        get() = violations.groupingBy { it.severity }.eachCount()

    /**
     * Compliance level based on score
     */
    val complianceLevel: ComplianceLevel
        get() =
            when {
                complianceScore >= 0.95 -> ComplianceLevel.FULL
                complianceScore >= 0.80 -> ComplianceLevel.HIGH
                complianceScore >= 0.60 -> ComplianceLevel.MODERATE
                complianceScore >= 0.40 -> ComplianceLevel.LOW
                else -> ComplianceLevel.NON_COMPLIANT
            }

    /**
     * Summary of the evaluation
     */
    val summary: String
        get() =
            buildString {
                append("$totalBssCount BSS(s) evaluated against $rulesEvaluated rule(s): ")
                if (isCompliant) {
                    append("COMPLIANT")
                } else {
                    append("${violations.size} violation(s) - $complianceLevel")
                }
            }
}

/**
 * Compliance levels for rule evaluation
 */
enum class ComplianceLevel {
    /** Fully compliant (95%+) */
    FULL,

    /** High compliance (80-94%) */
    HIGH,

    /** Moderate compliance (60-79%) */
    MODERATE,

    /** Low compliance (40-59%) */
    LOW,

    /** Non-compliant (<40%) */
    NON_COMPLIANT,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                FULL -> "Fully compliant"
                HIGH -> "High compliance"
                MODERATE -> "Moderate compliance"
                LOW -> "Low compliance"
                NON_COMPLIANT -> "Non-compliant"
            }
}
