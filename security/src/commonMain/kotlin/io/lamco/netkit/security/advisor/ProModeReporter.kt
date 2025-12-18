package io.lamco.netkit.security.advisor

import io.lamco.netkit.security.analyzer.BssSecurityAnalysis
import io.lamco.netkit.security.analyzer.NetworkSecurityAnalysis
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Professional security reporting engine
 *
 * The ProModeReporter generates comprehensive security assessment reports in
 * multiple formats, suitable for both technical teams and executive audiences.
 *
 * Report Types:
 * - Executive Summary: High-level overview for management
 * - Technical Report: Detailed technical analysis
 * - Compliance Report: Policy compliance status
 * - Action Plan: Prioritized remediation steps
 *
 * Output Formats:
 * - Plain text (console-friendly)
 * - Markdown (documentation-ready)
 * - Structured data (for further processing)
 *
 * Example Usage:
 * ```kotlin
 * val reporter = ProModeReporter()
 * val report = reporter.generateComprehensiveReport(
 *     networkAnalysis = networkAnalysis,
 *     securityAdvice = securityAdvice,
 *     riskAssessment = riskAssessment
 * )
 * println(report.toMarkdown())
 * ```
 *
 * @see ComprehensiveReport
 * @see ReportFormat
 */
class ProModeReporter {
    /**
     * Generate comprehensive security report
     *
     * @param networkAnalysis Network security analysis
     * @param securityAdvice Security advisor recommendations
     * @param riskAssessment Risk prioritization results
     * @param configurationAdvice Optional configuration advice
     * @param ruleEvaluation Optional rule evaluation results
     * @return Comprehensive security report
     */
    fun generateComprehensiveReport(
        networkAnalysis: NetworkSecurityAnalysis,
        securityAdvice: SecurityAdvice? = null,
        riskAssessment: RiskAssessment? = null,
        configurationAdvice: ConfigurationAdvice? = null,
        ruleEvaluation: RuleEvaluation? = null,
    ): ComprehensiveReport =
        ComprehensiveReport(
            timestamp = LocalDateTime.now(),
            networkAnalysis = networkAnalysis,
            securityAdvice = securityAdvice,
            riskAssessment = riskAssessment,
            configurationAdvice = configurationAdvice,
            ruleEvaluation = ruleEvaluation,
        )

    /**
     * Generate executive summary
     *
     * @param networkAnalysis Network security analysis
     * @param riskAssessment Risk assessment
     * @return Executive summary text
     */
    fun generateExecutiveSummary(
        networkAnalysis: NetworkSecurityAnalysis,
        riskAssessment: RiskAssessment? = null,
    ): String =
        buildString {
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("  EXECUTIVE SECURITY SUMMARY")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()

            appendLine("Assessment Date: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            appendLine()

            // Network Overview
            appendLine("NETWORK OVERVIEW")
            appendLine("─────────────────")
            appendLine("Access Points Analyzed: ${networkAnalysis.totalBssCount}")
            appendLine("Average Security Score: ${String.format("%.1f%%", networkAnalysis.averageSecurityScore * 100)}")
            appendLine("Overall Threat Level: ${networkAnalysis.overallThreatLevel}")
            appendLine("Compliance Status: ${networkAnalysis.complianceLevel}")
            appendLine()

            // Risk Summary
            if (riskAssessment != null) {
                appendLine("RISK SUMMARY")
                appendLine("─────────────────")
                appendLine("Overall Risk Level: ${riskAssessment.overallRiskLevel}")
                appendLine("Critical Risks: ${riskAssessment.criticalRisks.size}")
                appendLine("Immediate Actions Required: ${riskAssessment.immediateActions.size}")
                appendLine("Quick Wins Available: ${riskAssessment.quickWins.size}")
                appendLine()
            }

            // Key Findings
            appendLine("KEY FINDINGS")
            appendLine("─────────────────")

            val excellentCount =
                networkAnalysis.bssAnalyses.count {
                    it.securityScore.securityLevel.name == "EXCELLENT"
                }
            val insecureCount =
                networkAnalysis.bssAnalyses.count {
                    it.securityScore.securityLevel.name == "INSECURE"
                }

            if (excellentCount == networkAnalysis.totalBssCount) {
                appendLine("✓ All access points have excellent security configuration")
            } else if (insecureCount > 0) {
                appendLine("✗ $insecureCount of ${networkAnalysis.totalBssCount} access points have insecure configuration")
            } else {
                appendLine("△ Security configuration varies across access points")
            }

            appendLine()
            appendLine("RECOMMENDATION")
            appendLine("─────────────────")
            when (networkAnalysis.overallThreatLevel) {
                io.lamco.netkit.security.analyzer.ThreatLevel.CRITICAL ->
                    appendLine("IMMEDIATE ACTION REQUIRED: Critical security threats detected.")
                io.lamco.netkit.security.analyzer.ThreatLevel.HIGH ->
                    appendLine("HIGH PRIORITY: Significant security issues require prompt attention.")
                io.lamco.netkit.security.analyzer.ThreatLevel.MEDIUM ->
                    appendLine("MODERATE PRIORITY: Address identified security concerns.")
                io.lamco.netkit.security.analyzer.ThreatLevel.LOW ->
                    appendLine("LOW PRIORITY: Minor improvements recommended.")
                io.lamco.netkit.security.analyzer.ThreatLevel.NONE ->
                    appendLine("GOOD STANDING: Maintain current security posture.")
            }

            appendLine()
            appendLine("═══════════════════════════════════════════════════════")
        }

    /**
     * Generate technical report
     *
     * @param networkAnalysis Network security analysis
     * @param includeDetails Whether to include per-AP details
     * @return Technical report text
     */
    fun generateTechnicalReport(
        networkAnalysis: NetworkSecurityAnalysis,
        includeDetails: Boolean = true,
    ): String =
        buildString {
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("  TECHNICAL SECURITY ANALYSIS REPORT")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()

            appendLine("Generated: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine()

            // Network-Wide Analysis
            appendLine("NETWORK-WIDE ANALYSIS")
            appendLine("─────────────────────────────────────")
            appendLine("Total Access Points: ${networkAnalysis.totalBssCount}")
            appendLine(
                "Average Security Score: ${String.format("%.2f", networkAnalysis.averageSecurityScore)} (${String.format(
                    "%.1f%%",
                    networkAnalysis.averageSecurityScore * 100,
                )})",
            )
            appendLine("Minimum Security Percentage: ${String.format("%.1f%%", networkAnalysis.minimumSecurityPercentage)}")
            appendLine("Overall Threat Level: ${networkAnalysis.overallThreatLevel}")
            appendLine("Compliance Level: ${networkAnalysis.complianceLevel}")
            appendLine()

