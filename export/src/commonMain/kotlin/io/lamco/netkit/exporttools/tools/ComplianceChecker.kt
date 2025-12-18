package io.lamco.netkit.exporttools.tools

import io.lamco.netkit.exporttools.model.ValidationCategory
import io.lamco.netkit.exporttools.model.ValidationIssue
import io.lamco.netkit.exporttools.model.ValidationResult

/**
 * Compliance checker for WiFi security standards.
 *
 * Validates network configurations against compliance frameworks:
 * - PCI-DSS (Payment Card Industry Data Security Standard)
 * - NIST SP 800-97 (Wireless Network Security)
 * - NIST SP 800-53 (Security and Privacy Controls)
 *
 * ## Compliance Frameworks
 *
 * ### PCI-DSS v4.0
 * - Requirement 4.2: Strong cryptography for wireless networks
 * - Requirement 2.1: Change vendor defaults
 * - Requirement 8.2: Strong authentication
 *
 * ### NIST SP 800-97
 * - WPA3 or WPA2-Enterprise recommended
 * - Rogue AP detection
 * - Secure configuration
 *
 * @since 1.0.0
 */
class ComplianceChecker {
    /**
     * Checks PCI-DSS compliance for network.
     *
     * @param networkData Network configuration data
     * @return Compliance result with findings
     */
    fun checkPciDss(networkData: Map<String, Any>): ComplianceResult {
        val findings = mutableListOf<ComplianceFinding>()

        val encryption = networkData["encryption"] as? String
        when (encryption) {
            "WPA3", "WPA2-Enterprise" -> {
                findings.add(
                    ComplianceFinding(
                        requirement = "PCI-DSS 4.2",
                        status = ComplianceStatus.PASS,
                        description = "Strong cryptography in use",
                        detail = "Using $encryption",
                    ),
                )
            }
            "WPA2" -> {
                findings.add(
                    ComplianceFinding(
                        requirement = "PCI-DSS 4.2",
                        status = ComplianceStatus.WARNING,
                        description = "WPA2-Personal not recommended for PCI environments",
                        detail = "Consider WPA3 or WPA2-Enterprise",
                        remediation = "Upgrade to WPA3 or implement 802.1X authentication",
                    ),
                )
            }
            else -> {
                findings.add(
                    ComplianceFinding(
                        requirement = "PCI-DSS 4.2",
                        status = ComplianceStatus.FAIL,
                        description = "Weak or no encryption",
                        detail = "Current: ${encryption ?: "None"}",
                        remediation = "Implement WPA3 or WPA2-Enterprise immediately",
                    ),
                )
            }
        }

        val hasDefaultCreds = networkData["hasDefaultCredentials"] as? Boolean ?: false
        if (hasDefaultCreds) {
            findings.add(
                ComplianceFinding(
                    requirement = "PCI-DSS 2.1",
                    status = ComplianceStatus.FAIL,
                    description = "Default credentials in use",
                    detail = "Vendor defaults detected",
                    remediation = "Change all default passwords and SSIDs",
                ),
            )
        } else {
            findings.add(
                ComplianceFinding(
                    requirement = "PCI-DSS 2.1",
                    status = ComplianceStatus.PASS,
                    description = "No default credentials detected",
                ),
            )
        }

        val overallStatus =
            when {
                findings.any { it.status == ComplianceStatus.FAIL } -> ComplianceStatus.FAIL
                findings.any { it.status == ComplianceStatus.WARNING } -> ComplianceStatus.WARNING
                else -> ComplianceStatus.PASS
            }

        return ComplianceResult(
            framework = "PCI-DSS v4.0",
            overallStatus = overallStatus,
            findings = findings,
            checkedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Checks NIST SP 800-97 compliance for network.
     *
     * @param networkData Network configuration data
     * @return Compliance result with findings
     */
    fun checkNist80097(networkData: Map<String, Any>): ComplianceResult {
        val findings = mutableListOf<ComplianceFinding>()

        val encryption = networkData["encryption"] as? String
        when (encryption) {
            "WPA3" -> {
                findings.add(
                    ComplianceFinding(
                        requirement = "NIST 800-97 3.1",
                        status = ComplianceStatus.PASS,
                        description = "WPA3 encryption meets NIST recommendations",
                    ),
                )
            }
            "WPA2-Enterprise", "WPA2" -> {
                findings.add(
                    ComplianceFinding(
                        requirement = "NIST 800-97 3.1",
                        status = ComplianceStatus.WARNING,
                        description = "WPA2 acceptable but WPA3 recommended",
                        remediation = "Plan migration to WPA3",
                    ),
                )
            }
            else -> {
                findings.add(
                    ComplianceFinding(
                        requirement = "NIST 800-97 3.1",
                        status = ComplianceStatus.FAIL,
                        description = "Encryption does not meet NIST standards",
                        detail = "Current: ${encryption ?: "None"}",
                        remediation = "Implement WPA3 immediately",
                    ),
                )
            }
        }

        val hasRogueDetection = networkData["rogueApDetection"] as? Boolean ?: false
        findings.add(
            ComplianceFinding(
                requirement = "NIST 800-97 4.2",
                status = if (hasRogueDetection) ComplianceStatus.PASS else ComplianceStatus.WARNING,
                description =
                    if (hasRogueDetection) {
                        "Rogue AP detection enabled"
                    } else {
                        "Rogue AP detection not configured"
                    },
                remediation = if (!hasRogueDetection) "Enable rogue AP detection" else null,
            ),
        )

        val overallStatus =
            when {
                findings.any { it.status == ComplianceStatus.FAIL } -> ComplianceStatus.FAIL
                findings.any { it.status == ComplianceStatus.WARNING } -> ComplianceStatus.WARNING
                else -> ComplianceStatus.PASS
            }

        return ComplianceResult(
            framework = "NIST SP 800-97",
            overallStatus = overallStatus,
            findings = findings,
            checkedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Generates compliance validation result from compliance check.
     *
     * @param result Compliance result
     * @return ValidationResult for export validation
     */
    fun toValidationResult(result: ComplianceResult): ValidationResult {
        val issues =
            result.findings.map { finding ->
                val severity =
                    when (finding.status) {
                        ComplianceStatus.FAIL -> ValidationIssue.Severity.ERROR
                        ComplianceStatus.WARNING -> ValidationIssue.Severity.WARNING
                        ComplianceStatus.PASS -> ValidationIssue.Severity.INFO
                    }

                ValidationIssue(
                    severity = severity,
                    category = ValidationCategory.SECURITY,
                    message = "${finding.requirement}: ${finding.description}",
                    field = finding.requirement,
                    value = finding.detail,
                    suggestion = finding.remediation,
                )
            }

        val errors = issues.filter { it.severity == ValidationIssue.Severity.ERROR }
        val warnings = issues.filter { it.severity == ValidationIssue.Severity.WARNING }
        val infos = issues.filter { it.severity == ValidationIssue.Severity.INFO }

        return if (errors.isNotEmpty()) {
            ValidationResult.failure(errors, warnings, infos)
        } else {
            ValidationResult.success(warnings, infos)
        }
    }
}

/**
 * Compliance check result.
 *
 * @property framework Compliance framework name
 * @property overallStatus Overall compliance status
 * @property findings List of individual findings
 * @property checkedAt Timestamp when check was performed
 *
 * @since 1.0.0
 */
data class ComplianceResult(
    val framework: String,
    val overallStatus: ComplianceStatus,
    val findings: List<ComplianceFinding>,
    val checkedAt: Long,
) {
    /**
     * Returns summary of compliance check.
     */
    fun summary(): String {
        val passCount = findings.count { it.status == ComplianceStatus.PASS }
        val warnCount = findings.count { it.status == ComplianceStatus.WARNING }
        val failCount = findings.count { it.status == ComplianceStatus.FAIL }

        return buildString {
            appendLine("$framework Compliance Check")
            appendLine("Overall Status: $overallStatus")
            appendLine("PASS: $passCount | WARNING: $warnCount | FAIL: $failCount")
            if (failCount > 0) {
                appendLine("\nCritical Findings:")
                findings.filter { it.status == ComplianceStatus.FAIL }.forEach {
                    appendLine("  - ${it.requirement}: ${it.description}")
                }
            }
        }
    }
}

/**
 * Individual compliance finding.
 *
 * @property requirement Requirement identifier
 * @property status Compliance status for this requirement
 * @property description Finding description
 * @property detail Optional detailed information
 * @property remediation Optional remediation steps
 *
 * @since 1.0.0
 */
data class ComplianceFinding(
    val requirement: String,
    val status: ComplianceStatus,
    val description: String,
    val detail: String? = null,
    val remediation: String? = null,
)

/**
 * Compliance status enum.
 *
 * @since 1.0.0
 */
enum class ComplianceStatus {
    /** Requirement met */
    PASS,

    /** Recommendation not followed */
    WARNING,

    /** Requirement not met */
    FAIL,
}
