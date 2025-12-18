package io.lamco.netkit.security.model

import io.lamco.netkit.model.topology.AuthType
import io.lamco.netkit.model.topology.CipherSuite
import io.lamco.netkit.model.topology.SecurityFingerprint

/**
 * Comprehensive security analysis for a single BSS (Basic Service Set / Access Point)
 *
 * Provides multi-dimensional security scoring combining:
 * - Authentication type strength
 * - Cipher suite quality
 * - Management frame protection
 * - Overall security posture
 *
 * Each score is normalized to 0.0-1.0 scale where:
 * - 0.0 = No security / Completely vulnerable
 * - 0.3 = Weak security (deprecated protocols)
 * - 0.7 = Adequate security (WPA2-PSK minimum)
 * - 0.9 = Strong security (WPA3 or WPA2 + PMF)
 * - 1.0 = Maximum security (WPA3-Enterprise 192-bit)
 *
 * Based on:
 * - IEEE 802.11-2020 security specifications
 * - WPA3 specification (Wi-Fi Alliance)
 * - NIST SP 800-153 Guidelines for Securing Wireless Local Area Networks
 * - NIST SP 800-97 Establishing Wireless Robust Security Networks
 *
 * @property bssid MAC address of the access point (Basic Service Set Identifier)
 * @property ssid Network name (Service Set Identifier), may be hidden
 * @property authType Authentication and key management type
 * @property cipherStrengthScore Quality of encryption ciphers (0.0-1.0)
 * @property managementProtectionScore Management frame protection strength (0.0-1.0)
 * @property overallSecurityScore Composite security score (0.0-1.0)
 * @property issues List of specific security issues detected
 *
 * @see SecurityIssue
 * @see SecurityAnalyzer
 */
