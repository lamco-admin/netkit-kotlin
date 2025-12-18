package io.lamco.netkit.optimizer.model

/**
 * Enterprise network configuration and feature detection
 *
 * Models enterprise WiFi features that can be detected or inferred through
 * scanning and observation. Most enterprise configuration is not directly
 * accessible via WiFi scanning, so this focuses on observable behavior.
 *
 * Enterprise features include:
 * - VLAN segmentation (inferred from SSID patterns, security)
 * - 802.1X authentication (detectable from beacon IEs)
 * - Guest network isolation (inferred from SSID naming)
 * - Captive portals (detectable from DNS/HTTP behavior)
 *
 * Full enterprise configuration requires controller access,
 * which is out of scope for this scanning-based analyzer.
 *
 * @property ssid Network SSID
 * @property has8021X Whether 802.1X authentication is detected
 * @property hasVlanSupport Whether VLAN tagging is likely present (inferred)
 * @property isGuestNetwork Whether this appears to be a guest network
 * @property hasCaptivePortal Whether captive portal is detected
 * @property enterpriseFeatures Set of detected enterprise features
 */
data class EnterpriseConfig(
    val ssid: String,
    val has8021X: Boolean = false,
    val hasVlanSupport: Boolean = false,
    val isGuestNetwork: Boolean = false,
    val hasCaptivePortal: Boolean = false,
    val enterpriseFeatures: Set<EnterpriseFeature> = emptySet()
) {
    init {
        require(ssid.isNotBlank()) {
            "SSID must not be blank"
        }
    }

    /**
     * Whether this network has enterprise-grade features
     */
    val isEnterpriseGrade: Boolean
        get() = has8021X || hasVlanSupport || enterpriseFeatures.size >= 2

    /**
     * Enterprise security score (0-100)
     * Higher score indicates better enterprise security practices
     */
    val enterpriseSecurityScore: Int
        get() {
            var score = 0

            // 802.1X authentication (40 points)
            if (has8021X) score += 40

            // VLAN segmentation (20 points)
            if (hasVlanSupport) score += 20

            // Guest network isolation (15 points)
            if (isGuestNetwork) score += 15

            // Per-feature bonuses (5 points each)
            score += enterpriseFeatures.size * 5

            // Captive portal (10 points for guest networks)
            if (hasCaptivePortal && isGuestNetwork) score += 10

            return score.coerceIn(0, 100)
        }

    /**
     * Enterprise deployment tier
     */
    val deploymentTier: EnterpriseDeploymentTier
        get() = when {
            has8021X && hasVlanSupport && enterpriseFeatures.size >= 3 -> EnterpriseDeploymentTier.FULL_ENTERPRISE
            has8021X || hasVlanSupport -> EnterpriseDeploymentTier.PARTIAL_ENTERPRISE
            isGuestNetwork -> EnterpriseDeploymentTier.GUEST_CAPABLE
            else -> EnterpriseDeploymentTier.CONSUMER
        }

    /**
     * Human-readable summary
     */
    val summary: String
        get() = buildString {
            append("\"$ssid\" - ")
            append("${deploymentTier.displayName}")
            if (has8021X) append(", 802.1X")
            if (hasVlanSupport) append(", VLAN")
            if (isGuestNetwork) append(", Guest")
            if (hasCaptivePortal) append(", Portal")
        }
}

/**
 * Enterprise deployment tiers
 */
enum class EnterpriseDeploymentTier(val displayName: String, val description: String) {
    /**
     * Full enterprise deployment
     * - 802.1X authentication
     * - VLAN segmentation
     * - Multiple enterprise features
     * - Typical: Large enterprise, university, hospital
     */
    FULL_ENTERPRISE(
        "Full Enterprise",
        "Complete enterprise feature set with 802.1X and VLANs"
    ),

    /**
     * Partial enterprise deployment
     * - Some enterprise features (802.1X or VLANs)
     * - Professional but not fully featured
     * - Typical: Small/medium business, managed office
     */
    PARTIAL_ENTERPRISE(
        "Partial Enterprise",
        "Some enterprise features present"
    ),

    /**
     * Guest network capable
     * - Guest network segregation
     * - May have captive portal
     * - Basic enterprise feature
     * - Typical: Small business, retail, hospitality
     */
    GUEST_CAPABLE(
        "Guest Network",
        "Guest network isolation capability"
    ),

    /**
     * Consumer deployment
     * - No enterprise features detected
     * - Standard home/small office
     */
    CONSUMER(
        "Consumer",
        "No enterprise features detected"
    )
}

/**
 * Detectable enterprise features
 */
enum class EnterpriseFeature(val displayName: String, val description: String) {
    /**
     * 802.1X/EAP authentication
     * Detectable from: RSN IE, AKM suites in beacon
     */
    RADIUS_AUTHENTICATION(
        "RADIUS/802.1X",
        "Enterprise authentication via RADIUS"
    ),

    /**
     * VLAN tagging and segmentation
     * Inferred from: Multiple SSIDs, naming patterns, security variations
     */
    VLAN_SEGMENTATION(
        "VLAN Segmentation",
        "Network segmentation via VLANs"
    ),