            // Security Distribution
            appendLine("SECURITY LEVEL DISTRIBUTION")
            appendLine("─────────────────────────────────────")
            val distribution =
                networkAnalysis.bssAnalyses
                    .groupingBy {
                        it.securityScore.securityLevel
                    }.eachCount()

            distribution.forEach { (level, count) ->
                val percentage = (count.toDouble() / networkAnalysis.totalBssCount) * 100
                appendLine("  ${level.name.padEnd(12)} : $count (${String.format("%.1f%%", percentage)})")
            }
            appendLine()

            // Per-AP Details
            if (includeDetails) {
                appendLine("PER-ACCESS POINT ANALYSIS")
                appendLine("─────────────────────────────────────")
                networkAnalysis.bssAnalyses.forEachIndexed { index, bss ->
                    appendLine()
                    appendLine("AP ${index + 1}/${networkAnalysis.totalBssCount}")
                    appendLine("  BSSID: ${bss.bssid}")
                    appendLine("  SSID: ${bss.ssid ?: "(hidden)"}")
                    appendLine("  Authentication: ${bss.securityScore.authType.displayName}")
                    appendLine("  Security Level: ${bss.securityScore.securityLevel}")
                    appendLine("  Security Score: ${String.format("%.2f", bss.securityScore.overallSecurityScore)}")
                    appendLine("  Threat Level: ${bss.threatLevel}")
                    appendLine("  PMF Enabled: ${if (bss.pmfEnabled) "Yes" else "No"}")
                    appendLine("  Ciphers: ${bss.securityScore.cipherSet.joinToString(", ") { it.displayName }}")

                    if (bss.criticalIssues.isNotEmpty()) {
                        appendLine("  Critical Issues: ${bss.criticalIssues.size}")
                        bss.criticalIssues.forEach { issue ->
                            appendLine("    - [${issue.severity}] ${issue.description}")
                        }
                    }

                    val wpsRisk = bss.wpsRisk
                    if (wpsRisk != null && wpsRisk.riskScore > 0.0) {
                        appendLine("  WPS Risk: ${wpsRisk.riskLevel} (${String.format("%.2f", wpsRisk.riskScore)})")
                    }
                }
                appendLine()
            }

