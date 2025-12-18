package io.lamco.netkit.security.analyzer

import io.lamco.netkit.model.topology.AuthType
import io.lamco.netkit.model.topology.CipherCategory
import io.lamco.netkit.model.topology.CipherSuite

/**
 * Cipher suite analyzer for WiFi security assessment
 *
 * Analyzes cipher suites to identify:
 * - Deprecated/weak ciphers (WEP, TKIP)
 * - Strong modern ciphers (CCMP, GCMP, 256-bit variants)
 * - Cipher compatibility issues
 * - Recommended cipher configurations
 *
 * Cipher Security Background:
 * - WEP: Completely broken, cracks in minutes
 * - TKIP: Deprecated in IEEE 802.11-2012, vulnerable
 * - CCMP (AES-CCMP): Standard for WPA2, 128-bit
 * - GCMP (AES-GCMP): WiFi 6 standard, more efficient
 * - CCMP-256/GCMP-256: WPA3-Enterprise 192-bit mode
 *
 * References:
 * - IEEE 802.11-2020 Section 9.4.2.25.2 (Cipher suites)
 * - WPA3 Specification (Wi-Fi Alliance)
 * - NIST SP 800-38D (GCM mode)
 * - NIST SP 800-38C (CCM mode)
 *
 * @see CipherSuite
 */
class CipherAnalyzer {
    /**
     * Analyze cipher suite configuration
     *
     * @param authType Authentication type
     * @param cipherSet Set of supported cipher suites
     * @return Cipher analysis result
     */
    fun analyze(
        authType: AuthType,
        cipherSet: Set<CipherSuite>,
    ): CipherAnalysisResult {
        require(cipherSet.isNotEmpty()) { "Cipher set must not be empty" }

        // Identify cipher issues
        val issues = identifyCipherIssues(authType, cipherSet)

        // Determine overall cipher strength
        val strength = calculateCipherStrength(cipherSet)

        // Get recommended ciphers
        val recommended = getRecommendedCiphers(authType)

        // Check compatibility
        val hasCompatibilityIssues = checkCompatibility(authType, cipherSet)

        return CipherAnalysisResult(
            authType = authType,
            cipherSet = cipherSet,
            strength = strength,
            issues = issues,
            recommendedCiphers = recommended,
            hasCompatibilityIssues = hasCompatibilityIssues,
        )
    }

