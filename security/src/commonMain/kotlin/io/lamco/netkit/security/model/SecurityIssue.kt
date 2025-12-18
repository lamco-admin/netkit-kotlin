package io.lamco.netkit.security.model

import io.lamco.netkit.model.topology.AuthType
import io.lamco.netkit.model.topology.CipherSuite

/**
 * Security issues detected in WiFi network configuration
 *
 * Sealed class hierarchy representing various security vulnerabilities,
 * misconfigurations, and weaknesses found in WiFi networks.
 *
 * Each issue type includes relevant context and severity implications.
 * Issues are categorized by:
 * - Cryptographic weaknesses (weak ciphers, deprecated protocols)
 * - Configuration problems (missing PMF, transitional modes)
 * - Attack vectors (WPS vulnerabilities, open networks)
 * - Best practice violations (missing roaming optimizations)
 *
 * Based on:
 * - IEEE 802.11-2020 security specifications
 * - WPA3 security requirements
 * - NIST wireless security guidelines
 * - Industry best practices
 *
 * @see SecurityScorePerBss
 * @see SecurityAnalyzer
 */
sealed class SecurityIssue {
    /**
     * Severity level of the security issue
     */
    abstract val severity: Severity

    /**
     * Human-readable short description
     */
    abstract val shortDescription: String

    /**
     * Detailed technical explanation
     */
    abstract val technicalDetails: String

    /**
     * Recommended remediation action
     */
    abstract val recommendation: String

    /**
     * Issue severity levels
     */
    enum class Severity {
        /** Informational - no immediate risk */
        INFO,

        /** Low severity - minor weakness */
        LOW,

        /** Medium severity - notable security gap */
        MEDIUM,

        /** High severity - serious vulnerability */
        HIGH,

        /** Critical severity - immediate security risk */
        CRITICAL,
    }

    // ============================================
    // CRYPTOGRAPHIC WEAKNESSES
    // ============================================

    /**
     * Legacy cipher suite in use (TKIP, WEP)
     *
     * Legacy ciphers like TKIP and WEP have known cryptographic weaknesses
     * that can be exploited to decrypt network traffic.
     *
     * @property cipher The weak cipher suite detected
     */
    data class LegacyCipher(
        val cipher: CipherSuite,
    ) : SecurityIssue() {
        override val severity =
            when (cipher) {
                CipherSuite.WEP_40, CipherSuite.WEP_104 -> Severity.CRITICAL
                CipherSuite.TKIP -> Severity.HIGH
                else -> Severity.MEDIUM
            }

        override val shortDescription = "Weak cipher: ${cipher.displayName}"

        override val technicalDetails =
            when (cipher) {
                CipherSuite.WEP_40, CipherSuite.WEP_104 ->
                    "WEP encryption can be cracked in minutes using freely available tools. " +
                        "All network traffic is vulnerable to eavesdropping."
                CipherSuite.TKIP ->
                    "TKIP has known vulnerabilities (IEEE 802.11w deprecated it). " +
                        "Vulnerable to packet injection and decryption attacks."
                else ->
                    "Cipher ${cipher.displayName} has security limitations and should be upgraded."
            }

        override val recommendation =
            when (cipher) {
                CipherSuite.WEP_40, CipherSuite.WEP_104 ->
                    "Immediately upgrade to WPA2-Personal (AES-CCMP) or WPA3-Personal. " +
                        "WEP provides no meaningful security."
                CipherSuite.TKIP ->
                    "Upgrade to WPA2 with AES-CCMP or WPA3. " +
                        "Disable TKIP support in router settings."
                else ->
                    "Upgrade to modern cipher suites (CCMP, GCMP)."
            }
    }

    /**
     * WEP encryption in use
     *
     * WEP is completely broken and can be cracked in minutes.
     * Provides no meaningful security.
     */
    object WepInUse : SecurityIssue() {
        override val severity = Severity.CRITICAL
        override val shortDescription = "WEP encryption (completely broken)"
        override val technicalDetails =
            "WEP (Wired Equivalent Privacy) is cryptographically broken. " +
                "Attacks can recover the encryption key in minutes with minimal traffic capture. " +
                "Deprecated in 2004, forbidden in WPA standards."
        override val recommendation =
            "Immediately disable WEP and upgrade to WPA2-Personal (minimum) or WPA3-Personal (recommended). " +
                "WEP should never be used under any circumstances."
    }