            appendLine("═══════════════════════════════════════════════════════")
        }

    /**
     * Generate action plan report
     *
     * @param securityAdvice Security advice with action items
     * @return Action plan text
     */
    fun generateActionPlan(securityAdvice: SecurityAdvice): String =
        buildString {
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("  SECURITY IMPROVEMENT ACTION PLAN")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()

            appendLine("Total Actions: ${securityAdvice.prioritizedActions.size}")
            appendLine("Estimated Improvement: ${String.format("%.1f%%", securityAdvice.improvementDelta * 100)}")
            appendLine()

            // Group by priority
            val byPriority = securityAdvice.actionsByPriority

            listOf(ActionPriority.CRITICAL, ActionPriority.HIGH, ActionPriority.MEDIUM, ActionPriority.LOW).forEach { priority ->
                val actions = byPriority[priority]
                if (actions != null && actions.isNotEmpty()) {
                    appendLine()
                    appendLine("${priority.name} PRIORITY (${actions.size} actions)")
                    appendLine("─────────────────────────────────────")

                    actions.forEachIndexed { index, action ->
                        appendLine()
                        appendLine("${index + 1}. ${action.title}")
                        appendLine("   BSSID: ${action.bssid}")
                        appendLine("   SSID: ${action.ssid ?: "(unknown)"}")
                        appendLine("   Category: ${action.category.description}")
                        appendLine("   Effort: ${action.effort.description}")
                        appendLine("   Issue: ${action.description}")
                        appendLine("   Action: ${action.recommendation}")
                    }
                }
            }

            appendLine()
            appendLine("═══════════════════════════════════════════════════════")
        }

    /**
     * Generate compliance report
     *
     * @param ruleEvaluation Rule evaluation results
     * @return Compliance report text
     */
    fun generateComplianceReport(ruleEvaluation: RuleEvaluation): String =
        buildString {
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("  SECURITY POLICY COMPLIANCE REPORT")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()

            appendLine("Evaluation Date: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            appendLine()

            appendLine("COMPLIANCE SUMMARY")
            appendLine("─────────────────────────────────────")
            appendLine("Access Points Evaluated: ${ruleEvaluation.totalBssCount}")
            appendLine("Rules Evaluated: ${ruleEvaluation.rulesEvaluated}")
            appendLine("Compliance Score: ${String.format("%.1f%%", ruleEvaluation.complianceScore * 100)}")
            appendLine("Compliance Level: ${ruleEvaluation.complianceLevel}")
            appendLine("Violations Found: ${ruleEvaluation.violations.size}")
            appendLine()

            if (ruleEvaluation.isCompliant) {
                appendLine("✓ FULLY COMPLIANT")
                appendLine("All access points meet security policy requirements.")
            } else {
                appendLine("VIOLATIONS BY SEVERITY")
                appendLine("─────────────────────────────────────")

                ruleEvaluation.violationsBySeverity.forEach { (severity, count) ->
                    appendLine("  ${severity.name.padEnd(12)} : $count")
                }

                appendLine()
                appendLine("VIOLATION DETAILS")
                appendLine("─────────────────────────────────────")

                ruleEvaluation.violations.groupBy { it.severity }.forEach { (severity, violations) ->
                    if (violations.isNotEmpty()) {
                        appendLine()
                        appendLine("${severity.name}:")
                        violations.forEach { violation ->
                            appendLine("  • ${violation.bssid} (${violation.ssid ?: "unknown"})")
                            appendLine("    ${violation.description}")
                        }
                    }
                }
            }

            appendLine()
            appendLine("═══════════════════════════════════════════════════════")
        }
}

/**
 * Comprehensive security assessment report
 *
 * @property timestamp Report generation timestamp
 * @property networkAnalysis Network security analysis
 * @property securityAdvice Security advisor recommendations
 * @property riskAssessment Risk prioritization results
 * @property configurationAdvice Configuration optimization advice
 * @property ruleEvaluation Rule evaluation results
 */
