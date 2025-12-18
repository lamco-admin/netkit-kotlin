package io.lamco.netkit.security.analyzer

import io.lamco.netkit.model.topology.AuthType
import io.lamco.netkit.model.topology.CipherSuite

/**
 * Protected Management Frames (PMF) analyzer
 *
 * Analyzes IEEE 802.11w Protected Management Frames configuration to identify:
 * - Missing PMF on networks that should have it
 * - Weak management frame ciphers (BIP)
 * - PMF policy compliance
 * - Management frame attack vulnerabilities
 *
 * PMF (IEEE 802.11w) Background:
 * - Protects management frames from spoofing/forgery
 * - Prevents deauthentication attacks
 * - Prevents evil twin AP attacks
 * - MANDATORY for WPA3 and WiFi 6E (6 GHz)
 * - RECOMMENDED for WPA2
 *
 * Attack Vectors PMF Prevents:
 * - Deauthentication floods
 * - Disassociation attacks
 * - Beacon frame injection
 * - Probe response spoofing
 * - Action frame forgery
 *
 * References:
 * - IEEE 802.11w-2009 (Protected Management Frames)
 * - IEEE 802.11-2020 Section 12.6 (Management frame protection)
 * - WPA3 Specification (PMF mandatory)
 * - WiFi 6E requirements (6 GHz requires PMF)
 *
 * @see CipherSuite (BIP variants)
 */
class PmfAnalyzer {
    /**
     * Analyze PMF configuration
     *
     * @param authType Authentication type
     * @param pmfRequired Whether PMF is mandatory
     * @param pmfCapable Whether PMF is supported (optional)
     * @param managementCipher BIP cipher for management frames (null if no PMF)
     * @return PMF analysis result
     */
    fun analyze(
        authType: AuthType,
        pmfRequired: Boolean,
        pmfCapable: Boolean,
        managementCipher: CipherSuite?,
    ): PmfAnalysisResult {
        // Check if PMF should be mandatory for this auth type
        val shouldBeMandatory = isPmfMandatoryFor(authType)

        // Identify PMF issues
        val issues = identifyPmfIssues(authType, pmfRequired, pmfCapable, managementCipher, shouldBeMandatory)

        // Determine PMF status
        val status = determinePmfStatus(pmfRequired, pmfCapable)

        // Calculate protection level
        val protectionLevel = calculateProtectionLevel(pmfRequired, pmfCapable, managementCipher)

        // Check vulnerability to attacks
        val vulnerabilities = identifyVulnerabilities(pmfRequired, pmfCapable)

        return PmfAnalysisResult(
            authType = authType,
            status = status,
            protectionLevel = protectionLevel,
            managementCipher = managementCipher,
            issues = issues,
            vulnerabilities = vulnerabilities,
            shouldBeMandatory = shouldBeMandatory,
        )
    }

    /**
     * Check if PMF is mandatory for the given authentication type
     *
     * @param authType Authentication type
     * @return True if PMF is mandatory
     */
    private fun isPmfMandatoryFor(authType: AuthType): Boolean =
        when (authType) {
            // WPA3 always requires PMF
            AuthType.WPA3_SAE,
            AuthType.WPA3_SAE_PK,
            AuthType.WPA3_ENTERPRISE,
            AuthType.WPA3_ENTERPRISE_192,
            -> true

            // OWE requires PMF
            AuthType.OWE -> true

            // WPA2 and others: optional but recommended
            else -> false
        }