data class SecurityScorePerBss(
    val bssid: String,
    val ssid: String?,
    val authType: AuthType,
    val cipherSet: Set<CipherSuite>,
    val cipherStrengthScore: Double,
    val managementProtectionScore: Double,
    val overallSecurityScore: Double,
    val issues: List<SecurityIssue>,
) {
    init {
        require(bssid.isNotBlank()) { "BSSID must not be blank" }
        require(cipherStrengthScore in 0.0..1.0) {
            "Cipher strength score must be 0.0-1.0, got $cipherStrengthScore"
        }
        require(managementProtectionScore in 0.0..1.0) {
            "Management protection score must be 0.0-1.0, got $managementProtectionScore"
        }
        require(overallSecurityScore in 0.0..1.0) {
            "Overall security score must be 0.0-1.0, got $overallSecurityScore"
        }
    }

    /**
     * Security level category
     */
    val securityLevel: SecurityLevel
        get() =
            when {
                overallSecurityScore >= 0.90 -> SecurityLevel.EXCELLENT
                overallSecurityScore >= 0.70 -> SecurityLevel.GOOD
                overallSecurityScore >= 0.50 -> SecurityLevel.FAIR
                overallSecurityScore >= 0.30 -> SecurityLevel.WEAK
                else -> SecurityLevel.INSECURE
            }

    /**
     * Whether this network meets minimum security standards
     * Minimum: WPA2-PSK with AES-CCMP (score >= 0.70)
     */
    val meetsMinimumSecurity: Boolean
        get() = overallSecurityScore >= 0.70

    /**
     * Whether this network uses modern security (WPA3 or WPA2 with strong config)
     */
    val usesModernSecurity: Boolean
        get() = authType.isModern || (authType == AuthType.WPA2_PSK && overallSecurityScore >= 0.80)

    /**
     * Count of issues by severity
     */
    val criticalIssueCount: Int
        get() = issues.count { it.severity == SecurityIssue.Severity.CRITICAL }

    val highIssueCount: Int
        get() = issues.count { it.severity == SecurityIssue.Severity.HIGH }

    val mediumIssueCount: Int
        get() = issues.count { it.severity == SecurityIssue.Severity.MEDIUM }

    val lowIssueCount: Int
        get() = issues.count { it.severity == SecurityIssue.Severity.LOW }

    /**
     * Whether there are any critical security issues
     */
    val hasCriticalIssues: Boolean
        get() = criticalIssueCount > 0

    /**
     * Whether there are any high severity issues
     */
    val hasHighSeverityIssues: Boolean
        get() = highIssueCount > 0

    /**
     * Total issue count
     */
    val totalIssueCount: Int
        get() = issues.size

    /**
     * Primary security concern (highest severity issue)
     */
    val primaryConcern: SecurityIssue?
        get() = issues.maxByOrNull { it.severity.ordinal }

    /**
     * Human-readable security summary
     */
    val securitySummary: String
        get() =
            buildString {
                append("$securityLevel: ${authType.displayName}")
                when {
                    hasCriticalIssues -> append(" - $criticalIssueCount CRITICAL issue(s)")
                    hasHighSeverityIssues -> append(" - $highIssueCount HIGH severity issue(s)")
                    totalIssueCount > 0 -> append(" - $totalIssueCount issue(s) detected")
                    else -> append(" - No issues detected")
                }
            }

    /**
     * Detailed security report
     */
    val detailedReport: String
        get() =
            buildString {
                appendLine("=== Security Analysis for ${ssid ?: "Hidden Network"} ===")
                appendLine("BSSID: $bssid")
                appendLine("Overall Security: $securityLevel (${(overallSecurityScore * 100).toInt()}%)")
                appendLine()
                appendLine("Authentication: ${authType.displayName} (${authType.securityLevel}%)")
                appendLine("Cipher Strength: ${(cipherStrengthScore * 100).toInt()}%")
                appendLine("Management Protection: ${(managementProtectionScore * 100).toInt()}%")
                appendLine()

                if (issues.isNotEmpty()) {
                    appendLine("Security Issues (${issues.size}):")
                    issues.sortedByDescending { it.severity }.forEach { issue ->
                        appendLine("  [${issue.severity}] ${issue.shortDescription}")
                    }
                } else {
                    appendLine("No security issues detected.")
                }
            }

    companion object {
        /**
         * Calculate cipher strength score from cipher set
         *
         * Uses weighted average of cipher strengths, with penalty for weak ciphers.
         *
         * @param ciphers Set of cipher suites
         * @return Score 0.0-1.0
         */
        fun calculateCipherScore(ciphers: Set<CipherSuite>): Double {
            if (ciphers.isEmpty()) return 0.0

            // Get strongest and weakest ciphers
            val strongest = ciphers.maxByOrNull { it.strength }?.strength ?: 0
            val weakest = ciphers.minByOrNull { it.strength }?.strength ?: 0

            // Average cipher strength
            val avgStrength = ciphers.map { it.strength }.average()

            // Convert to 0.0-1.0 scale
            val baseScore = avgStrength / 100.0

            // Penalty for having weak ciphers alongside strong ones
            val weakCipherPenalty = if (weakest < 50 && strongest > 70) 0.2 else 0.0

            return (baseScore - weakCipherPenalty).coerceIn(0.0, 1.0)
        }

        /**
         * Calculate management protection score
         *
         * @param pmfRequired Whether PMF is mandatory
         * @param pmfCapable Whether PMF is supported (optional)
         * @param managementCipher BIP cipher for management frames (null if PMF disabled)
         * @return Score 0.0-1.0
         */
        fun calculateManagementProtectionScore(
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

                // PMF capable (optional) with good cipher
                pmfCapable && managementCipher != null && managementCipher.strength >= 80 -> 0.6

                // PMF capable but not enforced
                pmfCapable -> 0.4

                // No PMF support
                else -> 0.0
            }

        /**
         * Calculate overall security score
         *
         * Weighted combination of:
         * - Authentication type: 40%
         * - Cipher strength: 35%
         * - Management protection: 25%
         *
         * @param authType Authentication type
         * @param cipherScore Cipher strength score (0.0-1.0)
         * @param mgmtProtectionScore Management protection score (0.0-1.0)
         * @return Overall score 0.0-1.0
         */
        fun calculateOverallScore(
            authType: AuthType,
            cipherScore: Double,
            mgmtProtectionScore: Double,
        ): Double {
            val authScore = authType.securityLevel / 100.0

            // Weighted combination
            val weighted =
                (authScore * 0.40) +
                    (cipherScore * 0.35) +
                    (mgmtProtectionScore * 0.25)

            return weighted.coerceIn(0.0, 1.0)
        }

        /**
         * Create security score from security fingerprint
         *
         * @param bssid BSS identifier
         * @param ssid Network name (optional)
         * @param fingerprint Security configuration fingerprint
         * @param pmfCapable Whether PMF is supported but not required
         * @param managementCipher BIP cipher (null if no PMF)
         * @param additionalIssues Additional issues detected by analyzer
         * @return SecurityScorePerBss
         */
        fun fromFingerprint(
            bssid: String,
            ssid: String?,
            fingerprint: SecurityFingerprint,
            pmfCapable: Boolean = false,
            managementCipher: CipherSuite? = null,
            additionalIssues: List<SecurityIssue> = emptyList(),
        ): SecurityScorePerBss {
            val cipherScore = calculateCipherScore(fingerprint.cipherSet)
            val mgmtScore =
                calculateManagementProtectionScore(
                    fingerprint.pmfRequired,
                    pmfCapable,
                    managementCipher,
                )
            val overallScore = calculateOverallScore(fingerprint.authType, cipherScore, mgmtScore)

            // Detect issues from fingerprint
            val fingerprintIssues = mutableListOf<SecurityIssue>()

            // Check for weak/deprecated ciphers
            fingerprint.cipherSet.forEach { cipher ->
                when (cipher) {
                    CipherSuite.WEP_40, CipherSuite.WEP_104 -> {
                        fingerprintIssues.add(SecurityIssue.WepInUse)
                        fingerprintIssues.add(SecurityIssue.LegacyCipher(cipher))
                    }
                    CipherSuite.TKIP -> {
                        fingerprintIssues.add(SecurityIssue.TkipInUse)
                        fingerprintIssues.add(SecurityIssue.LegacyCipher(cipher))
                    }
                    else -> {}
                }
            }

            // Check for missing PMF on protected networks
            if (!fingerprint.pmfRequired && fingerprint.authType != AuthType.OPEN) {
                fingerprintIssues.add(SecurityIssue.PmfDisabledOnProtectedNetwork(fingerprint.authType))
            }

            // Check for open networks without OWE
            if (fingerprint.authType == AuthType.OPEN &&
                fingerprint.transitionMode != io.lamco.netkit.model.topology.TransitionMode.OPEN_OWE
            ) {
                fingerprintIssues.add(SecurityIssue.OpenNetworkWithoutOwe)
            }

            // Check for deprecated auth types
            if (fingerprint.authType.isDeprecated) {
                fingerprintIssues.add(SecurityIssue.DeprecatedAuthType(fingerprint.authType))
            }

            // Check transition modes
            fingerprint.transitionMode?.let { mode ->
                when (mode) {
                    io.lamco.netkit.model.topology.TransitionMode.WPA2_WPA3 ->
                        fingerprintIssues.add(SecurityIssue.TransitionalMode(AuthType.WPA2_PSK, AuthType.WPA3_SAE))
                    io.lamco.netkit.model.topology.TransitionMode.OPEN_OWE ->
                        ssid?.let { fingerprintIssues.add(SecurityIssue.OweTransitionWithOpenSideVisible(it)) }
                    io.lamco.netkit.model.topology.TransitionMode.WPA_WPA2 ->
                        fingerprintIssues.add(SecurityIssue.TransitionalMode(AuthType.WPA_PSK, AuthType.WPA2_PSK))
                }
            }

            // Combine fingerprint issues with additional issues
            val allIssues = (fingerprintIssues + additionalIssues).distinct()

            return SecurityScorePerBss(
                bssid = bssid,
                ssid = ssid,
                authType = fingerprint.authType,
                cipherSet = fingerprint.cipherSet,
                cipherStrengthScore = cipherScore,
                managementProtectionScore = mgmtScore,
                overallSecurityScore = overallScore,
                issues = allIssues,
            )
        }
    }
}

/**
 * Security level categories
 *
 * IMPORTANT: Ordered from WORST to BEST (INSECURE=0 → EXCELLENT=4)
 * for correct ordinal comparisons. Higher ordinal = better security.
 */
enum class SecurityLevel {
    /** Insecure (0-29%) - Critical vulnerabilities */
    INSECURE,

    /** Weak security (30-49%) - Deprecated protocols */
    WEAK,

    /** Fair security (50-69%) - Acceptable but not ideal */
    FAIR,

    /** Good security (70-89%) - WPA2 with strong ciphers */
    GOOD,

    /** Excellent security (90%+) - WPA3 or strong WPA2 + PMF */
    EXCELLENT,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent - Modern security standards"
                GOOD -> "Good - Adequate protection"
                FAIR -> "Fair - Consider upgrading"
                WEAK -> "Weak - Upgrade recommended"
                INSECURE -> "Insecure - Critical vulnerabilities"
            }

    /**
     * Color indicator (for UI)
     */
    val colorHint: String
        get() =
            when (this) {
                EXCELLENT -> "green"
                GOOD -> "lightgreen"
                FAIR -> "yellow"
                WEAK -> "orange"
                INSECURE -> "red"
            }
}