data class ComprehensiveReport(
    val timestamp: LocalDateTime,
    val networkAnalysis: NetworkSecurityAnalysis,
    val securityAdvice: SecurityAdvice?,
    val riskAssessment: RiskAssessment?,
    val configurationAdvice: ConfigurationAdvice?,
    val ruleEvaluation: RuleEvaluation?,
) {
    /**
     * Convert report to plain text format
     */
    fun toPlainText(): String {
        val reporter = ProModeReporter()
        return buildString {
            appendLine(reporter.generateExecutiveSummary(networkAnalysis, riskAssessment))
            appendLine()
            appendLine(reporter.generateTechnicalReport(networkAnalysis, includeDetails = true))

            if (securityAdvice != null) {
                appendLine()
                appendLine(reporter.generateActionPlan(securityAdvice))
            }

            if (ruleEvaluation != null) {
                appendLine()
                appendLine(reporter.generateComplianceReport(ruleEvaluation))
            }
        }
    }

    /**
     * Convert report to markdown format
     */
    fun toMarkdown(): String =
        buildString {
            appendLine("# WiFi Security Assessment Report")
            appendLine()
            appendLine("**Generated:** ${timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine()

            // Executive Summary
            appendLine("## Executive Summary")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|--------|-------|")
            appendLine("| Access Points | ${networkAnalysis.totalBssCount} |")
            appendLine("| Average Security Score | ${String.format("%.1f%%", networkAnalysis.averageSecurityScore * 100)} |")
            appendLine("| Overall Threat Level | ${networkAnalysis.overallThreatLevel} |")
            appendLine("| Compliance Level | ${networkAnalysis.complianceLevel} |")

            if (riskAssessment != null) {
                appendLine("| Overall Risk Level | ${riskAssessment.overallRiskLevel} |")
                appendLine("| Critical Risks | ${riskAssessment.criticalRisks.size} |")
            }
            appendLine()

            // Security Distribution
            appendLine("## Security Level Distribution")
            appendLine()
            appendLine("| Security Level | Count | Percentage |")
            appendLine("|----------------|-------|------------|")

            networkAnalysis.bssAnalyses.groupingBy { it.securityScore.securityLevel }.eachCount().forEach { (level, count) ->
                val percentage = (count.toDouble() / networkAnalysis.totalBssCount) * 100
                appendLine("| $level | $count | ${String.format("%.1f%%", percentage)} |")
            }
            appendLine()

            // Action Plan
            if (securityAdvice != null && securityAdvice.prioritizedActions.isNotEmpty()) {
                appendLine("## Recommended Actions")
                appendLine()

                securityAdvice.actionsByPriority.forEach { (priority, actions) ->
                    if (actions.isNotEmpty()) {
                        appendLine("### $priority Priority")
                        appendLine()
                        actions.forEach { action ->
                            appendLine("- **${action.title}** (${action.category.description})")
                            appendLine("  - SSID: ${action.ssid ?: "unknown"}")
                            appendLine("  - Issue: ${action.description}")
                            appendLine("  - Action: ${action.recommendation}")
                            appendLine("  - Effort: ${action.effort.description}")
                            appendLine()
                        }
                    }
                }
            }

            // Compliance
            if (ruleEvaluation != null) {
                appendLine("## Compliance Status")
                appendLine()
                appendLine("- **Compliance Score:** ${String.format("%.1f%%", ruleEvaluation.complianceScore * 100)}")
                appendLine("- **Compliance Level:** ${ruleEvaluation.complianceLevel}")
                appendLine("- **Violations:** ${ruleEvaluation.violations.size}")
                appendLine()

                if (!ruleEvaluation.isCompliant) {
                    appendLine("### Violations")
                    appendLine()
                    ruleEvaluation.violations.take(10).forEach { violation ->
                        appendLine("- **[${violation.severity}]** ${violation.bssid} (${violation.ssid ?: "unknown"})")
                        appendLine("  - ${violation.description}")
                        appendLine()
                    }
                }
            }
        }

    /**
     * Get structured data for programmatic access
     */
    fun toStructuredData(): Map<String, Any> =
        mapOf(
            "timestamp" to timestamp.toString(),
            "network" to
                mapOf(
                    "totalBssCount" to networkAnalysis.totalBssCount,
                    "averageSecurityScore" to networkAnalysis.averageSecurityScore,
                    "overallThreatLevel" to networkAnalysis.overallThreatLevel.name,
                    "complianceLevel" to networkAnalysis.complianceLevel.name,
                ),
            "risks" to (
                riskAssessment?.let {
                    mapOf(
                        "overallRiskLevel" to it.overallRiskLevel.name,
                        "totalRiskScore" to it.totalRiskScore,
                        "criticalRisksCount" to it.criticalRisks.size,
                        "immediateActionsCount" to it.immediateActions.size,
                    )
                } ?: emptyMap<String, Any>()
            ),
            "actions" to (
                securityAdvice?.prioritizedActions?.map { action ->
                    mapOf(
                        "title" to action.title,
                        "priority" to action.priority.name,
                        "category" to action.category.name,
                        "effort" to action.effort.name,
                        "description" to action.description,
                    )
                } ?: emptyList<Map<String, Any>>()
            ),
            "compliance" to (
                ruleEvaluation?.let {
                    mapOf(
                        "score" to it.complianceScore,
                        "level" to it.complianceLevel.name,
                        "isCompliant" to it.isCompliant,
                        "violationsCount" to it.violations.size,
                    )
                } ?: emptyMap<String, Any>()
            ),
        )
}

/**
 * Report output formats
 */
enum class ReportFormat {
    /** Plain text format (console-friendly) */
    PLAIN_TEXT,

    /** Markdown format (documentation-ready) */
    MARKDOWN,

    /** Structured data (for programmatic access) */
    STRUCTURED_DATA,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                PLAIN_TEXT -> "Plain Text"
                MARKDOWN -> "Markdown"
                STRUCTURED_DATA -> "Structured Data (Map)"
            }
}