    /**
     * Identify PMF-related security issues
     *
     * @param authType Authentication type
     * @param pmfRequired Whether PMF is mandatory
     * @param pmfCapable Whether PMF is supported
     * @param managementCipher BIP cipher
     * @param shouldBeMandatory Whether PMF should be mandatory
     * @return List of PMF issues
     */
    private fun identifyPmfIssues(
        authType: AuthType,
        pmfRequired: Boolean,
        pmfCapable: Boolean,
        managementCipher: CipherSuite?,
        shouldBeMandatory: Boolean,
    ): List<PmfIssue> {
        val issues = mutableListOf<PmfIssue>()

        // CRITICAL: PMF missing on WPA3 or OWE
        if (shouldBeMandatory && !pmfRequired) {
            issues.add(
                PmfIssue(
                    severity = PmfIssueSeverity.CRITICAL,
                    description = "PMF is MANDATORY for ${authType.displayName} but not enforced",
                    recommendation = "Enable PMF (802.11w) in 'Required' mode immediately",
                    details =
                        "WPA3 and OWE require PMF to be mandatory. This is a critical " +
                            "misconfiguration that violates the specification.",
                    attacksEnabled =
                        listOf(
                            "Deauthentication attacks",
                            "Evil twin APs",
                            "Management frame injection",
                        ),
                ),
            )
        }

        // HIGH: PMF not enabled on WPA2
        if (!shouldBeMandatory && authType != AuthType.OPEN && !pmfRequired && !pmfCapable) {
            issues.add(
                PmfIssue(
                    severity = PmfIssueSeverity.HIGH,
                    description = "PMF not enabled (recommended for ${authType.displayName})",
                    recommendation = "Enable PMF in 'Capable' or 'Required' mode",
                    details =
                        "While not mandatory for WPA2, PMF significantly improves security " +
                            "by preventing management frame attacks.",
                    attacksEnabled =
                        listOf(
                            "Deauthentication floods",
                            "Forced disconnections",
                            "Evil twin attacks",
                        ),
                ),
            )
        }

        // MEDIUM: PMF capable but not required
        if (pmfCapable && !pmfRequired && authType != AuthType.OPEN) {
            issues.add(
                PmfIssue(
                    severity = PmfIssueSeverity.MEDIUM,
                    description = "PMF optional (capable) - should be required",
                    recommendation = "Change PMF mode from 'Capable' to 'Required'",
                    details =
                        "PMF in 'Capable' mode allows legacy clients without PMF to connect, " +
                            "leaving those clients vulnerable to management frame attacks.",
                    attacksEnabled = listOf("Attacks on legacy clients without PMF"),
                ),
            )
        }

        // MEDIUM: Weak management cipher
        if (pmfRequired && managementCipher != null && managementCipher.strength < 90) {
            issues.add(
                PmfIssue(
                    severity = PmfIssueSeverity.MEDIUM,
                    description = "Weak management frame cipher: ${managementCipher.displayName}",
                    recommendation = "Use BIP-GMAC-128 (minimum) or BIP-GMAC-256 (best)",
                    details =
                        "Management frame cipher should be strong. For Suite-B 192-bit, " +
                            "BIP-GMAC-256 or BIP-CMAC-256 is required.",
                    attacksEnabled = listOf("Potential management cipher weaknesses"),
                ),
            )
        }

        // HIGH: PMF required but no BIP cipher
        if (pmfRequired && managementCipher == null) {
            issues.add(
                PmfIssue(
                    severity = PmfIssueSeverity.HIGH,
                    description = "PMF required but no BIP cipher configured",
                    recommendation = "Configure BIP-CMAC-128 or BIP-GMAC-256",
                    details =
                        "PMF requires a BIP (Broadcast/Multicast Integrity Protocol) cipher " +
                            "to protect group-addressed management frames.",
                    attacksEnabled = listOf("Group management frame attacks"),
                ),
            )
        }

        return issues
    }

    /**
     * Determine PMF status
     *
     * @param pmfRequired Whether PMF is mandatory
     * @param pmfCapable Whether PMF is supported
     * @return PMF status
     */
    private fun determinePmfStatus(
        pmfRequired: Boolean,
        pmfCapable: Boolean,
    ): PmfStatus =
        when {
            pmfRequired -> PmfStatus.REQUIRED
            pmfCapable -> PmfStatus.CAPABLE
            else -> PmfStatus.DISABLED
        }

    /**
     * Calculate protection level from PMF configuration
     *
     * @param pmfRequired Whether PMF is mandatory
     * @param pmfCapable Whether PMF is supported
     * @param managementCipher BIP cipher
     * @return Protection level (0.0-1.0)
     */
    private fun calculateProtectionLevel(
        pmfRequired: Boolean,
        pmfCapable: Boolean,
        managementCipher: CipherSuite?,
    ): Double =
        when {
            // PMF required with strong cipher
            pmfRequired && managementCipher != null && managementCipher.strength >= 90 -> 1.0

            // PMF required with adequate cipher
            pmfRequired && managementCipher != null && managementCipher.strength >= 80 -> 0.9

            // PMF required but weak/unknown cipher
            pmfRequired -> 0.7

            // PMF capable with good cipher
            pmfCapable && managementCipher != null && managementCipher.strength >= 80 -> 0.6

            // PMF capable but not enforced
            pmfCapable -> 0.4

            // No PMF
            else -> 0.0
        }