    /**
     * TKIP cipher in use
     *
     * TKIP was deprecated in IEEE 802.11-2012 and has known vulnerabilities
     * including packet injection and limited decryption attacks.
     */
    object TkipInUse : SecurityIssue() {
        override val severity = Severity.HIGH
        override val shortDescription = "TKIP cipher (deprecated, vulnerable)"
        override val technicalDetails =
            "TKIP (Temporal Key Integrity Protocol) is deprecated and has known weaknesses. " +
                "Vulnerable to Michael MIC key recovery, packet injection, and limited plaintext recovery. " +
                "Deprecated in IEEE 802.11-2012, not allowed in WiFi 6 (802.11ax)."
        override val recommendation =
            "Upgrade to WPA2 with AES-CCMP (minimum) or WPA3 with GCMP (recommended). " +
                "Disable TKIP support in router configuration."
    }

    // ============================================
    // MANAGEMENT FRAME PROTECTION
    // ============================================

    /**
     * PMF (Protected Management Frames) disabled on protected network
     *
     * Without PMF, management frames are vulnerable to spoofing attacks
     * like deauthentication floods and evil twin APs.
     *
     * @property authType Authentication type of the network
     */
    data class PmfDisabledOnProtectedNetwork(
        val authType: AuthType,
    ) : SecurityIssue() {
        override val severity =
            when {
                authType.isModern -> Severity.CRITICAL // PMF is mandatory for WPA3
                authType == AuthType.WPA2_PSK || authType == AuthType.WPA2_ENTERPRISE -> Severity.MEDIUM
                else -> Severity.LOW
            }

        override val shortDescription = "Protected Management Frames (PMF) disabled"

        override val technicalDetails =
            "IEEE 802.11w Protected Management Frames (PMF) prevents spoofing of management frames. " +
                "Without PMF, networks are vulnerable to deauthentication attacks, evil twin APs, " +
                "and other management frame injection attacks. " +
                if (authType.isModern) {
                    "PMF is MANDATORY for WPA3 and WiFi 6E (6 GHz) - this network is misconfigured."
                } else {
                    "PMF is optional for WPA2 but strongly recommended for security."
                }

        override val recommendation =
            if (authType.isModern) {
                "This is a critical misconfiguration. Enable PMF (802.11w) immediately. " +
                    "WPA3 networks MUST have PMF enabled."
            } else {
                "Enable PMF (Protected Management Frames / 802.11w) in router settings. " +
                    "Look for 'PMF' or '802.11w' option and set to 'Required' or 'Capable'."
            }
    }

    /**
     * Weak or missing group management cipher
     *
     * Management frame protection requires a strong BIP cipher.
     * Weak ciphers compromise PMF effectiveness.
     */
    object WeakGroupMgmtCipher : SecurityIssue() {
        override val severity = Severity.MEDIUM
        override val shortDescription = "Weak management frame cipher"
        override val technicalDetails =
            "Protected Management Frames (PMF) uses BIP (Broadcast/Multicast Integrity Protocol) " +
                "to protect group-addressed management frames. Weak BIP ciphers reduce PMF effectiveness. " +
                "Strong ciphers: BIP-CMAC-128, BIP-GMAC-128/256."
        override val recommendation =
            "Configure router to use BIP-CMAC-128 (minimum) or BIP-GMAC-256 (best) " +
                "for management frame protection."
    }

    // ============================================
    // OPEN NETWORKS
    // ============================================

    /**
     * Open network without OWE (Opportunistic Wireless Encryption)
     *
     * Traditional open networks transmit all traffic unencrypted.
     * OWE provides encryption without requiring a password.
     */
    object OpenNetworkWithoutOwe : SecurityIssue() {
        override val severity = Severity.MEDIUM
        override val shortDescription = "Open network - no encryption"
        override val technicalDetails =
            "Open WiFi networks transmit all data in plaintext, visible to anyone within range. " +
                "All traffic (including passwords, emails, browsing) is vulnerable to eavesdropping. " +
                "OWE (RFC 8110 Opportunistic Wireless Encryption) provides encryption for open networks."
        override val recommendation =
            "If this is a public network, upgrade to OWE (Enhanced Open) to encrypt traffic. " +
                "If this is a home network, configure WPA2-Personal or WPA3-Personal with a strong password."
    }

    /**
     * OWE transition mode with open side visible
     *
     * In OWE transition mode, the unencrypted open SSID is still broadcast,
     * allowing legacy clients to connect without encryption.
     *
     * @property transitionSsid The open SSID in transition mode
     */
    data class OweTransitionWithOpenSideVisible(
        val transitionSsid: String,
    ) : SecurityIssue() {
        override val severity = Severity.LOW
        override val shortDescription = "OWE transition mode - legacy clients use no encryption"
        override val technicalDetails =
            "OWE transition mode advertises both encrypted (OWE) and unencrypted (open) networks. " +
                "Clients without OWE support connect to the open side and transmit data in plaintext. " +
                "Transition SSID '$transitionSsid' accepts unencrypted connections."
        override val recommendation =
            "Monitor client adoption of OWE. Once all clients support OWE (RFC 8110), " +
                "disable transition mode and use OWE-only for full encryption."
    }