    /**
     * Guest network isolation
     * Inferred from: SSID naming (guest, visitor), open security
     */
    GUEST_NETWORK_ISOLATION(
        "Guest Isolation",
        "Isolated guest network"
    ),

    /**
     * Captive portal for authentication/acceptance
     * Detectable from: DNS redirection, HTTP interception
     */
    CAPTIVE_PORTAL(
        "Captive Portal",
        "Web-based authentication or terms acceptance"
    ),

    /**
     * Fast roaming (802.11k/r/v)
     * Detectable from: RSN capabilities, extended capabilities IE
     */
    FAST_ROAMING(
        "Fast Roaming",
        "802.11k/r/v for seamless roaming"
    ),

    /**
     * Band steering capability
     * Inferred from: Dual-band SSIDs, similar BSSIDs across bands
     */
    BAND_STEERING(
        "Band Steering",
        "Automatic client band selection"
    ),

    /**
     * Load balancing across APs
     * Inferred from: Multiple APs, coordinated channels
     */
    LOAD_BALANCING(
        "Load Balancing",
        "Client distribution across APs"
    ),

    /**
     * Rogue AP detection capability
     * Cannot be directly detected, inferred from enterprise deployment
     */
    ROGUE_AP_DETECTION(
        "Rogue AP Detection",
        "Security monitoring for unauthorized APs"
    ),

    /**
     * Wireless IDS/IPS
     * Cannot be directly detected, inferred from enterprise tier
     */
    WIRELESS_IDS(
        "Wireless IDS/IPS",
        "Intrusion detection/prevention"
    ),

    /**
     * Client fingerprinting/profiling
     * Inferred from enterprise-grade deployment
     */
    CLIENT_PROFILING(
        "Client Profiling",
        "Device type detection and policy enforcement"
    )
}

/**
 * VLAN configuration (inferred)
 *
 * VLANs cannot be directly detected via WiFi scanning, but can be
 * inferred from network naming patterns and security variations.
 *
 * @property vlanId VLAN ID (1-4094), null if unknown
 * @property purpose Inferred VLAN purpose
 * @property ssidPattern SSID naming pattern indicating VLAN
 */
data class VlanConfig(
    val vlanId: Int? = null,
    val purpose: VlanPurpose,
    val ssidPattern: String? = null
) {
    init {
        if (vlanId != null) {
            require(vlanId in 1..4094) {
                "VLAN ID must be 1-4094, got $vlanId"
            }
        }
    }

    /**
     * Whether VLAN ID is known
     */
    val hasKnownId: Boolean
        get() = vlanId != null
}

/**
 * Common VLAN purposes in enterprise deployments
 */
enum class VlanPurpose(val displayName: String, val typicalSsidPatterns: List<String>) {
    /**
     * Corporate employee network
     */
    CORPORATE(
        "Corporate",
        listOf("corp", "employee", "staff", "internal")
    ),

    /**
     * Guest/visitor network
     */
    GUEST(
        "Guest",
        listOf("guest", "visitor", "public")
    ),

    /**
     * IoT/device network
     */
    IOT(
        "IoT/Devices",
        listOf("iot", "devices", "things", "sensor")
    ),

    /**
     * Voice/VoIP network
     */
    VOICE(
        "Voice/VoIP",
        listOf("voice", "voip", "phone")
    ),

    /**
     * Management/admin network
     */
    MANAGEMENT(
        "Management",
        listOf("mgmt", "admin", "management")
    ),

    /**
     * Unknown purpose
     */
    UNKNOWN(
        "Unknown",
        emptyList()
    )
}

/**
 * 802.1X/EAP configuration
 *
 * Detectable from RSN Information Element in beacons.
 *
 * @property isEnabled Whether 802.1X is enabled
 * @property eapMethods Detected EAP methods (if observable)
 * @property requiresCertificate Whether client certificates required
 */
data class Dot1XConfiguration(
    val isEnabled: Boolean,
    val eapMethods: Set<EapMethod> = emptySet(),
    val requiresCertificate: Boolean = false
) {
    /**
     * Security strength of 802.1X configuration
     */
    val securityStrength: Dot1XSecurityStrength
        get() = when {
            !isEnabled -> Dot1XSecurityStrength.NONE
            requiresCertificate -> Dot1XSecurityStrength.VERY_STRONG
            eapMethods.any { it.isSecure } -> Dot1XSecurityStrength.STRONG
            eapMethods.isNotEmpty() -> Dot1XSecurityStrength.MODERATE
            else -> Dot1XSecurityStrength.UNKNOWN
        }
}

/**
 * EAP (Extensible Authentication Protocol) methods
 */