    /**
     * Identify vulnerabilities when PMF is not enabled
     *
     * @param pmfRequired Whether PMF is mandatory
     * @param pmfCapable Whether PMF is supported
     * @return List of attack vulnerabilities
     */
    private fun identifyVulnerabilities(
        pmfRequired: Boolean,
        pmfCapable: Boolean,
    ): List<ManagementFrameVulnerability> {
        if (pmfRequired) return emptyList() // PMF required = protected

        val vulnerabilities = mutableListOf<ManagementFrameVulnerability>()

        // Deauthentication attacks
        vulnerabilities.add(
            ManagementFrameVulnerability(
                attackType = "Deauthentication Flood",
                description = "Attacker can force client disconnections with spoofed deauth frames",
                severity = if (!pmfCapable) VulnerabilitySeverity.HIGH else VulnerabilitySeverity.MEDIUM,
                toolsUsed = listOf("aireplay-ng", "mdk3", "mdk4"),
            ),
        )

        // Evil twin attacks
        vulnerabilities.add(
            ManagementFrameVulnerability(
                attackType = "Evil Twin AP",
                description = "Attacker can impersonate the AP with forged beacon frames",
                severity = if (!pmfCapable) VulnerabilitySeverity.HIGH else VulnerabilitySeverity.MEDIUM,
                toolsUsed = listOf("airbase-ng", "hostapd", "wifipineapple"),
            ),
        )

        // Disassociation attacks
        vulnerabilities.add(
            ManagementFrameVulnerability(
                attackType = "Disassociation Attack",
                description = "Attacker can disassociate clients from the network",
                severity = if (!pmfCapable) VulnerabilitySeverity.MEDIUM else VulnerabilitySeverity.LOW,
                toolsUsed = listOf("aireplay-ng", "scapy"),
            ),
        )

        return vulnerabilities
    }
}

/**
 * PMF analysis result
 *
 * @property authType Authentication type
 * @property status PMF status (required, capable, disabled)
 * @property protectionLevel Protection level (0.0-1.0)
 * @property managementCipher BIP cipher for management frames
 * @property issues PMF-related security issues
 * @property vulnerabilities Attack vulnerabilities when PMF disabled
 * @property shouldBeMandatory Whether PMF should be mandatory for this auth type
 */
data class PmfAnalysisResult(
    val authType: AuthType,
    val status: PmfStatus,
    val protectionLevel: Double,
    val managementCipher: CipherSuite?,
    val issues: List<PmfIssue>,
    val vulnerabilities: List<ManagementFrameVulnerability>,
    val shouldBeMandatory: Boolean,
) {
    /**
     * Whether PMF is properly configured
     */
    val isProperlyConfigured: Boolean
        get() = issues.none { it.severity == PmfIssueSeverity.CRITICAL || it.severity == PmfIssueSeverity.HIGH }

    /**
     * Summary of PMF status
     */
    val summary: String
        get() =
            buildString {
                append("PMF: $status")
                managementCipher?.let { append(" (${it.displayName})") }
                append(" - Protection: ${(protectionLevel * 100).toInt()}%")
                if (issues.isNotEmpty()) {
                    append(" - ${issues.size} issue(s)")
                }
            }
}

/**
 * PMF-related security issue
 *
 * @property severity Issue severity
 * @property description Short description
 * @property recommendation Recommended action
 * @property details Technical details
 * @property attacksEnabled Attacks that are possible due to this issue
 */
data class PmfIssue(
    val severity: PmfIssueSeverity,
    val description: String,
    val recommendation: String,
    val details: String,
    val attacksEnabled: List<String>,
)

/**
 * Management frame vulnerability (when PMF is not enabled)
 *
 * @property attackType Type of attack
 * @property description Description of the vulnerability
 * @property severity Vulnerability severity
 * @property toolsUsed Common attack tools
 */
data class ManagementFrameVulnerability(
    val attackType: String,
    val description: String,
    val severity: VulnerabilitySeverity,
    val toolsUsed: List<String>,
)

/**
 * PMF status
 */
enum class PmfStatus {
    /** PMF disabled - no protection */
    DISABLED,

    /** PMF capable - optional protection */
    CAPABLE,

    /** PMF required - mandatory protection */
    REQUIRED,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                DISABLED -> "Disabled - No protection"
                CAPABLE -> "Capable - Optional protection"
                REQUIRED -> "Required - Mandatory protection"
            }
}

/**
 * PMF issue severity levels
 */
enum class PmfIssueSeverity {
    /** Low severity */
    LOW,

    /** Medium severity */
    MEDIUM,

    /** High severity */
    HIGH,

    /** Critical severity */
    CRITICAL,
}

/**
 * Vulnerability severity levels
 */
enum class VulnerabilitySeverity {
    /** Low severity */
    LOW,

    /** Medium severity */
    MEDIUM,

    /** High severity */
    HIGH,

    /** Critical severity */
    CRITICAL,
}