    // ============================================
    // WPA3 & ENTERPRISE
    // ============================================

    /**
     * Suite-B 192-bit security missing when required
     *
     * High-security environments (government, military, finance) may require
     * WPA3-Enterprise 192-bit mode with Suite-B cryptography.
     */
    object SuiteBMissingForHighSecurityClaim : SecurityIssue() {
        override val severity = Severity.HIGH
        override val shortDescription = "Suite-B 192-bit security not configured"
        override val technicalDetails =
            "WPA3-Enterprise 192-bit mode provides the highest WiFi security level: " +
                "GCMP-256/CCMP-256 data encryption, BIP-GMAC-256 management protection, " +
                "ECDHE with 384-bit prime curve, ECDSA with 384-bit prime curve. " +
                "Required for CNSA Suite (NSA Commercial National Security Algorithm Suite)."
        override val recommendation =
            "For high-security environments, configure WPA3-Enterprise 192-bit mode. " +
                "Requires: GCMP-256 or CCMP-256 cipher, BIP-GMAC-256 or BIP-CMAC-256, " +
                "and enterprise RADIUS infrastructure."
    }

    // ============================================
    // TRANSITIONAL MODES
    // ============================================

    /**
     * Network operating in transitional security mode
     *
     * Transitional modes allow both legacy and modern clients to connect,
     * but may expose security vulnerabilities on the legacy side.
     *
     * @property from Legacy authentication type
     * @property to Modern authentication type
     */
    data class TransitionalMode(
        val from: AuthType,
        val to: AuthType,
    ) : SecurityIssue() {
        override val severity =
            when {
                from == AuthType.OPEN && to == AuthType.OWE -> Severity.LOW // OWE transition is okay
                from == AuthType.WPA2_PSK && to == AuthType.WPA3_SAE -> Severity.MEDIUM
                else -> Severity.HIGH
            }

        override val shortDescription = "Transitional mode: ${from.displayName} → ${to.displayName}"

        override val technicalDetails =
            "Network operates in transition mode supporting both ${from.displayName} and ${to.displayName}. " +
                when {
                    from == AuthType.WPA2_PSK && to == AuthType.WPA3_SAE ->
                        "WPA2 side remains vulnerable to offline dictionary attacks. " +
                            "WPA3 provides protection against these attacks but only for WPA3-capable clients."
                    from == AuthType.OPEN && to == AuthType.OWE ->
                        "Legacy clients connect without encryption. " +
                            "Only OWE-capable clients get encrypted connections."
                    else ->
                        "Legacy authentication method has known security weaknesses. " +
                            "Transition modes are temporary measures during migration."
                }

        override val recommendation =
            when {
                from == AuthType.WPA2_PSK && to == AuthType.WPA3_SAE ->
                    "Monitor client compatibility. When all devices support WPA3, " +
                        "disable WPA2 and use WPA3-only mode for maximum security."
                from == AuthType.OPEN && to == AuthType.OWE ->
                    "Encourage clients to update to OWE-capable devices. " +
                        "Transition to OWE-only when legacy client count is acceptable."
                else ->
                    "Plan migration to ${to.displayName}-only mode. " +
                        "Transition modes should be temporary."
            }
    }

    // ============================================
    // WPS VULNERABILITIES
    // ============================================

    /**
     * WPS PIN method enabled
     *
     * WPS PIN authentication is vulnerable to brute force attacks.
     * An attacker can recover the PIN in hours using Reaver or Pixie Dust attacks.
     */
    object WpsPinEnabled : SecurityIssue() {
        override val severity = Severity.HIGH
        override val shortDescription = "WPS PIN method enabled"
        override val technicalDetails =
            "WiFi Protected Setup (WPS) PIN method has fundamental design flaws. " +
                "The 8-digit PIN has only 10^4 possible values (due to checksum). " +
                "Vulnerable to brute force (Reaver), Pixie Dust, and offline PIN attacks. " +
                "Some implementations can be cracked in under 4 hours."
        override val recommendation =
            "Disable WPS PIN method in router settings. " +
                "If WPS is needed, use push-button (PBC) method only, and disable immediately after use. " +
                "Best practice: disable WPS entirely."
    }