    /**
     * Identify cipher-related security issues
     *
     * @param authType Authentication type
     * @param cipherSet Set of cipher suites
     * @return List of cipher issues
     */
    private fun identifyCipherIssues(
        authType: AuthType,
        cipherSet: Set<CipherSuite>,
    ): List<CipherIssue> {
        val issues = mutableListOf<CipherIssue>()

        // Check for WEP
        val wepCiphers = cipherSet.filter { it.category == CipherCategory.WEP }
        if (wepCiphers.isNotEmpty()) {
            issues.add(
                CipherIssue(
                    severity = CipherIssueSeverity.CRITICAL,
                    ciphers = wepCiphers.toSet(),
                    description = "WEP encryption is completely broken",
                    recommendation = "Immediately upgrade to WPA2 or WPA3 with AES encryption",
                    details =
                        "WEP can be cracked in minutes using freely available tools. " +
                            "Provides no meaningful security. Deprecated in 2004.",
                ),
            )
        }

        // Check for TKIP
        val tkipCiphers = cipherSet.filter { it.category == CipherCategory.TKIP }
        if (tkipCiphers.isNotEmpty()) {
            issues.add(
                CipherIssue(
                    severity = CipherIssueSeverity.HIGH,
                    ciphers = tkipCiphers.toSet(),
                    description = "TKIP cipher is deprecated and vulnerable",
                    recommendation = "Disable TKIP and use only CCMP or GCMP",
                    details =
                        "TKIP was deprecated in IEEE 802.11-2012. Vulnerable to packet injection " +
                            "and limited decryption attacks. Not allowed in WiFi 6 (802.11ax).",
                ),
            )
        }

        // Check for mixed weak/strong ciphers
        val hasWeak = cipherSet.any { it.isDeprecated }
        val hasStrong = cipherSet.any { it.isSecure }
        if (hasWeak && hasStrong) {
            issues.add(
                CipherIssue(
                    severity = CipherIssueSeverity.MEDIUM,
                    ciphers = cipherSet.filter { it.isDeprecated }.toSet(),
                    description = "Mixed weak and strong ciphers",
                    recommendation = "Remove weak ciphers to prevent downgrade attacks",
                    details =
                        "Having both weak and strong ciphers allows attackers to force " +
                            "clients to use the weakest cipher through downgrade attacks.",
                ),
            )
        }

        // Check for missing management frame ciphers
        val dataCiphers = cipherSet.filter { !it.isManagementCipher }
        val mgmtCiphers = cipherSet.filter { it.isManagementCipher }
        if (dataCiphers.isNotEmpty() && mgmtCiphers.isEmpty() && authType != AuthType.OPEN) {
            issues.add(
                CipherIssue(
                    severity = CipherIssueSeverity.MEDIUM,
                    ciphers = emptySet(),
                    description = "No management frame protection cipher configured",
                    recommendation = "Enable PMF with BIP-CMAC-128 or BIP-GMAC-256",
                    details =
                        "Protected Management Frames (PMF / 802.11w) requires a BIP cipher " +
                            "for protecting broadcast/multicast management frames.",
                ),
            )
        }

        // Check for Suite-B requirements (WPA3-Enterprise 192-bit)
        if (authType == AuthType.WPA3_ENTERPRISE_192) {
            val has256BitCipher = cipherSet.any { it.keyLength >= 256 }
            if (!has256BitCipher) {
                issues.add(
                    CipherIssue(
                        severity = CipherIssueSeverity.HIGH,
                        ciphers = cipherSet,
                        description = "WPA3-Enterprise 192-bit requires 256-bit ciphers",
                        recommendation = "Use GCMP-256 or CCMP-256 for data, BIP-GMAC-256 for management",
                        details =
                            "Suite-B 192-bit mode mandates: GCMP-256/CCMP-256 data cipher, " +
                                "BIP-GMAC-256/BIP-CMAC-256 management cipher.",
                    ),
                )
            }
        }

        return issues
    }

    /**
     * Calculate overall cipher strength
     *
     * @param cipherSet Set of cipher suites
     * @return Cipher strength level
     */
    private fun calculateCipherStrength(cipherSet: Set<CipherSuite>): CipherStrength {
        val strongest = cipherSet.maxByOrNull { it.strength }?.strength ?: 0
        val weakest = cipherSet.minByOrNull { it.strength }?.strength ?: 0
        val average = cipherSet.map { it.strength }.average()

        return when {
            // Any WEP = insecure regardless of other ciphers
            cipherSet.any { it.category == CipherCategory.WEP } -> CipherStrength.INSECURE

            // 256-bit ciphers = excellent
            strongest >= 100 && weakest >= 80 -> CipherStrength.EXCELLENT

            // Strong ciphers (CCMP/GCMP) without weak ones = strong
            strongest >= 85 && weakest >= 80 -> CipherStrength.STRONG

            // CCMP/GCMP present but with some weak ciphers = moderate
            strongest >= 80 && average >= 60 -> CipherStrength.MODERATE

            // Primarily weak ciphers = weak
            average < 50 -> CipherStrength.WEAK

            // Fallback
            else -> CipherStrength.MODERATE
        }
    }

    /**
     * Get recommended cipher suites for authentication type
     *
     * @param authType Authentication type
     * @return Set of recommended cipher suites
     */
    private fun getRecommendedCiphers(authType: AuthType): Set<CipherSuite> =
        when (authType) {
            AuthType.WPA3_ENTERPRISE_192 ->
                setOf(
                    CipherSuite.GCMP_256, // Primary data cipher
                    CipherSuite.CCMP_256, // Alternative data cipher
                    CipherSuite.BIP_GMAC_256, // Management cipher
                )

            AuthType.WPA3_SAE, AuthType.WPA3_SAE_PK, AuthType.WPA3_ENTERPRISE ->
                setOf(
                    CipherSuite.GCMP, // Primary for WiFi 6
                    CipherSuite.CCMP, // Fallback
                    CipherSuite.BIP_GMAC_128, // Management
                )

            AuthType.WPA2_PSK, AuthType.WPA2_ENTERPRISE ->
                setOf(
                    CipherSuite.CCMP, // Standard WPA2
                    CipherSuite.BIP_CMAC_128, // PMF if enabled
                )

            AuthType.OWE ->
                setOf(
                    CipherSuite.CCMP, // OWE uses AES
                    CipherSuite.BIP_CMAC_128, // PMF mandatory
                )

            else -> emptySet() // Legacy or unsupported
        }