enum class EapMethod(
    val displayName: String,
    val isSecure: Boolean,
    val description: String
) {
    /**
     * EAP-TLS (certificate-based, most secure)
     */
    EAP_TLS(
        "EAP-TLS",
        true,
        "Certificate-based authentication"
    ),

    /**
     * EAP-TTLS (tunneled TLS)
     */
    EAP_TTLS(
        "EAP-TTLS",
        true,
        "Tunneled TLS authentication"
    ),

    /**
     * PEAP (Protected EAP)
     */
    PEAP(
        "PEAP",
        true,
        "Protected EAP with server certificate"
    ),

    /**
     * EAP-SIM (GSM SIM card)
     */
    EAP_SIM(
        "EAP-SIM",
        true,
        "GSM SIM card authentication"
    ),

    /**
     * EAP-AKA (UMTS authentication)
     */
    EAP_AKA(
        "EAP-AKA",
        true,
        "UMTS authentication"
    ),

    /**
     * EAP-PWD (Password-based)
     */
    EAP_PWD(
        "EAP-PWD",
        true,
        "Password-based authentication"
    ),

    /**
     * EAP-MD5 (weak, deprecated)
     */
    EAP_MD5(
        "EAP-MD5",
        false,
        "MD5 challenge (deprecated, insecure)"
    ),

    /**
     * LEAP (Cisco proprietary, weak)
     */
    LEAP(
        "LEAP",
        false,
        "Cisco LEAP (deprecated, insecure)"
    )
}

/**
 * 802.1X security strength
 */
enum class Dot1XSecurityStrength(val displayName: String) {
    VERY_STRONG("Very Strong (Certificate-based)"),
    STRONG("Strong (Secure EAP)"),
    MODERATE("Moderate (Basic EAP)"),
    UNKNOWN("Unknown"),
    NONE("Not Enabled")
}

/**
 * Captive portal configuration
 *
 * Detected through DNS/HTTP interception behavior.
 *
 * @property isDetected Whether captive portal detected
 * @property portalType Type of captive portal
 * @property redirectUrl Portal redirect URL (if observable)
 * @property requiresAuth Whether authentication required
 * @property requiresTerms Whether terms/conditions acceptance required
 */
data class CaptivePortalConfig(
    val isDetected: Boolean,
    val portalType: CaptivePortalType = CaptivePortalType.UNKNOWN,
    val redirectUrl: String? = null,
    val requiresAuth: Boolean = false,
    val requiresTerms: Boolean = false
) {
    /**
     * Portal complexity level
     */
    val complexity: PortalComplexity
        get() = when {
            requiresAuth && requiresTerms -> PortalComplexity.COMPLEX
            requiresAuth || requiresTerms -> PortalComplexity.MODERATE
            isDetected -> PortalComplexity.SIMPLE
            else -> PortalComplexity.NONE
        }
}

/**
 * Captive portal types
 */
enum class CaptivePortalType(val displayName: String) {
    /**
     * Click-through (accept terms only)
     */
    CLICK_THROUGH("Click-Through"),

    /**
     * Self-registration (email, phone)
     */
    SELF_REGISTRATION("Self-Registration"),

    /**
     * Voucher/code-based
     */
    VOUCHER("Voucher/Code"),

    /**
     * Social media login
     */
    SOCIAL_LOGIN("Social Media Login"),

    /**
     * Sponsored guest (employee sponsor)
     */
    SPONSORED("Sponsored Guest"),

    /**
     * Payment required (paid WiFi)
     */
    PAYMENT("Payment Required"),

    /**
     * Unknown portal type
     */
    UNKNOWN("Unknown")
}

/**
 * Captive portal complexity
 */
enum class PortalComplexity(val displayName: String) {
    COMPLEX("Complex (Auth + Terms)"),
    MODERATE("Moderate (Auth or Terms)"),
    SIMPLE("Simple (Click-Through)"),
    NONE("No Portal")
}

/**
 * Guest network isolation validation result
 *
 * Validates that guest networks are properly isolated from corporate
 * networks for security.
 *
 * @property isIsolated Whether guest network appears isolated
 * @property isolationMethods Detected isolation methods
 * @property securityIssues Identified security issues
 */
data class GuestIsolationValidation(
    val isIsolated: Boolean,
    val isolationMethods: Set<IsolationMethod> = emptySet(),
    val securityIssues: List<String> = emptyList()
) {
    /**
     * Isolation strength score (0-100)
     */
    val isolationScore: Int
        get() = if (isIsolated) {
            val baseScore = 60
            val methodBonus = isolationMethods.size * 15
            (baseScore + methodBonus - securityIssues.size * 10).coerceIn(0, 100)
        } else {
            0
        }
}

/**
 * Network isolation methods
 */
enum class IsolationMethod(val displayName: String) {
    /**
     * VLAN segmentation
     */
    VLAN_SEPARATION("VLAN Separation"),

    /**
     * Client isolation (peer-to-peer blocking)
     */
    CLIENT_ISOLATION("Client Isolation"),

    /**
     * Firewall rules
     */
    FIREWALL("Firewall Rules"),

    /**
     * Different subnet/IP range
     */
    SUBNET_SEPARATION("Subnet Separation"),

    /**
     * Rate limiting
     */
    RATE_LIMITING("Rate Limiting")
}