    /**
     * WPS in unknown or potentially risky mode
     *
     * WPS state cannot be determined or shows unusual configuration.
     */
    object WpsUnknownOrRiskyMode : SecurityIssue() {
        override val severity = Severity.MEDIUM
        override val shortDescription = "WPS configuration unclear or risky"
        override val technicalDetails =
            "WiFi Protected Setup (WPS) state could not be determined or shows unusual configuration. " +
                "WPS has known vulnerabilities, especially the PIN method. " +
                "Unable to verify if secure configuration is in use."
        override val recommendation =
            "Verify WPS status in router settings. " +
                "If WPS is enabled, ensure PIN method is disabled. " +
                "Best practice: disable WPS entirely unless actively needed for device setup."
    }

    // ============================================
    // ROAMING & MULTI-AP
    // ============================================

    /**
     * Missing roaming optimizations on multi-AP network
     *
     * Networks with multiple APs should implement 802.11k/v/r for seamless roaming.
     * Without these, clients experience connection drops and sticky client issues.
     */
    object MissingRoamingOptimizations : SecurityIssue() {
        override val severity = Severity.LOW // Performance issue, not security
        override val shortDescription = "Missing fast roaming features (802.11k/v/r)"
        override val technicalDetails =
            "Multi-AP network detected without 802.11k/v/r fast roaming features: " +
                "- 802.11k: Radio Resource Management (neighbor reports) " +
                "- 802.11v: BSS Transition Management (smart roaming hints) " +
                "- 802.11r: Fast BSS Transition (sub-50ms handoffs) " +
                "Without these, clients may stick to weak APs or experience dropped VoIP/video calls during roaming."
        override val recommendation =
            "Enable 802.11k/v/r in router/AP settings if supported. " +
                "Enterprise APs (Cisco, Aruba, Ubiquiti, Ruckus) typically support these features. " +
                "Improves roaming performance, reduces connection drops."
    }

    // ============================================
    // CONFIGURATION QUALITY
    // ============================================

    /**
     * Inconsistent security across APs in same network
     *
     * APs with same SSID but different security configurations can confuse clients
     * and create security gaps.
     *
     * @property ssid The network name
     * @property configCount Number of different security configurations detected
     */
    data class InconsistentSecurityAcrossAps(
        val ssid: String,
        val configCount: Int,
    ) : SecurityIssue() {
        override val severity = Severity.HIGH
        override val shortDescription = "Inconsistent security configuration"
        override val technicalDetails =
            "Network '$ssid' has $configCount different security configurations across its APs. " +
                "This typically indicates: misconfiguration, mixed firmware versions, or security policy gaps. " +
                "Clients may connect to less secure APs unknowingly."
        override val recommendation =
            "Ensure all APs for network '$ssid' use identical security settings: " +
                "same authentication type, same ciphers, same PMF setting. " +
                "Check router/controller configuration for consistency."
    }

    /**
     * Deprecated authentication type in use
     *
     * WPA (WPA1) and WEP are deprecated and should not be used.
     *
     * @property authType The deprecated authentication type
     */
    data class DeprecatedAuthType(
        val authType: AuthType,
    ) : SecurityIssue() {
        override val severity =
            when (authType) {
                AuthType.WEP -> Severity.CRITICAL
                AuthType.WPA_PSK, AuthType.WPA_ENTERPRISE -> Severity.HIGH
                else -> Severity.MEDIUM
            }

        override val shortDescription = "Deprecated: ${authType.displayName}"

        override val technicalDetails =
            when (authType) {
                AuthType.WEP ->
                    "WEP is fundamentally broken and was deprecated in 2004. " +
                        "Provides no meaningful security."
                AuthType.WPA_PSK, AuthType.WPA_ENTERPRISE ->
                    "WPA (WPA1) uses TKIP cipher which has known vulnerabilities. " +
                        "Deprecated in IEEE 802.11-2012. Not allowed in WiFi 6."
                else ->
                    "Authentication type ${authType.displayName} is deprecated and should be upgraded."
            }

        override val recommendation =
            when (authType) {
                AuthType.WEP ->
                    "Immediately upgrade to WPA2-Personal (minimum) or WPA3-Personal (recommended)."
                AuthType.WPA_PSK, AuthType.WPA_ENTERPRISE ->
                    "Upgrade to WPA2 (minimum) or WPA3 (recommended). " +
                        "Disable WPA1/TKIP support in router settings."
                else ->
                    "Upgrade to modern authentication: WPA2 or WPA3."
            }
    }
}