    /**
     * Check for cipher compatibility issues
     *
     * @param authType Authentication type
     * @param cipherSet Set of cipher suites
     * @return True if compatibility issues detected
     */
    private fun checkCompatibility(
        authType: AuthType,
        cipherSet: Set<CipherSuite>,
    ): Boolean {
        // WPA3 requires strong ciphers
        if (authType.isModern && cipherSet.any { it.isDeprecated }) {
            return true // Modern auth with deprecated ciphers
        }

        // WEP with any auth type other than WEP
        if (authType != AuthType.WEP && cipherSet.any { it.category == CipherCategory.WEP }) {
            return true
        }

        // Enterprise 192-bit requires 256-bit ciphers
        if (authType == AuthType.WPA3_ENTERPRISE_192 && !cipherSet.any { it.keyLength >= 256 }) {
            return true
        }

        return false
    }
}

/**
 * Cipher analysis result
 *
 * @property authType Authentication type
 * @property cipherSet Set of cipher suites
 * @property strength Overall cipher strength
 * @property issues Cipher-related security issues
 * @property recommendedCiphers Recommended ciphers for this auth type
 * @property hasCompatibilityIssues Whether compatibility issues exist
 */
data class CipherAnalysisResult(
    val authType: AuthType,
    val cipherSet: Set<CipherSuite>,
    val strength: CipherStrength,
    val issues: List<CipherIssue>,
    val recommendedCiphers: Set<CipherSuite>,
    val hasCompatibilityIssues: Boolean,
) {
    /**
     * Strongest cipher in the set
     */
    val strongestCipher: CipherSuite
        get() = cipherSet.maxByOrNull { it.strength } ?: CipherSuite.NONE

    /**
     * Weakest cipher in the set
     */
    val weakestCipher: CipherSuite
        get() = cipherSet.minByOrNull { it.strength } ?: CipherSuite.NONE

    /**
     * Whether any weak ciphers are present
     */
    val hasWeakCiphers: Boolean
        get() = cipherSet.any { it.isDeprecated }

    /**
     * Summary of cipher analysis
     */
    val summary: String
        get() =
            buildString {
                append("Cipher Strength: $strength")
                if (hasWeakCiphers) {
                    append(" (weak ciphers present)")
                }
                if (issues.isNotEmpty()) {
                    append(" - ${issues.size} issue(s)")
                }
            }
}

/**
 * Cipher-related security issue
 *
 * @property severity Issue severity
 * @property ciphers Ciphers involved in this issue
 * @property description Short description
 * @property recommendation Recommended action
 * @property details Technical details
 */
data class CipherIssue(
    val severity: CipherIssueSeverity,
    val ciphers: Set<CipherSuite>,
    val description: String,
    val recommendation: String,
    val details: String,
)

/**
 * Overall cipher strength levels
 */
enum class CipherStrength {
    /** Completely insecure (WEP) */
    INSECURE,

    /** Weak ciphers (TKIP, mixed) */
    WEAK,

    /** Moderate strength (CCMP with some weak) */
    MODERATE,

    /** Strong ciphers (CCMP/GCMP only) */
    STRONG,

    /** Excellent (256-bit Suite-B) */
    EXCELLENT,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                INSECURE -> "Insecure - Critical vulnerabilities"
                WEAK -> "Weak - Deprecated ciphers"
                MODERATE -> "Moderate - Some weak ciphers present"
                STRONG -> "Strong - Modern encryption"
                EXCELLENT -> "Excellent - Maximum strength"
            }
}

/**
 * Cipher issue severity levels
 */
enum class CipherIssueSeverity {
    /** Low severity */
    LOW,

    /** Medium severity */
    MEDIUM,

    /** High severity */
    HIGH,

    /** Critical severity */
    CRITICAL,
}
